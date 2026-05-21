# Compile Review Queue Approve 幂等性运行时复验报告

- **复验日期**：2026-05-21
- **复验类型**：运行时复验（只读，不改代码）
- **关联修复报告**：`compile_review_queue_approve_idempotency_fix_result_report.md`
- **验证 Agent**：agentD

---

## 1. Redline 扫描

| 指标 | 结果 |
|---|---|
| BLOCKER | **0** |
| REVIEW | 1911 |
| ALLOWLIST | 245 |
| 总命中 | 2156 |

BLOCKER=0，门禁通过。与修复前基线一致，无新增红线问题。

---

## 2. 复验使用的队列记录与 article_key

### 2.1 幂等目标记录：id=12

| 字段 | 值 |
|---|---|
| id | **12** |
| concept_id | `quality-progress-and-lessons-当前-gate` |
| article_key | `default-source--quality-progress-and-lessons-当前-gate` |
| article_key 是否已在 articles 表 | **是（EXISTS）** |
| 修复前 approve 行为 | 返回 `COMPILE_EXECUTION_FAILED: article already exists` |

### 2.2 正常 approve 对照：id=14

| 字段 | 值 |
|---|---|
| id | **14** |
| concept_id | `quality-progress-and-lessons-踩坑记录` |
| article_key | `default-source--quality-progress-and-lessons-踩坑记录` |
| article_key 是否已在 articles 表 | **否（NEW）** |

### 2.3 正常 reject 对照：id=16

| 字段 | 值 |
|---|---|
| id | **16** |
| concept_id | `document-overview-卡券三期-迁移方案` |
| article_key | `default-source--document-overview-卡券三期-迁移方案` |
| article_key 是否已在 articles 表 | **否（NEW）** |

---

## 3. 幂等 approve 复验（id=12）

### 3.1 接口响应

```json
SUCCESS
  id: 12
  review_status: published
  published_article_key: default-source--quality-progress-and-lessons-当前-gate
  reviewed_by: idempotency-test
  concept_id: quality-progress-and-lessons-当前-gate
```

**结论**：接口返回成功，**不再出现 `COMPILE_EXECUTION_FAILED` 或 `article already exists` 错误**。

### 3.2 各表数量变化

| 表 | 修复前基准 | approve 后 | 变化 | 结论 |
|---|---|---|---|---|
| `articles` | 8 | 8 | **0** | 未重复入库 |
| `article_chunks` | 27 | 27 | **0** | 未重复写入 |
| `article_vector_index` | 8 | 8 | **0** | 未重复索引 |
| `article_chunk_vector_index` | 27 | 27 | **0** | 未重复索引 |

### 3.3 队列状态变化

| 字段 | approve 前 | approve 后 |
|---|---|---|
| `review_status` | `needs_human_review` | **`published`** |
| `reviewed_by` | `null` | **`idempotency-test`** |
| `review_comment` | `null` | **`幂等复验-article_key已存在`** |
| `published_article_key` | `null` | **`default-source--quality-progress-and-lessons-当前-gate`** |

---

## 4. 正常 approve 对照（id=14）

### 4.1 接口响应

```json
SUCCESS
  review_status: published
  published_article_key: default-source--quality-progress-and-lessons-踩坑记录
```

### 4.2 各表数量变化

| 表 | approve 前 | approve 后 | 变化 | 结论 |
|---|---|---|---|---|
| `articles` | 8 | 9 | **+1** | 正常入库 |
| `article_chunks` | 27 | 36 | **+9** | 正常写入 chunk |
| `article_vector_index` | 8 | 9 | **+1** | 正常索引 |
| `article_chunk_vector_index` | 27 | 36 | **+9** | 正常索引 |

**结论**：正常 approve 行为未被幂等修复影响，新文章正确入库并完成 chunk/vector 写入。

---

## 5. 正常 reject 对照（id=16）

### 5.1 接口响应

```json
SUCCESS
  review_status: rejected
  published_article_key: None
```

### 5.2 各表数量变化

| 表 | reject 前 | reject 后 | 变化 | 结论 |
|---|---|---|---|---|
| `articles` | 9 | 9 | **0** | 未入库 |
| `article_chunks` | 36 | 36 | **0** | 未写入 |

**结论**：正常 reject 行为未被影响，拒绝后不入库。

---

## 6. 汇总结论

### 6.1 核心验证点

| 验证点 | 结果 |
|---|---|
| article_key 已存在时 approve 是否成功 | **是，返回 SUCCESS** |
| 是否仍返回 `article already exists` | **否，已消除** |
| 队列状态是否变成 `published` | **是** |
| `publishedArticleKey` 是否正确返回 | **是** |
| articles 表是否不重复增加 | **是，8→8** |
| article_chunks 是否不重复增加 | **是，27→27** |
| article_vector_index 是否不重复增加 | **是，8→8** |
| article_chunk_vector_index 是否不重复增加 | **是，27→27** |
| `COMPILE_EXECUTION_FAILED` 是否还出现 | **否** |

### 6.2 对照验证点

| 验证点 | 结果 |
|---|---|
| 正常 approve 是否保持原行为 | **是，正常入库 + chunk/vector 写入** |
| 正常 reject 是否保持原行为 | **是，不入库** |

### 6.3 最终队列统计

| review_status | 计数 |
|---|---|
| `needs_human_review` | 21 |
| `published` | 8 |
| `rejected` | 7 |

---

## 7. 是否建议进入提交前质量复核

**是，建议进入。** 幂等 approve 修复已在真实运行时通过验证：

- 核心场景（article_key 已存在 → 幂等成功）行为正确
- 正常 approve / reject 未受误伤
- 各正式表数量变化均符合预期
- 队列状态收口完整

---

## 8. 本轮是否修改代码

**否。本轮未修改任何代码、测试、配置或数据库。**

本轮所有操作均为只读：
- `bash scripts/scan-redline.sh` redline 扫描
- `docker exec ... psql` 数据库只读查询
- `curl` API 调用（approve ×2、reject ×1）
- 未执行任何 `INSERT`/`UPDATE`/`DELETE`（app 侧 API 调用导致的正常数据写入除外）

---

## 附录：验收环境

| 项目 | 值 |
|---|---|
| 应用端口 | `18082` |
| Schema | `lattice` |
| DB | `ai-rag-knowledge` @ `vector_db:5432` |
| Redis | `redis:6379` |
| 文章总数 | 9 |
| 待确认草稿 | 21 |
