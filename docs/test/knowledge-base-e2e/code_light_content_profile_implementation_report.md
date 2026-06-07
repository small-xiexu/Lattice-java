# CODE_LIGHT 内容画像实现报告

时间：2026-06-07
执行人：agentA（代码执行 Agent）
设计依据：`code_light_indexing_mode_design_report.md`

---

## 1. 目标

实现 CODE_LIGHT 内容处理策略的最小闭环——让 INTERNAL_MIRROR / GIT / UPLOAD 等资料源在 `contentProfile=CODE_LIGHT` 时，代码文件编译跳过 writer/reviewer/fixer LLM 调用，直接以源码原文作为 article content 入库。

## 2. 修改文件

### 2.1 `CompileExecutionRequest.java` — 新增 contentProfile 字段

- 新增 `CONTENT_PROFILE_DOCUMENT = "DOCUMENT"` 和 `CONTENT_PROFILE_CODE_LIGHT = "CODE_LIGHT"` 常量
- 新增 `contentProfile` final 字段
- 新增 9 参构造器（含 contentProfile），7 参和 8 参构造器逐级委托，默认值 `null` → 规范化后为 `DOCUMENT`
- 新增 `normalizeContentProfile(String)` — null/blank/未知值均回退 `DOCUMENT`
- 新增 `isCodeLightProfile(String)` — 判断是否 CODE_LIGHT
- 新增 `getContentProfile()` getter

### 2.2 `CompileGraphState.java` — 新增 contentProfile 字段

- 新增 `private String contentProfile` 字段（Lombok `@Getter @Setter`）

### 2.3 `CompileGraphStateKeys.java` — 新增键常量

- 新增 `public static final String CONTENT_PROFILE = "contentProfile"`

### 2.4 `CompileGraphStateMapper.java` — 读写 contentProfile

- `fromMap()` 中新增 `state.setContentProfile(readString(stateMap, CompileGraphStateKeys.CONTENT_PROFILE))`
- `toMap()` 中新增 `values.put(CompileGraphStateKeys.CONTENT_PROFILE, state.getContentProfile())`

### 2.5 `StateGraphCompileOrchestrator.java` — 传递 contentProfile

- `execute(CompileExecutionRequest)` 中新增 `initialState.setContentProfile(executionRequest.getContentProfile())`

### 2.6 `CompileJobService.java` — 从 KnowledgeSource 读取

- `executeRunningJob()` 中 `CompileExecutionRequest` 构造器新增第 9 参数：`knowledgeSource == null ? null : knowledgeSource.getContentProfile()`

### 2.7 `CompileGraphConditions.java` — CODE_LIGHT 条件路由

- `routeAfterMerge()`：CODE_LIGHT + full → `"build_lightweight_articles"`；CODE_LIGHT + incremental → `"plan_changes"`；其余不变
- `routeAfterPlanChanges()`：CODE_LIGHT + `hasCreates` → `"build_lightweight_articles"`；其余不变

### 2.8 `CompileGraphDefinitionFactory.java` — 注册节点和边

- 新增 `BuildLightweightArticlesNode` 字段、构造参数、赋值
- 注册节点：`stateGraph.addNode("build_lightweight_articles", ...)`
- `merge_concepts` 条件边新增 `"build_lightweight_articles"` 路由
- `plan_changes` 条件边新增 `"build_lightweight_articles"` 路由
- 新增边：`build_lightweight_articles` → `persist_articles`

### 2.9 `BuildLightweightArticlesNode.java`（新增）

- 继承 `AbstractCompileGraphNode`
- `execute()`：从 merged concepts（全量）或 conceptsToCreate（增量）加载概念列表，逐概念读取源文件，构建 `ArticleRecord`
- `buildArticleRecord()`：title=concept.getTitle()（源文件相对路径）、content=源码原文、lifecycle=ACTIVE、reviewStatus=passed、confidence=high、metadataJson 含 `contentProfile:CODE_LIGHT` + `sourcePath` + `fileType`
- `readSourceContent()`：路径边界检查后 `Files.readString()`，读取失败返回空字符串
- `resolveFileType()`：按扩展名返回 java/xml/yaml/properties/json/sql/markdown/unknown

## 3. 编译图路由变化

