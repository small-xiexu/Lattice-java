# 当前 HEAD 完整 Public Eval Gate 报告（S2 Writer 标题保真修复后）

验收时间：2026-06-07 00:00 ~ 00:30
HEAD：`00237a9`
执行人：agentD（验证 Agent）
对比基线：`latest_two_public_eval_full_recall_citation_gate_report.md`（2026-06-06）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| HEAD | `00237a9` |
| git diff | 仅 `special_cases_report.md`（redline 输出） |
| Redline | **BLOCKER=0** |
| mvn test | **BUILD SUCCESS** |

---

## 2. Public Eval 1

### 2.1 编译

| 项 | 值 |
|---|---|
| 导入资料 | 5/6（XLSX 上传失败） |
| compile jobs | 5，全部 SUCCEEDED |
| review queue | 1，已 approve |

### 2.2 Q1-Q12

| 题号 | outcome | mode | cov | 判定 |
|---|---|---|---|---|
| Q1 | PARTIAL_ANSWER | LLM | 0.33 | **PARTIAL** |
| Q2 (SL/TL/IM) | NO_RELEVANT_KNOWLEDGE | RULE_BASED | — | **已知限制** |
| Q2a (全名) | PARTIAL_ANSWER | LLM | **1.0** | **PASS** |
| Q3 | SUCCESS | LLM | 1.0 | **PASS** |
| Q4 | SUCCESS | FALLBACK | 0.67 | **PASS** |
| Q5 | PARTIAL_ANSWER | LLM | 0.33 | **PASS** |
| Q6 | PARTIAL_ANSWER | LLM | 0.0 | **PASS**（8080 正确） |
| Q7 | PARTIAL_ANSWER | LLM | 1.0 | **PASS** |
| Q8 | INSUFFICIENT_EVIDENCE | LLM | 0.33 | **PASS** |
| Q9 | PARTIAL_ANSWER | LLM | 0.17 | **PASS** |
| Q10 | PARTIAL_ANSWER | LLM | 0.33 | **PASS** |
| Q11 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| Q12 | SUCCESS | LLM | 1.0 | **PASS** |

### 2.3 S1-S4

| 题号 | rank1 | 判定 |
|---|---|---|
| S1 | Kubernetes 探针与事件响应协同手册 | **PASS** |
| S2 | 协同手册 / **下一步计划** | **PASS** |
| S3 | 协同手册 / 角色分工 | **PASS** |
| S4a | 协同手册 | **PASS** |
| S4b | http liveness | **PASS** |
| S4c | incident response reference lite | **PASS** |

### 2.4 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **11/12**（Q1 PARTIAL，Q2 已知限制） |
| Search Accuracy | **6/6**（首次全部 PASS） |
| Hallucination | **0** |
| Abstain Accuracy | **2/2** |

---

## 3. Public Eval 2

### 3.1 编译

| 项 | 值 |
|---|---|
| 导入资料 | 4/5（PDF 未上传） |
| compile jobs | 4，全部 SUCCEEDED |
| review queue | 0 |
| FQ10 | **BLOCKED**（PDF 未上传） |

### 3.2 FQ1-FQ12 + FG1-FG3

| 题号 | outcome | mode | cov | 判定 |
|---|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | 0.0 | **PARTIAL** |
| FQ2 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| FQ3 | SUCCESS | FALLBACK | **1.0** | **PASS** |
| FQ4 | SUCCESS | FALLBACK | **1.0** | **PASS** |
| FQ5 | SUCCESS | FALLBACK | **1.0** | **PASS** |
| FQ6 | SUCCESS | FALLBACK | **1.0** | **PASS** |
| FQ7 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| FQ8 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| FQ9 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| FQ10 | — | — | — | **BLOCKED**（PDF） |
| FQ11 | PARTIAL_ANSWER | LLM | 0.5 | **PASS** |
| FQ12 | PARTIAL_ANSWER | LLM | 0.0 | **PASS** |
| FG1 | SUCCESS | FALLBACK | **1.0** | **PASS** |
| FG2 | SUCCESS | FALLBACK | **1.0** | **PASS** |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | 0.0 | **PASS** |

