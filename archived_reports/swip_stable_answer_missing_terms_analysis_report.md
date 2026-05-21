# SWIP 稳定 answer_missing_term 失败归因分析报告

## 0. 本轮边界

- 身份：agentB，只做 SWIP answer grounding 稳定失败归因分析。
- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`docs/test/**`、`scripts/**`、`.claude/**`、`AGENTS.md`、redline allowlist。
- 本轮未清库、未重新导入资料、未重新编译知识库、未运行代码修复。
- 本轮允许改动：新增本报告；`bash scripts/scan-redline.sh special_cases_report.md` 按要求更新了 `special_cases_report.md`。

## 1. Redline 结果

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1830 |
| ALLOWLIST | 219 |

结论：redline 未阻塞本轮只读分析。

## 2. 分析输入与限制

| 输入 | 说明 |
|---|---|
| 最新 SWIP eval 输出 | `.codex/run/swip-stability-round3` |
| 稳定性报告 | `swip_rrf_revert_stability_verification_report.md` |
| 既有归因报告 | `swip_answer_grounding_failure_analysis_report.md`、`swip_eval_expectation_adjustment_report.md` |
| 题集 | `docs/test/swip-query-eval-candidates.json` |
| 只读数据库表 | `source_files`、`source_file_chunks`、`articles`、`article_chunks`、`fact_cards`、`query_retrieval_runs`、`query_retrieval_channel_hits`、`query_answer_audits`、`query_answer_claims`、`query_answer_citations` |
| 源码只读范围 | `src/main/java/com/xbk/lattice/query/**`、`src/main/java/com/xbk/lattice/compiler/**` |

限制说明：

- `query_answer_audits.model_snapshot_json` 当前为空 `{}`，完整最终 LLM prompt 未落库。
- 因此“prompt evidence 是否包含”无法直接从完整 prompt 文本逐字证明；本报告使用以下代理证据判断：retrieval fused topK、final response sources/articles/sourceText、answer claims/citations 的 `matched_excerpt`、以及 `AnswerPromptBuilder` / prompt evidence 组装代码路径。
- 最新 round3 中 `SWIP-USAGE-SAND-SETTLEMENT-001`、`SWIP-FAQ-NO-RESPONSE-001`、`SWIP-INSTALL-CERT-NAMING-001` 的 latest queryId 未在 DB 中找到对应 retrieval/audit 记录。本报告对这 3 个 case 使用历史同题最新审计记录做 retrieval/prompt 代理；actual answer 仍以 round3 eval 输出为准。

## 3. 总体结论

9 个稳定失败不是 SWIP docx 整体抽取缺失，也不是 retrieval top10 缺失：

- 31 个 required term 均存在于 `source_files.content_text` 与 `source_file_chunks.chunk_text`。
- 31 个 required term 均至少进入同题 retrieval top10；绝大多数进入 top5。
- `40mm`、`58mm` 是唯一明确存在“源文/source chunk 有，但 article/article_chunk/fact_card 编译投影缺失”的精确值。
- 主要风险集中在 answer grounding 后段：LLM 过度拒答或漏枚举、fallback deterministic 摘要漏点、citation/paragraph 后处理裁剪，以及编译投影缺精确值后 source evidence 没有被最终采用。

失败主因分布：

| 主因 | Case |
|---|---|
| citation/grounding 后处理裁剪 | `SWIP-FAQ-NO-RESPONSE-001`、`SWIP-INSTALL-CERT-NAMING-001` |
| fallback deterministic 摘要漏点 | `SWIP-USAGE-BANK-REFUND-001` |
| evidence 已召回但 LLM 漏点/过度拒答/答题形态误判 | `SWIP-USAGE-BANK-SETTLEMENT-001`、`SWIP-USAGE-SAND-SIGN-001`、`SWIP-USAGE-SAND-SETTLEMENT-001`、`SWIP-INSTALL-LOGS-001`、`SWIP-INSTALL-CERT-UPDATE-001` |
| 编译投影缺精确值，source evidence 被最终回答链路弱化 | `SWIP-FAQ-PRINT-PAPER-001` |
| retrieval/rerank 未召回 | 0 个 |
| 资料本身无对应表述 | 0 个 |

