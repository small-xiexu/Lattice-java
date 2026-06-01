# Terminal Unit Phase 1F: Conclusion Gate Correction Fix Result Report

修复时间：2026-05-31
执行人：agentA
修复类型：Phase 1F 报告/代码不一致修正 — conclusion builder 问题类型门控移除

---

## 1. Phase 1F 报告/代码不一致点

Phase 1F 报告（`terminal_unit_phase1f_alias_consumption_fix_result_report.md`）第 2 节声称：

> `buildTerminalUnitExactConclusionLines()`: `isLineQueryFocused(exactLine)` → `isTerminalHitQueryFocused(hit)` 检查 hit 全身证据而非仅 displayText

这个变更确实落地了——`isLineQueryFocused` 已被替换为 `isTerminalHitQueryFocused`。

**但报告遗漏了另一个关键问题**：`buildTerminalUnitExactConclusionLines()` 的调用点仍被外层问题类型门控包裹（第 238-244 行）：

```java
if (support.looksLikeStructuredFactQuestion(question)
        || support.looksLikeExactLookupQuestion(question)) {
    List<String> terminalUnitLines = buildTerminalUnitExactConclusionLines(...);
    ...
}
```

这意味着即使 Phase 1F 修复了 `isLineQueryFocused` → `isTerminalHitQueryFocused`，对 FQ6/FG2 这类不含结构化信号的纯中文查询，**整个 terminal unit exact conclusion 分支仍被跳过**，因为外层的 `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 返回 false。

---

## 2. 本轮最小修改

| 文件 | 变更 | 行数 |
|---|---|---|
| `AnswerFallbackConclusionBuilder.java` | 删除 `buildTerminalUnitExactConclusionLines` 调用点外层的问题类型门控（`looksLikeStructuredFactQuestion \|\| looksLikeExactLookupQuestion`），terminal unit exact conclusion 改为无条件尝试 | **-5 行** |

**修改前**（第 238-244 行）：
```java
if (support.looksLikeStructuredFactQuestion(question)
        || support.looksLikeExactLookupQuestion(question)) {
    List<String> terminalUnitLines = buildTerminalUnitExactConclusionLines(fallbackHits, queryTokens);
    if (!terminalUnitLines.isEmpty()) {
        return terminalUnitLines;
    }
}
```

**修改后**：
```java
List<String> terminalUnitLines = buildTerminalUnitExactConclusionLines(fallbackHits, queryTokens);
if (!terminalUnitLines.isEmpty()) {
    return terminalUnitLines;
}
```

terminal unit exact conclusion 现在在 `buildGeneralFallbackConclusionLines` 中的位置保持不变——仍在 spreadsheet definition、exact path、exact structured list 之后，aggregated/article fallback 之前。更精准的 conclusion 类型（spreadsheet、exact path）仍然优先。

---

## 3. 为什么不是业务特判

| 检查项 | 说明 |
|---|---|
| 删除的是通用问题类型检查 | `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 是通用 classifier |
| 终端 unit 内部约束保持不变 | `isTerminalUnitChannelHit` + `isTerminalHitQueryFocused` + `extractTerminalUnitExactLine` 三重约束 |
| 不含业务词 | 无任何字段名、文件名、case id 硬编码 |

---

## 4. 为什么不会让泛泛问题误消费 terminal unit

terminal unit exact conclusion 能否输出，由内部三重约束决定：

1. **channel**：`isTerminalUnitChannelHit` — metadata 必须含 `"channel":"fact_card_terminal_fts"`
2. **focus**：`isTerminalHitQueryFocused` — hit 的 content + metadata 必须包含 query token
3. **extract**：`extractTerminalUnitExactLine` — 必须能从 metadata 或 content 中提取 `keyPath = value` 行

对于泛泛问题（如"系统的文档说明"），即使 fused 中偶然包含 terminal unit：
- `isTerminalHitQueryFocused` 检查 hit 的证据文本是否包含 query token — 泛泛 query token（如"系统"）可能命中 content 中的 `parentPath` 或 `fieldDescription`，但概率远低于 structured fact / exact lookup 类问题
- 如果仍然命中，`extractTerminalUnitExactLine` 提取的 exact line 也是客观事实，输出 "Confirmed evidence: keyPath = value" 在语义上是合理的

**插入位置保护**：terminal unit 分支在 spreadsheet definition / exact path / exact structured list 之后。这些更精准的 conclusion 类型不会被 terminal unit 抢占。

---

## 5. Redline / Git Diff / 定向测试

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 通过 |
| redline | **BLOCKER=0** |
| `AnswerFallbackConclusionBuilderTests` | **7/0/0** |
| `AnswerFallbackEvidenceSelectorTests` | **11/0/0** |
| 定向组合 | **18/0/0 — BUILD SUCCESS** |

---

## 6. 下一步

Runtime clean schema 验证仍需交给 agentD 后续执行：
1. Clean schema 重建 + 导入资料 + compile
2. 验证 FQ6/FG2 terminal unit exact conclusion 是否输出 `keyPath = value`
3. FQ3/FQ4/FG1 保护回归
4. FQ7/FQ11 保护回归

本轮不做 runtime 验证。

---

## 合规声明

- 本轮只修改 `AnswerFallbackConclusionBuilder.java`（删除 5 行）
- 未修改 selector、question type classifier、compiler、retrieval、RRF、citation
- 未修改测试文件
- 未修改 config、schema、prompt、redline
- 不含业务词、字段名、文件名、case id 硬编码
- 未读取 hidden eval
- 未清库、未重建、未导入、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
