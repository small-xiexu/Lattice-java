# Terminal Unit Phase 1C: 字段语义 Alias / Label / Description 物化方案设计报告

设计时间：2026-05-29
设计人：agentB（治理/链路分析 Agent）
设计范围：只读分析，不修改任何文件

## 1. 背景与根因

### 1.1 Phase 1B 失败根因

Phase 1B `FactCardTerminalUnitIntentReranker` 在 clean schema 验证中 **terminal unit channel 排序完全无效**——全部 5 道结构化 terminal value 题（FQ3/FQ4/FQ6/FG1/FG2）仍选中与 Phase 1A 相同的 sibling terminal unit，目标 unit 进入 topK = 0/5。

唯一根因已定位：

> **Terminal unit rerank 依赖 query token 与 terminalKey/fieldLabel/fieldAliases 的精确 token 匹配。当 query 为中文、terminal key 为英文时，此匹配永远为 0。中国 N-gram（"最长借用天数"、"押金"、"版本号"）无法匹配英文字段名（"max_borrow_days"、"deposit_amount"、"version"），反而匹配到 sibling unit 的中文 value_text（"精密仪器"、"常规设备"）。**

详见 `terminal_unit_phase1b_ranking_clean_verification_report.md` 第 4.4 节。

### 1.2 为什么不是权重问题

Reranker 的单元测试在英文 synthetic 场景下 13/13 通过。`fieldMatchCount` 在中英文场景下永远为 0——这是匹配通道缺失，不是权重调整问题。提高 numericBonus 到 +10 可以暴力解决 FQ3，但会在其他场景引入假阳性，属于 case 特判。

### 1.3 Phase 1C 要解决什么

在 compile/index 层为 terminal unit metadata 补充**中文可检索字段语义文本**，使 Phase 1B 的 Reranker（以及 PostgreSQL FTS）能在中文 query 与英文 terminalKey 之间建立匹配通道。

---

## 2. Phase 1B 代码处置建议

### 2.1 结论：不提交，但也不回退

| 文件 | 状态 | 处置 |
|---|---|---|
| `FactCardTerminalUnitIntentReranker.java` | 新增（untracked） | **保留**，暂不提交 |
| `FactCardTerminalUnitIntentRerankerTests.java` | 新增（untracked） | **保留**，暂不提交 |
| `FactCardTerminalUnitFtsSearchService.java` | 修改（unstaged） | **保留**，暂不提交 |
| `QuerySemanticRules.java` | 修改（unstaged） | **保留**，暂不提交 |
| `lattice-query-semantic.yml` | 修改（unstaged） | **保留**，暂不提交 |
| `FactCardTerminalUnitFtsSearchServiceTests.java` | 修改（unstaged） | **保留**，暂不提交 |

### 2.2 理由

1. **Reranker 代码质量无问题**：redline BLOCKER=0，mvn test=947/0/0，Spring DI 链路正确，配置化信号路径正确。
2. **Reranker 是 Phase 1C 的消费者**：Reranker 从 `metadataJson` 中读取 `fieldAliases` 做 token 匹配。Phase 1C 负责**生产**中文别名，Phase 1B 负责**消费**中文别名。两者互补，不是替代关系。
3. **`QuerySemanticRules.numericValueIntentSignals` 是通用中文数值问法信号**（"多少"、"最大"、"最长"等），不是业务词硬编码。该配置项通过 YAML 外部化，符合配置化规范。
4. **回退成本高于保留成本**：回退需要额外一轮代码变更，且后续 Phase 1C 实现后仍需重新引入 Reranker。

### 2.3 提交策略

**Phase 1C 实现验证通过后，与 Phase 1B 代码一起提交。** commit message 必须写清楚：
- Phase 1B 提供通用 lexical rerank 框架（字段 token 匹配、sibling boost、numeric bonus）
- Phase 1C 提供中文 field alias 物化（使 Reranker 在中英文场景下生效）
- 两者组合构成完整的 "structured terminal value retrieval" 修复

---

## 3. Phase 1C 放置层分析

### 3.1 候选层

| 候选层 | 说明 | 评价 |
|---|---|---|
| **A) `FactCardTerminalUnitMaterializer`** | terminal unit 物化时生成 fieldAliases | **推荐** |
| B) Fact card 生成层 (`FactCardGenerationService`) | fact card 生成时附加字段语义 | 不推荐 |
| C) 上游 structured fact extraction 层 | 源文件解析时提取字段描述 | 不推荐 |
| D) Query 侧动态别名扩展 | 查询时翻译中文 token | 不推荐 |

