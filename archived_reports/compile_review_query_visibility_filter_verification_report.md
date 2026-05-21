# compile review query visibility hard filter 最终验证报告

验证时间：2026-05-17
验证角色：agentD（验证/测试）
验证类型：最终验证（只验证，不改代码）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1852
- ALLOWLIST：239

无新增 BLOCKER，REVIEW / ALLOWLIST 为既有人工复核候选。

## 2. Article-Backed 定向测试

- 命令：`mvn -Dtest=ArticleVisibilitySearchMapperTests,ArticleChunkJdbcRepositoryTests,VectorJdbcRepositoryOperatorTests test`
- 结果：BUILD SUCCESS
- Tests run: 8, Failures: 0, Errors: 0, Skipped: 0

覆盖情况：
- `ArticleVisibilitySearchMapperTests`（2 个 case）：FTS 覆盖 passed/ACTIVE、pending、needs_human_review、rejected、非 ACTIVE；RefKey 同样覆盖并通过括号验证 OR 不绕过 hard filter
- `ArticleChunkJdbcRepositoryTests`：ArticleChunk 补齐负例
- `VectorJdbcRepositoryOperatorTests`：ArticleVector / ArticleChunkVector 补齐负例

## 3. Source / Fact Card 定向测试

- 命令：`mvn -Dtest=SourceFileJdbcRepositoryTests,SourceFileChunkJdbcRepositoryTests,SourceSearchServiceTests,FactCardJdbcRepositoryTests,FactCardFtsSearchServiceTests,FactCardVectorSearchServiceTests,FactCardReviewUsagePolicyTests test`
- 结果：BUILD SUCCESS
- Tests run: 33, Failures: 0, Errors: 0, Skipped: 0

确认 source/source_chunk 与 fact card 链路未受本轮 mapper 修改影响。

## 4. 全量 Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：BUILD SUCCESS
- Tests run: 814, Failures: 0, Errors: 0, Skipped: 0

与 test_coverage_result_report 基线 814/0/0 一致。

## 5. 五个 Article-Backed Mapper Hard Filter 确认

| Mapper XML | 条件 | 括号包裹 OR | 状态 |
|---|---|---|---|
| `ArticleFtsSearchMapper.xml` | `where ... and a.review_status = 'passed' and a.lifecycle = 'ACTIVE'` | 不适用（AND 追加） | 通过 |
| `RefKeySearchMapper.xml` | `where (...) and a.review_status = 'passed' and a.lifecycle = 'ACTIVE'` | 是 | 通过 |
| `ArticleChunkMapper.xml` | `where (...) and a.review_status = 'passed' and a.lifecycle = 'ACTIVE'` | 是 | 通过 |
| `ArticleVectorMapper.xml` | `where ... and a.review_status = 'passed' and a.lifecycle = 'ACTIVE'` | 不适用（AND 追加） | 通过 |
| `ArticleChunkVectorMapper.xml` | `where ... and a.review_status = 'passed' and a.lifecycle = 'ACTIVE'` | 不适用（AND 追加） | 通过 |

**关键确认：**

- 所有 5 个 mapper 均包含 `review_status='passed' AND lifecycle='ACTIVE'` hard filter
- `RefKeySearchMapper` 原有 `where false or ...` 已用括号包裹为 `where (false or ...) and ...`，hard filter 不会被 OR 绕过
- `ArticleChunkMapper` 原有 OR 条件 `ac.search_tsv @@ query.tsq or ...` 已用括号包裹为 `where (...) and ...`，hard filter 不会被 OR 绕过
- ArticleVector / ArticleChunkVector / ArticleFts 是简单 AND 追加，无 OR 绕过风险

## 6. Source / Source_Chunk 是否未修改

**是，未修改。**

`git diff --name-only -- src/main/resources/` 只返回 5 个 article-backed mapper XML，不包含任何 `source_file*` 或 `source_chunk*` 相关文件。

`git diff --name-only -- src/main/java/com/xbk/lattice/source/` 无输出。

## 7. Fact Card 是否未修改

**是，未修改。**

- `git diff --name-only -- src/main/java/com/xbk/lattice/fact_card/` 无输出
- `git diff --name-only -- src/main/java/.../FactCardReviewUsagePolicy.java` 无输出
- 资源文件中无 `fact_card*` mapper 变更

## 8. 是否修改 Java 主链

**否。**

`git diff --name-only -- src/main/java/` 无任何输出。本轮所有生产代码变更仅限 5 个 MyBatis XML mapper 文件。

## 9. 是否跑 Baseline / SWIP Eval

**否。**

本轮未执行任何 baseline、SWIP eval 或 query 回归。

## 10. 当前无关工作区改动清单

以下改动与本轮 Query visibility hard filter **无关**，不应纳入 query visibility filter 提交范围：

| 文件 | 状态 | 说明 |
|---|---|---|
| `.gitignore` | 已修改（staged） | 本地工具缓存目录调整（`.codex/`、`.omx/`），属于环境治理 |
| `compile_review_observability_verification_report.md` | 已修改（staged） | 前序 observability 轮次报告内容更新 |
| `docs/oh-my-codex-agent-orchestration-guide.md` | 未跟踪 | 新增文档，不属于本轮范围 |

与本轮 Query visibility hard filter **有关**的变更：

| 文件 | 状态 | 说明 |
|---|---|---|
| `ArticleFtsSearchMapper.xml` | 已修改 | 新增 hard filter |
| `RefKeySearchMapper.xml` | 已修改 | 新增 hard filter + OR 括号 |
| `ArticleChunkMapper.xml` | 已修改 | 新增 hard filter + OR 括号 |
| `ArticleChunkVectorMapper.xml` | 已修改 | 新增 hard filter |
| `ArticleVectorMapper.xml` | 已修改 | 新增 hard filter |
| `ArticleChunkJdbcRepositoryTests.java` | 已修改 | 补齐负例 |
| `VectorJdbcRepositoryOperatorTests.java` | 已修改 | 补齐负例 |
| `ArticleVisibilitySearchMapperTests.java` | 未跟踪（新增） | FTS + RefKey 覆盖 |
| `special_cases_report.md` | 已修改 | redline 自动更新 |
| 4 个 query visibility 报告 | 未跟踪（新增） | 本轮分析/修复/验证报告 |

## 11. 是否建议进入台账更新与 Pre-Commit 复核

**建议进入。**

全部 10 项检查通过：

| 检查项 | 结果 |
|---|---|
| redline BLOCKER=0 | 通过 |
| article-backed 定向测试 8/0/0 | 通过 |
| source/fact card 测试 33/0/0 | 通过 |
| 全量 mvn test 814/0/0 | 通过 |
| 5 个 mapper hard filter 存在 | 通过 |
| RefKey/ArticleChunk OR 括号包裹 | 通过 |
| source/source_chunk 未修改 | 通过 |
| fact card 未修改 | 通过 |
| Java 主链未修改 | 通过 |
| 未跑 baseline / SWIP eval | 通过 |

**注意事项：**

- 提交时应排除 `.gitignore`、`compile_review_observability_verification_report.md` 和 `docs/oh-my-codex-agent-orchestration-guide.md`，这三项与本轮 query visibility hard filter 无关
- 所有前序轮次遗留的未提交文件（compile review observability、persist gate 相关代码与报告）同样不应混入本轮提交
