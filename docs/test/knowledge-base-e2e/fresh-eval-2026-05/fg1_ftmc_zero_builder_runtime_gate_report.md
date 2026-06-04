# FG1 ftmc=0 Builder 修复 — Runtime Gate 验证报告

验证时间：2026-06-03 16:03 ~ 16:05
执行人：agentD（验证 Agent）
修复报告：`fg1_ftmc_zero_builder_fix_result_report.md`

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
| 清库 | `bash scripts/reset-lattice-schema.sh` 已执行 |
| uploaded files | 4/5（Markdown/YAML/XLSX/CSV 成功；PDF 因 source name 超长失败，不在 FG1 范围） |
| review queue | 4 条 `needs_human_review`，已全部 approve |
| articles | 4 |
| terminal units | 118 |
| `late_fee_per_day` TU 行数 | 3（value=5/20/50） |
| 中文 fieldAliases（"逾期"、"押金"等） | **0 条**（field-alias-enricher 本轮未生成中文别名） |

---

## 3. FG1 `[TU_TRACE]` 原始关键信息

### 3.1 查询

```
equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？
```

### 3.2 运行时现场（两次调用一致）

```
tokens=[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml,
        里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]

cand#1 el=equipment_types[0].approval_required = 设备管理员 qf=false ftmc=-1 fs=6.0
cand#2 el=equipment_types[0].type = 常规设备 qf=false ftmc=-1 fs=9.0
cand#3 el=equipment_types[1].type = 精密仪器 qf=true ftmc=2 fs=8.0
cand#4 el=approval_chain[2].responsibility = ...      qf=true ftmc=2 fs=7.0

result=SELECTED el=equipment_types[1].type = 精密仪器 ftmc=2 fs=8.0
tuTotal=4 tuQfPassed=2
```

### 3.3 与上一轮对比

| 指标 | 上一轮 (qf 修复后) | 本轮 (ftmc 修复后) |
|---|---|---|
| `late_fee_per_day` 在候选池 | **是**（cand#5, cand#6） | **否**（完全不出现） |
| `late_fee_per_day` qf | true | N/A（未入池） |
| `late_fee_per_day` ftmc | 0 | N/A（未入池） |
| `精密仪器.type` ftmc | 0 | **2** |
| tuTotal | 7 | **4** |
| tuQfPassed | 4 | 2 |
| winner | `精密仪器.type` (fs=10.0) | `精密仪器.type` (ftmc=2, fs=8.0) |

---

## 4. 候选对比表

| # | 候选 | qf | ftmc | fs | 结果 |
|---|---|---|---|---|---|
| 1 | `equipment_types[0].approval_required = 设备管理员` | false | -1 | 6.0 | 被 qf 淘汰 |
| 2 | `equipment_types[0].type = 常规设备` | false | -1 | 9.0 | 被 qf 淘汰 |
| 3 | `equipment_types[1].type = 精密仪器` | **true** | **2** | 8.0 | **SELECTED** |
| 4 | `approval_chain[2].responsibility = ...` | true | 2 | 7.0 | qf 通过但 fs 更低 |
| — | `equipment_types[0].late_fee_per_day = 5` | **未入池** | N/A | N/A | — |
| — | `equipment_types[1].late_fee_per_day = 20` | **未入池** | N/A | N/A | — |
| — | `equipment_types[2].late_fee_per_day = 50` | **未入池** | N/A | N/A | — |

---

## 5. 4 个验收问题的逐条回答

### 5.1 `late_fee_per_day` 候选的 `ftmc` 是否从 0 变为 `>= 1`

**无法判断。** `late_fee_per_day` 候选在本轮 runtime 中**完全未进入 builder 候选池**（tuTotal=4，4 个候选均非 late_fee_per_day）。`ftmc` 未被计算，无法判断修复是否改变了其值。

### 5.2 `equipment_types[1].type = 精密仪器` 的 `ftmc` 实际值是多少

**ftmc=2**（上一轮为 ftmc=0）。

说明 `countFieldLevelTokenMatches()` 的 CJK bigram 重叠修复**已在 runtime 生效**——token "里精密仪"和"里精密"的大字组（如"精密"、"密仪"）与 fieldHaystack 中"精密仪器"的值产生了 bigram 重叠匹配。

### 5.3 最终 winner 是不是 `late_fee_per_day`

**否。** 最终 winner 是 `equipment_types[1].type = 精密仪器` (ftmc=2, fs=8.0)。

### 5.4 如果最终 winner 仍不是 `late_fee_per_day`，到底是哪个候选赢了

**`equipment_types[1].type = 精密仪器`** 赢了，参数：
- `qf=true`
- `ftmc=2`
- `fs=8.0`

---

## 6. 最终结论

**本轮不能把 FG1 标记为修复完成。**

原因分析：

1. **`countFieldLevelTokenMatches()` 的 CJK bigram 修复在 runtime 已生效**（证据：`精密仪器.type` 的 ftmc 从 0 升至 2）。但修复同时让 sibling 候选受益。

2. **目标候选 `late_fee_per_day` 本轮根本未进入 builder 候选池**（tuTotal=4，无 late_fee_per_day）。这是与上一轮的关键差异——上一轮 `late_fee_per_day` 还在候选池中（cand#5/#6），本轮完全不出现。

3. **field-alias-enricher 本轮未生成中文别名**。118 个 terminal unit 中，0 条 `field_aliases_json` 包含"逾期"、"押金"等中文词。`late_fee_per_day` 的别名仅含英文（"late_fee_per_day"、"late fee per day"等），不含"逾期日费"、"每日逾期费用"等中文别名。

本轮断点归类：

| 层级 | 状态 |
|---|---|
| `countFieldLevelTokenMatches` CJK 修复 | **runtime 已生效**（让 精密仪器 ftmc 0→2） |
| `late_fee_per_day` 在 builder 候选池 | **不在**（tuTotal=4，无 late_fee_per_day） |
| field-alias-enricher 中文别名 | **本轮未生成**（0 条中文逾期/押金别名） |

根因属于：**FG1 内部 `ftmc` 规则改进已落地，但目标候选未进入 builder 候选池（候选供给侧问题），且别名增强器本轮未生效。** 这不能再归为 builder 内排序问题。

---

## 7. 下一轮建议

1. **最高优先级**：排查为什么 `late_fee_per_day` terminal unit 未进入 builder 候选池。这已从"builder 排序问题"转变为"候选供给侧问题"——builder 在 4 个候选中无法选择它看不到的目标字段。

2. **只读审计 field-alias-enricher**：确认本轮编译中 enricher 是否被调用、是否返回了中文别名、返回的别名是否被正确写入了 `field_aliases_json`。上一轮 audit 确认 enricher 正常工作并生成了"逾期日费"别名，本轮却未生成。

3. **如果 enricher 未运行**：不要继续在 builder 中修改排序逻辑；先让 agent 确认 enricher 是否被编译链路正确调用。

4. **禁止**继续在 `AnswerFallbackConclusionBuilder` 中叠加排序/匹配规则来追 FG1 PASS——目标候选不在池中，排序规则无论怎么改都选不到它。

---

## 8. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 仅在 FG1 builder `ftmc` 验证范围内判定
- [x] 未扩大到 FQ4 或完整 Public Eval
- [x] 未修改 retrieval/reranker/candidate supply
- [x] 所有结论基于 runtime `[TU_TRACE]` 日志，非猜测
