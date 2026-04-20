# Claude 三次复审提示词

请你以“**架构评审 + 事实核查**”模式，对下面这份技术方案做**第三轮复审**。  
这次不是泛泛点评，而是要像前两轮一样，**逐条核对文档陈述是否与当前代码一致、是否仍有关键遗漏、是否存在 phase/验收标准/技术路径不闭环的问题**。

## 1. 复审目标

请重点判断这份文档现在是否已经从：

- “可以进入 Spike（Phase 0），但不能直接进入 Phase 1”

进一步收敛到：

- “可以继续进入 Phase 0 Spike，且 Phase 1 的前置条件是否已经被完整定义清楚”

如果你认为仍不能进入 Phase 1，请明确指出**阻断点是事实错误、设计缺口，还是验收标准不成立**。

## 2. 重点审查范围

请优先审查以下文件：

1. 方案文档  
   `/Users/sxie/xbk/Lattice-java/docs/LLM调用迁移到ChatClient-Advisor渐进式改造技术方案.md`
2. 进度台账  
   `/Users/sxie/xbk/Lattice-java/docs/plans/OpenAI配置回归改造清单.md`

请结合以下代码进行事实核对，不要只看文档：

1. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/LlmGateway.java`
2. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/llm/service/LlmClientFactory.java`
3. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionFactory.java`
4. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/AnswerGenerationService.java`
5. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/LlmReviewerGateway.java`
6. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/api/query/QueryResponse.java`
7. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/CompileGraphLifecycleListener.java`
8. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/GraphStepLogger.java`
9. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/source/infra/SourceSyncRunJdbcRepository.java`
10. `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/source/service/SourceUploadService.java`
11. `/Users/sxie/xbk/Lattice-java/pom.xml`

## 3. 本轮必须重点核对的点

请不要平均用力，优先看下面这些前两轮曾经出现过的问题是否已真正吸收：

1. `L1/L2` 双层缓存治理现在是否已经闭环。
   - 尤其核对文档是否已经明确：
   - 为什么 `LlmGateway.invoke()` 看不到 outcome
   - 为什么这轮选择“路径 B”
   - `LlmInvocationEnvelope / PromptCacheWritePolicy` 是否足以支撑 L1 write-suppression
   - compile 成功后 L1/L2 的失效策略是否已说清

2. reviewer 路径是否已纳入 L1 prompt cache 治理。
   - 请核对 `LlmReviewerGateway.review()` 仍然如何进入 `LlmGateway`
   - 文档是否已把 `query-review` 的 `SKIP_WRITE`、Phase 2 reviewer payload 迁移与 L1 写入策略连接起来

3. fallback 路径的语义是否已闭环。
   - 请核对 `AnswerGenerationService` 的 fallback 代码路径
   - 文档把它定义成 `PARTIAL_ANSWER + generationMode=FALLBACK + modelExecutionStatus=FAILED` 是否自洽
   - payload、phase、验收标准三处是否一致

4. `queryId` phase 归属是否已经真正统一。
   - 是否还存在“Phase 0 只设计、Phase 0.5 就要求验收、Phase 1 又重复列为输出”的冲突
   - 当前文档是否已经收敛到一个清晰口径

5. Phase 0.5 的日志与追踪验收是否已经变成可 pass/fail。
   - 请核对它是否已经明确：
   - 最小技术栈
   - 最小必达事件
   - 必须出现的字段
   - 失败判定条件

6. `traceId` 技术来源是否已经说清楚。
   - 文档是否已明确：`traceId` 的权威来源是 Micrometer Observation / Tracing，而不是 MDC
   - MDC 是否只被定义为日志传播载体
   - 当前代码/依赖里是否真的还没有 tracing bridge / JSON encoder

7. Phase 0 Spike 失败后的后备路线是否已经足够具体。
   - 是否仍是“重评估技术路线”这种空话
   - 还是已经明确到：保留 `LlmClientFactory + LlmClient`，先在自定义 executor/advisor-like 包装层实现 metadata/logging/retry/outcome 语义

8. `LlmInvocationContext` 的字段设计是否过度前置。
   - 请核对文档是否已经写清：
   - 哪些字段是 Phase 0 必填
   - 哪些字段 Phase 0/1 可以为空
   - 是否还能出现“Spike 被 tracing 基础设施绑死”的风险

## 4. 输出要求

请按以下格式输出：

### Findings（按严重性排序）

- `P0`：阻断性错误 / 严重事实错误 / 直接导致方案不能进入下一阶段的问题
- `P1`：进入下一阶段前必须补齐的设计缺口
- `P2`：可边做边补、但建议在实施前明确的风险或补全项
- `P3`：文档质量、严密性、表达一致性问题

每条 finding 请尽量包含：

1. 标题
2. 引用位置
3. 你核对到的代码事实
4. 为什么这会构成问题
5. 建议如何修正

### 已确认吸收的问题

请单列一节，明确说明哪些前两轮问题这次已经被真正吸收，不要只说“整体更好了”。

### 总体结论

请明确给出一句话结论，例如：

- `可进入 Phase 0 Spike，但仍不能进入 Phase 1`
- `可进入 Phase 0 Spike，且 Phase 1 前置条件已基本齐备`
- `仍不建议进入 Phase 0`

## 5. 审查纪律

1. 请务必以**代码事实优先**，不要只复述文档。
2. 如果文档把“目标状态”误写成“当前现状”，请直接指出。
3. 如果某项你无法从代码中确认，请明确写“未核实到，不下结论”。
4. 请继续保持前两轮那种严格风格，不要为了“给面子”而降低标准。
