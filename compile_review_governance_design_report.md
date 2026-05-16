# Compile Review 治理设计分析报告

## 结论

当前没有发现最近一次编译产生的 draft article 直接进入 query 可见面的实际样本；最近 job 的 4 篇文章均为 `review_status=passed`、`lifecycle=ACTIVE`，对应 article chunk / article vector / chunk vector 也都只关联 `passed` 文章。

但当前治理口径不够强：

| 判断项 | 结论 |
|---|---|
| 当前 rule-based review 是否符合产品预期 | 只适合作为 LLM reviewer 关闭或失败时的技术兜底，不适合作为“内容已被真实审查”的产品口径 |
| 是否需要启用 LLM reviewer | 需要。若产品承诺“生成后审查再入库”，应启用 `lattice.llm.review-enabled=true`，并把 LLM review 作为入库前主质量门 |
| 是否需要后台展示 review route / review status | 需要。后台应明确展示 `rule-based` / LLM route、review status、accepted/fixable/needs_human_review 数量与 fix 轮次 |
| 是否需要 query 侧过滤 `review_status=passed` / `lifecycle=ACTIVE` | 需要。仅依赖 compile 侧不落 pending 不够稳，query 检索入口应显式过滤可见性 |
| 当前是否应允许 `needs_human_review` 落库后被 query 召回 | 不建议。若为人工复核工作流需要落库，也应默认 query 不可见 |
| fact card `low_confidence` 是否可见 | 当前 lexical 可召回并降权；这是“弱证据可见”设计，不是严格审查通过设计 |

推荐治理基线：**方案 B + 方案 D**。

- B：启用 LLM reviewer，文章入库前必须经过 LLM review；只有 `passed` 进入 query 可见面。
- D：query 侧统一加可见性门禁：article / chunk / vector 只读 `review_status=passed AND lifecycle=ACTIVE`，fact card 至少排除 `conflict` / `needs_human_review`，是否保留 `low_confidence` 作为背景证据需产品确认。

方案 A 可作为短期过渡：保持 rule-based 默认，但必须在后台明确展示“规则审查通过”，并提示这不是 LLM 内容审查。

## 本轮边界

| 项 | 结果 |
|---|---|
| 是否修改源码 | 否 |
| 是否修改配置 | 否 |
| 是否修改数据库 | 否 |
| 是否修改脚本 / 题集 / 模型文档 | 否 |
| 是否运行 compile / query regression / 测试 | 否 |
| 是否只读查询数据库 | 是 |
| 本轮唯一文件变更 | 新增本报告 |

## Review 开关与路由

| 检查项 | 结论 |
|---|---|
| 默认值来源 | `src/main/resources/config/lattice-llm.yml:13`：`lattice.llm.review-enabled=${LATTICE_LLM_REVIEW_ENABLED:false}` |
| Java 属性默认 | `LlmProperties.reviewEnabled=false` |
| 当前 shell 环境变量 | `printenv LATTICE_LLM_REVIEW_ENABLED` 无输出 |
| 当前运行中 Java 进程环境 | 可见 `SPRING_DATASOURCE_URL`、`SPRING_PROFILES_ACTIVE`、`SERVER_PORT`，未见 `LATTICE_LLM_REVIEW_ENABLED` |
| 路由选择原因 | `ArticleReviewerGateway.review(...)` 在 `reviewEnabled=false` 时直接返回 `RuleBasedArticleReviewer.review(...)` |
| step 展示 route 原因 | `AgentModelRouter` 对 reviewer 角色同样在 `reviewEnabled=false` 时返回 `rule-based` |
| 当前绑定是否缺失 | 否。数据库中 compile `writer/reviewer/fixer` 绑定均启用，最近 job 也冻结了 reviewer/fixer 快照 |
| 若启用 LLM reviewer 调用哪个角色 | `scene=compile`、`agentRole=reviewer`，purpose=`compile-review` |
| 当前有效模型绑定 | `compile.reviewer.gpt-5.5`，provider=`openai_compatible`，model=`gpt-5.5` |

关键判断：最近 job 的 `review_articles model_route=rule-based` 不是因为模型绑定缺失，而是因为全局 `lattice.llm.review-enabled=false`。因此“review step 执行成功”不能等同于“LLM reviewer 审查通过”。

