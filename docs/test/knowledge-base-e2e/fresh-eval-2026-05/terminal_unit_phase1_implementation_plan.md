# terminal unit 第一阶段实施计划

## 结论

第一阶段新增 `fact_card_terminal_units` 表，只接入 unit 级 FTS 检索，不新增、不启用独立 vector 表，不改 query fallback selector / conclusion / snippet gate，不改 citation 展示语义。目标是把 `FACT_ENUM`、`key_value_list`、path-aware `items` 中的 scalar terminal assignment 物化为独立检索命中，使 RRF 以 unit identity 融合同卡 sibling。

验收重点不是直接追最终答案通过，而是先证明：结构化 terminal value 问题的 topK 中出现目标 terminal unit，且同一卡 sibling 不再因共享 `card_id` 被折叠。

## 第一阶段边界

| 项 | 决策 |
|---|---|
| 表结构 | 新增 `fact_card_terminal_units`。 |
| 检索 | 新增 `fact_card_terminal_fts` channel。 |
| 向量 | 第一阶段不新增 vector 表、不生成 unit embedding、不接 ANN。 |
| 生成点 | fact card rebuild 后，从已持久化 `FactCardRecord` 展开 unit。 |
| 融合身份 | `FACT_CARD:terminal-unit:{unitId}`，优先读取 metadata 中的 `terminalUnitIdentity`。 |
| citation | 第一阶段不改 citation 组装，只在 metadata 保留 unit ref。 |
| fallback | 不继续叠 selector / conclusion / snippet gate。 |
| 数据重建 | 需要显式 schema reset 或等价 DDL 初始化；不由本轮执行。 |

## 数据模型

第一阶段需要新增 `fact_card_terminal_units`，因为现有 `fact_cards` 是卡级表，FTS、review、RRF identity、citation metadata 都以整卡为单位；若把一条 terminal 伪装成 fact card，会污染卡级统计和生命周期。

建议字段：

| 字段 | 类型建议 | 说明 |
|---|---|---|
| `id` | `BIGSERIAL` | unit 主键。 |
| `unit_id` | `VARCHAR(320)` | 稳定业务标识，唯一。 |
| `terminal_unit_identity` | `VARCHAR(360)` | RRF / audit 使用的稳定 hit identity，建议固定前缀加 `unit_id`。 |
| `fact_card_id` | `BIGINT` | 所属 `fact_cards.id`，外键级联删除。 |
| `card_id` | `VARCHAR(256)` | 所属卡稳定标识，便于审计。 |
| `source_id` | `BIGINT` | 沿用 fact card。 |
| `source_file_id` | `BIGINT` | 沿用 fact card，外键级联删除。 |
| `source_chunk_ids` | `BIGINT[]` | 沿用 fact card，必要时从 item raw line 补充行级 ref。 |
| `article_ids` | `BIGINT[]` | 沿用 fact card。 |
| `card_type` | `VARCHAR(32)` | 所属卡类型。 |
| `answer_shape` | `VARCHAR(32)` | 所属答案形态。 |
| `structure` | `VARCHAR(64)` | `items_json.structure`，例如 key-value 或 table 结构类型。 |
| `item_index` | `INTEGER` | item 在卡内的稳定序号。 |
| `row_index` | `INTEGER` | 表格/数组场景的行序号，无则为空。 |
| `column_index` | `INTEGER` | 表格场景的列序号，无则为空。 |
| `key_path` | `TEXT` | 完整结构路径。 |
| `parent_path` | `TEXT` | 父级路径，用于 sibling 分组。 |
| `terminal_key` | `TEXT` | 末级字段名。 |
| `path_segments_json` | `JSONB` | 路径片段数组。 |
| `field_label` | `TEXT` | 源内容字段名或 terminal key。 |
| `field_aliases_json` | `JSONB` | 只含源内容派生和通用拆词变体。 |
| `field_description` | `TEXT` | 通用短上下文：父路径、结构、行/表上下文。 |
| `display_text` | `TEXT` | 面向 evidence 的短文本，优先 `keyPath = value`。 |
| `value_text` | `TEXT` | 原始可展示值。 |
| `normalized_value` | `TEXT` | 通用值归一：trim、成对引号、数字/URL/path 基础规整。 |
| `value_type` | `VARCHAR(32)` | 仅按值形态推断，如 number、boolean、url、path、version、date_like、string、empty。 |
| `source_refs_json` | `JSONB` | source file、chunk、item index、raw line、section/table/sheet 等回指。 |
| `fts_text` | `TEXT` | unit 级检索文本。 |
| `metadata_json` | `JSONB` | 查询命中透传 metadata。 |
| `review_status` | `VARCHAR(32)` | 继承 fact card review status。 |
| `confidence` | `DOUBLE PRECISION` | 继承或按 unit 质量轻微下调。 |
| `content_hash` | `VARCHAR(64)` | unit 内容哈希，用于幂等更新。 |
| `search_tsv` | `TSVECTOR` | unit 级全文检索向量。 |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | 审计时间。 |

