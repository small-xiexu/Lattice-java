# Query FALLBACK CITATION_QUALITY_INSUFFICIENT 根因分析报告

- 生成时间：2026-05-22
- 执行 Agent：agentB
- 任务类型：只读根因分析（未修改任何代码）
- 本轮是否修改代码：**否**

---

## 1. 核心结论

**当前所有 query 场景的 `generationMode=FALLBACK` + `fallbackReason=CITATION_QUALITY_INSUFFICIENT` 根因只有一个：**

> citation repair 循环在修复过程中将答案中的引用标记全部剥离，导致第二轮 citation check 判定 `noCitation=true` 或 `hasUsableCitationEvidence=false`，从而触发 `fallbackWhenCitationQualityIsInsufficient()` 将 LLM 合成回答替换为确定性证据列表。

**这不是 retrieval 问题，也不是 LLM 生成质量问题，而是 citation validation → repair → fallback 决策链的结构性问题。**

---

## 2. `CITATION_QUALITY_INSUFFICIENT` 在哪一层被判定

### 2.1 唯一定义位置

`CITATION_QUALITY_INSUFFICIENT` 字符串**只在唯一一个位置**被设置：

- 文件：`QueryFinalizationGraphFragment.java:291`
- 方法：`fallbackWhenCitationQualityIsInsufficient()`

```java
state.setFallbackReason("CITATION_QUALITY_INSUFFICIENT");
```

该常量不存在于 `AnswerGenerationBaseSupport` 的 `FALLBACK_REASON_*` 常量列表中（那些是 `LLM_CALL_FAILED`、`LLM_OUTPUT_INVALID`、`LLM_UNSTRUCTURED_FALLBACK` 等），它是一个**硬编码字符串**，仅在 citation check 阶段的 terminal fallback 路径中被写入。

### 2.2 触发条件链

`CITATION_QUALITY_INSUFFICIENT` 的触发需要经过以下**严格的 AND 条件**：

```
shouldFallbackToDeterministicAnswer() == true
  ├── citationRepairAttemptCount >= maxRepairRounds  (即 >= 1，已执行至少一次修复)
  └── AND (report.isNoCitation() == true
           OR hasUsableCitationEvidence(report) == false)
```

其中 `hasUsableCitationEvidence()` 的定义（`QueryFinalizationGraphFragment:329-334`）：

```java
private boolean hasUsableCitationEvidence(CitationCheckReport report) {
    if (report == null || report.isNoCitation()) {
        return false;
    }
    return report.getVerifiedCount() > 0
        || report.getSkippedCount() > 0
        || report.getCoverageRate() > 0.0D;
}
```

**这意味着：只有当 citation repair 已经被耗尽（至少 1 轮），且修复后的答案中已不存在任何 verified/skipped 引用或 coverage > 0 时，才会触发该 fallback。**

### 2.3 判定层

该判定发生在 **Query Graph 的 citation_check 节点内**（Graph 节点序列中的第 8 步）：

```
normalize_question → rewrite_query → classify_intent
→ resolve_retrieval_strategy → check_cache
→ dispatch_retrieval → fuse_candidates
→ answer_question → review_answer
→ [rewrite_answer → review_answer] (可选循环)
→ claim_segment → citation_check → [citation_repair → citation_check] (可选循环)
→ persist_response → finalize_response
```

具体调用栈：
1. `QueryGraphDefinitionFactory` 图定义注册 `citation_check` 节点 → `QueryFinalizationGraphFragment::citationCheck`
2. `citationCheck()` 调用 `citationCheckService.check(answer, projectionBundle)` 获取报告
3. `citationCheck()` 调用 `fallbackWhenCitationQualityIsInsufficient(state, report)`
4. 其中 `shouldFallbackToDeterministicAnswer(state, report)` 执行条件判断
5. 若判定为 true → 调用 `answerGenerationService.fallbackPayload()` 构造确定性答案 → 设置 `fallbackReason = "CITATION_QUALITY_INSUFFICIENT"`

---

## 3. 为什么 evidence 已足够时仍进入 FALLBACK

### 3.1 完整端到端事件链

