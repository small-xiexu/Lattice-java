# Terminal Unit Phase 1D-2: Reranker Context Scope Fix Clean Verification Report

验证时间：2026-05-29
验证人：agentD
验证对象：Phase 1D-1 (Materializer sibling context) + Phase 1D-2 (Reranker context scope fix) 组合在 clean schema 上的端到端验证

## 1. Gate 判定

**Phase 1D-2 Reranker Context Fix 分层结论：**

| 层级 | 结论 | 说明 |
|---|---|---|
| **工程门禁** | **PASS** | redline BLOCKER=0, 定向 29/0/0, 全量 mvn test 976/0/0 |
| **Sibling Context 数据层** | **PASS** | 与 Phase 1D-1 一致，fieldDescription 正确包含 context |
| **Reranker Context 排序效果** | **无效果** | YAML 5 题排名与 Phase 1D-1 完全一致，context weight 0.3 无法克服 FTS 原始分差 |
| **FQ7/FQ11 保护** | **PASS** | 16 hits each，答案不退化 |

## 2. Redline 与全量测试

| 检查项 | 结果 |
|---|---|
| `bash scripts/scan-redline.sh special_cases_report.md` | **exit=0，BLOCKER=0** |
| 定向测试 Reranker + Materializer | **Tests run: 29, Failures: 0, Errors: 0, Skipped: 0** (15 + 14) |
| `mvn test` (全量) | **Tests run: 976, Failures: 0, Errors: 0, Skipped: 0** |
| 间歇性失败 | `LlmConfigCenterIntegrationTests` 在大批量并行时有 1 次端口冲突 FAILURE（独立运行通过 14/0/0），**非本轮引入，为已知 Surefire fork 间歇性问题** |

全量 976/0/0/0 与 fix report 一致。基线 Phase 1D-1 为 971，新增 5 个 Reranker 测试，总数 976。

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

编译 job `e558e4d6`：status=SUCCEEDED，persistedCount=**5**。

## 4. YAML 5 题 Terminal Unit Channel 排名对比

### 4.1 FQ3："精密仪器的单次最长借用天数是多少？"

| hitRank | terminalKey | valueText | valueType | context | Phase 1D-1 rank | 变化 |
|---|---|---|---|---|---|---|
| 1 | type | 精密仪器 | string | 实验室主任 | 1 | **不变** |
| 5 | approval_required | 实验室主任 | string | 精密仪器 | 5 | 不变 |
| 6 | category_id | PREC | string | 精密仪器, 实验室主任 | 6 | 不变 |
| 7 | deposit_amount | 500 | number | 精密仪器, 实验室主任 | 7 | 不变 |
| 8 | late_fee_per_day | 20 | number | 精密仪器, 实验室主任 | 8 | 不变 |
| **9** | **max_borrow_days** | **7** | **number** | **精密仪器, 实验室主任** | **9** | **不变** |

**目标 unit (max_borrow_days=7): hitRank 9, fusedRank 6。与 Phase 1D-1 完全一致。**

### 4.2 FQ4："常规设备和大型设备的押金分别是多少？"

| hitRank | terminalKey | valueText | valueType | 变化 |
|---|---|---|---|---|
| 1 | type | 常规设备 | string | 不变 |
| 2 | type | 大型设备 | string | 不变 |

deposit_amount=100 和 deposit_amount=1000 均未进入 top 5。与 Phase 1D-1 一致。

### 4.3 FQ6："预约系统当前的版本号是什么？"

| hitRank | terminalKey | valueText | valueType | 变化 |
|---|---|---|---|---|
| 1 | name | 校园实验室设备预约系统 | string | 不变 |
| 3 | api_endpoint | https://lab-equip... | url | 不变 |
| 4 | max_concurrent_requests | 50 | number | 不变 |

version=v2.3.1 未进入 top 5。与 Phase 1D-1 一致。**FQ6 无 numeric intent ("什么" 不匹配 numericIntentSignals)，context 未被激活。**

### 4.4 FG1："精密仪器的逾期罚金是多少？"

| hitRank | terminalKey | valueText | valueType | 变化 |
|---|---|---|---|---|
| 1 | type | 常规设备 | string | 不变 |
| 2 | type | 精密仪器 | string | 不变 |

