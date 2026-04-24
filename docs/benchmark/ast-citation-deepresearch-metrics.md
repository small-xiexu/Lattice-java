# AST / Citation / Deep Research Benchmark Metrics

## 指标口径

- `unsupportedClaimRate`
  - 定义：`unsupportedClaims / totalClaims`
  - 含义：最终答案里无法被有效证据支持的结论占比，越低越好。
- `citationPrecision`
  - 定义：`verifiedCitations / (verifiedCitations + demotedCitations)`
  - 含义：被保留的引用里，真正能支持 claim 的比例，越高越好。
  - 说明：当场景没有可判定的 verified/demoted 引用时记为 `N/A`，不纳入比较。
- `citationCoverage`
  - 定义：`coveredClaims / totalClaims`
  - 含义：答案里的关键结论中，有多少被有效引用或可接受跳过覆盖，越高越好。
- `multiHopCompleteness`
  - 定义：`matchedExpectedClaims / expectedClaims`
  - 含义：多跳题中，预期应覆盖的关键子结论被真正回答到的比例，越高越好。
- `graphFactAcceptedRate`
  - 定义：`acceptedFacts / expectedFacts`
  - 含义：图谱检索命中的 facts block 中，关键 grounding 事实被保留下来的比例，越高越好。
  - 说明：该指标当前只用于 Java 版，因为原版 `/Users/sxie/xbk/Lattice` 没有同构的 graph channel shadow 指标。

## 汇总方式

- `unsupportedClaimRate`、`citationCoverage`
  - 先累加所有场景的 claim 数和对应分子，再做全局除法。
- `citationPrecision`
  - 先累加所有场景的 `verifiedCitations` 与 `demotedCitations`，再做全局除法。
- `multiHopCompleteness`
  - 先累加所有场景的 `matchedExpectedClaims` 与 `expectedClaims`，再做全局除法。

## 题集与 Runner

- 共享题集：`src/test/resources/benchmarks/ast-citation-deepresearch/shared-dataset.json`
- Java shadow runner：`src/test/java/com/xbk/lattice/benchmark/AstCitationDeepResearchBenchmarkRunner.java`
- TS shadow runner：`.claude/benchmarks/ast-citation-deepresearch/ts-shadow-runner.ts`

## 执行命令

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AstCitationDeepResearchBenchmarkRunner test
```

## 当前门槛

判定“超过原版”需同时满足：

- `unsupportedClaimRate` 低于原版
- `citationPrecision` 不低于原版
- `multiHopCompleteness` 高于原版
