# Compile Normal Doc Failure Triage Report

## 1. 失败任务的真实 jobId / sourceDir / step

当前 `processing-tasks` 中与用户描述一致的失败任务为：

- `title = lattice-normal-doc-smoke-src`
- `taskId = compile-job:2d895d6c-7241-43dc-ba25-769384737d96`
- `compileJobId = 2d895d6c-7241-43dc-ba25-769384737d96`
- `sourceDir = /tmp/lattice-normal-doc-smoke-src`
- `taskType = STANDALONE_COMPILE`
- `sourceType = DIRECT_COMPILE`
- `runId = null`

真实失败阶段：

- `compileCurrentStep = compile_new_articles`
- 前端展示阶段：`currentStepLabel = 内容生成`
- `progressSteps` 中：
  - `COMPILE_NEW_ARTICLES / 内容生成 = FAILED`
  - `REVIEW_ARTICLES / 质量检查 = PENDING`
  - `FINALIZE_JOB / 写入知识库 = PENDING`

补充：

- 同一 `sourceDir` 还存在一条更早失败 job：
  - `8539d4f2-fb28-42e1-9786-e16499bb8c33`
  - 失败在 `review_articles`
- 但当前后台显示为 Writer / 内容生成失败，对应的就是较新的 `2d895d6c-7241-43dc-ba25-769384737d96`

## 2. errorCode / reasonSummary / completionNotice

来自 `processing-tasks` 当前失败项：

- `compileErrorCode = COMPILE_EXECUTION_FAILED`
- `errorMessage = InterruptedException`
- `reasonSummary = 编译执行过程中出现异常，请结合当前步骤和错误信息排查。`
- `completionNotice = 编译执行过程中出现异常，请结合当前步骤和错误信息排查。`
- `progressText = 5 / 5 · Writer 草稿生成完成：quality-progress-and-lessons-下一步计划`

来自 `compile_jobs`：

- `status = FAILED`
- `current_step = compile_new_articles`
- `progress_message = Writer 草稿生成完成：quality-progress-and-lessons-下一步计划`
- `error_code = COMPILE_EXECUTION_FAILED`
- `error_message = InterruptedException`

来自 `compile_job_steps` 的真实 step 错误：

- `step_name = compile_new_articles`
- `agent_role = WriterAgent`
- `model_route = compile.writer.agentd-gpt-5-5-chat`
- `status = failed`
- `error_message = org.springframework.data.redis.RedisSystemException: Redis command interrupted`

## 3. 失败根因判断

根因判断：

- 当前失败更像是 **Writer 阶段完成后、状态写入 Redis 或步骤持久化相关的运行时中断**
- 不是 Writer prompt 构造本身失败
- 也不像模型调用本身失败

依据：

1. `compile_new_articles` 的 `progress_message` 已经推进到：
   - `Writer 草稿生成完成：quality-progress-and-lessons-下一步计划`
   说明最后一个 Writer concept 已经跑完，不是刚进入 prompt 构造就失败。
2. `compile_job_steps.compile_new_articles` 的 `summary` 显示：
   - `conceptCount=5`
   - 没有 budget exceeded / payload 相关错误码
3. step 级唯一明确异常是：
   - `org.springframework.data.redis.RedisSystemException: Redis command interrupted`
4. 同类异常并不只发生在这个 job：
   - `8539d4f2...` 同一 `sourceDir` 上一次失败在 `review_articles`，错误同样是 `RedisSystemException: Redis command interrupted`
   - `be7bd49a...` 另一个 `lattice-topic-gate-smoke-src` 也在 `compile_new_articles` 失败，`errorMessage = InterruptedException`

因此，这更像：

- Redis 访问被中断
- 线程被中断
- 或运行态在 step 完成前后被打断

而不是：

- 某个特定 normal doc 样本内容导致 Writer payload 失控

## 4. 是否与本轮 Writer payload slimming 直接相关

判断：**否，未发现直接相关证据。**

原因：

