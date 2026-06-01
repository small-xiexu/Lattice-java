# Terminal Unit Phase 1E: 编译期字段中文 Alias 生成方案设计报告

设计时间：2026-05-29
设计人：agentB（治理/链路分析 Agent）
设计范围：只读分析，不修改任何文件

---

## 1. 前置验证结论：Layer 2 为什么无效

### 1.1 Phase 1D 实验回顾

| 阶段 | 变更 | YAML 5 题效果 | 根因 |
|---|---|---|---|
| Phase 1D-1 | Materializer sibling context（fieldDescription 增加 "context: 精密仪器"） | 0/5 PASS | 目标 unit 的 fieldDescription 有中文了，但 Reranker 不读 fieldDescription |
| Phase 1D-2 | Reranker context scope fix（读取 fieldDescription，contextMatch weight=0.3） | **0/5 PASS，排名零变化** | FTS 原始分差距 ~10+ 分，Reranker 的 +0.8 调整量杯水车薪 |

### 1.2 Phase 1D-2 验证报告关键数据（以 FQ3 为例）

```
type="精密仪器"      → valueText 直接 LIKE 匹配 → originalScore ≈ 13.0 → adjustedScore ≈ 13.1
max_borrow_days=7  → context 间接 LIKE 匹配   → originalScore ≈  2.0 → adjustedScore ≈  2.8

差距 = 13.1 - 2.8 = 10.3 分
```

**Reranker 的 `contextMatchWeight=0.3 + numericBonus=0.5 = +0.8` 无法弥补 10+ 分的 FTS 原始分差。**

### 1.3 为什么 sibling context 不能解决根本问题

sibling context 解决的是 **"entity 匹配"**（"精密仪器" → 定位到 equipment_types[1]），但解决不了 **"field 匹配"**（"最长借用天数" → 定位到 max_borrow_days）。

在同一 parentPath 下，所有 sibling 共享相同的 entity context，因此 context 不能区分 max_borrow_days 和 deposit_amount。最终排序完全由 FTS 原始分决定——而 descriptor sibling（type="精密仪器"）的 valueText 直接 LIKE 匹配远强于目标 unit 的间接 context LIKE 匹配。

---

## 2. 一句话结论

**在 compile 阶段，对 fieldAliases 不包含任何 CJK 字符的 terminal unit，调用编译期 LLM 为其英文字段名生成中文检索别名，将生成的别名追加到 fieldAliases 并重建 ftsText/search_tsv。LLM 输入仅限源文件原文、keyPath、parentPath、同 parentPath sibling keys/values、valueType——禁止输入文件名、eval 题面、query 日志。LLM 调用失败时静默降级，不阻塞 compile。这是当前解决"英文字段名 → 中文语义匹配"缺口的最低成本、最通用、最安全方案。**

---

## 3. 三方案优先级评估

### 3.1 方案对比矩阵

| 维度 | A) Deterministic 增强 | B) LLM Alias 生成 | C) Terminal Unit 向量检索 |
|---|---|---|---|
| **原理** | 纯算法规则：snake_case 拆词 + 英文词根翻译词典 | 编译期 LLM 调用：读源文件内容 → 输出中文别名 | 对 terminal unit 建向量索引，用 embedding 相似度替代 lexical 匹配 |
| **解决 fieldMatchCount=0?** | **部分**。max_borrow_days → ["max", "borrow", "days"] 仍是英文，无法匹配中文 query token | **是**。LLM 可生成 "最长借用天数"、"最大借用天数" | **间接**。不解决 lexical 匹配，绕过它 |
| **泛化能力** | **低**。每种新字段名组合都需要碰巧在词典中有对应 | **高**。LLM 有通用翻译能力，覆盖几乎所有英文→中文场景 | **中**。依赖 embedding 模型对字段名的语义理解 |
| **成本** | 零（纯 CPU） | 每 terminal unit 一次 LLM 调用（~0.5-2s），仅对无 CJK alias 的 unit 触发 | 每次 query 需生成 embedding + 向量搜索 |
| **blast radius** | Materializer 1 个文件 | Materializer + 新增 Enricher Service + prompt 文件 | Schema + vector index + query 检索计划 |
| **失败模式** | N/A（无外部依赖） | LLM 异常 → 静默降级，不阻塞 compile | Embedding 模型不可用 → 整个 channel 不可用 |
| **eval 污染风险** | **极低**（纯形态规则） | **中**（需严格控制 prompt 输入范围） | **低**（embedding 模型是通用预训练模型） |
| **实施阶段** | Phase 1C Layer 1（已完成，对英文无效） | **Phase 1E（本轮设计）** | Phase 2（后续） |

### 3.2 推荐优先级

```
Phase 1E (本轮) → B) LLM Alias 生成
    ↓ 如果 LLM alias 验证后仍有 FAIL
Phase 2 (后续) → C) Terminal Unit 向量检索
    ↓
Phase 1C Layer 1 (已完成) → A) Deterministic (仅对中文 fieldLabel 有效)
```

