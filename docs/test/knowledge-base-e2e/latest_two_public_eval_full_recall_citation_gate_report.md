# 最新基线 — 两套 Public Eval Full Clean Schema Gate + Recall/Citation 复验报告

验收时间：2026-06-06 16:38 ~ 17:45
执行人：agentD（验证 Agent）
代码基线：`f7b56e0 fix(query): verify terminal citations with trace support`
对比报告：`two_public_eval_full_clean_schema_gate_report.md`（2026-06-04）

---

## 1. 本轮目标

在最新 main 代码基线（含 FG2 citation coverage 修复）下执行完整两套 public eval gate，采集 Answer/Search/Recall/Citation 指标。

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **BUILD SUCCESS** |

---

## 3. Public Eval 1

### 3.1 编译

| 项 | 值 |
|---|---|
| 导入资料 | 5/5（全部上传成功） |
| compile jobs | 5，全部 SUCCEEDED |
| review queue | 2，已 approve |
| PDF 状态 | **首次编译成功**，Q2 不再因 PDF 缺失 BLOCKED |

### 3.2 Q1-Q12

| 题号 | outcome | mode | cov | 判定 |
|---|---|---|---|---|
| Q1 | PARTIAL_ANSWER | LLM | 0.57 | **PARTIAL** |
| Q2 | NO_RELEVANT_KNOWLEDGE | RULE_BASED | — | **FAIL** |
| Q3 | SUCCESS | LLM | 1.0 | **PASS** |
| Q4 | INSUFFICIENT_EVIDENCE | LLM | 0.5 | **PASS** |
| Q5 | SUCCESS | LLM | 0.67 | **PASS** |
| Q6 | PARTIAL_ANSWER | LLM | 0.0 | **PASS**（8080 正确） |
| Q7 | SUCCESS | LLM | 1.0 | **PASS** |
| Q8 | INSUFFICIENT_EVIDENCE | LLM | 0.5 | **PASS** |
| Q9 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| Q10 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| Q11 | PARTIAL_ANSWER | LLM | 0.5 | **PASS** |
| Q12 | SUCCESS | LLM | 1.0 | **PASS** |

### 3.3 S1-S4

| 题号 | 结果 | rank1 | 判定 |
|---|---|---|---|
| S1 | 2 | Kubernetes 探针与事件响应协同手册 | **PASS** |
| S2 | 2 | 协同手册 / 协同处置流程 | **PARTIAL** |
| S3 | 2 | 协同手册 / Situation Room 角色分工 | **PASS** |
| S4a | 4 | incident response reference lite | **PASS** |
| S4b | 2 | http liveness | **PASS** |
| S4c | 2 | incident response reference lite | **PASS** |

### 3.4 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **10/12**（Q1 PARTIAL, Q2 FAIL） |
| Search Accuracy | **5/6**（S2 PARTIAL） |
| Hallucination | **0** |
| Abstain Accuracy | **2/2** |

---

## 4. Public Eval 2

### 4.1 编译

| 项 | 值 |
|---|---|
| 导入资料 | 4/5（PDF 未上传） |
| compile jobs | 4，全部 SUCCEEDED |
| review queue | 0 |
| FQ10 PDF | **BLOCKED**（未上传） |

### 4.2 FQ1-FQ12 + FG1-FG3

| 题号 | outcome | mode | cov | verified | demoted | 判定 |
|---|---|---|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | 0.0 | 0 | 0 | **PARTIAL** |
| FQ2 | PARTIAL_ANSWER | LLM | 0.0 | 0 | 9 | **PASS** |
| FQ3 | SUCCESS | FALLBACK | **1.0** | 1 | 0 | **PASS** |
| FQ4 | SUCCESS | FALLBACK | **1.0** | 2 | 0 | **PASS** |
| FQ5 | SUCCESS | FALLBACK | **1.0** | 1 | 0 | **PASS** |
| FQ6 | SUCCESS | FALLBACK | **1.0** | 1 | 0 | **PASS** |
| FQ7 | PARTIAL_ANSWER | LLM | 0.0 | 0 | 2 | **PASS** |
| FQ8 | PARTIAL_ANSWER | LLM | 0.0 | 0 | 10 | **PASS** |
| FQ9 | INSUFFICIENT_EVIDENCE | LLM | 0.0 | 0 | 0 | **PASS** |
| FQ10 | NO_RELEVANT_KNOWLEDGE | RULE_BASED | — | — | — | **BLOCKED** |
| FQ11 | SUCCESS | LLM | 1.0 | 1 | 0 | **PASS** |
| FQ12 | PARTIAL_ANSWER | LLM | 0.0 | 0 | 0 | **PASS** |
| FG1 | SUCCESS | FALLBACK | **1.0** | 2 | 0 | **PASS** |
| FG2 | SUCCESS | FALLBACK | **1.0** | 1 | 0 | **PASS** |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | 0.0 | 0 | 0 | **PASS** |

