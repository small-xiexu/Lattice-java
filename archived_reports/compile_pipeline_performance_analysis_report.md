# Compile Pipeline 性能分析报告

## 总体结论

当前 compile 慢，主因不是单一“模型慢”，而是：

1. **LLM 阶段占了绝大多数总耗时**
2. **Writer / Reviewer / Fixer / re-review 是按文章或概念单元重复调用的**
3. **很多内容仍走“逐单元叙述型生成”路径，而不是更激进的结构化分流**
4. **StateGraph 闭环天然会把单篇文章放大成多次 LLM 往返**

如果只看“哪一个点最值得先修”，我建议优先修：

- **减少进入 Writer 的内容单元数量，也就是优化内容路由 / 分流 gate**

比起先换模型、先调并发、先压 prompt，这个点更可能同时降低：

- 调用次数
- 总耗时
- 总成本
- 用户等待体感

## 1. compile 全链路阶段与耗时重心

当前 compile 主链阶段是：

1. 资料接收
2. Writer
3. Reviewer
4. Fixer
5. re-review
6. synthesis artifacts
7. refresh vector index

其中真正重的，是 2-5。

### 资料接收

主要是：

- staging
- materialize
- source/run/job 记录

这部分通常不是性能主瓶颈，更多是秒级。

### Writer

这是**第一大耗时源**。

原因：

- 每个 writer unit 都要单独喂给 LLM
- Writer 使用的是强模型路线
- prompt 会带：
  - merged concept 信息
  - structured concept section
  - selected source content
- 同一批资料会被拆成多个 merged concept / writer unit

只要 writer unit 数量一大，总时长会线性拉长。

### Reviewer

这是**第二大耗时源**。

原因：

- 默认新 job 已改为 `LLM` review mode
- 每篇 Writer 结果都要再过一遍 Reviewer
- Reviewer prompt 还会要求检查 exact value / citation / source grounding

即使 Reviewer 单次比 Writer 短，它也会对每篇文章再加一跳。

### Fixer

这是**波动型大头**。

在无 fixable issue 时为 0；一旦触发，就会显著放大总耗时。

原因：

- Fixer 也是 LLM 调用
- Fix 后还要回到 Reviewer 再审一次
- 最大轮数越高，最坏路径越长

### re-review

本质上是 Reviewer 重复调用。

一旦进入 fix loop，总耗时就不再是：

- Writer + Reviewer

而会变成：

- Writer + Reviewer + Fixer + Reviewer (+ ...)

### synthesis artifacts

这部分会额外走 LLM，但通常不是最核心瓶颈，除非：

- 文档很多
- synthesis artifact 数量多
- 每个 artifact prompt 很长

从链路位置看，它发生在主要文章编译结束之后，更像尾部长尾。

### refresh vector index

这部分不是主要瓶颈。

原因：

- embedding 虽然也走模型接口，但通常 token 和返回体都远小于 Writer / Reviewer
- 它更像批量索引维护，不是最重的生成型任务

## 2. 哪些阶段会调用 LLM，次数大致如何

### 明确会调用 LLM 的阶段

从代码路径看，以下阶段会走 LLM：

- `AnalyzeNode`
- `CompileArticleNode` / Writer
- `review_articles` / ReviewerAgent
- `fix_review_issues` / FixerAgent
- `generate_synthesis_artifacts`

其中高频主链是：

- Writer
- Reviewer
- Fixer
- re-review

### 调用次数特征

不是“每个 job 只调几次”，而是“按单元重复调很多次”：

- `AnalyzeNode`：通常按整批 source 做一次或少量次
- Writer：按 writer unit 一次一调
- Reviewer：按 article/draft 一次一调
- Fixer：只对需要修的 article 调
- re-review：只对被 fix 的 article 再调
- synthesis artifact：按 artifact 类型调

所以实际总调用数更接近：

```text
1 次 analyze
+ N 次 writer
+ N 次 reviewer
+ M 次 fixer
+ M 次 re-review
+ K 次 synthesis
```

其中：

- `N` = writer unit 数
- `M` = 进入 fix loop 的文章数
- `K` = synthesis artifact 类型数

真正把时间拖长的，通常是 `N` 和 `M`。

## 3. 是否存在串行调用过多

存在，而且这是当前慢的重要原因之一。

### 串行放大的结构

StateGraph 主链是：

```text
compile_new_articles
-> review_articles
-> fix_review_issues
-> review_articles
-> rebuild_article_chunks
-> refresh_vector_index
-> generate_synthesis_artifacts
```

风险不在图本身，而在：

- Writer 往往按 unit 串行生成
- Reviewer 往往按 article 串行审查
- Fixer 和 re-review 又把串行链路拉长

即使每次 LLM 调用都没问题，只要：

- 单次 30~90 秒
- 又有几十个单元

总耗时就会被拉到很难接受。

### 为什么并发不是第一修复点

并发当然重要，但它不是当前第一性问题。

如果当前本来就有太多“不该进 Writer 的内容”进入了 LLM，那么只加并发是在更快地做同样多的重活：

- 成本仍高
- reviewer/fixer 次数仍高
- 拥塞和限流风险更高

所以并发更像第二阶段优化。

## 4. structured table / overview / synthesis artifact 是否拖慢总耗时

### structured table

很可能曾经是大头，现在已有专项治理，但仍值得继续盯。

已知项目里已经做过：

- `StructuredTableWriterGate`

说明团队已经明确识别到：

- structured table / row 级内容不该大面积进入 Writer

这本身就是一个强信号：

- 过去慢，很可能就是因为结构化内容被错误路由到叙述型 article Writer

