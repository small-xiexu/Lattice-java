# SWIP Answer Grounding Failure Analysis Report

## 1. 本轮边界

- 只分析 adjusted strict eval 剩余 10 个失败 case。
- 未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、`docs/test/swip-query-eval-candidates.json`、SWIP 源文档、runner、redline allowlist。
- 本轮是否修改代码：否。
- 本轮是否修改题集：否。
- 本轮是否重新导入 SWIP 文档：否。
- 本轮是否重新 compile：否。
- 本轮是否切换模型：否。
- 本轮只新增本报告；运行 redline 时允许更新 `special_cases_report.md`。

## 2. 工作区与 Redline

工作区检查：

| 项目 | 结果 |
|---|---|
| branch | `codex/qa-polish...origin/codex/qa-polish` |
| 既有变更 | 工作区已有删除报告、`docs/test/swip-query-eval-candidates.json` 修改、`special_cases_report.md` 修改及若干未跟踪报告 |
| 本轮处理 | 既有变更只读观察，未回滚、未扩大修改范围 |

Redline 执行：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

| 类型 | 数量 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1830 |
| ALLOWLIST | 219 |

结论：`BLOCKER=0`，允许继续做本轮 answer grounding 分析。

## 3. 分析口径

本轮读取：

- `swip_eval_expectation_adjustment_report.md`
- `.codex/run/swip-expect-adjusted-eval-20260515-234728/query_results.jsonl`
- `.codex/run/swip-expect-adjusted-eval-20260515-234728/query_summary.tsv`
- `.codex/run/swip-expect-adjusted-eval-20260515-234728/query_metrics.json`
- `docs/test/swip-query-eval-candidates.json`
- 数据库 `lattice.source_files`、`source_file_chunks`、`articles`、`article_chunks`、`fact_cards`、`query_retrieval_runs`、`query_retrieval_channel_hits`、`query_answer_audits`、`query_answer_claims`、`query_answer_citations`
- `src/main/java/com/xbk/lattice/query/**` 只读，用于确认 prompt 与 fallback 的证据拼接方式

重要限制：

- 完整 LLM prompt 未落库，`query_answer_audits.model_snapshot_json={}`。
- 因此“final context 是否包含”采用可查代理判断：
  - runner 返回的 `response.sources/articles/sourceText`
  - `query_answer_claims` 与 `query_answer_citations.matched_excerpt`
  - `query_retrieval_channel_hits` 中 fused topK 的 `QueryArticleHit.content`
- 只读代码确认：`AnswerPromptBuilder` 会先追加 `QUESTION-FOCUSED EVIDENCE`，再按 evidence type 追加证据；每类最多 6 条，每条 content 限 1200 字，focus snippet 通常 1 到 2 条。这意味着“retrieval topK 召回文档或 chunk”不等于“完整关键事实一定进入最终 prompt”。

## 4. 总体结论

10 个失败 case 的共同特征：

- 所有缺失的 `requiredAnswerTerms` 都存在于 `source_files.content_text`，没有发现 docx 原文抽取完全缺失。
- 大多数缺失词也存在于 `articles` / `article_chunks` 或 fused topK 可见 evidence 中。
- 失败主因不是文档未召回，而是“召回后最终答案未覆盖关键事实”：
  - LLM 对已召回事实过度拒答或只答第一项。
  - fallback 在 citation quality degraded 时选择了不贴题摘要，导致关键短事实未进入最终答案。
  - 个别 case 的编译投影没有保留源文中的精确数值，最终答案引用 article 时产生“证据不足”判断。

## 5. 逐 Case 链路表

### SWIP-USAGE-SVC-READ-001

