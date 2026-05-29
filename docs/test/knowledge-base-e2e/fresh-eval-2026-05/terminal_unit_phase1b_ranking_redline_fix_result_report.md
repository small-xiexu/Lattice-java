# Terminal Unit Phase 1B Ranking Redline Fix Result Report

验证时间：2026-05-29
验证人：agentA
验证对象：移除 FactCardTerminalUnitIntentReranker Java 主链中文问法硬编码，迁移到 QuerySemanticRules / lattice-query-semantic.yml 配置体系

## 1. 结论

本轮为红线收口小修：已移除 `FactCardTerminalUnitIntentReranker` 中的 `NUMERIC_QUESTION_SIGNALS` 硬编码常量（"多少/最大/最小/最长/最短/上限/下限"），改为依赖 `QuerySemanticRules.containsAnyNumericValueIntentSignal()`，信号值从 `lattice-query-semantic.yml` 的 `numeric-value-intent-signals` 配置项读取。

本轮只修红线风险，不做 fresh eval 服务级结论。未 stage、未 commit、未 push。

## 2. 修改文件清单

### 修改文件

| 文件 | 变更说明 |
|---|---|
| `src/main/java/com/xbk/lattice/query/service/QuerySemanticRules.java` | 新增 `numericValueIntentSignals` 字段、getter/setter、`containsAnyNumericValueIntentSignal()` 方法 |
| `src/main/resources/config/lattice-query-semantic.yml` | 新增 `numeric-value-intent-signals` 配置项 |
| `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitIntentReranker.java` | 移除 `NUMERIC_QUESTION_SIGNALS` 硬编码常量；新增 `QuerySemanticRules` 构造器依赖；`hasNumericQuestionSignal()` 改为调用 `semanticRules.containsAnyNumericValueIntentSignal()` |
| `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchService.java` | 构造器链中传递 `new QuerySemanticRules()` 给 `FactCardTerminalUnitIntentReranker` |
| `src/test/java/com/xbk/lattice/query/service/FactCardTerminalUnitIntentRerankerTests.java` | reranker 实例化改为 `new FactCardTerminalUnitIntentReranker(new QuerySemanticRules())` |

### 未修改（遵守禁令）

- `AnswerGenerationFallback*`、`AnswerFallback*`、`QueryResponseCitation*`、`FactCardVector*`：未修改
- `schema.sql`、`FactCardTerminalUnitMaterializer.java`、`FactCardGenerationService.java`：未修改
- `KnowledgeSearchService.java`、`RetrievalStrategyResolver.java`、`RrfFusionService.java`、`QueryGraph*`：未修改
- `scripts/scan-redline.sh`、`redline allowlist`：未修改
- `docs/模型绑定配置参考.md`：未读取、未修改
- fresh eval 题集、标准答案、模型私有配置：未读取、未修改

## 3. 变更原理

### 3.1 问题

`FactCardTerminalUnitIntentReranker` 中存在硬编码常量：

```java
private static final Set<String> NUMERIC_QUESTION_SIGNALS = Set.of(
    "多少", "最大", "最小", "最长", "最短", "上限", "下限"
);
```

这违反了项目规则：通用中文语义信号必须走 `QuerySemanticRules` / `lattice-query-semantic.yml` 配置体系，不得在 Java 主链硬编码。

### 3.2 修复

1. 在 `QuerySemanticRules` 中新增 `numericValueIntentSignals` 配置属性（含默认值），与 `countSignals`、`comparisonSignals` 等保持一致的 getter/setter/contains 方法模式。
2. 在 `lattice-query-semantic.yml` 中新增 `numeric-value-intent-signals` 配置项，信号值与原来完全一致。
3. `FactCardTerminalUnitIntentReranker` 新增 `QuerySemanticRules` 构造器依赖，`hasNumericQuestionSignal()` 改为：
   ```java
   if (semanticRules.containsAnyNumericValueIntentSignal(question)) {
       return true;
   }
   ```
4. 保留原有的数字 token 正则匹配（`\d+` 且非 `"0"`）作为补充信号——这是通用 ASCII 模式，不是中文问法。

### 3.3 为什么不是 Fresh Eval 特判

- 新增的 `numeric-value-intent-signals` 只包含通用中文数值问法信号（"多少/最大/最小/最长/最短/上限/下限"），不包含任何具体字段语义、题面词、文件名、case id、答案值。
- 配置值与原硬编码常量完全一致，不新增任何业务词。
- 唯一变量是信号来源从 Java 常量变为 YAML 配置，行为语义不变。

## 4. 验证结果

### 4.1 Java 主链硬编码扫描

```bash
rg -n "NUMERIC_QUESTION_SIGNALS|多少|最大|最小|最长|最短|上限|下限" \
  src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitIntentReranker.java
```

结果：**NO_MATCHES_FOUND**（exit=1）

### 4.2 Redline 扫描

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：exit=0，**BLOCKER=0**，REVIEW=2059，ALLOWLIST=259

### 4.3 定向测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
  -Dtest=FactCardTerminalUnitIntentRerankerTests,FactCardTerminalUnitFtsSearchServiceTests test
```

结果：**Tests run: 13, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

| # | 测试 | 状态 |
|---|---|---|
| 1 | shouldPrioritizeFieldIntentHitOverValueOnlyHitWithinSameParent | PASS |
| 2 | shouldNotLetNumericValueTypeOverrideExplicitFieldTokenMatch | PASS |
| 3 | shouldGiveSmallNumericBonusWhenQueryHasNumericIntentNoFieldTokens | PASS |
| 4 | shouldPreserveOriginalOrderWhenMetadataIsMissing | PASS |
| 5 | shouldNotRerankWhenNoFieldIntentSignalAndNoNumericIntent | PASS |
| 6 | shouldReturnSingleHitUnchanged | PASS |
| 7 | shouldHandleEmptyAndNullInputs | PASS |
| 8 | shouldTreatFieldAliasesMatchAsFieldIntent | PASS |
| 9 | shouldTreatKeyPathMatchAsFieldIntent | PASS |
| 10 | shouldGiveVersionSameNumericBonusAsNumber | PASS |
| 11 | shouldReturnQueryHitWithTerminalUnitIdentity | PASS |
| 12 | shouldExposeDisplayTextAndDescriptionWithoutFullItemsJson | PASS |
| 13 | shouldReturnRerankedResultsWithFieldIntentFirst | PASS |

### 4.4 全量 Maven Test

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：**Tests run: 947, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS

基线：Phase 1B ranking fix 全量为 947。本轮无新增测试（仅修改构造方式），总数保持 947。

## 5. 是否仍只保留 terminal unit rerank 一个变量

是。本轮只修改了数值问法信号的来源（Java 硬编码 → YAML 配置），未新增任何 rerank 规则、权重、信号类型或业务语义映射。Rerank 算法的所有其他部分（字段 token 匹配、value 封顶、sibling boost、无信号不重排）保持不变。

## 6. 明确未 Stage、未 Commit、未 Push

本轮所有变更仅在 working tree 中，未执行 `git add`、`git commit`、`git push`。
