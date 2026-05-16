# SWIP-USAGE-BANK-SETTLEMENT-001 Outcome Guard 归因分析报告

## 1. 结论

本轮主因分类：**LLM 原始回答本身拒答**。

当前证据不支持把 `SWIP-USAGE-BANK-SETTLEMENT-001` 的三轮 `INSUFFICIENT_EVIDENCE` 归因于 outcome guard 过宽。更强证据是：

- 该 case 在 outcome guard 引入前的稳定 run 中已经多次表现为 `INSUFFICIENT_EVIDENCE`。
- 最近三轮的答案正文均从首行开始表达证据不足，不是“正确答案被 outcome 标签降级”。
- 当前库内源文、source chunk、article、article chunk 均存在题集要求的核心证据。
- 三轮 retrieval 均为 `coverage_status=covered`，直接证据进入 fused hits。
- Redis working set 中 `draft-answer` 已经是拒答式正文，说明问题发生在 LLM 生成阶段，而不是 citation / projection 后处理裁掉了正确答案。

因此，本 case 不建议回退或扩大 outcome guard。下一轮如修代码，应只针对回答生成阶段“已召回直接证据但 LLM 未使用”的通用 grounding 能力做一个最小修复。

## 2. Redline

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 238 |

执行命令：`bash scripts/scan-redline.sh special_cases_report.md`

说明：本轮 redline 仅刷新 `special_cases_report.md`，属于允许范围；未修改生产代码、测试、配置、题集、脚本或 allowlist。

## 3. 题集期望

| 字段 | 内容 |
|---|---|
| caseId | `SWIP-USAGE-BANK-SETTLEMENT-001` |
| question | `银行卡结算建议在什么时候执行，执行后会出现什么结果？` |
| answerability | `ANSWERABLE` |
| requiredAnswerTerms | `每日日结后`、`结算成功`、`结算小票` |
| expect.requiredAnswerTerms | `日结`、`结算成功`、`小票` |
| expectedEvidence.quoteHint | 原文说明每日日结后建议执行结算，成功后键盘提示结算成功，弹窗关闭后打印结算小票 |
| mustNotClaim | 不得说每天开店前执行；不得说成功后不打印小票 |
| humanJudgement.passRule | 必须说明建议执行时机，以及成功后的键盘提示、POS 返回、打印小票结果 |

判断：这些 required terms 与原文、题意和人工验收规则一致，当前不属于 eval 期望过严。

## 4. 三轮实际结果

| 轮次 | queryId | pass | failedReason | answerOutcome | generationMode | modelExecutionStatus | source/article | citation |
|---|---|---:|---|---|---|---|---|---|
| R1 | `0c29f138-2e87-4151-9ba9-f3f996987a02` | false | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 1 / 1 | markers=4, coverage=1, verified=6 |
| R2 | `1a42c400-21f7-42d8-99fa-3a98e9a27ebb` | false | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 1 / 1 | markers=3, coverage=1, verified=3 |
| R3 | `a0b52fc1-4b9b-4b9a-8145-6ec6fe373799` | false | `answer_missing_term:日结\|answer_missing_term:结算成功\|answer_missing_term:小票` | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | 1 / 1 | markers=4, coverage=1, verified=4 |

答案摘要：

| 轮次 | 答案形态 |
|---|---|
| R1 | 首句即称证据不足，无法确认执行时机和执行结果；随后只承认目录中存在相关章节，并称正文未出现。 |
| R2 | 首句称现有证据不足以确认；随后建议补充该章节原文或截图。 |
| R3 | 首句称现有证据不足以确认；随后称片段没有包含具体说明。 |

结论：三轮答案都不是“内容正确但 outcome 被下调”，而是答案正文自身已经拒答，且漏掉全部机器断言 term。

## 5. Outcome Guard 核验

`AnswerGenerationPayloadOrchestrator.normalizeStructuredAnswerOutcome(...)` 的 guard 只在以下条件同时成立时生效：

- 模型声明的原始 `answerOutcome == SUCCESS`
- `answerMarkdown` 看起来是证据不足式回答

当前持久化 eval / audit 只保留最终 `answerOutcome`，未保留 LLM 原始 JSON 中的 raw outcome，因此不能直接证明三轮是否实际从 `SUCCESS` 被 guard 下调。

如果三轮 raw outcome 曾是 `SUCCESS`，正文会命中 early insufficient evidence signal：

| 轮次 | 命中行 | 信号类型 |
|---|---|---|
| R1 | 首行开头即包含“证据不足”，并包含“无法确认” | first meaningful line early signal |
| R2 | 首行包含“现有证据不足以确认” | first meaningful line early signal |
| R3 | 首行包含“现有证据不足以确认” | first meaningful line early signal |

不是多行 conclusion signal。

但归因上不能据此认定 guard 过宽，因为：