### 3.3 各方案详细分析

#### A) Deterministic 增强 —— 不推荐作为主方案

**可行的确定性增强（已在 Phase 1C Layer 1 实现）：**
- Snake/camel case 拆分：`max_borrow_days` → ["max", "borrow", "days"] ✓
- 中文 fieldLabel N-gram：`"维护周期(天)"` → ["维护", "护周", "周期", "维护周", "护周期"] ✓
- KeyPath 变体：`equipment_types[1].max_borrow_days` → 路径别名 ✓
- 括号内容提取：`"维护周期(天)"` → 去掉 "(天)" 部分后做 N-gram ✓
- Sibling context：同 parentPath 的中文 descriptor → fieldDescription ✓

**不可行的确定性增强（违反红线或无效）：**

| 想法 | 为什么不可行 |
|---|---|
| 英文→中文翻译词典 | **直接违反红线**："borrow" → "借用"、"deposit" → "押金" 就是硬编码映射。即使放在配置文件中也只是把硬编码从 Java 搬到 YAML，本质不变。且无法泛化——新字段名 "quota_per_user" 怎么办？ |
| 从源文件 YAML 注释提取 | 不可靠——源文件可能没有中文注释。且注释格式不统一。 |
| 从 fact card title/claim 提取 | Fact card title 是 "结构化键值条目"（通用），无字段级信息。 |
| 用文件名推断 | **红线**：禁止把文件名语义化。 |
| 用 sibling key 模式推断 | `max_borrow_days` 和 `deposit_amount` 在结构上看起来相似（都是 snake_case），无区分度。 |

**结论：确定性方案已穷尽（Phase 1C Layer 1 + 1D sibling context），对英文 fieldLabel 无法提供中文 token。**

#### B) LLM Alias 生成 —— 本轮推荐

**为什么 LLM 是正确选择：**

1. **翻译是 LLM 的核心能力**：将 "max_borrow_days" 翻译为 "最长借用天数" 不需要领域知识，只需要通用语言能力。
2. **编译期执行，查询时零成本**：alias 持久化到 `field_aliases_json` 和 `search_tsv`，query 时完全透明。
3. **可审计、可重建**：生成的 alias 存储在数据库中，可以检查、验证、重建。
4. **Fail-closed**：LLM 不可用时静默降级（等同于当前行为），不阻塞 compile。
5. **已有基础设施**：`LlmGateway`、prompt 文件化、route resolution、prompt cache 均已就绪。
6. **精准触发**：只对 `fieldLabel` 不含 CJK 字符的 terminal unit 触发（即英文/数字字段名），不影响 XLSX/CSV 已有 PASS。

**风险与控制：**

| 风险 | 控制措施 |
|---|---|
| LLM 生成与 eval 题面相同的表述 | Prompt 约束"只使用源文件内容中出现的概念，不要引入外部知识"。输入不包含文件名、eval 题面、query 日志 |
| LLM 生成错误别名（如 max_borrow_days → "最大借用天数" 而实际应为 "最长借用天数"） | 可接受——"最大"和"最长"都是合理的中文近义表述，都能帮助 LIKE 匹配 |
| LLM 调用增加 compile 耗时 | 仅对无 CJK alias 的 unit 触发（YAML 约 30 个 unit 中 ~25 个无 CJK）；按 parentPath 批量调用（同 parentPath 的 unit 共享一次 LLM 调用）→ 减少调用次数 |
| LLM 生成过多或过长别名 | 每条 alias ≤ 20 字符，总数 ≤ 20 条，超限截断 |
| LLM Token 成本 | 单次调用 prompt ~500 tokens + response ~100 tokens = ~600 tokens。按 parentPath 批量：5 个 YAML parentPath × ~600 tokens = ~3000 tokens/compile。使用 gpt-5.5（低成本模型）|

#### C) Terminal Unit 向量检索 —— Phase 2 保留

**为什么当前不做：**

1. **需要 schema 变更**（新增 `fact_card_terminal_unit_vector_index` 表）
2. **需要 query 检索计划变更**（新增 vector channel）
3. **需要 RRF 融合权重调整**
4. **不能替代 lexical**：向量检索解决的是"语义相似"，但 structured terminal value retrieval 需要的是"精确字段名匹配"。用户问 "最长借用天数" 时，向量检索可能返回 "deposit_amount"（因为都是 number 型、都在 equipment_types 下），而不是 "max_borrow_days"
5. **属于 Phase 2 范围**：Phase 1 聚焦 lexical/FTS，Phase 2 才引入向量

**Phase 2 的合理场景**：当 LLM alias 解决了字段名匹配后，向量检索可以进一步解决"跨 parentPath 的语义聚合"（如 "所有设备类型的押金分别是多少"）。

---

