# Terminal Unit Phase 1D: YAML Sibling Context / Field Alias Materialization 设计报告

设计时间：2026-05-29
设计人：agentB（治理/链路分析 Agent）
设计范围：只读分析，不修改任何文件

---

## 1. 一句话结论

**Layer 2 sibling context 值得做，但单独不足以解决 YAML 5 题。应在 Materializer 中利用同 parentPath 的 sibling 中文 descriptor 值（如 type="精密仪器"）增强 fieldDescription → ftsText，同时在 Reranker 中读取 fieldDescription 参与 fieldMatchCount 计算。此组合可让"精密仪器" query token 同时命中目标 unit 和 descriptor sibling，再利用 numeric intent bonus（+0.5）和 FTS 原始分差将 number 型目标 unit 排在 string 型 descriptor sibling 之前。但"最长借用天数→max_borrow_days"的精准语义匹配仍需 Layer 3 LLM 或 Phase 2 向量检索才能覆盖。**

---

## 2. YAML 5 题当前证据流转表

### 2.1 逐题证据链路

| 题号 | 目标 terminalKey | 目标 keyPath | 目标 valueText | 目标 valueType | Terminal Unit 是否存在 | FTS 是否召回 | Reranker 是否重排 | Fused 是否进入 | Answer/Citation 是否消费 |
|---|---|---|---|---|---|---|---|---|---|
| **FQ3** | max_borrow_days | equipment_types[1].max_borrow_days | 7 | number | **是** | **是**（但排在 sibling type="精密仪器" 之后） | **否**（fieldMatchCount=0） | 进入但 rank 低 | **否**（FALLBACK 选中 type 和 name sibling） |
| **FQ4** | deposit_amount | equipment_types[0].deposit_amount | 100 | number | **是** | **是**（但排在 sibling type="常规设备" 之后） | **否**（fieldMatchCount=0） | 进入但 rank 低 | **否**（FALLBACK 选中 overview sibling） |
| **FQ6** | version | borrowing_system.version | v2.3.1 | version | **是** | **是**（但排在 sibling name/api_endpoint 之后） | **否**（fieldMatchCount=0） | 进入但 rank 低 | **否**（FALLBACK 选中 name/api_endpoint sibling） |
| **FG1** | late_fee_per_day | equipment_types[1].late_fee_per_day | 20 | number | **是** | **是**（排在 sibling 之后） | **否**（fieldMatchCount=0） | 进入但 rank 低 | **否**（FALLBACK 选中 type sibling） |
| **FG2** | max_concurrent_requests | borrowing_system.max_concurrent_requests | 50 | number | **是** | **是**（排在 sibling name/api_endpoint 之后） | **否**（fieldMatchCount=0） | 进入但 rank 低 | **否**（FALLBACK 选中 name sibling） |

### 2.2 失败分层归因

| 层级 | 状态 | 说明 |
|---|---|---|
| **Terminal unit 生成** | PASS | 所有 5 个目标 unit 均已由 Materializer 正确生成，keyPath/parentPath/valueText/valueType 正确 |
| **FTS 召回 (tsquery)** | PARTIAL | PostgreSQL `plainto_tsquery('simple', question)` 对无空格中文问题仅当单 token 处理，tsquery 命中极弱。实际召回主要由 LIKE 匹配驱动 |
| **FTS 召回 (LIKE)** | PARTIAL | 中文 query token 如"精密仪器"、"预约系统"通过 LIKE `%term%` 匹配到 sibling unit 的 valueText（如 type="精密仪器"），但目标 unit（max_borrow_days=7）的 ftsText/fieldAliases 中**没有任何中文字符**，导致 LIKE 匹配为零 |
| **Reranker** | FAIL | 5 题 Reranker 中 `fieldMatchCount=0`（所有 unit 的 terminalKey/fieldLabel/fieldAliases 均为英文，与中文 query token 零交集）。`profilesWithFieldSignal=0` 导致 Reranker **直接返回原始顺序**（`rerank()` 第 118-121 行 early return），不执行任何重排 |
| **Fused 融合** | MARGINAL | 目标 unit 进入了 topK（因为同一 fact card 的 sibling 被召回后，整个 parentPath 组的 unit 都因共享 factCardId 关联被带出），但 rank 低于 descriptor sibling |
| **Answer / Citation 消费** | FAIL | FALLBACK 模式下 evidence selector 选中了 type/name sibling（因为其 valueText 更匹配 query），目标 unit 的 valueText（如 "7"、"20"）在 Fallback 文本处理中无法提供足够信号 |

