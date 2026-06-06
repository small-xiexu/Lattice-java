# FG2 Terminal Citation Binding — 逐步骤追踪分析报告

分析时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读根因追踪，无代码修改

---

## 1. 现象复述

**来源**：`fallback_terminal_citation_binding_runtime_gate_report.md`

| 题号 | claim | cov | status | reason | overlap |
|------|-------|:---:|--------|--------|:---:|
| FG2 | `borrowing_system.max_concurrent_requests = 50` | **0.0** | DEMOTED | source_insufficient_overlap | 0.600 |

FQ4 和 FG1 的 terminal unit evidence 路径均已命中（FQ4: cov=0.0→1.0, FG1: cov=0.5→1.0），但 FG2 的同一路径未命中，走回了原有 source file overlap 路径。

---

## 2. 数据库只读证据

### 2.1 Citation 记录

| 字段 | 值 |
|------|-----|
| claim_text | `Confirmed evidence: borrowing_system.max_concurrent_requests = 50` |
| citation_literal | `[→ equipment-borrowing-policy.yaml]` |
| validation_status | `DEMOTED` |
| validated_by | `RULE` |
| overlap_score | `0.600` |
| reason | `source_insufficient_overlap` |

### 2.2 Terminal Unit 记录

| 字段 | 值 |
|------|-----|
| display_text | `borrowing_system.max_concurrent_requests = 50` |
| value_text | `50` |
| normalized_value | `50` |
| terminal_key | `max_concurrent_requests` |
| key_path | `borrowing_system.max_concurrent_requests` |
| source_file_id | **2** |

### 2.3 Source File 记录

| 字段 | 值 |
|------|-----|
| id | **2** |
| file_path | `equipment-borrowing-policy.yaml` |

**source_file_id 一致**：terminal unit 的 `source_file_id=2` 与 source file 的 `id=2` 匹配。

### 2.4 Answer Markdown

```
## 证据
- Confirmed evidence: borrowing_system.max_concurrent_requests = 50 [→ equipment-borrowing-policy.yaml]
```

单行 bullet，格式与 FQ4/FG1 完全一致。

---

## 3. 源码路径逐步分析（手工复算）

### Step 1: `extractHardFactTokens("Confirmed evidence: borrowing_system.max_concurrent_requests = 50")`

`normalizeForHardFactExtraction` 后文本不变（无 `**`、无 backtick、无 `[[...]]`、无 `[→ ...]`）。

| 模式 | 匹配文本 | 归一化 token |
|------|----------|-------------|
| `NUMERIC_LITERAL` | `50` | `50` |
| `SNAKE_CASE` | `borrowing_system` | `borrowing_system` |
| `SNAKE_CASE` | `max_concurrent_requests` | `max_concurrent_requests` |
| `LATIN_TERM` | `Confirmed` | `confirmed` |
| `LATIN_TERM` | `evidence` | `evidence` |
| `LATIN_TERM` | `borrowing_system.max_concurrent_requests` | `borrowing_system.max_concurrent_requests` |

**hardFactTokens = [50, borrowing_system, max_concurrent_requests, confirmed, evidence, borrowing_system.max_concurrent_requests]（6 个唯一 token）**

### Step 2: `isKeyValueClaim("Confirmed evidence: borrowing_system.max_concurrent_requests = 50")`

- `eqIndex = claimText.indexOf('=')` → 找到 `=`
- `left = claimText.substring(0, eqIndex).stripTrailing()` → `"Confirmed evidence: borrowing_system.max_concurrent_requests "` → 非空 ✓
- `right = claimText.substring(eqIndex + 1).stripLeading()` → `"50"` → 非空 ✓
- **返回 true** ✓

### Step 3: `buildSingleUnitEvidenceText(unit)`

- displayText: `borrowing_system.max_concurrent_requests = 50`
- valueText: `50`
- normalizedValue 与 valueText 相同，跳过

**evidenceText = `"borrowing_system.max_concurrent_requests = 50 50"`**

### Step 4: `claimValueMatchesUnit("Confirmed evidence: ...", unit)`

- `extractClaimValuePart` → 提取 `=` 右侧 → `"50"`
- `normalizeToken("50")` → `"50"`
- `buildUnitValueText(unit)` → `"50"`
- `unitValueText.contains("50")` → **true** ✓
- **返回 true** ✓

### Step 5: `calculateOverlapScore(hardFactTokens, evidenceText)`

