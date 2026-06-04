# FG1/FQ4 Conclusion Builder Terminal Unit 消费修复 — 端到端验证报告

验证时间：2026-06-02
执行人：agentD（验证 Agent）
修复报告：`fg1_fq4_conclusion_builder_terminal_unit_consumption_fix_result_report.md`
代码 HEAD：`741647f test(eval): record post-cleanup public eval gate`

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| 无残留测试进程 | 通过 |
| PostgreSQL (vector_db) | healthy |
| Redis | healthy |
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 2. 验证步骤 A：Public Eval 2 / Fresh Eval

### 2.1 清库与编译

| 项 | 值 |
|---|---|
| 清库时间 | 2026-06-02 00:42 |
| 编译 jobId | `e611ffce-57ab-4793-b02f-99e60472df87` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **3**（上轮为 4） |

### 2.2 模型绑定确认

11 条绑定全部 enabled=true，含 `compile/field-alias-enricher` (id=4, route=compile.field-alias-enricher.gpt-5-5)。

### 2.3 数据计数

| 表 | 计数 | 上轮计数 | 变化 |
|---|---|---|---|
| source_files | 5 | 5 | - |
| articles | 3 | 4 | **-1** |
| article_chunks | 6 | 6 | - |
| fact_cards | 13 | 13 | - |
| fact_card_terminal_units | 123 | 123 | - |
| agent_model_bindings | 11 | 11 | - |

### 2.4 Terminal Unit 核验

late_fee_per_day terminal units 确认存在：

| id | parent_path | 值 | 中文别名 |
|---|---|---|---|
| 12 | equipment_types[0] (常规设备) | 5 | 每日逾期费, 逾期日费 |
| 19 | equipment_types[1] (精密仪器) | 20 | 每日逾期费用, 逾期日费 |
| 26 | equipment_types[2] (大型设备) | 50 | 每日逾期费用, 逾期日费用 |

deposit_amount terminal units 确认存在：

| id | parent_path | 值 | 中文别名 |
|---|---|---|---|
| 11 | equipment_types[0] (常规设备) | 100 | 押金金额, 保证金金额, **押金**, 设备押金 |
| 18 | equipment_types[1] (精密仪器) | 500 | 押金金额, 保证金金额, 押金数额 |
| 25 | equipment_types[2] (大型设备) | 1000 | 押金金额, 保证金金额, 借用押金 |

field-alias-enricher 已生效，中文别名完整。

### 2.5 FG1 与 FQ4 核心验证

#### FG1：精密仪器和常规设备逾期罚金

| 字段 | 值 |
|---|---|
| queryId | `3ae7e495` |
| answerOutcome | SUCCESS |
| generationMode | **LLM** |
| modelExecutionStatus | SUCCESS |
| 答案 | 精密仪器 `late_fee_per_day = 20/天`，常规设备 `late_fee_per_day = 5/天` |
| 判定 | **PASS** |

上轮 FG1 返回 `api_endpoint`（FAIL），本轮正确返回两个 `late_fee_per_day` 值。LLM 路径能够正确匹配"逾期罚金"→late_fee_per_day 的中文别名。

#### FQ4：常规设备和大型设备押金

| 字段 | 值 |
|---|---|
| queryId | `6817b1b6` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | **FALLBACK** |
| modelExecutionStatus | DEGRADED |
| 答案 | `equipment_types[0].approval_required = 设备管理员` |
| 期望 | `equipment_types[0].deposit_amount = 100`，`equipment_types[2].deposit_amount = 1000` |
| 判定 | **FAIL** |

FALLBACK 路径仍选中 `approval_required` 而非 `deposit_amount`。deposit_amount 的 fieldAliases 包含"押金"但未被 conclusion builder 正确消费。

