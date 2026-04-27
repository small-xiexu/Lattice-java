# Java 版全面超越原版实施设计方案

**文档版本**：v1.1  
**编写日期**：2026-04-26  
**适用范围**：`/Users/sxie/xbk/Lattice-java`  
**设计目标**：让 `Lattice-java` 在查询质量、复杂问题准确性、运行可恢复性、评测闭环与平台治理能力上，全面超过 `/Users/sxie/xbk/Lattice`

---

## 1. 这份文档要回答什么

这份文档不回答“Java 和 TypeScript 谁更强”这种抽象问题，而是直接回答下面 4 个工程问题：

1. 当前 `Lattice-java` 为什么还没有在所有维度上全面超过原版。
2. 真正的差距到底在哪里，哪些是查询内核问题，哪些是执行态问题，哪些只是默认配置没打开。
3. 要全面超过原版，应该按什么架构思路改，先做什么，后做什么。
4. 如何定义“已经全面超过”，以及后续应该按什么指标验收。

一句话结论先写在前面：

- 当前差距的主因不是语言限制。
- 当前差距的主因是 **原版把更多精力花在检索召回与查询前处理上，而 Java 版把更多精力花在平台化与治理层上**。
- 所以 Java 版下一步的关键不是“证明 Java 能做”，而是 **把原版已经验证有效的查询内核经验，与 Java 版已经领先的平台能力真正合体**。

本方案额外采用一个明确前提：

- **按新项目重构口径推进，不考虑兼容旧数据、旧检索链路、旧运行时状态或旧升级路径。**
- **与原版的比较只用于离线对标和设计取长，不构成运行期双写、双读、平滑迁移或在线兼容要求。**

---

## 2. 当前基线判断

### 2.1 Java 版已经明显领先的部分

- 资料源、同步运行、解析连接、解析路由、模型中心、执行快照、答案审计、Deep Research 审计都已经是正式对象，不再是本地文件或运行时临时状态。
- 编译、问答、治理、Vault、MCP、Admin 已经形成统一后端，而不是若干脚本和本地命令的集合。
- `AST / Citation / Deep Research` 共享题集对标已经跑通，当前离线结果优于原版。

### 2.2 Java 版还没有完全超过的部分

- 查询召回内核还不够锋利，尤其是 query rewrite、chunk 级 lexical 召回、source / contribution 的数据库化检索还没有完全补齐。
- 查询工作集、Deep Research 工作集、编译工作集当前仍以进程内存实现为主，平台层虽然已经为恢复和审计铺好了数据模型，但真正的“跨实例接力 / 崩溃恢复 / 长任务恢复”还没有完全落地。
- 向量能力、图谱能力、并行召回虽然已经进入设计与实现，但默认收益尚未全部释放。
- 评测结论目前仍以共享 shadow benchmark 为主，离“真实业务问法分布下稳定领先”还有一层真实回放与 A/B 验证要做。

### 2.3 不是语言限制的原因

当前代码已经证明 Java 并不存在根本性能力上限：

- Query Graph 已经支持并行召回分发，而不是只能串行跑。
- Deep Research 已经支持单独场景路由、计划、执行快照冻结和审计落盘。
- PostgreSQL、Redis、Spring AI Graph、JDK 21 虚拟线程都足以支撑比原版更强的工程实现。

也就是说，当前未超越的部分不是“Java 做不到”，而是“Java 版还没有把查询内核打磨完”。

### 2.4 必须作为设计起点的真实代码骨架

这份方案后续所有改造，都必须从当前已经存在的类、图节点、表结构长出来，而不是另画一套脱离项目现实的架构图。

#### 2.4.1 Query 主链真实骨架

当前 `QueryGraphDefinitionFactory` 已经真实定义了下面这条问答主链：

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

这意味着 Query 侧不需要推翻重写 orchestrator，真正需要改的是：

- 在 `normalize_question` 与 `check_cache` 之间补 Query Preparation 能力。
- 在 `dispatch_retrieval` 之后补新的召回通道与检索审计。
- 在 `fuse_candidates` 之前把“静态加权”升级为“意图驱动策略加权”。

同时，`QueryGraphState` 已经具备大量工作集引用字段：

- `ftsHitsRef`
- `refkeyHitsRef`
- `sourceHitsRef`
- `contributionHitsRef`
- `graphHitsRef`
- `articleVectorHitsRef`
- `chunkVectorHitsRef`
- `fusedHitsRef`
- `draftAnswerRef`
- `reviewResultRef`
- `citationCheckReportRef`

这说明方案不应该重新发明一套状态模型，而应该在现有 `QueryGraphState` 上直接补字段，例如：

- `rewrittenQuestion`
- `rewriteAuditRef`
- `queryIntent`
- `retrievalStrategyRef`
- `articleChunkHitsRef`
- `sourceChunkHitsRef`
- `retrievalAuditRef`

#### 2.4.2 Knowledge Search 真实融合入口

当前 `KnowledgeSearchService` 已经是一个很关键的真实落点：

- 它直接顺序调用 `FtsSearchService`、`RefKeySearchService`、`SourceSearchService`、`ContributionSearchService`、`GraphSearchService`、`VectorSearchService`、`ChunkVectorSearchService`。
- 它最后统一走 `RrfFusionService` 融合。
- 它当前把 `refkey` 的权重直接复用了 `ftsWeight`。

这意味着：

- `KnowledgeSearchService` 适合作为“新检索策略”的第一落地入口。
- 即使 Query Graph 还没全部改完，也可以先在这里完成新通道接入、策略加权和 audit 验证。
- `refkey` 独立权重、chunk lexical 通道、动态权重解析器，最自然的第一站就是这里。

#### 2.4.3 Deep Research 真实骨架

当前 `DeepResearchGraphDefinitionFactory` 已经是按 `LayeredResearchPlan` 动态建图，而不是固定流程硬编码。真实执行节点包括：

- `initialize_plan`
- 每层多个 `executeResearchTask`
- 每层一个 `summarizeLayer`
- `synthesize_answer`

它当前已经真实保存了：

- task results
- evidence ledger
- layer summary
- projection bundle
- citation check report

所以 Deep Research 设计重点不应再停留在“要不要分层研究”，而是要直接回答：

- 每个 `executeResearchTask` 结束后往哪里写 checkpoint。
- `summarizeLayer` 后如何持久化 layer 级恢复点。
- `synthesize_answer` 后如何保留可恢复草稿、projection 与 citation report。

