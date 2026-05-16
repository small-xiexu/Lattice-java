# SWIP Unanswerable Outcome Guard Fix Result Report

## 1. 本轮结论

- 本轮只处理 `SWIP-NEG-UNANSWERABLE-001` 暴露出的 structured answer outcome normalization 缺口。
- 修复后该 case 从 `FAIL / SUCCESS` 改为 `PASS / INSUFFICIENT_EVIDENCE`，答案正文仍保持拒答/证据不足语义。
- `mvn test` 通过：`811 / 0 / 0`。
- 完整 SWIP strict eval 从上一轮 `14/23` 变为本轮 `13/23`。
- 本轮完整 eval 出现 2 个 PASS -> FAIL 变化，已按要求停止扩大修改：
  - `SWIP-INSTALL-APP-LIST-001`
  - `SWIP-INSTALL-IP-SUFFIX-001`

## 2. 修改范围

| 项 | 结果 |
|---|---|
| 修改生产代码文件 | `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` |
| 修改方法 | `normalizeStructuredAnswerOutcome(...)` |
| 新增 helper | `looksLikeInsufficientEvidenceAnswer(...)`、`firstMeaningfulAnswerLine(...)`、`normalizeOutcomeLine(...)`、`isGenericAnswerSectionLabel(...)`、`containsEarlyInsufficientEvidenceSignal(...)`、`looksLikeInsufficientEvidenceConclusionLine(...)`、`containsInsufficientEvidenceSignal(...)` |
| 是否只修改允许文件 | 是。本轮生产代码只修改首选允许文件；`special_cases_report.md` 由 redline 更新；本报告为本轮交付物 |
| 是否修改 `AnswerParagraphPostProcessor.java` | 否。本轮未触碰上一轮 lead-in 修复 |
| 是否修改题集 / runner / prompt / fallback / citation / RRF / retrieval / compiler | 否 |
| 是否新增业务词或 case 特判 | 否 |

说明：工作区中 `AnswerParagraphPostProcessor.java` 仍有上一轮既有改动，但本轮未修改该文件。

## 3. 实现说明

本轮在 structured answer outcome normalization 中增加一个 SUCCESS 下调 guard：

- 仅当原始 `answerOutcome == SUCCESS` 时生效。
- 仅当答案整体形态明显表达证据不足、无法确认、不能判定、缺少直接证据等通用 no-answer 语义时，下调为 `INSUFFICIENT_EVIDENCE`。
- 不因答案中零散出现一个限制性短语就降级：
  - 首个有效答案行在开头附近表达证据不足时才直接下调；
  - 或存在多处证据不足信号，并在结论性行中表达不能确认/不能判定时下调。
- 未改动 cache、fallback、prompt、citation、retrieval、rerank、RRF、compiler。

## 4. Redline

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 218 |

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

## 5. `mvn test`

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

| Tests | Failures | Errors | Skipped |
|---:|---:|---:|---:|
| 811 | 0 | 0 | 0 |

## 6. 目标 Case 修复前后

基线：`.codex/run/swip-structured-leadin-fix-20260516-112955`  
本轮：`.codex/run/swip-unanswerable-outcome-guard-fix-20260516-121843`

| Case | 修复前 | 修复后 | generationMode | answerOutcome 变化 | 答案摘要 | 结论 |
|---|---|---|---|---|---|---|
| `SWIP-NEG-UNANSWERABLE-001` | FAIL，`answerOutcome_unexpected_SUCCESS` | PASS | LLM -> LLM | `SUCCESS` -> `INSUFFICIENT_EVIDENCE` | 修复后答案明确表示不能据此确认完整交易流程，并说明现有证据只支持有限范围，缺少直接支持完整流程的证据 | 已修复；仍正确拒答 |
| `SWIP-INSTALL-CERT-NAMING-001` | PASS | PASS | LLM -> LLM | `SUCCESS` -> `SUCCESS` | 仍输出两类证书命名规则表格 | 仍 PASS |
| `SWIP-FAQ-NO-RESPONSE-001` | FAIL，缺少 `区域IT伙伴` | FAIL，缺少 `区域IT伙伴` | LLM -> LLM | `PARTIAL_ANSWER` -> `PARTIAL_ANSWER` | 仍输出无响应检查表，但缺少 required term | 未改善，也未作为本轮目标处理 |

