# S2 anchorTitle 搜索失败分析报告

## 1. 问题复现

- 复现请求：`GET http://127.0.0.1:18082/api/v1/search?question=下一步计划`
- 实际前 5 条返回如下：

| Rank | evidenceType | title | sourcePaths | score |
|---|---|---|---|---:|
| 1 | ARTICLE | Kubernetes 探针与事件响应协同手册 | `01_markdown/probe-and-incident-operations.md` | 0.11019368059227923 |
| 2 | ARTICLE | incident checklist | `04_office/incident-response-checklists-lite.xlsx` | 0.04597030209933436 |
| 3 | SOURCE | 01_markdown/probe-and-incident-operations.md | `01_markdown/probe-and-incident-operations.md` | 0.021311475409836068 |
| 4 | FACT_CARD | 结构化规则约束 - 01_markdown/probe-and-incident-operations.md#0 | `01_markdown/probe-and-incident-operations.md` | 0.022950819672131147 |
| 5 | FACT_CARD | 结构化键值条目 - 01_markdown/probe-and-incident-operations.md#0 | `01_markdown/probe-and-incident-operations.md` | 0.02258064516129032 |

## 2. 现象与实际返回

- `S2` 失败不是“完全没召回到目标资料”。
- 最近一次检索审计 `run_id=41` 显示：
  - `article_chunk_fts` 命中了 2 条
  - `source_chunk_fts` 命中了 1 条
  - `fts` 命中了 1 条
  - `article_vector` 命中了 5 条
  - `chunk_vector` 命中了 3 条
- 其中 `article_chunk_fts` 的 `hit_rank=1` 已经命中 `chunkIndex=2`，该 chunk 正是含有“下一步计划”段落的文章分块。
- 但融合后它没有以“弱标题切分块”的独立身份出现，而是和整篇文章折叠为同一个 `ARTICLE` 候选，最终 Top1 仍显示整篇文档标题 `Kubernetes 探针与事件响应协同手册`。

## 3. 数据层证据

### 3.1 正式文章里不存在 `anchorTitle=下一步计划`

- `articles` 当前 6 篇正式文章中，Markdown 对应文章为：
  - `article_key=01-markdown--01-markdown-probe-and-incident-operations`
  - `title=Kubernetes 探针与事件响应协同手册`
  - `analysisMode=LIGHTWEIGHT_SMALL_DOC`
  - `titleProfile.anchorTitle=Kubernetes 探针与事件响应协同手册`
  - `titleProfile.sourceTitle=Kubernetes 探针与事件响应协同手册`
  - `titleProfile.representativeTitle=Kubernetes 探针与事件响应协同手册`
- 数据库统计结果：
  - `articles.title like '%下一步计划%' = 0`
  - `articles.metadata_json::text like '%下一步计划%' = 0`
  - `articles.content like '%下一步计划%' = 1`
- 进一步看该文章：
  - `position('下一步计划' in title)=0`
  - `position('下一步计划' in metadata_json::text)=0`
  - `position('下一步计划' in search_text)>0`
  - `position('下一步计划' in content)>0`
- 结论：`下一步计划` 只作为正文词出现，没有作为 article 的 `anchorTitle / representativeTitle / sourceTitle` 落库。

### 3.2 目标内容确实存在于可检索候选中

- `article_chunks` 中：
  - `chunk_index=2` 明确包含“后续落地建议先从最小场景开始 ... [→ ..., 下一步计划]`
  - `chunk_index=1` 也因 chunk overlap 含有该短语
- `source_file_chunks` 中：
  - 该 Markdown 只切出 `chunk_index=0`
  - `chunk_len=2751`，基本等于整篇原文长度
  - 因此源文件 chunk 通道本身不是标题级分块，而是整文件分块
- `source_files.metadata_json` 只包含：
  - `documentTitle`
  - `relativePath`
  - `parseMode`
  - `contentFormat`
  - `parseProvider`
  - `ocrApplied`
- 结论：目标段落存在于 chunk 候选里，但没有以标题级元数据单独物化。

## 4. 代码链路证据

### 4.1 查询改写层不是根因

- `query_retrieval_runs.run_id=41` 显示：
  - `question=下一步计划`
  - `retrieval_question=下一步计划`
  - `rewrite_applied=false`
- 结论：不是查询改写把问题改坏了。

### 4.2 article 级搜索其实会搜 `titleProfile`

- [`src/main/java/com/xbk/lattice/infra/persistence/ArticleJdbcRepository.java`](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/infra/persistence/ArticleJdbcRepository.java:263) 会把 `title / summary / content / metadata_json / titleProfile(sourceTitle, anchorTitle, representativeTitle)` 一起写进 `search_text`。
- [`src/main/resources/com/xbk/lattice/query/service/mapper/ArticleFtsSearchMapper.xml`](/Users/sxie/xbk/Lattice-java/src/main/resources/com/xbk/lattice/query/service/mapper/ArticleFtsSearchMapper.xml:22) 直接检索 `articles.search_tsv`。
- 结论：如果 `anchorTitle` 真的落成“下一步计划”，article FTS 本来就能搜到；当前搜不到，不是因为 article FTS 完全不看 `anchorTitle`。

### 4.3 编译抽取层把小文档标题固定成文档标题

- [`src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java`](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java:306) 对这类资料走 `LIGHTWEIGHT_SMALL_DOC` 路径。
- 同文件 [`resolveLightweightTitle(...)`](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java:364) 直接返回 `resolveSourceTitleCandidate(rawSource)`。
- `resolveSourceTitleCandidate(...)` 优先拿 `documentTitle` / 文件名，不会把弱标题“下一步计划”作为该 article 的 `anchorTitle`。
- 结论：编译结果天然只有“文档级标题画像”，没有“弱标题级标题画像”。

### 4.4 article chunk 通道已经召回目标 chunk，但在输出前被折叠成 article

- [`src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkMapper.xml`](/Users/sxie/xbk/Lattice-java/src/main/resources/com/xbk/lattice/infra/persistence/mapper/ArticleChunkMapper.xml:122) 查询 `article_chunks` 时：
  - `item_key = a.article_key`
  - `title = a.title`
  - `content = ac.chunk_text`
  - `chunk_index` 只是附带字段
- [`src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java`](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java:85) 又把命中包装成 `QueryEvidenceType.ARTICLE`，只把 `chunkIndex` 塞进 metadata。
- [`src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/RrfFusionService.java:492) 的 `buildHitKey` 只用 `evidenceType + articleKey/conceptId` 去重。
- 因为 `article_chunk_fts`、`fts`、`article_vector`、`chunk_vector` 对同一篇 Markdown 的命中身份键相同，它们会在融合时折叠成一个 article 结果，而不是多个 chunk 结果。

