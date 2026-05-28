# Terminal Unit Phase 1A Fix Result Report

## 1. 结论

本轮已完成 terminal unit Phase 1A 最小闭环：`FACT_ENUM` / `key_value_list` / path-aware scalar item 会被物化为独立 terminal unit，进入独立 FTS channel，并以 unit identity 作为 `QueryArticleHit` 的 `articleKey` / `conceptId` 参与 RRF。

本轮没有追求 fresh eval 最终答案 5/5 PASS，没有修改 query fallback、citation schema、response DTO、prompt、规则配置、vector 表或 embedding 生成。

本轮未清库、未重建 schema、未重导数据；未 stage、未 commit、未 push。

## 2. 修改文件清单

生产实现：

- `src/main/resources/db/schema.sql`
- `src/main/java/com/xbk/lattice/infra/persistence/FactCardTerminalUnitRecord.java`
- `src/main/java/com/xbk/lattice/infra/persistence/FactCardTerminalUnitJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/FactCardTerminalUnitMapper.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/FactCardTerminalUnitMapper.xml`
- `src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationService.java`
- `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java`
- `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchService.java`
- `src/main/java/com/xbk/lattice/query/service/RetrievalStrategyResolver.java`
- `src/main/java/com/xbk/lattice/query/service/KnowledgeSearchService.java`
- `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`

测试：

- `src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializerTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/FactCardTerminalUnitJdbcRepositoryTests.java`
- `src/test/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchServiceTests.java`
- `src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java`
- `src/test/java/com/xbk/lattice/query/service/QueryPreparationServiceTests.java`

报告与门禁输出：

- `special_cases_report.md` 由 redline 扫描更新。
- `docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1a_fix_result_report.md`

说明：工作树中已有其他文档改动；本轮只读允许阅读的质量/roadmap/设计文档，未读取或修改禁止文档。

## 3. Schema 新增表和索引

新增表：`fact_card_terminal_units`。

核心字段：

- 身份字段：`id`、`unit_id`、`terminal_unit_identity`
- 归属字段：`fact_card_id`、`card_id`、`source_id`、`source_file_id`
- 来源字段：`source_chunk_ids`、`article_ids`、`source_refs_json`
- 结构字段：`card_type`、`answer_shape`、`structure`、`item_index`、`key_path`、`parent_path`、`terminal_key`、`path_segments_json`
- 检索字段：`field_label`、`field_aliases_json`、`field_description`、`display_text`、`value_text`、`normalized_value`、`value_type`、`fts_text`、`search_tsv`
- 治理字段：`metadata_json`、`review_status`、`confidence`、`content_hash`、`created_at`、`updated_at`

索引：

- `uk_fact_card_terminal_units_unit_id`：保证 upsert 幂等。
- `idx_fact_card_terminal_units_identity`：按 terminal identity 定位。
- `idx_fact_card_terminal_units_fact_card_id`：按 fact card 删除和回查。
- `idx_fact_card_terminal_units_source_file_id`：按 source file 重建删除。
- `idx_fact_card_terminal_units_parent_path`：支持 path-aware 分析与排查。
- `idx_fact_card_terminal_units_value_type`：支持值类型治理。
- `idx_fact_card_terminal_units_search_tsv`：GIN FTS 检索。

没有新增 vector 表，也没有新增 unit embedding 字段。

## 4. Terminal Unit 生成规则

生成入口在 fact card 生成链路：`FactCardGenerationService.rebuildForSourceFile` 保存 fact card 后，调用 `FactCardTerminalUnitMaterializer` 从已保存的 fact card record 物化 units，再通过 repository upsert。

第一阶段只展开以下内容：

- `cardType = FACT_ENUM`
- `items_json.structure = key_value_list`
- `items` 数组中的 scalar terminal assignment

过滤规则：

- 跳过空值。
- 跳过 object / array 等容器节点。
- 跳过超过长度阈值的大文本。
- 跳过缺少稳定 key path 或 terminal key 的 item。

字段来源约束：

