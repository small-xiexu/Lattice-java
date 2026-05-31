# Terminal Unit Phase 1E: Conclusion Consumption Fix Result Report

修复时间：2026-05-30
执行人：agentA
修复类型：最小通用修复 — conclusion builder 优先消费 terminal unit displayText

---

## 1. 唯一根因

`AnswerFallbackConclusionBuilder.buildGeneralFallbackConclusionLines()` 在遍历 conclusion 分支时，没有优先消费 terminal unit 的 `displayText` / `keyPath = value` exact line。即使 terminal unit 已通过 selector 豁免进入 fallback hits（Phase 1E evidence consumption 修复），conclusion builder 仍从 `primaryHit`（通常是 ARTICLE）的冗长段落中选 snippet，导致无法输出 exact value。

## 2. 修改文件

| 文件 | 变更 | 行数 |
|---|---|---|
| `AnswerFallbackConclusionBuilder.java` | 新增 `buildTerminalUnitExactConclusionLines()` + 4 个辅助方法；在 exact structured list 与 aggregated evidence 之间插入终端 unit 结论分支 | ~60 行 |
| `AnswerFallbackConclusionBuilderTests.java` | 新增 5 个 synthetic 单测 | ~120 行 |

**唯一变量**：`AnswerFallbackConclusionBuilder` 的 conclusion line 构造策略。未修改 selector、snippet selection、outcome、citation。

## 3. 插入 Conclusion 分支的位置和原因

**插入位置**：`buildGeneralFallbackConclusionLines()` 中，在 `buildExactStructuredListConclusionLines` 之后、`buildAggregatedEvidenceConclusionLines` 之前。

```java
if (support.looksLikeStructuredFactQuestion(question)
        || support.looksLikeExactLookupQuestion(question)) {
    List<String> terminalUnitLines = buildTerminalUnitExactConclusionLines(fallbackHits, queryTokens);
    if (!terminalUnitLines.isEmpty()) {
        return terminalUnitLines;
    }
}
```

**原因**：
- **在 spreadsheet/exact path 之后**：spreadsheet field definition 和 exact path contract 是更精准的结构化匹配，不应被 terminal unit 抢占
- **在 aggregated/article fallback 之前**：aggregated evidence 和 article snippet 是通用后备方案，terminal unit exact line 比这些更精准
- **仅结构化查值/精确查值问题触发**：复用 `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 已有检测，不对普通叙述型/流程型问题生效

## 4. Terminal Unit Exact Line 的通用判定逻辑

### 4.1 isTerminalUnitChannelHit

```java
evidenceType == FACT_CARD
    && metadataJson.contains("\"channel\":\"fact_card_terminal_fts\"")
```

### 4.2 extractTerminalUnitExactLine

优先从 metadataJson 读取 `displayText`（如 `serviceQuota.dailyLimit = 128`），fallback 到 content 中扫描 `key = value` 格式的非容器行。

### 4.3 isLineQueryFocused

exact line 与 query tokens 的通用包含匹配——至少一个 query token 在 exact line 中出现。

### 4.4 结论格式

```
Confirmed evidence: <displayText> <citation>
```

## 5. 为什么不是 Case 特判

| 检查项 | 说明 |
|---|---|
| 不含业务词 | 无 `if (key.equals("version"))` 等硬编码 |
| 不含文件名/case id | 不读取任何文件名或 case id |
| channel 检测通用 | `metadataJson.contains("\"channel\":\"fact_card_terminal_fts\"")` |
| 问题类型检测复用 | `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` |
| displayText 提取通用 | 从 metadata `displayText` 或 content ` = ` 行提取 |
| query focus 通用 | `isLineQueryFocused` 使用通用 token 包含匹配 |

## 6. 哪些场景保持原 ARTICLE/SOURCE Conclusion 行为

| 场景 | 终端 unit 结论 | 原因 |
|---|---|---|
| 普通描述性问题 | **否** — `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 为 false |
| 对比/流程/枚举问题 | **否** — 同上 |
| 非结构化摘要问题 | **否** — 同上 |
| spreadsheet field definition | **否** — 在终端 unit 分支之前返回 |
| exact path contract | **否** — 同上 |
| 没有 terminal unit hit | **否** — 扫描结果为空，fall through |
| terminal unit 不匹配 query | **否** — `isLineQueryFocused` 返回 false |
| ARTICLE/SOURCE conclusion | **是** — 所有不触发终端 unit 分支的场景 |

## 7. 测试覆盖

| 测试 | 覆盖风险 |
|---|---|
| `shouldOutputTerminalUnitDisplayTextAsConclusionForStructuredFactQuestion` | structured fact 问题中 terminal unit displayText 优先作为结论；不输出 alias JSON |
| `shouldConsumeNonPrimaryTerminalUnit` | terminal unit 非 primary hit 也能被消费 |
| `shouldNotOutputTerminalUnitForDescriptiveQuestion` | 描述性问题中 terminal unit 不抢占 ARTICLE |
| `shouldNotConsumeIrrelevantTerminalUnit` | 不相关 terminal unit 不被消费 |
| `shouldPreserveExistingConclusionBehaviorWithoutTerminalUnit` | 无 terminal unit 时现有行为不变 |

所有测试使用 synthetic 数据：`serviceQuota.dailyLimit`、`runtimeProfile.activeTier`、`gatewayConfig.requestLimit`、`runtimeConfig.maxRetryCount`。

## 8. Redline / 定向测试 / 全量 mvn test

### 8.1 git diff --check

无输出（通过）。

### 8.2 Redline

```
BLOCKER=0, REVIEW=2068, ALLOWLIST=260
```

### 8.3 定向测试

| 测试类 | 结果 |
|---|---|
| `AnswerFallbackConclusionBuilderTests` | **7/0/0** (2 原有 + 5 新增) |
| `AnswerFallbackEvidenceSelectorTests` | **11/0/0** (6 原有 + 5 新增) |
| 组合 | **18/0/0** |

### 8.4 全量 mvn test

```
Tests run: 997, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

全量 997/0/0/0 干净通过。较上一轮（992）增加 5 个 conclusion builder 测试。

## 9. 是否需要下一轮 Snippet Selection 修复

**本轮不需要额外 snippet selection 修复**。conclusion builder 直接输出 `displayText` exact line，不依赖 snippet selection 从 content 行中挑选。

如果 agentD clean schema 复验后仍发现答案未消费 terminal unit exact value，可能原因：
1. terminal unit 未进入 fallback hits → selector 问题（Phase 1E evidence consumption 已修）
2. 问题类型未被检测为 structured fact / exact lookup → 问题类型检测问题
3. `displayText` 在 metadata 或 content 中缺失 → compiler 问题

但这些都不属于本轮 conclusion builder 范围，应作为独立变量分析。

## 10. 下一步

交 agentD clean schema / runtime 复验——重点验证 FQ6/FG2 的 conclusion 是否输出 `borrowing_system.version = v2.3.1` / `borrowing_system.max_concurrent_requests = 50`。

## 合规声明

- 本轮只修改 `AnswerFallbackConclusionBuilder.java` 和对应测试
- 未修改 selector、snippet selection、outcome、citation、compiler、retrieval、RRF
- 不含任何业务词、字段名、文件名、case id、答案值硬编码
- 复用现有 `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 通用检测
- 未读取 hidden eval
- 未清库、未重建、未导入资料、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
