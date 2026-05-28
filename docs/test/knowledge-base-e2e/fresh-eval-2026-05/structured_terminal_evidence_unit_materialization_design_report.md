# structured terminal evidence unit materialization 设计报告

## 1. 本轮结论

- 已 scoped 回退无效 query fallback 实验改动：
  - `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
  - `src/test/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilderTests.java`
- 已确认以下文件无 diff：
  - `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
  - `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java`
  - `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
  - `src/test/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilderTests.java`
- 本轮只新增本设计报告，未修改生产代码、未修改 compile/index 代码、未修改 schema、未修改资源配置、未 stage、未 commit、未 push。
- 设计方向：不要继续在 query fallback 内叠加 selector/conclusion gate；应在 compile/index 层把 structured terminal assignment 物化为独立 evidence unit，让检索阶段直接召回“一个字段值”而不是“包含很多 sibling 的大卡”。

## 2. 现有链路只读结论

| 方向 | 现状 |
|---|---|
| Fact card 生成 | `FactCardGenerationService` 读取 `source_file_chunks`，经 `FactCardGenerationWindowSupport` 合并窗口，再由 table/list/status/policy 支撑类生成 `FactCardRecord`。 |
| 结构化路径 | `KeyValueItem` 已具备 `keyPath`、`parentPath`、`pathSegments`、`contextPath`、`displayText`，并写入 `items_json.items[]`。 |
| fact_cards schema | `fact_cards` 是卡级表：一行一张卡，`items_json` 可包含多个 terminal assignment，`search_tsv` 也是卡级。 |
| FTS 入口 | `FactCardJdbcRepository.buildSearchText` 把 `cardType/answerShape/title/claim/items_json/evidence_text` 合并为一段卡级 search text；`FactCardFtsSearchService` 返回一条 `FACT_CARD` hit。 |
| vector 入口 | `FactCardVectorIndexService` 用整张卡的 `title/claim/items_json/evidence_text` 生成一个 embedding；`FactCardVectorSearchService` 返回一条 `FACT_CARD` hit。 |
| Query 消费 | `QueryGraphDefinitionBaseSupport` / `KnowledgeSearchService` 将 `fact_card_fts`、`fact_card_vector` 放入检索计划；`RrfFusionService` 以 `FACT_CARD:card_id` 作为卡级身份融合；fallback 再从整张卡内容里挑行。 |

## 3. 为什么 FACT_ENUM 大卡导致 sibling 选择困难

当前 `FACT_ENUM` 一张卡会包含多个同父或近邻 terminal assignment。检索层只能判断“这张卡相关”，不能判断“卡内哪一个 terminal value 相关”。

具体问题：

- FTS / vector 的最小召回单位是 `fact_cards.id`，不是 `items_json.items[n]`。
- 同一卡内所有 sibling 共享相同 `card_id`、source refs、review status、RRF 身份和卡级分数。
- `items_json` 作为整体文本进入 FTS / embedding，命中分数无法落到具体 `keyPath = value`。
- fallback 必须在 answer 阶段重新解析 JSON/文本并做行级启发式选择，导致选择逻辑离真实索引信号太远。
- sibling 的 descriptor 字段、名称字段、父级上下文字段可能比目标 terminalKey 更容易覆盖问题 token，最终抢占目标字段。
- citation 只能绑定到卡或源文件，不能稳定绑定到“这个 terminal assignment 来自哪一行、哪个 parentPath、哪个 value”。

这说明根因在 evidence unit 粒度，而不是 fallback gate 分数不够精细。

## 4. terminal assignment evidence unit 最小数据模型

建议新增 `fact_card_terminal_units`，并预留独立向量索引表。第一阶段可以只启用 FTS 检索，后续再接 vector/rerank/citation。

| 字段 | 含义 |
|---|---|
| `id` | terminal unit 主键。 |
| `unitId` | 稳定业务标识，建议由 `factCardId + keyPath + valueHash` 生成。 |
| `sourceFileId` | 源文件主键。 |
| `factCardId` | 所属 fact card 主键。 |
| `sourceChunkIds` | 回指 source chunks。 |
| `articleIds` | 关联背景文章，沿用 fact card 上下文。 |
| `keyPath` | 完整结构路径。 |
| `parentPath` | 父级路径，用于 sibling 分组。 |
| `terminalKey` | 末级字段名。 |
| `pathSegments` | 路径片段数组。 |
| `displayText` | 面向 evidence 的展示文本，例如通用 `keyPath = value` 形态。 |
| `value` | 末级字段值，保留原始可展示值。 |
| `normalizedValue` | 仅做通用归一化：trim、去成对引号、数字/布尔/URL/path 基础归一。 |
| `valueType` | 通用值类型：`number`、`boolean`、`string`、`url`、`path`、`version`、`date_like`、`empty` 等，只由值形态推断。 |
| `fieldLabel` | 字段展示名，来自 terminalKey、表头或源内容中的字段名。 |
| `fieldAliases` | 字段别名数组，只允许通用拆词和源内容派生。 |
| `fieldDescription` | 字段上下文短描述，来自 parentPath、表头、同一对象/行的非答案上下文、章节标题。 |
| `sourceRefsJson` | source file、source chunks、行号/块序号、raw line、section/table/sheet 等引用信息。 |
| `ftsText` | FTS 检索文本。 |
| `embeddingText` | embedding 输入文本，第一阶段可先写入但不建向量。 |
| `reviewStatus` | 继承 fact card review status，必要时可被 unit 质量检查降级。 |
| `contentHash` | unit 内容哈希，用于幂等 upsert / reindex。 |
| `searchTsv` | unit 级 FTS tsvector。 |

## 5. 字段 label / aliases / description 生成规则

所有生成规则必须只依赖源文件内容和通用结构规则，不读取 eval 题面、case id、标准答案、hidden eval，也不在 Java 主链硬编码中文字段语义。

| 来源 | 允许派生 | 禁止事项 |
|---|---|---|
| 字段名 | 原始 terminalKey、大小写拆词、snake/kebab/camel 拆词、点路径片段组合。 | 禁止把某个中文业务词硬映射成某个英文 key。 |
| 表头 | Markdown/CSV/Excel 表头原文、列名归一化、行内主键列上下文。 | 禁止把某个评测问题里的说法补成 alias。 |
| 源文件结构 | section 标题、sheet/table name、parentPath、contextPath、同一 object/row 的通用 descriptor。 | 禁止把文件名、题集名、case 标识当作字段 alias；路径只做 source ref。 |
| 值形态 | number/boolean/url/path/version/date-like 等通用 valueType。 | 禁止根据具体业务字段名决定 valueType。 |
| 上下文描述 | `parentPath + fieldLabel + valueType + 局部结构上下文` 的短句。 | 禁止生成答案模板或针对某份资料的结论文案。 |

建议规则：

- `fieldLabel` 优先取源内容中的列名或 terminalKey 原文；没有表头时使用 terminalKey。
- `fieldAliases` 只做确定性变体：原字段、拆词字段、下划线/短横线/空格连接、完整 keyPath、末级 terminalKey、父路径末段 + terminalKey。
- `fieldDescription` 控制长度，最多包含 parentPath、章节/表格标题、相邻 descriptor 字段；descriptor 来自源内容，不写入业务词白名单。
- `ftsText` 包含 `fieldLabel`、`fieldAliases`、`keyPath`、`parentPath`、`displayText`、`value`、`valueType`、短上下文。
- `embeddingText` 使用更自然但仍通用的结构：字段标签、路径、父级上下文、值、源内短上下文；避免整张大卡 JSON。

## 6. 新增表还是复用现有 channel

结论：建议新增 `fact_card_terminal_units`。第一阶段检索返回仍可投影成 `QueryEvidenceType.FACT_CARD`，通过 metadata 携带 `terminalUnitId/unitId/keyPath/valueType`，减少 answer/fallback/citation 之外的改动面。

不建议复用：

| 复用对象 | 问题 |
|---|---|
| `fact_cards` | 一行一张 terminal 会污染卡级统计、review 语义和 fact card 生命周期；继续用 `card_id` 作为主身份也容易与原卡冲突。 |
| `article_chunks` | 该表语义是已审查文章分块，terminal unit 是结构化证据，不应伪装成文章正文。 |
| `source_file_chunks` | 该表语义是原始源文件分块，terminal unit 是编译产物；写回会污染 source ingest 与原文证据边界。 |

建议结构：

- 新增 `fact_card_terminal_units`：FTS、source refs、unit metadata。
- 第二阶段再新增 `fact_card_terminal_unit_vector_index`，或在确定兼容后扩展现有 fact card vector 服务。
- Query 第一阶段新增 terminal unit FTS 检索服务，channel 可命名为 `fact_card_terminal_fts`，也可先并入 `fact_card_fts` 服务返回。
- 为降低 blast radius，第一阶段 hit 的 `evidenceType` 可继续使用 `FACT_CARD`，但 `articleKey/conceptId` 使用 `unitId`，metadata 记录所属 `factCardId/cardId`。

## 7. 推荐最小实现路线

### 第一阶段：生成 terminal units 并进入检索

目标：让检索 topK 里出现“一个 terminal assignment”的独立 hit。

- 在 fact card 生成后，从 `FACT_ENUM` / `key_value_list` / path-aware items 中展开 terminal units。
- 新增 unit repository / mapper，按 `sourceFileId` 或 `factCardId` 删除后重建，保证幂等。
- FTS 用 unit 级 `search_tsv`，content 返回 `displayText + fieldDescription + source ref 摘要`，metadata 带 `terminalUnitId/keyPath/parentPath/terminalKey/value/valueType/sourceRefs`。
- 检索计划接入 unit FTS；RRF hit key 使用 unit identity，避免同一卡 sibling 折叠。
- answer fallback 不增加新 gate，只消费更小粒度 hit。

### 第二阶段：接入 rerank

目标：让 terminal unit 在结构化查值题中稳定高于卡级背景和 sibling。

- 在 `QueryHitIntentReranker` 增加 unit-aware 但通用的加权：path token 覆盖、terminalKey 覆盖、valueType 与问题形态匹配。
- 在 `RrfFusionService` 中把 terminal unit 作为 primary structured evidence；保留卡级 fact card 作为背景证据。
- 增加 retrieval audit 字段，区分 `factCardHitCount` 与 `terminalUnitHitCount`。

### 第三阶段：优化 citation

目标：答案 claim 能引用 terminal unit，而不是只引用源文件或整张卡。

- citation projection 支持 `terminalUnitId`、`keyPath`、`matchedExcerpt=displayText`。
- `query_answer_citations` 可继续用现有 source 类型兜底，但 metadata 中应保留 unit ref；若要一等公民展示，再引入 `FACT_CARD_UNIT` source type。
- citation 文案优先展示源文件 + chunk/行号 + terminal path，避免只展示卡标题。

## 8. 后续可能需要修改的代码文件

本轮不修改以下文件，仅列出下一轮候选范围。

| 目的 | 可能文件 |
|---|---|
| schema | `src/main/resources/db/schema.sql` |
| unit record / repository / mapper | `src/main/java/com/xbk/lattice/infra/persistence/**`、`src/main/resources/com/xbk/lattice/infra/persistence/mapper/**` |
| compile 生成 | `FactCardGenerationService.java`、`FactCardGenerationModels.java`、`FactCardGenerationBaseSupport.java`、`FactCardGenerationListSupport.java`、`FactCardGenerationTableSupport.java` |
| FTS 检索 | 新增 terminal unit FTS service，或调整 `FactCardFtsSearchService.java` / `FactCardJdbcRepository.java` |
| vector 索引 | `FactCardVectorIndexService.java`、`FactCardVectorSearchService.java`，或新增 terminal unit vector service |
| 检索计划 | `RetrievalStrategyResolver.java`、`QueryGraphDefinitionBaseSupport.java`、`KnowledgeSearchService.java` |
| 融合与审计 | `RrfFusionService.java`、`RetrievalAuditService.java`、`QueryArticleHit.java` metadata 约定 |
| citation | `QueryResponseCitationAssembler.java`、`QueryResponseCitationProjectionSupport.java`、citation persistence mapper |
| 管理侧 | `AdminVectorIndexMaintenanceService.java`、`AdminFactCardController.java` |
| 测试 | fact card generation、repository、FTS/vector search、RRF、retrieval audit、answer/citation 定向测试 |

## 9. 验证方案

| Gate | 要求 |
|---|---|
| redline | 先运行 `bash scripts/scan-redline.sh special_cases_report.md`，要求 `BLOCKER=0`；不得改扫描规则、allowlist、prompt、配置词表来过门禁。 |
| Maven | 先跑相关定向测试，再跑 `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`。 |
| 结构化 terminal 题 | 使用 fresh eval 中 5 个结构化 terminal value 题做服务级 gate；要求 terminal unit 进入 topK 且 answer claim 命中目标 unit。 |
| Q6 保护 | 回归既有 terminal field alias / path exact lookup 场景，确认不会因 unit 化导致 sibling 抢占回归。 |
| S2 保护 | 回归 chunk/anchor identity，确认新增 unit channel 不会重新挤掉 article chunk / source chunk 的展示身份。 |
| hidden eval | AI 不读取 hidden eval；只由验收流程运行指标。生产代码、prompt、配置、脚本不得写入 hidden 题面、答案、case id、文件名或 expected citation。 |
| 污染扫描 | 对本轮 diff 扫描 fresh eval 题面、case id、答案值、业务词白名单、中文字段语义硬编码。 |

## 10. 风险与处理

| 风险 | 影响 | 建议 |
|---|---|---|
| schema 迁移 | 新表和索引需要 DDL，旧库需显式 reset 或迁移。 | 先在 `schema.sql` 增量设计，验收时按项目约定显式 reset。 |
| index 重建 | 旧 fact card 已有卡级索引，但没有 unit 索引。 | 提供按 `sourceFileId` / 全量 rebuild 的 unit 重建入口；向量索引第二阶段再接。 |
| citation 粒度 | unit 命中后 citation 仍可能只展示源文件或文章。 | 第一阶段 metadata 保留 unit refs；第三阶段再升级 citation projection。 |
| existing FACT_CARD 兼容 | 既有 fact card FTS/vector 与质量统计不能被破坏。 | 保留卡级 fact card；terminal unit 是补充通道，不替代原卡。 |
| 查询复杂度 | 通道增加会提高融合、审计和调试复杂度。 | 第一阶段只加 FTS unit channel；观察 Recall@5/10 与 topK，再进入 rerank/vector。 |
| 噪声增加 | terminal unit 数量可能远高于 fact card 数量。 | 限制只展开 scalar terminal；跳过空值、大文本、容器节点；按 source 文件和 card 做数量上限与质量统计。 |
| 过拟合风险 | 若 alias/description 引入题集语言，会污染 eval。 | alias 只来自源内容和通用拆词；禁止 eval 派生、禁止业务词白名单、禁止 Java 中文语义硬编码。 |

## 11. 本轮验证记录

- `git diff --check`：通过。
- 定向测试命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackConclusionBuilderTests,AnswerGenerationServiceTests,AnswerFallbackEvidenceSelectorTests test`
- 定向测试结果：
  - `AnswerGenerationServiceTests`：77 run，0 failures，0 errors，0 skipped
  - `AnswerFallbackConclusionBuilderTests`：2 run，0 failures，0 errors，0 skipped
  - `AnswerFallbackEvidenceSelectorTests`：6 run，0 failures，0 errors，0 skipped
  - 合计：85 run，0 failures，0 errors，0 skipped
- 未运行全量 `mvn test`：本轮只做 scoped 回退和设计报告，用户要求不强制全量。

## 12. 合规声明

- 本轮未修改生产代码。
- 本轮未修改 compile/index 生产代码。
- 本轮未修改 `schema.sql`。
- 本轮未修改 `src/main/resources`、prompt、redline allowlist。
- 本轮未读取或修改 `docs/模型绑定配置参考.md`。
- 本轮未清库、未重建、未重导。
- 本轮未 stage、未 commit、未 push。
- 本轮新增报告：`docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md`。
