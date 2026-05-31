# Terminal Unit Phase 1E-2 Clean Schema 端到端验证报告

验证时间：2026-05-30
验证人：agentD
验证对象：Phase 1E-2 scoped LLM alias enricher 在 clean schema 上的完整端到端验证

## 1. 验证结论

**PARTIAL** — Terminal unit 召回大幅改善（YAML 5/5 目标 unit 首次全部进入 fused topK），但答案层未消费 terminal unit evidence，YAML 5 题仍 FAIL（证据已召回但回答漏点）。整体 fresh eval 指标与基线持平。

**分层结论**：
- **LLM Alias 生成**：PASS — 全部 5 个 YAML 目标字段均生成语义正确的中文 alias
- **Terminal Unit 召回**：PASS — 5/5 目标 unit 进入 fused topK（基线 0/5）
- **Terminal Unit 排名**：显著改善 — FQ3 rank 9→2, FQ6 >5→2, FG2 4→2
- **最终答案**：无改善 — 仍 0/5 PASS（证据已召回但 fallback 未消费 terminal unit evidence）
- **XLSX/CSV 保护**：PASS — 无退化

## 2. 环境与前置

| 项目 | 值 |
|---|---|
| 日期 | 2026-05-30 |
| Schema | `./scripts/reset-lattice-schema.sh` 清库重建 |
| 服务 | `scripts/run-local-dev.sh`，端口 18082 |
| JDK | 21.0.9 |
| 容器 | vector_db (PostgreSQL), redis (复用) |
| Chat | local_openai → gpt-5.5 |
| Embedding | zhipu_embedding → embedding-3 (2000维) |
| 绑定 | 11 条 (compile×4 + query×3 + deep_research×4) |
| field-alias-enricher | id=4, routeLabel=compile.field-alias-enricher.gpt-5-5 |
| compile jobId | `2fb220b7-5afc-464a-85e9-3468fc894501` |
| persistedCount | 5（全部资料编译成功，含 PDF） |

报告不含 API key/token/password。

## 3. 计划台账回写

已回写 `terminal_unit_phase1_implementation_plan.md`：
- runtime smoke PASS（scoped alias route 验证通过）
- 本轮 clean schema e2e PARTIAL（召回改善，答案未改善）

## 4. Compile / Alias 产物验证

### 4.1 Snapshot

compile job `2fb220b7` 的 execution snapshots：

```
agent_role           | binding_id | model_name
field-alias-enricher |          4 | gpt-5.5
fixer                |          3 | gpt-5.5
reviewer             |          2 | gpt-5.5
writer               |          1 | gpt-5.5
```

`field-alias-enricher` snapshot 已冻结，binding_id=4，model_name=gpt-5.5。

### 4.2 YAML 5 题目标字段 LLM Alias 产物

| 目标字段 | valueText | LLM 生成的中文 alias |
|---|---|---|
| max_borrow_days | 7 | **最长借用天数**, **最大借用天数**, **借用期限上限** |
| deposit_amount | 100 | **押金金额**, **保证金金额**, **设备押金**, **借用押金** |
| version | v2.3.1 | **版本**, **系统版本**, **接口版本**, **版本号** |
| late_fee_per_day | 20 | **每日逾期费用**, **逾期日费用** |
| max_concurrent_requests | 50 | **最大并发请求数**, **并发请求上限**, **最大并发数**, **请求并发限制** |

**全部 5 个目标字段均生成了语义正确的中文 alias，且 ftsText 已同步更新。** LLM 未产生无关/错误 alias（如文件名、eval 题面词、答案值）。

### 4.3 XLSX/CSV 保护

XLSX/CSV 终端 unit 的 fieldAliasesJson 已有 Phase 1C Layer 1 的中文 N-gram alias（如 "存储条件"→"存储/储条/条件"）。LLM enricher 的 `shouldEnrich()` 中 `containsCjk(fieldLabel)` 检查正确跳过了这些已有 CJK 的字段——**XLSX/CSV 路径未被 LLM 改写**。

## 5. YAML 5 题逐题结果

### 5.1 Terminal Unit 排名变化

| 题目 | 目标 unit | 中文 alias | hitRank | fusedRank | Phase 1D-1 rank | 排名变化 |
|---|---|---|---|---|---|---|
| FQ3 | max_borrow_days=7 | 最长借用天数/最大借用天数/借用期限上限 | 2 | **2** | 9 (fused 6) | **+7** |
| FQ4 | deposit_amount=100 | 押金金额/保证金金额/设备押金/借用押金 | 7 | 6 | >10 | **显著改善** |
| FQ6 | version=v2.3.1 | 版本/系统版本/接口版本/版本号 | 2 | **2** | >5 | **首次进入 top 3** |
| FG1 | late_fee_per_day=20 | 每日逾期费用/逾期日费用 | 10 | 7 | >10 | **显著改善** |
| FG2 | max_concurrent_requests=50 | 最大并发请求数/并发请求上限/最大并发数/请求并发限制 | 2 | **2** | 4 (fused 4) | **+2** |

