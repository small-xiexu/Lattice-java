# SWIP structured / exact lookup lead-in fix result

## 1. 修改范围

- 修改生产代码文件：
  - `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java`
- 修改方法：
  - `compressStructuredExactLookupAnswer(...)`
  - `looksLikeDanglingLeadInParagraph(...)`
  - `looksLikeDirectAnswerParagraph(...)`
  - `countMarkdownListItems(...)`
- 新增同文件 private helper：
  - `looksLikeStructuredAnswerBodyParagraph(...)`
  - `looksLikeMarkdownListItemLine(...)`
  - `looksLikeMarkdownTableLine(...)`
  - `looksLikeKeyValueAnswerLine(...)`
  - `firstKeyValueDelimiterIndex(...)`
  - `containsStructuredValueSignal(...)`
  - `stripTrailingLeadInPunctuation(...)`
- 未修改 `AnswerPayloadParser.java`。

结论：只修改允许的生产代码文件；`special_cases_report.md` 仅由 redline 扫描刷新结果，未修改规则或 allowlist。

## 2. 修复内容

本轮只修 exact lookup / structured answer 段落压缩中“引导句后面的列表、表格、模板主体被裁掉”的通用问题：

- 如果已保留段落最后一段是通用 dangling lead-in，且后续紧邻段落是结构化答案主体，则压缩时保留该主体段。
- dangling lead-in 识别不再要求“无 citation”，避免带 citation 的引导句被误当成完整短答案。
- 结构化主体只用通用信号识别：Markdown list、table、code fence、含具体值信号的 key-value/template 行。
- 未放宽 citation 要求；压缩结果仍必须包含 citation，否则回退原答案。

是否新增业务词或 case 特判：否。

## 3. 禁止项确认

- 是否修改题集 / runner：否。
- 是否修改 prompt：否。
- 是否修改 fallback：否。
- 是否修改 citation：否。
- 是否修改 retrieval / rerank / RRF / fusion：否。
- 是否修改 compiler / fact card：否。
- 是否扩大 topK：否。
- 是否切换模型：否。
- 是否提交代码：否。

## 4. Redline

- 修复前 redline：`BLOCKER=0 / REVIEW=1830 / ALLOWLIST=219`
- 修复后 redline：`BLOCKER=0 / REVIEW=1831 / ALLOWLIST=218`

新增 REVIEW 来自通用结构/引导语判断，不含 SWIP 业务词或 case 特判。

## 5. Maven Test

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：通过，`Tests run: 811, Failures: 0, Errors: 0, Skipped: 0`。

## 6. 两个目标 Case 前后结果

说明：修复前按要求跑了两例临时 suite；本轮修复前 `SWIP-FAQ-NO-RESPONSE-001` 走 FALLBACK，未复现 LLM dangling lead-in 路径。稳定 round3 报告中该 case 的 LLM 路径表现是只剩引导句。

| Case | 修复前结果 | 修复前摘要 | 修复后结果 | 修复后摘要 | requiredAnswerTerms 覆盖 |
|---|---:|---|---:|---|---|
| `SWIP-FAQ-NO-RESPONSE-001` | FAIL / `PARTIAL_ANSWER` / `FALLBACK` | fallback 证据摘要，覆盖 `SNIFF`、`区域IT伙伴`，漏 `HTTPS服务`、`已启动`。 | FAIL / `PARTIAL_ANSWER` / `LLM` | 引导句后保留了表格主体，覆盖 `SNIFF`、`HTTPS服务`、`已启动`，仍漏 `区域IT伙伴`。 | 4 项中 3 项覆盖，仍失败。 |
| `SWIP-INSTALL-CERT-NAMING-001` | PASS / `SUCCESS` / `LLM` | 直接表格回答，两个 strict expect 模板均覆盖。 | PASS / `SUCCESS` / `LLM` | 引导句后保留表格主体，两个 strict expect 模板均覆盖。 | strict eval required terms 全覆盖。 |

注：上表 requiredAnswerTerms 以 query regression 实际断言的 `expect.requiredAnswerTerms` 为准。

## 7. 完整 SWIP Strict Eval

命令：

```bash
QUERY_REGRESSION_BASE_URL=http://127.0.0.1:18086 \
QUERY_REGRESSION_SUITE=docs/test/swip-query-eval-candidates.json \
QUERY_REGRESSION_OUTPUT_DIR=.codex/run/swip-structured-leadin-fix-20260516-112955 \
QUERY_REGRESSION_ALLOW_FAILURES=1 \
bash scripts/run-query-regression.sh
```

结果：

- pass/fail：`14 / 9`
- casePassRate：`0.6086956521739131`
- Recall@5：`0.9347826086956522`
- Recall@10：`0.9347826086956522`
- citationPrecision：`0.7878019323671497`
- llmSuccessRate：`0.8695652173913043`

相对 `swip-stability-round3`：

- 改善：
  - `SWIP-INSTALL-CERT-NAMING-001`：FAIL -> PASS。
  - `SWIP-INSTALL-APP-UPGRADE-IMPACT-001`：FAIL -> PASS。
- 新增回归：
  - `SWIP-NEG-UNANSWERABLE-001`：PASS -> FAIL，原因 `answerOutcome_unexpected_SUCCESS`。

按用户要求，发现新增回归后本轮不继续扩大修改。

## 8. 剩余失败分类

本轮完整 eval 仍失败 9 个：

- `SWIP-FAQ-NO-RESPONSE-001`：后处理裁剪问题已缓解，但答案仍漏一个 required term。
- `SWIP-USAGE-BANK-REFUND-001`：非本轮 fallback / grounding 漏点。
- `SWIP-USAGE-BANK-SETTLEMENT-001`：非本轮 answer grounding 漏点。
- `SWIP-USAGE-SAND-SIGN-001`：非本轮 answer grounding 漏点。
- `SWIP-USAGE-SAND-SETTLEMENT-001`：非本轮 answer grounding 漏点。
- `SWIP-INSTALL-LOGS-001`：非本轮 answer grounding 漏点。
- `SWIP-INSTALL-CERT-UPDATE-001`：非本轮 answer grounding 漏点。
- `SWIP-FAQ-PRINT-PAPER-001`：非本轮 answer grounding 漏点。
- `SWIP-NEG-UNANSWERABLE-001`：新增回归，outcome 断言失败。

## 9. 结论

- 本轮最小修复对目标 post-processing 裁剪问题有效：带 citation 的引导句不再作为唯一压缩结果，后续结构化表格/list 主体可保留。
- `SWIP-INSTALL-CERT-NAMING-001` 修复后 PASS。
- `SWIP-FAQ-NO-RESPONSE-001` 结构化主体已保留，但仍缺一个 required term，不能标记完全修复。
- 完整 SWIP strict eval 为 `14/23`，但出现 1 个新增回归，因此本轮不继续扩修。

## 10. 下一步建议

只做一个最小动作：对 `SWIP-NEG-UNANSWERABLE-001` 做只读新增回归归因，确认是 LLM 波动、outcome normalization 问题，还是本轮段落压缩改动的间接影响；归因前不要继续修改代码。
