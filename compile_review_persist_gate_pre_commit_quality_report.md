# compile review persist gate 提交前质量复核报告

复核时间：2026-05-17
复核角色：agentD（验证/测试）
复核类型：提交前质量复核（只验证，不改代码）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1852
- ALLOWLIST：239

REVIEW / ALLOWLIST 为既有人工复核候选，均为既存命中，无新增 BLOCKER。

## 2. Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：BUILD SUCCESS
- Tests run: 812, Failures: 0, Errors: 0, Skipped: 0

与 persist gate 测试补强后的预期基线 812/0/0 一致（原基线 811 + 新增 1 个 `PersistArticlesNodeTests`）。

## 3. 本轮工作区变更清单

已修改（staged）：

| 文件 | 说明 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/graph/node/PersistArticlesNode.java` | persist gate 修复：不再合并 `needsHumanReviewArticlesRef`，新增 `retainPassedArticles()` / `isPassedArticle()` |
| `docs/quality-progress-and-lessons.md` | 质量台账更新 |
| `special_cases_report.md` | redline 扫描自动更新 |

已修改（uncommitted，来自前序轮次）：

| 文件 | 说明 |
|---|---|
| `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java` | compile review observability（前序轮次） |
| `src/main/java/com/xbk/lattice/api/admin/AdminCompileController.java` | compile review observability（前序轮次） |
| `src/main/java/com/xbk/lattice/api/admin/AdminCompileJobResponse.java` | compile review observability（前序轮次） |
| `src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskItemResponse.java` | compile review observability（前序轮次） |

未跟踪文件：

| 文件 | 说明 |
|---|---|
| `src/test/java/com/xbk/lattice/compiler/graph/node/PersistArticlesNodeTests.java` | 新增 persist gate 单元测试 |
| `compile_review_persist_visibility_governance_analysis_report.md` | 治理分析报告 |
| `compile_review_persist_gate_fix_result_report.md` | fix 结果报告 |
| `compile_review_persist_gate_runtime_verification_report.md` | 运行时验证报告 |
| `compile_review_persist_gate_test_result_report.md` | 测试补强结果报告 |
| `compile_review_persist_gate_quality_progress_update_report.md` | 质量台账更新报告 |
| `compile_review_query_visibility_filter_analysis_report.md` | query visibility 分析报告 |
| `compile_review_report_cleanup_plan.md` | 报告清理计划 |
| `compile_review_observability_*`（3 个文件，前序轮次） | observability 相关报告 |
| `src/main/java/.../AdminCompileReviewSummaryService.java`（前序轮次） | observability 新增服务 |
| `src/main/java/.../AdminCompileReviewSummaryResponse.java`（前序轮次） | observability 新增 DTO |

## 4. 变更范围判断：是否只属于 compile review persist gate + 测试 + 台账/报告

**是。**

生产代码变更仅 `PersistArticlesNode.java` 一个文件，且变更内容只涉及 persist gate 语义修正：

- 移除 `needsHumanReviewArticlesRef` 合并逻辑
- 新增 `retainPassedArticles()` 过滤仅 `review_status=passed` 的文章
- 新增 `isPassedArticle()` 判断方法

测试变更仅新增 `PersistArticlesNodeTests.java`，覆盖混合 `passed + needs_human_review` 输入的 persist 行为。

其余未跟踪文件均为 persist gate 相关报告、前序 observability 轮次遗留文件。

## 5. 是否修改 Query visibility filter：否

本轮仅修改 `PersistArticlesNode`（编译图 persist 节点）。未修改任何 SQL、mapper、检索、重排或可见性过滤逻辑。

## 6. 是否开启 LLM reviewer：否

`compile_review_settings` 表为空，review route 仍为 `rule-based`。未修改 `LlmProperties`、`lattice-llm.yml` 或任何模型配置。

## 7. 是否修改 source/fact_card/query/answer/retrieval/rerank/citation：否

变更范围严格限定在 `PersistArticlesNode.java`（编译图节点），不涉及 source、fact card、query、answer、retrieval、rerank 或 citation 相关代码。

## 8. 是否修改 eval/baseline 题集：否

未修改任何 eval、baseline、测试数据集文件。

## 9. 是否新增业务特判：否

`isPassedArticle()` 方法仅使用 `"passed".equalsIgnoreCase(reviewStatus)` 进行状态判断。`"passed"` 是 `ArticleReviewEnvelope` / `articles.review_status` 的标准枚举值，与 `RuleBasedArticleReviewer` 和 `ReviewDecisionPolicy` 保持一致，不涉及任何业务域、文档名、术语、问题文本或答案片段。

## 10. 是否建议提交

**可以提交。**

全部 9 项检查通过：

| 检查项 | 结果 |
|---|---|
| redline BLOCKER=0 | 通过（0/1852/239） |
| mvn test 812/0/0/0 | 通过 |
| 变更范围只含 persist gate + 测试 + 台账/报告 | 通过 |
| 未修改 Query visibility filter | 通过 |
| 未开启 LLM reviewer | 通过 |
| 未修改 source/fact_card/query/answer/retrieval/rerank/citation | 通过 |
| 未修改 eval/baseline 题集 | 通过 |
| 未新增业务特判 | 通过 |

阻塞原因：无。
