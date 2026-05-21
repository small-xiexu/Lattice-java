# Compile Pipeline 第二大性能瓶颈分析报告

## 结论

Writer gate 第一刀之后，新的主要剩余耗时点是：

- **Reviewer 阶段**

更准确地说，不只是“Reviewer 也要调 LLM”，而是：

- **Reviewer 现在已经成为所有剩余 article 的统一串行重成本阶段**
- **Reviewer / Fixer 共用的源文本输入构造仍然偏重，放大了单次调用时长**

如果下一轮只允许修一个最小点，我建议交给 agentA 的第二刀是：

- **收紧 Reviewer 输入载荷：不要再把完整 source content 先拼起来再截断，而是改成更像 Writer 那样的相关片段选择**

这比先换模型、先调并发、先改 synthesis，更稳、更安全，也更符合当前 compile 治理方向。

## 1. Writer gate 之后新的主要耗时点

上一轮完整 runtime 验证已经给出非常直接的时间分布：

来自 [compile_writer_unit_routing_gate_full_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_writer_unit_routing_gate_full_runtime_verification_report.md)：

- Writer 全部完成：约 **6.0 分钟**
- Reviewer 全部完成：约 **4.5 分钟**
- Fixer：**未触发**
- finalize：约 **1.5 分钟**
- 总计：约 **12.4 分钟**

Writer gate 已经把：

- Writer 调用数
- Reviewer 调用数

从约 25 次一起压到了 6 次左右。

在这个新基线下：

- Writer 还是第一大头
- **Reviewer 已经成为最明确的第二大头**

而且 Reviewer 和 Writer 不同：

- Writer 的第一刀已经落下来了
- Reviewer 还没有对应的第二刀

## 2. 为什么 Reviewer 会变成新的主要放大器

### 2.1 每篇 article 都必须经过 Reviewer

在 [ArticleCompileSupport.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java:265) 里，`reviewDraftArticles(...)` 是按 article 顺序循环：

- 逐篇 `buildSourceContents(...)`
- 逐篇 `reviewerAgent.review(...)`

这意味着：

- 只要还有 6 篇 article
- 就一定还有 6 次 Reviewer LLM 调用

而且是串行的。

### 2.2 Reviewer 当前输入比 Writer 更“笨重”

Writer 在 [CompileArticleNode.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java:364) 已经会做：

- `selectRelevantContent(...)`
- `documentSectionSelector.select(..., 4000)`
- 优先按 `sourceRef`/section 抽相关片段

但 Reviewer / Fixer 走的是另一条路。

在 [CompileArticleNode.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java:241)：

- `buildSourceContents(...)` 会把所有 source path 对应的**整段全文**拼起来

然后：

- [ArticleReviewerGateway.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ArticleReviewerGateway.java:145) 把它硬截成前 **12000** 字符
- [ReviewFixService.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ReviewFixService.java:74) 把它硬截成前 **10000** 字符

这有两个问题：

1. 单次 prompt 仍然偏大
2. 还是“前缀截断”，不是“与当前文章最相关的证据截断”

所以 Reviewer 的剩余成本，不只是“有 6 次调用”，而是“这 6 次调用的输入也不够精瘦”。

### 2.3 overview article 也走同样重的 Reviewer 路径

Writer gate 把过度专题化长文档收敛成了一个 overview concept，这是对的。

但这个 overview article 生成后，仍会：

- 和普通 article 一样进入 Reviewer
- 读取同样风格的 source payload

这不代表应该跳过 Reviewer。

真正的问题是：

- **overview article 仍在用过重的 Reviewer 输入方式被审查**

## 3. review/fix loop 是否已经成为新的主要放大器

**还不是。**

理由很直接：

- 当前完整 runtime 样本中，Fixer 没有触发
- 也没有 re-review 额外轮次

从 [ReviewDecisionPolicy.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/ReviewDecisionPolicy.java) 看，只有：

- 非 pass
- 且有 issue
- 且 `autoFixEnabled=true`
- 且 `fixAttemptCount < maxFixRounds`

才会进入 `fix_review_issues`

所以：

- Fixer / re-review 是重要的**长尾放大器**
- 但不是当前这条基线下的第二大瓶颈

如果未来 fixable issue 变多，它当然可能升级成更大的问题；但在当前 Writer gate 之后的真实观测里，还轮不到它做第二刀。

## 4. 哪些内容虽然被挡掉了 Writer，但仍可能在 Reviewer 或 synthesis 阶段浪费时间