不建议复活两条已回退尝试：

- 不建议继续 RRF retained content 修复。
- 不建议复活 prompt companion snippet 方案。

## 4. 逐 case 归因表

| Case | Question | Expected requiredAnswerTerms | Actual answer 要点 | 链路结论 | 主因 |
|---|---|---|---|---|---|
| `SWIP-USAGE-BANK-REFUND-001` | 银行卡退款时需要输入哪些原交易信息，原交易日期格式是什么？ | `参考号`、`原交易日期`、`月月日日` | fallback 输出“证据不足/不补写”式摘要，引用了“银行卡交易明细查询日期格式”为“日期6位，年年月月日日”，未给出退款流程中的“参考号”和“原交易日期”。 | 源文、chunk、article chunk、retrieval 均有退款原交易参考号/日期；进入 fallback 前 retrieval 不是问题。 | fallback deterministic 摘要漏点，并混入邻近但不对应的问题事实。 |
| `SWIP-USAGE-BANK-SETTLEMENT-001` | 银行卡结算建议在什么时候执行，执行后会出现什么结果？ | `日结`、`结算成功`、`小票` | 回答称只能确认存在“2.2.3 银行卡-结算”章节，未提供正文内容，因此证据不足。 | 相关术语在 source/chunk/article/retrieval rank1；但最终 answer claims 只支撑“章节存在/证据不足”。 | evidence 已召回，但 LLM 过度拒答，未使用已召回正文事实。 |
| `SWIP-USAGE-SAND-SIGN-001` | 新安装的 SWIP 智能键盘是否必须执行杉德签到，之后签到频率是什么？ | `开店前`、`杉德签到` | 回答称新安装必须执行一次杉德签到，之后不用定期或重复执行。 | `开店前` 位于 source/chunk/article，retrieval top5；最终回答与源文“每天开店前”方向相反。 | evidence 已召回，但 LLM 答题形态误判，把安装首次要求误解为后续无需重复。 |
| `SWIP-USAGE-SAND-SETTLEMENT-001` | 杉德手工结算应在什么时候执行，有交易和无交易时分别会怎样？ | `卡种` | 回答只说明入口与当天未结算交易，称未给固定钟点；漏“每天结束营业前”“按卡种分别打印”“无交易不打印”等关键行为。 | round3 latest audit 缺失；历史同题 audit 显示 `卡种` 与相关段落 top1/top10 召回。 | evidence 已召回，但回答压缩/答题形态偏移，只回答入口和局部对象。 |
| `SWIP-FAQ-NO-RESPONSE-001` | SWIP 系统无响应时应该先检查哪些状态？ | `SNIFF`、`HTTPS服务`、`已启动`、`区域IT伙伴` | round3 回答只剩“SWIP 系统无响应时，先检查以下状态：”并带引用，后续列表全部缺失。 | 源文、chunk、article、retrieval 历史同题 rank1 均包含完整状态项。 | citation 补齐与 exact lookup 段落压缩顺序造成的后处理裁剪：带 citation 的引导句被保留，后续列表被裁掉。 |
| `SWIP-INSTALL-CERT-NAMING-001` | SWIP 键盘入网证书和 HTTPS 服务证书的命名规则分别是什么？ | `swip-门店号-POS机号.starbucks.net`、`swip-https-门店号-POS机号.starbucks.net` | round3 回答只剩“命名规则分别如下：”并带引用，两个模板均缺失。 | 两个模板存在于 source/chunk/article/fact_card；历史同题 retrieval rank1，曾在保留内容实验中可答出。 | citation/paragraph 后处理裁剪，而非资料或 retrieval 缺失。 |
| `SWIP-INSTALL-LOGS-001` | 如何访问键盘上的日志，常见 APP 日志目录分别是什么？ | `9999/log`、`6666`、`XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand` | 回答只给 `9999/log`，并称当前证据没有 APP 日志目录规则。 | 目录表和 `6666` 在 source/chunk/article/retrieval rank1/4/5；部分目录名未进入 fact_card。 | evidence 已召回，但回答被“源材料未说明 APP 日志规则”的编译摘要/claim 污染后过度拒答。 |
| `SWIP-INSTALL-CERT-UPDATE-001` | 证书自动更新会提前多久通知和执行，需要键盘处于什么状态？ | `51/31天`、`50/30天`、`晚上11点`、`开机`、`SWIP网关APP` | 回答只给 24 小时检测/自动续约，称通知时间和状态要求没有证据；提到 `SWIP网关APP` 但未作为状态要求。 | 阈值、时间、开机状态和 APP 状态在 source/chunk/article/retrieval rank1；部分精确值未进入 fact_card。 | evidence 已召回，但 LLM 过度拒答，未把 FAQ 证书更新明细作为直接答案。 |
| `SWIP-FAQ-PRINT-PAPER-001` | SWIP 密码键盘打印纸规格是什么，不同门店由谁提供？ | `40mm`、`58mm`、`杉德`、`工坊` | 回答只确认 FAQ 有打印纸规格小节，称没有具体规格和责任方。 | `40mm/58mm/杉德/工坊` 在 source/source chunk retrieval rank1/2；但 `40mm/58mm` 缺失于 article/article_chunk/fact_card。 | 编译投影遗漏精确规格后，最终回答采用了“有章节但无细节”的 article/FAQ 视角，source evidence 未被有效用于回答。 |

