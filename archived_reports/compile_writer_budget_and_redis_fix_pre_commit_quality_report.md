# Compile Writer Budget + Redis Fix 组合提交前质量复核报告

## 结论

**不建议一起提交。**

这两组改动都发生在 compile 相关链路，表面上都在处理“正常文档 compile 变慢/变脆”的问题，但从提交卫生和风险边界看，它们更适合拆成两个 commit：

1. `Writer payload budget slimming`
2. `Redis interrupted runtime resilience`

原因很简单：

- 第一组是**性能优化**
- 第二组是**运行态韧性修复**
- 第一组只在 `compiler/node`
- 第二组落在 `query/service/StringRedisKeyValueStore` 这种共享基础设施层

它们没有代码依赖关系，回滚诉求也完全不同，绑在一起会让提交语义变浑。

## 1. 当前建议纳入提交的文件清单

### Commit A：Writer payload budget slimming

建议纳入：

- `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
- `src/test/java/com/xbk/lattice/compiler/service/SchemaAwarePromptsTests.java`
- `compile_writer_payload_budget_slimming_fix_result_report.md`
- `compile_writer_payload_budget_slimming_runtime_verification_report.md`

如团队要求附带本轮 pre-commit 报告，也可加：

- `compile_writer_budget_and_redis_fix_pre_commit_quality_report.md`

### Commit B：Redis interrupted runtime resilience

建议纳入：

- `src/main/java/com/xbk/lattice/query/service/StringRedisKeyValueStore.java`
- `src/test/java/com/xbk/lattice/compiler/graph/RedisCompileWorkingSetStoreTests.java`
- `src/test/java/com/xbk/lattice/query/service/StringRedisKeyValueStoreTests.java`
- `compile_redis_interrupt_fix_result_report.md`
- `compile_redis_interrupt_runtime_verification_report.md`

如果团队会提交归因文档，可选加入：

- `compile_normal_doc_failure_triage_report.md`
- `compile_redis_interrupt_root_cause_analysis_report.md`

## 2. 当前应排除的无关文件清单

明确应排除：

- `admin_review_queue_count_filter_visual_runtime_verification_report.md`

原因：

- 与本轮 Writer budget / Redis fix 无关
- 只是另一条 admin review queue 展示主线的报告文本差异

建议排除的分析类文件：

- `compile_pipeline_performance_analysis_report.md`
- `compile_pipeline_second_bottleneck_analysis_report.md`
- `compile_pipeline_third_bottleneck_analysis_report.md`
- `post_compile_second_cut_report_cleanup_result.md`

原因：

- 这些是前序/后续分析或清理报告
- 不是当前两组代码修复本身的交付物

`special_cases_report.md` 处理建议：

- 当前不建议纳入任一单独 commit

原因：

- 它同时吸收了 `CompileArticleNode.java` 和 `StringRedisKeyValueStore.java` 的行号/命中变化
- 若拆成两个 commit，`special_cases_report.md` 很难自然归属于其中一个
- 最干净的做法是各自提交前按最终 staged 状态重新扫描后再决定是否提交，或者直接不纳入代码提交

## 3. 是否围绕同一业务主题

从“业务背景”看，是同一条 compile 正常文档链路。

从“提交边界”看，不是同一组改动。

### Writer payload budget slimming

目标：

- 压缩 Writer 单次 prompt / source payload 体积
- 降低 Writer 单次 LLM 调用成本

代码范围：

- `CompileArticleNode`
- `SchemaAwarePromptsTests`

### Redis interrupted runtime fix

目标：

- 避免 compile 节点主逻辑完成后，Redis working set 写入被中断直接把 job 打成 FAILED

代码范围：

- `StringRedisKeyValueStore`
- `RedisCompileWorkingSetStoreTests`
- `StringRedisKeyValueStoreTests`

这两者在“为什么要做”上相关，但在“修什么层”上并不相同。

## 4. 当前代码改动是否只围绕指定范围

是。

当前工作区代码文件只有：

- `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
- `src/main/java/com/xbk/lattice/query/service/StringRedisKeyValueStore.java`
- `src/test/java/com/xbk/lattice/compiler/graph/RedisCompileWorkingSetStoreTests.java`
- `src/test/java/com/xbk/lattice/compiler/service/SchemaAwarePromptsTests.java`
- `src/test/java/com/xbk/lattice/query/service/StringRedisKeyValueStoreTests.java`