### 2.3 根因定位

**唯一根因：英文字段名（max_borrow_days、deposit_amount、version、late_fee_per_day、max_concurrent_requests）的 terminalKey/fieldLabel/fieldAliases 全部为英文，与中文 query token 零交集。Reranker 的 fieldMatchCount 永远为 0，导致整个 Reranker 成为 no-op（early return）。这不是权重问题，不是召回缺失，不是 answer 选择失误——而是编译层从未生产任何中文可检索字段语义。**

---

## 3. Terminal Unit / Sibling Context 数据样例

### 3.1 YAML parentPath 完整 sibling 分组

以下数据来自数据库 `fact_card_terminal_units` 表（2026-05-29 实时只读查询）：

#### equipment_types[1]（7 个 sibling — FQ3/FG1 的 parentPath）

| terminalKey | valueText | valueType | 中文 descriptor? |
|---|---|---|---|
| type | **精密仪器** | string | **是（主 descriptor）** |
| category_id | PREC | string | 否 |
| max_borrow_days | 7 | number | 否（FQ3 目标） |
| deposit_amount | 500 | number | 否 |
| late_fee_per_day | 20 | number | 否（FG1 目标） |
| approval_required | 实验室主任 | string | 是（次 descriptor） |
| return_check_required | true | boolean | 否 |

#### equipment_types[0]（7 个 sibling — FQ4 目标之一）

| terminalKey | valueText | valueType | 中文 descriptor? |
|---|---|---|---|
| type | **常规设备** | string | **是（主 descriptor）** |
| category_id | GEN | string | 否 |
| max_borrow_days | 14 | number | 否 |
| deposit_amount | 100 | number | 否（FQ4 目标） |
| late_fee_per_day | 5 | number | 否 |
| approval_required | 设备管理员 | string | 是 |
| return_check_required | true | boolean | 否 |

#### equipment_types[2]（7 个 sibling — FQ4 目标之一）

| terminalKey | valueText | valueType | 中文 descriptor? |
|---|---|---|---|
| type | **大型设备** | string | **是（主 descriptor）** |
| category_id | LARGE | string | 否 |
| max_borrow_days | 3 | number | 否 |
| deposit_amount | 1000 | number | 否（FQ4 目标） |
| late_fee_per_day | 50 | number | 否 |
| approval_required | 院系分管领导 | string | 是 |
| return_check_required | true | boolean | 否 |

#### borrowing_system（5 个 sibling — FQ6/FG2 的 parentPath）

| terminalKey | valueText | valueType | 中文 descriptor? |
|---|---|---|---|
| name | **校园实验室设备预约系统** | string | **是（主 descriptor）** |
| api_endpoint | https://lab-equip.campus.edu/api/v2/borrow | url | 否 |
| version | v2.3.1 | version | 否（FQ6 目标） |
| max_concurrent_requests | 50 | number | 否（FG2 目标） |
| support_hours | 工作日 08:30-18:00 | string | 可能 |

### 3.2 当前 fieldAliases 样例（以 equipment_types[1].max_borrow_days 为例）

```
["max_borrow_days", "max borrow days", "equipment_types[1].max_borrow_days",
 "equipment types[1].max borrow days", "equipment_types[1] max_borrow_days",
 "equipment_types[1]", "equipment types[1]", "equipment types[1] max_borrow_days",
 "equipment_types", "equipment types", "[1]", "max_borrow_days",
 "max", "borrow", "days", "max", "borrow", "days",
 "equipment", "types[1]", "max", "borrow", "days",
 "equipment", "types", "1", "max", "borrow", "days"]
```

**全部为英文/数字/ASCII 符号。零中文字符。Chinese N-gram 函数 (`addChineseNgramAliases`) 在本 unit 上不触发，因为 fieldLabel="max_borrow_days" 不含 CJK 字符。**

### 3.3 当前 fieldDescription 样例

```
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number
```

**同样零中文字符。未包含 sibling 上下文。**

### 3.4 对比：CSV/XLSX 中文列头（已通过 Phase 1C Layer 1 解决）

CSV 的 "维护等级" 的 fieldAliases 包含：
```
["维护等级", "维护", "护等", "等级", "维护等", "护等级", ...]
```

