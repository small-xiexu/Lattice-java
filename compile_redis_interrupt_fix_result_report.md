# compile_redis_interrupt_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/query/service/StringRedisKeyValueStore.java`
  - 修改 `get(...)`
  - 修改 `set(...)`
  - 修改 `getExpire(...)`
  - 修改 `deleteByPrefix(...)`
  - 新增中断识别与本地 fallback 辅助方法
  - 新增 `LocalFallbackValue` 内部类
- `src/test/java/com/xbk/lattice/query/service/StringRedisKeyValueStoreTests.java`
  - 新增 Redis interrupted / 非 interrupted 语义测试
- `src/test/java/com/xbk/lattice/compiler/graph/RedisCompileWorkingSetStoreTests.java`
  - 新增 compile working set 在 Redis interrupted 下的本地 fallback 读回测试

## 2. Interrupted / Redis interrupted 之前是怎么冒泡成 job FAILED 的

修复前路径是：

1. compile graph 节点主体逻辑已完成，例如 Writer 草稿已经生成完。
2. 节点结束前需要把 working set 写回 Redis。
3. `StringRedisTemplate.opsForValue().set(...)` 在应用 shutdown / restart / 线程中断场景下抛出：
   - `RedisSystemException: Redis command interrupted`
4. 该异常原样冒泡到 graph 节点。
5. compile job 最终收敛为：
   - `COMPILE_EXECUTION_FAILED`
   - `errorMessage = InterruptedException`

也就是说，失败点不在 Writer / Reviewer 主逻辑，而在 **working set 的 Redis 回写**。

## 3. 现在如何处理这类中断

本轮只对 **中断类 Redis 失败** 做最小降级：

- 在 `StringRedisKeyValueStore.set(...)` 中：
  - 若识别为 `InterruptedException` / `InterruptedIOException` / `CancellationException` / `Redis command interrupted`
  - 不再直接向上抛出
  - 而是把这次 value + TTL 暂存到进程内的 `interruptedFallbackValues`
- 在 `get(...)` / `getExpire(...)` 中：
  - Redis 正常可用时，优先读真实 Redis
  - 如果 Redis 返回空，或读取也被同类中断打断，则回退读取本地 fallback
- 在 `deleteByPrefix(...)` 中：
  - 先清理本地 fallback
  - 若 Redis 删除被中断，只保留本地清理，不把中断继续升级成业务失败

因此现在的语义是：

- **真实 Redis 正常时仍走 Redis**
- **只有中断类失败时才降级到同进程本地 fallback**
- 这样 compile working set 在同一执行进程内仍可被下一节点读回，不会因为中断写入直接把 job 打成 FAILED

## 4. 是否会误吞真实 Redis 错误

不会。

本轮只降级以下中断类信号：

- `InterruptedException`
- `InterruptedIOException`
- `CancellationException`
- 异常类名包含 `interrupted`
- 异常消息包含 `Redis command interrupted`
- 当前线程已处于 interrupted 状态

除此之外，**非中断类 Redis 错误仍原样抛出**，例如：

- 序列化问题
- 连接/协议问题
- 其他 `RedisSystemException` 但不含 interrupted 语义

定向测试已覆盖：

- interrupted 会降级
- 非 interrupted 会继续抛异常

## 5. 是否修改业务主链

否。

- 未修改 `src/main/java/com/xbk/lattice/compiler/graph/**`
- 未修改 Writer / Reviewer / Fixer 业务逻辑
- 未修改 approve / reject / publish
- 未修改 Query / AnswerGeneration 业务逻辑
- 未修改 schema

## 6. redline BLOCKER 是否仍为 0

- 已运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`，`REVIEW=1863`，`ALLOWLIST=244`

## 7. 测试是否通过

- 定向测试通过：
  - `StringRedisKeyValueStoreTests`
  - `RedisCompileWorkingSetStoreTests`
- 结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
- 全量 `mvn test`：`Tests run: 861, Failures: 0, Errors: 0, Skipped: 0`

## 8. 下一轮是否建议交给 agentD 做失败任务复验

建议。

下一轮建议 agentD 针对真实失败任务做运行时复验，重点确认：

- `lattice-normal-doc-smoke-src` 同类 compile 不再因 `Redis command interrupted` 直接 FAILED
- Writer 完成后若 working set 写入被中断，后续 REVIEW / persist 链路是否还能继续推进
- 非中断类 Redis 故障仍会被正确暴露，而不是被误吞