| 字段 | 结论 |
|---|---|
| question | SVC 卡支付、查询和退款在 SWIP 中由谁执行，SWIP 的读卡功能返回什么？ |
| requiredAnswerTerms | `UPP`、`读卡`、`卡号` |
| failed missing terms | `UPP`、`卡号` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。原文包含“支付、查询和退款都是经过 UPP 执行的”“返回卡号等信息”。 |
| article/chunk 是否存在 | 是。`Swip智能键盘系统使用手册 20250702#0` 含 `UPP`、`读卡`、`卡号`。 |
| retrieval topK 是否召回 | 是。fused topK rank1 article 命中含 `UPP`、`卡号`。 |
| final context 是否包含 | prompt 原文不可查；可查 `response.sourceText` 只有文章身份，citation matched excerpt 未覆盖 `UPP`、`卡号`。 |
| final answer 是否漏掉 | 是。答案只说“在读卡窗口操作刷卡”，未说明由 `UPP` 执行、读卡返回 `卡号`。 |
| citation 是否覆盖 | 否。citation 覆盖的是泛化系统目标和读卡窗口操作，不覆盖缺失词。 |
| 归因分类 | 证据已召回，且高概率进入候选 prompt，但 LLM 漏点并输出不完整 partial answer。 |

### SWIP-USAGE-BANK-REFUND-001

| 字段 | 结论 |
|---|---|
| question | 银行卡退款时需要输入哪些原交易信息，原交易日期格式是什么？ |
| requiredAnswerTerms | `参考号`、`原交易日期`、`月月日日` |
| failed missing terms | `参考号`、`原交易日期` |
| modelExecutionStatus / generationMode / answerOutcome | `DEGRADED` / `FALLBACK` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。原文包含“输入原交易参考号”“输入参考号后，提示输入原交易日期”“4位数字月月日日格式”。 |
| article/chunk 是否存在 | 是。`Swip智能键盘系统使用手册 20250702#1` 含相关事实。 |
| retrieval topK 是否召回 | 是。fused topK rank1 article 命中含 `参考号`、`原交易日期`。 |
| final context 是否包含 | citation matched excerpt 中出现“输入参考号后，系统提示输入原交易日期”，但 fallback 正文没有把该事实写成答案。 |
| final answer 是否漏掉 | 是。fallback 只写了“银行卡交易明细查询日期格式 = 日期6位，格式为年年月月日日”，没有回答退款需输入原交易参考号和原交易日期。 |
| citation 是否覆盖 | 部分。matched excerpt 覆盖了缺失词，但最终 answer statement 未覆盖。 |
| 归因分类 | fallback 摘要过短/择句错误，证据已召回但 fallback 结论选择了不贴题片段。 |

### SWIP-USAGE-BANK-SETTLEMENT-001

| 字段 | 结论 |
|---|---|
| question | 银行卡结算建议在什么时候执行，执行后会出现什么结果？ |
| requiredAnswerTerms | `日结`、`结算成功`、`小票` |
| failed missing terms | `日结`、`结算成功`、`小票` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `INSUFFICIENT_EVIDENCE` |
| source_files 是否存在 | 是。原文包含“每日日结后，建议进行银行卡结算操作”“提示结算成功”“打印结算小票”。 |
| article/chunk 是否存在 | 是。`Swip智能键盘系统使用手册 20250702#1` 含全部关键事实。 |
| retrieval topK 是否召回 | 是。fused topK rank1 article、rank2 source chunk 均命中相关事实。 |
| final context 是否包含 | prompt 原文不可查；可查 final citation excerpt 未覆盖关键事实，answer 反而声明“提供的证据片段没有包含”。 |
| final answer 是否漏掉 | 是。直接过度拒答。 |
| citation 是否覆盖 | 否。citation 只支撑“章节存在/证据不足”类说法，不支撑真实结算事实。 |
| 归因分类 | 证据已召回但 LLM 过度拒答，属于 evidence grounding 后的回答漏点。 |

### SWIP-USAGE-SAND-SIGN-001

| 字段 | 结论 |
|---|---|
| question | 新安装的 SWIP 智能键盘是否必须执行杉德签到，之后签到频率是什么？ |
| requiredAnswerTerms | `开店前`、`杉德签到` |
| failed missing terms | `开店前` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。原文包含“对于新安装的 SWIP 智能键盘，必须执行杉德签到”“每天开店前，执行一次杉德签到”。 |
| article/chunk 是否存在 | 是。`Swip智能键盘系统使用手册 20250702#2` 含 `开店前`。 |
| retrieval topK 是否召回 | 是。fused topK rank4 article、rank6 source chunk 命中 `开店前`。 |
| final context 是否包含 | citation matched excerpt 中出现“每天开店前”，但该 citation 被 demoted。 |
| final answer 是否漏掉 | 是，并且输出了相反结论：“之后不需要再执行”。 |
| citation 是否覆盖 | 否。包含关键事实的 citation 被 demoted，最终答案未覆盖 `开店前`。 |
| 归因分类 | 证据进入可查 citation excerpt，但 LLM 漏点并产生与证据冲突的结论。 |

