# Query Citation Quality Terminal Fallback 修复结果报告

- 生成时间：2026-05-22
- 执行 Agent：agentA
- 任务类型：最小代码修复（仅修 terminal fallback 决策）

---

## 1. 修改了哪些文件和方法

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `QueryFinalizationGraphFragment.java:313-316` | 修改 | `shouldFallbackToDeterministicAnswer()` 新增 `isHasFusedHits()` 保护条件 |
| `QueryFinalizationGraphFragment.java:307` | 修改 | 方法可见性从 `private` 改为 package-private，便于测试 |
| `QueryFinalizationGraphFragment.java:262` | 修改 | 更新 `fallbackWhenCitationQualityIsInsufficient()` Javadoc |
| `QueryFinalizationGraphFragment.java:306-312` | 修改 | 更新 `shouldFallbackToDeterministicAnswer()` Javadoc |
| `QueryFinalizationGraphFragmentTests.java` | **新增** | 4 个单元测试覆盖 fallback 决策的 4 个关键场景 |
| `QueryGraphOrchestratorTests.java` | 修改 | 更新 3 个集成测试断言，匹配修复后的正确行为 |

**未修改的文件（符合禁止范围）：**
- `compile` 主链：零修改
- `retrieval / rerank`：零修改
- `AnswerPromptBuilder / AnswerParagraphPostProcessor`：零修改
- `schema`：零修改
- `scripts/scan-redline.sh`：零修改

---

## 2. 之前的 terminal fallback 触发链

```
shouldFallbackToDeterministicAnswer() == true
  ├── citationRepairAttemptCount >= maxRepairRounds (即 >= 1)
  └── AND (report.isNoCitation() == true
           OR hasUsableCitationEvidence(report) == false)
```

**问题：** 当 fusedHits 明确存在（evidence 已检索到），LLM 或确定性路径产出了合成答案，但 citation repair 因 literal 格式不匹配而剥离了引用标记后，第二轮 citation check 报告 `noCitation=true` 或 `verifiedCount=0`，直接触发 terminal fallback，用 `# 查询回答 → ## 证据` 的确定性证据列表替换掉 LLM 合成答案。

完整事件链见 `query_fallback_citation_quality_root_cause_report.md` 第 3.1 节。

---

## 3. 现在如何阻止过度 fallback

在 `shouldFallbackToDeterministicAnswer()` 中新增一个保护条件（第 316-318 行）：

```java
if (state.isHasFusedHits()) {
    return false;
}
```

**逻辑：** 当 `isHasFusedHits() == true`（即 retrieval 阶段确实检索到了证据），即使 citation repair 已将引用标记全部剥离，也不再触发 terminal fallback。保留 repair 后的答案正文继续进入 `persist_response → finalize_response`。

新的判定链：

```
shouldFallbackToDeterministicAnswer() == true
  ├── report != null && answerGenerationService != null
  ├── AND citationRepairAttemptCount >= maxRepairRounds (repair 已耗尽)
  ├── AND isHasFusedHits() == false          ← 新增保护：有证据则不 fallback
  └── AND (report.isNoCitation() == true
           OR hasUsableCitationEvidence(report) == false)
```

`isHasFusedHits()` 在 Graph 的 `fuse_candidates` 节点中设置，准确反映 retrieval 阶段是否成功检索到证据。该字段在整个 Graph 生命周期内不变，是可靠的判定信号。

---

## 4. 什么场景仍然会继续 FALLBACK

| 场景 | `isHasFusedHits()` | `noCitation` | 是否 fallback | 说明 |
|---|---|---|---|---|
| 无任何检索命中 | `false` | `true` | **是** | 无证据，合理 fallback |
| 无检索命中 + repair 已耗尽 + verified=0 | `false` | `false` | **是** | `hasUsableCitationEvidence` 为 false |
| 有检索命中 + repair 未耗尽 | `true` | `true` | **否** | repair 尚未耗尽，走 repair 循环 |
| 有检索命中 + repair 已耗尽 + noCitation | `true` | `true` | **否（本次修复）** | evidence 存在，保留 repair 后答案 |
| 有检索命中 + repair 已耗尽 + verified>0 | `true` | `false` | **否** | citation 可用，无需 fallback |

