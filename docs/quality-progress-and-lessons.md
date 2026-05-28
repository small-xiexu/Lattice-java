# 项目质量打磨进度与踩坑台账

更新时间：2026-05-28（agentC 同步 fresh eval 2 三轮失败实验与 terminal unit 物化方向，写入长期路线策略与 Query 复杂度治理独立线）

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
- compile review LLM reviewer fail-closed 安全底座：`ArticleReviewerGateway` 已修复（1 行变更），`review-enabled=true` 且 LLM 调用异常或解析失败时不再回退到 rule-based pass，改为返回 `TIMEOUT_FALLBACK` / `PARSE_FAILED`（均进入 `needs_human_review`）。`review-enabled=false` 行为保持不变。未启用 LLM reviewer。详见 `compile_review_llm_reviewer_fail_closed_fix_result_report.md`、`compile_review_llm_reviewer_fail_closed_verification_report.md`。
- compile review LLM reviewer 小流量复验：在测试库 `ai-rag-knowledge-test` 通过运行时环境变量 `LATTICE_LLM_REVIEW_ENABLED=true` 临时启用，writer = `compile.writer.baseline-gpt-5-5-chat`，reviewer = `compile.reviewer.baseline-gpt-5-5-chat`（非 rule-based），LLM approved → passed → persist 全链路完整，后台可观测性正常。验证后已恢复默认关闭，主库未触碰。详见 `compile_review_llm_reviewer_small_flow_reverification_report.md`。
- compile review 默认 LLM 模式：代码实现已完成（agentA），新 job 默认 reviewMode 改为 `LLM`。运行时验证已完成（agentD）：默认不传 reviewMode 的真实 compile job 走 LLM reviewer（route=anthropic，非 rule-based），LLM non-pass 不入库，显式 RULE_BASED 仍走 rule-based 且可入库。入口闭环审计已完成（agentB）：用户可触发 compile 入口全部收敛到 StateGraph 的 Writer→Reviewer→Fixer→Reviewer→Persist gate 闭环。详见 `compile_review_default_llm_mode_fix_result_report.md`、`compile_review_default_llm_mode_runtime_verification_report.md`、`compile_review_entrypoint_loop_coverage_analysis_report.md`。
- compile review prompt externalization：Writer / Reviewer / Fixer system prompt 已从 `LatticePrompts.java` 硬编码常量外置到 `src/main/resources/prompts/compiler/*.md`，由新增 `CompilerPromptProvider` @Service 统一加载，支持 `{{shared-grounding-rules}}` 占位符替换。期间修复两轮回归：SchemaAwarePrompts 多构造器 DI 注入失败、shared rules 占位符未生效导致 prompt 文件内联重复。pre-commit 质量复核已通过：redline BLOCKER=0，mvn test=824/0/0，未发现业务硬编码/case 特判/eval 污染。详见 `compile_review_prompt_externalization_pre_commit_quality_report.md`、`compile_review_prompt_externalization_final_runtime_gate_report.md`。
- compile review 人工确认后入库链路：`needs_human_review` 编译草稿持久化到 `compile_article_review_queue`，后台 API 支持 list/detail/approve/reject，approve 后以 `review_status=passed` + `lifecycle=ACTIVE` 写入 articles/chunks/vector index，reject 后不入库。前端"待人工确认"入口可用。pre-commit 复核通过：redline BLOCKER=0，mvn test=844/0/0。已分两个提交：`8fe7001`（publish flow）+ `b453627`（admin API）。详见 `compile_human_review_queue_pre_commit_quality_report.md`。
- 知识库验收 Q6 结构化 fact card 路径修复：fact card 生成层已通过，已保留 YAML/JSON/缩进式结构化字段路径，兼容旧 `key/value/raw`，新增 `keyPath/parentPath/pathSegments/contextPath/displayText` 并增强结构化证据文本。fallback structured evidence、path shape gate、complementary selector、exact path terminal field alias 已全部收口。2026-05-27 agentD 端到端验证通过，Q6 query/fallback 修复已闭环：redline `BLOCKER=0`、全量 `mvn test=915/0/0`、Query Java 主链未见中文字段语义硬编码 / Q6 特判 / 端口值特判 / Kubernetes 特判；真实 API 返回 `fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`，citation 能支撑该事实，`periodSeconds=10` sibling 未被抢占；endpoint / URL / image / version / ordinary numeric 字段保护场景均通过。Q1-Q12 为 `12/12 PASS`，S1-S4 为 `3/4 PASS`，其中 S2 `下一步计划` 仍 FAIL。最终口径只能写成 `Q6 query/fallback 修复已闭环，整体最小验收仍因 S2 标题/anchor 搜索失败未完全通过`。下一步只应进入 Q6 terminal field alias scoped commit，然后把 S2 作为独立标题/anchor 搜索问题分析，禁止继续在 Q6/fallback 主链叠加规则。详见 `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_verification_report.md`、`docs/test/knowledge-base-e2e/q6_fallback_second_root_cause_analysis_report.md`、`docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_fix_result_report.md`、`docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_verification_report.md`、`docs/test/knowledge-base-e2e/q6_fallback_runtime_trace_analysis_report.md`、`docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_fix_result_report.md`、`docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_verification_report.md`、`docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_fix_result_report.md`。
- 知识库验收 S2 标题/anchor 搜索身份修复：agentB 已只读归因为 article chunk FTS 召回后被 ARTICLE articleKey 身份折叠，导致 chunk/anchor 独立席位丢失；agentA 已完成最小通用修复，chunk 级命中写入 `chunkIdentity/chunkIndex/sectionAnchor/channel`，RRF 对带 `chunkIdentity` 的 ARTICLE hit 使用 chunk 级 key，普通 article hit 仍按 articleKey/conceptId 融合。代码层验证通过：redline `BLOCKER=0`、定向测试 `13/0/0`、全量 `mvn test=921/0/0`；本轮未清库、未重建、未导入资料，仍需 agentD 做完整知识库验收，覆盖 Q1-Q12、S1-S4 与 Q6 保护场景。详见 `docs/test/knowledge-base-e2e/s2_title_anchor_search_root_cause_analysis_report.md`、`docs/test/knowledge-base-e2e/s2_chunk_anchor_identity_fix_result_report.md`。
- fresh eval 2 当前正式基线仍以 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md` 为准：Answer Accuracy `10/15`，Search Accuracy `1/4`，Recall@10 `13/15`，Citation Accuracy `2/15`，Abstain Accuracy `2/2`，Hallucination Count `5`，结论为未通过。
- fresh eval 2 结构化 terminal value 桶未闭环：`FQ3/FQ4/FQ6/FG1/FG2` 在 structured fact terminal binding、selector gate、conclusion gate 三轮 query fallback 方向实验后仍为 `0/5 PASS`。三轮实验报告只建议归档为失败实验，不建议提交对应代码。
- fresh eval 2 最新根因判断已从 retrieval 召回转向 evidence unit 粒度：FACT_CARD 已稳定召回且包含目标 terminal assignment，但整卡粒度导致同卡 sibling 共用卡级 identity、score、citation 边界，fallback 容易选中 `type/name/return_policy` 等近邻字段。下一步不再继续叠 query fallback selector/conclusion/snippet gate，应转向 compile/index 层 structured terminal assignment evidence unit materialization，第一阶段先生成 terminal units 并进入 FTS 检索。详见 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_verification_report.md`、`docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md`。
- 已提交 scoped commit 清单（2026-05-27 收口审计）：

  | Commit | 描述 | 所属桶 |
  |---|---|---|
  | `02f220e` | feat(compiler): 增加标题画像生成与文档标题回流 | title-generation |
  | `e551d4c` | feat(documentparse): 回流文档标题元数据 | documentparse |
  | `38ca188` | feat(admin): 展示文章标题画像 | admin title-profile API |
  | `9e16999` | feat(admin): 优化治理工作台诊断 UI 与处理历史 | admin UI / queue |
  | `e286c79` | fix(query): 保留结构化事实卡片作为 fallback 证据 | Q6 fallback fact card |
  | `4d5e8bc` | fix(query): 配置化 exact path terminal field alias | Q6 terminal field |
  | `be4d216` | fix(llm): apiKey 解密失败优雅降级，deep_research 保持 fail-closed | LLM infrastructure |

  上述 7 个 commit 均已按各自范围完成 redline、全量 Maven、定向测试或端到端验证闭环（Q6 terminal field alias 有真实 API 端到端验证；title-generation、documentparse、admin、LLM snapshot 为 redline + 全量 Maven + 定向测试/scoped verification）。剩余未提交项仅为 docs/report 审计归档，详见未提交文档收口计划 `docs/test/remaining_docs_reports_commit_plan.md`。