- guard 引入前的 `swip-rrf-revert-check` 与 `swip-stability-round1/2/3` 中，该 case 已为 `INSUFFICIENT_EVIDENCE / LLM / SUCCESS`。
- `swip-structured-leadin-fix` 阶段同样为 `INSUFFICIENT_EVIDENCE`。
- 最近三轮 Redis working set 的 `draft-answer` 已是拒答式正文，说明拒答发生在生成结果本身，而非后续 citation / projection 或段落裁剪阶段。

判断：如果没有当前 outcome guard，该 case 仍高度可能保持 `INSUFFICIENT_EVIDENCE`，至少现有证据不支持推断它会稳定变为 `PARTIAL_ANSWER` 或 `SUCCESS`。

## 6. 证据链核验

库内只读查询结果：

| 位置 | 是否存在 `日结` | 是否存在 `结算成功` | 是否存在 `小票` | 结论 |
|---|---:|---:|---:|---|
| `source_files.content_text` | 是 | 是 | 是 | 源文不缺 |
| `source_file_chunks.chunk_text` | 是 | 是 | 是 | 原文 chunk 不缺 |
| `articles.content` | 是 | 是 | 是 | 编译文章不缺 |
| `article_chunks.chunk_text` | 是 | 是 | 是 | article chunk 不缺 |
| `fact_cards.claim/evidence_text` | 是 | 否 | 否 | fact card 不完整，但非主因 |

三轮 retrieval audit：

| queryId | answer_shape | fused_hit_count | channel_count | fact_card_hit_count | source_chunk_hit_count | coverage_status |
|---|---|---:|---:|---:|---:|---|
| `0c29f138-2e87-4151-9ba9-f3f996987a02` | `STATUS` | 7 | 11 | 0 | 2 | `covered` |
| `1a42c400-21f7-42d8-99fa-3a98e9a27ebb` | `STATUS` | 7 | 11 | 0 | 2 | `covered` |
| `a0b52fc1-4b9b-4b9a-8145-6ec6fe373799` | `STATUS` | 7 | 11 | 0 | 2 | `covered` |

三轮 channel hits 均显示：

- fused rank 1 包含 `article_chunk_fts`、`article_vector`、`chunk_vector` 命中的同一手册文章。
- fused rank 2/3 包含 `source_chunk_fts` 命中的同一源文件 chunk。
- fact card hit 为 0，但 source/article direct evidence 已足够回答。

Redis working set 中 fused hits 的 rank 1 ARTICLE 内容包含该问题所需的直接证据，说明 retrieval 与 fused hit retained content 没有整体缺失。

判断：不是编译入库缺失，不是 retrieval 未召回，也不是 fact card 缺失导致无法回答。

## 7. 排他判断

| 候选原因 | 判断 | 理由 |
|---|---|---|
| outcome guard 过宽 | 否 | 失败早于 guard；raw outcome 未持久化；正文自身拒答。 |
| LLM 原始回答本身拒答 | 是 | 三轮答案首行均拒答；draft answer 已是拒答；证据已召回但未被用于作答。 |
| evidence / retrieval 不足 | 否 | 源文、chunk、article、article chunk 均有证据；retrieval 为 covered。 |
| citation / projection 问题 | 否 | draft 阶段已拒答；citation coverage 为 1，不是后处理裁掉正确内容。 |
| eval 期望过严 | 否 | required terms 对应原文和人工 passRule。 |
| 其他 | 否 | 暂无更强证据。 |

## 8. 应该怎样处理该 case

不建议保持 `INSUFFICIENT_EVIDENCE` 作为产品行为，因为当前可用证据足以回答。

不建议只把 outcome 改为 `PARTIAL_ANSWER`，因为答案正文仍缺少关键内容；只改标签会掩盖 answer grounding 问题。

不建议回退或扩大 outcome guard。该 guard 对“SUCCESS + 拒答正文”的规范化仍是通用保护，本 case 没有证明它误伤。

建议方向是让 LLM 在已召回直接证据存在时生成更完整回答，而不是继续承认目录存在后拒答。

## 9. 下一轮唯一最小建议

建议下一轮允许修代码，但只处理一个最小点：

- 文件范围：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java`
- 方法范围：`buildBoundedPromptEvidenceContent(...)` / `buildPromptFocusSnippets(...)`
- 修复目标：通用地提高 prompt evidence 对“问题已命中直接正文片段”的保留和贴题呈现，避免 LLM 在 source/article 已召回 direct evidence 时只基于目录或泛化上下文拒答。

约束：

- 不修改 outcome guard。
- 不修改题集 required terms。
- 不写业务域、文档名、题目文本、答案片段特判。
- 不处理其他 SWIP 失败。

## 10. 本轮修改范围

本轮是否修改代码：**否**。

本轮只新增本分析报告，并按允许范围刷新 `special_cases_report.md`。