建议索引：

| 索引 | 目的 |
|---|---|
| `unit_id` 唯一索引 | 幂等 upsert。 |
| `fact_card_id` 普通索引 | 按卡删除/重建。 |
| `source_file_id` 普通索引 | 按源文件删除/重建。 |
| `parent_path` 普通索引 | 审计 sibling 分组。 |
| `value_type` 普通索引 | 后续值形态 gate / audit。 |
| `search_tsv` GIN | FTS 检索。 |

## 向量策略

第一阶段只做 FTS，不建独立 vector 表。

原因：

| 原因 | 说明 |
|---|---|
| 根因先验 | 当前失败已定位为 evidence unit 粒度，先验证 lexical unit 是否能进入 topK。 |
| 改动面 | vector 需要 embedding 生成、维度对齐、ANN 索引、后台 rebuild、成本控制，容易扩大变量。 |
| 验收顺序 | 如果 FTS unit 进入 topK 但排序不稳，再进入 rerank / vector 第二阶段。 |
| 成本 | 避免因大量 terminal unit 生成 embedding 带来成本和重建耗时。 |

第二阶段如需要，再新增 `fact_card_terminal_unit_vector_index`，不要复用 `fact_card_vector_index`，避免卡级和 unit 级向量生命周期混杂。

## 生成流程

生成点放在 fact card rebuild 事务内，顺序如下：

1. `FactCardGenerationService.rebuildForSourceFile(sourceFileId)` 生成 fact cards。
2. 删除该 `sourceFileId` 下旧 fact cards 时，由外键级联删除旧 terminal units；或显式先删 units 再删 cards。
3. 每张 `FactCardRecord` upsert 后拿到数据库 `id`。
4. 对已保存的 fact card 执行 `FactCardTerminalUnitMaterializer.materialize(savedRecord)`。
5. 将 unit 列表批量 upsert 到 `fact_card_terminal_units`。

只展开这些来源：

| 来源 | 展开规则 |
|---|---|
| `FACT_ENUM` + `key_value_list` | `items_json.items[]` 中含 `key/value/keyPath/parentPath/pathSegments/displayText` 的 scalar item，一条 item 生成一个 unit。 |
| path-aware items | `pathAware=true` 或 item 中存在 `keyPath/pathSegments` 时，保留完整路径和 parentPath。 |
| Markdown table enum | `items_json.structure=markdown_table` 的行对象，每个非空 scalar cell 可生成 unit；`parentPath` 使用行身份，`terminalKey` 使用列名归一字段。 |

第一阶段不展开：

| 不展开对象 | 原因 |
|---|---|
| 空值、大文本值 | 噪声高，容易污染 topK。 |
| 容器节点 | 不是 terminal assignment。 |
| `FACT_SEQUENCE` / `FACT_POLICY` | 本阶段只处理 terminal value 桶，避免混修根因。 |
| 需要业务语义判断的字段 | 禁止 Java 主链硬编码中文字段语义。 |

