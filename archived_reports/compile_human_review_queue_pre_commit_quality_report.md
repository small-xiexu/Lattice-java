# Compile Human Review Queue 提交前质量复核报告

## 1. 结论

建议提交。

当前改动已经形成最小可用闭环：`needs_human_review` 编译草稿进入持久化队列，后台可 list/detail，人工 approve 后以 `review_status=passed`、`lifecycle=ACTIVE` 写入正式知识库并刷新 chunk/vector，人工 reject 后不入库。运行时验证已覆盖后端、向量刷新和前端主流程；本轮未发现红线 BLOCKER、业务硬编码、Query/Answer 主链误改、prompt/model 配置误改。

建议拆成两个提交，便于回看和回滚：

1. `feat(compile): add human review queue publish flow`
2. `feat(admin): expose compile human review queue`

如必须单提交，推荐 commit message：

`feat(compile): add human review queue publish flow`

## 2. redline 结果

本轮执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

| 项 | 数量 |
| --- | ---: |
| BLOCKER | 0 |
| REVIEW | 1863 |
| ALLOWLIST | 244 |
| 总命中 | 2107 |
| 高风险 | 0 |
| 中风险 | 1863 |
| 低风险 | 244 |

结论：`BLOCKER=0`，允许继续提交前复核。`special_cases_report.md` 由 redline 命令刷新，属于本轮允许范围。

## 3. mvn test 最新结果

本轮未重复运行全量 `mvn test`。理由：用户允许“如认为必要”再运行；当前代码变更在后端、向量、前端修复报告中均已有近期全量结果，后续主要是运行时验证报告和本次只读复核。

最近一次可信全量结果来自：

- `compile_human_review_queue_backend_fix_result_report.md`：全量第二次 `844 / 0 / 0`，通过。
- `compile_human_review_queue_approve_vector_fix_result_report.md`：全量 `844 / 0 / 0`，`BUILD SUCCESS`。
- `compile_human_review_queue_frontend_fix_result_report.md`：全量 `Tests run: 844`，`BUILD SUCCESS`。

结论：最新已记录全量测试为 `844 / 0 / 0`，通过。

## 4. 当前 git diff 摘要

`git status --short --branch` 显示当前分支：

- `codex/qa-polish...origin/codex/qa-polish [ahead 6]`

tracked 修改：

- `special_cases_report.md`
- `src/main/java/com/xbk/lattice/compiler/graph/node/ReviewArticlesNode.java`
- `src/main/resources/db/schema.sql`
- `src/main/resources/static/admin/admin.css`
- `src/main/resources/static/admin/index.html`

`git diff --stat` 只统计 tracked 文件：

- 5 files changed
- 270 insertions
- 4 deletions

untracked 实现文件：

- `src/main/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueService.java`
- `src/main/java/com/xbk/lattice/admin/service/CompileArticleReviewQueueActionRequest.java`
- `src/main/java/com/xbk/lattice/admin/service/CompileArticleReviewQueueActionResult.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueActionRequest.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueActionResponse.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueController.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueItemResponse.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueListResponse.java`
- `src/main/java/com/xbk/lattice/compiler/service/CompileArticleReviewQueueService.java`
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueRecord.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
- `src/main/resources/static/admin/compile-review-queue.js`

untracked 测试文件：

- `src/test/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueServiceTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminCompileReviewQueueControllerTests.java`
- `src/test/java/com/xbk/lattice/compiler/graph/node/ReviewArticlesNodeHumanReviewQueueTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`

untracked 报告文件较多，提交时应按本功能选择 stage，不要混入无关历史分析报告。

## 5. 改动范围复核

本轮生产改动集中在预期范围：

| 范围 | 结论 |
| --- | --- |
| `compile_article_review_queue` schema | 已新增表和索引 |
| backend queue repository/service/API | 已新增 repository、record、mapper、service、controller、DTO |
| `ReviewArticlesNode` 入队 | 已接入，将最终 `needs_human_review` 草稿写入持久化队列 |
| approve vector refresh | 已接入 approve 后 chunk/vector 刷新 |
| admin 前端待人工确认入口 | 已新增入口、列表、详情、approve/reject 操作 |
| 对应测试 | 已覆盖 repository、node 入队、service approve/reject、controller |
| 本轮报告 | 已产生设计、修复、运行时验证报告 |

未发现超出本功能的生产链路改动。

## 6. 禁止范围检查

| 禁止范围 | 是否触碰 | 说明 |
| --- | --- | --- |
| Query / AnswerGeneration | 否 | `git status` 未出现 query/answer 相关路径 |
| Reviewer / Fixer / Writer prompt | 否 | 未修改 prompt 文件和 prompt provider |
| query visibility hard filter | 否 | 未修改 query mapper/service |
| `scripts/scan-redline.sh` | 否 | 未修改 |
| redline allowlist | 否 | 未修改 |
| 模型配置 | 否 | 未修改 `.claude/t1.md`、`application*.yml`、LLM config |
| SWIP eval / query baseline | 否 | 未修改题集或 baseline |

