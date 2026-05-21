# Compile Redis Interrupt Runtime Verification Report

## 1. 当前分支是否真的包含 Redis interrupted fix

是。

本轮只读确认到当前工作区已包含以下修复代码与测试：

- 生产代码：
  - [StringRedisKeyValueStore.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/StringRedisKeyValueStore.java)
- 定向测试：
  - [StringRedisKeyValueStoreTests.java](/Users/sxie/xbk/Lattice-java/src/test/java/com/xbk/lattice/query/service/StringRedisKeyValueStoreTests.java)
  - [RedisCompileWorkingSetStoreTests.java](/Users/sxie/xbk/Lattice-java/src/test/java/com/xbk/lattice/compiler/graph/RedisCompileWorkingSetStoreTests.java)

已确认的修复特征：

- `set(...)` 对 interrupted 类 Redis 失败写入本地 fallback
- `get(...)` / `getExpire(...)` 在 interrupted 场景可回退到本地 fallback
- `deleteByPrefix(...)` 在 interrupted 场景只清本地 fallback，不把异常继续升级
- 非 interrupted Redis 异常仍继续抛出

## 2. 真实失败场景是否复现 / 是否已消失

### 2.1 历史真实失败任务

当前库中的历史失败任务仍存在：

- 旧失败 job 1：
  - `jobId = 2d895d6c-7241-43dc-ba25-769384737d96`
  - `sourceDir = /tmp/lattice-normal-doc-smoke-src`
  - `step = compile_new_articles`
  - `errorCode = COMPILE_EXECUTION_FAILED`
  - `errorMessage = InterruptedException`
  - `step.error_message = org.springframework.data.redis.RedisSystemException: Redis command interrupted`
- 旧失败 job 2：
  - `jobId = 8539d4f2-fb28-42e1-9786-e16499bb8c33`
  - `sourceDir = /tmp/lattice-normal-doc-smoke-src`
  - `step = review_articles`
  - `errorCode = COMPILE_EXECUTION_FAILED`
  - `errorMessage = InterruptedException`
  - `step.error_message = org.springframework.data.redis.RedisSystemException: Redis command interrupted`

### 2.2 本轮等价 smoke runtime 复验

由于原始目录 `/tmp/lattice-normal-doc-smoke-src` 已不存在，本轮使用等价 smoke 目录复验：

- 复验目录：`/tmp/lattice-normal-doc-smoke-src-rerun`
- 内容：仅包含 `docs/quality-progress-and-lessons.md`

提交的真实 compile job：

- `jobId = 3fc1679d-e127-4e53-97f5-ecdd47eb7c01`
- `sourceDir = /tmp/lattice-normal-doc-smoke-src-rerun`
- `reviewMode = LLM`

### 2.3 复验结果

本轮等价 smoke 最终完整通过，旧失败场景**未复现**。

最终 compile job 状态：

- `jobId = 3fc1679d-e127-4e53-97f5-ecdd47eb7c01`
- `status = SUCCEEDED`
- `current_step = finalize_job`
- `error_code = null`
- `error_message = null`
- `persisted_count = 1`
- `finished_at = 2026-05-21 08:28:53.927331+00`

并且已明确跨过旧失败点：

- `compile_new_articles` 已从 `1/5` 推进到 `5/5`
- 随后成功进入 `review_articles`
- `review_articles` 也已从 `1/5` 推进到 `5/5`
- 此后继续进入：
  - `persist_articles`
  - `rebuild_article_chunks`
  - `refresh_vector_index`
  - `generate_synthesis_artifacts`

也就是说：

- 旧的 Writer 失败点没有重现
- 旧的 Reviewer 失败点也没有重现
- 该等价 smoke 最终完整收敛为 `SUCCEEDED`

## 3. compile job / step / errorCode / errorMessage

### 3.1 复验 job 当前状态

- `jobId = 3fc1679d-e127-4e53-97f5-ecdd47eb7c01`
- 最终状态：`SUCCEEDED`
- 最终步骤：`finalize_job`
- `errorCode = null`
- `errorMessage = null`
- `persistedCount = 1`

### 3.2 复验 job 已完成步骤

