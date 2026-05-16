# Compile Review 治理下一步落地报告

## 1. 结论

下一轮最小代码修复建议：**先做方案 A 的后台可观测性补强，只展示 review route / review status / review counts，不改变编译、审查、落库或 query 行为。**

理由：

- 用户当前核心疑问是“草稿是不是直接入库、审查到底有没有发生”。这个疑问首先来自后台语义不可见，而不是最近数据已经污染 query。
- 最近 job 的 draft 没有直接写入 `articles`，而是先写 working set；`review_articles` 确实执行，但 route 是 `rule-based`，不是 LLM reviewer。
- 当前 query 可见性和 LLM reviewer 都需要治理，但它们是行为变更，风险大于展示补强；尤其当前工作区还有 Query/SWIP 主链改动，下一轮不宜再混入 query 过滤或 LLM 编译验收变量。
- 后台展示补强可复用现有 `compile_job_steps`，不用重新 compile、清库或改模型配置；能最小成本消除“审查成功 = LLM 审查”的误解。

## 2. Redline / 工作区

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1852 |
| ALLOWLIST | 238 |

执行命令：`bash scripts/scan-redline.sh special_cases_report.md`

当前工作区有其他 agent 已产生的 Query / 报告 / cleanup 变更，本轮只读分析未修改这些内容，也未回退：

| 类型 | 现状 |
|---|---|
| 生产代码既有改动 | `AnswerGenerationPayloadOrchestrator.java`、`AnswerGenerationPromptEvidenceSupport.java` |
| 文档 / 报告既有改动 | `docs/quality-progress-and-lessons.md`、若干 SWIP 报告新增/删除 |
| 本轮允许变更 | 新增本报告；redline 刷新 `special_cases_report.md` |
| 本轮是否修改源码 | 否 |

## 3. “草稿是否直接入库”的准确解释

| 问题 | 判断 |
|---|---|
| draft 是否直接进入 `articles` | 当前 `state_graph` 主链下：否。draft article 先保存到 working set 的 `draftArticlesRef`。 |
| review step 是否执行 | 是。最近 job 有 `review_articles` step，状态 `succeeded`。 |
| review route 是什么 | `rule-based`，不是 LLM reviewer。 |
| 为什么不是 LLM review | `lattice.llm.review-enabled=${LATTICE_LLM_REVIEW_ENABLED:false}`，当前 shell 未设置 `LATTICE_LLM_REVIEW_ENABLED`；`ArticleReviewerGateway` 因开关关闭直接走 `RuleBasedArticleReviewer`。 |
| fix step 为什么没发生 | `review_articles` 规则审查全部通过，`acceptedCount=4`、`needsHumanReviewCount=0`、无 fixable，因此路由直接到 `persist_articles`。 |
| 最近是否发现 draft 对 query 可见 | 未发现。当前落库文章均为 `review_status=passed`、`lifecycle=ACTIVE`。 |

对用户的产品口径建议：

> 不是“生成草稿后直接写入 articles 表”。最近这次是生成 draft 后进入 review step，再把通过审查的文章落库。但这次 review 是规则审查，不是 LLM 内容审查；因为规则审查全通过，所以没有进入自动修复步骤。

## 4. 当前运行态核验

最近 compile job：

| 项 | 值 |
|---|---|
| job_id | `fc155a9b-54f2-41af-87cc-0498c88521b9` |
| source_id | `1` |
| status | `SUCCEEDED` |
| orchestration_mode | `state_graph` |
| persisted_count | `4` |
| requested_at | `2026-05-15 11:39:50.436416+00` |
| finished_at | `2026-05-15 11:53:48.947624+00` |

关键步骤：