### 3.2 推荐：A) `FactCardTerminalUnitMaterializer`

**理由：**

1. **单一职责**：Materializer 已是 fieldAliases/fieldLabel/fieldDescription 的**唯一生成点**。在此处增强，改动面最小。
2. **上下文完备**：Materializer 拥有生成别名所需的全部上下文——`keyPath`、`parentPath`、`terminalKey`、`pathSegments`、`valueText`、`valueType`，以及 `FactCardRecord` 中的 `cardType`、`answerShape`、`itemsJson`。
3. **不需要上游变更**：Fact card 生成层、源文件解析层无需修改。
4. **不需要 schema 变更**：`fact_card_terminal_units` 表的 `field_label`、`field_aliases_json`、`field_description` 列已存在，只需改变填充逻辑。
5. **编译时一次性生成**：alias 在 compile 时计算并持久化，query 时零额外成本。

### 3.3 为什么不选其他层

| 层 | 拒绝理由 |
|---|---|
| B) Fact card 生成层 | Fact card 生成是 LLM 驱动的，增加字段语义输出会改变 prompt 和 card schema，blast radius 太大。且 fact card 是卡级粒度，不是字段级粒度。 |
| C) 上游 extraction 层 | 源文件解析层不感知"哪些字段会变成 terminal unit"。字段语义应该紧邻 terminal unit 物化点生成，不应在遥远的解析层预设。 |
| D) Query 侧动态扩展 | 查询时翻译中文 token 需要维护同义词库或调用 LLM，增加查询延迟和成本，且容易引入 eval 污染。编译时生成是 fire-and-forget。 |

---

## 4. 安全来源分析

### 4.1 允许来源（白名单）

#### 来源 1：源文件表头/列名（最安全、已有数据）

**适用范围**：XLSX、CSV 等表格类源文件。

当前 `KeyValueItem.key` 在表格场景下**已经是中文列头**（如 "存储条件"、"保管人角色"、"设备编号"）。Materializer 已将其作为 `terminalKey`/`fieldLabel` 使用，但**未对中文 fieldLabel 做 N-gram 切词生成别名**。

**示例**：对于 CSV 列 `维护周期(天)`，当前 fieldLabel = "维护周期(天)"，fieldAliases 包含 `"维护周期(天)"`、`"维护周期 天"`（括号替换为空格），但缺少 `"维护周期"`、`"维护"`、`"周期"` 等中文 N-gram。

**增强方式**：当 fieldLabel 包含中文字符时，额外应用中文 bigram/trigram 切词，生成中文 N-gram 别名。

#### 来源 2：父级对象描述符字段值（结构性、源内容派生）

**适用范围**：YAML/JSON 等嵌套结构，同一 parentPath 下存在中文描述符字段（如 `type`、`name`、`stage`）。

**示例**：`equipment_types[1].max_borrow_days = 7` 的 parentPath 为 `equipment_types[1]`，同一 parent 下存在 `type = "精密仪器"`。可将 "精密仪器" 加入 fieldDescription 或 fieldAliases 作为上下文信号。

**增强方式**：在 Materializer 中识别同一 `items_json.items[]` 内同 `parentPath` 的 sibling item，若存在中文 string 值字段，将其值作为上下文别名加入目标 unit 的 fieldAliases/fieldDescription。

**红线边界**：只能使用**同一 fact card 内、同一 parentPath 的 sibling item 的 value**。不能跨 card、不能跨 source file、不能使用 card title 或 article summary 做语义推断。

#### 来源 3：通用中文 N-gram 切词（纯算法、零风险）

**适用范围**：任何包含中文字符的 fieldLabel、parentPath 末段、keyPath 片段。

当前 `addSplitAliases` 只做英文 snake/kebab/camelCase 切词。当中文 fieldLabel（如 "维护周期(天)"）进入该函数时，`split("[._\\-\\s]+")` 不会切分中文字符，`CAMEL_PART_PATTERN` 不会匹配中文——导致中文 fieldLabel 只有完整字符串别名，没有子串别名。

**增强方式**：新增 `addChineseNgramAliases(aliases, value)` 方法，对包含 CJK 字符的文本生成 bigram + trigram 别名。例如 "维护周期" → `["维护", "护周", "周期", "维护周", "护周期", "维护周期"]`。

**工程约束**：只对 2-8 个中文字符的片段做 N-gram。跳过单字（噪声太高）。跳过 8+ 字长文本（可能是句子而非字段名）。

