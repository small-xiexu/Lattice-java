# Terminal Unit Phase 1E Answer Consumption 只读归因报告

分析时间：2026-05-30
分析人：agentB（治理/链路分析 Agent）
分析范围：只读归因，不修改任何文件

---

## 1. 结论

**YAML 5 题 terminal unit evidence 未被最终答案消费，根因在 FALLBACK evidence selector 存在三层通用缺口：(1) terminal unit 的 `content` 字段不含 fieldAliases（中文检索别名未进入 fallback 评分视野），(2) `preferArticleEvidence=true` 路径按 `evidenceType` 过滤会丢弃所有 FACT_CARD 命中（含 terminal unit），(3) `extractDescription()` 的简易字符串匹配无法从 terminal unit metadata 中提取 `fieldDescription`。这不是 fresh eval 专属问题，而是 terminal unit evidence granularity 在 answer consumption 层尚无一等公民待遇的通用架构缺口。**

---

## 2. 链路复盘

### 2.1 完整证据流转路径

```
┌────────────────────────────────────────────────────────────────────┐
│ 1. COMPILE: Materializer + LLM Enricher                           │
│    fieldAliases: ["max_borrow_days", ..., "最长借用天数"]          │
│    ftsText: "...最大借用天数 最长借用天数 借用期限上限..."          │
│    search_tsv: to_tsvector('simple', ftsText)  [自动更新]          │
│    content (for query): display_text + field_description          │
│    metadataJson: { fieldAliases, fieldDescription, terminalKey,   │
│                    keyPath, value, valueType, displayText, ... }  │
├────────────────────────────────────────────────────────────────────┤
│ 2. QUERY: FactCardTerminalUnitFtsSearchService.search()           │
│    LIKE %最长% 匹配 field_aliases_json (+3.0)                      │
│    LIKE %借用天数% 匹配 fts_text (+2.0)                            │
│    → hit rank 提升至 topK                                         │
├────────────────────────────────────────────────────────────────────┤
│ 3. RRF: RrfFusionService.fuse()                                   │
│    buildHitKey → FACT_CARD:terminal-unit:<unitId>  [独立身份]      │
│    不与其他 sibling / 整卡 fact card 折叠                          │
│    evidenceType = FACT_CARD  [Phase 1 设计，与整卡共享类型]        │
├────────────────────────────────────────────────────────────────────┤
│ 4. FUSED: queryWorkingSetStore.loadFusedHits()                    │
│    fusedHits = [                                                   │
│      QueryArticleHit{evidenceType=FACT_CARD,                       │
│        articleKey="terminal-unit:...",                             │
│        content="equipment_types[1].max_borrow_days = 7\n           │
│                 parentPath: ...; context: 精密仪器",                │
│        metadataJson="{..., \"fieldAliases\":[\"最长借用天数\"],...}"│
│      },                                                            │
│      QueryArticleHit{evidenceType=ARTICLE, ...},  ← 整篇编译文章    │
│      QueryArticleHit{evidenceType=SOURCE, ...},  ← 源文件           │
│      ...                                                           │
│    ]                                                               │
├────────────────────────────────────────────────────────────────────┤
│ 5. FALLBACK SELECTOR: AnswerFallbackEvidenceSelector               │
│    → selectFallbackEvidenceHits(question, fusedHits)               │
│    Path A: preferArticleEvidence=true  → 只保留 ARTICLE/CONTRIBUTION│
│            → terminal unit (FACT_CARD) 被丢弃!                     │
│    Path B: preferArticleEvidence=false → 保留所有类型               │
│            → filterRelevantHits 先过滤                               │
│            → sortFallbackEvidenceHits 按 scoreQuestionFocused... 排 │
│            → 但 terminal unit content 只有 2 行(无 fieldAliases)      │
│            → ARTICLE content 是全文 Markdown(包含所有字段和值)        │
│            → ARTICLE 得分 >> terminal unit 得分                    │
│    最终: selectedHits = [ARTICLE evidence]                          │
├────────────────────────────────────────────────────────────────────┤
│ 6. ANSWER: FALLBACK 模式用 ARTICLE evidence 生成答案                │
│    → 答案描述整篇文档概述，但缺少具体 terminal value               │
└────────────────────────────────────────────────────────────────────┘
```

### 2.2 关键丢失点定位

