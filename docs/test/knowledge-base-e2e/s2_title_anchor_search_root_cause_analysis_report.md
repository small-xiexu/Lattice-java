# S2 标题/anchor 搜索失败根因分析报告

## 结论

- 分析时间：2026-05-27 20:39:28 CST (+0800)
- 只读声明：本轮身份为 agentB，只读分析；除新增本报告外，未修改代码、测试、配置、脚本、题集、数据库或模型配置，未 stage、未 commit、未 push。
- 唯一主根因：`rerank 排序低`，更精确地说是 **chunk 级命中在融合/返回前被 article 身份折叠，导致目标 anchor chunk 虽已召回，却不能作为独立“下一步计划”切分块排到前列并展示**。
- 是否建议修复：建议 agentA 后续做通用 chunk/anchor 搜索结果身份治理；不建议先清库重建，也不建议写 `S2` / `下一步计划` 特判。

## S2 题集定义

| 项 | 内容 |
|---|---|
| 位置 | `docs/test/knowledge-base-e2e/eval/question-set.md` |
| 搜索词 | `下一步计划` |
| 搜索维度 | 按 `anchorTitle` 搜索 |
| 期望 | 命中对应弱标题切分条目；不要求显示为“下一步计划”，但应能定位到这段内容 |
| 失败口径 | `S2/S3` 任一失败，都判定标题链路仍未验收通过 |

目标资料为 `01_markdown/probe-and-incident-operations.md` 中的 `## 下一步计划` 段。该段核心内容是：先从最小场景落地三种 probe 与简化事件响应清单，再通过人工演练验证角色判定、记录、对外同步和严重级别升级。

## 当前搜索复现结果

当前 `18082` 服务未运行，`curl http://127.0.0.1:18082/actuator/health` 连接失败；本轮没有启动服务、没有重建库、没有重导资料。复现基于当前库中已有检索审计与只读 SQL。

最近一次 `下一步计划` 检索审计：

| run_id | question | retrieval_question | rewrite_applied | fused_hit_count | channel_count |
|---:|---|---|---|---:|---:|
| 44 | `下一步计划` | `下一步计划` | `false` | 5 | 11 |
| 19 | `下一步计划` | `下一步计划` | `false` | 5 | 11 |

run `44` TopN：

| fused_rank | channel | evidenceType | title | source | metadata |
|---:|---|---|---|---|---|
| 1 | `fts` / `article_chunk_fts` / `article_vector` / `chunk_vector` | `ARTICLE` | `Kubernetes 探针与事件响应协同手册` | `01_markdown/probe-and-incident-operations.md` | `article_chunk_fts` 命中 `chunkIndex=2/1` |
| 2 | `article_vector` / `chunk_vector` | `ARTICLE` | `incident checklist` | `04_office/incident-response-checklists-lite.xlsx` | 非目标 |
| 3 | `source_chunk_fts` | `SOURCE` | `01_markdown/probe-and-incident-operations.md` | 同上 | `chunkIndex=0` |
| 4 | `fact_card_fts` | `FACT_CARD` | `结构化规则约束 - 01_markdown/...#0` | 同上 | 背景 fact card |
| 5 | `fact_card_fts` | `FACT_CARD` | `结构化键值条目 - 01_markdown/...#0` | 同上 | 背景 fact card |

判定：目标段落已召回；失败不是“搜不到”，而是搜索结果仍以整篇文章或文件级候选展示，未稳定定位为弱标题切分块。

## 数据库只读审计

当前库规模：

| 表 | 数量 |
|---|---:|
| `source_files` | 6 |
| `articles` | 6 |
| `article_chunks` | 13 |
| `source_file_chunks` | 6 |
| `fact_cards` | 11 |
| `article_vector_index` | 6 |
| `article_chunk_vector_index` | 13 |

目标文章存在：

| article_key | title | review_status | lifecycle | analysisMode |
|---|---|---|---|---|
| `default-source--01-markdown-probe-and-incident-operations` | `Kubernetes 探针与事件响应协同手册` | `passed` | `ACTIVE` | `LIGHTWEIGHT_SMALL_DOC` |

目标 chunk 存在：

