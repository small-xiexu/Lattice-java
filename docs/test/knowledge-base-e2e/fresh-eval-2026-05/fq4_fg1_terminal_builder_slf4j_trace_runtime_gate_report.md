# FQ4/FG1 Terminal Builder SLF4J Trace Runtime Gate 报告

Trace 时间：2026-06-03 11:18
执行人：agentD（验证 Agent）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 2. 编译信息

| 项 | 值 |
|---|---|
| jobId | `f79be879-129c-4ee5-89bf-0a45ba65f39b` |
| 尝试次数 | 1（经历 1 次 LLM 超时恢复） |
| 编译结果 | **SUCCEEDED** |
| persistedCount | 2 → approve 3 → **5** |
| review queue | 3 条，已 approve |
| articles | 5 |
| terminal units | 123 |

---

## 3. FQ4 `[TU_TRACE]` 取证

### 3.1 入口

```
[TU_TRACE] enter fhSize=10 qaSize=10 tokens=[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml, 常规设备, 和大型设, 的押金分, 别是多少, 常规设, 大型设, 的押金, 是多少, 常规]
```

### 3.2 候选池（全部 5 个 TU channel hits）

| cand# | el | qf | ftmc | fs |
|---|---|---|---|---|
| 1 | `equipment_types[0].approval_required = 设备管理员` | true | **3** | **10.0** |
| 2 | `equipment_types[0].type = 常规设备` | true | 3 | 5.0 |
| 3 | `equipment_types[2].type = 大型设备` | true | 1 | 3.0 |
| **4** | **`equipment_types[0].deposit_amount = 100`** | true | **3** | 9.0 |
| **5** | **`equipment_types[2].deposit_amount = 1000`** | true | 1 | 4.0 |

### 3.3 结果

```
result=SELECTED el=equipment_types[0].approval_required = 设备管理员 ftmc=3 fs=10.0 tuTotal=5 tuQfPassed=5
```

### 3.4 FQ4 结论：**builder 内问题**

| 项 | 值 |
|---|---|
| deposit_amount 是否进入 builder 候选池 | **YES**（cand#4, cand#5） |
| deposit_amount 是否 qf=true | **YES**（cand#4 qf=true） |
| deposit_amount[0] ftmc | **3** |
| winner (approval_required) ftmc | **3** |
| deposit_amount[0] fs | 9.0 |
| winner fs | **10.0** |
| 输因 | **ftmc 平局（3 vs 3），fusedScore tiebreaker 选中 approval_required（10.0 > 9.0）** |

---

## 4. FG1 `[TU_TRACE]` 取证

### 4.1 入口

```
[TU_TRACE] enter fhSize=10 qaSize=10 tokens=[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml, 里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]
```

### 4.2 候选池（全部 7 个 TU channel hits）

| cand# | el | qf | ftmc | fs |
|---|---|---|---|---|
| 1 | `equipment_types[0].approval_required = 设备管理员` | false | -1 | 7.0 |
| 2 | `equipment_types[1].type = 精密仪器` | false | -1 | 10.0 |
| 3 | `equipment_types[0].type = 常规设备` | false | -1 | 9.0 |
| 4 | `equipment_types[2].type = 大型设备` | false | -1 | 6.0 |
| **5** | **`equipment_types[0].late_fee_per_day = 5`** | **false** | -1 | 5.0 |
| **6** | **`equipment_types[1].late_fee_per_day = 20`** | **false** | -1 | 4.0 |
| 7 | `approval_chain[2].responsibility = 审批高价值...` | false | -1 | 8.0 |

### 4.3 结果

```
result=NONE tuTotal=7 tuQfPassed=0
```

### 4.4 FG1 结论：**builder 内问题（qf=false 导致全池淘汰）**

| 项 | 值 |
|---|---|
| late_fee_per_day 是否进入 builder 候选池 | **YES**（cand#5, cand#6） |
| late_fee_per_day 是否 qf=true | **NO**（qf=false，ftmc=-1 表示未计算） |
| 全池 qf=true 数 | **0** |
| 输因 | **`isTerminalHitQueryFocused` 对所有候选返回 false**。CJK tokenizer 将"精密仪器的逾期罚金"切分为碎片 token（"里精密仪"、"器的逾期"、"金是多少"），query token "的逾期"/"器的逾期" 不直接等于 fieldAliases 中的 "逾期日费"/"每日逾期费用"，无法通过 `haystack.contains(token)` 检查 |

---

## 5. 最终结论

### **builder 内问题**（两个题均确认）

目标 terminal units **已进入 builder 候选池**（deposit_amount cand#4/#5, late_fee_per_day cand#5/#6）。断点不在 retrieval/reranker 供给侧，在 builder 内。

| 题号 | 根因 | 机制 |
|---|---|---|
| FQ4 | **ftmc 平局，fusedScore tiebreaker 选错** | deposit_amount ftmc=3 = approval_required ftmc=3，但 fs=9.0 < fs=10.0。字段别名"押金"已生效但不足以拉开 ftmc 差距 |
| FG1 | **qf=false，全池淘汰** | CJK tokenizer 产生碎片 token（"器的逾期"），无法匹配 fieldAliases 中的"逾期日费"。`isTerminalHitQueryFocused` 返回 false |

### 唯一下一步根因建议

1. **FQ4**：`countFieldLevelTokenMatches` 的字段级 token 匹配粒度不足以区分 deposit_amount 与 approval_required。两者在当前 token 集中 ftmc 均为 3。需要增强字段级 haystack 中中文别名对 query token 的匹配权重，或为 fieldAliases 匹配提供额外加分。
2. **FG1**：`isTerminalHitQueryFocused` 的 haystack 构建或 token 匹配对 CJK 碎片 token 不友好。query token "的逾期" 与 fieldAliases "逾期日费" 之间缺少子串/重叠匹配逻辑。

---

## 6. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] builder 内问题已通过 [TU_TRACE] 现场取证确认