#### 来源 4：编译阶段 LLM 字段说明生成（强力但需谨慎）

**适用范围**：YAML/JSON 等英文字段名、无表头的结构化源文件。

**方案**：在 `FactCardTerminalUnitMaterializer` 中，对于无法从来源 1-3 获得中文别名的 terminal unit，调用 compile-stage LLM 生成中文 field alias/description。

**LLM 输入（只允许）**：
- 源文件原文（source file content）
- 字段路径（keyPath）
- 父级路径（parentPath）
- 同 parent 下 sibling 字段的 key 和 value
- 字段值形态（valueType）

**LLM 输出**：`{"fieldAliases": ["中文别名1", "中文别名2"], "fieldDescription": "短描述"}`

**LLM 禁止输入**：
- eval 题面、case id、expected answer
- query 日志
- 文件名、题集名
- 任何非源文件内容的信息

**工程约束**：
- LLM 调用必须在 compile job 内、Reviewer 之前执行，受现有 fail-closed 安全底座保护
- LLM 调用失败时静默降级到来源 1-3 的纯算法别名，不阻塞 compile
- LLM 生成的别名必须经过长度和数量上限校验（单条 alias ≤ 20 字符，总数 ≤ 20 条）
- 建议使用与 Writer/Reviewer 相同的 LLM provider 路由

#### 来源 5：通用结构规则增强 fieldDescription（已有基础）

当前 `fieldDescription` = `"parentPath: X; field: Y; valueType: Z"`，纯结构化。可以安全增强：

- 当 parentPath 末段包含数组索引（如 `equipment_types[1]`），在 description 中增加 "第2项" 等通用序号表达
- 当 valueType=number 时，增加 "数值" 标记
- 当 valueType=version 时，增加 "版本号" 标记
- 当有 sibling descriptor 值时，增加上下文短句（如 "精密仪器 的 max_borrow_days"）

### 4.2 禁止来源（红线）

| 禁止来源 | 示例 | 红线类型 |
|---|---|---|
| eval 题面 | "精密仪器的单次最长借用天数是多少" → alias "最长借用天数" | eval 污染 |
| expected answer | "7" → alias "7天" | eval 污染 |
| case id | FQ3 → 特判 equipment_types[1] | 业务特判 |
| 文件名特判 | "equipment-borrowing-policy.yaml" → "设备借用政策" | 文件名语义化 |
| query 日志 | 用户真实查询词 → alias | eval 污染 |
| 业务词硬编码映射 | Java `if (key.equals("deposit_amount")) aliases.add("押金")` | 硬编码红线 |
| 中英文字典 | `Map.of("deposit", "押金", "borrow", "借用")` | 硬编码红线 |

**特别强调**：不得把 "押金→deposit_amount"、"逾期→late_fee_per_day"、"最长借用天数→max_borrow_days" 这类映射写成任何落地规则。只能通过来源 1-4 的通用方案间接覆盖。

---

## 5. 方案设计

### 5.1 推荐方案：分层增强 Materializer

```
Phase 1C = Layer 1 (算法增强) + Layer 2 (结构上下文) + Layer 3 (LLM 可选)
```

#### Layer 1: 算法增强（零风险，必须做）

**修改点**：`FactCardTerminalUnitMaterializer.buildFieldAliases()` 和 `addSplitAliases()`

**变更**：

1. 新增 `addChineseNgramAliases(aliases, value)` 方法：
   - 检测 value 是否包含 CJK 字符（Unicode 块 `一-鿿`、`㐀-䶿`）
   - 若包含，对纯中文片段（去除标点、括号、数字、英文字母）做 bigram + trigram
   - 只对长度 2-8 个中文字符的片段做 N-gram
   - 跳过单字 N-gram（噪声）
   - 每个 N-gram 作为独立 alias 加入

2. 在 `buildFieldAliases()` 中对以下字段调用 `addChineseNgramAliases`：
   - `fieldLabel`（最重要——覆盖表格类中文列头）
   - `parentPath` 末段（如 `equipment_types[1]` 中的路径片段）
   - `keyPath` 各段（但跳过纯数字索引如 `[1]`）

3. `fieldLabel` 保持现有逻辑不变（`= terminalKey = item.key` 或 `lastPathSegment(item.keyPath)`）。

**预期效果**：
- 表格类（XLSX/CSV）：中文列头 "维护周期(天)" → alias 包含 "维护周期"、"维护"、"周期" → 中文 query token 能匹配
- YAML 类：英文字段名不变，此层无增量收益（进入 Layer 2/3）

