# Terminal Unit Phase 1F Conclusion Gate Correction Clean Runtime 验证报告

验证时间：2026-05-31
验证人：agentD

## 1. 验证结论

**FAIL** — Phase 1F 双修复（alias consumption fix + conclusion gate correction）均已编译入代码并通过定向测试（18/0/0），但 runtime 答案仍未消费 terminal unit exact value。FQ6/FG2 仍 FAIL。

## 2. 环境与清库确认

| 项目 | 值 |
|---|---|
| git status | 多文件修改（全部为 Phase 1 系列累积改动），无意外文件 |
| Schema | `./scripts/reset-lattice-schema.sh` 完整重建 |
| 服务 | `scripts/run-local-dev.sh`，端口 18082 |
| compile jobId | `5355b79d-23d8-443a-9669-4af0354ab2f5` |
| 状态 | SUCCEEDED，acceptedCount=5, needsHumanReviewCount=0 |
| 入库 articles | 5/5（全部通过审查，无人工确认项） |

## 3. 门禁结果

| 检查项 | 结果 |
|---|---|
| git diff --check | 通过 |
| redline | **BLOCKER=0** |
| AnswerFallbackConclusionBuilderTests | 7/0/0 |
| AnswerFallbackEvidenceSelectorTests | 11/0/0 |
| 定向组合 | **18/0/0 BUILD SUCCESS** |

## 4. 代码修复确认

| 修复项 | 文件 | 源码存在 | 编译产物存在 |
|---|---|---|---|
| Selector: `isTerminalUnitQueryFocused` | AnswerFallbackEvidenceSelector.java | ✓ (3 引用) | ✓ |
| Selector: `buildTerminalUnitEvidenceHaystack` | AnswerFallbackEvidenceSelector.java | ✓ | ✓ |
| Conclusion: 外层问题类型门控移除 | AnswerFallbackConclusionBuilder.java | ✓ (0 处 `looksLike`) | ✓ |
| Conclusion: `isTerminalHitQueryFocused` | AnswerFallbackConclusionBuilder.java | ✓ | ✓ |
| Conclusion: `buildTerminalHitEvidenceHaystack` | AnswerFallbackConclusionBuilder.java | ✓ | ✓ |

**全部 Phase 1F 修复均已编译入代码并通过定向测试。**

## 5. FQ6 / FG2 Runtime 结果

### 5.1 Terminal Unit 召回（PASS）

| 题目 | 目标 unit | fusedRank | displayText |
|---|---|---|---|
| FQ6 | version=v2.3.1 | **1** | `borrowing_system.version = v2.3.1` |
| FG2 | max_concurrent_requests=50 | **1** | `borrowing_system.max_concurrent_requests = 50` |

### 5.2 Answer 消费（FAIL）

| 题目 | 预期 | 实际 |
|---|---|---|
| FQ6 | "v2.3.1" 或 "borrowing_system.version = v2.3.1" | 系统名称 + API endpoint + equipment_types 概述。**无 version 值** |
| FG2 | "50" 或 "borrowing_system.max_concurrent_requests = 50" | support_time + borrowing_system 概述 + equipment_types。**无 50** |

未见 "Confirmed evidence: borrowing_system.version = v2.3.1" 或类似 terminal unit exact line。答案仍以 ARTICLE/SOURCE 证据为主。

## 6. 失败归因

**类别**：conclusion builder 未消费 terminal unit。

**证据链**：
- terminal unit 在 fused_rank=1 ✓
- displayText = "borrowing_system.version = v2.3.1" ✓
- Phase 1F selector fix 已编译 ✓
- Phase 1F conclusion gate correction 已编译 ✓
- 定向测试 18/0/0 ✓
- **Runtime answer 未消费** ✗

**最可能阻断点**（需 agentB 只读归因）：
1. FQ6/FG2 的 `fallbackHits` 中是否实际包含 terminal unit — selector 的 `isTerminalUnitQueryFocused` 可能在 runtime 返回 false，因为 `extractHighSignalTokens` 提取的 token 在中文场景下与终端 unit 证据文本不匹配
2. 或 `buildTerminalUnitExactConclusionLines` 中的 `isTerminalHitQueryFocused` 对 FQ6/FG2 的 query token 返回 false — `buildTerminalHitEvidenceHaystack` 构造的证据文本可能不含 query 中文 token
3. 或 `buildGeneralFallbackConclusionLines` 在抵达 terminal unit 分支前已被更优先的 conclusion 类型（exact structured list / aggregated evidence）返回

**与上一轮 (Phase 1E) 的区别**：
- Phase 1E：阻塞点在问题类型门控（`looksLikeStructuredFactQuestion` 对 FQ6 返回 false）
- Phase 1F：门控已移除，但 runtime 仍失败，阻塞点下移到 `isTerminalHitQueryFocused` / `isTerminalUnitQueryFocused` 的 token 匹配逻辑

## 7. 保护回归

未执行完整 19 题 eval（本轮重点只在 FQ6/FG2）。编译全部成功（5/5 accepted），基础链路正常。

## 8. 未执行项

| 项目 | 状态 |
|---|---|
| 修改代码 | 未执行 |
| 完整 19 题 eval | 未执行（本轮仅验证 FQ6/FG2） |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |

## 9. 下一步

**agentB 只读归因**：在 running service 或日志中确认：
1. FQ6 的 `selectFallbackEvidenceHits` 返回值中是否含 terminal unit
2. `isTerminalUnitQueryFocused` 对 FQ6 的 terminal unit (version=v2.3.1) 和 query token 返回 true 还是 false
3. `buildTerminalUnitExactConclusionLines` 中 `isTerminalHitQueryFocused` 的返回值
4. `buildTerminalHitEvidenceHaystack` 构造的证据文本内容——是否包含 query 中文 token（"版本号"、"预约系统"等）

如 `isTerminalHitQueryFocused` 对中文 query 和英文 metadata 始终返回 false（因为 `contains(token)` 需要精确匹配，而 metadata fieldAliases JSON 中的中文 alias 格式为 `"版本号"` 而非裸 token），这是 token 匹配格式不兼容问题——需要改用更鲁棒的 token 匹配方式（如 JSON 解析后匹配，而非 `String.contains`）。

## 合规声明

- 本轮未修改任何代码、测试、配置、脚本、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 报告未输出 API key/token/password