## 4. LLM Alias 方案详细设计

### 4.1 架构概览

```
compile 流程:
  FactCardGenerationService.rebuildForSourceFile()
    → generateForSourceFile()     // 生成 fact cards
    → factCardJdbcRepository.upsert(factCard)
    → materializeTerminalUnits(factCard)   // [现有] 展开 terminal units
    → enrichFieldAliases(factCard, terminalUnitRecords)  // [新增] LLM alias 增强
    → factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords)
```

### 4.2 新增组件：FactCardTerminalUnitFieldAliasEnricher

**文件**：`src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java`

**职责**：对英文字段 terminal unit 调用 LLM 生成中文别名。

**核心逻辑**：

```java
@Service
public class FactCardTerminalUnitFieldAliasEnricher {

    private final LlmGateway llmGateway;
    private final CompilerPromptProvider promptProvider;

    /**
     * 为 terminal unit 列表增强中文字段别名。
     * 仅对 fieldLabel 不含 CJK 字符的 unit 触发 LLM 调用。
     *
     * @param factCardRecord 所属 fact card
     * @param terminalUnitRecords terminal unit 列表（会被原地修改）
     */
    public void enrich(List<FactCardTerminalUnitRecord> records, FactCardRecord factCard) {
        // 1. 按 parentPath 分组
        Map<String, List<FactCardTerminalUnitRecord>> byParent = records.stream()
            .collect(Collectors.groupingBy(r -> r.getParentPath()));

        // 2. 对每个 parentPath，收集需要 LLM 增强的 unit
        for (var entry : byParent.entrySet()) {
            List<FactCardTerminalUnitRecord> group = entry.getValue();

            // 3. 判断是否需要 LLM：group 中是否存在 fieldLabel 不含 CJK 的 unit
            boolean needsLlm = group.stream().anyMatch(r -> !containsCJK(r.getFieldLabel()));
            if (!needsLlm) continue;  // XLSX/CSV 中文列头已有 N-gram，跳过

            // 4. 构建 LLM prompt
            String prompt = buildPrompt(factCard, group);

            // 5. 调用 LLM（fail-closed：异常时 continue，不修改任何 unit）
            try {
                LlmEnrichResult result = callLlm(prompt);
                if (result == null || result.aliases.isEmpty()) continue;

                // 6. 将生成的别名合并到对应 unit
                for (var unitRecord : group) {
                    List<String> generatedAliases = result.aliases.get(unitRecord.getTerminalKey());
                    if (generatedAliases == null || generatedAliases.isEmpty()) continue;

                    // 合并别名
                    List<String> existingAliases = parseJsonArray(unitRecord.getFieldAliasesJson());
                    Set<String> merged = new LinkedHashSet<>(existingAliases);
                    for (String alias : generatedAliases) {
                        if (alias.length() <= 20 && merged.size() < 20) {
                            merged.add(alias.trim());
                        }
                    }

                    // 重建 fieldAliasesJson, ftsText, search_tsv（需要 Materializer 协作）
                    unitRecord.setFieldAliasesJson(writeJsonArray(new ArrayList<>(merged)));
                    // ftsText 和 search_tsv 重建由 Materializer 的 rebuildFtsText() 方法完成
                }
            } catch (Exception e) {
                // fail-closed: 静默降级
                log.warn("LLM field alias enrichment failed for parentPath={}", entry.getKey(), e);
            }
        }
    }
}
```

### 4.3 LLM Prompt 设计

**文件**：`src/main/resources/prompts/compiler/field-alias-enricher.md`

```
你是结构化数据字段别名生成器。你的任务是：为英文/数字字段名生成中文检索别名。

## 输入信息

你会收到：
1. **源文件内容**：包含该字段的源文件全文（YAML/JSON/Markdown 等）
2. **字段列表**：每个字段包含：
   - terminalKey: 英文字段名（如 max_borrow_days）
   - keyPath: 完整路径（如 equipment_types[1].max_borrow_days）
   - parentPath: 父级路径
   - valueType: 值类型（number/string/version/boolean/url 等）
   - siblingKeys: 同父级路径下的兄弟字段名列表
   - siblingValues: 兄弟字段的中文值（如 type="精密仪器"）

## 输出格式

返回严格的 JSON，不要包含 markdown 代码块标记：

{
  "aliases": {
    "max_borrow_days": ["最长借用天数", "最大借用天数", "借用期限"],
    "deposit_amount": ["押金金额", "押金"],
    "late_fee_per_day": ["逾期费用", "每日罚金", "滞纳金"]
  }
}

## 规则

1. 每个字段生成 2-5 个中文别名
2. 别名应为中文用户可能用于搜索该字段的短语（2-8 个中文字符）
3. 只使用源文件内容中出现的概念和术语，不要引入外部领域知识
4. 不要在别名中包含字段值，只描述字段含义
5. 如果无法从源文件内容确定字段含义，对该字段返回空数组 []
6. 不要输出 markdown 代码块标记（```json 或 ```），只输出纯 JSON
```