## 5. Required term 链路位置矩阵

标记说明：

- Source：是否存在于 `source_files` / `source_file_chunks`。
- Article/Fact：是否存在于 `articles`、`article_chunks` 或 `fact_cards` 的编译投影。
- Retrieval：同题 retrieval top5/top10 是否命中；无 latest audit 的 case 使用历史同题 audit。
- Prompt evidence：完整 prompt 未落库，本列为代理判断。
- Final answer：round3 actual answer 是否包含或语义覆盖。

| Case | Term | Source | Article/Fact | Retrieval | Prompt evidence 代理判断 | Final answer | 归因备注 |
|---|---|---|---|---|---|---|---|
| `BANK-REFUND` | `参考号` | 是 | 是 | top5/top10 是，first rank 1 | fallback 前 evidence 有；claim/citation 可匹配到相关片段 | 遗漏 | fallback 摘要未抽取退款原交易字段。 |
| `BANK-REFUND` | `原交易日期` | 是 | 是 | top5/top10 是，first rank 1 | fallback 前 evidence 有；claim/citation 可匹配到相关片段 | 遗漏 | fallback 摘要转向邻近日期格式事实。 |
| `BANK-REFUND` | `月月日日` | 是 | 是 | top5/top10 是，first rank 1 | fallback 前 evidence 有 | 字面可能出现，但搭配错误 | 答案倾向引用“日期6位/年年月月日日”，与题目要求不稳。 |
| `BANK-SETTLEMENT` | `日结` | 是 | 是 | top5/top10 是，first rank 1 | 大概率有：rank1 article/source 含正文 | 遗漏 | LLM 输出章节存在式拒答。 |
| `BANK-SETTLEMENT` | `结算成功` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 大概率有：rank1 article/source 含正文 | 遗漏 | LLM 未使用结算结果段落。 |
| `BANK-SETTLEMENT` | `小票` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 大概率有：rank1 article/source 含正文 | 遗漏 | LLM 未使用打印结果段落。 |
| `SAND-SIGN` | `开店前` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 4 | 是：top5 article/source 已含频率句 | 遗漏且语义相反 | LLM 把“首次必须”误压成“之后不用”。 |
| `SAND-SIGN` | `杉德签到` | 是 | 是 | top5/top10 是，first rank 1 | 是 | 包含 | 但后续频率错误，整体 answer grounding 不合格。 |
| `SAND-SETTLEMENT` | `卡种` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，按历史同题代理 | 遗漏 | 回答只覆盖入口/对象，未覆盖按卡种打印。 |
| `FAQ-NO-RESPONSE` | `SNIFF` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，历史同题 rank1 evidence | 遗漏 | 最新答案只剩引导句，疑似后处理裁剪列表。 |
| `FAQ-NO-RESPONSE` | `HTTPS服务` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，历史同题 rank1 evidence | 遗漏 | 同上。 |
| `FAQ-NO-RESPONSE` | `已启动` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，历史同题 rank1 evidence | 遗漏 | 同上。 |
| `FAQ-NO-RESPONSE` | `区域IT伙伴` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，历史同题 rank1 evidence | 遗漏 | 同上。 |
| `CERT-NAMING` | `swip-门店号-POS机号.starbucks.net` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，历史同题 rank1 evidence | 遗漏 | 最新答案只剩引导句，疑似后处理裁剪列表。 |
| `CERT-NAMING` | `swip-https-门店号-POS机号.starbucks.net` | 是 | 是 | top5/top10 是，历史 first rank 1 | 是，历史同题 rank1 evidence | 遗漏 | 同上。 |
| `INSTALL-LOGS` | `9999/log` | 是 | 是 | top5/top10 是，first rank 1 | 是 | 包含 | 访问入口回答正确但不完整。 |
| `INSTALL-LOGS` | `6666` | 是 | 是 | top5/top10 是，first rank 1 | 是，rank1 article/source 含日志目录访问 | 遗漏 | LLM 选择“无 APP 日志规则”结论。 |
| `INSTALL-LOGS` | `XBKSW` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是，source/article 表格 evidence 可见 | 遗漏 | 目录表未进入最终回答。 |
| `INSTALL-LOGS` | `ebxbk` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是 | 遗漏 | 同上。 |
| `INSTALL-LOGS` | `XBKYH` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是 | 遗漏 | 同上。 |
| `INSTALL-LOGS` | `XBKXT` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是 | 遗漏 | 同上。 |
| `INSTALL-LOGS` | `sand` | 是 | 是 | top5/top10 是，first rank 1 | 是 | 遗漏 | 目录名存在；解释含义可能受源表截断影响。 |
| `CERT-UPDATE` | `51/31天` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是，rank1 article/source 含更新明细 | 遗漏 | LLM 过度拒答通知时间。 |
| `CERT-UPDATE` | `50/30天` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是 | 遗漏 | 同上。 |
| `CERT-UPDATE` | `晚上11点` | 是 | article/chunk 是，fact_card 否 | top5/top10 是，first rank 1 | 是 | 遗漏 | 同上。 |
| `CERT-UPDATE` | `开机` | 是 | 是 | top5/top10 是，first rank 1 | 是 | 遗漏 | 状态要求未被采纳。 |
| `CERT-UPDATE` | `SWIP网关APP` | 是 | 是 | top5/top10 是，first rank 1 | 是 | 字面出现，语义未命中 | 回答称它不是证书更新状态要求，未作为 required condition。 |
| `PRINT-PAPER` | `40mm` | 是 | 否 | top5/top10 是，first rank 1 | source evidence 有；article/fact 投影无 | 遗漏 | 编译投影缺精确规格，source evidence 未被最终采用。 |
| `PRINT-PAPER` | `58mm` | 是 | 否 | top5/top10 是，first rank 1 | source evidence 有；article/fact 投影无 | 遗漏 | 同上。 |
| `PRINT-PAPER` | `杉德` | 是 | 是 | top5/top10 是，first rank 1 | source evidence 有 | 遗漏 | 与提供方职责绑定的事实未输出。 |
| `PRINT-PAPER` | `工坊` | 是 | 是 | top5/top10 是，first rank 1 | source evidence 有 | 遗漏 | 与提供方职责绑定的事实未输出。 |

