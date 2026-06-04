# FQ4/FG1 Terminal Entity Context Metadata — 最小数据模型修复设计报告

分析时间：2026-06-04
执行人：agentB（治理/链路分析 Agent）
类型：只读根因分析与通用数据模型设计，无代码修改

---

## 1. 结论：当前是否应继续 BLOCKED

**是，当前应保持 BLOCKED，等待数据模型变更后再解除。**

当前 `entityContextMatchesQuery` 在移除污染源（content、displayText）后，仅依赖 `parentPath` 和 `pathSegments` 两个纯结构信号。这两个字段均不携带实体展示值（如"常规设备"、"大型设备"、"精密仪器"），因此无法匹配中文 query token。多目标聚合的 entity context guard 对所有附加候选返回 false，FQ4/FG1 仍只返回单条结论。

解除阻塞需要：在 `metadataJson` 中新增一个**安全的实体展示上下文**字段，且该字段的数据来源必须在编译期已有（Materializer 的 `siblingDescriptors`），不需要新增 DB 列或 SQL join。

---

## 2. 当前 metadataJson 字段盘点

来源：`FactCardTerminalUnitMaterializer.buildMetadataJson()`（第 631-694 行）

### 2.1 已有字段清单

| 字段 | 示例值 | 类型 | 属于实体上下文？ | 安全用于 entityContextMatchesQuery？ |
|------|--------|------|:---:|:---:|
| `channel` | `"fact_card_terminal_fts"` | string | 否 | 否（通道标识） |
| `terminalUnitId` | null | null | 否 | — |
| `unitId` | `"fact-card-terminal:..."` | string | 否（记录标识） | 否 |
| `terminalUnitIdentity` | `"terminal-unit:..."` | string | 否（融合身份） | 否 |
| `factCardId` | 123 | number | 否（卡片标识） | 否 |
| `cardId` | `"equipment-borrowing-policy--..."` | string | 否（卡片标识） | 否 |
| `sourceFileId` | 3 | number | 否（源文件标识） | 否 |
| `cardType` | `"FACT_ENUM"` | string | 否（卡片类型） | 否 |
| `answerShape` | `"POLICY"` | string | 否（答案形态） | 否 |
| `structure` | `"key_value_list"` | string | 否（结构类型） | 否 |
| `itemIndex` | 0 | number | 否（序号） | 否 |
| `keyPath` | `"equipment_types[0].deposit_amount"` | string | 否（字段路径） | 否 |
| `parentPath` | `"equipment_types[0]"` | string | **是**（实体结构路径） | **是**，但无 CJK |
| `terminalKey` | `"deposit_amount"` | string | 否（字段标识） | 否 |
| `fieldLabel` | `"deposit_amount"` | string | 否（字段展示名） | 否 |
| `fieldDescription` | `"parentPath: equipment_types[0]; field: deposit_amount; valueType: number; context: 常规设备, 设备管理员"` | string | **混杂** | **否**（混有字段语义） |
| `value` | `"100"` | string | 否（字段值） | 否 |
| `normalizedValue` | `"100"` | string | 否（归一化值） | 否 |
| `valueType` | `"number"` | string | 否（值形态） | 否 |
| `displayText` | `"equipment_types[0].deposit_amount = 100"` | string | 否（字段展示） | 否 |
| `fieldAliases` | `["deposit_amount", "deposit amount", "押金金额", ...]` | array | 否（字段语义） | **绝对禁止**（会破坏单目标保护） |
| `pathSegments` | `["equipment_types", "0"]` | array | **是**（结构片段） | **是**，但无 CJK |
| `sourceChunkIds` | `[10, 11]` | array | 否（来源标识） | 否 |
| `articleIds` | `[5]` | array | 否（文章标识） | 否 |
| `sourceRefs` | `{...}` | object | 否（来源回指） | 否 |

### 2.2 关键发现

