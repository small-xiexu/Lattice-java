# Compile Review Query Visibility Filter Analysis

## 结论

当前 Query 检索链路没有统一的 article 可见性 hard filter。5 条 article-backed 通道都会读取 `review_status`，但 SQL 不限制 `review_status='passed'`，也不限制 `lifecycle='ACTIVE'`。

现有 query 侧治理主要是“排序 / 降权 / 证据层级调整”，不是不可见门禁。如果历史库或人工操作已产生非 passed 或非 ACTIVE article，只要它们仍在 `articles/article_chunks/article_vector_index/article_chunk_vector_index` 中，当前 Query 可能召回。

`source_files/source_file_chunks` 与 `fact_cards` 不适合直接套 article review_status：source 是原始信源层，表中无 article review_status；fact card 有自己的 `review_status` 策略，当前仅排除 `conflict`，`low_confidence` 仍可作为候选并可作为结构化主证据。

下一轮如补 Query visibility hard filter，建议只处理 article-backed 五条通道，暂不碰 source / fact_card。

## 门禁与工作区

| 项 | 结果 |
|---|---|
| redline BLOCKER | 0 |
| redline REVIEW | 1852 |
| redline ALLOWLIST | 239 |
| `git status --short --branch` | 当前工作区已有 agentA 的 `PersistArticlesNode.java` 改动、`special_cases_report.md` 改动，以及若干 compile review 报告；本轮未触碰生产代码 |
| `git diff --stat` | 写报告前显示 `special_cases_report.md` 与 `PersistArticlesNode.java` 有 diff，属于既有 persist gate 主线 |
| 本轮是否跑 baseline / compile / 清库 / 重建 | 否 |
| 本轮是否修改代码 | 否 |

## 当前数据库只读状态

| 对象 | 当前状态 |
|---|---|
| `articles` | `passed / ACTIVE`：4 |
| `article_chunks` | 对应 `passed / ACTIVE`：19 |
| `article_vector_index` | 对应 `passed / ACTIVE`：4 |
| `article_chunk_vector_index` | 对应 `passed / ACTIVE`：19 |
| `fact_cards` | `low_confidence`：14 |
| `source_file_chunks` | 列为 `id,source_file_id,file_path,chunk_index,chunk_text,is_verbatim,indexed_at,file_path_norm,search_tsv`，无 article `review_status/lifecycle` |

当前样本没有非 passed / 非 ACTIVE article，因此不能用当前库证明可见性 bug 已发生；代码路径证明风险存在。

## Article-Backed 通道

| 通道 | 入口 / SQL | 当前 hard filter | 结论 |
|---|---|---|---|
| article FTS | `ArticleFtsSearchMapper.xml`，`from articles a where a.search_tsv @@ query.tsq` | 无 `a.review_status='passed'`；无 `a.lifecycle='ACTIVE'` | 可能召回非 passed / 非 ACTIVE |
| article refKey | `RefKeySearchMapper.xml`，`from articles a where false or lower(...) like ...` | 无 review/lifecycle 条件 | 可能召回 |
| article chunk lexical | `ArticleChunkMapper.searchLexical`，`from article_chunks ac join articles a on a.id=ac.article_id` | 无 review/lifecycle 条件 | 可能召回 |
| article vector | `ArticleVectorMapper.searchNearestNeighbors`，`article_vector_index join articles` | 无 review/lifecycle 条件 | 可能召回 |
| article chunk vector | `ArticleChunkVectorMapper.searchNearestNeighbors`，`article_chunk_vector_index join articles join article_chunks` | 无 review/lifecycle 条件 | 可能召回 |

补充判断：

- 这些通道都能把 `review_status` 带入 `QueryArticleHit` 或聚合后的 hit。
- 但 `lifecycle` 当前没有进入 `QueryArticleHit`，所以在 service 层只靠现有对象无法检查 `ACTIVE`。
- 因此如果要做最小 hard filter，mapper SQL 比 service 过滤更小、更直接。

## 排序 / 降权逻辑

| 位置 | 当前行为 | 是否 hard filter |
|---|---|---|
| `KnowledgeSearchService.applyReviewQualityGuardrail` | 把 passed article 排在前面；如果存在 non-passed，仍追加返回；如果没有 passed，原样返回全部 fused hits | 否 |
| `QueryHitIntentReranker.reviewQualityBonus` | `passed` 加分；`needs_human_review` 降权；其他非 passed 降权 | 否 |
| `RrfFusionService.evidenceTier` | fact card `needs_human_review` 降为背景层级；article 仍可保留 | 否 |
| `AnswerGenerationFallbackSnippetSupport` | 仅基于正文中 `review_status: needs_human_review` 做 fallback snippet 处理 | 否 |

