# FQ4 Terminal Tie-Break 修复结果报告

修复时间：2026-06-04
执行人：agentA（代码执行 Agent）
前置依据：`fq4_fg1_terminal_builder_slf4j_trace_runtime_gate_report.md`

---

## 1. 本轮目标

只修 FQ4 的 builder 内 tie-break 问题：`deposit_amount` 与 `approval_required` 在 `fieldTokenMatchCount` 上打平（均为 3），然后被更高 `fusedScore` 的 `approval_required`（10.0 > 9.0）抢走，导致 FQ4 答案选错字段。

---

## 2. FQ4 已知失败模式

来自 agentD runtime trace（`fq4_fg1_terminal_builder_slf4j_trace_runtime_gate_report.md`）：

| cand# | el | qf | ftmc | fs |
|---|---|---|---|---|
| 1 | `equipment_types[0].approval_required = 设备管理员` | true | **3** | **10.0** |
| 2 | `equipment_types[0].type = 常规设备` | true | 3 | 5.0 |
| 3 | `equipment_types[2].type = 大型设备` | true | 1 | 3.0 |
| **4** | **`equipment_types[0].deposit_amount = 100`** | true | **3** | 9.0 |
| 5 | `equipment_types[2].deposit_amount = 1000` | true | 1 | 4.0 |

结果：**SELECTED** `equipment_types[0].approval_required = 设备管理员` (ftmc=3, fs=10.0)

输因：`ftmc` 平局（3 vs 3），`fusedScore` tiebreaker 选中 `approval_required`（10.0 > 9.0）。

---

## 3. 修改文件

`src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

### 3.1 排序逻辑变更（`buildTerminalUnitExactConclusionLines`）

在 `fieldTokenMatchCount` 与 `fusedScore` 之间，插入**字段别名匹配数**（`aliasTokenMatchCount`）作为第二优先级 tie-break：

```
旧排序：ftmc → fusedScore
新排序：ftmc → atmc → fusedScore
```

具体代码变更：第 335-378 行的候选选择逻辑。

### 3.2 新增方法

**`countFieldAliasTokenMatches(QueryArticleHit, List<String>)`**：
- 只统计 query token 与 `fieldAliases`（metadataJson 中的 fieldAliases 数组）的匹配数
- 匹配规则与 `countFieldLevelTokenMatches` 相同：子串包含 + CJK bigram 重叠
- 但不扫描 `displayText` 和 `fieldDescription`——只扫描 fieldAliases

**`buildFieldAliasesHaystack(QueryArticleHit)`**：
- 从 metadataJson 中提取 fieldAliases 数组，构建小写匹配文本
- 仅包含 fieldAliases，不含 displayText、fieldDescription

### 3.3 Trace 日志扩展

- 每个候选的 trace 增加 `atmc` 字段
- SELECTED 行增加 `atmc` 字段

---

## 4. 具体改动说明

```
排序优先级（旧）:
  ftmc > bestFtmc
  || (ftmc == bestFtmc && fs > bestFs)   ← 单一 tie-break

排序优先级（新）:
  ftmc > bestFtmc
  || (ftmc == bestFtmc && atmc > bestAtmc)   ← 新增：字段别名精度 tie-break
  || (ftmc == bestFtmc && atmc == bestAtmc && fs > bestFs)  ← 仅当 alias 也打平时才走 fusedScore
```

**为什么 aliasMatchCount 能区分 FQ4**：

- `deposit_amount` 的 fieldAliases 含 `"押金"`、`"押金金额"`——这些是 enricher 为中文查询专门生成的中文别名
- `approval_required` 的 fieldAliases 含 `"approval_required"`、`"approval required"` 等英文别名
- FQ4 query token `"的押金分"`、`"的押金"` 中的 CJK bigram `"押金"` 只匹配 deposit_amount 的 fieldAliases，不匹配 approval_required 的
- 因此 deposit_amount 的 `atmc` > approval_required 的 `atmc`，tie-break 生效

---

## 5. 为什么这不是 case 特判

- `fieldAliases` 是 terminal unit 数据模型中的通用字段，对所有 terminal unit 生效
- `countFieldAliasTokenMatches` 使用与 `countFieldLevelTokenMatches` 完全相同的通用匹配规则（子串包含 + CJK bigram 重叠）
- 不依赖任何具体业务词、文件名、字段名、文档标题、样例答案
- 对所有 query + 所有 terminal unit 一视同仁地生效
- 不做任何 "如果是 deposit_amount 则加分" 之类的硬编码

---

## 6. 为什么不碰 FG1 / 候选供给侧 / enricher

- **FG1**：已由 agentD 验证 runtime 收口（`late_fee_per_day = 20` 胜出，ftmc=5 全场最高）。本轮修改的 `atmc` tie-break 对 FG1 不会有负面影响——FG1 winner 的 ftmc 远高于其他候选（5 vs 2），不会进入 tie-break 层。其他 ftmc 打平场景下，atmc 对 FG1 候选是公平的通用排序。
- **候选供给侧**：FQ4 的目标候选 `deposit_amount` 已进入 builder 候选池（cand#4, cand#5），断点在 builder 内排序而非候选供给。
- **enricher**：field-alias-enricher bootstrap guard 已由 agentA 修复，agentD 已验证 runtime 生效，中文别名已正常生成。

---

## 7. redline 与 mvn test 结果

| 门禁 | 结果 |
|---|---|
| redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 8. 下一步交给 agentD 的 runtime gate 建议

1. 清库（`bash scripts/reset-lattice-schema.sh`）
2. 确保 LLM 绑定就位（含 `compile/field-alias-enricher`）
3. 上传 Fresh Eval 2 资料并编译
4. 确认 enricher 生成了中文别名（0 条 401 错误，`late_fee_per_day` 和 `deposit_amount` 的 fieldAliases 含中文）
5. 抓取 FQ4 的 `[TU_TRACE]` 日志，观察：
   - `deposit_amount`（cand#4）的 `atmc` 是否 > `approval_required`（cand#1）的 `atmc`
   - 最终 SELECTED 是否为 `deposit_amount`
6. FG1 保护回归：确认 FG1 仍 PASS（ftmc 优势足够，atmc tie-break 不干扰）
7. 仅当 FG1 PASS + FQ4 PASS，再考虑跑完整 Public Eval 2 或 Public Eval 1 保护回归

**禁止** agentD 在 FQ4 + FG1 双 PASS 之前标记修复为最终通过。

---

## 9. 明确声明

- [x] 只修改了 `AnswerFallbackConclusionBuilder.java` 一个文件
- [x] 只修改了 `buildTerminalUnitExactConclusionLines` 方法 + 新增 2 个 helper
- [x] 未修改 `countFieldLevelTokenMatches()` 的核心语义
- [x] 未修改 `isTerminalHitQueryFocused`（qf 判定）
- [x] 未修改 `FactCardTerminalUnitFtsSearchService`
- [x] 未修改 `FactCardTerminalUnitIntentReranker`
- [x] 未修改 `LlmFactCardTerminalUnitFieldAliasEnricher`
- [x] 未修改 `ExecutionLlmSnapshotService`
- [x] 未修改 `LlmGatewayRouteSupport`
- [x] 未修改 tests、scripts、prompt、config、题集
- [x] 未写入业务词/字段名/文档名/答案模板特判
- [x] 未扩到 FG1
- [x] 未提交 commit
- [x] redline `BLOCKER=0`
- [x] mvn test `995/0/0/0, BUILD SUCCESS`
