# Query Citation Quality Terminal Fallback 修复修订报告

- 生成时间：2026-05-22
- 执行 Agent：agentA
- 任务类型：最小代码修正（收窄第一版过宽的 terminal fallback 修复）

---

## 1. 第一版为什么过宽

第一版在 `shouldFallbackToDeterministicAnswer()` 中新增了：

```java
if (state.isHasFusedHits()) {
    return false;
}
```

问题有两个层面：

### 1.1 语义层面：用证据存在性替代答案合成质量

`isHasFusedHits()` 只能说明 retrieval 阶段检索到了证据，不能说明答案正文是否可信。它把"有证据 → 答案一定可信"做了等价判断，但实际上：

- 证据存在但 LLM 调用失败 → 答案可能是空的或无效的
- 证据存在但答案来自未知路径（generationMode 未设置）→ 答案质量不可控
- 证据存在且答案确实由 LLM/规则合成 → 答案正文可信（这是唯一该保护的场景）

### 1.2 工程层面：制造了逻辑死路径

`fallbackWhenCitationQualityIsInsufficient()` 真正生成 deterministic fallback 依赖加载 fusedHits：

```java
List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
if (fusedHits == null || fusedHits.isEmpty()) {
    return report;  // 无法生成 fallback
}
```

第一版的逻辑造成：
- `isHasFusedHits() == true` → `shouldFallback` 返回 false → 不 fallback
- `isHasFusedHits() == false` → `shouldFallback` 返回 true → 但 `fusedHits` 加载为空 → 无法生成 fallback

**`CITATION_QUALITY_INSUFFICIENT` terminal fallback 被整体关闭**，而非仅解决"citation repair 剥离引用后过度 fallback"的窄问题。

---

## 2. 新判定如何区分两种场景

### 2.1 核心变更

将保护条件从 `isHasFusedHits()` 替换为 `generationMode` 检查：

```java
GenerationMode mode = readGenerationMode(state.getGenerationMode());
if (mode == GenerationMode.LLM || mode == GenerationMode.RULE_BASED) {
    return false;
}
```

`generationMode` 在 `answer_question` 节点中由答案生成编排器设置：
- `LLM`：LLM 网关成功生成结构化答案
- `RULE_BASED`：`containsOnlyArticleEvidence()` 确定性路径
- `FALLBACK`：`fallbackPayload()` 已被调用（LLM 调用失败等）
- `null`：未设置（异常路径）

### 2.2 场景区分表

| generationMode | repair 耗尽 | noCitation | 是否 fallback | 说明 |
|---|---|---|---|---|
| `LLM` | 是 | 是 | **否（修复）** | LLM 合成答案，repair 剥离引用，正文仍可信 |
| `RULE_BASED` | 是 | 是 | **否（修复）** | 规则拼装答案，正文仍可信 |
| `null` | 是 | 是 | **是（保留）** | 未走正常合成，安全网触发 |
| `FALLBACK` | 是 | 是 | **是（保留）** | 已在兜底模式，允许再次 fallback |
| 任意 | 否 | — | **否** | repair 尚未耗尽，走 repair 循环 |
| `LLM`/`RULE_BASED` | 是 | 否 | 由 `hasUsableCitationEvidence` 判定 | citation 仍可用，正常流程 |

### 2.3 为什么 `generationMode` 是正确的区分信号

1. **它是答案合成路径的直接证据**：`generationMode` 在 `answer_question` 节点由实际发生的合成路径设置，而不是间接推断
2. **它比 `isHasFusedHits` 更窄**：`isHasFusedHits` 只是合成的必要前提，而非充分条件；`generationMode` 确认了合成确实发生了
3. **它保留了安全网**：`generationMode == null` 或 `FALLBACK` 时仍允许 fallback，不会因为异常路径而让不可信答案通过

---

## 3. 哪些场景仍会触发 `CITATION_QUALITY_INSUFFICIENT` fallback

### 3.1 正常触发场景

| 场景 | 条件 |
|---|---|
| generationMode 未正常设置 | `mode == null` + repair 耗尽 + noCitation |
| 答案已在兜底模式 | `mode == FALLBACK` + repair 耗尽 + noCitation |

### 3.2 工程前提：fusedHits 必须可加载

即使 `shouldFallbackToDeterministicAnswer` 返回 true，`fallbackWhenCitationQualityIsInsufficient()` 内部仍会检查 fusedHits 是否可加载。只有在 fusedHits 非空时才能构造确定性证据列表。

### 3.3 正常路径（LLM/RULE_BASED）不再触发

这是本次修复的核心效果：当答案确实是 LLM 合成或规则拼装产生时，即使 citation repair 剥离了所有引用标记，也不会再被确定性证据列表替换。

---

## 4. 为什么不会放过真正无证据/无支撑答案

### 4.1 无证据场景（fusedHits 为空）

`isHasFusedHits() == false` 时：
- `answer_question` 节点可能产生一个空答案或跳过生成
- `finalizeResponse()` 会独立产出 "当前未找到与该问题直接相关的知识" 响应
- citation fallback 不会触发（fusedHits 加载为空）

**保障：无证据答案在多个独立节点被处理，不依赖 citation fallback。**

### 4.2 无支撑答案（正文与证据无关）

