# compile review per-job reviewMode 修复结果报告

## 1. 修改了哪些文件

### 生产代码

- `src/main/java/com/xbk/lattice/api/admin/AdminCompileJobRequest.java`
  - 新增 `reviewMode` 请求字段，兼容不传场景。
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileJobResponse.java`
  - 新增 `reviewMode` 响应字段。
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewSummaryResponse.java`
  - 新增 `requestedReviewMode` 字段。
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileController.java`
  - admin compile job / upload compile 透传 `reviewMode`。
  - job detail 返回 `reviewMode`。
- `src/main/java/com/xbk/lattice/compiler/service/CompileExecutionRequest.java`
  - 新增 `RULE_BASED` / `LLM` 审查模式常量与规范化方法。
  - `null` / 空值 / 非法值统一规范化为 `RULE_BASED`。
- `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java`
  - submit 时固化 job 级 `reviewMode`。
  - 执行 job 时从 `CompileJobRecord` 传入 `CompileExecutionRequest`。
  - structured event 输出 `reviewMode`。
- `src/main/java/com/xbk/lattice/compiler/service/ArticleReviewerGateway.java`
  - 根据 job-scoped `reviewMode` 决定 rule-based 或 LLM reviewer。
  - `reviewMode=LLM` 绕过全局 `review-enabled=false` 的挡板，直接调用 LLM reviewer。
  - `reviewMode=RULE_BASED` 不受全局 `review-enabled=true` 影响。
  - LLM 异常 / 超时 / 不可用仍 fail-closed，返回非 pass。
- `src/main/java/com/xbk/lattice/compiler/agent/ReviewTask.java`
  - 新增 `reviewMode` 字段，保留旧构造器兼容。
- `src/main/java/com/xbk/lattice/compiler/agent/DefaultReviewerAgent.java`
  - Reviewer 每轮带 scope/reviewMode 调用 `ArticleReviewerGateway`。
- `src/main/java/com/xbk/lattice/compiler/agent/AgentModelRouter.java`
  - 新增按 job `reviewMode` 解析 reviewer route 的入口。
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphState.java`
  - 新增 `reviewMode`。
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphStateKeys.java`
  - 新增 `reviewMode` key。
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphStateMapper.java`
  - GraphState 与 Map 之间读写 `reviewMode`。
- `src/main/java/com/xbk/lattice/compiler/graph/node/InitializeJobNode.java`
  - 初始化时从 job scope 解析并固化 `reviewMode` 到 GraphState。
  - 初始化 `reviewRoute` 时按 job `reviewMode` 展示实际 route。
- `src/main/java/com/xbk/lattice/compiler/graph/GraphStepLogger.java`
  - step summary/input/output 写入 `reviewMode`。
- `src/main/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryService.java`
  - review summary 返回 requested `reviewMode` 与 actual `reviewRoute`。
  - RULE_BASED 展示为“规则审查（不是 LLM 内容审查）”。
- `src/main/java/com/xbk/lattice/infra/persistence/CompileJobRecord.java`
  - 新增 `reviewMode` 字段，默认规范化为 `RULE_BASED`。
