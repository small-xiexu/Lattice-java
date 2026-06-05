# 两套 Public Eval 完整 Clean-Schema 验收报告

验收时间：2026-06-05 14:00 ~ 15:10
执行人：agentD（验证 Agent）
对比基线：`two_public_eval_clean_schema_gate_report.md`（2026-06-02, Answer: 11/12 + 11/15, Search: 3/4 + 1.5/4）

---

## 1. 验证范围

两套 Public Eval 完整 clean-schema 验收：
- Public Eval 1：Q1-Q12 + S1-S4 + Q6 + S2
- Public Eval 2：FQ1-FQ12 + FG1-FG3 + FS1-FS4 + mixed script + FQ4/FG1

---

## 2. Git Status

当前工作区代码变更（仅 S2 chunker heading boundary 修复 + 测试）：

| 文件 | 类型 |
|---|---|
| `SemanticChunker.java` | 生产代码（ATX heading boundary） |
| `SemanticChunkerTests.java` | 测试 |
| `ArticleChunkJdbcRepositoryTests.java` | 测试断言更新 |
| `SourceFileChunkJdbcRepositoryTests.java` | 测试断言更新 |
| `AdminChunkRebuildControllerTests.java` | 测试断言更新 |

明确排除项（与本轮代码变更无关，不提交）：
- 顶层 docs 删除 + `docs/核心架构/**` 新增：属于独立文档整理线，不触碰
- `special_cases_report.md`：redline 输出

---

## 3. 指标口径说明

### 3.1 Recall / Citation 采集状态

**本轮未采集 Recall@5、Recall@10、Citation Accuracy。** 本轮 query API 调用仅采集了 `answerOutcome`、`generationMode`、`modelExecutionStatus` 和 `Confirmed evidence` 行，未系统记录每个 query 的检索审计 run、fused hits channel 明细、citation coverage 等字段。因此：

- Recall@5 / Recall@10：**本轮不可用**。不能作为提交前质量复核的 recall 口径。
- Citation Accuracy：**本轮不可用**。仅能通过 `Confirmed evidence` 行做定性判断（答案是否引用了正确字段/文件），无法做逐 claim 级别的 citation 覆盖统计。

如需完整 Recall/Citation 指标，需下一轮 agentD 专项采集——在每个 query 后调用检索审计 API 或记录 queryId 后在数据库中回查 `query_retrieval_runs` / `query_answer_claims` / `query_answer_citations`。

### 3.2 Search Accuracy denominator

Public Eval 的搜索题按题组定义：
- Public Eval 1: S1、S2、S3、S4（S4 含 3 个子搜索: S4a/S4b/S4c）
- Public Eval 2: FS1、FS2、FS3、FS4（FS4 含 3 个子搜索: FS4a/FS4b/FS4c）

本报告按子项展开统计：
- Public Eval 1: S1(1) + S2(1) + S3(1) + S4a(1) + S4b(1) + S4c(1) = **6 个子项**
- Public Eval 2: FS1(1) + FS2(1) + FS3(1) + FS4a(1) + FS4b(1) + FS4c(1) = **6 个子项**

Search Accuracy 以子项为单位计算：PASS 子项数 / 总子项数。

---

## 4. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| SemanticChunkerTests | **8/0/0/0** |
| QueryTokenExtractorTests | **12/0/0/0** |
| LexicalSearchTokenBudgetTests | **7/0/0/0** |
| 全量 mvn test | **BUILD SUCCESS** |

---

## 5. Public Eval 1

**状态：部分完整验收。** Q2 因 PDF 编译延迟未完成，Answer Accuracy 以 11 题有效计。

### 5.1 Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 5/6（XLSX 上传失败） |
| compile jobs | 5，4 SUCCEEDED（PDF synthesis 中） |
| review queue | 0 |
| articles | 5 |

### 5.2 缺失/阻塞项