**`fieldDescription` 中已经包含了实体展示上下文**（`context: 常规设备, 设备管理员`），但它是作为字符串嵌入在与字段语义混杂的文本中。这导致：
- 如果直接使用 `fieldDescription`，会把 `"deposit_amount"`、`"number"`、`"equipment_types[0]"` 等字段语义也带入 entity context haystack
- 如果完全不使用 `fieldDescription`，则丢失了唯一携带 CJK 实体展示值的信号

**理想方案**：把 `fieldDescription` 中 `context:` 部分的数据独立出来，作为一个干净的 JSON 数组字段。

---

## 3. 安全 entity context 的候选来源

### 3.1 来源一：Materializer 的 `collectParentPathDescriptors()`（推荐）

**位置**：`FactCardTerminalUnitMaterializer.java` 第 390-435 行

**机制**：在 `materialize()` 方法的第一步，遍历所有 items，按 `parentPath` 分组收集 CJK string 类型的 sibling 值作为 descriptor。每个 parentPath 最多保留 2 个去重后的 descriptor。

**对于 `equipment-borrowing-policy.yaml` 的事实卡**，该方法的产出：

| parentPath | siblingDescriptors |
|------------|-------------------|
| `equipment_types[0]` | `["常规设备", "设备管理员"]` |
| `equipment_types[1]` | `["精密仪器", "实验室主任"]` |
| `equipment_types[2]` | `["大型设备", "实验室主任"]` |

**为什么这是安全的**：
- 数据来源是源文档中同 parentPath 下的 sibling item 值（`valueText`）
- 只筛选 `valueType=string` + 含 CJK + 长度 2-20 的值
- 不来自 query、eval expected、标准答案或日志
- 在 compile 期固化，query 期只读消费

**当前用途**：仅用于构建 `fieldDescription` 字符串（第 445-469 行），混在字段语义文本中。

### 3.2 来源二：fact_cards 的 title（不推荐）

通过 SQL join `fact_cards` 表获取 `fc.title`。问题：
- `fc.title` 是整张事实卡的标题（如 "equipment borrowing policy"），不是 entity 级的
- 不能区分 `equipment_types[0]` 和 `equipment_types[1]`

### 3.3 来源三：article 的 title/content（不推荐）

- 粒度太粗（整篇 article），无法定位到具体 entity
- 需要额外的 SQL join，查询期开销大

---

## 4. 推荐最小方案

### 方案：Materializer 在 metadataJson 中新增 `contextDisplayValues` 字段

**改动文件**：仅 `FactCardTerminalUnitMaterializer.java` 一个文件

**改动方法**：仅 `buildMetadataJson()` 一个方法（第 631-694 行）

**核心思路**：`materializeItem()` 已经接收了 `parentPathDescriptors` 参数并传递给 `buildFieldDescription()`。只需要在 `buildMetadataJson()` 中也接收 `siblingDescriptors`，将其序列化为 `contextDisplayValues` 数组写入 metadataJson。

### 4.1 具体改动

```
materializeItem() 中（第 143-144 行）：
  已有：List<String> siblingDescriptors = parentPathDescriptors.getOrDefault(parentPath, List.of());
  已有：传给 buildFieldDescription(parentPath, fieldLabel, valueType, siblingDescriptors, normalizedValue)

buildMetadataJson() 签名变更：
  新增参数：List<String> siblingDescriptors
  新增 JSON 字段：
    ArrayNode contextValuesNode = rootNode.putArray("contextDisplayValues");
    for (String d : siblingDescriptors) {
        contextValuesNode.add(d);
    }

materializeItem() 调用点变更：
  将 siblingDescriptors 也传给 buildMetadataJson()
```

### 4.2 产生的 metadataJson 示例

对于 `equipment_types[0].deposit_amount = 100`：

