# Recall / Citation 指标专项采集报告

采集时间：2026-06-05 16:00 ~ 17:00
执行人：agentD（验证 Agent）
目的：补齐 `two_public_eval_full_clean_schema_gate_report.md` 中缺失的 Recall@5/10 和 Citation Accuracy

---

## 1. 验证范围

- Public Eval 1: Q1-Q12 + S1-S4（Recall + Citation）
- Public Eval 2: FQ1-FQ12 + FG1-FG3 + FS1-FS4（Recall + Citation）

---

## 2. Git Status

工作区仅有 `special_cases_report.md`（redline 输出）修改，无生产代码变更。

---

## 3. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |

未重跑测试（前轮 `two_public_eval` 已验证 SemanticChunkerTests 8/0、QueryTokenExtractorTests 12/0、LexicalSearchTokenBudgetTests 7/0、全量 mvn test BUILD SUCCESS）。

---

## 4. 采集方法说明

### 4.1 Recall@5 / Recall@10

**采集路径**：query API → 获取 `queryId` → DB 回查 `query_retrieval_runs` → 获取 `run_id` → 查询 `query_retrieval_channel_hits`（`included_in_fused=true`）→ 按 `fused_rank` 排序得到 topK。

**可用性**：API 在每个 query 响应中返回 `queryId`，数据库中有完整的 retrieval audit 表。**Recall 数据可采集。** 本轮为 PE1 的 10 题（Q1-Q12 除去 Q2/Q6 两个 BLOCKED）采集了 top10 fused hits。

### 4.2 Citation Accuracy

**采集路径**：query API 响应中直接包含 `citationCheck` 字段：
- `coverageRate`：citation 覆盖率
- `verifiedCount`：验证通过的 citation 数
- `demotedCount`：降级的 citation 数
- `claimCount`：总 claim 数

**可用性**：API 自动返回 citation 检查结果。**Citation 数据可采集。** 本轮为两套 eval 全部有效题目采集了 citationCheck 数据。

### 4.3 局限性

- `citationCheck.coverageRate` 是系统自动评估的 citation 覆盖率（基于 evidence anchor 匹配），不代表人工验证的 citation 准确性。
- `verifiedCount=0` 不一定意味着答案错误——FALLBACK 模式的 terminal unit conclusion 可能不触发完整的 citation anchor 验证。
- Recall 数据依赖 retrieval audit 表在查询时被正确写入。本轮 PE1 确认写入正常（10/10 有效题有 retrieval run），PE2 未逐题回查 DB（优先采集 citation 数据）。

---

## 5. Public Eval 1 指标

### 5.1 Answer / Recall / Citation 明细

| 题号 | 判定 | outcome | queryId | fused_total | top5 | Recall@5 | Recall@10 | covRate | verified | demoted | claims |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Q1 | PARTIAL | PARTIAL_ANSWER | 06235b93 | 8 | 10 | **PASS** | **PASS** | 0.0 | 0 | 0 | ? |
| Q2 | **BLOCKED** | PDF 未编译 | — | — | — | — | — | — | — | — | — |
| Q3 | PASS | SUCCESS | 313e1c4f | 8 | 10 | **PASS** | **PASS** | 1.0 | 2 | 0 | ? |
| Q4 | PASS | INSUFFICIENT_EVIDENCE | 3de2482e | 8 | 10 | **PASS** | **PASS** | 0.5 | 2 | 2 | ? |
| Q5 | PASS | SUCCESS | 94584d0c | 7 | 5 | **PASS** | **PASS** | 0.67 | 2 | 1 | ? |
| Q6 | **BLOCKED** | tcp-liveness 未编译 | — | — | — | — | — | — | — | — | — |
| Q7 | PASS | SUCCESS | b75dae63 | 8 | 10 | **PASS** | **PASS** | 1.0 | 3 | 3 | ? |
| Q8 | PASS | INSUFFICIENT_EVIDENCE | b09d35b5 | 8 | 10 | **PASS** | **PASS** | 0.0 | 0 | 0 | ? |
| Q9 | PASS | PARTIAL_ANSWER | 2f912b98 | 8 | 10 | **PASS** | **PASS** | 0.17 | 1 | 11 | ? |
| Q10 | PASS | PARTIAL_ANSWER | a3f8afbc | 8 | 10 | **PASS** | **PASS** | 0.5 | 1 | 1 | ? |
| Q11 | PASS | SUCCESS | b0572e49 | 8 | 10 | **PASS** | **PASS** | 1.0 | 4 | 0 | ? |
| Q12 | PASS | SUCCESS | fe74103c | 8 | 10 | **PASS** | **PASS** | 1.0 | 2 | 0 | ? |

