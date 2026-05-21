# SWIP-USAGE-BANK-SETTLEMENT-001 Prompt Evidence 截断链路分析报告

## 1. 结论

本轮主因判断：**关键事实没有在 retrieval / chunk / fused hit 阶段丢失，而是在最终 Answer LLM prompt 的 evidence 构建阶段被弱化和截断。**

更具体地说：

- 源文、source chunk、article、article chunk、Redis fused hits / `QueryArticleHit.content` 均包含 `日结`、`结算成功`、`小票`。
- 最终 prompt snapshot 中只出现 `日结`，且主要来自目录或旁路上下文；`结算成功`、`小票`完全不可见。
- `QUESTION-FOCUSED EVIDENCE` 没选中目标正文。
- `SOURCE EVIDENCE` 和 `ARTICLE EVIDENCE` 都存在，但单条 evidence content 被 `... [truncated]` 截断；目标结果句位于 retained content 后半段，未进入 1200 字左右的 bounded content。

关键事实最可能丢失阶段：**prompt focus snippet 没选中目标句，随后 SOURCE / ARTICLE evidence 的 per-hit bounded content 从内容头部截断，导致后半段结果事实没有进入最终 prompt。**

## 2. Redline 与工作区

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1846 |
| ALLOWLIST | 238 |

执行命令：`bash scripts/scan-redline.sh special_cases_report.md`

工作区摘要：

| 项 | 状态 |
|---|---|
| 当前分支 | `codex/qa-polish...origin/codex/qa-polish` |
| 既有生产代码改动 | `AnswerGenerationPayloadOrchestrator.java` 有 prompt audit instrumentation 改动 |
| redline 报告 | `special_cases_report.md` 已由 redline 刷新 |
| 本轮是否修改源码 | 否 |
| 本轮是否修改测试 / 配置 / 题集 / 脚本 | 否 |
| 本轮新增 / 更新报告 | `swip_bank_settlement_prompt_evidence_truncation_analysis_report.md` |

`git diff --stat` 当前显示既有 instrumentation 与 redline 报告变更：

| 文件 | 摘要 |
|---|---|
| `special_cases_report.md` | 64 行变更 |
| `AnswerGenerationPayloadOrchestrator.java` | 171 行变更，来自既有 prompt audit instrumentation |

## 3. Prompt Audit Run

审计目录：

```text
.codex/run/swip-answer-prompt-audit-bank-settlement-20260516-203013
```

单 case 结果：

| case | pass | answerOutcome | generationMode | modelExecutionStatus | failedReasons |
|---|---:|---|---|---|---|
| `SWIP-USAGE-BANK-SETTLEMENT-001` | false | `INSUFFICIENT_EVIDENCE` | `LLM` | `SUCCESS` | `answer_missing_term:日结`; `answer_missing_term:结算成功`; `answer_missing_term:小票` |

Prompt audit 摘要：

| 项 | 值 |
|---|---:|
| promptLength | 13093 |
| containsTruncatedSuffix | true |
| containsOmittedMarker | false |

Section 摘要：

| section | present | length | truncated | omitted |
|---|---:|---:|---:|---:|
| QUESTION-FOCUSED EVIDENCE | true | 1421 | false | false |
| CONTRIBUTION EVIDENCE | true | 6 | false | false |
| STRUCTURED FACT CARD EVIDENCE | true | 6 | false | false |
| SOURCE EVIDENCE | true | 4852 | true | false |
| GRAPH EVIDENCE | true | 6 | false | false |
| ARTICLE EVIDENCE | true | 6629 | true | false |

## 4. 关键 Term 链路矩阵

| 阶段 | `日结` | `结算成功` | `小票` | 判断 |
|---|---:|---:|---:|---|
| `source_files.content_text` | 有，2 条 source 命中 | 有，1 条 source 命中 | 有，1 条 source 命中 | 源文不缺 |
| `source_file_chunks.chunk_text` | 有，5 个 chunk 命中 | 有，1 个 chunk 命中 | 有，1 个 chunk 命中 | chunk 不缺 |
| `articles.content` | 有，2 篇 article 命中 | 有，1 篇 article 命中 | 有，1 篇 article 命中 | 编译文章不缺 |
| `article_chunks.chunk_text` | 有，9 个 chunk 命中 | 有，1 个 chunk 命中 | 有，1 个 chunk 命中 | article chunk 不缺 |
| `fact_cards` | 有，5 张 card 命中 | 无 | 无 | fact card 不完整，但本 case 已有 source/article 直接证据 |
| Redis `hits-article_chunk_fts` | 有，rank 1 content pos 1973 | 有，rank 1 content pos 2293 | 有，rank 1 content pos 2369 | 检索召回直接证据 |
| Redis `hits-source_chunk_fts` | 有，rank 1 content pos 720 | 有，rank 1 content pos 1979 | 有，rank 1 content pos 2007 | 检索召回直接证据 |
| Redis `fused-hits` | 有，rank 1/2 均可见 | 有，rank 1/2 均可见 | 有，rank 1/2 均可见 | fusion retained content 不缺 |
| `QUESTION-FOCUSED EVIDENCE` | 无 | 无 | 无 | focus snippet 未选中目标正文 |
| `SOURCE EVIDENCE` | 有，4 次 | 无 | 无 | section 内 content 被截断 |
| `ARTICLE EVIDENCE` | 有，1 次 | 无 | 无 | section 内 content 被截断 |
| final answer | 无 | 无 | 无 | LLM 没看到或没使用关键事实 |