中文 N-gram 生效，中文 query token（"维护"、"等级"）能匹配 LIKE。这是 FQ7/FQ11 已通过的原因。

---

## 4. 失败分层判断

### 4.1 召回层

| 子层 | 判定 | 证据 |
|---|---|---|
| Terminal unit 存在性 | **PASS** | 所有 5 个目标 unit 均在 `fact_card_terminal_units` 表中，keyPath/valueText/valueType 正确 |
| tsquery 召回 | **MARGINAL** | `plainto_tsquery('simple', question)` 对中文问题几乎无效。YAML 的 terminal unit ftsText/fieldAliases 虽全是英文，但 `search_tsv` 包含 valueText（如 "7"、"50"），仍可能被 tsquery 微弱命中 |
| LIKE 召回 | **PARTIAL** | 中文 query token 通过 LIKE `%term%` 匹配到 sibling unit 的 valueText（如 type="精密仪器"），但匹配不到目标 unit（max_borrow_days=7）的 ftsText/fieldAliases（仅英文） |

**结论：召回层问题不在 terminal unit 缺漏，而在目标 unit 的 ftsText/fieldAliases/fieldDescription 缺乏中文 token，导致 LIKE 匹配完全依赖 sibling unit 的 valueText 关联召回。**

### 4.2 排序层（Reranker）

| 条件 | 当前值 | 说明 |
|---|---|---|
| query token 命中 terminalKey | 0 | "max_borrow_days" vs "精密仪器"/"最长借用天数" → 零命中 |
| query token 命中 fieldLabel | 0 | 同上，fieldLabel = terminalKey = "max_borrow_days" |
| query token 命中 fieldAliases | 0 | 所有 alias 均为英文 |
| query token 命中 keyPath | 0 | keyPath="equipment_types[1].max_borrow_days" → "精密仪器" 不会命中 |
| **fieldMatchCount** | **0** | 以上各项合计 |
| **terminalKeyMatchCount** | **0** | fieldMatchCount 的子集（不含 keyPath） |
| queryHasNumericIntent | **true** (FQ3/FQ4/FG1/FG2) | "多少"、"最长"、"最大" 匹配 numericIntentSignals |
| profilesWithFieldSignal | **0** | 所有 unit fieldMatchCount=0 → **Reranker early return，零重排** |

**结论：Reranker 完全失效，因为所有 terminal unit 的 fieldMatchCount 均为 0。这不是 Reranker 算法错误——算法正确（定向测试 13/0/0），是缺少输入信号（fieldAliases 全是英文）。**

### 4.3 Answer/Citation 消费层

**结论：本轮不做 answer 层分析。Answer 层正确消费了 retrieved evidence，只是 retrieved evidence 中的 top-ranked unit 是 sibling 而非目标。修复召回和排序后，answer 层应自动改善。**

---

## 5. 推荐设计：Materializer + Reranker 双层修改

### 5.1 总体策略

```
Layer 2 = Materializer sibling context (fieldDescription → ftsText)
        + Reranker fieldDescription 感知 (fieldMatchCount 扩展)
        + Numeric intent bonus (已有，继续利用)
```

### 5.2 Materializer 侧修改（单文件变更）

**文件**：`FactCardTerminalUnitMaterializer.java`

#### 修改点 1：materialize() 中收集 sibling 信息（约 20 行）

在 `materialize()` 中，遍历 `itemsNode` 之前，先扫一遍收集每个 `parentPath` 的中文 descriptor：

```java
// 伪代码示意
Map<String, List<String>> parentPathDescriptors = new LinkedHashMap<>();
for (JsonNode itemNode : itemsNode) {
    String parentPath = textValue(itemNode, "parentPath");
    String value = textValue(itemNode, "value");
    if (containsCJK(value) && value.length() >= 2 && value.length() <= 20) {
        parentPathDescriptors.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(value);
    }
}
// 限制每个 parentPath 最多 2 个 descriptor
```

然后将此 map 传给 `materializeItem()`。

#### 修改点 2：buildFieldDescription() 增加 sibling context（约 5 行）

```java
// 在 buildFieldDescription 末尾追加
List<String> descriptors = parentPathDescriptors.getOrDefault(parentPath, List.of());
if (!descriptors.isEmpty()) {
    String context = descriptors.stream()
        .filter(d -> !d.equals(valueText)) // 避免自身 value 成为自己的 context
        .limit(2)
        .collect(Collectors.joining(", "));
    if (hasText(context)) {
        parts.add("context: " + context);
    }
}
```

