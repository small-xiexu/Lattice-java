# SWIP-NEG-UNANSWERABLE-001 新增回归分析报告

## 1. 结论

`SWIP-NEG-UNANSWERABLE-001` 的新增失败不应归因于 agentA 本轮 `AnswerParagraphPostProcessor.java` 的段落压缩改动。

归类：**outcome normalization 问题**。

触发表现是 LLM 在修复后 run 中输出了语义正确的拒答正文，但将 `answerOutcome` 标成 `SUCCESS`；当前 normalization 只会把部分 `PARTIAL_ANSWER` 上调为 `SUCCESS`，不会把带明显拒答/证据不足语义的 `SUCCESS` 下调。因此 eval 只因 `answerOutcome_unexpected_SUCCESS` 失败。

建议：

- 不建议回退本轮 lead-in 修复。
- 建议保留本轮改动。
- 如下一轮修，只给一个最小修复点：在 structured answer outcome normalization 中增加通用 negative/insufficient evidence 下调 guard，防止“拒答正文 + SUCCESS outcome”进入可缓存成功答案。

## 2. 本轮边界与 redline

| 项 | 结果 |
|---|---:|
| redline BLOCKER | 0 |
| redline REVIEW | 1831 |
| redline ALLOWLIST | 218 |

本轮执行：

- 已运行 `git status --short --branch`。
- 已运行 `bash scripts/scan-redline.sh special_cases_report.md`。
- 已读取必需文件：`AGENTS.md`、`docs/quality-progress-and-lessons.md`、`docs/multi-agent-model-routing-guide.md`、`swip_structured_exact_lookup_leadin_fix_result_report.md`。
- 已读取修复前稳定 run、修复后 run、题集、`AnswerParagraphPostProcessor.java`、query answer/retrieval audit。

本轮未执行：

- 未修改生产代码、测试、配置、题集、脚本、redline allowlist。
- 未清库、未导入资料、未编译知识库。
- 未复跑单 case 3 次：`scripts/run-query-regression.sh` 仅转发到 `scripts/run-query-regression.mjs`，mjs 固定遍历 suite 全量 `cases`，未发现 case id 过滤参数或环境变量；按约束未改脚本、未临时造 suite。

## 3. Case 期望

| 项 | 内容 |
|---|---|
| Case | `SWIP-NEG-UNANSWERABLE-001` |
| Question | `SWIP 系统是否支持支付宝和微信扫码支付的完整交易流程？` |
| Type | `NO_ANSWER` |
| Answerability | `UNANSWERABLE` |
| Expected outcome | `INSUFFICIENT_EVIDENCE`、`NO_RELEVANT_KNOWLEDGE`、`PARTIAL_ANSWER` 任一 |
| Required answer terms | 顶层有 `未提供`、`支付宝`、`微信`；但 `expect.requiredAnswerTerms` 未配置，runner 本轮不按它断言 |
| Required source terms | `支付宝微信签到` |
| Forbidden terms | `文档详细说明了支付宝扫码支付流程`、`文档详细说明了微信扫码支付流程`、`可以直接按银行卡消费流程处理支付宝和微信` |
| Human pass rule | 应正确拒答或限定回答范围，不能编造扫码支付步骤 |

修复后失败原因只有一个：`answerOutcome_unexpected_SUCCESS`。未命中 forbidden terms。

## 4. 修复前后对比

