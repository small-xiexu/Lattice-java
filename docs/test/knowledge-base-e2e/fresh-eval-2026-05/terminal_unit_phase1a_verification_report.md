# Terminal Unit Phase 1A 验证报告

验证时间：2026-05-28
验证人：agentD
验证对象：terminal unit Phase 1A（`fact_card_terminal_units` 表、terminal unit 生成、FTS channel、RRF identity）

## 1. Gate 判定

**Phase 1A: FAIL**

| Gate | 状态 | 说明 |
|---|---|---|
| `fact_card_terminal_units` 表创建成功 | PASS | schema reset 后表与索引均已创建 |
| 重新导入后生成 terminal units | PASS | 46 个 terminal units 已生成 |
| `fact_card_terminal_fts` channel 有命中 | **FAIL** | channel 未接入 QueryGraph 调度路径 |
| 目标 terminal unit 进入 topK | **FAIL** | 依赖 channel 调度，未验证 |
| RRF unit identity 正确 | **FAIL** | 依赖 channel 调度，未验证 |
| metadata 字段完整 | PASS | DB 查询验证通过 |

**阻断根因：`QueryGraphDefinitionBaseSupport.buildDispatchPlan()` 未包含 `CHANNEL_FACT_CARD_TERMINAL_FTS`。**

## 2. 修改范围核对

Git diff 与 `terminal_unit_phase1a_fix_result_report.md` 一致。未修改禁止文件：
- `AnswerGenerationFallback*`：未修改
- `AnswerFallback*`：未修改
- `QueryResponseCitation*`：未修改
- `FactCardVector*`：未修改
- `config/synonyms.yaml`：未修改
- `config/rules.yaml`：未修改
- `prompt`：未修改
- `scripts/scan-redline.sh`：未修改

## 3. Redline 结果

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：exit=0，**BLOCKER=0**
- `REVIEW=2051`，`ALLOWLIST=259`

## 4. 硬编码扫描结果

- `git diff -- src/main/java src/test/java ... | rg -n "FQ3|FQ4|..."`：**无命中**
- 未跟踪新文件扫描：**无命中**
- 无 fresh eval 题面、case id、答案值、业务词、密钥泄露到生产代码

## 5. 工程测试结果

### 5.1 定向测试

| 测试类 | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| FactCardTerminalUnitMaterializerTests | 4 | 0 | 0 | 0 |
| FactCardTerminalUnitFtsSearchServiceTests | 2 | 0 | 0 | 0 |
| WeightedRrfFusionTest | 10 | 0 | 0 | 0 |
| QueryPreparationServiceTests | 9 | 0 | 0 | 0 |
| **小计** | **25** | **0** | **0** | **0** |

### 5.2 Fact Card 回归

| 测试类 | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| FactCardGenerationServiceTests | 21 | 0 | 0 | 0 |

### 5.3 Repository 测试

| 阶段 | Run | Failures | Errors | Skipped | 说明 |
|---|---|---|---|---|---|
| clean schema 前 | 3 | 0 | 0 | 3 | 测试库无 `fact_card_terminal_units` 表，表存在性保护跳过 |
| clean schema 后 | 3 | 0 | 0 | 0 | 测试库 (`ai-rag-knowledge-test`) schema 应用后通过 |

### 5.4 全量测试

| Run | Failures | Errors | Skipped | 结果 |
|---|---|---|---|---|
| **933** | **0** | **0** | **3** → **0**（修复后） | BUILD SUCCESS |

全量测试中 3 skipped 为 `FactCardTerminalUnitJdbcRepositoryTests`（测试库无新表）。对测试库执行 `LATTICE_DEV_DB_NAME=ai-rag-knowledge-test ./scripts/reset-lattice-schema.sh` 后复跑，3 个测试全部通过。

## 6. 清库/重建 Schema 与模型配置

### 6.1 Schema 重置

- 执行：`./scripts/reset-lattice-schema.sh`
- 结果：`lattice` schema 已按 `schema.sql` 重建
- `fact_card_terminal_units` 表：**已创建**，含全部索引和唯一约束

### 6.2 服务启动

- 命令：`./scripts/run-local-dev.sh`
- 端口：18082
- 健康检查：`{"status":"UP"}`

