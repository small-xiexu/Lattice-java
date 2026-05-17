# 项目质量打磨进度与踩坑台账

更新时间：2026-05-17（query visibility hard filter 验证通过后更新）

本台账记录质量打磨、Query/SWIP eval、baseline 修复与多 agent 协作的当前状态。后续推进前先读本文件；阶段结论变化后必须回写。

## 当前阶段

- 主工程状态：Phase 12 clean rebuild 已完成阶段性验收，主工程进入质量打磨、RAG eval、多 agent 协作阶段。
- 主 query baseline 状态：`final_query_baseline_gate_report.md` 与 `phase12_final_clean_rebuild_gate_report.md` 均给出 gate 通过结论；但当前数据库据 SWIP 报告为 SWIP clean 库，不能直接代表主 baseline 可跑状态。
- SWIP eval 状态：SWIP 题集已接入 strict eval；expect 机械断言修正后从 `0/23` 恢复到 `13/23`；RRF revert 后三轮稳定性校验确认稳定区间为 `13-14/23`，11/23 为偶发波动未复现。SWIP answer grounding patch 完成后稳定区间提升至 `15-16/23`。focus snippet patch 副作用复核三轮为 `16/23、17/23、15/23`，维持并略超 baseline 区间。
- RRF retained content 主线：已回退。修复尝试 strict pass 未提升、Recall/Citation/LLM 指标下降且新增回归，结论为不保留。
- SWIP answer grounding 主线：patch 已完成提交前质量复核，代码可保留，详见 `swip_answer_grounding_pre_commit_quality_review_report.md`。
- SWIP BANK-SETTLEMENT focus snippet 主线：副作用复核已通过，结论为可保留。`swip_focus_snippet_patch_side_effect_review_report.md` 确认：redline BLOCKER=0，BANK-SETTLEMENT-001 三轮稳定 PASS，保护 case 三轮稳定 PASS，无新增稳定回归。
- 报告 cleanup：本轮按 `report_cleanup_plan_after_bank_settlement_focus_snippet.md` 执行清理，删除 4 个过期中间报告，详见 `report_cleanup_after_bank_settlement_focus_snippet_result.md`。
- compile review observability：后台可观测性改动已完成，API 与后台 UI 均已验证通过。验证报告见 `compile_review_observability_verification_report.md`，fix result report 见 `compile_review_observability_fix_result_report.md`。
- compile review persist gate：`PersistArticlesNode` 已修复，不再合并 `needsHumanReviewArticlesRef`，只允许 `review_status=passed` 的 article 进入正式 persist。测试补强已完成，新增 `PersistArticlesNodeTests` 覆盖混合 status 旧风险路径。详见 `compile_review_persist_gate_fix_result_report.md`、`compile_review_persist_gate_runtime_verification_report.md`、`compile_review_persist_gate_test_result_report.md`。
- compile review query visibility hard filter：5 条 article-backed 通道 SQL 已增加 `review_status='passed' AND lifecycle='ACTIVE'` 条件，RefKey/ArticleChunk 的 OR 条件已用括号包裹防止绕过。source/source_chunk/fact_card 未修改。最终验证通过：redline BLOCKER=0、article-backed 定向测试 8/0/0、source/fact card 定向测试 33/0/0、全量 mvn test=814/0/0。详见 `compile_review_query_visibility_filter_verification_report.md`。
- 下阶段：agentD 做 query visibility pre-commit 质量复核。

## 当前 Gate