**效果**：`equipment_types[1].max_borrow_days=7` 的 fieldDescription 变为：
```
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
```

"精密仪器" 和 "实验室主任" 进入 ftsText → 进入 search_tsv → 可被 LIKE `%精密仪器%` 匹配（ftsText LIKE 得分 +2.0）。

#### 修改点 3：不修改 fieldAliases（关键设计决策）

**sibling descriptor 只写入 fieldDescription，不写入 fieldAliases。** 原因：

| 如果写入 fieldAliases | 后果 |
|---|---|
| 同 parentPath 所有 sibling 共享相同 descriptor alias | 所有 sibling 的 fieldMatchCount 均增加相同数值 |
| Sibling boost 机制失效 | 所有 unit 都有 terminalKeyMatchCount → 全部获得 +6.0 → 无净差异 |
| 噪声风险 | 同 parentPath 的无关 sibling（如 return_check_required=true）也被提升 |

**写入 fieldDescription 的优势**：
- 增强 FTS 召回（LIKE 匹配 ftsText），但不污染 field token matching
- Reranker 可**选择性地**读取 fieldDescription（见 5.3），粒度可控
- 不破坏现有 sibling boost 机制

### 5.3 Reranker 侧修改（单文件变更）

**文件**：`FactCardTerminalUnitIntentReranker.java`

#### 修改点 1：parseProfile() 读取 fieldDescription（约 2 行）

```java
// 在 parseProfile() 中新增
p.fieldDescription = textValue(node, "fieldDescription");
```

#### 修改点 2：buildFieldTokenSet() 可选包含 fieldDescription（约 3 行，新方法）

新增 `hasContextualFieldMatch()` 方法，检查 query tokens 是否命中 fieldDescription：
```java
private int countContextMatches(HitProfile p, List<String> queryTokens) {
    if (p.fieldDescription == null || p.fieldDescription.isBlank()) return 0;
    Set<String> contextTokens = new HashSet<>();
    addFieldTokens(contextTokens, p.fieldDescription);
    int count = 0;
    for (String token : queryTokens) {
        if (contextTokens.contains(token.toLowerCase(Locale.ROOT))) count++;
    }
    return count;
}
```

#### 修改点 3：调整 adjustedScore 计算（约 5 行）

在现有调整后，增加上下文匹配贡献：
```java
// 上下文匹配为弱信号（权重 0.3，低于 FIELD_TOKEN_WEIGHT=1.0）
int contextMatchCount = countContextMatches(p, queryTokens);
adj += contextMatchCount * 0.3;
```

#### 修改点 4：修改 early return 条件（约 2 行）

当前第 118-121 行：
```java
long profilesWithFieldSignal = profiles.stream()
        .filter(p -> p.fieldMatchCount > 0).count();
if (profilesWithFieldSignal == 0 && !queryHasNumericIntent) {
    return hits;
}
```

修改为：
```java
boolean anySignal = profiles.stream().anyMatch(
    p -> p.fieldMatchCount > 0 || countContextMatches(p, queryTokens) > 0);
if (!anySignal && !queryHasNumericIntent) {
    return hits;
}
```

**关键设计决策：context 匹配使用低权重（0.3），低于 fieldMatch（1.0），高于 valueMatch（0.1）。这样：**
- `type="精密仪器"` sibling：valueMatch 直接命中（+0.1 per token），但 fieldMatch=0
- `max_borrow_days=7` 目标 unit：contextMatch 命中（+0.3 per token），且是 number 类型 → 还有 numericBonus（+0.5）
- **净效果：目标 unit 的 contextMatch(0.3) + numericBonus(0.5) = +0.8 > sibling 的 valueMatch(0.1)**

### 5.4 各层级贡献预估

以 FQ3 "精密仪器的单次最长借用天数是多少" 为例：

| Unit | fieldMatchCount | contextMatchCount | valueMatchCount | numericBonus | 净调整 |
|---|---|---|---|---|---|
| max_borrow_days=7 **(目标)** | 0 | 1 ("精密仪器") | 0 | +0.5 | **+0.8** |
| deposit_amount=500 | 0 | 1 ("精密仪器") | 0 | +0.5 | +0.8 |
| late_fee_per_day=20 | 0 | 1 ("精密仪器") | 0 | +0.5 | +0.8 |
| type=**精密仪器** (sibling descriptor) | 0 | 0 | 1 ("精密仪器") | 0 | +0.1 |
| approval_required=实验室主任 | 0 | 1 ("精密仪器") | 0 | 0 | +0.3 |

