# compile review persist gate 运行时验证报告

验证时间：2026-05-17
验证角色：agentD（验证/测试）
验证类型：运行时验证（只验证，不改代码）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1852
- ALLOWLIST：239

REVIEW / ALLOWLIST 为既有人工复核候选，均为既存命中，不涉及本轮 PersistArticlesNode 变更。无新增 BLOCKER。

## 2. Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：BUILD SUCCESS
- Tests run: 811, Failures: 0, Errors: 0, Skipped: 0

与 fix_result_report 基线一致，修复未破坏现有测试。

## 3. 验证使用的数据库与 compile job

- 数据库：`ai-rag-knowledge`（PostgreSQL Docker 容器 `vector_db`）
- 编译 job：`fc155a9b-54f2-41af-87cc-0498c88521b9`
- 资料：`source_id=1`（SWIP 智能键盘系统使用手册，2 个 docx 文件）
- job 状态：`SUCCEEDED`，orchestration_mode=`state_graph`
- 审查步骤：`review_articles` route=`rule-based`，acceptedCount=4，needsHumanReviewCount=0
- 无 `fix_review_issues` 步骤执行记录

## 4. 是否触发或复用了 needs_human_review 场景

**否。**

当前数据库全部三个库（`ai-rag-knowledge`、`ai-rag-knowledge-test`、`ai-rag-swip-eval`）中所有 articles 均为 `review_status=passed`，无任何 `needs_human_review` 文章。

经分析，在当前条件下无法安全构造 `needs_human_review` 场景，原因如下：

| 条件 | 当前状态 | 影响 |
|---|---|---|
| Reviewer 路由 | `rule-based` | 只检查 6 项结构规则（非空、sources/review_status frontmatter、无 TODO/TBD、有标题、sources 非空），LLM 编译器产出的文章基本全部通过 |
| autoFixEnabled | `true`（默认） | 非 pass 会先进入 fix → 再 review，不是直接进 needs_human_review |
| maxFixRounds | `1`（默认） | 一次 fix+re-review 后仍不过才会进 needs_human_review |
| 启用 LLM reviewer | 禁止 | 无法通过 LLM 审查产生非 pass 结果 |
| 修改生产代码 | 禁止 | 无法注入/模拟 needs_human_review |

尝试的替代路径及被排除原因：

1. **禁用 autoFix + 编译**：需写入 `compile_review_settings`（修改 DB，非只读）；且 LLM 编译器产出文章仍大概率通过 rule-based review，无法保证产生 `needs_human_review`
2. **使用 ai-rag-knowledge-test 库**：该库有 1 篇 `pending` 文章，但无 compile job、无 chunks、无 vector——属于手工插入残留，非正常编译流水线产物，不能用于验证 persist gate
3. **直接写入 needs_human_review 文章到 articles 表**：绕过 persist gate，无法验证"persist 时是否阻止入库"

## 5. needs_human_review article 是否进入 articles

**无法直接验证**（数据库中无 `needs_human_review` 文章）。

通过源码审查确认：
- `PersistArticlesNode.execute()` 第 74 行：`articlesToPersist = retainPassedArticles(acceptedArticles)`
- `retainPassedArticles()` 只保留 `review_status="passed"` 的文章
- `needsHumanReviewArticlesRef` 不再被加载或合并
- `isPassedArticle()` 对 `reviewStatus != "passed"` 返回 `false`

代码级结论：`needs_human_review` article **不会**进入 articles 正式持久化。

## 6. needs_human_review article 是否进入 article_chunks

**无法直接验证**（同上）。

`article_chunks` 由 `ArticleAtomicWriteService.persistArticlesAtomic()` 在同一事务中写入，入参即 `articlesToPersist`。由于 `articlesToPersist` 已过滤为仅 `passed`，`needs_human_review` 文章不会进入 chunk 重建链路。

## 7. needs_human_review article 是否进入 article_vector_index / article_chunk_vector_index

**无法直接验证**（同上）。

`article_vector_index` 和 `article_chunk_vector_index` 由 `RefreshVectorIndexNode` 对 `persisted_articles` 后的 `reviewedArticlesRef` 建索引。`ReviewedArticlesRef` 在 persist 步骤第 88 行仅在 `!articlesToPersist.isEmpty()` 时保存——内容即为 `retainPassedArticles` 的结果。`needs_human_review` 文章不在该 ref 中，不会被建索引。

代码级结论：`needs_human_review` article **不会**进入 vector index。

## 8. passed article 是否仍正常入库

**是。**

数据库验证结果（`ai-rag-knowledge`）：

| article_id | title | review_status | chunks | vectors |
|---|---|---|---|---|
| 1 | Swip智能键盘系统使用手册 20250702 | passed | 6 | 1 |
| 2 | 系统架构 5 | passed | 3 | 1 |
| 3 | FAQ 33 | passed | 6 | 1 |
| 4 | HTTPS证书安装（门店内网）… | passed | 4 | 1 |

- `articles`：4 篇，全部 `passed / ACTIVE`
- `article_chunks`：19 条
- `article_vector_index`：4 条
- `article_chunk_vector_index`：19 条

与 compile job `persistedCount=4` 一致。passed → persist → chunks → vector 全链路完整。

## 9. 是否修改代码

**否。**

本轮未执行任何代码、测试、配置、脚本或文档修改。

## 10. 是否开启 LLM reviewer

**否。**

`compile_review_settings` 表为空，review 路由仍为 `rule-based`。未修改任何模型配置。

## 11. 是否修改 query visibility filter

**否。**

未修改任何 SQL、mapper、检索或可见性过滤逻辑。

## 12. 结论与下一步建议

### 本轮可确认的

| 验证项 | 方法 | 结果 |
|---|---|---|
| redline BLOCKER=0 | 扫描 | 通过 |
| mvn test 811/0/0/0 | 运行 | 通过 |
| passed article 正常入库 | 数据库查询 | 4/19/4/19，全链路完整 |
| 代码逻辑阻止 needs_human_review 入库 | 源码审查 | `retainPassedArticles` + 移除 merge，gate 完整 |
| 未破坏 fix/review 流程 | 源码审查 + mvn test | fix 节点未修改；811 测试通过 |
| 后台可观测性保留 | 源码审查 | `finalize_job` summary 仍输出 `needsHumanReviewCount` |

### 无法在本轮验证的

- `needs_human_review` article 不入库的端到端行为：当前 rule-based reviewer + autoFixEnabled=true 条件下，无法自然产出 `needs_human_review`。

### 下一步最小建议

在用户允许修改测试后，为 `PersistArticlesNode` 增加一个单元测试：
- 构造混合 `review_status`（passed + needs_human_review）的 `ArticleReviewEnvelope` 列表
- 调用 `retainPassedArticles()`
- 断言只返回 `passed` 文章
- 断言 `needs_human_review` 文章不在返回结果中

该测试覆盖成本极低，不依赖数据库、LLM 或 compile pipeline 运行，可彻底闭合 persist gate 的端到端验证缺口。
