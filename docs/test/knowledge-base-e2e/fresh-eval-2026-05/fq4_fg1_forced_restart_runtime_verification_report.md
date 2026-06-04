# FQ4/FG1 强制重启后运行时复验报告

验证时间：2026-06-02 10:23
执行人：agentD（验证 Agent）
前置分析：agentB `fq4_fg1_fallback_runtime_breakpoint_analysis_report.md`

---

## 1. 强制重启确认

| 检查项 | 值 |
|---|---|
| 操作 | `pkill -9` 所有 Java 进程 → `mvn clean compile` → `run-local-dev.sh --reset-schema` |
| 新 PID | **88833** |
| 启动时间 | 2026-06-02 10:23:06 |
| `AnswerFallbackConclusionBuilder.class` 编译时间 | 2026-06-02 10:23 |
| `javap -c` 确认 Jackson 代码 | **1 次 readTree 调用，JsonNode.isArray + iterator 循环确认存在** |
| 服务启动 | 10:23:18 UP |

**结论：修复代码已成功编译并加载到运行进程。排除"类加载未刷新"假说。**

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 3. Fresh Eval 2 编译

| 项 | 值 |
|---|---|
| jobId | `9bd928c6-ebe9-413a-856a-76f67a8e2681` |
| 结果 | **SUCCEEDED** |
| persistedCount | 3 |
| 模型绑定 | 11 条，compile/field-alias-enricher enabled=true |

---

## 4. FQ4 与 FG1 核心验证

### FQ4

| 字段 | 值 |
|---|---|
| queryId | `aa3c0ab4` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | **FALLBACK** |
| 答案 | `equipment_types[0].approval_required = 设备管理员` |
| 期望 | `equipment_types[0].deposit_amount = 100`, `equipment_types[2].deposit_amount = 1000` |
| 判定 | **FAIL** |

**强制重启后 FQ4 FALLBACK 路径仍选中 approval_required 而非 deposit_amount。**

### FG1

| 字段 | 值 |
|---|---|
| queryId | `0af3cb22` |
| answerOutcome | SUCCESS |
| generationMode | **LLM** |
| 答案 | 精密仪器 `late_fee_per_day: 20`，常规设备 `late_fee_per_day: 5` |
| 判定 | **PASS** |

FG1 通过 LLM 路径（与之前一致），LLM 语义匹配"逾期罚金"→late_fee_per_day。

---

## 5. 回归结果

| 题号 | generationMode | 答案 | 判定 |
|---|---|---|---|
| FQ3 | FALLBACK | equipment_types[1].max_borrow_days=7 | **PASS** |
| FQ5 | FALLBACK | borrowing_system.api_endpoint=https://lab-equip.campus.edu/api/v2/borrow | **PASS** |
| FQ6 | FALLBACK | borrowing_system.version=v2.3.1 | **PASS** |
| FG2 | LLM | borrowing_system.max_concurrent_requests=50 | **PASS** |

FALLBACK 路径对非 sibling 竞争的 terminal unit（FQ3/FQ5/FQ6）均正确。仅 FQ4（deposit_amount vs approval_required sibling 竞争）失败。

---

## 6. 是否可以标记 agentA 修复为 runtime 验证通过

**不可以。**

- Jackson JsonNode 修复已编译、已加载（javap 确认），但 FQ4 FALLBACK 路径仍产出相同错误结果
- agentB 的"类加载未刷新"假说已通过 `mvn clean compile` + 强杀重启排除
- FG1 多次通过 LLM 路径，不能证明 FALLBACK 路径的 conclusion builder 修复生效

---

## 7. 最终结论：强制重启后仍失败

| 排除的假说 | 排除方式 |
|---|---|
| 类加载未刷新 | `mvn clean compile` + 进程 PID 变化 + javap 确认 |
| metadataJson 丢失 | agentB 全链路追踪已排除 |
| fieldAliases 缺失或 JSON 解析错误 | javap 确认 Jackson 逐元素遍历代码存在 |
| 修复代码不存在 | 源码 + class 文件双重确认 |

**唯一剩余可能性：`countFieldLevelTokenMatches` 在 FQ4 场景下的实际 token 匹配数与 agentB 静态推演不符。** 需要在运行时打印 fallbackHits 候选池中每个 terminal unit 的 `fieldTokenMatchCount` 和 `fusedScore` 才能定位。

---

## 8. 下一步建议

**不需要 agentA 再改 `buildFieldLevelHaystack`（代码已正确）。**

下一步必须是运行时段子追踪，而非继续猜测：
1. 在 `buildTerminalUnitExactConclusionLines` 中加临时的 `log.debug` 打印每个通过 `isTerminalHitQueryFocused` 的候选：`terminal_key`、`fieldTokenMatchCount`、`fusedScore`
2. 重跑 FQ4
3. 观察 deposit_amount 的 `fieldTokenMatchCount` 是否真的 > approval_required
4. 如果 deposit_amount 的 count 确实更高却没被选中 → 排序逻辑 bug
5. 如果 deposit_amount 的 count 与 approval_required 相同或更低 → `buildFieldLevelHaystack` 中对具体 metadataJson 产生的 haystack 内容有 bug

---

## 9. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未把两套 public eval 混在同一个 schema