| Run | QueryId | Pass | Failed reason | AnswerOutcome | GenerationMode | Citation precision | Unsupported claim rate | Actual answer 摘要 |
|---|---|---:|---|---|---|---:|---:|---|
| `swip-stability-round1` | `85cdcdb9-fb48-4daf-8909-cbf7e0b61550` | PASS | 无 | `INSUFFICIENT_EVIDENCE` | `LLM` | 0.7143 | 0.2857 | 不能确认支持完整交易流程；只说明微信/支付宝入口用于密钥/签到初始化；需要补充操作章节或流程说明。 |
| `swip-stability-round2` | `29149d4b-d2e8-4602-9767-df4f99289d98` | PASS | 无 | `INSUFFICIENT_EVIDENCE` | `LLM` | 0.75 | 0.2222 | 不能确认支持完整交易流程；只能确认与密钥获取相关。 |
| `swip-stability-round3` | `3c308ad9-fd7b-4d29-84ee-f474e30e18e9` | PASS | 无 | `INSUFFICIENT_EVIDENCE` | `LLM` | 1.0 | 0 | 无法确认支持完整交易流程；微信/支付宝管理只用于 SWIP 交易秘钥下载/初始化校验。 |
| `swip-structured-leadin-fix-20260516-112955` | `e0d041d0-70c8-4a6e-a5c4-660d71ee08d5` | FAIL | `answerOutcome_unexpected_SUCCESS` | `SUCCESS` | `LLM` | 0.4444 | 0.375 | 不能确认支持完整交易流程；微信/支付宝管理只用于获取密钥；最终仍说缺少直接证据、不能判定为支持。 |

关键差异：

- 语义上，修复后 answer 仍是拒答/限定回答，并未编造扫码支付步骤。
- 机械失败只来自 outcome：`INSUFFICIENT_EVIDENCE` 变成 `SUCCESS`。
- 修复后 citation 质量下降：`demoted_citation_count=5`、`unsupported_claim_count=3`，但 outcome 仍为 `SUCCESS` 且 `cacheable=true`。

## 5. 是否经过 `AnswerParagraphPostProcessor` 压缩链路

结论：**方法会被调用，但该 case 不会执行本轮新增的段落压缩保留逻辑。**

依据：

- `AnswerPayloadParser.parseStructuredAnswerPayload(...)` 固定调用：
  - `normalizeStructuredAnswerMarkdown(...)`
  - `compressStructuredExactLookupAnswer(...)`
  - `normalizeStructuredAnswerOutcome(...)`
- `AnswerParagraphPostProcessor.compressStructuredExactLookupAnswer(...)` 开头有早退：
  - 如果 `looksLikeComparisonQuestion(question)` 或 `looksLikeFlowQuestion(question)` 为 true，直接返回原 `answerMarkdown`。
- 本 case question 含 `完整交易流程`。
- `QuerySemanticRules` 的 flow signals 包含 `流程`，`looksLikeFlowQuestion(question)` 会命中。

因此：

| 检查项 | 判断 |
|---|---|
| 是否调用 `compressStructuredExactLookupAnswer(...)` | 是，调用入口固定存在 |
| 是否进入段落遍历/压缩主体 | 否，flow question 早退 |
| 是否触发本轮新增的“dangling lead-in + structured body”保留逻辑 | 否 |
| 是否裁掉了拒答/证据不足表述 | 否 |
| 是否保留了原本应被丢弃的结构化主体 | 否 |
| 是否由该压缩逻辑导致 `PARTIAL_ANSWER` / `NO_RELEVANT_KNOWLEDGE` 变 `SUCCESS` | 否 |

## 6. DB audit 观察

`query_answer_audits` 中四次同 case 均为 `generation_mode=LLM`、`review_status=PASSED`。

| QueryId | Audit outcome | Cacheable | Citation coverage | Unsupported claims | Verified | Demoted | Skipped |
|---|---|---:|---:|---:|---:|---:|---:|
| `85cdcdb9-fb48-4daf-8909-cbf7e0b61550` | `INSUFFICIENT_EVIDENCE` | false | 0.7143 | 2 | 5 | 2 | 0 |
| `29149d4b-d2e8-4602-9767-df4f99289d98` | `INSUFFICIENT_EVIDENCE` | false | 0.7778 | 2 | 6 | 2 | 1 |
| `3c308ad9-fd7b-4d29-84ee-f474e30e18e9` | `INSUFFICIENT_EVIDENCE` | false | 1.0 | 0 | 6 | 0 | 0 |
| `e0d041d0-70c8-4a6e-a5c4-660d71ee08d5` | `SUCCESS` | true | 0.625 | 3 | 4 | 5 | 2 |

修复后 answer claims 中仍有拒答语义：

