# Terminal Unit Phase 1E borrowing_system 失败只读归因报告

## 1. 总结结论

当前唯一推荐下一步：优先修 fallback / answer consumption 对 terminal unit FACT_CARD evidence 的通用消费规则，而不是继续优先加强编译期 article summary 覆盖。

不推荐优先修 article summary 的原因：

- 当前 DB 只读审计显示，FQ6 / FG2 的目标 terminal unit 已进入 fused topK，且当前 article 正文也已有 `borrowing_system.version = v2.3.1` 与 `borrowing_system.max_concurrent_requests = 50` 行；最终答案仍未消费这些值。
- 失败答案来自 fallback selected snippets，实际选择了 `return_policy`、`equipment_types` 等 ARTICLE / SOURCE 片段；这说明阻塞点已经从“检索不到 / 编译完全缺失”下移到“有证据但未被 answer consumption 选中”。
- 继续扩大 article summary 只能提高聚合文章的冗余覆盖，不能解决 terminal unit 已召回却被 `preferArticleEvidence=true` 丢弃、或 terminal unit 进入候选后未成为 selected evidence 的通用问题。

## 2. FQ6 / FG2 链路分解

### retrieval / fused topK

只读 DB 以最新 run 为准：

- FQ6：`equipment-borrowing-policy.yaml 里，预约系统当前的版本号是什么？`
  - `borrowing_system.version = v2.3.1`
  - `channel_name=fact_card_terminal_fts`
  - `evidence_type=FACT_CARD`
  - `fused_rank=1`
  - `included_in_fused=true`
  - `score=54`

- FG2：`equipment-borrowing-policy.yaml 里预约系统的最大并发请求数是多少？`
  - `borrowing_system.max_concurrent_requests = 50`
  - `channel_name=fact_card_terminal_fts`
  - `evidence_type=FACT_CARD`
  - `fused_rank=1`
  - `included_in_fused=true`
  - `score=89`

上一轮验证报告中的 fused rank 为 2；当前库最新 run 中目标 unit 已升到 rank 1。两者共同结论一致：目标 terminal unit 确实在 fused topK 内，不是 retrieval topK 缺失。

### fallback candidates

源码上，`AnswerFallbackEvidenceSelector.selectFallbackEvidenceHits(...)` 会先计算 `preferArticleEvidence=false` 的全量相关候选，再计算 `preferArticleEvidence=true` 的 article / contribution 优先候选。terminal unit 的 mapper content 已由 `display_text + field_description + field_aliases_json::text` 组成，因此 FQ6 / FG2 目标 unit 具备进入全量相关候选的条件。

审计表没有单独持久化 fallback candidate list，所以“进入 fallback candidate”不能由 DB 字段直接证明；但根据当前 fused 命中、terminal content、field aliases 与 `QueryEvidenceRelevanceSupport.filterRelevantHits(...)` 的匹配逻辑，目标 unit 应进入 `preferArticleEvidence=false` 全量候选。

### preferArticleEvidence 过滤

关键阻塞在 `preferArticleEvidence=true` 路径：

- `AnswerFallbackEvidenceSelector.filterFallbackEvidenceHits(...)` 在 `preferArticleEvidence=true` 时只保留 `ARTICLE` / `CONTRIBUTION`，直接跳过 `FACT_CARD`。
- `selectFallbackEvidenceHits(...)` 只要 preferred article hits 非空，默认返回 retained article hits。
- `shouldPreferMixedEvidence(...)` 只有在全量最佳证据明显优于 article 证据，或特定聚合 / 状态场景下才切回 mixed evidence；FQ6 / FG2 没有触发这个保护。

因此，terminal unit FACT_CARD 虽然进入 fused topK，也能作为全量候选参与评分，但在 article preferred path 中被过滤，最终没有进入 selected evidence。

### selected evidence

query answer audit 显示，FQ6 / FG2 最终答案为 `PARTIAL_ANSWER + FALLBACK`，正文选择的是：

- `damage_report_required = true`
- `overdue_notice_channels = 站内通知；邮件`
- `equipment_types` 的最长借用天数 / 押金 / 逾期费用摘要
- 参考说明中出现 `api_endpoint`

这些 selected evidence 均未直接回答 `version` 或 `max_concurrent_requests`。

### answer generation payload

`AnswerGenerationFallbackOutcomeSupport.buildEvidencePayload(...)` 调用 `selectFallbackEvidenceHits(...)` 后再构造 deterministic fallback Markdown。由于 selected fallback hits 已偏向 ARTICLE / SOURCE，后续 `AnswerFallbackConclusionBuilder`、`selectQuestionFocusedFallbackSnippets(...)` 只能在错误的 fallback hit 集合里挑句子。

当前 article 正文实际包含目标表格行：

- `version = v2.3.1`
- `max_concurrent_requests = 50`

但最终 payload 没有选到这些行。这进一步说明问题不只是“article 正文没有值”，而是 answer consumption 选择了错误片段。

### final answer

FQ6 / FG2 final answer 均没有输出目标值，且 audit claims 显示输出 claim 围绕归还规则、通知渠道和设备类型摘要。最终失败点是 selected evidence / snippet 不含目标 terminal values。

## 3. 编译期 summary 分析

### equipment_types 为什么能被 article summary 覆盖

`equipment_types` 在 article 中被 writer 生成为密集表格和政策理解段：

- 设备类型、最长借用天数、押金、逾期费用、审批要求、归还检查要求被放在同一表格或同一摘要段。
- FQ3 / FQ4 / FG1 的目标值正好落在这些高密度 ARTICLE 句子中。
- fallback 在偏向 ARTICLE 时，仍能从这些句子中选到足够接近的问题答案。

