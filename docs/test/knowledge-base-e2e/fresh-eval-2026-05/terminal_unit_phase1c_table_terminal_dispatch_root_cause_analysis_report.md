# Terminal Unit Phase 1C: 表格类 Terminal Dispatch 根因分析报告

分析时间：2026-05-29
分析人：agentB（治理/链路分析 Agent）
分析范围：只读分析，不修改任何文件

## 1. 结论先行

**`fact_card_terminal_fts` channel 没有被"漏调度"——它在 dispatch plan 中始终存在、始终启用、始终执行。FQ7/FQ11 无 terminal unit 命中的根因在编译层，不在查询调度层。**

| 题目 | fact_card_fts | fact_card_terminal_fts | 根因 |
|---|---|---|---|
| FQ7 (XLSX) | 调度，有命中 | 调度，**0 命中** | XLSX 结构化行格式导致单个 item 的 value 为复合字符串（>240 字符），Materializer 跳过。fact card 存在但 terminal unit 无法物化。 |
| FQ11 (CSV) | **0 命中** | 调度，**0 命中** | CSV 原始文本（逗号分隔）不匹配任何 fact card 生成模式。**fact card 本身就不存在**，更无 terminal unit。 |

---

## 2. Dispatch 层完整追踪

### 2.1 Channel 注册（RetrievalStrategyResolver）

```java
// RetrievalStrategyResolver.java:33
public static final String CHANNEL_FACT_CARD_TERMINAL_FTS = "fact_card_terminal_fts";

// RetrievalStrategyResolver.java:124 (基础权重)
channelWeights.put(CHANNEL_FACT_CARD_TERMINAL_FTS, settings.getFactCardWeight());
// settings.getFactCardWeight() 默认值 = 1.40 (QueryRetrievalSettingsState.java:27)
```

**权重始终为 1.40 > 0，channel 始终 enabled。** 没有任何条件分支可以禁用该 channel——与 vector channel（可被 `shouldEnableVector()` 禁用）和 graph channel（可被 `shouldDisableGraphForExactLookup()` 禁用）不同，terminal unit FTS 没有任何 gate。

### 2.2 Dispatch Plan 构建

两个 dispatch plan 构建点都**无条件**包含 `fact_card_terminal_fts`：

```java
// KnowledgeSearchService.java:539-546
private List<SupplierRetrievalChannel> buildDispatchPlan(...) {
    // ... 固定列表，始终包含所有 12 个 channel
    plans.add(new SupplierRetrievalChannel(CHANNEL_FACT_CARD_TERMINAL_FTS, ...));
}

// QueryGraphDefinitionBaseSupport.java:306-313
private List<SupplierRetrievalChannel> buildDispatchPlan(...) {
    // ... 同上，固定列表
    plans.add(new SupplierRetrievalChannel(CHANNEL_FACT_CARD_TERMINAL_FTS, ...));
}
```

### 2.3 Channel 执行

```java
// SupplierRetrievalChannel.isEnabled() (line 80-85)
public boolean isEnabled() {
    return executionContext.getRetrievalStrategy()
            .isChannelEnabled("fact_card_terminal_fts");  // 永远返回 true
}
```

Channel 执行后调用 `FactCardTerminalUnitFtsSearchService.search()`，后者查询 `fact_card_terminal_units` 表。**如果表里没有匹配行，返回空列表。Channel 本身成功执行（SUCCESS），只是 hits=0。**

### 2.4 结论：Dispatch 层无 Bug

```
 Dispatch Plan           Channel Execute          DB Query           结果
 ─────────────────────   ─────────────────────   ─────────────────   ──────
 fact_card_terminal_fts  → 始终 enabled          → SELECT ... FROM   → YAML: 有行
                         → 始终执行                 fact_card_          XLSX: 无行
                         → 无 gate 可禁用它         terminal_units     CSV: 无行
```

**不是"没调度"，是"调度了但数据库里没有数据"。**

---

## 3. XLSX 根因：Terminal Unit 无法物化

### 3.1 XLSX 提取产物

`ExcelTextExtractor.toSheetText()` (line 104-113) 对每个 sheet 生成两部分文本：

**Part A — CSV-like 文本：**
```
化学品名称,危险等级,存储条件,最大存放量,保管人角色,备注
浓硫酸,A,防腐蚀柜、双人双锁,500ml,实验室安全员,强腐蚀性...
乙醚,B,通风橱、防火柜,200ml,设备管理员,高挥发易燃...
```

