# Deep Research GRAPH_FACT Projection 缺口修复结果报告

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

## 2. 修改了哪些文件和方法

### 文件

仅 1 个文件：`src/main/java/com/xbk/lattice/query/deepresearch/service/DeepResearchResearcherBaseSupport.java`

### 修改 1：`buildEvidenceAnchor()`（第 87–89 行）

当 hit 为 FACT_CARD 且 `firstSourcePath(hit)` 非空时，将 sourceId 重写为源文件路径。

```java
        String sourceId = resolveSourceId(hit);
        if (hit.getEvidenceType() == QueryEvidenceType.FACT_CARD && firstSourcePath(hit) != null) {
            sourceId = firstSourcePath(hit);
        }
```

**效果**：sourceId 从 `fact-card:3:1:fact_policy:9e2bc90202353870` 变为 `docs/卡券三期-迁移方案.md`。

### 修改 2：`mapSourceType()`（第 301–303 行）

FACT_CARD 映射到 SOURCE_FILE（而非 GRAPH_FACT），利用现有 SOURCE_FILE 投影路径。

```java
        if (hit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            return EvidenceAnchorSourceType.SOURCE_FILE;
        }
```

**效果**：FACT_CARD 锚点可直接进入 `EvidenceLedger.buildProjectionCandidate` 已有的 `SOURCE_FILE` case，无需修改 EvidenceLedger。

### EvidenceLedger.java

**未修改。** 完全还原为原始状态。投影通过已有的 `SOURCE_FILE` case（line 328–338）处理。

## 3. 是否只处理 FACT_CARD 派生锚点

**是。** `buildEvidenceAnchor` 中的 sourceId 重写仅当 `hit.getEvidenceType() == QueryEvidenceType.FACT_CARD` 时触发。`mapSourceType` 中仅 `QueryEvidenceType.FACT_CARD` 被映射到 `SOURCE_FILE`。

## 4. 是否影响 QueryEvidenceType.GRAPH

**否。** `QueryEvidenceType.GRAPH` 仍映射为 `EvidenceAnchorSourceType.GRAPH_FACT`，sourceId 不经任何改写，投影阶段 `buildProjectionCandidate` 无 `GRAPH_FACT` case（仍返回 null）。行为与修复前完全一致。

## 5. 是否影响 CONTRIBUTION

**否。** `QueryEvidenceType.CONTRIBUTION` 仍映射为 `EvidenceAnchorSourceType.CONTRIBUTION`，投影阶段无 `CONTRIBUTION` case。行为与修复前完全一致。

## 6. 是否新增 ProjectionCitationFormat

**否。** 使用已有的 `SOURCE_FILE` 格式。`ProjectionCitationFormat` 枚举未变更。

## 7. 是否修改 DeepResearchProjector / CitationValidator

**否。** `DeepResearchProjector` 已支持 `SOURCE_FILE` → `[→ targetKey]` 渲染。`CitationValidator` 已支持 `SOURCE_FILE` 校验。两者均未修改。

## 8. Redline BLOCKER

仍为 0。修复前后均无 redline 变更。

## 9. mvn test

