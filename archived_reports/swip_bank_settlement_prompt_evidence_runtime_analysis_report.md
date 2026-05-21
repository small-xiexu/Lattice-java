# SWIP-USAGE-BANK-SETTLEMENT-001 Prompt Evidence Runtime 分析报告

## 1. 结论

主因：**retrieval / fusion 已带回完整正文，但进入 LLM 的高可见 prompt evidence 被 focus snippet 与 bounded content 弱化；`结算成功` / `小票` 没有稳定进入可见证据位，或进入位置过弱，最终未被 LLM 使用。**

更精确地说：

- 不是 retrieval 缺失：三轮稳定 run 的 fused hits 已召回含完整结算正文的 article/source 证据。
- 不是 fusion retained content 缺失：article chunk 和 source chunk 均含 `日结`、`结算成功`、`小票`。
- 不是 postprocess 裁掉：修复前答案是拒答；尝试补丁后答案只生成了 `日结`，正文里从未生成 `结算成功` / `小票`。
- 当前系统没有保存完整 LLM input prompt；无法逐字证明“最终 prompt 原文”是否包含三个词。只能基于 DB retrieval audit、run 输出和 prompt builder 代码还原最接近的 prompt evidence。
- 可还原链路显示：`日结` 更容易进入前段 context 或 focus snippet；`结算成功` / `小票` 位于同一正文后半段，容易被 `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT=1200` 和单条 focus snippet 策略截断或弱化。

下一轮不建议直接继续改 prompt evidence 行为。先补最小 prompt audit，再决定是否修 evidence 排列。

## 2. Redline 与工作区

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1836 |
| ALLOWLIST | 238 |

执行命令：`bash scripts/scan-redline.sh special_cases_report.md`

工作区状态：

| 检查项 | 结果 |
|---|---|
| 生产代码未提交改动 | 是，`src/main/java` / `src/test/java` / `src/main/resources` / `docs/test` / `scripts` 均无未提交改动 |
| 本轮允许变更 | `special_cases_report.md` 被 redline 刷新 |
| 报告文件 | 当前存在未提交分析/结果报告文件，不属于生产代码 |

本轮未修改代码。

## 3. 对比 Run

| 阶段 | run | queryId | 结果 |
|---|---|---|---|
| 修复前稳定 R1 | `.codex/run/swip-answer-grounding-patch-stability-r1-173956` | `0c29f138-2e87-4151-9ba9-f3f996987a02` | FAIL，`INSUFFICIENT_EVIDENCE`，缺 `日结` / `结算成功` / `小票` |
| 修复前稳定 R2 | `.codex/run/swip-answer-grounding-patch-stability-r2-174427` | `1a42c400-21f7-42d8-99fa-3a98e9a27ebb` | FAIL，`INSUFFICIENT_EVIDENCE`，缺 `日结` / `结算成功` / `小票` |
| 修复前稳定 R3 | `.codex/run/swip-answer-grounding-patch-stability-r3-174741` | `a0b52fc1-4b9b-4b9a-8145-6ec6fe373799` | FAIL，`INSUFFICIENT_EVIDENCE`，缺 `日结` / `结算成功` / `小票` |
| prompt evidence 尝试 | `.codex/run/swip-bank-settlement-prompt-evidence-target-20260516-1907` | `2471f829-82f4-479c-b0a3-751a97fe73fa` | FAIL，`SUCCESS / LLM`，已含 `日结`，仍缺 `结算成功` / `小票` |
| prompt evidence main-target | `.codex/run/swip-bank-settlement-prompt-evidence-main-target-20260516-1930` | 无 | `request_error:fetch failed`，不作为行为证据 |

prompt evidence 尝试 run 没有落入当前 DB 的 `query_retrieval_runs` / `query_answer_audits` / `execution_llm_snapshots`，只能使用 `.codex/run/.../query_results.jsonl`。

## 4. Prompt 原文可观测性

当前没有完整 prompt snapshot：

| 位置 | 结果 |
|---|---|
| `execution_llm_snapshots` | 只保存模型路由、provider、model、温度、token 限制等快照；不保存 prompt 文本 |
| `query_answer_audits` | 保存最终 answer、outcome、generation mode；不保存 raw LLM input prompt，也不保存 raw LLM JSON outcome |
| `query_retrieval_runs` / `query_retrieval_channel_hits` | 保存 retrieval 元信息和 hit 元数据；不保存 prompt evidence section |
| Redis working set | 当前四个相关 queryId 均已无 `lattice:query:ws:<id>:*` key |
| `.codex/run/.../query_results.jsonl` | 保存最终 response / metrics / sourceText；不保存 LLM input prompt |