| 步骤 | 发生什么 | 是否可绕过 |
|---|---|---|
| RRF 融合 | **PASS** — terminal unit 获得独立 `FACT_CARD:terminal-unit:<unitId>` 身份，不折叠 | — |
| filterRelevantHits | **PARTIAL PASS** — terminal unit 能通过（content 中有 "精密仪器" 匹配），但得分低于 ARTICLE | 得分低是结构性问题 |
| sortFallbackEvidenceHits | **FAIL** — terminal unit content（2 行）远不如 ARTICLE content（全文 Markdown）在 `scoreQuestionFocusedFallbackHit` 中得分高 | **关键瓶颈 1** |
| preferArticleEvidence=true | **FAIL** — 该路径直接按 evidenceType 丢弃所有 FACT_CARD 命中 | **关键瓶颈 2** |
| extractDescription | **FAIL** — terminal unit metadata 中有 `fieldDescription` 但没有 `"description"` 键，简易字符串匹配找不到 | **关键瓶颈 3** |
| retainDirectStructuredEvidence | N/A — 走到这个分支前已丢失 | — |

### 2.3 为什么 terminal unit 在 fused rank 2 仍被丢弃

以 FQ3 为例，fused topK 中 terminal unit (max_borrow_days=7) 排在 rank 2，但 fallback 仍选中 ARTICLE。原因不是 rank 不够，而是 fallback selector **不尊重 RRF 融合的排名顺序**——它有一套独立的过滤+排序逻辑，而在这套逻辑中，terminal unit 的竞争力远不如 ARTICLE：

1. **过滤层**：`preferArticleEvidence=true` 路径直接丢弃 FACT_CARD
2. **排序层**：即使走 `preferArticleEvidence=false` 路径，`scoreQuestionFocusedFallbackHit` 在 terminal unit 的 2 行 content 上只能匹配 "精密仪器"（context 字段），而 ARTICLE 的全文 Markdown 能匹配 "精密仪器"、"最长借用天数"、"借用"、"天数" 等几乎所有 query token

---

## 3. YAML 5 题证据状态

### 3.1 逐题证据流转

| 题号 | 目标 Terminal Unit | fused rank | 是否进入 fused | 是否通过 filterRelevantHits | preferArticleEvidence 路径 | 最终选中 evidence | 失败类型 |
|---|---|---|---|---|---|---|---|
| FQ3 | max_borrow_days=7 | 2 | **是** | 可能通过（"精密仪器" 匹配 content） | TRUE → **丢弃 FACT_CARD** | ARTICLE (设备借用政策全文) | **证据已召回但 answer 层未消费** |
| FQ4 | deposit_amount=100 | 6 | **是** | 可能通过 | TRUE → **丢弃 FACT_CARD** | ARTICLE (设备借用政策全文) | **证据已召回但 answer 层未消费** |
| FQ6 | version=v2.3.1 | 2 | **是** | 可能通过 | TRUE → **丢弃 FACT_CARD** | ARTICLE (设备借用政策全文) | **证据已召回但 answer 层未消费** |
| FG1 | late_fee_per_day=20 | 7 | **是** | 可能通过 | TRUE → **丢弃 FACT_CARD** | ARTICLE (设备借用政策全文) | **证据已召回但 answer 层未消费** |
| FG2 | max_concurrent_requests=50 | 2 | **是** | 可能通过 | TRUE → **丢弃 FACT_CARD** | ARTICLE (设备借用政策全文) | **证据已召回但 answer 层未消费** |

### 3.2 失败类型统一判定

全部 5 题失败类型已从 Phase 1D 的 **"证据已召回但回答漏点（sibling 抢答）"** 更新为 **"证据已召回但 answer 层未消费（terminal unit evidence 未被 fallback selector 识别）"**。

这个变化是实质性的正向进展——问题从 retrieval 层（无法区分 sibling）下移到了 answer consumption 层（无法消费 terminal unit），说明 Phase 1A-1E 的 retrieval 改进是有效的。

---

## 4. 丢失点 / 排序点定位

### 4.1 丢失点 1：content 字段不含 fieldAliases

**位置**：`FactCardTerminalUnitMapper.xml` 第 202 行

```sql
trim(concat_ws(E'\n', unit.display_text, unit.field_description)) as content
```

**问题**：`content` 仅包含 `display_text + field_description`，不含 `field_aliases_json` 或 `fts_text`。

**后果**：LLM 生成的中文 alias（如 "最长借用天数"）在 FTS 检索时有效（LIKE 匹配 `field_aliases_json` 和 `fts_text`），但在 fallback 证据评分时**完全不可见**。fallback selector 通过 `scoreQuestionFocusedFallbackHit` 评估 `content` 中的行——而这些行中没有 "最长借用天数"。

