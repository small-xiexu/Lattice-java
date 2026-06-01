# Terminal Unit Phase 1E: Content / Description 增强修复结果报告

修复时间：2026-05-30
执行人：agentA
修复类型：最小通用增强 — content 扩展 + description 识别

---

## 1. 唯一根因

Terminal unit evidence 已进入 fused topK（Phase 1E-2 clean schema 验证：YAML 5/5 目标 unit 进入 fused），但 answer/fallback 侧无法稳定消费，原因有两个通用缺口：

| # | 缺口 | 位置 | 影响 |
|---|---|---|---|
| 1 | `content` 只含 `display_text + field_description`，不含 `fieldAliases` | `FactCardTerminalUnitMapper.xml:202` | LLM 生成的中文 alias 在 FTS 检索时有效，但在 fallback scoring 中完全不可见 |
| 2 | `extractDescription()` 只搜 `"description":`，不识别 `"fieldDescription":` | `QueryEvidenceRelevanceSupport.java:490` | terminal unit metadata 中的 `fieldDescription` 无法参与 `matchesStructuredField` / `matchesTitleOrDescription` |

两个缺口都是**通用架构问题**，不是 fresh eval 专属。terminal unit 的 `evidenceType=FACT_CARD`、`content` 字段设计、`extractDescription` 的字符串匹配逻辑，都是针对整卡 evidence 设计的，未适配 terminal unit 的单字段粒度。

---

## 2. 修改摘要

| 文件 | 变更 | 行数 |
|---|---|---|
| `FactCardTerminalUnitMapper.xml` | content 从 `display_text + field_description` 扩展为 `display_text + field_description + field_aliases_json::text` | 1 行 |
| `QueryEvidenceRelevanceSupport.java` | `extractDescription()` 在 `"description":` 未匹配时 fallback 搜索 `"fieldDescription":` | 3 行 |

**总计：4 行代码变更。**

---

## 3. 具体通用修复点

### 3.1 Content 增强（Mapper XML）

**修改前：**
```sql
trim(concat_ws(E'\n', unit.display_text, unit.field_description)) as content,
```

**修改后：**
```sql
trim(concat_ws(E'\n', unit.display_text, unit.field_description,
    unit.field_aliases_json::text)) as content,
```

**效果**（以 max_borrow_days=7 为例）：

修改前 content：
```
equipment_types[1].max_borrow_days = 7
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
```

修改后 content：
```
equipment_types[1].max_borrow_days = 7
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
["max_borrow_days","max borrow days","equipment_types[1].max_borrow_days",...,"最长借用天数","最大借用天数","借用期限上限"]
```

现在 `scoreQuestionFocusedFallbackHit` 在遍历 content lines 时，第 3 行包含中文 alias token（"最长借用天数"、"最大借用天数"等），可以匹配 query token（"最长"、"借用天数"），大幅提升 terminal unit 在 fallback evidence 排序中的竞争力。

### 3.2 Description 识别（QueryEvidenceRelevanceSupport）

**修改前：**
```java
String marker = "\"description\":";
int markerIndex = metadataJson.indexOf(marker);
if (markerIndex < 0) {
    return "";
}
```

**修改后：**
```java
String marker = "\"description\":";
int markerIndex = metadataJson.indexOf(marker);
if (markerIndex < 0) {
    marker = "\"fieldDescription\":";
    markerIndex = metadataJson.indexOf(marker);
}
if (markerIndex < 0) {
    return "";
}
```

**效果**：`matchesStructuredField` 和 `matchesTitleOrDescription` 现在可以通过 `fieldDescription` 匹配 query token（如 fieldDescription 中的 "精密仪器" context），提升 terminal unit 的 relevance 判断。

---

## 4. 为什么不是 Case 特判

| 检查项 | 说明 |
|---|---|
| 不改 fallback selector gate | `preferArticleEvidence`、`filterRelevantHits`、`sortFallbackEvidenceHits` 逻辑未变 |
| 不改 evidence priority 规则 | `evidenceSupport.priority()` 未变 |
| 不新增 terminal unit 专属分支 | content 增强和 fieldDescription 识别对所有 evidence type 生效 |
| 不含业务词判断 | 无 `if (key.equals("max_borrow_days"))` 等硬编码 |
| 不含 eval 文件名/case id | 不读取任何 eval 数据 |
| SQL 增强是通用拼接 | `field_aliases_json::text` 对所有 fact card terminal unit 生效 |
| Java 增强是通用键名 fallback | `fieldDescription` 键名对所有 metadata JSON 生效（不只是 terminal unit） |

---