这些逻辑会影响排序、证据层级或摘要，但不会阻止非 passed article 被检索、融合、审计或进入 prompt 候选。

## Raw Source 通道

| 通道 | 现状 | 是否适合套 article review_status |
|---|---|---|
| `source_files` | `SourceFileMapper.searchLexical` 查询原始源文件表，表中无 article `review_status/lifecycle` | 不适合 |
| `source_file_chunks` | `SourceFileChunkMapper.searchLexical` 查询原始 source chunk，表中无 article `review_status/lifecycle` | 不适合 |

原因：

- source/source_chunk 是编译输入材料，不是由 writer 生成的 article。
- source chunks 在 article review 前已经持久化，还会用于 fact card 生成、source evidence 和 query 证据回指。
- 直接按 article status 过滤 raw source 会混淆“原始资料可见性”和“生成文章审查状态”，并可能损伤现有 SWIP / baseline 中依赖 source evidence 的链路。

## Fact Card 通道

fact card 有自己的 review status，不等同于 article review_status。

| fact card 状态 | `allowsQueryCandidate` | `allowsPrimaryEvidence` | 当前影响 |
|---|---:|---:|---|
| `valid` | 是 | 是 | 正常候选 |
| `incomplete` | 是 | 是 | 正常候选 |
| `low_confidence` | 是 | 是 | 分数乘 0.45，仍可作为结构化主证据 |
| `needs_human_review` | 是 | 否 | 分数乘 0.20，背景使用 |
| `conflict` | 否 | 否 | 被排除 |
| 空 / 未知 | 按 `low_confidence` 处理 | 是 | 可进入候选 |

当前 fact card SQL 本身不筛状态；`FactCardFtsSearchService` 和 `FactCardVectorSearchService` 在 service 层应用 `FactCardReviewUsagePolicy`。因此 fact card 已有独立策略，不应在本轮建议中直接套 article `passed/ACTIVE`。

## 风险判断

| 场景 | 当前是否可能被 Query 召回 | 说明 |
|---|---|---|
| 历史库中存在 `review_status != passed` 的 article | 可能 | 5 条 article-backed 通道 SQL 都未过滤 |
| 历史库中存在 `lifecycle != ACTIVE` 的 article | 可能 | 5 条 article-backed 通道 SQL 都未过滤 |
| 非 passed article 已有 article chunks | 可能 | chunk lexical / chunk vector 会 join article，但不筛状态 |
| 非 passed article 已有 vector index | 可能 | vector SQL 不筛状态 |
| `low_confidence` fact card | 可能 | 当前策略允许候选和主证据，仅降权 |
| source/source_chunk | 可能 | 这是 raw source 设计，不由 article review_status 管控 |

## 下一轮是否建议修代码

建议修，但前提是 persist gate 主线先完成并验证。

建议原因：

- Persist gate 防止新非 passed article 进入正式表，是第一道门。
- Query visibility filter 是第二道防线，防止历史脏数据、人工写入、旧版本残留或配置绕过继续被 Query 召回。
- 当前数据库全是 `passed/ACTIVE`，对当前 SWIP clean 库理论上应是 no-op；但要避免碰 source/fact_card，以减少 eval 扰动。

## 下一轮最小修复范围

只建议改 article-backed mapper SQL，暂不改 service、不改 source/fact_card、不改题集。

最小文件范围：

- `src/main/resources/com/xbk/lattice/query/service/mapper/ArticleFtsSearchMapper.xml`
- `src/main/resources/com/xbk/lattice/query/service/mapper/RefKeySearchMapper.xml`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkMapper.xml`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleVectorMapper.xml`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkVectorMapper.xml`

目标语义：

- 所有 article-backed 查询只返回 `review_status='passed' AND lifecycle='ACTIVE'` 的 article。
- 不改变 `QueryArticleHit` 结构。
- 不改变 `KnowledgeSearchService.applyReviewQualityGuardrail`，避免同轮混入排序行为变更。
- 不改变 fact card 策略。
- 不改变 source/source_chunk 检索。

## 如何避免影响 baseline / SWIP

- 第一轮只过滤 article-backed 通道，不碰 source/fact_card；当前 SWIP 依赖较多 source chunk / fact card 证据，不能一并收紧。
- 当前库中 article-backed 数据全是 `passed/ACTIVE`，该改动在当前数据上应不改变候选集合。
- 验证顺序应为：redline -> `mvn test` -> 针对 query visibility 的合成数据测试 -> 再由验证轮决定是否跑 SWIP strict eval。
- 不通过放宽题集或修改 baseline 追结果。
- 不写业务域、文档、术语、题目、答案片段特判。

## 本轮修改说明

本轮只新增本报告；未修改源码、测试、配置、脚本、题集、数据库，未运行 baseline、compile、清库或重建。