| 项 | 当前状态 | 说明 |
|---|---|---|
| redline | `BLOCKER=0` | query visibility 验证后：`BLOCKER=0 / REVIEW=1852 / ALLOWLIST=239`，无新增阻断项。最终以 pre-commit 复核 REV/ALLOWLIST 结果为准。 |
| mvn test | `814/0/0` 通过 | query visibility 测试补强后：新增 `ArticleVisibilitySearchMapperTests`（2 个 case），全量 814 通过。测试库已隔离到 `ai-rag-knowledge-test`。 |
| main baseline | 阶段 gate 已通过 | `final_query_baseline_gate_report.md` 为 `9/10` 且 gate 通过；`phase12_final_clean_rebuild_gate_report.md` 为 `8/10` 且 6 项 gate 通过。 |
| SWIP strict eval | 稳定区间 `15-17/23` | focus snippet patch 副作用复核三轮：16/23、17/23、15/23；BANK-SETTLEMENT-001 三轮稳定 PASS；保护 case 三轮稳定 PASS。详见 `swip_focus_snippet_patch_side_effect_review_report.md`。 |
| 当前数据库状态 | SWIP clean 库 | 据 RRF/QFE 报告：`source_files=2`、`articles=4`，只含 SWIP 两份 docx；不能在该库跑主 baseline。 |
| 模型配置状态 | 不在本轮变更范围 | Phase 12 主链曾配置 `gpt-5.5 + zhipu_embedding`；compile review 当前默认 `review-enabled=false`，实际 review route 为 rule-based。 |
| compile review observability | API + UI 验证通过 | `compile_review_observability_fix_result_report.md` 显示 redline BLOCKER=0、mvn test=811/0/0；`compile_review_observability_verification_report.md` 确认 API 与后台 UI 均展示 route/outcome/fix 信息。提交前仍需最终复核。 |
| compile review persist gate | 修复 + 测试补强完成 | `PersistArticlesNode` 已移除 `needsHumanReviewArticlesRef` 合并，只 persist `passed`；新增 `PersistArticlesNodeTests` 覆盖混合 status 旧风险路径。运行时验证 passed 全链路完整。`needs_human_review` 端到端场景当前无法自然构造，已通过源码审查 + 定向单元测试闭合。 |
| compile review query visibility | 修复 + 验证通过 | 5 条 article-backed mapper SQL 均增加 `review_status='passed' AND lifecycle='ACTIVE'`；RefKey/ArticleChunk OR 条件已用括号包裹防绕过；source/fact_card 未修改。定向测试 8/0/0（article-backed）+ 33/0/0（source/fact card），全量 814/0/0。详见 `compile_review_query_visibility_filter_verification_report.md`。 |

## 多 Agent 当前职责

| Agent | 职责 | 当前状态 | 是否允许改代码 |
|---|---|---|---|
| agentA | 单一代码修复执行者 | answer grounding + focus snippet patch 均已完成，副作用复核通过；待提交 | 是，但同一轮只能有一个 agentA 改主链 |
| agentB | 治理/链路分析 | 已产出 compile review 治理分析；只读判断 rule-based 不等于 LLM 内容审查 | 否 |
| agentC | 项目进度台账与文档治理 | 已完成 query visibility 台账更新与报告输出 | 否，除文档/报告 |
| agentD | 验证/测试 | 负责 redline、`mvn test`、baseline、业务 eval 验证报告；已完成 compile review observability + persist gate + query visibility 验证；下一步做 query visibility pre-commit 质量复核 | 否，除验证报告 |

## 已验证结论

