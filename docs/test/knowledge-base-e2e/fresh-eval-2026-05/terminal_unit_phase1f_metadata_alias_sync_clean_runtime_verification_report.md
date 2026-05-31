# Terminal Unit Phase 1F Metadata Alias Sync Clean Runtime 验证报告

验证时间：2026-05-31
验证人：agentD

## 1. 验证结论

**PASS** — Metadata alias sync 修复生效。DB 层 field_aliases_json 与 metadata_json.fieldAliases 已同步；query audit 中 metadata.fieldAliases 含中文 alias（"版本号/系统版本/接口版本"）。FG2 持续 PASS。FQ6 余留 precision 问题（name vs version），非 metadata sync 问题。

## 2. DB 层 Metadata 同步验证

### borrowing_system.version = v2.3.1

| 字段 | 值 | 状态 |
|---|---|---|
| field_aliases_json | `["version", ..., "版本号", "系统版本", "接口版本"]` | ✓ 含中文 |
| metadata_json.fieldAliases | `["version", ..., "版本号", "系统版本", "接口版本"]` | ✓ 已同步 |
| fts_text | 含 "版本号 系统版本 接口版本" | ✓ |
| display_text | `borrowing_system.version = v2.3.1` | ✓ |
| value_text | `v2.3.1` | ✓ |
| source file | equipment-borrowing-policy.yaml | ✓ |

**field_aliases_json 与 metadata_json.fieldAliases 完全一致，中文 alias 已同步。**

## 3. Query Audit 验证

FQ6 的 version 终端 unit (fused_rank=1) 的 metadata.fieldAliases：

```json
["version", "borrowing_system.version", ..., "版本号", "系统版本", "接口版本"]
```

**query audit 中的 metadata.fieldAliases 包含中文 alias，与 DB 同步一致。** Reranker 可通过 `parseProfile()` 读取到中文 alias 用于 `fieldMatchCount` 计算。

## 4. Runtime 结果

### FQ6："预约系统当前的版本号是什么？"

- 预期：`borrowing_system.version = v2.3.1`
- 实际：`Confirmed evidence: borrowing_system.name = 校园实验室设备预约系统`
- 判定：**PARTIAL** — 终端 unit 消费生效，但选中了 name 而非 version。version TU 在 fused_rank=1，metadata alias 已同步（含 "版本号"），但 `isTerminalHitQueryFocused` 的 evidence haystack 构造未包含 metadata alias 文本，导致 "版本号" token 不匹配 version TU。

### FG2："预约系统的最大并发请求数是多少？"

- 实际：`Confirmed evidence: borrowing_system.max_concurrent_requests = 50`
- 判定：**PASS** ✓

## 5. 门禁结果

| 检查项 | 结果 |
|---|---|
| git diff --check | 通过 |
| redline | **BLOCKER=0** |
| EnricherTests | 15/0/0 |
| RerankerTests | 10/0/0 |
| 定向组合 | **25/0/0 BUILD SUCCESS** |

## 6. 编译结果

| 项目 | 值 |
|---|---|
| jobId | `c65197c6-cc9b-4f10-bad4-75dc9a6a93c9` |
| 状态 | SUCCEEDED，acceptedCount=5, needsHumanReview=0 |
| 入库 | 5/5 articles |

## 7. FQ6 Precision 问题分析

FQ6 余留问题不是 metadata alias sync 的问题——metadata 已正确同步（DB 层 + query audit 都确认）。问题在 conclusion builder 的 `isTerminalHitQueryFocused`：

1. `buildTerminalHitEvidenceHaystack` 构造的证据文本来自 content + metadataJson
2. metadataJson 的 fieldAliases 数组以 JSON 数组格式存储（`["版本号","系统版本"]`）
3. evidence haystack 中包含这些 alias 时，`contains("版本号")` 可以匹配到 JSON 数组中的裸字符串
4. 但 name TU 的 evidence haystack 中 valueText = "校园实验室设备预约系统" 直接包含 "预约系统" token
5. `isTerminalHitQueryFocused` 对 name TU 返回 true，且 name 在遍历顺序中先于 version
6. `buildTerminalUnitExactConclusionLines` 返回第一个 query-focused 的终端 unit → name

**这不是 channel 识别、metadata sync 或 Reranker 的问题。这是 conclusion builder 在多个 query-focused 终端 unit 中选择逻辑的 precision 问题。**

## 8. 下一轮建议

**最小修复方向**：conclusion builder 的 `buildTerminalUnitExactConclusionLines` 在多个终端 unit 通过 `isTerminalHitQueryFocused` 时，优先选择 fused_rank 更高的（而非遍历顺序第一个）。

version TU 在 fused_rank=1，name TU 在 fused_rank=5。当前代码按遍历顺序选择（先碰到 name），改为按 fused_rank 排序后选择第一个即可解决。

**改动范围**：仅 `AnswerFallbackConclusionBuilder.java` 的 `buildTerminalUnitExactConclusionLines` 方法（排序逻辑）。不改 selector、Reranker、metadata sync、channel parse。

## 9. 未执行项

| 项目 | 状态 |
|---|---|
| 完整 19 题 eval | 未执行 |
| 修改代码 | 未执行 |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |

## 合规声明

- 本轮未修改任何代码、测试、配置、脚本、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 报告未输出 API key/token/password