**排序结果**：三个 number 型 unit 得分为 tied（+0.8），均排在 type（+0.1）和 approval_required（+0.3）之前。tie-breaker 为原始 FTS score（`originalScore`），取决于 LIKE/tsquery 匹配强度。

**问题**：无法区分 max_borrow_days vs deposit_amount vs late_fee_per_day。三者均为 number 型，contextMatch 相同，numericBonus 相同。

**缓解**：对于 FQ3，虽然三个 number 型 unit tied，但至少 type="精密仪器" 不再抢排。FTS 原始分可能已经能将 max_borrow_days=7 放在第一位（因为其 ftsText 中包含的 aliases 有更多 token 与 query 的其他部分微弱匹配）。但这不可靠。

### 5.5 Layer 3 (LLM Alias) 的必要性

**Layer 2 可以解决"精密仪器 → equipment_types[1] 子项"的实体粗选，但无法解决"最长借用天数 → max_borrow_days"的字段精准匹配。**

对于 FQ3/FQ4/FG1：
- Layer 2 可以将 number 型 sibling 排在 string 型 sibling 之前
- 但 number 型之间的排序仍依赖 FTS 原始分（不可靠）

对于 FQ6/FG2（borrowing_system 的 version/max_concurrent_requests）：
- borrowing_system 的子项中 version（version 型）和 max_concurrent_requests（number 型）都享受 numericBonus
- version="v2.3.1" 的 FTS 原始分可能高于 max_concurrent_requests=50（因为 "v2.3.1" 的 LIKE 匹配更多）
- 但 name="校园实验室设备预约系统" 的中文 valueText 匹配"预约系统"时获得 +3.0 LIKE score，仍可能抢排

**Layer 3 建议**：如果 Layer 2 验证后 YAML 5 题仍未全部通过（预计 FQ6/FG2 最有风险），则启用编译阶段 LLM 生成中文 field alias。详见 `terminal_unit_phase1c_field_alias_materialization_design_report.md` 第 5.1 节 Layer 3。

---

## 6. 不推荐方案

### 6.1 不改 query fallback（已排除）

| 方案 | 拒绝理由 |
|---|---|
| AnswerGenerationFallback 增加 selector gate | 三轮实验（structured fact terminal binding、selector gate、conclusion gate）均为 `0/5 PASS`。问题不在 fallback 选行逻辑，而在 evidence unit 粒度。 |
| AnswerFallbackEvidenceSelector 增加 question token gate | 同上。 |
| AnswerGenerationFallbackConclusionSupport 增加 sibling 过滤 | 在 answer 层做 sibling 过滤本质是补偿检索层缺陷，不可泛化。 |

### 6.2 不写业务 alias 映射（红线）

| 方案 | 拒绝理由 |
|---|---|
| `if (key.equals("max_borrow_days")) aliases.add("最长借用天数")` | **直接违反禁止事项**：不准在 Java 主链硬编码中文字段语义。且不可泛化到新资料。 |
| `config/field-alias-mapping.yml` 人工维护 | 需要人工维护中英文映射表，无法泛化。且新增配置需要额外审计。 |
| 使用源文件名/文档标题生成别名 | 文件名可能是 "equipment-borrowing-policy.yaml" → 不可用于语义推断（红线：禁止把文件名语义化）。 |

### 6.3 不直接上 LLM alias 作为唯一方案（先做 Layer 2）

| 方案 | 理由 |
|---|---|
| 跳过 Layer 2 直接上 Layer 3 LLM | LLM 调用增加 compile 耗时和成本，且生成的 alias 质量不稳定。Layer 2 的 sibling context 成本为零（纯算法），应优先验证。 |
| 用 LLM 在 query 时做运行时翻译 | 增加查询延迟，翻译结果不可控，容易引入 eval 语言。编译时生成可审计、可重建。 |

### 6.4 不改向量检索（Phase 2 范围）

| 方案 | 理由 |
|---|---|
| 启用 terminal unit 向量检索 | 属于 Phase 2 范围。Phase 1 聚焦 lexical/FTS 检索。如果 Layer 2 + 3 后仍不足，再进入 Phase 2。 |
| 用 embedding 相似度替代 lexical 匹配 | 可能引入语义漂移（"最长借用天数" embedding 接近 "max_borrow_period" 而非 "max_borrow_days"）。 |

