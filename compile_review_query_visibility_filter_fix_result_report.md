# Compile Review Query Visibility Filter Fix Result

## 1. 修改了哪些 mapper

本轮只修改 5 个 article-backed 查询 mapper：

- `src/main/resources/com/xbk/lattice/query/service/mapper/ArticleFtsSearchMapper.xml`
- `src/main/resources/com/xbk/lattice/query/service/mapper/RefKeySearchMapper.xml`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkMapper.xml`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleVectorMapper.xml`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkVectorMapper.xml`

未修改 Java 主链、测试、source/source_chunk mapper、fact card mapper/service/policy。

## 2. 每条 article-backed 通道新增的 hard filter

| 通道 | Mapper | 新增 hard filter |
|---|---|---|
| Article FTS | `ArticleFtsSearchMapper.xml` | `a.review_status = 'passed'` and `a.lifecycle = 'ACTIVE'` |
| RefKey article search | `RefKeySearchMapper.xml` | `a.review_status = 'passed'` and `a.lifecycle = 'ACTIVE'` |
| Article chunk lexical | `ArticleChunkMapper.xml` | `a.review_status = 'passed'` and `a.lifecycle = 'ACTIVE'` |
| Article vector | `ArticleVectorMapper.xml` | `a.review_status = 'passed'` and `a.lifecycle = 'ACTIVE'` |
| Article chunk vector | `ArticleChunkVectorMapper.xml` | `article.review_status = 'passed'` and `article.lifecycle = 'ACTIVE'` |

`RefKeySearchMapper.xml` 与 `ArticleChunkMapper.xml` 中原有 `OR` 条件已被括号包住，避免 `OR` 绕过新增 hard filter。

## 3. 是否过滤 review_status='passed'

是。5 条 article-backed 查询通道均增加 `review_status = 'passed'` hard filter。

## 4. 是否过滤 lifecycle='ACTIVE'

是。5 条 article-backed 查询通道均增加 `lifecycle = 'ACTIVE'` hard filter。

## 5. 是否修改 source/source_chunk

否。

## 6. 是否修改 fact card

否。

## 7. 是否修改 Java 主链

否。

## 8. 是否修改测试

否。

## 9. redline

修复前：

- BLOCKER=0
- REVIEW=1351
- ALLOWLIST=166

修复后：

- BLOCKER=0
- REVIEW=1351
- ALLOWLIST=166

## 10. mvn test 是否通过

未通过。

执行命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

- Tests run: 812
- Failures: 3
- Errors: 0
- Skipped: 0

失败项：

- `ArticleChunkJdbcRepositoryTests.shouldSearchArticleChunksByLexicalIndex`
- `VectorJdbcRepositoryOperatorTests.shouldSearchArticleVectorsWhenOperatorLivesInPublicSchema`
- `VectorJdbcRepositoryOperatorTests.shouldSearchChunkVectorsWhenOperatorLivesInPublicSchema`

原因判断：

- 3 个失败测试都构造了 `review_status=pending` 的 article fixture。
- 本轮 hard filter 只允许 `review_status='passed'` 且 `lifecycle='ACTIVE'` 的 article-backed evidence 进入查询结果。
- 因此这些旧 fixture 不再应被 article-backed 查询通道召回。
- 本轮禁止修改测试，且不应通过把 `pending` 放回 SQL 可见集合来换取测试通过，否则会破坏 query visibility hard filter 的治理目标。

## 11. 是否建议进入验证轮

否。

建议下一轮只做一个最小动作：补齐或调整 article-backed repository 测试 fixture，使查询类测试显式写入 `review_status=passed` 的 article，并另补一个 `pending/needs_human_review` 不可见断言。不要修改本轮 SQL hard filter 语义。
