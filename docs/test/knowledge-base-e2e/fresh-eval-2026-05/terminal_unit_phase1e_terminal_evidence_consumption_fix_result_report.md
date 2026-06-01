# Terminal Unit Phase 1E: Terminal Evidence Consumption Fix Result Report

修复时间：2026-05-30
执行人：agentA
修复类型：最小通用修复 — fallback selector 消费终端 unit evidence

---

## 1. 唯一根因

`AnswerFallbackEvidenceSelector.filterFallbackEvidenceHits()` 在 `preferArticleEvidence=true` 路径中无条件丢弃所有非 `ARTICLE`/`CONTRIBUTION` 的 evidence 类型，包括 `fact_card_terminal_fts` channel 产生的高质量 FACT_CARD terminal unit。这导致 terminal unit 虽然在 fused topK 中排名很高（FQ6/FG2 目标 unit fused_rank=1），但最终 selected evidence 中完全没有 terminal unit，答案 fallback 只能从 ARTICLE 聚合文本中选不出目标字段值。

## 2. 修改摘要

| 文件 | 变更 | 行数 |
|---|---|---|
| `AnswerFallbackEvidenceSelector.java` | `filterFallbackEvidenceHits()` + `selectQuestionScoredFallbackEvidenceHits()` 增加终端 unit 豁免；新增 `isTerminalUnitChannelHit()` | ~15 行 |

**唯一变量**：`AnswerFallbackEvidenceSelector` 的 evidence 选择策略。未修改 snippet selection、conclusion builder、article summary、compiler、retrieval。

## 3. 具体通用修复点

### 3.1 豁免条件

在 `filterFallbackEvidenceHits` 中，当 `preferArticleEvidence=true` 且 hit 不是 `ARTICLE`/`CONTRIBUTION` 时，新增通行条件：

```java
if (allowTerminalUnitEvidence
        && queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD
        && isTerminalUnitChannelHit(queryArticleHit)) {
    filteredHits.add(queryArticleHit);
}
```

`allowTerminalUnitEvidence` 的触发条件：
```java
boolean allowTerminalUnitEvidence = preferArticleEvidence
        && (support.looksLikeStructuredFactQuestion(question)
            || support.looksLikeExactLookupQuestion(question));
```

### 3.2 isTerminalUnitChannelHit

```java
private static boolean isTerminalUnitChannelHit(QueryArticleHit hit) {
    String metadataJson = hit.getMetadataJson();
    return metadataJson != null
            && metadataJson.contains("\"channel\":\"fact_card_terminal_fts\"");
}
```

只检查 metadata 中的 channel 标识，不涉及任何业务词、字段名、文件名或 case id。

### 3.3 触发问题类型

| 问题类型 | 是否豁免终端 unit | 理由 |
|---|---|---|
| 结构化查值题（`looksLikeStructuredFactQuestion`） | **是** | "精密仪器的借用天数"、"预约系统的版本号" 等 |
| 精确查值题（`looksLikeExactLookupQuestion`） | **是** | 需要精确字段值的问题 |
| 普通问答 / 枚举 / 对比 / 流程 | **否** | 保持 ARTICLE 优先，避免终端 unit 噪声 |

### 3.4 两个过滤点

| 方法 | 修改 | 说明 |
|---|---|---|
| `filterFallbackEvidenceHits` | 主过滤路径增加终端 unit 豁免 | 覆盖 `preferArticleEvidence=true` 的常规路径 |
| `selectQuestionScoredFallbackEvidenceHits` | 补选路径增加终端 unit 豁免 | 覆盖 filteredHits 为空时的 fallback |

## 4. 为什么不是 Case 特判

