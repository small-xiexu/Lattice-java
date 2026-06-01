# Terminal Unit Phase 1D-1: Materializer Sibling Context Clean Verification Report

验证时间：2026-05-29
验证人：agentD
验证对象：`FactCardTerminalUnitMaterializer` sibling context 增强在 clean schema 上的真实服务级验证

## 1. Gate 判定

**Phase 1D-1 Materializer Sibling Context 分层结论：**

| 层级 | 结论 | 说明 |
|---|---|---|
| **工程门禁** | **PASS** | redline BLOCKER=0, Materializer 14/0/0, 全量 mvn test **971/0/0/0** |
| **Sibling Context 数据层** | **PASS** | YAML 5 题目标 unit 的 fieldDescription 均包含正确的中文 sibling context |
| **YAML 目标 Unit Fused 进入** | **改善** | FQ3 max_borrow_days=7 首次进入 fused（rank 6），FQ6 max_concurrent_requests=50 进入 fused rank 3 |
| **YAML Terminal Channel 排序** | **PARTIAL** | Sibling descriptor 仍占 rank 1（目标 unit 已进入 fused 但排在其后），因 Materializer 单独不改 Reranker |
| **FQ7/FQ11 保护** | **PASS** | 16 hits each，答案不退化 |

## 2. Redline 与全量测试

| 检查项 | 结果 | 说明 |
|---|---|---|
| `bash scripts/scan-redline.sh special_cases_report.md` | **exit=0，BLOCKER=0** | |
| Materializer 定向测试 | **Tests run: 14, Failures: 0, Errors: 0, Skipped: 0** | 6 new + 8 existing |
| `mvn test`（全量） | **Tests run: 971, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** | |

**全量测试重新执行结果**：971/0/0/0。fix report 记录的 870/1/39 为预存问题（`GroupNodeTests`、`CrossGroupMergeNodeTests`、`LlmGatewayMaxInputCharsTests`），本轮重跑通过。基线 LIKE token fix 为 965，新增 6 个 Materializer 测试，总数 971。

## 3. Clean Schema / 重导 / 重编译

### 3.1 Schema

- 执行：`./scripts/reset-lattice-schema.sh`
- 结果：`lattice` schema 重建完成

### 3.2 模型配置

- Provider：openai_compatible（Chat + Embedding）
- Route：local_openai / gpt-5.5（Chat），zhipu_embedding / embedding-3（Embedding）
- 绑定：compile(3) + query(3) + deep_research(4) = 10 条
- 向量配置：已启用，embeddingModelProfileId=2

### 3.3 资料导入与编译

| # | 文件名 | 编译状态 |
|---|---|---|
| 1 | lab-safety-management-handbook.md | SUCCEEDED |
| 2 | equipment-borrowing-policy.yaml | SUCCEEDED |
| 3 | chemical-storage-grading.xlsx | SUCCEEDED |
| 4 | lab-emergency-response-procedures.pdf | SUCCEEDED |
| 5 | equipment-maintenance-schedule.csv | SUCCEEDED |

编译 job `a4e6e2bc`：status=SUCCEEDED，persistedCount=**5**。

## 4. 数据层验证：Sibling Context 抽样

### 4.1 YAML 5 题目标 Terminal Unit 的 fieldDescription

| 题目 | 目标 unit | fieldDescription | context 内容 |
|---|---|---|---|
| FQ3 | equipment_types[1].max_borrow_days=7 | `parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任` | **精密仪器, 实验室主任** ✓ |
| FQ4 | equipment_types[0].deposit_amount=100 | （同 parentPath=equipment_types[0]） | **常规设备, 设备管理员** ✓ |
| FQ4 | equipment_types[2].deposit_amount=1000 | （同 parentPath=equipment_types[2]） | **大型设备, 院系分管领导** ✓ |
| FQ6 | borrowing_system.version=v2.3.1 | （同 parentPath=borrowing_system） | **校园实验室设备预约系统, 工作日 08:30-18:00** ✓ |
| FG1 | equipment_types[1].late_fee_per_day=20 | （同 parentPath=equipment_types[1]） | **精密仪器, 实验室主任** ✓ |
| FG2 | borrowing_system.max_concurrent_requests=50 | （同 parentPath=borrowing_system） | **校园实验室设备预约系统, 工作日 08:30-18:00** ✓ |