## Review 语义

### Rule-based Review 检查项

`RuleBasedArticleReviewer` 只检查最小结构条件：

| 检查 | 问题级别 |
|---|---|
| 文章内容为空 | HIGH |
| frontmatter 缺少 `sources:` | HIGH |
| frontmatter 缺少 `review_status:` | MEDIUM |
| 包含 `TODO` / `TBD` | HIGH |
| 正文缺少一级标题 `# ` | MEDIUM |
| 源文件正文为空 | HIGH |

### Rule-based Review 未检查项

| 未覆盖能力 | 影响 |
|---|---|
| 是否遗漏源文件中的明确性知识 | 不能保证清单、枚举、端口、状态码、配置值完整 |
| citation 是否真实支撑正文 claim | 不能发现虚假引用或引用错位 |
| 数字 / URL / path / route / code 是否与源一致 | 不能发现精确值错误 |
| 是否引入源文件没有的异常分支、状态流转、DB 结论 | 不能阻止推断性编造 |
| 多源冲突、旧值纠正、新旧口径链路 | 不能做语义核查 |
| OCR / 图片类资料可见内容是否被夸大 | 不能做视觉语义核查 |

结论：rule-based review 是“结构有效性检查”，不是“内容真实性审查”。

### LLM Reviewer 预期检查项

`SYSTEM_REVIEW` 的语义是 adversarial reviewer，重点检查：

| 检查 | 目标 |
|---|---|
| Referential completeness | 源文件中的明确性知识是否完整进入文章 |
| Provenance sampling | 抽样验证带来源标记的 claim 是否真实存在 |
| Value accuracy | 数字、端口、阈值、状态等精确值是否一致 |
| Unsupported exact values | 文章是否新增源文件没有的精确值 |
| Speculative abnormal scenarios | 异常场景、失败分支、状态流转是否来自证据而非推断 |

图片 / OCR 文章走 `SYSTEM_REVIEW_IMAGE_ARTICLE`，更偏保守核查 UI / 架构重点、路径真实性、可见值准确性。

### fix_review_issues 触发条件

`ReviewDecisionPolicy` 规则：

| review 结果 | 条件 | 后续 |
|---|---|---|
| `ReviewResult.isPass=true` | 任何轮次 | `review_status=passed`，进入 accepted |
| 有 issues | `autoFixEnabled=true` 且 `fixAttemptCount < maxFixRounds` | `review_status=pending`，进入 `fix_review_issues` |
| 未通过且不可修 / 轮次耗尽 / 无 issues | 其余情况 | `review_status=needs_human_review` |

`fix_review_issues` 调用 `ReviewFixService.applyFix(...)`，使用 `scene=compile`、`agentRole=fixer`、purpose=`review-fix`。修复后再次回到 `review_articles`，由 reviewer 重新审查。

### allowPersistNeedsHumanReview=true 的影响

`PersistArticlesNode` 会先取 accepted articles；如果 `allowPersistNeedsHumanReview=true`，再合并 `needsHumanReviewArticlesRef` 一起落库。

当前没有持久化 `compile_review_settings` 记录，因此运行态来自 properties。YAML 覆盖值为：

| 配置 | 当前默认 |
|---|---|
| `auto-fix-enabled` | `true` |
| `max-fix-rounds` | `1` |
| `allow-persist-needs-human-review` | `true` |

风险：如果未来 LLM reviewer 或 rule-based reviewer 产生 `needs_human_review`，当前默认允许它进入 `articles`。由于 query 侧没有硬过滤，这类文章具备被召回的路径。

## 当前数据状态

只读数据库查询结果：

| 对象 | 当前值 |
|---|---|
| `articles.review_status` | `passed=4` |
| `articles.lifecycle` | `ACTIVE=4` |
| `fact_cards.review_status` | `low_confidence=14` |
| `article_chunks` | 19 条；关联非 `passed` article 为 0 |
| `article_vector_index` | 4 条；关联非 `passed` article 为 0 |
| `article_chunk_vector_index` | 19 条；关联非 `passed` article 为 0 |
| `fact_card_vector_index` | 0 条 |
| `article_review_audits` | 0 条 |
| `compile_job_steps` | 17 条 |
| `compile_review_settings` | 表存在，但当前无配置记录 |

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

