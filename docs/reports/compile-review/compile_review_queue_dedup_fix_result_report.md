# compile_review_queue_dedup_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/resources/db/schema.sql`
  - 调整 compile review queue 的最终唯一约束定义
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
  - 调整 `ensureTable`
  - 调整 `upsertPending`
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`
  - 新增去重与历史保留回归测试
- `scripts/deduplicate-review-queue.sql`
  - 删除独立去重脚本，避免出现第二套 SQL 入口

## 2. 最终唯一性约束是什么

最终采用的是：

- **`article_key` 上的 partial unique index，仅作用于 `review_status = 'needs_human_review'`**

具体形式：

```sql
CREATE UNIQUE INDEX uk_compile_article_review_queue_article_pending
    ON compile_article_review_queue (article_key)
    WHERE review_status = 'needs_human_review';
```

这样做的原因是：

- 对 `needs_human_review` 实现跨 job 去重
- `published / rejected` 不受该唯一约束影响，因此仍可保留历史记录

## 3. 命中已有待确认草稿时现在如何处理

现在 `upsertPending` 的语义是：

- 如果同一 `article_key` 已存在一条 `needs_human_review` 草稿：
  - 不再新增一条重复记录
  - 而是 `ON CONFLICT ... DO UPDATE`
  - 用新的 `job_id / source_id / source_code / concept_id / title / content / metadata / review_issues / fixAttemptCount / maxFixRounds / updated_at` 覆盖旧草稿
- 如果同一 `article_key` 只有 `published / rejected` 历史记录：
  - 新的 `needs_human_review` 草稿仍可正常入队

也就是说：

- **同一 `article_key + needs_human_review` 始终只保留最新一条**
- 历史决策记录不会被静默丢掉

## 4. published / rejected 是否仍保留历史

是。

本轮没有给 `published` 或 `rejected` 加唯一约束，因此：

- 已 `published` 的旧记录保留
- 已 `rejected` 的旧记录保留
- 后续同一 `article_key` 再次进入 `needs_human_review` 时，仍可形成新的待确认草稿
- 新草稿后续再次 `published` / `rejected`，历史记录也能继续累积

## 5. 是否修改 schema

是。

修改内容：

1. 移除旧的 `job_id + concept_id` 唯一性依赖
   - 通过 `DROP INDEX IF EXISTS uk_compile_article_review_queue_job_concept`
2. 新增 pending 去重唯一索引
   - `uk_compile_article_review_queue_article_pending`
3. 保留 `(job_id, concept_id)` 作为普通索引
   - 继续支持按 job 查询与聚合性能

## 6. 是否做了旧数据迁移处理

否。

按当前项目口径：

- 只保留一份完整的 `schema.sql`
- 不再维护独立的旧数据迁移 / dedup 脚本
- 不在 `schema.sql` 或 `ensureTable` 中保留面向历史脏数据的清理逻辑

本轮实际采取的是：

- 删除独立 `scripts/deduplicate-review-queue.sql`
- 删除 `schema.sql` / `ensureTable` 中的迁移式去重 SQL
- 直接使用 `bash scripts/reset-lattice-schema.sh` 重建 `lattice` schema
- 然后按最新 `schema.sql` 重新初始化

也就是说，这轮不是“迁移旧数据”，而是按新项目口径用唯一完整脚本重建当前 schema。

## 7. redline BLOCKER 是否仍为 0

- 已运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`，`REVIEW=1911`，`ALLOWLIST=245`

## 8. 测试是否通过

- 定向测试通过：
  - `CompileArticleReviewQueueJdbcRepositoryTests`
- 结果：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`
- 全量 `mvn test`：`Tests run: 866, Failures: 0, Errors: 0, Skipped: 0`

## 9. 下一轮是否建议交给 agentD 做 runtime 复验

建议。

下一轮建议 agentD 做 runtime 复验，重点确认：

- 同一 `article_key` 跨不同 compile job 不再堆积多条 `needs_human_review`
- 新 compile 命中已有 pending 草稿时，页面上只看到一条最新草稿
- `published / rejected` 历史仍可在数据库中保留
- approve / reject / query 语义不受影响