## 幂等重建

幂等策略：

| 层级 | 规则 |
|---|---|
| `unit_id` | 由 `card_id + item identity + key_path + normalized_value + content_hash` 的稳定哈希生成。 |
| 删除 | `rebuildForSourceFile` 中按 `source_file_id` 删除旧 units，或依赖 `fact_cards.source_file_id` 级联删除。建议显式删除 units，便于统计。 |
| upsert | `unit_id` 唯一冲突时更新所有派生字段、`search_tsv`、`metadata_json`、`updated_at`。 |
| source reset | schema reset 后从源文件重新 compile，可生成相同业务 unit identity。 |
| 内容变化 | 值或路径变化导致 `content_hash` 变化，旧 unit 被按源文件删除，新 unit 重建。 |

`unit_id` 不得包含 fresh eval 题面、case id、文件名、答案值白名单或业务词白名单。源文件路径只允许作为 source ref，不参与 alias 语义扩展。

## FTS 文本与 Metadata

`fts_text` 只由源内容和通用结构规则生成。

建议拼接字段：

| 组成 | 说明 |
|---|---|
| `card_type` / `answer_shape` / `structure` | 保留结构化证据信号。 |
| `field_label` | 原始字段名、表头或 terminal key。 |
| `field_aliases` | 大小写、snake/kebab/camel、空格分词、父路径末段 + terminal key、完整 keyPath。 |
| `key_path` / `parent_path` / `terminal_key` | path-aware exact lookup。 |
| `display_text` | 直接 evidence 文本。 |
| `value_text` / `normalized_value` / `value_type` | value 形态与原值。 |
| `field_description` | parentPath、结构、行/表上下文，长度受控。 |
| `source_refs` 摘要 | chunk index、line index、table row/column 等通用引用信息。 |

`metadata_json` 建议包含：

| 字段 | 用途 |
|---|---|
| `terminalUnitId` | 数据库主键。 |
| `unitId` | 稳定业务标识。 |
| `terminalUnitIdentity` | RRF / audit identity。 |
| `factCardId` / `cardId` | 回指原 fact card。 |
| `sourceFileId` / `sourceChunkIds` / `articleIds` | 回指来源。 |
| `cardType` / `answerShape` / `structure` | 结构化证据类型。 |
| `keyPath` / `parentPath` / `terminalKey` / `pathSegments` | path-aware 证据定位。 |
| `fieldLabel` / `fieldAliases` / `fieldDescription` | 后续 rerank / audit。 |
| `value` / `normalizedValue` / `valueType` | terminal value。 |
| `displayText` | answer/fallback 可直接消费的 evidence 行。 |
| `sourceRefs` | citation 后续升级所需回指。 |

## Query 接入

新增 `fact_card_terminal_fts` channel，作为结构化证据补充通道，不替代现有 `fact_card_fts`。

接入步骤：

| 步骤 | 修改点 | 说明 |
|---|---|---|
| 1 | `RetrievalStrategyResolver` | 增加 `CHANNEL_FACT_CARD_TERMINAL_FTS` 常量，默认权重沿用 fact card 或略高于 fact card；结构化 answer shape 与 exact lookup 时同步 boost。 |
| 2 | `KnowledgeSearchService` | 注入 `FactCardTerminalUnitFtsSearchService`，在 `buildDispatchPlan` 中加入 channel。 |
| 3 | `QueryGraphDefinitionBaseSupport` | 注入 terminal unit FTS 服务，保存 / 读取该 channel hits。 |
| 4 | `QueryGraphState` / `QueryGraphStateKeys` | 增加 terminal unit hits ref，避免与 fact card hits 混在同一 working set。 |
| 5 | `RetrievalAuditService` | 审计中保留新 channel；factCardHitCount 可继续只统计 FACT_CARD，也可新增 terminalUnitHitCount，第一阶段建议新增以便验收。 |
| 6 | `AdminQueryRetrievalAuditController` | 展示 channel 列表增加 terminal unit FTS，便于 agentD 定位。 |

