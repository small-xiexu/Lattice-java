# Terminal Unit Phase 1F: Alias Consumption Fix Result Report

修复时间：2026-05-30
执行人：agentA
修复类型：通用 terminal unit consumption — 去问题类型门控，改用 hit-focus 证据匹配

---

## 1. 失败根因确认

Phase 1E conclusion consumption 修复（evidence selector 豁免 + conclusion builder 消费）在单元测试全部通过，但 runtime 对 FQ6/FG2 无效。根因定位：

**两个修复都依赖 `looksLikeStructuredFactQuestion(question) || looksLikeExactLookupQuestion(question)` 作为 terminal unit 通行条件。**

这两个问题类型检测方法需要 query 包含结构化信号（`=`、`/`、`.`、`config`、`value` 等）或精确标识符 token（camelCase、点分隔路径）。纯中文查询如 "预约系统当前的版本号是什么"、"预约系统的最大并发请求数是多少" 不含这些信号，导致：
- selector 的 `allowTerminalUnitEvidence` 为 false → terminal unit 被 preferArticleEvidence 路径丢弃
- conclusion builder 的 terminal unit 分支在 `buildGeneralFallbackConclusionLines` 中同样被跳过

这不是业务特判问题，而是**通用问题类型检测对中文自然语言查询覆盖不足**的结构性缺口。

---

## 2. 修改文件与最小变更

| 文件 | 变更 | 行数 |
|---|---|---|
| `AnswerFallbackEvidenceSelector.java` | `filterFallbackEvidenceHits()`: 移除问题类型门控 `allowTerminalUnitEvidence`，改用 per-hit `isTerminalUnitQueryFocused()`；`selectQuestionScoredFallbackEvidenceHits()` 同步更新；新增 `isTerminalUnitQueryFocused()` + `buildTerminalUnitEvidenceHaystack()` | ~35 行 |
| `AnswerFallbackConclusionBuilder.java` | `buildTerminalUnitExactConclusionLines()`: `isLineQueryFocused(exactLine)` → `isTerminalHitQueryFocused(hit)` 检查 hit 全身证据而非仅 displayText；新增 `isTerminalHitQueryFocused()` + `buildTerminalHitEvidenceHaystack()`；删除旧 `isLineQueryFocused()` | ~30 行 |

**总计 ~65 行。**

---

## 3. 具体通用修复点

### 3.1 Selector：问题类型门控 → per-hit focus 判断

**修改前**（问题类型门控）：
```java
boolean allowTerminalUnitEvidence = preferArticleEvidence
    && (support.looksLikeStructuredFactQuestion(question)
        || support.looksLikeExactLookupQuestion(question));
// ... then for each hit:
if (allowTerminalUnitEvidence && is FACT_CARD + channel) → pass through
```

**修改后**（per-hit focus 判断）：
```java
// For each FACT_CARD + channel=fact_card_terminal_fts hit:
if (preferArticleEvidence
    && hit.evidenceType == FACT_CARD
    && isTerminalUnitChannelHit(hit)
    && isTerminalUnitQueryFocused(hit, highSignalTokens)) → pass through
```

`isTerminalUnitQueryFocused`：从 hit 的 content + metadataJson 构建证据文本（`buildTerminalUnitEvidenceHaystack`），检查 query 的 `extractHighSignalTokens` 是否命中。不再依赖问题类型分类器。

**三重约束**（保持不变）：
1. evidenceType = FACT_CARD
2. channel = fact_card_terminal_fts
3. query high-signal tokens 命中 hit 证据文本 ← **新约束**

### 3.2 Conclusion Builder：displayText token match → hit 全身证据 match

**修改前**：
```java
if (!isLineQueryFocused(exactLine, queryTokens)) → skip
// isLineQueryFocused: exactLine.contains(queryToken) — 英文 displayText 永不对中文 token 返回 true
```

**修改后**：
```java
if (!isTerminalHitQueryFocused(hit, queryTokens)) → skip
// isTerminalHitQueryFocused: hit 的 content + metadataJson 包含 query token
// 中文 alias ("版本号") 在 metadata fieldAliases 中，可以被匹配
// 最终结论仍输出 displayText exact line
```

---

## 4. 为什么不是业务特判

| 检查项 | 说明 |
|---|---|
| 不含业务词 | 无 `version`、`max_concurrent_requests`、`borrowing_system` 等 |
| channel 检测通用 | `metadataJson.contains("\"channel\":\"fact_card_terminal_fts\"")` |
| focus 判断通用 | `extractHighSignalTokens` + 证据文本 `contains(token)` |
| 不依赖文件名/case id | 无任何 eval 数据引用 |
| 不依赖问题类型分类器 | 去除 `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 对 terminal unit 的硬门控 |

---

## 5. 对 Non-Terminal Fallback 的影响边界

| 场景 | 影响 | 说明 |
|---|---|---|
| ARTICLE / CONTRIBUTION evidence | **无影响** | `preferArticleEvidence=true` 路径对 ARTICLE/CONTRIBUTION 无条件放行 |
| SOURCE / GRAPH evidence | **无影响** | 不在 terminal unit 豁免范围内 |
| 非 terminal FACT_CARD | **无影响** | `isTerminalUnitChannelHit` 返回 false，不触发豁免 |
| terminal unit + 不匹配 query | **无影响** | `isTerminalUnitQueryFocused` 返回 false，不触发豁免 |
| 泛泛问题 + 偶然召回 terminal unit | **不会错误输出** | focus token 三重约束阻止不相关 terminal unit 进入 |

---

## 6. Redline / Git Diff / 定向测试

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| redline | **BLOCKER=0** |
| `AnswerFallbackConclusionBuilderTests` | **7/0/0** |
| `AnswerFallbackEvidenceSelectorTests` | **11/0/0** |
| 定向组合 | **18/0/0 — BUILD SUCCESS** |

---

## 7. AgentD Runtime 验证项目

1. Clean schema 重建 + 导入 5 份资料 + compile
2. FQ6 "预约系统当前的版本号是什么" — 验证 terminal unit (version=v2.3.1) 进入 selected evidence + conclusion 输出 `borrowing_system.version = v2.3.1`
3. FG2 "预约系统的最大并发请求数是多少" — 同上，验证 `borrowing_system.max_concurrent_requests = 50`
4. FQ3/FQ4/FG1 保护回归 — 验证 equipment_types 相关答案不退化
5. FQ7/FQ11 保护回归 — XLSX/CSV 答案不退化

---

## 合规声明

- 本轮只修改 `AnswerFallbackEvidenceSelector.java` + `AnswerFallbackConclusionBuilder.java`
- 不含业务词、字段名、文件名、case id、答案值硬编码
- 未修改 question type classifier、compiler、retrieval、RRF、citation
- 未修改测试文件（已有测试全部保护通过）
- 未修改 schema.sql、config、prompt、redline
- 未读取 hidden eval
- 未清库、未重建、未导入、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