#### 2.4.4 Compile 主链真实骨架

当前 `CompileGraphDefinitionFactory` 已经定义了完整编译主链：

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

因此 Compile 侧最优方案不是改图拓扑，而是：

- 保留现有图顺序与节点职责。
- 直接替换 `CompileWorkingSetStore` 的实现。
- 在现有节点边界上补版本化工作集、checkpoint 和恢复指针。

#### 2.4.5 当前最该替换的短板实现

当前最明显的“真实短板类”已经很清楚：

- `FtsSearchService`：当前只做最小 article FTS，没有 article chunk FTS。
- `RefKeySearchService`：当前没有直接利用好 `articles.referential_keywords`，而是先把文章捞出来再内存打分。
- `SourceSearchService`：当前基于 `source_file_chunks.findAll()` 做全量内存扫描。
- `ContributionSearchService`：当前基于 `contributionJdbcRepository.findAll()` 做全量内存扫描。
- `QueryTokenExtractor`：当前 token 提取偏轻量，尚未覆盖路径、类名、枚举值、配置键等更稳定的结构化 token。
- `SourceFileChunkJdbcRepository`：当前只有 `findAll()` / `findByFilePaths()`，缺少数据库侧检索方法。
- `ContributionJdbcRepository`：当前只有 `findAll()`，缺少 FTS/精确检索方法。

另外，表结构层已经具备可以直接扩展的真实基础：

- `articles` 已有 `referential_keywords`
- `source_files` 已有 `file_path`、`relative_path`、`content_text`
- `article_chunks` 已是正式表
- `source_file_chunks` 已是正式表
- `contributions` 已是正式表

这意味着 Phase A 的正确方向不是“先造新表”，而是优先扩展现有主表与现有 JDBC Repository。

---

## 3. 当前差距拆解

## 3.1 检索召回差距

原版当前查询内核已经具备以下特点：

- 查询重写先行，能把简称、缩写、业务代号扩成更可检索的表达。
- article 级和 chunk 级 lexical 召回并存，能缓解长文档分数稀释。
- source file 级和 source chunk 级 lexical 召回并存，能把长源文件中后半段内容拉回来。
- 向量检索只在 query 有真实语义内容时触发，避免纯停用词查询引入泛化噪声。
- refkey 通道拥有独立且更高的权重，而不是与普通 FTS 复用同一个权重。

Java 版当前虽然已经有多通道融合框架，但存在下面这些具体差距：

- `FtsSearchService` 目前仍是最小化 article FTS，尚未覆盖 article chunk FTS。[src/main/java/com/xbk/lattice/query/service/FtsSearchService.java]
- `SourceSearchService` 当前是 `findAll()` 后在内存中做 token 匹配，不是数据库侧索引检索。[src/main/java/com/xbk/lattice/query/service/SourceSearchService.java]
- `ContributionSearchService` 当前同样是全表扫后内存打分，不适合作为增长后的长期方案。[src/main/java/com/xbk/lattice/query/service/ContributionSearchService.java]
- `KnowledgeSearchService` 当前把 `refkey` 权重直接复用了 `ftsWeight`，没有保留原版“明确性命中高于普通全文命中”的独立权重语义。[src/main/java/com/xbk/lattice/query/service/KnowledgeSearchService.java]
- 当前尚未形成正式的 `query rewrite` 子系统。

结论：

- 如果不先补齐检索召回内核，Java 版即使平台再强，也只能做到“更可运营”，很难做到“答案稳定更强”。

## 3.2 执行恢复差距

Java 版当前的编译、问答、Deep Research 都已经有正式状态对象和审计表，但跨节点工作集仍主要依赖内存实现：

- `InMemoryCompileWorkingSetStore`
- `InMemoryQueryWorkingSetStore`
- `InMemoryDeepResearchWorkingSetStore`

这带来 3 个问题：

- 进程重启后，长任务无法无损恢复。
- 多实例部署时，执行上下文无法天然迁移。
- 当前“Graph 可视化执行”与“平台级恢复能力”之间还存在最后一段落地差距。

结论：

- 这块并不会立刻拉低离线准确性，但会限制 Java 版在生产可靠性上真正拉开差距。

## 3.3 评测闭环差距

当前 Java 版已经具备一个很好的起点：

- 共享题集对标 runner 已存在。
- gap report 已存在。
- 引用、Deep Research、graph grounding 已能做 shadow compare。

但如果目标是“全面超过原版”，还缺少下面这层：

- 真实业务问题集。
- 真实回放集。
- 检索层 recall 评测。
- 离线版本对比。
- 按问题类型做误差归因。

结论：

- 现在能证明“共享 benchmark 上已经领先”。
- 还不能只靠现有 benchmark 证明“所有真实场景都已经领先”。

---

## 4. 设计目标

本次方案把“全面超过原版”拆成 5 个并列目标。

### 4.1 查询质量目标

- 普通问答的召回质量不低于原版，并在复杂长文本场景明显优于原版。
- 对简称、业务代号、配置键、Java 类名、路径、枚举值、数值编码的命中率高于原版。
- 多跳问题与对比题的回答完整性高于原版。

### 4.2 证据与准确性目标

- unsupported claim 更少。
- citation precision 不低于原版。
- citation coverage 不低于原版。
- graph grounding 在 Java 版特有 channel 中继续保持高接受率。

### 4.3 恢复与可靠性目标

- Query / Deep Research / Compile 三条主链在实例重启后都能恢复或安全失败，而不是静默丢失。
- 多实例场景下，长任务不依赖单机内存。

### 4.4 平台默认收益目标

- 新能力不是“支持即可”，而是默认配置下就能带来比原版更强的收益。
- 向量、图谱、chunk lexical、query rewrite、并行召回要成为默认主链的一部分，而不是长期停留在开关后面。

### 4.5 验收与运营目标

- 离线评测可复现。
- 回归失败可定位到通道、阶段、模型、问题类型。
- 离线回放期间可对比不同版本检索结果与最终答案。

---

## 5. 非目标

为了避免范围失控，这次方案明确不把下面这些内容作为首期目标：

- 不在第一阶段引入复杂学习排序器或在线 reinforcement 机制。
- 不在第一阶段引入外部独立搜索集群。
- 不在第一阶段做跨语言统一代码索引平台。
- 不把“让所有 Deep Research 都走最重模型”当作目标，成本必须受控。
- 不为旧数据、旧表结构、旧工作集、旧查询链路设计兼容层。
- 不提供平滑升级、双写迁移、在线兼容回放或历史运行态续接方案。

