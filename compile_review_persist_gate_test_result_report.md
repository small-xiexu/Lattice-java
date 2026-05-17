# Compile Review Persist Gate Test Result Report

## 1. 修改文件

- 新增测试文件：`src/test/java/com/xbk/lattice/compiler/graph/node/PersistArticlesNodeTests.java`
- 本轮未修改生产代码。当前工作区中 `src/main/java/com/xbk/lattice/compiler/graph/node/PersistArticlesNode.java` 的修改来自前序 persist gate 修复，不属于本轮测试补强新增改动。

## 2. 是否修改生产代码

- 否。

## 3. 测试构造方式

测试 `shouldPersistOnlyPassedArticlesWhenAcceptedRefContainsMixedReviewStatuses` 构造了两个 `ArticleReviewEnvelope`：

- `approved-concept`：`review_status=passed`，`ReviewResult.passed()`
- `review-needed-concept`：`review_status=needs_human_review`，`ReviewResult.issuesFound(...)`

测试将两者同时写入 `acceptedArticlesRef`，并将 `needs_human_review` 文章写入 `needsHumanReviewArticlesRef`。同时设置 `allowPersistNeedsHumanReview=true`，用于覆盖旧风险路径：即使配置允许持久化人工复核文章，正式 query-facing persist 仍只能接收 passed 文章。

## 4. 断言内容

- 断言 `ArticlePersistSupport.persistArticles(...)` 收到的列表只包含 `approved-concept`。
- 断言正式 persist 列表的 `reviewStatus` 只包含 `passed`。
- 断言正式 persist 列表不包含 `needs_human_review`。
- 断言 `rebuildArticleChunks(...)` 收到的列表与正式 persist 列表一致，也只包含 passed 文章。

## 5. 外部依赖

- 不依赖真实 PostgreSQL。
- 不依赖 Redis。
- 不依赖 LLM。
- 测试使用 `InMemoryCompileWorkingSetStore` 与手写 `RecordingArticlePersistSupport` 捕获落库入参，不触发真实落库、分块重建或模型调用。

## 6. 定向测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=PersistArticlesNodeTests test
```

结果：

- Tests run: 1
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

## 7. 全量测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

- Tests run: 812
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

## 8. Redline

修复后 redline：

- BLOCKER=0
- REVIEW=1351
- ALLOWLIST=166

## 9. 业务特判

- 是否新增业务特判：否。
- 测试未使用 SWIP、卡券、业务文档、eval case 或具体业务文件名作为测试语义。

## 10. 范围确认

- 未修改 `src/main/java/**`。
- 未修改 `src/main/resources/**`。
- 未修改 `scripts/**`。
- 未修改 `docs/**`。
- 未修改 redline allowlist。
- 未开启 LLM reviewer。
- 未修改 query visibility filter。
- 未清库、重建、导入资料或运行 baseline / SWIP eval。