| 表 | 命中 |
|---|---|
| `article_chunks` | `chunkIndex=2` 与 `chunkIndex=1` 均包含 `[→ 01_markdown/probe-and-incident-operations.md, 下一步计划]` |
| `source_file_chunks` | `chunkIndex=0` 包含原文 `## 下一步计划`，但整份 Markdown 只有 1 个 source chunk |
| `fact_cards` | 当前 3 张 Markdown fact card 不包含 `下一步计划`，不构成目标 anchor 证据 |

titleProfile 状态：

```json
{
  "anchorTitle": "Kubernetes 探针与事件响应协同手册",
  "sourceTitle": "Kubernetes 探针与事件响应协同手册",
  "representativeTitle": "Kubernetes 探针与事件响应协同手册",
  "titleGenerationMode": "ANCHOR_DIRECT",
  "titleGenerationConfidence": "HIGH",
  "titleGenerationVersion": "v1"
}
```

`titleProfile` 已存在，且已进入 article 搜索文本构建逻辑；但它保存的是文档级标题，不是弱标题 `下一步计划`。

search_text / tsvector 状态：

| 对象 | 是否包含 `下一步计划` | 是否命中 tsvector | 说明 |
|---|---:|---:|---|
| `articles.search_text/search_tsv` | 是 | 是 | 来自文章正文/引用文本，不是 titleProfile anchor |
| `article_chunks.search_tsv` | 是 | 是 | `chunkIndex=2/1` 均命中目标段 |
| `source_files.search_tsv` | 是 | 是 | 源文件全文命中 |
| `source_file_chunks.search_tsv` | 是 | 是 | 仅整文件 `chunkIndex=0` |
| `fact_cards.search_tsv` | 否 | 否 | fact card 不是 S2 目标载体 |

## 代码链路只读审计

| 链路 | 证据 | 判断 |
|---|---|---|
| titleProfile 入 article search_text | `ArticleJdbcRepository.buildArticleSearchText()` 会拼入 `sourceTitle/anchorTitle/representativeTitle` | article 级标题画像索引逻辑存在 |
| 小文档标题生成 | `AnalyzeNode.buildLightweightSmallDocConcept()` 将 section heading 设为文档标题，`resolveLightweightTitle()` 直接取 source title | 当前 Markdown 作为 `LIGHTWEIGHT_SMALL_DOC`，没有物化 `## 下一步计划` 为独立 article titleProfile |
| article chunk FTS | `ArticleChunkMapper.searchLexical` 返回 `a.article_key`、`a.title`、`ac.chunk_text`、`ac.chunk_index` | chunk 可召回，但 title 仍是 article title |
| article chunk 服务 | `ArticleChunkFtsSearchService.toQueryArticleHit()` 把 chunk 命中包装为 `QueryEvidenceType.ARTICLE`，chunkIndex 只放进 metadata | chunk 身份没有成为独立 evidence identity |
| RRF 融合 | `RrfFusionService.buildHitKey()` 使用 `evidenceType + articleKey/conceptId` 去重 | 同一 article 的 chunk 命中、article FTS、article vector、chunk vector 被折叠成同一 ARTICLE |
| 搜索 API | `SearchController` 原样返回 `QueryArticleHit.title/content/metadataJson` | 管理端搜索接口不是主因，返回的是上游融合后的 article/file 级候选 |

## 唯一主根因

主根因归类：`rerank 排序低`。

这里的“排序低”不是普通分数不足，而是融合身份导致的排序/展示失败：

1. `article_chunk_fts` 已在原始通道 rank 1/2 命中目标 chunk。
2. 命中被包装成 `ARTICLE`，`articleKey` 与整篇文章一致。
3. RRF 用 `ARTICLE:articleKey` 去重，保留的展示 title 是 `Kubernetes 探针与事件响应协同手册`。
4. 搜索结果 Top1 因多通道叠加仍是目标文章，但不是题集要求的 anchor/chunk 级定位结果。
5. Top2 被向量通道的 `incident checklist` 占据，进一步说明列表层没有给目标 chunk 独立席位。

## 非根因排除项

