# FQ4 / FG1 受控 FALLBACK 候选分数运行时 Trace 报告

## 1. 执行范围

- 角色：agentA
- 本轮目标：只做受控 FALLBACK runtime trace，不做功能修复，不提交代码
- 允许触碰生产代码文件：`src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`
- 临时动作：仅在该文件中加入运行时 trace 日志，完成取证后必须移除
- 日志文件：`/tmp/lattice_fallback_trace.log`
- 服务端口：`18083`，未触碰既有 `18082` 进程

## 2. 诊断环境

- 使用诊断编译：Fresh Eval 2 资料以 `reviewMode=none` 编译入库；这是为了绕开 reviewer 并生成可追踪的 terminal units，不作为 public eval gate。
- `query.answer`：未修改配置文件、未提交任何运行时降级开关；本轮查询因诊断环境中的 LLM 调用不可用而自然进入 FALLBACK。
- 敏感信息处理：未打印、未记录任何 key 或 baseUrl。

## 3. 数据前提

清库并诊断编译后，库内数据确认如下：

| 项 | 数量 |
| --- | ---: |
| articles | 5 |
| fact_cards | 13 |
| fact_card_terminal_units | 123 |

目标 terminal units 在库内存在：

| case | terminal unit |
| --- | --- |
| FQ4 | `equipment_types[0].deposit_amount = 100` |
| FQ4 | `equipment_types[1].deposit_amount = 500` |
| FQ4 | `equipment_types[2].deposit_amount = 1000` |
| FG1 | `equipment_types[0].late_fee_per_day = 5` |
| FG1 | `equipment_types[1].late_fee_per_day = 20` |
| FG1 | `equipment_types[2].late_fee_per_day = 50` |

这些目标 terminal units 的 `fieldAliases` 与 `fieldDescription` 已落库。例如 `deposit_amount` 包含 `deposit_amount`、`deposit amount`、完整 path alias 与 `context: 常规设备 / 大型设备`；`late_fee_per_day` 包含 `late_fee_per_day`、`late fee per day`、完整 path alias 与 `context: 常规设备 / 精密仪器`。

## 4. FQ4 Trace

### 4.1 请求与最终状态

- question：`equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？`
- HTTP：200
- `generationMode`：`FALLBACK`
- `answerOutcome`：`PARTIAL_ANSWER`
- `fallbackReason`：`CITATION_QUALITY_INSUFFICIENT`
- 最终 answer 选中：`equipment_types[0].approval_required = 设备管理员`

### 4.2 入口参数

- `queryTokens`：`[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml, 常规设备, 和大型设, 的押金分, 别是多少, 常规设, 大型设, 的押金, 是多少, 常规]`
- `comparisonOptions`：`[]`
- `fallbackHitsSize`：`10`
- `queryArticleHitsSize`：`10`
- `terminalUnits > 0`：是，runtime trace 中出现 terminal candidates

### 4.3 fallbackHits 中的 terminal candidates

| exactLine | focused | fieldTokenMatchCount | fusedScore | 说明 |
| --- | ---: | ---: | ---: | --- |
| `equipment_types[0].approval_required = 设备管理员` | true | 3 | 10.0 | 最终 selected bestCandidate |
| `equipment_types[0].type = 常规设备` | true | 3 | 6.0 | 与问题设备类型相关，但不是押金字段 |
| `equipment_types[2].type = 大型设备` | true | 1 | 5.0 | 与问题设备类型相关，但不是押金字段 |
| `approval_chain[1].stage = 设备管理员审批` | false | 0 | 3.0 | 不是目标字段 |
| `approval_chain[2].responsibility = 审批高价值或高风险设备的借用安排` | false | 0 | 4.0 | 不是目标字段 |

### 4.4 目标字段与错误 sibling 对比

| 候选 | runtime 状态 | fieldTokenMatchCount | fusedScore |
| --- | --- | ---: | ---: |
| `equipment_types[0].deposit_amount = 100` | 库内存在，但未进入本次 fallbackHits terminal candidates | N/A | N/A |
| `equipment_types[2].deposit_amount = 1000` | 库内存在，但未进入本次 fallbackHits terminal candidates | N/A | N/A |
| `equipment_types[0].approval_required = 设备管理员` | runtime 命中并被选中 | 3 | 10.0 |
| `equipment_types[0].type = 常规设备` | runtime 命中 | 3 | 6.0 |

### 4.5 FQ4 结论

FQ4 不是在目标 `deposit_amount` 与错误 sibling 之间发生 tie-break 失败；目标 `deposit_amount` terminal units 根本没有进入 fallbackHits 的 terminal candidates。结论构建器只能在已有候选里排序，最终因 `approval_required` 的 `fieldTokenMatchCount=3` 且 `fusedScore=10.0` 最高，返回错误字段。

## 5. FG1 Trace