- **长期路线（2026-05-28 agentC 写入）**：

  长期目标为 **5 套 public eval + 1-2 套 hidden eval**，形成持续泛化验收闭环：
  ```
  public eval 发现问题 → 修通用能力 → 旧题集保护回归 → 新 public eval 泛化检查 → hidden eval 最终验收
  ```
  - **短期**：terminal unit Phase 1A — 让 structured terminal assignment 生成独立 evidence unit，并进入 FTS / RRF。这是 evidence 粒度建设，不是 query fallback 补丁。
  - **中期**：Query 主链复杂度治理 — 降低 AnswerGeneration 继承链深度，治理 `.contains()` 规则分流，减少 query fallback 主链 gate 式补丁累积。**独立开线，不与 terminal unit Phase 1A 并行改代码。**
  - **长期**：完成 5+2 eval 闭环，禁止无限修题。hidden eval 只用于最终泛化验收，AI 不得读取题目/答案/关键词/文件名/case id。

  详见 `docs/test/knowledge-base-e2e/eval-validation-roadmap.md`。

## 当前 Gate

| 项 | 当前状态 | 说明 |
|---|---|---|
| redline | `BLOCKER=0` | Q6 terminal field alias 配置化修复后：`bash scripts/scan-redline.sh special_cases_report.md` 通过，汇总为 `BLOCKER=0`、`REVIEW=2028`、`ALLOWLIST=259`。 |
| mvn test | `已恢复` | 2026-05-27 Q6 terminal field alias 配置化修复阶段全量 `mvn test=915/0/0`；LLM snapshot 测试补强后最新全量 `mvn test=917/0/0` 通过。 |
| main baseline | 阶段 gate 已通过 | `final_query_baseline_gate_report.md` 为 `9/10` 且 gate 通过；`phase12_final_clean_rebuild_gate_report.md` 为 `8/10` 且 6 项 gate 通过。 |
| SWIP strict eval | 稳定区间 `15-17/23` | focus snippet patch 副作用复核三轮：16/23、17/23、15/23；BANK-SETTLEMENT-001 三轮稳定 PASS；保护 case 三轮稳定 PASS。详见 `swip_focus_snippet_patch_side_effect_review_report.md`。 |
| 当前数据库状态 | Q6 验收 clean 库 | agentD 已重建 `ai-rag-knowledge.lattice` 并导入完整知识库验收资料；用户要求确认的 2 条 `needs_human_review` 已 approve 发布。当前计数：`source_files=6`、`articles=6`、`article_chunks=13`、`fact_cards=11`、`article_vector_index=6`、`article_chunk_vector_index=13`。该库用于 Q6 复验，不代表 SWIP clean 库或主 baseline 库。 |
| 模型配置状态 | 测试库已绑定，生产默认关闭 | LLM reviewer 小流量复验使用测试库 binding：writer=`compile.writer.baseline-gpt-5-5-chat`，reviewer=`compile.reviewer.baseline-gpt-5-5-chat`。生产 `review-enabled=false`，默认仍为 rule-based。 |
| compile review observability | API + UI 验证通过 | `compile_review_observability_fix_result_report.md` 显示 redline BLOCKER=0、mvn test=811/0/0；`compile_review_observability_verification_report.md` 确认 API 与后台 UI 均展示 route/outcome/fix 信息。提交前仍需最终复核。 |
| compile review persist gate | 修复 + 测试补强完成 | `PersistArticlesNode` 已移除 `needsHumanReviewArticlesRef` 合并，只 persist `passed`；新增 `PersistArticlesNodeTests` 覆盖混合 status 旧风险路径。运行时验证 passed 全链路完整。`needs_human_review` 端到端场景当前无法自然构造，已通过源码审查 + 定向单元测试闭合。 |
| compile review query visibility | 修复 + 验证通过 | 5 条 article-backed mapper SQL 均增加 `review_status='passed' AND lifecycle='ACTIVE'`；RefKey/ArticleChunk OR 条件已用括号包裹防绕过；source/fact_card 未修改。定向测试 8/0/0（article-backed）+ 33/0/0（source/fact card），全量 814/0/0。详见 `compile_review_query_visibility_filter_verification_report.md`。 |
| compile review LLM reviewer fail-closed | 修复 + 验证通过 | `ArticleReviewerGateway` 1 行变更：LLM 异常/解析失败不再回退 rule-based pass，返回 `TIMEOUT_FALLBACK`/`PARSE_FAILED`（进入 `needs_human_review`）。`review-enabled=false` 行为不变。未启用 LLM reviewer。详见 `compile_review_llm_reviewer_fail_closed_verification_report.md`。 |
| compile review LLM reviewer 小流量复验 | 测试库全链路通过 | 测试库 `ai-rag-knowledge-test` 临时启用：writer/reviewer route 均非 rule-based，JSON 可解析，approved→passed→persist 完整，后台可观测性正常。验证后已恢复默认关闭，主库未触碰。详见 `compile_review_llm_reviewer_small_flow_reverification_report.md`。 |
| compile review 默认 LLM 模式 | 代码实现 + runtime 验证通过 | agentA 实现：新 job 默认 reviewMode=`LLM`，显式 `RULE_BASED` 仍可用。agentD runtime 验证通过：默认不传 reviewMode 走 LLM reviewer（route=anthropic），LLM non-pass 不入库，显式 RULE_BASED 仍走 rule-based 且可入库，未被测试 approved reviewer 掩盖，未触碰主库。agentB 入口闭环审计通过：用户 compile 入口全部收敛到 StateGraph。redline BLOCKER=0，mvn test=825/0/0。详见 `compile_review_default_llm_mode_fix_result_report.md`、`compile_review_default_llm_mode_runtime_verification_report.md`、`compile_review_entrypoint_loop_coverage_analysis_report.md`。 |
| compile review prompt externalization | 代码实现 + runtime gate + pre-commit 复核通过 | agentA 实现：6 个 prompt 文件 + `CompilerPromptProvider` + DI 接入。agentD 两轮 runtime 验证（发现并修复 DI 注入失败 + shared rules 占位符未生效），最终 runtime gate 通过。agentD pre-commit 复核通过：redline BLOCKER=0，mvn test=824/0/0，无业务硬编码/case 特判/eval 污染。详见 `compile_review_prompt_externalization_pre_commit_quality_report.md`、`compile_review_prompt_externalization_final_runtime_gate_report.md`。 |
| compile review 人工确认后入库 | 代码实现 + runtime 验证 + pre-commit 复核通过 + 已提交 | agentA 实现：后端 publish flow + 后台 list/detail/approve/reject API + 前端入口。agentD runtime 验证通过：后端全链路、approve 向量刷新、前端主流程。agentD pre-commit 复核通过：redline BLOCKER=0，mvn test=844/0/0，无主链误改。已提交（`8fe7001` + `b453627`）。详见 `compile_human_review_queue_pre_commit_quality_report.md`。 |
| 知识库验收 Q6 fact card 路径 | Q6 query/fallback 修复已闭环；整体最小验收仍因 S2 标题/anchor 搜索失败未完全通过 | redline `BLOCKER=0`，`AnswerGenerationServiceTests=77/0/0`，`AnswerFallbackEvidenceSelectorTests + FactCardGenerationServiceTests=27/0/0`，全量 `mvn test=915/0/0`。agentD 已完成真实 API 验证：`spec.containers[0].readinessProbe.tcpSocket.port = 8080`，`periodSeconds=10` 未被抢占，endpoint / URL / image / version / ordinary numeric 字段保护场景均通过。 |
| 知识库验收 S2 chunk/anchor identity | 代码层修复 + 待 agentD 端到端验收 | agentA 完成 chunk identity 最小修复：article chunk FTS / chunk vector 保留 chunk 级身份，RRF 不再把带 `chunkIdentity` 的 chunk hit 与整篇 article hit 按 articleKey 折叠；展示标题通用保留 section anchor。redline `BLOCKER=0`，定向测试 `13/0/0`，全量 `mvn test=921/0/0`。 |
| fresh eval 2 | 未通过；正式基线仍以 `acceptance-report.md` 为准 | 结构化 terminal value 题 `FQ3/FQ4/FQ6/FG1/FG2` 三轮 query fallback 实验后仍 `0/5 PASS`；FACT_CARD 已召回但整卡 evidence unit 粒度导致 sibling 抢答。下一步进入 terminal unit 第一阶段：生成 terminal units 并进入 FTS 检索。 |