1. 若与 payload budget slimming 直接相关，优先预期会看到：
   - `COMPILE_TOTAL_BUDGET_EXCEEDED`
   - `CompileBudgetExceededException`
   - prompt 构造 / source content 选择异常
2. 当前实际错误是：
   - `COMPILE_EXECUTION_FAILED`
   - `InterruptedException`
   - step.error_message = `RedisSystemException: Redis command interrupted`
3. 同类 `InterruptedException` 还出现在：
   - 同一 `sourceDir` 的 `review_articles`
   - 另一个 smoke source 的 `compile_new_articles`
   说明它不是只绑定到 Writer payload slimming 这组改动
4. 修复报告中的 payload budget 关键点：
   - `WRITER_TOTAL_PAYLOAD_MAX_CHARS`
   - `WRITER_STRUCTURED_SECTIONS_MAX_CHARS`
   - `WRITER_SOURCE_SNIPPET_MAX_CHARS`
   - `buildWriterSourceContents(...)`
   这些都属于 **输入裁剪 / prompt 构造预算控制**
   而当前异常出现在 **Writer step 完成后的 Redis 中断**

分项判断：

- `WRITER_TOTAL_PAYLOAD_MAX_CHARS`：无直接证据相关
- `WRITER_STRUCTURED_SECTIONS_MAX_CHARS`：无直接证据相关
- `WRITER_SOURCE_SNIPPET_MAX_CHARS`：无直接证据相关
- `buildWriterSourceContents(...)`：无直接证据相关

## 5. 对应 source-run（如果有）

无。

当前失败任务是：

- `taskType = STANDALONE_COMPILE`
- `sourceType = DIRECT_COMPILE`
- `source_sync_run_id = null`
- `runId = null`

因此没有对应的 `source-run` 可继续下钻。

## 6. 后台日志中的异常栈或 errorCode

日志 / 运行态侧已检查：

- 已检查本地保留日志：
  - `.codex/run/local-dev-18082.log`
  - `.codex/run/local-dev-foreground.log`
  - 其他 `.codex/run/*.log`
- 未在现存本地日志中找到这两个 jobId 或 `lattice-normal-doc-smoke-src` 的完整异常栈

因此本轮日志侧可落到的最强证据是：

- `compile_jobs.error_code = COMPILE_EXECUTION_FAILED`
- `compile_jobs.error_message = InterruptedException`
- `compile_job_steps.error_message = org.springframework.data.redis.RedisSystemException: Redis command interrupted`

说明：

- 本地运行日志没有保留这条失败的完整堆栈
- 但数据库中的 step/error 字段已经足够确认异常类型不是 payload budget exceeded，而是 Redis / 线程中断类异常

## 7. 是否阻塞当前这轮提交

判断：**阻塞。**

原因：

- 当前真实后台仍有失败任务，且用户已明确要求对这条真实失败做归因
- 虽然它看起来**不是** Writer payload slimming 直接引入的回归
- 但在未解释清楚并决定如何处理前，这会影响本轮“当前运行环境是否健康”的提交信心

更具体地说：

- 若这轮提交目标只覆盖 Writer payload slimming，本失败不构成“直接由本轮改动引起”的 blocker
- 但它是当前真实环境中的活跃失败信号，提交前需要最小收口策略

## 8. 下一轮只建议修一个最小点

只建议一个最小点：

- **先处理 `Redis command interrupted` 的运行态中断根因，聚焦 `compile_new_articles/review_articles` step 结束前后的 Redis 状态写入/线程中断路径。**

不建议下一轮同时改：

- Writer payload budget
- Reviewer payload
- 题集
- 前端展示

## 9. 本轮是否修改代码

否。

本轮只做了：

- redline
- `processing-tasks` 只读 API
- `compile_jobs / compile_job_steps / source_sync_runs` 只读数据库查询
- 本地日志只读搜索
- 相关 runtime/fix report 对照

未修改任何代码、测试、前端、后端或数据。