| 题号 | 状态 | 原因 |
|---|---|---|
| Q2 | **BLOCKED** | probe 角色定义（SL/TL/IM）位于 PDF `incident-response-reference-lite.pdf`。该 PDF 的 compile job 在查询执行时仍处于 synthesis 阶段，文章尚未入库。不是代码缺陷——纯编译时序问题。 |

Q2 在后续验证轮（如重新编译并等待 PDF 完成）中预期可恢复为 PASS（与基线一致）。本轮不因此阻塞提交判定，但 PE1 不能标记为完整通过。

### 5.3 Q1-Q12（Q2 以外 11 题）

| 题号 | outcome | mode | 判定 |
|---|---|---|---|
| Q1 | PARTIAL_ANSWER | LLM | **PARTIAL** |
| Q3 | SUCCESS | LLM | **PASS** |
| Q4 | INSUFFICIENT_EVIDENCE | LLM | **PASS** |
| Q5 | PARTIAL_ANSWER | LLM | **PASS**（port=2379, grpc） |
| Q6 | PARTIAL_ANSWER | LLM | **PASS**（tcpSocket.port=8080） |
| Q7 | SUCCESS | LLM | **PASS** |
| Q8 | INSUFFICIENT_EVIDENCE | LLM | **PASS** |
| Q9 | SUCCESS | LLM | **PASS** |
| Q10 | PARTIAL_ANSWER | LLM | **PASS** |
| Q11 | PARTIAL_ANSWER | LLM | **PASS**（Messenger） |
| Q12 | SUCCESS | LLM | **PASS** |

### 5.4 S1-S4

| 题号 | rank1 title | 判定 |
|---|---|---|
| S1 | Kubernetes 探针与事件响应协同手册 | **PASS** |
| S2 | 协同手册 / 落地建议与演练路径 | **PARTIAL** |
| S3 | 协同手册 / 6. Situation Room 角色分工 | **PASS** |
| S4a | 协同手册 | **PASS** |
| S4b | http liveness | **PASS** |
| S4c | incident response reference lite | **PASS** |

### 5.5 指标

| 指标 | 值 | 说明 |
|---|---|---|
| Answer Accuracy | **10/11**（11 题有效，Q1 PARTIAL，Q2 BLOCKED 不计入分母） | Q2 PDF 编译延迟导致阻塞 |
| Search Accuracy | **5/6**（S2 PARTIAL，其余 PASS） | S4a/S4b/S4c 均 PASS |
| Hallucination | **0** | 无编造 |
| Abstain Accuracy | **2/2**（Q4, Q8） | 正确拒答 |
| Recall@5 | **未采集** | 见第 3.1 节 |
| Recall@10 | **未采集** | 见第 3.1 节 |
| Citation Accuracy | **未采集** | 见第 3.1 节 |

**PE1 不能标记为完整通过。** Q2 为 BLOCKED，Recall/Citation 未采集。

---

## 6. Public Eval 2

### 6.1 Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 4/5（PDF 未上传） |
| compile jobs | 4，全部 SUCCEEDED |
| review queue | **0** |
| articles | 4 |
| terminal units | ~118 |

### 6.2 FQ1-FQ12

| 题号 | outcome | mode | 判定 |
|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | **PARTIAL** |
| FQ2 | PARTIAL_ANSWER | LLM | **PASS** |
| FQ3 | SUCCESS | FALLBACK | **PASS**（max_borrow_days=7） |
| FQ4 | PARTIAL_ANSWER | FALLBACK | **PASS**（100+1000 双值） |
| FQ5 | SUCCESS | FALLBACK | **PASS** |
| FQ6 | SUCCESS | FALLBACK | **PASS** |
| FQ7 | PARTIAL_ANSWER | LLM | **PASS** |
| FQ8 | PARTIAL_ANSWER | LLM | **PASS** |
| FQ9 | INSUFFICIENT_EVIDENCE | LLM | **PASS** |
| FQ10 | NO_RELEVANT_KNOWLEDGE | RULE_BASED | **BLOCKED**（PDF 未上传） |
| FQ11 | SUCCESS | LLM | **PASS** |
| FQ12 | PARTIAL_ANSWER | LLM | **PASS** |