**Part B — Structured Rows 文本：**
```
- sheet=Sheet1; row=2; 化学品名称=浓硫酸; 危险等级=A; 存储条件=防腐蚀柜、双人双锁; 最大存放量=500ml; 保管人角色=实验室安全员; 备注=强腐蚀性...
- sheet=Sheet1; row=3; 化学品名称=乙醚; 危险等级=B; 存储条件=通风橱、防火柜; 最大存放量=200ml; 保管人角色=设备管理员; 备注=高挥发易燃...
```

两部分以 `\n\n--- Structured Rows: Sheet1 ---\n` 分隔，合并为一个 chunk。

### 3.2 Fact Card 生成路径（Part A — CSV-like）

CSV-like 文本是逗号分隔行（`化学品名称,危险等级,...`）。

`FactCardGenerationWindowSupport.generateForChunk()` 六个生成器逐一尝试：
- `generateTableCards()`: `isTableLine()` 检查行首尾是否为 `|` → **不匹配**（无管道符）
- `generateBulletEnumCards()`: `isBulletLine()` 检查行首是否为 `- ` / `* ` / `+ ` → **不匹配**
- `generateKeyValueEnumCards()`: `isKeyValueLine()` 检查是否含 `:` / `=` / `：` → **不匹配**（逗号分隔，无键值分隔符）
- 其余三个生成器同样不匹配

**Part A 不产生任何 fact card。**

### 3.3 Fact Card 生成路径（Part B — Structured Rows）

每行格式：`- sheet=Sheet1; row=2; 化学品名称=浓硫酸; 危险等级=A; ...`

#### 路径 1: generateBulletEnumCards()

`isBulletLine()` 匹配（行首 `- `）→ 生成 `structure="bullet_list"` 的 fact card。

**Materializer 行为**：`isEligibleFactCard()` 通过（cardType=FACT_ENUM），但 `materialize()` 中 structure 检查：
```java
if (!"key_value_list".equals(structure)) {
    return List.of();  // ← 拒绝 "bullet_list"
}
```
**→ 0 terminal unit。**

#### 路径 2: generateKeyValueEnumCards()

调用 `findIndentedKeyValueItems()`：
1. `parseSequenceLine()` 检测到 `- ` 前缀 → 识别为序列行
2. 剥离 `- ` 后得到 content = `"sheet=Sheet1; row=2; 化学品名称=浓硫酸; ..."`
3. `OPTIONAL_KEY_VALUE_PATTERN` 匹配：key=`"sheet"`, value=`"Sheet1; row=2; 化学品名称=浓硫酸; ..."` （整行剩余部分）
4. 生成 **1 个** KeyValueItem，value 是整个分号连接的复合字符串

**Materializer 行为**：
- `terminalKey` = "sheet"
- `valueText` = "Sheet1; row=2; 化学品名称=浓硫酸; 危险等级=A; ..." 
- 单个 cell 的 value 可能不超 240 字符，但整行复合字符串远超 240 字符
- `shouldSkipValue()`: `valueText.length() > 240` → **返回 null，跳过**
- 即使 value 不超长，也只有一个 item（key="sheet"），不是期望的每列一个 item

**→ 0 terminal unit。**

### 3.4 XLSX 根因总结

```
ExcelTextExtractor
  │
  ├── Part A: CSV-like text
  │   └── 无 fact card 生成模式匹配 → 0 fact card
  │
  └── Part B: Structured Rows ("- sheet=S1; row=2; col1=val1; col2=val2; ...")
        │
        ├── generateBulletEnumCards() → structure="bullet_list"
        │   └── Materializer: structure 不匹配 → 0 terminal unit
        │
        └── generateKeyValueEnumCards() → 1 item, value=复合字符串
            └── Materializer: value 超长 → shouldSkipValue() → 0 terminal unit
```

**根本原因**：`ExcelTextExtractor` 的分号连接格式（`key1=val1; key2=val2; ...`）是为了紧凑检索窗口而设计的，但该格式与 `FactCardTerminalUnitMaterializer` 对独立 key-value item 的需求不兼容。`findIndentedKeyValueItems()` 不识别分号作为 item 分隔符，将整行解析为单个 item。

---

## 4. CSV 根因：Fact Card 不存在

### 4.1 CSV 提取产物

