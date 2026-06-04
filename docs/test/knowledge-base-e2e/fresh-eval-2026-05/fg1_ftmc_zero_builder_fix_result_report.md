# FG1 ftmc=0 Builder 修复报告

修复时间：2026-06-03
执行人：agentA
根因：FG1 — `countFieldLevelTokenMatches` 对 CJK 碎片 token 不敏感

---

## 1. 修改文件

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | `countFieldLevelTokenMatches` 新增 CJK bigram 重叠匹配 fallback（复用已有 `hasCjkOverlap`） |

## 2. 问题

qf 修复已生效（`late_fee_per_day` 候选 qf=true），但 `ftmc=0` 导致候选无法区分：

```
cand#5 el=equipment_types[0].late_fee_per_day = 5 qf=true ftmc=0 fs=5.0
cand#6 el=equipment_types[1].late_fee_per_day = 20 qf=true ftmc=0 fs=4.0
cand#2 el=equipment_types[1].type = 精密仪器 qf=true ftmc=0 fs=10.0
result=SELECTED equipment_types[1].type = 精密仪器 ftmc=0 fs=10.0
```

`countFieldLevelTokenMatches` 使用 `fieldHaystack.contains(token)` 做完整字符串匹配。CJK 碎片 token（"器的逾期"、"的逾期"）无法以完整子串形式匹配 fieldAliases（"逾期日费"、"每日逾期费用"），导致 `ftmc=0`。全池同处 `ftmc=0`，fusedScore tiebreaker 选中 `精密仪器`（fs=10.0）。

## 3. 修复

在 `countFieldLevelTokenMatches` 中，当完整 token 匹配失败时，使用 `hasCjkOverlap` 做 CJK bigram 重叠匹配（该方法已在上一轮 `isTerminalHitQueryFocused` 中实现，本轮复用）：

```java
if (fieldHaystack.contains(lower)) { matchCount++; continue; }  // 原有完整匹配
if (hasCjkOverlap(fieldHaystack, token)) matchCount++;            // CJK bigram fallback
```

### 效果（预期）

| token | bigrams | fieldHaystack 中命中 | 结果 |
|---|---|---|---|
| `器的逾期` | `器的`,`的逾`,`逾期` | `逾期` in "逾期日费" | **matchCount++** |
| `的逾期` | `的逾`,`逾期` | `逾期` in "逾期日费" | **matchCount++** |
| `金是多` | `金是`,`是多` | 两者均不在 fieldHaystack | 不匹配 |

`late_fee_per_day` 候选的 `ftmc` 从 0 提升到 ≥1，`精密仪器` type 候选的 `ftmc` 保持 0，排序正确选出 `late_fee_per_day`。

## 4. 为什么不是 case 特判

- 使用通用 `hasCjkOverlap`：检查 CJK bigram 重叠，与业务词无关
- 完整匹配优先，CJK fallback 作为兜底
- 在 `isTerminalHitQueryFocused` 和 `countFieldLevelTokenMatches` 中使用同一通用逻辑
- 不修改 fieldHaystack 内容或排序规则

## 5. 为什么不处理 FQ4

FQ4 的根因是 `ftmc` 平局（deposit_amount ftmc=3 = approval_required ftmc=3）+ fusedScore tiebreaker 选错。这需要独立的 tie-break 修复（如终端键精确匹配优先），不在本轮范围。

## 6. `[TU_TRACE]` 保留状态

已保留。

## 7. 验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: BLOCKER=0
mvn test: 995/0/0/0, BUILD SUCCESS
```

## 8. 交给 agentD

1. 正常清库 → 编译 → approve review queue
2. 验证 FG1 的 `[TU_TRACE]` 日志：
   - 确认 `late_fee_per_day` 候选的 `ftmc >= 1`
   - 确认 winner 是 `late_fee_per_day` 而非其他 sibling
3. 如果 FG1 PASS，再验证 FQ4（继续 FAIL 是预期，tie-break 未修）
