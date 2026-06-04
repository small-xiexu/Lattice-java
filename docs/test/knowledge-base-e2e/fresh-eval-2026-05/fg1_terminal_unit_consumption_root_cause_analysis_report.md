# FG1 Terminal Unit 未消费根因分析报告

分析时间：2026-06-01
分析人：agentB（只读归因）
数据库：ai-rag-knowledge.lattice（123 个 terminal unit，直接可用）

---

## 1. 总结论：断在 "中文语义别名缺失"

**断点**：`fact_card_terminal_units` 表中 `late_fee_per_day` 终端单元的 `field_aliases_json` 和 `fts_text` 中**完全没有**"逾期"或"罚金"的中文语义别名。这导致：

1. FTS 检索阶段：中文 query token "逾期"/"罚金"无法匹配终端单元的 fts_text，终端单元 FTS 得分低于卡级 FTS
2. Conclusion builder 阶段：即使终端单元进入 fallbackHits（靠"精密仪器"/"常规设备"匹配 context），`isTerminalHitQueryFocused` 只能靠匹配 context 中的中文类型名通过，而非字段语义匹配
3. 两目标场景：`buildTerminalUnitExactConclusionLines` 只选**一个**最佳 candidate，无法同时回答"精密仪器"和"常规设备"两个子问题

**主因不是 conclusion builder 逻辑错误，也不是 FTS 未召回**。是**终端单元的中文语义映射表缺失**——英文字段名 `late_fee_per_day` 只能靠 camelCase 拆词生成英文别名（"late", "fee", "per", "day"），而"逾期"/"罚金"作为中文语义等价词，在编译/索引阶段从未被注入。

---

## 2. 数据库现场：Terminal Unit 确认存在

### 2.1 FG1 两个目标终端单元

| 字段 | equipment_types[1]（精密仪器） | equipment_types[0]（常规设备） |
|---|---|---|
| unit_id | `...:16:503d7b188bc9a281d46572c3` | `...:9:71f2b0506ea205995cef701c` |
| key_path | `equipment_types[1].late_fee_per_day` | `equipment_types[0].late_fee_per_day` |
| value_text | **20** | **5** |
| display_text | `equipment_types[1].late_fee_per_day = 20` | `equipment_types[0].late_fee_per_day = 5` |
| field_description | `context: 精密仪器, 实验室主任` | `context: 常规设备, 设备管理员` |
| field_aliases（18项） | 全部英文：`late_fee_per_day`, `late fee per day`, `equipment`, `types`, `[1]`, ... | 全部英文：`late_fee_per_day`, `late fee per day`, `equipment`, `types`, `[0]`, ... |

### 2.2 "逾期"/"罚金"匹配验证

```
全表查询：SELECT * WHERE field_description LIKE '%逾期%' OR field_description LIKE '%罚金%'
结果：0 行
```

**整个 `fact_card_terminal_units` 表（123 行）中没有任何 unit 包含"逾期"或"罚金"的中文别名**。

### 2.3 return_policy 终端单元对比

| 字段 | return_policy.damage_report_required |
|---|---|
| field_aliases | `["damage_report_required", "damage report required", ...]` — 纯英文 |
| field_description | `parentPath: return_policy; field: damage_report_required; valueType: boolean` — 无中文 context |

`return_policy` 相关终端单元同样完全没有中文别名。当前答案落到 `return_policy`，是因为**卡级 FTS** 的 `items_json` 包含完整中文 YAML 文本（含"逾期""罚金"），而非终端单元级 FTS 匹配到的。

---

## 3. 检索现场：源码级仿真

### 3.1 终端单元 FTS 匹配分析（FG1 query: "精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？"）

| Query Token | late_fee_per_day(精密仪器) | late_fee_per_day(常规设备) | return_policy | 卡级 FACT_CARD |
|---|---|---|---|---|
| **精密仪器** | ✅（context: 精密仪器） | ❌ | ❌ | ✅（items_json） |
| **逾期** | ❌（无中文别名） | ❌ | ❌ | ✅（items_json） |
| **罚金** | ❌（无中文别名） | ❌ | ❌ | ✅（items_json） |
| **常规设备** | ❌ | ✅（context: 常规设备） | ❌ | ✅（items_json） |
| **late_fee** | ✅（别名） | ✅（别名） | ❌ | ✅ |

**结论**：终端单元只在 1-2 个 token 上命中（靠 context 中的中文类型名），卡级 FACT_CARD 在所有 token 上全命中。卡级 FTS 分数 >> 终端单元 FTS 分数。

### 3.2 结论构建器路径