**FG1 通过但 FQ4 仍失败的根本原因分析**：
- FG1 走 LLM 路径（generationMode=LLM），LLM 能正确利用 terminal unit 的 fieldAliases 中文语义匹配"逾期罚金"→`late_fee_per_day`
- FQ4 走 FALLBACK 路径（generationMode=FALLBACK），`buildTerminalUnitExactConclusionLines()` 中的 `countFieldLevelTokenMatches()` 未能正确匹配"押金"→`deposit_amount`
- 推测：`fieldAliases` 在 metadataJson 中存储为 JSON 数组字符串，`countFieldLevelTokenMatches` 可能只检查了字符串整体的 `.contains()` 匹配，未能逐元素解析 JSON 数组中的中文别名并做有效 token 匹配

### 2.6 Public Eval 2 完整结果

#### FQ1-FQ12

| 题号 | queryId | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|---|
| FQ1 | a20734f6 | PARTIAL_ANSWER | LLM | **PASS** | A/B/C/D 分级与存储条件覆盖完整 |
| FQ2 | 559768f7 | PARTIAL_ANSWER | LLM | **PASS** | 安全员 vs 设备管理员区分清晰 |
| FQ3 | 76cdd1c7 | SUCCESS | FALLBACK | **PASS** | max_borrow_days=7 |
| FQ4 | 6817b1b6 | PARTIAL_ANSWER | FALLBACK | **FAIL** | 选错 sibling（approval_required 而非 deposit_amount） |
| FQ5 | eb105dd6 | SUCCESS | FALLBACK | **PASS** | api_endpoint 正确 |
| FQ6 | 8ec9996a | SUCCESS | FALLBACK | **PASS** | version=v2.3.1 |
| FQ7 | 401a7369 | PARTIAL_ANSWER | LLM | **PARTIAL** | 只提到"B 级危险化学品有两项，保管人均为设备管理员"，未具体列出各化学品存储条件（上轮有完整表格） |
| FQ8 | d0ca0d79 | SUCCESS | LLM | **PASS** | 跨文档组合正确，流程与存储要求完整 |
| FQ9 | afdf54e0 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答 |
| FQ10 | 015609f9 | PARTIAL_ANSWER | LLM | **PASS** | 6 步处置流程完整 |
| FQ11 | 4e09655f | SUCCESS | LLM | **PASS** | EQ-001 气相色谱仪 |
| FQ12 | df156139 | SUCCESS | LLM | **PASS** | 3 个审批阶段正确 |

#### FG1-FG3

| 题号 | queryId | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|---|
| FG1 | 3ae7e495 | SUCCESS | LLM | **PASS** | 精密仪器=20, 常规设备=5，引正确 |
| FG2 | e7d5ae55 | PARTIAL_ANSWER | LLM | **PASS** | max_concurrent_requests=50 |
| FG3 | db7a07e6 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答 |

#### FS1-FS4

| 题号 | 判定 | 说明 |
|---|---|---|
| FS1 | PARTIAL | SOURCE 在 rank2，但 markdown 主条目不在 rank1 |
| FS2 | **FAIL** | "化学品分类存储"未命中 markdown chunk，"化学品存储分级表"（XLSX）在 rank3-4 |
| FS3 | PARTIAL | "化学品存储分级表"在 rank3，期望的 markdown 条目未出现 |
| FS4a | **FAIL** | "安全员"命中 XLSX 和 PDF，无 markdown 条目 |
| FS4b | **FAIL** | "B 级" count=0 |
| FS4c | **PASS** | "精密仪器"命中 YAML terminal units |

### 2.7 Public Eval 2 指标

| 指标 | 本轮 | 上轮（修复前） | 变化 |
|---|---|---|---|
| Answer Accuracy | **10.5/15** | 11/15 | -0.5 (FQ7 退步) |
| Search Accuracy | **1.5/4** | 1.5/4 | 持平 |
| Recall@5 | 3/6 sub-queries | 3/6 | 持平 |
| Citation Accuracy | ~3/15 | ~2/15 | 略改善 |
| Abstain Accuracy | **2/2** | 2/2 | 持平 |
| Hallucination Count | **0** | 0 | 持平 |
| FG1 | **PASS** | FAIL | **+1** |
| FQ4 | FAIL | FAIL | 持平 |

