# Terminal Unit Phase 1D-2 Scope Fix: Context Rerank 收窄与测试脱敏

修复时间：2026-05-29
执行人：agentA
修复类型：scope/redline 收窄 — 不扩功能、不追服务级 PASS

---

## 1. 修正摘要

本轮修正 Phase 1D-2 Reranker context fix 的两个问题：

| # | 问题 | 修正 |
|---|---|---|
| 1 | 测试含 fresh eval 语义词（精密仪器、借用天数、equipment_types、max_borrow_days、实验室负责人） | 全部替换为中性 synthetic 词（甲类对象、辅助角色、group_alpha、target_metric） |
| 2 | context-only 可单独触发 rerank（无 field match、无 numeric intent 时仍允许重排），风险偏大 | 收窄为：context 仅在有 fieldMatch 或 queryHasNumericIntent 时参与 adjustedScore 计算，且 context-only 不触发 early return 放行 |
| 3 | context-only 测试 query "乙类项的值是多少" 含 "多少"，命中 `numericValueIntentSignals`，导致 `queryHasNumericIntent=true`，测试未覆盖真正的 context-only 路径 | query 改为 "乙类项的定义是什么"，"什么" 不在任何 numeric intent 信号列表中 |

## 2. Context-Only 触发条件收窄

### 2.1 修改前

```java
// early return: context 可单独放行
boolean anySignal = profiles.stream().anyMatch(
    p -> p.fieldMatchCount > 0 || p.contextMatchCount > 0);
if (!anySignal && !queryHasNumericIntent) return hits;

// adjustedScore: context 无条件参与
adj += p.contextMatchCount * CONTEXT_TOKEN_WEIGHT;
```

### 2.2 修改后

两阶段循环：

**Phase 1** — 收集所有 match count，同时计算 `hasFieldSignal`：
```java
for (HitProfile p : profiles) {
    p.fieldMatchCount = countFieldMatches(p, queryTokens);
    p.terminalKeyMatchCount = countTerminalKeyMatches(p, queryTokens);
    p.valueMatchCount = countValueOnlyMatches(p, queryTokens);
    p.contextMatchCount = countContextMatches(p, queryTokens);
}
boolean hasFieldSignal = profiles.stream().anyMatch(p -> p.fieldMatchCount > 0);
boolean canUseContext = hasFieldSignal || queryHasNumericIntent;
```

**Phase 2** — 只在授权条件下让 context 参与 adjustedScore：
```java
for (HitProfile p : profiles) {
    double adj = p.originalScore;
    adj += p.fieldMatchCount * FIELD_TOKEN_WEIGHT;
    if (canUseContext) {                          // <-- 条件化
        adj += p.contextMatchCount * CONTEXT_TOKEN_WEIGHT;
    }
    adj += Math.min(p.valueMatchCount, 5) * VALUE_TOKEN_WEIGHT;
    if (queryHasNumericIntent && isNumericLikeType(p.valueType)) {
        adj += NUMERIC_VALUE_TYPE_BONUS;
    }
    p.adjustedScore = adj;
}
```

**Early return** — context 不单独放行：
```java
if (!hasFieldSignal && !queryHasNumericIntent) {
    return hits;
}
```

### 2.3 触发条件决策表

| fieldMatch > 0 | queryHasNumericIntent | contextMatch > 0 | 是否 rerank | context 权重 | 说明 |
|---|---|---|---|---|---|
| ✓ | - | - | ✓ | ✓ (0.3) | fieldMatch 触发 |
| ✗ | ✓ | ✓ | ✓ | ✓ (0.3) | numeric intent + context 触发 |
| ✗ | ✓ | ✗ | ✓ | N/A | numeric intent 单独触发（valueType bonus 生效） |
| ✗ | ✗ | ✓ | **✗** | **✗** | **context-only 不触发，保持原顺序** |
| ✗ | ✗ | ✗ | ✗ | ✗ | 无任何信号，保持原顺序 |

## 3. 测试数据脱敏

### 3.1 替换清单

