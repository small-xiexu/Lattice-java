# Terminal Unit Phase 1H: fused rank signal analysis report

分析时间：2026-05-31
执行人：agentB
任务边界：只读源码与既有报告；不修改 `src/main/java/**`、`src/test/java/**`、配置、脚本、题集或 redline allowlist；不清库、不写库、不读取 hidden eval。

## 1. 当前失败类型归类

FQ6 当前失败类型应归为：

**证据已召回，但 answer/conclusion 阶段 terminal candidate precision 使用了错误或不可稳定复原的排序信号。**

它不是资料缺失、编译抽取缺失、terminal unit 召回缺失，也不是 Phase 1F metadata alias sync 未生效。Phase 1F 报告已经确认 terminal unit 的 metadata alias 在 DB 与 query audit 中同步；Phase 1G 报告确认 best-score 逻辑本身能按分数挑候选，但 `getScore()` 这个信号不适合表达 terminal 字段意图精度。

## 2. `QueryArticleHit.getScore()` 为什么不适合作为 terminal candidate precision 信号

`QueryArticleHit` 只有一个 `score` 字段，没有 rank 字段，也没有字段说明能区分“通道原始分”“RRF 融合分”“fallback 相关性分”。

源码链路显示该字段语义会随阶段变化：

- terminal unit FTS 通道内，`FactCardTerminalUnitFtsSearchService` 从 `LexicalSearchRecord.getScore()` 构造 `QueryArticleHit`，这是 lexical/LIKE/FTS 侧分数，经 review policy 调整后保留。
- `FactCardTerminalUnitIntentReranker` 只重排 terminal unit FTS hits，不把 adjusted score 写回 `QueryArticleHit`。
- `SupplierRetrievalChannel` 会对通道 hit 统一调用 `QueryHitIntentReranker`，该步骤会用 intent/review bonus 构造新的 `QueryArticleHit.score`。
- `RrfFusionService` 融合时会重新构造 `QueryArticleHit`，此时 `score` 写成 RRF score map 的融合分。
- structured guardrail 与 fallback selector 会重排列表，但不会把 fallback 排序分或 fused rank 写回 `score`。

因此 `getScore()` 是一个被多阶段复用的载体，不是稳定的 terminal candidate precision 信号。Phase 1G clean runtime 中出现的高低分差异，本质反映的是词面匹配强度：直接命中 value text 的候选会压过通过字段 alias/metadata 命中的候选。terminal candidate precision 需要的是“字段意图对齐”，不能用该词面强度直接替代。

结论：**Phase 1G best-score 不应作为收口修复继续提交。**

## 3. `fused_rank` 在哪里计算，runtime answer 阶段是否可访问

`fused_rank` 不是 `QueryArticleHit` 字段。

它在 `RetrievalAuditService.persist(...)` 内部由 `buildFusedRankByKey(fusedHits)` 根据 `fusedHits` 当前列表位置临时计算，随后通过 `persistSingleChannelHit(...)` 写入 retrieval audit 明细表。

该 rank map 的生命周期只在 audit persistence 内：

- 不写回 `QueryArticleHit.metadataJson`。
- 不写入 `QueryArticleHit.score`。
- 不随 `queryWorkingSetStore.saveFusedHits(...)` 传给 answer 阶段。
- `AnswerGenerationPayloadOrchestrator` 只接收 `List<QueryArticleHit>`，没有 audit rank map 或 retrieval audit ref。

所以，**审计表里的 `fused_rank` 当前只用于审计与分析；runtime answer/conclusion 阶段不能直接读取它。**

## 4. fallbackHits 进入 ConclusionBuilder 前是否丢失原始 fused order

是，至少在普通 fallback 主链中已经丢失。

answer 阶段原始输入是 `queryArticleHits`，它来自 graph working set 中保存的 fused hits。这个列表在进入 deterministic fallback 后会经过 `AnswerFallbackEvidenceSelector.selectFallbackEvidenceHits(...)`：

- 先做相关性过滤。
- 再走 `sortFallbackEvidenceHits(...)`。
- 排序优先级依次为 question-focused fallback line score、`QueryEvidenceRelevanceSupport.score(...)`、证据类型优先级、最后才是 `getScore()`。
- 去重后输出 `fallbackHits`。

也就是说，`fallbackHits` 的列表顺序已经是 fallback selector 的顺序，不再可靠代表 original fused order。Phase 1F 已经暴露过这一点：审计中 fused rank 更靠前的 terminal unit，在 conclusion builder 遍历顺序中并不一定先出现。

## 5. 能否只在 ConclusionBuilder 内用 fallbackHits 原始顺序恢复 fused rank

不能。

`AnswerFallbackConclusionBuilder` 当前只拿到 `fallbackHits` 与 `queryTokens`。此时 `fallbackHits` 已经被 selector 过滤、排序和去重，`fallbackHits.indexOf(...)` 只能恢复 fallback selector rank，不能恢复 fused rank。

除非在进入 selector 之前把原始 fused order 显式带进 builder，否则 builder 无法区分：

- 某个 terminal unit 在 retrieval fused list 中排第几。
- 某个 terminal unit 是被 fallback scoring 提前，还是原本 fused rank 就靠前。
- `getScore()` 当前语义到底是通道分、融合分，还是某个补充候选保留的旧分。

## 6. 候选修复方案与风险

### 方案 A：继续提交 Phase 1G best-score

不推荐，且应明确禁止作为收口修复。

风险：

- 使用 `getScore()` 会奖励 value text 的词面命中，而不是字段意图命中。
- `score` 字段跨阶段语义不稳定。
- 当前 clean runtime 已证明该方案会稳定选错 terminal candidate。