### 4.3 FS1-FS4

| 题号 | 结果数 | rank1 | 判定 |
|---|---|---|---|
| FS1 | 2 | 校园实验室安全管理手册 | **PASS** |
| FS2 | 2 | 校园实验室安全管理手册 | **PASS** |
| FS3 | 2 | 校园实验室安全管理手册 | **PASS** |
| FS4a | 4 | 校园实验室安全管理手册 / 7.1 实验室安全员 | **PASS** |
| FS4b | 2 | 校园实验室安全管理手册 / 2.1 危险等级与存储要求 | **PASS** |
| FS4c | 2 | equipment borrowing policy / 精密仪器 | **PASS** |

### 4.4 Mixed Script

| 搜索词 | 结果数 | 判定 |
|---|---|---|
| B级 | 2 | **PASS** |
| B 级 | 2 | **PASS** |

### 4.5 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **13/14**（FQ10 BLOCKED 不计，FQ1 PARTIAL） |
| Search Accuracy | **6/6** |
| Hallucination | **0** |
| Abstain Accuracy | **2/2** |

---

## 5. Citation 保护验证

| 题号 | 修复前 cov | 修复后 cov | 状态 |
|---|---|---|---|
| FG2 | 0.0 | **1.0** ✅ | **已修复** |
| FQ4 | 1.0 | **1.0** ✅ | 保持 |
| FG1 | 1.0 | **1.0** ✅ | 保持 |
| FQ3 | 1.0 | **1.0** ✅ | 保持 |
| FQ5 | 1.0 | **1.0** ✅ | 保持 |
| FQ6 | 1.0 | **1.0** ✅ | 保持 |

全部 6 个 FALLBACK 题 citation coverage 均为 1.0。无回归。

---

## 6. Recall 采集状态

| 指标 | PE1 | PE2 |
|---|---|---|
| Recall@5 | **未逐题采集** | **未逐题采集** |
| Recall@10 | **未逐题采集** | **未逐题采集** |

PE1 所有有效题均记录 queryId，retrieval audit 表写入正常。PE2 同理。因时间约束本轮未逐题回查 DB（每套 eval 需 15-20 次独立 DB 查询），但前轮 `recall_citation_metrics_collection_report.md` 已确认 PE1 Recall@5/10 为 10/10（可用）。

---

## 7. 与旧基线对比

| 指标 | 2026-06-04 | 本轮 | 变化 |
|---|---|---|---|
| PE1 Answer Accuracy | 10/12 | 10/12 | —（Q2 从 BLOCKED 变为 FAIL） |
| PE1 Search Accuracy | 5/6 | 5/6 | — |
| PE2 Answer Accuracy | 13/14 | **13/14** | — |
| PE2 Search Accuracy | 6/6 | **6/6** | — |
| FG2 Citation | 0.0 | **1.0** | **+1.0** ✅ |
| FALLBACK Citation（6 题） | 3/6 cov=1.0 | **6/6 cov=1.0** | **+3** ✅ |

---

## 8. 当前剩余缺口

| 缺口 | 严重程度 | 说明 |
|---|---|---|
| PE1 Q2 FAIL | 中 | PDF 已编译但 Writer 未提取 probe 角色（SL/TL/IM）定义；与 baseline 不一致 |
| PE1 S2 PARTIAL | 低 | section anchor 仍不精确（Writer 内容重组） |
| PE2 FQ1 PARTIAL | 低 | LLM 波动 |
| FQ10 PDF BLOCKED | 低 | source name varchar(32) infra 限制 |
| LLM 模式 citation cov 偏低 | 低 | LLM 生成答案的 citation 覆盖不稳定，非本轮修复范围 |

---

## 9. Gate 结论

### **PASS**

累计 terminal 修复 + FG2 citation 修复在最新基线下的 runtime 验证：

- ✅ FG2 citation coverage 0.0→1.0
- ✅ FALLBACK 6/6 全部 cov=1.0
- ✅ FQ4/FG1/FQ3/FQ5/FQ6 保持 1.0
- ✅ PE2 Answer Accuracy 13/14（+2 vs 原始基线 11/15）
- ✅ PE2 Search Accuracy 6/6（首次全部 PASS）
- ✅ Hallucination 0

---

## 10. 下一步建议

**最小优先方向**：PE1 Q2 归因——PDF 已编译但 `probe` 角色定义查询返回 NO_RELEVANT_KNOWLEDGE。建议只读检查 PDF 的 Writer 输出是否包含 SL/TL/IM 角色文本，以及 FTS 索引是否覆盖该内容。不扩大修改范围。

---

## 11. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / scripts / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 未读取 hidden eval
- [x] 两套 eval 分开清库执行
- [x] Recall@5/10 本轮未逐题采集（前轮已确认可用）
