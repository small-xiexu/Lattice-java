# Compile Review 入口闭环覆盖审计报告

生成时间：2026-05-18  
执行角色：agentB  
任务类型：只读架构审计  
任务边界：不改源码、不改测试、不改配置、不运行 compile、不清库、不跑 baseline。

## 1. 结论

当前用户可触发的主编译入口都收敛到 `CompileJobService -> CompileOrchestratorRegistry -> StateGraphCompileOrchestrator`，再进入 StateGraph 的 `Writer -> Reviewer -> Fixer -> Reviewer -> Persist gate` 闭环。未发现后台 admin compile、upload compile、source sync compile、公开 compile API、CLI/MCP compile 入口绕到旧式 `CompileArticleNode.compile(...)`。

旧式 direct compile 仍存在，调用点在 `CompilePipelineService` / `IncrementalCompileService` 内部与相关测试中；它不经过 StateGraph 的二次 reviewer 和 `PersistArticlesNode` gate。如果未来重新暴露或被生产入口复用，会产生治理绕过风险。当前看更像遗留服务/测试入口，可暂缓，但建议下一轮做最小封存审计或去生产 Bean 化。

## 2. redline

| 项 | 结果 |
|---|---:|
| BLOCKER | 0 |
| REVIEW | 1858 |
| ALLOWLIST | 242 |

命令：`bash scripts/scan-redline.sh special_cases_report.md`。本轮只读扫描刷新了既有 `special_cases_report.md`，未修改源码。

## 3. 用户可触发 compile 入口清单

| 入口 | 用户触发方式 | 第一跳 | 是否走 StateGraph 闭环 | 备注 |
|---|---|---|---:|---|
| 后台目录编译 | `POST /api/v1/admin/compile/jobs` | `AdminCompileController.submit(...) -> CompileJobService.submit(...)` | 是 | 新 job 默认 reviewMode 已为 `LLM`，也可显式传 `RULE_BASED` |
| 后台上传并编译 | `POST /api/v1/admin/compile/upload` | `AdminCompileController.uploadAndCompile(...) -> CompileJobService.submit(...)` | 是 | 上传到 workspace 后提交 compile job |
| 统一资料上传 | `POST /api/v1/admin/uploads` | `AdminUploadController.upload(...) -> SourceUploadService.acceptUpload(...) -> submitCompile(...)` | 是 | 自动识别或人工确认后进入 `CompileJobService.submit(...)` |
| source run 人工确认 | `POST /api/v1/admin/source-runs/{id}/confirm` | `SourceUploadService.confirmRun(...) -> submitCompile(...)` | 是 | WAIT_CONFIRM 转 compile job |
| source run retry | `POST /api/v1/admin/source-runs/{id}/retry` | `SourceUploadService.retryRun(...) -> CompileJobService.retry(...)` 或重新 `submitCompile(...)` | 是 | 已有 compile job 时 retry 原 job；否则重新入队 |
| source sync | source 管理侧同步 | `SourceSyncWorkflowService.syncSource(...) -> SourceUploadService.acceptMaterializedSource(...) -> submitCompile(...)` | 是 | 物化资料源后提交 compile job |
| 公开 compile API | `POST /api/v1/compile` | `CompileController.compile(...) -> CompileApplicationFacade.compile(...) -> CompileJobService.submit(...)` | 是 | 同步执行 job |
| 公开 compile retry API | `POST /api/v1/compile/retry` | `CompileApplicationFacade.retry(...) -> CompileJobService.retryNow(...)` | 是 | retry 后同步执行原 job |
| CLI compile | `CompileCommand` standalone / remote | standalone 走 `CompileApplicationFacade.compile(...)`；remote 走 `/api/v1/compile` | 是 | 两种模式都进入 job service |
| MCP compile | `CompileMcpTools.lattice_compile` | `CompileApplicationFacade.compile(...)` | 是 | MCP 工具也进入 job service |

## 4. 调用链收敛点

核心链路：

```text
用户入口
  -> CompileJobService.submit(...) / retry(...)
  -> compile_jobs 固化 jobId/source/reviewMode/orchestrationMode
  -> CompileJobService.executeRunningJob(...)
  -> CompileExecutionRequest(..., reviewMode)
  -> CompileOrchestratorRegistry.execute(...)
  -> CompileOrchestrationModes.normalize(...)
  -> StateGraphCompileOrchestrator.execute(...)
  -> CompileGraphDefinitionFactory.build()
```