hit 投影：

| 字段 | 建议 |
|---|---|
| `evidenceType` | 第一阶段继续用 `FACT_CARD`，降低 answer/citation 改动面。 |
| `articleKey` | 使用 `unit_id` 或 `terminal_unit_identity`，不要使用 `card_id`。 |
| `conceptId` | 使用 `unit_id`。 |
| `title` | 使用 fact card title + terminal key/path 的通用短标题。 |
| `content` | 使用 `display_text + field_description + source ref 摘要`，不要塞整张 `items_json`。 |
| `metadataJson` | 带完整 unit ref。 |
| `reviewStatus` | 继承 unit/fact card review status。 |
| `sourcePaths` | 沿用 source file path。 |
| `score` | unit FTS rank + 通用 LIKE 加分。 |

## RRF Identity

必须避免同一卡 sibling 继续折叠。当前 RRF 默认用 `evidenceType + articleKey/conceptId`；如果 terminal hit 的 `articleKey` 仍是 `card_id`，问题会复现。

第一阶段建议双保险：

1. terminal unit FTS 返回的 `articleKey` / `conceptId` 直接使用 unit identity。
2. `RrfFusionService.buildHitKey` 增加 FACT_CARD unit-aware 分支：当 metadata 有 `terminalUnitIdentity` 或 `unitId` 时，使用该值构建 hit key。

这样即使后续某个服务误把 `articleKey` 填回 `card_id`，metadata 仍能保护 sibling 不折叠。

`isPrimaryStructuredEvidence` 也要把 `fact_card_terminal_fts` 视为结构化主证据通道，否则结构化 guardrail 可能仍优先保留卡级 / source chunk，而不保护 unit hit。

## Citation 策略

第一阶段不改 citation 展示与 persistence schema。

保留方式：

| 项 | 处理 |
|---|---|
| citation source type | 不新增 `FACT_CARD_UNIT`。 |
| answer claim citation | 仍按现有 FACT_CARD / source 逻辑走。 |
| unit ref | 通过 `metadata_json.sourceRefs`、`terminalUnitId`、`keyPath`、`displayText` 透传。 |
| 后续升级 | 第三阶段再把 citation projection 显式升级到 terminal unit。 |

理由：当前目标是先让检索返回单字段 evidence unit。citation 一等公民化会牵涉 response DTO、citation assembler、persistence mapper 和前端展示，属于第二个变量。

## 文件修改清单

下一轮确认后允许修改的文件范围如下；本计划本轮不修改这些文件。

