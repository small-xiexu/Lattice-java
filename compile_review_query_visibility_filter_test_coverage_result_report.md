# Query Visibility Hard Filter 测试覆盖补强结果报告

## 1. 修改了哪些测试文件

- `src/test/java/com/xbk/lattice/query/service/ArticleVisibilitySearchMapperTests.java`
  - 新增 Article FTS 与 RefKey mapper 直接覆盖。
- `src/test/java/com/xbk/lattice/infra/persistence/ArticleChunkJdbcRepositoryTests.java`
  - 补强 ArticleChunk lexical 查询的 review status 与 lifecycle 负例。
- `src/test/java/com/xbk/lattice/infra/persistence/VectorJdbcRepositoryOperatorTests.java`
  - 补强 ArticleVector 与 ArticleChunkVector 查询的 review status 与 lifecycle 负例。

说明：当前工作区仍存在上一轮已产生的 mapper XML / 其他报告改动，本轮没有修改这些文件，也没有触碰 `compile_review_observability_verification_report.md`。

## 2. 是否修改生产代码

否。

本轮未修改：

- `src/main/java/**`
- `src/main/resources/**`
- 5 个 article-backed mapper XML
- `scripts/**`
- eval / baseline 题集

## 3. Article FTS 覆盖情况

已补直接覆盖：`ArticleVisibilitySearchMapperTests#shouldFilterArticleFtsByReviewStatusAndLifecycle`。

- passed + ACTIVE 正例：`visibility-passed-active` 可返回。
- pending 负例：`visibility-pending-active` 不返回。
- needs_human_review 负例：`visibility-needs-human-review-active` 不返回。
- rejected 负例：`visibility-rejected-active` 不返回。
- passed + 非 ACTIVE 负例：`visibility-passed-archived` 不返回。

断言结果：同一检索 token 下只返回 passed/ACTIVE 正例，且返回记录的 `reviewStatus` 为 `passed`。

## 4. RefKey 覆盖情况

已补直接覆盖：`ArticleVisibilitySearchMapperTests#shouldFilterRefKeySearchByReviewStatusAndLifecycle`。

- passed + ACTIVE 正例：`visibility-passed-active` 可返回。
- pending 负例：`visibility-pending-active` 不返回。
- needs_human_review 负例：`visibility-needs-human-review-active` 不返回。
- rejected 负例：`visibility-rejected-active` 不返回。
- passed + 非 ACTIVE 负例：`visibility-passed-archived` 不返回。
- OR 条件未绕过 hard filter：所有正负例都写入相同 `MATCH_TOKEN`，负例同样可命中 RefKey 原有 OR 匹配条件；最终只返回 passed/ACTIVE 正例，说明 OR 条件没有绕过 hard filter。

## 5. ArticleChunk / ArticleVector / ArticleChunkVector 补强情况

- ArticleChunk lexical：
  - 已补 pending、needs_human_review、rejected、passed + 非 ACTIVE 负例。
  - 查询同一 lexical token 时只返回 passed/ACTIVE 正例。
- ArticleVector：
  - 已补 pending、needs_human_review、rejected、passed + 非 ACTIVE 负例。
  - 负例使用与正例相同 embedding，仍不返回。
- ArticleChunkVector：
  - 已补 pending、needs_human_review、rejected、passed + 非 ACTIVE 负例。
  - 负例 chunk 使用与正例相同 embedding，仍不返回。

## 6. 定向测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=ArticleVisibilitySearchMapperTests,ArticleChunkJdbcRepositoryTests,VectorJdbcRepositoryOperatorTests test
```

结果：通过。

- Tests run: 8
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

## 7. 全量 mvn test 结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：通过。

- Tests run: 814
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS
- Finished at: 2026-05-17T15:38:07+08:00

## 8. redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：通过，脚本退出码为 0。

- BLOCKER：0
- REVIEW：1852
- ALLOWLIST：239
- 总命中：2091
- 高风险：0
- 中风险：1852
- 低风险：239

## 9. 是否删除/跳过测试

否。

本轮没有删除测试，没有添加跳过测试，也没有放宽断言为接受空结果。

## 10. 是否修改 SQL hard filter

否。

本轮没有修改 5 个 mapper XML，也没有调整 SQL hard filter。测试只验证已有 hard filter 行为。

## 11. 剩余覆盖缺口

本轮目标内覆盖已补齐：

- Article FTS 已有 passed/ACTIVE 正例、pending / needs_human_review / rejected 负例、非 ACTIVE 负例。
- RefKey 已有 passed/ACTIVE 正例、pending / needs_human_review / rejected 负例、非 ACTIVE 负例，并覆盖 OR 条件不能绕过 hard filter。
- ArticleChunk lexical、ArticleVector、ArticleChunkVector 已补 pending / needs_human_review / rejected / 非 ACTIVE 负例。

未扩大到本轮禁止范围：

- 未补 source/source_chunk mapper 或测试。
- 未补 fact card mapper/service/policy 或测试。
- 未跑 query baseline / SWIP eval。