数据库 `compile_job_steps` 已确认以下步骤均成功：

- `initialize_job`
- `ingest_sources`
- `persist_source_files`
- `persist_source_file_chunks`
- `extract_ast_graph`
- `group_sources`
- `split_batches`
- `analyze_batches`
- `merge_concepts`
- `compile_new_articles`
- `review_articles`
- `persist_articles`
- `rebuild_article_chunks`
- `refresh_vector_index`

后续尾部步骤也全部完成：

- `generate_synthesis_artifacts`
- `capture_repo_snapshot`
- `finalize_job`

### 3.3 与历史失败对比

历史失败：

- 在 `compile_new_articles` 或 `review_articles` 结束前后失败
- `COMPILE_EXECUTION_FAILED`
- `InterruptedException`
- `Redis command interrupted`

本轮复验：

- `compile_new_articles` 已成功完成
- `review_articles` 已成功完成
- 当前仍无 `COMPILE_EXECUTION_FAILED`
- 当前仍无 `InterruptedException`

## 4. 是否仍有 `Redis command interrupted`

本轮复验中，**未观察到**新的：

- `Redis command interrupted`
- `RedisSystemException`
- `InterruptedException`

证据：

- `jobs/{jobId}` 返回 `errorCode = null`
- `jobs/{jobId}` 返回 `errorMessage = null`
- `compile_job_steps` 已完成步骤的 `error_message` 为空

## 5. 如果 Writer 主体完成后 Redis 写入被中断，后续步骤是否还能继续

从源码和测试看：**是，设计目标如此。**

从本轮 runtime 结果看：**至少旧失败点已经消失，链路已成功继续推进到 Writer/Reviewer 之后的后续步骤。**

更具体地说：

- 历史失败说明旧问题出在 Writer / Reviewer 主体完成后的 Redis working set 写回
- 本轮复验中：
  - Writer 全部完成后没有立刻失败
  - Reviewer 全部完成后也没有立刻失败
  - 后续已继续进入 `persist_articles / rebuild_article_chunks / refresh_vector_index / generate_synthesis_artifacts`

这说明当前代码至少已经**不再在同一失败窗口复现旧问题**。

## 6. 非 interrupted 类 Redis 故障是否仍会暴露

从当前源码和测试看：**是。**

依据：

- `StringRedisKeyValueStoreTests.shouldPropagateNonInterruptedRedisFailure()`
  断言非 interrupted 的 `RedisSystemException("serializer broken", ...)` 会继续原样抛出

说明：

- 本轮没有对真实 Redis 做故障注入
- 因此这条结论来自当前分支源码 + 定向测试，而非运行时故障注入验证

## 7. 该问题是否还阻塞当前这轮提交

基于本轮复验，**这个 Redis interrupted 问题本身不再构成当前提交的直接阻塞项。**

原因：

- 修复已在当前代码中
- 等价 `lattice-normal-doc-smoke-src` 场景已真实跨过旧失败点
- 旧的 `COMPILE_EXECUTION_FAILED / InterruptedException / Redis command interrupted` 未再复现

保留说明：

- 本轮已经拿到完整 `SUCCEEDED` 结果
- 就用户要求聚焦的“Writer / Reviewer 附近 Redis interrupted 失败”而言，旧问题已明显消失

## 8. 下一步建议

建议：

- 可以进入提交前质量复核，但在复核说明里注明：
  - 本轮 runtime 已验证等价 normal-doc smoke 不再在 Writer / Reviewer 阶段触发 `Redis command interrupted`
  - 非 interrupted Redis 故障仍由定向测试保证继续暴露
  - 若需要更强信心，可让当前 `3fc1679d...` job 自然跑到最终 `SUCCEEDED` 后再补一条尾部完成记录

## 9. 本轮是否修改代码

否。

本轮只进行了：

- 只读看源码
- 只读看测试
- 打包当前工作区 jar
- 启动真实应用
- 提交一个等价 normal-doc smoke compile
- 只读查询 `jobs` / `processing-tasks` / `compile_job_steps`
- 只读看历史失败记录

未修改任何代码、测试、前端、后端或数据。