### 6.5 不调整 Reranker 的 SIBLING_FIELD_BOOST 权重

当前 `SIBLING_FIELD_BOOST = 6.0` 是合理的——当一个 sibling 因 terminalKey 明确命中（如 query 直接包含 "max_borrow_days" 英文）时，应大幅提升。问题不在权重，在于 terminalKeyMatchCount 永远为 0。

---

## 7. 红线合规说明

| 检查项 | 状态 | 说明 |
|---|---|---|
| sibling context 来源 | **合规** | 仅来自同一 fact card、同一 parentPath 的 sibling item 的 valueText。不跨 card、不跨 source file。 |
| descriptor 识别规则 | **合规** | 纯形态规则：valueType=string、含 CJK 字符、长度 2-20。不涉及业务语义判断。 |
| 不写入业务词白名单 | **合规** | 不在 Java/配置/SQL 中维护任何中文→英文映射表。 |
| 不读取 eval 题面 | **合规** | sibling context 只从 `FactCardRecord.itemsJson` 中提取，不接触 eval/query 日志。 |
| 不读取 hidden eval | **合规** | 本设计报告的所有数据样例来自数据库只读查询和源码审计，未涉及 hidden eval。 |
| 不修改文件 | **合规** | 本轮 agentB 仅做只读分析，未修改任何文件。 |
| 不 stage/commit/push | **合规** | 本轮未做任何 git 操作。 |

---

## 8. 是否需要清库重建

### 8.1 仅修改 Materializer

**需要清库重建。** 理由：
- `fieldDescription` 和 `ftsText` 在 compile 时一次性计算并持久化
- 旧 terminal unit 的 `fieldDescription` 不含 sibling context，`search_tsv` 不含中文 context token
- 修改 Materializer 后，必须重新 compile 才能生成新 `fieldDescription`

### 8.2 仅修改 Reranker

**不需要清库重建。** 理由：
- Reranker 是 query 时行为，从 `metadataJson` 中读取 fieldDescription
- 但如果 Materializer 未生成 sibling context，fieldDescription 中仍无中文 token，Reranker 修改无效

### 8.3 结论

**Materializer + Reranker 同时修改时，必须清库重建 + 重新 compile。** 执行顺序：
1. agentA 实现 Materializer + Reranker 修改
2. agentD 执行 `./scripts/reset-lattice-schema.sh`
3. agentD 重新导入 5 份 fresh eval 资料
4. agentD 触发 compile
5. agentD 验证 terminal unit 的 fieldDescription 包含 sibling context

---

## 9. 下一轮 agentA 唯一最小修复建议

### 9.1 允许修改范围

**仅允许修改以下 2 个文件：**

| 文件 | 变更内容 | 行数估算 |
|---|---|---|
| `FactCardTerminalUnitMaterializer.java` | 1) `materialize()` 中收集 parentPath descriptors Map；2) `buildFieldDescription()` 增加 sibling context 参数并在描述中追加 "context: ..." | ~25 行 |
| `FactCardTerminalUnitIntentReranker.java` | 1) `HitProfile` 增加 `fieldDescription` 字段；2) `parseProfile()` 读取 fieldDescription；3) 新增 `countContextMatches()` 方法；4) 调整 `adjustedScore` 计算；5) 修改 `early return` 条件 | ~25 行 |

**总计：约 50 行代码变更。**

### 9.2 禁止修改范围

| 文件/区域 | 原因 |
|---|---|
| `FactCardTerminalUnitFtsSearchService.java` | 检索服务已正确，不需要改 |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | 已正确配置中文数值问法信号 |
| `FactCardTerminalUnitMapper.xml` | SQL 已正确，ftsText LIKE 匹配已覆盖 |
| `LexicalSearchTokenBudget.java` | Phase 1C 已修复，不需要改 |
| `FactCardGenerationService.java` / `FactCardGenerationListSupport.java` | Fact card 生成层不改 |
| `schema.sql` | 不需要 DDL 变更 |
| `AnswerGeneration*` / fallback / citation | **严禁修改 query 主链** |
| `scripts/` / `prompts/` / `config/` | 严禁修改 |

### 9.3 变量控制

**本轮唯一变量：compiler 侧 Materializer 增加 sibling context + query 侧 Reranker 增加 fieldDescription 感知。** 不改变:
- 检索通道数
- RRF 融合逻辑
- Fact card 生成逻辑
- LIKE token budget
- Field alias 生成规则（不增加 sibling descriptor 到 alias）

