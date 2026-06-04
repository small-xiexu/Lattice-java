# FQ4/FG1 Terminal Entity Context Metadata — 修复结果报告

修复时间：2026-06-04
执行人：agentA（代码执行 Agent）
前置设计：`fq4_fg1_terminal_entity_context_metadata_design_report.md`（agentB）
前置修复：`fq4_fg1_multi_target_terminal_context_guard_fix_result_report.md`（agentA）

---

## 1. 根因确认

### 1.1 当前状态

- Query builder 的 `buildEntityContextHaystack` 已移除 `hit.getContent()` 和 `displayText` 污染源，仅使用 `parentPath`、`pathSegments` 及名称含 `context` 的字段
- 当前 metadataJson 缺少安全 CJK entity context——`parentPath`、`pathSegments` 均为纯结构信号（Latin + 数字），无法匹配中文 query token
- Materializer 在 compile 期已通过 `collectParentPathDescriptors()` 收集了每个 parentPath 的 sibling descriptor（CJK string 值），但这些值只混在 `fieldDescription` 字符串中（"context: 常规设备, 设备管理员"），没有作为独立 JSON 字段写入 metadataJson

### 1.2 数据来源验证

`collectParentPathDescriptors()` 从源文档的 items 中筛选：
- `valueType=string` + 含 CJK + 长度 2-20
- 按 `parentPath` 分组，每个 parentPath 最多 2 个（去重）
- 数据来自源文档，不来自 query/eval/答案/日志

对于 equipment-borrowing-policy.yaml：
| parentPath | siblingDescriptors |
|------------|-------------------|
| `equipment_types[0]` | `["常规设备", "设备管理员"]` |
| `equipment_types[1]` | `["精密仪器", "实验室主任"]` |
| `equipment_types[2]` | `["大型设备", "实验室主任"]` |

### 1.3 本轮方案

将这些 siblingDescriptors 写入 `contextDisplayValues` 字段（JSON 数组），存储在 metadataJson 中。Query builder 的 `fieldName.contains("context")` 扫描会自动发现并纳入 entity context haystack。

**本轮只改 Materializer，不改 Query builder、Schema、Mapper、Record、Enricher 中的任何一个。**

---

## 2. 修改文件与修改范围

**文件**：`src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java`

**修改点**：
1. `buildMetadataJson()` 方法签名 — 新增 `List<String> siblingDescriptors` 参数
2. `buildMetadataJson()` 方法体 — 新增 `contextDisplayValues` 数组
3. `materializeItem()` 调用点 — 将已有的 `siblingDescriptors` 传给 `buildMetadataJson()`

**未修改**：所有其他方法、所有其他文件。

---

## 3. 具体改动

### 3.1 `materializeItem()` 调用点（第 171-189 行）

将已有的 `siblingDescriptors` 变量（第 143 行已声明）额外传递给 `buildMetadataJson()`。

### 3.2 `buildMetadataJson()` 签名

新增最后一个参数 `List<String> siblingDescriptors`。

### 3.3 `buildMetadataJson()` 方法体

在 `pathSegments` 数组写入之后、`fieldAliases` 数组写入之前，新增：

```java
ArrayNode contextValuesNode = rootNode.putArray("contextDisplayValues");
if (siblingDescriptors != null) {
    for (String descriptor : siblingDescriptors) {
        if (hasText(descriptor)) {
            contextValuesNode.add(descriptor.trim());
        }
    }
}
```

**常量字段名**：`"contextDisplayValues"` — 小写 `context` 开头，确保 Query builder 的 `fieldName.contains("context")` 扫描可以发现。

### 3.4 产生的 metadataJson 示例

对于 `equipment_types[0].deposit_amount = 100`：

```json
{
  ...
  "parentPath": "equipment_types[0]",
  "terminalKey": "deposit_amount",
  "pathSegments": ["equipment_types", "0"],
  "contextDisplayValues": ["常规设备", "设备管理员"],
  "fieldAliases": ["deposit_amount", "deposit amount", "押金金额", ...],
  "fieldDescription": "parentPath: equipment_types[0]; field: deposit_amount; valueType: number; context: 常规设备, 设备管理员",
  ...
}
```

---

## 4. 数据来源说明

`contextDisplayValues` 的数据来自 `collectParentPathDescriptors()`（第 390-435 行）：

