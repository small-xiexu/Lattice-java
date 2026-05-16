# SWIP Eval Expectation Adjustment Report

## 1. 结论

- 已按 `swip_eval_expectation_review_report.md` 调整 23 个 case 的 `expect`。
- 顶层人工验收字段全部保留：`expectedPoints`、`expectedEvidence`、`mustNotClaim`、`humanJudgement`、`requiredAnswerTerms`、`requiredSourceTerms`。
- 未修改 runner、生产代码、测试代码、模型配置、SWIP 源文档。
- 调整后 strict eval 从上一轮 `0/23` 提升为 `13/23`。
- 剩余 10 个失败全部是 `missing requiredAnswerTerms`，未再出现 `missing requiredSourceTerms` 硬失败。

## 2. 修改范围

本轮只修改：

- `docs/test/swip-query-eval-candidates.json`
- `swip_eval_expectation_adjustment_report.md`

未修改：

| 项目 | 是否修改 |
|---|---|
| query regression runner | 否 |
| 生产代码 | 否 |
| 测试代码 | 否 |
| 模型配置 | 否 |
| SWIP 源文档 | 否 |
| redline 规则或 allowlist | 否 |
| 数据库数据 | 否 |
| 重新导入 SWIP 源文档 | 否 |
| 重新 compile | 否 |
| 提交代码 | 否 |

## 3. expect 调整概览

23 个 case 均修改了 `expect`：

- `requiredAnswerTerms`：21 个正答案 case 保留；2 个无答案 case 移除固定话术断言。
- `requiredSourceTerms`：23 个 case 全部从 `expect` 移除。
- `expectedRetrievalTargets`：23 个 case 均改用稳定 source identity，主要为对应 SWIP docx 文件名。
- `forbiddenAnswerTerms`：23 个 case 全部保留，由顶层 `mustNotClaim` 映射而来。
- `answerOutcomeAny`：仅 2 个无答案 case 添加。

## 4. requiredAnswerTerms 规则摘要

删除或降级：

- 删除可转述语气词：`必须`、`未提供`、`没有提供`。
- 删除过宽或人工判断更合适的词：`POS`、`监控`、`最后一笔`、`只允许申请一次`、`秘钥下载成功`、`一机一密`、`POS DLL`、`swip_initialization_files`。
- 删除无答案题的固定拒答话术和问题关键词：`未提供`、`没有提供`、`支付宝`、`微信`、`表结构`、`pinpad_information`。

缩短或稳定化：

- `每日日结后` -> `日结`
- `结算小票` -> `小票`
- `每天开店前` -> `开店前`
- `按卡种` -> `卡种`
- `仅得仕卡` -> `得仕卡`
- `返回处理中` -> `处理中`
- `星巴克LOGO` -> `LOGO`
- `10秒` -> `10`
- `9个APP` -> `9`

保留：

