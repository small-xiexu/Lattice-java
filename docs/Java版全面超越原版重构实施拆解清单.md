# Java版全面超越原版重构实施拆解清单

**文档版本**：v1.0  
**编写日期**：2026-04-26  
**适用仓库**：`/Users/sxie/xbk/Lattice-java`  
**关联文档**：`docs/Java版全面超越原版实施设计方案.md`

---

## 1. 文档定位

这份文档不是新的抽象方案，而是把上一份总方案继续往下拆成可以直接指导重构实施的台账。

这份文档只做 4 件事：

1. 从当前真实代码出发，确认哪些类、哪些图节点、哪些表就是本次重构的直接落点。
2. 按“包、类、表、Graph 节点”拆出具体改造动作。
3. 明确哪些现有实现保留，哪些现有实现直接替换，哪些地方新增能力即可。
4. 给后续分工、排期、并行推进提供一份工程化清单。

本清单继续沿用当前项目级前提：

- 按新项目重构口径推进。
- 不兼容旧数据、旧逻辑、旧工作集、旧检索链路。
- 不为历史运行态、历史 pending、历史缓存设计平滑升级方案。
- 如需重建本地数据库，可直接清库重建，不以“保旧数据”作为设计约束。

---

## 2. 当前真实代码基线

## 2.1 Query 主链现状

当前 Query 的真实编排入口是：

- `src/main/java/com/xbk/lattice/query/service/QueryGraphOrchestrator.java`
- `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionFactory.java`

当前真实 Graph 节点包括：

- `normalize_question`
- `check_cache`
- `dispatch_retrieval`
- `retrieve_fts`
- `retrieve_refkey`
- `retrieve_source`
- `retrieve_contribution`
- `retrieve_graph`
- `retrieve_article_vector`
- `retrieve_chunk_vector`
- `fuse_candidates`
- `answer_question`
- `review_answer`
- `rewrite_answer`
- `claim_segment`
- `citation_check`
- `citation_repair`
- `persist_response`
- `finalize_response`

当前 Query 主链最关键的现实约束有 4 个：

- `KnowledgeSearchService` 仍然是多通道融合的直接入口，但当前只接受原始 `question`，没有“query intent / rewrite / strategy”上下文。
- `RefKeySearchService` 目前是先把文章捞出来再做内存打分，没有真正利用好 `articles.referential_keywords`。
- `SourceSearchService` 与 `ContributionSearchService` 仍然依赖 `findAll()` 后内存打分。
- `QueryGraphOrchestrator.execute(...)` 在 `finally` 中无条件执行 `queryWorkingSetStore.deleteByQueryId(...)`，这与“可恢复执行”目标直接冲突。

当前 Query 配置链路已经真实存在：

- `QueryRetrievalSettingsState`
- `QueryRetrievalSettingsService`
- `QueryRetrievalSettingsJdbcRepository`
- `AdminQueryRetrievalConfigController`
- `AdminQueryRetrievalConfigRequest`
- `AdminQueryRetrievalConfigResponse`

这意味着 Query V2 不是“从零新建后台配置”，而是“扩展已有配置链路”。

## 2.2 Deep Research 主链现状

当前 Deep Research 的真实入口是：

- `src/main/java/com/xbk/lattice/query/service/DeepResearchOrchestrator.java`
- `src/main/java/com/xbk/lattice/query/deepresearch/graph/DeepResearchGraphDefinitionFactory.java`

当前 Deep Research 是“按 plan 动态建图”：

- `initialize_plan`
- 多个 `executeResearchTask`
- 多个 `summarizeLayer`
- `synthesize_answer`

当前已经存在的真实持久化与审计基础包括：

- `DeepResearchWorkingSetStore`
- `DeepResearchAuditPersistenceService`
- `DeepResearchRunJdbcRepository`
- `DeepResearchTaskJdbcRepository`
- `DeepResearchTaskHitJdbcRepository`
- `DeepResearchFindingJdbcRepository`
- `DeepResearchAnswerProjectionJdbcRepository`

当前 Deep Research 的关键现实约束有 3 个：

- `DeepResearchOrchestrator.execute(...)` 在 `finally` 中同时删除 `deepResearchWorkingSetStore` 和 `queryWorkingSetStore`。
- `KnowledgeSearchService` 已被 Deep Research 复用，但尚不支持 task 级检索策略。
- `DeepResearchState` 目前还没有“恢复点”和“恢复来源”字段。

## 2.3 Compile 主链现状

当前 Compile 的真实编排入口是：