**总结：只有在 fusedHits 为空（完全没有检索到任何证据）且 citation repair 已耗尽的场景下，才触发 terminal fallback。**

---

## 5. 为什么不会误放过真正无证据答案

1. **`isHasFusedHits()` 是 retrieval 阶段的硬信号**：该字段仅在 `fuse_candidates` 节点中设置为 `true`，前提是至少有一条融合命中。如果 retrieval 完全无结果，该字段为 `false`，仍会触发 fallback。

2. **不会放过空答案**：如果 `isHasFusedHits() == false`，后续 `finalizeResponse()` 也会产出一条 "当前未找到与该问题直接相关的知识" 的无证据响应，与 citation fallback 形成双重保障。

3. **不会放过 LLM 幻觉**：如果 fusedHits 存在但答案与证据完全无关，citation repair 会剥离无关引用并标记 `(当前证据不足)`。但答案正文仍保留——这与之前的确定性 fallback 相比，信息量不会更少。如果正文确实是幻觉，这是 LLM 生成阶段的问题，应在 review 节点解决，而非在 citation check 节点用一刀切 fallback 掩盖。

4. **`isHasFusedHits()` 与 fusedHits 数据一致性**：`fuse_candidates` 节点设置 `isHasFusedHits` 的同时会保存 fusedHits 到 working set。后续节点通过 `fusedHitsRef` 加载实际数据。两者在正常流程中保持一致。

---

## 6. redline BLOCKER 是否仍为 0

**是。** 修复后 redline 扫描结果：

- 总命中：2159
- BLOCKER：**0**
- REVIEW：1913
- ALLOWLIST：246

---

## 7. 测试是否通过

**是。** 全量 `mvn test` 结果：

- Tests run: **871**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

新增 4 个单元测试 `QueryFinalizationGraphFragmentTests`：

| 测试用例 | 场景 | 结果 |
|---|---|---|
| `shouldNotFallbackWhenFusedHitsExistEvenIfNoCitationAfterRepair` | fusedHits 存在 + repair 耗尽 + noCitation=true | 不 fallback |
| `shouldStillFallbackWhenNoFusedHitsAndNoCitationAfterRepair` | fusedHits 不存在 + repair 耗尽 + noCitation=true | 仍 fallback |
| `shouldNotFallbackWhenFusedHitsExistAndNoUsableCitationEvidence` | fusedHits 存在 + repair 耗尽 + verified=0/coverage=0 | 不 fallback |
| `shouldNotFallbackWhenRepairRoundsNotExhausted` | fusedHits 不存在 + repair 未耗尽 + noCitation=true | 不 fallback (走 repair) |

更新 3 个 `QueryGraphOrchestratorTests` 集成测试：
- `shouldFinalizeWithoutCachingWhenRewriteLimitIsReached` — 更新为验证 repair 后答案被保留
- `shouldNotCacheEvidenceInsufficientAnswerEvenWhenReviewPasses` — 更新为验证 INSUFFICIENT_EVIDENCE 答案不被替换
- `shouldRepairCitationOutsideTopKProjectionWhitelist` — 更新为验证 citation 被剥离后答案仍被保留

---

## 8. 下一轮是否建议交给 agentD 做 query runtime 复验

**是。** 本轮修复仅改变了 terminal fallback 决策逻辑，阻止了 "有证据但引用被剥离后一刀切 FALLBACK" 的问题。但以下问题需要 runtime 环境验证：

1. **端到端答案质量验证**：修复后的答案（repair 剥离引用 + 保留正文）在真实用户查询中的表现如何，需要实际运行观察。
2. **Citation 覆盖率统计**：修复后 `CITATION_QUALITY_INSUFFICIENT` fallback 的触发频率应显著下降，需要 runtime 监控确认。
3. **边界场景**：`isHasFusedHits() == true` 但答案完全由 rule-based 路径产出的场景（如 `containsOnlyArticleEvidence()`），修复后行为是否仍正确。

建议 agentD 使用 `QueryFinalizationGraphFragmentTests` 作为回归基线，在真实环境中验证修复效果。
