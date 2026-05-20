# Admin Dashboard Review Queue Summary Fix Result Report

## 1. 修改了哪些文件

- `src/main/java/com/xbk/lattice/governance/StatusSnapshot.java`
  - 新增 `humanReviewDraftPendingCount` 字段、JSON 构造参数和 getter。
  - 保留旧构造器兼容现有测试与调用，旧路径默认该字段为 `0`。
- `src/main/java/com/xbk/lattice/governance/StatusService.java`
  - 注入 `CompileArticleReviewQueueJdbcRepository`。
  - `snapshot()` 中新增人工确认草稿待处理数统计。
  - 保留旧构造器兼容现有单元测试。
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
  - 新增 `countByStatus(String status)`。
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
  - 新增 `countByStatus` mapper 方法。
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
  - 新增按 `review_status` 计数 SQL。
- `src/main/resources/static/admin/modules/management-runtime-part-02.js`
  - 状态摘要新增“待人工确认草稿”卡片。
  - 首页提示在存在待确认草稿时引导到“当前处理任务”处理。
- `src/test/java/com/xbk/lattice/governance/StatusServiceTests.java`
  - 覆盖 `humanReviewDraftPendingCount` 汇总。
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`
  - 覆盖 `needs_human_review` 草稿计数。
- `src/test/java/com/xbk/lattice/api/admin/AdminOverviewControllerTests.java`
  - 覆盖 `/api/v1/admin/overview` 返回新字段。
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`
  - 覆盖前端摘要卡片与提示引导。

## 2. 新增字段名称

- `humanReviewDraftPendingCount`

## 3. 统计口径

- 统计来源：`compile_article_review_queue`
- 统计条件：`review_status = needs_human_review`

## 4. 语义区分

- “需复核内容”：保持原语义，仍表示已经进入 `articles` 表、且 `review_status != passed` 的已入库文章复核积压。
- “待人工确认草稿”：新增语义，表示编译审查后仍停留在 `compile_article_review_queue` 中、等待人工确认发布的草稿。

## 5. Redline

- 初始 redline：通过。
- 最终 redline：通过。
- 最终结果：`BLOCKER=0`，`REVIEW=1863`，`ALLOWLIST=244`。

## 6. 测试结果

- JS 语法检查：`node --check src/main/resources/static/admin/modules/management-runtime-part-02.js` 通过。
- 定向测试：
  - `StatusServiceTests`
  - `CompileArticleReviewQueueJdbcRepositoryTests`
  - `AdminOverviewControllerTests`
  - `ManagementJsRuntimeTests`
  - 结果：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- 全量测试：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`Tests run: 844, Failures: 0, Errors: 0, Skipped: 0`

## 7. 是否修改编译主链

- 否。

## 8. 是否修改 Query / AnswerGeneration

- 否。

## 9. 是否修改 schema

- 否。
