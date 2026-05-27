# Compile Review/Fix Loop 性能分析报告

## 结论

当前 compile 中，一旦进入 `fix_review_issues`，**review/fix loop 已经会成为新的主要性能瓶颈**。

它的慢，不是单一某个点，而是三个因素叠加：

1. **Reviewer 已经把问题筛得很严，容易把一批 article 一次性送进 Fixer**
2. **Fixer 是逐篇串行的大模型调用，且每篇输入都很重**
3. **Fixer 之后还要 re-review，天然形成“再来一轮 Reviewer”的放大链**

如果下一轮只修一个最小点，我建议先修：

- **Fixer payload slimming**

也就是把 Fixer 的输入进一步瘦身，而不是先改轮次、先换模型、先碰 Query。

## 1. review/fix loop 是否已成为主要瓶颈

**在未触发 loop 的 job 上，不是。**

**一旦触发 loop，就是。**

### 证据一：历史基线里 loop 不触发时，Fixer 为 0

从现有运行时报告看：

- [compile_writer_unit_routing_gate_full_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_writer_unit_routing_gate_full_runtime_verification_report.md)
- [compile_reviewer_payload_slimming_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_reviewer_payload_slimming_runtime_verification_report.md)
- [compile_writer_payload_budget_slimming_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_writer_payload_budget_slimming_runtime_verification_report.md)

这些样本里共同点都是：

- Reviewer 在跑
- Fixer **未触发**
- re-review **未触发**

因此前几轮我们看到的瓶颈主要还在：

- Writer
- Reviewer

### 证据二：当前真实 job 已进入 Fixer，且规模不小

当前数据库里最新真实 job：

- `job_id = eb9d2a90-6b33-4e41-a90f-5cb68877c373`

从 `compile_job_steps` 可见：

- `review_articles` 已完成
- `conceptCount=8`
- `acceptedCount=1`
- `pendingReviewCount=7`
- `needsHumanReviewCount=0`
- 随后进入：
  - `fix_review_issues`
  - `progress_current = 1`
  - `progress_total = 7`

这说明：

- 8 篇里有 7 篇一次性进入 Fixer
- Fix loop 从“偶发尾巴”变成了“主流程大头”

在这种 job 上，Fixer 就不再是长尾，而是主体耗时。

## 2. 慢的具体放大机制

### 2.1 Reviewer 会把大量 non-pass article 一次性推给 Fixer

从 [ReviewDecisionPolicy.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/ReviewDecisionPolicy.java)：

- non-pass
- `autoFixEnabled=true`
- `fixAttemptCount < maxFixRounds`
- `hasIssues(reviewedArticle)`

就会进入 `fix_review_issues`

当前真实 job 的 step summary 已经说明：

- `pendingReviewCount=7`

也就是 Reviewer 一轮之后，直接把 7 篇文章丢给了 Fixer。

### 2.2 Fixer 是逐篇串行调用

从 [ArticleCompileSupport.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java)：

- `fixReviewedArticles(...)` 是按 `reviewedArticles` for-loop 逐篇执行
- 每篇都：
  - `buildReviewSourceContents(...)`
  - `fixerAgent.fix(...)`

所以只要有 7 篇进入 Fixer，就是：

- 7 次串行 LLM 调用

### 2.3 Fixer 输入仍然偏重

从 [ReviewFixService.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ReviewFixService.java)：

- 会把 `issueList`
- 原始文章全文
- source 内容

一起发给 LLM

虽然 source 内容已经复用了 Reviewer slimming 之后的 `buildReviewSourceContents(...)`，但 Fixer 额外还带了：

- 最多前 5 条 issue
- 整篇原始文章

所以单次 Fixer 通常会比 Reviewer 更重。

### 2.4 Fixer 后还要再跑一轮 Reviewer

图定义里：

- `fix_review_issues -> review_articles`

也就是说，只要 Fixer 触发，就天然多出：

- 一轮 Fixer
- 一轮 re-review

这会把总体耗时从：

- `Writer + Reviewer`

放大成：

- `Writer + Reviewer + Fixer + Reviewer`