### 5.1 重构边界

本方案按“新项目重构”执行，允许直接做下面这些动作：

- 直接替换现有检索实现，不保留旧检索路径作为长期 fallback。
- 直接新增或重建检索列、检索索引、向量索引与审计表。
- 直接切换 Query / Deep Research / Compile 的工作集存储实现。
- 对旧实验开关、旧临时仓储、旧中间态表述进行清理，不为其保留长期兼容语义。

本方案默认不做下面这些事情：

- 不设计旧数据迁移脚本。
- 不承诺旧 schema 数据原地升级。
- 不承诺旧 pending、旧 working set、旧 cache 的跨版本可恢复。
- 不要求新链路与旧链路在同一运行时长期并存。

---

## 6. 目标架构总览

全面超越原版的目标架构，核心不是“重写整个系统”，而是在现有 Java 平台上补 4 层能力：

1. `Retrieval Core V2`
2. `Recoverable Execution V2`
3. `Evaluation Loop V2`
4. `Default Capability Rollout`

可以把未来的 Query 主链理解成下面这条路径：

`问题规范化 -> Query Rewrite -> 意图识别 -> 多通道并行召回 -> 通道级审计 -> 动态 RRF 融合 -> 证据投影 -> 答案生成/审查/修复 -> 审计落盘`

其中最关键的变化是：

- Query 不再只看“搜到了什么”，而是要看“为什么搜到、漏了什么、应该补什么”。
- Deep Research 不再只看“生成了什么”，而是要保证执行上下文和中间证据可恢复、可追踪、可复盘。

## 6.1 基于当前 Query Graph 的目标改造落点

当前真实链路是：

`normalize_question -> check_cache -> dispatch_retrieval -> retrieve_* -> fuse_candidates -> answer_question -> review_answer -> rewrite_answer -> claim_segment -> citation_check -> citation_repair -> persist_response -> finalize_response`

推荐改造成：

`normalize_question -> rewrite_query -> classify_intent -> resolve_retrieval_strategy -> check_cache -> dispatch_retrieval -> retrieve_* -> audit_retrieval -> fuse_candidates -> answer_question -> review_answer -> rewrite_answer -> claim_segment -> citation_check -> citation_repair -> persist_response -> finalize_response`

具体设计判断如下：

- `normalize_question` 不再只做 `trim()` 与 LLM scope 初始化，而是保留“轻清洗”职责。
- `rewrite_query` 独立成节点，而不是塞回 `normalize_question`，原因是 rewrite 需要单独 audit、单独缓存、单独恢复。
- `classify_intent` 与 `resolve_retrieval_strategy` 也建议单独成节点，因为它们直接影响通道启停与权重，不适合藏在 `KnowledgeSearchService` 内部变成黑盒。
- `audit_retrieval` 建议放在 `fuse_candidates` 前，保留融合前全量候选；否则后面只能看到融合结果，无法定位是哪一路漏召回。

检索节点层建议的具体处理方式：

- `retrieve_fts`：保留节点名，职责收敛为 article 级 FTS。
- `retrieve_refkey`：保留节点名，改成直接查询 `articles.referential_keywords`、`concept_id`、标题与结构化字段。
- `retrieve_source`：保留节点名，但职责调整为 source path / class name / relative path / endpoint path 命中。
- 新增 `retrieve_article_chunk_fts`：承接 article chunk lexical 通道。
- 新增 `retrieve_source_chunk_fts`：承接 source chunk lexical 通道。
- `retrieve_contribution`：保留节点名，底层改为数据库侧检索。
- `retrieve_graph`、`retrieve_article_vector`、`retrieve_chunk_vector`：保留现有节点，作为后续策略化通道继续参与。

这套方案的好处是：

- 与当前 Graph 骨架连续，不需要重写 orchestrator。
- 新增能力能在 graph audit 中被直接观测。
- 后续做恢复时，可以按节点粒度恢复，而不是把多个动作糊在一个节点里。

## 6.2 基于当前 Deep Research Graph 的目标改造落点

Deep Research 当前已经是“按 plan 动态建图”，所以这里不建议大改图模型，而建议补“层级恢复点”。

推荐的落点如下：

- `initialize_plan`
  - 除了设置 `currentLayerIndex = 0`，还要立即写入 plan checkpoint。
- `executeResearchTask`
  - 继续复用 `deepResearchWorkingSetStore.saveTaskResults(...)`。
  - 每个 task 成功后额外写 `task checkpoint`，记录本层、本任务、证据卡数量、预算剩余。
- `summarizeLayer`
  - 继续复用 `saveEvidenceLedger`、`saveLayerSummary`。
  - 在现有 `layerSummaryRef`、`ledgerRef` 写完后，补 `layer checkpoint`，作为恢复起点。
- `synthesize_answer`
  - 继续复用 `queryWorkingSetStore.saveAnswer(...)`、`saveCitationCheckReport(...)`、`saveAnswerProjectionBundle(...)`。
  - 在最终投影和 citation report 生成后，写 `final checkpoint`。

对应 `DeepResearchState` 建议补字段：

- `checkpointRef`
- `researchStrategyRef`
- `resumeFromLayerIndex`
- `lastCompletedTaskSlot`

这意味着 Deep Research V2 的核心不是再造一个新 orchestrator，而是让当前已经存在的 task/layer/ledger/projection 存储点变成真正的恢复锚点。

## 6.3 基于当前 Compile Graph、Repository 与表结构的目标改造落点

Compile 侧建议“图尽量不动，状态实现重构”。

具体原因很明确：

- 当前 `CompileGraphDefinitionFactory` 的节点切分已经足够细。
- `CompileGraphState` 已经有大量 `*Ref` 字段，例如 `rawSourcesRef`、`groupedSourcesRef`、`sourceBatchesRef`、`reviewedArticlesRef`、`acceptedArticlesRef`。
- 当前真正的短板不是节点不够，而是这些引用背后的工作集存储还主要停留在内存。

因此建议：

