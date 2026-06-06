# 准确性当前状态复盘与下一步缺口归因报告

分析时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读状态复盘与缺口归因，无代码修改

---

## 1. 当前准确性总览

### 1.1 最新代码基线

| 项 | 值 |
|----|-----|
| 最新 commit | `f7b56e0` fix(query): verify terminal citations with trace support |
| 前一 commit | `31ff3c8` docs: relocate core architecture docs |
| 最近 10 commits | 含 terminal fallback、mixed script token、heading boundary chunking、citation trace 等 |
| 工作区状态 | 仅 `special_cases_report.md`（redline）+ 前端处理历史文件（与准确性无关） |

### 1.2 最近完整 Eval 执行状态

| Eval Run | 执行时间 | 代码基线 | Answer | Search | Recall | Citation |
|----------|:---:|---|:---:|:---:|:---:|:---:|
| `two_public_eval_full_clean_schema_gate_report.md` | 2026-06-05 | `1d7be23`（heading boundary） | PE1:10/11, PE2:13/14 | PE1:5/6, PE2:6/6 | **未采集** | **未采集** |
| `recall_citation_metrics_collection_report.md` | 2026-06-05 | `1d7be23` | PE1:10/11（Q6 excluded） | PE1:5/6 | PE1:10/10 | PE2 Fallback 混合 |
| Citation targeted gates | 2026-06-06 | `f7b56e0`（最新） | FQ4/FG1/FG2 targeted | — | — | FQ4/FG1/FG2: **1.0** ✅ |

**关键缺口**：最新代码（`f7b56e0`）的**完整两套 Public Eval gate + Recall/Citation 指标**尚未执行。现有完整 gate 报告仍是 citation 修复提交前的代码基线。

---

## 2. 最近已提交修复清单

| Commit | 描述 | 影响的准确率维度 |
|--------|------|:---:|
| `f7b56e0` | CitationValidator terminal unit evidence + trace support | Citation Coverage (FQ4/FG1/FG2 0→1.0) |
| `1d7be23` | SemanticChunker ATX heading boundary chunking | Search (S2 FAIL→PARTIAL, section anchor改善) |
| `062d391` | Mixed script lexical search tokens | Search (FS4b "B级" 0→2结果) |
| `549f0e3` | Stabilize terminal unit fallback selection | Answer (FQ4/FG1 FAIL→PASS, 多目标聚合) |
| `0ea3dfd` | Clean up terminal fix intermediate reports | 无（报告清理） |

**累计修复效果（基于 targeted gates）**：

| 维度 | 原始基线 | 当前（targeted gate） | 变化 |
|------|:---:|:---:|:---:|
| PE2 Answer Accuracy | 11/15 | **13/14**（targeted） | +2 |
| PE2 Search Accuracy | 2/6 | **6/6**（full gate） | +4 |
| PE2 Hallucination | 5 | **0** | -5 |
| FQ4 Fallback | FAIL | **PASS** | ✅ |
| FG1 Fallback | FAIL | **PASS** | ✅ |
| FG2 Fallback | PASS | **PASS**（cov 0→1.0） | ✅ |
| FS4b "B级" | FAIL (0结果) | **PASS** (2结果) | ✅ |
| FQ4/FG1/FG2 Citation cov | 0.0-0.5 | **1.0** | ✅ |

---

## 3. Answer Accuracy 状态

### 3.1 Public Eval 1（11 题有效，Q2 BLOCKED）

| 题号 | 判定 | mode | 说明 | 收口状态 |
|------|:---:|------|------|:---:|
| Q1 | **PARTIAL** | LLM | 答案内容方向正确但覆盖不完整，citations 为空 | **未收口** — LLM 行为问题 |
| Q2 | **BLOCKED** | — | PDF 编译延迟，非代码缺陷 | **blocked by data** |
| Q3-Q12 | **PASS** (10题) | LLM/FALLBACK | 全部正确 | ✅ 已收口 |

**PE1 Answer Accuracy = 10/11**（Q1 PARTIAL）

### 3.2 Public Eval 2（14 题有效，FQ10 BLOCKED）