### 4.5 source chunk 通道也没有可显示的弱标题

- [`src/main/resources/com/xbk/lattice/infra/persistence/mapper/SourceFileChunkMapper.xml`](/Users/sxie/xbk/Lattice-java/src/main/resources/com/xbk/lattice/infra/persistence/mapper/SourceFileChunkMapper.xml:84) 里：
  - `item_key = file_path#chunk_index`
  - `title = sfc.file_path`
  - 没有 section heading / anchor title 字段
- [`src/main/java/com/xbk/lattice/query/service/SourceChunkFtsSearchService.java`](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/query/service/SourceChunkFtsSearchService.java:211) 只返回 `SOURCE`，title 仍是文件路径。
- 结论：即便 source chunk 召回成功，它也只能表现成“文件路径命中”，不是“弱标题块命中”。

## 5. 主根因

- **主根因层级：排序层**
- 唯一主根因是：**chunk 级命中在融合层被按 article 身份折叠，导致系统无法把“下一步计划”对应分块作为独立结果返回。**
- 直接证据：
  - `article_chunk_fts` 已经召回目标 `chunkIndex=2`
  - 但它被包装成 `QueryEvidenceType.ARTICLE`
  - `RrfFusionService.buildHitKey()` 继续按 `ARTICLE:articleKey` 去重
  - 同一篇文章又同时被 `fts / article_vector / chunk_vector` 命中，RRF 分数叠加后自然稳居 Top1
- 所以当前 `/api/v1/search` 的行为本质上是：
  - “chunk 负责召回”
  - “article 身份负责展示与融合”
  - 最终表现为“文档级主题命中”，而不是“标题级切分块命中”

## 6. 次根因

- **次根因层级：编译抽取层**
- `LIGHTWEIGHT_SMALL_DOC` 路径把这篇 Markdown 只编译成 1 个文档级 concept，并把 `documentTitle` 直接当作 `anchorTitle / representativeTitle`。
- 结果是：
  - article 层没有任何一条记录携带“下一步计划”这类弱标题画像
  - source file 层也没有 section-heading 级标题元数据
  - source chunk 还是整文件 chunk，无法成为稳定的标题级替代结果
- 这不是本次 Top1 错排的主因，但它让 article 层没有第二条可竞争的“弱标题候选”，从而放大了主根因的影响。

## 7. 不踩红线的修复方向

- 方向 1：做**通用 chunk 身份治理**
  - 让 `article_chunk_fts` / `source_chunk_fts` 在融合时保留 chunk 身份，而不是统一折叠回 article 身份。
  - 命中键至少应能区分 `articleKey + chunkIndex` 或稳定的 section identity。

- 方向 2：做**通用标题物化治理**
  - 对 `LIGHTWEIGHT_SMALL_DOC` 中存在稳定章节标题的文本，补充 section/anchor 级标题画像落点。
  - 可以落到 chunk metadata、独立 section index，或 article 元数据中的可检索 section-title 列表，但必须是通用结构信号，不是样例词表。

- 方向 3：做**通用 chunk 展示字段治理**
  - `source_chunk_fts` 和 `article_chunk_fts` 的 title 不应默认只返回文件路径或整篇 article title。
  - 应优先返回通用可解释字段，例如 section heading、anchor title、representative title 或 source ref 中的 heading。

- 方向 4：做**通用融合策略治理**
  - 当同一 article 的 direct chunk evidence 与背景 article 同时命中时，列表检索场景应允许 chunk 候选保留一个独立席位，而不是全部并回整篇文章。
  - 这属于通用召回/排序治理，不是对“下一步计划”做特判。

## 8. 暂不建议的错误修法

- 不建议给 `anchorTitle` 搜索单独写“字段 boost”并只对当前样例生效。
- 不建议把“下一步计划”、`probe-and-incident-operations` 或任何文档名、标题词写进 query rewrite / rerank / fallback 分支。
- 不建议为了当前样例，把小文档 topic gate 阈值机械下调到“刚好能切出这一篇”。
- 不建议往 `refkey_text`、`metadata_json` 手工补样例关键词来伪造命中。
- 不建议只调大 `source_chunk_weight` 或只调小 `fts/article_vector` 权重来碰运气，因为 chunk 身份仍会丢失，问题会在其他资料上复现。

是否已确认根因：是

是否建议进入代码修复：是
