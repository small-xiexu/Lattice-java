# Legacy Direct Compile 删除 - 提交前质量审查报告

- 报告类型：agentD 提交前质量审查（Pre-Commit Quality Review）
- 审查时间：2026-05-18
- 前置报告：
  - compile_review_legacy_direct_compile_delete_result_report.md
  - compile_review_legacy_direct_compile_delete_gate_report.md
  - compile_review_stategraph_test_secret_fixture_fix_result_report.md
- 是否修改代码：否

---

## 1. Redline 扫描结果

| 指标 | 值 |
|---|---|
| BLOCKER | 0 |
| REVIEW | 存量条目（未新增） |
| ALLOWLIST | 存量条目（未新增） |
| 扫描退出码 | 0 |

**结论：redline 门禁通过。**

---

## 2. 全量 mvn test 结果

| 指标 | 值 |
|---|---|
| Tests run | 811 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| BUILD | SUCCESS |
| 耗时 | 5:55 min |

**结论：全量测试全绿。**

---

## 3. 删除的 Legacy Direct Compile 入口清单

### CompilePipelineService.java（生产代码）

| 删除项 | 类型 | 说明 |
|---|---|---|
| `compile(Path sourceDir)` | 方法 | 旧全量编译直执行入口 |
| `retry(String jobId)` | 方法 | 旧重试直执行入口 |
| `incrementalCompile(Path sourceDir)` | 方法 | 旧增量编译直执行入口 |
| `commitPendingConcepts(String jobId, Path sourceDir)` | 私有方法 | compile/retry 内部实现 |
| `evictQueryCacheIfNeeded(int persistedCount)` | 私有方法 | compile/retry 缓存清理 |
| `createSupportBundle(...)` | 静态工厂方法 | 旧构造器内部辅助 |
| `SupportBundle` 内部类 | 内部类 | 旧构造器 DI 辅助 |
| 5 个重载构造器 | 构造器 | 旧直调链路 DI 入口 |
| `setQueryCacheStore(QueryCacheStore)` | setter | 旧直调链路缓存注入 |
| 关联字段 | 字段 | `compileArticleNode`, `compilationWalStore`, `synthesisArtifactsService`, `articleJdbcRepository`, `articleChunkJdbcRepository`, `articleVectorIndexService`, `articleChunkVectorIndexService`, `incrementalCompileService`, `llmGateway`, `queryCacheStore` |

### IncrementalCompileService.java（生产代码）

| 删除项 | 类型 | 说明 |
|---|---|---|
| `incrementalCompile(Path sourceDir)` | 方法 | 旧增量编译直执行入口（70 行） |

**净删除：~571 行（CompilePipelineService）+ 70 行（IncrementalCompileService）= 641 行生产代码**

---

## 4. 保留的 StateGraph Helper 清单

CompilePipelineService 当前保留 23 个 public 方法，全部为 StateGraph 编排节点能力：

| # | 方法签名 | 用途 |
|---|---|---|
| 1 | `ingest(Path sourceDir)` | 源数据摄入 |
| 2 | `groupSources(List<RawSource>)` | 源数据分组 |
| 3 | `splitBatches(Map<String, List<RawSource>>)` | 批次拆分 |
| 4 | `analyzeBatches(...)` | 概念分析 |
| 5 | `mergeConcepts(List<AnalyzedConcept>)` | 概念合并 |
| 6 | `stageWal(String jobId, List<MergedConcept>)` | WAL 暂存 |
| 7 | `compileDraftArticles(List<MergedConcept>, Path)` | 草稿编译 |
| 8 | `planIncrementalGraphChanges(List<MergedConcept>)` | 增量规划 |
| 9 | `enhanceExistingArticles(Map<String, List<MergedConcept>>)` | 文章增强 |
| 10 | `reviewDraftArticles(List<ArticleRecord>)` | 审查 |
| 11 | `fixReviewedArticles(List<ArticleReviewEnvelope>)` | 修复 |
| 12 | `persistArticles(String jobId, List<ArticleReviewEnvelope>)` | 落盘 |
| 13 | `rebuildArticleChunks(List<ArticleReviewEnvelope>)` | chunk 重建 |
| 14 | `refreshVectorIndex(List<ArticleReviewEnvelope>)` | 向量刷新 |
| 15 | `generateGraphSynthesisArtifacts()` | 合成产物 |
| 16 | `captureRepoSnapshot(String, Path, int)` | 快照捕获 |
| 17 | `persistSourceFiles(List<RawSource>)` | 源文件落盘 |
| 18 | `persistSourceFileChunks(List<RawSource>)` | 源文件 chunk 落盘 |
| 19 | `finalizeArticleForPersist(ArticleReviewEnvelope)` | 文章最终化 |
| 20 | `currentCompileRoute()` | 编译路由查询 |
| 21 | `currentReviewRoute()` | 审查路由查询 |
| 22 | `currentFixRoute()` | 修复路由查询 |
| 23 | `setRepoSnapshotService(RepoSnapshotService)` | 快照服务注入 |