最近 job 关键步骤：

| sequence | step | agent_role | model_route | 结果 |
|---:|---|---|---|---|
| 10 | `compile_new_articles` | `WriterAgent` | `compile.writer.gpt-5.5` | draft=4 |
| 11 | `review_articles` | `ReviewerAgent` | `rule-based` | `acceptedCount=4`、`needsHumanReviewCount=0` |
| 12 | `persist_articles` | - | - | `persistedCount=4` |
| 13 | `rebuild_article_chunks` | - | - | 成功 |
| 14 | `refresh_vector_index` | - | - | 成功 |
| 15 | `generate_synthesis_artifacts` | - | - | 成功 |

当前可用的展示 / 审计数据：

| 数据源 | 是否存在 | 当前用途 |
|---|---|---|
| `compile_job_steps` | 有 | 可展示节点、agent_role、model_route、summary、output_summary |
| `execution_llm_snapshots` | 有 | 可展示 job 冻结的 writer/reviewer/fixer 绑定 |
| `article_review_audits` | 表存在但为空 | 主要用于人工复核 approve / request_changes，不记录自动 review/fix |
| 后台文章列表 / 详情 | 有 `reviewStatus` | 可展示文章最终状态 |
| 当前处理任务步骤 | 有压缩版步骤链 | 只展示“生成 / 审查 / 完成”，不展示 route 与每篇 review 结论 |

## Query 可见性风险

### SQL 过滤现状

| 通道 | 当前 SQL 行为 | 是否过滤 `review_status/lifecycle` |
|---|---|---|
| Article FTS | `from articles a where a.search_tsv @@ query.tsq` | 否 |
| RefKey | `from articles a where lower(refkey/title/metadata) like ...` | 否 |
| Article chunk lexical | join `article_chunks` + `articles` 后按 chunk/text/title/concept 匹配 | 否 |
| Article vector | join `article_vector_index` + `articles` 后按 embedding 排序 | 否 |
| Article chunk vector | join `article_chunk_vector_index` + `articles` 后按 embedding 排序 | 否 |
| Fact card lexical | `from fact_cards fc`，按 card 内容匹配 | SQL 不过滤；服务层排除 `conflict`，保留 `low_confidence` |
| Fact card vector | join `fact_card_vector_index` + `fact_cards` 后按 embedding 排序 | SQL 不过滤；服务层排除 `conflict`，保留 `low_confidence` |

### 服务层状态处理

| 对象 | 当前处理 |
|---|---|
| article `passed` | rerank 加分 `+8` |
| article `needs_human_review` | rerank 扣分 `-40`；fallback snippet 再有额外扣分 |
| article 其他非 passed | rerank 扣分 `-12` |
| fact card `conflict` | 不允许作为 query candidate |
| fact card `low_confidence` | 允许作为候选，分数乘 `0.45`，也允许作为 primary evidence |
| fact card `needs_human_review` | 允许作为候选，分数乘 `0.20`，标记 background only |

结论：

- 对 article 而言，当前是“非 passed 降权”，不是“非 passed 不可见”。
- 对 fact card 而言，`low_confidence` 当前可见并可作为结构化主证据；这是产品口径上需要明确承认的弱证据策略。
- 如果未来 `pending` / `needs_human_review` article 被落库并建索引，query 可能召回；能否进入最终答案取决于排序、融合、证据选择，不是硬门禁。

这是产品风险，不建议视为当前可接受的长期设计。原因是 query 可见性应该由显式发布/审查状态控制，而不是依赖“编译侧一般不会落非 passed”或“排序大概率降下去”。

## 治理方案选项

