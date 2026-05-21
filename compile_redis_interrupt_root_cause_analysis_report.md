# Compile Redis Interrupt 根因分析报告

## 1. 根因判断

当前 `Redis command interrupted` 最可能发生在：

- **compile graph 节点执行线程在写 Redis working set 时被中断**

更具体地说，最像的路径是：

1. `compile_new_articles` 或 `review_articles` 的主体工作已经完成
2. 节点准备把结果写回 Redis working set
3. 此时应用发生 restart / shutdown 或作业线程被中断
4. `StringRedisTemplate` 在阻塞中的 Redis 命令被打断
5. Spring Data Redis 抛出：
   - `org.springframework.data.redis.RedisSystemException: Redis command interrupted`
6. 图节点异常冒泡，最终 compile job 被标记为：
   - `COMPILE_EXECUTION_FAILED`
   - `errorMessage = InterruptedException`

所以当前更像：

- **线程被中断 / 生命周期干扰下的 Redis 写入失败**

而不是：

- Redis 本身连接质量长期不稳
- payload slimming 直接引入的新逻辑错误
- 数据库进度心跳写入失败

## 2. 直接证据

### 证据 A：失败发生在 Writer 已完成之后

来自 [compile_normal_doc_failure_triage_report.md](/Users/sxie/xbk/Lattice-java/compile_normal_doc_failure_triage_report.md)：

- `compileCurrentStep = compile_new_articles`
- `progress_message = Writer 草稿生成完成：quality-progress-and-lessons-下一步计划`

这说明：

- 最后一个 Writer concept 已经跑完
- 失败不是发生在模型调用开始前，也不像 prompt 构造失败

### 证据 B：step 级具体异常是 Redis 中断

同一报告给出的 `compile_job_steps` 真实错误是：

- `step_name = compile_new_articles`
- `error_message = org.springframework.data.redis.RedisSystemException: Redis command interrupted`

而 `compile_jobs` 聚合字段只留下了更粗的：

- `error_code = COMPILE_EXECUTION_FAILED`
- `error_message = InterruptedException`

说明：

- 更底层的异常来自 Redis 调用
- 顶层只把它折叠成了“编译执行中断”

### 证据 C：同类异常不止一次，且跨步骤复现

同一 triage 报告指出：

- `2d895d6c...` 失败在 `compile_new_articles`
- `8539d4f2...` 失败在 `review_articles`
- 另一个 smoke job 也出现 `InterruptedException`

这很重要，因为它说明：

- 问题不是某个单一 Writer payload 样本触发
- 也不是仅限 Writer 阶段
- 只要节点结束前后需要写 Redis working set，就都可能被打断

### 证据 D：local-dev 明确启用了 devtools restart

来自 [application-local-dev.yml](/Users/sxie/xbk/Lattice-java/src/main/resources/application-local-dev.yml)：

```yaml
spring:
  devtools:
    restart:
      enabled: true
      additional-paths:
        - src/main/resources/static
        - src/main/resources
```

这说明本地运行环境本身就允许：

- 资源路径变化触发 restart

而此前多份 runtime 报告也反复提到：

- `devtools` 会在 compile 中途重启

### 证据 E：`CompileJobLeaseManager` 会主动中断线程

来自 [CompileJobLeaseManager.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/CompileJobLeaseManager.java)：

- `cancelJob(jobId)` 调用 `heartbeatFuture.cancel(true)`
- `destroy()` 调用 `scheduledExecutorService.shutdownNow()`

这些都属于：

- 明确的线程中断源

虽然它们直接针对的是心跳线程，但在 shutdown/restart 过程里，应用上下文销毁会让正在执行的基础设施组件一起进入中断 / 关闭状态。

## 3. 最可能的异常触发点

### 第一候选：`CompileNewArticlesNode.execute()` 末尾的 `saveDraftArticles(...)`

当前链路：

- [CompileNewArticlesNode.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/node/CompileNewArticlesNode.java)

在 Writer 全部跑完后，会执行：