- **来源**：源文档中同 `parentPath` 下的 sibling item 的 `valueText`
- **筛选规则**：`valueType=string` + 含 CJK + 长度 2-20
- **限制**：每个 parentPath 最多 2 个（去重后）
- **安全性**：不来自 query、eval expected、标准答案、日志；在 compile 期固化

对于非 YAML 事实卡（如 XLSX/CSV），`collectParentPathDescriptors()` 同样生效。对于纯数字/英文的 parentPath（无 CJK string 值），`siblingDescriptors` 为空 → `contextDisplayValues` 为空数组。

---

## 5. 为什么不是 case 特判

- `contextDisplayValues` 是通用字段名，不含任何业务词
- 数据来源是通用规则（CJK string 值），不依赖具体业务域、文档标题、字段名
- 对所有 `FACT_ENUM` + `key_value_list` 事实卡类型一视同仁生效
- 不写入任何样例专属字符串或字段名判断

---

## 6. 为什么不需要 schema / mapper / record 变更

- `contextDisplayValues` 存储在 `metadataJson`（jsonb 列）内部
- `FactCardTerminalUnitMapper.xml` 的 `upsert` 中 `metadataJson` 整体写入 jsonb，新字段自动跟随
- `FactCardTerminalUnitMapper.xml` 的 `searchLexical` 中 `metadata_json` 通过 `|| jsonb_build_object(...)` 透传，`contextDisplayValues` 自动出现在查询结果中
- `FactCardTerminalUnitRecord` 不需要新字段
- `LlmFactCardTerminalUnitFieldAliasEnricher` 的 `rebuildMetadataJsonFieldAliases()` 只替换 `fieldAliases` 数组，透传其余字段

---

## 7. redline 结果

`BLOCKER=0`

---

## 8. mvn test 结果

**995/0/0/0, BUILD SUCCESS**

---

## 9. 下一步交给 agentD 的 runtime gate 建议

1. 清库（`bash scripts/reset-lattice-schema.sh`）
2. 确保 LLM 绑定就位（含 `compile/field-alias-enricher`）
3. 上传 Fresh Eval 2 资料并编译
4. 只读 SQL 验证：
   ```sql
   SELECT terminal_key, parent_path,
          metadata_json::jsonb -> 'contextDisplayValues' AS context_display_values
   FROM fact_card_terminal_units
   WHERE terminal_key IN ('deposit_amount', 'late_fee_per_day');
   ```
   确认 `contextDisplayValues` 数组存在且含中文实体展示值
5. 抓取 FQ4/FG1 的 `[TU_TRACE]` 日志，观察 `additionalCandidates` 计数 > 0
6. FQ4 API 回答包含 `deposit_amount = 100` 和 `deposit_amount = 1000`
7. FG1 API 回答包含 `late_fee_per_day = 20` 和 `late_fee_per_day = 5`
8. FQ3 单目标保护回归：仍只返回一条 `max_borrow_days`
9. 单问"精密仪器逾期罚金"：不得带出常规设备或大型设备的 `late_fee_per_day`

---

## 10. 明确声明

- [x] 只修改了 `FactCardTerminalUnitMaterializer.java` 一个文件
- [x] 只修改了 `materializeItem()` 调用点 + `buildMetadataJson()` 签名和实现
- [x] 新增字段名 `contextDisplayValues`（小写 `context` 开头）
- [x] 数据来源：`collectParentPathDescriptors()` 产出的 siblingDescriptor
- [x] 未修改 `FactCardTerminalUnitRecord.java`
- [x] 未修改 `FactCardTerminalUnitMapper.xml`
- [x] 未修改 `LlmFactCardTerminalUnitFieldAliasEnricher.java`
- [x] 未修改 `AnswerFallbackConclusionBuilder.java`
- [x] 未修改 `AnswerFallbackMarkdownBuilder.java`
- [x] 未修改 `FactCardTerminalUnitFtsSearchService`
- [x] 未修改 `FactCardTerminalUnitIntentReranker`
- [x] 未修改 `schema.sql`
- [x] 未修改 tests、scripts、prompt、config、题集
- [x] 未写入任何业务词/字段名/样例字符串
- [x] 未从 query/eval/答案/日志生成 contextDisplayValues
- [x] 未提交 commit
- [x] redline `BLOCKER=0`
- [x] mvn test `995/0/0/0, BUILD SUCCESS`