| 文件 | 职责 |
|---|---|
| `src/main/resources/db/schema.sql` | 新增 `fact_card_terminal_units` 表、索引、comment。 |
| `src/main/java/com/xbk/lattice/infra/persistence/FactCardTerminalUnitRecord.java` | unit 持久化对象。 |
| `src/main/java/com/xbk/lattice/infra/persistence/FactCardTerminalUnitJdbcRepository.java` | upsert、delete by source/card、FTS search。 |
| `src/main/java/com/xbk/lattice/infra/persistence/mapper/FactCardTerminalUnitMapper.java` | MyBatis mapper 接口。 |
| `src/main/resources/com/xbk/lattice/infra/persistence/mapper/FactCardTerminalUnitMapper.xml` | unit 表 SQL、FTS 查询、metadata 构造。 |
| `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java` | 从 `items_json` 展开 terminal units，生成 label/alias/fts/metadata/hash。 |
| `src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationService.java` | 在 fact card rebuild 事务内调用 materializer + repository。 |
| `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchService.java` | terminal unit FTS search，投影为 `QueryArticleHit`。 |
| `src/main/java/com/xbk/lattice/query/service/RetrievalStrategyResolver.java` | 新增 channel、权重、结构化和 exact lookup boost。 |
| `src/main/java/com/xbk/lattice/query/service/KnowledgeSearchService.java` | 注入并调度 terminal unit FTS channel。 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionBaseSupport.java` | Query graph 调度、保存、加载 terminal unit hits。 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphState.java` | 新增 terminal unit hits ref 字段。 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphStateKeys.java` | 新增 working set key。 |
| `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java` | FACT_CARD unit-aware hit key；terminal channel 作为 primary structured evidence。 |
| `src/main/java/com/xbk/lattice/query/service/RetrievalAuditService.java` | 审计中记录 terminal unit channel 与可选 `terminalUnitHitCount`。 |
| `src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalAuditController.java` | 管理侧 channel 列表展示新增 channel。 |

暂不修改：

| 文件/区域 | 原因 |
|---|---|
| `AnswerGenerationFallback*` | 明确不继续叠 fallback gate。 |
| `QueryResponseCitation*` / citation mapper | 第一阶段只 metadata 保留 unit ref。 |
| `FactCardVector*` / vector schema | 第一阶段只做 FTS。 |
| `config/synonyms.yaml` / `config/rules.yaml` / prompt | 避免题集语言或字段语义污染。 |
| `scripts/scan-redline.sh` / allowlist | 禁止通过改扫描规则过门禁。 |

## 测试计划

新增或补强测试：

| 测试 | 覆盖点 |
|---|---|
| `FactCardTerminalUnitMaterializerTests` | `key_value_list`、path-aware items、table scalar cell 展开；空值/容器/大文本跳过；alias 只源内容派生。 |
| `FactCardTerminalUnitJdbcRepositoryTests` | upsert 幂等、按 source/card 删除、FTS metadata、`search_tsv` 可查。 |
| `FactCardGenerationServiceTests` | rebuild fact cards 后同步重建 units；重复 rebuild 数量稳定。 |
| `FactCardTerminalUnitFtsSearchServiceTests` | 返回 `QueryArticleHit` 的 `articleKey/conceptId/content/metadata/reviewStatus` 正确。 |
| `RetrievalStrategyResolverTests` | 新 channel 默认启用，结构化 answer shape / exact lookup boost 生效，vector 禁用逻辑不影响 terminal FTS。 |
| `KnowledgeSearchServiceTests` 或等价检索测试 | dispatch plan 包含 terminal unit channel，channel disabled 时稳定跳过。 |
| `QueryGraphDefinition*Tests` | graph working set 保存/加载 terminal unit hits。 |
| `WeightedRrfFusionTest` | 同一 fact card 的两个 terminal units 不折叠；metadata unit identity 优先。 |
| `RetrievalAuditServiceTests` | 审计能记录 terminal channel，terminal unit metadata 可追踪。 |
| 保护测试 | 旧 Q6 path-aware exact lookup、S2 chunk identity 不回归。 |

测试数据必须使用 synthetic source 内容，不使用 fresh eval 题面、case id、文件名、答案值或 hidden eval 信息。

## 验证命令

下一轮实现后建议按顺序执行：

1. `bash scripts/scan-redline.sh special_cases_report.md`
2. `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitMaterializerTests,FactCardTerminalUnitJdbcRepositoryTests,FactCardGenerationServiceTests test`
3. `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitFtsSearchServiceTests,RetrievalStrategyResolverTests,WeightedRrfFusionTest test`
4. `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
5. 由 agentD 在 clean schema 上运行 Public Eval 2 结构化 terminal value 服务级 gate，先验收 terminal unit 是否进入 topK 和 answer claim 是否使用 unit。
6. 由 agentD 回归 Public Eval 1 Q6 terminal field alias / path exact lookup 与 S2 chunk/anchor identity 保护场景。
7. 通过后再运行完整 Public Eval 2，并输出 Recall@5、Recall@10、Citation Accuracy、Abstain Accuracy、Hallucination Count 对比。