- `state.setDraftArticlesRef(workingSetStore().saveDraftArticles(state.getJobId(), currentDrafts));`

如果 working set store 是 Redis 版：

- [RedisCompileWorkingSetStore.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/RedisCompileWorkingSetStore.java)

那么 `saveDraftArticles(...)` 最终会走：

- `savePayload(...)`
- `saveJson(...)`
- `redisKeyValueStore.set(...)`
- `StringRedisTemplate.opsForValue().set(...)`

这正是最符合当前症状的一跳：

- Writer 已完成
- 节点还没返回
- Redis set 被中断
- step 标记为 `compile_new_articles` 失败

### 第二候选：`ReviewArticlesNode.execute()` / `FixReviewIssuesNode.execute()` 里的 working set 写入

同类写入点包括：

- `saveReviewedArticles(...)`
- `saveReviewPartition(...)`
- `saveAcceptedArticles(...)`
- `saveNeedsHumanReviewArticles(...)`

这解释了为什么另一条 job 会失败在：

- `review_articles`

说明问题不是某个节点独有，而是：

- **所有“节点主逻辑跑完后，结果写 Redis working set”的位置都可能中招**

### 不是主要候选：数据库进度心跳写入

`ArticleCompileSupport.touchProgress(...)` 和 `CompileGraphLifecycleListener.touchCurrentStep(...)` 最终写的是：

- `CompileJobJdbcRepository.updateProgressSnapshot(...)`
- `CompileJobJdbcRepository.updateCurrentStep(...)`

它们走数据库，不走 Redis。

因此：

- 如果失败源头是这些进度更新
- 不应该在 step 错误里看到 `RedisSystemException`

所以数据库进度写入不是最可能的直接爆点。

## 4. 发生在哪一层

最合理的分层判断是：

| 层 | 判断 |
| --- | --- |
| 业务层 | Writer / Reviewer 主逻辑本身大概率已完成，不是业务规则错误 |
| 状态保存层 | **是最可能的直接爆点** |
| Redis 客户端层 | 抛出了 `RedisSystemException` |
| 线程/生命周期层 | **是最可能的根根因** |

换句话说：

- 表面异常在 Redis 客户端层
- 真正根因更像线程/生命周期中断
- 直接受害层是 compile working set 的 Redis 持久化

## 5. 是否和以下因素有关

### 应用 shutdown / restart

**高度相关。**

理由：

- local-dev 开着 devtools restart
- `CompileJobLeaseManager.destroy()` 会 `shutdownNow()`
- 历史 runtime 验证已经多次观测到 compile 中途 restart

这是当前最强的外部触发因素。

### devtools / local-dev lifecycle

**高度相关。**

这是本次判断里最可疑的环境因素。

尤其是：

- `src/main/resources` 被纳入 restart 监听

意味着本地开发期触发 restart 的机会本来就比生产更高。

### 异步线程池或 future cancel

**中度相关。**

当前明显能看到的 cancel / interrupt 源是：

- `ScheduledFuture.cancel(true)`
- `shutdownNow()`

它们不一定直接取消业务线程，但属于同一类“中断正在执行中的资源访问”的危险源。

### Redis key-value store 封装

**是直接受害层，但不是最像根根因。**

`AbstractRedisJsonStore` / `StringRedisKeyValueStore` 本身只是薄封装，没有复杂重试、熔断或防中断处理。

它的问题在于：

- 被中断时会直接把异常抛出来
- 上层没有降级或区分“工作集写入失败但主逻辑已完成”

### compile working set store

**高度相关。**

它是最具体的业务写入点。

尤其是：

- `saveDraftArticles(...)`
- `saveReviewedArticles(...)`
- `saveReviewPartition(...)`
- `saveAcceptedArticles(...)`
- `saveNeedsHumanReviewArticles(...)`

### 进度刷新 / 心跳写入

**不是最可能的直接爆点。**

原因：

- 这些走数据库
- 当前异常明确指向 Redis

## 6. 失败发生时，compile job 为什么还能部分推进

因为失败更像发生在：