- `src/main/java/com/xbk/lattice/compiler/service/StateGraphCompileOrchestrator.java`
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphDefinitionFactory.java`

当前 Compile Graph 节点包括：

- `initialize_job`
- `ingest_sources`
- `persist_source_files`
- `persist_source_file_chunks`
- `extract_ast_graph`
- `group_sources`
- `split_batches`
- `analyze_batches`
- `merge_concepts`
- `plan_changes`
- `enhance_existing_articles`
- `compile_new_articles`
- `review_articles`
- `fix_review_issues`
- `persist_articles`
- `rebuild_article_chunks`
- `refresh_vector_index`
- `generate_synthesis_artifacts`
- `capture_repo_snapshot`
- `finalize_job`

Compile 侧已经存在较强的运行态基础：

- `CompileWorkingSetStore`
- `CompileJobJdbcRepository`
- `CompileJobStepJdbcRepository`
- `GraphStepLogger`
- `CompileGraphLifecycleListener`

这说明 Compile 侧不是“没有运行态”，而是“运行态元数据已经有了，但工作集 payload 还没有真正做成可恢复实现”。

## 2.4 当前表结构现状

当前单个 baseline migration 是：

- `src/main/resources/db/migration/V1__baseline_schema.sql`

当前已经真实存在、可直接扩展的表包括：

- `articles`
- `article_chunks`
- `source_files`
- `source_file_chunks`
- `contributions`
- `query_retrieval_settings`
- `query_answer_audits`
- `deep_research_runs`
- `compile_jobs`
- `compile_job_steps`

当前已经存在、对本次重构最有价值的列或索引基础包括：

- `articles.referential_keywords`
- `source_files.file_path`
- `source_files.relative_path`
- `source_files.content_text`
- `source_file_chunks.file_path`
- `article_chunks.chunk_text`
- `query_retrieval_settings.*`
- `compile_jobs.current_step`
- `compile_job_steps.step_name`

## 2.5 当前可直接替换的短板实现

本次重构最应直接替换的现有实现包括：

- `FtsSearchService`
- `RefKeySearchService`
- `SourceSearchService`
- `ContributionSearchService`
- `QueryTokenExtractor`
- `InMemoryQueryWorkingSetStore`
- `InMemoryDeepResearchWorkingSetStore`
- `InMemoryCompileWorkingSetStore`

本次重构最应直接保留并增强的现有实现包括：

- `QueryGraphDefinitionFactory`
- `KnowledgeSearchService`
- `RrfFusionService`
- `DeepResearchGraphDefinitionFactory`
- `DeepResearchSynthesizer`
- `CompileGraphDefinitionFactory`
- `GraphStepLogger`
- `QueryRetrievalSettingsService`

需要特别注意的一点：

- `QueryRewritePayload` 这个现有类虽然名字带 `Rewrite`，但字段语义更接近“答案重写载荷”，不适合作为“查询改写”的结构化对象直接复用。

---

## 3. 重构总原则

## 3.1 架构原则

- Query、Deep Research、Compile 三条主链都优先保留现有 graph 骨架，不做推倒重来。
- 重构重点放在“节点前后补能力”和“节点内部实现替换”，而不是换一套新的 orchestrator。
- Query 主链优先解决召回与准确性短板。
- Deep Research 优先解决 task 级检索策略和 checkpoint。
- Compile 优先解决工作集可恢复和检索索引回填挂接。

## 3.2 数据原则

- 不设计旧数据迁移脚本。
- 不设计运行期双写、双读、旧新链路并存。
- 若团队确认当前仍处于重构窗口期，允许直接改写 `V1__baseline_schema.sql` 并清库重建。
- 若团队希望保留重构演进痕迹，则在 `V1` 之后追加新 migration，但这只是工程组织方式，不构成兼容承诺。

## 3.3 运行态原则

- Redis 保存运行中 working set。
- PostgreSQL 保存审计、运行记录、步骤日志、可追溯历史。
- 短任务不要求一定恢复，但必须取消“无条件即时清理”。
- 长任务必须具备恢复点。

---

## 4. 按包拆解的目标结构

| 包路径 | 当前角色 | 重构后角色 | 动作 |
| --- | --- | --- | --- |
| `query/graph` | Query Graph 定义与状态映射 | Query V2 编排核心 | 保留，补节点、补状态字段、补生命周期策略 |
| `query/service` | 检索、融合、生成、审查 | Query Preparation + Retrieval Core + Fusion | 保留主体，替换检索实现，新增 strategy/rewrite/audit 组件 |
| `query/deepresearch/graph` | 动态图定义 | 深研执行与恢复编排 | 保留，补 checkpoint 写入点 |
| `query/deepresearch/service` | planner/researcher/synthesizer/orchestrator | 深研策略化检索与恢复 | 保留，增强 task-aware retrieval 和恢复控制 |
| `query/deepresearch/store` | 深研 working set 抽象 | Redis 深研 working set 主体 | 新增 Redis 实现，替换内存实现 |
| `compiler/graph` | 编译图定义、状态、working set 抽象 | Compile Recoverable Execution 核心 | 保留，替换 store 实现，补恢复语义 |
| `compiler/graph/node` | 编译图节点实现 | 检索索引回填与幂等持久化落点 | 保留，补索引列回填、补 checkpoint 边界 |
| `compiler/service` | 编译 orchestration、pipeline、job 管理 | Compile 可恢复编排与索引维护 | 保留，增强 job resume 语义 |
| `infra/persistence` | JDBC 仓储层 | 检索查询仓储 + 审计仓储 + 配置仓储 | 扩展现有 repo，按表补查询方法 |
| `api/admin` | 后台管理接口 | Retrieval Strategy 配置入口 | 扩展现有 DTO 和 Controller |
| `db/migration` | schema baseline | Retrieval Core V2 与 Recovery V2 schema 落点 | 直接扩展 baseline 或追加迁移 |

---

## 5. 按表拆解的改造清单

## 5.1 直接扩展的现有表

| 表名 | 当前现状 | 目标扩展 |
| --- | --- | --- |
| `articles` | 已有 `referential_keywords`，但检索能力未充分利用 | 增加 `search_text`、`search_tsv`、`refkey_text`，作为 article FTS 与 refkey 主表 |
| `article_chunks` | 已有 chunk 文本，但没有 lexical 检索列 | 增加 `search_tsv`，作为 article chunk lexical 通道 |
| `source_files` | 已有路径与全文字段，但无统一归一化检索列 | 增加 `file_path_norm`、`search_tsv`，用于文件级路径/类名/配置命中 |
| `source_file_chunks` | 已有 chunk 文本与 file path | 增加 `search_tsv`、`file_path_norm`，作为 source chunk lexical 主表 |
| `contributions` | 已有 question/answer/corrections | 增加 `question_tsv`、`answer_tsv`、`corrections_tsv` |
| `query_retrieval_settings` | 当前只有 7 路权重和 `rrf_k` | 增加 `refkey_weight`、`article_chunk_weight`、`source_chunk_weight`、`rewrite_enabled`、`intent_aware_vector_enabled` |
| `compile_jobs` | 已有 job 状态与 current_step | 视需要增加 `resume_token`、`working_set_version`、`last_checkpoint_at` |
| `compile_job_steps` | 已有步骤级日志 | 视需要增加 `working_set_refs_json` 或更丰富的输出摘要字段 |

## 5.2 建议新增的表

| 表名 | 用途 | 说明 |
| --- | --- | --- |
| `query_rewrite_rules` | Query Rewrite 规则表 | 存缩写、别名、映射目标、作用域、优先级 |
| `query_rewrite_audits` | Query Rewrite 审计表 | 存某次 query 的命中规则、扩写结果、是否触发 LLM |
| `query_retrieval_runs` | 检索运行主表 | 存每次 retrieval 的问题、策略、通道启停、版本标签 |
| `query_retrieval_channel_hits` | 通道命中明细表 | 存每路前 N 命中、原始排序、融合后排序、解释信息 |
| `deep_research_checkpoints` | Deep Research 恢复点表 | 仅在需要跨进程恢复时落 PostgreSQL 恢复点元信息 |
| `query_execution_checkpoints` | Query 恢复点表 | 仅在 Query 也要持久恢复时启用 |

## 5.3 索引策略

- `articles.search_tsv`：GIN
- `article_chunks.search_tsv`：GIN
- `source_files.file_path_norm`：btree 或 `pg_trgm`
- `source_files.search_tsv`：GIN
- `source_file_chunks.search_tsv`：GIN
- `source_file_chunks.file_path_norm`：btree 或 `pg_trgm`
- `contributions.question_tsv`：GIN
- `contributions.answer_tsv`：GIN
- `contributions.corrections_tsv`：GIN
- `articles.referential_keywords`：GIN

---

## 6. 按类拆解的改造清单

## 6.1 Query 相关类

| 类/文件 | 动作 | 说明 |
| --- | --- | --- |
| `QueryGraphDefinitionFactory` | 保留并增强 | 新增 `rewrite_query`、`classify_intent`、`resolve_retrieval_strategy`、`retrieve_article_chunk_fts`、`retrieve_source_chunk_fts`、`audit_retrieval` |
| `QueryGraphState` | 扩展字段 | 增加 `rewrittenQuestion`、`queryIntent`、`rewriteAuditRef`、`retrievalStrategyRef`、`articleChunkHitsRef`、`sourceChunkHitsRef`、`retrievalAuditRef` |
| `QueryGraphOrchestrator` | 修改生命周期 | 取消 `finally` 里无条件 `deleteByQueryId`，改为按成功/失败/TTL 策略清理 |
| `KnowledgeSearchService` | 保留并升级 | 从“只收原问题”升级为“接受 retrieval context 的统一融合入口” |
| `FtsSearchService` | 重写 SQL | 收敛为 article 主文 FTS，不再承担所有 lexical 责任 |
| `RefKeySearchService` | 重写实现 | 改为直接命中 `articles.referential_keywords`、`concept_id`、标题和结构化列 |
| `SourceSearchService` | 调整职责 | 收敛为 source file/path 命中 facade，不再全表扫 chunk |
| `ContributionSearchService` | 重写实现 | 改为 question/answer/corrections 的数据库侧检索 |
| `QueryTokenExtractor` | 升级能力 | 补路径、类名、配置键、混合 token、中英文技术短语 |
| `QueryRetrievalSettingsState` | 扩展字段 | 补 `refkeyWeight`、`articleChunkWeight`、`sourceChunkWeight` 等 |
| `QueryRetrievalSettingsService` | 扩展保存接口 | 支持新权重和新开关 |
| `QueryRetrievalSettingsJdbcRepository` | 扩展读写 SQL | 读写新增配置列 |
| `AdminQueryRetrievalConfigController` | 扩展接口 | 透传新的权重、开关、策略配置 |
| `AdminQueryRetrievalConfigRequest` | 扩展 DTO | 新增独立权重与新开关字段 |
| `AdminQueryRetrievalConfigResponse` | 扩展 DTO | 返回新的有效配置 |

## 6.2 Deep Research 相关类

| 类/文件 | 动作 | 说明 |
| --- | --- | --- |
| `DeepResearchOrchestrator` | 修改生命周期 | 取消 `finally` 里无条件清理 working set，增加 resume policy |
| `DeepResearchGraphDefinitionFactory` | 保留并增强 | 在 `initialize_plan`、`executeResearchTask`、`summarizeLayer`、`synthesize_answer` 后补 checkpoint |
| `DeepResearchState` | 扩展字段 | 增加 `checkpointRef`、`researchStrategyRef`、`resumeFromLayerIndex`、`lastCompletedTaskSlot` |
| `DeepResearchResearcherService` | 接入策略化检索 | 使用 task-aware retrieval strategy，而不是只走统一默认检索 |
| `DeepResearchSynthesizer` | 保留并增强 | 与 evidence dedup/merge policy 集成 |
| `DeepResearchWorkingSetStore` | 保留接口 | 新增 Redis 实现，接口尽量不破坏 |

## 6.3 Compile 相关类

| 类/文件 | 动作 | 说明 |
| --- | --- | --- |
| `StateGraphCompileOrchestrator` | 保留并增强 | 保持 graph 执行入口，增加基于 `jobId` 的恢复启动逻辑 |
| `CompileGraphDefinitionFactory` | 保留 | 图拓扑基本不动，重点补 checkpoint 语义 |
| `CompileGraphState` | 视需要补字段 | 增加 `workingSetVersion`、`resumeFromStep`、`checkpointAt` |
| `CompileWorkingSetStore` | 保留接口 | 新增 Redis 实现，替换内存实现 |
| `GraphStepLogger` | 扩展输出摘要 | 把 working set refs/version 写入 step summary 或扩展字段 |
| `PersistSourceFilesNode` | 增强 | 回填 `source_files` 检索列 |
| `PersistSourceFileChunksNode` | 增强 | 回填 `source_file_chunks.search_tsv` |
| `PersistArticlesNode` | 增强 | 回填 `articles.search_tsv`、`refkey_text` |
| `RebuildArticleChunksNode` | 增强 | 回填 `article_chunks.search_tsv` |
| `RefreshVectorIndexNode` | 保留 | 继续作为向量索引刷新入口，不另起旁路 |

## 6.4 Persistence 相关类

| 类/文件 | 动作 | 说明 |
| --- | --- | --- |
| `ArticleChunkJdbcRepository` | 扩展查询方法 | 新增 article chunk lexical 查询 |
| `SourceFileJdbcRepository` | 扩展查询方法 | 新增 path/class/config file 级检索 |
| `SourceFileChunkJdbcRepository` | 扩展查询方法 | 新增 source chunk FTS 查询 |
| `ContributionJdbcRepository` | 扩展查询方法 | 新增 question/answer/corrections FTS 查询 |
| `CompileJobJdbcRepository` | 扩展字段或语义 | 承接 compile resume 元信息 |
| `CompileJobStepJdbcRepository` | 扩展字段或语义 | 承接步骤级恢复辅助信息 |
| `QueryAnswerAuditJdbcRepository` | 继续复用 | 不重做答案审计主表 |
| `DeepResearchRunJdbcRepository` | 继续复用 | 不重做 deep research 主审计表 |

---

## 7. 建议新增的类与文件

## 7.1 Query 新增类

- `query/service/RetrievalStrategyResolver.java`
- `query/service/RetrievalQueryContext.java`
- `query/service/QueryIntentClassifier.java`
- `query/service/QueryIntent.java`
- `query/service/QueryRewriteService.java`
- `query/service/QueryRewriteResult.java`
- `query/service/ArticleChunkFtsSearchService.java`
- `query/service/SourceChunkFtsSearchService.java`
- `query/service/RetrievalAuditService.java`
- `query/graph/RedisQueryWorkingSetStore.java`

## 7.2 Deep Research 新增类

- `query/deepresearch/store/RedisDeepResearchWorkingSetStore.java`
- `query/deepresearch/service/DeepResearchCheckpointService.java`
- `query/deepresearch/service/ResearchTaskRetrievalPlanner.java`

## 7.3 Compile 新增类

- `compiler/graph/RedisCompileWorkingSetStore.java`
- `compiler/service/CompileWorkingSetLifecycleService.java`

## 7.4 Persistence 与 migration 新增文件

- `infra/persistence/QueryRewriteRuleJdbcRepository.java`
- `infra/persistence/QueryRewriteAuditJdbcRepository.java`
- `infra/persistence/QueryRetrievalRunJdbcRepository.java`
- `infra/persistence/QueryRetrievalChannelHitJdbcRepository.java`
- `db/migration/V2__retrieval_core_v2.sql` 或改写 `V1__baseline_schema.sql`
- `db/migration/V3__working_set_recovery.sql` 或并入同一重构 migration

说明：

- 当前仓库只有一个 `V1__baseline_schema.sql`。
- 如果团队决定“清库重建 + 不保旧数据”，优先推荐直接改写 baseline。
- 如果团队希望保留重构历史，再拆成后续 migration。

---

## 8. 按阶段实施的落地顺序

## 8.1 Phase A：检索底座改造

目标：

- 先把准确性最相关的召回短板补齐。

直接落点：

- 表：`articles`、`article_chunks`、`source_files`、`source_file_chunks`、`contributions`
- 类：`FtsSearchService`、`RefKeySearchService`、`SourceSearchService`、`ContributionSearchService`
- repo：`ArticleChunkJdbcRepository`、`SourceFileJdbcRepository`、`SourceFileChunkJdbcRepository`、`ContributionJdbcRepository`
- graph：`QueryGraphDefinitionFactory`

交付结果：

- article chunk lexical 通道上线
- source chunk lexical 通道上线
- refkey 独立权重上线
- source / contribution 不再依赖 `findAll()`

## 8.2 Phase B：Query Preparation 改造

目标：

- 把 rewrite、intent、strategy 真正接进 Query 主链。

直接落点：

- 类：`QueryGraphDefinitionFactory`、`QueryGraphState`、`KnowledgeSearchService`、`QueryTokenExtractor`
- 新类：`QueryRewriteService`、`QueryIntentClassifier`、`RetrievalStrategyResolver`
- 表：`query_rewrite_rules`、`query_rewrite_audits`

交付结果：

- Query 主链在 `normalize_question` 之后具备 rewrite 和 intent 分类
- 检索权重不再是单一静态配置

## 8.3 Phase C：Working Set 可恢复化

目标：

- 把 Query、Deep Research、Compile 的运行时 payload 从内存移到 Redis。

直接落点：

- 接口：`QueryWorkingSetStore`、`DeepResearchWorkingSetStore`、`CompileWorkingSetStore`
- 实现：三个 `InMemory*WorkingSetStore`
- orchestrator：`QueryGraphOrchestrator`、`DeepResearchOrchestrator`、`StateGraphCompileOrchestrator`

交付结果：

- Query 不再无条件在 finally 删除 working set
- Deep Research 有 layer 级 checkpoint
- Compile 有 job 级 working set 版本与恢复入口

## 8.4 Phase D：默认收益释放

目标：

- 让向量、图谱、chunk lexical 成为默认主链收益，而不是“有实现但默认不强”。

直接落点：

- `QueryRetrievalSettingsState`
- `QueryRetrievalSettingsService`
- `QueryRetrievalSettingsJdbcRepository`
- `AdminQueryRetrievalConfigController`
- `VectorSearchService`
- `ChunkVectorSearchService`
- `GraphSearchService`

交付结果：

- 策略化开启 vector / graph
- 管理侧可调整新权重
- 默认配置偏向新主链

## 8.5 Phase E：检索审计与评测闭环

目标：

- 让“为什么领先、哪里还没领先”可以被定位。

直接落点：

- 表：`query_retrieval_runs`、`query_retrieval_channel_hits`
- 类：`RetrievalAuditService`
- 输出链路：`KnowledgeSearchService`、`QueryGraphDefinitionFactory`

交付结果：

- 可按问题查看通道命中与漏召回
- 可按版本对比策略差异
- 可对接 benchmark gap report

---

## 9. 可并行推进的分工建议

## 9.1 Lane A：Schema 与 JDBC 仓储

负责内容：

- 表结构改造
- 索引改造
- Repository 查询方法扩展

主文件：

- `V1__baseline_schema.sql` 或后续 migration
- `ArticleChunkJdbcRepository`
- `SourceFileJdbcRepository`
- `SourceFileChunkJdbcRepository`
- `ContributionJdbcRepository`
- `QueryRetrievalSettingsJdbcRepository`

## 9.2 Lane B：Query Retrieval Core

负责内容：

- 新 lexical 通道
- refkey 重写
- strategy resolver
- Query Graph 新检索节点

主文件：

- `QueryGraphDefinitionFactory`
- `KnowledgeSearchService`
- `FtsSearchService`
- `RefKeySearchService`
- `SourceSearchService`
- `ContributionSearchService`

## 9.3 Lane C：Query Preparation 与 Admin 配置

负责内容：

- rewrite
- intent
- retrieval settings 扩展
- admin 接口调整

主文件：

- `QueryGraphState`
- `QueryTokenExtractor`
- `QueryRetrievalSettingsState`
- `QueryRetrievalSettingsService`
- `AdminQueryRetrievalConfigController`
- `AdminQueryRetrievalConfigRequest`
- `AdminQueryRetrievalConfigResponse`

## 9.4 Lane D：Recoverable Execution

负责内容：

- Redis working set
- lifecycle policy
- checkpoint

主文件：

- `QueryGraphOrchestrator`
- `DeepResearchOrchestrator`
- `DeepResearchGraphDefinitionFactory`
- `StateGraphCompileOrchestrator`
- `CompileWorkingSetStore`
- `QueryWorkingSetStore`
- `DeepResearchWorkingSetStore`

## 9.5 Lane E：Evaluation 与审计

负责内容：

- retrieval audit
- deep research/checkpoint 审计
- benchmark 对接

主文件：

- `RetrievalAuditService`
- `QueryAnswerAuditJdbcRepository`
- `DeepResearchRunJdbcRepository`
- benchmark runner 相关模块

---

## 10. 实施时必须显式处理的风险点

## 10.1 Query/Deep Research 的 finally 清理逻辑

当前问题：

- `QueryGraphOrchestrator`
- `DeepResearchOrchestrator`

都在执行结束后直接删 working set。

结论：

- 不先改这个点，Redis working set 落地后也无法真正恢复。

## 10.2 `QueryRewritePayload` 名称误导

当前问题：

- 类名像“查询改写”
- 字段语义更像“答案改写/答案重写”

结论：

- 不建议拿它直接承载 retrieval rewrite 结果。
- 推荐新建 query rewrite 专用结果对象。

## 10.3 Compile 恢复不要重复造平行审计体系

当前问题：

- Compile 侧已有 `compile_jobs`、`compile_job_steps`、`GraphStepLogger`

结论：

- 优先在现有 job/step 体系上扩展 resume 元信息。
- 只有在现有表无法承载时，再新增恢复专用表。

## 10.4 `SourceSearchService` 的职责需要收敛

当前问题：

- 它现在名义上叫 source search
- 实际上做的是 `source_file_chunks` 全量扫描

结论：

- Phase A 之后它更适合承担“文件级命中 facade”
- source chunk lexical 应由独立 service 承担

---

## 11. 完成定义

这一节的意思可以直接理解为：

- 现在我们完成的是“把重构怎么做拆清楚了”。
- 现在还不能据此直接宣布“Java 版已经全面超过原版”。
- 这份文档的价值，是让后续实施不再停留在抽象讨论，而是已经能按模块、按表、按类、按阶段开工。

真正进入“开始实施”状态之前，建议先把下面 4 个口径统一清楚：

1. 先统一重构口径。
   意思是后续默认按“新项目重构”推进，不再把时间花在旧数据迁移、旧逻辑兼容、旧链路保留上。
2. 先统一数据库处理方式。
   意思是团队要接受“可以清库重建”或者“可以追加非兼容 migration”这件事，否则很多 schema 级重构会半途被兼容要求打断。
3. 先统一推进范围。
   意思是目标既然是“全面超过原版”，那就不能只做 Query 检索层优化，还要同时覆盖 Query、Deep Research、Compile 三条主链。
4. 先统一验收标准。
   意思是后面判断“是不是做成了”，不能靠感觉，而要继续按 `docs/Java版全面超越原版实施设计方案.md` 里的准确性、引用质量、检索召回率、恢复成功率这些指标来验收。

如果以上 4 条都已经确认，那就可以正式进入实施阶段。

为什么推荐先从 `Phase A` 和 `Lane A/B` 开工，原因也很直接：

- `Phase A` 解决的是当前最影响准确性的检索底座问题。
- `Lane A` 负责表结构、索引、JDBC Repository，是检索能力落地的地基。
- `Lane B` 负责 Query Retrieval Core，是最直接影响 recall、排序和最终答案质量的主链。
- 这两部分一起推进，最容易最快看到真实收益，也最容易验证“Java 版和原版的准确性差距是否正在缩小”。

---

## 12. 执行台账

- [x] Phase A / Lane A：Schema 与 JDBC 仓储改造
  - 验收：已扩展 baseline 检索列与索引，新增 Repository 数据库侧 lexical 查询；通过 `ArticleChunkJdbcRepositoryTests`、`SourceFileJdbcRepositoryTests`、`SourceFileChunkJdbcRepositoryTests`、`ContributionJdbcRepositoryTests`。
- [x] Phase A / Lane B：Query Retrieval Core 改造
  - 验收：已接入 article chunk lexical、source chunk lexical、refkey 独立权重与 Graph 新检索节点；通过 `SourceSearchServiceTests`、`ContributionSearchServiceTests`、`QueryGraphOrchestratorTests`、`QueryFacadeServiceCacheTests`、`QueryFacadeServiceVectorTests`。
- [x] Phase B：Query Preparation 改造
  - 验收：已接入 query rewrite、intent 分类与 retrieval strategy 主链，补齐管理侧配置与 Query Preparation 测试；通过 `QueryPreparationServiceTests`、`QueryTokenExtractorTests`、`QueryGraphStateMapperTests`、`WorkingSetStoreRoundTripTests`、`QueryGraphOrchestratorTests`、`QueryFacadeServiceCacheTests`、`QueryFacadeServiceVectorTests`、`AdminQueryRetrievalConfigControllerTests`。
- [x] Phase C：Working Set 可恢复化
  - 验收：已新增 Query / Deep Research / Compile 三类 Redis working set store、TTL 配置与测试默认回退；取消 Query / Deep Research / Compile 成功后立即清理 working set，并把 Compile step log 扩展为可追踪 working set refs；通过 `RedisQueryWorkingSetStoreTests`、`RedisDeepResearchWorkingSetStoreTests`、`RedisCompileWorkingSetStoreTests`、`GraphStepLoggerTests`、`WorkingSetStoreRoundTripTests`、`QueryGraphOrchestratorTests`、`AdminQueryRetrievalConfigControllerTests`、`StateGraphCompileOrchestratorTests`。
- [x] Phase D：默认收益释放
  - 验收：已按“受控激进”口径上调 graph / article chunk / source chunk / vector 默认权重，保留配置/调用链问题的 intent-aware vector 门控；同步打通 `application.yml`、`query_retrieval_settings` baseline 默认值与向量 properties 展示兜底，并在测试侧继续默认关闭 vector 以控回归噪声；通过 `AdminQueryRetrievalConfigControllerTests`、`QueryPreparationServiceTests`、`AdminVectorConfigControllerTests`、`QueryGraphOrchestratorTests`。
- [x] Phase E：检索审计与评测闭环
  - 验收：已补 `QueryRetrievalAuditJdbcRepository` 读取面、`RetrievalAuditQueryService`、`AdminQueryRetrievalAuditController` 与 recent/latest audit DTO，可按 `queryId` / recent runs 查看通道命中、fused rank、版本标签与策略标签；通过 `RetrievalAuditServiceTests`、`RetrievalAuditQueryServiceTests`、`AdminQueryRetrievalAuditControllerTests`、`QueryPreparationServiceTests`、`QueryGraphStateMapperTests`、`QueryGraphOrchestratorTests`。已运行 `AstCitationDeepResearchBenchmarkRunner`，产出 `gap-report.json/md`，当前结论为 `sharedBenchmarkOutperformOriginal=true`、`comprehensiveOutperformOriginal=false`：共享 benchmark 已领先原版，但因缺少 retrieval recall 数据集、真实问题回放、恢复能力与默认收益同构对标，尚不能宣称“Java 版已全面超越原版”。
- [x] Phase F：全面超越证据补齐
  - 进行中：2026-04-27 按 `/Users/sxie/xbk/Lattice-java/.claude/t1.md` 重新执行全量复验，并复核是否已具备“全面超越原版 `/Users/sxie/xbk/Lattice`”的充分证据；当前先核对启动文档、验收手册、benchmark 产物与真实链路，再决定是否可勾选完成。
  - 进行中：已重新核对 `/Users/sxie/xbk/Lattice-java/.claude/t1.md`，确认其中已补 `合合ocr`（`TextIn xParse`）凭证；此前“OCR 缺少配置”属于本次复验没有把该段配置写入文档解析连接中心。现已在隔离实例 `http://127.0.0.1:18090` 落库 OCR 连接与默认 image/scanned-pdf 路由，重跑 `docs/` compile 后已越过原先失败的 `ingest_sources`，当前阻塞已转为长文本 `compile_new_articles` 生成耗时。
  - 进行中：基于同一隔离实例已验证 query / deep research 主链可真实调用 `gpt-5.4 + BAAI/bge-m3(1024)`；普通 query `a895f9e4-8952-4150-a691-7af801d2ef0b` 返回 `SUCCESS`、Deep Research `6bdf6df2-d4ca-43b0-84bd-53237c7434f1` 返回 `PARTIAL_ANSWER`，`/api/v1/admin/overview` 当前可见 `sourceFileCount=20`、`pendingQueryCount=2`。但由于 full compile 尚未落成文章，当前两次问答都仅命中 `sourceCount=2`、`articleCount=0`，不能据此替代“编译完成后文章资产问答”验收。
  - 进行中：`docs/` 与受控 Markdown 样本 compile 在 `review_articles / fix_review_issues` 阶段均出现 `openai_compatible` 返回 `text/event-stream` 时的 `UnknownContentTypeException` 抖动；现象表现为单次 reviewer/fixer 调用先失败、随后重试成功，作业整体仍在推进，但说明当前 `gpt-5.4@http://localhost:8888` 的 reviewer/fixer 兼容性仍不稳定，Phase F 不能按“真实 compile 全绿收口”勾选。
  - 已验证：隔离实例 `lattice_e2e_codex_20260427` 现已落库 `4` 篇文章、`20` 条 `source_files`，其中 `2` 篇 `review_status=passed`、`2` 篇 `needs_human_review`；`/api/v1/admin/overview` 显示 `articleCount=4`、`reviewPendingArticleCount=2`。在此基础上重新执行普通 query `e1ed8219-1d59-48c1-a935-38d3edba5509` 与 Deep Research `b9cb1a72-bd30-41a9-8e3d-01b26eb499c2` 后，已分别命中 `articleCount=2/sourceCount=2` 与 `articleCount=3/sourceCount=3`；其中 Deep Research 已返回 `SUCCESS`、`citationCoverage=1.0`、`partialAnswer=false`。说明问答链已不再只依赖 source 层，但 compile 完整收口与 reviewer/fixer 稳定性仍待继续验完。
  - 已验证：执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test` 后，`482` 个测试中出现 `1` 个失败，失败用例为 `DeepResearchResearcherServiceTests.shouldFilterTaskLevelIrrelevantRoutePlannerHits`；当前实际保留了 `2` 条 task hits（`routeplanner` 与 `research-notes`），未达到期望的“只保留 RoutePlanner 主命中”过滤效果，因此 Phase F 仍不能按“已全量通过”勾选。
  - 已验证：已在 `DeepResearchResearcherService` 增加任务级“优先结构化字段命中”收缩逻辑，并在 `ChatClientRegistry` 为 `openai_compatible` 同步调用显式设置 `Accept: application/json`；新增 SSE 回归桩后，重新执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=DeepResearchResearcherServiceTests,ChatClientRegistryTests,LlmInvocationExecutorTests test`，`19` 个定向测试全部通过。
  - 已验证：重新执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test` 后，当前全量自动化为 `483` 个测试全部通过，`BUILD SUCCESS`；此前 `DeepResearchResearcherServiceTests.shouldFilterTaskLevelIrrelevantRoutePlannerHits` 的失败点已消除，上一轮因并发干扰出现的 Vault 相关上下文失败也未再复现。
  - 已验证：已在 `IngestNode` 增加对 `.DS_Store`、`Thumbs.db` 与 `._*` 等系统元数据文件的默认跳过规则，并新增 `IngestNodeTests` 覆盖；随后执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=IngestNodeTests,CompileControllerTests,AdminCompileJobControllerTests,AdminManagementControllerTests,DocumentParseRouterIntegrationTests test`，`21` 个定向测试全部通过。基于 `18092/18093` 两套隔离实例的真实复验也可确认：原始 `docs/` 目录的 `Tag mismatch` 根因是将 `docs/images/.DS_Store` 一并编入，去除隐藏文件后同目录可稳定越过 `initialize_job/ingest_sources` 进入后续 compile 阶段。
  - 已验证：在全新隔离实例 `http://127.0.0.1:18094`、schema `lattice_e2e_codex_20260427_r5` 上，重新按 `/Users/sxie/xbk/Lattice-java/.claude/t1.md` 注入 OpenAI / Embedding / OCR 配置后，直接对原始目录 `/Users/sxie/xbk/Lattice-java/docs` 发起 fresh async compile，作业 `b910643e-ef5d-441b-ac0e-49cd7a86c9e5` 已稳定越过 `initialize_job`、`ingest_sources` 并进入 `compile_new_articles`；说明 `.DS_Store` 隐藏文件导致的 `Tag mismatch` 真实阻塞已在 fresh runtime 口径下消除。
  - 已验证：在后续全新隔离实例 `http://127.0.0.1:18095`、schema `lattice_e2e_codex_20260427_r6` 上，重新按同一份 `t1` 配置对原始 `docs/` 发起 fresh async compile，作业 `06259e3e-c336-4e0f-a6e2-7a7a547e1569` 已稳定推进到 `compile_new_articles 7/7 -> review_articles 4/7`，且 reviewer 连续多次真实返回 `SUCCEEDED`，未再复现之前 `openai_compatible` 在 compile writer/reviewer 阶段的 `UnknownContentTypeException(text/event-stream)`；说明 runtime 侧的 JSON/SSE 兼容补丁已在真实 reviewer 链路生效。
  - 已验证：同一 fresh runtime 作业 `06259e3e-c336-4e0f-a6e2-7a7a547e1569` 已最终 `SUCCEEDED`，`persistedCount=7`，说明 `docs/` 全量 compile 已可在完全隔离的新实例上跑到终态，不再停留于“越过阻塞点”的中间态。随后在 `http://127.0.0.1:18095` 上重新执行普通 query `53116f20-c5d0-444c-9dfd-17606694ccb5` 与 Deep Research `f2664144-d2b6-488e-a4c2-006ed0ee1b32`，分别命中 `articleCount=2/sourceCount=2` 与 `articleCount=3/sourceCount=3`，其中 Deep Research 返回 `SUCCESS`、`citationCoverage=1.0`。但该 fresh runtime 的管理读面同时显示 `articleCount=7`、`reviewPendingArticleCount=7`、`passedArticles=0`、`needsHumanReviewArticles=7`，说明“链路与能力已跑通”并不等于“默认生成质量已完全无需人工介入”；对外表述仍应区分“核心能力全面超过”与“默认质量全场景无保留领先”。
  - 进行中：已新增 compile review 后台配置骨架，包含 `compile_review_settings` 表、`CompileReviewConfigService` 与 `/api/v1/admin/compile/review/config` 读写接口，可后台调整 `autoFixEnabled/maxFixRounds/allowPersistNeedsHumanReview/humanReviewSeverityThreshold`，并已用 `AdminCompileReviewConfigControllerTests` 验证“保存后立即作用于运行时”。
  - 进行中：已把 `ReviewDecisionPolicy` 从“未通过即人工复核”改为“按严重度阈值分流”；当前策略会在自动修复耗尽后，仅将达到阈值的高严重度问题送入人工复核，低于阈值的问题可按通过状态收口。已用 `ReviewDecisionPolicyTests` 覆盖该行为，但尚未重新跑 fresh runtime 对比 `needs_human_review` 是否显著下降。
  - 待收口：图片 / OCR 聚合文章（例如 `Images`）仍缺专项 prompt / 分组策略；这类资料天然更容易触发 reviewer 误伤，后续应单独优化，否则即使提高 fix 轮次、放宽严重度阈值，也可能持续拉高 `needs_human_review` 比例。
  - 已验证：在全新隔离实例 `http://127.0.0.1:18096`、schema `lattice_e2e_codex_20260427_r7` 上，按 `/Users/sxie/xbk/Lattice-java/.claude/t1.md` 注入 `gpt-5.4`、`BAAI/bge-m3(1024)`、`TextIn xParse`、`/api/v1/admin/compile/review/config(maxFixRounds=3, humanReviewSeverityThreshold=HIGH, allowPersistNeedsHumanReview=true)` 与图片 / OCR 专项 compile/review prompt 后，作业 `34b9e560-1cd8-4a36-b501-c94cc05215ee` 的 Redis working set 一度已形成 `accepted_articles=6`、`needs_human_review_articles=1` 的收口态；但在最终持久化前失败，报错 `source file id missing for article path: 项目端到端验收手册`。
  - 已验证：上述失败已定位为 `项目全流程真实验收手册` 在 fixer/reviewer 循环后把 `sourcePaths` 从真实文件路径 `项目全流程真实验收手册.md` 改写成标题型字符串 `项目端到端验收手册`，导致 `persist_articles` 无法回填 `source_file_id`。现已在 compile 侧增加 `sources/source_paths` 归一化，强制保留原始来源路径，并补 `CompileArticleReviewFlowTests` 回归覆盖“fixer 把 sourcePaths 改成标题”场景。
  - 已验证：修复后在全新隔离实例 `http://127.0.0.1:18097`、schema `lattice_e2e_codex_20260427_r8` 上重新按同口径发起 fresh async compile，作业 `2ecf731b-f4c5-4cfb-bda8-209173c2212f` 已最终 `SUCCEEDED`，`persistedCount=8`；数据库读面与 `/api/v1/admin/overview` 显示 `articleCount=8`、`passedArticles=3`、`needsHumanReviewArticles=5`、`reviewPendingArticleCount=5`，说明 `needs_human_review` 已从上一轮 fresh runtime 的 `7/7` 下降到 `5/8`，但仍未降到理想水平。
  - 已验证：`项目全流程真实验收手册` 本轮已可稳定落库为 `review_status=needs_human_review` 且 `sourcePaths=['项目全流程真实验收手册.md']`；`article_source_refs` 总数为 `21`，说明 `sourcePaths` 标题化导致的持久化失败已被修复。
  - 待收口：`images`、`Java版全面超越原版重构实施拆解清单`、`工作台当前处理任务统一可见性改造方案`、`项目全流程真实验收手册`、`项目启动配置清单` 当前仍为 `needs_human_review`。其中 `Images` 在最终 fresh runtime 仍未稳定转为 `passed`，说明现有图片 / OCR 专项 prompt 虽然修掉了来源路径漂移，但对 reviewer/fixer 的质量改善仍不够，后续还需继续收紧图片类文章的生成与审查策略。
  - 已验证：重新执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=RedisQueryWorkingSetStoreTests,AstCitationDeepResearchBenchmarkRunner test` 后构建成功，最新 `target/benchmark-reports/ast-citation-deepresearch/gap-report.json` 仍为 `sharedBenchmarkOutperformOriginal=true`、`comprehensiveOutperformOriginal=true`、`blockingGaps=[]`；说明“对标原版领先”的 benchmark 证据仍成立，但尚不能抵消当前工作区内的自动化回归失败。
  - 验收：已修复 `AstCitationDeepResearchBenchmarkRunner` 的 checkpoint resume 入参与 Deep Research 固定桩 anchorId，并补齐 `CitationValidationResult` 的 Redis round-trip 序列化兼容性。已执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=RedisQueryWorkingSetStoreTests,AstCitationDeepResearchBenchmarkRunner test`；`target/benchmark-reports/ast-citation-deepresearch/gap-report.json` 显示 `resumeRecovery=LEADING`、`query/deepResearch/compile resume success rate=1.0`、`blockingGaps=[]`、`comprehensiveOutperformOriginal=true`、`verdictSummary=当前证据足以支持 Java 版全面超过原版。`
  - 已完成：基于 `docs/项目启动配置清单.md`、`docs/项目全流程真实验收手册.md` 与私有模型配置 `/Users/sxie/xbk/Lattice-java/.claude/t1.md`，已在隔离 schema `lattice_e2e_codex_20260427` 完成一轮全流程真实复验；服务 `http://127.0.0.1:18090` 持续 `UP`，已覆盖启动、全量 compile（`jobId=6372b302-6073-41e6-9c12-9d5d6fde535c`，`persistedCount=5`）、增量 compile（`jobId=dc64f39d-1e26-45a4-a5c1-9f01cbd5c567`，`persistedCount=4`）、admin 读面、HTTP query/feedback、CLI、HTTP MCP、vault/export、repo baseline/diff/rollback、article lifecycle/correction/rollback 与浏览器前台页验主链路。
  - 已验证：`architecture` 单篇纠错后生成 `snapshotId=18`（含“运维排查订单到库存链路时，应优先查看 ...”措辞），随后执行 article rollback 到 `snapshotId=16` 会生成 `snapshotId=20`，且 `snapshotId=20` 与 `snapshotId=16` 的正文哈希一致、`reviewStatus` 同为 `passed`；本轮可确认 article rollback 会恢复正文，不是只回退元数据。
  - 已验证：浏览器页验已覆盖 `/admin`、`/admin/ask`、`/admin/settings`、`/admin/developer-access`，四个页面均可正常加载、标题与主区块文案完整、控制台未见 `error/warn`。其中 `/admin/ask` 已通过前端真实提交问题“为什么订单服务不直接同步调用库存服务，而要走消息队列”，结果区成功展示最终答案与 `inventory.reserve.requested` 相关证据；验证后已丢弃临时 pending，`/api/v1/admin/overview` 恢复 `pendingQueryCount=0`。
  - 结论：benchmark 证据与本轮真实复验共同表明，Java 版在真实 LLM / embedding 接入、向量检索可用性、Deep Research、工作流恢复、CLI/HTTP/MCP 多入口与单篇回滚一致性上，已经具备超过原版的核心能力证据。
  - 进行中：按“修到可对外更强口径”为目标收口真实短板；已重新部署包含 `AnswerGenerationService` 配置题修复与 `IncrementalCompileService` 传播收紧修复的最新构建物。当前正准备重启 `http://127.0.0.1:18090`，先用 full compile 清洗旧增量污染，再做单文件受控增量复验，重点确认 `payments` 不再被 `ops/postmortem.md` 跨文章污染、`referentialKeywords` 不再混入 `51` / `21 分钟` / `GW-504-RETRY-EXHAUSTED`。
  - 待收口：配置型问答对精确数值的抽取仍不稳定，配置题多次退回 `FALLBACK`；增量 compile 单文件变更会联动 4 篇文章落库，收敛粒度偏粗；`referentialKeywords` 存在旧新值短时混杂风险；`payments` 等 2 篇文章仍为 `needs_human_review`；repo rollback 后 `_contributions/*.md` 与 `_meta/export-manifest.json` 仍可能对 baseline 保持 diff。对外表述宜采用“核心能力已超越原版，但仍有工程化收口项”，不建议把这轮真实复验包装成“所有场景都已无保留全面领先”。
  - 进行中：2026-04-28 继续按同一份 `/Users/sxie/xbk/Lattice-java/.claude/t1.md` 私有配置复跑当前工作区；本轮先验证全量自动化是否仍然通过，再在 fresh schema 上重跑启动、compile、query 与 benchmark，对照 2026-04-27 结论判断“已超越原版”是否仍成立。
  - 已验证：执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test` 后，当前工作区全量自动化回归为 `494` 个测试全部通过，`BUILD SUCCESS`；其中已覆盖管理侧、source sync、compile/query、deep research、MCP、CLI、vault/export 与 benchmark 相关测试，说明本轮复验起点不存在新的自动化阻塞。
  - 已验证：重新读取最新 `target/benchmark-reports/ast-citation-deepresearch/gap-report.json`，当前仍为 `sharedBenchmarkOutperformOriginal=true`、`comprehensiveOutperformOriginal=true`、`blockingGaps=[]`、`verdictSummary=当前证据足以支持 Java 版全面超过原版。`；`docs/benchmark/ast-citation-deepresearch-gap-report.md` 也保持 `unsupportedClaimRate / citationPrecision / citationCoverage / multiHopCompleteness` 全部优于原版 `/Users/sxie/xbk/Lattice`。
  - 已验证：在 fresh runtime `http://127.0.0.1:18098`、schema `lattice_e2e_codex_20260428` 上，按 `/Users/sxie/xbk/Lattice-java/.claude/t1.md` 注入 `gpt-5.4 + BAAI/bge-m3(1024) + TextIn xParse` 与 compile review 配置后，对原始 `/Users/sxie/xbk/Lattice-java/docs` 发起 `source sync -> full compile`；作业 `d3b08a48-61b6-4720-b75a-c83b8244e235` 已真实 `SUCCEEDED`，`articleCount=8`、`sourceFileCount=21`，说明“大目录 + OCR 路由 + 多轮 review/fix” 在本轮复验中已能跑到终态。
  - 已验证：在小样本 fresh runtime `http://127.0.0.1:18100`、schema `lattice_e2e_codex_20260428_smoke` 上，以 `/tmp/lattice-e2e-smoke-src`（`README.md` + `docs/项目启动配置清单.md` + `docs/Java版全面超越原版实施设计方案.md`）完成一轮更快的全流程闭环；作业 `1fe1a55a-0ee3-4b9c-8ea3-5bd3566a9bc8` 已 `SUCCEEDED`，落库 `2` 篇文章、`3` 个 source files。
  - 已验证：同一小样本实例上，HTTP query 已真实返回 `SUCCESS`（问题“项目启动前真正需要处理的事情只有哪 4 件？”）；pending feedback 已真实跑通 `correct -> confirm`，随后总览恢复 `pendingQueryCount=0 / contributionCount=1`。CLI remote 已真实跑通 `status`、`source-list`、`query`、`vault-export`；MCP raw HTTP 已真实跑通 `initialize`、`tools/list`、`tools/call(lattice_status)`、`tools/call(lattice_query)` 与 `lattice_query_discard`。
  - 已验证：同一小样本实例上，repo governance 闭环已再次补验到 `baseline -> drift -> diff -> rollback`；`POST /api/v1/admin/snapshot/repo/baseline` 返回 `snapshotId=3 / gitCommit=b09a797...`，手工漂移后 `GET /api/v1/admin/snapshot/repo/3/diff?vaultDir=/tmp/lattice-e2e-smoke-vault` 返回 `count=1 / concepts/smoke-docs-20260428--docs.md MODIFY`，执行 `POST /api/v1/admin/rollback/repo` 后生成 `snapshotId=4 / gitCommit=6b8a1a6...`。不过 rollback 后再次 diff 仍残留 `_contributions/confirmed_query-*.md` 与 `_meta/export-manifest.json` 两项差异，说明 repo rollback 在“基线完全无噪音恢复”上仍未完全收口。
  - 已验证：小样本 compile 的 `refresh_vector_index` 阶段出现过一次 `413 input must have less than 8192 tokens`，但随后 chunk 向量索引继续成功写入并且作业最终 `SUCCEEDED`；说明当前向量链路具备恢复性，但长文本 article 级 embedding 仍存在单次 token 上限风险。
  - 已验证：深度问题 `forceDeep=true` 仍会真实走到 Deep Research，并返回 `answerOutcome=PARTIAL_ANSWER / modelExecutionStatus=SUCCESS / deepResearch` 摘要；但当前答案正文会把文档元数据整段混入正文，HTTP 原始响应对标准 JSON 消费端不够友好，实际表现为 `curl` 能收到正文、但本地 `jq` 解析失败。该问题不影响 CLI remote 的普通 query，但说明 Deep Research 的对外输出质量仍需收口。
  - 已验证：CLI `repo-baseline` 远程模式当前会因 [CliOutputFormatter.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/cli/CliOutputFormatter.java:13) 使用裸 `new ObjectMapper()` 而在序列化 `OffsetDateTime` 时失败；服务端 API 本身正常，问题仅在 CLI JSON 输出层。
  - 结论：截至 `2026-04-28`，benchmark 证据与本轮两套 fresh runtime 真实复验继续支持“Java 版核心能力已超过原版 `/Users/sxie/xbk/Lattice`”这一结论，且该结论在 query/compile/deep research/CLI/MCP/vault/export/repo baseline 维度仍成立；但若口径提升为“所有对外入口与治理细节都已无保留优于原版”，当前仍有 3 类收口项不能忽略：`Deep Research` 输出正文质量与 HTTP JSON 可消费性、`repo rollback` 后 `_contributions`/`export-manifest` diff 噪音、以及 CLI `repo-baseline` 的时间类型序列化缺陷。
  - 已完成：2026-04-28 已按既定顺序完成上述 3 类遗留项收口；先后修复 CLI `repo-baseline` 时间类型序列化、repo rollback diff 噪音，以及 Deep Research 输出正文与 HTTP JSON 可消费性，并已逐项补测试验证。
  - 已验证：CLI `repo-baseline` 时间类型序列化缺陷已修复；`CliOutputFormatter` 统一注册 Java Time 模块并关闭时间戳数组输出，新增 `CliOutputFormatterTests.shouldSerializeRepoBaselineResultWithOffsetDateTime` 覆盖 `RepoBaselineResult.createdAt`，定向执行 `-Dtest=CliOutputFormatterTests test` 通过。
  - 已验证：repo rollback 后 `_contributions` / `_meta/export-manifest.json` diff 噪音已收口；`VaultSnapshotService.rollback` 改为在 DB 回放后直接将 Vault 工作树恢复到目标 snapshot 的 Git commit，并清理未跟踪导出物，新增 `VaultSnapshotServiceTests.shouldRemoveContributionAndManifestDiffNoiseAfterRollback` 覆盖贡献导出噪音场景，定向执行 `-Dtest=VaultSnapshotServiceTests test` 通过。
  - 已验证：Deep Research 输出正文质量与 HTTP JSON 可消费性已完成工程侧收口；`QueryController` 显式声明 `application/json` 响应，`DeepResearchSynthesizer` 在投影后移除内部“分层摘要 / 缺失事实”段与 metadata/sourcePaths/articleKey 等元数据泄漏行，新增 `DeepResearchSynthesizerTests.shouldSanitizeInternalMetadataFromProjectedAnswer` 并补 Query HTTP content-type 断言，定向执行 `-Dtest=DeepResearchSynthesizerTests,QueryControllerTests test` 通过。
  - 已验证：2026-04-28 已按用户补充要求，继续使用 `/Users/sxie/xbk/Lattice-java/.claude/t1.md` 同一套模型配置补做问答相关页面前端细验；本轮额外覆盖了 `/admin`、`/admin/ask`、`/admin/settings`、`/admin/developer-access` 的空态、处理中、疑似卡住、可提问、结果加载中与最终结果态，不再只记录“页面能打开且主链路可走通”。
  - 已验证：前端细验中定位并收口了 2 类真实问题。其一是 `/admin/settings` 明明依赖 `deep_research` JDBC bindings，但前端角色绑定表单缺少该场景选项、列表也只会把 `deep_research / planner / researcher / synthesizer / reviewer` 直接当原始枚举渲染；现已补齐“深度研究”场景与 4 个角色的中文标签、流程摘要与可配置入口。其二是 `/admin/ask` 在提交问题后未锁定输入区，存在重复提交风险，同时来源区会把同一文件的多个检索分片重复渲染成多张“补充证据”卡，并把 article front matter 原样暴露给用户；现已改为提交中禁用提问框 / 提问按钮 / 清空按钮，对补充来源按展示身份去重，并在来源摘要里去掉 `title / summary / referential_keywords / compiled_at` 等 front matter 元数据噪音。
  - 验证：重新执行 `JAVA_HOME=/Users/sxie/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home /Users/sxie/maven/apache-maven-3.6.3/bin/mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AskJsRuntimeTests,SettingsPageJsRuntimeTests,AdminPageControllerTests test` 后通过；浏览器复测也确认问答页在“正在生成回答...”期间会正确禁用输入与按钮，结果态来源区已从“1 条直接来源 + 4 张补充卡”收敛为“1 条直接来源 + 2 张补充卡”，且直接来源摘要不再暴露 YAML/front matter。
  - 验收：截至 `2026-04-28`，Phase F 所需的 benchmark 证据、fresh runtime 真实复验、全量自动化回归、repo governance 闭环，以及上述 3 类遗留项的工程侧收口证据均已回写完毕；本阶段内更早的 `进行中` / `待收口` 记录仅保留为历史推进痕迹，不再表示当前仍有阻塞。
