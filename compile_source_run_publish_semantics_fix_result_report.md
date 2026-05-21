# Compile Source Run Publish Semantics Fix Result Report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
  - 新增 `summarizeByJobId(String jobId)`
  - 新增内部只读投影 `PublishOutcomeSummary`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
  - 新增 `summarizeByJobId(...)`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
  - 新增按 `job_id` 聚合 `needs_human_review / published / rejected` 的 SQL
- `src/main/java/com/xbk/lattice/source/domain/SourceSyncRunDetail.java`
  - 新增 `pendingHumanReviewCount`
  - 新增 `publishedCount`
  - 新增 `rejectedCount`
  - 保留旧构造器兼容原调用
- `src/main/java/com/xbk/lattice/source/service/SourceUploadWorkflowSupport.java`
  - `toDetail(...)` 接入 publish outcome 汇总
  - 新增 `summarizePublishOutcome(...)`
  - 让 `SourceSyncRunDetail.message` 在人工确认发布场景下对齐业务语义
- `src/main/java/com/xbk/lattice/source/service/SourceUploadService.java`
  - 构造器注入 `CompileArticleReviewQueueJdbcRepository`
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskPresentationResolver.java`
  - `resolve(...)` 新增带 publish outcome 统计的重载
  - 新增 publish outcome 语义覆盖逻辑
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java`
  - `toSourceSyncTask(...)` 透传 publish outcome 统计
  - `buildSummary(...)` / `buildSummaryCards(...)` 按待人工确认发布语义修正 waiting / completed 统计与说明
- `src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskItemResponse.java`
  - 新增 `pendingHumanReviewCount`
  - 新增 `publishedCount`
  - 新增 `rejectedCount`
  - 保留旧构造器兼容原调用

测试文件：

- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`
- `src/test/java/com/xbk/lattice/admin/service/AdminProcessingTaskPresentationResolverTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminProcessingTaskControllerTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminUploadControllerTests.java`

## 2. 是否新增了按 jobId 聚合的 publish outcome 统计

- 是。
- 聚合口径：
  - `pendingHumanReviewCount`
  - `publishedCount`
  - `rejectedCount`
- 聚合维度：
  - `compile_article_review_queue.job_id`

## 3. SourceSyncRunDetail 新增了哪些字段

- `pendingHumanReviewCount`
- `publishedCount`
- `rejectedCount`

## 4. 各人工确认场景现在的 displayStatusLabel / completionNotice

- 全部待人工确认，尚未发布
  - `displayStatusLabel = 待人工确认`
  - `completionNotice = 草稿尚未入库，需人工确认后才能发布`
- 部分 approve，部分待确认
  - `displayStatusLabel = 待人工确认`
  - `completionNotice = 部分内容已入库，其余仍待人工确认`
- 部分 approve，部分 reject，且已无待确认
  - `displayStatusLabel = 已处理`
  - `completionNotice = 本次草稿已处理完成，但只有部分内容进入知识库`
- 全部 approve
  - `displayStatusLabel = 已完成`
  - `completionNotice = 资料已正式发布到知识库`
- 全部 reject
  - `displayStatusLabel = 未入库`
  - `completionNotice = 本次草稿已全部驳回，未进入正式知识库`

补充：

- 当某个 compile job 根本没有进入人工确认队列时，仍保留原先自动成功发布语义，不做误伤。

## 5. 是否修改了编译主链

- 否。
- 未修改 `src/main/java/com/xbk/lattice/compiler/**` 的 Writer / Reviewer / Fixer 主链行为。

## 6. 是否修改了 approve / reject / publish

- 否。
- 未修改人工确认 approve / reject / publish 落库逻辑，只读取其结果并做展示语义修正。

## 7. 是否修改了 SourceSyncRun 持久化主状态机

- 否。
- `SourceSyncRun.status` 持久化主状态值定义未改。

## 8. redline BLOCKER 是否仍为 0

- 是。
- 最终结果：
  - `BLOCKER=0`
  - `REVIEW=1863`
  - `ALLOWLIST=244`

## 9. 测试是否通过

定向测试通过：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=CompileArticleReviewQueueJdbcRepositoryTests,AdminProcessingTaskPresentationResolverTests,AdminProcessingTaskControllerTests,AdminUploadControllerTests test`
- 结果：`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`

全量测试通过：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：`Tests run: 854, Failures: 0, Errors: 0, Skipped: 0`

## 10. 下一轮是否建议交给 agentD 做小样本链路复验

- 是。
- 建议 agentD 用小样本真实链路分别复验：
  - 全待人工确认
  - 部分发布 + 待确认
  - 全驳回
  - 全发布
- 重点核对 `source-run`、`processing-tasks`、首页帮助卡与 summary 统计是否一致。