## 5. 测试结果

### 5.1 git diff --check

无输出（通过）。

### 5.2 Redline 扫描

```
BLOCKER=0, REVIEW=2065, ALLOWLIST=260
```

与 scoped route 修复后一致，无新增命中。

### 5.3 定向测试

```
FactCardTerminalUnitFtsSearchServiceTests: 3/0/0 — BUILD SUCCESS
```

无 `QueryEvidenceRelevanceSupportTests` （该类无独立测试文件，逻辑被 `AnswerFallbackEvidenceSelectorTests` 间接覆盖）。

### 5.4 全量 mvn test

```
Tests run: 987, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

全量 987/0/0/0 干净通过。content 增强和 fieldDescription 识别未引入任何回归。

---

## 6. preferArticleEvidence 丢弃 FACT_CARD 的潜在后续风险

本轮未修改 `AnswerFallbackEvidenceSelector.filterFallbackEvidenceHits()` 中的 `preferArticleEvidence` 过滤。该路径在第 485-506 行按 `evidenceType` 丢弃所有非 `ARTICLE`/`CONTRIBUTION` 的命中。

| 场景 | 影响 | 是否本轮解决 |
|---|---|---|
| `preferArticleEvidence=true` + ARTICLE 存在于 fused | terminal unit (FACT_CARD) 被丢弃 | **否** — 本轮不改 fallback selector |
| `preferArticleEvidence=false` | terminal unit 保留，但排序得分低于 ARTICLE | **间接改善** — content 增强后 terminal unit 排序得分提升 |
| `shouldPreferMixedEvidence` | 同 `preferArticleEvidence=false` 路径 | **间接改善** — 同上 |

**建议**：如果 content 增强后 terminal unit 在 `preferArticleEvidence=false` 路径中排序提升但仍被 `preferArticleEvidence=true` 路径丢弃，后续轮次可能需要考虑：
1. 在 `preferArticleEvidence=true` 路径中为 terminal unit（metadata 含 `terminalUnitIdentity` / `channel=fact_card_terminal_fts`）开最小豁免 — 但这涉及改 fallback selector gate，需谨慎评估。
2. 或者在 answer flow 中为精确查值题调整 `preferArticleEvidence` 判断条件。
这属于独立变量，不应与本轮 content/description 增强合在一个 commit 中。

---

## 7. 未修改清单

| 文件/区域 | 状态 |
|---|---|
| `AnswerFallbackEvidenceSelector.java` | **未修改** |
| `AnswerGenerationService.java` / fallback outcome / snippet selector | **未修改** |
| `AnswerGenerationPayloadOrchestrator.java` | **未修改** |
| `FactCardTerminalUnitMaterializer.java` | **未修改** |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | **未修改** |
| `FactCardTerminalUnitIntentReranker.java` | **未修改** |
| `RrfFusionService.java` | **未修改** |
| `FactCardTerminalUnitFtsSearchService.java` | **未修改** |
| `QueryEvidenceRelevanceSupport.java`（除 extractDescription 外） | **未修改** |
| `schema.sql` / prompts / config | **未修改** |
| `scripts/scan-redline.sh` / allowlist | **未修改** |
| eval 题集 / fixtures / hidden eval | **未修改** |

---

## 8. 下一步

交 agentD 做 clean schema / runtime 复验：
1. 清库重建 + 导入资料 + compile
2. 验证 YAML 5 题 terminal unit 在 fallback evidence 中的 scored content lines 是否包含中文 alias token
3. 验证 `extractDescription` 是否能从 terminal unit metadata 提取 `fieldDescription`
4. 评估终端答案是否改善（不做硬性 PASS/FAIL 要求，优先看排序和消费路径变化）

agentA 本轮不自行验证业务 eval。

---

## 9. 计划台账回写

已回写 `terminal_unit_phase1_implementation_plan.md`：
- 本轮 checkpoint 标为进行中（agentA content/description 增强）
- 待全量测试完成后更新为已完成

---

## 合规声明

- 本轮未修改 fallback selector / gate / priority / conclusion / snippet 逻辑
- 本轮未修改 citation / reranker / RRF / vector / LLM enricher / compiler route
- 本轮未修改 prompt 模板
- 本轮未修改 schema.sql
- 本轮不包含业务词、文件名、case id、中文字段语义硬编码
- 本轮未读取 hidden eval
- 本轮未清库、未重建、未导入资料、未跑业务 eval
- 本轮未 stage、未 commit、未 push
- 修改文件：2（Mapper XML + QueryEvidenceRelevanceSupport.java）
- 新增报告：1（本报告）
