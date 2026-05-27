# Q6 exact path sibling 根因分析报告

## 1. 本轮结论一句话

Q6 仍然选错 sibling 字段，不是因为正确 fact card 没进来，而是因为 `buildExactPathConclusionLines` 只做“结构化路径外形 + 问题焦点 token”的候选筛选与打分，没有把“问题字段语义”显式绑定到“结构化路径终端字段”，于是同一 fact card 内多个 sibling 里，`periodSeconds` 这类同样带 `readinessProbe`、数值、赋值形态的候选能够和 `tcpSocket.port` 同台竞争并被先选中。

## 2. 关键调用链

1. `AnswerFallbackEvidenceSelector.selectFallbackEvidenceHits` 先把 `SOURCE + ARTICLE + FACT_CARD` 互补补齐；前序复验已确认 fact card 9 已进入最终 fallback 证据上下文。
2. `AnswerFallbackConclusionBuilder.buildEvidenceConclusionLines` 先走 comparison / exact path / aggregation 等分支。
3. 对 Q6 这类显式路径题，`AnswerGenerationReferencePathSupport.buildExactPathConclusionLines` 被调用。
4. `buildExactPathConclusionLines` 内部先调用 `selectExactPathCandidateLines(question, fallbackHit, queryTokens)`。
5. `selectExactPathCandidateLines` 先拿 `selectQuestionFocusedFallbackSnippets(..., limit=3)` 的结果，再按路径契约补扫少量额外行。
6. `selectQuestionFocusedFallbackSnippets` 走 coverage-aware structured fact 选择：先按高信号 token 命中，再按 `number/status/ordinal/path/rule/change/flow/identifier` 的 shape 补位。
7. 其中 `path` shape 的优先逻辑是 `addBestCandidateForRequiredShape(..., "path")`。
8. 最终结论来自 `appendAggregatedConclusionLine`，先选中一个 primary match，再补 companion。

## 3. 候选排序/选取逻辑梳理

`selectQuestionFocusedFallbackSnippets` 的排序顺序是：

1. 先按 `QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)` 提取的问题高信号 token，逐个找第一个命中的候选。
2. 再依次补 `number -> status -> ordinal -> path -> rule -> change -> flow -> identifier` 的 shape 候选。
3. 再补不同机器标识符。
4. 最后把其余未覆盖候选按原排序补齐。

对 `path` shape，`addBestCandidateForRequiredShape` 里虽然已经加了一个“question-focused structured path value candidate”优先分支，但这个优先分支只判断：

- 候选是不是结构化路径元数据字段；
- 值里是不是 dotted field path / assignment-like mapping；
- 候选是否覆盖问题中的结构化路径焦点 token。

它没有判断“这个候选是不是叶子字段，还是同父级 sibling 字段”。

进入 `buildExactPathConclusionLines` 后，候选再做一次门槛过滤：

- 必须包含 `containsPathSignal`，或 path contract signal；
- 不能是 path header；
- 如果问题显式点名路径标识，还要排除引入了未请求路径的候选；
- 然后按 `scoreQuestionFocusedFallbackLine` 评分选分最高者。

这个评分函数对 Q6 这种结构化查值题，虽然给了 `looksLikeQuestionFocusedStructuredPathValueCandidate` 很高的加分，但它仍然只看：

- 结构化路径元数据；
- assignment 形态；
- 数值形态；
- 问题 token 覆盖；
- `readinessProbe` / `path` / `label` 之类通用形态。

它并没有任何“终端字段语义”规则来区分 `port` 和 `periodSeconds`。

## 4. sibling 字段误选的直接原因

直接原因不是“正确证据缺失”，而是同一 fact card 内多个候选的评分都能过线，且 sibling 字段没有被通用降权：

- `periodSeconds = 10` 也是结构化路径值候选，满足 `looksLikeStructuredPathValueCandidate`。
- 它同样包含 `readinessProbe` 上下文，能吃到路径题加分。
- 它同样是数值赋值，能吃到 `containsNumericAssignmentSignal` / `normalizedLine.matches(".*\\d.*")` 这类通用加分。
- `buildExactPathConclusionLines` 的筛选只要求“像 path”，没有要求“终端字段语义必须匹配问题的 port/端口 意图”。