| seq | step | agent_role | model_route | status | 结果 |
|---:|---|---|---|---|---|
| 10 | `compile_new_articles` | `WriterAgent` | `compile.writer.gpt-5.5` | `succeeded` | 生成 4 篇 draft |
| 11 | `review_articles` | `ReviewerAgent` | `rule-based` | `succeeded` | `acceptedCount=4`、`needsHumanReviewCount=0` |
| - | `fix_review_issues` | - | - | 未出现 | 无 fixable |
| 12 | `persist_articles` | - | - | `succeeded` | `persistedCount=4` |
| 13 | `rebuild_article_chunks` | - | - | `succeeded` | 19 个 chunk |
| 14 | `refresh_vector_index` | - | - | `succeeded` | 文章/分块向量刷新 |
| 15 | `generate_synthesis_artifacts` | - | - | `succeeded` | fact cards 等综合产物 |

当前数据状态：

| 对象 | 当前状态 |
|---|---|
| `articles` | `passed / ACTIVE = 4` |
| `article_chunks` | 关联 `passed / ACTIVE = 19` |
| `article_vector_index` | 关联 `passed / ACTIVE = 4` |
| `article_chunk_vector_index` | 关联 `passed / ACTIVE = 19` |
| `fact_cards` | `low_confidence = 14` |
| `article_review_audits` | 0 |
| `compile_review_settings` | 0，当前无 DB 覆盖配置 |

## 5. 当前审查是不是 LLM 审查

明确判断：**不是。**

| 检查项 | 结论 |
|---|---|
| `lattice.llm.review-enabled` 默认 | `false` |
| 当前环境变量 | 未见 `LATTICE_LLM_REVIEW_ENABLED` |
| `ArticleReviewerGateway` 行为 | 开关关闭时直接返回 `RuleBasedArticleReviewer.review(...)` |
| `AgentModelRouter` 行为 | reviewer 开关关闭时 route label 返回 `rule-based` |
| 最近 step route | `review_articles model_route=rule-based` |
| 模型绑定是否缺失 | 否。已有 compile reviewer/fixer 绑定，但未被调用。 |

Rule-based review 的语义只是结构兜底，主要检查空文章、frontmatter、`sources:`、`review_status:`、TODO/TBD、一级标题、源正文是否为空。它不检查引用真实性、事实完整性、精确值、推断性编造或多源冲突。

因此后台如果只写“质量检查完成”或“审查通过”，会让用户误以为这是 LLM 内容审查。

## 6. 治理缺口排序

| 优先级 | 缺口 | 当前风险 | 建议 |
|---:|---|---|---|
| P0 | 后台展示 review route / review status | 误导风险最高：用户看不到 rule-based / LLM 差异，也看不到 fix 为什么没发生 | 下一轮先做 |
| P1 | query 侧可见性过滤 | 如果未来非 `passed` / 非 `ACTIVE` article 被落库并建索引，当前检索入口可能召回 | 当前不混入下一轮，待 Query 分支稳定后单独修 |
| P2 | 启用 LLM reviewer | 产品承诺“审查后入库”时必须启用；否则只能叫规则审查 | 需要产品/成本/验收确认 |
| P3 | `allowPersistNeedsHumanReview` 有效默认 | Java 字段默认 false，但 `lattice-compiler.yml` 默认 `${...:true}`；DB 当前无覆盖，存在 needs_human_review 可落库路径 | 需产品确认复核稿是否允许落库 |
| P4 | 自动 review/fix per-article 审计 | 当前 `compile_job_steps` 有 step 摘要，但 `article_review_audits` 不记录自动 review/fix | 后续增强，不作为第一轮 |

说明：P0 是第一轮最小落地点，不代表 P1/P2 不重要。P1/P2 是行为治理，应该在展示补强后分开做，避免一次改动同时改变后台、编译、检索三条主链。

## 7. 方案优先级与风险

| 方案 | 优先级 | 价值 | 风险 | 本轮判断 |
|---|---:|---|---|---|
| A：只增强后台展示，不改行为 | 1 | 最小成本解释真实链路；可立即展示 `rule-based` 与 `acceptedCount` | 不降低 query 暴露风险，不等于真实内容审查 | 下一轮首选 |
| D：query 侧过滤 `passed + ACTIVE` | 2 | 建立硬可见性门禁，防止非审查通过内容进入问答证据面 | 会影响所有 article/chunk/vector 召回，可能改变 Query/SWIP 指标 | 单独主线，不与 A 混修 |
| B：启用 LLM reviewer | 3 | 才能支撑“LLM 内容审查后入库”的产品承诺 | 成本、耗时、模型失败 fallback、compile 验收都要重新确认 | 产品确认后再做 |
| E：`needs_human_review` 落库但 `query_visible=false` | 4 | 兼容后台复核稿落库和 query 隔离 | 需要字段/迁移/索引/后台联动，范围大 | 不是第一轮 |