因此，本报告不能声称“逐字 prompt 原文包含/不包含某词”。下文的 prompt evidence 判断，是基于 fused hits、DB chunk 内容、`AnswerPromptBuilder` 与 `AnswerGenerationPromptEvidenceSupport` 代码路径还原。

## 5. Fused Hit 内容摘要

修复前三轮 retrieval audit 一致：

| queryId | answer_shape | fused_hit_count | channel_count | fact_card_hit_count | source_chunk_hit_count | coverage_status |
|---|---|---:|---:|---:|---:|---|
| `0c29f138-2e87-4151-9ba9-f3f996987a02` | `STATUS` | 7 | 11 | 0 | 2 | `covered` |
| `1a42c400-21f7-42d8-99fa-3a98e9a27ebb` | `STATUS` | 7 | 11 | 0 | 2 | `covered` |
| `a0b52fc1-4b9b-4b9a-8145-6ec6fe373799` | `STATUS` | 7 | 11 | 0 | 2 | `covered` |

top fused hits：

| rank | channel | evidence_type | 内容判断 |
|---:|---|---|---|
| 1 | `article_chunk_fts` / `article_vector` / `chunk_vector` | ARTICLE | 命中同一手册文章；`article_chunk` 的正文段包含完整结算段 |
| 2 / 3 | `source_chunk_fts` | SOURCE | 命中同一源文件 chunk；原始 chunk 包含完整结算段 |

库内位置：

| 位置 | `日结` | `结算成功` | `小票` | 说明 |
|---|---:|---:|---:|---|
| `source_file_chunks.chunk_text` chunk 0 | 约 721 | 约 1980 | 约 2008 | 源 chunk 前部含目录和正文；结果句在较后位置 |
| `article_chunks.chunk_text` chunk 1 | 约 1974 | 约 2294 | 约 2370 | article chunk 前部先是前一章节和消费/退款内容；结算段在后半 |

结论：fused hit 内容本身不缺。

## 6. 可还原 Prompt Evidence

`AnswerPromptBuilder.buildAnswerPrompt(...)` 拼装顺序：

1. `QUESTION`
2. `QUESTION-FOCUSED EVIDENCE`
3. `CONTRIBUTION EVIDENCE`
4. `STRUCTURED FACT CARD EVIDENCE`
5. `SOURCE EVIDENCE`
6. `GRAPH EVIDENCE`
7. `ARTICLE EVIDENCE`

当前关键限制：

| 规则 | 当前行为 |
|---|---|
| 单 hit content 上限 | `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200` |
| section hit 上限 | `PROMPT_EVIDENCE_SECTION_HIT_LIMIT = 6` |
| focus snippet 数 | `buildPromptFocusSnippets(...)` 对 exact / enum / flow 才取 2，否则取 1 |
| 本问题在 retrieval 的 answer_shape | `STATUS`，由 query semantic 里的中文“结果”触发 |
| 本问题在 answer prompt snippet 侧 | 不稳定等价于 STATUS；`looksLikeStatusQuestion(...)` 主要看英文 status/state/current 等，不直接使用 retrieval `answer_shape` |

这导致两个实际后果：

- 问题虽然问“什么时候执行”和“执行后结果”，prompt focus snippet 侧更像普通问题处理，通常只有 1 条强 focus snippet。
- `source_file_chunks` 中 `日结` 更靠前，可能进入 bounded context；`结算成功` / `小票` 位于 1200 字之后，除非被 focus snippet 选中，否则会被 content context 截断。

## 7. 三个 Term 的链路位置

| term | fused hit | 可还原 prompt evidence | final answer 修复前 | final answer 尝试补丁后 | 判断 |
|---|---|---|---|---|---|
| `日结` | 有 | 大概率有：source chunk 前段 context 可覆盖；尝试补丁后答案已使用 | 无 | 有 | 可进入或已被补丁带出 |
| `结算成功` | 有 | 不能确认进入完整 prompt；按位置和 1200 限制，大概率未进入高可见 snippet/content | 无 | 无 | 在 prompt focus / bounded content 环节丢失或弱化 |
| `小票` | 有 | 不能确认进入完整 prompt；按位置和 1200 限制，大概率未进入高可见 snippet/content | 无 | 无 | 在 prompt focus / bounded content 环节丢失或弱化 |

如果完整 prompt 原文实际包含 `结算成功` / `小票`，也应归为“位置太靠后/弱化，LLM 未使用”；但当前缺少 prompt snapshot，不能直接证明。

## 8. LLM 实际使用了哪些证据

修复前三轮答案：

