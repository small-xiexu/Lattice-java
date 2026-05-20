# Admin Dashboard Review Queue Summary 提交前质量复核报告

## 结论

建议提交。

这轮改动范围小、边界清晰，目标只有一个：让后台首页状态摘要显式统计编译人工确认草稿，并在前端展示“待人工确认草稿”卡片。当前没有发现越界修改编译 Writer / Reviewer / Fixer 主链、approve / reject / publish 逻辑、Query / AnswerGeneration 或 schema 的情况。

推荐 commit message：

`feat(admin): surface pending human review drafts in dashboard summary`

## 当前改动文件清单

### 生产代码

- `src/main/java/com/xbk/lattice/governance/StatusService.java`
- `src/main/java/com/xbk/lattice/governance/StatusSnapshot.java`
- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
- `src/main/resources/static/admin/modules/management-runtime-part-02.js`

### 测试

- `src/test/java/com/xbk/lattice/api/admin/AdminOverviewControllerTests.java`
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`
- `src/test/java/com/xbk/lattice/governance/StatusServiceTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`

### 报告 / redline 产物

- `special_cases_report.md`
- `admin_dashboard_review_queue_summary_analysis_report.md`
- `admin_dashboard_review_queue_summary_fix_result_report.md`
- `admin_dashboard_review_queue_summary_runtime_verification_report.md`

## 是否只属于 dashboard review queue summary 修复

是。

从 `git diff --name-status` 看，代码层只涉及：

- `StatusSnapshot`：新增 `humanReviewDraftPendingCount`
- `StatusService`：把 queue pending 数汇总进 overview.status
- `CompileArticleReviewQueueJdbcRepository` 与 mapper：新增 `countByStatus(...)`
- `management-runtime-part-02.js`：状态摘要卡片与 help state 文案
- 对应测试：overview、status service、queue repository、前端 runtime

没有混入其他主题代码。

## redline

本轮执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

| 项 | 数量 |
| --- | ---: |
| BLOCKER | 0 |
| REVIEW | 1863 |
| ALLOWLIST | 244 |

`special_cases_report.md` 的 diff 只有扫描时间刷新，没有规则、范围或 allowlist 变更。

## mvn test 是否需要重跑

不需要重跑。

理由：

- `admin_dashboard_review_queue_summary_fix_result_report.md` 已记录全量 `mvn test = 844 / 0 / 0`
- `admin_dashboard_review_queue_summary_runtime_verification_report.md` 是只读运行时验证，明确说明本轮未改代码
- 当前工作区相对该修复主题的代码 diff 与修复报告一致，没有后续新增实现改动

因此沿用最近一次全量结果即可：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：`844 / 0 / 0`

## 是否触碰编译主链

没有触碰。

本轮未修改：

- Writer
- Reviewer
- Fixer
- review loop
- persist gate
- approve / reject / publish 主流程

唯一和 compile review queue 相关的后端改动，是读取 `compile_article_review_queue.review_status='needs_human_review'` 的数量用于首页摘要展示，不改变任何编译行为。

## 是否触碰 Query / AnswerGeneration

没有触碰。

`git diff` 中没有 `src/main/java/com/xbk/lattice/query/**` 或 `AnswerGeneration*` 相关文件。

## 是否触碰 schema

没有触碰。

`git diff` 中没有 `src/main/resources/db/schema.sql` 或其他 schema 变更。

## 是否存在硬编码、case 特判、红线风险

未发现本轮新增的业务特判或评测污染。

检查结果：

- 没有新增 `SWIP`、`caseId`、`expectedAnswer`、`query-regression`、hidden eval 相关内容
- 没有按具体文件名、具体问题、具体答案片段分支处理
- `needs_human_review` 是系统状态枚举，不属于 case 特判
- 前端文案“待人工确认草稿”与“去待人工确认”是通用产品文案，不是业务硬编码

注意：

- `ManagementJsRuntimeTests.java` 中本来就有历史业务化测试样本字符串，但本轮新增断言没有引入新的业务特判逻辑

## 正数 runtime 未实测的残余风险

存在，但可接受，且不构成提交阻塞。

当前 runtime 验证只覆盖了 0 场景：

- DB `needs_human_review count = 0`
- overview `humanReviewDraftPendingCount = 0`
- 前端显示“待人工确认草稿 / 0 / 当前没有待发布草稿”

正数场景没有在 runtime 环境再造数验证，但已经有足够测试覆盖：

- `StatusServiceTests`：验证 `humanReviewDraftPendingCount=4`
- `CompileArticleReviewQueueJdbcRepositoryTests`：验证 `countByStatus("needs_human_review")` 从 `1 -> 0`
- `AdminOverviewControllerTests`：真实插入 queue 记录后断言 `$.status.humanReviewDraftPendingCount = 1`
- `ManagementJsRuntimeTests`：验证前端在 `humanReviewDraftPendingCount=2` 时显示卡片，并把 help state 引导到“当前处理任务”

因此剩余风险更像“缺少一次正数 runtime 冒烟”，不是实现正确性缺口。

## 是否建议提交

建议提交。

判断依据：

- 改动边界小，目标单一
- redline `BLOCKER=0`
- 最近全量测试 `844 / 0 / 0`
- runtime 0 场景已验证
- 正数场景虽未 runtime 实测，但单测、集成测试、JS runtime 测试都已覆盖
- 未碰编译主链、Query / AnswerGeneration、approve / reject / publish、schema

## 本轮是否修改代码

否。

本轮仅做只读复核，并新增本报告文件。