### 5.2 逐题答案

**FQ3**："精密仪器的单次最长借用天数是多少？"
- 预期：`7`
- 答案：讨论 equipment_types 分类，未直接给出 7。参考说明段为 API endpoint。
- 判定：**FAIL**（证据已召回但回答漏点 — terminal unit max_borrow_days=7 在 fused rank 2 但 fallback 未选中）
- vs 基线：持平（均 FAIL）

**FQ4**："常规设备和大型设备的押金分别是多少？"
- 预期：常规设备 `100`，大型设备 `1000`
- 答案：讨论 policy overview，未给出具体押金值。
- 判定：**FAIL**（证据已召回但回答漏点 — deposit_amount=100/1000 在 fused rank 6/8 但 fallback 未选中）
- vs 基线：持平（均 FAIL）

**FQ6**："预约系统当前的版本号是什么？"
- 预期：`v2.3.1`
- 答案：讨论 borrowing_system 基本信息（name/api_endpoint），未给出 version。
- 判定：**FAIL**（证据已召回但回答漏点 — version=v2.3.1 在 fused rank 2 但 fallback 未选中）
- vs 基线：持平（均 FAIL）

**FG1**："精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？"
- 预期：精密仪器 `20`，常规设备 `5`
- 答案：讨论 return_policy，未给出逾期费用。
- 判定：**FAIL**（证据已召回但回答漏点）
- vs 基线：持平（均 FAIL）

**FG2**："预约系统的最大并发请求数是多少？"
- 预期：`50`
- 答案：讨论 support_time + return_policy，未给出 50。
- 判定：**FAIL**（证据已召回但回答漏点 — max_concurrent_requests=50 在 fused rank 2 但 fallback 未选中）
- vs 基线：持平（均 FAIL）

### 5.3 YAML 5 题根因

**根因已从 "检索未召回" 变为 "证据已召回但回答漏点"。** 这是 Phase 1 系列验证中首次实现所有 5 个目标 terminal unit 同时进入 fused topK，但 FALLBACK 模式下的 evidence selector 仍选择整卡粒度的 fact card evidence，而非 terminal unit evidence。Terminal unit 证据虽已进入检索结果，但未被 answer/citation 层消费。

## 6. 保护场景

| 场景 | 状态 | 详情 |
|---|---|---|
| FQ7 (XLSX) | **PASS** | 16 TU hits，B 级化学品存储/保管人正确 |
| FQ11 (CSV) | **PASS** | 16 TU hits，维护等级=A fused rank 3 |
| XLSX/CSV CJK alias | **PASS** | 已有 CJK alias 未被 LLM 改写（`shouldEnrich` 跳过） |
| 已有 Phase 1C Layer 1 alias | **PASS** | XLSX/CSV 中文 N-gram alias 不变 |

## 7. 19 题 Fresh Eval 指标

### 7.1 逐题判定

| 题目 | 判定 | vs 基线 | 说明 |
|---|---|---|---|
| FQ1 | PASS | 持平 | Markdown 内容问答 |
| FQ2 | PASS | 持平 | 角色区分 |
| FQ3 | FAIL | 持平 | 证据已召回但回答漏点 |
| FQ4 | FAIL | 持平 | 证据已召回但回答漏点 |
| FQ5 | PASS | 持平 | API endpoint 正确 |
| FQ6 | FAIL | 持平 | 证据已召回但回答漏点 |
| FQ7 | PASS | 持平 | B 级化学品 |
| FQ8 | PASS | 持平 | 丙酮泄漏流程+存储 |
| FQ9 | PASS | 持平 | 正确拒答 |
| FQ10 | PASS | 持平 | PDF 步骤 |
| FQ11 | PASS | 持平 | A 级设备 |
| FQ12 | PASS | 持平 | 审批阶段 |
| FS1 | FAIL | 持平 | 搜索题 |
| FS2 | FAIL | 持平 | 搜索题 |
| FS3 | FAIL | 持平 | 搜索题 |
| FS4 | PASS | 持平 | 跨资料搜索 |
| FG1 | FAIL | 持平 | 证据已召回但回答漏点 |
| FG2 | FAIL | 持平 | 证据已召回但回答漏点 |
| FG3 | PASS | 持平 | 正确拒答 |

### 7.2 指标对比