- `initialize_job`：初始化 Redis working set 根 key 与版本号。
- `ingest_sources` / `persist_source_files` / `persist_source_file_chunks`：每步结束时刷新 job checkpoint，并记录本步产出的引用版本。
- `group_sources` / `split_batches` / `analyze_batches` / `merge_concepts`：保留图结构，只把工作集载荷从内存替换成 Redis。
- `plan_changes` / `enhance_existing_articles` / `compile_new_articles` / `review_articles` / `fix_review_issues`：重点补“重复执行幂等语义”和“最近成功节点恢复”。
- `persist_articles` / `rebuild_article_chunks` / `refresh_vector_index`：与现有 `ArticleChunkJdbcRepository`、向量索引仓储直接对接，不另造第二套索引流程。

数据侧的真实落点也已经存在：

- `SourceFileJdbcRepository`
- `SourceFileChunkJdbcRepository`
- `ArticleChunkJdbcRepository`
- `ContributionJdbcRepository`

所以 Compile 相关设计不应该抽象地写“重建索引层”，而应明确为：

- 在现有 `persist_source_files` 节点扩展 `source_files` 检索列。
- 在现有 `persist_source_file_chunks` 节点扩展 `source_file_chunks` 检索列。
- 在现有 `rebuild_article_chunks` 节点扩展 `article_chunks` 检索列。
- 在现有 `refresh_vector_index` 节点继续刷新向量索引，不新开平行链路。

---

## 7. Retrieval Core V2 设计

## 7.1 设计原则

- 先保证召回，再谈生成。
- 先用确定性召回扩大覆盖，再用模型做高成本语义补足。
- 明确性命中优先于模糊性命中。
- 源文件与文章都必须支持长文本内部命中，而不是只靠整文打分。
- 通道质量必须可度量，不能只看最终答案。

## 7.2 Query 预处理层

新增 `Query Preparation` 子层，职责拆成 4 部分：

- 规范化：清洗空白符、中英文标点、大小写、常见路径格式。
- token 提取 V2：替换当前偏轻量的 `QueryTokenExtractor`，补齐更稳的中英文、路径、类名、接口路径、snake_case、枚举值、业务码提取能力。
- query rewrite：把简称、别名、代号、拼音缩写、口语问法扩成可检索表达。
- 意图识别：区分“配置查询 / 调用链查询 / 对比题 / 原因题 / 枚举穷举题 / 错误排查题”，驱动后续通道权重。

结合当前代码，Query Preparation 不应该另起一条平行链，而应直接落在：

- `QueryGraphDefinitionFactory`
- `QueryGraphState`
- `QueryTokenExtractor`
- `KnowledgeSearchService`

推荐的具体改法是：

- 把当前 `normalize_question` 节点保留为“规范化入口”。
- 新增 `rewrite_query`、`classify_intent`、`resolve_retrieval_strategy` 三个 graph 节点。
- `QueryTokenExtractor` 升级为 Query Preparation 的基础工具，而不是只给 source / contribution 内存扫描使用。
- `KnowledgeSearchService` 改成接受 `normalizedQuestion + rewrittenQuestion + queryIntent + strategy` 的融合入口，而不是只接受原始 `question`。

### 7.2.1 Query Rewrite 设计

Rewrite 分两层：

- 第一层：规则层
  - 资料源级缩写词典
  - 业务码别名字典
  - 技术术语同义词
  - Java 符号与中文叫法映射
- 第二层：LLM 层
  - 只在“短问题、歧义问题、缩写问题”上触发
  - 输出必须是结构化 rewrite payload
  - 重写结果必须缓存并带版本

首期目标不是“让模型自由改写问题”，而是：

- 以确定性规则为主。
- 模型只做低风险扩展，不做语义漂移式重写。

从当前项目出发，Rewrite 的真实落点建议是：

- 规则表：`query_rewrite_rules`
- 服务：`QueryRewriteService`
- 状态落点：`QueryGraphState.rewriteAuditRef`
- 缓存落点：沿用当前 query working set，而不是另造一套文件缓存

同时，当前 `QueryTokenExtractor` 建议直接升级以下能力：

- 路径 token：`/api/order/create`、`com/xbk/lattice/...`
- Java token：类名、方法名、枚举值、配置前缀
- 混合 token：`spring.ai.alibaba.graph`、`sa-token`、`text-embedding-3-small`
- 中文技术短语：避免仅用 2-4 字滑窗覆盖

### 7.2.2 新增数据对象

建议新增：

- `query_rewrite_rules`
  - 存业务别名、缩写、映射目标、作用域、优先级。
- `query_rewrite_audits`
  - 存某次 query 命中的 rewrite 规则、最终扩展文本、是否触发 LLM rewrite。

## 7.3 多通道并行召回层

Java 版当前已有并行召回骨架，这一层不重做编排，只增强通道实现。

目标通道如下：

| 通道 | 当前状态 | 目标状态 |
| --- | --- | --- |
| article FTS | 已有最小实现 | 升级为带索引列、带权重、可解释命中 |
| article chunk lexical | 缺失 | 新增 |
| refkey exact | 已有 | 独立权重、精确匹配增强 |
| source file lexical | 内存扫 | 改成数据库索引检索 |
| source chunk lexical | 缺失 | 新增 |
| contribution lexical | 内存扫 | 改成数据库索引检索 |
| graph retrieval | 已有 | 继续保留并扩展召回解释 |
| article vector | 已有 | 作为默认主链可控启用 |
| chunk vector | 已有 | 与 lexical chunk 共同组成深召回层 |

## 7.3.1 基于当前类与节点的通道改造映射

| 当前类/节点 | 当前现状 | 目标改造 |
| --- | --- | --- |
| `retrieve_fts` / `FtsSearchService` | 仅 article FTS | 保留节点，升级为 article 主文 FTS，并补 explainable score 字段 |
| 新增 `retrieve_article_chunk_fts` | 当前缺失 | 新增 article chunk lexical 通道，直接依赖 `article_chunks` |
| `retrieve_refkey` / `RefKeySearchService` | 全量加载文章后内存打分 | 改为直接查询 `articles.referential_keywords`、`concept_id`、标题与结构化字段 |
| `retrieve_source` / `SourceSearchService` | `source_file_chunks.findAll()` 后内存打分 | 调整为 source path / relative path / class name / endpoint path 命中通道 |
| 新增 `retrieve_source_chunk_fts` | 当前缺失 | 新增 source chunk FTS，直接命中 `source_file_chunks.chunk_text` |
| `retrieve_contribution` / `ContributionSearchService` | `contributions.findAll()` 后内存打分 | 改为 question / answer / corrections 的数据库侧检索 |
| `retrieve_graph` / `GraphSearchService` | 已有 | 保留，并在 audit 中记录命中 relation / entity 原因 |
| `retrieve_article_vector` / `VectorSearchService` | 已有 | 保留，交给策略层决定是否启用 |
| `retrieve_chunk_vector` / `ChunkVectorSearchService` | 已有 | 保留，与 article/source chunk lexical 共同参与深召回 |
| `fuse_candidates` / `RrfFusionService` | 静态权重 | 升级为策略化权重 + 融合前后审计 |