### 方案 B：`RrfFusionService` 给 metadata 写入 fusedRank

可行，但不是最小、也不是最稳的 answer 侧修复。

优点：

- fused order 会随 `QueryArticleHit` 进入 working set、answer prompt、fallback selector 和 conclusion builder。
- 不需要扩展 `QueryArticleHit` 构造器。

风险：

- 需要修改 metadata JSON，影响 retrieval 层所有或部分 hit 的透传元数据。
- 如果 rank 在 `RrfFusionService` 内写入，后续 graph 侧 enrichment/filter/review guardrail 可能改变最终 answer 列表，metadata rank 与 audit final rank 可能出现绝对序号差异。
- 容易把审计字段和业务 metadata 混在一起，后续维护要额外约束字段命名与覆盖规则。

### 方案 C：`QueryArticleHit` 增加 rank 字段

语义最清晰，但改动面偏大。

优点：

- 类型化表达 retrieval rank，不污染 metadata。
- 后续 prompt、citation、audit 都可显式消费。

风险：

- `QueryArticleHit` 构造器重载多，Jackson 序列化、working set、测试构造都要同步。
- 会扩大本轮高危主链修改范围。
- 若只为 terminal conclusion candidate precision 引入全局字段，性价比不高。

### 方案 D：`AnswerFallbackEvidenceSelector` 保留 fused order

不推荐作为本轮最小修复。

优点：

- selector 接收的原始 `queryArticleHits` 还保留 answer 输入顺序，可以在排序前记录 index。

风险：

- selector 顺序同时影响 fallback reference section、secondary evidence、`resolveFallbackAnswerOutcome(...)` 的首条证据判断。
- 为了一个 terminal conclusion 选择问题改 selector 全局排序，容易引入 outcome 或 citation 侧回归。
- 如果只是用 original index 做 tie-break，无法覆盖当前这类 question-focused score 已经把候选重排的场景。

### 方案 E：ConclusionBuilder 内新增字段意图匹配分，不用 fused rank

不推荐。

优点：

- 不需要传 rank。
- 修改范围看似集中在 conclusion builder。

风险：

- 会在 answer fallback 主链复制 terminal unit reranker 的字段意图逻辑。
- 容易为了当前 case 增加语言信号、字段语义或问法特征，红线风险更高。
- 字段意图能力应该在 retrieval/rerank 层通用化，conclusion builder 不应再发展一套隐式 reranker。

### 方案 F：把 answer 原始 fused order 显式传入 ConclusionBuilder

推荐作为唯一下一步。

做法：

- 不改 `RrfFusionService`。
- 不改 `QueryArticleHit` 数据结构。
- 不改 `AnswerFallbackEvidenceSelector` 的排序与 outcome 输入。
- 在 answer markdown/conclusion 调用链中，将原始 `queryArticleHits` 同 `fallbackHits` 一起传给 `AnswerFallbackConclusionBuilder`。
- `buildTerminalUnitExactConclusionLines(...)` 只对 terminal unit 候选使用原始 `queryArticleHits` 的列表 index 作为 fused-order rank，选择 index 更小的候选。
- 当候选不在原始列表中时，回退到现有顺序或最低风险 tie-break，不使用 `getScore()` 作为主信号。

优点：

- 只影响 terminal unit exact conclusion 的候选选择，不改 retrieval、selector、prompt、citation、outcome。
- 不新增字段语义规则，不需要业务词、字段名、文件名、答案值或问法特判。
- 保留 selector 当前过滤能力，同时恢复它丢失的原始 fused ordering。
- `buildEvidencePayload(...)` 的 outcome 仍由原有 `fallbackHits` 推导，不因本修复改变首条 fallback 证据。

风险：

- 需要轻微调整 `AnswerFallbackMarkdownBuilder` / `AnswerGenerationFallbackConclusionSupport` / `AnswerFallbackConclusionBuilder` 的内部方法签名。
- 直接调用 builder 的既有测试需要保持兼容入口或同步调整。
- 如果未来 selector 把目标 terminal unit 过滤掉，仅传 rank 不能补回候选；这不属于本轮根因。

## 7. 推荐唯一下一步 agentA 最小修复范围

推荐交给 agentA 的唯一目标：

**在 answer fallback conclusion 调用链中，把原始 `queryArticleHits` 的 fused ordering 传入 `AnswerFallbackConclusionBuilder`，并让 terminal unit exact conclusion 在多个候选同时 query-focused 时按原始 fused order 选择。**

允许修改范围建议：

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackMarkdownBuilder.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

禁止修改范围建议：

- 不改 `RrfFusionService`。
- 不改 `QueryArticleHit` 字段。
- 不改 `AnswerFallbackEvidenceSelector` 排序。
- 不改 retrieval audit。
- 不改测试预期、题集、配置、脚本、redline allowlist。
- 不写任何业务词、字段名、文件名、答案值、case id 或具体问法判断。

验证建议：

- 先跑 redline。
- 跑 `AnswerFallbackConclusionBuilderTests` 与 `AnswerFallbackEvidenceSelectorTests`。
- 由 agentD 再做 clean runtime 复验：重点看 FQ6 terminal candidate 是否按 fused order 收敛，同时保护 FG2 与其他 terminal unit 场景。

## 8. 明确禁止事项

**禁止继续提交 Phase 1G best-score 作为收口修复。**

原因：

- clean runtime 已证明该方案选择了词面分更高但字段意图更弱的 terminal unit。
- `getScore()` 不是 fused rank，也不是 terminal 字段意图精度。
- 继续提交会把错误信号固化进高危 answer fallback 主链。

本轮分析未修改生产代码、测试、配置、脚本、题集或 redline allowlist，未运行清库或写库命令，未读取 hidden eval。