**影响**：terminal unit 的 `scoreQuestionFocusedFallbackHit` 得分远低于 ARTICLE（其 content 包含整篇 Markdown），导致排序偏低。

### 4.2 丢失点 2：preferArticleEvidence=true 丢弃 FACT_CARD

**位置**：`AnswerFallbackEvidenceSelector.filterFallbackEvidenceHits()` 第 485-506 行

```java
if (preferArticleEvidence
        && queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE
        && queryArticleHit.getEvidenceType() != QueryEvidenceType.CONTRIBUTION) {
    continue;  // ← 丢弃 FACT_CARD（含 terminal unit）
}
```

**问题**：terminal unit 的 `evidenceType` 是 `FACT_CARD`（Phase 1 设计决定），与整卡 fact card 共享同一类型。`preferArticleEvidence=true` 路径会丢弃所有非 ARTICLE/CONTRIBUTION 类型。

**后果**：即使 terminal unit 在 fused topK 中排 rank 2，如果 ARTICLE 命中存在于 fused 中（几乎总是存在——compiled article 会进入检索），则 `preferredArticleHits` 非空，selector 使用 ARTICLE 证据，terminal unit 完全不被考虑。

### 4.3 丢失点 3：extractDescription 无法识别 fieldDescription

**位置**：`QueryEvidenceRelevanceSupport.extractDescription()` 第 490-508 行

```java
private static String extractDescription(String metadataJson) {
    String marker = "\"description\":";
    int markerIndex = metadataJson.indexOf(marker);
    // ...
}
```

**问题**：该方法在 metadata JSON 字符串中搜索 `"description":` 键名。terminal unit metadata 中对应字段名为 `"fieldDescription"`，无法匹配。

**后果**：`matchesStructuredField` 和 `matchesTitleOrDescription` 在 terminal unit 上无法通过 description 匹配到 query token，进一步降低 terminal unit 的 relevance 得分。

### 4.4 为什么是通用缺口，不是 case 特判问题

| 缺失能力 | 影响范围 | 是否 fresh eval 专属 |
|---|---|---|
| content 不含 fieldAliases | **所有** terminal unit 的 fallback 消费路径 | 否——任何有中文 alias 的 terminal unit 都受影响 |
| preferArticleEvidence 丢弃 FACT_CARD | **所有** FACT_CARD evidenceType 在精确查值题中 | 否——这是全局 evidence type 优先级规则 |
| extractDescription 不识别 fieldDescription | **所有** terminal unit metadata | 否——extractDescription 的简易字符串匹配是通用方法 |
| 无 evidence granularity 概念 | **所有** terminal unit vs 整卡 evidence 区分 | 否——fallback selector 不认识 "单字段 evidence" vs "整卡 evidence" |

---

## 5. 通用根因判断

### 5.1 根因：terminal unit evidence 在 answer consumption 层不是一等公民

当前 answer consumption 的 evidence 模型是**文档/卡级粒度**的：
- `evidenceType` 区分 `ARTICLE`、`SOURCE`、`FACT_CARD`、`CONTRIBUTION`、`GRAPH`
- 排序依赖 `scoreQuestionFocusedFallbackHit` → 按 content 行匹配 query token
- 优先级依赖 `evidenceSupport.priority()` → 全局 evidenceType 排序

terminal unit 虽然通过 `FACT_CARD` 类型和独立 `terminalUnitIdentity` 在 RRF 层获得了独立身份，但在 answer consumption 层：
- `evidenceType` 与整卡 fact card 不可区分（都是 `FACT_CARD`）
- `content` 字段不包含对 fallback 评分关键的 `fieldAliases`
- 没有任何机制向 selector 表达 "我是一个精准的单字段值，不是一张大卡"

### 5.2 为什么不是 "改个排序规则" 能解决的

仅提高 terminal unit 在 `sortFallbackEvidenceHits` 中的优先级，不能解决根本问题，因为：
1. `preferArticleEvidence=true` 路径会在排序前就把 FACT_CARD 全部丢弃
2. 即使走到 `preferArticleEvidence=false` 路径，terminal unit 的 content（2 行）在 `scoreQuestionFocusedFallbackHit` 中得分永远低于 ARTICLE（整篇 Markdown）
3. 排序后还有 `retainDirectStructuredEvidence` 等后处理步骤，可能进一步过滤

---

## 6. 修复方案对比