## 7.4 数据库存储与索引设计

当前 `articles`、`article_chunks`、`source_file_chunks`、`contributions` 都是可用表，但检索列和索引能力不完整。

结合当前 schema 与 repository，推荐按“扩展现有表”而不是“新建平行检索表”来做：

- `articles`
  - 继续作为 article FTS 与 refkey 检索主表。
  - 直接利用已有 `referential_keywords`，不再只在 Java 内存里做 token 比较。
- `article_chunks`
  - 继续由现有 `rebuild_article_chunks` 节点负责重建。
  - 由 `ArticleChunkJdbcRepository` 扩展 FTS 查询方法。
- `source_files`
  - 用于路径、相对路径、类名、配置文件名、接口路径等“文件级命中”。
  - 由 `SourceFileJdbcRepository` 扩展检索方法。
- `source_file_chunks`
  - 用于 source chunk lexical。
  - 由 `SourceFileChunkJdbcRepository` 扩展 FTS 查询方法，而不是继续 `findAll()`。
- `contributions`
  - 用于 question / answer / corrections 的结构化检索。
  - 由 `ContributionJdbcRepository` 扩展 question / answer / corrections FTS。

建议新增或补齐下面这些检索列：

- `articles`
  - `search_text`
  - `search_tsv`
  - `refkey_text`
- `article_chunks`
  - `search_tsv`
- `source_files`
  - `file_path_norm`
  - `search_tsv`
- `source_file_chunks`
  - `search_tsv`
  - `file_path_norm`
- `contributions`
  - `question_tsv`
  - `answer_tsv`
  - `corrections_tsv`

建议配套索引：

- `GIN(search_tsv)` 用于 article / article chunk / source chunk / contribution FTS。
- `GIN(referential_keywords)` 或等价精确匹配索引，用于 refkey 命中。
- `btree(file_path_norm)` 或 `pg_trgm`，用于路径、类名、接口路径命中。
- 保留并继续使用 pgvector 相关索引。

设计原则：

- 尽量把召回计算下推到 PostgreSQL。
- 避免持续依赖 `findAll()` 后 Java 内存打分。
- 查询层的主要 CPU 时间应花在“融合与解释”，而不是“全表扫描”。

与现有编译链路的关系也要写清楚：

- `persist_source_files` 负责回填 `source_files` 相关检索列。
- `persist_source_file_chunks` 负责回填 `source_file_chunks.search_tsv`。
- `rebuild_article_chunks` 负责回填 `article_chunks.search_tsv`。
- `persist_articles` 或其下游补齐 `articles.search_tsv`、`articles.refkey_text`。

也就是说，索引建设不是额外旁路任务，而是直接挂接当前 Compile Graph 的既有节点。

## 7.5 召回打分与动态权重

当前 Java 版已经有 `QueryRetrievalSettingsState`，但权重还偏静态，且 `refkey` 仍复用 `ftsWeight`。

目标是引入 `Retrieval Strategy Resolver`：

- 输入：
  - query intent
  - query token features
  - 是否包含业务码 / 路径 / Java 符号 / 比较词 / 排查词
- 输出：
  - 本次启用的通道列表
  - 每个通道的权重
  - RRF K
- 是否允许向量通道参与
- 是否需要 query rewrite

从当前实现出发，`QueryRetrievalSettingsState` 本身也应扩展，而不是让所有动态参数散落在 service 内部。建议新增：

- `refkeyWeight`
- `articleChunkWeight`
- `sourceChunkWeight`
- `rewriteEnabled`
- `intentAwareVectorEnabled`

建议的默认权重思路：

- 业务码 / 配置键 / 枚举值问题：
  - `refkey` > `source chunk lexical` > `article chunk lexical` > `vector`
- 调用链 / 类职责问题：
  - `graph` + `source chunk lexical` + `article chunk lexical`
- 对比题 / 原因题：
  - `article FTS` + `article chunk lexical` + `graph` + `contribution`
- 宽泛概念题：
  - `article FTS` + `article vector` + `chunk vector`

## 7.6 通道级审计

新增 `Retrieval Audit` 能力，不只记录最终答案，还要记录：

- 哪些通道命中了。
- 每个通道前 N 个候选是什么。
- 最终融合前后排名如何变化。
- 哪些期望命中被漏掉了。

建议新增：

- `query_retrieval_runs`
- `query_retrieval_channel_hits`

同时明确当前代码落点：

- Graph 入口：新增 `audit_retrieval` 节点
- Service 入口：`KnowledgeSearchService`
- 状态落点：`QueryGraphState.retrievalAuditRef`
- 存储入口：新增 `QueryRetrievalAuditJdbcRepository`

这样后续可以回答下面这些高价值问题：

- 某个问题是 FTS 漏召回，还是 refkey 没命中，还是 vector 误召回。
- 某类问题是否应该把 `graphWeight` 拉高。
- 某轮改造是否真的提升了 recall@k，而不是只改变了答案措辞。

---

## 8. Recoverable Execution V2 设计

## 8.1 总体目标

把当前三套内存工作集替换为“Redis 主存储 + PostgreSQL 审计落盘”的混合模型：

- Compile 工作集可恢复。
- Query 工作集可恢复。
- Deep Research 工作集可恢复。

### 8.1.1 为什么不是直接全放 PostgreSQL

- Graph 节点之间会传大量中间载荷，全部落 PostgreSQL 会引入频繁 JSON 读写和事务噪声。
- Redis 更适合保存短生命周期工作集。
- PostgreSQL 继续负责审计、快照、最终结果、可追踪历史。

因此建议的职责分工是：

- Redis：运行中的工作集、节点载荷、租约、恢复指针。
- PostgreSQL：作业主表、步骤日志、审计快照、恢复元信息。

## 8.2 Compile Working Set 改造

替换：

- `InMemoryCompileWorkingSetStore`

新增：

- `RedisCompileWorkingSetStore`

要求：

