# Task 1：Phase 8 前置收口（结构重构）

## 任务目标

在不改变任何外部行为的前提下，完成以下结构性重构，为 Phase 8 的模型池 + Agent 绑定配置中心做清洁地基。

**硬性验收标准：`mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test` 必须全部通过。**

---

## 变更清单（按顺序执行）

### 1. 修复 `StateGraphCompileOrchestrator` 双重初始化

**文件：** `src/main/java/com/xbk/lattice/compiler/service/StateGraphCompileOrchestrator.java`

**问题：** `execute()` 方法在 `initialState` 里提前设置了 `autoFixEnabled`、`maxFixRounds`、`allowPersistNeedsHumanReview`、`stepLogFailureMode`，但 `initialize_job` 节点随后又从 `compileReviewProperties` 覆盖相同字段（设计方案 §6.1.1 明确这些配置快照字段只在 `initialize_job` 时固化）。

**变更：** `execute()` 中的 `initialState` 只保留以下三个字段的赋值，其余全部删除：
- `setJobId(...)`
- `setSourceDir(...)`
- `setCompileMode(...)`

`initialize_job` 节点（在 `CompileGraphDefinitionFactory.initializeJob()` 中）已负责处理其余所有配置快照字段，无需改动。

---

### 2. 提取 `CompileGraphDefinitionFactory` 节点到 `graph/node/` 子包

**背景：** `CompileGraphDefinitionFactory`（485 行）将 18 个节点的执行逻辑全部作为私有方法内嵌，违背设计方案 §14 的包结构要求。

**目标包：** `src/main/java/com/xbk/lattice/compiler/graph/node/`

**规则：**
- 每个节点方法抽取为独立的 `@Component @Profile("jdbc")` Spring bean
- 类名为节点名 PascalCase 加 `Node` 后缀（如 `initialize_job` → `InitializeJobNode`）
- 每个节点类实现方法签名：`public Map<String, Object> execute(OverAllState overAllState)`
- 节点类通过构造器注入其所需依赖（参考当前私有方法的入参引用）
- `CompileGraphDefinitionFactory.build()` 中的节点注册改为引用各自的 bean：

```java
stateGraph.addNode("initialize_job", AsyncNodeAction.node_async(initializeJobNode::execute));
// ... 以此类推
```

**需要创建的节点类（共 18 个）：**