| 方案 | 内容 | 优点 | 风险 / 不足 | 适用口径 |
|---|---|---|---|---|
| A | 保持 rule-based review 默认，但后台明确展示“规则审查通过”，并增加说明 | 成本低、稳定、速度快 | 不能承诺内容真实性审查；仍可能把结构合法但内容错误的文章标为 `passed` | 临时过渡 / 无模型环境 |
| B | 启用 LLM reviewer，所有文章入库前必须经过 reviewer 审查，未通过则 fix 后复审，仍不通过进入人工复核 | 符合“审查后入库”的产品直觉；能检查遗漏、引用、精确值、推断 | 成本、耗时、模型稳定性要求更高；需要清晰展示 fallback | 推荐主方案 |
| C | 后台只增强展示 route/status，不改变 review 或 query 策略 | 快速消除“是不是直接入库”的误解 | 只提升可观测性，不降低 query 暴露风险 | 必做补强，但不能单独作为治理方案 |
| D | query 侧显式过滤 `review_status=passed AND lifecycle=ACTIVE`；fact card 按产品策略过滤或降级 | 防止非审查通过内容进入问答证据面 | 可能短期降低召回；需明确人工复核区与 query 可见区边界 | 推荐与 B 组合 |
| E | 允许 `needs_human_review` 落库，但新增 `query_visible=false` 或发布状态字段 | 支持后台人工复核工作流，同时隔离 query | 需要字段 / 索引 / 后台展示配套 | 如果仍要保留复核草稿落库，建议采用 |

## 推荐目标态

### 入库前审查门

| 规则 | 建议 |
|---|---|
| LLM reviewer 开关 | 生产 / 真实验收默认开启 |
| rule-based review | 保留为兜底，但结果应显示为 `rule_based_passed` 或在 route 上强提示 |
| LLM 调用失败 fallback | 不建议静默 rule-based pass；应至少标记 route fallback，并进入人工复核或阻断入库 |
| 自动修复 | 保持 `autoFixEnabled=true`、`maxFixRounds=1` 可接受 |
| needs_human_review | 默认不进入 query 可见面 |
| allowPersistNeedsHumanReview | 建议默认 `false`；如果产品要落库复核稿，必须配 query 可见性隔离 |

### 后台展示

应显式展示：

| 展示项 | 来源 |
|---|---|
| review route | `compile_job_steps.model_route` 或 `ArticleReviewEnvelope.reviewerRoute` |
| review mode | `rule-based` / `llm` / `llm-fallback-rule-based` |
| review status 分布 | `acceptedCount`、`pendingReviewCount`、`needsHumanReviewCount` |
| fix route 与 fix 轮次 | `fix_review_issues` step、`fixAttemptCount` |
| 每篇文章最终状态 | `articles.review_status` |
| query 可见性 | 基于 `review_status` + `lifecycle` 计算展示 |

当前后台只压缩展示“生成 / 审查 / 完成”，没有把 `model_route=rule-based` 作为产品级状态暴露出来，容易让用户误以为“审查”就是 LLM 审查。

### Query 可见性

建议定义统一可见性：

| 对象 | Query 可见规则 |
|---|---|
| article | `review_status='passed' AND lifecycle='ACTIVE'` |
| article chunk | 继承 article 可见性 |
| article vector | 继承 article 可见性 |
| article chunk vector | 继承 article 可见性 |
| fact card | 至少排除 `conflict` / `needs_human_review`；`low_confidence` 是否可作为背景证据需产品确认 |

如果短期不改 SQL，至少要在报告和后台说明当前是“降权策略”，不是“不可见策略”。

## 最小落地顺序

1. 后台展示补强：在 compile job / processing task 展示 `review_articles` 的 `model_route`、`agent_role`、accepted/fixable/needs_human_review 数量。
2. 启用 LLM reviewer：设置 `LATTICE_LLM_REVIEW_ENABLED=true`，用最近资料做一次 compile 专项验收，确认 `review_articles model_route=compile.reviewer.*`。
3. query 可见性门禁：article / chunk / vector 检索入口过滤 `passed + ACTIVE`。
4. 调整 `allowPersistNeedsHumanReview`：默认改为 false，或引入 `query_visible=false` 隔离复核稿。
5. 自动审查审计：为每篇自动 review/fix 记录 route、status、issue count、fix round，避免只靠 step summary 排查。

## 最终判断

当前 rule-based review **不应被解释为产品意义上的“已完成内容审查”**。它能证明 draft 没有完全绕过 review step，但不能证明文章内容经过了真实 reviewer 审查。

要消除“生成草稿后像是直接入库”的产品疑虑，单靠解释当前流程不够；需要同时做到：

- 启用 LLM reviewer；
- 后台明确展示 review route / review status；
- query 侧显式隔离非 `passed/ACTIVE` 内容；
- 对 `low_confidence` fact card 的问答使用口径做产品确认。