- Q-MQ 已闭环；主 baseline 阶段 gate 已通过，但需注意 LLM 引用标记偶发不稳定。
- 测试库隔离已完成，`mvn test` 默认写入 `ai-rag-knowledge-test`，不再污染真实 baseline 库。
- 主 query baseline 已达到当前 gate。
- SWIP 题集已接入 strict eval。
- SWIP docx 抽取没有整体缺失；剩余失败主要不是源文抽取缺失。
- prompt companion snippet 尝试负收益，已回退，不建议继续沿该形态扩大。
- RRF retained content 修复尝试负收益或无净提升，已回退且确认不保留；RRF 主线已收口。
- compile review 当前是 rule-based，不等于 LLM 内容审查；若产品承诺“审查后入库”，需启用 LLM reviewer 并补 query 可见性门禁。
- SWIP answer grounding patch 代码可保留：redline BLOCKER=0、mvn test=811/0/0、三轮 strict eval 稳定区间 15-16/23、三个目标 case 稳定通过、无新增稳定回归。详见 `swip_answer_grounding_pre_commit_quality_review_report.md`。
- SWIP focus snippet patch 代码可保留：redline BLOCKER=0、BANK-SETTLEMENT-001 三轮稳定 PASS、保护 case 三轮稳定 PASS、无新增稳定回归、无业务特判、触发条件合理保守。详见 `swip_focus_snippet_patch_side_effect_review_report.md`。
- 当前生产代码改动：`AnswerParagraphPostProcessor.java`（structured/exact lookup lead-in 和 sequence supplement 后处理）、`AnswerGenerationPayloadOrchestrator.java`（SUCCESS 证据不足 outcome guard + prompt audit instrumentation）、`AnswerGenerationPromptEvidenceSupport.java`（focus snippet 分布式窗口选择），均为通用能力，未发现 case 特判。
- compile review observability 已验证：rule-based review 在后台 API 与 UI 均明确标识为 `规则审查（不是 LLM 内容审查）`，`reviewRoute=rule-based` 可见，`acceptedCount/pendingReviewCount/needsHumanReviewCount` 可见，fix 未触发原因 `未触发自动修复：无 fixable issue` 可见。
- 本轮 observability 只解决了"看不清是否审查"的问题，没有改变审查治理行为：未启用 LLM reviewer，未修改 persist/query 可见性过滤。
- persist gate 已修复：`PersistArticlesNode` 不再合并 `needsHumanReviewArticlesRef`，只允许 `review_status=passed` 的 article 进入正式 persist。passed article 入库全链路（articles → chunks → vector index）已验证完整。
- persist gate 测试补强已完成：`PersistArticlesNodeTests` 覆盖混合 passed + needs_human_review 输入，断言只 persist passed，定向测试 + 全量 812/0/0 通过。
- Query visibility hard filter 已完成：5 条 article-backed 通道 SQL 均已添加 `review_status='passed' AND lifecycle='ACTIVE'` 条件，RefKey/ArticleChunk 的 OR 条件已用括号包裹防止硬过滤被绕过。source/source_chunk/fact card 未修改。redline BLOCKER=0，article-backed 定向测试 8/0/0，全量 mvn test=814/0/0。
- persist gate 与 query visibility hard filter 互补：persist gate 防止新入库的非 passed article 进入正式表；query filter 防止历史脏数据或人工操作残留被 query 召回。两道门禁缺一不可。
- 当前未开启 LLM reviewer。

## 踩坑记录