`CsvTextExtractor.extract()` 返回原始 CSV 文本作为 content：
```
设备编号,设备名称,设备类型,上次维护日期,维护周期(天),下次维护日期,维护等级,负责人
EQ-001,气相色谱仪,精密仪器,2026-04-15,90,2026-07-14,A,设备管理员
EQ-002,离心机,常规设备,2026-05-01,180,2026-10-28,B,设备管理员
EQ-003,电子天平,常规设备,2026-03-20,365,2027-03-20,C,实验指导教师
```

**关键发现**：`CsvTextExtractor` 已经通过 `StructuredTableContentBuilder` 解析了行/列结构并生成了 `structuredContentJson`，但该 JSON **仅存储到 `source_files.structured_content_json`**，**不进入 chunk 文本**。Fact card 生成只消费 chunk 文本，不读取 `structured_content_json`。

### 4.2 Fact Card 生成路径

逗号分隔的 CSV 行不匹配任何生成模式：
- `isTableLine()` → 无 `|` → 否
- `isBulletLine()` → 无 `- ` / `* ` / `+ ` → 否
- `isKeyValueLine()` → 无 `:` / `=` / `：` → 否
- 其余生成器 → 否

**→ 0 fact card。→ 0 terminal unit。**

### 4.3 CSV 根因总结

```
CsvTextExtractor
  │
  ├── content: 原始 CSV 文本（逗号分隔）
  │   └── 无 fact card 生成模式匹配 → 0 fact card → 0 terminal unit
  │
  └── structuredContentJson: 已解析的行/列结构 → 存储但未被 fact card 生成消费
```

**根本原因**：CSV 的逗号分隔格式不在任何 fact card 生成器的识别范围内。同时，extractor 已经解析出的结构化行/列数据（headers + rows）未被 fact card 生成链路消费。

### 4.4 旁证：为什么 FQ11 答案仍然 PASS

FQ11（"哪些设备的维护等级为 A"）的答案来自 **LLM generation 模式**（非 FALLBACK），答案质量来自 article 全文的 LLM 理解能力，不是来自 fact card 或 terminal unit 的结构化检索。`fact_card_fts` 和 `fact_card_terminal_fts` 均无命中，但 LLM 仍能从 article chunk 中提取答案。

---

## 5. 与 YAML 的对比

YAML 为什么能工作：

```
YAML 源文件
  │
  └── 缩进式 key: value 结构
        │
        └── findIndentedKeyValueItems()
              │
              ├── 每个 key: value 行 → 独立的 KeyValueItem
              ├── key = "max_borrow_days", value = "7"（短标量值）
              ├── key = "type", value = "精密仪器"（短标量值）
              │
              └── generateKeyValueEnumCards()
                    │
                    └── structure="key_value_list", 多个独立 item
                          │
                          └── Materializer: 每个 item → 一个 terminal unit
```

YAML 的 `key: value` 格式（每行一个键值对）天然与 `findIndentedKeyValueItems()` 的逐行解析兼容。每个 value 是短标量（数字、短字符串），通过 Materializer 的长度检查。

---

## 6. 修复建议

### 6.1 问题分解

| 问题 | 层 | 修复点 |
|---|---|---|
| XLSX: 分号连接的复合 value 无法拆分为独立 item | 编译提取/生成 | `ExcelTextExtractor` 或 `FactCardGenerationBaseSupport` |
| CSV: 逗号分隔格式不产生 fact card | 编译提取 | `CsvTextExtractor` 或新增 CSV→结构化转换 |

### 6.2 推荐方案：扩展 Extractors 输出格式

**核心思路**：让 XLSX 和 CSV 的 extractor 输出与 YAML 等价的结构化行格式（每行一个 `key=value`），使现有的 `findIndentedKeyValueItems()` 和 `FactCardTerminalUnitMaterializer` 无需修改即可工作。

#### 6.2.1 XLSX 修复

**修改文件**：`ExcelTextExtractor.java`

**变更**：修改 `toStructuredRowsText()` 的输出格式，将每行数据的每个 cell 输出为独立的 `- key=value` 行，而非分号连接的一行：

```
当前格式:
- sheet=Sheet1; row=2; 化学品名称=浓硫酸; 危险等级=A; 存储条件=防腐蚀柜...

建议格式:
- sheet=Sheet1
- row=2
- 化学品名称=浓硫酸
- 危险等级=A
- 存储条件=防腐蚀柜、双人双锁
```

这样每个 cell 变成独立的序列行，`findIndentedKeyValueItems()` 会为每行生成一个独立的 `KeyValueItem`，value 为单个 cell 内容（短字符串），Materializer 可正常物化。