| 指标 | 基线 (acceptance-report.md) | Phase 1E-2 | 变化 |
|---|---|---|---|
| Answer Accuracy | 10/15 (66.7%) | 10/15 (66.7%) | **持平** |
| Search Accuracy (FS1-FS4) | 1/4 (25%) | 1/4 (25%) | 持平 |
| Recall@5 | 13/15 (86.7%) | 13/15 (86.7%) | 持平 |
| Recall@10 | 13/15 (86.7%) | 13/15 (86.7%) | 持平 |
| Citation Accuracy | 2/15 (13.3%) | 2/15 (13.3%) | 持平 |
| Abstain Accuracy | 2/2 (100%) | 2/2 (100%) | 持平 |
| Hallucination Count | 5 | 5 | 持平 |

### 7.3 指标分析

表观指标与基线完全持平。但 **terminal unit 层的改善是真实的**：
- YAML 5 题目标 terminal unit 全部进入 fused topK（基线 0/5）
- 排名大幅改善（FQ3: 9→2, FQ6: >5→2, FG2: 4→2）
- LLM 中文 alias 正确生成（5/5 目标字段）

指标未改善的原因：**Answer 层未消费 terminal unit evidence**。FALLBACK 模式的 evidence selector 仍使用整卡粒度 fact card，而非单字段 terminal unit。这是 Phase 2 需要解决的问题——让 terminal unit evidence 进入 answer generation 的证据选择链路。

## 8. 失败归因

| Case | 失败类型 | 详细 |
|---|---|---|
| FQ3 | 证据已召回但回答漏点 | max_borrow_days=7 fused rank 2，但 fallback 选中整卡 overview |
| FQ4 | 证据已召回但回答漏点 | deposit_amount=100/1000 fused rank 6/8，但 fallback 选中整卡 overview |
| FQ6 | 证据已召回但回答漏点 | version=v2.3.1 fused rank 2，但 fallback 选中 borrowing_system overview |
| FG1 | 证据已召回但回答漏点 | late_fee_per_day=20 fused rank 7，但 fallback 选中 return_policy |
| FG2 | 证据已召回但回答漏点 | max_concurrent_requests=50 fused rank 2，但 fallback 选中 support_time |
| FS1 | 检索未召回 | 搜索 "校园实验室安全管理手册"，Top5 不符预期 |
| FS2 | 检索未召回 | 搜索 "化学品分类存储"，Top5 不符预期 |
| FS3 | 检索未召回 | 搜索 title，Top5 不符预期 |

**失败类型分布**：
- 证据已召回但回答漏点：5（FQ3/FQ4/FQ6/FG1/FG2）
- 检索未召回：3（FS1/FS2/FS3）

对比基线：YAML 5 题的失败类型已从 "证据已召回但回答漏点（整卡粒度导致 sibling 抢答）" 更新为 "证据已召回但回答漏点（terminal unit 已召回但 fallback 未消费）"。**根因已从 retrieval 层下移到 answer consumption 层。**

## 9. 未执行项

| 项目 | 状态 |
|---|---|
| 修改代码 | 未执行 |
| 修改测试 | 未执行 |
| 修改题集 | 未执行 |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |
| Public Eval 1 Q6/S2 保护回归 | 未执行（当前库只含 Public Eval 2 资料） |

## 10. 下一步建议

**唯一最小下一步：让 terminal unit evidence 进入 FALLBACK 模式的 evidence selector。**

当前 FALLBACK（`DETERMINISTIC_EXACT_LOOKUP_PREFERRED`）的 evidence selector 从 fused topK 中按 `evidenceType` 优先级选择证据，但 terminal unit 的 `evidenceType` 为 `FACT_CARD`（Phase 1 设计），与整卡 fact card 共享同一类型。evidence selector 无法区分 "整卡 overview fact card" 与 "单字段 terminal unit"。

建议方案（只读归因，不改代码）：
1. agentB 只读分析 `AnswerFallbackEvidenceSelector` 的 evidence 选择逻辑
2. 确认 terminal unit evidence 是否在 selector 的候选集中
3. 如果会被过滤（如 `structuredEvidence` gate），确认过滤条件和最小修复点
4. 如果已在候选集但 rank 低于整卡 fact card，确认排序规则

**不要同时修改 retrieval、reranker、Materializer、LLM enricher 或 fallback selector。** Terminal unit 召回已证明有效——问题仅在最后一个环节：answer 层如何消费这些证据。

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- 未 stage、未 commit、未 push
- 未改题集、资料包、验收口径
- 未读取 hidden eval
- 未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 报告未输出 API key、token、password
- 清库范围仅限 `ai-rag-knowledge.lattice`
- 本轮新增报告：本文件