**tokenize(evidenceText)**：
- 分割 `[^\p{IsAlphabetic}\p{IsDigit}_./-]+` → 产生 `["borrowing_system.max_concurrent_requests", "50", "50"]`
- `appendCompositeTokenParts` 拆解 → 追加 `["borrowing_system", "max_concurrent_requests"]`
- evidenceTokens = **{borrowing_system.max_concurrent_requests, 50, borrowing_system, max_concurrent_requests}（4 个）**

**Overlap 计算**：

| claimToken | 在 evidenceTokens 中？ | matchedCount |
|------------|:---:|:---:|
| `50` | ✓ | 1 |
| `borrowing_system` | ✓ | 2 |
| `max_concurrent_requests` | ✓ | 3 |
| `confirmed` | ✗ | 3 |
| `evidence` | ✗ | 3 |
| `borrowing_system.max_concurrent_requests` | ✓ | 4 |

**overlapScore = 4 / 6 = 0.6667**

### Step 6: 阈值检查

```
overlapScore >= 1.0 ? → 0.6667 < 1.0 → NO

isHighConfidencePartialOverlap(6 tokens, 0.6667):
  → (6 >= 4 && 0.6667 >= 0.75) → 0.6667 < 0.75 → NO
  → (6 >= 2 && 0.6667 >= 0.66) → 0.6667 >= 0.66 → YES
```

**手工复算结论：`isHighConfidencePartialOverlap` 应返回 true，terminal unit evidence 路径应返回 VERIFIED（reason: `terminal_unit_evidence_near_complete_verified`）。**

---

## 4. 与 FQ4/FG1 成功案例对比

| 维度 | FQ4 (PASS) | FG1 (PASS) | FG2 (FAIL) |
|------|:---:|:---:|:---:|
| claim 格式 | `Confirmed evidence: equipment_types[N].field = value` | 同左 | `Confirmed evidence: borrowing_system.field = value` |
| 含 `=` | 是 | 是 | 是 |
| `isKeyValueClaim` | 应通过 | 应通过 | 手工复算通过 |
| terminal unit 存在 | 是 | 是 | 是 |
| `claimValueMatchesUnit` | 应通过 | 应通过 | 手工复算通过 |
| hardFactTokens 数 | ~5（无 "Confirmed evidence:" 污染？） | ~5 | **6**（含 `confirmed`, `evidence`） |
| overlapScore | 0.6667 | ~0.75 | 手工复算 0.6667 |
| `isHighConfidencePartialOverlap` | 通过 | 通过 | 手工复算应通过 |
| runtime 实际 | VERIFIED ✓ | VERIFIED ✓ | **DEMOTED** ✗ |

**FQ4/FG1 也可能包含 `confirmed`/`evidence` token**（claim 前缀相同），但仍通过了 terminal unit 路径。FG2 的失败不能简单归因于 claim 前缀污染。

---

## 5. 根因判断

### 手工复算 vs Runtime 差异

手工复算表明 terminal unit evidence 路径**应该**通过（overlapScore=0.6667, 6 tokens ≥ 2, 0.6667 ≥ 0.66）。但 runtime 显示该路径**未命中**（reason 为 `source_insufficient_overlap`，而非 `terminal_unit_evidence_*`）。

### 可能原因（按概率排序）

| # | 假说 | 可能性 | 如何验证 |
|---|------|:---:|------|
| **A** | **Projection 路径将 citation 重映射为 ARTICLE 类型**：`QueryAnswerProjectionBuilder` 构建的 `AnswerProjection.sourceType` 可能不是 `SOURCE_FILE`。投影路径在 `validateAgainstProjection` 中创建 `projectedCitation`，其 `sourceType` 由 `mapProjectionSourceType(answerProjection)` 决定。如果返回 `ARTICLE`，validator 进入 ARTICLE 分支，terminal unit evidence 路径不被触发。 | **高** | 检查 `query_answer_citations.target_key` 是否等于文章 key 还是文件 path |
| **B** | **claimText 在 validator 内部与 DB 存储值不同**：`citation.getClaimText()` 在 validator 中返回的文本可能与 DB 中 `query_answer_claims.claim_text` 不同（例如经过了额外的 trim 或前缀剥离） | 中 | 在 `validateAgainstTerminalUnitEvidence` 入口加 SLF4J log |
| **C** | **Token 提取在 runtime 与手工分析有差异**：`LATIN_TERM_PATTERN` 的边界条件在特定 JVM/输入下与手工分析不同 | 低 | jshell 或单元测试验证 |
| **D** | **Overlap 浮点精度问题**：`4.0/6.0` 在某些极端情况下可能产生 `0.6666666666666666 < 0.66` | 极低 | Java double 不可能 |

