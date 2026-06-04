# Terminal Unit Candidate Supply 修复修订报告

修复时间：2026-06-02
执行人：agentA
类型：修正上一轮 patch 的两个实现缺口

---

## 1. 修改文件

| 文件 | 变更 |
|---|---|
| `FactCardTerminalUnitFtsSearchService.java` | `search()`：DB raw limit 扩大为 `requestedLimit*3`（最少 15），rerank 后 `subList(0, requestedLimit)` 截回 |
| `FactCardTerminalUnitIntentReranker.java` | 移除 early-exit gate + 排序改为字段意图信号优先（primary sort）+ adjustedScore 降序（tiebreaker） |

## 2. 修正点 1：截回原始 limit

**修复前**：`safeLimit(limit)` 返回 `limit*3`，rerank 后直接返回全部扩展结果，未截断。

**修复后**：
```java
int requestedLimit = limit <= 0 ? 5 : limit;
int rawLimit = Math.max(requestedLimit * 3, 15);
// ... DB query with rawLimit
// ... rerank
if (hits.size() > requestedLimit) {
    return new ArrayList<>(hits.subList(0, requestedLimit));
}
```

## 3. 修正点 2：字段意图显式优先

**修复前**：排序仅使用 `adjustedScore` 降序。adjustedScore = `originalScore + fieldMatchCount*1.0 + valueMatchCount*0.1 + siblingBoost*6.0`。value/context-only sibling 的 DB score（如 81）远高于字段别名 hit（如 15），1.0 加权无法翻转。

**修复后**：两级排序：
1. Primary: `getFieldIntentSignal()` — terminalKeyMatchCount > 0 或 fieldMatchCount > 0 时返回 1，否则 0。按升序排列（1 在前，0 在后）。
2. Tiebreaker: `adjustedScore` 降序 + `originalIndex` 升序。

```java
profiles.sort(Comparator
    .comparingInt((HitProfile p) -> p.getFieldIntentSignal() > 0 ? 0 : 1)
    .thenComparing(Comparator.comparingDouble(HitProfile::getAdjustedScore).reversed())
    .thenComparingInt(HitProfile::getOriginalIndex));
```

**为什么不用 `reversed()` 级联**：`thenComparingDouble().reversed()` 会反转整条比较链，抵消前序 `reversed()`。改用显式 lambda 映射 `fIntentSignal > 0 ? 0 : 1` 确保升序自然排在前面。

## 4. 为什么不是 case 特判

- 扩大候选窗口：通用的 `requestedLimit*3` 策略
- 字段意图优先：基于 `terminalKeyMatchCount` / `fieldMatchCount`——通用字段画像信号
- 不依赖任何业务词、字段名、文件名、查询文本
- 不修改 weights 本身（1.0 field / 0.1 value / 6.0 sibling boost）

## 5. 验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: BLOCKER=0
FactCardTerminalUnitIntentRerankerTests: 10/0/0/0
FactCardTerminalUnitFtsSearchServiceTests: 3/0/0/0
mvn test (full): 995/0/0/0, BUILD SUCCESS
```

## 6. 交给 agentD

1. 清库 → 编译（默认 reviewMode=LLM）
2. 确认 persistedCount > 0 且 terminal units 存在
3. 跑 Public Eval 2（FQ4/FG1 重点）+ Public Eval 1 保护回归