本计划阶段不执行上述命令，因为用户要求只读分析和写实施计划。

## 是否需要清库重建

下一轮实现后需要显式 schema reset 或等价 DDL 初始化，原因：

| 原因 | 说明 |
|---|---|
| 新表 | `fact_card_terminal_units` 不存在，旧库无法查询 terminal channel。 |
| 旧 fact cards | 已编译资料只有卡级索引，没有 unit 物化产物。 |
| 项目约定 | 项目不使用 Flyway，DDL 变化按 `schema.sql` 和 reset 脚本显式执行。 |
| 幂等验证 | clean rebuild 才能证明 unit 生成不依赖历史脏数据。 |

不在本轮清库、重建、重导；后续由确认后的实现轮或 agentD 验证轮按授权执行。

## Hidden Eval 防污染

防污染规则：

| 风险点 | 控制 |
|---|---|
| label / alias | 只来自源文件字段名、表头、path segments 和通用拆词；不得来自题集、答案、query 日志。 |
| valueType | 只按值形态推断，不按具体字段语义推断。 |
| fieldDescription | 只使用 parentPath、结构、行/表上下文和源内 descriptor，不写业务结论。 |
| Java 主链 | 不硬编码中文字段语义、业务词、文件名、case id、答案片段。 |
| 配置 | 第一阶段不新增 synonyms/rules/prompt，避免把 eval 语言变成检索规则。 |
| 报告 | hidden eval 只记录指标和失败类型分布，不记录题目、答案、关键词、文件名。 |
| 测试 | 单元测试使用 synthetic fixtures，不复刻 hidden 或 fresh eval 题面。 |

## 风险与回滚

| 风险 | 影响 | 缓解 | 回滚 |
|---|---|---|---|
| unit 数量膨胀 | FTS 噪声、检索耗时上升 | 只展开 scalar terminal；跳过空值/大文本/容器；保留数量统计 | 禁用 channel 权重或移除 dispatch plan。 |
| sibling 仍折叠 | topK 仍只剩一个卡级身份 | `articleKey/conceptId` 用 unit identity，RRF 再读 metadata 兜底 | 回滚 RRF unit-aware 分支和 channel。 |
| metadata 太大 | audit / working set 变重 | metadata 只保留必要 unit ref，source raw line 截断 | 精简 metadata 字段。 |
| 结构化 guardrail 未保护 unit | unit 召回但被背景证据挤出 | terminal channel 加入 primary structured evidence | 回滚 guardrail 变更或关闭 channel。 |
| citation 不够细 | 答案引用仍显示卡级/源文件 | 第一阶段接受，metadata 留升级入口 | 后续第三阶段升级 citation；不阻塞 phase1。 |
| schema reset 成本 | 需要重新导入资料验收 | 只在验证轮执行，记录 reset 前后计数 | 删除新表和 channel，恢复旧卡级检索。 |
| hidden eval 污染 | 泛化指标虚高 | alias 来源审计、redline、diff 人审 | 删除污染规则，重建索引。 |

最小回滚路径：

1. 将 `RetrievalStrategyResolver` 中 terminal channel 权重置 0 或从 dispatch plan 移除。
2. 保留新表不查询，避免影响现有卡级检索。
3. 若需彻底回滚，再删除 terminal unit 生成调用与 schema 表定义，并 clean reset。

## 下一轮交付标准

下一轮实现完成后，agentA 输出 `*_fix_result_report.md`，至少包含：

| 项 | 要求 |
|---|---|
| schema | 新表和索引说明。 |
| 生成 | 每个 source file / fact card 生成 unit 数量统计。 |
| 检索 | terminal unit FTS channel 命中样例，禁止泄露 hidden eval。 |
| RRF | 同卡 sibling 不折叠的测试结果。 |
| 门禁 | redline、定向测试、全量 `mvn test`。 |
| 未做项 | 明确未接 vector、未改 citation、未叠 fallback gate。 |
