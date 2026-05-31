# Terminal Unit Phase 1E Conclusion Consumption Clean Runtime 验证报告

验证时间：2026-05-30
验证人：agentD

## 1. 验证结论

**FAIL** — Conclusion builder + evidence selector 双修复均已编译入代码，但 runtime 答案仍未消费 terminal unit exact value。FQ6 和 FG2 仍 FAIL，答案继续选中 ARTICLE 的 return_policy 证据。终端 unit (version=v2.3.1, max_concurrent_requests=50) 均在 fused_rank=1，displayText 正确，但未被 answer 层消费。

## 2. 门禁确认

| 检查项 | 结果 |
|---|---|
| redline | BLOCKER=0 |
| git diff --check | 通过 |
| AnswerFallbackConclusionBuilderTests | 7/0/0 |
| AnswerFallbackEvidenceSelectorTests | 11/0/0 |
| 定向测试合计 | **18/0/0** |
| 全量 mvn test | 995, Failures: 1 (ManagementJsRuntimeTests — 既有问题，与本轮无关) |

## 3. 验证环境

| 项目 | 值 |
|---|---|
| Schema | `./scripts/reset-lattice-schema.sh` 完整重建 |
| compile jobId | `828084bb-b325-44fd-b1b9-5ec4a897f916` |
| 状态 | SUCCEEDED，4 accepted + 1 needs_human_review (PDF) |
| 入库 articles | 4（化学品存储分级表, equipment borrowing policy, equipment maintenance schedule, 校园实验室安全管理手册）+ PDF approve 后 5 |
| equipment-borrowing-policy | **已入库** ✓ |

## 4. FQ6 / FG2 Runtime 结果

### 4.1 Terminal Unit 召回（PASS）

| 题目 | 目标 unit | hitRank | fusedRank | displayText | evidenceType | channel |
|---|---|---|---|---|---|---|
| FQ6 | version=v2.3.1 | 2 | **1** | `borrowing_system.version = v2.3.1` | FACT_CARD | fact_card_terminal_fts |
| FG2 | max_concurrent_requests=50 | 2 | **1** | `borrowing_system.max_concurrent_requests = 50` | FACT_CARD | fact_card_terminal_fts |

FQ6 的 fused top-5 全部为 terminal unit (FACT_CARD + fact_card_terminal_fts)。目标 unit 数据完整。

### 4.2 Answer 消费（FAIL）

| 题目 | 预期值 | 实际答案 | 选中的证据 |
|---|---|---|---|
| FQ6 | v2.3.1 | 讨论 return_policy (damage_report, overdue_notice, same_day_return_cutoff) | ARTICLE |
| FG2 | 50 | 同上（return_policy 证据） | ARTICLE |

答案中未见 "Confirmed evidence: borrowing_system.version = v2.3.1" 或类似终端 unit exact line。结论构建器的 terminal unit 分支未被触发或未能输出。

## 5. 代码状态确认

| 修复 | 文件 | 方法 | 代码存在 |
|---|---|---|---|
| Evidence selector 豁免 | `AnswerFallbackEvidenceSelector.java:490-503` | `allowTerminalUnitEvidence` + `isTerminalUnitChannelHit` | ✓ |
| Conclusion builder 消费 | `AnswerFallbackConclusionBuilder.java:240-399` | `buildTerminalUnitExactConclusionLines` + `extractTerminalUnitExactLine` | ✓ |

两个修复均已编译并通过了定向测试（18/0/0），但 runtime 未产生预期效果。

## 6. 失败归因

**类别**：conclusion builder 未消费 terminal unit。

**证据链**：
- terminal unit 在 fused_rank=1 ✓
- displayText 正确 ✓
- channel=fact_card_terminal_fts ✓
- evidenceType=FACT_CARD ✓
- 但 answer 选中了 ARTICLE (return_policy) 证据 ✗
- "Confirmed evidence: borrowing_system.version = v2.3.1" 未出现 ✗

**可能的阻断点**（需 agentB 只读归因）：
1. Evidence selector 的 `allowTerminalUnitEvidence` 在 runtime 中为 false（问题类型检测未通过 — "版本号是什么" 可能不匹配 `looksLikeStructuredFactQuestion` 或 `looksLikeExactLookupQuestion`）
2. 或 selector 通过了 terminal unit，但 conclusion builder 的 `buildTerminalUnitExactConclusionLines` 中 `isLineQueryFocused` 返回 false
3. 或 `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 对 FQ6/FG2 的 query 返回 false

## 7. 未执行项

| 项目 | 状态 |
|---|---|
| 修改代码 | 未执行 |
| 修改配置/测试/题集 | 未执行 |
| stage/commit/push | 未执行 |
| 读取 hidden eval | 未执行 |

## 8. 下一步

**agentB 只读归因**：用 FQ6/FG2 的运行时 query 数据和当前源码，确认 `looksLikeStructuredFactQuestion("预约系统当前的版本号是什么")` 和 `looksLikeExactLookupQuestion(...)` 的返回值。如果两者均为 false，说明问题类型检测未覆盖 borrowing_system 类问题——这是 selector 和 conclusion builder 两个修复都无法触发的根本原因。

## 合规声明

- 本轮未修改任何代码、测试、配置、脚本、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 报告未输出 API key/token/password
