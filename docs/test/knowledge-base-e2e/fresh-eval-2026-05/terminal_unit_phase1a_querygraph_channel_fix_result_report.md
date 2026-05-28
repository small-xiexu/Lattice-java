# Terminal Unit Phase 1A QueryGraph Channel Fix Result Report

## 1. 结论

本轮已完成 terminal unit Phase 1A 的唯一阻断点修复：`QueryGraphDefinitionBaseSupport.buildDispatchPlan()` 已包含 `CHANNEL_FACT_CARD_TERMINAL_FTS`，真实 StateGraph query 路径现在会调度 terminal unit FTS channel，使其进入 retrieval / RRF。

本轮未修改 fallback、citation、vector、prompt、配置、fresh eval 题集或资料包。未 stage、未 commit、未 push。

## 2. 修改文件清单

### 本轮修改（QueryGraph channel 接入）

| 文件 | 变更 |
|---|---|
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionBaseSupport.java` | 注入 `FactCardTerminalUnitFtsSearchService`；新增 `CHANNEL_FACT_CARD_TERMINAL_FTS` 常量；在 `buildDispatchPlan()` 中加入 terminal unit channel（fact_card 分组）；在 `saveChannelHitsRef()` 和 `loadChannelHits()` 中加入 terminal unit 分支 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphState.java` | 新增 `factCardTerminalUnitHitsRef` 字段 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphStateKeys.java` | 新增 `FACT_CARD_TERMINAL_UNIT_HITS_REF` 键 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphStateMapper.java` | 在 `fromMap()` 和 `toMap()` 中加入 terminal unit hits ref 映射 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphRetrievalSupport.java` | 构造函数透传 `FactCardTerminalUnitFtsSearchService` |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphAnswerSupport.java` | 构造函数透传 `FactCardTerminalUnitFtsSearchService` |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionFactory.java` | 构造函数透传 `FactCardTerminalUnitFtsSearchService` |
| `src/test/java/com/xbk/lattice/query/graph/QueryGraphDefinitionBaseSupportTest.java` | **新增**：验证 dispatch plan 包含 terminal unit channel、channel 位置正确、state key 映射正确 |
| `src/test/java/com/xbk/lattice/query/service/QueryGraphOrchestratorTests.java` | 更新 `shouldUseDispatcherForSerialRetrievalPath` 的预期 channel 列表 |
| `src/test/java/com/xbk/lattice/query/service/QueryGraphTestSupport.java` | 传入 `FactCardTerminalUnitFtsSearchService` 到 `QueryGraphDefinitionFactory` |
| `src/test/java/com/xbk/lattice/benchmark/AstCitationDeepResearchBenchmarkRunner.java` | 传入 `FactCardTerminalUnitFtsSearchService` 到 `QueryGraphDefinitionFactory` |

### 未修改的文件（Phase 1A 已就绪，本轮不涉及）

- `FactCardTerminalUnitFtsSearchService.java` — 已在 Phase 1A 实现，本轮不修改
- `KnowledgeSearchService.java` — 已在 Phase 1A 接入 terminal unit channel（非 Graph 路径）
- `RetrievalStrategyResolver.java` — 已在 Phase 1A 注册 channel 权重和 boost
- `RrfFusionService.java` — 已在 Phase 1A 添加 unit-aware hit key

## 3. QueryGraph Channel 接入点

### 3.1 buildDispatchPlan() 中的位置

`fact_card_terminal_fts` 放在 `fact_card_fts` 之后、`fact_card_vector` 之前，同属 `fact_card` channel group：

```
... → source_chunk_fts (source) → fact_card_fts (fact_card) → fact_card_terminal_fts (fact_card) → fact_card_vector (vector) → contribution (graph) → ...
```

### 3.2 saveChannelHitsRef() 分支

```java
else if (CHANNEL_FACT_CARD_TERMINAL_FTS.equals(channel)) {
    delta.put(QueryGraphStateKeys.FACT_CARD_TERMINAL_UNIT_HITS_REF, ref);
}
```

### 3.3 loadChannelHits() 分支

```java
channelHits.put(CHANNEL_FACT_CARD_TERMINAL_FTS, queryWorkingSetStore.loadHits(state.getFactCardTerminalUnitHitsRef()));
```

### 3.4 构造函数注入

`FactCardTerminalUnitFtsSearchService` 通过 `QueryGraphDefinitionFactory → QueryGraphAnswerSupport → QueryGraphRetrievalSupport → QueryGraphDefinitionBaseSupport` 完整构造函数链注入，null-safe fallback 为 `new FactCardTerminalUnitFtsSearchService(null)`（与 KnowledgeSearchService 一致）。

## 4. 是否新增 QueryGraphState / StateKeys

是，新增了：

- `QueryGraphState.factCardTerminalUnitHitsRef` — terminal unit hits 的 working set 引用
- `QueryGraphStateKeys.FACT_CARD_TERMINAL_UNIT_HITS_REF = "factCardTerminalUnitHitsRef"` — Graph Map 键名
- `QueryGraphStateMapper` 在 `fromMap()` / `toMap()` 中双向映射该字段

不新增其他字段。terminal unit channel 的 working set 存储与其他 channel 使用相同的 `QueryWorkingSetStore.saveHits/loadHits` 机制，不引入新的存储抽象。

## 5. 为什么没有修改 fallback / citation / vector

- **fallback**：明确禁止。terminal unit 进入 topK 后由现有 RRF 逻辑自然处理排序，不叠 selector/conclusion/snippet gate。
- **citation**：明确禁止。Phase 1 只保留 unit metadata 用于后续升级，不改 citation schema/response DTO。
- **vector**：明确禁止。terminal unit 不做 embedding，不新增 vector 表或 ANN 通道。
- **prompt / config**：明确禁止。不修改 config/synonyms.yaml、config/rules.yaml 或 prompt。
- **fallback selector**：不修改 `AnswerGenerationFallback*` / `AnswerFallback*`。
- **citation schema**：不修改 `QueryResponseCitation*` / response DTO。

本轮只修复了一个问题：QueryGraph dispatch plan 未调度 terminal unit FTS channel。其他能力已在 Phase 1A 实现并通过测试。

## 6. Redline 结果

命令：
```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：
- Exit code: 0
- BLOCKER=0