在 `buildGeneralFallbackConclusionLines`（`AnswerFallbackConclusionBuilder.java:207`）中：
1. `buildTerminalUnitExactConclusionLines` 在优先级 8（行 248），位于 `buildExactStructuredListConclusionLines` 之后
2. 但实际执行的可能是更早返回的 path：`exactPathLines` / `exactStructuredListLines` / 或 fallback 到 `primarySnippets`（行 278-310）
3. 如果终端单元 FTS 得分低，它们可能在 `fallbackHits` 中的排序靠后，被前面的 return_policy 或卡级摘要命中

### 3.3 `isTerminalHitQueryFocused` 行为

```java
// AnswerFallbackConclusionBuilder.java:377-391
String haystack = buildTerminalHitEvidenceHaystack(hit);
// haystack = content(lowercase) + " " + metadataJson(lowercase)
// content = displayText: "equipment_types[1].late_fee_per_day = 20"
// metadataJson = {..., "fieldDescription":"parentPath: equipment_types[1]; field: late_fee_per_day; valueType: number; context: 精密仪器, 实验室主任", ...}

for (String token : queryTokens) {
    if (token.length() >= 2 && haystack.contains(token.toLowerCase())) {
        return true;  // 精密仪器 = ✅, 逾期 = ❌, 罚金 = ❌
    }
}
```

"精密仪器" token 匹配成功→ `isTerminalHitQueryFocused` 返回 `true`。但"逾期""罚金"两个 token 都**不匹配**。如果 query token 抽取逻辑中这两个 token 存在，终端单元只能靠"精密仪器"这一个 token 勉强通过。

---

## 4. 根因确认：缺中文字段语义别名

### 4.1 Materializer 已做的

`FactCardTerminalUnitMaterializer.buildFieldAliases()` 生成以下别名：

| 来源 | 示例（late_fee_per_day） |
|---|---|
| fieldLabel（terminalKey） | `late_fee_per_day` |
| keyPath | `equipment_types[1].late_fee_per_day` |
| parentPath | `equipment_types[1]` |
| parentTailWithLabel | `equipment_types[1] late_fee_per_day` |
| pathSegments | `[1]`, `equipment_types` |
| camelCase 拆词 | `late`, `fee`, `per`, `day`, `equipment`, `types` |
| CJK N-gram（**仅对含中文文本**） | **无**（late_fee_per_day 无中文字符） |
| sibling context（parentPathDescriptors） | `精密仪器`, `实验室主任` → 写入 fieldDescription |

### 4.2 Materializer 没做的

**CJK N-gram 别名只在字段名/路径本身含中文字符时才生成**。对于纯英文字段名 `late_fee_per_day`：
- `addChineseNgramAliases(aliases, fieldLabel)` → `"late_fee_per_day"` 不含 CJK → 跳过
- `addChineseNgramAliases(aliases, keyPath)` → 同上

**sibling context（"精密仪器""实验室主任"）只写入 `fieldDescription`，不写入 `fieldAliases`**。`fieldDescription` 虽然进入了 `fts_text`，但位于描述性位置，不如别名在 FTS 中的匹配权重。

### 4.3 LLM Enricher 的状态

`LlmFactCardTerminalUnitFieldAliasEnricher`（90ad165）已提交接口+实现+prompt，但它依赖编译阶段 LLM 调用为英文字段名生成中文别名。当前：
- Enricher 接口已定义
- Prompt 文件 `field-alias-enricher.md` 已外置
- 但是否在 clean eval 环境中实际启动了 LLM 调用来生成中文别名？从数据库现场（全部 123 个 unit 的别名均为纯英文）推断：**未执行 LLM enricher 生成中文别名**。

### 4.4 为什么 FQ3/FQ4/FQ6/FG2 通过了但 FG1 没通过

| Case | Query | 中文 alias 命中 | 通过？ |
|---|---|---|---|
| FQ3 | 精密仪器单次最长借用天数 | "精密仪器"匹配 context，数字值 `7` 唯一 | ✅ |
| FQ4 | 常规设备和大型设备押金 | "常规设备"匹配 context，"大型设备"匹配 context | ✅ |
| FQ6 | 预约系统当前版本号 | "版本号"匹配 YAML 中的"版本号"字段 | ✅ |
| FG2 | 预约系统最大并发请求数 | "最大并发"匹配 card 级文本 | ✅ |
| **FG1** | **精密仪器**逾期罚金 + **常规设备**逾期罚金 | "逾期"/"罚金"无中文 alias → FTS 弱匹配；两个 target → 只选一个 | **PARTIAL** |