- `fieldLabel`、`fieldAliases`、`fieldDescription` 只来自源结构字段：`key`、`keyPath`、`parentPath`、`displayText`、`pathSegments`。
- alias 只做通用拆词：snake、kebab、dot、slash、camel case 等格式拆分。
- Java 主链没有加入中文字段语义、业务词、题面、文件名、case id 或答案值特判。

幂等规则：

- `unit_id` 由 `cardId`、`itemIndex`、`keyPath`、`terminalKey`、`normalizedValue` 等稳定信息哈希生成。
- `terminal_unit_identity = terminal-unit:{unit_id}`。
- repository 以 `unit_id` 做唯一约束 upsert；按 source file 重建时先删除该 source file 下旧 units，再写入新 units。

## 5. FTS Search Hit 示例

以下为 synthetic 示例，不来自 hidden eval：

```json
{
  "articleKey": "terminal-unit:fact-card-terminal:synthetic-card-a:0:6b1f4c8e9a2d",
  "conceptId": "terminal-unit:fact-card-terminal:synthetic-card-a:0:6b1f4c8e9a2d",
  "evidenceType": "FACT_CARD",
  "channel": "fact_card_terminal_fts",
  "content": "alpha_limit = 31\nroot.entries[0].settings.alpha_limit",
  "metadata": {
    "terminalUnitId": 17,
    "unitId": "fact-card-terminal:synthetic-card-a:0:6b1f4c8e9a2d",
    "terminalUnitIdentity": "terminal-unit:fact-card-terminal:synthetic-card-a:0:6b1f4c8e9a2d",
    "factCardId": 9,
    "cardId": "synthetic-card-a",
    "keyPath": "root.entries[0].settings.alpha_limit",
    "parentPath": "root.entries[0].settings",
    "terminalKey": "alpha_limit",
    "value": "31",
    "valueType": "number",
    "displayText": "alpha_limit = 31"
  }
}
```

FTS content 使用 `displayText` 和 `fieldDescription`，不使用整张 `items_json`。metadata 保留 unit、card、path、value 与 channel/source 标识，避免 terminal unit 与整卡命中混淆。

## 6. Query 接入与 RRF Identity

新增 `FactCardTerminalUnitFtsSearchService`，通过 `FactCardTerminalUnitJdbcRepository.searchLexical` 检索 terminal units，并返回 `QueryArticleHit`。

接入点：

- `RetrievalStrategyResolver` 新增 channel：`fact_card_terminal_fts`。
- `KnowledgeSearchService` 在 fact card FTS 邻近流程中调度 terminal unit FTS。
- `QueryArticleHit.articleKey` 与 `conceptId` 使用 `terminalUnitIdentity`，不是 `card_id`。

RRF 调整：

- `RrfFusionService` 对 `FACT_CARD` hit 优先读取 metadata 中的 `terminalUnitIdentity` 构造 hit identity。
- 对 terminal channel 可回退使用 `unitId`。
- 同一 fact card 下不同 sibling terminal units 因 identity 不同，不会被折叠为同一个 hit。
- 普通 fact card hit 仍沿用原有 card-level identity，不被 terminal unit 逻辑误伤。

## 7. 明确未做

- 未新增 vector 表。
- 未生成 terminal unit embedding。
- 未修改 citation schema。
- 未修改 query response DTO。
- 未修改 query fallback selector / conclusion / snippet gate。
- 未修改 prompt、`config/synonyms.yaml`、`config/rules.yaml`。
- 未读取 hidden eval。

## 8. Redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

- Exit code：0
- `BLOCKER=0`
- `REVIEW=2051`
- `ALLOWLIST=259`

说明：`REVIEW` / `ALLOWLIST` 为扫描候选与既有白名单项；本轮 gate 以 `BLOCKER=0` 通过。

## 9. 定向测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitMaterializerTests,FactCardTerminalUnitFtsSearchServiceTests,WeightedRrfFusionTest,QueryPreparationServiceTests test
```

结果：

- BUILD SUCCESS
- Tests run: 25
- Failures: 0
- Errors: 0
- Skipped: 0

补充回归：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test
```