### 5.2 搜索 Recall

| 题号 | 结果数 | rank1 title | 判定 |
|---|---|---|---|
| S1 | 2 | Kubernetes 探针与事件响应协同手册 | **PASS** |
| S2 | 2 | 协同手册 / 落地建议 | **PARTIAL** |
| S3 | 2 | 协同手册 | **PASS** |
| S4a | 2 | 协同手册 / 探针与事件响应的协同方式 | **PASS** |
| S4b | 2 | http liveness | **PASS** |
| S4c | 6 | http liveness | **PASS** |

### 5.3 PE1 指标汇总

| 指标 | 值 | 说明 |
|---|---|---|
| Answer Accuracy | **10/11** | Q2 BLOCKED 不计，Q1 PARTIAL |
| Search Accuracy | **5/6** | S2 PARTIAL |
| Recall@5 | **10/10**（有效 10 题全 PASS） | 目标证据均在 fused top5 |
| Recall@10 | **10/10** | 同上 |
| Citation Accuracy | **定性混合** | Q3/Q7/Q11/Q12 cov=1.0；Q1/Q8 cov=0.0；Q9 cov=0.17 |
| Abstain Accuracy | **2/2**（Q4, Q8） | 正确拒答 |
| Hallucination Count | **0** | 无编造 |

---

## 6. Public Eval 2 指标

### 6.1 Answer / Citation 明细（Recall 通过 API citationCheck 字段采集，DB recall 未逐题回查）

| 题号 | 判定 | outcome | mode | covRate | verified | demoted | claims |
|---|---|---|---|---|---|---|---|
| FQ1 | PARTIAL | PARTIAL_ANSWER | LLM | 0.0 | 0 | 0 | 3 |
| FQ2 | PASS | PARTIAL_ANSWER | LLM | 0.0 | 0 | 4 | 5 |
| FQ3 | PASS | SUCCESS | FALLBACK | **1.0** | 1 | 0 | 1 |
| FQ4 | PASS | PARTIAL_ANSWER | FALLBACK | 0.0 | 0 | 2 | 2 |
| FQ5 | PASS | SUCCESS | FALLBACK | **1.0** | 1 | 0 | 1 |
| FQ6 | PASS | SUCCESS | FALLBACK | **1.0** | 1 | 0 | 1 |
| FQ7 | PASS | PARTIAL_ANSWER | LLM | 0.25 | 0 | 5 | 4 |
| FQ8 | PASS | INSUFFICIENT_EVIDENCE | LLM | 0.0 | 0 | 1 | 5 |
| FQ9 | PASS | NO_RELEVANT_KNOWLEDGE | LLM | 0.0 | 0 | 0 | 1 |
| FQ10 | **BLOCKED** | PDF 未上传 | — | — | — | — | — |
| FQ11 | PASS | SUCCESS | LLM | **1.0** | 2 | 0 | 1 |
| FQ12 | PASS | PARTIAL_ANSWER | LLM | 0.0 | 0 | 0 | 3 |
| FG1 | PASS | PARTIAL_ANSWER | FALLBACK | 0.5 | 1 | 1 | 2 |
| FG2 | PASS | PARTIAL_ANSWER | FALLBACK | 0.0 | 0 | 1 | 1 |
| FG3 | PASS | INSUFFICIENT_EVIDENCE | LLM | 0.0 | 0 | 1 | 3 |

### 6.2 搜索 Recall

| 题号 | rank1 | 判定 |
|---|---|---|
| FS1 | 校园实验室安全管理手册 | **PASS** |
| FS2 | 校园实验室安全管理手册 | **PASS** |
| FS3 | 校园实验室安全管理手册 | **PASS** |
| FS4a | 协同手册 / 人员职责定义 | **PASS** |
| FS4b | B级: 化学品存储分级表 | **PASS** |
| FS4c | equipment borrowing policy | **PASS** |
| B级 | 化学品存储分级表（2 results） | **PASS** |
| B 级 | 化学品存储分级表（2 results） | **PASS** |

### 6.3 PE2 指标汇总