- `src/main/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepository.java`
  - 支持 `review_mode` 列存在性保障。
  - 新增按 jobId 读取 `reviewMode`。
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.java`
  - 新增 `ensureReviewModeColumn` 与 `findReviewModeByJobId`。
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.xml`
  - `compile_jobs` 读写 `review_mode`。
  - 新增兼容旧库的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS review_mode`。
- `src/main/resources/db/schema.sql`
  - `compile_jobs` 新增 `review_mode VARCHAR(32) NOT NULL DEFAULT 'RULE_BASED'`。

### 测试代码

- `src/test/java/com/xbk/lattice/compiler/service/ArticleReviewerGatewayTests.java`
  - 覆盖 job LLM 不受全局 disabled 影响。
  - 覆盖 job RULE_BASED 不受全局 enabled 影响。
  - 覆盖 LLM exception / parse failed fail-closed。
- `src/test/java/com/xbk/lattice/compiler/service/CompilerAgentAdaptersTests.java`
  - 覆盖 per-job reviewer route。
- `src/test/java/com/xbk/lattice/compiler/graph/node/ReviewArticlesNodeReviewModeTests.java`
  - 覆盖 draft review 与 fixer 后再次 review 都保留同一 job scope。
- `src/test/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryServiceTests.java`
  - 覆盖 API summary 返回 requested `reviewMode` 与 actual `reviewRoute`。
- `src/test/java/com/xbk/lattice/compiler/graph/GraphStepLoggerTests.java`
  - 覆盖 step log 输出 `reviewMode`。
- `src/test/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepositoryTests.java`
  - 覆盖 `review_mode` DDL、默认 RULE_BASED、retry 保持原 job `reviewMode`。
- `src/test/java/com/xbk/lattice/api/admin/AdminCompileJobControllerTests.java`
  - 覆盖 admin job detail / summary 返回 `reviewMode`。
  - 覆盖 structured event 包含 `reviewMode`。

### 运行产物

- `special_cases_report.md`
  - redline 扫描刷新。

## 2. 是否实现 compile_jobs.review_mode

是。

- 新建表定义包含 `review_mode VARCHAR(32) NOT NULL DEFAULT 'RULE_BASED'`。
- mapper resultMap / column list / upsert 均包含 `review_mode`。
- 旧库通过 `ALTER TABLE compile_jobs ADD COLUMN IF NOT EXISTS review_mode ...` 兼容，不需要清库。

## 3. 默认 reviewMode 是否为 RULE_BASED

是。

- API 不传、传空值或非法值时，`CompileExecutionRequest.normalizeReviewMode` 统一返回 `RULE_BASED`。
- `CompileJobRecord` 构造时也会再次规范化，避免非法值落库。

## 4. LLM job 是否不受全局 review-enabled=false 影响

是。

- job `reviewMode=LLM` 时，`ArticleReviewerGateway` 按 job mode 进入 LLM reviewer 调用路径。
- `AgentModelRouter.routeForReviewerAgent(scope, scene, reviewMode)` 在 LLM mode 下不再受全局 `review-enabled=false` 挡板影响。
- 已由 `ArticleReviewerGatewayTests.shouldUseLlmReviewerForLlmJobWhenGlobalReviewDisabled` 覆盖。

## 5. RULE_BASED job 是否不受全局 review-enabled=true 影响

是。

- job `reviewMode=RULE_BASED` 时，`ArticleReviewerGateway` 直接走 `RuleBasedArticleReviewer`。
- 不会因为全局 `review-enabled=true` 调用 LLM reviewer。
- 已由 `ArticleReviewerGatewayTests.shouldUseRuleBasedReviewerForRuleBasedJobWhenGlobalReviewEnabled` 覆盖。

## 6. reviewMode=LLM 是否会作用于闭环中的每一轮 Reviewer

是。

- `ReviewArticlesNode` 每轮 reviewer 调用均保留 `jobId` scope。
- `DefaultReviewerAgent` 在有 scope 时调用 `ArticleReviewerGateway`，由 gateway 通过 `compile_jobs.review_mode` 解析当前 job 的 reviewer 类型。
- Fixer 后回到 `review_articles` 时仍使用同一个 job scope，因此仍沿用同一 job `reviewMode`。
- 已由 `ReviewArticlesNodeReviewModeTests` 覆盖 draft review 与 fixer 后重新 review 都携带同一 job scope。

## 7. Fixer 后是否仍回 Reviewer

是。

- 本轮未修改 StateGraph / review loop / `ReviewArticlesNode` 主流程。
- 现有 `fix_review_issues -> review_articles` 回边保持不变。
- 测试覆盖 fixer 后重新进入 Reviewer 的 scope 保持，不存在 Fixer 直接置 passed 的改动。

## 8. 是否保持 fail-closed

是。

- `reviewMode=LLM` 下：
  - LLM 调用异常返回 `ReviewResult.timeoutFallback()`，非 pass。
  - LLM 返回不可解析 JSON 时由 `ReviewResultParser` 返回 `PARSE_FAILED`，非 pass。
  - LLM gateway 不可用也返回非 pass。
- 非 pass 后仍走现有 review partition：可修复则进入 fixer，多轮仍不通过则进入 needs_human_review；persist gate 只允许最终 passed 入库。

## 9. 是否修改 prompt / persist gate / query visibility filter / review_status enum

否。

- 未修改 prompt 模板。
- 未修改 `PersistArticlesNode` / persist gate。
- 未修改 query visibility mapper/filter。
- 未修改 review_status enum 或 DB schema enum。

## 10. redline BLOCKER 是否为 0

是。

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：
  - `BLOCKER=0`
  - `REVIEW=1858`
  - `ALLOWLIST=242`

## 11. mvn test 是否通过

是。

- 定向测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=ArticleReviewerGatewayTests,CompilerAgentAdaptersTests,ReviewArticlesNodeReviewModeTests,AdminCompileReviewSummaryServiceTests,GraphStepLoggerTests,CompileJobJdbcRepositoryTests,AdminCompileJobControllerTests,PersistArticlesNodeTests,StateGraphCompileOrchestratorTests test`
  - 结果：`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`
- 全量测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`Tests run: 823, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`

## 12. 是否需要后续 agentD 做 runtime canary 验证

是。

建议后续 agentD 做一次后台 runtime canary：

- 提交默认 compile job，确认 `reviewMode=RULE_BASED`、`reviewRoute=rule-based`、review summary 文案为“规则审查（不是 LLM 内容审查）”。
- 提交 `reviewMode=LLM` compile job，在真实模型路由下确认每轮 Reviewer 使用 LLM route。
- 构造 LLM reviewer 非 pass 场景，确认非 passed 不入库，最终进入 fix 或 needs_human_review。

## 结论

通过。

本轮已实现 compile review per-job `reviewMode`，默认 RULE_BASED，LLM job 可单独启用 LLM reviewer，RULE_BASED job 不受全局开关误影响，LLM 异常/解析失败保持 fail-closed。未修改 prompt、persist gate、query visibility filter、review_status enum，也未清库、未跑 query baseline、未提交代码。