#### Layer 2: 结构上下文增强（低风险，建议做）

**修改点**：`FactCardTerminalUnitMaterializer.materialize()` 和 `materializeItem()`

**变更**：

1. 在 `materialize()` 中，遍历 `items[]` 时，按 `parentPath` 分组收集 sibling 信息：
   - 收集同一 `parentPath` 下所有 item 的 `key` 和 `value`
   - 识别中文 string value 字段（valueType=string、value 包含 CJK 字符、长度 2-20 字符）

2. 在 `materializeItem()` 中：
   - `buildFieldDescription()` 增加 sibling 上下文：若同 parentPath 下存在中文 descriptor 值（如 `type="精密仪器"`），追加 `"context: <descriptor值>"` 
   - `buildFieldAliases()` 可选地加入 sibling descriptor 值作为上下文别名（需控制数量，每个 parentPath 最多取 2 个 descriptor 值）

3. **安全约束**：
   - 只能使用同一 `factCardRecord` 内的 sibling item
   - descriptor 识别规则：valueType=string、value 长度 2-20、value 包含 CJK 字符、对应的 key 不是当前 target key
   - 不跨 fact card、不跨 source file

**预期效果**：
- `equipment_types[1].max_borrow_days = 7` 的 fieldDescription 变为 `"parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器"`
- "精密仪器" 出现在 fieldDescription 中，进入 ftsText，可被 PostgreSQL FTS 检索
- 但不能解决 "最长借用天数" → "max_borrow_days" 的语义鸿沟（进入 Layer 3）

#### Layer 3: 编译阶段 LLM 字段别名生成（中风险，可选，视 Layer 1+2 效果决定）

**触发条件**：仅当 Layer 1+2 验证后，YAML 类 source 的 terminal unit 仍无法进入 topK 时启用。

**修改点**：新增 `FactCardTerminalUnitFieldAliasEnricher` + Materializer 集成

**变更**：

1. 新增 `FactCardTerminalUnitFieldAliasEnricher` @Service：
   - 输入：`SourceFileRecord`（源文件内容）+ `List<FieldContext>`（字段路径、parentPath、sibling descriptor、valueType）
   - 输出：`Map<String, FieldAliasResult>`（terminalKey → 中文别名列表 + 短描述）
   - 内部调用 LLM（复用现有 `compile.writer` provider 路由）
   - LLM 异常时返回空 map，不阻塞 compile

2. LLM Prompt 设计原则：
   - **只描述任务**：为英文字段名生成中文检索别名
   - **只引用输入数据**：源文件内容、字段路径、父级上下文
   - **不引用任何外部知识**：不提示业务域、不提示文档类型
   - **不预设答案**：不要求 LLM 输出字段值，只要求输出字段名的中文表达

3. `FactCardTerminalUnitMaterializer` 在 `buildFieldAliases()` 之后、`buildFtsText()` 之前调用 Enricher：
   - 将 LLM 生成的别名追加到 `fieldAliases`
   - 将 LLM 生成的描述合并到 `fieldDescription`
   - LLM 返回空时保持 Layer 1+2 的 alias 不变

4. **安全底座**：
   - LLM 调用超时 5s，超时降级
   - LLM 响应解析失败降级
   - 生成的 alias 长度 ≤ 20 字符、数量 ≤ 20 条（截断）
   - 生成的 alias 不得包含 eval 题面特征词（post-hoc redline 扫描，但那太晚——应在 prompt 中约束 "只使用源文件中出现的概念和表述，不要引入源文件中未出现的术语"）

**预期效果**：
- `max_borrow_days` → LLM 基于源文件上下文生成 `["最长借用天数", "最大借用天数", "借用期限"]`
- `deposit_amount` → `["押金金额", "押金", "保证金"]`
- `late_fee_per_day` → `["逾期费用", "每天罚金", "滞纳金"]`

**风险**：
- LLM 生成质量不稳定，可能产生无关别名
- 增加 compile 耗时和成本
- Prompt 设计不当可能引入 eval 语言

### 5.2 不推荐方案

