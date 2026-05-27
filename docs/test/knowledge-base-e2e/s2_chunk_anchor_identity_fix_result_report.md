# S2 Chunk Anchor Identity Fix Result Report

## 修改文件清单

- `src/main/java/com/xbk/lattice/query/service/ChunkHitIdentitySupport.java`
- `src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java`
- `src/main/java/com/xbk/lattice/query/service/ChunkToArticleAggregator.java`
- `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`
- `src/test/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchServiceTests.java`
- `src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java`
- `src/test/java/com/xbk/lattice/query/service/ChunkVectorSearchServiceTests.java`
- `src/test/java/com/xbk/lattice/query/service/ChunkToArticleAggregatorTest.java`

未修改 `AnswerGenerationService`、fallback outcome、evidence selector、terminal field alias、Q6 题集或 Q6 报告。未修改 schema，未清库，未重建库，未重新导入资料。

## 根因对应关系

agentB 只读报告给出的主根因是：article chunk FTS 已经召回目标 chunk，但命中在 query hit 层被包装为 `QueryEvidenceType.ARTICLE`，RRF 融合 key 又按 `ARTICLE:articleKey` 折叠，导致 chunk/anchor 级命中失去独立席位，不能作为弱标题切分块排到前列并展示。

本轮修复直接对应该根因：

- chunk 级通道写入通用 `chunkIdentity`、`chunkIndex`、`sectionAnchor`、`channel` 元数据。
- RRF 对带 `chunkIdentity` 的 ARTICLE hit 使用 chunk 级 key，避免与整篇 article 命中按同一 `articleKey` 折叠。
- 普通 article 命中未带 `chunkIdentity` 时仍沿用原 articleKey/conceptId 融合逻辑。

## 最小修复点

1. 新增 `ChunkHitIdentitySupport`，统一负责 chunk identity 生成/读取、chunk metadata 增强、Markdown heading / source-ref heading 的通用 anchor 提取、展示标题拼接。
2. `ArticleChunkFtsSearchService` 在 article chunk FTS 命中转 `QueryArticleHit` 时保留 chunk identity，并将展示标题通用化为 `articleTitle / sectionAnchor`。
3. `ChunkToArticleAggregator` 不再把同一 article 下的不同 chunk vector 命中聚合成同一个 article 席位，而是按 `ARTICLE_CHUNK:<article-or-concept>#<chunkIndex>` 保留 chunk 级身份；同一 chunk 的重复命中仍只保留最高分。
4. `RrfFusionService` 在 build key 时优先读取 `chunkIdentity`，让 chunk 级 ARTICLE hit 与整篇 ARTICLE hit 分离；普通 article hit 行为保持不变。

## 为什么不是 S2 / 下一步计划特判

生产代码没有写入 S2、具体标题、具体文件名、具体业务术语、具体问题问法或具体答案片段。anchor 提取只使用两类通用文本结构：

- Markdown 标题行：`#` 到 `######`。
- 通用 source ref 形态：方括号引用中最后一个逗号后的 heading 候选。

测试数据使用 `Release Guide`、`Deployment Plan`、`Article Alpha`、`Section One/Two` 这类通用样本，仅验证 chunk identity、anchor 展示和 RRF 融合语义，不作为生产逻辑判断依据。

## chunk identity 如何避免 article 折叠

修复前：

- article chunk FTS 命中 evidence type 仍是 `ARTICLE`。
- RRF key 近似为 `ARTICLE:<articleKey>`。
- 同一 article 的整篇命中与 chunk 命中融合成一个席位，chunk/anchor 解释信息容易丢失。

修复后：

- chunk 命中 metadata 中保留 `chunkIdentity`，形态为 `ARTICLE_CHUNK:<articleKey-or-conceptId>#<chunkIndex>`。
- RRF 遇到 `QueryEvidenceType.ARTICLE` 且 metadata 带 `chunkIdentity` 时，使用 `ARTICLE:<chunkIdentity>` 作为融合 key。
- 普通 article 命中没有 `chunkIdentity`，仍使用 `ARTICLE:<articleKey>` 或 `ARTICLE:<conceptId>`，保留 article 背景命中的原有合并收益。

## anchor/title 展示如何通用化

chunk 命中优先从 chunk text 中提取 section anchor：

- 如果 chunk text 中存在 Markdown heading，则取第一个 heading 文本。
- 如果存在通用 source ref 行，则取 ref 中的 heading 候选。
- 如果无法提取，则保持原 article title。

展示标题采用通用拼接规则：有 article title 且有不同的 section anchor 时展示为 `articleTitle / sectionAnchor`；否则保持原 title 或 anchor。metadata 同步暴露 `sectionAnchor`，便于搜索 API 或后台 UI 展示更可解释的 chunk/anchor 线索。

## 新增/修改测试清单

