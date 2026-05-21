# Compile Job Progress Display Semantics Pre-Commit Quality Report

## 总体结论

- 是否建议提交：建议提交。
- 是否建议拆分提交：不需要拆分。本轮只有一个展示语义收口目标，适合单独一个 commit。
- 推荐 commit message：`fix(admin): 收口编译任务进度展示语义`
- 本轮是否修改代码：否。本轮仅执行只读复核、redline 扫描、`mvn test`，并新增本报告。

## 当前 Diff 摘要

`git diff --stat`：

| 文件 | 变更规模 | 复核结论 |
| --- | ---: | --- |
| `special_cases_report.md` | 29 行 | redline 扫描刷新产物。 |
| `src/main/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryService.java` | 61 行 | 仅调整默认进度 detail 文案组装，保留审计 summary。 |
| `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskPresentationResolver.java` | 2 行 | 仅调整 `generate_synthesis_artifacts` 的用户可见中文文案。 |
| `src/test/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryServiceTests.java` | 74 行 | 增补展示语义单测，验证默认 detail 不暴露内部审计字段。 |

当前已修改文件仅为上述 3 个代码/测试文件加 `special_cases_report.md`，符合本轮预期范围。未发现前端、数据库、prompt、编译链路、API DTO/schema、query baseline 或 eval 题集变更。

## 门禁结果

- redline：`BLOCKER=0`，`REVIEW=1860`，`ALLOWLIST=244`。
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`：`Tests run: 835, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 说明：测试期间出现若干 Spring/Hikari/MCP 常规 WARN 日志，以及测试场景内的后台 worker 错误日志，但 Surefire 汇总无失败、无错误、无跳过。

## 质量复核

| 检查项 | 结论 |
| --- | --- |
| 是否修改 API schema | 否。未修改 controller、DTO、OpenAPI/schema 或序列化字段。 |
| 是否修改前端 | 否。无前端文件变更。 |
| 是否修改编译链路 | 否。未触碰 compile graph、writer/reviewer/fixer、persist gate 或 job 执行链路。 |
| 是否删除 `compileReviewSummary` 审计字段 | 否。`AdminCompileReviewSummaryService.resolve(...)` 仍保留 review route、mode、model route、accepted/pending/needs human review counts、fix 信息等审计字段。 |
| 默认 `progressSteps.detail` 是否仍不暴露内部字段 | 是。代码和单测均覆盖不暴露 `review_articles`、`reviewMode`、`model_route`、`acceptedCount`、`pendingReviewCount`、`needsHumanReviewCount`。 |
| 是否存在业务硬编码 | 未发现。变更只包含通用任务展示文案和内部审计字段隐藏规则，不包含业务域、文件名、caseId、expected、query-regression 或答案片段特判。 |
| 是否触碰禁止范围 | 未发现。未修改前端、数据库、prompt、redline 脚本、allowlist、baseline、SWIP eval 或编译治理主链。 |

## 剩余风险

- 本轮只收口默认任务卡展示语义，没有新增“展开技术详情”前端能力；技术审计信息仍通过 `compileReviewSummary` 保留给调试/API 使用。
- 运行时验证报告覆盖 7/7 个任务样本，足以支撑本轮展示语义修复；不建议为该展示文案变更额外跑 full clean rebuild、query baseline 或 SWIP eval。

## 提交前清理建议

- 建议本次提交只纳入展示语义相关文件：3 个代码/测试文件、`special_cases_report.md`、`compile_job_progress_display_semantics_fix_result_report.md`、`compile_job_progress_display_semantics_verification_report.md`、本报告。
- 当前工作区还有多份无关未跟踪旧报告，例如 duplicate task、job idempotency、performance、structured gate、post prompt acceptance 等主题报告。若目标是“单独提交展示语义修复”，这些报告应从本次 staging 中排除；不需要为了提交而删除。

## 最终判断

可以提交。当前 patch 是展示层语义收口，不改变 API schema、前端、编译链路或审计字段；redline 无 blocker，Maven 全量测试通过。
