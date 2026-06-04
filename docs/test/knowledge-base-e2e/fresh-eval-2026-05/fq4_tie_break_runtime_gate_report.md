# FQ4 Terminal Tie-Break 修复 — Runtime Gate 验证报告

验证时间：2026-06-04 11:28 ~ 11:40
执行人：agentD（验证 Agent）
修复报告：`fq4_terminal_tie_break_fix_result_report.md`

---

## 1. 累计 Terminal 修复包

本轮 gate 验证的是累计 terminal 修复包，包含以下已修改的生产文件：

| 文件 | 修复内容 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | qf CJK bigram 重叠 + ftmc CJK bigram 重叠 + **atmc tie-break（本轮新增）** |
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
| 清库 | `bash scripts/reset-lattice-schema.sh` 已执行 |
| LLM 绑定 | 2 连接 + 2 模型 + 11 绑定（含 `compile/field-alias-enricher`） |
| compile jobs | 4 个全部 `SUCCEEDED` |
| review queue | **0**（全部 auto-published，无需人工确认） |
| articles | 4 |
| terminal units | 88 |
| enricher 401 错误 | **0** |

### Enricher 验证

| 字段 | 中文别名（抽样） |
|---|---|
| `deposit_amount` (100) | "押金金额", "保证金金额", "设备押金", "借用押金" |
| `deposit_amount` (500) | "押金金额", "保证金金额", "押金", "保证金" |
| `deposit_amount` (1000) | "押金金额", "保证金金额", "押金数额", "借用押金" |
| `late_fee_per_day` (5) | "每日逾期费", "逾期日费用" |
| `late_fee_per_day` (20) | "每日逾期费用", "逾期日费" |
| `late_fee_per_day` (50) | "每日逾期费", "逾期费用" |

---

## 4. FQ4 `[TU_TRACE]` 现场

### 4.1 查询

```
equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？
```

### 4.2 Trace

```
tokens=[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml,
        常规设备, 和大型设, 的押金分, 别是多少, 常规设, 大型设, 的押金, 是多少, 常规]

cand#1 el=equipment_types[0].approval_required = 设备管理员  qf=true ftmc=3 atmc=0 fs=10.0
cand#2 el=equipment_types[0].type = 常规设备               qf=true ftmc=3 atmc=1 fs=4.0
cand#3 el=equipment_types[0].deposit_amount = 100          qf=true ftmc=5 atmc=3 fs=9.0  ← WINNER
cand#4 el=equipment_types[0].category_id = GEN             qf=true ftmc=3 atmc=1 fs=8.0
cand#5 el=equipment_types[2].deposit_amount = 1000         qf=true ftmc=5 atmc=2 fs=3.0

result=SELECTED el=equipment_types[0].deposit_amount = 100 ftmc=5 atmc=3 fs=9.0
tuTotal=5 tuQfPassed=5
```

### 4.3 API 回答

```
Confirmed evidence: equipment_types[0].deposit_amount = 100 [→ equipment-borrowing-policy.yaml]
answerOutcome: PARTIAL_ANSWER
generationMode: FALLBACK
```

### 4.4 修复前后对比（FQ4）

| 指标 | 修复前 | 修复后 |
|---|---|---|
| `deposit_amount` 在候选池 | YES（cand#4） | YES（cand#3 排名提升） |
| `deposit_amount` ftmc | 3 | **5** |
| `approval_required` ftmc | 3 | 3 |
| `deposit_amount` atmc | N/A | **3**（全场最高） |
| `approval_required` atmc | N/A | **0** |
| ftmc 是否平局 | 是（3 vs 3） | **否**（5 > 3） |
| winner | `approval_required` (fs=10.0) | **`deposit_amount = 100`** |
| answerOutcome | PARTIAL_ANSWER | **PARTIAL_ANSWER**（回答值正确） |

### 4.5 FQ4 结论：**PASS**

`deposit_amount` 的 `ftmc=5` 已超过 `approval_required` 的 `ftmc=3`，不需要 atmc tie-break 介入即已分出胜负。`atmc` 作为安全网保留在排序链中，对所有 terminal unit 候选公平生效。

---

## 5. FG1 `[TU_TRACE]` 保护现场

### 5.1 查询

```
equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？
```

### 5.2 Trace

```
tokens=[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml,
        里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]

cand#1 el=equipment_types[0].approval_required = 设备管理员   qf=false ftmc=-1 atmc=-1 fs=8.0
cand#2 el=equipment_types[1].type = 精密仪器                  qf=true  ftmc=2  atmc=0  fs=11.0
cand#3 el=equipment_types[0].type = 常规设备                  qf=false ftmc=-1 atmc=-1 fs=10.0
cand#4 el=equipment_types[2].type = 大型设备                  qf=false ftmc=-1 atmc=-1 fs=7.0
cand#5 el=equipment_types[0].late_fee_per_day = 5             qf=true  ftmc=3  atmc=3  fs=6.0
cand#6 el=equipment_types[1].late_fee_per_day = 20            qf=true  ftmc=5  atmc=3  fs=5.0  ← WINNER
cand#7 el=approval_chain[2].responsibility = ...              qf=true  ftmc=2  atmc=0  fs=9.0
cand#8 el=approval_chain[1].responsibility = ...              qf=false ftmc=-1 atmc=-1 fs=4.0

result=SELECTED el=equipment_types[1].late_fee_per_day = 20 ftmc=5 atmc=3 fs=5.0
tuTotal=8 tuQfPassed=4
```

### 5.3 API 回答

```
Confirmed evidence: equipment_types[1].late_fee_per_day = 20 [→ equipment-borrowing-policy.yaml]
answerOutcome: SUCCESS
```

### 5.4 FG1 结论：**PASS**

`late_fee_per_day = 20` 的 `ftmc=5` 远高于第二名（`精密仪器.type` ftmc=2），不需要 atmc tie-break 介入。atmc 新增排序未对 FG1 产生任何负面影响。

---

## 6. 最终结论

### **FQ4: PASS | FG1: PASS**

| 题号 | 判定 | 说明 |
|---|---|---|
| FQ4 | **PASS** | winner 从 `approval_required` 变为 `deposit_amount = 100` |
| FG1 | **PASS** | winner 保持 `late_fee_per_day = 20`，atmc 未干扰 |

### 候选供给/enricher/runtime 配置

| 项 | 状态 |
|---|---|
| enricher 中文别名 | **已生成**（0 条 401 错误） |
| `deposit_amount` 进入 builder 候选池 | **是** |
| `late_fee_per_day` 进入 builder 候选池 | **是** |
| LLM 绑定 | **就绪**（11 条） |
| review queue | **空**（全部 auto-published） |

---

## 7. 下一步建议

1. FQ4 + FG1 双 PASS 已达标，可考虑跑完整 Public Eval 2 做全面回归
2. 建议同步跑 Public Eval 1 保护（Q6 terminal field alias + S2 chunk identity）
3. 不在此轮继续扩大 builder 排序逻辑

---

## 8. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥（API key 已脱敏）
- [x] LLM 绑定通过 Admin API 配置（运行时数据）
- [x] 所有结论基于 runtime `[TU_TRACE]` 日志 + 数据库证据