## 6. 为什么进入 prompt 后仍遗漏

### 6.1 后处理裁剪：`FAQ-NO-RESPONSE`、`CERT-NAMING`

只读代码显示，结构化 answer 的处理顺序存在确定性风险：

1. LLM JSON answer 先被 `normalizeStructuredAnswerMarkdown()` 处理。
2. `AnswerCitationPostProcessor.attachDefaultCitationWhenMissing()` 会给非标题、非表格分隔行补默认 citation。
3. 之后 `compressStructuredExactLookupAnswer()` 对精确查值类答案做段落压缩。
4. 压缩逻辑会保留第一段；如果第二段是短直接答案段也保留；遇到 list/table 等结构后停止。
5. “先检查以下状态：”或“命名规则分别如下：”这类引导句被补 citation 后，不再被 dangling lead-in 清理，后续列表则可能被裁掉。

这解释了两个 latest actual answer 的共同形态：只剩带 citation 的引导句，所有 required term 都消失。该问题不是 retrieval，也不是资料缺失。

### 6.2 fallback deterministic 摘要漏点：`BANK-REFUND`

该 case 进入 fallback，status 为 `DEGRADED`，reason 为 `CITATION_QUALITY_INSUFFICIENT`。fallback 摘要未围绕问题抽取退款流程中的原交易参考号/日期，而是混入邻近的“银行卡交易明细查询日期格式”事实。源文和 retrieval 都能找到退款字段，因此根因在 fallback deterministic 摘要选择和压缩。