符合本轮目标，没有扩到更多生产文件。

## 5. 是否越界修改

### 编译主链其他逻辑

没有明显越界。

`CompileArticleNode` 的修改仍然围绕：

- Writer prompt 预算
- structured sections budget
- source snippet budget

没有去动：

- review loop
- persist gate
- queue publish

### Query / AnswerGeneration

没有触碰 Query / AnswerGeneration 语义。

虽然 `StringRedisKeyValueStore.java` 位于 `src/main/java/com/xbk/lattice/query/service/` 下，但它是：

- Redis 键值存储基础设施封装

不是：

- Query 检索排序
- AnswerGeneration
- fallback / citation / prompt 主链

### approve / reject / publish

没有。

当前工作区没有这组路径上的生产代码变更。

### schema

没有。

没有 `schema.sql` 或持久化结构变更。

### 模型配置

没有。

没有改 `.claude/t1.md`、provider、binding、profile、embedding 或 YAML 配置。

## 6. redline 是否仍为 BLOCKER=0

是。

本轮重新执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

- `BLOCKER=0`
- `REVIEW=1911`
- `ALLOWLIST=245`

说明：

- 相比前一轮，`REVIEW/ALLOWLIST` 增加，主要来自 `StringRedisKeyValueStore` 新增的 fallback/interrupted 相关代码命中
- 但没有新增 blocker

## 7. 测试与 runtime 验证是否足够支撑提交

各自都足够支撑**单独提交**。

### Writer payload budget slimming

来自修复/验证报告：

- 定向测试：`15 / 0 / 0`
- 全量测试：`858 / 0 / 0`
- runtime：
  - Writer 覆盖面未变
  - compile 总耗时轻微改善
  - 主要收益偏向 token 成本而非延迟

### Redis interrupted runtime fix

来自修复/验证报告：

- 定向测试：`4 / 0 / 0`
- 全量测试：`861 / 0 / 0`
- runtime：
  - 等价 normal-doc smoke 不再复现 `Redis command interrupted`
  - 旧失败点 `compile_new_articles / review_articles` 已跨过

### 组合提交视角

虽然两组各自验证充分，但**不代表必须组合提交**。

恰恰因为它们：

- 测试集不同
- 风险层次不同
- 回滚诉求不同

更适合拆开。

## 8. 是否建议一起提交

**不建议一起提交。**

理由：

1. 一组是性能优化，一组是韧性修复
2. 一组只改 compile writer 输入，一组改共享 Redis 基础设施
3. 两组没有强依赖
4. 任何一组将来单独回退都应该是可操作的

如果合并成一个 commit，后续会有两个问题：

- 回滚 Redis fallback 时会把 Writer budget 一起带回去
- 复盘性能收益与运行态修复收益时会混在一起

## 9. 如果不建议一起提交，建议如何拆分

推荐拆成两个 commit。

### Commit A

`perf(compile): add writer payload budget limits`

包含：

- `CompileArticleNode.java`
- `SchemaAwarePromptsTests.java`
- `compile_writer_payload_budget_slimming_fix_result_report.md`
- `compile_writer_payload_budget_slimming_runtime_verification_report.md`

### Commit B

`fix(redis): degrade interrupted compile working-set writes to local fallback`

包含：

- `StringRedisKeyValueStore.java`
- `RedisCompileWorkingSetStoreTests.java`
- `StringRedisKeyValueStoreTests.java`
- `compile_redis_interrupt_fix_result_report.md`
- `compile_redis_interrupt_runtime_verification_report.md`

可选附带分析文档：

- `compile_normal_doc_failure_triage_report.md`
- `compile_redis_interrupt_root_cause_analysis_report.md`

## 10. 推荐 commit message

如果必须给当前两组分别推荐：

- Writer budget：
  - `perf(compile): add writer payload budget limits`
- Redis fix：
  - `fix(redis): degrade interrupted compile working-set writes to local fallback`

如果用户坚持合并成一个 commit，最不坏的 message 是：

- `perf(compile): slim writer payload and harden interrupted working-set writes`

但这不是首选。

## 11. 是否存在阻塞项

当前没有代码质量层面的 blocker。

但存在一个**提交策略层面**阻塞项：

- 这两组改动不适合合成一个提交

也就是说：

- 不是“不能提交”
- 而是“不要以一个 commit 提交”

## 12. 本轮是否修改代码

否。

本轮只做组合提交前质量复核，并新增本报告文件。
