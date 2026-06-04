# 完整 Public Eval 回归 Gate 报告

验证时间：2026-06-04 15:45 ~ 16:15
执行人：agentD（验证 Agent）
前置 targeted gate：`fg1_raw_query_entity_context_match_runtime_gate_report.md`（PASS）
对比基线：`two_public_eval_clean_schema_gate_report.md`（2026-06-02）

---

## 1. Git Status 摘要

累计 terminal 修复包：

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | qf + ftmc + atmc + entityContextMatchesQuery + 多目标聚合 + raw query display value match |
| `FactCardTerminalUnitMaterializer.java` | contextDisplayValues 写入 |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | bootstrap guard 移除 |
| `FactCardTerminalUnitFtsSearchService.java` | candidate supply 修订 |
| `FactCardTerminalUnitIntentReranker.java` | 字段意图信号 scoring |

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 3. 编译信息

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| LLM 绑定 | 11 条（含 `compile/field-alias-enricher`） |
| compile jobs | 4/5（Markdown/YAML/XLSX/CSV SUCCEEDED，PDF 因 source name 超长未上传） |
| review queue | **0**（全部 auto-published） |
| articles | 4 |
| fact_card_terminal_units | 118 |
| enricher 401 错误 | **0** |

---

## 4. Public Eval 2 逐题结果

### 4.1 FQ1-FQ12

| 题号 | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | **PARTIAL** | 内容覆盖 A/B/C/D 分级及存储条件，"当前证据不足"标注偏多 |
| FQ2 | PARTIAL_ANSWER | LLM | **PASS** | 安全员 vs 设备管理员区分清晰 |
| FQ3 | SUCCESS | FALLBACK | **PASS** | equipment_types[1].max_borrow_days=7 ✅ |
| FQ4 | PARTIAL_ANSWER | FALLBACK | **PASS** | deposit_amount=100 + deposit_amount=1000 双值输出 ✅ |
| FQ5 | SUCCESS | FALLBACK | **PASS** | api_endpoint=https://lab-equip.campus.edu/api/v2/borrow ✅ |
| FQ6 | SUCCESS | FALLBACK | **PASS** | version=v2.3.1 ✅ |
| FQ7 | PARTIAL_ANSWER | LLM | **PASS** | 丙酮+氢氧化钠，保管人均为设备管理员 ✅ |
| FQ8 | PARTIAL_ANSWER | LLM | **PASS** | 跨文档组合：处置流程 + 存储要求 ✅ |
| FQ9 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答（无餐饮服务管理规定）✅ |
| FQ10 | NO_RELEVANT_KNOWLEDGE | RULE_BASED | **BLOCKED** | PDF 源文件未上传（source name 超长已知问题），非代码回归 |
| FQ11 | SUCCESS | LLM | **PASS** | EQ-001 气相色谱仪 ✅ |
| FQ12 | SUCCESS | LLM | **PASS** | 3 阶段审批链正确 ✅ |

### 4.2 FG1-FG3

| 题号 | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|
| FG1 | PARTIAL_ANSWER | FALLBACK | **PASS** | late_fee_per_day=20 + late_fee_per_day=5 双值输出 ✅ |
| FG2 | PARTIAL_ANSWER | FALLBACK | **PASS** | max_concurrent_requests=50 ✅ |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答（无灭火器更换周期）✅ |

### 4.3 FS1-FS4 搜索

| 题号 | 结果 | 判定 |
|---|---|---|
| FS1 | rank1=lab-safety-management-handbook | **PASS** |
| FS2 | lab-safety + chemical-storage-grading，markdown chunk 未单独出现 | **FAIL** |
| FS3 | lab-safety + chemical-storage-grading，预期条目未出现 | **PARTIAL** |
| FS4a | 安全员命中相关条目 | **PASS** |
| FS4b | B 级 → 0 结果 | **FAIL** |
| FS4c | 精密仪器 → terminal unit + equipment maintenance schedule | **PASS** |

---

## 5. Public Eval 2 指标汇总