| 坑 | 表现 | 结论 | 后续规则 |
|---|---|---|---|
| SWIP `23/23` 弱通过不是严格验收 | 弱口径可能只证明链路跑通，不能证明答案完整覆盖 required terms | 必须以 strict eval 和人工字段共同判断 | 禁止把弱通过当作质量收口。 |
| `requiredSourceTerms` 机械映射导致 `0/23` 伪失败 | runner 的 `sourceText` 不包含部分人工 evidence 线索，机器硬断言全挂 | 这是评测断言口径问题，不是系统能力全挂 | 机器断言只放 runner 稳定可见字段；人工证据留在顶层验收字段。 |
| prompt companion snippet 负收益 | `14/23` 降到 `13/23`，新增原通过 case 回归 | 追加 companion 会扰动 LLM 对证据的取舍 | 不继续沿 prompt companion 形态扩大；失败也要记录并回退。 |
| RRF retained content 修复尝试负收益 | strict pass 未提升，Recall/Citation/LLM 指标下降，并新增 SWIP-USAGE-REPRINT-001 回归 | 当前 retained content 选择规则不是安全收益点 | 已回退且确认不保留；RRF 主线收口。回退后 REPRINT-001 回归已恢复。 |
| SWIP clean 库不能跑主 baseline | 当前库只含 2 个 SWIP source、4 篇文章 | 主 baseline 与 SWIP eval 的知识库前提不同 | 跑主 baseline 前必须确认库已恢复主 clean rebuild 数据。 |
| rule-based review 不等于 LLM reviewer | compile step 显示 review 成功，但 route 为 `rule-based` | 只能证明结构兜底检查通过，不能证明内容审查通过 | 后台、报告和产品口径必须展示 review route；启用 LLM reviewer 前不得宣称 LLM 审查。 |
| 测试库曾污染真实 baseline 库 | 旧测试硬编码写入 `ai-rag-knowledge` | 已通过 `ai-rag-knowledge-test` 隔离 | 发现 baseline 异常先查库污染；`mvn test` 后应确认未改真实库。 |
| 多 agent 同时改主链会导致不可归因 | RRF、prompt、fallback、citation、题集若同时变更，eval 波动无法定位 | 每轮只允许一个代码主变量 | 同一轮最多一个 agent 改 `src/main/java/**`；其他 agent 只读或写报告。 |
| outcome guard 存在过度降级风险 | `SWIP-USAGE-BANK-SETTLEMENT-001` 三轮均为 `INSUFFICIENT_EVIDENCE`，疑似被 outcome guard 过度降级 | 中文拒答信号在 outcome guard 中较敏感，存在误降级残余风险 | 已通过 focus snippet patch 解决，BANK-SETTLEMENT-001 三轮稳定 PASS。 |
| focus snippet 分布式窗口导致 promptLength 增大 | BANK-SETTLEMENT promptLength 从 13093 增至 23815（+82%） | 增加集中在多焦点/流程/枚举问题类型，path/exact-identifier 不受影响；当前未观察到 token 超限或回答质量下降 | promptLength 增大作为后续观察项；若后续出现 token 超限或成本问题，再评估窗口上限收紧。 |
| SWIP-USAGE-REPRINT-001 已知 LLM 波动 | 多轮验证中反复出现单轮 PASS/FAIL 交替 | 非 focus snippet 引入，属于已知 LLM 枚举完整性波动 | 不纳入本轮修复范围；后续可观察是否需调整 eval 预期或后处理策略。 |
| compile review 成功不等于 LLM 内容审查成功 | compile step 显示 review 成功，但 route 为 `rule-based`，只能证明结构兜底检查通过 | 当前 compile review 只是 rule-based，没有 LLM 内容审查能力 | 后台、API、报告必须明确展示 review route；在启用 LLM reviewer 并通过 persist/query 可见性门禁之前，不得宣称 LLM 审查。 |
| 可观测性解决"看不清"但不改变治理行为 | 本轮 observability 改动让 route/outcome/fix 在后台可见，但没有改变 review/persist/query 的实际行为 | 可观测性是治理的前提，但不是治理本身 | 后续如需启用 LLM reviewer，必须单独设计 persist/query 可见性门禁，不能假设当前 rule-based 的可见性对 LLM reviewer 同样有效。 |
| `allowPersistNeedsHumanReview=true` 曾存在绕过 persist gate 风险 | 旧 `PersistArticlesNode` 在 `allowPersistNeedsHumanReview=true` 时合并 `needsHumanReviewArticlesRef`，可能将未通过审查的文章写入正式 query-facing 表 | 这是 persist gate 的第一道治理缺口，优先级高于 query visibility filter | persist gate 已修复，移除该合并逻辑；后续新增 persist 节点时必须验证 gate 语义不变。 |
| 运行时不易自然构造 `needs_human_review` | 当前 rule-based reviewer + autoFixEnabled=true 条件下，所有文章均为 `passed`，无法端到端验证 persist gate 阻止 `needs_human_review` 入库 | rule-based 审查门槛低，autoFix 进一步降低非 pass 概率 | 对这类"正常流程不易触发"的防护路径，必须用极窄单元测试覆盖（如 `PersistArticlesNodeTests`），不能依赖端到端运行时验证。 |
| persist gate 不能替代 query visibility filter | persist gate 只防止新编译产出经正式 persist 入库；历史脏数据、人工 SQL 写入、旧版本残留仍可能存在于 articles 表并被 query 召回 | persist gate 是第一道门（防新入库），query visibility filter 是第二道门（防历史残留被召回），两道缺一不可 | 实现 persist gate 后仍必须补 query visibility hard filter；不能因为"当前库全是 passed"就跳过第二道门。 |
| OR 条件加 AND 追加容易绕过 hard filter | `RefKeySearchMapper` 原有 `where false or lower(...) like ...`，`ArticleChunkMapper` 原有 `where ac.search_tsv @@ query.tsq or ...`——若直接在后面追加 `and a.review_status='passed'`，OR 的优先级会使 hard filter 只约束最后一个 OR 分支 | SQL OR/AND 优先级：不加括号时 OR 优先级低于 AND，如果某行满足 OR 的另一个分支，hard filter 会被绕过 | 在包含 OR 的 WHERE 子句中追加 hard filter 时，必须先用括号包裹原有 OR 条件，再追加 AND。 |

