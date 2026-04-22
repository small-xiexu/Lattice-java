# ChatClient + Advisor Phase 1 实施清单

## 1. 目标

基于 `docs/LLM调用迁移到ChatClient-Advisor渐进式改造技术方案.md` 与已完成的 `Phase 0.5`，正式进入 Query 主链迁移阶段，在不触碰 compile / governance / SQL schema 的前提下，完成：

- `query-answer / query-rewrite / query-review` 的 typed payload 迁移
- `AnswerOutcome` 统一问答结果语义
- `generationMode / modelExecutionStatus` 明确降级与失败状态
- `LlmInvocationEnvelope`、`PromptCacheWritePolicy`、双层缓存治理落地
- `/admin/ask` 与 API 可明确区分“成功 / 部分答案 / 证据不足 / 无相关知识 / 模型失败已降级”

## 2. 范围

- 文档台账：
  - `docs/plans/ChatClient-Advisor-Phase1实施清单.md`
- Query 主链与缓存：
  - `src/main/java/com/xbk/lattice/query/service/AnswerGenerationService.java`
  - `src/main/java/com/xbk/lattice/query/service/LlmReviewerGateway.java`
  - `src/main/java/com/xbk/lattice/query/graph/QueryGraphState.java`
  - `src/main/java/com/xbk/lattice/query/graph/QueryGraphStateMapper.java`
  - `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionFactory.java`
  - `src/main/java/com/xbk/lattice/query/service/QueryFacadeService.java`
  - `src/main/java/com/xbk/lattice/api/query/QueryResponse.java`
  - `src/main/java/com/xbk/lattice/query/service/QueryCacheStore.java`
- 新执行抽象与模型：
  - `src/main/java/com/xbk/lattice/llm/**`
  - `src/main/java/com/xbk/lattice/observability/**`
  - `src/main/java/com/xbk/lattice/query/domain/**`
  - `src/main/java/com/xbk/lattice/query/service/**`
- 回归测试：
  - `src/test/java/com/xbk/lattice/query/service/QueryGraphOrchestratorTests.java`
  - `src/test/java/com/xbk/lattice/api/query/QueryControllerTests.java`
  - `src/test/java/com/xbk/lattice/compiler/service/LlmGatewayTests.java`
  - 视需要新增 Query typed payload / cache policy / advisor execution 定向测试

## 3. 非目标

- 本轮**不做 SQL 迁移**
  - `compile_jobs / source_sync_runs` 的 `root_trace_id` 持久化与 worker 恢复单独作为后续增强项跟进
- 本轮**不迁 compile / governance**
  - `compile review`、`治理 JSON 调用`、`全文本调用统一底层执行链` 仍留在后续 Phase 2/3/4
- 本轮**不触碰**用户已有脏改文件
  - `src/main/resources/db/migration/V1__baseline_schema.sql`

## 4. 实施清单

- [x] P1-1 完成 Query structured-output Spike 与降级映射
  - 验收：已补齐 `AnswerGenerationServiceTests`，覆盖结构化成功、JSON fail-open、`queryArticleHits == null/only article evidence/llmGateway == null` 等降级映射；OpenAI/Codex 主回归见 `docs/plans/OpenAI配置回归改造清单.md` 中 D9 与 D11。

- [x] P1-2 定义 typed payload / outcome / execution envelope
  - 验收：已落地 `QueryAnswerPayload`、`QueryRewritePayload`、`ReviewerPayload`、`AnswerOutcome`、`GenerationMode`、`ModelExecutionStatus`、`LlmInvocationEnvelope`、`PromptCacheWritePolicy`，并由 `QueryGraphState/QueryResponse` 承载统一语义。

- [x] P1-3 新增统一执行抽象
  - 验收：已补齐 `ChatClientRegistry`、`AdvisorChainFactory`、`LlmInvocationExecutor`，并在 `LlmGateway` 内保留 facade；`LlmGatewayTests` 与 `LlmInvocationExecutorTests` 已覆盖 OpenAI/Codex 动态 ChatClient 执行路径。

- [x] P1-4 迁移 `query-answer / query-rewrite / query-review`
  - 验收：`AnswerGenerationService` 已输出 typed answer payload，`LlmReviewerGateway` 已走 raw facade + `ReviewResultParser`，`QueryGraphDefinitionFactory` 已把 answer/review/rewrite 的 outcome/status 回写到 Graph 状态与最终响应。

- [x] P1-5 落地 `PromptCacheWritePolicy` 与双层缓存治理
  - 验收：Query answer/rewrite/review 与 compile review 已按 payload/outcome 决定 L1 prompt cache 写入；L2 query cache 已改为基于 `AnswerOutcome + answerCacheable` 决策，并在 compile/vector 侧清理双层缓存。

- [x] P1-6 扩展 Query API / `/admin/ask` 状态语义
  - 验收：`QueryResponse` 已补齐 `queryId / reviewStatus / answerOutcome / generationMode / modelExecutionStatus`；`ask.js` 与对应 Controller/Service 测试已覆盖结果态展示。

- [x] P1-7 完成回归验收与断点回写
  - 验收：`mvn -q -s .codex/maven-settings.xml -Dtest=AnswerGenerationServiceTests,LlmReviewerGatewayTests,LlmGatewayTests,QueryControllerTests,QueryGraphOrchestratorTests test` 已覆盖核心回归；真实 OpenAI/Codex 主回归与 `/admin/ask` 验收已记录在 `docs/plans/OpenAI配置回归改造清单.md`。

## 5. Phase 1 验收口径

- `AnswerOutcome` 成为 Query cache、前端状态、日志统计的统一语义源
- “当前证据不足”不再依赖文案 marker 判断
- fallback 路径稳定收敛为 `PARTIAL_ANSWER + generationMode=FALLBACK + modelExecutionStatus=FAILED`
- `query-answer / query-rewrite / query-review` 三条路径都可输出 typed payload 或受控降级结果
- `/admin/ask` 可明确区分状态
- OpenAI/Codex 路径回归通过

## 6. 当前断点

- 当前阶段：Phase 1 已完成
- 已知约束：
  - `Phase 0.5` 已完成，可直接进入 Query 主链迁移
  - 本轮不动 SQL，不把 `rootTraceId` 持久化增强混入主迁移
  - 工作区存在用户未提交改动 `src/main/resources/db/migration/V1__baseline_schema.sql`，本轮不触碰该文件
- 下一步：后续 reviewer / governance / compile 文本调用收口改由 `docs/plans/ChatClient-Advisor-后续阶段实施清单.md` 继续跟进