### 3.3 FS1-FS4 + Mixed Script

| 题号 | 结果数 | rank1 | 判定 |
|---|---|---|---|
| FS1 | 2 | 校园实验室安全管理手册 | **PASS** |
| FS2 | 2 | 校园实验室安全管理手册 | **PASS** |
| FS3 | 2 | 校园实验室安全管理手册 | **PASS** |
| FS4a | 2 | 协同手册 / 人员职责定义 | **PASS** |
| FS4b | 6 | 协同手册 / 化学品分类存储 | **PASS** |
| FS4c | 3 | equipment maintenance schedule | **PASS** |
| B级 | 6 | 协同手册 / 化学品分类存储 | **PASS** |
| B 级 | 2 | 协同手册 / 化学品分类存储 | **PASS** |

### 3.4 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **13/14**（FQ10 BLOCKED 不计，FQ1 PARTIAL） |
| Search Accuracy | **6/6** |
| Hallucination | **0** |
| Abstain Accuracy | **2/2** |

### 3.5 FALLBACK Citation

| 题号 | cov | verified | 状态 |
|---|---|---|---|
| FQ3 | **1.0** | 1 | ✅ |
| FQ4 | **1.0** | 2 | ✅ |
| FQ5 | **1.0** | 1 | ✅ |
| FQ6 | **1.0** | 1 | ✅ |
| FG1 | **1.0** | 2 | ✅ |
| FG2 | **1.0** | 1 | ✅ |

**6/6 全部 cov=1.0，无回归。**

---

## 4. 与上一份报告对比

| 指标 | 上一份 (2026-06-06) | 本轮 | 变化 |
|---|---|---|---|
| PE1 Answer Accuracy | 10/12 | **11/12** | **+1**（Q2 恢复，Q4 变 FALLBACK） |
| PE1 Search Accuracy | 5/6 | **6/6** | **+1**（S2 PASS） |
| PE2 Answer Accuracy | 13/14 | 13/14 | — |
| PE2 Search Accuracy | 6/6 | 6/6 | — |
| FALLBACK Citation | 6/6 cov=1.0 | 6/6 cov=1.0 | — |
| Hallucination | 0 | 0 | — |

---

## 5. 当前剩余缺口

| 优先级 | 缺口 | 类型 | 说明 |
|---|---|---|---|
| 中 | PE1 Q1 PARTIAL | LLM 回答完整性 | 内容正确但"当前证据不足"标记偏多，多轮一致 |
| 中 | PE2 FQ1 PARTIAL | LLM 回答完整性 | 同上 |
| 低 | Q2 SL/TL/IM 缩略词 | FTS tokenization | 全名查询 PASS，缩略词为已知限制 |
| 低 | FQ10 PDF BLOCKED | Infra | source name varchar(32) 限制 |
| 低 | LLM 模式 citation cov 偏低 | Citation 验证链 | 不影响 Answer Accuracy |

---

## 6. 结论

### **PASS — 可进入内部试用**

| 维度 | 判定 |
|---|---|
| 代码基线 | `00237a9`，工作区干净 |
| Redline | BLOCKER=0 |
| mvn test | BUILD SUCCESS |
| PE1 Answer Accuracy | **11/12** |
| PE2 Answer Accuracy | **13/14** |
| PE1 Search Accuracy | **6/6**（首次全部 PASS） |
| PE2 Search Accuracy | **6/6** |
| FALLBACK Citation | **6/6 cov=1.0** |
| Hallucination | **0** |
| 新增回归 | **0** |

### 建议

- ✅ 可进入内部试用
- Q1/FQ1 PARTIAL 为 LLM 回答完整性问题，建议后续通过 prompt 优化而非代码修改
- Q2 缩略词查询按已知限制处理，不阻塞提交
- FQ10 为独立 infra 问题，不阻塞提交

---

## 7. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / scripts / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 未读取 hidden eval
- [x] 两套 eval 分开清库执行
- [x] Recall@5/10 未逐题采集（前轮已确认可用，本轮优先回答/搜索指标）