- `不能确认 SWIP 系统支持“支付宝和微信扫码支付”的完整交易流程`
- `这只能证明与密钥下载/签到初始化有关，不能证明支付宝、微信扫码支付的完整交易流程受 SWIP 支持`
- `但“支付宝和微信扫码支付完整交易流程”缺少直接证据，不能判定为支持`

但 audit outcome 为 `SUCCESS`。这说明问题不是正文被改成“可回答”，而是 answer outcome 与正文语义不一致。

## 7. 是否由本轮改动导致

判断：**不是本轮 `AnswerParagraphPostProcessor` 改动直接导致。**

理由：

- 本轮改动只在 `compressStructuredExactLookupAnswer(...)` 内新增结构化主体保留和 dangling lead-in 判断。
- 该 case 因 `完整交易流程` 命中 flow question，压缩方法早退。
- 修复前后 retrieval 身份一致：都召回使用手册与安装手册 FAQ 33；retrieval recall@5/@10 均为 1。
- 修复后 answer 正文未丢失拒答内容，也没有新增 forbidden claim。
- 失败原因只来自 LLM payload / normalized payload 的 `answerOutcome=SUCCESS`。

不能完全排除“完整 eval 中模型采样顺序/上下文时序造成一次性波动”，但从代码路径看，本轮段落压缩改动没有改变该答案正文或 outcome 的直接执行分支。

## 8. 是否为 LLM 方差

判断：**有 LLM 方差触发，但不把最终归类定为纯 LLM 方差。**

证据：

- 修复前三轮稳定 run 均为 `INSUFFICIENT_EVIDENCE / LLM / PASS`。
- 修复后 run 为 `SUCCESS / LLM / FAIL`。
- 四次 answer 正文语义高度接近，都是“不能确认/缺少直接证据/不能判定为支持”。
- 唯一导致 pass/fail 翻转的是模型或解析后的 outcome 标签。

为什么最终归类为 outcome normalization 问题：

- 系统已经有 `normalizeStructuredAnswerOutcome(...)`，说明 answer outcome 不能完全信任模型自报。
- 当前 normalization 只处理 `PARTIAL_ANSWER -> SUCCESS` 的上调路径。
- 对 `SUCCESS + 明显拒答正文` 没有下调 guard，导致语义拒答被记录为成功且可缓存。

## 9. 是否需要回退或加 guard

| 问题 | 判断 |
|---|---|
| 是否建议回退 agentA 本轮 lead-in 修复 | 否 |
| 是否建议保留本轮改动 | 是 |
| 是否需要为了该 case 牺牲无答案保护 | 否 |
| 是否建议加 guard | 是，但只能作为下一轮唯一最小修复点 |

建议的唯一最小修复点：

- 在 structured answer outcome normalization 层增加通用下调 guard：
  - 当 LLM 返回 `SUCCESS`，但 answerMarkdown 明确包含“不能确认 / 无法确认 / 证据不足 / 缺少直接证据 / 不能判定 / 未提供”等通用拒答或证据不足语义时，将 outcome 下调为 `INSUFFICIENT_EVIDENCE` 或 `PARTIAL_ANSWER`。
  - guard 必须基于通用否定/证据不足语言信号，不允许绑定 SWIP、支付宝、微信、扫码支付、具体文件名或具体答案片段。

最小允许文件范围建议：

- 首选：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java`
- 如需复用已有语义支持方法，可最小扩展到同包 outcome/question type support，但不得改 retrieval、prompt、citation、题集或 runner。

## 10. 最终归类

| 候选归类 | 判断 |
|---|---|
| LLM 方差 | 不是最终归类；它是触发因素 |
| 本轮段落压缩改动导致 | 否 |
| outcome normalization 问题 | 是 |
| eval expectation 问题 | 否，NO_ANSWER case 不应接受 `SUCCESS` outcome |
| 其他 | 否 |

## 11. 本轮改动声明

- 本轮是否修改代码：否。
- 本轮是否修改题集：否。
- 本轮是否修改 outcome gate：否。
- 本轮是否处理其他 SWIP 失败：否。
- 本轮新增报告：`swip_unanswerable_regression_analysis_report.md`。
- 本轮 redline 刷新：`special_cases_report.md`。
