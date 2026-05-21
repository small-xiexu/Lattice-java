# Compile Writer Unit Routing Gate 提交前质量复核报告

## 结论

建议提交。

这组改动边界非常清楚：它只是在 `AnalyzeNode` 的长文档 topic 产物进入 Writer 之前，加了一层通用的 routing gate，把“单源、被过度拆分的长文档”收敛成一个 overview concept。没有越界碰 compile 主链其他执行逻辑，也没有碰 Query / AnswerGeneration、approve / reject / publish、schema 或模型配置。

推荐 commit message：

`feat(compile): collapse over-fragmented document topics before writer`

## 当前建议纳入提交的文件清单

### 代码

- `src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java`
- `src/main/java/com/xbk/lattice/compiler/node/DocumentTopicWriterGatePolicy.java`
- `src/test/java/com/xbk/lattice/compiler/service/AnalyzeNodeTests.java`

### 报告 / redline

- `compile_writer_unit_routing_gate_fix_result_report.md`
- `compile_writer_unit_routing_gate_full_runtime_verification_report.md`
- `compile_writer_unit_routing_gate_pre_commit_quality_report.md`
- `special_cases_report.md`

说明：

- `special_cases_report.md` 当前改动来自 redline 重新扫描，以及 `AnalyzeNode.java` 行号变化引起的定位更新；可随本轮一起提交，也可按团队习惯不纳入代码提交。

## 当前应排除的无关文件清单

### 明确应排除

- `admin_review_queue_count_filter_visual_fix_result_report.md`

原因：

- 这是另一条“人工确认发布语义 / review queue 展示”主线的报告文本差异
- 与 Writer gate 功能无关

### 建议一并排除

- `compile_pipeline_performance_analysis_report.md`
- `compile_writer_unit_routing_gate_runtime_verification_report.md`

原因：

- `compile_pipeline_performance_analysis_report.md` 是后续性能归因报告，不属于本轮功能提交物
- `compile_writer_unit_routing_gate_runtime_verification_report.md` 看起来已被 `compile_writer_unit_routing_gate_full_runtime_verification_report.md` 覆盖；若团队只保留最终验证报告，建议不混入这次提交

## 是否只属于 Writer gate 这组改动

是。

当前代码层面只有：

- `AnalyzeNode.java`
- `DocumentTopicWriterGatePolicy.java`
- `AnalyzeNodeTests.java`

改动目标也只有一个：

- 对单源过度专题化长文档做 overview collapse，减少 Writer unit 数量

未发现顺手扩到其他 compile 路由、review 策略、persist 或 query 行为。

## 是否越界修改

### Writer / Reviewer / Fixer 主链其他逻辑

没有。

没有修改：

- `CompileArticleNode`
- `ReviewerAgent`
- `FixerAgent`
- review loop
- persist gate

只是在 `AnalyzeNode` 里把 `documentTopicConceptExtractor.extract(...)` 的结果交给 `documentTopicWriterGatePolicy.rewrite(...)` 再返回。

### Query / AnswerGeneration

没有。

当前 diff 中没有 `src/main/java/com/xbk/lattice/query/**` 或 `AnswerGeneration*` 路径。

### approve / reject / publish

没有。

当前 diff 中没有人工确认队列 publish flow 相关文件，也没有 articles/chunks/vector 落库行为改动。

### schema

没有。

未修改 `src/main/resources/db/schema.sql`。

### 模型配置

没有。

未修改 `.claude/t1.md`、LLM binding、provider、profile 或 YAML 配置。

## redline 是否仍为 BLOCKER=0

是。

本轮重新执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

当前 `special_cases_report.md` 中与本轮相关的变化，主要是：

- 扫描时间刷新
- `AnalyzeNode.java` 行号偏移更新

未发现新的 blocker。

## 全量测试结果是否足够支撑提交

足够。

依据：

- 修复报告记录的定向测试：
  - `AnalyzeNodeTests`
  - `AnalyzeNodeStructuredTableWriterGateTests`
  - 结果：`16 / 0 / 0`
- 完整 runtime 验证报告记录的全量测试：
  - `mvn test`
  - 结果：`855 / 0 / 0`

同时 runtime 已验证：

- 真实长文档 `卡券三期-迁移方案.md` collapse 成 1 个 overview concept
- 普通长文档 `quality-progress-and-lessons.md` 保持原拆分
- Writer / Reviewer 次数从约 25 降到 6，降幅约 76%

对这类 compile routing gate 来说，已经足够支撑提交。

## 是否存在阻塞项

当前未发现阻塞项。

唯一需要说明的非阻塞点：

- `compile_writer_unit_routing_gate_full_runtime_verification_report.md` 里提到 local-dev 下 devtools/classpath 重启会干扰运行时观测，但应用自动恢复，job 未丢失；这不是本轮 Writer gate 引入的新行为，也不阻塞这组改动提交

## 是否建议提交

建议提交。

理由：

- 代码边界小
- 无越界
- redline `BLOCKER=0`
- 全量 `mvn test=855 / 0 / 0`
- 真实运行时收益明确
- 没有业务特判或文档名硬编码分支

## 推荐 commit message

`feat(compile): collapse over-fragmented document topics before writer`

## 本轮是否修改代码

否。

本轮只做提交前质量复核，并新增本报告文件。