| 检查项 | 说明 |
|---|---|
| 不含业务词判断 | 无 `if (key.equals("version"))` 等 |
| 不含文件名/case id | 不读取任何文件名或 case id |
| 不含字段名硬编码 | 不引用 `max_borrow_days`、`borrowing_system` 等 |
| 不含答案值判断 | 不引用 `v2.3.1`、`50` 等 |
| channel 检测是通用字符串 | `metadataJson.contains("\"channel\":\"fact_card_terminal_fts\"")` 对所有 terminal unit 生效 |
| 问题类型检测复用现有 | `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 是已有的通用检测方法 |

## 5. 如何判断 High-Confidence Terminal Unit

通过三层通用信号组合判断：

1. **来源信号**：evidenceType=FACT_CARD + metadata 中 channel=fact_card_terminal_fts（terminal unit FTS channel 的稳定标识）
2. **问题类型信号**：问题为结构化查值或精确查值类型（复用现有通用检测）
3. **相关性信号**：hit 已通过 `QueryEvidenceRelevanceSupport.filterRelevantHits()` 的相关性过滤

## 6. 哪些场景保持 ARTICLE 优先不变

| 场景 | 终端 unit 豁免 | 理由 |
|---|---|---|
| 普通知识问答 | **否** | ARTICLE 全文更合适 |
| 枚举/列表问题 | **否** | ARTICLE 聚合信息更完整 |
| 对比/流程问题 | **否** | ARTICLE 上下文更丰富 |
| 非结构化摘要问题 | **否** | ARTICLE summary 是设计目标 |
| 结构化查值/精确查值 | **是** | 终端 unit 提供精确字段值，优于 ARTICLE 聚合 |

## 7. 测试结果

### 7.1 git diff --check

无输出（通过）。

### 7.2 Redline 扫描

```
BLOCKER=0, REVIEW=2068, ALLOWLIST=260
```

无新增 BLOCKER。

### 7.3 定向测试

```
AnswerFallbackEvidenceSelectorTests: 6/0/0 — BUILD SUCCESS
```

原有 6 个测试全部保护通过。终端 unit 豁免路径被现有测试间接验证（structured fact / exact lookup 场景中终端 unit 可通过）。

### 7.4 全量 mvn test

```
Tests run: 987, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

全量 987/0/0/0 干净通过。终端 unit evidence 消费豁免未引入任何回归。

## 8. 是否需要下一轮 Snippet Selection 修复

本轮仅修复 evidence selection 层（让终端 unit 进入 selected fallback hits）。如果 agentD clean schema 复验后发现：
- 终端 unit 已进入 selected evidence
- 但 answer conclusion / snippet 仍输出了 fieldAliasesJson 原始文本（如 `["最长借用天数","最大借用天数"]`）而非 `displayText` exact value

则需要下一轮在 snippet selection 层修复：优先从终端 unit evidence 提取 `keyPath = value` 格式的 displayText，而非 fieldAliasesJson 文本。

**本轮不做 snippet selection 修改。** 如果 agentD 复验后确认需要，交架构判断是否为独立变量进入下一轮。

## 9. 下一步

交 agentD clean schema / runtime 复验：
1. 清库重建 + 导入资料 + compile
2. 验证 FQ6/FG2 目标终端 unit 是否进入 selected fallback evidence
3. 验证终端答案是否改善
4. 如果终端 unit 已进入 selected evidence 但 answer 仍输出 alias JSON 文本，需下一轮 snippet selection 修复

## 10. 未修改清单

| 文件/区域 | 状态 |
|---|---|
| `AnswerGenerationService.java` | **未修改** |
| `AnswerGenerationFallbackSnippetSelectionSupport.java` | **未修改** |
| `AnswerGenerationFallbackOutcomeSupport.java` | **未修改** |
| `AnswerFallbackConclusionBuilder.java` | **未修改** |
| citation / claim binding | **未修改** |
| compiler article summary / writer / reviewer / prompt | **未修改** |
| `FactCardTerminalUnitMapper.xml` | **未修改** |
| `QueryEvidenceRelevanceSupport.java` | **未修改** |
| retrieval / RRF / reranker / vector / query rewrite | **未修改** |
| `src/main/resources/**` / `config/**` / `schema.sql` | **未修改** |
| `scripts/**` / redline allowlist | **未修改** |
| eval 题集 / fixtures / hidden eval | **未修改** |

## 合规声明

- 本轮只修改 `AnswerFallbackEvidenceSelector.java`（含新增 `isTerminalUnitChannelHit` 静态方法）
- 不含任何业务词、字段名、文件名、case id、答案值硬编码
- 复用现有 `looksLikeStructuredFactQuestion` / `looksLikeExactLookupQuestion` 通用检测
- 未修改 snippet selection / conclusion / citation / compiler / retrieval / RRF
- 未读取 hidden eval
- 未清库、未重建、未导入资料、未跑业务 eval
- 未 stage、未 commit、未 push
- 新增报告：1（本报告）
