# Deep Research Fact Card Evidence Anchor 修复结果报告

生成时间：2026-05-13

## 1. Redline 状态

| 指标 | 值 |
|---|---|
| BLOCKER | 0 |
| EXIT | 0 |

```
$ bash scripts/scan-redline.sh special_cases_report.md
(无输出 → EXIT=0, BLOCKER=0)
```

## 2. mvn test 结果

```
Tests run: 811, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**811 / 0 / 0** ✅。

## 3. 修复内容

### 文件

`src/main/java/com/xbk/lattice/query/deepresearch/service/DeepResearchResearcherBaseSupport.java`

### 方法

`mapSourceType(QueryArticleHit hit)`，第 297–299 行

### diff

```diff
         if (hit.getEvidenceType() == QueryEvidenceType.GRAPH) {
             return EvidenceAnchorSourceType.GRAPH_FACT;
         }
+        if (hit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
+            return EvidenceAnchorSourceType.GRAPH_FACT;
+        }
         if (hit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
             return EvidenceAnchorSourceType.CONTRIBUTION;
         }
```

**+3 行**（if 语句 + return + 闭合括号），符合设计报告中 +1 逻辑行的预期。

## 4. 单 Case 验证 (Q-DEEP-001, forceDeep=true)

```bash
curl -s -X POST http://127.0.0.1:18082/api/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"综合说明迁移后 FC、DPFM、入口防腐层和 MQ 消费者边界之间的关系，以及这些边界为什么能降低上游改造成本。","forceDeep":true}'
```

### 结果

| 指标 | 修复前 | 修复后 |
|---|---|---|
| answer | "证据不足，无法生成可核验引用版答案（当前证据不足）" | **"证据不足，无法生成可核验引用版答案（当前证据不足）"**（不变） |
| answerOutcome | PARTIAL_ANSWER | PARTIAL_ANSWER（不变） |
| generationMode | LLM | LLM |
| modelExecutionStatus | SUCCESS | SUCCESS |
| sourceCount | 0 | **0**（不变） |
| citationCoverage | 0 | **0**（不变） |
| deepResearch.routed | true | true |
| deepResearch.evidenceCardCount | 0 | **1** ✅ |
| deepResearch.llmCallCount | 1 | 1 |

### 数据库内部状态（run_id=5）

| 维度 | 修复前 (run_id≤4) | 修复后 (run_id=5) |
|---|---|---|
| task-1 status | **PARTIAL** | **SUCCEEDED** ✅ |
| 证据锚点 (anchors) | **0** | **3** (全部 GRAPH_FACT 类型) ✅ |
| 事实发现 (findings) | **0** | **3** (confidence 0.586–0.587, >0.55 gate) ✅ |
| 答案投影 (answer projections) | **0** | **0** ❌ |

3 条 anchor 明细：

| anchor_id | source_type | source_id |
|---|---|---|
| ev#1 | GRAPH_FACT | fact-card:3:1:fact_policy:9e2bc90202353870 |
| ev#2 | GRAPH_FACT | fact-card:3:1:fact_enum:2a862f3634f7c1fe |
| ev#3 | GRAPH_FACT | fact-card:3:1:fact_sequence:f16255621660a26b |

3 条 finding 均通过质量门（confidence > 0.55, DIRECT support, claimText 非空）。

## 5. 修复效果分级评估

### 第一缺口：已修复 ✅

`mapSourceType()` 中 `FACT_CARD` → `GRAPH_FACT` 映射成功生效：

- `QueryEvidenceType.FACT_CARD` 不再落入 `return null` 分支
- `buildEvidenceAnchor()` 能正常创建 `EvidenceAnchor`（sourceType=GRAPH_FACT, sourceId=card_id, quoteText=content 前 180 字符）
- `buildFactFinding()` 能正常创建 `FactFinding`（factKey, claimText, confidence 均有效）
- 3 条 anchor + 3 条 finding 成功写入 `deep_research_evidence_anchors` 和 `deep_research_findings` 表
- task 状态从 PARTIAL 恢复为 SUCCEEDED

### 第二缺口：仍阻塞 ❌

`EvidenceLedger.buildProjectionCandidate()` 只处理 `ARTICLE` 和 `SOURCE_FILE`，对 `GRAPH_FACT` 返回 null：

```java
// EvidenceLedger.java
private ProjectionCandidate buildProjectionCandidate(FactFinding factFinding, EvidenceAnchor evidenceAnchor) {
    // ...
    switch (evidenceAnchor.getSourceType()) {
        case ARTICLE:
            return new ProjectionCandidate(..., ProjectionCitationFormat.ARTICLE, ...);
        case SOURCE_FILE:
            return new ProjectionCandidate(..., ProjectionCitationFormat.SOURCE_FILE, ...);
        default:
            return null;  // ← GRAPH_FACT 落到这里
    }
}
```

导致连锁反应：

1. `buildProjectionCandidate()` → null（3 条 finding 全部未转化）
2. `EvidenceLedger` 无 projection candidates
3. `DeepResearchSynthesizer.synthesize()` 检测到空 projections → `partialAnswer=true`
4. `resolveAnswerProjectionBundle()` → `insufficientProjectionBundle()`
5. 最终答案 → "证据不足"

**修复所需**：在 `EvidenceLedger.java` 的 switch 中增加 `case GRAPH_FACT` 分支，返回 `ProjectionCandidate`（映射到 `SOURCE_FILE` citation format，targetKey 为 sourceId 对应的文件路径）。改动量约 3 行。

**但本轮按指示不扩大修改范围。**

## 6. 非 Deep Research 路径行为

当不带 `forceDeep: true` 参数时，同一问题走普通 query 路径：

| 指标 | 值 |
|---|---|
| answerOutcome | **SUCCESS** ✅ |
| generationMode | LLM |
| citationCoverage | **1.0** (14/14) ✅ |
| verifiedCount | 14 |
| demotedCount | 0 |
| sourceCount | 1 (docs/卡券三期-迁移方案.md) |

普通路径直接通过 `knowledge_search` 检索 → LLM 生成答案 → `CitationValidator` 验证，不经过 Deep Research 的 `EvidenceLedger` → `Projector` 链路。这说明知识库中确实有充足数据，瓶颈仅在 Deep Research 投影链路。

## 7. 全量 Baseline

**未执行。** 用户指示："如果 Q-DEEP 仍失败，只输出原因分析，不扩大修改范围。" 因 Q-DEEP-001 (forceDeep=true) 仍返回 PARTIAL_ANSWER，按指示跳过全量 baseline。

mvn test 811/0/0 已确认无代码回归。

## 8. 本轮禁手确认

| 禁手项 | 状态 |
|---|---|
| 仅修改 `DeepResearchResearcherBaseSupport.java` | ✅（仅此 1 文件） |
| 仅修改 `mapSourceType()` 方法 | ✅（仅 +3 行） |
| 未修改 `EvidenceAnchorSourceType` 枚举 | ✅ |
| 未修改 `DeepResearchPlanner` | ✅ |
| 未修改 `KnowledgeSearchService` / 检索融合逻辑 | ✅ |
| 未修改 citation threshold / gate | ✅ |
| 未修改普通 query 主链 | ✅ |
| 未修改 `CitationValidator` | ✅ |
| 未修改 `scripts/scan-redline.sh` | ✅ |
| 未写问题文本/文件名特判 | ✅ |
| 未修改 Fact Card 编译 lineage | ✅ |
| 未修改测试文件 | ✅ |
| 未引入 eval 文件作为知识库数据 | ✅ |

## 9. 总结

| 维度 | 状态 |
|---|---|
| mapSourceType() FACT_CARD→GRAPH_FACT 映射 | ✅ 修复生效，3 anchors + 3 findings 产出 |
| task 状态恢复 | ✅ PARTIAL → SUCCEEDED |
| evidenceCardCount | ✅ 0 → 1 |
| answer projections | ❌ 仍为 0（第二缺口：EvidenceLedger 不处理 GRAPH_FACT） |
| Q-DEEP-001 (forceDeep=true) | ❌ 仍为 PARTIAL_ANSWER |
| mvn test | ✅ 811/0/0，无回归 |

**核心结论**：`mapSourceType()` 修复解决了锚点和发现的生产断层（第一缺口），但无法解决答案投影断层（第二缺口），因为 `EvidenceLedger.buildProjectionCandidate()` 不在本轮允许修改范围内。要根治 Q-DEEP-001 (forceDeep=true) 需在 `EvidenceLedger.java` 中增加 `GRAPH_FACT` 的投影支持，改动量约 +3 行。