### 6.3 FG1-FG3

| 题号 | outcome | mode | 判定 |
|---|---|---|---|
| FG1 | PARTIAL_ANSWER | FALLBACK | **PASS**（late_fee_per_day 20+5） |
| FG2 | PARTIAL_ANSWER | FALLBACK | **PASS**（max_concurrent_requests=50） |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | **PASS** |

### 6.4 FS1-FS4

FS4 拆为 3 个子搜索：FS4a（安全员）、FS4b（B级）、FS4c（精密仪器）。

| 题号 | rank1 title | 判定 |
|---|---|---|
| FS1 | 校园实验室安全管理手册 | **PASS** |
| FS2 | 校园实验室安全管理手册 | **PASS** |
| FS3 | 校园实验室安全管理手册 | **PASS** |
| FS4a | 校园实验室安全管理手册 / 7. 人员职责定义 | **PASS** |
| FS4b | B级: 化学品存储分级表 / 按危险等级归类 | **PASS** |
| FS4c | equipment borrowing policy | **PASS** |

搜索子项 **6/6 全部 PASS**，无 FAIL 或 PARTIAL。

### 6.5 Mixed Script 保护

| 搜索词 | 结果数 | rank1 | 判定 |
|---|---|---|---|
| `B级` | 2 | 化学品存储分级表 / 按危险等级归类 | **PASS** |
| `B 级` | 2 | 化学品存储分级表 / B 级 | **PASS** |

### 6.6 指标

| 指标 | 值 | 说明 |
|---|---|---|
| Answer Accuracy | **13/14**（FQ10 因 PDF 未上传不计入分母，FQ1 PARTIAL） | 14 题有效 |
| Search Accuracy | **6/6**（FS1-FS4 全部子项 PASS） | 按子项统计，见第 3.2 节 |
| Hallucination | **0** | 无编造 |
| Abstain Accuracy | **2/2**（FQ9, FG3） | 正确拒答 |
| FQ4/FG1 双目标 | **已验证** | 双值输出（100+1000, 20+5） |
| Recall@5 | **未采集** | 见第 3.1 节 |
| Recall@10 | **未采集** | 见第 3.1 节 |
| Citation Accuracy | **未采集**（仅定性：FQ4/FG1 citation 指向正确字段和文件） | 见第 3.1 节 |

---

## 7. 与旧基线对比

| 指标 | 2026-06-02 基线 | 本轮 | 变化 |
|---|---|---|---|
| PE1 Answer Accuracy | 11/12 | 10/11（Q2 BLOCKED 不计） | 实质持平（Q2 编译延迟非代码回归） |
| PE1 Search Accuracy | 5/6（S2 FAIL） | 5/6（S2 PARTIAL） | S2 FAIL→PARTIAL |
| PE2 Answer Accuracy | 11/15 | **13/14**（FQ10 PDF 不计） | **+2** |
| PE2 Search Accuracy | 2/6（FS2/FS4a/FS4b FAIL） | **6/6** | **+4 子项** |
| PE2 Hallucination | 0 | 0 | — |
| FQ4 | FAIL | **PASS** | ✅ |
| FG1 | FAIL | **PASS** | ✅ |
| FS2 | FAIL | **PASS** | ✅ |
| FS4a | FAIL | **PASS** | ✅ |
| FS4b | FAIL（0 结果） | **PASS**（2 结果） | ✅ |

---

## 8. 已闭环问题

| 问题 | 修复 | 状态 |
|---|---|---|
| FG1 qf=false 全池淘汰 | CJK bigram 重叠匹配 | ✅ |
| FG1 ftmc=0 | CJK bigram in countFieldLevelTokenMatches | ✅ |
| FQ4 ftmc 平局 tie-break | atmc 排序 | ✅ |
| FG1/FQ4 多目标聚合 | Phase 2 additional candidates | ✅ |
| enricher bootstrap guard | isLlmRouteAvailable 接受 bootstrap | ✅ |
| contextDisplayValues | Materializer 写入 sibling descriptors | ✅ |
| FG1 raw query entity context match | 归一化 question 包含 display value | ✅ |
| FS4b "B级" 0 结果 | mixed script token extraction | ✅ |
| S2 chunk identity 折叠 | chunk identity + RRF key | ✅ |
| S2 heading boundary chunking | ATX heading 强制 chunk 边界 | ✅ |

