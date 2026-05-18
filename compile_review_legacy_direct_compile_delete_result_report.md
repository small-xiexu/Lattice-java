# compile_review_legacy_direct_compile_delete_result_report

- 执行时间：2026-05-18
- 执行 Agent：agentA
- 基线 commit：4864cbc

## 1. 删除的 legacy 方法

| 类 | 方法签名 | 说明 |
|---|---|---|
| CompilePipelineService | `compile(Path sourceDir)` | 遗留 direct full compile 入口 |
| CompilePipelineService | `retry(String jobId)` | 遗留 WAL retry 入口 |
| CompilePipelineService | `incrementalCompile(Path sourceDir)` | 遗留 direct incremental compile 入口 |
| IncrementalCompileService | `incrementalCompile(Path sourceDir)` | 遗留 direct incremental compile 实现 |

## 2. 删除的只服务旧入口的私有方法/字段

### CompilePipelineService 删除的私有方法

| 方法 | 说明 |
|---|---|
| `commitPendingConcepts(String jobId, Path sourceDir)` | WAL 提交循环，仅被 compile() 和 retry() 调用 |
| `evictQueryCacheIfNeeded(int persistedCount)` | 查询缓存清理，仅被 compile/retry/incrementalCompile 调用 |

### CompilePipelineService 删除的字段

| 字段 | 说明 |
|---|---|
| `compileArticleNode` | 仅被 commitPendingConcepts 使用 |
| `compilationWalStore` | 仅被 commitPendingConcepts 使用 |
| `synthesisArtifactsService` | 仅被 compile() 使用 |
| `articleJdbcRepository` | 仅被 commitPendingConcepts 使用 |
| `articleChunkJdbcRepository` | 仅被 commitPendingConcepts 使用 |
| `articleVectorIndexService` | 仅被 commitPendingConcepts 使用 |
| `articleChunkVectorIndexService` | 仅被 commitPendingConcepts 使用 |
| `incrementalCompileService` | 仅被 incrementalCompile() 和旧 setter 转发使用 |
| `llmGateway` | 仅被 evictQueryCacheIfNeeded 使用 |
| `queryCacheStore` | 仅被 evictQueryCacheIfNeeded 使用 |

### CompilePipelineService 删除的构造器/内部类

| 项目 | 说明 |
|---|---|
| 5 个非 @Autowired 构造器重载 | 仅服务旧测试和旧 direct pipeline |
| `SupportBundle` 私有内部类 | 仅服务旧构造器链 |
| `createSupportBundle()` 静态工厂 | 仅服务旧构造器链 |

### CompilePipelineService 简化的 setter

| setter | 变化 |
|---|---|
| `setRepoSnapshotService` | 移除对 incrementalCompileService 的转发 |
| `setFactCardGenerationService` | 移除对 incrementalCompileService 的转发 |
| `setQueryCacheStore` | 整体删除 |

## 3. 保留的 StateGraph helper

以下方法仍由 StateGraph 编排调用，全部保留：

| 类 | 方法 |
|---|---|
| CompilePipelineService | `ingest`, `groupSources`, `splitBatches`, `analyzeBatches`, `mergeConcepts`, `stageWal` |
| CompilePipelineService | `compileDraftArticles`, `planIncrementalGraphChanges`, `enhanceExistingArticles` |
| CompilePipelineService | `reviewDraftArticles`, `fixReviewedArticles`, `persistArticles` |
| CompilePipelineService | `rebuildArticleChunks`, `refreshVectorIndex`, `generateGraphSynthesisArtifacts` |
| CompilePipelineService | `captureRepoSnapshot`, `persistSourceFiles`, `persistSourceFileChunks` |
| CompilePipelineService | `finalizeArticleForPersist`, `currentCompileRoute`, `currentReviewRoute`, `currentFixRoute` |
| IncrementalCompileService | `filterChangedRawSources` |
| IncrementalCompileService | `planGraphChanges` |
| IncrementalCompileService | `enhanceExistingArticles` |
| IncrementalCompileService | `refreshGraphSynthesisArtifacts` / `refreshGraphSynthesisArtifacts(String)` |

## 4. 修改/删除的旧测试

| 测试文件 | 操作 | 说明 |
|---|---|---|
| CompilePipelineServiceTests.java | **整文件删除** | 4 个测试全部验证 compile() 成功路径 |
| CompilePipelineVectorIndexingTests.java | **整文件删除** | 3 个测试全部验证 compile()/incrementalCompile() 向量索引 |
| CompilePipelineWalRecoveryTests.java | **整文件删除** | 1 个测试验证 compile()+retry() WAL 恢复 |
| IncrementalCompileServiceTests.java | **保留 1 个测试，删除 6 个** | 保留 `shouldIgnoreMetadataJsonFormattingDifferencesWhenFilteringChangedSources`（验证 filterChangedRawSources helper），删除 6 个 incrementalCompile() 成功路径测试 |

## 5. 是否修改 StateGraph 主链

**否**。未修改 `src/main/java/com/xbk/lattice/compiler/graph/**` 下任何文件。

## 6. 是否修改 Reviewer / Fixer / Persist gate

**否**。未修改 ReviewArticlesNode、FixReviewIssuesNode、PersistArticlesNode、ArticleReviewerGateway、RuleBasedArticleReviewer、ReviewFixService。

## 7. redline BLOCKER 是否仍为 0

**是**。`bash scripts/scan-redline.sh special_cases_report.md` 返回 exit code 0，无 BLOCKER。

## 8. 定向测试是否通过

**是**。`IncrementalCompileServiceTests` 1 个测试通过。

## 9. mvn test 是否通过

**808 个测试通过，3 个预存失败（与本轮改动无关）**。

失败的 3 个测试为 `StateGraphCompileOrchestratorTests`，失败原因为 `IllegalState: Failed to decrypt llm secret`。已通过 `git stash` 回退验证，改动前同样失败。这是运行环境缺少加密密钥的预存问题。

## 10. 是否清库 / 跑 baseline

**否**。未执行任何清库、重建、query baseline 操作。
