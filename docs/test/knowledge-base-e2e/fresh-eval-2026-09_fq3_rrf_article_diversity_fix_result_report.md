# PE6 FQ3 RRF 文章多样性修复 — 结果报告

修复时间：2026-06-10
执行人：agentC（RRF 融合修复 Agent）
修复分支：main（本地未提交）

---

## 1. 修复点

### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java` | 新增 diversity-aware topK 选择逻辑 |
| `src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java` | 新增 4 个多样性约束单元测试 |

### 具体变更

**RrfFusionService.java:**

1. 新增常量 `MAX_HITS_PER_DIVERSITY_GROUP = 2`（第 33 行）— 同一 diversity group（article/source）在 fused topK 中最多保留 2 个命中。

2. 新增方法 `selectWithDiversity(List<QueryArticleHit>, int limit)`（第 468–495 行）— 两轮选择：
   - 第一轮：按 RRF 分数降序遍历，每个 diversity group 最多保留 `MAX_HITS_PER_DIVERSITY_GROUP` 个命中。
   - 第二轮：若第一轮未选够 limit，从剩余命中中按原始分数顺序回填。

3. 新增方法 `buildDiversityGroupKey(QueryArticleHit)`（第 506–517 行）— 构建多样性分组键：
   - 优先使用 `articleKey`
   - fallback 到 `conceptId`
   - 再 fallback 到 `sourcePaths`
   - 最终 fallback 使用 `buildHitKey`

4. 修改 `fuse()` 方法（第 220 行）：将 GENERAL 路径的 `subList(0, limit)` 替换为 `selectWithDiversity(fusedHits, limit)`。

**关键设计决策：**
- 仅修改 GENERAL 答案形态路径（非结构化证据保护路径），不触碰 `applyStructuredEvidenceGuardrail`。
- RRF 分数计算（`mergeHits`）完全不变，只改变最终 topK selection。
- diversity group key 基于通用 identity 字段（articleKey / conceptId / sourcePaths），不涉及任何业务特判。

### 未修改的范围（遵守禁止清单）

- `AnswerGenerationPromptEvidenceSupport.java` — 未触碰
- `AnswerGeneration` / prompt / citation 主链 — 未触碰
- `fresh-eval-2026-09` 题集 expected/source — 未触碰
- `scripts/scan-redline.sh` — 未触碰
- redline allowlist — 未触碰
- 数据库 / schema / Redis — 未触碰
- 无 FQ3、FineServiceImpl、feature-flags、阶梯罚金、library.fine.max-days 等任何 case 特判

---

## 2. 为什么是通用 RRF 多样性修复，不是 FQ3 特判

修复规则不包含任何与具体 query、article、文件名相关的判断：

| 判断维度 | 修复使用的 identity | 是否涉及具体业务 |
|---------|-------------------|:---:|
| 分组依据 | `articleKey`（通用字段） | 否 |
| fallback | `conceptId`、`sourcePaths`（通用字段） | 否 |
| 上限阈值 | `MAX_HITS_PER_DIVERSITY_GROUP = 2`（全局常量） | 否 |

规则等价于："在 fused topK 中，同一 article 的 chunk 不超过 2 个"。对 ADR-002、FineServiceImpl、Feature Flag 或任何其他 article 一视同仁。

受益面：
- FQ3（阶梯罚金）：ADR-002 从独占 7 席降为最多 2 席，FineServiceImpl 和 Feature Flag 有机会进入 topK。
- 任何查询中，若某个 article 有大量 chunk 命中，不再系统性挤出其他互补性证据。

---

## 3. 对已有 structured evidence guardrail 的影响

**无影响。** 多样性选择仅作用于 GENERAL 答案形态路径（`isStructuredAnswerShape` 和 `hasRelevantDirectEvidence` 均返回 false 时）。

结构化证据保护路径（ENUM / COMPARE / SEQUENCE / STATUS / POLICY，以及触发 `hasRelevantDirectEvidence` 的 GENERAL 问题）仍然走原有的 `applyStructuredEvidenceGuardrail`，其 Fact Card / source chunk 优先、背景 article 替换逻辑完全不变。

已有测试 `shouldProtectStructuredEvidenceForCompareSequenceAndStatusShapes`、`shouldProtectQuestionRelevantStructuredEvidenceBeforeGenericFactCard`、`shouldProtectDirectEvidenceForGeneralMultiFocusFactQuestion` 等全部通过，确认 guardrail 未被破坏。

---

## 4. 测试结果

### 单元测试

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

新增 4 个测试：

| 测试方法 | 验证点 | 结果 |
|---------|--------|:---:|
| `shouldLimitSameArticleChunksInTopK` | 同一 article 5 个 chunk 排名前列时，不全部进入 topK，其他 article 能进入 | 通过 |
| `shouldKeepScoreOrderWithSmallLimit` | limit=2 时仍按分数优先，结果数量正确 | 通过 |
| `shouldNotMergeDifferentArticleGroups` | 不同 article 各自享有独立的 diversity quota | 通过 |
| `shouldBackfillWhenNotEnoughDiversityHits` | diversity quota 用完后不足 limit 时，回填剩余高分命中 | 通过 |

### 组合测试

```
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
```

QueryGraphOrchestratorTests (9) + QueryGraph* (8) + WeightedRrfFusionTest (14) = 全部通过。

### Redline 扫描

`bash scripts/scan-redline.sh special_cases_report.md` — 通过，无新增违规。

---

## 5. 是否需要 Docker 打包重启

**需要。** 修改了 `RrfFusionService.java`（运行时 Spring Bean），变更需要重新编译打包并重启 Docker 容器才能生效。

后续 agentD 做 Docker runtime gate 验证时需执行：
1. `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -DskipTests package`
2. Docker 镜像重建与容器重启
3. 对 queryId `11ee53c9-0b1d-42af-972d-69f7b8c1b29e`（阶梯罚金）重新执行检索验证 fused topK 多样性

---

## 6. 是否需要后续 agentD 做 Docker runtime gate

**需要。** 单元测试验证了 diversity 选择逻辑的正确性，但需要 Docker runtime 环境验证：
- 真实多通道检索 → RRF 融合 → diversity 选择端到端行为
- FQ3 query（阶梯罚金）的 fused topK 是否不再被 ADR-002 独占
- FineServiceImpl 和 Feature Flag 是否能进入 fused topK
- 回归验证其他 query 的 fused 结果质量

---

## 7. 明确声明

- [x] 未修改 AnswerGenerationPromptEvidenceSupport.java
- [x] 未修改 AnswerGeneration / prompt / citation 主链
- [x] 未修改 fresh-eval-2026-09 题集
- [x] 未修改 scripts/scan-redline.sh / redline allowlist
- [x] 未清库、未重建 schema、未删除 Redis key
- [x] 无任何 case 特判（FQ3 / FineServiceImpl / feature-flags / 阶梯罚金 / ADR-002）
- [x] 修复基于通用 identity（articleKey / conceptId / sourcePaths）
- [x] RRF 分数计算不变（mergeHits 未改）
- [x] Structured evidence guardrail 未被破坏
- [x] 未提交 commit