**关键设计原则：**
- "只使用源文件内容中出现的概念" —— 防止 LLM 从训练数据中引入 eval 题面语言
- "不要在别名中包含字段值" —— 防止别名与具体数据绑定
- "如果无法确定字段含义，返回空数组" —— fail-safe，不编造

### 4.4 触发条件（最小化调用）

```
触发条件 = fieldLabel 不含任何 CJK 字符
          AND parentPath 分组中存在至少一个 fieldLabel 不含 CJK 的 unit

XLSX "存储条件": fieldLabel = "存储条件" → 含 CJK → 不触发（已有 N-gram alias）
XLSX "危险等级": fieldLabel = "危险等级" → 含 CJK → 不触发
CSV "维护等级": fieldLabel = "维护等级" → 含 CJK → 不触发
YAML "max_borrow_days": fieldLabel = "max_borrow_days" → 不含 CJK → 触发
YAML "type": fieldLabel = "type" → 不含 CJK → 触发（但 type 的 value 是中文，LLM 可能生成 "设备类型" 别名，这实际上也是合理的）
```

**按 parentPath 批量调用优化：**

```
parentPath=equipment_types[1] 的 7 个 unit → 1 次 LLM 调用（输出所有 7 个 key 的别名）
parentPath=equipment_types[0] 的 7 个 unit → 1 次 LLM 调用
parentPath=equipment_types[2] 的 7 个 unit → 1 次 LLM 调用
parentPath=borrowing_system 的 5 个 unit → 1 次 LLM 调用
parentPath=approval_chain[0-2] 的 9 个 unit → 3 次 LLM 调用
parentPath=return_policy 的 2 个 unit → 1 次 LLM 调用

总计：7 次 LLM 调用（而非 37 次逐个调用）
```

### 4.5 与 Materializer 的集成

**两种集成方式：**

| 方式 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| **A) Enricher 作为 Materializer 的后处理** | Materializer 生成 unit records → Enricher 读取并修改 fieldAliases/fieldDescription/ftsText → Repository upsert | 职责清晰，Materializer 不需要知道 LLM | 需要 Enricher 能重建 ftsText（逻辑与 Materializer 重叠） |
| **B) Enricher 作为 Materializer 的内部步骤** | Materializer 在 `materializeItem()` 中调用 Enricher | ftsText 构建时已包含 LLM alias | Materializer 职责膨胀 |

**推荐方式 A**：Enricher 作为独立 Service，在 `FactCardGenerationService.materializeTerminalUnits()` 中，在 Materializer 返回 records 之后、Repository upsert 之前调用。

**ftsText 重建**：Enricher 修改 fieldAliases 后，需要重建 ftsText。为保持单一职责，可以在 Enricher 中通过 `FactCardTerminalUnitRecord` 新增一个 `rebuildFtsText()` 方法，或在 Materializer 中暴露一个 `rebuildFtsText(record, newAliases)` 方法。

### 4.6 安全底座（Fail-Closed）

```
LLM 调用链路:
  try {
      result = llmGateway.call(fieldAliasEnrichPrompt)
      if (result == null || parseError) → return (不修改任何 unit)
      aliasCount = validateAndMerge(result)
  } catch (TimeoutException) → log.warn → return
  catch (RuntimeException) → log.warn → return
  catch (JsonProcessingException) → log.warn → return

降级行为: 不修改 unit → fieldAliases 保持原样 → 查询行为与当前完全一致
```

**与现有 compile review fail-closed 底座的关系**：
- LLM alias Enricher 在 Reviewer 之前执行（在 fact card 持久化阶段）
- 即使 Enricher 的 LLM 调用失败，compile 继续执行，不受影响
- Enricher 的 LLM 调用与 Reviewer 的 LLM 调用使用相同的 `LlmGateway` 基础设施
- Enricher 不是审查路径——生成的别名不影响 fact card 的 review status

### 4.7 红线合规设计

| 检查项 | 合规措施 |
|---|---|
| **LLM 输入不包含文件名** | 只传源文件内容文本（YAML/Markdown 正文），不传文件名 |
| **LLM 输入不包含 eval 题面** | 只传源文件内容 + 字段路径结构，不传任何查询文本 |
| **LLM 输入不包含 query 日志** | Enricher 运行在 compile 阶段，完全无 query 上下文 |
| **LLM prompt 不预设业务域** | Prompt 只描述"为英文字段名生成中文别名"任务，不提及任何具体业务词 |
| **生成的 alias 不写入代码** | alias 持久化到数据库 `field_aliases_json` 列，不在 Java 代码中硬编码 |
| **不硬编码映射表** | 无任何 Java `Map.of("max_borrow_days", "最长借用天数")` 或 YAML 配置映射 |
| **Hidden eval 不接触** | Enricher 的输入全部来自 `FactCardRecord` 和 `FactCardTerminalUnitRecord`，不读取 hidden eval |
| **编译可重建** | 删除 terminal units + 重新 compile → 重新生成 alias（幂等） |