| 方案 | 拒绝理由 |
|---|---|
| **Query 侧中英文翻译** | 需要在查询时维护翻译词典或调用翻译 API，增加延迟和成本。翻译结果不可控，容易引入 eval 污染。编译时生成可审计、可重建。 |
| **Hardcoded 字段映射表** | 直接违反禁止事项：不准在 Java 主链硬编码中文字段语义。且无法泛化到新 source。 |
| **修改源文件添加中文注释** | 不可泛化——无法要求所有用户都在 YAML 中写中文注释。属于修改 eval 资料。 |
| **Embedding/向量替代 lexical 匹配** | 属于 Phase 2 范围（向量检索）。Phase 1C 聚焦 FTS 可检索性。如果 lexical + LLM alias 仍不足，再进入 Phase 2。 |
| **在 FactCardGenerationService 中生成别名** | Blast radius 太大：需要修改 LLM prompt、card schema、解析逻辑。Fact card 是卡级粒度，不是字段级粒度。 |
| **新增 sysnonyms.yaml 配置** | 配置化比硬编码好，但仍需要人工维护中英文映射表，无法泛化。且 `config/synonyms.yaml` 当前不存在，新增配置项需要额外审计。 |

---

## 6. Schema 与数据重建

### 6.1 Schema 变更

**不需要**。`fact_card_terminal_units` 表的目标列均已存在：

| 列 | 类型 | 用途 | 是否需变更 |
|---|---|---|---|
| `field_label` | `TEXT` | 字段展示名 | 否 |
| `field_aliases_json` | `JSONB` | 字段别名数组 | 否 |
| `field_description` | `TEXT` | 字段上下文描述 | 否 |
| `fts_text` | `TEXT` | FTS 检索文本 | 否 |
| `metadata_json` | `JSONB` | 查询命中透传 metadata | 否 |

变更仅限于 Materializer **填充这些列的逻辑**，不涉及 DDL。

### 6.2 清库重建

**需要**。理由：

1. 旧 terminal unit 的 `field_aliases_json`、`field_description`、`fts_text` 是按旧算法生成的，不包含中文别名
2. 这些字段在 compile 时一次性计算并持久化，没有运行时动态补充机制
3. 项目约定：DDL/索引变化后显式执行 `reset-lattice-schema.sh` + 重新 compile

**执行顺序**（由 agentA 实现后、agentD 验证时执行）：
1. `./scripts/reset-lattice-schema.sh` — 清库重建 schema
2. 重新导入 5 份 fresh eval 资料
3. 触发 compile（使用新 Materializer 逻辑）
4. 验证 terminal unit 的 fieldAliases 包含中文 N-gram

---

## 7. 最小可归因实现范围

### 7.1 允许修改文件

| 文件 | 修改内容 | 层级 |
|---|---|---|
| `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java` | Layer 1: 新增 `addChineseNgramAliases` + `buildFieldAliases` 中调用；Layer 2: `buildFieldDescription` 增加 sibling 上下文；`materialize()` 中收集 sibling info | 核心 |
| `src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializerTests.java`（如需新增） | 测试中文 N-gram 切词、sibling 上下文收集、LLM alias 合并 | 测试 |

**如果启用 Layer 3（LLM）**，额外增加：