## 7. 定向测试结果

命令：
```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
  -Dtest=FactCardTerminalUnitFtsSearchServiceTests,WeightedRrfFusionTest,QueryPreparationServiceTests,QueryGraphDefinitionBaseSupportTest test
```

结果：

| 测试类 | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| QueryGraphDefinitionBaseSupportTest | 3 | 0 | 0 | 0 |
| QueryPreparationServiceTests | 9 | 0 | 0 | 0 |
| FactCardTerminalUnitFtsSearchServiceTests | 2 | 0 | 0 | 0 |
| WeightedRrfFusionTest | 10 | 0 | 0 | 0 |
| **合计** | **24** | **0** | **0** | **0** |

BUILD SUCCESS.

QueryGraphDefinitionBaseSupportTest 覆盖：
1. `shouldIncludeFactCardTerminalFtsChannelInDispatchPlan` — dispatch plan 包含 `fact_card_terminal_fts`
2. `shouldPlaceTerminalUnitFtsAfterFactCardFts` — channel 位置在 `fact_card_fts` 之后
3. `shouldMapTerminalUnitHitsToCorrectStateKey` — state key 映射到 `FACT_CARD_TERMINAL_UNIT_HITS_REF`

## 8. 全量 mvn test 结果

命令：
```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：
- **Tests run: 936, Failures: 0, Errors: 0, Skipped: 0**
- BUILD SUCCESS
- Total time: ~06:19 min

原有 3 个 skip（FactCardTerminalUnitJdbcRepositoryTests）在本次运行中不再 skip（原因：测试环境数据库已由之前 agentD 的 schema reset 应用了新表）。

## 9. 硬编码扫描结果

```bash
git diff -- src/main/java src/test/java | rg -n "FQ3|FQ4|FQ6|FG1|FG2|..."
```

结果：无命中（exit 1，rg 未找到匹配）。

新增文件扫描：无命中。

无 fresh eval 题面、case id、答案值、业务词、密钥泄露到本轮生产代码或测试。

## 10. 残余风险

- 本轮只修复了 QueryGraph dispatch plan 的 channel 接入，不改变 terminal unit 的 FTS 检索、RRF identity 或项目 schema。
- terminal unit 能否在实际 runtime 中被调度并进入 topK，需要在真实服务中验证（由 agentD 执行），因为：
  - `fact_card_terminal_fts` 是可选 channel，其启用取决于 `RetrievalStrategy` 的权重 > 0
  - channel 的 enabled/disabled 由 `RetrievalStrategyResolver.resolve()` 的权重和 `SupplierRetrievalChannel.isEnabled()` 共同决定
  - 当前 CR 只保证 dispatch plan 包含该 channel，不保证所有 query 都会启用它
- 同一个 `fact_card` group 内的并发上限（`maxConcurrencyPerGroup`）可能影响 terminal unit 和 fact card FTS 同时执行的调度策略
- `RrfFusionService` 的 `isPrimaryStructuredEvidence` 已在 Phase 1A 把 terminal channel 视为主证据通道，本轮未修改此逻辑

## 11. 是否需要 agentD 重新 clean schema 验证

**不需要重新清库**。原因：

- 本轮没有修改 schema、repository、编译器或导入流程。
- `fact_card_terminal_units` 表和数据已于 agentD 验证轮创建并导入。
- 本轮修改仅限 QueryGraph 调度层（Java 代码），不需要 DDL 变更或数据重建。

但 agentD **需要重新验证**：
1. 服务重启后 `fact_card_terminal_fts` channel 出现在 dispatch plan 和 retrieval audit 中
2. 目标 terminal unit 进入 fused topK
3. RRF identity 使用 unit identity 而非 card_id
4. 同卡 sibling 不折叠
5. fresh eval 指标对比（FQ3/FQ4/FQ6/FG1/FG2）

## 12. 明确未 stage、未 commit、未 push

本轮所有修改限制在本地工作树，未执行任何 git stage、commit 或 push 操作。

```bash
git status --short
```

确认只有未跟踪文件和已修改文件在工作树中，无暂存区内容。

## 13. 回滚方式

如需回滚本轮修复：

1. 从 `QueryGraphDefinitionBaseSupport.buildDispatchPlan()` 中移除 `CHANNEL_FACT_CARD_TERMINAL_FTS` channel
2. 从 `saveChannelHitsRef()` 和 `loadChannelHits()` 中移除对应分支
3. 从构造函数和字段中移除 `factCardTerminalUnitFtsSearchService`
4. 移除 `QueryGraphState.factCardTerminalUnitHitsRef` 和 `QueryGraphStateKeys.FACT_CARD_TERMINAL_UNIT_HITS_REF`
5. 恢复 `QueryGraphStateMapper` 中的映射
6. 恢复构造函数链（RetrievalSupport / AnswerSupport / DefinitionFactory）
7. 恢复测试文件中的 channel 列表和构造函数调用
8. 删除 `QueryGraphDefinitionBaseSupportTest.java`

回滚不影响数据库、Phase 1A 其他实现（terminal unit 生成、repository、FTS search service）或非 QueryGraph 路径（KnowledgeSearchService）。