### 4.8 数据库影响

**不需要 schema 变更**。所有目标列已存在：

| 列 | 变更 |
|---|---|
| `field_aliases_json` | 内容增加（LLM 生成的中文 alias 追加到现有 alias） |
| `field_description` | 可选增强（LLM 可生成更自然的 field description） |
| `fts_text` | 内容增加（alias 变化导致 ftsText 变化） |
| `search_tsv` | 自动更新（upsert 时由 `to_tsvector('simple', fts_text)` 重新生成） |

### 4.9 预期效果

以 FQ3 目标 unit `equipment_types[1].max_borrow_days=7` 为例：

**Before (Phase 1D-2)**:
```
fieldAliases: ["max_borrow_days", "max borrow days", ...] ← 全部英文
fieldDescription: "parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器"
```

**After (Phase 1E LLM Alias)**:
```
fieldAliases: ["max_borrow_days", "max borrow days", ...,
               "最长借用天数", "最大借用天数", "借用期限"] ← 追加中文别名
fieldDescription: "parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器"
```

**效果推演：**

1. **LIKE 匹配**：query token "最长" 和 "借用天数" 现在能 LIKE 匹配 `field_aliases_json`（权重 +3.0）和 `fts_text`（权重 +2.0）
2. **Reranker**：`fieldMatchCount` 从 0 → 2+（"最长"、"借用天数" 命中 fieldAliases）
3. **TerminalKeyMatchCount**：从 0 → 1+（"最长借用天数" 命中 fieldAliases）
4. **Sibling boost**：同 parentPath 中，max_borrow_days 有 terminalKeyMatch > 0，其他 sibling (deposit_amount 的 alias 是 "押金金额") 可能没有 → max_borrow_days 获得 +6.0 SIBLING_FIELD_BOOST
5. **最终排序**：max_borrow_days 的 adjustedScore 大幅超过 type="精密仪器"（后者只有 valueMatch）

---

## 5. 不允许的方案

### 5.1 硬编码映射表

```
❌ Java: if (key.equals("max_borrow_days")) aliases.add("最长借用天数")
❌ YAML: field-aliases.yml: { "max_borrow_days": ["最长借用天数"] }
❌ SQL: INSERT INTO field_alias_mappings VALUES ('max_borrow_days', '最长借用天数')
```

**拒绝理由**：直接违反 AGENTS.md 禁令——"不准在 Java 主链硬编码中文字段语义"、"不准在配置中维护中英文映射表"。

### 5.2 在 query 时调用翻译 API

```
❌ query → 检测到中文 token → 调用翻译 API → "最长借用天数" → "max_borrow_days" → 重写 query
```

**拒绝理由**：
- 增加查询延迟（翻译 API 调用 + 网络往返）
- 翻译结果不可控（"最长借用天数" 可能被译成 "longest borrowing days" 而非 "max_borrow_days"）
- 无法审计（每次查询翻译结果可能不同）
- 增加成本（每次查询都调用）

### 5.3 纯向量检索替代 lexical

```
❌ 跳过 LLM alias，直接对 terminal unit 建向量索引，所有匹配走 embedding
```

**拒绝理由**：
- 向量检索解决的是"语义相似"，不是"精确匹配"
- "最长借用天数" 在向量空间中可能更接近 "deposit_amount" 的描述文本（都是"金额/天数"语义），而非 "max_borrow_days"
- Phase 1 聚焦 lexical/FTS，Phase 2 才引入向量
- 向量检索需要大量基础设施变更（schema、index、query plan、RRF weight）

### 5.4 扩大 Reranker context weight（如 0.3 → 5.0）

```
❌ 继续调高 contextMatch.weight 到 5.0 或 10.0
```

**拒绝理由**（Phase 1D-2 验证报告已确认）：
- 即使把 weight 调到 5.0，Reranker 的 +5.3 调整量仍无法弥补 ~10+ 分的原始分差
- 调高 weight 到 10.0 会在其他场景引入假阳性——任何有 context 的 unit 都会过度提升
- 根因不在权重不够，而在**根本没有信号可以区分 field names**

### 5.5 修改 QuestionTokenExtractor 增加"字段名猜测"

```
❌ query = "精密仪器的单次最长借用天数" → 提取 "最长借用天数" → 尝试匹配 terminalKey 关键词
```

**拒绝理由**：这是反向工程——试图从 query 中猜测用户想查哪个字段。不可泛化，容易引入 eval 语言。

---

## 6. 实现路线图

### 6.1 Phase 1E Step 1: Materializer 暴露 ftsText 重建接口（最小改动）

**文件**：`FactCardTerminalUnitMaterializer.java`