### 6.3 LLM 过度拒答/答题形态误判

以下 case 都有 source/chunk/retrieval 命中，但 final answer 仍输出“证据不足”或局部答案：

- `BANK-SETTLEMENT`：正文有“每日日结后”“结算成功”“结算小票”，回答却只承认章节存在。
- `SAND-SIGN`：正文有“每天开店前”，回答却推断“之后不用定期或重复执行”。
- `SAND-SETTLEMENT`：正文有按卡种打印与无交易不打印，回答只覆盖入口/对象。
- `INSTALL-LOGS`：目录表与 `6666` 召回，回答却采纳“源材料未说明 APP 日志规则”的结论。
- `CERT-UPDATE`：更新阈值、晚上 11 点、开机和 APP 状态召回，回答仍说没有通知时间和状态要求证据。

共同问题不是 topK recall，而是 evidence 进入 answer 阶段后没有被稳定地转成枚举完整答案。部分 case 还受到编译摘要中“源材料未说明/截断”类 claim 的干扰。

### 6.4 编译投影缺精确值：`PRINT-PAPER`

`PRINT-PAPER` 的源文/source chunk 明确包含 `40mm`、`58mm`、`杉德`、`工坊`，且 retrieval rank1/2 命中；但 `40mm`、`58mm` 未进入 `articles`、`article_chunks`、`fact_cards`。final answer 更信任“有 FAQ 小节但无具体细节”的 article/FAQ 视角，未采用 source evidence。因此这是 source evidence 与 article/fact 投影不一致后的 grounding 失败。

## 7. 是否属于题集 expected term 过严

不建议通过放宽 requiredAnswerTerms 直接追 pass。多数 required term 是核心事实，适合机器硬断言。

| Case | 适合机器硬断言 | 仅适合人工/语义验收补充 | 判断 |
|---|---|---|---|
| `BANK-REFUND` | `参考号`、`原交易日期`、`月月日日` | 无 | 都是问题直接要求的字段或格式。 |
| `BANK-SETTLEMENT` | `日结`、`结算成功`、`小票` | 可补充“建议执行时机”和“执行后结果”语义关系 | 当前 terms 合理。 |
| `SAND-SIGN` | `开店前`、`杉德签到` | `必须` 可作为语义补充，不宜单独判定 | 当前 terms 合理。 |
| `SAND-SETTLEMENT` | `卡种` 可机器断言 | 当前仅检查 `卡种` 偏弱，不能保证“每天结束营业前/无交易不打印”完整覆盖 | 不是过严，反而偏宽。 |
| `FAQ-NO-RESPONSE` | `SNIFF`、`HTTPS服务`、`已启动`、`区域IT伙伴` | 状态关系可人工验收 | 当前 terms 合理。 |
| `CERT-NAMING` | 两个证书命名模板 | `只允许申请一次` 可作为机器或人工补充 | 当前 terms 合理。 |
| `INSTALL-LOGS` | `9999/log`、`6666`、`XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand` | 目录含义解释可人工验收，尤其 `sand` 的完整解释受源表截断影响 | term 本身适合机器断言。 |
| `CERT-UPDATE` | `51/31天`、`50/30天`、`晚上11点`、`开机`、`SWIP网关APP` | `SWIP网关APP` 需要绑定“正常运行状态”，否则字面出现可能误判 | 不建议放宽；可后续增强语义验收。 |
| `PRINT-PAPER` | `40mm`、`58mm`、`杉德`、`工坊` | `杉德`、`工坊` 最好绑定“提供方/准备方”关系 | 当前 terms 合理，但关系语义可增强。 |