### 5.1 请求与最终状态

- question：`equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？`
- HTTP：200
- `generationMode`：`FALLBACK`
- `answerOutcome`：`PARTIAL_ANSWER`
- `fallbackReason`：`LLM_CALL_FAILED`
- terminal 分支最终：`finalBranch=empty selectedBestCandidate=null`
- general fallback 最终：`finalBranch=aggregatedEvidence`

### 5.2 入口参数

- `queryTokens`：`[equipment-borrowing-policy.yaml, equipment-borrowing-policy, yaml, 里精密仪, 器的逾期, 金是多少, 备的逾期, 里精密, 的逾期, 金是多, 备的逾, 里精]`
- `comparisonOptions`：`[]`
- `fallbackHitsSize`：`10`
- `queryArticleHitsSize`：`10`
- `terminalUnits > 0`：是，runtime trace 中出现 terminal candidates

### 5.3 fallbackHits 中的 terminal candidates

| exactLine | focused | fieldTokenMatchCount | fusedScore | 说明 |
| --- | ---: | ---: | ---: | --- |
| `equipment_types[0].approval_required = 设备管理员` | false | 0 | 7.0 | 非目标字段 |
| `equipment_types[0].type = 常规设备` | false | 0 | 10.0 | 设备类型字段，未通过 query-focused |
| `equipment_types[1].type = 精密仪器` | false | 0 | 9.0 | 设备类型字段，未通过 query-focused |
| `equipment_types[2].type = 大型设备` | false | 0 | 5.0 | 非目标字段 |
| `borrowing_system.name = 校园实验室设备预约系统` | false | 0 | 6.0 | 非目标字段 |
| `approval_chain[2].responsibility = 审批高价值或高风险设备的借用安排` | false | 0 | 8.0 | 非目标字段 |

### 5.4 目标字段与错误 sibling 对比

| 候选 | runtime 状态 | fieldTokenMatchCount | fusedScore |
| --- | --- | ---: | ---: |
| `equipment_types[1].late_fee_per_day = 20` | 库内存在，但未进入本次 fallbackHits terminal candidates | N/A | N/A |
| `equipment_types[0].late_fee_per_day = 5` | 库内存在，但未进入本次 fallbackHits terminal candidates | N/A | N/A |
| `equipment_types[0].type = 常规设备` | runtime 命中，但 `isTerminalHitQueryFocused=false` | 0 | 10.0 |
| `equipment_types[1].type = 精密仪器` | runtime 命中，但 `isTerminalHitQueryFocused=false` | 0 | 9.0 |

### 5.5 FG1 结论

FG1 的目标 `late_fee_per_day` terminal units 没有进入 fallbackHits。进入 terminal 分支的候选全部 `isTerminalHitQueryFocused=false`，因此 terminal exact 分支返回 empty；随后 general fallback 走 aggregatedEvidence，最终输出 source 与 `approval_required` / `return_check_required` 等非目标事实。

## 6. 唯一根因

本轮唯一根因：runtime fallback 的候选供给没有把目标 terminal value candidates 送到 `AnswerFallbackConclusionBuilder`。

更具体地说，目标 terminal units 已经落库，但 FQ4 的 `deposit_amount` 与 FG1 的 `late_fee_per_day` 没有进入本轮 fallbackHits top10；结论构建器的字段级排序无法选择不存在的候选。FG1 还暴露出中文问题 token 与英文字段 alias 之间缺少通用语义桥接，导致设备类型候选也未通过 query-focused 判断。

## 7. 下一轮最小功能修改范围建议

下一轮若进入功能修复，最小范围应放在 terminal unit 检索候选供给侧，而不是继续扩大 fallback conclusion builder：

- terminal unit FTS / retrieval query term expansion
- structured field alias / fieldDescription 的通用中文语义补全
- query token normalization 与字段 alias 的通用匹配桥接

禁止方向仍应保持：不要在 query 主链写特定题目、特定字段名、特定文件名或样例答案的硬编码分支；不要用 fallback answer 模板硬补 `deposit_amount` 或 `late_fee_per_day`。

## 8. 临时日志清理状态

已移除 `AnswerFallbackConclusionBuilder.java` 中的临时 logger、`TRACE_PREFIX`、trace helper 以及所有 `[FALLBACK_TRACE]` 日志调用。

复核命令未命中任何临时 trace 标记：

- `rg -n "FALLBACK_TRACE|TRACE_PREFIX|Slf4j|log\\.info" src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

诊断端口 `18083` 已无监听进程。

## 9. 最终验证门禁

已完成最终门禁：

- `bash scripts/scan-redline.sh special_cases_report.md`：`BLOCKER=0`
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`：`Tests=995 Failures=0 Errors=0 Skipped=0`
- `target/surefire-reports` 未发现 `<failure`、`<error`、`.dump` 或 `.dumpstream`
