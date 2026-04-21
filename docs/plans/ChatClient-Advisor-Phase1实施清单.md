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

- [ ] P1-1 完成 Query structured-output Spike 与降级映射
  - 进行中：已回读运行/验收文档与技术方案，确认 `rootTraceId` 落库不纳入本轮；下一步用 OpenAI/Codex 路径验证 `answerMarkdown` JSON 转义、structured output fail-open、`non-LLM` 路径的 `AnswerOutcome` 映射。

- [ ] P1-2 定义 typed payload / outcome / execution envelope
  - 待开始：计划新增 `QueryAnswerPayload`、`QueryReviewPayload`、`QueryRewritePayload`、`AnswerOutcome`、`generationMode`、`modelExecutionStatus`、`LlmInvocationEnvelope` 等结构，统一 Query 主链输出语义。

- [ ] P1-3 新增统一执行抽象
  - 待开始：计划补齐 `ChatClientRegistry`、`AdvisorChainFactory`、`LlmInvocationExecutor` 的最小实现，并保留 `LlmGateway` 作为上层兼容入口，不直接扩散底层依赖到业务层。

- [ ] P1-4 迁移 `query-answer / query-rewrite / query-review`
  - 待开始：计划把 `AnswerGenerationService`、`LlmReviewerGateway`、Query Graph answer/rewrite/review 节点切到 typed payload 路径，并保留 legacy 文本路径作为受控降级。

- [ ] P1-5 落地 `PromptCacheWritePolicy` 与双层缓存治理
  - 待开始：计划移除 `NON_CACHEABLE_ANSWER_MARKERS` 文案判断，改为基于 `AnswerOutcome + answerCacheable + PromptCacheWritePolicy` 决定 L1/L2 写入、抑制与清理策略。

- [ ] P1-6 扩展 Query API / `/admin/ask` 状态语义
  - 待开始：计划在 `QueryResponse` 或其等价返回结构中补齐 `answerOutcome / generationMode / modelExecutionStatus`，让前端和 API 能明确区分“成功 / 部分答案 / 证据不足 / 无相关知识 / 模型失败已降级”。

- [ ] P1-7 完成回归验收与断点回写
  - 待开始：计划完成 OpenAI/Codex 路径回归、缓存语义回归、`/admin/ask` 状态展示验收，并按清单逐项回写完成/阻塞依据。

## 5. Phase 1 验收口径

- `AnswerOutcome` 成为 Query cache、前端状态、日志统计的统一语义源
- “当前证据不足”不再依赖文案 marker 判断
- fallback 路径稳定收敛为 `PARTIAL_ANSWER + generationMode=FALLBACK + modelExecutionStatus=FAILED`
- `query-answer / query-rewrite / query-review` 三条路径都可输出 typed payload 或受控降级结果
- `/admin/ask` 可明确区分状态
- OpenAI/Codex 路径回归通过

## 6. 当前断点

- 当前阶段：P1-1 进行中
- 已知约束：
  - `Phase 0.5` 已完成，可直接进入 Query 主链迁移
  - 本轮不动 SQL，不把 `rootTraceId` 持久化增强混入主迁移
  - 工作区存在用户未提交改动 `src/main/resources/db/migration/V1__baseline_schema.sql`，本轮不触碰该文件
- 下一步：先做 Query structured-output Spike 与降级映射，再决定 typed payload 最终落点和 API 返回形态