| 候选根因 | 判定 | 理由 |
|---|---|---|
| 资料缺失 | 排除 | 源文件存在 `## 下一步计划`，article/source chunk 均含目标文本 |
| 编译抽取缺失 | 非主因 | 目标段进入 article 内容与 chunk；但弱标题未物化为独立 article titleProfile，是次要放大因素 |
| chunk 切分问题 | 排除 | `article_chunks` 已有 `chunkIndex=2/1` 命中目标段；source chunk 只有整文件，但不是唯一检索通道 |
| 标题画像已生成但未入索引 | 排除 | article titleProfile 已生成且会进入 search_text，只是内容为文档标题 |
| anchorTitle 未进入 chunk/search_text | 非主因 | `下一步计划` 已进入 chunk/search_tsv；缺的是 anchor/chunk 级结构身份，不是文本完全没入索引 |
| 检索未召回 | 排除 | `article_chunk_fts`、`source_chunk_fts`、`article_fts` 均召回目标资料 |
| 数据未重建导致新字段未生效 | 排除 | 当前库已有 `titleProfile`，并且 `articles.created_at=2026-05-26`；新字段已在数据中体现，只是语义粒度不满足 S2 |
| 验收口径过期 | 排除 | 题集明确要求 `anchorTitle` 搜索能定位弱标题切分条目；当前能力确实没有返回该粒度 |
| 管理端搜索接口展示问题 | 排除 | API 只透传融合后的 `QueryArticleHit`；上游已经把 chunk 命中折叠为 article/file 级候选 |
| Q6 terminal field alias | 排除 | S2 查询无字段 alias、无 exact path、无 Q6 fallback 参与 |

## 风险判断与建议

是否需要改代码：需要，但应在 agentA 独立轮次处理，且只做通用搜索/索引能力修复。

agentA 最小通用修复范围建议：

| 范围 | 建议 |
|---|---|
| `ArticleChunkFtsSearchService` / chunk 命中模型 | 让 article chunk 命中保留可区分的 chunk identity，例如 `articleKey#chunkIndex`，并在 metadata/title 中暴露通用 section/anchor 信息 |
| `RrfFusionService.buildHitKey()` | 对 chunk 级通道使用 chunk identity 去重，避免和整篇 article 折叠；同时保留 article 背景命中的合并收益 |
| `ArticleChunkMapper.searchLexical` / chunk 展示字段 | 优先返回通用可解释标题：section heading、anchor title、source ref heading、或 chunk metadata；不要写样例词 |
| 可选后续：编译侧 chunk metadata | 为 chunk 持久化通用 section/anchor/title 元数据；当前 `article_chunks` 无 metadata 列，若要长期稳定支持 anchorTitle 搜索，需要补结构化落点 |

为什么不是 `下一步计划` 特判：修复点只处理“chunk 级证据如何保留身份、标题和展示席位”的通用问题，适用于任意 Markdown/PDF/Office 的章节标题、source ref heading 或 chunk anchor；不依赖 S2 查询词、文件名、业务术语或答案片段。

是否需要清库重建才能验证：

- 不建议先清库重建来当作修复手段；当前库已有 titleProfile 和目标 chunk，失败不是新字段未生效。
- 代码修复后需要 agentD 清库重建或至少重建 chunk/title/vector 索引做端到端验收，因为如果新增 chunk metadata 或改变索引字段，旧数据不会自动拥有新结构。

是否影响 Q6 / query fallback：

- 不应触碰 `AnswerGenerationService`、fallback outcome、terminal field alias 或 Q6 exact path 主链。
- 风险主要在 `KnowledgeSearchService` 搜索结果与 RRF 融合候选身份；需回归 Q1-Q12、S1-S4、Q6 保护场景，确认结构化 fact card 与 source chunk guardrail 未被 chunk identity 改动冲掉。

下一步建议：

1. 不把 S2 归因到 Q6，Q6 保持已闭环。
2. 先由 agentA 做一轮最小通用 chunk/anchor 搜索身份修复设计与实现。
3. agentD 再执行 redline、`mvn test`、清库重建/重导资料、S1-S4 与 Q1-Q12 验收。

## 未做事项

- 未读取、输出或修改私有模型配置文档。
- 未修改 `src/main/java/**`。
- 未修改 `src/test/java/**`。
- 未修改 `src/main/resources/**`。
- 未修改 `scripts/**`。
- 未修改数据库、未清库、未重建库、未重新导入资料。
- 未重新配置模型。
- 未 stage、未 commit、未 push。
