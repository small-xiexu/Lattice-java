# compile review query visibility hard filter 提交前质量复核报告

复核时间：2026-05-17
复核角色：agentD（验证/测试）
复核类型：提交前质量复核（只验证，不改代码）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1852
- ALLOWLIST：239

无新增 BLOCKER。REVIEW / ALLOWLIST 为既有人工复核候选，不涉及本轮 mapper XML 变更。

## 2. Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：BUILD SUCCESS
- Tests run: 814, Failures: 0, Errors: 0, Skipped: 0

与最终验证基线 814/0/0 一致。

## 3. 本轮允许提交文件清单

**生产代码（5 个 mapper XML）：**

| 文件 | 变更内容 |
|---|---|
| `src/main/resources/.../mapper/ArticleFtsSearchMapper.xml` | 新增 `review_status='passed' AND lifecycle='ACTIVE'` |
| `src/main/resources/.../mapper/RefKeySearchMapper.xml` | 新增括号包裹 OR + hard filter |
| `src/main/resources/.../mapper/ArticleChunkMapper.xml` | 新增括号包裹 OR + hard filter |
| `src/main/resources/.../mapper/ArticleVectorMapper.xml` | 新增 `review_status='passed' AND lifecycle='ACTIVE'` |
| `src/main/resources/.../mapper/ArticleChunkVectorMapper.xml` | 新增 `review_status='passed' AND lifecycle='ACTIVE'` |

**测试代码（3 个文件）：**

| 文件 | 变更内容 |
|---|---|
| `src/test/java/.../ArticleVisibilitySearchMapperTests.java` | 新增：FTS + RefKey 全覆盖（passed/pending/needs_human_review/rejected/非ACTIVE） |
| `src/test/java/.../ArticleChunkJdbcRepositoryTests.java` | 修改：补齐负例 |
| `src/test/java/.../VectorJdbcRepositoryOperatorTests.java` | 修改：补齐负例 |

**台账与报告：**

| 文件 | 说明 |
|---|---|
| `docs/quality-progress-and-lessons.md` | 质量台账更新：记录 Query visibility hard filter 收口 |
| `special_cases_report.md` | redline 扫描自动更新 |
| `compile_review_query_visibility_filter_analysis_report.md` | 治理分析报告 |
| `compile_review_query_visibility_filter_fix_result_report.md` | fix 结果报告 |
| `compile_review_query_visibility_filter_test_fix_result_report.md` | 测试修复报告 |
| `compile_review_query_visibility_filter_verification_design_report.md` | 验证设计报告 |
| `compile_review_query_visibility_filter_test_coverage_result_report.md` | 测试覆盖结果报告 |
| `compile_review_query_visibility_filter_verification_report.md` | 最终验证报告 |
| `compile_review_query_visibility_quality_progress_update_report.md` | 质量台账更新报告 |
| `compile_review_query_visibility_quality_progress_draft.md` | 台账草稿（可选） |
| `compile_review_query_visibility_pre_commit_quality_report.md` | 本报告 |

## 4. 本轮必须排除文件清单

以下文件存在于工作区但与 Query visibility hard filter **无关**，提交时不得纳入：

| 文件 | 状态 | 排除原因 |
|---|---|---|
| `.gitignore` | 已修改 | 本地工具缓存目录调整（`.codex/`、`.omx/`），属环境治理 |
| `compile_review_observability_verification_report.md` | 已修改 | 前序 observability 轮次报告更新，非本轮范围 |
| `docs/oh-my-codex-agent-orchestration-guide.md` | 未跟踪 | 新增独立文档，非本轮范围 |
| `unrelated_workspace_changes_triage_report.md` | 未跟踪 | 无关改动分类报告，非本轮范围 |

## 5. 是否修改 Java 主链

**否。**

`git diff --name-only -- src/main/java/` 无任何输出。本轮所有生产代码变更仅限 5 个 MyBatis XML mapper 文件，零 Java 文件改动。

## 6. 是否修改 Source / Fact Card 查询链路

**否。**

资源文件变更仅限 5 个 article-backed mapper XML，不含任何 `source*` 或 `fact_card*` mapper。`src/main/java/com/xbk/lattice/source/`、`src/main/java/com/xbk/lattice/fact_card/` 无任何变更。source/fact card 定向测试 33/0/0 通过，确认未受影响。

## 7. 是否存在业务特判

**否。**

`review_status='passed'` 和 `lifecycle='ACTIVE'` 是 `articles` 表的标准枚举值，与 `RuleBasedArticleReviewer`、`ReviewDecisionPolicy`、`PersistArticlesNode` 保持一致。不涉及任何业务域、文档名、术语、问题文本或答案片段。

## 8. 是否可以提交

**YES。**

全部检查项通过：

| 检查项 | 结果 |
|---|---|
| redline BLOCKER=0 | 通过 |
| mvn test 814/0/0 | 通过 |
| article-backed 定向测试 8/0/0 | 通过（前序验证） |
| source/fact card 测试 33/0/0 | 通过（前序验证） |
| 5 个 mapper hard filter 存在 + OR 括号包裹 | 通过（前序验证） |
| Java 主链未修改 | 通过 |
| source/fact card 未修改 | 通过 |
| 无业务特判 | 通过 |
| 排除文件明确 | 通过 |

## 9. 建议 Commit Message

```
fix(query): add hard filter to 5 article-backed mappers

Add `review_status='passed' AND lifecycle='ACTIVE'` to all
article-backed query mappers so that non-passed or inactive
articles cannot surface through FTS, RefKey, chunk lexical,
article vector, or chunk vector retrieval.

Wrap OR conditions in parentheses for RefKeySearchMapper and
ArticleChunkMapper to prevent the hard filter from being bypassed.

Tests: 814/0/0. New ArticleVisibilitySearchMapperTests covers
passed/pending/needs_human_review/rejected/non-ACTIVE across
FTS and RefKey. Existing ArticleChunkJdbcRepositoryTests and
VectorJdbcRepositoryOperatorTests extended with negative cases.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```