- **节点主业务逻辑成功之后**
- **节点结果回写或状态推进之前**

以 `compile_new_articles` 为例：

1. Writer 循环已经跑完
2. 最后一次 `touchProgress(...)` 已写入“Writer 草稿生成完成”
3. 但 `saveDraftArticles(...)` 写 Redis working set 时被中断
4. 节点抛异常
5. Graph lifecycle 记录 step failed
6. compile job 最终被标记 FAILED

所以用户会看到一种很别扭的状态：

- 进度看起来像“已经做完了”
- 但 job 最终是 FAILED

这和“主计算完成，状态回写失败”非常一致。

## 7. 为什么最终 `status=FAILED`

因为 compile graph 节点是 fail-fast 的：

- 节点执行时只要抛 `RuntimeException`
- Graph 会进入 `onError(...)`
- `compile_job_steps` 会标记当前 step failed
- `compile_jobs` 聚合状态也会记为 FAILED

当前代码里没有“Redis working set 写失败但主逻辑继续推进”的兜底语义。

所以：

- 即使 Writer 本身已经完成
- 只要 working set 写失败
- 整个 job 还是会 FAILED

## 8. 这类失败是否会同时影响 Writer 后和 Reviewer 后状态推进

**会。**

因为两处都共享同一个模式：

- 节点主逻辑完成
- 再把结果写 Redis working set

典型位置：

- `CompileNewArticlesNode.execute()` -> `saveDraftArticles(...)`
- `ReviewArticlesNode.execute()` -> `saveReviewedArticles(...)` / `saveReviewPartition(...)` / `saveAcceptedArticles(...)` / `saveNeedsHumanReviewArticles(...)`
- `FixReviewIssuesNode.execute()` -> `saveReviewedArticles(...)`

这解释了为什么同类异常能分别落在：

- `compile_new_articles`
- `review_articles`

## 9. 最小安全修复范围

只建议一个最小修复切口：

- **`StringRedisKeyValueStore.set(...)` 及其上层 `AbstractRedisJsonStore.saveJson(...)` 的中断语义处理**

更具体地说，最小安全修复范围建议是：

- [StringRedisKeyValueStore.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/StringRedisKeyValueStore.java)
- 必要时配套 [AbstractRedisJsonStore.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/infra/redis/AbstractRedisJsonStore.java)

为什么这里是最小切口：

1. 所有 compile working set Redis 写入最终都汇到这里
2. 不需要改 Writer / Reviewer / graph 业务逻辑
3. 不需要扩散到多处 node 分别兜底
4. 可以先把：
   - “线程被中断”
   - “Redis 生命周期结束”
   - “连接关闭中的 set/get”
   做更清楚的异常分类或受控降级

### 不建议的更大切口

当前不建议先动：

- `CompileNewArticlesNode`
- `ReviewArticlesNode`
- `FixReviewIssuesNode`
- `CompileJobLeaseManager`

这些都更像第二层修复范围。

先在 Redis store 把异常类型钉清、传播语义收紧，是更小更安全的第一刀。

## 10. 是否阻塞当前 payload slimming 提交

**不阻塞。**

更新判断如下：

- 它是当前真实环境里的活跃失败信号，这点成立
- 但从现有证据看，它**不是** `Reviewer payload slimming` 直接引入的回归
- 它更像 local-dev lifecycle / interrupt + Redis working set 写入的独立问题

因此：

- **阻塞当前运行环境健康判断**
- **但不阻塞 payload slimming 这组代码本身的提交**

换句话说：

- 这应作为下一条独立故障线处理
- 不应把 payload slimming 和 Redis interrupted 绑成同一回归

## 11. 下一轮建议交给哪个 agent

建议交给：

- **agentA**

理由：

- 根因已经收敛到很窄的基础设施写入层
- 适合做一个最小代码修复
- 不需要再让 agentB 继续扩读新的性能主题

## 12. 本轮是否修改代码

否。

本轮只做只读根因分析，未修改任何代码、测试、前端、后端或数据。
