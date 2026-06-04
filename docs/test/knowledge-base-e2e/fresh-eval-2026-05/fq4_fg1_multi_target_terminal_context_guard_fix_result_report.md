# FQ4/FG1 多目标 Terminal Context Guard 修复结果报告

修复时间：2026-06-04
执行人：agentA（代码执行 Agent）
前置修复：`fq4_fg1_multi_target_terminal_conclusion_fix_result_report.md`
前置分析：`fq4_fg1_multi_target_terminal_conclusion_analysis_report.md`

---

## 1. 根因确认

### 1.1 污染源确认

`FactCardTerminalUnitMapper.xml` 第 202-203 行：

```sql
trim(concat_ws(E'\n', unit.display_text, unit.field_description,
    unit.field_aliases_json::text)) as content,
```

**terminal unit 的 `content` 字段 = `display_text + field_description + field_aliases_json`**

其中：
- `field_description` — 字段语义，非实体上下文
- `field_aliases_json` — 字段语义（含 LLM 生成的中文别名），非实体上下文

### 1.2 当前代码污染路径

`buildEntityContextHaystack`（第 787-813 行）使用 `hit.getContent()` 构建 entity context haystack：

```java
String content = hit.getContent();
if (content != null) {
    sb.append(content.toLowerCase());  // ← 包含了 field_description + field_aliases_json
}
```

这导致 `fieldAliases`（如"逾期日费"、"押金金额"）和 `fieldDescription` 被重新带入 entity context haystack，破坏了"entity context 不使用字段语义"的设计原则。

### 1.3 后果

单目标问题（如"精密仪器的逾期罚金是多少?"）中，精密仪器的 `late_fee_per_day=20` 是 bestCandidate。常规设备的 `late_fee_per_day=5` 虽然 terminalKey 相同，但由于 fieldAliases 包含"每日逾期费"，`hit.getContent()` 会让它通过 entity context 检查。如果该候选的 ftmc 也 >= minThreshold，就会被误纳入——即使 query 中完全没有提到"常规设备"。

---

## 2. 修改文件与修改范围

**文件**：`src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

**修改方法**：
- `buildEntityContextHaystack` — 移除全部污染源

**未修改**：多目标聚合主逻辑（Phase 2）、qf/ftmc/atmc/fusedScore、CandidateProfile、extractTerminalKey 等。

---

## 3. 具体改动说明

### 3.1 `buildEntityContextHaystack` — 移除污染源

**移除**：
- `hit.getContent()` — 包含 `display_text + field_description + field_aliases_json`，三者均为字段语义污染
- `displayText`（metadataJson 中的 displayText）— 包含字段值和路径，非实体上下文

**保留的安全字段**（纯结构实体级信号）：
- `parentPath` — 实体结构路径（如 "equipment_types[0]"）
- `pathSegments` — 路径片段数组（如 ["equipment_types", "0"]）

### 3.2 排除字段清单

以下字段明确不出现在 entity context haystack 中：

| 字段 | 排除原因 |
|------|----------|
| `hit.getContent()` | 包含 field_description + field_aliases_json + display_text |
| `displayText` | 字段值/路径，非实体上下文 |
| `fieldAliases` | 字段语义 |
| `fieldDescription` | 字段语义 |
| `terminalKey` | 字段标识符 |
| `value` / `normalizedValue` | 字段值 |
| `valueType` | 值形态 |
| `channel` | 通道标识 |
| `unitId` / `terminalUnitId` / `terminalUnitIdentity` | 记录标识符 |
| `factCardId` / `cardId` | 卡片标识符 |

### 3.3 当前安全信号覆盖分析

| 信号 | 示例值 | 能否匹配 CJK query token？ |
|------|--------|---------------------------|
| `parentPath` | `"equipment_types[0]"` | 否（纯 Latin + 数字） |
| `pathSegments` | `["equipment_types", "0"]` | 否（纯 Latin + 数字） |

**结论**：当前 metadataJson 中**没有任何字段携带实体展示值**（如"常规设备"、"大型设备"、"精密仪器"）。因此 `entityContextMatchesQuery` 在移除污染后，对 FQ4/FG1 的所有附加候选将返回 false。

---

## 4. 当前状态：BLOCKED

### 解除阻塞所需的数据模型变更

需要 Materializer（或 SQL 映射层）在 metadataJson 中新增实体上下文字段，例如：

```
metadataJson 中增加:
  "contextPath": "equipment_types / 常规设备"  (entity display context)
  "contextDisplayValues": ["常规设备"]          (或类似字段)