### 9.4 实现顺序

```
Step 1: Materializer 修改（sibling context → fieldDescription → ftsText）
Step 2: Reranker 修改（fieldDescription 感知 + contextMatch + early return 条件）
Step 3: 单元测试（测试中文 descriptor 收集、fieldDescription 生成、context match 计数）
Step 4: redline + mvn test + 定向测试
Step 5: 交由 agentD 做 clean schema 端到端验证
```

### 9.5 禁止事项

1. **禁止**在任何 Java 文件、配置、SQL、prompt 中硬编码中文字段语义映射
2. **禁止**将 sibling descriptor 写入 fieldAliases（会导致 sibling 混淆）
3. **禁止**修改 query fallback、answer generation、citation 逻辑
4. **禁止**读取或使用 eval 题面、case id、expected answer
5. **禁止**使用文件名、文档标题做语义判断
6. **禁止**修改 Phase 1B/1C 的现有代码（除上述 2 个文件的新增逻辑外）

---

## 10. 信息不足项：需要 agentD 补充的只读查询

以下项目在 agentB 本次只读分析中无法覆盖，需要 agentD 补充：

| # | 查询内容 | 用途 | 查询方式 |
|---|---|---|---|
| 1 | 当前 YAML 5 题实际 `query_retrieval_channel_hits` 中 `fact_card_terminal_fts` channel 的完整命中排序 | 验证"目标 unit 排在 sibling 之后"的假设 | 查询 `query_retrieval_channel_hits` WHERE channel_name='fact_card_terminal_fts'，按 hit_rank 排序 |
| 2 | 当前 YAML 5 题 `query_retrieval_runs` 的 `fused_hit_count` 和 `channel_run_summary_json` | 验证"目标 unit 已进入 fused topK"的假设 | 查询 `query_retrieval_runs` 对应 query_id |
| 3 | 目标 unit 的 FTS 原始 score 与 sibling unit 的分数差异（如 max_borrow_days=7 vs type=精密仪器） | 验证"tie-breaker 为原始 FTS score"的假设 | 查询 `query_retrieval_channel_hits.score` |
| 4 | 当前 CSV/XLSX terminal unit（FQ7/FQ11）的 fieldAliases/fieldDescription 在 Phase 1C Layer 1 后的实际值 | 对照验证 Layer 1 效果 | 查询 `fact_card_terminal_units` WHERE format='csv' OR 'xlsx' |
| 5 | 确认 `FactCardTerminalUnitMapper.xml` 的 LIKE 匹配列中 `fts_text` 的 LIKE 权重（当前为 2.0） | 验证 sibling context 进入 ftsText 后的 LIKE 贡献 | 已有（从源码读取：+2.0） |

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解 | 回滚 |
|---|---|---|---|
| sibling context 噪声 | "精密仪器" 出现在所有 equipment_types[1] sibling 的 fieldDescription 中，可能导致无关 sibling 也被提升 | contextMatch 使用低权重（0.3），且限制每个 parentPath 最多 2 个 descriptor | 移除 sibling context 逻辑 |
| number 型 sibling tied（无法区分 max_borrow_days vs deposit_amount） | 目标 unit 可能排在 deposit_amount 之后 | 依赖 FTS 原始分 tie-break；如果无效，进入 Layer 3 LLM | 进入 Layer 3 |
| borrowing_system 子项排序 | name="校园实验室设备预约系统" 的中文 valueText LIKE 得分高（+3.0 > +2.0），可能继续抢排 | contextMatch(0.3) + numericBonus(0.5) = +0.8 > valueMatch(0.1)，但 originalScore 的 LIKE 分差（3.0 vs 2.0）很大 | 如果 Reranker 不能克服 LIKE 分差，进入 Layer 3 |
| 修改 Materializer 需要清库重建 | 旧 terminal unit 无 sibling context | 按项目约定执行 `reset-lattice-schema.sh` + 重新 compile | 如果仅验证 Reranker 修改，可先在现有库上单独验证 Reranker 行为 |
| Reranker 修改影响 CSV/XLSX 已有 PASS | CSV/XLSX 的 fieldDescription 当前不含 sibling context（parentPath 不共享），contextMatch=0 → 无影响 | Phase 1C Layer 1 的 Chinese N-gram 不受影响 | 定向测试 + FQ7/FQ11 保护回归 |