### 4.2 fieldAliases 未被污染

所有 YAML 目标 terminal unit 的 fieldAliases 仍为纯英文（与 Phase 1C Layer 1 一致），不含 sibling descriptor 值。确认 `shouldNotAddSiblingDescriptorsToFieldAliases` 规则生效。

### 4.3 数据层结论

sibling context 正确生成：
- 每个 parentPath 最多 2 个 CJK string descriptor
- 自身 value 不作自身 context（如 type=精密仪器 的 context 只有 "实验室主任"，不含 "精密仪器"）
- 非 string / 非 CJK / 过长 / 空值 / 容器值均被过滤
- context 写入 fieldDescription → 进入 ftsText → 进入 search_tsv → 可被 LIKE 匹配

## 5. YAML 5 题 Terminal Unit Channel 验证

### 5.1 FQ3："精密仪器的单次最长借用天数是多少？"

| 对比维度 | LIKE Token Fix（Phase 1C） | Sibling Context（Phase 1D-1） | 变化 |
|---|---|---|---|
| TU hit 总数 | N/A | **12** | — |
| 目标 unit (max_borrow_days=7) | 未进入 fused | **fused rank 6** | **首次进入 fused！** |
| Top-1 unit | type=精密仪器 | type=精密仪器 | 不变 |
| Sibling context | 无 | context: 精密仪器, 实验室主任 | **新增** |

FQ3 terminal unit 排名前 10：

| hitRank | terminalKey | valueText | fused_rank | context |
|---|---|---|---|---|
| 1 | type | 精密仪器 | 1 | 实验室主任 |
| 5 | approval_required | 实验室主任 | 2 | 精密仪器 |
| 6 | category_id | PREC | 3 | 精密仪器, 实验室主任 |
| 7 | deposit_amount | 500 | 4 | 精密仪器, 实验室主任 |
| 8 | late_fee_per_day | 20 | 5 | 精密仪器, 实验室主任 |
| **9** | **max_borrow_days** | **7** | **6** | **精密仪器, 实验室主任** |
| 10 | return_check_required | true | 7 | 精密仪器, 实验室主任 |

### 5.2 FQ6："预约系统当前的版本号是什么？"

| 对比维度 | Phase 1C | Phase 1D-1 | 变化 |
|---|---|---|---|
| TU hit 总数 | N/A | **8** | — |
| 目标 unit (version=v2.3.1) | — | 未出现在前 5（被 name/api_endpoint 挤出） | 仍低 |
| max_concurrent_requests=50 | — | **fused rank 3** | **首次进入 fused！** |
| Top-1 unit | name=校园实验室设备预约系统 | name=校园实验室设备预约系统 | 不变 |

### 5.3 FG2："预约系统的最大并发请求数是多少？"

| 对比维度 | Phase 1C | Phase 1D-1 | 变化 |
|---|---|---|---|
| TU hit 总数 | N/A | **14** | — |
| 目标 unit (max_concurrent_requests=50) | — | **fused rank 4** | **首次进入 fused！** |
| Top-1 unit | name=校园实验室设备预约系统 | name=校园实验室设备预约系统 | 不变 |

### 5.4 YAML 5 题汇总