- 每类 payload 独立 key。
- job 级 TTL 可续租。
- 节点写入必须版本化，避免重复消费时读到旧版本。
- 任务失败或完成后延迟回收，而不是立即硬删除，便于故障排查。

结合当前 Compile Graph，恢复粒度直接绑定现有节点，而不是抽象到“某一步”：

- `rawSourcesRef`：对应 `ingest_sources`
- `groupedSourcesRef`：对应 `group_sources`
- `sourceBatchesRef`：对应 `split_batches`
- `mergedConceptsRef`：对应 `merge_concepts`
- `reviewedArticlesRef` / `reviewPartitionRef`：对应 `review_articles`
- `acceptedArticlesRef`：对应 `persist_articles` 前后

推荐的恢复策略是：

- 如果最近成功节点在 `merge_concepts` 之前，从最近 `*Ref` 恢复并继续。
- 如果最近成功节点在 `persist_articles` 之后，必须保证持久化幂等，避免重复写文章与重复重建 chunks。

## 8.3 Query Working Set 改造

替换：

- `InMemoryQueryWorkingSetStore`

新增：

- `RedisQueryWorkingSetStore`

要求：

- 检索命中、融合结果、草稿答案、review 结果、citation check report 都能按 queryId 恢复。
- Query Graph 如果在 `review -> rewrite -> citation repair` 中途重启，应能从最近成功节点恢复，而不是整题重跑。

结合当前 `QueryGraphState`，首期恢复对象应直接覆盖已有字段：

- `ftsHitsRef`
- `refkeyHitsRef`
- `sourceHitsRef`
- `contributionHitsRef`
- `graphHitsRef`
- `articleVectorHitsRef`
- `chunkVectorHitsRef`
- `fusedHitsRef`
- `draftAnswerRef`
- `reviewResultRef`
- `citationCheckReportRef`

如果 Phase A/B 已新增新通道，还要同步覆盖：

- `articleChunkHitsRef`
- `sourceChunkHitsRef`
- `retrievalAuditRef`

## 8.4 Deep Research Working Set 改造

替换：

- `InMemoryDeepResearchWorkingSetStore`

新增：

- `RedisDeepResearchWorkingSetStore`
- `DeepResearchCheckpointService`

要求：

- plan、layer summary、task results、evidence ledger、projection bundle 全部可恢复。
- 每一层 researcher 任务结束后写 checkpoint。
- 重启后可从最近完成层继续，而不是从 planner 重新开始。

结合当前 `DeepResearchGraphDefinitionFactory`，checkpoint 绑定现有节点：

- `initialize_plan`：写 plan checkpoint
- `executeResearchTask`：写 task checkpoint
- `summarizeLayer`：写 layer checkpoint
- `synthesize_answer`：写 final checkpoint

对应 `DeepResearchState` 首期必须确保可恢复的字段包括：

- `planRef`
- `taskResultRefs`
- `ledgerRef`
- `currentLayerIndex`
- `layerSummaryRefs`
- `internalAnswerDraftRef`
- `projectionRef`
- `citationCheckReportRef`

## 8.5 恢复策略

按场景区分：

- Compile：
  - 优先恢复。
  - 如果 payload 损坏，则回退到失败并保留错误摘要。
- Query：
  - 短任务优先快速重跑。
  - 只有进入 review/rewrite/citation repair 后才走恢复。
- Deep Research：
  - 默认恢复。
  - 超过总超时阈值则回退为 `PARTIAL_ANSWER + audit preserved`。

---

## 9. Deep Research V2 设计

Java 版当前的 Deep Research 已经比原版更像正式产品，但还需要从“可用”进化到“稳定领先”。

重点补强 4 个点：

在当前项目中，这 4 个点都不应该脱离现有图结构单独实现，而应分别挂在：

- `DeepResearchGraphDefinitionFactory`
- `DeepResearchResearcherService`
- `DeepResearchSynthesizer`
- `DeepResearchWorkingSetStore`
- `KnowledgeSearchService`

### 9.1 Query-aware 检索接入

Deep Research 目前最终会走 `KnowledgeSearchService` 拿 root hits，但 research task 级别还需要更细粒度检索策略：

- 事实抽取型 task 偏 source / graph / refkey。
- 比较型 task 偏 article chunk / contribution / graph。
- 追调用链型 task 偏 graph + source chunk。

因此建议不要单独再造一套 Deep Research 检索栈，而是：

- 让 `DeepResearchResearcherService` 调用同一套 `RetrievalStrategyResolver`
- 但输入从“整题问题”换成“task 级目标与 layer 上下文”
- 继续复用 `KnowledgeSearchService` 作为实际通道融合入口

### 9.2 证据账本去重与合并

为避免 answer 只是一堆 findings 拼接，新增：

- `EvidenceDedupService`
- `FactKeyMergePolicy`

目标：

- 同一事实来自多个来源时，合并成稳定的 fact block。
- 不让最终答案因为证据重复而显得碎片化。

### 9.3 冲突解释增强

当前冲突已经能显式暴露，下一步要补的是：

- 冲突按主题归类。
- 冲突按来源类型区分。
- 冲突按时间或版本区分。

这样对比题、演进题和排障题会更有优势。

### 9.4 成本与超时控制

Deep Research 领先不能建立在“无限放大模型调用次数”上。

建议引入：

- 任务级 `maxSearchPass`
- 任务级 `maxEvidenceCards`
- scene 级 `budget bucket`
- 按 query intent 决定 planner / researcher / synthesizer 的角色模型

---

## 10. 默认能力与配置策略

“全面超过”不能只靠实验开关。

所以需要把下面这些能力从“可选增强”升级为“默认收益”：

- article chunk lexical 召回
- source chunk lexical 召回
- refkey 独立权重
- Query Rewrite 规则层
- graph channel
- article vector / chunk vector 的受控默认启用

建议增加一组明确的特性开关，但默认值应向“新主链”靠拢：

- `lattice.query.rewrite.rules-enabled`
- `lattice.query.rewrite.llm-enabled`
- `lattice.query.search.article-chunk-fts-enabled`
- `lattice.query.search.source-chunk-fts-enabled`
- `lattice.query.search.contribution-fts-enabled`
- `lattice.query.search.retrieval-audit-enabled`
- `lattice.query.state.redis-enabled`
- `lattice.deepresearch.state.redis-enabled`
- `lattice.compile.state.redis-enabled`