```
Step 1: answer_question 节点
  ├── 调用 generatePayload(question, fusedHits)
  ├── 两种可能路径：
  │   A) containsOnlyArticleEvidence() == true (仅1条ARTICLE命中)
  │      → 返回 RULE_BASED 确定性答案（不调用 LLM）
  │      → 答案包含 [[article-key]] 引用标记
  │   B) containsOnlyArticleEvidence() == false (多条命中或有其他证据类型)
  │      → 调用 LLM 生成结构化 JSON 答案
  │      → LLM 答案包含 [[article-key]] 引用标记
  └── 任意路径：答案包含引用标记 → 进入下一步

Step 2: review_answer 节点
  └── Reviewer 审查答案 → 通常 PASS

Step 3: claim_segment 节点
  └── CitationExtractor 将答案切分为 claim 片段

Step 4: citation_check 节点（第一轮）
  ├── citationCheckService.check(answer, projectionBundle)
  │   ├── 从答案提取 citation literal（如 [[article-key]]）
  │   ├── 在 projection bundle 中查找对应 literal
  │   ├── 如果 literal 匹配 → VERIFIED
  │   └── 如果 literal 不匹配 → DEMOTED (reason: "projection_literal_not_found")
  ├── 报告: demotedCount > 0 或 coverageRate < 0.6
  ├── shouldFallbackToDeterministicAnswer():
  │   └── citationRepairAttemptCount=0 < maxRepairRounds=1 → **不触发 fallback**
  └── 路由: shouldRepairCitationReport → true → **进入 citation_repair**

Step 5: citation_repair 节点
  ├── citationCheckService.repair(answer, report)
  │   ├── 对每个 demoted citation：移除引用标记
  │   ├── 对无引用支持的 claim：追加 "(当前证据不足)" 标记
  │   └── 结果是：**原始引用被大量剥离或全部清除**
  ├── citationRepairAttemptCount: 0 → 1
  └── 路由: 回到 citation_check

Step 6: citation_check 节点（第二轮）
  ├── citationCheckService.check(repairedAnswer, projectionBundle)
  │   └── 修复后的答案中几乎没有引用 → noCitation=true 或 verifiedCount=0
  ├── shouldFallbackToDeterministicAnswer():
  │   ├── citationRepairAttemptCount=1 < maxRepairRounds=1 → false → 继续判断
  │   ├── report.isNoCitation() → **true** → 触发 fallback!
  │   └── 或 hasUsableCitationEvidence(report) → false → 触发 fallback!
  └── **fallbackWhenCitationQualityIsInsufficient() 执行：**
      ├── 调用 answerGenerationService.fallbackPayload()
      │   └── 构造确定性证据列表答案（# 查询回答 → ## 证据 → 逐条罗列）
      ├── 设置 generationMode = FALLBACK
      ├── 设置 fallbackReason = "CITATION_QUALITY_INSUFFICIENT"
      ├── 设置 modelExecutionStatus = DEGRADED
      └── 保存新的 answer / projection / citation report

Step 7: persist_response → finalize_response
  └── 最终返回给用户的是 Step 6 中构造的确定性证据列表
      而非 Step 1 中 LLM 生成的合成回答
```

### 3.2 根因本质

**evidence 足够 ≠ citation validation 通过。** 两个层面的问题叠加：

1. **Citation 匹配层**：LLM 输出的引用标记（citation literal）与 Projection Bundle 中注册的 literal 存在格式或键值偏差，导致匹配失败 → DEMOTED
2. **Repair 破坏层**：citation repair 的修复策略是"剥离不可验证引用"，而不是"保留并降级标注"。一轮修复后答案变成无引用状态 → 第二轮 check 判为 noCitation → terminal fallback

### 3.3 为什么 deterministic fallback 答案能看到 evidence

最终用户看到的是 `fallbackPayload()` 产出的确定性证据列表，它包含从原始命中中提取的 evidence 片段和正确的引用标记。所以用户看到"evidence 已被检索"，这是因为 **fallback 答案本身就是从 evidence 中拼装出来的**，而不是因为 LLM 合成成功。

---

## 4. 当前未提交 query 改动为何未改善端到端结果

### 4.1 改动范围