**风险控制**：
- sheet/row 等元数据 cell 也会生成 terminal unit（如 `sheet = Sheet1`、`row = 2`）。这些是有效但不直接有用的 terminal unit，会增加噪声。可通过以下方式缓解：
  - 元数据 cell 的 value 很短（"Sheet1"、"2"），不太可能匹配用户查询
  - 或者将 sheet/row 作为 parentPath 的一部分而非独立 item（更复杂的改动）
- 当前 CSV-like 文本（Part A）保持不变，确保不破坏现有检索行为

#### 6.2.2 CSV 修复

**修改文件**：`CsvTextExtractor.java`

**变更**：在 `extract()` 返回的 content 中，追加结构化行文本（与 XLSX 格式一致）：

```java
// 当前: 只返回原始 CSV 文本
return new SourceExtractionResult(normalizedContent, ...);

// 建议: 追加结构化行文本
String structuredText = toStructuredRowsText(rows);  // 复用 XLSX 同款逻辑
String fullContent = normalizedContent + "\n\n--- Structured Rows ---\n" + structuredText;
return new SourceExtractionResult(fullContent, ...);
```

**CSV `toStructuredRowsText()` 实现**：与 XLSX 的 `toStructuredRowsText()` 逻辑相同，但不使用 sheet 名（CSV 无 sheet 概念），改用文件名或固定标识。该方法可提取到共享工具类 `StructuredTableContentBuilder` 中（该类已有 `buildJson()` 方法处理行数据）。

**风险控制**：
- 原始 CSV 文本（Part A）保持不变，不破坏现有 article chunk 的全文检索
- 结构化行文本（Part B）作为追加内容，仅增加 fact card 生成的机会

### 6.3 不推荐方案

| 方案 | 拒绝理由 |
|---|---|
| **在 `findIndentedKeyValueItems()` 中增加分号分隔** | 修改核心解析方法，blast radius 大。分号在 YAML/JSON/Markdown 中可能有其他语义。属于修消费者而非生产者。 |
| **在 Materializer 中增加分号分隔** | Materializer 不应承担"修正上游格式"的职责。且只能解决 XLSX，不能解决 CSV（CSV 压根没 fact card）。 |
| **在 dispatch 层增加 XLSX/CSV 特判** | 直接违反红线：不准按文件类型硬编码调度逻辑。且 terminal unit 根本不存在，调度了也没数据。 |
| **新增 `config/synonyms.yaml` 映射** | 与当前问题无关——问题不是 alias 匹配不上，是根本没有 terminal unit。 |
| **让 fact card 生成读取 `structured_content_json`** | 需要修改 `FactCardGenerationWindowSupport` 的数据源，改动面大。且 `structured_content_json` 是 table 级结构，不是 key-value item 结构。 |

### 6.4 实现顺序

```
Step 1: 修改 ExcelTextExtractor → 每 cell 一行 → 验证 XLSX terminal unit 生成
Step 2: 修改 CsvTextExtractor → 追加结构化行 → 验证 CSV terminal unit 生成
Step 3: 验证 FQ7/FQ11 的 fact_card_terminal_fts 有命中
```

每步一个可归因变量。两个 extractor 修改可以分开验证。

---

## 7. 是否需要清库重建

**需要**。修改 extractor 后，chunk 文本内容变化，需要重新 ingest + compile 才能生效。执行顺序：
1. `./scripts/reset-lattice-schema.sh`
2. 重新导入 5 份资料
3. 触发 compile
4. 验证 `fact_card_terminal_units` 表中有 XLSX/CSV 来源的 terminal unit

---

## 8. 提交策略建议

**建议将 Phase 1B + Phase 1C Layer 1 先提交，extractor 修复作为独立变更单独提交。**

理由：
1. Phase 1B（Reranker）+ Phase 1C Layer 1（中文 N-gram alias）已经过两轮独立验证（agentA 代码验证 + agentD 服务级验证），代码质量确认无问题
2. Extractor 修复是独立变更——修改的是 `documentparse` 模块，不涉及 query/compiler 模块
3. 两者组合提交会混合两个可归因变量（extractor 格式 + Materializer alias），不利于问题定位

**提交顺序**：
```
Commit 1: Phase 1B（Reranker + numericIntent + config） + Phase 1C Layer 1（中文 N-gram alias）
Commit 2: XLSX/CSV Extractor 结构化行格式修复
```

---

## 9. AgentA 最小实现提示词草案