| 文件 | 修改内容 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java` | 新增 LLM 别名生成服务 |
| `src/main/resources/prompts/compiler/field-alias-enricher.md`（或同类 prompt 文件） | LLM prompt 外置 |

### 7.2 禁止修改文件

| 文件/区域 | 原因 |
|---|---|
| `FactCardTerminalUnitIntentReranker.java` | Phase 1B 已有，Phase 1C 不改它 |
| `FactCardTerminalUnitFtsSearchService.java` | Phase 1B 已有，Phase 1C 不改它 |
| `QuerySemanticRules.java` | Phase 1B 已有，Phase 1C 不改它 |
| `lattice-query-semantic.yml` | Phase 1B 已有，Phase 1C 不改它 |
| `FactCardGenerationService.java` | 不改变 fact card 生成逻辑 |
| `src/main/resources/db/schema.sql` | 不需要 DDL 变更 |
| `AnswerGeneration*` / fallback 相关 | 不继续叠 query fallback gate |
| `QueryResponseCitation*` | Phase 1 不改 citation |
| `scripts/scan-redline.sh` / allowlist | 禁止通过改扫描规则过门禁 |
| 题集、资料包、标准答案、hidden eval | 禁止读取、禁止写入 |
| `docs/模型绑定配置参考.md` | 私有配置，禁止提交 |

### 7.3 实现顺序

```
Step 1: Layer 1（中文 N-gram 切词）→ 编译 → 验证
Step 2: Layer 2（sibling 上下文）→ 编译 → 验证
Step 3: 如果 Layer 1+2 后 YAML 类 target unit 仍未进入 topK → 启用 Layer 3（LLM）
```

每步验证通过后再进入下一步，保持每轮只有一个可归因变量。

---

## 8. 验证方案

### 8.1 不污染 Hidden Eval

| 检查项 | 方法 |
|---|---|
| Materializer 代码审计 | 确认 alias 生成路径只读取 `FactCardRecord.itemsJson` + `keyPath` + `parentPath` + `pathSegments` + `valueText`，不读取 eval 题面、case id、expected answer |
| LLM prompt 审计（若启用 Layer 3） | 确认 prompt 只引用源文件内容，不包含业务域提示、不预设字段语义 |
| redline 扫描 | `bash scripts/scan-redline.sh special_cases_report.md`，要求 `BLOCKER=0` |
| Diff 人工审查 | 检查是否有中文字符串常量匹配 eval 题面关键词 |
| 测试数据审计 | 单元测试使用 synthetic fixtures，不复刻 eval 题面 |

### 8.2 验证 FQ3/FQ4/FQ6/FG1/FG2 目标 Terminal Unit 进入 topK

**验证步骤**（由 agentD 执行）：

1. Clean schema reset + 重新编译 5 份资料
2. 确认 `fact_card_terminal_units` 表中目标 unit 的 `field_aliases_json` 包含中文别名
3. 对每道题调用 query API，检查 `fact_card_terminal_fts` channel 的返回 hits：
   - FQ3: `equipment_types[1].max_borrow_days = 7` 是否在 topK 中
   - FQ4: `equipment_types[0].deposit_amount = 100` 是否在 topK 中（注意同时验证 `equipment_types[2].deposit_amount = 1000` 也在候选）
   - FQ6: `borrowing_system.version = v2.3.1` 是否在 topK 中
   - FG1: `equipment_types[1].late_fee_per_day = 20` 是否在 topK 中
   - FG2: `borrowing_system.max_concurrent_requests = 50` 是否在 topK 中
4. 对每道题验证目标 unit 排在其 sibling（同 parentPath 的其他 unit）之前
5. 验证 answer claim 是否使用了目标 unit 的值

**Gate 判定**：
- **PASS**：目标 unit 进入 topK（topK ≤ 5）且排在 sibling 之前
- **PARTIAL**：目标 unit 进入 topK 但排在某个 sibling 之后
- **FAIL**：目标 unit 未进入 topK

### 8.3 保护回归

| 回归项 | 验证内容 |
|---|---|
| FQ7（B 级化学品存储条件） | 表格类 XLSX 中文列头别名不应破坏已有 PASS |
| FQ11（A 级维护设备） | CSV 中文列头别名不应破坏已有 PASS |
| Q6 terminal field alias | `spec.containers[0].readinessProbe.tcpSocket.port = 8080` 保护——确认新增中文别名不引入 sibling 抢占回归 |
| S2 chunk/anchor identity | 确认 terminal unit channel 变更不影响 article chunk FTS/chunk vector 的独立身份 |
| 非目标 fresh eval 题 | 全部 19 题回归，确认无新增 FAIL |
| Phase 1B reranker 13 个单元测试 | 确认中文 alias 不破坏英文 synthetic 场景的测试预期 |

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解 | 回滚 |
|---|---|---|---|
| 中文 N-gram 噪声 | 过多短中文 alias（单字、双字）污染 FTS 检索 | 只对 2-8 字中文片段做 bigram/trigram，跳过单字 | 移除中文 N-gram 生成逻辑 |
| sibling descriptor 误选 | 把 sibling 的 value 作为 alias 后，query 匹配 sibling 反而更强 | 控制 descriptor alias 数量（每个 parentPath ≤ 2 个），不加入 fieldAliases，只加入 fieldDescription | 移除 sibling context 逻辑 |
| LLM 别名质量不稳定 | 生成无关或错误的中文别名 | 长度/数量上限裁剪，降级到 Layer 1+2 | 关闭 LLM Enricher |
| LLM 增加 compile 耗时 | 每个 source file 增加 2-5s | 可选启用，默认关闭；超时 5s 降级 | 关闭 LLM Enricher |
| eval 语言泄漏进 alias | LLM 生成与 eval 题面相同的中文表述 | Prompt 约束"只使用源文件中出现的概念"；redline 扫描 | 清库重建，关闭 LLM Enricher |
| hidden eval 污染 | 泛化指标虚高 | alias 来源审计、redline、diff 人审 | 删除污染规则，重建索引 |

---

## 10. 对 agentA 的最小实现提示词草案

```
你是 agentA，本轮任务：实现 Terminal Unit Phase 1C Layer 1 + Layer 2。

