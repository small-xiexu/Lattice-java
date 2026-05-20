# compile human review queue approve vector fix result report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueService.java`
  - `approve(...)`：发布成功并将 queue 标记为 `published` 后，改为调度发布后向量刷新。
  - 新增 `scheduleVectorRefreshAfterPublication(...)`：如果当前存在事务同步，则在 `afterCommit` 后执行向量刷新；否则直接刷新。
  - 新增 `refreshPublishedArticleVectorIndex(...)`：优先使用 `PROPAGATION_REQUIRES_NEW` 独立事务刷新向量索引。
  - 新增 `doRefreshPublishedArticleVectorIndex(...)`：按 `articleKey` 重新读取正式落库文章，再调用现有向量刷新能力。
  - 新增 Spring 注入构造器与 `buildVectorRefreshTransactionTemplate(...)`，用于生产环境获取事务管理器；保留测试构造器。
- `src/test/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueServiceTests.java`
  - 补强 approve 断言：确认向量刷新使用的是正式落库后的 `passed` article。
  - 保留 reject 断言：确认 reject 不写文章、不 rebuild chunk、不刷新 vector。

## 2. approve 后如何触发 vector index

- approve 仍先执行正式发布：`persistArticles(...)` 写入 `articles`，`rebuildArticleChunks(...)` 写入 `article_chunks`，审计写入后将 queue 标记为 `published`。
- queue 状态更新成功后注册 `TransactionSynchronization.afterCommit()`。
- 发布事务提交后，再用新事务按 `articleKey` 重新读取正式文章，并调用 `articlePersistSupport.refreshVectorIndex(...)`。
- 这样 article / chunks 已提交可见，`ArticleChunkVectorIndexService` 能读到刚发布的 chunk。

## 3. 是否复用现有 vector refresh / embedding 能力

是。复用 `ArticlePersistSupport.refreshVectorIndex(...)`，继续走现有：

- `ArticleVectorIndexService.indexArticle(...)`
- `ArticleChunkVectorIndexService.indexArticle(...)`

本轮没有新增人工确认专用 embedding 逻辑。

## 4. reject 是否仍不触发 vector index

是。`reject(...)` 未调用 `persistArticles(...)`、`rebuildArticleChunks(...)` 或 `refreshVectorIndex(...)`；现有测试仍覆盖 reject 不触发 vector。

## 5. 未 approve 草稿是否仍不生成 vector index

是。只有 `approve(...)` 在发布并提交后触发向量刷新；queue 中待确认草稿不会进入正式 `articles`，也不会生成 vector index。

## 6. 是否修改 Query / AnswerGeneration

否。

## 7. 是否修改 Reviewer / Fixer / prompt

否。

## 8. 是否修改前端

否。

## 9. redline BLOCKER 是否为 0

是。`bash scripts/scan-redline.sh special_cases_report.md` 通过，报告结果：

- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

## 10. mvn test 是否通过

通过。

- 定向测试：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AdminCompileArticleReviewQueueServiceTests,AdminCompileReviewQueueControllerTests,AdminVectorIndexControllerTests,ArticleVectorIndexServiceTests test`
  - 结果：`15 / 0 / 0`
- 全量测试：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`844 / 0 / 0`，`BUILD SUCCESS`

## 11. 下一步建议

交给 agentD 重新运行 approve vector runtime verification，重点确认真实环境 approve 后：

- `article_vector_index > 0`
- `article_chunk_vector_index > 0`
- reject 后两类 vector index 仍不增加