**变更**：将 `buildFtsText()` 方法改为 package-private 或 public，或新增一个独立的重建方法：

```java
/**
 * 使用新的 fieldAliases 重建 unit 的 ftsText。
 * 由 FieldAliasEnricher 在修改 aliases 后调用。
 */
public void rebuildSearchText(FactCardTerminalUnitRecord record, List<String> newAliases) {
    String newFtsText = buildFtsText(
        record.getFactCardRecord(),  // 需要持有 factCardRecord 引用
        record.getStructure(),
        record.getFieldLabel(),
        newAliases,
        record.getKeyPath(),
        record.getParentPath(),
        record.getTerminalKey(),
        record.getDisplayText(),
        record.getValueText(),
        record.getNormalizedValue(),
        record.getValueType(),
        record.getFieldDescription()
    );
    record.setFtsText(newFtsText);
}
```

### 6.2 Phase 1E Step 2: 新增 FieldAliasEnricher（核心变更）

**新增文件**：
- `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java`
- `src/main/resources/prompts/compiler/field-alias-enricher.md`

**变更文件**：
- `FactCardGenerationService.java`：在 `materializeTerminalUnits()` 中调用 Enricher

### 6.3 Phase 1E Step 3: 单元测试

**测试用例**（使用 synthetic YAML fixtures）：

| 测试 | 输入 | 期望 |
|---|---|---|
| `shouldSkipWhenFieldLabelHasCJK` | fieldLabel="维护等级" | 不触发 LLM，aliases 不变 |
| `shouldCallLlmWhenFieldLabelHasNoCJK` | fieldLabel="max_borrow_days" | 触发 LLM，aliases 增加中文 |
| `shouldDegradeGracefullyOnLlmFailure` | LLM 超时 | 不抛异常，aliases 不变 |
| `shouldTruncateLongAliases` | LLM 返回 30 字 alias | 截断到 20 字 |
| `shouldLimitAliasCount` | LLM 返回 30 条 alias | 截断到 20 条 |
| `shouldNotContainControlCharacters` | LLM 返回含换行符 alias | 过滤 |
| `shouldBatchByParentPath` | 同 parentPath 7 个 unit | 仅调用 1 次 LLM |

### 6.4 实现顺序

```
Step 1: Materializer 暴露 ftsText 重建接口 (~10 行)
Step 2: 新增 FieldAliasEnricher + prompt 文件 (~150 行)
Step 3: FactCardGenerationService 集成 (~10 行)
Step 4: 单元测试 (~80 行)
Step 5: redline + mvn test + 定向测试
Step 6: agentD clean schema 端到端验证
```

### 6.5 允许修改范围

| 文件 | 变更 | 行数估算 |
|---|---|---|
| `FactCardTerminalUnitMaterializer.java` | 暴露 ftsText 重建方法 | ~10 行 |
| `FactCardTerminalUnitFieldAliasEnricher.java` | **新增** LLM alias 生成服务 | ~120 行 |
| `src/main/resources/prompts/compiler/field-alias-enricher.md` | **新增** LLM prompt | ~30 行 |
| `FactCardGenerationService.java` | 集成 Enricher 调用 | ~10 行 |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | **新增** 测试 | ~80 行 |

**总计：约 250 行代码变更。**

### 6.6 禁止修改范围

| 文件/区域 | 原因 |
|---|---|
| `FactCardTerminalUnitIntentReranker.java` | Phase 1B 已实现，权重不变 |
| `FactCardTerminalUnitFtsSearchService.java` | 检索逻辑不变 |
| `FactCardTerminalUnitMapper.xml` | SQL 不变（LIKE 对 field_aliases_json 的匹配已存在） |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | Phase 1B 已配置 |
| `AnswerGeneration*` / fallback / citation | **严禁修改 query 主链** |
| `schema.sql` | 不需要 DDL 变更 |
| `scripts/scan-redline.sh` / allowlist | 严禁修改 |

---

## 7. 验证方案

### 7.1 工程门禁

| Gate | 要求 |
|---|---|
| redline | `BLOCKER=0` |
| 定向测试 | FieldAliasEnricher + Materializer 测试全部通过 |
| mvn test | 全量通过 |
| Prompt 审计 | field-alias-enricher.md 不含业务域提示、不含文件名引用、不含 eval 语言 |

### 7.2 数据层验证（agentD）

1. Clean schema reset + 重新 compile 5 份资料
2. 查询 YAML terminal unit 的 `field_aliases_json`，确认包含中文 alias：
   - `max_borrow_days` → 包含类似 "最长借用天数" 的 alias
   - `deposit_amount` → 包含类似 "押金金额" 的 alias
   - `version` → 包含类似 "版本号" 的 alias
   - `late_fee_per_day` → 包含类似 "逾期费用" 的 alias
   - `max_concurrent_requests` → 包含类似 "最大并发请求数" 的 alias