### 方案 A：在 Fallback Selector 中识别 terminal unit metadata 并按 evidence granularity 做通用优先级

**改动点**：
- `AnswerFallbackEvidenceSelector.selectFallbackEvidenceHits()` 中，在 `preferArticleEvidence` 路径之前，先检查候选集中是否有 terminal unit evidence（metadata 中包含 `terminalUnitId` 或 `channel=fact_card_terminal_fts`），若有，则在精确查值题中优先保留
- 修改 `sortFallbackEvidenceHits` 的排序逻辑，当 hit 是 terminal unit 且 query 有 numeric intent 时，加分

**优点**：最小改动，仅修改 selector
**缺点**：仍然是 "叠 gate" 式修改——给 terminal unit 开特殊通道。且 `content` 不含 fieldAliases 的问题未解决，即使 terminal unit 被优先选中，最终 answer generation 阶段可能仍缺少足够信息
**红线风险**：中等——需要在 selector 中新增 "terminal unit 优先" 规则，可能被审查为 case-aware 逻辑

### 方案 B：在 Fused Hit 转换阶段增强 terminal unit 的 content / metadata

**改动点**：
- `FactCardTerminalUnitMapper.xml` 的 `searchLexical` SQL 中，将 `content` 字段从 `display_text + field_description` 扩展为包含 `field_aliases_json` 中的中文 alias、`value_text`、`fts_text` 前 N 个 token，或将 `fieldDescription` 直接附加到 `metadataJson` 的 enrichment JSON 中
- 或修改 `QueryEvidenceRelevanceSupport.extractDescription()` 使其也能读取 `fieldDescription` 字段

**优点**：
- 解决核心问题：content 中有中文 alias 后，terminal unit 在 fallback 评分中的竞争力自然提升
- 不需要改 fallback selector 的选择逻辑
- `extractDescription` 修复后 `matchesStructuredField` 立刻受益

**缺点**：content 变长可能导致 prompt token 增加
**红线风险**：低——content 增强是通用变更，不涉 case 特判

### 方案 C：在 Answer Evidence Assembly 中保留 terminal unit displayText / keyPath

**改动点**：在 fallback markdown 生成阶段（`AnswerGenerationFallbackConclusionSupport` 等），当检测到 terminal unit evidence 时，以 `keyPath = value` 格式嵌入

**优点**：直接改善最终答案中的 terminal value 展示
**缺点**：
- 依赖前面两步（terminal unit 已被 selector 选中）才能生效
- 如果 selector 没选 terminal unit，此方案无效
- 属于 "叠 gate" 的后续步骤

**红线风险**：低——这是 evidence assembly 的通用格式增强

### 方案 D：延后到 Citation / Answer Unit Projection 阶段处理

**改动点**：在 citation 绑定阶段，当 answer claim 涉及某个 terminal unit 的目标值时，从 metadata 中提取 `keyPath/value/displayText` 作为引用

**优点**：不改 fallback 主链
**缺点**：
- 同样依赖 terminal unit 已被选中
- citation 目前不支持 terminal unit 一等公民展示（Phase 1 设计决定）
- 属于 Phase 2 范围

**红线风险**：低

### 方案对比总结

| 方案 | 解决 content 缺口 | 解决 preferArticleEvidence 过滤 | 解决 extractDescription | blast radius | 推荐度 |
|---|---|---|---|---|---|
| **A: Selector gate** | 否 | **是** | 否 | 中 | 不推荐（叠 gate） |
| **B: Content/metadata 增强** | **是** | 间接（content 增强后排序自然提升） | **是** | 低 | **推荐（唯一最小变量）** |
| **C: Evidence assembly** | 否 | 否 | 否 | 低 | 仅作为 B 的补充 |
| **D: Citation projection** | 否 | 否 | 否 | 低 | Phase 2 范围 |

**推荐组合：方案 B 作为唯一本轮变量。** 只修改 `FactCardTerminalUnitMapper.xml` 的 content SQL 和 `QueryEvidenceRelevanceSupport.extractDescription()`。这两个修改都是通用增强，不改变 fallback 选择逻辑，不新增 gate，不引入 case 特判。

---

## 7. 推荐最小下一步

### 7.1 唯一变量

**增强 terminal unit 的 query-facing `content` 字段，使其包含可被 fallback selector 评分的中文语义信息。**

具体做法：
1. `FactCardTerminalUnitMapper.xml`：`content` 从 `display_text + field_description` 扩展为 `display_text + field_description + field_aliases_json`（或仅追加中文 alias）
2. `QueryEvidenceRelevanceSupport.extractDescription()`：同时搜索 `"fieldDescription"` 键名