两个关键收敛事实：

| 事实 | 审计结果 |
|---|---|
| `CompileOrchestrationModes.normalize(...)` | 当前无条件返回 `state_graph` |
| `CompileOrchestrator` 实现 | 当前生产实现只发现 `StateGraphCompileOrchestrator` |

因此即使入口传入空值或其他 `orchestrationMode`，当前 registry 也会走 StateGraph。

## 5. StateGraph 闭环覆盖

StateGraph 节点与回边：

```text
compile_new_articles
  -> review_articles
  -> [passed] persist_articles
  -> [fixable] fix_review_issues
  -> review_articles
```

| 阶段 | StateGraph 节点 / 服务 | 覆盖情况 |
|---|---|---|
| Writer | `CompileNewArticlesNode -> ArticleCompileSupport.compileDraftArticles(...) -> DefaultWriterAgent -> CompileArticleNode.compileDraft(...)` | 覆盖 |
| Reviewer | `ReviewArticlesNode -> ArticleCompileSupport.reviewDraftArticles(...) -> DefaultReviewerAgent -> ArticleReviewerGateway` | 覆盖 |
| Fixer | `FixReviewIssuesNode -> ArticleCompileSupport.fixReviewedArticles(...) -> DefaultFixerAgent -> ReviewFixService` | 覆盖 |
| Re-review | `CompileGraphDefinitionFactory` 显式配置 `fix_review_issues -> review_articles` | 覆盖 |
| Persist gate | `PersistArticlesNode.retainPassedArticles(...)` 只保留 `review_status=passed` | 覆盖 |
| Query gate | 既有 article-backed hard filter 要求 `passed + ACTIVE` | 覆盖 article-backed 通道 |

## 6. per-job reviewMode 对入口链路的覆盖

agentA 当前改动使新 job 默认 `LLM`：

| 环节 | 当前行为 |
|---|---|
| `AdminCompileController.submit/uploadAndCompile` | 透传 `reviewMode` |
| `CompileJobService.submitInternal` | 使用 `CompileExecutionRequest.normalizeNewJobReviewMode(...)`；空值默认 `LLM` |
| `CompileJobRecord / compile_jobs.review_mode` | 持久化 job 级 reviewMode |
| `CompileExecutionRequest` | 执行时携带 `reviewMode` |
| `InitializeJobNode` | 从 job scope 解析并固化 `reviewMode` 到 `CompileGraphState` |
| `ArticleReviewerGateway` | 根据 job scope 或 requested mode 解析 reviewer 类型 |

注意点：`StateGraphCompileOrchestrator.execute(...)` 当前构造 `CompileGraphState` 时没有直接 `setReviewMode(executionRequest.getReviewMode())`。但 `InitializeJobNode` 会用 `state.jobId` 从 `compile_jobs.review_mode` 解析并固化，因此通过 `CompileJobService` 创建的用户 job 仍能拿到正确 reviewMode。  
这不影响用户入口覆盖；只影响不经过 `CompileJobService`、直接手工调用 `StateGraphCompileOrchestrator.execute(CompileExecutionRequest)` 的测试/内部路径，空 reviewMode 会按非 job scope 逻辑处理。

## 7. direct compile 路径审计

### 7.1 仍存在的 direct compile 调用

| 调用点 | 行为 | 是否用户入口 |
|---|---|---:|
| `CompilePipelineService.commitPendingConcepts(...)` | 调 `CompileArticleNode.compile(...)` 后直接 upsert article/chunks/vector | 否，当前未见 controller/facade/job service 调用 |
| `IncrementalCompileService` 创建新文章分支 | 调 `CompileArticleNode.compile(...)` 后直接 upsert article/chunks/vector | 否，当前未见用户入口路由到它 |
| `CompilePipelineService.compile(...) / retry(...) / incrementalCompile(...)` | 旧 service 直接执行 pipeline，不走 StateGraph | 未见当前用户入口调用 |
| 测试 | `CompilePipelineServiceTests`、`CompilePipelineVectorIndexingTests`、`CompilePipelineWalRecoveryTests` 等 | 否 |

### 7.2 direct compile 风险

旧式 `CompileArticleNode.compile(...)` 的风险：