### 假说 A 的进一步分析

从 DB 数据看：
- `reason = source_insufficient_overlap` — 这表明走的是 **source file overlap 路径**
- `validated_by = RULE` — 非 projection 路径
- `target_key = equipment-borrowing-policy.yaml` — 源文件路径

如果假说 A 成立（projection 映射为 ARTICLE），则：
- `validated_by` 仍可能是 `RULE`（projection 验证通过 projection record 后调用 `citationValidator.validate(projectedCitation)`）
- 但如果 `projectedCitation.sourceType = ARTICLE`，validator 会走 ARTICLE 分支而非 SOURCE_FILE 分支
- ARTICLE 分支不触发 terminal unit evidence 检查
- 最终 reason 应为 `article_insufficient_overlap` 而非 `source_insufficient_overlap`

DB 中的 reason 是 `source_insufficient_overlap`，这直接排除了假说 A。**validator 确实进入了 SOURCE_FILE 分支**。

### 假说 B 的进一步分析

如果 `citation.getClaimText()` 在 validator 中与 DB 存储值不同，最可能的差异是：
- 前缀未被包含（如 claimText 仅为 `borrowing_system.max_concurrent_requests = 50`，无 "Confirmed evidence: "）
- 或末尾有额外字符

如果 claimText 不含 "Confirmed evidence: " 前缀，则：
- hardFactTokens 不含 `confirmed`、`evidence`
- token 数 = 4（50, borrowing_system, max_concurrent_requests, borrowing_system.max_concurrent_requests）
- overlapScore = 4/4 = 1.0 → **直接 VERIFIED** → 不匹配 runtime

如果 claimText 与 DB 一致（含前缀），则手工复算结果应成立。

**无论 claimText 是否含前缀，overlap 都应在理论上通过 threshold。两者都不能解释 runtime 失败。**

### 结论：当前证据不足以在纯源码层面定位根因

手工复算与 runtime 行为存在不可调和的矛盾。所有可验证的 guard（`isKeyValueClaim`、`claimValueMatchesUnit`、overlap threshold）在手工复算中均通过。runtime 却未进入 terminal unit evidence 路径。

**需要在 `validateAgainstTerminalUnitEvidence` 中增加运行时 DEBUG 日志，才能确认真实执行路径。**

---

## 6. 需要补证的运行时证据

以下信息无法通过源码只读分析获得，必须通过在 `validateAgainstTerminalUnitEvidence` 中加临时 SLF4J log 或 DEBUG 断点获取：

| # | 需要确认的信息 | 如何获取 |
|---|---------------|----------|
| 1 | `citation.getClaimText()` 在 validator 中的确切值 | `log.debug("claimText=[{}]", citation.getClaimText())` |
| 2 | `isKeyValueClaim` 返回值 | log |
| 3 | `findBySourceFileId(sourceFileRecord.getId())` 返回的 unit 列表大小 | log |
| 4 | 对每条 unit：`buildSingleUnitEvidenceText` 的输出 | log |
| 5 | 对每条 unit：`claimValueMatchesUnit` 返回值 | log |
| 6 | 对每条 unit：`calculateOverlapScore` 的输入 token 和得分 | log |
| 7 | `isHighConfidencePartialOverlap` 的输入参数和返回值 | log |
| 8 | 方法最终的返回值（null 还是 VERIFIED） | log |

**特别关键**：确认 `citation.getClaimText()` 是否等于 DB 中存储的 `"Confirmed evidence: borrowing_system.max_concurrent_requests = 50"`。如果 validator 收到的 claimText 不同（例如在 projection 路径中被重写），一切手工复算的前提都不成立。

---

## 7. 建议的下一步动作

### 选项 A（推荐）：agentA 加 DEBUG log 后，agentD 重新 runtime gate

1. agentA 在 `validateAgainstTerminalUnitEvidence` 方法入口和每个 guard 处加 `log.info` 级别日志（不是 debug，确保默认输出）
2. agentD 清库 + 重编译 + 重启服务 + 查询 FG2
3. agentD 抓取日志，agentB 根据日志做最终归因
4. 定位后 agentA 做最小修复，agentA 移除临时日志