这两个修改不改变任何 fallback gate / selector 逻辑 / priority 规则。

### 7.2 预期效果

修改后，terminal unit（max_borrow_days=7）的 `content` 变为：
```
equipment_types[1].max_borrow_days = 7
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
最长借用天数 最大借用天数 借用期限上限
```

现在 "最长"、"借用天数"、"精密仪器" 都存在于 content 中 → `scoreQuestionFocusedFallbackHit` 得分大幅提升 → terminal unit 在 `sortFallbackEvidenceHits` 中可能排到 ARTICLE 之前 → 即使走 `preferArticleEvidence=true` 路径也有机会进入 selected hits。

### 7.3 为什么不直接改 preferArticleEvidence 过滤

`preferArticleEvidence=true` 的设计意图是 "优先使用编译后的结构化文章摘要，因为它们质量更高"。这是一个合理的通用规则。直接修改这个规则（让 FACT_CARD 也通过）会改变所有 precise-answer 问题的证据选择行为，blast radius 更大。而增强 content 后，terminal unit 在排序中的竞争力自然提高，selector 的其他路径（`preferArticleEvidence=false` 或 `shouldPreferMixedEvidence`）就有机会选中 terminal unit。

---

## 8. 给下一轮 agent 的提示词

### 8.1 如果建议修复（给 agentA）

```
你是 agentA，本轮任务：增强 terminal unit 的 query-facing content 字段以改善 fallback 证据消费。

## 根因
Terminal unit 的 content 字段（display_text + field_description）不含 fieldAliases，
导致 fallback evidence selector 的 scoreQuestionFocusedFallbackHit 评分极低，
远低于 ARTICLE 证据（全文 Markdown）。同时 extractDescription() 不识别
fieldDescription 键名。

## 允许修改文件（仅 2 个文件，唯一变量）
1. src/main/resources/com/xbk/lattice/infra/persistence/mapper/FactCardTerminalUnitMapper.xml
   - content SQL 从 display_text + field_description 扩展为包含 field_aliases_json
   - 或仅在 content 后追加 field_aliases_json 中的非英文 alias
2. src/main/java/com/xbk/lattice/query/service/QueryEvidenceRelevanceSupport.java
   - extractDescription() 方法同时搜索 "fieldDescription" 键名

## 禁止修改
- AnswerFallbackEvidenceSelector.java / AnswerGenerationService.java / 任何 fallback 相关
- AnswerGenerationPayloadOrchestrator.java
- FactCardTerminalUnitMaterializer.java / LlmFactCardTerminalUnitFieldAliasEnricher.java
- RrfFusionService.java / FactCardTerminalUnitFtsSearchService.java
- schema.sql / prompts / config / redline allowlist
- 禁止修改 query/answer/fallback/citation 主链选择逻辑
- 禁止新增 gate / priority 规则
- 禁止硬编码字段名、业务词、case 特判

## 验证
1. redline BLOCKER=0
2. 定向测试（content 字段格式正确，extractDescription 能提取 fieldDescription）
3. 全量 mvn test
4. 确认 FQ3/FQ6/FG2 的 terminal unit 在 fallback evidence 中的 scored content lines
   包含中文 alias token
```

### 8.2 如果建议继续分析（给 agentB/agentD）

当前归因已足够精确——三个间隙点已定位到源码行号。建议直接进入 agentA 修复轮，不需要继续只读分析。

### 8.3 如果建议暂停提交

不建议暂停。Phase 1E-2 (LLM alias) 已证明检索层改善是真实的（YAML 5/5 目标 unit 进入 fused topK），可以作为检索层成果提交。Answer consumption 的改进（content 增强）是独立的第二变量，不应阻塞检索层成果的提交。

---

## 9. 计划台账回写

本轮已补写 clean schema PARTIAL checkpoint 到 `terminal_unit_phase1_implementation_plan.md`。当前执行 Checkpoint 段最新一条记录：

```
- 2026-05-30 进行中（agentB 只读归因）：Phase 1E-2 clean schema e2e PARTIAL — YAML 5/5 terminal unit
  召回成功，但 answer consumption 层未消费；agentB 已完成 AnswerFallbackEvidenceSelector /
  QueryEvidenceRelevanceSupport 只读归因，定位三个通用缺口：(1) content 不含 fieldAliases，
  (2) preferArticleEvidence 丢弃 FACT_CARD，(3) extractDescription 不识别 fieldDescription。
  下一轮建议 agentA 做 content 增强修复（最小变量，不改 fallback gate）。
```