## 多 Agent 当前职责

| Agent | 职责 | 当前状态 | 是否允许改代码 |
|---|---|---|---|
| agentA | 单一代码修复执行者 | 下一轮若进入 fresh eval 2 修复，只做 terminal unit 第一阶段：生成 terminal units 并进入 FTS 检索；禁止继续叠 query fallback gate | 是，但同一轮只能有一个 agentA 改主链 |
| agentB | 治理/链路分析 | 可在 terminal unit 修复后只读定位 unit 生成、FTS 召回、RRF 身份或 citation binding 失效点；不得与 agentA 并行改主链 | 否 |
| agentC | 项目进度台账与文档治理 | 已同步 fresh eval 2 最新失败结论、terminal unit 方向与提交计划验证码 | 否，除文档/报告 |
| agentD | 验证/测试 | terminal unit 第一阶段完成后，先验证 `FQ3/FQ4/FQ6/FG1/FG2` 是否命中目标 unit；通过后再跑完整 Public Eval 2 与 Q6/S2 保护回归。 | 否，除验证报告 |

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
- LLM reviewer fail-closed 安全底座已完成：`review-enabled=true` 时 LLM 异常或解析失败返回非 pass（`TIMEOUT_FALLBACK`/`PARSE_FAILED`），不再静默回退到 rule-based pass。`review-enabled=false` 仍为 rule-based，行为不变。当前未启用 LLM reviewer。
- fail-closed 依赖现有两道门禁兜底：persist gate 阻止 `needs_human_review` 入库，query visibility hard filter 阻止非 `passed/ACTIVE` 被查询。fail-closed 本身不新增门禁。
- LLM reviewer 小流量复验通过：在测试库使用 `compile.reviewer.baseline-gpt-5-5-chat` 成功调用 LLM reviewer，JSON 可解析，approved→passed→persist 全链路完整，后台可观测性正常。writer 路由也使用测试库 binding，与 reviewer 一致。
- 当前仍未生产默认启用 LLM reviewer。小流量复验仅在测试库通过环境变量临时启用，验证后已恢复默认关闭。
- LLM reviewer 全链路 compile review 治理体系已就绪：fail-closed 安全底座 → persist gate → query visibility hard filter，三道防线全部验证通过。小流量复验确认正向路径（LLM approved→passed→persist）可行，fail-closed 路径在前序轮次已独立验证。
- 默认 LLM 模式代码实现已完成：新 compile job 默认 reviewMode 为 `LLM`（不再依赖 `review-enabled` 环境变量作为主决策），显式 `RULE_BASED` 仍可用。schema 默认值、MyBatis 初始化 SQL、retry 路径均正确保留 job 级 reviewMode。
- 默认 LLM 模式 runtime 验证通过：默认不传 reviewMode 的真实 compile job 走 LLM reviewer（route=anthropic），LLM non-pass 不入库（persistedCount=0），显式 RULE_BASED 仍走 rule-based 且可入库。未被测试 approved reviewer 掩盖。未触碰生产主库。
- 用户可触发 compile 入口已全部收敛到 `CompileJobService → StateGraphCompileOrchestrator`，覆盖 Writer→Reviewer→Fixer→Reviewer→Persist gate 闭环。旧式 direct compile 仍存在但无用户入口调用，需后续封存审计。
- LLM approved 正向 canary 未在 runtime 验证中自然触发（Reviewer 为严格 fail-closed 模式），Fixer→Re-reviewer loop 也未触发（Reviewer 未标记 fixable issue）。这两项需要在正式 rollout 后用真实高质量文档观察。
- per-job reviewMode 实现已完成：支持 job 级 `LLM` / `RULE_BASED` 选择，不受全局 `review-enabled` 覆盖。详见 `compile_review_per_job_review_mode_fix_result_report.md`。
- compile review prompt externalization 已完成：Writer / Reviewer / Fixer system prompt 已从 `LatticePrompts.java` 硬编码常量外置到 6 个 `.md` 文件，由 `CompilerPromptProvider` 统一加载。`LatticePrompts.java` 存量常量保持不变，始终保持向后兼容。旧常量仍在 `ArticleReviewerGateway` 和 `ReviewFixService` 的 null-provider fallback 路径中被引用，属于安全兜底，不构成双轨风险。
- prompt externalization 期间修复两轮回归：SchemaAwarePrompts 多构造器无 `@Autowired` 导致 Spring DI 失败（1 行修复）；4 个 role prompt 文件 shared grounding rules 内联重复而非常量引用 `{{shared-grounding-rules}}` 占位符（4 文件替换 + 测试补强）。
- prompt 文件红线扫描通过：6 个 prompt 文件中 `expected` 关键词命中均为通用证据约束语境，无业务特判。
- pre-commit 质量复核通过：redline BLOCKER=0，mvn test=824/0/0，未发现业务硬编码/case 特判/eval 污染，建议提交。
- compile review 人工确认后入库链路已完成并提交：`needs_human_review` 编译草稿持久化到 `compile_article_review_queue`，后台 API list/detail/approve/reject 完整，approve 以 `review_status=passed` + `lifecycle=ACTIVE` 写入正式表并重建 chunk/vector，reject 不入库。前端"待人工确认"入口已联通。pre-commit 复核通过：redline BLOCKER=0，mvn test=844/0/0。
- 已知非阻断遗留问题：（1）状态摘要未接 `compile_article_review_queue`，人工确认队列计数暂不反映在 Dashboard 摘要；（2）草稿正文 frontmatter 在队列详情中可见，后续可考虑隐藏；（3）`reviewRoute`/`reviewerModel` 展示可能不准确（取第一条 job step 路由而非 review step）；（4）审查/修复轮次展示仍待做。
- Q6 terminal field alias 配置化修复已由 agentD 端到端验证闭环：redline `BLOCKER=0`、全量 `mvn test=915/0/0`，Query Java 主链未见中文字段语义硬编码 / Q6 特判 / 端口值特判 / Kubernetes 特判；真实 API 返回 `spec.containers[0].readinessProbe.tcpSocket.port = 8080`，citation 能支撑该目标字段事实，`periodSeconds=10` sibling 未被抢占，endpoint / URL / image / version / ordinary numeric 字段保护场景均通过。
- Q6 query/fallback 修复已闭环；整体最小验收仍因 S2 标题/anchor 搜索失败未完全通过，Q1-Q12 为 `12/12 PASS`，S1-S4 为 `3/4 PASS`。S2 `下一步计划` 失败属于独立标题/anchor 搜索链路问题，不能归因到 Q6 terminal field alias。
- S2 标题/anchor 搜索已完成代码层身份修复：article chunk FTS / chunk vector 命中现在保留 `chunkIdentity`，RRF 不再与整篇 article hit 按 articleKey 无条件折叠；搜索结果 title/metadata 可通用展示 section anchor。当前仅完成 redline、定向测试、全量 Maven，真实 API 排序与展示仍待 agentD 端到端验证。
- `citation_coverage=1.0` 仍不能单独替代 Citation Accuracy；Q6 这次之所以成立，是因为 answer claim、source 文件和人工核验三者一致。
- fresh eval 2 未通过，且三轮 query fallback 方向实验均失败；`FQ3/FQ4/FQ6/FG1/FG2` 当前仍是 `0/5 PASS`。
- fresh eval 2 当前不再归因为 retrieval 缺失：目标 FACT_CARD 已召回并包含 terminal assignment，真正缺口是整卡 evidence unit 粒度过粗，导致 sibling 字段抢答与 citation 边界不稳定。
- structured terminal unit 的 `fieldLabel`、`fieldAliases`、`fieldDescription` 只能来自源文件内容与通用结构规则，不得来自 public/hidden 题集、标准答案、expected citation、case id 或 query 日志；hidden eval 仍不得被 AI 读取。
- 长期路线已确立（2026-05-28 agentC）：5 套 public eval + 1-2 套 hidden eval 闭环；短期 terminal unit Phase 1A（evidence 粒度建设），中期 Query 主链复杂度治理（独立线），长期 5+2 eval 持续泛化验收。详见 `docs/test/knowledge-base-e2e/eval-validation-roadmap.md`。
- Query 复杂度治理必须独立开线，不与 terminal unit Phase 1A 并行改代码；两条线串行执行，治理线待 Phase 1A agentD 验收通过后单开。

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
| `review-enabled=true` 时 LLM 异常不能静默 fallback 到 rule-based pass | 旧 `ArticleReviewerGateway` 在 `catch(RuntimeException)` 中调用 `ruleBasedArticleReviewer.review()`，LLM 调用失败时静默获得 pass，绕过 LLM 内容审查 | rule-based 可以作为 disabled 模式或人工诊断兜底，但不能替代 LLM 内容审查通过；否则"启用 LLM reviewer"在 LLM 出问题时自动降级为全通过，等于关了审查 | LLM 异常/超时/解析失败必须 fail-closed 到非 pass（如 `needs_human_review`），已修复。后续新增任何 LLM 调用点都必须检查 catch 路径是否静默 pass。 |
| fail-closed 依赖现有 gate 兜底，不新增门禁 | `TIMEOUT_FALLBACK` 和 `PARSE_FAILED` 进入 `needs_human_review` 后，依赖 persist gate 阻止入库、query visibility hard filter 阻止查询 | fail-closed 本身不创建新的 gate，完全依赖 persist gate 和 query visibility gate | 后续任何时候若修改 persist gate 或 query visibility gate 的语义，必须同步验证 fail-closed 路径的兜底仍然有效。 |
| 默认 LLM 模式后非 reviewer 测试变成 LLM 可用性测试 | 将新 job 默认 reviewMode 改为 `LLM` 后，API / query / management / upload 等集成测试因不传 reviewMode 也开始走 LLM reviewer，部分测试因 LLM 不可用而失败 | 修改全局默认值会影响所有不显式传参的测试，把非 reviewer 目标的测试变成隐式 LLM 依赖 | 引入测试专用 approved reviewer（`ApprovedArticleReviewerTestConfiguration`），让非 reviewer 目标的集成测试在默认 LLM 下仍验证原本业务目标。后续任何修改全局默认行为的改动，必须同步检查是否有测试被意外改变了测试目标。 |
| 旧式 direct compile 路径绕过 StateGraph 闭环 | `CompilePipelineService` 和 `IncrementalCompileService` 中仍存在 `CompileArticleNode.compile(...)` 直接调用，不经过 StateGraph 的二次 reviewer 和 `PersistArticlesNode` gate | 这些路径当前无用户入口调用，但如果未来被重新接入 controller / facade，会产生治理绕过 | 当前不阻塞默认 LLM 模式上线；建议下一轮做最小封存审计，防止旧路径被误作为生产入口。 |
| prompt 外置后存量 Java 常量与外部文件双轨并存 | `LatticePrompts.java` 常量未删除，`ArticleReviewerGateway` 和 `ReviewFixService` 在 null-provider fallback 路径中仍引用旧常量 | 这是有意保留的向后兼容兜底——provider 为 null 时回退到旧常量（测试/手工构造路径） | 当前属于安全兜底，不构成双轨风险。后续若彻底删除旧常量，必须确保所有构造路径都传入 provider。 |
| prompt 外置后 shared rules 占位符未生效 | 初始外置时 4 个 role prompt 文件（writer/reviewer 各 text+image）直接内联了 shared grounding rules 全文，未使用 `{{shared-grounding-rules}}` 占位符 | shared-grounding-rules.md 成为死配置；内联重复导致 prompt 维护分散 | shared rules 修复：4 个文件内联替换为 `{{shared-grounding-rules}}`，测试补强断言占位符已解析且无未解析 `{{`。后续外置任何含共享片段的 prompt，必须验证占位符替换机制已生效。 |
| Spring 多构造器无 `@Autowired` 导致 BeanCreationException | `SchemaAwarePrompts` 新增双参数构造器后，两个构造器均无 `@Autowired`，Spring 无法确定 DI 使用哪个构造器 | 多构造器 Bean 必须显式标注 DI 入口 | 新增构造器时，若类已有其他构造器，必须加 `@Autowired` 标注 DI 目标构造器。 |
| 人工确认 approve 后向量索引未刷新 | approve 后 article 写入 articles 表但 `article_chunks` 未重建、向量索引未刷新，导致 query 无法召回 | 人工 approve 路径与 StateGraph persist 路径在 chunk/vector 重建逻辑上不共享代码路径，approve 侧缺失全量 chunk+vector 刷新 | approve 路径已补全 chunk 重建 + `SearchEngineMaintainer.refreshVectorIndex`。后续任何新增"绕开 StateGraph 写入 articles"的路径，必须同步验证 chunk/vector 重建。 |
| Q6 exact path sibling 字段误选问题已闭环 | 真实 API 现在返回 `spec.containers[0].readinessProbe.tcpSocket.port = 8080`，`periodSeconds=10` sibling 未再抢占；endpoint / URL / image / version / ordinary numeric 保护场景通过 | 这说明 exact path terminal field alias 与通用 leaf key 绑定已经生效，Q6 主链不应再继续叠加规则 | 后续只保留 scoped commit 收口，S2 作为独立标题/anchor 搜索问题分析。 |
| 中文 terminal field alias 必须保持配置化 | 上一轮曾在 Java 中把中文字段语义直接归一到 leaf key，现已迁移到配置 | 中文字段语义必须放在短小、通用、可审计的配置中；Java 主链只允许读取规则和处理英文通用 token | 后续新增中文字段 alias 必须走配置审计，不能在 Java if/else、业务词表、资料词表或题集词表中实现。 |
| query fallback 叠 gate 对 fresh eval 2 无收益 | structured fact terminal binding、selector gate、conclusion gate 三轮服务级复验均为 `0/5 PASS` | 问题不在“再精调 fallback 选行”，而在 FACT_CARD 整卡粒度无法稳定表达单个 terminal assignment | 禁止继续在 query fallback 主链叠加 selector/conclusion/snippet gate；下一步先做 terminal unit 第一阶段，让检索返回单字段证据。 |
| terminal unit alias 来源存在 eval 污染风险 | field label/alias/description 若从题集、答案或 query 日志派生，会把 public/hidden eval 泄漏进索引规则 | alias 必须来自源文件和通用结构规则；hidden eval 只允许记录指标与失败类型 | 生成 terminal unit 时不得读取 hidden eval；不得把题面、case id、expected citation、答案值写入代码、prompt、配置或 SQL。 |
| `compile_article_review_queue` 不区分 compile job | 多次 compile 产生的 `needs_human_review` 草稿混在同一队列，无 jobId 过滤 | 当前接受这种简化——人工确认场景本身就是低频率、逐条处理的 | 后续若需要按 job 维度管理人工确认，需给 `compile_article_review_queue` 增加 `job_id` 字段并支持筛选。 |
| 前端编译进度卡片语义与轮次展示仍有缺口 | 前端进度卡片展示的步骤数、审查轮次、fix 轮次仍不完全反映 StateGraph 实际执行轮数 | 后端步骤和轮次信息已写入 job steps，前端尚未完全接入 | 不阻断当前提交。后续状态摘要和轮次展示迭代时统一接入。 |

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
- 不准为 Q6 文件名、题面、端口值写特判。
- 不准为 Kubernetes / readiness / liveness / tcpSocket 等具体业务字段写生产逻辑特判。
- 不准在 Java 主链硬编码中文字段语义；中文 terminal field alias 必须配置化、短小、可审计。
- Q6 下一轮必须由 agentD 做真实 API 与全面回归，不得只回归 Q6。
- fresh eval 2 禁止继续沿 query fallback 主链叠加 selector / conclusion / snippet gate 追通过。
- structured terminal unit 的 label / alias / description 禁止来自题集、答案、query 日志或 hidden eval。
- 禁止 terminal unit Phase 1A 与 Query 复杂度治理并行改代码；两条线必须串行，治理线待 Phase 1A 验收通过后单开。
- terminal unit 是 evidence 粒度建设，不是 query fallback 补丁；不得在 terminal unit 实现中向 query fallback 主链追加新 gate。
- 禁止无限修题：长期目标是 5+2 eval 闭环（public eval 发现 → 修通用能力 → 回归保护 → hidden eval 验收），不是逐题追 PASS。

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
10. （已完成）compile review LLM reviewer fail-closed 安全底座修复 + 独立验证已完成。详见 `compile_review_llm_reviewer_fail_closed_fix_result_report.md`、`compile_review_llm_reviewer_fail_closed_verification_report.md`。
11. （已完成）compile review LLM reviewer 小流量复验通过：测试库全链路 LLM approved→passed→persist，后台可观测性正常。详见 `compile_review_llm_reviewer_small_flow_reverification_report.md`。
12. （已完成）小范围启用策略设计：已产出 canary 验证与 rollout 策略报告。详见 `compile_review_llm_reviewer_single_source_canary_report.md`、`compile_review_llm_reviewer_rollout_strategy_report.md`。
13. （已完成）compile review 默认 LLM 模式代码实现 + runtime 验证 + 入口闭环审计。详见 `compile_review_default_llm_mode_fix_result_report.md`、`compile_review_default_llm_mode_runtime_verification_report.md`、`compile_review_entrypoint_loop_coverage_analysis_report.md`。
14. （已完成）compile review 默认 LLM 模式 pre-commit quality review 已通过并提交（4864cbc）。
15. （后续）LLM approved 正向 canary：当前 Reviewer 为严格 fail-closed 模式，LLM approved 未自然触发；正式 rollout 后用真实高质量文档观察 approved 率。
16. （后续）Fixer→Re-reviewer loop runtime 验证：当前未触发 fixable issue 路径；后续设计专门测试 case 或降低 Reviewer fixable 阈值后验证。
17. （后续）legacy direct compile 封存审计：`CompilePipelineService` / `IncrementalCompileService` 中旧式 direct compile 路径需做最小可达性防护。
18. （已完成）prompt 文件化：Writer/Reviewer/Fixer prompt 已从 `LatticePrompts.java` 外置到 `src/main/resources/prompts/compiler/*.md`，pre-commit 复核通过。详见 `compile_review_prompt_externalization_pre_commit_quality_report.md`。
19. （已完成）prompt externalization 代码 + 锚点报告已提交（576531f）。
20. （已完成）compile review 人工确认后入库链路已完成并提交（8fe7001 + b453627）。详见 `compile_human_review_queue_pre_commit_quality_report.md`。
21. （已完成）Q6 Answer deterministic fallback 通用修复：基于 `q6_end_to_end_verification_report.md` 处理“正确 fact card 已召回但 fallback 选错行”的证据选择/grounding 问题，禁止 case 特判。详见 `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_fix_result_report.md`。
22. （已完成，FAIL）Q6 agentD 首轮端到端复验：redline 与测试通过，完整资料导入并确认 2 条人工队列后，真实 API 仍回答 `image` 行；结论为 FAIL。详见 `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_verification_report.md`。
23. （已完成）Q6 fallback 二次根因分析：agentB 定位 path shape gate 未优先消费 question-focused structured path value candidate。详见 `docs/test/knowledge-base-e2e/q6_fallback_second_root_cause_analysis_report.md`。
24. （已完成）Q6 path shape gate 最小修复：agentA 仅修改 `AnswerGenerationFallbackSnippetSelectionSupport.addBestCandidateForRequiredShape` 与相关测试。详见 `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_fix_result_report.md`。
25. （已完成，FAIL）Q6 agentD path shape gate 端到端复验：redline、定向测试、全量测试通过，服务确认加载最新 class，当前 Q6 clean 库可复用；真实 API 仍回答 `image` 行，citation 只支撑错误 claim，结论为 FAIL。详见 `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_verification_report.md`。
26. （已完成）Q6 fallback runtime trace 只读归因：agentD 确认最终 `fallbackHits` 为 `SOURCE + ARTICLE`，fact card 9 未进入最终 fallback markdown；唯一 runtime gate 为 `selectComplementaryEvidenceByQuestionTokens` early return。详见 `docs/test/knowledge-base-e2e/q6_fallback_runtime_trace_analysis_report.md`。
27. （已完成）Q6 complementary evidence gate 修复：只处理 `AnswerFallbackEvidenceSelector.selectComplementaryEvidenceByQuestionTokens` 一个最小变量，让高分 question-focused structured fact / path-aware fact card 不被 `SOURCE + ARTICLE` early return 屏蔽；redline、定向测试、全量 `mvn test` 通过。详见 `docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_fix_result_report.md`。
28. （已完成，FAIL）Q6 complementary evidence gate agentD 端到端复验：真实链路已改变，fact card 已进入最终 fallback evidence，原机器标识符误答消失；但最终答案误选同一 fact card 内 sibling 字段，Answer Accuracy 仍 FAIL。详见 `docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_verification_report.md`。
29. （已完成）Q6 exact path sibling 字段误选处理及 terminal field alias 配置化红线修正：agentD 已完成端到端验证，Q6 query/fallback 修复闭环；redline `BLOCKER=0`，全量 `mvn test=915/0/0`，真实 API 返回 `spec.containers[0].readinessProbe.tcpSocket.port = 8080`，`periodSeconds=10` 未被抢占。详见 `docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_fix_result_report.md`。
30. （已完成）Q6 terminal field alias scoped commit 已提交（4d5e8bc），不再扩大 Q6/fallback 主链。
31. （已完成）agentC 剩余文档收口审计：已输出 `docs/test/remaining_docs_reports_commit_plan.md`，判定 5 组建议提交、2 组不建议提交、2 组永远排除、1 组因真实 API 密钥阻塞。详见该报告。