## 当前禁止事项

- 不准多个 agent 同时改主链。
- 不准在 SWIP clean 库上跑主 baseline。
- 不准继续调题集来追 pass。
- 不准写 SWIP / 文档 / case 特判。
- 不准跳过 redline / `mvn test` / baseline 归因。
- 不准把 rule-based review 描述成 LLM 内容审查。
- 不准在代码修复未收口时删除仍可能被引用的 SWIP/RRF 报告。
- 不准继续扩大 AnswerParagraphPostProcessor。
- 不准继续扩大 outcome guard。
- 不准混修其他 SWIP 稳定 FAIL。
- 不准为 SWIP / IP / 151 / 银行结算写特判。

## 下一步计划

1. （已完成）agentA 已回退 RRF retained content 改动，RRF 主线已收口。
2. （已完成）已审 RRF revert/stability 报告。
3. （已完成）RRF 收口后报告 cleanup 已执行。
4. （已完成）SWIP answer grounding patch 已完成提交前质量复核，代码可保留。
5. （已完成）SWIP focus snippet patch 副作用复核通过，BANK-SETTLEMENT-001 稳定 PASS，代码可保留。
6. （已完成）BANK-SETTLEMENT 后报告 cleanup 已执行，详见 `report_cleanup_after_bank_settlement_focus_snippet_result.md`。
7. （已完成）compile review observability 后台可观测性改动已完成，API + UI 验证通过。详见 `compile_review_observability_verification_report.md`。
8. （已完成）compile review persist gate 修复 + 运行时验证 + 测试补强已完成。详见 `compile_review_persist_gate_fix_result_report.md`、`compile_review_persist_gate_runtime_verification_report.md`、`compile_review_persist_gate_test_result_report.md`。
9. （已完成）compile review query visibility hard filter 修复 + 测试补强 + 最终验证已完成。详见 `compile_review_query_visibility_filter_verification_report.md`。
10. （当前）agentD 做 query visibility pre-commit 质量复核：确认 redline BLOCKER=0、mvn test=814/0/0、工作区只含允许变更；提交时排除 `.gitignore`、`compile_review_observability_verification_report.md`、`docs/oh-my-codex-agent-orchestration-guide.md`。
11. （后续）单独决定是否提交 `.gitignore` 和 `docs/oh-my-codex-agent-orchestration-guide.md`。
12. （后续）如要启用 LLM reviewer，必须单独设计并验证 persist/query 可见性门禁，不能假设当前 rule-based 的可见性对 LLM reviewer 同样有效。

## 更新规则

- 每次质量打磨、Query/SWIP eval、baseline 修复或多 agent 并行前，先读本文件。
- 每轮阶段性结论变化后，更新本文件。
- 当前 gate、下一步计划、踩坑结论、agent 分工发生变化时，必须同步更新本文件。
- 代码修复失败、回退、负收益也必须记录。
- 不允许只在聊天里说明而不更新台账。
- 本文件是质量打磨阶段的进度台账，不替代用户指定的计划文件；如果用户指定 `docs/**/plans/*.md`，仍以计划文件为唯一进度台账并随做随回写。