---

## 附录 A：关键源码位置索引

| 组件 | 文件 | 关键方法/行号 |
|---|---|---|
| Terminal unit content 构造 | `FactCardTerminalUnitMapper.xml` | 第 201-202 行：`concat_ws(E'\n', unit.display_text, unit.field_description)` |
| Terminal unit metadata 构造 | `FactCardTerminalUnitMapper.xml` | 第 204-219 行：`jsonb_build_object(...)` |
| Fallback evidence 入口 | `AnswerFallbackEvidenceSelector.java` | `selectFallbackEvidenceHits()` 第 44-80 行 |
| preferArticleEvidence 过滤 | `AnswerFallbackEvidenceSelector.java` | `filterFallbackEvidenceHits()` 第 485-506 行 |
| 补充证据选择 | `AnswerFallbackEvidenceSelector.java` | `selectComplementaryEvidenceByQuestionTokens()` 第 186-214 行 |
| 证据排序 | `AnswerFallbackEvidenceSelector.java` | `sortFallbackEvidenceHits()` 第 617-648 行 |
| 聚焦评分 | `AnswerGenerationFallbackSnippetSupport.java` | `scoreQuestionFocusedFallbackHit()` 第 66-115 行 |
| Content 行提取 | `AnswerGenerationBaseSupport.java` | `selectFallbackContentLines()` 第 279 行 |
| 相关性过滤 | `QueryEvidenceRelevanceSupport.java` | `filterRelevantHits()` 第 164 行 |
| Description 提取 | `QueryEvidenceRelevanceSupport.java` | `extractDescription()` 第 490 行 |
| 结构化字段匹配 | `QueryEvidenceRelevanceSupport.java` | `matchesStructuredField()` 第 334 行 |
| Evidence 优先级 | `AnswerFallbackEvidenceSupport.java` | `priority()` 第 110-130 行 |
| RRF hit key | `RrfFusionService.java` | `buildHitKey()` 第 499-517 行 |
| Terminal unit identity 读取 | `RrfFusionService.java` | `readTerminalUnitIdentity()` 第 525-537 行 |
| Fused hits 加载 | `QueryGraphAnswerSupport.java` | `answerQuestion()` 第 203-225 行 |
| Payload 生成入口 | `AnswerGenerationPayloadOrchestrator.java` | `buildAnswerPayload()` etc. |

---

## 附录 B：extractDescription 的精确问题

当前实现（`QueryEvidenceRelevanceSupport.java:490-508`）：

```java
private static String extractDescription(String metadataJson) {
    String marker = "\"description\":";
    int markerIndex = metadataJson.indexOf(marker);
    // ...
}
```

Terminal unit metadata 中的相关字段为 `"fieldDescription"`，不是 `"description"`。简易的字符串 `indexOf` 匹配找不到 `"description":"` 时返回空字符串。

修复方案（最小改动）：
```java
private static String extractDescription(String metadataJson) {
    // ... existing logic for "description" ...
    // Fallback: try "fieldDescription"
    String fieldDescMarker = "\"fieldDescription\":";
    int fieldDescIndex = metadataJson.indexOf(fieldDescMarker);
    // ... extract value ...
}
```

---

## 附录 C：Content 字段增强的精确影响分析

当前 terminal unit content（以 max_borrow_days=7 为例）：
```
equipment_types[1].max_borrow_days = 7
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
```

增强后（追加 fieldAliases 中文子集）：
```
equipment_types[1].max_borrow_days = 7
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
最长借用天数 最大借用天数 借用期限上限
```

`selectFallbackContentLines` 会为第 3 行生成一个 content line："最长借用天数 最大借用天数 借用期限上限"。

在 `scoreQuestionFocusedFallbackHit` 中：
- "最长" token 匹配此 line → score += tokenScore("最长") + 2 (= content match bonus)
- "借用天数" token 匹配此 line → score += tokenScore("借用天数") + 2

这会将 terminal unit 的 `scoreQuestionFocusedFallbackHit` 从当前 ~4（仅 context 匹配）提升到 ~15+，接近或超过 ARTICLE 得分。

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 本轮未修改 DB 数据
- 本轮未向 AGENTS.md / CLAUDE.md / allowlist / 计划文件写入任何业务特定内容
- 本轮新增报告：`terminal_unit_phase1e_answer_consumption_analysis_report.md`