| 节点名 | 类名 | 主要依赖 |
|---|---|---|
| `initialize_job` | `InitializeJobNode` | `CompileGraphConditions`, `CompileReviewProperties`, `CompileGraphProperties`, `CompilePipelineService`（仅路由方法） |
| `ingest_sources` | `IngestSourcesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `persist_source_files` | `PersistSourceFilesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `persist_source_file_chunks` | `PersistSourceFileChunksNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `group_sources` | `GroupSourcesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `split_batches` | `SplitBatchesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `analyze_batches` | `AnalyzeBatchesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `merge_concepts` | `MergeConceptsNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `plan_changes` | `PlanChangesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `enhance_existing_articles` | `EnhanceExistingArticlesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `compile_new_articles` | `CompileNewArticlesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `review_articles` | `ReviewArticlesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper`, `ReviewDecisionPolicy` |
| `fix_review_issues` | `FixReviewIssuesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `persist_articles` | `PersistArticlesNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `rebuild_article_chunks` | `RebuildArticleChunksNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `refresh_vector_index` | `RefreshVectorIndexNode` | `CompilePipelineService`, `CompileWorkingSetStore`, `CompileGraphStateMapper` |
| `generate_synthesis_artifacts` | `GenerateSynthesisArtifactsNode` | `CompilePipelineService`, `CompileGraphStateMapper` |
| `capture_repo_snapshot` | `CaptureRepoSnapshotNode` | `CompilePipelineService`, `CompileGraphStateMapper` |
| `finalize_job` | `FinalizeJobNode` | `CompileWorkingSetStore`, `CompileGraphStateMapper` |

**注意：** `ReviewArticlesNode` 和 `FixReviewIssuesNode` 中存在 `private` helper 方法（`loadReviewedArticles`、`mergeAttemptMetadata`、`mergeReviewEnvelopes` 等），这些辅助方法随节点逻辑一起搬迁到对应节点类中（设为 private 方法），不要放回工厂。

**工厂改造后：** `CompileGraphDefinitionFactory` 只保留 `build()` 方法和必要的字段注入，所有私有节点方法全部删除，工厂文件预期缩减到 80 行以内。

---

### 3. 拆分 `CompilePipelineService`

**文件：** `src/main/java/com/xbk/lattice/compiler/service/CompilePipelineService.java`（现 677 行）

**目标：** 将其按职责拆分为 4 个支撑组件，降低单类职责范围。

**拆分方案：**

#### 3.1 新建 `SourceIngestSupport`（`compiler/service/` 包下）
职责：源文件摄入、分组、批次切分、分析、归并、WAL 暂存、源文件持久化。

迁入以下方法（逻辑不变，仅搬迁）：
- `ingest(Path sourceDir)`
- `groupSources(List<RawSource>)`
- `splitBatches(Map<String, List<RawSource>>)`
- `analyzeBatches(Map<String, List<SourceBatch>>, Path)`
- `mergeConcepts(List<AnalyzedConcept>)`
- `stageWal(String, List<MergedConcept>)`
- `persistSourceFiles(List<RawSource>)`
- `persistSourceFileChunks(List<RawSource>)`
- `planIncrementalGraphChanges(List<MergedConcept>)`（增量规划）
- `enhanceExistingArticles(Map<String, List<MergedConcept>>)`

依赖注入：`CompilerProperties`、`LlmGateway`（仅 `analyzeBatches` 需要）、`SourceFileJdbcRepository`、`SourceFileChunkJdbcRepository`、`CompilationWalStore`、`IncrementalCompileService`（内部委托用）。

标注 `@Service @Profile("jdbc")`。

#### 3.2 新建 `ArticleCompileSupport`（`compiler/service/` 包下）
职责：通过 Agent 完成文章草稿生成、审查、修复。

迁入以下方法：
- `compileDraftArticles(List<MergedConcept>, Path)`
- `reviewDraftArticles(List<ArticleRecord>)`
- `fixReviewedArticles(List<ArticleReviewEnvelope>)`
- `currentCompileRoute()`
- `currentReviewRoute()`
- `currentFixRoute()`

依赖注入：`WriterAgent`、`ReviewerAgent`、`FixerAgent`、`AgentModelRouter`、`CompileArticleNode`、`ArticleReviewerGateway`、`ReviewFixService`。

标注 `@Service @Profile("jdbc")`。

#### 3.3 新建 `ArticlePersistSupport`（`compiler/service/` 包下）
职责：文章落库、chunks 重建、向量索引刷新、合成产物生成、快照。

迁入以下方法：
- `persistArticles(String, List<ArticleReviewEnvelope>)`
- `rebuildArticleChunks(List<ArticleReviewEnvelope>)`
- `refreshVectorIndex(List<ArticleReviewEnvelope>)`
- `generateGraphSynthesisArtifacts()`
- `captureRepoSnapshot(String, Path, int)`
- `finalizeArticleForPersist(ArticleReviewEnvelope)`

依赖注入：`ArticleJdbcRepository`、`ArticleChunkJdbcRepository`、`ArticleVectorIndexService`、`SynthesisArtifactsService`、`RepoSnapshotService`（`@Autowired(required=false)`）。

标注 `@Service @Profile("jdbc")`。

#### 3.4 `CompilePipelineService` 本身改为委托类
保留类和 Spring bean 注册，内部把方法调用委托给上述三个新 Support bean，避免改动所有现存节点类和测试的 import（后续可逐步移除）。

示例：
```java
public List<RawSource> ingest(Path sourceDir) throws IOException {
    return sourceIngestSupport.ingest(sourceDir);
}
```

---

### 4. 清理 `CompileArticleNode.compile()` 遗留审查逻辑

**文件：** `src/main/java/com/xbk/lattice/compiler/service/CompileArticleNode.java`

**问题：** `compile(MergedConcept, Path)` 方法（第 147 行）内嵌了完整的审查 + 修复调用链，但图流程已通过独立节点处理审查，此方法已不被 Graph 使用。

**变更：**
- 在 `compile(MergedConcept, Path)` 方法上加 `@Deprecated` 注解
- 在方法 Javadoc 里补一行：`@deprecated 图流程已通过独立审查节点处理，此方法仅保留用于过渡期测试兼容`

不需要删除逻辑（避免破坏可能依赖此方法的测试），仅标注废弃。

---

### 5. 清理 `CompileOrchestrationModes` 死代码

**文件：** `src/main/java/com/xbk/lattice/compiler/service/CompileOrchestrationModes.java`

**变更：**
- 删除 `LEGACY_SERVICE_ALIAS` 常量（第 16 行）
- 删除 `normalize()` 方法中对 `LEGACY_SERVICE_ALIAS` 的引用分支
- 简化 `normalize()` 为：

```java
public static String normalize(String orchestrationMode) {
    return STATE_GRAPH;
}
```

---

### 6. 更新设计文档进度台账

**文件：** `.codex/Spring AI Alibaba Graph 完整接入设计方案.md`

Phase 8 进度台账的"进行中"项前，补一行已完成条目：

```markdown
- [x] Phase 8 前置收口
  - 验收：`StateGraphCompileOrchestrator` 双重初始化已收缩；节点已拆至 `graph/node/` 子包；
    `CompilePipelineService` 已委托至 `SourceIngestSupport / ArticleCompileSupport / ArticlePersistSupport`；
    `CompileArticleNode.compile()` 已标注 `@Deprecated`；死代码已清理；`mvn test` 通过。
```

---

## 不允许做的事

- 不允许修改任何 `@Test` 方法的逻辑
- 不允许修改 `CompileGraphState`、`CompileGraphStateMapper`、`CompileWorkingSetStore` 等图状态核心类
- 不允许改变任何节点的业务逻辑（只搬迁，不修改行为）
- 不允许删除 `CompilePipelineService` 类（只委托，保留 bean）

## 验收命令

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

全部通过后任务完成。