```

或：SQL 的 `searchLexical` 中为 terminal unit 查询增加一个独立的 entity context 列（不混入 field_level 的 content）。

### 为什么不用 content 硬凑

`hit.getContent()` 包含 `field_aliases_json`。一旦它出现在 entity context haystack 中：
- 单目标问"精密仪器逾期罚金"时，常规设备的 `late_fee_per_day` 会因 fieldAliases "每日逾期费"（CJK bigram "逾期"）通过 entity context 检查
- 这意味着 entityContextMatchesQuery 无法区分"当前 query 提到了精密仪器"和"当前 query 没提到常规设备"
- entity context guard 的设计目的被完全破坏

---

## 5. 代码修改（已完成）

### 修改前

```java
private static String buildEntityContextHaystack(QueryArticleHit hit) {
    StringBuilder sb = new StringBuilder();
    String content = hit.getContent();          // ← 污染！
    if (content != null) {
        sb.append(content.toLowerCase());       // ← 含 field_description + field_aliases_json
    }
    ...
    sb.append(' ').append(node.path("displayText").asText(""));  // ← 字段值，非实体上下文
    ...
}
```

### 修改后

```java
private static String buildEntityContextHaystack(QueryArticleHit hit) {
    StringBuilder sb = new StringBuilder();
    String metadataJson = hit.getMetadataJson();
    if (metadataJson == null || metadataJson.isBlank()) {
        return "";
    }
    try {
        JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
        // 只提取 entity-level 结构信号，排除所有字段语义字段
        sb.append(node.path("parentPath").asText(""));
        JsonNode segments = node.path("pathSegments");
        if (segments.isArray()) {
            for (JsonNode segment : segments) {
                if (!segment.isNull()) {
                    sb.append(' ').append(segment.asText(""));
                }
            }
        }
        // 扫描 metadataJson 顶层 key，纳入任何名称含 "context" 的字段值
        node.fieldNames().forEachRemaining(fieldName -> {
            if (fieldName.contains("context")) {
                JsonNode contextValue = node.path(fieldName);
                if (contextValue.isArray()) {
                    for (JsonNode item : contextValue) {
                        if (!item.isNull() && item.isTextual()) {
                            sb.append(' ').append(item.asText(""));
                        }
                    }
                } else if (contextValue.isTextual()) {
                    sb.append(' ').append(contextValue.asText(""));
                }
            }
        });
        return sb.toString().toLowerCase();
    } catch (Exception ignored) {
        return "";
    }
}
```

### 关键变化

1. **移除 `hit.getContent()`** — 不再引入 field_description + field_aliases_json + display_text
2. **移除 `displayText`** — 字段值非实体上下文
3. **保留 `parentPath`、`pathSegments`** — 纯结构信号
4. **新增向前兼容的 `context` 字段扫描** — 当未来 Materializer 在 metadataJson 中添加 `contextPath`、`contextDisplayValues` 等字段时，自动纳入

---

## 6. 代码层面影响

| 场景 | 影响 |
|------|------|
| 多目标聚合主逻辑 | 不变（Phase 2 的条件检查、排序、去重、上限均不变） |
| bestCandidate 选择 | 不变（Phase 1 完全不动） |
| qf/isTerminalHitQueryFocused | 不变（仍使用 content + metadataJson 构建 haystack） |
| entityContextMatchesQuery | **更严格**（移除污染后，除非未来新增 context 字段，否则只匹配结构路径） |
| 单目标保护 | **增强**（不再因 fieldAliases 污染而误纳入无关实体） |

---

## 7. redline 与 mvn test 结果

| 门禁 | 结果 |
|---|---|
| redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 8. 下一步建议

### BLOCKED — 交给架构师/agentB 决策

当前 entityContextMatchesQuery 在移除污染后，依赖纯结构信号（parentPath、pathSegments）。这些字段不携带实体展示值，因此 FQ4/FG1 的多目标聚合在当前数据模型下无法通过 entity context 检查。

解除阻塞有两种路径：

**路径 A**（推荐）：Materializer/SQL 层增加 entity context

在 metadataJson 中新增 `contextPath` 或 `contextDisplayValues` 字段，携带实体的展示值。例如：
```
contextPath: "equipment_types/常规设备"
```
或：
```
contextDisplayValues: ["常规设备"]
```

由于 `buildEntityContextHaystack` 已支持向后兼容的 `context` 字段扫描，只需数据层变更即可生效，无需再改 Java 代码。

**路径 B**：SQL 层新增独立 entity context 列

在 `FactCardTerminalUnitMapper.xml` 的 `searchLexical` 中新增一个独立列，专门用于 entity context 匹配。QueryArticleHit 侧需要对应新增字段。该路径改动面较大。

### 交给 agentD 的当前验证

尽管多目标聚合因 entity context guard 过严而暂时阻塞，但单目标保护得到了增强。agentD 可验证：

1. 单目标问题（FQ3）仍只返回一条结论 ✓
2. 单目标问题（"精密仪器逾期罚金"）不会误带出其他实体的同名字段 ✓
3. mvn test 全量通过、redline BLOCKER=0 ✓
4. TU_TRACE 日志确认 entity context guard 有在运行（additionalCandidates=0 for single-target queries）

---

## 9. 明确声明

- [x] 只修改了 `AnswerFallbackConclusionBuilder.java` 一个文件
- [x] 只修改了 `buildEntityContextHaystack` 一个方法
- [x] 移除了 `hit.getContent()` 污染源
- [x] 移除了 `displayText` 污染源
- [x] 保留了安全的 `parentPath`、`pathSegments`
- [x] 新增了向前兼容的 `context` 字段扫描
- [x] 排除了 fieldAliases、fieldDescription、terminalKey、value、valueType、channel、unitId 等字段语义和标识符字段
- [x] 未修改 qf/ftmc/atmc/fusedScore 计算语义
- [x] 未修改多目标聚合主逻辑
- [x] 未使用 content/fieldDescription/fieldAliases 硬凑 entity context
- [x] 未写入任何样例字符串
- [x] 未提交 commit
- [x] redline `BLOCKER=0`
- [x] mvn test `995/0/0/0, BUILD SUCCESS`
- [x] BLOCKED：当前数据模型无安全 entity display context，entityContextMatchesQuery 对多实体场景返回 false