| 文件 | 改动内容 | 影响层级 |
|------|---------|---------|
| `AnswerPromptBuilder.java` | 增强多点展开 prompt 约束（规则 22-23） | LLM Prompt 层 |
| `AnswerParagraphPostProcessor.java` | 新增 `shouldKeepExpandedMultiPointAnswer()` 保护多焦点答案不被压缩 | 答案后处理层 |
| `AnswerGenerationServiceTests.java` | 新增多点展开回归测试 | 测试层 |

### 4.2 为什么无效

这两项改动位于整个决策链的最上游（LLM 调用的 prompt 构造 + LLM 输出的后处理），但当前问题的断点在下游（citation validation → repair → fallback）。

具体来说：

1. **Prompt 增强不会改变 citation literal 格式**：无论 prompt 怎么要求"逐项展开"，LLM 输出的引用标记格式（`[[article-key]]`）不变。如果 projection bundle 中的 literal 与 LLM 输出的 literal 不匹配，DEMOTED 仍然发生。

2. **后处理保护在 citation check 之前执行**：`AnswerParagraphPostProcessor.compressStructuredExactLookupAnswer()` 在 `parseStructuredAnswerPayload()` 阶段执行（`AnswerPayloadParser:61`）。此时答案的引用标记仍然完整。问题是 citation check 之后的 repair 把引用剥离了——后处理层无法阻止这一步。

3. **改动与 fallback 决策链完全无关**：
   - 不改动 `CitationCheckService.repair()` 的剥离策略
   - 不改动 `QueryFinalizationGraphFragment.shouldFallbackToDeterministicAnswer()` 的判断条件
   - 不改动 `QueryAnswerProjectionBuilder.build()` 的 projection 映射逻辑
   - 不改动 `CitationCheckOptions` 的阈值（minCitationCoverage=0.6, maxRepairRounds=1）

**结论：当前未提交的 query 改动在功能层面是正确的（增强了多点展开能力），但它们作用的层级在 citation fallback 决策链的上游。只要 citation repair 循环仍然剥离引用并触发 terminal fallback，这些 prompt 和后处理改进就无法体现在端到端结果中。**

---

## 5. 问题归类

| 可能原因 | 是否当前根因 | 说明 |
|---------|------------|------|
| synthesis 触发条件问题 | **否** | LLM synthesis 实际被触发了（或确定性路径产出了答案），但结果被下游替换 |
| citation 质量阈值问题 | **部分** | minCitationCoverage=0.6 本身合理，但 combined with repair 剥离策略导致二次检查必失败 |
| answerOutcome 归类问题 | **否** | answerOutcome 本身正确反映最终状态，不是归类错误 |
| **fallback 决策问题** | **是——核心根因** | citation repair 循环的设计导致"有证据但引用格式不匹配"退化为"无引用→terminal fallback" |
| retrieval 问题 | **否** | evidence 已正确检索，不应与 synthesis 决策混为一谈 |
| LLM 生成质量问题 | **否** | LLM 网关正常、输出格式正常，问题不在生成侧 |

**当前问题本质上是：citation validation + repair 管线的"全有或全无"策略——当引用标记无法精确匹配 projection 时，repair 不是降级保留而是全部剥离，导致二次检查必然失败。**

---

## 6. 下一轮最值得修的一个最小点

### 推荐修复点

**修复 `QueryFinalizationGraphFragment.shouldFallbackToDeterministicAnswer()` 的判定逻辑**，使得当 fusedHits 非空（即有证据）且 citation repair 已被耗尽时，不触发 terminal fallback，而是保留修复后的答案（即使引用不完美）。

### 具体位置

`QueryFinalizationGraphFragment.java:310-321` — `shouldFallbackToDeterministicAnswer()` 方法

### 当前逻辑

```java
private boolean shouldFallbackToDeterministicAnswer(QueryGraphState state, CitationCheckReport report) {
    if (report == null || answerGenerationService == null) {
        return false;
    }
    if (state.getCitationRepairAttemptCount() < CITATION_CHECK_OPTIONS.getMaxRepairRounds()) {
        return false;
    }
    if (report.isNoCitation()) {
        return true;  // ← 问题：即使有证据，无引用也直接 fallback
    }
    return !hasUsableCitationEvidence(report);  // ← 问题：verified=0 即 fallback
}
```