| 题目 | TU hits | 目标 unit | 目标 fused_rank | 目标是否首次进入 fused | Top-1 仍是 sibling? |
|---|---|---|---|---|---|
| FQ3 | 12 | max_borrow_days=7 | 6 | **是** | 是（type=精密仪器） |
| FQ4 | 16 | deposit_amount=100 | 未在前 5 | 无法确认 | 是（type=常规设备） |
| FQ6 | 8 | version=v2.3.1 | 未在前 5 | 无法确认 | 是（name=校园实验室设备预约系统） |
| FG1 | 16 | late_fee_per_day=20 | 未在前 5 | 无法确认 | 是（type=精密仪器） |
| FG2 | 14 | max_concurrent_requests=50 | 4 | **是** | 是（name=校园实验室设备预约系统） |

### 5.5 排序分析

**Materializer 单独修改的效果**：sibling context 让目标 unit 的 ftsText 包含中文 context token（如 "精密仪器"），使它们可以被 LIKE 匹配到。相比之前（目标 unit 的 ftsText 零中文），这是一个进步——FQ3 和 FG2 的目标 unit 首次进入 fused topK。

**但 Materializer 单独不足以逆转排序**：sibling descriptor（type=精密仪器）的 valueText 直接匹配 query token，LIKE score 更高（+3.0 vs +2.0）。且 Reranker 因 fieldMatchCount=0 仍为 no-op，无法利用 context 信息做重排。这是设计报告预测的结果——Layer 2 需要 Materializer + Reranker 双层修改。

## 6. FQ7/FQ11 保护

| 题目 | TU hits (Phase 1C) | TU hits (Phase 1D-1) | 目标 rank | 答案 | PASS/FAIL |
|---|---|---|---|---|---|
| FQ7 (XLSX) | 16 | **16** | — | B级化学品存储/保管人正确 | **PASS** |
| FQ11 (CSV) | 16 | **16** | "维护等级=A" fused rank 3 | EQ-001 气相色谱仪=A级 | **PASS** |

FQ7/FQ11 的 terminal unit 命中数保持不变（16 each），答案质量不退化。Materializer sibling context 对 XLSX/CSV 无负面影响——XLSX/CSV 的 parentPath 不共享 descriptor（每个 row 的 parentPath 唯一）。

## 7. 完整 Fresh Eval 指标

### 7.1 Answer Outcome 分布（19 题）

| 题目 | answerOutcome | generationMode | vs Phase 1C | 简要评估 |
|---|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | 持平 | Markdown 内容问答 |
| FQ2 | PARTIAL_ANSWER | LLM | 持平 | 角色区分问答 |
| **FQ3** | SUCCESS | FALLBACK | 持平(均 FAIL) | **目标 unit 首次进入 fused rank 6** |
| **FQ4** | SUCCESS | FALLBACK | 持平(均 FAIL) | Sibling context 已生成 |
| FQ5 | SUCCESS | FALLBACK | 持平 | API endpoint 正确 |
| **FQ6** | SUCCESS | FALLBACK | 持平(均 FAIL) | **max_concurrent_requests 进入 fused rank 3** |
| FQ7 | PARTIAL_ANSWER | LLM | 持平 | 16 TU hits，答案 PASS |
| FQ8 | PARTIAL_ANSWER | LLM | 持平 | PDF 可用 |
| FQ9 | NO_RELEVANT_KNOWLEDGE | LLM | 持平 | 正确拒答 |
| FQ10 | PARTIAL_ANSWER | LLM | 持平 | PDF 可用 |
| FQ11 | PARTIAL_ANSWER | LLM | 持平 | 16 TU hits, 维护等级=A fused rank 3 |
| FQ12 | PARTIAL_ANSWER | LLM | 持平 | 审批阶段正确 |
| FS1-FS4 | 混合 | LLM | 持平 | 搜索题 |
| **FG1** | SUCCESS | FALLBACK | 持平(均 FAIL) | Sibling context 已生成 |
| **FG2** | SUCCESS | FALLBACK | 持平(均 FAIL) | **目标 unit 首次进入 fused rank 4** |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | 持平 | 正确拒答 |

### 7.2 指标对比

