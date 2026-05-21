# Compile 发布语义 + 待人工确认展示组合提交前质量复核报告

## 结论

建议一起提交。

这两组改动属于同一业务主题的前后两半：

1. `source-run / processing-tasks` 说清楚“系统执行完成”与“正式发布完成”不是一回事
2. 后台页面把“待人工确认任务数”和“草稿篇数”讲清楚，并把 review queue 相关可见文案和筛选口径收拢

如果拆开提交，第一组会把后端语义修正到位，但前端顶部摘要、任务卡和 review queue 视觉/筛选口径仍然半旧半新；第二组单独提交则会依赖第一组新增的 publish outcome 字段，叙事不完整。合并成一个提交更稳。

推荐 commit message：

`feat(admin): align compile publish semantics with human review queue state`

## 1. 当前改动文件清单

### 生产代码

- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskPresentationResolver.java`
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskItemResponse.java`
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
- `src/main/java/com/xbk/lattice/source/domain/SourceSyncRunDetail.java`
- `src/main/java/com/xbk/lattice/source/service/SourceUploadService.java`
- `src/main/java/com/xbk/lattice/source/service/SourceUploadWorkflowSupport.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
- `src/main/resources/static/admin/admin.css`
- `src/main/resources/static/admin/index.html`
- `src/main/resources/static/admin/modules/management-runtime-part-03.js`
- `src/main/resources/static/admin/modules/management-runtime-part-04.js`
- `src/main/resources/static/admin/modules/management-runtime-part-05.js`

### 测试

- `src/test/java/com/xbk/lattice/admin/service/AdminProcessingTaskPresentationResolverTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminProcessingTaskControllerTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminUploadControllerTests.java`
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`

### 报告 / redline

- `special_cases_report.md`
- `compile_source_run_publish_semantics_fix_result_report.md`
- `compile_source_run_publish_semantics_runtime_verification_report.md`
- `compile_source_run_publish_semantics_design_report.md`
- `compile_small_sample_end_to_end_acceptance_report.md`
- `admin_review_queue_count_filter_visual_fix_result_report.md`
- `admin_review_queue_count_filter_visual_runtime_verification_report.md`

## 2. 是否仍围绕同一业务主题

是。

可以把当前改动分成两层，但它们解决的是同一个用户问题：

### A. 发布语义修复

目标：

- 在 source-run / processing-tasks 中区分：
  - compile 自动执行结束
  - 草稿待人工确认
  - 部分发布
  - 全部发布
  - 全部驳回

核心文件：

- `SourceUploadWorkflowSupport`
- `AdminProcessingTaskPresentationResolver`
- `AdminProcessingTaskService`
- `SourceSyncRunDetail`
- `AdminProcessingTaskItemResponse`
- queue repository / mapper 聚合

### B. review queue / 待人工确认展示修复

目标：

- 顶部摘要按“任务数”展示待人工确认
- 单任务卡正文按“草稿篇数”展示
- 已入库内容移除 `needs_human_review` 筛选
- review queue 区块视觉高对比、口径统一

核心文件：

- `static/admin/**`
- `ManagementJsRuntimeTests.java`

两者在业务上是串联关系，不是两个无关特性。

## 3. 是否越界修改

### 编译 Writer / Reviewer / Fixer 主链

没有。

未修改 `src/main/java/com/xbk/lattice/compiler/**` 中的 Writer / Reviewer / Fixer 执行逻辑，也未改 compile graph 行为。

### approve / reject / publish 行为

没有。

当前改动只读取 `compile_article_review_queue` 的：

- `needs_human_review`
- `published`
- `rejected`

用于展示和统计，没有改：

- `markPublished(...)`
- `markRejected(...)`
- 正式 articles/chunks/vector 写入路径

### Query / AnswerGeneration

没有。

未出现 `src/main/java/com/xbk/lattice/query/**` 或 `AnswerGeneration*` 相关改动。

### schema

没有。

