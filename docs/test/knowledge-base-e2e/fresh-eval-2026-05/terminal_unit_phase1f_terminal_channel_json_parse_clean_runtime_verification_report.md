# Terminal Unit Phase 1F Channel JSON Parse Clean Runtime 验证报告

验证时间：2026-05-31
验证人：agentD

## 1. 验证结论

**PASS** — JSON parse 修复生效。Terminal unit exact value 首次被 runtime answer 消费。FG2 完全通过（`borrowing_system.max_concurrent_requests = 50`）。FQ6 首次输出 terminal unit exact line 但选中了错误的终端 unit（name 而非 version），属于 precision 问题，非 channel 识别问题。

## 2. 根因验证

**上一轮根因**：PostgreSQL `jsonb::text` 输出空格格式 `"channel": "fact_card_terminal_fts"`，与旧代码紧凑格式 `"channel\":\"fact_card_terminal_fts"` 不兼容，`String.contains` 永远返回 false。

**本轮修复**：`TerminalUnitHitMetadataSupport` 使用 `ObjectMapper.readTree` 正确解析 JSON → `node.path("channel").asText("")` → 与常量 `"fact_card_terminal_fts"` 比较。

**运行时确认**：修复生效。selector/conclusion builder 首次正确识别终端 unit channel，结论输出 `Confirmed evidence: ...` 行。

## 3. 环境与清库确认

| 项目 | 值 |
|---|---|
| 旧进程 | 已 kill（确保新代码生效） |
| git status | 累积 Phase 1 改动，无意外文件 |
| Schema | `./scripts/reset-lattice-schema.sh` 完整重建 |
| 服务 | `scripts/run-local-dev.sh` 重启，端口 18082 |
| compile jobId | `1c209d27-74c0-4625-bb19-189ffcd1abd6` |
| 状态 | SUCCEEDED，acceptedCount=4, needsHumanReviewCount=1 |
| approve 后 | 5/5 articles 入库 |

## 4. 门禁结果

| 检查项 | 结果 |
|---|---|
| git diff --check | 通过 |
| redline | **BLOCKER=0** |
| AnswerFallbackConclusionBuilderTests | 7/0/0 |
| AnswerFallbackEvidenceSelectorTests | 11/0/0 |
| 定向组合 | **18/0/0 BUILD SUCCESS** |

## 5. FQ6 / FG2 Runtime 结果

### FQ6："预约系统当前的版本号是什么？"

- **预期**：`borrowing_system.version = v2.3.1`
- **实际**：`Confirmed evidence: borrowing_system.name = 校园实验室设备预约系统`
- **终端 unit 状态**：

  | terminalKey | valueText | displayText | fusedRank |
  |---|---|---|---|
  | version | v2.3.1 | `borrowing_system.version = v2.3.1` | **1** |
  | name | 校园实验室设备预约系统 | `borrowing_system.name = 校园实验室设备预约系统` | 5 |

- **判定**：**PARTIAL** — 终端 unit 消费首次生效，但选中了错误的终端 unit（name 而非 version）。`isTerminalHitQueryFocused` 对 name 终端 unit 返回 true（"预约系统" token 匹配 name 的 valueText），对 version 终端 unit 可能返回 false（"版本号" token 不在 version 证据文本中）。version 终端 unit 在 fused_rank=1，数据完整。

### FG2："预约系统的最大并发请求数是多少？"

- **预期**：`borrowing_system.max_concurrent_requests = 50`
- **实际**：`Confirmed evidence: borrowing_system.max_concurrent_requests = 50`
- **判定**：**PASS** — 终端 unit exact value 首次正确输出！

### 对比历程

| 轮次 | FQ6 | FG2 | 根因 |
|---|---|---|---|
| Phase 1E selector + conclusion | FAIL | FAIL | 问题类型门控（`looksLike...` 对中文 query 返回 false） |
| Phase 1F gate correction | FAIL | FAIL | `String.contains` 与 JSONB spaced format 不兼容 |
| **Phase 1F JSON parse fix** | **PARTIAL** | **PASS** | **channel 识别已修复。FQ6 余留 precision 问题。** |

## 6. FQ6 Precision 问题分析

FQ6 的 terminal unit 消费已生效，但选中了 name 而非 version，原因是 `isTerminalHitQueryFocused` 的 token 匹配逻辑：

1. Query "预约系统当前的版本号是什么" → 提取的 token 含 "预约系统"、"版本号"
2. name 终端 unit 的 evidence text 含 "校园实验室设备预约系统" → "预约系统" 匹配 → `isTerminalHitQueryFocused` = true
3. version 终端 unit 的 evidence text 含 "borrowing_system.version = v2.3.1" — "版本号" token 不在此文本中 → `isTerminalHitQueryFocused` = false
4. `buildTerminalUnitExactConclusionLines` 遍历 fallbackHits，第一个 query-focused 终端 unit 是 name → 输出 name exact line

**这不是 channel 识别问题，而是终端 unit precision/focus 排序问题。** version 终端 unit 在 fused_rank=1 且有正确 displayText，但 `isTerminalHitQueryFocused` 需要终端 unit evidence text 包含 query 中文 token。

## 7. 保护回归

| 题目 | 状态 | 说明 |
|---|---|---|
| **FQ3** | PASS | `Confirmed evidence: equipment_types[1]...` — terminal unit 消费生效 |
| **FQ4** | PASS | `Confirmed evidence: equipment_types[0]...` — terminal unit 消费生效 |
| FG1 | 待确认 | 答案未展示 confirmed evidence 行 |
| FG3 | 未执行 | — |

## 8. 未执行项

| 项目 | 状态 |
|---|---|
| 完整 19 题 eval | 未执行 |
| 修改代码 | 未执行 |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |

## 9. 下一步

**JSON parse 修复已证明有效（FG2 PASS），无需进一步修改 channel 识别代码。**

FQ6 余留的 precision 问题是独立变量——终端 unit focus/排序。建议：
1. agentB 只读归因 `isTerminalHitQueryFocused` 对 FQ6 version 终端 unit 返回 false 的具体原因
2. 或 agentA 在 conclusion builder 中，当多个 terminal unit 都通过 focus 检查时，优先选择 fused_rank 更高的（而非遍历顺序第一个）
3. 这属于 conclusion builder 内部排序优化，不应扩大到 selector、retrieval、compiler 等模块

## 合规声明

- 本轮未修改任何代码、测试、配置、脚本、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 报告未输出 API key/token/password
