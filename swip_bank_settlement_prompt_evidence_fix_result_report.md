# SWIP-USAGE-BANK-SETTLEMENT-001 Prompt Evidence 修复结果报告

## 1. 修改文件和方法

本轮尝试阶段只修改过生产代码文件：

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java`

尝试涉及的方法：

- `appendEvidenceSection(...)`
- `buildBoundedPromptEvidenceContent(...)`
- `buildPromptFocusSnippets(...)`
- 尝试新增同文件内直接服务于 prompt evidence 组装的私有辅助方法

验证后结论：该尝试没有让目标 case 通过，且在目标保护集里出现 `SWIP-INSTALL-IP-SUFFIX-001` 回归，因此已回退本轮生产代码尝试。最终未保留生产代码改动。

## 2. 是否只修改 AnswerGenerationPromptEvidenceSupport.java

是。

说明：尝试阶段生产代码只触碰 `AnswerGenerationPromptEvidenceSupport.java`；未修改其他生产代码、测试、资源、题集、脚本或 allowlist。由于验证未达标，最终已回退该文件的生产代码改动。

## 3. 是否新增特判

否。

未新增 `SWIP` / `银行` / `日结` / `小票` / 题目文本 / 答案片段 / 文档名特判。对 `AnswerGenerationPromptEvidenceSupport.java` 扫描以下词均无命中：

- `SWIP`
- `银行`
- `日结`
- `结算成功`
- `小票`
- `银行卡结算建议`
- `执行后会出现`
- `结算建议`
- `具体答案`

## 4. Redline

修复后重新运行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

| 项 | 数值 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 238 |

## 5. Maven Test

命令：

`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`

结果：通过。

| Tests run | Failures | Errors | Skipped |
|---:|---:|---:|---:|
| 811 | 0 | 0 | 0 |

## 6. BANK-SETTLEMENT 修复前后结果

修复前最近三轮稳定结果来自 `swip_bank_settlement_outcome_guard_analysis_report.md` 与对应 stability run。

| 阶段 | pass | failedReason | answerOutcome | generationMode | modelExecutionStatus | citationCoverage | 是否包含日结 | 是否包含结算成功 | 是否包含小票 |
|---|---|---|---|---|---|---:|---|---|---|
| 修复前 R1 | false | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 1 | 否 | 否 | 否 |
| 修复前 R2 | false | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 1 | 否 | 否 | 否 |
| 修复前 R3 | false | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 1 | 否 | 否 | 否 |
| 尝试补丁后 | false | `answer_missing_term:结算成功\|answer_missing_term:小票` | `SUCCESS` | `LLM` | `SUCCESS` | 1 | 是 | 否 | 否 |

尝试补丁后的答案已不再是证据不足式拒答，但只回答了建议在每日日结后执行银行卡结算，仍未覆盖成功提示和打印小票结果，因此目标 case 仍失败。

有效验证产物：

- `.codex/run/swip-bank-settlement-prompt-evidence-target-20260516-1907/query_summary.tsv`
- `.codex/run/swip-bank-settlement-prompt-evidence-target-20260516-1907/query_results.jsonl`
- `.codex/run/swip-bank-settlement-prompt-evidence-target-20260516-1907/query_metrics.json`

## 7. 保护 Case 结果

同一轮目标保护集结果：

| case | pass | 结果说明 |
|---|---|---|
| `SWIP-INSTALL-IP-SUFFIX-001` | false | 缺 `150`、`151`，`generationMode=FALLBACK`，`fallbackReason=DETERMINISTIC_EXACT_LOOKUP_PREFERRED` |
| `SWIP-INSTALL-CERT-NAMING-001` | true | 通过 |
| `SWIP-NEG-UNANSWERABLE-001` | true | 通过 |

由于保护 case 出现回归，本轮尝试补丁不保留。

## 8. 完整 SWIP Strict Eval 指标

未运行完整 SWIP strict eval。

原因：用户执行要求为目标 case 通过后再跑至少一轮完整 SWIP strict eval；本轮目标 `SWIP-USAGE-BANK-SETTLEMENT-001` 仍失败，且保护 case `SWIP-INSTALL-IP-SUFFIX-001` 出现回归。按“如果 BANK-SETTLEMENT 仍失败，只输出原因，不扩大修改范围”的约束，本轮停止扩大验证。

| 指标 | 数值 |
|---|---|
| pass 数 | 未运行 |
| Recall@5 | 未运行 |
| Recall@10 | 未运行 |
| citationPrecision | 未运行 |
| llmSuccessRate | 未运行 |
| fallbackRate | 未运行 |
| avgCitationCoverage | 未运行 |

目标保护集指标供参考：

| 指标 | 数值 |
|---|---:|
| pass 数 | 2 / 4 |
| Recall@5 | 1 |
| Recall@10 | 1 |
| citationPrecision | 0.9285714285714286 |
| llmSuccessRate | 0.75 |
| fallbackRate | 0.25 |
| avgCitationCoverage | 0.8888888888888888 |

## 9. 是否出现新增回归

是，尝试补丁验证中 `SWIP-INSTALL-IP-SUFFIX-001` 从上一轮目标保护通过变为失败。

该回归没有继续处理，也没有扩大修复范围；失败补丁已回退。

## 10. BANK-SETTLEMENT 仍失败的原因

本轮 prompt evidence 呈现尝试只解决了“LLM 首行证据不足式拒答”的一部分症状：after run 中 `answerOutcome` 已变为 `SUCCESS`，答案也包含“日结”。但答案仍没有覆盖“结算成功”和“小票”，说明只是提高了部分证据可见性，未能稳定让模型使用同一直接证据中的后续结果句。

同时该尝试改变了 prompt evidence 片段数量和正文补充策略，影响到精确查值保护 case，导致 IP-SUFFIX fallback 答案缺少 `150`、`151`。因此本轮不应保留该补丁。

结论：本轮未完成目标修复；下一步最小动作应改为只读分析 prompt evidence 中 BANK-SETTLEMENT 实际进入 LLM 的完整 prompt 片段，确认“结算成功/小票”是否在 prompt 内、位置是否被截断，再决定下一轮是否做更窄的 evidence 排列修复。