### SWIP-FAQ-NO-RESPONSE-001

| 字段 | 结论 |
|---|---|
| question | SWIP 系统无响应时应该先检查哪些状态？ |
| requiredAnswerTerms | `SNIFF`、`HTTPS服务`、`已启动`、`区域IT伙伴` |
| failed missing terms | `HTTPS服务`、`已启动` |
| modelExecutionStatus / generationMode / answerOutcome | `DEGRADED` / `FALLBACK` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。原文包含“网络连接应显示 SNIFF”“HTTPS服务：已启动”“联系区域IT伙伴”。 |
| article/chunk 是否存在 | 是。使用手册 article chunk 与 fact_card 均含相关事实。 |
| retrieval topK 是否召回 | 是。fused topK rank1 使用手册 article、rank3 source、rank5 fact_card 含 `HTTPS服务`、`已启动`。 |
| final context 是否包含 | final `response.sources` 只剩安装手册 fact card；fallback 正文主要输出设备初始化证据，未保留使用手册 FAQ 状态检查细节。 |
| final answer 是否漏掉 | 是。只回答了 SNIFF 和区域 IT 相关背景，漏掉应检查 HTTPS 服务已启动。 |
| citation 是否覆盖 | 否。最终 citation 未覆盖缺失词。 |
| 归因分类 | 证据已召回但未进入最终 fallback 摘要，属于 fallback 择证/摘要过短。 |

### SWIP-INSTALL-APP-LIST-001

| 字段 | 结论 |
|---|---|
| question | SWIP 系统一共有几个 APP，分别是什么？ |
| requiredAnswerTerms | `9`、`SWIP APP Store`、`SWIP网关`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡` |
| failed missing terms | `SWIP APP Store`、`SWIP网关`、`资和信`、`易百`、`杉德`、`得仕卡`、`苏州市民卡` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。安装手册原文明确“共有9个APP”，并逐项列出 9 个 APP。 |
| article/chunk 是否存在 | 是。FAQ、系统架构、source chunk 中均可检索到相关 APP 名称。 |
| retrieval topK 是否召回 | 是。fused topK rank1 source、rank2 FAQ article 等含关键词。 |
| final context 是否包含 | prompt 原文不可查；final answer 只引用 FAQ 33，citation excerpt 偏向“其他7个APP”描述，没有覆盖完整列表。 |
| final answer 是否漏掉 | 是。答案声称“不能完整确认全部 APP 名称”。 |
| citation 是否覆盖 | 否。最终 citation 未覆盖完整 APP 列表。 |
| 归因分类 | 证据已召回，但 question-focused/final answer 未保留相邻完整枚举列表，LLM 过度拒答。 |

### SWIP-INSTALL-APP-UPGRADE-IMPACT-001

| 字段 | 结论 |
|---|---|
| question | SWIP APP Store、SWIP 网关 APP、资和信 APP、杉德 APP、易百 APP 升级或卸载重装对签到和日结有什么影响？ |
| requiredAnswerTerms | `SWIP APP Store`、`SWIP网关APP`、`资和信`、`杉德`、`易百` |
| failed missing terms | `SWIP网关APP`、`资和信`、`杉德`、`易百` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。安装手册 FAQ 中逐项说明 APP Store、网关、资和信、杉德、易百影响。 |
| article/chunk 是否存在 | 是。`FAQ 33` article chunks 含全部相关 APP 名称与影响说明。 |
| retrieval topK 是否召回 | 是。fused topK rank1 FAQ 33、rank2 fact_card、rank3-5 source 均含相关实体。 |
| final context 是否包含 | final source 是 FAQ 33，但最终答案只采纳 APP Store 两句，没有逐项覆盖其他被问 APP。 |
| final answer 是否漏掉 | 是。只回答 `SWIP APP Store`。 |
| citation 是否覆盖 | 否。最终 citation 只覆盖 APP Store 结论。 |
| 归因分类 | 证据进入最终候选，但 LLM 对多实体问题只答第一项，属于 LLM 漏点。 |

### SWIP-INSTALL-LOGS-001

| 字段 | 结论 |
|---|---|
| question | 如何访问键盘上的日志，常见 APP 日志目录分别是什么？ |
| requiredAnswerTerms | `9999/log`、`6666`、`XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand` |
| failed missing terms | `6666`、`XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。安装手册原文包含 `9999/log`、`6666` 访问方式及目录 `XBKSW`、`ebxbk`、`XBKYH`、`XBKXT`、`sand`。 |
| article/chunk 是否存在 | 是。`FAQ 33` article chunks 含日志访问和目录事实。 |
| retrieval topK 是否召回 | 是。fused topK rank1 FAQ 33、rank4 source 均含目录项；rank7/8 fact_card 含 `6666`。 |
| final context 是否包含 | final sources 包含 HTTPS article 与 FAQ 33，但答案采纳了 `9999/log`，同时错误声称没有 APP 日志目录规则。 |
| final answer 是否漏掉 | 是。漏掉 `6666` 和全部目录项。 |
| citation 是否覆盖 | 否。最终 citation 未覆盖目录项。 |
| 归因分类 | 证据已召回并在 final source 候选中可见，但 LLM 选择了编译摘要中的“未说明 APP 日志规则”式缺口结论，漏掉源文精确值。 |