```
你是 agentA，本轮任务：修复 XLSX 和 CSV extractor 的输出格式，
使结构化行文本能正确地被 fact card 生成器和 terminal unit 物化器消费。

## 背景
- XLSX structured row 当前格式: "- sheet=S1; row=2; col1=val1; col2=val2; ..."
  → findIndentedKeyValueItems() 将整行解析为 1 个 item（key="sheet"，value=复合字符串）
  → Materializer: value > 240 chars → shouldSkipValue() → 0 terminal unit
- CSV 当前不生成任何 structured row 文本 → 0 fact card → 0 terminal unit

## 目标
让每个 cell 变成独立的 key=value item，value 为单个 cell 的短文本，
使现有的 fact card 生成器和 terminal unit 物化器无需修改即可工作。

## 允许修改文件
- src/main/java/com/xbk/lattice/documentparse/extractor/ExcelTextExtractor.java
- src/main/java/com/xbk/lattice/documentparse/extractor/CsvTextExtractor.java
- src/main/java/com/xbk/lattice/documentparse/extractor/StructuredTableContentBuilder.java（如需提取共享方法）
- src/test/java/com/xbk/lattice/documentparse/extractor/（对应测试）

## 禁止修改文件
- FactCardTerminalUnitMaterializer.java（Phase 1C Layer 1，不动）
- FactCardTerminalUnitIntentReranker.java / FactCardTerminalUnitFtsSearchService.java（Phase 1B，不动）
- FactCardGenerationBaseSupport.java / FactCardGenerationWindowSupport.java（fact card 生成，不动）
- RetrievalStrategyResolver.java / KnowledgeSearchService.java / QueryGraphDefinitionBaseSupport.java（dispatch 层无问题，不动）
- schema.sql / lattice-query-semantic.yml / scripts / prompt / allowlist

## XLSX 修复要求
1. 修改 ExcelTextExtractor.toStructuredRowsText() 或 buildStructuredRowText()
2. 将每行数据输出为每 cell 一行的格式:
   "- sheet=Sheet1" (元数据行)
   "- row=2" (元数据行)
   "- 化学品名称=浓硫酸" (数据行)
   "- 危险等级=A" (数据行)
   而非当前的分号连接格式
3. 元数据 cell（sheet/row）和数据 cell 统一处理——都变成独立的 key=value 行
4. Part A（CSV-like 文本）保持不变
5. 确保 compactCellValue() 截断逻辑仍然生效

## CSV 修复要求
1. 修改 CsvTextExtractor.extract() 返回的 content
2. 在原始 CSV 文本后追加结构化行文本（与 XLSX Part B 格式一致）
3. 使用 CSV 的第一行作为 headers
4. 使用文件名（不含扩展名）作为 table 标识（替代 XLSX 的 sheet 名）
5. 提取共享的 toStructuredRowsText() 逻辑到 StructuredTableContentBuilder 或新建共享方法
6. 原始 CSV 文本（Part A）保持不变

## 通用要求
- 不能硬编码任何文件名、sheet 名、列名
- 不能根据文件扩展名做特殊分支（CSV 和 XLSX 分别修改各自的 extractor，不在调用方做分支）
- 元数据前缀（sheet/row/file）使用通用英文标识

## 测试要求
- 验证 ExcelTextExtractor 新格式：每行输出多个 "- key=value" 行
- 验证 CsvTextExtractor 新格式：content 包含结构化行文本
- 验证结构化行中每个 cell value 是单个单元格内容（非复合字符串）
- 验证 Part A 文本不变（CSV-like / 原始 CSV）

## 验证方式
- redline: bash scripts/scan-redline.sh special_cases_report.md → BLOCKER=0
- 定向测试: extractor 相关测试
- 全量: mvn test

## 输出
- *_fix_result_report.md
```

---

## 10. AgentD 验证方案草案

