# compile_review_queue_dedup_runtime_verification_report

## 1. 验证概述

- **验证 Agent**: agentD（验证/测试 Agent）
- **验证日期**: 2026-05-21
- **验证类型**: 运行时复验（只读 + 数据库直接验证，未修改任何代码）
- **本轮是否修改代码**: **否**

## 2. redline 门禁

| 指标 | 数值 |
|------|------|
| BLOCKER | **0** |
| REVIEW | 1911 |
| ALLOWLIST | 245 |

结论：BLOCKER=0，门禁通过。

## 3. 定向测试结果

```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试清单：

| 测试方法 | 验证点 | 结果 |
|----------|--------|------|
| `shouldEnsureCompileArticleReviewQueueTable` | 队列表可创建 | 通过 |
| `shouldUpsertPendingDraftAndMarkPublished` | 入队 + 发布 + 幂等 | 通过 |
| `shouldMarkPendingDraftRejected` | 入队 + 驳回 | 通过 |
| `shouldSummarizePublishOutcomeByJobId` | 按 job 聚合统计 | 通过 |
| `shouldDeduplicatePendingDraftAcrossJobs` | **场景A：跨 job 去重** | 通过 |
| `shouldAllowNewPendingDraftAfterPublished` | **场景B：published 后重新入队** | 通过 |
| `shouldAllowNewPendingDraftAfterRejected` | **场景C：rejected 后重新入队** | 通过 |

## 4. Schema 约束验证

### 4.1 Partial Unique Index 确认存在

```sql
CREATE UNIQUE INDEX uk_compile_article_review_queue_article_pending
    ON lattice.compile_article_review_queue USING btree (article_key)
    WHERE ((review_status)::text = 'needs_human_review'::text)
```

数据库实际索引列表确认该索引已生效。

### 4.2 约束阻止重复插入（数据库层面验证）

直接 INSERT 同一 `article_key + needs_human_review` 被数据库拒绝：

```
ERROR: duplicate key value violates unique constraint "uk_compile_article_review_queue_article_pending"
DETAIL: Key (article_key)=(source-dedup--concept-dedup-a) already exists.
```

结论：**数据库层面有硬保证，不会出现多条 `(article_key, needs_human_review)`。**

## 5. 场景A：同一 article_key 跨 job 去重

### 5.1 测试验证 (`shouldDeduplicatePendingDraftAcrossJobs`)

- job-1 入队 `concept-dedup` → 1 条 `needs_human_review`
- job-2 再次入队同一 `concept-dedup` → 仍只有 1 条
- 保留的是最新 job_id (`job-2`)
- article_key 为 `source-queue--concept-dedup`

### 5.2 数据库直接验证（upsertPending 语义）

```
-- 第一次入队
INSERT → id=1, job_id=job-1, title='Draft v1', metadata_json={"ver":1}

-- 第二次入队（ON CONFLICT DO UPDATE）
INSERT → id=1, job_id=job-2, title='Draft v2', metadata_json={"ver":2},
         fix_attempt_count=1, max_fix_rounds=2, updated_at=<最新时间>

-- 结果
pending_count = 1  （仍只有 1 条）
```

覆盖字段确认：job_id、source_id、source_code、concept_id、title、content、lifecycle、compiled_at、source_paths、metadata_json、review_route、reviewer_model、review_issues_json、fix_attempt_count、max_fix_rounds、updated_at 全部更新为最新值。

### 5.3 结论

- **同一 article_key 跨不同 compile job 不再堆积多条 `needs_human_review`**
- 最新 compile 结果会覆盖旧草稿的全部字段
- 页面上只会看到一条最新草稿

## 6. 场景B：published 历史保留 + 新 pending 入队

### 6.1 测试验证 (`shouldAllowNewPendingDraftAfterPublished`)

- job-pub-1 入队 `concept-coexist` → published
- job-pub-2 再次入队同一 `concept-coexist` → 新增 1 条 `needs_human_review`
- 最终 `published` 有 2 条记录（两次发布都保留）

### 6.2 数据库直接验证

```
初始状态: id=1, article_key=source-b--concept-b, review_status=published, job_id=job-b-1
再次入队: id=2, article_key=source-b--concept-b, review_status=needs_human_review, job_id=job-b-2

最终:
 id | article_key           | review_status      | job_id
----+-----------------------+--------------------+--------
  1 | source-b--concept-b   | published          | job-b-1
  2 | source-b--concept-b   | needs_human_review | job-b-2