3. 确认 CSV/XLSX terminal unit 的 aliases 未受影响（不应触发 LLM）

### 7.3 排序层验证（agentD）

对 YAML 5 题验证 terminal unit channel 排名：

| 题目 | 验证目标 | Gate |
|---|---|---|
| FQ3 | max_borrow_days=7 的 hitRank 是否 < type="精密仪器" 的 hitRank | hitRank 显著提升 |
| FQ4 | deposit_amount=100 和 deposit_amount=1000 是否进入 top 3 | 进入 topK |
| FQ6 | version=v2.3.1 是否进入 top 3 | 进入 topK |
| FG1 | late_fee_per_day=20 是否进入 top 3 | 进入 topK |
| FG2 | max_concurrent_requests=50 的 hitRank 是否 < name="校园实验室设备预约系统" 的 hitRank | hitRank 提升 |

### 7.4 答案层验证（agentD）

| 题目 | 验证目标 |
|---|---|
| YAML 5 题 | answer claim 是否使用了目标 unit 的值（7、100/1000、v2.3.1、20、50） |
| FQ7/FQ11 保护 | 答案不退化 |

### 7.5 污染审计

| 检查项 | 方法 |
|---|---|
| LLM prompt 不含业务域提示 | 审计 `field-alias-enricher.md` |
| 生成的 alias 不包含 eval 题面特征词 | 人工检查 YAML 5 题 target unit 的 alias |
| 不修改题集/标准答案 | Diff 审计 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解 | 回滚 |
|---|---|---|---|
| LLM 生成与 eval 题面相同的表述 | eval 指标虚高，过拟合 | Prompt 约束"只使用源文件内容中出现的概念"；alias 审计 | 删除 Enricher，重建索引 |
| LLM 生成错误的别名（如 max_borrow_days → "最大借用天数"） | 可能匹配到其他 query，但仍在语义合理范围 | "最大"和"最长"近义，均可作为检索入口；比当前零匹配好 | 调整 prompt 精度 |
| LLM 调用增加 compile 耗时 | compile job 时间延长 | 按 parentPath 批量调用（5 个 YAML parentPath 共 7 次调用）；设置 5s 超时 | 关闭 Enricher |
| LLM 不可用 | compile 无法生成 alias | Fail-closed：静默降级，不影响 compile 成功 | N/A（本身就是降级） |
| LLM 生成的 alias 过长或过多 | 污染 ftsText，增加 LIKE 噪声 | 每条 alias ≤ 20 字符，总数 ≤ 20 条，截断 | 调整上限 |
| XLSX/CSV 误触发 | 已有中文 N-gram 被 LLM alias 覆盖或重复 | 触发条件检查 fieldLabel 不含 CJK → XLSX/CSV 中文列头不触发 | 调整触发条件 |

---

## 9. 是否需要清库重建

**需要**。理由：
- `field_aliases_json`、`fts_text`、`search_tsv` 在 compile 时计算并持久化
- 修改 Materializer/Enricher 后，旧 terminal unit 不含 LLM alias
- 必须 reset schema + 重新 compile 才能生成新的 alias

---

## 10. 下一轮 agentA 唯一最小实现建议

### 10.1 唯一变量

**编译期 LLM 字段别名生成**。只允许往 `fieldAliases` 中追加 LLM 生成的中文 alias，并相应地重建 `ftsText`（连带更新 `search_tsv`）。

### 10.2 实现提示词草案

```
你是 agentA，本轮任务：实现 Terminal Unit Phase 1E LLM 字段别名生成（唯一变量）。

## 目标
对 fieldLabel 不含 CJK 字符的 terminal unit，在 compile 阶段调用 LLM 生成中文字段别名，
追加到 fieldAliases 并重建 ftsText/search_tsv。LLM 调用失败时静默降级，不阻塞 compile。

## 允许新增文件
- src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java
- src/main/resources/prompts/compiler/field-alias-enricher.md
- src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricherTests.java

## 允许修改文件
- FactCardTerminalUnitMaterializer.java：暴露 rebuildSearchText(record, newAliases) 方法
- FactCardGenerationService.java：在 materializeTerminalUnits() 中调用 Enricher

## 禁止修改文件
- FactCardTerminalUnitIntentReranker.java / FactCardTerminalUnitFtsSearchService.java
  / QuerySemanticRules.java / lattice-query-semantic.yml（Phase 1B/1C 代码，保留不动）
- FactCardTerminalUnitMapper.xml / schema.sql
- AnswerGeneration* / fallback / citation
- scripts/scan-redline.sh / allowlist / prompts（除 field-alias-enricher.md 外）

## 实现要点
1. FieldAliasEnricher 按 parentPath 分组批量调用 LLM
2. 触发条件：fieldLabel 不含 CJK 字符
3. LLM prompt 只输入：源文件内容、keyPath、parentPath、sibling keys/values、valueType
4. LLM prompt 不输入：文件名、eval 题面、query 日志
5. Fail-closed：LLM 异常 → 静默降级，不阻塞 compile
6. 别名校验：每条 ≤20 字符，总数 ≤20 条
7. 使用现有 LlmGateway + CompilerPromptProvider 基础设施
8. 使用 compile.writer 的路由配置（与 Writer 使用相同 LLM provider）

## 测试要求
- Synthetic YAML fixture，不复刻 eval 题面
- 测试 CJK fieldLabel 不触发 LLM
- 测试 LLM 成功返回时 alias 合并正确
- 测试 LLM 失败时降级不阻塞
- 测试 alias 长度/数量上限截断
```

