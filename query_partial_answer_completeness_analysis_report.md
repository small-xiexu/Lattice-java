# Query PARTIAL_ANSWER 完整度分析报告

## 结论

当前 `PARTIAL_ANSWER` 的主问题，不像 retrieval 召回断层，更像：

- **证据够，但答案没有把多点信息完整展开**

也就是说，当前更主要的是：

- 漏列点
- 漏原因
- 漏步骤
- 漏边界条件
- citation 足够，但回答覆盖不够完整

最值得先修的一个小点，我建议放在：

- **answer generation 的“多点答案展开约束”**

更具体地说，是让 answer generation 在 evidence 已足够时：

- 更稳定地枚举多点信息
- 不把多条证据压扁成一句摘要

而不是先回头改 retrieval。

## 1. 当前 PARTIAL_ANSWER 的主要类型

从 [phase_compile_query_stage_acceptance_report.md](/Users/sxie/xbk/Lattice-java/phase_compile_query_stage_acceptance_report.md) 可以直接看到一个典型样本：

- 问题：`RRF 是什么？`
- `answerOutcome = PARTIAL_ANSWER`
- `generationMode = LLM`
- `reviewStatus = PASSED`
- `citationCheck.verified = 6`
- `coverageRate = 1.0`

这类信号很关键：

- 回答不是胡说
- citation 也不是缺
- evidence coverage 也够
- 但仍是 `PARTIAL_ANSWER`

因此当前 `PARTIAL_ANSWER` 最主要的类型不是“没找到证据”，而是“回答不够展开”。

结合题集结构和现有 query 验收口径，当前可归纳为 5 类：

### 1. 漏列点

最常见。

表现：

- 问题本质上是一个多点答案
- 证据里有 2-4 个并列要点
- 最终答案只说了其中 1-2 个

### 2. 漏原因

表现：

- 回答给了结论
- 但没有补“为什么是这样”
- 证据中存在解释性段落，却没被表达出来

### 3. 漏步骤

表现：

- 回答知道主题是什么
- 但没有按过程/顺序说完整
- 常见于流程型或操作型问题

### 4. 漏边界条件

表现：

- 给出一般结论
- 但没说明适用前提、限制条件、例外情况

### 5. 漏对比项

表现：

- 问题隐含“X 和 Y 的区别/联系”
- 回答只解释了一侧
- 或者没有把对照维度列齐

## 2. 哪些更像 retrieval 问题，哪些更像 answer generation 问题

### 更像 retrieval 不足的情况

只有当出现下面这些信号时，我才会优先怀疑 retrieval：

- sourceCount 很低
- citationCoverage 明显不足
- 引用只来自单一片段
- expected point 在 source 中存在，但没进前端最终 evidence

但就当前阶段报告来看，最显眼的 `PARTIAL_ANSWER` 例子并不符合这一类：

- `verified=6`
- `coverageRate=1.0`

所以当前不是 retrieval 先背锅。

### 更像 answer generation 不完整的情况

当前主流 `PARTIAL_ANSWER` 更像这一类。

典型特征：

- evidence 召回够
- citation 也够
- 模型给了正确方向
- 但答案只覆盖部分 expected points

这说明：

- evidence 在
- 但 generation 没展开完

## 3. 哪些更像 prompt 问题，哪些更像 evidence selection 问题

### 更像 prompt / generation 约束问题

当前更主要的是这一类。

原因：

- 题目里很多是“解释 / 枚举 / 对比 / 多点说明”型问题
- LLM 很容易在 evidence 足够时仍然产出一个“正确但压缩”的答案
- 现有 AnswerPromptBuilder 明确要求：
  - `answerOutcome`
  - citation
  - 不编造
  - 结构化 JSON

但没有看到非常强的“如果问题天然是多点问题，必须尽量枚举完整要点”这一层约束。

所以当前更像：

- prompt 对“完整度”的约束不够强

### 更像 evidence selection 问题的情况

不是没有，但优先级稍低。

如果 evidence selection 有问题，通常会表现为：

- 某个关键点明明在 source 中，但没有进入最终 answer context
- citation 看起来足够，但其实总是同一段被反复引用

当前阶段报告里没有足够多的强证据表明：

- `PARTIAL_ANSWER` 主要是 evidence selector 把关键点漏掉了

反而更像：

- evidence 有了，但答案没把它写出来

## 4. 当前最值得先修的一个小点

我建议下一轮最值得先修的一个小点是：

- **增强 answer generation 对“多点答案”的完整展开约束**

这比直接改 retrieval 更稳，也比大改 evidence selection 风险更小。

### 为什么是它

因为当前最典型的信号是：

- `PARTIAL_ANSWER`
- `generationMode = LLM`
- `reviewStatus = PASSED`
- `citationCoverage = 1.0`

这组信号几乎就在说：

- “模型看到了证据，也答对了方向，但没答完整”

### 为什么不是先修 retrieval

因为当前没有足够证据表明：

- retrieval 没把关键证据找回来

相反，已有证据更支持：

- retrieval/citation 已经足以支撑答案

### 为什么不是先修 citation

因为 citation 已经够了。

当前问题不是“没引用”，而是：

- “引用了，但没把信息讲完整”

## 5. 下一轮建议交给哪个 agent

建议交给：

- **agentA**

但前提是：

- 只修一个最小点
- 不回到 compile 主线
- 不顺手碰 retrieval / citation / fallback / eval

如果还想更保守一步，也可以先让 agentB 再补一轮只读 case mapping，把具体 `PARTIAL_ANSWER` case 列成“漏列点 / 漏原因 / 漏步骤 / 漏边界 / 漏对比”清单；但就当前阶段判断，已经足够给 agentA 一个小切口了。

## 6. 本轮是否修改代码

否。

本轮只做只读分析，未修改任何代码、测试、配置、脚本或数据库。
