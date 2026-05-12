# shouldExposeStructuredJsonValuesInFallbackAnswer 修复结果报告

| 项目 | 值 |
|---|---|
| 修复时间 | 2026-05-12 |
| 修改文件 | `src/main/java/com/xbk/lattice/query/service/AnswerSpreadsheetFieldDefinitionConclusionBuilder.java` |
| 修改方法 | `buildFocusedSpreadsheetFieldDefinitionConclusionLines`（新增过滤调用）、新增 `looksLikeJsonStructuralFragment` |

---

## 1. 问题描述

在 `shouldExposeStructuredJsonValuesInFallbackAnswer` 测试中，证据内容为格式化 JSON：

```json
{
  "items": [
    {"field": "promo_credit", "raw": "promo_credit: loyalty credit amount"},
    {"field": "platform_credit", "raw": "platform_credit: third-party credit amount"}
  ]
}
```

`buildFocusedSpreadsheetFieldDefinitionConclusionLines` 在完成标识匹配后，遍历 content 每一行，将未匹配到请求标识的行作为补充证据加入结论。JSON 结构行 `{`、`"items": [`、`]`、`}` 被 `normalizeFallbackLineCandidate` 处理后仍为非空，最终渲染为独立证据条目：

```
- { [→ generic-fields.md]
- "items": [ [→ generic-fields.md]
- ] [→ generic-fields.md]
- } [→ generic-fields.md]
```

测试断言 `doesNotContain("\"items\"")` 触发失败。

## 2. 修改内容

### 2.1 循环中加入 JSON 结构片段过滤（`buildFocusedSpreadsheetFieldDefinitionConclusionLines`）

```java
// 修复前（line 131-134）：
if (alreadyMatched) {
    continue;
}
String normalizedLine = evidenceNormalizer.normalizeFallbackLineCandidate(rawLine);

// 修复后：
if (alreadyMatched) {
    continue;
}
if (looksLikeJsonStructuralFragment(rawLine)) {
    continue;
}
String normalizedLine = evidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
```

### 2.2 新增 helper 方法 `looksLikeJsonStructuralFragment`

```java
private boolean looksLikeJsonStructuralFragment(String line) {
    if (line == null || line.isBlank()) {
        return true;
    }
    String trimmed = line.trim();
    // 纯 JSON 括号：{ } [ ] 可选逗号
    if (trimmed.matches("[\\[\\]{}](?:,)?\\s*")) {
        return true;
    }
    // JSON key 值为结构体：如 "items": [
    return trimmed.matches("\"[^\"]+\"\\s*:\\s*[\\[\\{].*");
}
```

**设计要点**：
- 仅匹配纯 JSON 结构符号（`{`、`}`、`[`、`]`）及 key 值为结构体的 opener（如 `"items": [`）
- 不匹配含实际数据的 JSON 对象/数组行（如 `{"field": "promo_credit", ...}`），它们由已有的 `lineContainsIdentifier` 处理
- null/blank 行返回 true，与上游 `rawLine.isBlank()` 的 continue 语义一致

## 3. 运行结果

### 3.1 目标测试

```
mvn test -Dtest=AnswerGenerationServiceTests#shouldExposeStructuredJsonValuesInFallbackAnswer

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 3.2 AnswerGenerationServiceTests 全类

| 指标 | 修复前 | 修复后 | 变化 |
|---|---|---|---|
| Tests run | 65 | 65 | — |
| Failures | **3** | **2** | **-1** |
| Errors | 0 | 0 | — |

剩余 2 个失败（未受影响）：

1. `shouldFallbackRewriteWhenPlainMarkdownOmitsCitations`
2. `shouldKeepUnsupportedDetailCaveatInAnsweredDiffQuestion`

### 3.3 全量 mvn test

| 指标 | 修复前 | 修复后 | 变化 |
|---|---|---|---|
| Tests run | 811 | 811 | — |
| Failures | 3 | **2** | **-1** |
| Errors | 0 | 0 | — |

## 4. 合规检查

| 检查项 | 状态 |
|---|---|
| 是否只修改 `AnswerSpreadsheetFieldDefinitionConclusionBuilder.java` | ✅ 是 |
| 是否修改测试 | ✅ 否 |
| 是否触碰 `AnswerGenerationFallbackOutcomeSupport` | ✅ 否 |
| 是否触碰 `AnswerParagraphPostProcessor` | ✅ 否 |
| 是否触碰 `QuerySemanticRules` | ✅ 否 |
| 是否触碰 `lattice-query-semantic.yml` | ✅ 否 |
| 是否触碰 outcome / rewrite / comparison 主链 | ✅ 否 |
| 是否新增具体问题文本特判 | ✅ 否 |
| 是否新增具体字段名特判 | ✅ 否 |
| 是否中文关键词硬编码 | ✅ 否 |
| redline BLOCKER 是否仍为 0 | ✅ 是（`EXIT_CODE=0`） |
| 是否修复扩大到其他类 | ✅ 否 |

## 5. 修改统计

| 指标 | 值 |
|---|---|
| 修改文件数 | 1 |
| 新增方法数 | 1（`looksLikeJsonStructuralFragment`） |
| 修改行数 | +8（循环内 4 行过滤 + helper 方法 14 行） |