- `ArticleChunkFtsSearchServiceTests.shouldExposeChunkIdentityAndSectionAnchor`
  - 覆盖 article chunk FTS 命中保留 `chunkIndex`、`chunkIdentity`、`sectionAnchor` 和 channel。
  - 覆盖搜索结果 title 展示为 article title + section anchor。
- `WeightedRrfFusionTest.shouldKeepArticleChunkHitSeparateFromArticleHit`
  - 覆盖 chunk 级命中与同一 article 的整篇命中不被折叠。
- `WeightedRrfFusionTest.shouldStillMergePlainArticleHitsByArticleKey`
  - 覆盖非 chunk article 命中仍按 articleKey 正常融合。
- `ChunkVectorSearchServiceTests.shouldSearchChunkVectorHitsWithSharedEmbedding`
  - 补强 chunk vector 搜索结果 metadata 中的 `chunkIdentity` / `chunkIndex` 断言。
- `ChunkToArticleAggregatorTest.shouldKeepDifferentChunksAsIndependentHits`
  - 覆盖同一 article 下不同 chunkIndex 保留独立席位。
- `ChunkToArticleAggregatorTest.shouldUseBestHitWhenSameChunkAppearsMoreThanOnce`
  - 覆盖同一 chunk 重复命中仍按最高分聚合。

## redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：通过，退出码 0。`special_cases_report.md` 汇总为：

- `BLOCKER=0`
- `REVIEW=2030`
- `ALLOWLIST=259`

本轮新增 helper 相关 REVIEW：

- `ChunkHitIdentitySupport.java:159`：通用 title 与 anchor 相等判断。
- `ChunkHitIdentitySupport.java:269`：JSON metadata 解析失败时返回空 JSON 对象。

上述均为通用结构处理，不包含业务 case 特判。

## 定向测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
  -Dtest=WeightedRrfFusionTest,ArticleChunkFtsSearchServiceTests,ChunkVectorSearchServiceTests,ChunkToArticleAggregatorTest test
```

结果：通过。

- `Tests run: 13`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

## 全量 Maven 结果

第一次全量 Maven 在代码修复后失败于旧测试语义：

- `ChunkToArticleAggregatorTest.shouldUseBestChunkWhenAggregatingSameArticle` 仍期望同一 article 下不同 chunk 折叠为 1 条。
- 该断言与本轮 chunk identity 修复目标冲突，已改为覆盖“不同 chunk 独立、同一 chunk 去重取最高分”。

修正测试后重新运行：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：通过。

- `Tests run: 921`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`
- `BUILD SUCCESS`
- 总耗时：`06:56 min`
- 完成时间：`2026-05-27T22:23:30+08:00`

## 硬编码扫描结果

全量扫描命令：

```bash
rg -n "S2|下一步计划|probe-and-incident-operations|Kubernetes 探针与事件响应协同手册|tcp-liveness-readiness|8080" src/main/java src/test/java
```

结果：有既存命中，均非本轮新增或修改文件中的生产逻辑；包括编译/title profile 相关既存代码与历史测试、后台样例测试、端口类工程测试。按本轮约束未修改这些文件。

本轮变更文件聚焦扫描命令：

```bash
rg -n "S2|下一步计划|probe-and-incident-operations|Kubernetes 探针与事件响应协同手册|tcp-liveness-readiness|8080" \
  src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java \
  src/main/java/com/xbk/lattice/query/service/ChunkHitIdentitySupport.java \
  src/main/java/com/xbk/lattice/query/service/ChunkToArticleAggregator.java \
  src/main/java/com/xbk/lattice/query/service/RrfFusionService.java \
  src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java \
  src/test/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchServiceTests.java \
  src/test/java/com/xbk/lattice/query/service/ChunkVectorSearchServiceTests.java \
  src/test/java/com/xbk/lattice/query/service/ChunkToArticleAggregatorTest.java
```

结果：无输出，退出码 1。本轮生产代码与新增/修改测试未写入上述特判词。

## 是否建议 agentD 做清库重建/完整验收

建议 agentD 执行完整端到端验收，但不建议把清库重建视为本轮代码修复的必要前置。

原因：

- 本轮是 query/search 结果身份治理，不涉及 schema、编译链路或资料重建。
- 单元测试和全量 Maven 已覆盖 chunk identity / RRF 融合语义。
- S2 真实效果仍需要在完整知识库验收链路中验证搜索 API 排序、展示字段、Q1-Q12、S1-S4 以及 Q6 保护场景。

建议 agentD 后续验证：

- redline `BLOCKER=0`
- 全量 `mvn test`
- clean rebuild / 完整知识库导入后的 Q1-Q12、S1-S4
- Q6 结构化路径保护场景，确认本轮未影响 query/fallback 闭环

## Git 状态说明

本轮未 stage、未 commit、未 push。