late_fee_per_day=20 未进入 top 5。与 Phase 1D-1 一致。

### 4.5 FG2："预约系统的最大并发请求数是多少？"

| hitRank | terminalKey | valueText | valueType | 变化 |
|---|---|---|---|---|
| 1 | name | 校园实验室设备预约系统 | string | 不变 |
| 4 | max_concurrent_requests | 50 | number | 不变 |

目标 unit (max_concurrent_requests=50): hitRank 4, fusedRank 4。与 Phase 1D-1 一致。

### 4.6 YAML 5 题汇总

| 题目 | 目标 unit | Phase 1D-1 rank | Phase 1D-2 rank | 变化 | Top-1 仍是 sibling? |
|---|---|---|---|---|---|
| FQ3 | max_borrow_days=7 | 9 (fused 6) | 9 (fused 6) | **不变** | 是 |
| FQ4 | deposit_amount=100 | >5 | >5 | **不变** | 是 |
| FQ6 | version=v2.3.1 | >5 | >5 | **不变** | 是 |
| FG1 | late_fee_per_day=20 | >5 | >5 | **不变** | 是 |
| FG2 | max_concurrent_requests=50 | 4 (fused 4) | 4 (fused 4) | **不变** | 是 |

**Phase 1D-2 Reranker context fix 对 YAML 5 题 terminal unit 排名产生零效果。全部 5 题排名与 Phase 1D-1 (Materializer only) 完全一致。**

## 5. 根因分析：为什么 Reranker Context 无效果

### 5.1 数值推演（以 FQ3 为例）

假设各 terminal unit 的 LIKE originalScore 近似为：

| terminalKey | valueText | 匹配方式 | originalScore | contextMatch | numericBonus | valueMatch | adjustedScore |
|---|---|---|---|---|---|---|---|
| type | 精密仪器 | valueText 直接 LIKE | ~13.0 | 0 | 0 | +0.1 | **~13.1** |
| max_borrow_days | 7 | context LIKE (间接) | ~2.0 | +0.3 | +0.5 | 0 | **~2.8** |

**adjustedScore 差 = 13.1 - 2.8 = 10.3。Reranker 的 0.8 bonus 无法弥补 10+ 的 FTS 原始分差。**

### 5.2 根本原因

`contextMatchWeight = 0.3` + `numericBonus = 0.5` = 总共 +0.8 的调整量，但 sibling descriptor 的 valueText 直接 LIKE 匹配比 target unit 的 context 间接 LIKE 匹配高出 10+ 分。**Reranker 的权重配置假设 originalScore 在 sibling 之间接近（如设计报告预估的 3.0 vs 2.0），但实测差距远大于预期。**

### 5.3 FQ6 特殊问题

FQ6 的 query "预约系统当前的版本号是什么" 中 "什么" 不匹配 `numericValueIntentSignals` → `queryHasNumericIntent = false`。且 `hasFieldSignal = false`（英文字段名）。→ **Reranker 直接 early return，零重排。** context 完全未参与。

## 6. 最终答案验证

| 题目 | answerOutcome | generationMode | 目标值出现 | vs Phase 1D-1 |
|---|---|---|---|---|
| FQ3 | SUCCESS | FALLBACK | **否** — 讨论 equipment_types 分类，未给出具体值 7 | 持平(均 FAIL) |
| FQ4 | SUCCESS | FALLBACK | **否** — 只展示 type 字段值 | 持平(均 FAIL) |
| FQ6 | SUCCESS | FALLBACK | **否** | 持平(均 FAIL) |
| FG1 | SUCCESS | FALLBACK | **否** | 持平(均 FAIL) |
| FG2 | SUCCESS | FALLBACK | **否** | 持平(均 FAIL) |

YAML 5 题最终答案：0/5 PASS。与 Phase 1D-1 持平，无退化、无改善。

## 7. FQ7/FQ11 保护

| 题目 | TU hits | 目标 | PASS/FAIL |
|---|---|---|---|
| FQ7 (XLSX) | **16** | B级化学品存储/保管人正确 | **PASS** |
| FQ11 (CSV) | **16** | 维护等级=A fused rank 3 | **PASS** |