| 测试方法 | 旧词（fresh eval 相关） | 新词（中性 synthetic） |
|---|---|---|
| `shouldRankContextMatchedNumericTargetAboveValueOnlyStringSibling` | max_borrow_days | target_metric |
| | equipment_types[1] | group_alpha |
| | 精密仪器 | 甲类对象 |
| | 实验室负责人 | 辅助角色 |
| | 精密仪器的单次最长借用天数是多少 | 甲类对象的指标数量是多少 |

### 3.2 替换的测试方法

- `shouldAllowRerankWhenOnlyContextSignalPresentNoNumericIntent` → **替换为** `shouldNotRerankWhenOnlyContextSignalNoFieldMatchNoNumericIntent`
- 旧：断言 context-only 可触发重排（tgt_num 排第一）
- 新：断言 context-only 保持原顺序（first_ctx 排第一）

### 3.3 全量脱敏验证

```bash
grep -n -E "(精密仪器|借用|实验室|equipment|max_borrow|维护周期|borrowing_system|deposit_amount|late_fee|concurrent)" \
  src/test/java/com/xbk/lattice/query/service/FactCardTerminalUnitIntentRerankerTests.java
# 输出：无匹配
```

## 4. 测试结果

### 4.1 git diff --check

```
无输出（通过）
```

### 4.2 Redline 扫描

```
BLOCKER=0, REVIEW=2063, ALLOWLIST=259
```

与修复前完全一致，无新增命中。

### 4.3 定向测试（FactCardTerminalUnitIntentRerankerTests）

```
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

**测试变化明细：**

| 测试 | 变化 | 结果 |
|---|---|---|
| `shouldRankContextMatchedNumericTargetAboveValueOnlyStringSibling` | 数据脱敏，语义不变 | PASS |
| `shouldPrioritizeFieldAliasMatchOverContextMatch` | 无变化 | PASS |
| `shouldHandleMissingOrEmptyFieldDescription` | 无变化 | PASS |
| `shouldPreserveOriginalOrderWhenNoSignalsAtAll` | 无变化 | PASS |
| `shouldNotRerankWhenOnlyContextSignalNoFieldMatchNoNumericIntent` | **修正 query**（"乙类项的定义是什么" 替代 "乙类项的值是多少"，避免 "多少" 误触发 numericIntent） | PASS |
| 原有 9 个测试 | 无变化 | PASS |

### 4.4 全量 mvn test

```
Tests run: 976, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

**全量 976/0/0/0 干净通过**。此前报告中的 963/0/9 为 Surefire fork `ClassNotFoundException` 间歇性问题，本轮重新执行已全部通过。无任何失败与 Reranker 或本轮修改相关。

## 5. 未修改清单（确认）

| 文件/区域 | 状态 |
|---|---|
| `FactCardTerminalUnitMaterializer.java` | **未修改** |
| `FactCardTerminalUnitFtsSearchService.java` | **未修改** |
| `LexicalSearchTokenBudget.java` | **未修改** |
| `SQL / Mapper / schema.sql` | **未修改** |
| `Query fallback / AnswerGeneration* / citation` | **未修改** |
| `QuerySemanticRules.java` / `lattice-query-semantic.yml` | **未修改** |
| `src/main/resources/**` | **未修改** |
| `scripts/**` | **未修改** |
| `AGENTS.md` / `CLAUDE.md` | **未修改** |
| `docs/模型绑定配置参考.md` | **未修改** |
| `special_cases_report.md` | **未修改** |
| Fresh eval 题集/标准答案/验收口径 | **未修改** |

## 6. 下一步

交给 agentD clean schema 验证 Phase 1D-1 + 1D-2 组合效果：

1. 清库重建 + 重新导入 + compile
2. 验证 YAML 5 题目标 terminal unit 排名变化
3. 验证 FQ7/FQ11 保护不退化
4. 输出完整 Fresh Eval 指标

## 合规声明

- 本轮未修改 Materializer、query fallback、SQL、配置、题集
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未新增中文业务词、字段映射、case 特判
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 修改文件数：2（Reranker.java + RerankerTests.java）
- 新增文件数：1（本报告）