如果产品明确要求“下一次验收必须是真实 LLM 审查”，则 B 的业务优先级可提前；但仍建议先做 A，否则即使启用 LLM，后台仍难以解释 route、fallback、fix 轮次。

## 8. 下一轮唯一最小代码修复建议

建议修复点：**后台 Compile / Processing Task 展示层增加 review route 与 review outcome 摘要。**

最小范围建议：

| 范围 | 说明 |
|---|---|
| 允许文件范围 | `src/main/java/com/xbk/lattice/api/admin/**`、`src/main/java/com/xbk/lattice/admin/service/**` |
| 可读取仓储 | 复用 `CompileJobStepJdbcRepository.findByJobId(...)` |
| 不改范围 | 不改 compiler graph、不改 reviewer、不改 query SQL、不改配置默认值、不改数据库 schema |
| 展示数据来源 | `compile_job_steps.step_name='review_articles'` 的 `agent_role`、`model_route`、`summary`、`output_summary` |
| 最小展示内容 | `reviewRoute=rule-based/compile.reviewer.*`、`acceptedCount`、`pendingReviewCount`、`needsHumanReviewCount`、`fixAttemptCount`；如无 `fix_review_issues`，显示“未触发自动修复：无 fixable issue” |

验收口径：

- 读取最近 job 不需要重新 compile。
- 后台响应能明确展示 `review_articles / ReviewerAgent / rule-based / acceptedCount=4 / needsHumanReviewCount=0`。
- 页面或 API 文案不能把 `rule-based` 说成 LLM 审查。
- redline 仍为 `BLOCKER=0`。

## 9. 需要用户确认的产品问题

| 问题 | 为什么要确认 |
|---|---|
| rule-based 是否可作为临时 review | 如果可以，后台必须标明“规则审查”；如果不可以，下一步应启用 LLM reviewer 或阻断落库。 |
| `low_confidence` fact card 是否可被 Query 使用 | 当前 14 张 fact card 全部是 `low_confidence`，并且 mapper 无 SQL 硬过滤；这是弱证据策略还是不可见内容，需要产品定口径。 |
| `needs_human_review` 是否允许落库 | 当前 YAML 默认允许；若允许落库，必须定义 query 不可见或发布态隔离。 |
| 真实验收是否默认启用 LLM reviewer | 若产品承诺“审查后入库”，验收环境应设置 `LATTICE_LLM_REVIEW_ENABLED=true` 并验证 route。 |
| LLM reviewer 失败时是否允许 rule-based fallback pass | 当前 `ArticleReviewerGateway` catch RuntimeException 后走 rule-based；产品需要确认失败时是降级、阻断还是转人工复核。 |

## 10. 下一轮禁止事项

- 不准一轮同时做后台展示、LLM 开关、query 过滤和 schema 变更。
- 不准把 `rule-based` 文案写成“LLM 审查通过”。
- 不准运行 compile、清库、重建或重新导入，除非下一轮任务明确要求验证。
- 不准修改 Query/SWIP answer 主链来夹带 compile review 治理。
- 不准改 redline 脚本、allowlist、题集或模型文档。
- 不准用特定资料、文件名、业务词或样例问题做特判。

## 11. 本轮变更声明

| 项 | 结果 |
|---|---|
| 是否修改源码 | 否 |
| 是否修改测试 | 否 |
| 是否修改配置 | 否 |
| 是否修改脚本 | 否 |
| 是否修改数据库 | 否 |
| 是否运行 compile | 否 |
| 是否清库 / 重建 / 重新导入 | 否 |
| 是否提交代码 | 否 |
| 本轮报告 | `compile_review_governance_next_steps_report.md` |