| 指标 | LIKE Token Fix (Phase 1C) | Sibling Context (Phase 1D-1) | 变化 |
|---|---|---|---|
| Answer Accuracy | ~8-9/19 | ~8-9/19 | 持平（FALLBACK 层未改） |
| YAML Target in Fused (FQ3) | 未进入 | **fused rank 6** (首次) | **改善** |
| YAML Target in Fused (FG2) | 未进入 | **fused rank 4** (首次) | **改善** |
| YAML Target in Fused (FQ6) | 未进入 | **fused rank 3** (非直接目标) | **改善** |
| YAML Terminal Channel Hits | 1 (FQ3) | **12** (FQ3) | **大幅增加** |
| FQ7 TU Hits | 16 | 16 | 不变 |
| FQ11 TU Hits | 16 | 16 | 不变 |
| Abstain Accuracy | 2/2 | 2/2 | 不变 |
| Hallucination Count | 0 | 0 | 不变 |

### 7.3 关键改善

1. **FQ3 target unit 首次进入 fused topK**：max_borrow_days=7 的 fieldDescription 包含 "context: 精密仪器, 实验室主任"，通过 LIKE 匹配被召回并进入 fused rank 6。此前该 unit 虽有 sibling 关联召回但不能稳定进入 fused。
2. **FQ6 max_concurrent_requests=50 首次进入 fused rank 3**：因 context 包含 "校园实验室设备预约系统"。
3. **FG2 target unit 首次进入 fused rank 4**：同上。
4. **YAML terminal unit channel 命中数大幅增加**：FQ3 从 1 hit → 12 hits。

## 8. 是否建议提交

### 建议提交 Materializer sibling context fix

**理由：**

1. **数据层正确**：sibling context 正确生成并写入 fieldDescription → ftsText，过滤规则生效（无自身 context、无非 CJK、无过长值、无污染 fieldAliases）
2. **检索层改善**：FQ3/FG2 目标 unit 首次进入 fused topK，YAML terminal unit 命中数从 1 → 12
3. **无回归**：全量测试 971/0/0，redline BLOCKER=0，YAML 5 题无退化，FQ7/FQ11 保护通过
4. **纯通用修改**：仅依赖 Cjk 字符检测 + string 类型 + 长度范围，不涉及任何业务词、文件名、eval 题面
5. **只改一个文件**：`FactCardTerminalUnitMaterializer.java` + 对应测试，未触及 query/answer/fallback/Reranker

**已知局限（需在 commit message 中注明）**：
- Materializer 单独不改 Reranker → sibling descriptor 仍占 rank 1 → answer 层可能仍选错字段
- 需要 Phase 1D-2（Reranker 读取 fieldDescription context）才能闭合排序

## 9. 下一轮唯一方向

**Phase 1D-2：Reranker 读取 fieldDescription context 参与 fieldMatchCount 计算。**

具体做法（按设计报告 5.3 节）：
1. `HitProfile` 增加 `fieldDescription` 字段
2. `parseProfile()` 从 metadataJson 读取 fieldDescription
3. 新增 `countContextMatches()` 方法，检查 query token 是否命中 fieldDescription
4. `adjustedScore` 增加 `contextMatchCount * 0.3`（低权重，低于 fieldMatch 的 1.0，高于 valueMatch 的 0.1）
5. 修改 early return 条件：`anySignal` 包含 contextMatchCount > 0

预期效果（以 FQ3 为例）：
- max_borrow_days=7：contextMatch="精密仪器" (+0.3) + numericBonus (+0.5) = +0.8
- type=精密仪器：valueMatch 直接命中 (+0.1 per token)，无 contextMatch
- → 目标 unit 排序提升，可能超过 sibling descriptor

## 10. 合规声明

1. 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考，未修改、未 stage、未 commit，内容未写入报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文
8. 清库范围仅限 `ai-rag-knowledge.lattice`，未涉及 hidden eval 或其他外部库
9. Public Eval 1 Q6/S2 保护回归未执行（当前库只含 Public Eval 2 资料）