结论：

- 本轮没有发现“必须通过放宽 requiredAnswerTerms 才合理”的 case。
- 有 3 类评测表达可后续增强，但不是本轮修复点：`SAND-SETTLEMENT` 当前断言偏弱；`CERT-UPDATE` 的 `SWIP网关APP` 需要语义绑定；`PRINT-PAPER` 的提供方关系可补充人工验收字段。

## 8. 通用能力问题与评测断言问题

| 类型 | Case | 说明 |
|---|---|---|
| 通用能力问题：后处理不应裁掉枚举答案 | `FAQ-NO-RESPONSE`、`CERT-NAMING` | 与具体业务词无关，任何精确查值/枚举型答案都可能只剩引导句。 |
| 通用能力问题：fallback 摘要应围绕 question 抽取直接字段 | `BANK-REFUND` | 与具体业务词无关，fallback 不能混入邻近但不回答问题的事实。 |
| 通用能力问题：已召回 evidence 的完整枚举与过度拒答控制 | `BANK-SETTLEMENT`、`SAND-SIGN`、`SAND-SETTLEMENT`、`INSTALL-LOGS`、`CERT-UPDATE` | 不是 retrieval 缺失，而是 answer 阶段没有稳定使用 direct evidence。 |
| 通用能力问题：source evidence 与 article/fact 投影不一致时的精确值保护 | `PRINT-PAPER` | 源文命中但 article/fact 缺精确值，最终回答未采用 source。 |
| 评测断言问题 | 无需立即修改 | 当前 required terms 大多合理；个别 case 可后续增强语义关系检查，但不建议放宽。 |

## 9. 下一轮唯一最小修复建议

建议下一轮修代码：是，但只建议一个最小修复点。

唯一最小修复点：

- 修复 exact lookup / structured answer 的段落压缩防护，避免 citation 已补齐的 dangling lead-in 被当作有效短答案保留，同时裁掉后续 list/table 枚举内容。

最小允许文件范围：

- 首选仅限：`src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java`
- 若实现证明必须调整调用顺序，才允许最小扩展到：`src/main/java/com/xbk/lattice/query/service/AnswerPayloadParser.java`

不建议下一轮触碰：

- 不改 retrieval/RRF retained content。
- 不改 prompt companion snippet。
- 不改题集 requiredAnswerTerms。
- 不改 compiler 投影。
- 不引入任何 SWIP、银行卡、杉德、证书、SNIFF、门店号、POS机号、具体文件名、具体答案片段特判。

选择该修复点的原因：

- 它是 9 个稳定失败中最确定的代码级通用缺陷。
- 它可解释 2 个 latest round3 失败的异常形态：答案只剩引导句。
- 它不依赖 LLM 方差，不扩大到 retrieval/prompt/compiler 多变量。
- 它不能一次性修完 9 个 case，符合下一轮只处理一个最小根因的约束。

## 10. 本轮改动声明

- 本轮是否修改代码：否。
- 本轮是否修改配置/题集/脚本/模型文档：否。
- 本轮是否修改数据库：否。
- 本轮新增/更新报告：是，`swip_stable_answer_missing_terms_analysis_report.md`。
- 本轮 redline 扫描更新：是，`special_cases_report.md`。