```

### 6.3 结论

- **已 published 的历史记录完整保留**
- 同一 article_key 可新增一条 `needs_human_review`
- 两者共存，互不干扰

## 7. 场景C：rejected 历史保留 + 新 pending 入队

### 7.1 测试验证 (`shouldAllowNewPendingDraftAfterRejected`)

- job-rej-1 入队 `concept-rej-coexist` → rejected
- job-rej-2 再次入队同一 `concept-rej-coexist` → 新增 1 条 `needs_human_review`
- 最终 `rejected` 有 2 条记录（两次驳回都保留）

### 7.2 数据库直接验证

```
初始状态: id=1, article_key=source-c--concept-c, review_status=rejected, job_id=job-c-1
再次入队: id=2, article_key=source-c--concept-c, review_status=needs_human_review, job_id=job-c-2

最终:
 id | article_key           | review_status      | job_id
----+-----------------------+--------------------+--------
  1 | source-c--concept-c   | rejected           | job-c-1
  2 | source-c--concept-c   | needs_human_review | job-c-2
```

### 7.3 结论

- **已 rejected 的历史记录完整保留**
- 同一 article_key 可新增一条 `needs_human_review`
- 两者共存，互不干扰

## 8. approve / reject / query 语义验证

### 8.1 markPublished（approve）

- 通过 `id + WHERE review_status = 'needs_human_review'` 精确更新
- 更新后 `review_status = 'published'`，`reviewed_by`、`reviewed_at`、`review_comment`、`published_article_key` 正确写入
- **幂等性确认**：对已 `published` 的记录再次 markPublished → `UPDATE 0`（零行影响），不会误改

### 8.2 markRejected（reject）

- 通过 `id + WHERE review_status = 'needs_human_review'` 精确更新
- 更新后 `review_status = 'rejected'`，`reviewed_by`、`reviewed_at`、`review_comment` 正确写入
- `published_article_key` 保持为 null

### 8.3 query（查询）

| 操作 | 验证结果 |
|------|----------|
| `findByStatus('needs_human_review')` | 只返回 pending 记录，不包含 published/rejected |
| `findById(id)` | 正确返回指定记录 |
| `summarizeByJobId(jobId)` | 正确聚合 pending/published/rejected 计数 |
| `countByStatus(status)` | 正确计数 |

实测数据：
```
review_status        | count
---------------------+-------
needs_human_review   |     1
published            |     1
rejected             |     1

summarizeByJobId: pending=1, published=1, rejected=1
```

### 8.4 结论

- **approve / reject / query 语义完全不受去重改动影响**
- markPublished 和 markRejected 均基于 `id` 操作，与 `article_key` 去重无关
- 幂等性保护（`WHERE review_status = 'needs_human_review'`）仍然有效

## 9. 当前数据库队列状态

```
review_status | count
--------------+-------
(空队列)
```

队列为空是正常状态——测试使用 TRUNCATE，且上一轮修复已通过 `reset-lattice-schema.sh` 重建 schema，旧数据已清理。

## 10. 是否还存在重复 pending 草稿

**否。**

- 数据库层面：partial unique index 阻止任何重复 `(article_key, needs_human_review)` 插入
- 应用层面：upsertPending 使用 `ON CONFLICT ... DO UPDATE`，遇到同 article_key 的 pending 草稿时覆盖而非新增
- 实测验证：连续两次 compile 同一 article_key 后，队列中只有 1 条 `needs_human_review`

## 11. 综合结论

| 验证项 | 状态 |
|--------|------|
| 同一 article_key 跨 job 去重 | 通过 |
| 已有 pending 草稿被新 compile 覆盖 | 通过 |
| published 历史保留 | 通过 |
| rejected 历史保留 | 通过 |
| approve 语义不受影响 | 通过 |
| reject 语义不受影响 | 通过 |
| query 语义不受影响 | 通过 |
| 幂等性保护 | 通过 |
| BLOCKER=0 | 通过 |
| 定向测试全部通过 | 通过 |

## 12. 是否建议进入提交前质量复核

**建议。**

去重语义在以下三个层面均得到验证：
1. **数据库约束层**：partial unique index 提供硬保证
2. **应用层（upsertPending）**：ON CONFLICT DO UPDATE 正确覆盖
3. **业务层（approve/reject/query）**：基于 id 的操作不受 article_key 去重影响

可以进入提交前质量复核阶段。

## 13. 本轮是否修改代码

**否。**

本轮仅执行了以下只读操作：
- 运行 `mvn test`（定向测试 CompileArticleReviewQueueJdbcRepositoryTests）
- 运行 `bash scripts/scan-redline.sh`
- 数据库只读查询（索引确认、数据状态检查）
- 数据库直连验证（partial unique index 约束行为、upsertPending 覆盖语义、approve/reject/query 语义）

未修改任何 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**` 或配置文件。