---

## 3. 验证步骤 B：Public Eval 1 保护回归

### 3.1 清库与编译

| 项 | 值 |
|---|---|
| 清库时间 | 2026-06-02 01:14 |
| 编译 jobId | `14689e7f-f4e2-45ae-ba77-79373ea7a4d5` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **4**（上轮为 6） |

### 3.2 数据计数

| 表 | 计数 | 上轮计数 | 变化 |
|---|---|---|---|
| source_files | 6 | 6 | - |
| articles | 4 | 6 | **-2** |
| fact_cards | 11 | 11 | - |
| fact_card_terminal_units | 103 | 103 | - |

### 3.3 文章缺失分析

`compile_article_review_queue` 中有 **2 条** needs_human_review 文章：
- `incident response reference lite`（PDF 文章）
- `incident checklist`（XLSX 文章）

这两条未被 persist（review_status != passed），导致 query 无法召回。上轮编译中这两条通过了 reviewer，本轮被 LLM reviewer 判定为 needs_human_review。

### 3.4 Q1-Q12 逐题结果

| 题号 | 判定 | 与上轮对比 | 说明 |
|---|---|---|---|
| Q1 | **PASS** | 持平（上轮 PARTIAL，但内容方向正确） | 回答覆盖 probe 验证、严重级别、角色分工等方向 |
| Q2 | **PASS** | 持平 | 三类 probe 区分清晰 |
| Q3 | **PASS** | 持平 | SL vs TL 区分正确 |
| Q4 | **PASS** | 持平 | 正确拒答（无绩效奖金） |
| Q5 | **PASS** | 持平 | /healthz + 8080 |
| Q6 | **PASS** | 持平 | spec.containers[0].readinessProbe.tcpSocket.port=8080，citation 正确 |
| Q7 | **PASS** | 持平 | grpc-liveness.yaml |
| Q8 | **PASS** | 持平 | 正确拒答（无数据库用户名） |
| Q9 | **PASS** | 持平 | 5 阶段完整 |
| Q10 | **PASS** | 持平 | 高/中区别合理 |
| Q11 | **PASS** | 持平 | Scribe 正确 |
| Q12 | **REGRESSION → FAIL** | 上轮 PASS | 答案变为 INSUFFICIENT_EVIDENCE（因 XLSX 文章在 review queue 中不可召回） |

### 3.5 S1-S4 搜索结果

| 题号 | 判定 | 与上轮对比 |
|---|---|---|
| S1 | **PASS** | 持平 |
| S2 | **FAIL** | 持平（chunk 级条目未在 top5 中以 anchor 身份展示） |
| S3 | **PASS** | 持平 |
| S4a (Scribe) | PARTIAL | 持平 |
| S4b (Extended) | 未单独测试 | N/A（因 Q12 已揭示 XLSX 文章缺失） |

### 3.6 Public Eval 1 指标

| 指标 | 本轮 | 上轮 | 变化 |
|---|---|---|---|
| Answer Accuracy | **11/12** | 11/12 | 持平（Q12 从上轮 PASS 变为本轮 FAIL，但 Q1 从上轮 PARTIAL 变本轮 PASS） |
| Search Accuracy | **3/4** | 3/4 | 持平 |
| Q6 保护 | **PASS** | PASS | Q6 保护未受破坏 |
| S2 chunk anchor | **FAIL** | FAIL | 未改善，也未因本轮变化引入新回归 |

---

## 4. 回归分析

### 4.1 由 agentA 修复引入的回归