## 目标
在 FactCardTerminalUnitMaterializer 中增强 fieldAliases 和 fieldDescription 的生成逻辑，
让中文 query token 能匹配到 terminal unit 的字段语义。

## 允许修改文件
- src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializer.java
- src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitMaterializerTests.java（如需新增）

## 禁止修改文件
- FactCardTerminalUnitIntentReranker.java / FactCardTerminalUnitFtsSearchService.java / QuerySemanticRules.java（Phase 1B 代码，保留不动）
- FactCardGenerationService.java / schema.sql / lattice-query-semantic.yml
- 任何 query/answer/fallback/citation 相关文件

## Layer 1 实现要求
1. 新增 addChineseNgramAliases(Set<String> aliases, String value) 方法：
   - 检测 value 是否包含 CJK 字符
   - 提取纯中文片段（去除标点、括号、数字、英文字母）
   - 对每个 2-8 字中文片段生成 bigram + trigram
   - 所有 N-gram 加入 aliases
2. 在 buildFieldAliases() 中，对 fieldLabel 调用 addChineseNgramAliases
3. 处理括号内容：如 "维护周期(天)" → 提取 "维护周期" 和 "天" 两部分分别做 N-gram

## Layer 2 实现要求
1. 在 materialize() 中，遍历 items[] 时收集 sibling 信息：
   - 按 parentPath 分组
   - 记录每个 item 的 key 和 value
2. 在 materializeItem() 的 buildFieldDescription() 中：
   - 查找同 parentPath 下的中文 string value（valueType=string、含 CJK、2-20 字符）
   - 若找到，在 fieldDescription 中追加 "context: <descriptor值>"
3. 不要将 sibling descriptor 加入 fieldAliases（避免 sibling 匹配过强）

## 禁止事项
- 禁止硬编码任何中英文映射（如 "押金" → "deposit"）
- 禁止读取 eval 题面、case id、expected answer
- 禁止使用文件名、路径做语义判断
- 禁止修改 Phase 1B 代码

## 测试要求
- 测试中文 N-gram 切词：验证 "维护周期" → aliases 包含 "维护"、"护周"、"周期"、"维护周"、"护周期"、"维护周期"
- 测试 sibling 上下文：验证同 parentPath 的 descriptor 值进入 fieldDescription
- 测试非中文 fieldLabel 不变：英文 key 的 alias 生成逻辑不退化
- 测试数据必须使用 synthetic fixtures，不复刻 eval 题面

## 输出
- *_fix_result_report.md（包含 redline、定向测试、全量 mvn test 结果）
```

---

## 11. 对 agentD 的验证方案草案

```
你是 agentD，本轮任务：验证 Terminal Unit Phase 1C 效果。

## 前置条件
- agentA 已完成 Phase 1C Layer 1+2 实现
- redline BLOCKER=0，全量 mvn test 通过

## 验证步骤
1. 清库重建：./scripts/reset-lattice-schema.sh
2. 重新导入 fresh eval 5 份资料（跳过 PDF）
3. 触发 compile
4. 检查 fact_card_terminal_units 表中目标 unit 的 field_aliases_json 是否包含中文别名
5. 对 5 道结构化 terminal value 题运行 query，检查 terminal unit channel 命中：
   - FQ3: 目标 unit equipment_types[1].max_borrow_days=7 是否进入 topK
   - FQ4: 目标 unit equipment_types[0].deposit_amount=100 是否进入 topK
   - FQ6: 目标 unit borrowing_system.version=v2.3.1 是否进入 topK
   - FG1: 目标 unit equipment_types[1].late_fee_per_day=20 是否进入 topK
   - FG2: 目标 unit borrowing_system.max_concurrent_requests=50 是否进入 topK
6. 全量 19 题 fresh eval 回归
7. 保护回归：FQ7/FQ11（已有 PASS）、Q6 terminal field alias、S2 chunk identity

## Gate 判定
- 目标 unit 进入 topK ≤ 5 且排在 sibling 之前 → PASS
- 目标 unit 进入 topK 但排在某个 sibling 之后 → PARTIAL
- 目标 unit 未进入 topK → FAIL

