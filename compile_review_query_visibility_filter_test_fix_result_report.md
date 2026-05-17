# Compile Review Query Visibility Filter Test Fix Result

## 1. 修改了哪些测试文件

本轮只修改 2 个相关 repository 测试文件：

- `src/test/java/com/xbk/lattice/infra/persistence/ArticleChunkJdbcRepositoryTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/VectorJdbcRepositoryOperatorTests.java`

## 2. 是否修改生产代码

否。

说明：工作区中仍包含上一轮已完成的 5 个 mapper XML hard filter 改动；本轮没有继续修改任何 `src/main/java/**` 或 `src/main/resources/**` 生产文件。

## 3. 哪些 fixture 从 pending 改为 passed

- `ArticleChunkJdbcRepositoryTests.shouldSearchArticleChunksByLexicalIndex`
  - 原命中文章 `payment` 使用 `ArticleRecord` 短构造器，默认 `review_status=pending`。
  - 本轮改为显式构造 `review_status=passed`，`lifecycle=ACTIVE` 保持不变。

- `VectorJdbcRepositoryOperatorTests.shouldSearchArticleVectorsWhenOperatorLivesInPublicSchema`
  - 原命中文章 `refund-status` 通过 `seedArticle(...)` 写入 `review_status=pending`。
  - 本轮将默认 `seedArticle(...)` 调整为写入 `review_status=passed`，`lifecycle=ACTIVE` 保持不变。

- `VectorJdbcRepositoryOperatorTests.shouldSearchChunkVectorsWhenOperatorLivesInPublicSchema`
  - 同样复用默认 `seedArticle(...)`，命中文章 `refund-status` 现在显式为 `review_status=passed`，`lifecycle=ACTIVE`。

## 4. 是否补充 pending / needs_human_review 不可见断言

是，补充了 `pending` 不可见断言。

- `ArticleChunkJdbcRepositoryTests`
  - 新增同样可匹配查询词的 `payment-pending` 文章与 chunk。
  - 断言检索结果不包含 `payment-pending`。

- `VectorJdbcRepositoryOperatorTests`
  - 文章级向量检索新增 `refund-status-pending` article vector。
  - chunk 级向量检索新增 `refund-status-pending` article chunk vector。
  - 两个测试均断言结果不包含 `refund-status-pending`。

未补 `needs_human_review` 对照；本轮按最小测试 fixture 修复，只补 `pending` 不可见覆盖。

## 5. 定向测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=ArticleChunkJdbcRepositoryTests,VectorJdbcRepositoryOperatorTests test
```

结果：

- Tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

## 6. 全量 mvn test 结果

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

## 7. redline

修复前：

- BLOCKER=0
- REVIEW=1351
- ALLOWLIST=166

修复后：

- BLOCKER=0
- REVIEW=1351
- ALLOWLIST=166

## 8. 是否删除/跳过测试

否。没有删除测试，没有增加 skip/disable，也没有把断言改成接受空结果。

## 9. 是否修改 SQL hard filter

否。本轮没有修改 5 个 mapper XML，也没有放宽 `review_status='passed'` / `lifecycle='ACTIVE'` hard filter。
