# Compile Human Review Queue Backend Fix Result Report

## 1. 修改文件和方法

- `src/main/resources/db/schema.sql`
  - 新增 `compile_article_review_queue` 表与状态、任务、来源、文章键索引。
- `src/main/java/com/xbk/lattice/compiler/graph/node/ReviewArticlesNode.java`
  - 在最终 review 分区后，将 `needs_human_review` 草稿交给持久化队列服务入队。
- `src/main/java/com/xbk/lattice/compiler/service/CompileArticleReviewQueueService.java`
  - 新增编译人工确认草稿入队服务，持久化草稿内容、review issues、route/model、fix round 元数据。
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueRecord.java`
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
  - 新增 review queue record、repository、MyBatis mapper 与 SQL。
- `src/main/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueService.java`
  - 新增后台人工确认服务：列表、详情、approve、reject。
  - `approve` 复用现有 `ArticlePersistSupport` 写入正式文章、重建 chunks、刷新 vector index，并写入 article review audit。
  - `reject` 仅更新队列状态并写入 audit，不写入正式文章。
- `src/main/java/com/xbk/lattice/admin/service/CompileArticleReviewQueueActionRequest.java`
- `src/main/java/com/xbk/lattice/admin/service/CompileArticleReviewQueueActionResult.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueController.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueActionRequest.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueActionResponse.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueItemResponse.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueListResponse.java`
  - 新增后台 API DTO 与 Controller。
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`
- `src/test/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueServiceTests.java`
- `src/test/java/com/xbk/lattice/compiler/graph/node/ReviewArticlesNodeHumanReviewQueueTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueControllerTests.java`
  - 新增 repository、admin service、review node 入队、controller API 覆盖。

## 2. 新增表 / Repository / API

- 新增表：`compile_article_review_queue`
  - 保存 `job_id`、`source_id/source_code`、`concept_id`、`article_key`、`title`、`content`、`metadata_json`、`review_status`、`review_route`、`reviewer_model`、`review_issues_json`、fix round、审核人与审核时间、发布后的 article key。
  - 状态包括 `needs_human_review`、`published`、`rejected`、`superseded`。
- 新增 Repository：`CompileArticleReviewQueueJdbcRepository`
  - 支持 pending upsert、按状态列表、详情、标记 published、标记 rejected。
- 新增 API：
  - `GET /api/v1/admin/compile/review-queue?status=needs_human_review`
  - `GET /api/v1/admin/compile/review-queue/{id}`
  - `POST /api/v1/admin/compile/review-queue/{id}/approve`
  - `POST /api/v1/admin/compile/review-queue/{id}/reject`

## 3. needs_human_review 草稿持久化

已实现。`ReviewArticlesNode` 在最终分区得到 `needs_human_review` 草稿后，会写入 `compile_article_review_queue`，不再只依赖 Redis working set。队列内容包含草稿正文、review issues、review route/model、fix attempt/max rounds 与来源元数据。

## 4. approve 发布行为

已实现。`approve` 后草稿以 `review_status=passed`、`lifecycle=ACTIVE` 写入正式 `articles`，并复用现有文章持久化能力重建 `article_chunks` 与刷新向量索引。发布动作写入 `article_review_audit`，metadata 中记录 `compile_review_queue`、queue id 和人工审核时间。

如正式 `article_key` 或同一 `source_id + concept_id` 已存在，当前实现返回明确冲突，不静默覆盖。

## 5. reject 行为

已实现。`reject` 只将队列状态改为 `rejected` 并写入 audit，不写入 `articles`、不重建 chunks、不刷新 vector index。

## 6. 未确认前 query 可见性

未确认草稿只写入 `compile_article_review_queue`，不写入正式 `articles`、`article_chunks` 或 vector index，因此不会进入现有 query 可见路径。未修改 query visibility hard filter，也未把 `needs_human_review` 放开为可见状态。

## 7. 禁止范围确认

- 是否修改 Query / AnswerGeneration：否。
- 是否修改 Reviewer / Fixer / prompt：否。
- 是否修改前端：否。
- 是否修改模型配置：否。
- 是否清库 / 重建 / 跑 SWIP eval：否。

## 8. 测试和门禁

- 定向测试：
  - `CompileArticleReviewQueueJdbcRepositoryTests`
  - `AdminCompileArticleReviewQueueServiceTests`
  - `ReviewArticlesNodeHumanReviewQueueTests`
  - `AdminCompileReviewQueueControllerTests`
  - `PersistArticlesNodeTests`
  - `ArticleManualReviewServiceTests`
  - 结果：15 / 0 / 0，通过。
- 全量测试：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 第二次全量结果：844 / 0 / 0，通过。
  - 首次全量曾出现 `AdminOverviewControllerTests` 与 `AdminSourceControllerTests` 的非本轮相关测试隔离波动；两个测试单独重跑通过，随后第二次全量通过。
- redline：
  - `bash scripts/scan-redline.sh special_cases_report.md`
  - `BLOCKER=0`，`REVIEW=1863`，`ALLOWLIST=244`。

## 9. 结论

本轮后端最小闭环已完成：编译产生的 `needs_human_review` 草稿会进入持久化人工确认队列；后台 API 可查询、查看、批准发布、驳回；批准后才进入正式文章、chunks 与 vector index；驳回不入库；未确认前 query 不可见。

下一轮建议：交给 agentD 做运行时验证，再补前端人工确认入口。