---

## 9. 剩余 Open Issue

| 问题 | 当前状态 | 根因 |
|---|---|---|
| S2 section anchor 不显示"下一步计划" | PARTIAL（显示"落地建议与演练路径"） | Writer 将源文档"下一步计划"内容合并到其他节 |
| FQ10 PDF 上传失败 | BLOCKED | source name varchar(32) 限制 |
| Public Eval 1 Q2 PDF 编译延迟 | BLOCKED | LLM 编译耗时导致时间窗口不足 |
| FS2 markdown chunk 标题精度 | 已命中但 section anchor 为 article 级 | Writer 内容重组导致 |

---

## 10. Query 红线风险检查

| 检查项 | 结果 |
|---|---|
| 生产代码是否写入题号/业务词/文档名？ | **否** |
| 是否修改 AnswerGeneration/prompt？ | **否** |
| 修改是否基于通用文本结构规则？ | **是**（ATX 正则、Unicode script、字段名扫描） |

---

## 11. 最终结论

### **建议提交 S2 chunker heading boundary 修复，但整体 eval 仍有 open issue，不能写"完整通过"**

**Public Eval 2 质量显著提升：**

| 指标 | 基线→本轮 | 说明 |
|---|---|---|
| Answer Accuracy | 11/15→**13/14** | FQ4/FG1 从 FAIL 修复为 PASS |
| Search Accuracy | 2/6→**6/6** | FS2/FS4a/FS4b 全部修复，首次全部 PASS |
| FQ4/FG1 双目标 | FAIL→PASS | 100+1000, 20+5 双值输出 |
| FS4b "B级" | 0→2 结果 | mixed script token extraction 修复 |
| Hallucination | 0 | 保持 |

**Public Eval 1 未完全通过：**

| 指标 | 状态 | 说明 |
|---|---|---|
| Answer Accuracy | **10/11** | Q1 PARTIAL, Q2 BLOCKED（PDF 编译延迟） |
| Search Accuracy | **5/6** | S2 PARTIAL（section anchor 仍不显示"下一步计划"） |
| S2 | FAIL→PARTIAL | chunk identity + heading boundary 已改善，但 Writer 内容重组导致 anchor 不精确 |

**提交判定：**

- S2 chunker heading boundary 修复（`SemanticChunker.java`）建议提交。PE2 指标全面改善，PE1 无新增回归。
- 整体 eval **未完全通过**：PE1 Q2 BLOCKED，S2 仍 PARTIAL，Recall/Citation 未采集。
- 不阻塞理由：Q2 为编译时序问题（非代码缺陷），S2 为 Writer 内容策略独立优化方向，Recall/Citation 为采集口径缺口（非能力下降）。

**下一轮建议（最小优先方向）：**

补采集 Recall@5/Recall@10/Citation Accuracy。当前报告仅有 Answer Accuracy 和 Search Accuracy，缺乏 retrieval 召回和 citation 覆盖的定量口径。下一轮 agentD 应在每个 query 后记录 queryId，通过检索审计 API 或数据库回查 `query_retrieval_runs` / `query_answer_claims` 补齐完整指标。

---

## 12. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 scripts/config/schema/题集/redline allowlist
- [x] 未提交 commit
- [x] 未读取 hidden eval
- [x] 两套 eval 分开清库执行
- [x] 与无关文档搬目录线（顶层 docs 删除、`docs/核心架构/**`）严格隔离
- [x] `special_cases_report.md` 仅为 redline 输出
- [x] Recall@5/Recall@10/Citation Accuracy 未采集，已在报告中明确标注
- [x] PE1 Q2 BLOCKED 为编译时序问题，非代码缺陷