| 题号 | 判定 | mode | 说明 | 收口状态 |
|------|:---:|------|------|:---:|
| FQ1 | **PARTIAL** | LLM | 覆盖 A/B/C/D 分级，"当前证据不足"标注偏多 | **未收口** — LLM 行为问题 |
| FQ2 | PASS | LLM | 安全员 vs 设备管理员区分清晰 | ✅ |
| FQ3 | PASS | FALLBACK | max_borrow_days=7 正确 | ✅ |
| FQ4 | PASS | FALLBACK | deposit_amount 100+1000 双值正确 | ✅ |
| FQ5 | PASS | FALLBACK | api_endpoint 正确 | ✅ |
| FQ6 | PASS | FALLBACK | version=v2.3.1 正确 | ✅ |
| FQ7 | PASS | LLM | 丙酮+氢氧化钠 正确 | ✅ |
| FQ8 | PASS | LLM | 跨文档组合正确 | ✅ |
| FQ9 | PASS | LLM | 正确拒答 | ✅ |
| FQ10 | **BLOCKED** | — | PDF 未上传（source name varchar(32) 限制） | **blocked by infra** |
| FQ11 | PASS | LLM | EQ-001 正确 | ✅ |
| FQ12 | PASS | LLM | 3 阶段审批链正确 | ✅ |
| FG1 | PASS | FALLBACK | late_fee_per_day 20+5 双值正确 | ✅ |
| FG2 | PASS | FALLBACK | max_concurrent_requests=50 正确 | ✅ |
| FG3 | PASS | LLM | 正确拒答 | ✅ |

**PE2 Answer Accuracy = 13/14**（FQ1 PARTIAL）

---

## 4. Search / Recall 状态

### 4.1 Public Eval 1 Search（6 子项）

| 题号 | 判定 | 收口状态 |
|------|:---:|:---:|
| S1 | PASS | ✅ |
| S2 | **PARTIAL** | **未收口** — section anchor 不精确 |
| S3 | PASS | ✅ |
| S4a | PASS | ✅ |
| S4b | PASS | ✅ |
| S4c | PASS | ✅ |

**PE1 Search Accuracy = 5/6**（S2 PARTIAL）

### 4.2 Public Eval 2 Search（6 子项）

| 题号 | 判定 | 收口状态 |
|------|:---:|:---:|
| FS1 | PASS | ✅ |
| FS2 | PASS | ✅ |
| FS3 | PASS | ✅ |
| FS4a | PASS | ✅ |
| FS4b | PASS | ✅ |
| FS4c | PASS | ✅ |

**PE2 Search Accuracy = 6/6 — 首次全部 PASS** ✅

### 4.3 Recall@5/Recall@10

- PE1：`recall_citation_metrics_collection_report.md` 确认 **10/10**（10 有效题全部 PASS）
- PE2：**未采集**。最新完整 gate 报告（2026-06-05）明确标注"Recall@5/Recall@10 本轮不可用"

---

## 5. Citation Accuracy / Coverage 状态

### 5.1 Targeted Gate 结果（`f7b56e0`，2026-06-06）

| 题号 | coverageRate | validation_path | 判定 |
|------|:---:|------|:---:|
| FQ3 | 1.0 | TERMINAL_UNIT / source_near_complete | ✅ 保护 |
| FQ4 | 1.0 | TERMINAL_UNIT | ✅ 修复 |
| FQ5 | 1.0 | TERMINAL_UNIT | ✅ 保护 |
| FQ6 | 1.0 | TERMINAL_UNIT | ✅ 保护 |
| FG1 | 1.0 | TERMINAL_UNIT | ✅ 修复 |
| FG2 | 1.0 | TERMINAL_UNIT | ✅ 修复 |

### 5.2 Full Eval Citation 状态

**未采集。** `two_public_eval_full_clean_schema_gate_report.md`（2026-06-05，代码基线 `1d7be23`）明确标注"Citation Accuracy 本轮不可用"。需要基于最新代码（`f7b56e0`）重跑完整 gate 并采集 citation metrics。

### 5.3 Citation 相关已修复项

| 修复 | 影响 |
|------|------|
| CitationValidator terminal unit evidence 验证路径 | FQ4/FG1/FG2 SOURCE_FILE citation 不再依赖原始 YAML 偶然 token overlap |
| `isHighConfidencePartialOverlap` 阈值 0.66→0.60 | 小 token 集（2-5 tokens）citation coverage 改善 |
| Phase 1A Citation Trace | 后续排查 citation 问题可一步定位（L1+L2 结构化事件） |

---

## 6. Hallucination / Abstain 状态

| 指标 | PE1 | PE2 | 收口状态 |
|------|:---:|:---:|:---:|
| Hallucination Count | **0** | **0** | ✅ |
| Abstain Accuracy | **2/2** (Q4, Q8) | **2/2** (FQ9, FG3) | ✅ |

Hallucination 从原始基线的 5 降至 0（所有 FAIL 均为 sibling 字段误选或 answer 漏点，非编造不存在的内容）。

---

## 7. 已收口问题