### 6.3 模型配置

- 按 `docs/模型绑定配置参考.md` 只读配置
- Provider：openai_compatible（Chat）+ openai_compatible（Embedding）
- Route：local_openai / gpt-5.5（Chat）+ zhipu_embedding / embedding-3（Embedding）
- 绑定：compile（3条）+ query（3条）+ deep_research（4条）= **10条全部就绪**
- 向量配置：已启用，embeddingModelProfileId=2
- 报告中不记录 API key / token / password / sk- 明文

### 6.4 模型网关

- 127.0.0.1:8888：`{"status":"ok"}`，可用
- LLM compile 流程正常运行（Writer → Reviewer → Fixer → Reviewer）

## 7. 资料导入结果

### 7.1 导入状态

| # | 文件名 | 状态 | 说明 |
|---|---|---|---|
| 1 | lab-safety-management-handbook.md | SUCCEEDED | article 经人工确认已发布 |
| 2 | equipment-borrowing-policy.yaml | SUCCEEDED | article 已自动发布 |
| 3 | chemical-storage-grading.xlsx | SUCCEEDED | article 已自动发布 |
| 4 | lab-emergency-response-procedures.pdf | **FAILED** | DataIntegrityViolationException (500)，未成功导入 |
| 5 | equipment-maintenance-schedule.csv | RUNNING | 编译进行中（修正阶段） |

### 7.2 数据计数

| 表 | 数量 |
|---|---|
| source_files | 4（不含 PDF） |
| articles | 3 |
| article_chunks | 7 |
| fact_cards | 7 |
| fact_card_terminal_units | **46** |

### 7.3 Terminal Unit 分布

| fact card | unit 数 | 来源 |
|---|---|---|
| fact-card:1（lab-safety-management-handbook） | 2 | Markdown FACT_ENUM |
| fact-card:2（equipment-borrowing-policy） | 37 | YAML FACT_ENUM key_value_list |
| fact-card:3（chemical-storage-grading） | 7 | XLSX FACT_ENUM |

### 7.4 Terminal Unit 样例（id=17，FQ3 目标）

```
id: 17
unit_id: fact-card-terminal:fact-card:2:0:fact_enum:41aa37638b50706c:11:...
terminal_unit_identity: terminal-unit:fact-card-terminal:fact-card:2:0:...
key_path: equipment_types[1].max_borrow_days
parent_path: equipment_types[1]
terminal_key: max_borrow_days
value_text: 7
value_type: number
display_text: equipment_types[1].max_borrow_days = 7
```

metadata_json 包含：terminalUnitId, unitId, terminalUnitIdentity, factCardId, cardId, keyPath, parentPath, terminalKey, value, valueType, displayText, sourceRefs, fieldLabel, fieldAliases, fieldDescription 等全部必需字段。

注：`terminalUnitId` 在 metadata_json 中为 null（应为数据库 id），需后续修复。

## 8. Terminal Unit FTS 检索验证

### 8.1 关键发现

`fact_card_terminal_fts` channel **未出现在检索计划中**。

**根因**：`QueryGraphDefinitionBaseSupport.buildDispatchPlan()`（line 263-329）的 dispatch plan 列表中未包含 `CHANNEL_FACT_CARD_TERMINAL_FTS`。

对比分析：
- `RetrievalStrategyResolver`：已注册 `CHANNEL_FACT_CARD_TERMINAL_FTS`，权重和 boost 已配置 ✓
- `KnowledgeSearchService.buildDispatchPlan()`：已包含 terminal unit channel ✓
- **`QueryGraphDefinitionBaseSupport.buildDispatchPlan()`**：**未包含** ✗

当前 query 执行路径使用 QueryGraph（StateGraph）流程，调用 `QueryGraphRetrievalSupport → QueryGraphDefinitionBaseSupport.buildDispatchPlan()`。由于该 dispatch plan 不含 terminal unit channel，terminal unit FTS 从未被调度执行。

### 8.2 FQ3 验证详情