### 2.5 某类文档更容易触发循环

从当前真实 job 的 sourceDir：

- `/tmp/lattice-multi-point-verify-src-small`

以及 `review_articles` 的结果：

- 8 篇里 7 篇 non-pass

可以推断：**多点校验 / 多条明确性知识密集的文档**，更容易触发 loop。

原因：

- Reviewer prompt 很强调：
  - referential completeness
  - exact values
  - unsupported exact values
  - provenance sampling

这类文档天然更容易被挑出“遗漏了一些点”。

## 3. “未发现需要修复的问题”更像文案问题还是状态映射问题

更准确地说，它是：

- **状态映射问题**

而不是单纯文案问题。

### 为什么

当前这句来自：

- [AdminCompileReviewSummaryService.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/admin/service/AdminCompileReviewSummaryService.java)

逻辑是：

1. `needsHumanReviewCount > 0` -> `质量检查后需要人工确认`
2. `fixStepPresent` -> `已根据检查结果修正内容`
3. `hasNoReviewIssue(summary)` -> `未发现需要修复的问题`
4. 否则 -> `质量检查已完成`

问题在于：

- 这段逻辑本质上是在用 **review step 的摘要** 去猜 **整个后续流程语义**
- `未发现需要修复的问题` 只能表达：
  - “当前没有进入 fix step”
- 但普通用户会理解成：
  - “后面不会再修 / 事情已经结束”

所以它的问题不只是措辞不自然，而是：

- **用 step 级观测，错误表达成了流程级结论**

### 当前更准确的分类

这不是 compile 状态机真的错了，而是：

- **展示语义问题**

但它会放大性能体感问题：

- 用户看到“未发现需要修复的问题”
- 结果任务还在继续跑很久
- 就会以为系统卡住了

所以这是：

- **展示语义问题 + 性能问题的混合问题**

## 4. 当前更像性能问题、状态机问题、展示语义问题，还是混合问题

我的判断是：

- **混合问题**

### 性能问题

真实存在，而且核心就是：

- Fixer 串行慢
- re-review 追加慢
- 多篇文章一旦一起进 loop，总耗时暴涨

### 状态机问题

不是主问题。

当前状态机本身很清楚：

- review
- fix
- re-review
- persist / needs_human_review

流程并没有乱。

### 展示语义问题

是。

尤其是：

- “未发现需要修复的问题”

这句很容易让人误解流程已经不会再修，但实际上它只是某一轮 step 摘要。

### 综合判断

所以最好把它理解为：

- **主问题是性能**
- **被展示语义放大成“像卡住”**

## 5. 下一轮最值得先修的一个点

我建议下一轮最值得先修的是：

- **Fixer payload slimming**

### 为什么是它

因为当前真实 job 已经说明：

- 7 篇文章直接进入 Fixer

在这种情况下：

- 调低 `maxFixRounds`
- 或直接改状态展示

都不能解决真正的耗时。

而 Fixer 的单次成本是最有机会下降的，因为它现在每篇都带：

- 问题列表
- 整篇文章
- source 证据

只要把 Fixer 输入进一步瘦下来，就能同时改善：

- 当前 Fixer 耗时
- 随后的 re-review 触发整体窗口

### 为什么不是先修显示文案

因为改文案只能改善“用户以为卡住”的感受，不能减少真实等待时间。

### 为什么不是先修 `maxFixRounds`

因为当前默认：

- `maxFixRounds = 1`

本来就不高。

当前痛点不是轮次太多，而是：

- 一次 Fixer 就已经很贵

### 为什么不是先换模型 / 并发

不优先。

原因：

- 换模型先碰质量风险
- 并发只是在更快地发更多重 Fixer 请求
- 当前单次 payload 成本还没压到足够低

## 6. 下一轮建议交给哪个 agent

建议交给：

- **agentA**

前提是保持单变量修复：

- 只修 Fixer payload slimming
- 不顺手改 Reviewer
- 不顺手改展示语义
- 不顺手调轮次配置

## 7. 本轮是否修改代码

否。

本轮只做只读性能/链路分析，未修改任何代码、测试、配置、脚本或数据库。
