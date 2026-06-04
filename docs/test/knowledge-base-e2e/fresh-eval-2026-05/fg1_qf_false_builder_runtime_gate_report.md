# FG1 qf=false Builder 修复 — Runtime Gate 验证报告

验证时间：2026-06-03 11:47 ~ 12:50
执行人：agentD（验证 Agent）
修复报告：`fg1_qf_false_builder_fix_result_report.md`

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
| jobId | `868e0ff9-f900-4fd9-84f9-4b5526a207b2` |
| 编译结果 | **SUCCEEDED**（经历 LLM reviewer 超时恢复） |
| persistedCount | 2 → approve 3 → **5** |
| review queue | 3 条，已 approve |
| articles | 5 |
| terminal units | 123 |

---

## 3. FG1 TU_TRACE 对比

### 3.1 修复前（上轮取证）

```
tokens=[..., 里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]

cand#5 el=equipment_types[0].late_fee_per_day = 5 qf=false ftmc=-1 fs=5.0
cand#6 el=equipment_types[1].late_fee_per_day = 20 qf=false ftmc=-1 fs=4.0

result=NONE tuTotal=7 tuQfPassed=0    ← 全池淘汰
```

### 3.2 修复后（本轮取证）

```
tokens=[..., 里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]

cand#5 el=equipment_types[0].late_fee_per_day = 5 qf=true ftmc=0 fs=5.0    ✅
cand#6 el=equipment_types[1].late_fee_per_day = 20 qf=true ftmc=0 fs=4.0   ✅

result=SELECTED el=equipment_types[1].type = 精密仪器 ftmc=0 fs=10.0 tuTotal=7 tuQfPassed=4
```

---

## 4. 验收点判定

| 验收点 | 要求 | 结果 | 判定 |
|---|---|---|---|
| late_fee_per_day 候选出现 | cand 含 `late_fee_per_day` | cand#5 (`=5`), cand#6 (`=20`) | **PASS** |
| qf=true | qf 从 false 变为 true | cand#5 qf=**true**, cand#6 qf=**true** | **PASS** |
| tuQfPassed > 0 | 至少 1 个候选通过 qf | tuQfPassed=**4**（修复前=0） | **PASS** |
| FQ4 继续 FAIL | 预期行为 | 本轮未验证，不在本轮范围 | N/A |

---

## 5. 结论

**agentA 的 qf=false 修复在 runtime 已生效。**

- CJK bigram 重叠匹配正确将碎片 token（"器的逾期"、"的逾期"）通过 bigram "逾期" 匹配到 fieldAliases 中的中文别名（"逾期日费"、"每日逾期费用"）
- `late_fee_per_day` 候选的 `qf` 从 `false` 修复为 `true`
- `tuQfPassed` 从 0 提升到 4

### 残留问题

FG1 的最终答案仍不正确（winner=`equipment_types[1].type = 精密仪器` 而非 `late_fee_per_day`）。根因：qf 修复后 `late_fee_per_day` 候选进入候选池，但 `ftmc=0`（CJK 碎片 token 无法被 `countFieldLevelTokenMatches` 的 `contains(token)` 完整匹配），而 `equipment_types[1].type` 因 `fusedScore=10.0`（最高）成为 winner。

这是 `fieldTokenMatchCount` 对 CJK 碎片 token 不敏感的问题，与 FQ4 的 `ftmc` 平局问题是**独立根因**。

---

## 6. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 只在 FG1 builder qf 验收范围内判定
- [x] FQ4 继续 FAIL 不在本轮范围