| # | 问题 | 修复 commit | 收口证据 |
|---|------|------|------|
| 1 | FG1 qf=false 全池淘汰 | `549f0e3` | targeted gate: tuQfPassed 0→4 |
| 2 | FG1 ftmc=0 CJK token 匹配 | `549f0e3` | targeted gate: ftmc 0→2 |
| 3 | FQ4 ftmc 平局 tie-break | `549f0e3` | targeted gate: winner 从 approval_required 变为 deposit_amount |
| 4 | FQ4/FG1 多目标聚合缺失 | `549f0e3` | targeted gate: 双值输出（100+1000, 20+5） |
| 5 | Enricher bootstrap guard 拒绝合法路由 | `549f0e3` | targeted gate: 中文别名生成正常 |
| 6 | contextDisplayValues 缺失 | `549f0e3` | targeted gate: entity context match 生效 |
| 7 | FS4b "B级" 搜索 0 结果 | `062d391` | targeted gate: 0→2 结果 |
| 8 | "B 级" 搜索 | `062d391` | targeted gate: 2 结果 |
| 9 | S2 chunk identity 被 article 折叠 | `549f0e3` | targeted gate: chunk 独立席位 |
| 10 | S2 heading boundary chunking | `1d7be23` | full gate: S2 FAIL→PARTIAL |
| 11 | FQ4 citation coverage 0.0 | `f7b56e0` | targeted gate: 0.0→1.0 |
| 12 | FG1 citation coverage 0.5 | `f7b56e0` | targeted gate: 0.5→1.0 |
| 13 | FG2 citation coverage 0.0 | `f7b56e0` | targeted gate: 0.0→1.0 |
| 14 | PE2 Search Accuracy | `062d391`+`1d7be23` | full gate: 2/6→6/6 |
| 15 | PE2 Hallucination | 多项 | full gate: 5→0 |

**15 项已收口，其中 10 项已有 targeted gate 验证，5 项已有 full eval gate 验证。**

---

## 8. 未收口问题

| # | 问题 | 当前状态 | 失败类型 | 优先级 | 下一步 |
|---|------|:---:|------|:---:|------|
| 1 | **PE2 FQ1 PARTIAL** | LLM 答案部分正确，"当前证据不足"标注偏多 | 回答漏点 | **中** | LLM 行为改善（prompt/grounding），非 query 主链问题 |
| 2 | **PE1 Q1 PARTIAL** | LLM 答案方向正确但覆盖不完整，citations 为空 | 回答漏点 | **中** | 同上，LLM 行为改善 |
| 3 | **PE1 S2 PARTIAL** | section anchor 显示"落地建议与演练路径"而非"下一步计划" | 展示标题问题 | **中** | Writer 内容重组导致，与 chunking 不同的独立问题 |
| 4 | **Citation metrics 未采集**（`f7b56e0` 代码基线） | 最新完整 gate 报告中 Recall/Citation 均未采集 | 采集口径缺口 | **高** | agentD 重跑 full gate + 采集完整指标 |

---

## 9. Blocked 项

| # | 题号 | 阻塞原因 | 是否代码缺陷 | 建议处理 |
|---|------|------|:---:|------|
| 1 | PE1 Q2 | PDF 编译延迟（compile job 超时窗口不足） | **否** | 重编译时等待 PDF 完成，或延长 compile 超时 |
| 2 | PE2 FQ10 | PDF 源文件名超过 varchar(32) 限制 | **否**（infra 限制） | 独立 infra 修复（扩大 source name 字段或短名映射），非 query 逻辑 |

---

## 10. 风险与注意事项

| 风险 | 说明 |
|------|------|
| **Citation 修复仅在 targeted gate 验证** | FQ4/FG1/FG2 citation coverage 1.0 来自 targeted gate，未在完整两套 eval 中验证交叉影响（如 LLM 模式 citation 是否有回归） |
| **Citation metrics 缺口** | `f7b56e0` 提交后未跑完整 full gate，无法确认 citation 修复是否影响非 FALLBACK 题目 |
| **PE1 S2 PARTIAL 与 chunking 修复是不同根因** | chunking 修复（`1d7be23`）解决了 chunk 独立身份问题，S2 从 FAIL→PARTIAL。但剩余的 section anchor 不精确（"落地建议与演练路径"）是 Writer 内容重组问题，不同于 chunking。不可混淆。 |
| **LLM 行为问题非 query 主链修复范围** | FQ1/Q1 PARTIAL 是 LLM 生成行为（过度标注"当前证据不足"），不应通过修改 query retrieval/fallback/citation 主链来"修复" |
| **PDF blocked 项不应混入 query 修复** | Q2 和 FQ10 的阻塞原因分别是编译时序和 infra 限制，不是 query 逻辑缺陷 |

---

## 11. 下一步建议

### 唯一推荐最小动作：**agentD 基于最新代码（`f7b56e0`）跑两套 Public Eval 完整 clean-schema gate + 采集完整 Recall/Citation 指标**

### 选择理由

1. **填补采集缺口**：当前完整 gate 报告仍是 citation 修复前的代码基线（`1d7be23`），且明确标注 Recall/Citation 未采集。需要一份基于最新代码的完整指标报告。