## 5. Source / Article Chunk 位置

只读数据库查询显示：

| 位置 | id | chunkIndex | len | `日结` pos | `结算成功` pos | `小票` pos |
|---|---:|---:|---:|---:|---:|---:|
| `source_file_chunks` | 1 | 0 | 3595 | 721 | 1980 | 2008 |
| `article_chunks` | 1 | 1 | 3577 | 1974 | 2294 | 2370 |

这解释了为什么 `日结` 更容易进入 prompt，而 `结算成功` / `小票` 没进 prompt：当前 prompt evidence 单 hit content 会优先放 focus snippet，再追加从 `QueryArticleHit.content` 头部截取的 context。结果事实位于 chunk 后半段，如果没被 focus snippet 单独选中，就会被 per-hit content 上限截掉。

## 6. Fused Hit / QueryArticleHit 核验

Redis working set key：`lattice:query:ws:5c973b7e-d6ae-406a-8086-6564e1d85b93:*`

关键命中：

| key | rank | evidenceType | content length | `日结` pos | `结算成功` pos | `小票` pos | 判断 |
|---|---:|---|---:|---:|---:|---:|---|
| `fused-hits` | 1 | ARTICLE | 3577 | 1973 | 2293 | 2369 | 直接证据完整 |
| `fused-hits` | 2 | SOURCE | 3595 | 720 | 1979 | 2007 | 直接证据完整 |
| `hits-article_chunk_fts` | 1 | ARTICLE | 3577 | 1973 | 2293 | 2369 | FTS 已召回 |
| `hits-source_chunk_fts` | 1 | SOURCE | 3595 | 720 | 1979 | 2007 | FTS 已召回 |
| `hits-source` | 1 | SOURCE | 4510 | 720 | 1979 | 2007 | source evidence 已召回 |

`RrfFusionService` 在 fusion 时用首个命中的 `QueryArticleHit.content` 构造 fused hit，只重算融合分数，不改写 content。当前 Redis 中 fused hit content 已包含完整目标事实，因此不能归因到 RRF retained content 缺失。

## 7. 最终 Prompt Section 细节

从 `server.log` 中的 masked prompt snapshot 解析：

| section | `日结` count | `结算成功` count | `小票` count | 截断说明 |
|---|---:|---:|---:|---|
| QUESTION-FOCUSED EVIDENCE | 0 | 0 | 0 | 未选中目标正文 |
| SOURCE EVIDENCE | 4 | 0 | 0 | 出现 `... [truncated]`，截断发生在目录 / 系统目标附近 |
| ARTICLE EVIDENCE | 1 | 0 | 0 | 出现 `... [truncated]`，目标 article content 截断在前置章节 / 退款段附近，未到后续结果句 |

SOURCE section 的可见 `日结` 主要来自目录或旁路内容，例如目录中的每日结算项，不是完整目标结果事实。ARTICLE section 中目标 article 虽然出现，但其 bounded content 先放前置消费 / 退款相关上下文，随后在到达目标结果句之前被截断。

## 8. 相关代码路径解释

`AnswerGenerationPayloadOrchestrator`

- 当前 instrumentation 只把 `AnswerPromptBuilder.buildAnswerPrompt(...)` 的结果保存为局部变量并打 audit / snapshot 日志。
- 没有改变 prompt 内容、retrieval、RRF、postprocess 或 outcome guard。

`AnswerPromptBuilder`

- `buildAnswerPrompt(...)` 固定按 `QUESTION`、`QUESTION-FOCUSED EVIDENCE`、`CONTRIBUTION`、`FACT CARD`、`SOURCE`、`GRAPH`、`ARTICLE` 顺序拼接。
- 本 case 的 `SOURCE EVIDENCE` 和 `ARTICLE EVIDENCE` 都存在，没有被 section-level omitted 掉。

`AnswerGenerationPromptEvidenceSupport`