### 全量编译（full + CODE_LIGHT）

```
merge_concepts
  → build_lightweight_articles (跳过 compile_new_articles/review/fix)
    → persist_articles → rebuild_article_chunks → refresh_vector_index → finalize_job
```

### 增量编译（incremental + CODE_LIGHT）

```
merge_concepts
  → plan_changes
    → nothingToDo → finalize_job
    → hasCreates → build_lightweight_articles → persist_articles → ...
```

### DOCUMENT profile（不受影响）

```
merge_concepts
  → plan_changes / compile_new_articles → review_articles → fix_review_issues → persist_articles
  → generate_synthesis_artifacts → capture_repo_snapshot → finalize_job
```

## 4. ArticleRecord 构建对照

| 字段 | DOCUMENT（现有） | CODE_LIGHT（新增） |
|------|------|------|
| title | LLM Writer 生成 | 源文件相对路径 |
| content | LLM Writer 生成 | 源码原文 (Files.readString) |
| reviewStatus | Reviewer 审查后决定 | 直接设为 `passed` |
| lifecycle | ArticleRecord 默认 | `ACTIVE` |
| confidence | LLM Writer 输出 | `high` |
| metadataJson | LLM 编译元数据 | `contentProfile=CODE_LIGHT` + sourcePath + fileType |

## 5. 修改面分析

| 修改 | 类型 | 风险 |
|------|------|------|
| `CompileExecutionRequest` 新增字段和构造器 | 扩展（旧构造器委托新构造器） | 无——向后兼容 |
| `CompileGraphState` + `StateKeys` + `Mapper` | 新增字段+读写 | 无——独立字段 |
| `StateGraphCompileOrchestrator` | 新增一行赋值 | 无 |
| `CompileJobService` | 构造器调用多一个参数 | 无 |
| `CompileGraphConditions` | 条件路由前置 CODE_LIGHT 判断 | 无——DOCUMENT 路径未改动 |
| `CompileGraphDefinitionFactory` | 新增节点注册+边 | 无——仅新增路由，不改已有 |
| `BuildLightweightArticlesNode` | 新增完整节点 | 无——独立节点，不引用任何 LLM 组件 |

## 6. 红线验证

- [x] 未修改 `schema.sql`
- [x] 未修改 query/answer/rerank/fallback/citation 主链
- [x] 未修改 writer/reviewer/fixer 现有逻辑
- [x] 未修改 prompt 文件
- [x] 未新增 source type
- [x] `contentProfile` 默认值为 `DOCUMENT`（向后兼容）
- [x] 对所有代码文件类型一视同仁（无类名/方法名/文件名特判）
- [x] 未提交 commit

## 7. mvn 验证

| 阶段 | 结果 |
|------|------|
| `mvn compile` | BUILD SUCCESS |
| `mvn test-compile` | BUILD SUCCESS |
| `CrossGroupMergeNodeTests`（单独） | 4/4 pass, 0 fail |
| `BatchSplitNodeRankingTests`（单独） | 1/1 pass, 0 fail |
| `DocumentSectionSelectorTests`（单独） | 1/1 pass, 0 fail |
| 全量 `mvn test`（959 tests） | 0 failure, 13 errors 均为预存在的环境资源争用 |

## 8. agentD 验证建议

1. 创建 INTERNAL_MIRROR source 时，API 请求体传入 `"contentProfile": "CODE_LIGHT"`
2. 验证编译图走 `build_lightweight_articles` 节点（graph step log）
3. 验证 article content 为源码原文而非 LLM 生成文本
4. 验证 reviewStatus 直接为 `passed`
5. 验证编译耗时显著低于 DOCUMENT 模式
6. 使用 `java-codebase-public-eval/` fixture 跑 PE5 题集

## 9. 未提交文件

- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphConditions.java`
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphDefinitionFactory.java`
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphState.java`
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphStateKeys.java`
- `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphStateMapper.java`
- `src/main/java/com/xbk/lattice/compiler/graph/node/BuildLightweightArticlesNode.java`
- `src/main/java/com/xbk/lattice/compiler/service/CompileExecutionRequest.java`
- `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java`
- `src/main/java/com/xbk/lattice/compiler/service/StateGraphCompileOrchestrator.java`