生产代码 scoped commits 已全部收口（item 30 + 已提交 commit 清单）。剩余未提交项主要是 docs/report 归档（见 item 31 审计报告）、私有配置（`docs/模型绑定配置参考.md`，永远排除提交）与 redline 输出（`special_cases_report.md`，不建议提交）。S2 标题/anchor 搜索已完成只读归因与代码层修复，后续应由 agentD 做完整知识库端到端验收。

32. （已完成）S2 标题/anchor 搜索问题独立分析：agentB 单独排查 `下一步计划` 的标题/anchor 命中链路，确认不归因到 Q6 terminal field alias。详见 `docs/test/knowledge-base-e2e/s2_title_anchor_search_root_cause_analysis_report.md`。
33. （已完成，待验收）S2 chunk/anchor identity 最小通用修复：agentA 保留 chunk 级 identity，避免 article chunk FTS / chunk vector 命中被整篇 article 折叠；redline `BLOCKER=0`，定向测试 `13/0/0`，全量 `mvn test=921/0/0`。详见 `docs/test/knowledge-base-e2e/s2_chunk_anchor_identity_fix_result_report.md`。
34. （后续）S2 agentD 完整知识库端到端验收：清库/重建/导入如由 agentD 判断需要后执行，至少回归 Q1-Q12、S1-S4、S2 搜索展示与 Q6 保护场景。
35. （已完成，FAIL）fresh eval 2 首轮正式验收：正式基线以 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md` 为准，Answer Accuracy `10/15`，Search Accuracy `1/4`，Recall@10 `13/15`，Citation Accuracy `2/15`，结论为未通过。
36. （已完成，FAIL）fresh eval 2 structured fact terminal binding 实验：工程门禁通过，但服务级 `FQ3/FQ4/FQ6/FG1/FG2 = 0/5 PASS`，不建议提交对应代码。
37. （已完成，FAIL）fresh eval 2 selector gate 实验：FACT_CARD 仍未进入最终 answer/citation，服务级 `0/5 PASS`，不建议提交对应代码。
38. （已完成，FAIL）fresh eval 2 conclusion gate 实验：FACT_CARD 已召回但仍选错 sibling，服务级 `0/5 PASS`，不建议提交对应代码。
39. （已完成）fresh eval 2 terminal unit 设计：已明确下一步转向 compile/index 层 structured terminal assignment evidence unit materialization；第一阶段先生成 terminal units 并进入 FTS 检索，禁止继续叠 query fallback gate。详见 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md`。
40. （后续）fresh eval 2 terminal unit 第一阶段实现：展开 FACT_ENUM / key_value_list / path-aware items 为 terminal units，接入 FTS，使用 unit identity 避免 sibling 折叠；field label/alias/description 只能来自源文件与通用结构规则。
41. （后续）terminal unit agentD 验证：先验证 `FQ3/FQ4/FQ6/FG1/FG2` 是否命中目标 unit 且 answer claim 命中；通过后再跑完整 Public Eval 2、Q6 terminal field alias 保护和 S2 chunk/anchor identity 保护。
42. （后续，独立线）Query 主链复杂度治理：terminal unit Phase 1A 验收通过后单开一轮，降低 AnswerGeneration 继承链深度，治理 `.contains()` 规则分流，冻结 query fallback 主链不再接受新 gate 式补丁。此线不与 terminal unit Phase 1A 并行改代码。详见 `docs/test/knowledge-base-e2e/eval-validation-roadmap.md`。
43. （后续）状态摘要接入人工确认队列：Dashboard 摘要展示 `compile_article_review_queue` 待处理计数。
44. （后续）SWIP 两文档重建验收：验证 clean rebuild 全链路在人工确认队列就位后的正确性。
45. （后续）审查/修复轮次展示：前端进度卡片接入 StateGraph 实际执行轮数。
46. （后续）LLM approved 正向 canary 观察。
47. （后续）Fixer→Re-reviewer loop runtime 验证。

## 更新规则

- 每次质量打磨、Query/SWIP eval、baseline 修复或多 agent 并行前，先读本文件。
- 每轮阶段性结论变化后，更新本文件。
- 当前 gate、下一步计划、踩坑结论、agent 分工发生变化时，必须同步更新本文件。
- 代码修复失败、回退、负收益也必须记录。
- 不允许只在聊天里说明而不更新台账。
- 本文件是质量打磨阶段的进度台账，不替代用户指定的计划文件；如果用户指定 `docs/**/plans/*.md`，仍以计划文件为唯一进度台账并随做随回写。