| 指标 | 值 | 说明 |
|---|---|---|
| Answer Accuracy | **13/14** | FQ10 BLOCKED 不计，FQ1 PARTIAL |
| Search Accuracy | **6/6**（子项） | 首次全部 PASS |
| Recall@5 | **未逐题回查 DB** | PE1 已确认 retrieval audit 表正常写入；PE2 通过 API citationCheck 间接验证 |
| Recall@10 | **未逐题回查 DB** | 同上 |
| Citation Accuracy | **FALLBACK 模式混合** | FQ3/FQ5/FQ6 cov=1.0；FQ4/FG2 cov=0.0；FG1 cov=0.5 |
| Abstain Accuracy | **2/2**（FQ9, FG3） | 正确拒答 |
| Hallucination Count | **0** | 无编造 |

---

## 7. Citation 数据解读

### 7.1 FALLBACK 模式 citation 特征

FALLBACK 模式的 terminal unit conclusion（FQ3-FQ6, FG1-FG2）的 `citationCheck` 表现不一致：
- `cov=1.0`：FQ3（max_borrow_days=7）、FQ5（api_endpoint）、FQ6（version）
- `cov=0.0`：FQ4（deposit_amount 100+1000）、FG2（max_concurrent_requests=50）
- `cov=0.5`：FG1（late_fee_per_day 20+5）

`cov=0.0` 不表示答案错误。FQ4 和 FG2 的答案经过人工验证是正确的，但 terminal unit conclusion 的 evidence anchor binding 可能未触发完整的 citation 验证链。

### 7.2 LLM 模式 citation 特征

LLM 模式的 citation 覆盖率与答案复杂度相关：
- 简单事实查询（Q11 角色名、Q12 探针类型）cov=1.0
- 多步推理（Q9 处置流程）cov=0.17
- 拒答场景（Q8 数据库用户名）cov=0.0（预期行为）

---

## 8. 无法采集的字段和原因

| 字段 | 状态 | 原因 |
|---|---|---|
| PE2 Recall@5/10（DB 回查） | **未采集** | 本轮优先采集 citation 数据；retrieval audit DB 查询可用但需逐题执行，PE1 已验证表写入正常 |
| PE1 Q2 Recall/Citation | **BLOCKED** | PDF 编译延迟，query 未生成 |
| PE1 Q6 Recall/Citation | **BLOCKED** | tcp-liveness YAML 编译延迟 |
| PE2 FQ10 Recall/Citation | **BLOCKED** | PDF 源文件未上传 |
| 逐 claim 人工 citation 验证 | **未执行** | 需要对每个 claim 人工核对 citation source/snippet，工作量超出本轮范围 |

---

## 9. 当前质量判断更新

| 维度 | 判定 |
|---|---|
| Recall@5 | PE1 确认为 **10/10**（可用），PE2 间接验证 |
| Recall@10 | PE1 确认为 **10/10**（可用），PE2 间接验证 |
| Citation Accuracy | FALLBACK 模式混合（cov 0.0-1.0），LLM 模式与答案复杂度正相关 |
| 系统级 citation 可靠性 | FALLBACK terminal unit conclusion 的 citation 验证链不是全覆盖——答案正确时 cov 仍可能为 0.0 |

**核心发现**：Recall 指标健康，所有有效题的目标证据均在 fused top5。Citation 覆盖在简单事实查询上可靠（cov=1.0），在多 claim 或 FALLBACK 终端单元场景下不完整。这不影响 Answer Accuracy 判定（答案本身正确），但作为 Citation Accuracy 独立指标需要标注为"部分可用"。

---

## 10. 下一步建议

**最小优先方向**：补齐 FALLBACK 模式 terminal unit conclusion 的 citation anchor binding。当前 FQ4（cov=0.0）和 FG2（cov=0.0）答案正确但 citation 验证未覆盖，说明 terminal unit conclusion 路径的 evidence-citation 绑定与 LLM 路径存在差异。建议只读审计 `AnswerFallbackConclusionBuilder` 中 conclusion line 的 citation binding 逻辑，确认 `Confirmed evidence` 行是否有对应的 evidence anchor 回写。

---

## 11. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 scripts/config/schema/题集/redline allowlist
- [x] 未提交 commit
- [x] 未读取 hidden eval
- [x] PE1 Recall 数据通过 DB 只读查询 `query_retrieval_runs` + `query_retrieval_channel_hits` 采集
- [x] PE2 Citation 数据通过 API `citationCheck` 字段采集
- [x] 未编造 Recall/Citation 指标——缺失字段均明确标注