- `appendQuestionFocusedEvidenceSection(...)` 会对所有 hit 排序后，最多追加 6 条 focus snippets。
- `appendEvidenceSection(...)` 对每条 hit 调用 `buildPromptFocusSnippets(...)`，再调用 `buildBoundedPromptEvidenceContent(...)`。
- `buildPromptFocusSnippets(...)` 对 exact lookup / enum / flow 问法取 2 条 snippet，否则取 1 条。当前问题没有在 prompt snippet 侧稳定变成多 snippet 覆盖。
- `buildBoundedPromptEvidenceContent(...)` 先放 focus snippets，再放 fallback snippet，然后从 `QueryArticleHit.content` 头部追加 context，并按 `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT` 截断。

`QueryArticleHit`

- `content` 是 prompt evidence 使用的 retained content。
- 当前 Redis `QueryArticleHit.content` 已包含目标结果事实，说明对象载荷不缺。

`RrfFusionService`

- `mergeHits(...)` 以 hit key 合并不同 channel，只保留首个 `QueryArticleHit`，并累加 RRF score。
- `fuse(...)` 生成新 `QueryArticleHit` 时沿用原 hit 的 content。
- 当前 fused hit rank 1/2 content 已包含目标事实，因此 RRF fusion 不是本轮主因。

## 9. 丢失环节排除表

| 候选环节 | 结论 | 理由 |
|---|---|---|
| 原始 source 缺失 | 否 | `source_files.content_text` 三项均存在 |
| article chunk 缺失 | 否 | `article_chunks.chunk_text` 三项均存在 |
| fused hit retained content 缺失 | 否 | Redis `fused-hits` rank 1/2 三项均存在 |
| question-focused snippet 没选中 | 是 | `QUESTION-FOCUSED EVIDENCE` 三项均无 |
| SOURCE evidence 截断 | 是 | SOURCE section 有 `[truncated]`，只见 `日结`，不见结果事实 |
| ARTICLE evidence 截断 | 是 | ARTICLE section 有 `[truncated]`，不见结果事实 |
| prompt section budget 裁剪 | 次要否 | `containsOmittedMarker=false`，section 没整体 omitted；主要是 per-hit content 截断 |
| LLM 明明看到但没使用 | 证据不足 | prompt snapshot 中 `结算成功` / `小票`不可见，因此不应把主因归给 LLM 漏用 |
| postprocess 裁剪 | 否 | final answer 从未生成这两个 term，不是后处理裁掉 |

## 10. 为什么上一轮 Prompt Evidence 尝试负收益

上一轮尝试能带出 `日结`，说明它提高了部分直接证据可见性；但没有解决“同一 chunk 后半段结果事实”问题。

负收益的可能原因：

- 只增强了前段或局部 evidence 的可见性，仍无法保证多事实问题覆盖同一 retained content 内的后续结果句。
- 如果通过扩大或重排 prompt evidence 粗暴增加内容，会挤压其他保护 case 的高价值 evidence，使原本应走 LLM 的 case 退化到 fallback 或拿不到正确片段。
- 这说明下一轮不宜单纯扩大 section budget 或复活粗粒度 retained content / companion snippet 方案。

## 11. 下一轮建议

建议下一轮可以修代码，但只允许一个最小修复点：

| 项 | 建议 |
|---|---|
| 修复方向 | `focus snippet 选择` |
| 最小文件范围 | `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java` |
| 最小方法范围 | `buildPromptFocusSnippets(...)` |
| 目标 | 通用地让多事实 / 状态结果类问题从同一 retained content 中选出覆盖不同问题焦点的多条 snippet，避免只选目录、标题、前置章节或第一个高分片段 |

不建议下一轮优先修：

- `retained content 选择`：当前 retained content 已完整包含关键事实。
- `source/article evidence section budget`：扩大预算风险更大，上一轮负收益提示这不是最小安全点。
- `prompt evidence ordering`：目标 hit 已进入 prompt，问题主要是 hit 内部 snippet 与 bounded content。

避免 case 特判的约束：

- 不写业务域、资料名、章节名、题目文本、答案片段、具体 term 分支。
- 只基于通用问题结构处理：例如多问句、多焦点词、结果/状态/后果/输出类通用信号、同一 evidence 内不同匹配窗口覆盖。
- 只使用通用文本结构和 query token 覆盖度，不绑定某份资料或某个评测 case。

## 12. 本轮修改范围

本轮是否修改代码：**否**。

本轮是否修改测试、配置、题集、脚本、模型配置：**否**。

本轮是否清库、重建库、重新导入资料、提交代码：**否**。

本轮仅新增本分析报告，并按允许范围运行 redline 刷新 `special_cases_report.md`。