```json
{
  "channel": "fact_card_terminal_fts",
  "parentPath": "equipment_types[0]",
  "terminalKey": "deposit_amount",
  "displayText": "equipment_types[0].deposit_amount = 100",
  "fieldAliases": ["deposit_amount", "deposit amount", "押金金额", "保证金金额", "押金"],
  "fieldDescription": "parentPath: equipment_types[0]; field: deposit_amount; valueType: number; context: 常规设备, 设备管理员",
  "contextDisplayValues": ["常规设备", "设备管理员"],
  ...
}
```

对于 `equipment_types[2].deposit_amount = 1000`：

```json
{
  "contextDisplayValues": ["大型设备", "实验室主任"],
  ...
}
```

### 4.3 Query builder 侧自动生效

`buildEntityContextHaystack()`（当前代码）已有向前兼容扫描：

```java
node.fieldNames().forEachRemaining(fieldName -> {
    if (fieldName.contains("context")) {
        // 自动读取 contextDisplayValues 数组
    }
});
```

**不需要修改 Query builder 任何代码**。新增的 `contextDisplayValues` 字段名包含 `"context"`，会被自动扫描并纳入 entity context haystack。

### 4.4 Enricher 兼容性

`LlmFactCardTerminalUnitFieldAliasEnricher.rebuildMetadataJsonFieldAliases()` 读取原始 metadataJson 为 `ObjectNode`，只替换 `fieldAliases` 数组，保留所有其他字段。`contextDisplayValues` 会被透传，不被修改。

---

## 5. 不推荐方案及原因

| 方案 | 不推荐原因 |
|------|-----------|
| **在 Query builder 中解析 `fieldDescription` 字符串提取 context** | 字符串解析脆弱，依赖 `"context: "` 前缀格式；未来 Materializer 改变 fieldDescription 格式会导致静默失败；违反了"不在 Query 主链解析 fieldDescription 字符串"的设计约束 |
| **新增 DB 列 `entity_context_json`** | 需要 schema 迁移、Record 新字段、Mapper XML 变更、JDBC Repository 变更；改动面大；jsonb metadataJson 足以承载此数据 |
| **在 SQL `searchLexical` 中 join `fact_cards` 动态补充** | 运行时 join 开销；fact_cards 表没有 entity 级上下文数据；如果未来需要，属于独立优化，不应作为当前最小修复 |
| **使用 `hit.getContent()` 或 `fieldDescription` 整体作为 entity context** | 已被 `context_guard_fix_result_report.md` 明确排除——会将 fieldAliases/fieldDescription 污染带回 entity context，破坏单目标保护 |
| **在 Query builder 中做 parentPath → 实体名的映射表** | 这是 case 特判的变体——需要为每个文档的每个 parentPath 维护映射；不可泛化 |
| **降低 entityContextMatchesQuery 的阈值或移除 guard** | 会导致单目标问题误纳入无关实体的同名字段（如"精密仪器逾期罚金"误带出"常规设备"的 late_fee_per_day）；entity context guard 的设计目的是正确且必要的 |

---

## 6. 影响边界

### 6.1 Materializer（唯一修改点）

| 项 | 影响 |
|----|------|
| `materializeItem()` 方法 | 将已有的 `siblingDescriptors` 额外传给 `buildMetadataJson()` |
| `buildMetadataJson()` 方法签名 | 新增 `List<String> siblingDescriptors` 参数 |
| `buildMetadataJson()` 方法体 | 新增约 5 行：创建 `contextDisplayValues` 数组并填充 |
| `collectParentPathDescriptors()` | **不变**（已经收集了正确的数据） |
| `buildFieldDescription()` | **不变**（继续使用 siblingDescriptors 构建 fieldDescription） |
| 其余所有方法 | **不变** |

### 6.2 FactCardTerminalUnitRecord

**不变**。`contextDisplayValues` 存储在 `metadataJson`（jsonb 列）内部，不需要新字段。

### 6.3 SQL Mapper（FactCardTerminalUnitMapper.xml）