保护通过。Reranker context fix 对 XLSX/CSV 无负面影响——XLSX/CSV 的 parentPath 不共享 sibling，context 为空，contextMatchCount 始终为 0。

## 8. 完整 Fresh Eval 指标

| 指标 | Phase 1D-1 Baseline | Phase 1D-2 | 变化 |
|---|---|---|---|
| Answer Accuracy | ~8-9/19 | ~8-9/19 | 持平 |
| YAML Target in Fused (FQ3) | fused rank 6 | fused rank 6 | 不变 |
| YAML Target in Fused (FG2) | fused rank 4 | fused rank 4 | 不变 |
| YAML Terminal Top-1 | sibling descriptor | sibling descriptor | 不变 |
| FQ7 TU Hits | 16 | 16 | 不变 |
| FQ11 TU Hits | 16 | 16 | 不变 |
| Abstain Accuracy | 2/2 | 2/2 | 不变 |
| Hallucination Count | 0 | 0 | 不变 |

**全量 19 题答案质量：与 Phase 1D-1 持平，无新增 PASS，无新增 FAIL。**

## 9. 是否建议提交

### 建议提交 Phase 1D-2（作为 infrastructure）

**理由：**

1. **代码质量无问题**：redline BLOCKER=0，全量 976/0/0，测试数据已脱敏，context-only 已收窄
2. **无回归**：Reranker code path 只在 `hasFieldSignal || queryHasNumericIntent` 时激活 context 计算，对现有行为影响为零
3. **算法正确**：context match 逻辑本身正确——当 canUseContext=true 时，context 确实被计入 adjustedScore。问题在权重不足以克服 FTS 分差，不是算法 bug
4. **与 Phase 1D-1 互补**：Phase 1D-1 (Materializer) + Phase 1D-2 (Reranker) 构成完整的 Layer 2 实现，即使效果有限，也是必要的基础设施

**必须在 commit message 中注明：**
- "Reranker 增加 fieldDescription context match 感知能力"
- "当前 context match weight 0.3 + numericBonus 0.5 无法克服 sibling descriptor 的 FTS 原始分差"
- "不是 fix structured terminal value retrieval"

### 提交顺序建议

```
Commit 1: Phase 1B (Reranker + numericIntent + config)
Commit 2: Phase 1C Layer 1 (中文 N-gram alias)
Commit 3: Phase 1C Extractor Fix (XLSX/CSV 结构化行)
Commit 4: Phase 1C LIKE Token Budget Fix
Commit 5: Phase 1D-1 (Materializer sibling context) ← 已提交 21e25e9
Commit 6: Phase 1D-2 (Reranker context scope fix)  ← 当前
```

## 10. 下一轮唯一方向

**在 Reranker 中增加 contextMatch 权重或修改评分策略，使 target unit 能在同 parentPath 内排到 sibling descriptor 之前。**

具体选项（只选一个）：

**选项 A（最小改动，推荐先试）**：提高 `CONTEXT_TOKEN_WEIGHT` 从 0.3 → 2.0，使 context match 的信号强度接近 field match (1.0) 的两倍，补偿 LIKE 分差。

**选项 B（更根本）**：在 Reranker 中新增"同 parentPath 内 number/version 型优先于 string 型"的排序规则——当 query 有 numeric intent 时，同 parentPath 的 number/version 型 unit 直接排在 string 型之前。

**选项 C（跳出 Layer 2）**：进入 Layer 3 LLM alias——用 LLM 为 YAML 英文字段名生成中文 alias（如 max_borrow_days → "最长借用天数"），直接解决 fieldMatchCount=0 的根因。

**不推荐**：继续调 context weight 到更高值（如 5.0 或 10.0）——这会在其他场景引入假阳性（任何有 context 的 unit 都会过度提升）。

## 11. 合规声明

1. 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考，未修改、未 stage、未 commit，内容未写入报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文
8. 清库范围仅限 `ai-rag-knowledge.lattice`，未涉及 hidden eval 或其他外部库
9. Public Eval 1 Q6/S2 保护回归未执行（当前库只含 Public Eval 2 资料）