没有修改 `src/main/resources/db/schema.sql`。

### 模型配置

没有。

没有修改 `.claude/t1.md`、LLM binding、provider、profile 或 YAML 配置。

## 4. redline 状态

本轮重新执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

`special_cases_report.md` 只刷新了扫描时间戳，没有规则、范围或 allowlist 变更。

## 5. 测试是否足够

整体上足够，且是分层覆盖。

### compile publish semantics

修复报告已给出：

- 定向测试：`24 / 0 / 0`
- 全量测试：`854 / 0 / 0`

覆盖点包括：

- queue `summarizeByJobId(...)`
- `SourceSyncRunDetail` 新增 publish outcome
- `AdminProcessingTaskPresentationResolver` 各场景语义
- `AdminProcessingTaskController`
- `AdminUploadController`

runtime 验证报告已覆盖 4 类真实场景：

- 全待人工确认
- 部分 approve + 部分 reject
- 全 approve
- 全 reject

### admin 前端 / review queue 展示

修复报告已给出：

- 定向测试：`ManagementJsRuntimeTests + AdminPageControllerTests`
- 结果：`5 / 0 / 0`
- 多个 `node --check` 通过

runtime 验证覆盖：

- 顶部摘要“待人工确认任务”口径
- 任务卡正文“待人工确认草稿 X 篇”口径
- 已入库内容移除 `needs_human_review` 筛选
- review queue 高对比样式

### 组合角度的判断

因为第一组修复已经有全量测试 `854 / 0 / 0`，第二组又补了前端定向测试和 runtime 验证，所以**不需要为组合提交再额外跑 SWIP 或清库重建**。

## 6. 是否适合组合为一个 commit

适合。

原因：

- 同一业务主题：人工确认发布的真实语义
- 同一用户路径：首页摘要 -> 当前处理任务 -> 待人工确认队列
- 同一数据源：`compile_article_review_queue`
- 同一提交后效果：用户终于能同时看到“任务有没有跑完”和“草稿有没有正式发布”

如果硬拆，会产生两个问题：

1. 先提“发布语义修复”，前端任务数/草稿数/筛选口径还不一致
2. 先提“前端展示修复”，又依赖后端 publish outcome 字段，含义不完整

因此组合提交更合理。

## 7. 是否存在阻塞项

当前未发现阻塞项。

已确认无阻塞的点：

- 没有越界到编译主链
- 没有越界到 approve / reject / publish 行为
- 没有越界到 Query / AnswerGeneration
- 没有 schema 变更
- redline `BLOCKER=0`
- 定向测试 + 全量测试 + runtime 验证均存在

剩余非阻塞说明：

- review queue 视觉 runtime 仍以 0 条待人工确认任务为主，正数视觉更多依赖 `ManagementJsRuntimeTests` 和前一轮 runtime 语义场景验证
- 这不构成阻塞，因为正数场景在 compile publish semantics runtime 验证中已经真实跑过

## 8. 是否建议一起提交

建议一起提交。

这是一个完整的“人工确认发布语义对齐”改动包，合并后用户得到的是：

- source-run 不再在未发布时说“资料已写入知识库”
- processing-tasks 顶部摘要和任务卡不再混淆“任务数”和“草稿篇数”
- review queue 页面和已入库筛选不再把待人工确认和正式已入库内容混用

## 9. 推荐 commit message

推荐：

`feat(admin): align compile publish semantics with human review queue state`

如果希望更强调任务展示，也可以用：

`feat(admin): reflect human review publish state in processing tasks`

## 10. 如果不建议一起提交，建议如何拆分

本轮不建议拆分。

如将来必须拆，唯一相对合理的拆法是：

1. `feat(admin): expose compile publish outcome in source-run tasks`
2. `feat(admin): clarify human review queue counts and wording`

但这不是当前首选。

## 11. 本轮是否修改代码

否。

本轮只做组合提交前质量复核，并新增本报告文件。