```
Tests run: 811, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**811 / 0 / 0** ✅。无测试回归。关键测试通过：
- `EvidenceLedgerTests.shouldPreferStructuredFindingsAndOnlyProjectArticleOrSourceAnchors` ✅
- `DeepResearchProjectorTests.shouldProjectOnlyOutboundCitationCandidates` ✅

## 10. Q-DEEP-001 修复前后对比

### 10.1 外显指标

| 指标 | 修复前 | 修复后 |
|---|---|---|
| pass | **FAIL** | **PASS** ✅ |
| answerOutcome | PARTIAL_ANSWER | **SUCCESS** ✅ |
| generationMode | LLM | **LLM**（不变） |
| modelExecutionStatus | SUCCESS | **SUCCESS**（不变） |
| sourceCount | 0 | **1** ✅ |
| citationCoverage | 0.0 | **0.6** (3/5) ✅ |
| citationVerifiedCount | 0 | **3** ✅ |
| citationDemotedCount | 0 | **0** ✅ |
| answer preview | "证据不足，无法生成…" | 完整 Deep Research 答案（含 FC/DPFM/MQ 边界分析） ✅ |

### 10.2 数据库内部状态

| 指标 | 修复前 (run_id=5) | 修复后 (run_id=6) |
|---|---|---|
| evidence anchors | 3 (GRAPH_FACT) | **3 (SOURCE_FILE)** |
| findings | 3 | **3** |
| answer projections | **0** | **1** ✅ |
| partial_answer | true | **false** ✅ |
| task status | SUCCEEDED | **SUCCEEDED**（不变） |

Anchor 明细（run_id=6）：

| anchor_id | source_type | source_id |
|---|---|---|
| ev#1 | SOURCE_FILE | docs/卡券三期-迁移方案.md |
| ev#2 | SOURCE_FILE | docs/卡券三期-迁移方案.md |
| ev#3 | SOURCE_FILE | docs/卡券三期-迁移方案.md |

Answer projection：

| projection_ordinal | source_type | target_key | citation_literal |
|---|---|---|---|
| 1 | SOURCE_FILE | docs/卡券三期-迁移方案.md | `[→ docs/卡券三期-迁移方案.md]` |

### 10.3 最终 citation literal 示例

```
[→ docs/卡券三期-迁移方案.md]
```

**无 `fact-card:*` 内部 ID** ✅。

## 11. 全量 Baseline 结果

### 11.1 逐 Case

| Case | 修复前 | 修复后 | 说明 |
|---|---|---|---|
| Q-RUNTIME-OCR-001 | FAIL | **FAIL** | 数据缺失（无 OCR 文档），预期内 |
| Q-STRUCT-ROW-001 | PASS | **PASS** | 不变 ✅ |
| Q-STRUCT-PROJECTION-001 | PASS | **PASS** | 不变 ✅ |
| Q-STRUCT-AGG-001 | PASS | **PASS** | 不变 ✅ |
| Q-STRUCT-COMPARE-001 | PASS | **PASS** | 不变 ✅ |
| Q-EXACT-PATH-001 | PASS | **PASS** | 不变 ✅ |
| Q-MQ-BOUNDARY-001 | PASS | **PASS** | 不变 ✅ |
| Q-CONFIG-001 | PASS | **PASS** | 不变 ✅ |
| Q-DEEP-001 | FAIL | **PASS** ✅ | **目标修复** |
| Q-NO-HIT-001 | PASS | **PASS** | 不变 ✅ |

### 11.2 总体指标

| 指标 | 值 |
|---|---|
| total | 10 |
| passCount | **9** |
| casePassRate | **0.9** ✅ (>0.8 gate) |
| httpFailureRate | 0 ✅ |
| timeoutRate | 0 ✅ |
| fallbackRate | 0 ✅ (<0.4 gate) |
| llmSuccessRate | 0.4 ✅ (≥0.4 gate) |
| averageCitationCoverage | 0.721 ✅ (>0.6 gate) |
| Recall@5 | 0.889 |
| Recall@10 | 0.889 |
| citationPrecision | 0.902 |

**所有 gate 通过，无新增回归。**

## 12. Q-RUNTIME-OCR-001

**仍未处理。** 本轮未修改 OCR 相关代码，该 Case 因知识库缺少 OCR 运行状态文档而持续返回 `NO_RELEVANT_KNOWLEDGE`。属于数据缺失问题，非代码缺陷。

## 13. 本轮禁手确认

| 禁手项 | 状态 |
|---|---|
| 未修改 `src/test/java/**` | ✅ |
| 未修改 `src/main/resources/**` | ✅ |
| 未修改 `scripts/scan-redline.sh` | ✅ |
| 未修改 redline allowlist | ✅ |
| 未修改 `ProjectionCitationFormat` | ✅ |
| 未修改 `DeepResearchProjector` | ✅ |
| 未修改 `CitationValidator` | ✅ |
| 未修改 `EvidenceAnchorSourceType` | ✅ |
| 未修改 `DeepResearchPlanner` | ✅ |
| 未修改 `KnowledgeSearchService` / RRF / rerank | ✅ |
| 未修改 FactCard 编译相关代码 | ✅ |
| 未修改普通 Query / AnswerGeneration 主链 | ✅ |
| 未修改 eval / baseline 脚本 | ✅ |
| 未写 Q-DEEP / FC / DPFM / MQ / 卡券三期等特判 | ✅ |
| 未新增 `ProjectionCitationFormat.GRAPH_FACT` | ✅ |
| 未出现 `fact-card:*` 用户可见引用 | ✅ |
| 未无条件投影所有 GRAPH_FACT | ✅ |
| 未修改 `EvidenceLedger.java` | ✅（最终状态为原始代码） |

## 14. 总结

| 维度 | 状态 |
|---|---|
| 第一缺口（FACT_CARD→映射缺失） | ✅ 已修复（上一轮） |
| 第二缺口（→投影缺失） | ✅ 已修复（本轮） |
| Q-DEEP-001 (forceDeep=true) | ✅ PASS, citationCoverage=0.6 |
| 用户可见 citation 格式 | ✅ `[→ docs/卡券三期-迁移方案.md]` |
| fact-card:* 内部 ID 暴露 | ✅ 无 |
| mvn test | ✅ 811/0/0 |
| 全量 baseline | ✅ 9/10 PASS, 无回归 |
| redline | ✅ BLOCKER=0, EXIT=0 |
| EvidenceLedger.java | ✅ 未修改（通过 FACT_CARD→SOURCE_FILE 映射绕过） |

**核心修复**：`FACT_CARD` 映射到 `SOURCE_FILE` anchor 类型，sourceId 重写为文件路径，复用已有的 SOURCE_FILE 投影路径。共修改 1 个文件，+6 行（buildEvidenceAnchor +3 行，mapSourceType +3 行）。