- 专名/入口：`UPP`、`eBuy日结打印`、`SNIFF`、`HTTPS服务`、`SWIP APP Store`、`SWIP网关APP`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡`。
- 字段/路径/文件：`storeId`、`stationId`、`pinpadIp`、`c:\SWIP\swip-keys.p13`、`9999/log`、`6666`、`XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand`。
- 数字/格式/短事实：`149`、`150`、`151`、`40mm`、`58mm`、`51/31天`、`50/30天`、`晚上11点`、`月月日日`、`参考号`、`原交易日期`、`结算成功`、`全额撤销`、`已启动`、`开机`、`再次执行`。

## 5. requiredSourceTerms 规则摘要

删除：

- 23 个 case 的 `expect.requiredSourceTerms` 全部移除。
- 删除原因：这些字段多为章节标题、quoteHint、问题词或人工证据线索，而 runner 的 `sourceText` 只序列化 `sources/articles/structuredEvidence`，不包含 citation marker 的 `matchedExcerpt`。

迁移：

- 将稳定 source 身份迁移到 `expect.expectedRetrievalTargets`。
- 迁移值使用 runner 可匹配的 source path/docx 文件名：
  - `SWIP智能键盘系统使用手册-20250702.docx`
  - `SWIP智能键盘系统安装手册-202509.docx`

保留：

- 顶层 `requiredSourceTerms` 未删除，继续作为人工验收口径。
- 章节标题、quoteHint、人工证据线索保留在 `expectedEvidence` / `humanJudgement` 中，不再作为机器硬断言。

## 6. 无答案题调整

| Case | 调整结果 |
|---|---|
| SWIP-NEG-UNANSWERABLE-001 | 移除 `expect.requiredAnswerTerms` 的 `未提供`、`支付宝`、`微信`；移除 `expect.requiredSourceTerms` 的 `支付宝微信签到`；新增 `answerOutcomeAny=[INSUFFICIENT_EVIDENCE, NO_RELEVANT_KNOWLEDGE, PARTIAL_ANSWER]`；保留 forbidden 防止编造支付宝/微信完整扫码流程。 |
| SWIP-NEG-UNANSWERABLE-002 | 移除 `expect.requiredAnswerTerms` 的 `没有提供`、`表结构`、`pinpad_information`；移除 `expect.requiredSourceTerms` 的 `pinpad_information`；新增 `answerOutcomeAny=[INSUFFICIENT_EVIDENCE, PARTIAL_ANSWER]`；保留 forbidden 防止编造完整表结构。 |

重跑结果：两个无答案 case 均通过。

## 7. 人工字段保留确认

| 字段 | 是否全部保留 |
|---|---|
| `expectedPoints` | 是 |
| `expectedEvidence` | 是 |
| `mustNotClaim` | 是 |
| `humanJudgement` | 是 |
| 顶层 `requiredAnswerTerms` | 是 |
| 顶层 `requiredSourceTerms` | 是 |

## 8. Redline

执行：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

| 类型 | 数量 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1830 |
| ALLOWLIST | 219 |

## 9. Adjusted Strict Eval

执行：

```bash
QUERY_REGRESSION_SUITE=docs/test/swip-query-eval-candidates.json \
QUERY_REGRESSION_OUTPUT_DIR=.codex/run/swip-expect-adjusted-eval-20260515-234728 \
QUERY_REGRESSION_ALLOW_FAILURES=1 \
bash scripts/run-query-regression.sh
```

`QUERY_REGRESSION_ALLOW_FAILURES=1` 仅用于完整落盘失败结果，不关闭单 case 断言。

输出目录：

- `.codex/run/swip-expect-adjusted-eval-20260515-234728`

指标：

| 指标 | 结果 |
|---|---:|
| case 总数 | 23 |
| pass 数 | 13 |
| fail 数 | 10 |
| casePassRate | 0.5652173913 |
| llmSuccessRate | 0.8260869565 |
| fallbackRate | 0.1739130435 |
| averageCitationCoverage | 0.8225108225 |
| Recall@5 | 0.9130434783 |
| Recall@10 | 0.9130434783 |
| citationPrecision | 0.8079710145 |

补充：

- `missing requiredAnswerTerms`：36 次。
- `missing requiredSourceTerms`：0 次。
- `forbiddenAnswerTerms 命中`：0 次。
- `answerOutcome 不符合`：0 次。
- `generationMode 不符合`：0 次。
- `citationCoverage 不足`：0 次。

## 10. 剩余失败 Case

| Case | 失败原因 | answerOutcome | generationMode | 归因 |
|---|---|---|---|---|
| SWIP-USAGE-SVC-READ-001 | missing requiredAnswerTerms: `UPP`、`卡号` | PARTIAL_ANSWER | LLM | answer grounding |
| SWIP-USAGE-BANK-REFUND-001 | missing requiredAnswerTerms: `参考号`、`原交易日期` | PARTIAL_ANSWER | FALLBACK | answer grounding |
| SWIP-USAGE-BANK-SETTLEMENT-001 | missing requiredAnswerTerms: `日结`、`结算成功`、`小票` | INSUFFICIENT_EVIDENCE | LLM | answer grounding |
| SWIP-USAGE-SAND-SIGN-001 | missing requiredAnswerTerms: `开店前` | PARTIAL_ANSWER | LLM | answer grounding |
| SWIP-FAQ-NO-RESPONSE-001 | missing requiredAnswerTerms: `HTTPS服务`、`已启动` | PARTIAL_ANSWER | FALLBACK | answer grounding |
| SWIP-INSTALL-APP-LIST-001 | missing requiredAnswerTerms: `SWIP APP Store`、`SWIP网关`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡` | PARTIAL_ANSWER | LLM | answer grounding |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | missing requiredAnswerTerms: `SWIP网关APP`、`资和信`、`杉德`、`易百` | PARTIAL_ANSWER | LLM | answer grounding |
| SWIP-INSTALL-LOGS-001 | missing requiredAnswerTerms: `6666`、`XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand` | PARTIAL_ANSWER | LLM | answer grounding |
| SWIP-INSTALL-CERT-UPDATE-001 | missing requiredAnswerTerms: `51/31天`、`50/30天`、`晚上11点`、`开机`、`SWIP网关APP` | PARTIAL_ANSWER | LLM | answer grounding |
| SWIP-FAQ-PRINT-PAPER-001 | missing requiredAnswerTerms: `40mm`、`58mm`、`杉德`、`工坊` | INSUFFICIENT_EVIDENCE | LLM | answer grounding |

## 11. 下一步建议

下一轮只分析 answer grounding：为什么已召回的 SWIP 证据没有稳定进入最终答案的关键事实覆盖。