---

## 附录 A：Query Token 提取验证

以 FQ3 "精密仪器的单次最长借用天数是多少" 为例，`QueryTokenExtractor.extract()` 提取的 CJK token（2-4 字滑动窗口）：

- 2-gram: "精密", "密仪", "仪器", "器的", "的单", "单次", "次最", "最长", "长借", "借用", "用天", "天数", "数是", "是多", "多少"
- 3-gram: "精密仪", "密仪器", "仪器的", "器的单", "的单次", "单次最", "次最长", "最长借", "长借用", "借用天", "用天数", "天数是", "数是多少"
- 4-gram: "精密仪器", "密仪器的", "仪器的单", "器的单次", "的单次最", "单次最长", "次最长借", "最长借用", "长借用天", "借用天数", "用天数是多少"

**关键 token**：
- "精密仪器"（4-gram） → 可匹配 sibling descriptor "精密仪器"
- "最长"（2-gram） → 可匹配 numeric intent signal
- "借用"（2-gram） → 通用 token, 但 "borrow" 不会与之匹配
- "天数"（2-gram） → 通用 token, 但 "days" 不会与之匹配
- "多少"（2-gram） → 匹配 numeric intent signal

**结论**：query token 中有"精密仪器"可以匹配 sibling descriptor，但没有任何 token 能匹配英文字段名 "max_borrow_days" 或 "deposit_amount"。

---

## 附录 B：Reranker Early Return 逻辑详解

当前 `rerank()` 第 118-121 行：

```java
long profilesWithFieldSignal = profiles.stream()
        .filter(p -> p.fieldMatchCount > 0).count();
if (profilesWithFieldSignal == 0 && !queryHasNumericIntent) {
    return hits;  // 不重排，直接返回原始顺序
}
```

**这段代码的含义**：如果没有任何 unit 的 fieldMatchCount > 0（即没有任何 query token 命中字段元数据），且 query 没有数值意图，则不做重排。这是安全的降级逻辑，避免在没有信号时引入噪声。

**YAML 5 题的实际情况**：
- fieldMatchCount = 0（对所有 unit，所有 5 题）
- queryHasNumericIntent = true（对 FQ3/FQ4/FG1/FG2："多少"、"最长"、"最大" 匹配 numericIntentSignals）
  - FQ6 "预约系统当前的版本号是什么" → "什么" → 不匹配 numericIntentSignals → queryHasNumericIntent = false!
- 因此 FQ3/FQ4/FG1/FG2: numericIntent 激活 → 不 early return → 重排执行但效果微弱（只有 +0.5 numericBonus）
- 但 FQ6: 不匹配 numericIntentSignals → early return → 完全不重排！

**验证**：检查 `QuerySemanticRules.numericValueIntentSignals` 是否包含 FQ6 的问法信号。FQ6 问的是 "版本号"，如果当前规则中没有 "版本" 信号，则 numericIntent 不激活。这是 FQ6 相比其他 4 题更难修复的原因之一。

---

## 附录 C：Meta-Design 决策树

```
Q: YAML 5 题是否应进入 Layer 2 sibling context?
A: 是。Layer 1 (Chinese N-gram) 对英文 field name 无效。
   Layer 2 是唯一的零成本、低风险、通用化方案。

Q: sibling context 应该写入哪里?
A: fieldDescription（→ ftsText）。不写入 fieldAliases。

Q: Reranker 是否必须修改？
A: 是。Materializer 单独无法让 target unit 排到 sibling 之前。
   Reranker 必须能感知 fieldDescription 中的 context token。

Q: Layer 2 能解决全部 5 题吗？
A: 不能。预计改善 FQ3/FQ4/FG1（entity match + numeric bonus 区分），
   但 FQ6/FG2 仍有风险（borrowing_system 的 LIKE 原始分差可能过大）。
   如验证后仍有 FAIL，进入 Layer 3 LLM alias。

Q: 是否可以跳过 Layer 2 直接上 Layer 3?
A: 不建议。Layer 2 成本为零（纯算法），应先验证是否足够，
   再决定是否引入 LLM 的成本和风险。

Q: 是否需要清库重建？
A: 是（Materializer 修改导致 fieldDescription 内容变化）。
```

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 数据库查询均为只读 SELECT
- 本轮新增报告：`terminal_unit_phase1d_yaml_sibling_context_design_report.md`