## 7. 完整 SWIP Strict Eval

| 指标 | 修复前 | 修复后 |
|---|---:|---:|
| pass / total | 14 / 23 | 13 / 23 |
| fail / total | 9 / 23 | 10 / 23 |
| casePassRate | 0.6087 | 0.5652 |
| Recall@5 | 0.9348 | 0.9565 |
| Recall@10 | 0.9348 | 0.9565 |
| citationPrecision | 0.7878 | 0.7977 |
| llmSuccessRate | 0.8696 | 0.7826 |
| averageCitationCoverage | 0.8044 | 0.8225 |

修复后失败 case：

| Case | failedReasons | answerOutcome | generationMode |
|---|---|---|---|
| `SWIP-USAGE-BANK-REFUND-001` | `answer_missing_term:参考号`，`answer_missing_term:原交易日期` | `PARTIAL_ANSWER` | FALLBACK |
| `SWIP-USAGE-BANK-SETTLEMENT-001` | `answer_missing_term:日结`，`answer_missing_term:结算成功`，`answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | LLM |
| `SWIP-USAGE-SAND-SIGN-001` | `answer_missing_term:开店前` | `SUCCESS` | LLM |
| `SWIP-USAGE-SAND-SETTLEMENT-001` | `answer_missing_term:卡种` | `SUCCESS` | LLM |
| `SWIP-FAQ-NO-RESPONSE-001` | `answer_missing_term:区域IT伙伴` | `PARTIAL_ANSWER` | LLM |
| `SWIP-INSTALL-APP-LIST-001` | 多个列表项缺失 | `PARTIAL_ANSWER` | LLM |
| `SWIP-INSTALL-IP-SUFFIX-001` | `answer_missing_term:150`，`answer_missing_term:151` | `SUCCESS` | FALLBACK |
| `SWIP-INSTALL-LOGS-001` | 多个目录/账号项缺失 | `PARTIAL_ANSWER` | LLM |
| `SWIP-INSTALL-CERT-UPDATE-001` | 多个时间/触发条件项缺失 | `PARTIAL_ANSWER` | LLM |
| `SWIP-FAQ-PRINT-PAPER-001` | 多个纸张/场景项缺失 | `INSUFFICIENT_EVIDENCE` | LLM |

新增 PASS -> FAIL 变化：

| Case | 修复前 | 修复后 | 备注 |
|---|---|---|---|
| `SWIP-INSTALL-APP-LIST-001` | PASS | FAIL | 本轮 guard 仅作用于 `SUCCESS` 下调；该 case 修复后为 `PARTIAL_ANSWER`，不继续扩大分析 |
| `SWIP-INSTALL-IP-SUFFIX-001` | PASS | FAIL | 该 case 为 FALLBACK / `SUCCESS`，本轮未修改 fallback；不继续扩大分析 |

本轮改善：

| Case | 修复前 | 修复后 |
|---|---|---|
| `SWIP-NEG-UNANSWERABLE-001` | FAIL | PASS |

## 8. 禁止项确认

| 项 | 是否触碰 |
|---|---|
| lead-in 修复 | 否 |
| RRF / fusion | 否 |
| retrieval / rerank | 否 |
| fallback summary / fallback outcome | 否 |
| compiler / fact card | 否 |
| prompt builder | 否 |
| citation | 否 |
| runner | 否 |
| 题集 | 否 |
| 业务词 / case 特判 | 否 |

## 9. 下一步建议

只建议一个最小动作：对本轮新增的两个 PASS -> FAIL 变化做只读复核，先判断是 LLM/eval 波动、当前库状态差异，还是本轮 outcome guard 的间接影响；复核前不继续改代码。
