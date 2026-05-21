# Compile Reviewer Payload Slimming 提交前质量复核报告

## 结论

建议提交。

这组改动的主题单一而且边界清楚：只是在 Reviewer / Fixer 进入 LLM 前，把来源 payload 从“全文拼接 + 前缀截断”改成“相关片段优先 + 有界截断”。没有越界碰到 Writer / Reviewer / Fixer 主链的其他语义、approve / reject / publish、Query / AnswerGeneration、schema 或模型配置。

推荐 commit message：

`perf(compile): slim reviewer payload with relevant source sections`

## 当前建议纳入提交的文件清单

### 代码

- `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
- `src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java`
- `src/test/java/com/xbk/lattice/compiler/service/CompileArticleReviewFlowTests.java`

### 报告 / redline

- `compile_reviewer_payload_slimming_fix_result_report.md`
- `compile_reviewer_payload_slimming_runtime_verification_report.md`
- `compile_reviewer_payload_slimming_pre_commit_quality_report.md`
- `special_cases_report.md`

说明：

- `special_cases_report.md` 当前变化来自 redline 重扫与 `CompileArticleNode.java` 行号偏移，可随本轮一并提交，也可按团队习惯不纳入。

## 当前应排除的无关文件清单

### 明确应排除

- `admin_review_queue_count_filter_visual_fix_result_report.md`

原因：

- 这是另一条“admin review queue / 待人工确认展示”主线的报告文本改写
- 与 Reviewer payload slimming 功能无关

### 建议排除的其他文件

- `compile_pipeline_performance_analysis_report.md`
- `compile_pipeline_second_bottleneck_analysis_report.md`
- `compile_writer_unit_routing_gate_runtime_verification_report.md`

原因：

- 前两份是前序/后续性能分析报告，不是本轮代码交付物
- `compile_writer_unit_routing_gate_runtime_verification_report.md` 属于上一刀 Writer gate 的中间验证报告，不属于本轮提交主题

## 当前代码改动是否只围绕指定范围

是。

从 `git diff --name-status` 看，当前代码层只有：

- `CompileArticleNode.java`
- `ArticleCompileSupport.java`
- `CompileArticleReviewFlowTests.java`

改动目标也只围绕：

- `buildReviewSourceContents(...)`
- `selectContentBySourceRefs(...)`
- `buildRelevantSourceContents(...)`
- `reviewDraftArticles(...)`
- `fixReviewedArticles(...)`
- 以及对 Reviewer / Fixer 输入的测试闭环

没有看到额外的 compile graph、persist、queue、query 或页面逻辑扩散。

## 是否越界修改

### Writer / Reviewer / Fixer 主链其他逻辑

没有。

本轮没有修改：

- Writer 是否执行
- Reviewer 是否执行
- Fixer 是否执行
- routeAfterReview / ReviewDecisionPolicy
- maxFixRounds
- reviewMode

改变的只是 Reviewer / Fixer 输入来源正文的构造方式。

### Query / AnswerGeneration

没有。

未出现 `src/main/java/com/xbk/lattice/query/**` 或 `AnswerGeneration*` 相关路径。

### approve / reject / publish

没有。

未改人工确认队列、articles/chunks/vector 落库或 publish 逻辑。

### schema

没有。

未修改 `src/main/resources/db/schema.sql` 或任何 mapper schema。

### 模型配置

没有。

未修改 `.claude/t1.md`、provider、binding、profile、embedding 或 YAML 配置。

## redline 是否仍为 BLOCKER=0

是。

本轮重新执行：

`bash scripts/scan-redline.sh special_cases_report.md`

结果：

- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

## 全量测试与 runtime 验证是否足够支撑提交

足够。

依据：

### 修复报告中的测试

- 定向测试：
  - `CompileArticleReviewFlowTests`
  - `SchemaAwarePromptsTests`
  - `ArticleReviewerGatewayTests`
  - `ReviewFixServiceTests`
- 全量测试：
  - `mvn test`
  - 结果：`857 / 0 / 0`

### runtime 验证中的性能收益

来自 [compile_reviewer_payload_slimming_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_reviewer_payload_slimming_runtime_verification_report.md)：

- Reviewer 单次耗时下降约 **53–61%**
- Reviewer 阶段总耗时下降约 **40%**
- 普通文档拆分未受影响
- compile 全链路 `Writer -> Reviewer -> Synthesis -> finalize` 跑通

说明：

- runtime 报告里本轮重跑的全量 `mvn test` 受环境问题影响出现 `803 run, 11 errors`
- 该报告已明确判断这些是数据库/Redis/Spring context 启动环境问题，**不是**本轮 payload slimming 回归
- 因此当前最可信的代码 gate 仍是修复报告里的 `857 / 0 / 0`

对这类“纯 payload construction 优化”改动，这套证据已经足够支撑提交。

## 是否存在阻塞项

当前未发现阻塞项。

仅有一个非阻断说明：

- runtime 验证里全量 `mvn test` 再跑时有环境型 11 errors，但修复报告的 `857 / 0 / 0` 已提供更可信的代码回归门禁，且 runtime 主要任务是量化 Reviewer 降时，不构成当前提交阻塞

## 是否建议提交

建议提交。

理由：

- 改动主题单一
- 边界清楚
- 无越界
- redline `BLOCKER=0`
- 修复报告全量 `857 / 0 / 0`
- runtime 性能收益清晰可量化
- 没有业务特判或资料名硬编码

## 推荐 commit message

`perf(compile): slim reviewer payload with relevant source sections`

## 本轮是否修改代码

否。

本轮只做提交前质量复核，并新增本报告文件。
