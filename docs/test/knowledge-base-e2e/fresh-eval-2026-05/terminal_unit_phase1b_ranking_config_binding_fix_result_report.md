# Terminal Unit Phase 1B Ranking Config Binding Fix Result Report

验证时间：2026-05-29
验证人：agentA
验证对象：修复 FactCardTerminalUnitFtsSearchService 未真正使用 Spring 绑定 QuerySemanticRules 的问题

## 1. 结论

本轮修复了上轮红线收口的遗留问题：`FactCardTerminalUnitFtsSearchService` 的 `@Autowired` 构造器中用 `new QuerySemanticRules()` 创建 reranker，导致 `lattice-query-semantic.yml` 的 `numeric-value-intent-signals` 配置不会进入真实服务链路。

修复方式：`@Autowired` 构造器新增 `QuerySemanticRules` 参数，由 Spring 注入携带 YAML 配置绑定的实例，再传入 `FactCardTerminalUnitIntentReranker`。同时删除未使用的 `semanticRules` 字段。

本轮只修配置绑定问题，不做 fresh eval 服务级结论。未 stage、未 commit、未 push。

## 2. 修改文件清单

### 修改文件

| 文件 | 变更说明 |
|---|---|
| `src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchService.java` | `@Autowired` 构造器签名从 2 参数变为 3 参数，新增 `QuerySemanticRules semanticRules` 由 Spring 注入；删除未使用的 `semanticRules` 实例字段；3 参数（测试用）构造器移除无意义的 `this.semanticRules = new QuerySemanticRules()` 赋值 |

### 未修改

- `FactCardTerminalUnitIntentReranker.java`：未修改（上轮已正确依赖 `QuerySemanticRules`）
- `QuerySemanticRules.java`：未修改
- `lattice-query-semantic.yml`：未修改
- 测试文件：未修改（现有测试使用 1 参数或 3 参数构造器，不受影响）
- 所有禁令范围内的文件：未修改

## 3. 问题与修复

### 3.1 问题

上轮红线收口将 `NUMERIC_QUESTION_SIGNALS` 从 Java 硬编码迁移到 `QuerySemanticRules` / `lattice-query-semantic.yml`，但 `FactCardTerminalUnitFtsSearchService` 的 `@Autowired` 构造器：

```java
@Autowired
public FactCardTerminalUnitFtsSearchService(
        FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository,
        FtsConfigResolver ftsConfigResolver
) {
    this(factCardTerminalUnitJdbcRepository, ftsConfigResolver,
            new FactCardTerminalUnitIntentReranker(new QuerySemanticRules()));
}
```

`new QuerySemanticRules()` 创建的是普通 Java 对象，只有硬编码默认值，**不会经过 Spring `@ConfigurationPropertiesScan` 的 YAML 绑定**。因此即使 `lattice-query-semantic.yml` 中配置了 `numeric-value-intent-signals`，真实服务链路中 reranker 使用的仍是代码默认值，YAML 配置形同虚设。

此外，类中存在一个 `private final QuerySemanticRules semanticRules` 字段，在 3 参数构造器中被赋值为 `new QuerySemanticRules()`，但 `search()` 等方法中从未使用该字段，属于无用代码。

### 3.2 修复

1. **`@Autowired` 构造器新增 `QuerySemanticRules` 参数**：Spring 通过 `@ConfigurationPropertiesScan`（`LatticeApplication` 已启用）自动扫描 `@ConfigurationProperties` 类并注册为 Bean。新增参数后，Spring 注入的是携带 YAML 配置绑定的实例。

   ```java
   @Autowired
   public FactCardTerminalUnitFtsSearchService(
           FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository,
           FtsConfigResolver ftsConfigResolver,
           QuerySemanticRules semanticRules   // Spring 注入，携带 YAML 绑定
   ) {
       this(factCardTerminalUnitJdbcRepository, ftsConfigResolver,
               new FactCardTerminalUnitIntentReranker(semanticRules));
   }
   ```

2. **删除未使用的 `semanticRules` 字段**：该字段仅在构造器中赋值，从未在 `search()` 或任何其他方法中使用。reranker 已持有自己的 `QuerySemanticRules` 引用，服务层无需冗余存储。

3. **1 参数构造器和 3 参数（测试用）构造器保持不变**：1 参数构造器仍使用 `new QuerySemanticRules()`（默认值），供非 Spring 测试路径使用。3 参数构造器接收显式 `intentReranker`，测试可直接注入 mock/stub。

### 3.3 配置绑定链路验证

```
lattice-query-semantic.yml
  → @ConfigurationPropertiesScan (LatticeApplication)
    → QuerySemanticRules Bean (YAML 绑定完成)
      → @Autowired FactCardTerminalUnitFtsSearchService 构造器
        → new FactCardTerminalUnitIntentReranker(semanticRules)
          → semanticRules.containsAnyNumericValueIntentSignal(question)
```

Spring 启动时，`@ConfigurationPropertiesScan` 扫描到 `QuerySemanticRules`（`@ConfigurationProperties(prefix = "lattice.query.semantic")`），将 `lattice-query-semantic.yml` 中的 `numeric-value-intent-signals` 列表绑定到 Bean。该 Bean 被注入到 `FactCardTerminalUnitFtsSearchService`，再传入 `FactCardTerminalUnitIntentReranker`，最终在 `hasNumericQuestionSignal()` 中生效。

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

## 5. 是否仍只保留 terminal unit rerank 一个变量

是。本轮只修复了 `QuerySemanticRules` 的 Spring 注入链路，使 YAML 配置能真正进入 reranker。未新增任何 rerank 规则、权重、信号类型或业务语义映射。Rerank 算法所有部分保持不变。

## 6. 明确未 Stage、未 Commit、未 Push

本轮所有变更仅在 working tree 中，未执行 `git add`、`git commit`、`git push`。
