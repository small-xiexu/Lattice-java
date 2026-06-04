# FG1 qf=false Builder 修复报告

修复时间：2026-06-03
执行人：agentA
根因：FG1 — `isTerminalHitQueryFocused` 对 CJK 碎片 token 过严

---

## 1. 修改文件

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | `isTerminalHitQueryFocused` 新增 CJK bigram 重叠匹配。新增 `hasCjkOverlap` helper。 |

## 2. 问题

runtime trace 确认：

```
[T] tokens=[..., 里精密仪, 器的逾期, 金是多少, 备的逾期, ..., 的逾期, 金是多, ...]
[T] cand#5 el=equipment_types[0].late_fee_per_day = 5 qf=false
[T] cand#6 el=equipment_types[1].late_fee_per_day = 20 qf=false
[T] result=NONE tuTotal=7 tuQfPassed=0
```

CJK tokenizer 将"精密仪器的逾期罚金"切成碎片 token（"器的逾期"、"的逾期"、"金是多"）。`isTerminalHitQueryFocused` 使用 `haystack.contains(token)` 做完整字符串匹配——碎片 token 不在 haystack 中以完整子串形式存在，导致 qf=false，全池淘汰。

## 3. 修复

在 `isTerminalHitQueryFocused` 中，当完整 token 匹配失败且 token 含 >=2 个 CJK 字符时，逐 2-char CJK bigram 检查重叠：

```java
if (haystack.contains(lowerToken)) {
    return true;  // 原有完整匹配
}
if (hasCjkOverlap(haystack, token)) {
    return true;  // 新增 CJK bigram 重叠匹配
}
```

`hasCjkOverlap`:
1. 统计 token 中 CJK Unified Ideographs 字符数（<2 则跳过）
2. 逐 CJK bigram（连续 2 个 CJK 字符）检查是否在 haystack 中出现

### 效果

| token | bigrams | haystack 中命中 | 结果 |
|---|---|---|---|
| `器的逾期` | `器的`,`的逾`,`逾期` | `逾期` in "逾期日费"/"每日逾期费用" | **qf=true** |
| `的逾期` | `的逾`,`逾期` | `逾期` in "逾期日费" | **qf=true** |

## 4. 为什么不是 case 特判

- **通用 CJK bigram 匹配**：不依赖任何特定业务词（"逾期"、"罚金"、"押金"等）
- **仅对 CJK 字符生效**：通过 `Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS` 检测，对英文/数字 token 保持原有行为
- **仅在完整匹配失败后作为 fallback**：先尝试 `contains(token)`，失败后才尝试 bigram 重叠
- **不修改 `countFieldLevelTokenMatches`**：仅影响 qf 准入门，不影响排序权重

## 5. 为什么不处理 FQ4

FQ4 是另一个根因：`ftmc` 平局（deposit_amount ftmc=3 = approval_required ftmc=3），fusedScore tiebreaker 选错。这需要修 tie-break 逻辑（如终端键精确匹配优先级），不在本轮范围。

## 6. `[TU_TRACE]` 保留状态

已保留。agentD 下一轮 gate 中可验证 FG1 的 `qf` 已从 false 变为 true。

## 7. 验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: BLOCKER=0
mvn test: 995/0/0/0, BUILD SUCCESS
```

## 8. 交给 agentD

1. 正常清库 → 编译 → approve review queue
2. 只验证 FG1 的 `[TU_TRACE]` 日志：
   - 确认 `late_fee_per_day` 候选的 `qf=true`
   - 确认 `tuQfPassed > 0`
3. FG1 是否最终 PASS 不作为本轮门禁
4. FQ4 继续 FAIL 是预期行为（tie-break 未修）
