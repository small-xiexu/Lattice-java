# Compile Review Persist Gate Fix Result Report

## 1. 修改文件和方法

- `src/main/java/com/xbk/lattice/compiler/graph/node/PersistArticlesNode.java`
  - 修改 `execute(...)`。
  - 新增同文件私有方法 `retainPassedArticles(...)`。
  - 新增同文件私有方法 `isPassedArticle(...)`。
- `special_cases_report.md`
  - 仅由 redline 扫描命令更新。

## 2. 是否只修改 PersistArticlesNode

是，生产代码只修改 `PersistArticlesNode.java`。

本轮另新增本结果报告；`special_cases_report.md` 是 redline 命令输出文件。

## 3. 新的 Persist Gate 语义

- passed article 是否仍正常入库：是。
  - `persist_articles` 仍读取 `acceptedArticlesRef`。
  - 只有 `review_status=passed` 的 `ArticleReviewEnvelope` 会进入 `articleAtomicWriteService.persistArticlesAtomic(...)`。
- needs_human_review 是否不再进入正式 persist：是。
  - `PersistArticlesNode.execute(...)` 不再在 `allowPersistNeedsHumanReview=true` 时合并 `needsHumanReviewArticlesRef`。
  - `needs_human_review` 文章保留在 working set / 后台复核域，不进入正式 query-facing 的 `articles / article_chunks / article_vector_index / article_chunk_vector_index` 写入入口。
- `allowPersistNeedsHumanReview` 当前不再作为正式落库放行开关使用。

## 4. 禁止范围确认

- 是否修改 reviewer / fixer：否。
- 是否修改 Query visibility filter：否。
- 是否开启 LLM reviewer：否。
- 是否修改测试：否。
- 是否修改 query / answer / retrieval / rerank / citation：否。
- 是否修改 fact card 编译或使用策略：否。
- 是否修改 LLM 配置 / `lattice-llm.yml`：否。
- 是否修改数据库 schema / migration：否。
- 是否清库 / 重建 / 导入资料 / 跑 baseline：否。
- 是否新增业务特判：否。

## 5. Redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：通过，退出码 0。
- BLOCKER：0。
- REVIEW：1351。
- ALLOWLIST：166。

说明：ALLOWLIST 较前序报告多 1 个通用工程状态字符串候选，来自本轮 `passed` 审查状态判断；不包含业务域、文档名、术语、问题文本或答案片段特判。

## 6. Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：通过。
- Maven 汇总：`Tests run: 811, Failures: 0, Errors: 0, Skipped: 0`

## 7. 测试覆盖说明

现有完整测试通过，且编译主链中 passed article 正常入库路径未被破坏。

本轮未新增测试，因为用户明确禁止修改 `src/test/java/**`。当前测试集中未发现专门覆盖 `allowPersistNeedsHumanReview=true` 时 `needs_human_review` 不再进入正式 persist 的 `PersistArticlesNode` 回归用例；建议后续如用户确认，可补一个极窄单元测试或图节点集成测试。

## 8. 下一步建议

只建议一个最小动作：在用户确认允许改测试后，为 `PersistArticlesNode` 增加一个专门回归测试，断言 `allowPersistNeedsHumanReview=true` 时也只持久化 `review_status=passed` 文章。