### SWIP-INSTALL-CERT-UPDATE-001

| 字段 | 结论 |
|---|---|
| question | SWIP 系统自动证书更新会在什么时候通知和执行，键盘需要满足什么状态？ |
| requiredAnswerTerms | `51/31天`、`50/30天`、`晚上11点`、`开机`、`SWIP网关APP` |
| failed missing terms | `51/31天`、`50/30天`、`晚上11点`、`开机`、`SWIP网关APP` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `PARTIAL_ANSWER` |
| source_files 是否存在 | 是。安装手册原文包含提前 `51/31天`、`50/30天`、`晚上11点` 通知/执行，并要求键盘晚上 `开机` 且 `SWIP网关APP` 正常运行。 |
| article/chunk 是否存在 | 是。`FAQ 33#0/#4` 含这些事实；`51/31天`、`50/30天`、`晚上11点` 未进入 fact_card。 |
| retrieval topK 是否召回 | 是。fused topK rank1 FAQ 33、rank6 source 含全部关键词。 |
| final context 是否包含 | final source 是 FAQ 33，但 answer 声明“具体内容未在证据中展开”；citation 对关键结论有 demoted。 |
| final answer 是否漏掉 | 是。只回答 24 小时检测与自动更新机制，漏掉具体通知/执行时间和状态要求。 |
| citation 是否覆盖 | 否。最终有效 citation 未覆盖缺失词。 |
| 归因分类 | 证据已召回但 LLM 过度拒答，且结构化 fact_card 未保留具体阈值，降低了精确事实优先级。 |

### SWIP-FAQ-PRINT-PAPER-001

| 字段 | 结论 |
|---|---|
| question | SWIP 密码键盘打印纸规格是什么，不同门店由谁准备热敏纸？ |
| requiredAnswerTerms | `40mm`、`58mm`、`杉德`、`工坊` |
| failed missing terms | `40mm`、`58mm`、`杉德`、`工坊` |
| modelExecutionStatus / generationMode / answerOutcome | `SUCCESS` / `LLM` / `INSUFFICIENT_EVIDENCE` |
| source_files 是否存在 | 是。使用手册和安装手册原文均包含“直径40mm * 长度58mm”，并说明江浙沪非工坊门店由杉德提供、烘焙工坊店由工坊自行准备。 |
| article/chunk 是否存在 | 部分。`杉德`、`工坊` 存在；`40mm`、`58mm` 未进入 `articles` / `article_chunks` / `fact_cards`。 |
| retrieval topK 是否召回 | 是。fused topK rank1/rank2 source evidence 含 `40mm`、`58mm`、`杉德`、`工坊`。 |
| final context 是否包含 | final `response.sources/articles` 只保留使用手册 article，未保留含规格的 source evidence；citation excerpt 未覆盖规格或责任方。 |
| final answer 是否漏掉 | 是。答案声明没有具体规格和责任规则。 |
| citation 是否覆盖 | 否。 |
| 归因分类 | docx 提取未缺失；article/fact_card 编译投影缺失精确规格，且最终答案未采用已召回 source evidence。 |