结果：

- BUILD SUCCESS
- Tests run: 21
- Failures: 0
- Errors: 0
- Skipped: 0

Repository 测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardTerminalUnitJdbcRepositoryTests test
```

结果：

- BUILD SUCCESS
- Tests run: 3
- Failures: 0
- Errors: 0
- Skipped: 3

跳过原因：当前本地数据库未应用新增 schema，本轮明确禁止清库、重建、重导；repository 测试使用表存在性假设保护。

## 10. 全量测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

- BUILD SUCCESS
- Tests run: 933
- Failures: 0
- Errors: 0
- Skipped: 3
- Total time: 06:44 min

跳过项为 `FactCardTerminalUnitJdbcRepositoryTests`，原因同上：当前库尚未应用新增表。

## 11. 其他门禁结果

格式检查：

```bash
git diff --check -- . ':(exclude)docs/模型绑定配置参考.md'
```

结果：无输出，exit 0。

硬编码扫描，针对本轮实现范围：

```bash
{ git diff -- src/main/java src/test/java src/main/resources/db/schema.sql src/main/resources/com/xbk/lattice/infra/persistence/mapper; for file in $(git ls-files --others --exclude-standard src/main/java src/test/java src/main/resources/db/schema.sql src/main/resources/com/xbk/lattice/infra/persistence/mapper); do git diff --no-index -- /dev/null "$file" 2>/dev/null || true; done; } | rg -n "FQ3|FQ4|FQ6|FG1|FG2|equipment-borrowing-policy|押金|逾期|最长借用|最大并发|v2\\.3\\.1|1000|8080|Kubernetes|readiness|liveness|apiKey|sk-[A-Za-z0-9]"
```

结果：无输出。

更宽的全 diff 扫描排除禁止文档后，仅既有质量台账历史说明命中扫描词；本轮生产代码、测试、schema、mapper 实现范围无命中。

## 12. 残余风险

- 当前数据库尚未应用新增表，因此本轮只能完成编译、单元测试与表存在性保护，不能证明真实库中 terminal unit FTS 可被端到端召回。
- terminal unit Phase 1A 只覆盖 scalar assignment；复杂表格、多层容器摘要、长文本切片仍不展开。
- FTS 仍是 lexical channel，没有 unit vector；对同义表达或弱词面匹配的召回提升有限。
- 第一阶段 `evidenceType` 仍使用 `FACT_CARD`，需要依赖 channel 和 metadata 区分 terminal unit。
- 未改 citation schema，回答端引用仍可能停留在 card/source 粒度；本轮只保留 unit metadata，为后续 citation 精细化留接口。

## 13. 是否需要 AgentD 清库重建验证

需要。

原因：schema 已新增 `fact_card_terminal_units`，但本轮按约束未清库、未重建、未重导。AgentD 后续应在独立验证轮执行标准 schema reset / 数据重导 / query 回归，重点验证：

- 新表真实创建成功。
- 编译导入后 terminal units 被写入。
- terminal unit FTS 能返回独立 `QueryArticleHit`。
- 同一 fact card 下 sibling units 在 RRF 中不折叠。
- metadata 中 unit/card/path/value/channel 字段完整。
- fresh eval 与 baseline 指标无 hidden eval 污染迹象。

## 14. 回滚方式

如需回滚本轮改动：

- 删除 `fact_card_terminal_units` DDL 与相关索引。
- 移除 terminal unit record、repository、mapper、materializer、FTS search service。
- 从 `FactCardGenerationService` 移除 terminal unit 物化与 upsert。
- 从 `RetrievalStrategyResolver` / `KnowledgeSearchService` 移除 `fact_card_terminal_fts` channel。
- 从 `RrfFusionService` 移除 terminal unit identity 优先逻辑。
- 删除本轮新增测试和对应测试补强。

回滚不涉及数据迁移；若后续验证轮已经应用 schema，需要通过标准清库重建恢复到回滚后的 schema。