结合当前项目，配置层还应同步扩展：

- `QueryRetrievalSettingsState`
- `QueryRetrievalSettingsService`
- Admin 检索配置页面
- Query Retrieval 相关 Controller / DTO

---

## 11. 分阶段实施方案

## 11.1 Phase A：检索底座升级

目标：

- 把最影响准确性的召回短板先补齐。

内容：

- 新增 article chunk lexical 通道。
- 新增 source chunk lexical 通道。
- source / contribution 从内存扫切到数据库检索。
- refkey 使用独立权重。
- FTS 检索列与索引建设。

交付物：

- `ArticleChunkFtsSearchService`
- `SourceChunkFtsSearchService`
- `ContributionFtsSearchService`
- 新版 `RetrievalStrategyResolver` 初版
- 新版检索配置表字段补齐

当前代码落点：

- 改造 `FtsSearchService`
- 改造 `RefKeySearchService`
- 拆分并重写 `SourceSearchService`
- 重写 `ContributionSearchService`
- 扩展 `ArticleChunkJdbcRepository`
- 扩展 `SourceFileJdbcRepository`
- 扩展 `SourceFileChunkJdbcRepository`
- 扩展 `ContributionJdbcRepository`
- 在 `QueryGraphDefinitionFactory` 新增 chunk lexical 相关节点
- 在 `KnowledgeSearchService` 接入新通道并替换静态权重逻辑

阶段完成标准：

- recall@10 明显高于当前 Java 基线。
- source / contribution 检索不再依赖 `findAll()`。

## 11.2 Phase B：Query Rewrite 与意图路由

目标：

- 把简称、别名、路径类问题的召回稳定性拉起来。

内容：

- `query_rewrite_rules`
- `QueryRewriteService`
- `QueryIntentClassifier`
- rewrite audit

当前代码落点：

- 在 `QueryGraphDefinitionFactory` 中新增 `rewrite_query`、`classify_intent`、`resolve_retrieval_strategy`
- 扩展 `QueryGraphState`
- 升级 `QueryTokenExtractor`
- 新增 `QueryRewriteRuleJdbcRepository`
- 新增 `QueryRewriteAuditJdbcRepository`
- 扩展 `KnowledgeSearchService` 的输入参数与融合上下文

阶段完成标准：

- 缩写类问题 recall 提升。
- rewrite 没有显著引入 query drift。

## 11.3 Phase C：工作集可恢复化

目标：

- 把 Query / Deep Research / Compile 的中间态从内存移走。

内容：

- Redis working set stores
- checkpoint 机制
- 恢复策略与清理策略

当前代码落点：

- 新增 `RedisCompileWorkingSetStore`
- 新增 `RedisQueryWorkingSetStore`
- 新增 `RedisDeepResearchWorkingSetStore`
- 新增 `DeepResearchCheckpointService`
- 替换 `InMemoryCompileWorkingSetStore`
- 替换 `InMemoryQueryWorkingSetStore`
- 替换 `InMemoryDeepResearchWorkingSetStore`
- 在 `QueryGraphDefinitionFactory`、`DeepResearchGraphDefinitionFactory`、`CompileGraphDefinitionFactory` 对应节点后补 checkpoint 写入

阶段完成标准：

- 手动重启实例后，进行中的 Deep Research 能恢复。
- 长编译作业不会因单次重启完全丢失中间态。

## 11.4 Phase D：向量与图谱默认收益释放

目标：

- 让向量和图谱成为默认主链收益，而不是“存在但不常用”。

内容：

- article vector / chunk vector 默认启用策略
- graph channel 与调用链问题策略绑定
- vector 路径质量门禁

当前代码落点：

- 扩展 `QueryRetrievalSettingsState`
- 扩展 `VectorSearchService`
- 扩展 `ChunkVectorSearchService`
- 扩展 `GraphSearchService`
- 在 `RetrievalStrategyResolver` 中为调用链类、宽泛概念类、多跳类问题建立策略模板

阶段完成标准：

- 宽泛概念题、相似表达题、多跳调用链题稳定领先当前基线。

## 11.5 Phase E：真实评测与对标收口

目标：

- 从“共享 benchmark 胜出”升级为“真实场景稳定领先”。

内容：

- 真实问题集
- 回放集
- query retrieval audit dashboard
- 版本间 shadow compare

当前代码落点：

- 基于 `query_retrieval_runs`、`query_retrieval_channel_hits` 出 dashboard
- 继续复用当前已有 benchmark runner 与 gap report 能力
- 为 `KnowledgeSearchService` 和 Query Graph 输出增加版本标签、策略标签、问题类型标签

阶段完成标准：

- 新链路离线对标具备稳定领先证据。
- 可明确定位仍未超过原版的剩余问题类型。

---

## 12. 评测与验收口径

## 12.1 “全面超过”定义

只有同时满足下面 5 条，才可以宣布 Java 版全面超过原版：

1. 共享 `AST / Citation / Deep Research` benchmark 持续领先。
2. 真实问题集上的 retrieval recall 与最终答案准确性持续领先。
3. Query / Deep Research / Compile 三条主链在重启场景下恢复能力领先。
4. 默认配置下的真实收益领先，而不是靠实验开关临时领先。
5. 平台可追踪性、审计性、治理性保持不退化。

## 12.2 建议验收指标

离线质量指标：

- `unsupportedClaimRate`
- `citationPrecision`
- `citationCoverage`
- `multiHopCompleteness`
- `graphFactAcceptedRate`
- `retrievalRecall@5`
- `retrievalRecall@10`
- `MRR`

运行可靠性指标：

- `query_resume_success_rate`
- `deep_research_resume_success_rate`
- `compile_resume_success_rate`
- `stalled_job_detection_latency`

成本与性能指标：

- `p95_retrieval_latency_ms`
- `p95_query_latency_ms`
- `deep_research_avg_llm_calls`
- `cost_per_successful_answer`

## 12.3 验收门槛建议

建议把首轮“全面超过”门槛定义为：

- 共享 benchmark 持续保持领先。
- 真实问题集 `recall@10` 较当前 Java 基线提升至少 `15%`。
- 真实问题集 `unsupportedClaimRate` 下降至少 `20%`。
- Deep Research 多跳完整率不低于 `0.95`。
- 关键长任务恢复成功率达到 `90%+`。

---

## 13. 需要修改的核心模块

首轮预计会直接影响下面这些模块：