**结论：StateGraph helper 完整保留，无误删。**

---

## 5. 删除/修改的测试清单

| 测试文件 | 操作 | 行数 | 合理性 |
|---|---|---|---|
| `CompilePipelineServiceTests.java` | 删除 | 223 行 | 合理：测试旧 `compile()/retry()` 入口，已无生产代码 |
| `CompilePipelineVectorIndexingTests.java` | 删除 | 510 行 | 合理：测试旧 `compile()` 的向量索引逻辑，已无生产代码 |
| `CompilePipelineWalRecoveryTests.java` | 删除 | 294 行 | 合理：测试旧 `retry()` 的 WAL 恢复逻辑，已无生产代码 |
| `IncrementalCompileServiceTests.java` | 修改 | 净删 762 行 | 合理：删除旧 `incrementalCompile(Path)` 测试，保留 `planIncrementalChanges` 测试 |
| `StateGraphCompileOrchestratorTests.java` | 修改 | +5 行 | 合理：添加测试专用加密密钥 + TRUNCATE LLM 快照表，修复环境问题 |

**结论：测试删除/修改与生产代码删除一致，无孤立或遗漏。**

---

## 6. 是否修改生产 LLM Secret / Crypto

**否。**

- `git diff --name-status | grep -i llm/secret/crypto/encrypt/decrypt` 返回空
- `LlmSecretCryptoService`、`ExecutionLlmSnapshotService`、加解密密钥配置均未被触碰
- StateGraph 测试的密钥修复仅添加了 `@SpringBootTest(properties = {"lattice.llm.secret-encryption-key=..."})`，属于测试级别配置

---

## 7. 是否修改 StateGraph 主链

**否。**

- `StateGraphCompileOrchestrator`、所有 Graph Node（`InitializeJobNode`、`IngestSourcesNode`、`CompileNewArticlesNode` 等）、`CompileGraphConditions`、`CompileGraphLifecycleListener` 均未出现在 diff 中
- 保留的 23 个 helper 方法签名、实现逻辑均未变动

---

## 8. 是否修改 Query / AnswerGeneration

**否。**

- diff 中无任何 `query/`、`answer/`、`AnswerGeneration`、`Fallback`、`Citation`、`Rerank` 路径下的文件
- 删除的 `QueryCacheStore` 引用仅为旧 `evictQueryCacheIfNeeded()` 内部使用，Graph 编排路径中的缓存清理由 `CompileGraphLifecycleListener` 负责，未被触碰

---

## 9. 是否需要 Query Baseline

**否。** 原因：

1. 本轮仅删除旧 direct compile 执行入口和相关测试，未修改任何检索、问答、回答生成、证据排序、重排、引用逻辑
2. 未修改任何 prompt 模板或 config/rules
3. 未修改向量索引写入/读取逻辑（Graph 路径的 `refreshVectorIndex` 保持不变）
4. 未修改数据库 schema 或存量数据
5. Query 链路运行时不依赖已删除的旧入口

---

## 10. 是否建议提交

**是，建议提交。**

- Redline BLOCKER=0
- 全量测试 811/0/0 全绿
- 改动方向明确：删除 legacy direct compile 旧入口，保留 StateGraph helper
- 无任何生产主链（StateGraph、Query、LLM crypto）被修改
- 测试删除与生产代码删除完全对应
- 无功能回归风险

---

## 11. 建议提交信息

```
refactor(compiler): remove legacy direct compile entry points

Remove CompilePipelineService.compile/retry/incrementalCompile and
IncrementalCompileService.incrementalCompile(Path) which are superseded
by StateGraph orchestration. Retain all StateGraph helper methods.

- Delete 5 legacy constructors and SupportBundle inner class
- Delete 3 obsolete test classes and legacy test methods
- Fix StateGraphCompileOrchestratorTests secret fixture (test-only)
- Net reduction: ~2413 lines across 8 files
- All 811 tests pass, redline BLOCKER=0
```
