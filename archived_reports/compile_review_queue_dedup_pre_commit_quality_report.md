# Compile Review Queue 去重提交前质量复核报告

## 结论

建议提交。

这轮代码改动边界清楚，只围绕：

- `schema.sql`
- `CompileArticleReviewQueueMapper.xml`
- `CompileArticleReviewQueueJdbcRepositoryTests.java`

去实现和验证：

- 同一 `article_key` 的 pending 草稿跨 job 去重
- `published / rejected` 历史保留

没有越界碰到 approve / reject 主逻辑、compile 主链、Query / AnswerGeneration、前端或模型配置。

推荐 commit message：

`fix(compile): deduplicate pending review-queue drafts by article key`

## 1. 当前建议纳入提交的文件清单

### 代码

- `src/main/resources/db/schema.sql`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`

### 报告 / redline

- `compile_review_queue_dedup_fix_result_report.md`
- `compile_review_queue_dedup_runtime_verification_report.md`
- `compile_review_queue_dedup_pre_commit_quality_report.md`

`special_cases_report.md` 是否纳入：

- 可选，不强制

原因：

- 当前变化主要是扫描时间和命中行号刷新
- 不属于 dedup 业务语义本身

## 2. 当前应排除的无关文件清单

明确应排除：

- `AGENTS.md`
- `admin_review_queue_count_filter_visual_runtime_verification_report.md`
- `compile_review_queue_dedup_design_report.md`
- `compile_pipeline_performance_analysis_report.md`
- `compile_pipeline_second_bottleneck_analysis_report.md`
- `compile_pipeline_third_bottleneck_analysis_report.md`
- `compile_writer_budget_and_redis_fix_pre_commit_quality_report.md`
- `phase_compile_query_stage_acceptance_report.md`
- `post_compile_second_cut_report_cleanup_result.md`

原因：

- 这些都不属于本轮 dedup 功能代码或其直接运行时验证

## 3. `scripts/deduplicate-review-queue.sql` 是否应纳入本轮提交

**不建议纳入。**

当前工作区状态显示：

- `AD scripts/deduplicate-review-queue.sql`

也就是：

- 该文件曾被加入索引
- 当前工作区里又被删除

结合修复报告，当前项目对这条线的选择是：

- 不再维护独立去重脚本
- 只保留唯一的 `schema.sql` + `mapper ensureTable`

从“最终仓库状态”角度看，删除它本身是合理的；但从“本轮提交卫生”角度，我仍建议：

- **不要把这个删除动作混进本轮 dedup 提交**

理由：

1. 本轮真正生效的去重能力已经完全落在：
   - `schema.sql`
   - `mapper XML`
   - repository test
2. 独立脚本删除属于“交付物清理 / 入口收敛”，不是功能正确性的必要条件
3. `AD` 状态本身不够干净，容易把历史 staging 状态一起带进去

更安全的处理方式：

- 本轮提交先不带 `scripts/deduplicate-review-queue.sql`
- 如团队确实要删，放到后续单独 cleanup commit 更合适

## 4. 当前代码改动是否只围绕指定范围

是。

当前生产代码只有：

- `src/main/resources/db/schema.sql`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`

测试只有：

- `src/test/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepositoryTests.java`

没有出现额外 service / repository 生产代码扩散。

## 5. 是否越界修改

### approve / reject 主逻辑

没有。

本轮没有修改：

- `AdminCompileArticleReviewQueueService`
- `markPublished(...)`
- `markRejected(...)`

approve / reject 语义保持不变。

### compile 主链

没有。

未修改 Writer / Reviewer / Fixer、review loop、persist gate 或 graph 节点逻辑。

### Query / AnswerGeneration

没有。

当前 diff 中没有 `src/main/java/com/xbk/lattice/query/**` 或 `AnswerGeneration*` 相关路径。

### 前端

没有。

当前 dedup 改动未涉及任何 `static/admin/**` 文件。

### 模型配置

没有。

未改 `.claude/t1.md`、binding、provider、profile 或 YAML 配置。

## 6. redline 是否仍为 BLOCKER=0

是。

本轮重新执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

- `BLOCKER=0`
- `REVIEW=1911`
- `ALLOWLIST=245`

没有新增 blocker。

## 7. runtime 复验是否足够支撑提交

足够。

来自 [compile_review_queue_dedup_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_review_queue_dedup_runtime_verification_report.md) 的直接证据：

- partial unique index 已真实存在
- 数据库层面重复 `(article_key, needs_human_review)` 插入被硬拒绝
- `ON CONFLICT DO UPDATE` 语义正确
- 同一 `article_key` 跨 job 只保留一条 pending 草稿
- `published / rejected` 历史记录仍保留
- approve / reject / query 语义不受影响

配套测试也足够：

- 定向 repository 测试：通过
- 全量 `mvn test = 866 / 0 / 0`

这已经足以支撑本轮提交。

## 8. 是否存在阻塞项

当前未发现阻塞项。

唯一需要明确的是提交 hygiene：

- 不要把 `AGENTS.md`
- 不要把无关 runtime / performance / acceptance 报告
- 不要把 `scripts/deduplicate-review-queue.sql` 的删除动作

混进本轮提交。

## 9. 是否建议提交

建议提交。

理由：

- 改动很窄
- 主题单一
- 无越界
- redline `BLOCKER=0`
- 定向测试 + 全量测试 + runtime 验证都已覆盖
- approve / reject / query 语义未被误伤

## 10. 推荐 commit message

`fix(compile): deduplicate pending review-queue drafts by article key`

## 11. 本轮是否修改代码

否。

本轮只做提交前质量复核，并新增本报告文件。