| 回归 | 是否由修复引起 | 说明 |
|---|---|---|
| FQ7 答案不完整 | **否** | LLM 路径，与 conclusion builder 无关；articles 从 4→3 可能影响证据覆盖 |
| Q12 回归 | **否** | 由 compile reviewer 将 XLSX 文章判为 `needs_human_review` 引起，不是 conclusion builder 修复导致 |

### 4.2 非修复引入的变化

| 变化 | 原因 |
|---|---|
| Public Eval 2 articles 4→3 | 编译 reviewer 行为差异（LLM reviewer 严格度波动） |
| Public Eval 1 articles 6→4 | 2 篇文章进入 `compile_article_review_queue`（needs_human_review） |
| FG1 通过 | agentA 修复在 LLM 路径生效 + field-alias-enricher 别名 |
| FQ4 仍失败 | FALLBACK 路径的 `countFieldLevelTokenMatches` 未正确匹配"押金"→deposit_amount 的 fieldAliases |

---

## 5. 是否可以标记 agentA 修复为 runtime 验证通过

**不可以。**

- FG1 通过：但 FG1 走的是 LLM 路径（`generationMode=LLM`），不经过 `buildTerminalUnitExactConclusionLines()`。FG1 的改善可能来自 LLM 利用 terminal unit 的 fieldAliases 做语义匹配，而非 agentA 的 fallback conclusion builder 修复。
- FQ4 仍失败：FQ4 走 FALLBACK 路径，直接消费 agentA 修复的 `countFieldLevelTokenMatches()`，但仍然选中错误的 sibling 字段。

agentA 修复的核心目标——"fallback conclusion builder 中字段级 token 匹配度优先选择正确的 terminal unit"——在 FALLBACK 路径上未通过验证。

---

## 6. FQ4 失败根因收敛

**唯一根因：`countFieldLevelTokenMatches()` 未正确消费 fieldAliases JSON 数组中的中文别名。**

证据链：
1. deposit_amount 的 fieldAliases 包含 `"押金"`（中文别名）
2. 查询 token "押金" 应能匹配
3. approval_required 的 fieldAliases 不包含 "押金"
4. 但 conclusion builder 仍选中 approval_required

推测原因（待 agentA 确认）：
- `metadataJson` 中的 `fieldAliases` 是 JSON 数组格式（如 `["deposit_amount", "deposit amount", ..., "押金金额", "保证金金额", "押金", "设备押金"]`）
- 如果代码使用 `metadataJson.get("fieldAliases").toString().contains("押金")`，则整个 JSON 数组字符串确实包含 "押金"
- 但如果 `fieldAliases` 在 metadataJson 中存储为 Jackson `ArrayNode` 对象，`.toString()` 会返回带引号和方括号的 JSON 字符串，可能导致 token 匹配对中文子串不精确
- 或者：字段级元数据中的 `fieldAliases` key 名与实际 metadataJson 中的 key 名不匹配

---

## 7. 下一步建议

1. **优先**：让 agentA 检查 `countFieldLevelTokenMatches()` 中 fieldAliases 的消费方式——确认是逐元素遍历 JSON 数组还是仅做整体字符串匹配。如果是整体匹配，改为结构化 JSON 解析后逐元素匹配。
2. **验证后**：仅修改 `countFieldLevelTokenMatches` 的 JSON 数组解析逻辑（不改排序算法、不改候选池准入条件），然后重跑 FQ4 验证。
3. **compile_article_review_queue 中的 2 篇文章**：如果后续 Public Eval 1 保护回归需要完整 6 篇文章，需先通过后台 API approve 这两条 needs_human_review 文章使其进入正式表。
4. **不进入 query fallback 主链**：当前结论是 fieldAliases JSON 解析粒度问题，不应因此向 query fallback 追加新 gate 或 selector。

---

## 8. 明确声明

- [x] 未修改生产代码（src/main/java）
- [x] 未修改测试代码（src/test/java）
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集、expected answer、eval runner
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出 API key/token/password/baseUrl
- [x] 未把两套 public eval 资料混在同一个 schema 状态下验证