### 建议修改方向

在 `report.isNoCitation()` 和 `!hasUsableCitationEvidence(report)` 之前，增加一个保护条件：**如果 fusedHits 非空且至少存在可映射的 evidence，则不触发 terminal fallback，而是保留 repair 后的答案并继续 persist_response**。这样 repair 剥离引用后的答案（即使引用标记被移除，正文仍包含从 evidence 提取的信息）能够作为最终答案输出，而不是被确定性证据列表完全替换。

### 为什么是最小修复点

1. **改动范围最小**：只影响一个方法中的一个条件分支
2. **不改变 repair 策略**：不触动 `CitationCheckService.repair()` 的复杂逻辑
3. **不改变 citation 匹配逻辑**：不触动 projection 构建和 validation
4. **直接解决端到端问题**：阻止 terminal fallback 替换掉 LLM 合成（或确定性路径产出的）答案
5. **向后兼容**：对 citation 真正为空的场景（noCitation=true 且 fusedHits 也为空）仍然正确触发 fallback

### 为什么不是其他修复点

- **修改 repair 剥离策略**（保留降级引用而非剥离）：影响面更大，可能改变 citation check 的语义约定
- **修改 projection 匹配逻辑**：是 citation literal 格式对齐问题，需要深入理解 LLM 输出格式与 projection 构建的一致性
- **修改 CitationCheckOptions 阈值**：降低 minCitationCoverage 只是推迟问题，不解决根因
- **修改 prompt 或后处理**：已验证无效（见第 4 节）

---

## 7. 补充分析：为什么第一轮 citation check 会产生 DEMOTED

虽然这不是当前最值得修的根因（因为即使修好 citation 匹配，repair 的"全剥离"策略仍会在边界情况下触发同样的问题），但理解这一点有助于后续深度修复：

### 可能原因（按可能性排序）

1. **Projection literal 与 LLM 输出 literal 格式不完全一致**：`QueryAnswerProjectionBuilder.toAnswerProjection()` 在构建 projection 时使用 `citation.getLiteral()` 作为 key。如果 LLM 输出 `[[article-key|显示标签]]` 而 projection 注册的是 `[[article-key]]`，literal 不匹配 → DEMOTED。

2. **Claim 分段导致无引用 claim**：`CitationExtractor.extractClaims()` 将答案按句号/分号切分为多个 claim。如果某些 claim 文本在句子拆分后不含引用标记（引用在后续句子中），则这些 claim 会被标记为 unsupported → repair 追加 `(当前证据不足)`。

3. **NON_CLAIM_SECTIONS 过滤**：`CitationExtractor` 会跳过标题为"问题""参考说明"等 section 下的内容。如果答案的关键引用分布在"参考说明" section，这些引用不会被计入 citation check，导致 coverage 被低估。

---

## 8. 本轮是否修改代码

**否。** 本轮严格遵守只读约束：

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改任何配置文件
- 未提交任何代码
- 未清库、未运行 SWIP eval、未推进 compile 线

所有分析基于对以下文件的只读审查：
- `QueryFinalizationGraphFragment.java` — citation fallback 判定
- `QueryGraphAnswerSupport.java` — answer/review/rewrite 节点
- `QueryGraphDefinitionFactory.java` — Graph 节点拓扑
- `QueryGraphConditions.java` — citation 后路由
- `AnswerGenerationPayloadOrchestrator.java` — generatePayload 编排
- `AnswerPayloadParser.java` — LLM 输出解析
- `CitationCheckService.java` — citation 验证与 repair
- `CitationCheckReport.java` — 报告数据结构
- `CitationCheckOptions.java` — 阈值配置
- `CitationExtractor.java` — claim 分段与引用提取
- `QueryAnswerProjectionBuilder.java` — projection 白名单构建
- `AnswerFallbackMarkdownBuilder.java` — 确定性 fallback 答案格式
- `AnswerGenerationFallbackOutcomeSupport.java` — fallback 载荷构造
- `AnswerGenerationOutcomeSupport.java` — 答案语义推导
- `AnswerPromptBuilder.java` — prompt 构造（未提交改动）
- `AnswerParagraphPostProcessor.java` — 答案后处理（未提交改动）
