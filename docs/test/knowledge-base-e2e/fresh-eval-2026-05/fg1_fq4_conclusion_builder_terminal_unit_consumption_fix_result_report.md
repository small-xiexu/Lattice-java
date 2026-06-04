# FG1/FQ4 Fallback Conclusion Builder Terminal Unit Sibling Selection 修复报告

修复时间：2026-06-02
修复人：agentA
轮次：单根因修复

---

## 1. 根因

`AnswerFallbackConclusionBuilder.buildTerminalUnitExactConclusionLines()` 在多个 terminal unit hit 都通过 `isTerminalHitQueryFocused` 检查后，仅使用 **fused order** 选最优候选。terminal unit siblings（如 `equipment_types[0].late_fee_per_day`、`equipment_types[1].late_fee_per_day`、`borrowing_system.api_endpoint`）共享同一张 fact card 的上下文，其 content + metadata 均会被 query token 命中。fused order 优先排序靠前的 hit，可能选中 sibling 字段（如 `api_endpoint`）而非 question-focused 字段（如 `late_fee_per_day`）。

## 2. 失败类型

证据已存在（terminal units 正确生成且包含中文别名），但 fallback conclusion builder 因共享 card 上下文的 sibling 竞争误选非问题目标字段。

## 3. 修改文件清单

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | `buildTerminalUnitExactConclusionLines()` 增加字段级 token 匹配度排序 |

## 4. 修复逻辑

### 4.1 问题

```java
// 旧逻辑：只用 fused order 选最优
double score = fusedOrderScore(fallbackHit, queryArticleHits);
if (bestCandidate == null || score > bestScore) { ... }
```

fused order 是 card 级的排序信号——同一 card 的所有 terminal unit siblings 共享相同的 fused order 位置。当 `isTerminalHitQueryFocused` 对多个 siblings 都返回 true 时，fused order 无法区分哪个 sibling 更匹配问题字段。

### 4.2 修复

增加 `countFieldLevelTokenMatches()` 作为**主排序**，fused order 仅作为 tiebreaker：

```java
// 新逻辑：字段级 token 匹配度优先，fused order 仅作 tiebreaker
int fieldTokenMatchCount = countFieldLevelTokenMatches(fallbackHit, queryTokens);
double fusedScore = fusedOrderScore(fallbackHit, queryArticleHits);
if (fieldTokenMatchCount > bestFieldTokenMatchCount
        || (fieldTokenMatchCount == bestFieldTokenMatchCount && fusedScore > bestFusedOrderScore)) { ... }
```

`countFieldLevelTokenMatches()` 仅检查 terminal unit 的字段级元数据：

| 元数据字段 | 说明 |
|---|---|
| `displayText` | 终端字段的展示文本（如 `late_fee_per_day = 20` 中的 `late_fee_per_day`） |
| `fieldAliases` | 字段的中文别名（如 `逾期日费`、`每日逾期费`） |
| `fieldDescription` | 字段描述 |

这些元数据是**字段独有的**，不会被 sibling terminal unit 共享。例如：
- `late_fee_per_day` 的 fieldAliases 包含 `逾期罚金`
- `api_endpoint` 的 fieldAliases 包含 `API地址`
- 查询 `逾期罚金` 时，`late_fee_per_day` 的 fieldTokenMatchCount 更高

### 4.3 为什么不是 case 特判

- 不依赖任何具体业务词（如 `逾期罚金`、`精密仪器`、`押金`）
- 不依赖任何文件名、文档标题、card type
- 不依赖任何 query 文本硬编码分支
- 不依赖任何特定 terminal unit 的 keyPath 或概念 ID
- 仅使用通用的字段级 token 匹配度——任何语言（中文/英文）的 query token 匹配字段别名/描述都能正确排序
- 逻辑只改变候选排序，不改变候选池的准入条件

## 5. 可能影响的场景

- **正面**：多 terminal unit sibling 竞争场景下，字段级匹配度更准确地选出 question-focused 字段
- **中性**：单 terminal unit 或无 sibling 竞争场景行为完全不变（所有 candidate 的 fieldTokenMatchCount 相同时回退到 fused order）
- **风险**：如果某个 terminal unit 的 fieldAliases 恰好不包含 query token 但正确答案就是它——此时回退到 fused order，与旧逻辑一致

## 6. 验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: BLOCKER=0 (无输出)
mvn test: 995/0/0/0, BUILD SUCCESS
```

## 7. 残留风险

- `countFieldLevelTokenMatches` 依赖 metadataJson 中的 `displayText`/`fieldAliases`/`fieldDescription` 字段名——如果 JSON key 名在未来版本变更，会影响匹配效果
- 字段级元数据中的 token 粒度匹配是简单字符串包含，不做语义归一；理论上可能出现 token 误匹配，但概率远低于全文 haystack 匹配
- 未做端到端 Public Eval 重新验证（留给 agentD）

## 8. 下一步

交给 agentD 做端到端验证：
1. 清库 + 重新导入资料 + 重新编译
2. 运行 FG1 和 FQ4，确认 fallback conclusion 选中正确的 terminal unit 字段
3. 运行完整 Public Eval 2 回归，确认无引入新回归
4. 如果仍有 failure，输出 failure 根因分析
