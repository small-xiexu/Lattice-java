# compile_review_queue_approve_idempotency_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueService.java`
  - 修改 `approve(...)`
  - 新增 `approveAlreadyPublishedArticle(...)`
  - 新增 `findExistingArticleByKey(...)`
  - 将 `assertNoArticleConflict(...)` 拆为 `assertNoSourceConceptConflict(...)`
- `src/test/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueServiceTests.java`
  - 将 `article_key` 已存在场景改为幂等成功断言
  - 新增 `sourceId + conceptId` 仍报错的负向断言

## 2. approve 之前遇到已存在 article_key 时为什么报错

修复前，`approve(...)` 在正常发布前会调用冲突校验：

- 先查 `articleJdbcRepository.findByArticleKey(queueRecord.getArticleKey())`
- 一旦命中就直接抛出：
  - `IllegalStateException("article already exists: ...")`

因此，即使这个 article 实际上已经存在于正式知识库中、从业务语义上可以视为“已经完成”，接口仍会被收敛成失败，最终表现为：

- `COMPILE_EXECUTION_FAILED: article already exists`

## 3. 现在如何做幂等处理

现在 `approve(...)` 改成了两段处理：

1. 如果 `article_key` **不存在**：
   - 继续走原来的正常发布路径
   - 正常落库 / rebuild chunk / refresh vector / 写审计 / 队列置为 `published`

2. 如果 `article_key` **已存在**：
   - 不再抛错
   - 直接走 `approveAlreadyPublishedArticle(...)`
   - 只做幂等收口：
     - 写一条 approve 审计
     - 将队列状态置为 `published`
     - `publishedArticleKey` 写回已存在的 article key
     - 返回成功结果
   - **不会重复执行**：
     - `persistArticles(...)`
     - `rebuildArticleChunks(...)`
     - `refreshVectorIndex(...)`

这样用户再次 approve 已存在的 article_key 时，会得到成功响应，而不是失败。

## 4. 是否会误吞其他真实错误

不会。

本轮只对这一个已知幂等冲突做收口：

- `articleJdbcRepository.findByArticleKey(queueRecord.getArticleKey())` 命中

除此之外，其它真实错误仍然保留原语义，例如：

- `sourceId + conceptId` 冲突但 `article_key` 不同
- 队列状态变化
- 正常发布路径中的落库 / chunk / vector / 审计异常

这些情况仍会继续抛出异常，不会被这轮幂等保护误吞。

## 5. 是否修改 reject 逻辑

否。

- `reject(...)` 未修改
- 驳回语义、审计与状态流转保持不变

## 6. 是否修改 compile 主链

否。

- 未修改 compile Writer / Reviewer / Fixer 主链
- 未修改 queue 去重逻辑
- 未修改 schema
- 未修改 Query / AnswerGeneration

## 7. redline BLOCKER 是否仍为 0

- 已运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`，`REVIEW=1911`，`ALLOWLIST=245`

## 8. 测试是否通过

- 定向测试通过：
  - `AdminCompileArticleReviewQueueServiceTests`
  - `AdminCompileReviewQueueControllerTests`
- 结果：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- 全量 `mvn test`：`Tests run: 862, Failures: 0, Errors: 0, Skipped: 0`

## 9. 下一轮是否建议交给 agentD 做 runtime 复验

建议。

下一轮建议 agentD 做运行时复验，重点确认：

- 待人工确认草稿 approve 时，若 `article_key` 已存在，接口返回成功而非失败
- 队列状态正确收口为 `published`
- `publishedArticleKey` 正确返回
- 正常 approve / reject 路径未受影响