所以当前判断是：

- structured table 不一定还是最大瓶颈
- 但“结构化内容误入 Writer”这类问题，仍然是 compile 慢的根问题范式

### overview / synthesis artifacts

会拖慢，但通常不是第一刀该砍的点。

原因：

- 它们发生在主内容完成之后
- 调用次数通常少于 Writer / Reviewer
- 即使砍掉一点 synthesis，前面的 N 次 writer/reviewer 还在

结论：

- synthesis artifact 是 P1/P2 优化项
- 不是当前最值得先修的第一刀

## 5. prompt 体积是否明显过大

是，存在明显偏大的风险。

从代码看，Writer prompt 会拼接：

- concept title / summary
- structured concept sections
- selected source content

Reviewer prompt 又会带：

- draft article
- source grounding
- exact identifier / value 检查要求

再叠加 prompt externalization 后更完整的 grounding rules，单次 prompt 体积天然不小。

但这里要分清：

- **prompt 大** 是成本和单次时长问题
- **单元太多** 是总时长问题

如果先只压 prompt，而不减少 unit 数，compile 还是会慢，只是每次稍微短一点。

所以 prompt 体积是明确问题，但我不建议把它排成第一刀。

## 6. 是否存在不该进入 Writer 的内容也进入了 LLM

大概率存在，而且这是我认为最值得先修的方向。

已知信号：

- 项目已经单独做过 structured table writer gate
- 说明之前确实有“不该进 Writer 的结构化内容”进入叙述型生成链路
- `AnalyzeNode`、`MergeConceptsNode`、`CompileArticleNode` 这条路，本质上还是会把很多内容先抽成 concept，再交给 Writer

问题的本质是：

- compile 现在默认把很多“可结构化保留 / 可直接索引 / 可做 overview 聚合”的内容，也交给了昂贵的自然语言生成路径

只要这个入口没继续收紧，总会出现：

- 表格内容
- 明细行内容
- 低价值重复内容
- 本可合并的细粒度 concept

被逐个送进 Writer。

这就是最典型的设计级低效。

## 7. review/fix loop 是否存在过多轮次

存在潜在放大风险，但它更像“长尾放大器”，不一定是平均耗时第一因。

原因：

- 默认 LLM review mode 下，每篇文章至少要多一跳 Reviewer
- 一旦 fixable issue 出现，就会进入：
  - Fixer
  - re-review
- 如果 `max_fix_rounds` 配置偏高，总时长会指数式变差于用户体感

不过这件事的优先级仍低于“先减少进入 Writer 的单元数”，因为：

- fix loop 不是每篇都会触发
- writer/reviewer 次数则是基础盘，所有内容都会吃到

所以我判断：

- review/fix loop 是重要性能放大器
- 但不是当前第一刀

## 8. 是否存在明显的低效设计

存在，主要有 4 类。

### 低效设计 A：内容路由过宽

很多本应：

- 保持结构化
- 做 overview 聚合
- 直接进入检索证据层

的内容，仍被送入 Writer。

### 低效设计 B：按单元串行 LLM 往返

Writer / Reviewer / Fixer / re-review 以“单元 * 次数”线性放大。

### 低效设计 C：Prompt 携带内容偏重

Writer / Reviewer 都携带较重上下文，单次延迟高。

### 低效设计 D：后置产物生成继续占用 LLM

synthesis artifacts 在主任务尾部继续增加时长，但不是第一瓶颈。

## 9. 最应该先修的一个性能点

### 推荐：优化内容路由，进一步减少进入 Writer 的单元数量

这是下一轮最值得先修的一个点。

比起：

- 先换更快模型
- 先调并发
- 先压 prompt
- 先缩 reviewer/fixer

我更推荐：

- **继续收紧“哪些内容必须进 Writer”**

### 为什么是它

因为它能同时解决三件事：

1. 直接减少 Writer 调用次数
2. 间接减少 Reviewer / Fixer / re-review 次数
3. 同时降低 token 成本

也就是说，它不是单点优化，而是会连锁降低整个 LLM 闭环负担。

### 为什么不是先换模型

换更快模型当然可能见效，但风险更高：

- 可能伤内容质量
- 可能伤 reviewer 判断质量
- 可能引入新的稳定性问题

而内容路由收紧更符合当前治理方向：

- 不牺牲审查质量
- 不绕过 review gate
- 不碰模型配置

## 10. 下一轮建议交给哪个 agent 执行

建议交给：

- **agentB 先继续只读做一轮“进入 Writer 的内容单元归因分析”**
- 然后再由 **agentA** 做一个最小代码修复

如果这轮必须只选一个执行角色来推进下一步代码修复准备，我建议：

- **下一轮先交给 agentB**

原因：

- 当前最关键的是先把“哪些内容不该进 Writer”再归因清楚
- 这涉及 `AnalyzeNode / MergeConcepts / StructuredTable gate / CompileArticleNode` 的边界
- 还不适合直接让 agentA 下手改

等 agentB 把“最该收紧的单一入口”钉死后，再交给 agentA 做最小实现。

## 11. 优先级建议

### P0

- 收紧内容路由，减少进入 Writer 的单元数量

### P1

- 在不降质量前提下，压缩 Writer / Reviewer prompt 体积
- 评估 reviewer/fixer 触发条件和最大轮次是否过宽

### P2

- synthesis artifact 生成优化
- 适度并发优化
- 更快模型替换评估

## 12. 本轮是否修改代码

否。

本轮只做只读性能分析，未修改代码、配置、prompt、数据库或测试。