所以 sibling 字段并不是被“强行优先于目标字段”的，而是它在当前规则里没有被识别成需要降权的 sibling，结果在候选池里和目标字段一起竞争，最终被选成 primary match。

## 5. 为什么不是上游问题

不是资料缺失：

- 目标 source chunk 存在，包含 `readinessProbe`、`tcpSocket`、`port: 8080`。
- 目标 fact card 9 存在，包含 `fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`。

不是检索召回问题：

- 复验里 source chunk rank 1，fact card 9 rank 2，已经进了 fused 前列。
- complementary evidence gate 修复后，fact card 9 已进入最终 fallback 证据上下文，不再被 early return 屏蔽。

不是 complementary selector 问题：

- 上一轮已确认 fact card 9 进入最终 `fallbackHits` / 参考说明。
- 当前错误发生在 `buildExactPathConclusionLines` 之后，而不是 `selectComplementaryEvidenceByQuestionTokens` 之前。

不是 citation binding 首要问题：

- citation 只是对错误 claim 做了同源弱验证，说明它能引用到源文件，但不能解释为什么选成了 sibling。
- 错误在 citation 前已经形成。

## 6. 为什么不是 case 特判

这不是 Q6 文件名、题面、端口值、Kubernetes 术语、`readinessProbe` 的特判能修好的东西。原因很简单：

- 当前问题暴露的是“结构化路径终端字段语义未建模”的通用缺口。
- 如果只给 Q6 加白名单或字段名分支，别的 YAML / JSON / properties 结构化查值题还会继续出现 sibling 误选。
- 当前实现已经证明，`port`、`periodSeconds`、`initialDelaySeconds` 这类 sibling 在通用规则下会共享同样的 `readinessProbe`、数值、赋值形态加分；要修的是统一的字段语义绑定，而不是某个样例。

## 7. 下一轮最小修复点建议

下一轮如果只修一个最小通用点，应该修 `buildExactPathConclusionLines` 这一层的候选排序/选择语义，而不是再改 selector、retrieval、prompt 或 citation。

最小通用修复边界应是：

- 给 exact path 结论候选增加“终端字段语义”判别；
- 让结构化路径候选不只比较父级路径覆盖，还要比较末级字段是否和问题焦点一致；
- 对同一父级下的 sibling 字段做通用降权，而不是把所有 `path` 形态都当成等价答案。

这属于通用结构化路径字段语义绑定，不是硬编码，因为它只依赖：

- 问题里的通用字段焦点 token；
- 候选里的结构化路径元数据；
- 候选末级字段与问题焦点的通用匹配关系；
- sibling / 终端字段的通用相对优先级。

## 8. 当前不应修改的范围

本轮不应动：

- `src/main/resources/**`
- `scripts/**`
- `src/test/java/**`
- retrieval / RRF / rerank / citation / prompt / LLM binding
- fact card 生成链路
- redline allowlist
- 清库 / 重导 / 重建向量
- 任何面向 Q6 文件名、题面、端口值、Kubernetes 专属概念、`readinessProbe` 的生产特判

## 9. 风险与待验证点

1. 目前证据足以确认“结构化路径终端字段语义没被显式建模”，但还不能从只读分析直接断定该点应该落在“exact path conclusion”还是“path shape candidate selection”的哪一个最小函数里；两者的耦合很紧。
2. `selectQuestionFocusedFallbackSnippets` 已经给了 sibling 和目标字段同样的路径/数值奖励，说明即便下一轮在 exact path 结论层修正，仍要保留对 sibling 的回归测试，避免问题从 `image` 或 `periodSeconds` 迁移到另一个同父级字段。
3. 当前 `extractQueryTokens` / `extractHighSignalTokens` 能提到 `port`，但没有把它升格成“叶子字段语义约束”；这意味着真正的修复必须明确把“端口”映射为字段终端语义，而不是只增加更多 token。
4. 目前没有证据支持再往上游扩到 retrieval、RRF、fact card 生成或 complementary selector；继续往上改只会扩大变量，不会更接近根因。