本方案的实现原则不是“完全另起炉灶”，而是 **优先复用现有 Java 版已经具备的平台骨架，并按新项目口径直接替换旧实现**：

- 继续沿用现有 `QueryGraphDefinitionFactory` 的并行召回编排骨架，只替换或增强通道实现。
- 继续沿用现有 `KnowledgeSearchService + RrfFusionService` 的融合入口，但把静态权重升级为策略化权重。
- 继续沿用现有 `DeepResearchOrchestrator`、`DeepResearchSynthesizer` 的主链，只把检索策略、证据治理和 checkpoint 能力补齐。
- 继续沿用现有审计表、执行快照表、compile job / step 表，而不是另起一套平行运行时。
- 对已经确认落后的旧实现，允许直接下线，不额外设计长期兼容桥接层。

也就是说，这份方案的核心不是“推翻当前 Java 版”，而是：

- 在现有平台上补齐原版更强的查询内核。
- 再利用 Java 版已经更成熟的治理与审计框架，把这些能力沉淀成可运营资产。

### 13.1 查询层

- `KnowledgeSearchService`
- `FtsSearchService`
- `RefKeySearchService`
- `SourceSearchService`
- `ContributionSearchService`
- `VectorSearchService`
- `ChunkVectorSearchService`
- `QueryTokenExtractor`
- `QueryGraphDefinitionFactory`

具体改造判断：

- `KnowledgeSearchService`：保留，升级为策略化融合入口。
- `FtsSearchService`：保留，收敛为 article FTS 主通道。
- `RefKeySearchService`：保留，但重写底层查询逻辑，直接使用数据库列与索引。
- `SourceSearchService`：不建议继续保留“全量扫描”实现，建议改造成 source path/file 级检索 facade。
- `ContributionSearchService`：保留类名，但重写为数据库侧检索。
- `QueryTokenExtractor`：保留名称，升级为 Query Preparation 共享组件。
- `QueryGraphDefinitionFactory`：保留，是所有 Query V2 能力的第一编排落点。

### 13.2 新增服务建议

- `QueryRewriteService`
- `QueryIntentClassifier`
- `RetrievalStrategyResolver`
- `ArticleChunkFtsSearchService`
- `SourceChunkFtsSearchService`
- `ContributionFtsSearchService`
- `RetrievalAuditService`
- `EvidenceDedupService`
- `DeepResearchCheckpointService`

新增服务与现有类的关系：

- `RetrievalStrategyResolver`：直接服务于 `KnowledgeSearchService` 与 `QueryGraphDefinitionFactory`
- `ArticleChunkFtsSearchService`：与 `FtsSearchService` 并列，不替代 article FTS
- `SourceChunkFtsSearchService`：与 `SourceSearchService` 形成 file/path 命中和 chunk 命中分工
- `ContributionFtsSearchService`：如果需要快速落地，可先内聚进 `ContributionSearchService`，后续再拆类
- `DeepResearchCheckpointService`：直接服务于 `DeepResearchGraphDefinitionFactory`

### 13.3 持久化层

- `SourceFileChunkJdbcRepository`
- `SourceFileJdbcRepository`
- `ArticleChunkJdbcRepository`
- `ContributionJdbcRepository`
- 新增 `QueryRewriteRuleJdbcRepository`
- 新增 `QueryRetrievalAuditJdbcRepository`
- 新增 Redis working set repository 族

具体改造判断：

- `SourceFileJdbcRepository`：负责 source file 级路径与文本检索。
- `SourceFileChunkJdbcRepository`：负责 source chunk lexical 检索。
- `ArticleChunkJdbcRepository`：负责 article chunk lexical 检索。
- `ContributionJdbcRepository`：负责 contribution question / answer / corrections 检索。
- Redis working set repository 族：直接实现 `CompileWorkingSetStore`、`QueryWorkingSetStore`、`DeepResearchWorkingSetStore` 三个接口，不另造第四套抽象。

### 13.4 配置与后台

- `AdminQueryRetrievalConfigController`
- settings 页面中的检索与 rewrite 配置
- 运维态指标与 dashboard

---

## 14. 风险与缓解

### 14.1 Rewrite 过度扩写导致 query drift

缓解：

- 规则层优先。
- LLM rewrite 只在特定问题类型触发。
- 原问题与扩写后问题并行检索，比较命中差异后再融合。

### 14.2 PostgreSQL 检索列与索引回填成本过高

缓解：

- 采用增量回填。
- 先从 `articles / article_chunks / source_file_chunks` 三张主表开始。
- contribution FTS 放在后续阶段。

### 14.3 向量默认开启导致成本升高

缓解：

- 按 query intent 决定是否启用向量通道。
- embedding 请求缓存。
- 仅在 lexical 召回不足时提升向量权重。

### 14.4 Redis 工作集膨胀

缓解：

- 明确 TTL。
- 作业完成后延迟清理。
- 审计和工作集分层存储，避免把最终历史都压在 Redis。

### 14.5 离线版本对标结论不一致

缓解：

- 先做离线 shadow compare，再决定是否切换默认主链。
- 差异大于阈值时自动落 audit，继续回归问题集，不要求运行期双链并存。

---

## 15. 推荐落地顺序

如果资源有限，推荐严格按下面顺序推进：

1. 先做 `Phase A`，因为这是最直接决定答案质量的部分。
2. 再做 `Phase B`，补齐简称、别名、业务码、排障问法的召回。
3. 第三步做 `Phase C`，把平台能力从“看起来完整”变成“真正可恢复”。
4. 第四步做 `Phase D`，释放向量与图谱默认收益。
5. 最后用 `Phase E` 收口真实领先证据。

这条顺序的核心思想是：

- 先赢答案。
- 再赢可靠性。
- 最后把“赢了”这件事证明出来并固化成长期机制。

---

## 16. 最终结论

如果 Java 版要全面超过原版，真正要做的不是“继续证明 Java 能不能做”，而是：

- 把原版已经验证有效的查询内核技巧系统化迁移过来。
- 利用 Java 版已经领先的平台化基础，把这些技巧做成可配置、可审计、可恢复、可对标的正式能力。

因此，本方案的主判断是：

- **不是语言限制。**
- **是实现重心还没有完全从“平台搭起来了”走到“查询内核也打磨到最强了”。**

只要按本方案推进，Java 版完全有机会从“平台更强”进化为“平台更强且答案也更强”，并最终在整体工程价值上全面超过原版。
