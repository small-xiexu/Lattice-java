# Terminal Channel Candidate Supply 修订 — Runtime Gate 验证报告

验证时间：2026-06-02 23:54 ~ 2026-06-03 00:30
执行人：agentD（验证 Agent）
修复报告：`fq4_fg1_terminal_channel_candidate_supply_fix_revision_report.md`

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 2. Public Eval 2 / Fresh Eval

### 2.1 编译

| 项 | 值 |
|---|---|
| reviewMode | LLM（默认） |
| 编译 jobId | `69a59fd5-3cf3-48dc-8c84-55f931d8545c` |
| 编译结果 | **SUCCEEDED**（经历一次 LLM 网关 502 恢复） |
| persistedCount | **3**（5→approve 2→5） |
| compile_article_review_queue | 2 条，已 approve |

### 2.2 数据计数

| 表 | 计数 |
|---|---|
| articles | 5（approve 后） |
| fact_cards | 13 |
| fact_card_terminal_units | 123 |

### 2.3 Terminal Unit 核验

| terminal_key | values | 中文别名确认 |
|---|---|---|
| deposit_amount | 100 / 500 / 1000 | ✅ |
| late_fee_per_day | 5 / 20 / 50 | ✅ |

### 2.4 FQ4 与 FG1 核心验证

| 题号 | answerOutcome | generationMode | fallbackReason | 答案 | 判定 |
|---|---|---|---|---|---|
| FQ4 | PARTIAL_ANSWER | **FALLBACK** | CITATION_QUALITY_INSUFFICIENT | `equipment_types[0].approval_required = 设备管理员` | **FAIL** |
| FG1 | SUCCESS | **FALLBACK** | DETERMINISTIC_EXACT_LOOKUP_PREFERRED | 通用政策描述（borrowing_system api_endpoint）+ equipment_types 概述 | **FAIL** |

**candidate supply 修订（DB limit 扩展 + 字段意图优先排序）未修复 FQ4/FG1 的 FALLBACK 路径。目标 terminal units 仍未被选中。**

### 2.5 其他题目回归

| 题号 | 判定 |
|---|---|
| FQ3 | PASS |
| FQ5 | PASS |
| FQ6 | PASS |
| FQ9 | PASS（正确拒答） |
| FG2 | PASS |
| FG3 | PASS（正确拒答） |

### 2.6 搜索

| 题号 | 判定 |
|---|---|
| FS4b (B级) | **FAIL**（count=0） |
| FS4c (精密仪器) | PASS |

### 2.7 Public Eval 2 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **13/15**（FQ4=FAIL, FG1=FAIL） |
| Search Accuracy | ~1.5/4 |
| Abstain Accuracy | 2/2 |
| Hallucination | 0 |

---

## 3. Public Eval 1 保护回归

### 3.1 编译

| 项 | 值 |
|---|---|
| jobId | `b24f65fd-70ee-46b7-aa91-4429e01d52a8` |
| persistedCount | **0**（6 条全部 needs_human_review，已 approve） |

### 3.2 保护回归

| 题号 | 判定 | 说明 |
|---|---|---|
| Q6 | **PASS** | FALLBACK, spec.containers[0].readinessProbe.tcpSocket.port=8080 |
| Q12 | **REGRESSION** | FALLBACK, SUCCESS 但答案错误——通用文档描述，非"Extended" |
| S2 | **FAIL** | "下一步计划"内容在 rank2 fact card 中，但非 chunk anchor |

---

## 4. 结论

### 4.1 terminal candidate supply 修订是否 runtime 生效

**修订已编译部署（mvn compile + 服务重启），但未修复 FQ4/FG1。**

FQ4 仍选中 `approval_required`，FG1 仍给出通用政策概述。目标 terminal units（deposit_amount=100/1000, late_fee_per_day=5/20）存在于数据库且有中文别名，但查询时未被 FALLBACK conclusion builder 选中。

### 4.2 FQ4 是否修复

**否。** FALLBACK 路径仍选中错误 sibling（approval_required）。

### 4.3 FG1 是否修复

**否。** FALLBACK 路径答案不含 late_fee_per_day 值。

### 4.4 是否有新增回归

Q12 出现新回归——FALLBACK 路径答案从"Extended"变为通用文档描述。根因待定（可能是编译 reviewer 严格度导致 XLSX 文章内容变化）。

### 4.5 唯一下一步根因

**FALLBACK conclusion builder 中的 `buildTerminalUnitExactConclusionLines` 仍未被有效消费。** DB 侧候选扩展和 reranker 字段意图排序可能已生效，但 fallbackHits 进入 conclusion builder 后的候选选择逻辑仍有问题。

需要：在 `buildTerminalUnitExactConclusionLines` 中做 runtime trace，打印每个 terminal unit 候选的 `exactLine`、`isQueryFocused`、`fieldTokenMatchCount`、`fusedScore`，确认 deposit_amount 和 late_fee_per_day 是否已出现在 candidate 池中、以及它们的 fieldTokenMatchCount 是否高于 approval_required/api_endpoint。

---

## 5. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未把两套 eval 混在同一 schema
- [x] 未把 LLM 路径 PASS 写成 FALLBACK 路径已修复
- [x] 未混修 FS2/FS4b/S2 搜索问题