- 答案使用的是目录/章节存在信息。
- `query_answer_claims` 中的 claim 均为“证据不足”“只看到章节”“正文未出现”等。
- `query_answer_citations.matched_excerpt` 多数命中封面、目录、报修电话、标题、referential keywords 等弱片段。
- 没有 claim 使用完整结算正文。

prompt evidence 尝试补丁后：

- 答案变为 `SUCCESS / LLM`，并写出建议在每日日结后执行。
- 仍未写出成功提示和打印小票。
- 答案中“对账和汇总处理”不是题集要求的结果句，且不覆盖 `结算成功` / `小票`。

结论：上一轮尝试只让 LLM 使用了正文前半段，未让同一直接证据中的后半结果句进入或占据足够强的位置。

## 9. 丢失环节定位

| 环节 | 是否丢失 | 证据 |
|---|---|---|
| retrieval | 否 | 三轮 `coverage_status=covered`，top fused hits 命中含完整正文的 source/article |
| fusion retained content | 否 | DB chunk 内容含三项 term；前一轮 outcome guard 报告也确认 fused hits 含完整正文 |
| prompt evidence bounded content | 是，主风险点 | content 上限 1200；结果句在 source chunk 约 1980/2008、article chunk 约 2294/2370 |
| prompt focus snippets | 是，主风险点 | 当前问题在 prompt snippet 侧通常只取 1 条；结果句没有因中文“结果/成功/小票”获得稳定强信号 |
| LLM 生成 | 次要表现 | LLM 只使用目录或前半句；但更上游的 prompt evidence 可见性不足是更强解释 |
| postprocess | 否 | 最终答案从未生成 `结算成功` / `小票`，不是被裁掉 |

主因落点：**prompt focus snippets + bounded content**。

## 10. 上一轮修复为什么只带出 `日结`

上一轮尝试修改了 prompt evidence 组装，结果从“拒答”改善为“回答执行时机”，说明补丁确实提高了部分 direct evidence 可见性。

但它没有解决后半句问题：

- `日结` 是结算段 lead-in，语义上更像“什么时候执行”的直接答案。
- `结算成功` / `小票` 是同一段后续结果，位置更靠后。
- 当前 focus snippet 数量和 content 上限不足以保证“时机 + 执行后结果”两个问题面都被覆盖。
- prompt 仍没有显式把多子问题拆成必须覆盖的多个 evidence facet。

所以补丁改善了一个子问题，但没有覆盖第二个子问题。

## 11. 上一轮为什么让 IP-SUFFIX 走 FALLBACK

有效 run 中 `SWIP-INSTALL-IP-SUFFIX-001`：

| 字段 | 值 |
|---|---|
| pass | false |
| failedReason | `answer_missing_term:150` / `answer_missing_term:151` |
| answerOutcome | `SUCCESS` |
| generationMode | `FALLBACK` |
| modelExecutionStatus | `DEGRADED` |
| fallbackReason | `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` |

代码路径判断：

- `preferDeterministicExactLookupPayload(...)` 会在 exact lookup 问题中，用 deterministic fallback 替换 LLM payload。
- 触发条件包括 LLM payload 非 `SUCCESS`、过度保守、或 grounding mismatch。
- 当前 run 只保存最终 `fallbackReason=DETERMINISTIC_EXACT_LOOKUP_PREFERRED`，没有保存 preferenceReason / raw LLM payload。

可观察后果：

- deterministic fallback 选中的证据是初始化/静态 IP 一般说明，只覆盖 `.149`。
- 该 fallback 没覆盖 `.150` / `.151`，所以保护 case 回归。

判断：上一轮补丁改变 prompt evidence 后，很可能扰动了 exact lookup LLM payload 或 grounding 判定，触发 deterministic fallback 替换；但具体是 overcautious 还是 grounding mismatch，现有 audit 不能证明。

## 12. 下一轮建议

下一轮不建议直接修 answer behavior。

唯一最小动作：**先补 prompt audit / runtime prompt snapshot**，只为 answer LLM 调用保存可审计的 prompt evidence，不改变生成、检索、fallback、postprocess 行为。

如果允许做最小代码改动，建议范围只限：

- 文件：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java`
- 方法：`generatePayloadByLlm(...)`
- 动作：将 `answerPromptBuilder.buildAnswerPrompt(question, queryArticleHits)` 的返回值先保存为局部变量，并在 query audit / debug artifact 中记录完整 prompt 或 evidence section 摘要、term presence、长度与截断标记。

不建议在缺少 prompt 原文前继续修改 `AnswerGenerationPromptEvidenceSupport.java` 的排序、截断或 snippet 数量；上一轮已经证明盲改有保护 case 负收益。

## 13. 本轮修改范围

本轮是否修改代码：**否**。

本轮只新增本报告，并按允许范围刷新 `special_cases_report.md`。