### Reviewer

被 Writer gate 收敛后的 overview article，仍会进入 Reviewer。

这本身没错，但会继续消耗：

- article 全文
- source full-text 拼接后的 12k 前缀

所以浪费不在“是否进入 Reviewer”，而在“Reviewer 仍吃到过大的原始输入”。

### synthesis artifacts

从 [SynthesisArtifactsService.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/SynthesisArtifactsService.java) 看：

- 默认会生成 4 类 artifact
- 用 `newFixedThreadPool(4)` 并行跑
- 输入是 `buildConceptSummary(...)`

这说明 synthesis 有两个特点：

1. 会调 LLM
2. 但调用数固定且并行

所以它会拖慢尾部，但不像 Reviewer 那样：

- 对每篇剩余 article 都重复一次
- 串行放大

结论：

- synthesis 不是第二刀

## 5. 是否存在“低价值 article 仍进入 Reviewer”问题

**存在，但我不建议把“跳过 Reviewer”作为第二刀。**

原因：

- compile review 现在是重要治理门禁
- overview article、普通 article、边界 article 默认都应被审查
- 直接减少 Reviewer 覆盖面，会碰质量与治理语义

所以更安全的第二刀不是：

- 跳过 Reviewer

而是：

- **保持 Reviewer 全覆盖，但缩小它看到的 source payload**

这样既保住审查门禁，也能减轻时延。

## 6. synthesis artifacts 是否值得作为第二刀

**不值得优先做第二刀。**

理由：

1. 它在主链后段
2. 它是并行的
3. 它的调用数固定，不会随 article 数线性放大

相比之下，Reviewer：

- 串行
- 每篇都要跑
- 还吃大输入

优先级明显更高。

## 7. prompt 体积是否已经足以成为第二刀

**是，但要说清楚：这里主要是 Reviewer / Fixer 的输入体积，不是先去改 system prompt 模板。**

当前更像是：

- payload construction 问题

而不是：

- prompt wording 问题

所以我不建议下一轮先动：

- `prompts/compiler/reviewer.md`
- `prompts/compiler/fixer.md`

去做大范围提示词改写。

更值得修的是：

- `buildSourceContents(...)`
- Reviewer/Fixer 输入证据选择方式

也就是把：

- “全文拼接 + 前缀截断”

改成：

- “相关片段选择 + 有界截断”

## 8. 第二刀最值得先修哪里

### 推荐结论

第二刀最值得先修：

- **Reviewer 输入瘦身**

### 更具体的最小修复方向

优先考虑的最小代码切口应落在：

- `CompileArticleNode.buildSourceContents(...)`
- `ArticleReviewerGateway.review(...)`
- `ReviewFixService.applyFix(...)`

目标不是改业务逻辑，而是改输入构造：

1. 不再把所有 source file 全文拼起来
2. 不再只取前 10000/12000 字符
3. 优先按：
   - source path
   - section/sourceRef
   - article 相关标题/小节
   选择更贴近当前 article 的证据片段

### 为什么它最值得

因为它能同时改善：

- Reviewer 单次时长
- Fixer 单次时长
- re-review 单次时长

而且：

- 不需要换模型
- 不需要改并发策略
- 不需要动 publish gate
- 不需要改 compile graph

## 9. 为什么不是先换模型 / 并发 / prompt

### 不是先换模型

原因：

- Reviewer 是治理门，质量风险高
- 换更快模型会先碰审查质量与 fail-closed 稳定性
- 当前更大的问题是“输入太重”，不是“模型一定选错了”

### 不是先调并发

原因：

- Reviewer 仍然会一篇一篇都调
- 如果单次 payload 不瘦，提并发只是更快地打更多重请求
- 成本、限流、排队压力都会先上来

### 不是先改通用 prompt 文案

原因：

- Writer / Reviewer / Fixer 的 system prompt 已经较稳定
- 当前更明显的重载来自动态 user payload
- 先改模板文案，收益不如先减输入体积直接

## 10. 下一轮建议交给哪个 agent

建议交给：

- **agentA**

前提是继续保持本轮的分析边界，只做一个最小代码修复：

- Reviewer / Fixer source payload slimming

如果还要先补一份更细的只读归因，也可以先让 agentB 再单独做一轮 reviewer payload 分析；但从当前证据看，已经足够给 agentA 下最小实现 prompt 了。

## 11. 本轮是否修改代码

否。

本轮只做只读性能分析，未修改任何代码、配置、prompt、数据库或测试。
