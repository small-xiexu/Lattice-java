# SWIP Answer Prompt Audit Instrumentation Result

## 结论

- 本轮只做 Answer LLM prompt audit 可观测性补强，不修答案质量。
- 生产代码只修改 `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java`。
- 未修改 `AnswerGenerationPromptEvidenceSupport.java`、prompt 模板、retrieval、rerank、RRF、fallback、postprocess、outcome guard、citation、runner、题集。
- BANK-SETTLEMENT 单 case 已成功产出 prompt audit 和 masked prompt snapshot。
- 审计结论：最终 LLM prompt 中可见 `日结`，但不可见 `结算成功`、`小票`；SOURCE / ARTICLE evidence section 存在且发生截断。

## 修改范围

| 文件 | 方法 / 位置 | 变更 |
|---|---|---|
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` | `generatePayloadByLlm(...)` | 将 `buildAnswerPrompt(...)` 结果先保存为局部变量，调用 `logPromptAudit(...)` 后再传入原 LLM invoker。 |
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` | 新增 private helper | 新增 prompt audit 摘要、section 摘要、query token presence、可选 masked prompt snapshot 输出。 |
| `special_cases_report.md` | redline 输出 | 由 `scripts/scan-redline.sh` 自动更新，允许范围内。 |

是否只修改允许文件：是。生产代码只修改 `AnswerGenerationPayloadOrchestrator.java`。  
是否改变 prompt 内容：否。传给 LLM 的 `answerPrompt` 是同一个 `buildAnswerPrompt(...)` 返回值。  
是否改变生成 / retrieval / fallback / postprocess / outcome guard：否。  
是否新增业务词或 case 特判：否。代码中未新增 SWIP、银行、日结、结算成功、小票、题目文本、答案片段、文件名特判。  
本轮是否修改答案质量逻辑：否。

## Audit 设计

- 常规 INFO audit：
  - prompt 总长度。
  - QUESTION-FOCUSED / CONTRIBUTION / STRUCTURED FACT CARD / SOURCE / GRAPH / ARTICLE section 是否存在。
  - 每个 section 长度、是否包含截断后缀、是否包含 omitted 标记。
  - 从问题中抽取的通用 query token 在 evidence prompt 中是否出现。
- masked prompt snapshot：
  - 默认不输出。
  - 仅在显式设置 `LATTICE_QUERY_ANSWER_PROMPT_AUDIT_SNAPSHOT_ENABLED=true` / `1` / `yes` 时输出。
  - 输出前经过 `SensitiveTextMasker.mask(...)` 脱敏。
  - 用于运行时验证任意调查词是否实际进入 LLM input prompt。

## Redline

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

| 指标 | 数值 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1846 |
| ALLOWLIST | 238 |

说明：无 BLOCKER。本轮新增的 REVIEW 属于通用字符串比较 / 日志审计候选，未出现业务特判。

## Maven Test

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：811 / 0 / 0，BUILD SUCCESS。

## BANK-SETTLEMENT 单 Case 验证

runner 本身没有 case filter 参数；本轮从正式 SWIP suite 只读生成 `.codex/run` 下的一次性单 case suite，未修改正式题集和 runner。

运行目录：

```text
.codex/run/swip-answer-prompt-audit-bank-settlement-20260516-203013
```

验证命令：

```bash
QUERY_REGRESSION_SUITE=.codex/run/swip-answer-prompt-audit-bank-settlement-20260516-203013/bank-settlement-single-case-suite.json \
QUERY_REGRESSION_OUTPUT_DIR=.codex/run/swip-answer-prompt-audit-bank-settlement-20260516-203013/eval \
QUERY_REGRESSION_ALLOW_FAILURES=1 \
QUERY_REGRESSION_BASE_URL=http://127.0.0.1:18087 \
bash scripts/run-query-regression.sh
```

服务启动时使用独立 query cache prefix，避免旧缓存短路 LLM 调用；未清库、未重新导入、未重建库。

单 case 结果：

| case | pass | answerOutcome | generationMode | modelExecutionStatus | failedReasons |
|---|---|---|---|---|---|
| `SWIP-USAGE-BANK-SETTLEMENT-001` | FAIL | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` |

actual answer 摘要：答案仍表达证据不足，只确认目录中存在相关章节，未给出结算时机、成功提示或打印结果。本轮不要求该 case 通过。

## Prompt Audit 结果

audit 日志已产出：

| 项 | 值 |
|---|---:|
| audit_count | 1 |
| snapshot_count | 1 |
| promptLength | 13093 |
| containsTruncatedSuffix | true |
| containsOmittedMarker | false |

section 摘要：

| section | present | length | truncated | omitted |
|---|---|---:|---|---|
| QUESTION-FOCUSED EVIDENCE | true | 1421 | false | false |
| CONTRIBUTION EVIDENCE | true | 6 | false | false |
| STRUCTURED FACT CARD EVIDENCE | true | 6 | false | false |
| SOURCE EVIDENCE | true | 4852 | true | false |
| GRAPH EVIDENCE | true | 6 | false | false |
| ARTICLE EVIDENCE | true | 6629 | true | false |

masked prompt snapshot 验证词可见性：

| 词 | 是否在完整 prompt snapshot 中可见 | 可见 section |
|---|---|---|
| 日结 | 是 | SOURCE EVIDENCE、ARTICLE EVIDENCE |
| 结算成功 | 否 | 无 |
| 小票 | 否 | 无 |

缺失 section 与截断位置：

| 缺失词 | QUESTION-FOCUSED | SOURCE | ARTICLE | 截断观察 |
|---|---|---|---|---|
| 结算成功 | 不含 | 不含 | 不含 | SOURCE 与 ARTICLE 均存在 `... [truncated]`；SOURCE snapshot 字符偏移约 3095 / 4720 / 6323，ARTICLE 约 9711 / 11312。 |
| 小票 | 不含 | 不含 | 不含 | 同上。 |

这说明 BANK-SETTLEMENT 当前不是“完整关键事实已进入 prompt 但 LLM 漏点”的可确认状态；至少 `结算成功`、`小票` 未进入本次可见 prompt snapshot。下一轮应先继续围绕 prompt evidence selection / retained content / 截断位置做只读定位，暂不建议直接转向 LLM 生成指令约束修复。

## 未触碰项

- 是否修改 `AnswerGenerationPromptEvidenceSupport.java`：否。
- 是否修改 retrieval / rerank / RRF / fusion：否。
- 是否修改 fallback：否。
- 是否修改 postprocess：否。
- 是否修改 outcome guard：否。
- 是否修改 citation：否。
- 是否修改 prompt 模板：否。
- 是否修改 runner / 正式题集：否。
- 是否新增数据库迁移：否。
- 是否提交代码：否。

## 下一步建议

只建议一个最小动作：基于本次 prompt snapshot，对 BANK-SETTLEMENT 做只读 prompt evidence 截断定位，确认 `结算成功`、`小票` 在候选 evidence 到最终 prompt section 之间具体在哪一步被裁掉；在该报告完成前不要继续修改 prompt evidence 逻辑。