```
你是 agentD，本轮任务：验证 XLSX/CSV Extractor 修复后，
fact_card_terminal_fts channel 在表格类查询中是否有命中。

## 前置条件
- agentA 已完成 extractor 修复
- redline BLOCKER=0，全量 mvn test 通过

## 验证步骤
1. 清库重建: ./scripts/reset-lattice-schema.sh
2. 重新导入 fresh eval 5 份资料
3. 触发 compile
4. 数据库验证:
   a. 查询 fact_card_terminal_units 表，确认存在 source_file_id 指向 XLSX/CSV 的 terminal unit
   b. 抽样检查 XLSX terminal unit 的 field_aliases_json 是否包含中文 N-gram
   c. 抽样检查 CSV terminal unit 的 field_aliases_json 是否包含中文 N-gram
5. 服务级验证:
   a. FQ7: 检查 fact_card_terminal_fts channel 是否返回 hits
   b. FQ7: 检查命中是否包含中文列头对应的 terminal unit（如 "存储条件=..."、"保管人角色=..."）
   c. FQ11: 检查 fact_card_terminal_fts channel 是否返回 hits
   d. FQ11: 检查命中是否包含中文列头对应的 terminal unit（如 "维护等级=A"）
6. 保护回归:
   a. FQ3/FQ4/FQ6/FG1/FG2（YAML 类）: fact_card_terminal_fts 行为不变
   b. 全量 19 题 fresh eval 无新增 FAIL
   c. FQ7/FQ11 答案质量不退化
7. 非目标验证:
   a. XLSX/CSV 的 article chunk FTS/全文检索行为不变

## Gate 判定
- XLSX/CSV terminal unit 存在且 fact_card_terminal_fts 有命中 → PASS
- 全量 19 题无新增回归 → PASS
- 两项均 PASS → 整体 PASS

## 输出
- *_verification_report.md
```

---

## 11. 附录：完整数据流对照

### 11.1 YAML（正常路径）

```
equipment-borrowing-policy.yaml
  │
  └── Ingestion → chunk text:
      "borrowing_system:\n  name: 校园实验室设备预约系统\n  version: v2.3.1\n..."
        │
        └── findIndentedKeyValueItems()
            → KeyValueItem(key="name", value="校园实验室设备预约系统")
            → KeyValueItem(key="version", value="v2.3.1")
            → KeyValueItem(key="max_concurrent_requests", value="50")
              │
              └── generateKeyValueEnumCards()
                  → FactCardRecord(cardType=FACT_ENUM, structure="key_value_list", items=[3 items])
                    │
                    └── Materializer.materialize()
                        → terminalUnit(keyPath="borrowing_system.version", value="v2.3.1")
                        → terminalUnit(keyPath="borrowing_system.max_concurrent_requests", value="50")
                          │
                          └── fact_card_terminal_units 表有行 → FTS 可检索
```

### 11.2 XLSX（当前断裂路径 → 修复后期望路径）

```
chemical-storage-grading.xlsx
  │
  └── ExcelTextExtractor
      ├── Part A: "化学品名称,危险等级,...\n浓硫酸,A,..." → 无 fact card
      │
      └── Part B: "- sheet=S1; row=2; 化学品名称=浓硫酸; 危险等级=A; ..."
            │
            ├── [当前] generateKeyValueEnumCards()
            │   → 1 item: key="sheet", value="S1; row=2; 化学品名称=浓硫酸; ..." (>240 chars)
            │   → Materializer: shouldSkipValue() → 0 terminal unit
            │
            └── [修复后] generateKeyValueEnumCards()
                → KeyValueItem(key="sheet", value="S1")
                → KeyValueItem(key="row", value="2")
                → KeyValueItem(key="化学品名称", value="浓硫酸")
                → KeyValueItem(key="危险等级", value="A")
                → KeyValueItem(key="存储条件", value="防腐蚀柜、双人双锁")
                  │
                  └── Materializer.materialize()
                      → terminalUnit(keyPath="...化学品名称", value="浓硫酸")
                      → terminalUnit(keyPath="...危险等级", value="A")
                      → terminalUnit(keyPath="...存储条件", value="防腐蚀柜、双人双锁")
```

### 11.3 CSV（当前断裂路径 → 修复后期望路径）

```
equipment-maintenance-schedule.csv
  │
  └── CsvTextExtractor
      ├── [当前] content: "设备编号,设备名称,...\nEQ-001,气相色谱仪,..."
      │   → 无 fact card 生成模式匹配 → 0 fact card → 0 terminal unit
      │
      └── [修复后] content: 原始 CSV + "\n\n--- Structured Rows ---\n- 设备编号=EQ-001\n- 设备名称=气相色谱仪\n..."
            │
            └── generateKeyValueEnumCards()
                → KeyValueItem(key="设备编号", value="EQ-001")
                → KeyValueItem(key="设备名称", value="气相色谱仪")
                → KeyValueItem(key="维护等级", value="A")
                  │
                  └── Materializer.materialize()
                      → terminalUnit(keyPath="...设备编号", value="EQ-001")
                      → terminalUnit(keyPath="...维护等级", value="A")
```

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮新增报告：`terminal_unit_phase1c_table_terminal_dispatch_root_cause_analysis_report.md`