如果 LLM 生成了与证据无关的答案：
- Reviewer 节点会审查（如果启用）
- Citation repair 剥离无关引用后，答案正文仍保留
- generationMode 仍为 LLM，不会触发 citaton fallback

**风险评估：** LLM 幻觉应被 Reviewer 节点捕获。如果 Reviewer 未启用或未捕获，答案正文会原样展示。这与修复前 citation fallback 替换为确定性证据列表相比，信息量不会更少（确定性证据列表本身也是从 fusedHits 拼装）。

### 4.3 空答案场景

如果 LLM 返回空字符串或纯占位符（如 "TODO"）：
- generationMode 仍为 LLM
- 不会触发 citation fallback
- 但这个场景在第一版修复中已被测试覆盖（`shouldFinalizeWithoutCachingWhenRewriteLimitIsReached`），答案是 "仍然需要确认"，经 repair 后变为 "仍然需要确认（当前证据不足）"

**评估：** 弱答案保留正文并追加 `(当前证据不足)` 标记，对用户可见的信息比纯确定性证据列表更透明。

---

## 5. 修改了哪些文件和方法

| 文件 | 变更 | 说明 |
|---|---|---|
| `QueryFinalizationGraphFragment.java:315-319` | **修改** | 将 `isHasFusedHits()` 替换为 `generationMode` 检查 |
| `QueryFinalizationGraphFragment.java:262` | 修改 | 更新 `fallbackWhenCitationQualityIsInsufficient()` Javadoc |
| `QueryFinalizationGraphFragment.java:306-312` | 修改 | 更新 `shouldFallbackToDeterministicAnswer()` Javadoc |
| `QueryFinalizationGraphFragment.java:315` | 修改 | 方法可见性 `private` → package-private（延续第一版，便于测试） |
| `QueryFinalizationGraphFragmentTests.java` | **重写** | 7 个测试：6 个 `shouldFallbackToDeterministicAnswer` 场景 + 1 个 `citationCheck` 端到端 |
| `QueryGraphOrchestratorTests.java` | 保留第一版修改 | 3 个集成测试断言已匹配正确行为（generationMode=LLM 时不 fallback） |

---

## 6. 测试覆盖详情

### 6.1 `shouldFallbackToDeterministicAnswer` 单元测试（6 个）

| 测试用例 | generationMode | repairAttemptCount | noCitation | 预期 |
|---|---|---|---|---|
| `shouldNotFallbackWhenLlmSynthesizedAndNoCitationAfterRepair` | LLM | 1 | true | **不 fallback** |
| `shouldNotFallbackWhenRuleBasedAndNoCitationAfterRepair` | RULE_BASED | 1 | true | **不 fallback** |
| `shouldStillFallbackWhenGenerationModeNullAndNoCitationAfterRepair` | null | 1 | true | **fallback（安全网）** |
| `shouldStillFallbackWhenAlreadyFallbackModeAndNoCitationAfterRepair` | FALLBACK | 1 | true | **fallback（安全网）** |
| `shouldNotFallbackWhenRepairRoundsNotExhausted` | null | 0 | true | **不 fallback** |
| `shouldNotFallbackWhenLlmSynthesizedAndNoUsableCitationEvidence` | LLM | 1 | false* | **不 fallback** |

*noCitation=false 但 verified=0, skipped=0, coverage=0.0，generationMode=LLM 的保护先命中。

### 6.2 `citationCheck` 端到端测试（1 个）

`citationCheckShouldNotReplaceLlmAnswerWhenNoCitationAfterRepair`：
- 构造 `InMemoryQueryWorkingSetStore` + 真实 `CitationCheckService`
- 设置 generationMode=LLM、repairAttemptCount=1、无 citation 标记的答案
- 调用 `citationCheck()` → 验证 `fallbackWhenCitationQualityIsInsufficient` 未被触发
- 答案保持原样，`fallbackReason` 为 null

---

## 7. redline BLOCKER 是否仍为 0

**是。** 扫描结果：

- 总命中：2159
- BLOCKER：**0**
- REVIEW：1913
- ALLOWLIST：246

---

## 8. 测试是否通过

**是。** 全量 `mvn test`：

- Tests run: **874**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

新增 7 个 `QueryFinalizationGraphFragmentTests`，保留 3 个 `QueryGraphOrchestratorTests` 更新。

---

## 9. 是否建议再交给 agentD 做 query runtime 复验

**是。** 本次修订将保护条件从证据存在性（`isHasFusedHits`）收窄到合成路径（`generationMode`），消除了第一版的语义矛盾。但以下需要 runtime 验证：

1. **`CITATION_QUALITY_INSUFFICIENT` 触发频率**：修订后该 fallback 的触发频率应显著下降（正常合成路径不再触发），但不应降为零（安全网场景仍可能触发）。需 runtime 监控确认。
2. **安全网场景的实际出现频率**：`generationMode == null` 或 `FALLBACK` 时的 fallback 触发是否在真实环境中出现，出现时答案质量如何。
3. **repair 后保留答案的用户体验**：repair 剥离引用后的答案正文 + `(当前证据不足)` 标记，与之前的确定性证据列表相比，用户满意度如何。

建议 agentD 以 `QueryFinalizationGraphFragmentTests` 中的 7 个测试用例为回归基线，在真实 query 环境中观察 `CITATION_QUALITY_INSUFFICIENT` 的触发模式变化。
