# compile review 默认 LLM 模式修复结果报告

## 1. 修改了哪些文件

- `src/main/java/com/xbk/lattice/compiler/service/CompileExecutionRequest.java`
  - 新增 `normalizeNewJobReviewMode`，用于新建 compile job 场景：`reviewMode` 为空时默认 `LLM`。
  - 保留底层 `normalizeReviewMode` 的空值默认 `RULE_BASED`，避免无上下文工具构造路径误开 LLM。
- `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java`
  - 新建 job 路径改用 `normalizeNewJobReviewMode`，最终值写入 `compile_jobs.review_mode`。
  - retry / 执行路径继续沿用已落库的 `reviewMode`，不重新读取新建默认值。
- `src/main/resources/db/schema.sql`
  - `compile_jobs.review_mode` 默认值改为 `LLM`。
  - 补充 `ALTER COLUMN review_mode SET DEFAULT 'LLM'`，保证已存在列的默认值也被收正。
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.xml`
  - `ensureReviewModeColumn` 的列默认值改为 `LLM`。
  - 补充设置既有列默认值为 `LLM`。
- `src/test/java/com/xbk/lattice/api/admin/AdminCompileJobControllerTests.java`
  - 补充“不传 `reviewMode` 时新 job 固化为 `LLM`”断言。
  - 原本依赖 rule-based 通过的 smoke 用例显式传入 `RULE_BASED`。
- `src/test/java/com/xbk/lattice/compiler/service/ArticleReviewerGatewayTests.java`
  - 补充 LLM job 在 reviewer 不可用时 fail-closed 的断言。
- `src/test/java/com/xbk/lattice/testsupport/ApprovedArticleReviewerTestConfiguration.java`
  - 新增测试专用 approved reviewer，用于非 reviewer 目标的 API / query / management 集成测试，让这些测试在默认 LLM 下仍验证原本业务目标。
- 下列测试类引入上述测试配置，避免默认 LLM 后把非 reviewer 测试变成 LLM 可用性测试：
  - `src/test/java/com/xbk/lattice/api/compiler/CompileControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/query/QueryControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/query/PendingQueryControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/admin/AdminGovernanceApiIntegrationTests.java`
  - `src/test/java/com/xbk/lattice/api/admin/AdminManagementControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/admin/AdminOverviewControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/admin/AdminSourceControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/admin/AdminUploadControllerTests.java`
- `special_cases_report.md`
  - 由 redline 扫描刷新。

说明：当前工作区还包含上一轮 per-job reviewMode 的未提交实现改动；本报告只描述本轮“新 job 默认 LLM”小修及其直接测试适配。

## 2. 新 job 默认 reviewMode 是否已改为 LLM

是。

- 新建 compile job 时，`request.reviewMode` 为 `null` / 空白会规范化为 `LLM`。
- 最终采用值会写入 `compile_jobs.review_mode`。
- `lattice.llm.review-enabled` / `LATTICE_LLM_REVIEW_ENABLED` 不再作为新 job 是否使用 LLM reviewer 的主决策。

## 3. schema 默认值是否为 LLM

是。

- `schema.sql` 中 `compile_jobs.review_mode` 默认值为 `LLM`。
- MyBatis 初始化 SQL 中也会把既有列默认值设置为 `LLM`。

## 4. 显式 RULE_BASED 是否仍可用

是。

- 请求显式传 `RULE_BASED` 时仍固化为 `RULE_BASED`。
- `RULE_BASED` job 不受全局 `review-enabled=true` 影响，仍走 rule-based。

## 5. retry 是否沿用已落库 reviewMode

是。

- retry / 执行路径读取 `compile_jobs.review_mode`。
- 不会因为默认值改为 `LLM` 而覆盖原 job 已落库的审查模式。

## 6. 是否修改 prompt / StateGraph / persist gate / query visibility / review_status

否。

- 本轮没有修改 Writer / Reviewer / Fixer prompt。
- 本轮没有修改 StateGraph 闭环。
- 本轮没有修改 `PersistArticlesNode`。
- 本轮没有修改 query visibility filter。
- 本轮没有修改 `review_status` enum 或 DB schema 枚举语义。

## 7. 关键行为验证

- 新 job 不传 `reviewMode`：已覆盖，响应与数据库均为 `LLM`。
- 新 job 显式传 `RULE_BASED`：已覆盖，仍为 `RULE_BASED`。
- LLM job 不受全局 `review-enabled=false` 阻挡：已有 `ArticleReviewerGatewayTests` 覆盖。
- RULE_BASED job 不受全局 `review-enabled=true` 影响：已有 `ArticleReviewerGatewayTests` 覆盖。
- LLM 异常 / parse failed / reviewer unavailable：保持 fail-closed，返回非 pass。
- Fixer 后仍重新进入 Reviewer：本轮未改闭环；reviewMode 通过 jobId 落库值解析，闭环中每轮 reviewer 都沿用该 job 的模式。
- passed 才入库、needs_human_review 不入库：persist gate 未改，相关测试随全量测试通过。

## 8. redline 结果

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：通过
- BLOCKER：0
- REVIEW：1858
- ALLOWLIST：242

## 9. mvn test 结果

- 定向测试：通过，`Tests run: 78, Failures: 0, Errors: 0, Skipped: 0`
- 全量测试：通过，`Tests run: 825, Failures: 0, Errors: 0, Skipped: 0`
- 全量命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`

## 10. 是否需要 agentD 做 runtime 验证

需要。

建议 agentD 后续做后台真实 canary 验证：

- admin compile job / upload compile 不传 `reviewMode` 时，API、DB、后台详情均显示 `LLM`。
- 真实 LLM reviewer approved 时可以进入 persist。
- 真实 LLM reviewer 异常、超时或 JSON parse failed 时仍 fail-closed。
- 显式 `RULE_BASED` 仍可作为低成本 smoke 模式。