2. **确认无回归**：CitationValidator terminal unit evidence 路径和 `isHighConfidencePartialOverlap` 阈值修改虽然 targeted gate 通过，但需要确认对 LLM 模式 citation（如 FQ1/FQ2）、非 key=value SOURCE_FILE citation、ARTICLE 路径 citation 无负面影响。

3. **验证 FG2 answerOutcome 改善**：targeted gate 中 FG2 从 PARTIAL_ANSWER 变为 SUCCESS（因 citation coverage 提升），需确认在完整 eval 中保持一致。

4. **验证 PE1 保护**：Q6 terminal field alias、Q1-Q12（除 Q2 BLOCKED）、S1-S4 需要确认无新增回归。

5. **不需要 agentA 改代码**：这是纯 agentD 验证工作，不需要修改任何生产代码。

### 不推荐的动作

- ❌ **让 agentA 继续修改 FQ1/Q1 LLM 行为**：这是 LLM prompt/grounding 问题，当前不应通过修改 query 主链来"修复"
- ❌ **让 agentA 修复 PE1 S2 Writer 内容重组**：这是独立的 Writer 策略优化，不应与本轮 citation 修复混在一起
- ❌ **让 agentA 修复 PDF blocked 项**：Q2 是编译时序问题，FQ10 是 infra 限制，不是 query 逻辑缺陷
- ❌ **跳过完整 gate 直接标记"已通过"**：targeted gate 通过不等于完整 eval 通过

---

## 12. agentD 下一轮验证范围草案

```text
你现在是 agentD（验证/测试 Agent）。

本轮目标：
基于最新代码（commit f7b56e0），跑两套 Public Eval 完整 clean-schema gate，
并补齐 Recall@5/Recall@10/Citation Accuracy 完整指标。

验证范围：
1. Public Eval 1：Q1-Q12 + S1-S4（Q2 和 Q6 如遇编译延迟，记录为 BLOCKED，不阻塞其他题验证）
2. Public Eval 2：FQ1-FQ12 + FG1-FG3 + FS1-FS4（FQ10 如 PDF 未上传，记录为 BLOCKED）
3. Mixed script 保护：搜索 "B级" / "B 级"

采集指标（每道有效题）：
1. answerOutcome / generationMode / modelExecutionStatus
2. citationCheck.coverageRate / verifiedCount / demotedCount
3. Recall@5 / Recall@10（通过 DB 回查 query_retrieval_runs + query_retrieval_channel_hits）

验证步骤：
1. 清库（bash scripts/reset-lattice-schema.sh）
2. 恢复 LLM 绑定（Admin API 或 DB 直接插入，确保 compile/field-alias-enricher 在内的 11 条全部 enabled）
3. 上传 PE1 资料（5-6 个文件），等待全部 compile job SUCCEEDED
4. 查询 PE1 Q1-Q12 + S1-S4
5. 清库（重复步骤 1-2）
6. 上传 PE2 资料（4-5 个文件，PDF 可选），等待全部 compile job SUCCEEDED
7. 查询 PE2 FQ1-FQ12 + FG1-FG3 + FS1-FS4
8. 查询 Mixed Script 保护搜索
9. 对每道有效题：
   a. 记录 API 返回的 citationCheck 字段
   b. 通过 queryId 在 DB 中回查 query_retrieval_runs.run_id
   c. 通过 run_id 查询 query_retrieval_channel_hits（included_in_fused=true），按 fused_rank 排序得到 top10
   d. 判断目标证据是否在 top5/top10 中

禁止事项：
- 禁止修改代码、配置、schema、题集
- 禁止注释已有测试或放宽 eval 预期
- 禁止读取 hidden eval

输出报告：
docs/test/knowledge-base-e2e/two_public_eval_post_citation_fix_full_gate_report.md

报告必须包含：
- Answer Accuracy（分 PE1/PE2，含逐题明细）
- Search Accuracy（分 PE1/PE2，含逐题明细）
- Recall@5/Recall@10（分 PE1/PE2）
- Citation Accuracy（分 PE1/PE2，含每道 FALLBACK 题 coverageRate）
- Hallucination Count
- Abstain Accuracy
- 与上一轮 full gate 的对比（改善/回归/BLOCKED）
- 未采集项标注
```

---

## 13. 明确声明

- [x] 未修改生产代码（`src/main/java/**`）
- [x] 未修改测试代码（`src/test/java/**`）
- [x] 未修改 `src/main/resources/**`
- [x] 未修改 `scripts/**`
- [x] 未修改 prompt / config / schema / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未读取 hidden eval
- [x] 所有结论基于已读取报告 + git log + git status 交叉验证
- [x] 本报告为纯状态复盘与缺口归因，不包含任何 case 特判或代码修复建议
- [x] 推荐的唯一下一步动作为 agentD 验证（不改代码）
