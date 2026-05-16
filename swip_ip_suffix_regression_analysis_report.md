# SWIP-INSTALL-IP-SUFFIX-001 回归归因分析报告

- 生成时间：2026-05-16
- 角色：agentB
- 本轮性质：只读回归归因分析
- 本轮是否修改代码：否

## 1. Redline 与 Git 状态

| 项 | 结果 |
|---|---:|
| redline BLOCKER | 0 |
| redline REVIEW | 1836 |
| redline ALLOWLIST | 218 |

当前 `git diff --stat` 摘要：

| 文件 | 变更摘要 |
|---|---:|
| `special_cases_report.md` | 57 行变更，来自 redline 刷新 |
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` | 183 行新增，outcome guard |
| `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java` | 191 行变更，structured/exact lookup lead-in 裁剪修复 |

`git status --short --branch` 显示当前分支为 `codex/qa-polish...origin/codex/qa-polish`，生产代码改动仍限于上述两个 query service 文件。本轮未修改源码、测试、配置、题集、脚本、数据库内容；仅允许范围内刷新 redline 报告并新增本分析报告。

## 2. 题集期望

| 字段 | 内容 |
|---|---|
| case | `SWIP-INSTALL-IP-SUFFIX-001` |
| priority / visibility / type | `P0` / `INTERNAL_ONLY` / `RULE_CONSTRAINT` |
| question | POS 机号 1 和 POS 机号 2 分别对应键盘 IP 的哪个后缀，如果顺序颠倒应该怎么调整？ |
| requiredAnswerTerms | `149`, `150`, `151` |
| requiredSourceTerms | `POS机号`, `IP后缀` |
| expectedPoints | POS 机号 1 对应后缀 `149`；POS 机号 2 对应后缀 `150`；顺序颠倒时需要用 `151` 作为中间后缀完成三步调整 |
| expectedEvidence | `SWIP智能键盘系统安装手册-202509.docx`，quoteHint 指向 POS 机号与键盘 IP 后缀规则及顺序颠倒调整说明 |
| mustNotClaim | 不得声称 POS 机号 1 对应后缀 150；不得说直接把两台机器 IP 对调即可 |
| humanJudgement | 必须同时覆盖 149/150 对应关系和 149→151→150→149→151→150 的调整思路 |

判断：`151` 是源文直接表达的临时调整后缀，也是 humanJudgement 明确要求的一部分，适合当前机器硬断言；不属于明显过严或仅适合人工验收的字段。

## 3. 三轮实际答案对比

最新三轮目录：

- `.codex/run/swip-grounding-stability-round1`
- `.codex/run/swip-grounding-stability-round2`
- `.codex/run/swip-grounding-stability-round3`

| 轮次 | queryId | pass | failedReason | answerOutcome | generationMode | modelExecutionStatus | sourceCount / articleCount | citationCoverage | citation verified / demoted | answer 是否含 `151` |
|---|---|---|---|---|---|---|---:|---:|---:|---|
| R1 | `31f8f83d-3ec7-4fc1-ae07-1644e9c03bc1` | FAIL | `answer_missing_term:151` | `SUCCESS` | `LLM` | `SUCCESS` | 1 / 2 | 0.6667 | 2 / 1 | 否 |
| R2 | `966e42c5-aa9c-4b99-a332-fd9a087db400` | FAIL | `answer_missing_term:151` | `SUCCESS` | `LLM` | `SUCCESS` | 1 / 2 | 0.6667 | 2 / 1 | 否 |
| R3 | `6dbab749-613a-4f1f-8a9d-170db287601a` | FAIL | `answer_missing_term:151` | `SUCCESS` | `LLM` | `SUCCESS` | 1 / 2 | 0.6667 | 2 / 1 | 否 |

三轮答案正文完全同形：先给一句“对应关系如下”，然后只保留一个 Markdown 表格，表格只覆盖 `1 -> 149` 与 `2 -> 150`。答案中完全没有 `151`，也没有“顺序颠倒时怎么调整”的后续段落。

返回来源与 citation：

| 项 | 三轮共同表现 |
|---|---|
| response.sources | 只返回 `HTTPS证书安装（门店内网）：点击“HTTPS服务证书补下载安装”` |
| response.articles | 包含上述 HTTPS 文章与 `FAQ 33` |
| citation claim | 3 条：引导句 1 条、`1 -> 149`、`2 -> 150` |
| citation 状态 | 引导句对 HTTPS 文章 `DEMOTED / insufficient_overlap`；两条数字对应关系使用 source file 直连校验为 `VERIFIED` |
| 缺失内容 | 没有 `151` 调整步骤 claim，因此 citation 绑定没有机会校验该步骤 |

补充：三条 answer audit 的 `cacheable=true`；R2/R3 耗时约 1 秒，可能复用了 R1 的可缓存答案。因此“三轮稳定 FAIL”更像同一错误答案稳定复用，不一定代表三次独立 LLM 都重新生成了相同遗漏。

## 4. `151` 在数据与检索链路中的位置

当前 SWIP clean 库规模：`source_files=2`、`articles=4`、`article_chunks=19`、`fact_cards=14`。

| 层级 | 是否存在 `151` | 只读查询结果 |
|---|---|---:|
| `source_files.content_text` | 是 | 1 |
| `source_file_chunks.chunk_text` | 是 | 3 |
| `articles.content/summary/search_text/refkey_text` | 是 | 3 |
| `article_chunks.chunk_text` | 是 | 5 |
| `fact_cards.claim/evidence_text/items_json/title` | 否 | 0 |

关键源文片段含义：源文件和 source chunk 中均有“POS 机号 1 对应后缀 149、POS 机号 2 对应后缀 150；顺序颠倒时先把 149 改成 151，再把 150 改成 149，最后把 151 改成 150”的完整规则。

命中的 article/chunk：

| article | 是否含 `151` | 说明 |
|---|---|---|
| `FAQ 33` | 是 | 正文第 4 节直接包含完整后缀对应关系和调整步骤 |
| `HTTPS证书安装（门店内网）：点击“HTTPS服务证书补下载安装”` | 是 | referential keywords 与 chunk 中包含同一规则 |
| `系统架构 5` | 是 | 关键词/表格化说明中包含 149/150/151 |

检索 audit：

| 轮次 | answer_shape | fused_hit_count | coverage_status | fact_card_hit_count | source_chunk_hit_count |
|---|---|---:|---|---:|---:|
| R1/R2/R3 | `SEQUENCE` | 8 | `covered` | 0 | 3 |

三轮 retrieval fused 结果均显示：

- fused rank 1：`FAQ 33`，article 与 article chunk 均含 `151`。
- fused rank 2：`HTTPS证书安装...`，article 与 article chunk 均含 `151`。
- fused rank 3：`系统架构 5`，article 与 article chunk 均含 `151`。
- fused rank 4-6：source chunk 命中，三条 source chunk 均含 `151`。
- fact card 通道没有命中，且当前 fact card 本身不含 `151`；但 article/source 证据已经覆盖该字段，所以 fact card 缺失不是主因。

判断：不是编译入库缺失，也不是 retrieval 未召回；`151` 已经在 source、article chunk 和 retrieval top evidence 中可见。

## 5. Patch 前后对比

| 阶段 / 目录 | 结果 | generation / outcome | sourceCount / articleCount | citationCoverage | 答案覆盖 |
|---|---|---|---:|---:|---|
| RRF revert stability R1/R2/R3 `.codex/run/swip-stability-round*` | PASS / PASS / PASS | `LLM / SUCCESS` | 1 / 1 | 1.0 | 覆盖 `149/150/151` 和完整调整步骤 |
| lead-in fix 完整 eval `.codex/run/swip-structured-leadin-fix-20260516-112955` | PASS | `LLM / SUCCESS` | 1 / 1 | 1.0 | 覆盖 `149/150/151` 和完整调整步骤 |
| outcome guard fix 单轮 `.codex/run/swip-unanswerable-outcome-guard-fix-20260516-121843` | FAIL | `FALLBACK / SUCCESS` | 见历史报告 | 见历史报告 | 缺 `150/151`，属于不同表现 |
| 当前 grounding stability R1/R2/R3 | FAIL / FAIL / FAIL | `LLM / SUCCESS` | 1 / 2 | 0.6667 | 只覆盖 `149/150`，缺整个调整步骤 |

patch 前稳定 PASS 与 lead-in fix 单轮 PASS 都说明该 case 不是题集口径天然过严，也不是资料缺失。当前三轮稳定 FAIL 的答案形态变成“引导句 + 表格主体”，与 `AnswerParagraphPostProcessor.compressStructuredExactLookupAnswer(...)` 新增的 dangling lead-in + structured body 保留分支高度一致。

需要说明的证据边界：当前 audit 只持久化最终 `answer_markdown`，`model_snapshot_json={}`，没有保存 raw LLM structured payload。因此无法直接看到“压缩前 LLM 答案是否已经包含 `151`”。下面的主因判断是基于最终答案形态、代码路径、历史 PASS 对比和 retrieval evidence 的高置信间接归因。

## 6. 回归主因分类

主因分类：**AnswerParagraphPostProcessor lead-in 裁剪导致含 `151` 的后续段落被裁掉**。

依据：

| 证据 | 判断 |
|---|---|
| 当前问题 retrieval `answer_shape=SEQUENCE` | 问题本质需要顺序/步骤型答案，不是只查两个静态值 |
| `looksLikeFlowQuestion(question)` 只覆盖“流程/链路/怎么走”等 flow 信号 | 当前问题虽然被 retrieval 识别为 `SEQUENCE`，但不一定命中 postprocessor 的 flow 早退条件 |
| 当前最终答案为“dangling lead-in + Markdown 表格” | 与当前 `compressStructuredExactLookupAnswer` 中“上一段是 dangling lead-in 且下一段是 structured body，则保留两段后 break”的形态吻合 |
| 后续调整步骤整体缺失 | 不是只少一个数字，而是 `151` 所在的调整段落完全不在最终答案中 |
| retrieval top evidence 已含 `151` | 排除入库缺失与检索未召回 |
| patch 前 / lead-in fix run 曾 PASS | 同一题集和同一资料能产出完整答案，说明 required term 不是不可满足 |

非主因排除：

| 候选原因 | 结论 |
|---|---|
| LLM 生成遗漏 | 不能完全排除，因为 raw LLM payload 未保存；但最终答案形态与 postprocessor 截断分支更吻合，且三轮后两轮可能是缓存复用，不宜把它当作三次独立 LLM 方差 |
| answer projection / citation 绑定遗漏 | citation 缺失是结果：最终 answer 已没有调整步骤，citation 层没有可绑定的 `151` claim |
| 编译入库缺失 | 否，source/article/article chunk 均有 `151` |
| retrieval 未召回 | 否，top fused article 与 source chunk 均含 `151` |
| eval required term 口径不合理 | 否，`151` 是源文与 humanJudgement 共同要求的关键字段 |
| fact card 缺失 | 不是主因；fact card 无 `151` 且未命中，但 article/source 已覆盖 |

## 7. 是否归因到 AnswerParagraphPostProcessor

结论：**可以高置信归因到 `AnswerParagraphPostProcessor` 的 structured/exact lookup 段落压缩边界，但不是 raw payload 级铁证。**

具体说，当前可观察结果不是“证据没有进入系统”，而是“最终答案被压成只含静态对应关系的结构化主体”。该形态正好由本轮新增逻辑保护 lead-in 与表格主体时产生：它修复了“只剩引导句”的目标 bug，但对顺序类、多意图问题可能过早 `break`，导致后续步骤段被丢弃。

## 8. 是否归因到 outcome guard

结论：**不能归因到 outcome guard。**

依据：

- 当前三轮 `answerOutcome` 均为 `SUCCESS`，没有被下调为 `INSUFFICIENT_EVIDENCE`。
- 当前答案没有证据不足、无法确认、不能判断等 insufficient evidence signals。
- `normalizeStructuredAnswerOutcome(...)` 即使执行，也没有改变该 case 的 outcome。
- 失败原因是 `answer_missing_term:151`，不是 `answerOutcome` 变化。

outcome guard 的间接影响未观察到。当前 LLM/SUCCESS 三轮失败与此前 outcome guard 单轮中的 `FALLBACK / SUCCESS` 失败不是同一表现，不能混为一类。

## 9. 下一轮建议

建议下一轮允许修代码，但只做一个最小动作：

**最小修复点：收窄 `AnswerParagraphPostProcessor.compressStructuredExactLookupAnswer(...)` 的适用范围，让通用顺序/步骤型问题不要在 dangling lead-in + structured body 后提前截断后续段落。**

最小允许文件范围：

- `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java`

禁止扩大范围：

- 不改 retrieval / rerank / citation / outcome guard。
- 不改题集 required terms。
- 不新增任何面向 SWIP、IP 后缀、`151`、具体题目或具体答案片段的特判。
- 不一次性处理其他 SWIP 失败。

如果执行者认为 raw payload 级证据仍不足，下一轮也只能围绕该同一最小点补诊断；不要转向 RRF、prompt companion、题集或 outcome guard。

## 10. 本轮修改说明

- 本轮是否修改代码：**否**。
- 本轮是否修改题集/配置/脚本/数据库：**否**。
- 本轮是否清库、重新导入、重建库：**否**。
- 本轮是否运行全量 eval：**否**。已有最新三轮有效输出，未额外扰动环境。
- 本轮文件变更：仅新增本报告；redline 执行按允许范围刷新了 `special_cases_report.md`。