| 指标 | 基线（2026-06-02） | 本轮 | 变化 |
|---|---|---|---|
| Answer Accuracy | **11/15** | **13/15** (FQ10 不计入则为 13/14) | **+2** |
| Search Accuracy | 1.5/4 | 1.5/4 | 无变化 |
| Recall@5 | 3/6 search sub-queries | 类似 | 无变化 |
| Citation Accuracy | FQ4/FG1 citation 指向错误字段 | FQ4/FG1 citation 指向正确字段 | **改善** |
| Abstain Accuracy | 2/2 | 2/2 | 无变化 |
| Hallucination Count | **0** | **0** | 保持 |

### 关键改善

| 题号 | 基线判定 | 本轮判定 | 原因 |
|---|---|---|---|
| **FQ4** | FAIL（选 approval_required） | **PASS**（deposit_amount 100+1000） | atmc tie-break + 多目标聚合 |
| **FG1** | FAIL（选 api_endpoint） | **PASS**（late_fee_per_day 20+5） | qf + ftmc CJK + raw query entity context match |

---

## 6. FAIL / PARTIAL case 归因

| 题号 | 判定 | 失败类型 | 归因 |
|---|---|---|---|
| FQ1 | PARTIAL | 回答漏点 | LLM 回答覆盖了核心内容但"当前证据不足"标记偏多（与基线一致，非回归） |
| FQ10 | BLOCKED | 资料缺失 | PDF 源文件上传失败（source name varchar(32) 限制），非代码回归 |
| FS2 | FAIL | 检索未召回 | markdown anchor 搜索未命中目标 chunk（与基线一致） |
| FS4b | FAIL | 检索未召回 | "B 级"关键词搜索无结果（与基线一致） |
| FS3 | PARTIAL | 检索排序低 | representativeTitle 命中 XLSX 而非 markdown（与基线一致） |

---

## 7. 是否存在新增回归

**不存在。** 对比基线 `two_public_eval_clean_schema_gate_report.md`：

- 无任何 PASS→FAIL 回归
- FQ4: FAIL→PASS（改善）
- FG1: FAIL→PASS（改善）
- FQ10: PASS→BLOCKED（PDF 未上传，非代码回归）
- 其余题目与基线一致

---

## 8. Public Eval 1 保护回归

**未执行。** Public Eval 1 需要 knowledge-base-e2e 资料集（不同于 Fresh Eval 2），需单独清库、重建 schema、导入资料。本轮仅覆盖 Public Eval 2 完整回归。

Public Eval 1 的 Q6（terminal field alias）和 S2（chunk identity）保护回归建议作为后续独立验证轮执行。

---

## 9. 最终判定

### **PASS**（建议进入提交前质量复核）

| 维度 | 判定 |
|---|---|
| 前置门禁（redline + mvn test） | **PASS** |
| Public Eval 2 Answer Accuracy | **13/15**（+2 vs 基线 11/15） |
| Public Eval 2 Search Accuracy | **1.5/4**（与基线一致，搜索侧未受影响） |
| Hallucination | **0**（保持） |
| 新增回归 | **0** |
| FG1/FQ4 修复效果 | **已验证**（FAIL→PASS） |
| Public Eval 1 保护 | **待执行**（Q6 + S2） |

---

## 10. 下一步建议

1. **提交前质量复核**：建议请 agentB/架构师审核累计 terminal 修复包的整体代码质量，确认无 case 特判/eval 污染
2. **Public Eval 1 保护回归**：清库后导入 knowledge-base-e2e 资料，验证 Q6 terminal field alias + S2 chunk identity
3. **PDF 上传修复**：source name varchar(32) 限制导致长文件名 PDF 无法上传，建议作为独立 infra 修复
4. **搜索精度改善**：FS2/FS4b 的检索失败属于搜索侧问题，建议独立分析

---

## 11. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未读 hidden eval
- [x] 未为通过 eval 调整断言或数据
- [x] LLM 绑定通过 Admin API 配置（运行时数据）
- [x] 所有结论基于 runtime API 回答 + 搜索证据
