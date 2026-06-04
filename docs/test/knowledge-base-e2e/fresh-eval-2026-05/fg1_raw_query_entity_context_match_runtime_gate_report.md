# FG1 Raw Query Entity Context Match — Runtime Gate 验证报告

验证时间：2026-06-04 15:24 ~ 15:37
执行人：agentD（验证 Agent）
修复报告：`fg1_raw_query_entity_context_match_fix_result_report.md`（agentA）

---

## 1. Git Status 摘要

本轮验证的是累计 terminal 修复包 + raw query entity context match 修复：

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | qf + ftmc + atmc + entityContextMatchesQuery + 多目标聚合 + **raw query display value match（本轮新增）** |
| `FactCardTerminalUnitMaterializer.java` | contextDisplayValues 写入 |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | bootstrap guard 移除 |
| `FactCardTerminalUnitFtsSearchService.java` | candidate supply 修订 |
| `FactCardTerminalUnitIntentReranker.java` | 字段意图信号 scoring |

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 3. 编译信息

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| LLM 绑定 | 11 条（含 `compile/field-alias-enricher`） |
| compile jobs | 4，全部 `SUCCEEDED` |
| review queue | 1 条（equipment borrowing policy），已 approve |
| articles | 4 |
| terminal units | 118 |
| enricher 401 错误 | **0** |

---

## 4. SQL Metadata Gate

```sql
SELECT terminal_key, parent_path, display_text,
       metadata_json::jsonb -> 'contextDisplayValues' AS ctx
FROM lattice.fact_card_terminal_units
WHERE terminal_key IN ('deposit_amount','late_fee_per_day','max_borrow_days')
ORDER BY terminal_key, parent_path;
```

| terminal_key | parent_path | contextDisplayValues |
|---|---|---|
| deposit_amount | equipment_types[0] | `["常规设备", "设备管理员"]` |
| deposit_amount | equipment_types[1] | `["精密仪器", "实验室主任"]` |
| deposit_amount | equipment_types[2] | `["大型设备", "院系分管领导"]` |
| late_fee_per_day | equipment_types[0] | `["常规设备", "设备管理员"]` |
| late_fee_per_day | equipment_types[1] | `["精密仪器", "实验室主任"]` |
| late_fee_per_day | equipment_types[2] | `["大型设备", "院系分管领导"]` |
| max_borrow_days | equipment_types[0] | `["常规设备", "设备管理员"]` |
| max_borrow_days | equipment_types[1] | `["精密仪器", "实验室主任"]` |
| max_borrow_days | equipment_types[2] | `["大型设备", "院系分管领导"]` |

**SQL gate：PASS**（9/9 条含 contextDisplayValues）

---

## 5. FG1 多目标（主 Gate）

### 5.1 TU_TRACE

```
tokens=[..., 里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]

cand#2 el=equipment_types[1].type = 精密仪器               qf=true  ftmc=2 atmc=0 fs=10.0
cand#5 el=equipment_types[0].late_fee_per_day = 5          qf=true  ftmc=3 atmc=3 fs=5.0
cand#6 el=equipment_types[1].late_fee_per_day = 20         qf=true  ftmc=5 atmc=3 fs=4.0  ← WINNER
cand#7 el=approval_chain[2].responsibility = ...           qf=true  ftmc=2 atmc=0 fs=8.0

result=SELECTED el=equipment_types[1].late_fee_per_day = 20 ftmc=5 atmc=3 fs=4.0 tuTotal=7 tuQfPassed=4
additionalCandidates=1 deduped=1 selected=1
```

Phase 2 附加候选成功：
- cand#5 `late_fee_per_day = 5`（同 `terminalKey` + 不同 `parentPath`）通过 raw query display value match：归一化后原始 question 包含 `常规设备` → `contextDisplayValues[0]` 命中。

### 5.2 API 回答

```
- Confirmed evidence: equipment_types[1].late_fee_per_day = 20 [→ equipment-borrowing-policy.yaml]
- Confirmed evidence: equipment_types[0].late_fee_per_day = 5 [→ equipment-borrowing-policy.yaml]
```

- 双值输出：20 + 5 ✅
- 未选中 `type/approval_required/api_endpoint` ✅
- `additionalCandidates=1`，raw query match 消费了常规设备的 `late_fee_per_day = 5` ✅

### 5.3 FG1 多目标判定：**PASS**

---

## 6. FQ4 多目标（保护）

### 6.1 TU_TRACE

```
tokens=[..., 常规设备, 和大型设, 的押金分, 别是多少, 常规设, 大型设, 的押金, 是多少, 常规]

cand#4 el=equipment_types[0].deposit_amount = 100   qf=true ftmc=5 atmc=3 fs=9.0  ← WINNER
cand#5 el=equipment_types[2].deposit_amount = 1000  qf=true ftmc=5 atmc=2 fs=4.0

result=SELECTED el=equipment_types[0].deposit_amount = 100 ftmc=5 atmc=3 fs=9.0 tuTotal=5 tuQfPassed=5
additionalCandidates=1 deduped=1 selected=1
```

### 6.2 API 回答

```
- Confirmed evidence: equipment_types[0].deposit_amount = 100
- Confirmed evidence: equipment_types[2].deposit_amount = 1000
```

双值输出，未选中 `approval_required`。

### 6.3 FQ4 判定：**PASS**

---

## 7. FQ3 单目标保护

### 7.1 API 回答

```
- Confirmed evidence: equipment_types[1].max_borrow_days = 7
```

### 7.2 TU_TRACE

无 `additionalCandidates` 行。单目标问题，仅返回 `max_borrow_days = 7`，未带出常规设备/大型设备的同名字段。

### 7.3 FQ3 判定：**PASS**

---

## 8. 单问 FG1 保护

### 8.1 API 回答

```
- Confirmed evidence: equipment_types[1].late_fee_per_day = 20
```

### 8.2 TU_TRACE

无 `additionalCandidates` 行。cand#8 (`equipment_types[0].late_fee_per_day = 5`) 虽有同 `terminalKey`，但：
- `entityContextMatchesQuery` 不通过（token 匹配失败 + raw query 中无"常规设备"）
- 且 `ftmc=1 < max(1,5/2)=2`（minThreshold 过滤）

单值输出，未带出 5 或 50。

### 8.3 单问 FG1 判定：**PASS**

---

## 9. 最终判定

| Gate | 判定 |
|---|---|
| 前置门禁（redline + mvn test） | **PASS** |
| SQL metadata gate | **PASS**（9/9 条） |
| FG1 多目标（20 + 5） | **PASS**（additionalCandidates=1） |
| FQ4 多目标（100 + 1000） | **PASS**（additionalCandidates=1） |
| FQ3 单目标保护 | **PASS** |
| 单问 FG1 保护 | **PASS** |

### **总体判定：PASS**

raw query entity context match 修复已在 runtime 生效。FG1 多目标首次输出双值（20 + 5），且 FQ3/单问 FG1 的单目标保护未被破坏。

---

## 10. 下一步建议

**可以进入完整 Public Eval 2 + Public Eval 1 保护回归。**

当前 targeted gate 已全部通过，建议：
1. 跑完整 Public Eval 2（FQ1-FQ12 + FS1-FS4 + FG1-FG3）作为全面回归
2. 跑 Public Eval 1 保护回归（Q6 terminal field alias + S2 chunk identity）
3. 不在此轮继续扩大 builder 或 context guard 逻辑

---

## 11. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] LLM 绑定通过 Admin API 配置（运行时数据）
- [x] 所有结论基于 runtime `[TU_TRACE]` 日志 + SQL 查询证据