**优点**：一次 runtime 即可拿到全部证据，不需要猜测。
**缺点**：需要两轮 agent 协作（A 加 log → D 跑 gate → B 分析 → A 修复）。

### 选项 B：agentB 继续用 jshell 做本地 token extraction + overlap 复算

使用 `jshell --class-path target/classes` 手动调用 `CitationValidator.extractHardFactTokens()` 和 `tokenize()`，验证手工复算的正确性。但无法模拟 projection 路径中的 claimText 重写。

**优点**：纯只读，不需要 agentA 介入。
**缺点**：无法确认 runtime projection 路径中的实际 claimText 值。

### 选项 C：直接假设根因并修复

假设根因为 claim 前缀 "Confirmed evidence: " 中的 `confirmed` 和 `evidence` token 污染了 hardFactTokens，导致分母变大（6 而非 4），overlap 从 1.0 降到 0.6667，接近但不满足某些边界条件。

修复方向：在 `extractHardFactTokens` 中增加通用前缀剥离规则（如移除 `Confirmed evidence:`、`Additional evidence:` 等 fallback 结论模板前缀）。

**不推荐**：这是在猜根因，不是在定位根因。而且即使移除前缀后 overlap=1.0，手工复算中 0.6667 已经满足 threshold，前缀污染不是 FG2 失败的原因。

---

## 8. 推荐方案：选项 A

**理由**：
1. 手工复算无法复现 runtime 行为——说明存在源码分析无法覆盖的运行时变量
2. 唯一可靠的方式是在 runtime 中打印实际执行路径
3. 加 log 是纯观测性改动，不影响业务逻辑
4. 定位后移除 log 即可，不留技术债务

### agentA 提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
在 CitationValidator.validateAgainstTerminalUnitEvidence 方法中加临时观测日志，
用于定位 FG2 terminal unit evidence 路径未命中的确切原因。

修改范围：
- 只修改 src/main/java/com/xbk/lattice/query/citation/CitationValidator.java
- 只修改 validateAgainstTerminalUnitEvidence 方法
- 不改其他文件

修改要求：
在关键路径点加 log.info 级别日志（确保默认输出，不依赖 log level 配置）：

1. 方法入口：log.info("[TU_CIT_TRACE] enter claimText=[{}] sourceFileId=[{}]", ...)
2. isKeyValueClaim 结果：log.info("[TU_CIT_TRACE] isKeyValueClaim=[{}]", ...)
3. terminalUnits 数量：log.info("[TU_CIT_TRACE] terminalUnits.size=[{}]", ...)
4. 每条 unit 遍历：log.info("[TU_CIT_TRACE] unit[{}] displayText=[{}] valueText=[{}]", ...)
5. claimValueMatchesUnit 结果：log.info("[TU_CIT_TRACE] claimValueMatchesUnit=[{}]", ...)
6. calculateOverlapScore：log.info("[TU_CIT_TRACE] overlapScore=[{}] hardFactTokens=[{}] evidenceTokens=[{}]", ...)
7. isHighConfidencePartialOverlap：log.info("[TU_CIT_TRACE] isHighConfidencePartialOverlap=[{}] tokens=[{}] score=[{}]", ...)
8. 最终返回：log.info("[TU_CIT_TRACE] result=[{}]", ...)

日志 TAG 使用 [TU_CIT_TRACE] 便于 agentD 过滤。

禁止事项：
- 禁止修改任何业务逻辑、guard 条件、threshold
- 禁止修改 isKeyValueClaim / claimValueMatchesUnit / calculateOverlapScore
- 禁止修改其他方法
- 禁止提交 commit

agentD runtime gate：
- 清库 + 重编译 + 查询 FG2
- 抓取含 [TU_CIT_TRACE] 的日志
- 交给 agentB 做最终归因
- agentB 归因后 agentA 移除日志 + 做最小修复
```

---

## 9. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 所有结论基于源码只读分析 + 数据库 citation 表直接查询 + 手工 token/overlap 复算
- [x] 手工复算表明 terminal unit evidence 路径应通过，但 runtime 未命中——存在源码分析无法覆盖的运行时变量
- [x] 推荐通过加临时 DEBUG 日志获取运行时证据，而非直接猜根因修代码
- [x] 不建议修改 overlap threshold、不建议放宽 guard、不建议写 case 特判
