# ChatClient + Advisor Phase 0.5 实施清单

## 1. 目标

基于 `docs/LLM调用迁移到ChatClient-Advisor渐进式改造技术方案.md` 的 Phase 0 Spike 结论，补齐进入 Phase 1 前必须完成的最小日志与追踪基础设施，确保当前 legacy 主链在不切换到底层 `ChatClient executor` 的前提下，已经具备：

- `QueryResponse.queryId` 稳定回传
- `pending query` 与 Query Graph 共用同一 `queryId`
- 最小 JSON 结构化日志输出
- `traceId/spanId` 在主链日志中可见
- `query_received / llm_call_started / llm_call_succeeded|failed / query_completed / compile_submitted` 最小事件链

## 2. 范围

- 文档台账：
  - `docs/plans/ChatClient-Advisor-Phase0.5实施清单.md`
- Query 主链：
  - `src/main/java/com/xbk/lattice/query/service/QueryFacadeService.java`
  - `src/main/java/com/xbk/lattice/query/service/QueryGraphOrchestrator.java`
  - `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionFactory.java`
  - `src/main/java/com/xbk/lattice/query/service/PendingQueryService.java`
- LLM 调用与日志：
  - `src/main/java/com/xbk/lattice/compiler/service/LlmGateway.java`
  - `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java`
  - `src/main/java/com/xbk/lattice/observability/**`
  - `src/main/resources/logback-spring.xml`
  - `src/main/resources/application.yml`
  - `pom.xml`
- 回归测试：
  - `src/test/java/com/xbk/lattice/query/service/QueryGraphOrchestratorTests.java`
  - `src/test/java/com/xbk/lattice/api/query/QueryControllerTests.java`
  - `src/test/java/com/xbk/lattice/api/admin/AdminCompileJobControllerTests.java`
  - `src/test/java/com/xbk/lattice/compiler/service/LlmGatewayTests.java`

## 3. 实施清单

- [x] P05-1 修复 `queryId` 回传与 pending query 语义
  - 验收：已统一 Query Graph 成功响应、无命中响应、cache hit 响应与 pending query 记录的 `queryId` 语义；L2 cache 仅缓存不带 `queryId` 的响应副本，返回给当前请求时会重新绑定当前 `queryId`。
  - 验证：`mvn -q -s .codex/maven-settings.xml -Dtest=QueryGraphOrchestratorTests,QueryControllerTests test` 已通过，覆盖 API 返回 `queryId` 与缓存命中重绑 `queryId` 两条路径。

- [x] P05-2 引入 JSON 结构化日志与 tracing 基础设施
  - 验收：已接入 `logstash-logback-encoder`、Micrometer Tracing bridge、JSON console appender 与最小 `management.tracing` 配置；`StructuredEventLogger` 已按 `Tracer + MDC` 双路径补齐 `eventName / traceId / spanId / rootTraceId`，`bootstrap` 回退场景的 `scopeId` 也已保真，Query Graph 并行节点可继承入口 trace。
  - 验证：`mvn -q -s .codex/maven-settings.xml -Dtest=QueryGraphOrchestratorTests,QueryControllerTests,AdminCompileJobControllerTests,LlmGatewayTests test` 已通过。

- [x] P05-3 输出最小事件链
  - 验收：已落地 `query_received / llm_call_started / llm_call_succeeded|failed / query_completed / compile_submitted` 事件；Query 主链与 compile 提交链均输出 JSON 结构化日志，并带上 `queryId|compileJobId` 与 `traceId/spanId`。
  - 验证：`QueryControllerTests.shouldEmitStructuredQueryLifecycleEvents` 已校验同一 `queryId` 下的完整 query 事件链；`AdminCompileJobControllerTests.shouldEmitStructuredCompileSubmittedEvent` 已校验 `compile_submitted` 事件。

- [x] P05-4 补齐 Phase 0.5 回归测试
  - 验收：已补齐 `queryId` 回传、缓存命中重绑、结构化 query 事件链与 `compile_submitted` 事件的定向回归测试，并覆盖 `bootstrap` 回退下的 `scopeId` 透传行为。
  - 验证：`mvn -q -s .codex/maven-settings.xml -Dtest=QueryGraphOrchestratorTests,QueryControllerTests,AdminCompileJobControllerTests,LlmGatewayTests test` 已通过。

- [x] P05-5 回写验收结果与下一步断点
  - 验收：Phase 0.5 最小验收口径已通过，可进入 Phase 1；当前 legacy 主链已经具备 `queryId` 闭环、最小 JSON 事件链与 `traceId/spanId` 观测能力。
  - 下一步：新建 `Phase 1` 实施清单，开始 `AnswerOutcome / typed payload / ChatClientRegistry / AdvisorChainFactory / PromptCacheWritePolicy` 主链迁移；`compile worker` 的 `rootTraceId` 持久化与恢复可作为后续增强项单列跟进。

## 4. 当前断点

- 当前阶段：本轮清单已完成
- 已知约束：工作区存在用户未提交改动 `src/main/resources/db/migration/V1__baseline_schema.sql`，本轮不触碰该文件
- 下一步：新建 `Phase 1` 实施清单，并按新清单继续主链迁移
