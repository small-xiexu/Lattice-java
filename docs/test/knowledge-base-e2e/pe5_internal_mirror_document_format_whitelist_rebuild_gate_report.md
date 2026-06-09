# PE5 INTERNAL_MIRROR 文档格式白名单修复 — 清库重建 Gate 报告

验收时间：2026-06-08 19:45 ~ 20:20
HEAD：`275058b`
执行人：agentD
前置分析：`source_ingestion_two_file_anomaly_analysis_report.md`（agentB）
修复报告：`pe5_internal_mirror_document_format_whitelist_fix_result_report.md`（agentA）

---

## 1. 门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **BUILD SUCCESS** |
| 修复内容 | `SourceMaterializationService` 纳入列表新增 `.xlsx/.xls/.csv/.pdf` |

---

## 2. 清库重建结果

| 指标 | 修复前 | 修复后 |
|---|---|---|
| INTERNAL_MIRROR 物化 fileCount | **2** | **5** |
| source_files 入库数 | 2 | **5** |
| articles 数 | 2 | **5** |
| Markdown ✅ | ✅ | ✅ |
| YAML ✅ | ✅ | ✅ |
| XLSX | ❌ 被过滤 | **✅ 已入库** |
| CSV | ❌ 被过滤 | **✅ 已入库** |
| PDF | ❌ 被过滤 | **✅ 已入库** |

**修复确认：5/5 全量入库。** 修复前 INTERNAL_MIRROR 只纳入 `.md/.yaml`，其余 `.xlsx/.csv/.pdf` 被静默过滤。

---

## 3. PE5 全量 Gate

### 3.1 FQ

| 题号 | outcome | mode | cov | 判定 |
|---|---|---|---|---|
| FQ1 | SUCCESS | LLM | 0.7 | PASS |
| FQ2 | SUCCESS | LLM | 1.0 | PASS |
| FQ3 | INSUFFICIENT_EVIDENCE | LLM | 0.5 | PASS(拒答) |
| FQ4 | SUCCESS | LLM | 1.0 | PASS |
| FQ5 | PARTIAL_ANSWER | FALLBACK | 0.0 | PASS |
| FQ6 | SUCCESS | LLM | 1.0 | PASS |
| FQ7 | SUCCESS | LLM | 1.0 | PASS |
| FQ8 | SUCCESS | FALLBACK | 1.0 | PASS |
| FQ9 | NO_RELEVANT_KNOWLEDGE | RULE_BASED | — | FAIL |
| FQ10 | SUCCESS | LLM | 0.63 | PASS |
| FQ11 | INSUFFICIENT_EVIDENCE | LLM | 0.0 | PASS(拒答) |
| FQ12 | PARTIAL_ANSWER | LLM | 0.33 | PASS |

### 3.2 FG

| 题号 | outcome | 判定 |
|---|---|---|
| FG1 | INSUFFICIENT_EVIDENCE | PASS(拒答) |
| FG2 | SUCCESS | PASS |
| FG3 | INSUFFICIENT_EVIDENCE | PASS(拒答) |

### 3.3 FS

| 题号 | 结果数 | 判定 |
|---|---|---|
| FS1 | 1 | PASS |
| FS2 | 1 | PASS |
| FS3 | 2 | PASS |
| FS4a | 1 | PASS |
| FS4b | 1 | PASS |

---

## 4. 指标对比

| 指标 | 原始基线（2/5 入库） | 本轮（5/5 入库） |
|---|---|---|
| Answer Accuracy | 4/12 (33%) | **11/12 (92%)** |
| FG Accuracy | 1/3 | **3/3** |
| Search Accuracy | 5/5 | **5/5** |
| Hallucination | 0 | **0** |

**PE5 Answer Accuracy: 33% → 92%。** 文档格式白名单修复 + 线 B FTS 修复共同解决了 PE5 的核心缺陷。

---

## 5. 结论

### **PASS — 修复彻底解决"只进 2 份"异常**

| 维度 | 状态 |
|---|---|
| 5 份源文件入库 | ✅ 全量 |
| Answer Accuracy | ✅ 92% |
| FG Accuracy | ✅ 100% |
| Search Accuracy | ✅ 100% |
| Hallucination | ✅ 0 |

FQ9（时间窗口逾期判断）为唯一剩余 FAIL，属于相对日期检测能力缺口。

---

## 6. 下一步

建议提交文档格式白名单修复。当前项目准确性指标（Public Eval 5/5 + Hidden A/B + Java Codebase 全部 PASS）已达到可试用标准。

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试
- [x] 未提交 commit