- queryId: `3849bef7-3ffe-4995-a719-7eba7e6fdf3a`
- runId: 2
- 实际执行的 channels：article_chunk_fts, fact_card_fts, refkey, source, source_chunk_fts (+ skipped: article_vector, chunk_vector, fact_card_vector, graph)
- **fact_card_terminal_fts：未执行**
- fusedHitCount: 7
- factCardHitCount: 2（卡级 fact card 命中，非 terminal unit）
- 最终 answer：返回审批链内容，非目标值 7
- answerOutcome: SUCCESS, generationMode: FALLBACK

### 8.3 FQ4/FQ6/FG1/FG2

因 FQ3 已确认 `fact_card_terminal_fts` channel 未调度，FQ4/FQ6/FG1/FG2 的 terminal unit retrieval 同样无法生效。未逐题执行完整 query（避免浪费 token），但通过数据库直接验证确认：

- FQ4 目标 terminal units 存在：`equipment_types[0].deposit_amount = 100`（id=12），`equipment_types[2].deposit_amount = 1000`（id=26）
- FQ6 目标 terminal unit 存在：`borrowing_system.version = v2.3.1`（id=5）
- FG1 目标 terminal units 存在：`equipment_types[1].late_fee_per_day = 20`（id=19），`equipment_types[0].late_fee_per_day = 5`（id=11）
- FG2 目标 terminal unit 存在：`borrowing_system.max_concurrent_requests = 50`（id=9）

所有目标 terminal unit 均在 `fact_card_terminal_units` 表中，key_path、value、valueType 均正确。

## 9. RRF Identity 验证

**未验证**（channel 未调度，无法观察 RRF 行为）。

代码层面：
- `RrfFusionService` 已添加 FACT_CARD unit-aware 分支，优先读取 metadata 中的 `terminalUnitIdentity` → `unitId` 构造 hit key
- WeightedRrfFusionTest（10 个测试）全部通过，覆盖同卡 sibling 不折叠场景
- 但端到端无法验证，因为 terminal unit hit 从未进入 RRF 输入

## 10. 最终 Answer 观察

**不适用**（terminal unit 未进入检索，最终 answer 不能反映 terminal unit 能力）。

FQ3 实际 answer 返回了审批链内容而非 `max_borrow_days = 7`，这符合预期——没有 terminal unit 参与检索时，系统回退到卡级 fact card 的整卡内容选择。

## 11. 保护回归

**未执行**。原因：terminal unit channel 未接入 query 路径，不存在新增回归风险。Q6（terminal field alias / exact path）和 S2（chunk/anchor identity）的保护代码未被本轮改动触及。

## 12. 合规声明

1. 本轮未修改 `src/**`、`scripts/**`、`src/main/resources/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考模型配置，未修改、未 stage、未 commit、未输出内容到报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文
8. 清库范围仅限 `ai-rag-knowledge.lattice` 和 `ai-rag-knowledge-test.lattice`（测试库），未涉及 hidden eval 或其他外部库

## 13. 下一步建议

**唯一根因修复**：在 `QueryGraphDefinitionBaseSupport.buildDispatchPlan()` 的 dispatch plan 列表中加入：

```java
new SupplierRetrievalChannel(
    CHANNEL_FACT_CARD_TERMINAL_FTS,
    "fact_card",
    context -> factCardTerminalUnitFtsSearchService.search(
        context.getRetrievalQuestion(),
        context.getLimit()
    )
),
```

同时需要：
1. 注入 `FactCardTerminalUnitFtsSearchService` 到 `QueryGraphDefinitionBaseSupport`
2. 在 `QueryGraphState` / `QueryGraphStateKeys` 中增加 terminal unit hits 的 working set 存储

修复范围与实施计划 `terminal_unit_phase1_implementation_plan.md` 第 5 节一致（步骤 5-6 未实现）。

修复后需重新验证：
- `fact_card_terminal_fts` channel 出现在 channel hits 中
- 目标 terminal unit 进入 fused topK
- RRF identity 使用 unit identity 而非 card_id
- 同卡 sibling 不折叠

## 14. 是否建议提交

**不建议提交当前状态。** 需先完成 `QueryGraphDefinitionBaseSupport` 的 channel 接入。

当前已验证可提交的部分（terminal unit 生成、repository、materializer、FTS search service）为独立模块，代码质量通过测试验证。但整体功能不完整，应等 channel 接入修复后再统一提交。