**不变**。
- `upsert`：`metadataJson` 已经作为 jsonb 整体写入
- `searchLexical`：`metadata_json` 已经通过 `jsonb_build_object` 补充后透传
- 新增的 `contextDisplayValues` 是 metadataJson 内部字段，自动跟随

### 6.4 Enricher（LlmFactCardTerminalUnitFieldAliasEnricher）

**不变**。`rebuildMetadataJsonFieldAliases()` 保留除 `fieldAliases` 外的所有字段。

### 6.5 Query builder（AnswerFallbackConclusionBuilder）

**不变**。`buildEntityContextHaystack()` 已有 `fieldName.contains("context")` 扫描逻辑，自动发现 `contextDisplayValues`。

### 6.6 Reranker / Candidate Supply / FTS Search

**不变**。这些层不消费 metadataJson 中的 entity context 字段。

---

## 7. 风险与保护用例

### 7.1 单目标问题保护

| 场景 | entityContextMatchesQuery 行为 | 结果 |
|------|-------------------------------|------|
| "精密仪器的逾期罚金是多少？" | 精密仪器实体：`contextDisplayValues=["精密仪器", "实验室主任"]` → "精密" bigram 匹配 "精密仪器" → **true** | bestCandidate 通过 |
| 同上，常规设备实体的 late_fee_per_day | 常规设备实体：`contextDisplayValues=["常规设备", "设备管理员"]` → query 中不含"常规设备"或"设备管理员" → **false** | 附加候选被排除 ✓ |
| "设备的 API 地址是什么？" | query 无 entity 特定 token，所有 entity 都可能匹配 | 取决于 query token 是否命中 contextDisplayValues |

**保护机制**：`entityContextMatchesQuery` 要求 query token 必须能匹配到实体的展示上下文。单目标问题的 query 通常只提到一个 entity 的名称，其他 entity 的 `contextDisplayValues` 不会被匹配。

### 7.2 误纳入无关 sibling 的风险

`contextDisplayValues` 包含同 parentPath 下的所有 CJK string 值（最多 2 个）。对于 `equipment_types[0]`，包括 `"常规设备"` 和 `"设备管理员"`。

**风险**：query 中提到"设备管理员"时，`equipment_types[0]` 的所有字段（包括 `deposit_amount=100`）都会被纳入 entity context 匹配。

**评估**：这不是误纳入。"设备管理员"确实是 `equipment_types[0]` 的实体属性，query 提到它时匹配到该实体是合理行为。如果用户问"设备管理员的押金是多少"，返回 `equipment_types[0].deposit_amount = 100` 比不返回更好。

### 7.3 非 YAML 类型事实卡的兼容性

对于 XLSX/CSV 事实卡：
- `collectParentPathDescriptors()` 同样会收集同 parentPath 下的 CJK string 值
- 例如 XLSX 中 `parentPath="[14]"` 的行会有 `contextDisplayValues=["丙酮", "通风橱"]`
- 这些值同样是安全的实体展示上下文
- 如果 parentPath 下没有 CJK string 值（纯数字/英文），`siblingDescriptors` 为空 → `contextDisplayValues` 为空数组 → `buildEntityContextHaystack` 仍可使用 parentPath + pathSegments 作为 fallback

### 7.4 与 fieldAliases 的隔离

`contextDisplayValues` 出现在 metadataJson 中，但：
- `buildEntityContextHaystack` 已移除对 fieldAliases 的引用
- `fieldAliases` 仍然存在于 metadataJson，但仅被 `countFieldAliasTokenMatches` 使用（字段语义匹配）
- 两者在 Query builder 中的消费路径完全隔离，不会交叉污染

---

## 8. 建议交给 agentA 的最小代码修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
在 terminal unit metadataJson 中新增安全的实体展示上下文字段（contextDisplayValues），
解除 entityContextMatchesQuery 因缺少 CJK 实体信号而导致的 BLOCKED 状态。