| 风险 | 说明 |
|---|---|
| Fixer 后无二次 Reviewer | `reviewResult` 不通过后，若 `ReviewFixService.applyFix(...)` 产生内容，会直接置 `reviewStatus=passed` |
| 不经过 StateGraph review partition | 没有 `ReviewDecisionPolicy` 的 accepted/fixable/needs_human_review 分区 |
| 不经过 `PersistArticlesNode` | 直接 upsert article/chunks/vector，绕过当前 persist gate 实现点 |
| 不携带 per-job reviewMode | direct 调用没有 compile job scope，可能按全局 `review-enabled` 或 legacy 逻辑判断 |
| 审计信息不完整 | 不会产生完整 `compile_job_steps` 的 review/fix/re-review step 序列 |

如果未来把 `CompilePipelineService` 或 `IncrementalCompileService` 重新接入 controller / facade / orchestrator registry，这会成为实质治理绕过。当前未发现这种用户入口触发路径。

## 8. CompileApplicationFacade / CompilePipelineService / CompileJobService / Registry 关系

| 组件 | 当前生产入口角色 | 是否绕过 StateGraph |
|---|---|---:|
| `CompileApplicationFacade` | 公开 compile API、CLI、MCP 的同步门面 | 否，内部调用 `CompileJobService.submit(..., async=false, ...)` |
| `CompileJobService` | 所有 job 提交、执行、重试的中心 | 否，执行时调用 `CompileOrchestratorRegistry.execute(...)` |
| `CompileOrchestratorRegistry` | 按 mode 选择编排器 | 否，当前 normalize 固定为 `state_graph`，实现只见 `StateGraphCompileOrchestrator` |
| `StateGraphCompileOrchestrator` | 真正执行编译图 | 否，本身就是 StateGraph |
| `CompilePipelineService` | 旧 pipeline / 节点能力服务 / 测试覆盖对象 | 是，若被直接调用会绕过 StateGraph；当前未见用户入口调用 |

## 9. 是否影响 agentD runtime 验证

不影响当前 agentD 对默认 LLM review runtime 的结论，前提是 agentD 使用以下入口之一：

| 验证入口 | 结论 |
|---|---|
| `POST /api/v1/admin/compile/jobs` | 可验证默认 `LLM` job + StateGraph review loop |
| `POST /api/v1/admin/compile/upload` | 可验证 upload compile + StateGraph review loop |
| `POST /api/v1/admin/uploads` / source sync | 可验证 source sync compile job + StateGraph review loop |
| `POST /api/v1/compile` / CLI / MCP | 可验证 facade 路径 + StateGraph review loop |

不建议 agentD 用 `CompilePipelineService.compile(...)`、`IncrementalCompileService.incrementalCompile(...)` 或直接构造 `CompileArticleNode.compile(...)` 做 runtime 验证，因为这些不是当前用户入口主链，且会绕过本轮要验证的 job-scoped reviewMode / StateGraph 闭环。

## 10. 是否需要修代码

建议下一轮可以修，但不是阻塞 agentD 当前 runtime 验证的 blocker。

唯一最小动作建议：

> 封存旧式 direct compile 的生产可达性：将 `CompilePipelineService.compile/retry/incrementalCompile` 与 `IncrementalCompileService` 中 direct `CompileArticleNode.compile(...)` 路径限制为测试/内部非用户入口，或让其改为复用 StateGraph / `ArticleCompileSupport.compileDraftArticles + reviewDraftArticles + fixReviewedArticles + persist gate`。第一步只做可达性防护，不重构整条 pipeline。

若严格按最小文件范围执行，优先只做：

| 文件范围 | 目的 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/service/CompilePipelineService.java` | 防止旧 `compile/retry/incrementalCompile` 被误作为生产入口；不要动 StateGraph |
| `src/main/java/com/xbk/lattice/compiler/service/IncrementalCompileService.java` | 防止新文章分支 direct compile 绕过 review loop |
| 对应极窄测试 | 只验证当前用户入口仍走 StateGraph，direct legacy 路径不会被 controller/facade/job service 使用 |

不建议下一轮做大重构，也不建议在 agentD runtime 验证前混入这类清理。

## 11. 本轮是否修改代码

否。

本轮未修改源码、测试、配置、脚本，未运行 compile、未清库、未跑 baseline。仅新增本只读审计报告。
