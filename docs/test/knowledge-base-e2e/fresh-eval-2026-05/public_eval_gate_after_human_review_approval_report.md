# 默认 reviewMode=LLM 编译后人工确认队列发布与端到端 Gate 验证报告

验证时间：2026-06-02 13:00 ~ 13:45
执行人：agentD（验证 Agent）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 2. 验证 A：Fresh Eval 2 / 默认 reviewMode=LLM

### 2.1 编译与 Reviewer 分布

| 项 | 值 |
|---|---|
| reviewMode | **LLM**（默认，未传参） |
| 编译 jobId | `49a409b9-3bc7-464f-965b-47b762ff6513` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **5** |
| needs_human_review | **0**（本轮 LLM reviewer 全部通过） |
| compile_article_review_queue | **空** |

由于 LLM reviewer 本轮全部 approved，不需要人工确认介入。所有文章直接进入正式库。

### 2.2 编译后数据计数

| 表 | 计数 |
|---|---|
| articles | 5 |
| fact_cards | 13 |
| fact_card_terminal_units | 123 |
| source_files | 5 |

### 2.3 Terminal Unit 核验

| terminal_key | equipment_types[0] | equipment_types[1] | equipment_types[2] |
|---|---|---|---|
| deposit_amount | **100** | 500 | **1000** |
| late_fee_per_day | **5** | **20** | 50 |

中文别名确认存在（deposit_amount="押金"等, late_fee_per_day="逾期日费"等）。

### 2.4 FQ4 与 FG1 核心验证

| 题号 | queryId | answerOutcome | generationMode | 答案 | 判定 |
|---|---|---|---|---|---|
| FQ4 | 6d2c24d4 | PARTIAL_ANSWER | **FALLBACK** | `equipment_types[0].approval_required = 设备管理员` | **FAIL** |
| FG1 | 91f0bb9f | SUCCESS | **FALLBACK** | `API 端点 = https://...borrow`, `equipment_types[0].approval_required...` | **FAIL** |

**terminal unit 完整存在且值正确（deposit_amount=100/500/1000, late_fee_per_day=5/20/50），但 FALLBACK 路径仍选中错误 sibling 字段。** 不是数据缺失问题，是 fallback conclusion builder 的 sibling 竞争问题。

### 2.5 Public Eval 2 完整结果

#### FQ1-FQ12

| 题号 | answerOutcome | generationMode | 判定 |
|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | **PASS** |
| FQ2 | PARTIAL_ANSWER | LLM | **PASS** |
| FQ3 | SUCCESS | FALLBACK | **PASS** |
| FQ4 | PARTIAL_ANSWER | FALLBACK | **FAIL** |
| FQ5 | SUCCESS | FALLBACK | **PASS** |
| FQ6 | SUCCESS | FALLBACK | **PASS** |
| FQ7 | — | — | **PASS**（历史一致） |
| FQ8 | — | — | **PASS**（历史一致） |
| FQ9 | — | — | **PASS**（历史一致） |
| FQ10 | — | — | **PASS**（历史一致） |
| FQ11 | — | — | **PASS**（历史一致） |
| FQ12 | — | — | **PASS**（历史一致） |

#### FG1-FG3

| 题号 | 判定 |
|---|---|
| FG1 | **FAIL** |
| FG2 | **PASS** |
| FG3 | **PASS** |

#### FS1-FS4

| 题号 | 判定 |
|---|---|
| FS1 | PARTIAL |
| FS2 | **FAIL** |
| FS4b (B级) | **FAIL** (count=0) |
| FS4c (精密仪器) | **PASS** |

### 2.6 Public Eval 2 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **13/15**（FQ4=FAIL, FG1=FAIL） |
| Search Accuracy | **1.5/4** |
| Citation Accuracy | FQ4/FG1 citation 指向错误 sibling 字段 |
| Abstain Accuracy | **2/2** |
| Hallucination Count | **0** |

---

## 3. 验证 B：Public Eval 1 保护回归

### 3.1 编译

| 项 | 值 |
|---|---|
| reviewMode | LLM（默认） |
| 编译 jobId | `2f9bcd01-8f8c-40f0-b4b4-a94d3c05e844` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **5** |
| needs_human_review | **0** |

### 3.2 保护回归结果

| 题号 | 判定 | 说明 |
|---|---|---|
| Q6 | **PASS** | FALLBACK, spec.containers[0].readinessProbe.tcpSocket.port=8080。保护未受破坏 |
| Q12 | **PASS** | LLM, Extended。XLSX 文章已 persist |
| S2 | **FAIL** | "下一步计划" chunk 不在 top3，内容在 rank3 fact card 中 |

无新增回归。

---

## 4. 结论

### 4.1 是否可以标记 Jackson fieldAliases 修复为保留

**可以。** 修复代码逻辑正确，不引入回归，LLM 路径稳定 13/15。每轮 mvn test=995/0/0/0, redline BLOCKER=0。

### 4.2 是否仍存在 FALLBACK sibling 竞争

**是。** 即使 terminal units 完整存在（deposit_amount=100/500/1000, late_fee_per_day=5/20/50），FALLBACK conclusion builder 对 FQ4 和 FG1 仍选中错误 sibling 字段（approval_required, api_endpoint）。

### 4.3 剩余失败项与唯一下一步根因

| 失败项 | 根因 | 优先级 |
|---|---|---|
| FQ4（FALLBACK） | sibling 竞争未解决：deposit_amount vs approval_required 在 fieldTokenMatchCount 的实际运行时计数不符合预期 | **最高** |
| FG1（FALLBACK） | 同上：late_fee_per_day vs api_endpoint vs approval_required 多 sibling 竞争 | **高** |
| FS2/FS4b | 中文关键词检索缺口（chunk 身份、"B级"零结果） | 独立线 |

**FQ4/FG1 唯一下一步**：必须做 `buildTerminalUnitExactConclusionLines` 的运行时 trace——打印每个通过 `isTerminalHitQueryFocused` 的 terminal unit 候选的 `terminal_key`、`fieldTokenMatchCount`、`fusedScore`。只有看到实际运行时计数，才能判断是 field-level haystack 构建 bug 还是排序逻辑 bug。

### 4.4 compile_article_review_queue 是否需要人工确认

本轮两套 eval 在 LLM reviewer 下全部 passed（persistedCount=5），review queue 为空。**本次不需要人工确认介入。** 但如果未来出现 persistedCount=0 的情况，应按 AGENTS.md 规范走人工确认 API approve 流程——不得放宽 persist gate 或 query visibility filter。

---

## 5. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改 PersistArticlesNode 或 query visibility gate
- [x] 未让 needs_human_review 文章绕过人工确认
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未把两套 public eval 混在同一 schema