根因：
Materializer 已在 compile 期收集了每个 parentPath 的 sibling descriptor（CJK string 值），
但这些值只混在 fieldDescription 字符串中，未作为独立字段写入 metadataJson。
Query builder 的 buildEntityContextHaystack 已具备向前兼容的 "context" 字段自动扫描能力，
只等数据层提供干净的实体上下文字段。

修改范围：
- 只修改 src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java
- 只修改两个方法：materializeItem() 的调用点 + buildMetadataJson() 的签名和实现
- 不改其他任何文件

修改要求：
1. buildMetadataJson() 方法签名新增 List<String> siblingDescriptors 参数
2. 在 buildMetadataJson() 方法体中，pathSegments 数组写入之后，新增：
   ArrayNode contextValuesNode = rootNode.putArray("contextDisplayValues");
   for (String d : siblingDescriptors) {
       contextValuesNode.add(d);
   }
3. materializeItem() 中，将已有的 siblingDescriptors 变量额外传递给 buildMetadataJson()

禁止事项：
- 禁止修改 FactCardTerminalUnitRecord.java（不需要新字段，数据在 metadataJson 内）
- 禁止修改 FactCardTerminalUnitMapper.xml（jsonb 列自动承载新字段）
- 禁止修改 LlmFactCardTerminalUnitFieldAliasEnricher.java（rebuildMetadataJsonFieldAliases 已透传）
- 禁止修改 AnswerFallbackConclusionBuilder.java（buildEntityContextHaystack 已自动扫描）
- 禁止修改 AnswerFallbackMarkdownBuilder.java
- 禁止修改 FactCardTerminalUnitFtsSearchService / FactCardTerminalUnitIntentReranker
- 禁止修改 tests、scripts、prompt、config、题集
- 禁止修改 schema.sql（不需要新列）
- 禁止在 contextDisplayValues 中写入任何业务词、字段名、题号、文件名特判
- 禁止提交 commit

通用性要求：
- contextDisplayValues 的数据来源是 Materializer 已有的 siblingDescriptors（collectParentPathDescriptors 产出）
- 不依赖具体业务域、文档标题、字段名、答案值
- 对所有 FACT_ENUM key_value_list 事实卡类型一视同仁生效
- 空 siblingDescriptors 时 contextDisplayValues 为空数组（不影响现有行为）

redline 与 mvn test 要求：
- redline BLOCKER=0
- mvn test 全量通过

数据重建说明：
- 修改后需要重新编译资料（清库 + 上传 + 编译）才能在新的 terminal unit 中看到 contextDisplayValues
- 这是数据模型变更，不是运行时热修复
- agentD 验证时需要执行完整清库重建流程

agentD runtime gate 要求（在 agentA 修复完成后）：
1. 清库（bash scripts/reset-lattice-schema.sh）
2. 确保 LLM 绑定就位（含 compile/field-alias-enricher）
3. 上传 Fresh Eval 2 资料并编译
4. 只读 SQL 验证：查询 fact_card_terminal_units 的 metadata_json 是否包含 "contextDisplayValues" 数组
5. 抓取 FQ4/FG1 的 [TU_TRACE] 日志，观察 additionalCandidates 计数是否 > 0
6. FQ4 API 回答是否包含 deposit_amount=100 和 deposit_amount=1000
7. FG1 API 回答是否包含 late_fee_per_day=20 和 late_fee_per_day=5
8. FQ3 单目标保护回归：仍只返回一条结论
```

---

## 9. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未跑 mvn test / redline / baseline
- [x] 未读取 hidden eval
- [x] 所有结论基于源码只读分析 + 已有报告交叉验证
- [x] 推荐方案中无 case 特判
- [x] 推荐字段名 `contextDisplayValues` 为通用名称，不含业务词
- [x] 数据来源为源文档结构（siblingDescriptors），不含 query/eval/答案/日志信息
- [x] 修复提示词草案中无样例专属字符串