FG1 的特殊性：两个子问题 + "逾期"/"罚金"都缺中文别名。其他 case 要么只有一个 target，要么查询 token 有其他匹配路径。

---

## 5. Risk 判断

### 5.1 为什么不是 B0-B20 治理回归

B0-B20 只修改了 DTO/getter/Lombok/Javadoc，未修改：
- `FactCardTerminalUnitMaterializer` 的 alias 生成逻辑
- `FactCardTerminalUnitFtsSearchService` 的 FTS 检索
- `AnswerFallbackConclusionBuilder` 的 conclusion 选择
- 任何 FTS tsvector、schema、索引结构

### 5.2 为什么不是 FG1 case 特判

缺失的是通用能力：**英文字段名的中文语义别名生成**。这不仅影响 FG1 的 `late_fee_per_day` → "逾期罚金"，也影响所有其他英文字段名 + 中文查询的场景。修复应该是通用的中文别名注入机制。

### 5.3 是否影响其他 case

- **不影响 FQ3/FQ4/FQ6/FG2**：这些 case 靠 context 匹配或卡级文本匹配已通过
- **不影响 Public Eval 1**：Q6 的修复基于 exact path terminal field alias，是独立链路
- **可能帮助未来的中英文语义匹配场景**

---

## 6. 给 agentA 的下一轮最小修复提示词草案

```
交给 agentA。

本轮任务：为终端单元物化器补充中文字段语义别名生成能力，使英文字段名的终端单元能被中文查询匹配。

唯一进度台账：docs/quality-progress-and-lessons.md
根因报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/fg1_terminal_unit_consumption_root_cause_analysis_report.md

## 根因

FG1 query "精密仪器的逾期罚金"中：
- "精密仪器"能匹配 terminal unit 的 context field_description ✅
- "逾期"无法匹配 — late_fee_per_day 的 field_aliases 只有英文拆词 ❌
- "罚金"无法匹配 — 同上 ❌

全表 123 个 terminal unit 无一包含"逾期"或"罚金"中文别名。

## 允许修改文件

- `FactCardTerminalUnitMaterializer.java`（`buildFieldAliases` 方法或新增小方法）

## 修复方向

在 `buildFieldAliases` 中，为英文字段名补充**中文语义别名**。必须遵守以下约束：

1. **只允许通用语言规则，不允许业务词硬编码**：
   - 禁止添加 `"late_fee_per_day" → "逾期罚金"` 之类的固定映射
   - 禁止添加任何具体业务词（如"精密仪器""常规设备""押金""逾期""罚金"等）
   - 只允许通用规则：读取已有 sibling context（"精密仪器"），从其 CJK N-gram 中派生别名 → 但这是 context，不是字段语义

2. **推荐方案：利用已存在的 LLM Enricher**：
   - `LlmFactCardTerminalUnitFieldAliasEnricher` 已提交（90ad165），接口+实现+prompt 齐全
   - 确保 Materializer 在 `materialize()` 完成后调用 enricher 为 terminal unit 补充中文别名
   - 或确认 enricher 的调用链是否在 compile 流程中已连通但未生效
   - prompt 文件 `field-alias-enricher.md` 已在 `src/main/resources/prompts/compiler/`

3. **备选方案（无 LLM 依赖）**：
   - 将 sibling context descriptors（如"精密仪器""实验室主任"）中提取的 CJK N-gram，注入到对应 parentPath 下所有 terminal unit 的 field_aliases 中
   - 但注意：context 描述的是兄弟 OBJECT，不是字段语义。需明确这是 context 注入而非字段语义翻译

## 禁止事项

- 禁止在 Java 主链硬编码中文字段语义映射
- 禁止新增 case 特判、contains 分支、selector gate
- 禁止修改 AnswerFallbackConclusionBuilder
- 禁止修改 FTS schema 或 SQL
- 禁止修改 prompt 中的 grounding rules
- 禁止修改 fact card 生成链路
- 禁止为"逾期""罚金""精密仪器""常规设备"写固定业务词

## 验证

- mvn compile -pl . -q
- 需要清库重建：修改 Materializer 后需重新编译 YAML → 重新生成 terminal units
- agentD 跑 FG1 + 完整 Public Eval 2 + Public Eval 1 保护回归

## 输出：fg1_terminal_unit_alias_fix_result_report.md
```

---

## 7. 数据库现场附录

- 数据库：`ai-rag-knowledge.lattice`
- `fact_card_terminal_units`：123 行
- `equipment-borrowing-policy.yaml` → 3 个 `equipment_types[*].late_fee_per_day` 单元均已生成
- `return_policy` 下 2 个单元也无中文别名
- 全表零中文字段语义别名