## 6. 统计

按 case 统计：

| 类别 | case 数 | Case |
|---|---:|---|
| 证据未召回 | 0 | 无。10 个 case 的缺失词均可在 fused topK 或源文件 evidence 中定位。 |
| 证据已召回但答案漏点 | 10 | 全部 10 个失败 case。 |
| 证据已召回但未进入最终可查 source/citation context | 3 | `SWIP-FAQ-NO-RESPONSE-001`、`SWIP-INSTALL-APP-LIST-001`、`SWIP-FAQ-PRINT-PAPER-001` |
| 证据进入最终候选但 LLM 漏点/过度拒答 | 6 | `SWIP-USAGE-SVC-READ-001`、`SWIP-USAGE-BANK-SETTLEMENT-001`、`SWIP-USAGE-SAND-SIGN-001`、`SWIP-INSTALL-APP-UPGRADE-IMPACT-001`、`SWIP-INSTALL-LOGS-001`、`SWIP-INSTALL-CERT-UPDATE-001` |
| fallback 摘要过短或择句错误 | 2 | `SWIP-USAGE-BANK-REFUND-001`、`SWIP-FAQ-NO-RESPONSE-001` |
| chunk 切分导致关键事实分散 | 0 | 未发现主要由 chunk 边界切散导致的失败。 |
| docx 提取缺失 | 0 | 所有缺失词均在 `source_files.content_text` 中存在。 |
| article/fact_card 编译投影缺失精确值 | 1 | `SWIP-FAQ-PRINT-PAPER-001` 的 `40mm`、`58mm` 未进入 article/chunk/fact_card。 |
| answer post-processing 丢内容 | 0 | 未发现明确后处理删除 required terms 的证据。 |
| 题集机器断言仍过严 | 0 | required terms 均有源文支撑，且属于问题核心短事实。 |

按 generation mode 统计：

| generationMode | 失败 case 数 | 主要问题 |
|---|---:|---|
| LLM | 8 | 已召回事实未被答案覆盖，或对多实体/精确查值过度拒答 |
| FALLBACK | 2 | fallback 结论择句不贴题，摘要过短或选择了错误证据 |

## 7. 是否建议下一轮修代码

建议下一轮修代码，但只推荐一个最小修复对象。

推荐最小修复点：

> 调整 `AnswerGenerationPromptEvidenceSupport` 中通用的 question-focused evidence 选择逻辑，让精确查值、枚举、多实体问题在构建 prompt 时优先保留“含问题高信号词且含数字/枚举项/冒号键值/相邻列表行”的源文片段，而不是只保留单句或概述句。

推荐原因：

- 10 个失败中没有 docx 抽取缺失，且大多数 topK 已召回。
- 多数失败发生在“已召回事实未进入最终答案或被概述句压过”。
- 该修复点是通用 evidence grounding 能力，不依赖 SWIP case、业务词、APP 名称、日志目录、证书、打印纸等特判。
- 它同时覆盖：
  - APP 列表这种相邻枚举行丢失。
  - 证书更新时间这种精确阈值丢失。
  - 日志目录这种键值/目录项丢失。
  - 退款、结算这种短事实被不贴题摘要压过。

不建议下一轮优先修改：

- 不建议改题集 expect：本轮 required terms 均在源文中可证。
- 不建议写 SWIP case 特判：会违反 Query 红线。
- 不建议优先改 docx extractor：源文层没有缺失。
- 不建议优先改 runner：runner 已正确暴露 missing requiredAnswerTerms。

## 8. 红线声明

- 下一轮若修复，必须禁止 SWIP case 特判。
- 禁止硬编码 SWIP、APP 名称、日志目录、证书、打印纸、具体文档名、具体问题问法、具体答案片段。
- 只能做通用 evidence selection / prompt grounding 能力增强。
- 每次修复后需重新运行 redline、基础测试与 SWIP query regression。