### borrowing_system 为什么没被稳定覆盖

这里要区分三层：

- `article.summary` 字段只是 `MergedConcept.description`，不是 terminal value 的确定性摘要。
- article metadata description 当前只覆盖了 `borrowing_system` 的部分信息，例如 name / api_endpoint，没有稳定覆盖 version / max_concurrent_requests。
- 当前 article 正文已经包含 version / max_concurrent_requests 表格行，但 fallback selected snippets 没有选中这些行。

所以 borrowing_system 的失败不是单纯“编译期 article 没写入值”。更准确的归因是：article 层聚合摘要和 metadata 对这些 scalar terminal values 覆盖不稳定，且 answer fallback 在已有 terminal unit 与正文目标行存在时仍未优先消费它们。

### 是否属于通用结构摘要缺口

是，但不是下一步最小变量。结构化小文档里的 scalar object 需要更稳定地进入 article summary / metadata，这是通用缺口；但 Terminal Unit Phase 1 的目标正是避免 answer 完全依赖 article 聚合摘要。当前 terminal unit 已经把 exact scalar value 投影、召回并审计出来，下一步应先让 answer consumption 消费这类证据。

## 4. fallback selector 分析

### terminal unit FACT_CARD 是否被丢弃

是。`preferArticleEvidence=true` 明确按 `evidenceType` 丢弃所有非 ARTICLE / CONTRIBUTION 命中，包括 `fact_card_terminal_fts` 产生的 terminal unit FACT_CARD。

### 丢弃是否是决定性阻塞

是当前唯一下一步根因。理由：

- 目标 terminal unit 已在 fused topK，且 rank 很高。
- source 原文也进入 fused，且包含目标 YAML 行。
- article 正文也包含目标表格行。
- final answer 仍选错片段，说明阻塞在 selected evidence / snippet consumption。

### 是否已有 mixed evidence 机制可复用

已有，但条件过窄：

- `shouldPreferMixedEvidence(...)` 可在部分场景回到全量证据。
- `addQuestionFocusedStructuredPathFactCard(...)` 可补充结构化 fact card，但只在互补证据场景触发，并要求 selected hits 同时有 SOURCE 与 ARTICLE / CONTRIBUTION。
- `buildTerminalFieldExactPathConclusionLines(...)` 已能在 fallback hits 中寻找 structured path value，但前提是 terminal unit 已进入 fallback hits，且问题侧 terminal field intent 能识别。

推荐复用这些机制，而不是另起一条 query fallback gate：当 exact lookup / structured fact 问题中存在高分、source-derived、query-focused 的 terminal unit FACT_CARD 时，应允许它进入 selected fallback hits，必要时置于 ARTICLE 前，后续结论构造才能选中 `displayText` 里的 exact value。

## 5. 红线判断

下一轮修复必须满足：

- 不允许在 Java 主链硬编码业务词、字段名、题目问法、答案值、文件名或 case id。
- 不允许把 `version`、`max_concurrent_requests`、`borrowing_system`、`equipment-borrowing-policy.yaml`、`v2.3.1`、`50` 写成专用分支。
- 不允许 hidden eval 污染。
- 不允许通过 prompt 写答案模板。
- 不允许为 FQ6 / FG2 单独开白名单。

可接受的通用依据：

- evidence type、channel、metadata 中的 `channel=fact_card_terminal_fts`。
- terminal unit 自身 source-derived metadata：`displayText`、`keyPath`、`parentPath`、`terminalKey`、`value`、`fieldAliases`、`fieldDescription`。
- 通用 exact lookup / structured fact 问题类型。
- query tokens 与 terminal unit metadata / content 的通用匹配分数。

## 6. 推荐修复方向

交给 agentA。

本轮只允许改：

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
- 如 agentA 证明仅 selector 提升后仍会输出 alias JSON 而非 `displayText`，才允许在同一变量内最小修改 `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`，用于通用 terminal unit snippet 选择；不得扩大到其他模块。
- 可新增或补强对应 synthetic 单元测试，优先 `AnswerFallbackEvidenceSelectorTests` 与 `AnswerGenerationServiceTests`。

禁止改：

- compiler article summary / writer / reviewer / prompt。
- `src/main/resources/**`、`config/**`、`schema.sql`。
- redline 脚本、allowlist。
- eval 题集、fixtures、case id、标准答案、hidden eval。
- retrieval / RRF / citation / deep_research / query rewrite。

是否允许改代码：允许，只允许上述 answer consumption 单变量。

是否允许跑测试：允许。要求：

- `bash scripts/scan-redline.sh special_cases_report.md`
- 定向测试：`AnswerFallbackEvidenceSelectorTests`、`AnswerGenerationServiceTests`
- 全量 `mvn test`

是否允许清库 / 重建：agentA 不允许。代码修复完成后交 agentD 再做 clean schema / runtime 验证。

下一轮报告命名：

- agentA：`docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1e_terminal_evidence_consumption_fix_result_report.md`
- agentD：`docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1e_terminal_evidence_consumption_verification_report.md`

## 7. 计划台账回写

已回写 `terminal_unit_phase1_implementation_plan.md`：

- 清理旧的 `agentB 只读归因进行中` 状态，将上一轮 answer consumption 分析标记为已完成。
- 记录本轮 FQ6 / FG2 只读归因结论：目标 terminal unit 已进入 fused topK，最终失败点是 fallback / answer consumption 未优先消费有效 terminal unit evidence。
- 记录下一步 agent 分工：agentA 做 answer consumption 最小修复，agentD 做后续 clean schema / runtime 验证。
