# FG2 Terminal Citation Binding — Trace Runtime Gate 最终报告

验证时间：2026-06-06 14:15 ~ 14:30
执行人：agentD（验证 Agent）
MDC 语法修复：`query_debug_trace_local_dev_mdc_syntax_fix_result_report.md`（agentA）
前置 trace gate：`fg2_terminal_citation_binding_trace_runtime_gate_after_mdc_report.md`

---

## 1. 本轮目标

在 local-dev MDC 可见性修复后，重跑 FG2 trace gate，通过 MDC 字段钉死 FG2 terminal unit citation binding 的真实失败点。

---

## 2. 启动参数

```
JAVA_TOOL_OPTIONS="-Dlattice.query.trace.enabled=true -Dlattice.query.trace.stages.citation_validation=true -Dlattice.query.trace.stages.citation_check=true"
```

---

## 3. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |

---

## 4. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 4/5 |
| compile jobs | 4，3+ SUCCEEDED（YAML compiled） |
| 服务端口 | 18082 |

---

## 5. Local-Dev MDC 可见性验证

| 检查项 | 结果 |
|---|---|
| 日志 pattern | `%msg %mdc%n` ✅ |
| MDC 字段可见 | **是**（eventName, trace_id, tu_overlap_score, tu_is_high_confidence 等均可见） |
| L2 trace 事件 | **38 次** `citation_terminal_unit_checked` |
| query_id / trace_id 传播 | **可见**（trace_id=6a23be1aa8bc6c336f347965ed7847e1） |

---

## 6. FG2 查询结果

| 字段 | 值 |
|---|---|
| queryId | `da3c16e0-7854-4cc2-9fbd-40aa6366cd06` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | FALLBACK |
| coverageRate | **0.0** |
| verifiedCount | 0 |
| demotedCount | 1 |

---

## 7. Citation Trace 事件明细

### 7.1 citation_validated

| 字段 | 值 |
|---|---|
| validation_path | **INSUFFICIENT** |
| validation_status | **DEMOTED** |
| reason | `source_insufficient_overlap` |
| hard_fact_token_count | **5** |
| overlap_score | **0.6** |
| matched_excerpt | `borrowing_system:` |
| source_type | SOURCE_FILE |
| target_key | equipment-borrowing-policy.yaml |

### 7.2 citation_terminal_unit_checked（目标 unit: index 3）

| 字段 | 值 |
|---|---|
| tu_unit_index | **3** |
| tu_matched_unit_id | `fact-card-terminal:...:3:4a273743788dcbfd434d09c4` |
| tu_is_key_value_claim | **true** |
| tu_claim_value_matched | **true** |
| tu_evidence_text | `borrowing_system.max_concurrent_requests = 50 50` |
| tu_claim_text | `Confirmed evidence: borrowing_system.max_concurrent_requests = 50` |
| tu_overlap_score | **0.6** |
| tu_is_high_confidence | **false** |

### 7.3 其他 unit（均未通过 claim value match）

全部 19 个 terminal units 中，仅 3 个（index 3, 15, 另一个）通过了 `claimValueMatchesUnit`，但：
- Index 3：overlap=0.6, highConfidence=false
- Index 15：匹配到 `deposit_amount=500`（value "50" ⊂ "500"），overlap=0.0

**没有一个 unit 同时满足 value match + overlap >= 1.0 + high confidence。**

---

## 8. FG2 真实失败点判断

### 失败卡在：**overlap_score 不足（`tu_is_high_confidence=false`）**

**因果链**：

```
hard_fact_tokens=5 (claim: "Confirmed evidence: borrowing_system.max_concurrent_requests = 50")
  → terminal unit evidenceText: "borrowing_system.max_concurrent_requests = 50 50"
    → matched tokens: 3/5
      → overlap_score = 0.6
        → isHighConfidencePartialOverlap(5 tokens, 0.6):
          - (5 >= 4 && 0.6 >= 0.75) → false
          - (5 >= 2 && 0.6 >= 0.66) → **false**  ← 失败点
            → tu_is_high_confidence = false
              → 方法返回 null
                → 回退 source overlap 路径
                  → source overlap = 0.6 < 0.66 → DEMOTED
```