## 输出
- *_verification_report.md
```

---

## 12. 结论汇总

| 问题 | 答案 |
|---|---|
| 1. Phase 1B 代码是否应回退？ | **不提交，也不回退**。保留作为 infrastructure，与 Phase 1C 一起提交。 |
| 2. Phase 1C 应放在哪层？ | **FactCardTerminalUnitMaterializer**（compile/index 层，fieldAliases 唯一生成点） |
| 3. 安全来源 | **来源 1** 表头/列名（已有）、**来源 2** 父级 descriptor（结构规则）、**来源 3** 中文 N-gram 切词（纯算法）、**来源 4** 编译阶段 LLM（可选） |
| 4. 是否需要 schema 变更？ | **不需要**。所有目标列已存在。 |
| 5. 是否需要清库重建？ | **需要**。alias 在 compile 时持久化，必须重建 terminal units。 |
| 6. 最小可归因实现范围 | **Layer 1（中文 N-gram）+ Layer 2（sibling 上下文）**，仅修改 1 个 Java 文件 + 可选 1 个测试文件。 |
| 7. 如何验证不污染 hidden eval？ | alias 来源审计 + redline 扫描 + diff 人审 + 测试 synthetic fixtures |
| 8. 如何验证目标 unit 进入 topK？ | agentD 服务级验证：5 道结构化 terminal value 题 + 全量 19 题回归 + 保护回归 |
| 9. 是否必须先回退 Phase 1B 再做 Phase 1C？ | **否**。Phase 1B（消费者）和 Phase 1C（生产者）互补，保留不动。 |

---

## 附录 A: 当前 fieldAliases 生成逻辑（Phase 1A 基线）

以 `equipment_types[1].max_borrow_days = 7` 为例，当前生成的 `fieldAliases`：

```
["max_borrow_days", "max_borrow_days", "max borrow days", "max-borrow-days",
 "equipment_types[1].max_borrow_days", "equipment_types[1].max_borrow_days",
 "equipment types[1] max borrow days", "equipment-types[1]-max-borrow-days",
 "equipment_types[1]", "equipment_types[1]", "equipment types[1]",
 "equipment_types[1] max_borrow_days", "equipment_types[1] max_borrow_days",
 "equipment types[1] max borrow days", "equipment-types[1]-max-borrow-days",
 "equipment_types[1]", "equipment", "types[1]", "max_borrow_days",
 "max", "borrow", "days", "max", "borrow", "days",
 "equipment", "types[1]", "max", "borrow", "days",
 "equipment", "types", "1", "max", "borrow", "days"]
```

**全部为英文/数字**。中文 query "精密仪器的单次最长借用天数是多少" 提取的 token（"精密仪器"、"最长借用天数"、"借用天数"、"借用"、"天数"）**零命中**。

## 附录 B: Phase 1C 后期望效果（Layer 1+2）

以同一 unit 为例，Layer 1+2 后 fieldDescription 变化：

**Before:**
```
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number
```

**After:**
```
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器
```

"精密仪器" 进入 `ftsText` → 可被 PostgreSQL FTS `tsvector` 检索 → 中文 query 中的 "精密仪器" token 能匹配到该 terminal unit → Reranker 的 `fieldMatchCount` 增加。

但仍不能解决 "最长借用天数" → "max_borrow_days" 的匹配。这一步需要 Layer 3（LLM）或 Phase 2（向量检索）。

## 附录 C: 各 source 类型受益分析

| Source 类型 | 示例 | Layer 1 受益 | Layer 2 受益 | 需要 Layer 3 |
|---|---|---|---|---|
| XLSX/CSV（中文列头） | chemical-storage-grading.xlsx | **高** — 中文列头 N-gram 直接匹配 query token | 中 — 行标识符（化学品名称）提供上下文 | 否 |
| YAML（英文 key + 中文 value） | equipment-borrowing-policy.yaml | 低 — fieldLabel 仍是英文 | **高** — sibling descriptor（type="精密仪器"）提供上下文 | **是**（如果 Layer 1+2 后仍不足） |
| Markdown（中文正文） | lab-safety-management-handbook.md | 中 — 少量 terminal unit 来自表格 | 低 — 非结构化正文 sibling 少 | 可能 |
| PDF | lab-emergency-response-procedures.pdf | 低 — 编译失败，无 terminal unit | 低 | 不适用 |

**关键判断**：Layer 1 主要解决 XLSX/CSV 表格类 source 的中文检索问题（已有 PASS 的 FQ7/FQ11 来自此类）。Layer 2 主要解决 YAML 类 source 的上下文匹配（"精密仪器" 匹配）。YAML 类英文字段名→中文语义的鸿沟（"max_borrow_days" → "最长借用天数"）只能通过 Layer 3 LLM 或 Phase 2 向量检索解决。

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮新增报告：`terminal_unit_phase1c_field_alias_materialization_design_report.md`
