# Terminal Unit Phase 1C: 表格 Extractor 结构化行修复报告

修复时间：2026-05-29
执行者：agentA（代码执行 Agent）
修复范围：仅 XLSX/CSV extractor 结构化行输出格式

## 1. 结论先行

| 项目 | 结果 |
|---|---|
| XLSX structured rows 格式 | 已修复：每 cell 独立 `- key=value` 行 |
| CSV structured rows 追加 | 已完成：Part A 保留原始 CSV，Part B 追加结构化行 |
| 是否修改 query/fallback/rerank/dispatch | **否** — 零修改 |
| 是否硬编码文件名/列名/题集词/答案值 | **否** — 零硬编码 |
| redline | BLOCKER=0 |
| 定向测试 | 19/0/0 |
| 全量 mvn test | 961/0/0 |
| 是否 stage/commit/push | 否 |

## 2. 变更清单

### 2.1 修改文件

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `StructuredTableContentBuilder.java` | 新增 + 修饰符 | 新增 `toStructuredRowsText` 公共静态方法；`isBlankRow`/`normalizeHeader` 改为 static；新增 `compactCellValue` 静态方法 |
| `ExcelTextExtractor.java` | 重构 | `toStructuredRowsText` 改为委托共享方法；删除 `buildStructuredRowText`/`normalizeHeader`/`compactCellValue` |
| `CsvTextExtractor.java` | 新增逻辑 | `extract()` 中追加 structured rows 文本到 content |
| `DocumentParseRouterIntegrationTests.java` | 断言更新 | 拆分复合格式断言为每 cell 独立行断言 |
| `IngestNodeTests.java` | 断言更新 | 拆分两处复合格式断言为每 cell 独立行断言 |
| `ExcelTextExtractorTests.java` | 新增测试 | 4 个测试用例 |
| `CsvTextExtractorTests.java` | 新增测试 | 6 个测试用例 |

### 2.2 未修改文件

以下文件零修改：
- `FactCardTerminalUnitMaterializer.java`
- `FactCardTerminalUnitIntentReranker.java`
- `FactCardTerminalUnitFtsSearchService.java`
- `FactCardGenerationBaseSupport.java`
- `FactCardGenerationWindowSupport.java`
- `RetrievalStrategyResolver.java`
- `KnowledgeSearchService.java`
- `QueryGraphDefinitionBaseSupport.java`
- `QuerySemanticRules.java`
- `lattice-query-semantic.yml`
- `schema.sql`
- `scripts/scan-redline.sh`
- prompt / allowlist / fresh eval 题集 / 标准答案 / hidden eval / 私有配置

## 3. XLSX 新旧格式对比

### 旧格式（修复前）

```
- sheet=Sheet1; row=2; 化学品名称=浓硫酸; 危险等级=A; 存储条件=防腐蚀柜、双人双锁; 最大存放量=500ml
- sheet=Sheet1; row=3; 化学品名称=乙醚; 危险等级=B; 存储条件=通风橱、防火柜; 最大存放量=200ml
```

问题：`findIndentedKeyValueItems()` 将整行解析为 1 个 KeyValueItem，key="sheet"，value=整行复合字符串（>240 字符）。Materializer 因 `shouldSkipValue()` 跳过，生成 0 terminal unit。

### 新格式（修复后）

```
- sheet=Sheet1
- row=2
- 化学品名称=浓硫酸
- 危险等级=A
- 存储条件=防腐蚀柜、双人双锁
- 最大存放量=500ml
- sheet=Sheet1
- row=3
- 化学品名称=乙醚
- 危险等级=B
- 存储条件=通风橱、防火柜
- 最大存放量=200ml
```

收益：每个 cell 变成独立的 `- key=value` 行，`findIndentedKeyValueItems()` 为每行生成独立的 KeyValueItem，value 为单 cell 短文本。Materializer 可为每个 cell 正常物化 terminal unit。

### Part A 保持不变

CSV-like 表格文本（逗号分隔行）不受影响，全文检索行为不变。

## 4. CSV 追加 Structured Rows

### 修复前

CSV extractor 仅返回原始逗号分隔文本：
```
设备编号,设备名称,维护等级
EQ-001,气相色谱仪,A
```

→ 不匹配任何 fact card 生成模式 → 0 fact card → 0 terminal unit

### 修复后

追加 structured rows Part B：
```
设备编号,设备名称,维护等级
EQ-001,气相色谱仪,A

--- Structured Rows ---
- table=equipment-maintenance-schedule
- row=2
- 设备编号=EQ-001
- 设备名称=气相色谱仪
- 维护等级=A
```

收益：`findIndentedKeyValueItems()` 现在可以识别每 cell 为独立 key=value 行，生成 fact card 和 terminal unit。

### 元数据 key 选择

- CSV 使用 `table` (非 `sheet`) 作为元数据 key，值取文件名（不含扩展名）
- 未使用具体文件名做语义判断

## 5. 验证结果

### 5.1 redline

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：BLOCKER=0，无新增红线命中。

### 5.2 定向测试

```bash
mvn test -Dtest="DocumentParseResultNormalizerTests,DocumentParseRouterIntegrationTests,ExcelTextExtractorTests,CsvTextExtractorTests"
```

结果：19/0/0

| 测试类 | 用例数 | 结果 |
|---|---|---|
| CsvTextExtractorTests | 6 | PASS |
| ExcelTextExtractorTests | 4 | PASS |
| DocumentParseRouterIntegrationTests | 6 | PASS |
| DocumentParseResultNormalizerTests | 3 | PASS |

新增测试覆盖：
- XLSX: 每 cell 独立行、单 cell 不含其他列、空 cell 跳过、多行 sheet/row 元数据
- CSV: Part A 保留 + Part B 追加、每 cell 独立行、单 cell 不含其他列、table 元数据 key、多行 table/row 元数据、空 CSV

### 5.3 全量 mvn test

```bash
mvn test
```

结果：**961/0/0**，BUILD SUCCESS。

- 修复 `IngestNodeTests` 中 2 个断言（`sheet=Steps; row=42` → `- sheet=Steps` + `- row=42` 等）
- 修复 `DocumentParseRouterIntegrationTests` 中 1 个断言（同上模式）
- 无其他测试退化

## 6. 合规声明

- 未修改 `FactCardTerminalUnitMaterializer.java`、`FactCardTerminalUnitFtsSearchService.java`、`FactCardTerminalUnitIntentReranker.java`
- 未修改 `FactCardGenerationBaseSupport.java`、`FactCardGenerationWindowSupport.java`
- 未修改 `RetrievalStrategyResolver.java`、`KnowledgeSearchService.java`、`QueryGraphDefinitionBaseSupport.java`
- 未修改 `QuerySemanticRules.java`、`lattice-query-semantic.yml`
- 未修改 `schema.sql`、`scripts/scan-redline.sh`
- 零硬编码：无文件名特判、无 sheet 名特判、无列名特判、无题集词、无答案值、无 case id
- 未 stage、未 commit、未 push
- 未读取 hidden eval
- 未把 eval 题面、答案、case id 写入代码或配置