**根因确认**：`isHighConfidencePartialOverlap` 的 **0.66 阈值对 5-token 场景过严**。FQ4/FG1 的 claim token 更多（含 `equipment_types[N]` 等路径 token），overlap 更高（0.6667），刚好越过 0.66 线。FG2 的 claim 仅有 5 个 token，以 0.6 分刚好低于 0.66 线。

### 已排除的假说

| 假说 | 结论 |
|---|---|
| terminal unit 路径未被触发 | **排除**（38 次 trace 事件确认） |
| tu_is_key_value_claim=false | **排除**（runtime 确认为 true） |
| tu_claim_value_matched=false | **排除**（目标 unit index=3 确认为 true） |
| repo/table 不可用 | **排除**（19 个 units 成功查询） |
| source_file_id 不匹配 | **排除**（source_file_id=2 一致） |
| trace 字段缺失 | **排除**（MDC 语法修复后全部可见） |

---

## 9. Query ID / Trace ID 传播

| 字段 | 状态 |
|---|---|
| query_id | API 响应中有值 |
| trace_id | **MDC 中可见**（`traceId=6a23be1aa8bc6c336f347965ed7847e1`） |
| rootTraceId | **MDC 中可见** |
| spanId | **MDC 中可见** |

---

## 10. FQ4 / FG1 保护性检查

| 题号 | coverageRate | verifiedCount | demotedCount | 判定 |
|---|---|---|---|---|
| FQ4 | **1.0** | 2 | 0 | **PASS** ✅ |
| FG1 | **1.0** | 2 | 0 | **PASS** ✅ |

无回归。

---

## 11. 是否发现新增回归

**否。**

---

## 12. 下一步建议

### 唯一最小方向

**修复 `isHighConfidencePartialOverlap` 的阈值**，使其覆盖 FG2 的 5-token / 0.6 overlap 场景。

具体选项：

| 选项 | 修改 | 风险 |
|---|---|---|
| **A（推荐）** | 将第二阈值从 `0.66` 降为 `0.60`：`(tokens >= 2 && score >= 0.60)` | 低——FG2 的 0.6 刚好在 0.60 边界，且 0.6 < 0.66 的唯一差值为 FG2 这种较小 token 集的 case；FQ4/FG1 不受影响 |
| B | 将第一阈值从 `0.75/4` 降为 `0.65/3` 并新增 `0.60/5` 档 | 中等——多层阈值增加复杂度 |
| C | 改进 evidence text 构造，使 overlap 自然提升到 0.66+ | 高——改动面大 |

**推荐选项 A**：一行阈值变更，风险最低。修改后 FG2 overlap=0.6 ≥ 0.60 → `isHighConfidencePartialOverlap` 返回 true → terminal unit evidence 路径 VERIFIED。

不改动 `calculateOverlapScore`、不改动 `claimValueMatchesUnit`、不改动 `isKeyValueClaim`。

### 不推荐

- 修改 evidence text 构造逻辑（改动面大）
- 为 FG2 写特判
- 继续在观察性层面做 trace——trace 已完备，根因已钉死

---

## 13. 未提交文件提醒

| 类别 | 文件 |
|---|---|
| MDC 语法修复 | `src/main/resources/logback-spring.xml` |
| Trace 基础设施 | `QueryTraceProperties.java`, `QueryTraceManager.java`, `StructuredEventLogger.java` |
| Citation fix | `CitationValidator.java`, `CitationCheckService.java` |

---

## 14. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / logback / redline allowlist
- [x] 未提交 commit
- [x] MDC 字段全部可见（`%mdc` 修复有效）
- [x] FG2 根因已钉死：`isHighConfidencePartialOverlap` 的 0.66 阈值对 5-token 场景过严
- [x] FQ4/FG1 保护验证通过（cov 保持 1.0）