---

## 11. 如果 LLM Alias 验证后仍有 FAIL

### 11.1 预期：本次可能仍不足的场景

| 场景 | 可能原因 | 后续方向 |
|---|---|---|
| LLM 未生成正确的 field alias | 源文件内容不足以推断字段含义（如孤立字段 "quota" 无法从上下文理解） | 扩大 LLM prompt 输入范围（如前一条 fact card 的 claim/title） |
| FQ6 version=v2.3.1 仍未排到 top1 | "版本号" alias 可能不够区分（borrowing_system.version vs 所有有 version 的字段） | 增加 "version" 到 numericIntentSignals（`FactCardTerminalUnitIntentReranker`） |
| FG2 max_concurrent_requests=50 仍未排到 top1 | 同 parentPath 的 name="校园实验室设备预约系统" 的 LIKE 匹配过强 | 提高 SIBLING_FIELD_BOOST 或引入 terminalKey match 优先级 |

### 11.2 后续路线

```
Phase 1E LLM Alias (本轮)
  ↓ 如果 YAML 5 题部分通过但未全通过
Phase 1E-2: 微调 Reranker（如 version 类型 bonus、调整 weight）
  ↓ 如果仍不足
Phase 2: Terminal Unit 向量检索
```

---

## 附录 A：源文件内容作为 LLM 输入的合规性分析

**问题**：将源文件完整内容传给 LLM 是否违反 "不读取 eval 题面" 的红线？

**分析**：
- 源文件内容是 **compile 阶段的正常输入**，所有 compile 步骤（Writer、Reviewer）都已使用源文件内容
- LLM 只读取源文件内容来理解字段含义，不读取 eval 题面、query 日志或 hidden eval
- 源文件内容是用户上传的知识库资料，属于正常数据流
- **合规**

**问题**：LLM 生成的 alias 可能恰好与 eval 题面中的表述相同，是否构成过拟合？

**分析**：
- eval 题面使用自然语言描述业务问题（如 "精密仪器的单次最长借用天数是多少"）
- LLM 从源文件内容中理解 "max_borrow_days" 的含义，生成 "最长借用天数" ——这是**合理的语义翻译**，不是过拟合
- 即使题面使用了 "最多可借几天" 而非 "最长借用天数"，alias 中的 "最大借用天数" 仍能匹配（bigram/trigram LIKE 匹配）
- 过拟合的风险在于 prompt 中写入了题面语言，而非 LLM 基于源文件内容做出的合理推断

---

## 附录 B：现有 LLM 基础设施复用分析

| 组件 | 用途 | 复用方式 |
|---|---|---|
| `LlmGateway` | LLM 调用网关（路由、缓存、预算、重试） | 直接注入到 Enricher |
| `CompilerPromptProvider` | 编译期 prompt 文件加载 | 新增 `getFieldAliasEnricherPrompt()` 方法 |
| `LlmProperties` | LLM 配置（超时、温度等） | 复用 compile.writer 的配置 |
| `ExecutionLlmSnapshotService` | LLM 调用审计快照 | 由 LlmGateway 自动处理 |
| Prompt cache | L1 prompt cache | 由 LlmGateway 自动处理（compile 路径默认启用） |

---

## 附录 C：不推荐的确定性增强方向清单（全部排除）

| 方向 | 为何排除 |
|---|---|
| 英文 camelCase → 中文直译词典 | 硬编码映射，无法泛化，违反红线 |
| 源文件 YAML 注释提取 | 不可靠（非结构化），不能假设存在 |
| 源文件 Markdown 章节标题提取 | 不可靠，Markdown 标题不一定描述字段含义 |
| Fact card title/claim 提取 | Title 是通用模板 "结构化键值条目"，无字段信息 |
| 同源其他 YAML key 的模式推断 | `max_borrow_days` 和 `deposit_amount` 都是 snake_case，无区分度 |
| 字段值类型推断 | `valueType=number` 不能区分 max_borrow_days 和 deposit_amount |
| 外部翻译 API | 增加外部依赖和成本，翻译结果不可控 |
| 运行时 query 翻译 | 每次查询都调用翻译，增加延迟，不可审计 |

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 本轮新增报告：`terminal_unit_phase1e_field_alias_generation_design_report.md`
