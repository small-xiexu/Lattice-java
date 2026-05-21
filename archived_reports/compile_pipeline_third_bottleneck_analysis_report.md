# Compile Pipeline 第三大瓶颈分析报告

## 结论

在：

- Writer 单元数收紧
- Reviewer payload 变轻

之后，当前 compile pipeline 最值得先修的第三刀是：

- **Writer 单次生成成本**

更具体地说，是：

- **Writer prompt / source payload 体积仍然偏大，且缺少 Reviewer 那样的总量约束**

这不是回到第一刀重做，而是针对 Writer 剩余成本继续下探：

- 第一刀解决了“Writer 调多少次”
- 第三刀要解决“每次 Writer 还太重”

## 1. 当前第三大瓶颈是什么

当前第三大瓶颈是：

- **Writer 阶段的单次 LLM 生成成本**

### 直接证据

来自 [compile_writer_unit_routing_gate_full_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_writer_unit_routing_gate_full_runtime_verification_report.md)：

- Writer 总耗时：约 **6.0 min**
- Reviewer 总耗时：约 **4.5 min**
- Fixer：**0**
- Synthesis + finalize：约 **1.5 min**

来自 [compile_reviewer_payload_slimming_runtime_verification_report.md](/Users/sxie/xbk/Lattice-java/compile_reviewer_payload_slimming_runtime_verification_report.md)：

- Writer 总耗时：约 **10.3 min**
- Reviewer 总耗时：约 **2.7 min**
- Fixer：**0**
- Synthesis + finalize：约 **1.3 min**

虽然两轮 Writer 总耗时受缓存影响不能直接横比，但有一点非常稳定：

- Reviewer 经过第二刀后已经明显下降
- Fixer / re-review 当前没有触发
- synthesis 是并行固定尾部
- **剩余最大头重新回到了 Writer 本身**

所以“第三刀”不是去找一个新阶段替代 Writer，而是继续压 Writer 的剩余单次成本。

## 2. 为什么它比其他候选更值得先修

### 比 review/fix loop 更值得

当前 fix loop 还不是主要放大器。

原因：

- 两轮真实 runtime 都没有触发 Fixer
- [ReviewDecisionPolicy.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/graph/ReviewDecisionPolicy.java) 只有在：
  - non-pass
  - 有 issue
  - `autoFixEnabled=true`
  - `fixAttemptCount < maxFixRounds`
  时才进入 `fix_review_issues`
- 当前默认 `maxFixRounds=1`，而且 fixable issue 没自然出现

结论：

- review/fix loop 是潜在长尾放大器
- 但不是“当前基线下最该先修的第三刀”

### 比 synthesis artifacts 更值得

[SynthesisArtifactsService.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/SynthesisArtifactsService.java) 的特征是：

- 固定 4 类 artifact
- `newFixedThreadPool(4)` 并行生成
- 发生在主文章链路之后

而当前 runtime 里它只表现为：

- 约 **1.3 分钟** 的尾部耗时

相比之下，Writer 仍是：

- 多次调用
- 每次 70–150 秒级
- 总量显著高于 synthesis

结论：

- synthesis 是 P1/P2
- 不是第三刀优先项

### 比 vector refresh 更值得

vector refresh 主要是 embedding / 索引维护：

- 不是最重的生成型 LLM 阶段
- 也没有在现有 runtime 报告里表现出接近 Writer / Reviewer 的量级

因此不应优先于 Writer 单次成本。

### 比“先换模型 / 先并发”更值得

这一点很关键。

#### 不是先换模型

因为：

- Writer 仍是知识生成主链
- 模型更换会直接碰内容质量
- 现在更大的问题是“输入太重”，不是“模型一定选错了”

#### 不是先调并发

因为：

- 并发解决的是“同时跑多少个”
- 当前第三刀面对的是“单次 Writer 仍太贵”
- 如果 prompt 不瘦，并发只是在更快地发更多重请求

结论：

- 先减单次 Writer 成本，比先换模型 / 先提并发更稳

## 3. 为什么判断是 Writer prompt / payload 体积，而不是别的

从代码上看，Writer 和 Reviewer 现在已经不对称了。

### Reviewer 现在已有总量约束

第二刀之后，Reviewer / Fixer 走：

- `buildReviewSourceContents(...)`
- `REVIEW_SOURCE_PAYLOAD_MAX_CHARS = 9000`
- `REVIEW_SOURCE_PER_SOURCE_MAX_CHARS = 4000`
- `sourceRef` 优先 + 相关章节回退

也就是：

- 总量有上限
- 单 source 有上限
- 内容选择是相关片段优先

### Writer 还没有同等级的总量约束

在 [CompileArticleNode.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java)：

- `buildCompilePrompt(...)` 会追加全部 `Structured concept sections`
- 然后对每个 source path：
  - `selectRelevantContent(...)`
  - 每个 source 最多 `4000` 字符

但问题在于：

- **Writer 没有 Reviewer 那样的总 payload 上限**
- source 数一多，prompt 体积就线性涨
- structured sections 也是整体追加，没有总预算控制

所以当前最明显的不对称是：

- Reviewer 已经做了 payload slimming
- Writer 还没做总量预算 slimming

这就是第三刀最自然的落点。

## 4. 哪些阶段仍然存在“低价值内容进入 LLM”的问题

当前最明显的仍是 Writer：

1. 多 source concept 时，每个 source 仍可能带入 4k 片段
2. structured sections 会整体拼进 prompt
3. overview concept 虽然把 unit 数降下来了，但它本身仍可能携带较大的综合上下文

这类问题不再是“太多 unit 进入 Writer”，而是：

- **单个 writer unit 仍带入过多上下文**

synthesis 也有“低价值 LLM 调用”的特征，但它：

- 固定只有 4 次
- 还是并行

所以性价比不如继续压 Writer。

## 5. 最小安全修复范围

最小安全修复范围建议只落在：

- [CompileArticleNode.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java)

必要时配套：

- `CompileArticleNode` 对应测试
- 现有 compile flow / runtime 测试补充

建议只做一种类型的改动：

- **给 Writer prompt 增加总量预算与更稳的内容裁剪策略**

而不要同时做：

- 模型切换
- 并发调整
- synthesis 关闭/改路由
- review/fix 策略调整

这样才能保证第三刀仍然是单变量。

## 6. 下一轮建议交给哪个 agent

建议交给：

- **agentA**

原因：

- 当前第三刀的目标已经明确
- 修复范围可以压到 `CompileArticleNode` 一个主文件
- 属于典型的“一个明确根因 -> 一个最小代码修复”

更具体地说，下一轮 agentA 适合做的是：

- Writer payload / prompt budget slimming

而不是扩到其他阶段一起修。

## 7. 本轮是否修改代码

否。

本轮只做只读性能分析，未修改代码、配置、prompt、数据库或测试。
