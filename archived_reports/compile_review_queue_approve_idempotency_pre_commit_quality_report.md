# Compile Review Queue Approve 幂等性提交前质量复核报告

## 结论

建议提交。

这组改动边界清楚，主题单一：只修 `approve` 在 `article_key` 已存在时的幂等收口，不改 reject，不改 compile 主链，不改 publish 语义，不改 Query / AnswerGeneration，不改 schema。

推荐 commit message：

`fix(admin): make compile review queue approve idempotent`

## 当前建议纳入提交的文件清单

### 代码

- `src/main/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueService.java`
- `src/test/java/com/xbk/lattice/admin/service/AdminCompileArticleReviewQueueServiceTests.java`

### 报告 / redline

- `compile_review_queue_approve_idempotency_fix_result_report.md`
- `compile_review_queue_approve_idempotency_runtime_verification_report.md`
- `compile_review_queue_approve_idempotency_pre_commit_quality_report.md`

`special_cases_report.md` 是否纳入：

- 可选

原因：

- 当前它只是 redline 扫描时间和命中行号刷新
- 不属于 approve 幂等修复本身的业务交付物

## 当前应排除的无关文件清单

明确应排除：

- `admin_review_queue_count_filter_visual_runtime_verification_report.md`

原因：

- 属于另一条 admin review queue 展示主线
- 当前只是报告文本改写，不是 approve 幂等修复的一部分

建议一并排除：

- `compile_pipeline_performance_analysis_report.md`
- `compile_pipeline_second_bottleneck_analysis_report.md`
- `compile_pipeline_third_bottleneck_analysis_report.md`
- `compile_writer_budget_and_redis_fix_pre_commit_quality_report.md`
- `phase_compile_query_stage_acceptance_report.md`
- `post_compile_second_cut_report_cleanup_result.md`

原因：

- 这些都是其他主题的分析、复核或清理报告
- 与本轮 approve 幂等修复无关

## 当前代码改动是否只围绕指定范围

是。

从当前工作区实际 diff 看，代码层只有：

- `AdminCompileArticleReviewQueueService.java`
- `AdminCompileArticleReviewQueueServiceTests.java`

没有出现额外的 controller、repository、schema、前端或其他服务扩散修改。

## 是否越界修改

### reject 逻辑

没有。

`reject(...)` 未修改，驳回语义、状态流转和审计保持原样。

### compile 主链

没有。

未修改：

- Writer
- Reviewer
- Fixer
- review loop
- persist gate
- queue 去重主逻辑

### Query / AnswerGeneration

没有。

当前 diff 中没有 `src/main/java/com/xbk/lattice/query/**` 或 `AnswerGeneration*` 相关路径。

### schema

没有。

未修改 `src/main/resources/db/schema.sql` 或任何表结构。

### 模型配置

没有。

未修改 `.claude/t1.md`、provider、binding、profile 或 YAML 配置。

## redline 是否仍为 BLOCKER=0

是。

本轮重新执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

- `BLOCKER=0`
- `REVIEW=1911`
- `ALLOWLIST=245`

说明：

- 没有新增 blocker
- 数量相较早前主线略高，但来自当前工作区其他已存在代码命中，不是 approve 幂等这组修复单独引入的门禁变化

## runtime 验证是否足够支撑提交

足够。

来自 [compile_review_queue_approve_idempotency_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_review_queue_approve_idempotency_runtime_verification_report.md) 的直接证据：

- `article_key` 已存在时，approve 返回 `SUCCESS`
- 不再出现 `article already exists`
- 队列状态正确收口为 `published`
- `publishedArticleKey` 正确返回
- `articles/chunks/vector` 不重复增加
- 正常 approve 未受影响
- 正常 reject 未受影响

来自修复报告的测试证据：

- 定向测试：`6 / 0 / 0`
- 全量测试：`862 / 0 / 0`

这已经足够支撑本轮提交。

## 是否存在阻塞项

当前未发现阻塞项。

唯一需要强调的是提交 hygiene：

- 不要把无关报告文件混入本轮提交

除此之外，没有代码质量、运行时正确性或门禁层面的 blocker。

## 是否建议提交

建议提交。

理由：

- 改动小
- 主题单一
- 无越界
- redline `BLOCKER=0`
- 定向测试、全量测试、真实 runtime 复验都已覆盖
- 正常 approve / reject 路径未被误伤

## 推荐 commit message

`fix(admin): make compile review queue approve idempotent`

## 本轮是否修改代码

否。

本轮只做提交前质量复核，并新增本报告文件。