针对本轮新增/修改实现文件的关键词扫描未命中 `SWIP`、`caseId`、`expectedAnswer`、`query-regression`、`hidden eval`、具体业务词、具体文件名或答案片段特判。

## 7. 新增 API 最小闭环检查

新增 API 符合最小闭环：

| API 能力 | 路径 | 结论 |
| --- | --- | --- |
| list | `GET /api/v1/admin/compile/review-queue` | 已有，默认查 `needs_human_review` |
| detail | `GET /api/v1/admin/compile/review-queue/{id}` | 已有 |
| approve | `POST /api/v1/admin/compile/review-queue/{id}/approve` | 已有，发布正式文章 |
| reject | `POST /api/v1/admin/compile/review-queue/{id}/reject` | 已有，不入库 |

API 是新增后台管理接口，未破坏既有 API schema。列表 `total` 当前等于本页返回数量，不是全库匹配总数；这不影响最小闭环，可作为后续分页增强。

## 8. 入库与 query 可见性检查

| 场景 | 当前行为 | 依据 |
| --- | --- | --- |
| 未 approve 前 | 不进入 `articles` / `article_chunks` / vector index，因此不可被 article-backed query 召回 | 后端与前端运行时报告均验证 |
| approve 后 | `articles.review_status=passed`，`lifecycle=ACTIVE`，chunk/vector 可用 | `compile_human_review_queue_approve_vector_runtime_verification_report.md` 和前端运行时报告验证 |
| reject 后 | 队列状态为 `rejected`，不写正式 articles/chunks/vector | 后端、向量、前端运行时报告均验证 |

结论：当前实现没有绕过 persist gate / query visibility hard filter。人工确认前草稿仍在 queue 中，不对 query 可见；人工确认后以正式 passed+ACTIVE 文章进入 query 可见范围。

## 9. 前端展示检查

前端新增“待人工确认”入口、列表、详情、approve/reject 操作。默认页面面向用户展示标题、状态、来源、轮次、审查问题和动作按钮；内部技术字段通过折叠区承载。

结论：

- 默认未把内部 route/model/raw audit 字段作为首页摘要卡片暴露。
- 详情正文仍显示草稿原文，草稿 frontmatter 可能可见，属于已知非阻断体验问题。
- 状态摘要卡片尚未接入 `compile_article_review_queue`，属于已知非阻断问题。

## 10. 运行时验证充分性

已完成的运行时验证覆盖足够进入提交：

- 后端队列入队：`needs_human_review` 草稿可持久化。
- list/detail：API 可读取队列与详情。
- approve：写入 articles/chunks/vector index，并记录 audit。
- reject：不入库，只标记 rejected 并记录 audit。
- 前端：入口可见，列表/详情/approve/reject 可用，当前处理任务刷新。
- redline：前后均 `BLOCKER=0`。

当前缺少的不是提交阻断项，而是后续产品完善项：状态摘要接 queue、草稿正文展示净化、reviewRoute/reviewerModel 精准展示、列表分页总数。

## 11. 已知非阻断问题

1. 草稿正文 frontmatter 在详情正文中仍可见，影响观感但不影响入库安全。
2. 后台首页“需复核内容”尚未统计 `compile_article_review_queue`，容易误导用户，但已有单独分析报告。
3. `reviewRoute/reviewerModel` 展示来源仍有不准风险，不影响 publish gate。
4. approve 后 vector refresh 若运行时异常会记录 warning，不会回滚发布；本轮运行时已验证 vector 正常生成，后续可单独补失败可见性或重试策略。
5. queue list response 的 `total` 当前为返回条数，不是完整匹配总数；MVP 可接受。

以上均不阻塞本次提交。

## 12. 提交建议

建议提交，且建议拆成两个 commit：

### Commit 1

`feat(compile): add human review queue publish flow`

包含：

- `src/main/resources/db/schema.sql`
- `src/main/java/com/xbk/lattice/compiler/graph/node/ReviewArticlesNode.java`
- backend queue service / repository / mapper / controller / DTO
- backend 对应测试
- 后端与向量相关报告

### Commit 2

`feat(admin): expose compile human review queue`

包含：

- `src/main/resources/static/admin/index.html`
- `src/main/resources/static/admin/admin.css`
- `src/main/resources/static/admin/compile-review-queue.js`
- 前端运行时验证报告

如果项目希望“功能闭环一次提交”，可以合并为单 commit：

`feat(compile): add human review queue publish flow`

提交前 staging 建议：

- stage 本功能代码、schema、前端、测试、相关人审队列报告和本报告。
- 不要混入无关报告，例如 embedding、SWIP、round progress、cleanup 类报告，除非另起报告整理提交。

## 13. 本轮是否修改代码

否。

本轮仅执行只读检查、刷新 redline 报告，并新增本提交前质量复核报告；未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、前端实现、prompt、脚本、模型配置或数据库。
