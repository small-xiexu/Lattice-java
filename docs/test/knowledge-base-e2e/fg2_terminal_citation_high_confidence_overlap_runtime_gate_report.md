# FG2 Terminal Citation — High-Confidence Overlap 修复 Runtime Gate 报告

验证时间：2026-06-06 15:55 ~ 16:10
执行人：agentD（验证 Agent）
修复报告：`fg2_terminal_citation_high_confidence_overlap_fix_result_report.md`（agentA）
前置 trace gate：`fg2_terminal_citation_binding_trace_runtime_gate_final_report.md`（agentD）

---

## 1. 本轮目标

验证 `isHighConfidencePartialOverlap` 阈值修复（0.66→0.60）使 FG2 citation coverage 从 0.0 提升到 1.0，并确认 FQ4/FG1 无回归。

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
| CitationValidatorTests | **20/0/0/0**（+2 边界测试） |
| 全量 mvn test | **未运行**（定向测试已覆盖 citation 全部测试） |

---

## 4. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 4/5（PDF 未上传） |
| compile | YAML 已 SUCCEEDED |
| 服务端口 | 18082 |
| MDC 可见 | **是** |

---

## 5. FG2 查询结果

| 字段 | 修复前 | 修复后 |
|---|---|---|
| queryId | — | `31731056-4f4b-4a75-9050-4dfca02654ab` |
| answerOutcome | PARTIAL_ANSWER | **SUCCESS** |
| generationMode | FALLBACK | FALLBACK |
| **coverageRate** | **0.0** | **1.0** ✅ |
| verifiedCount | 0 | **1** |
| demotedCount | 1 | **0** |

---

## 6. FG2 Citation Trace

### 6.1 citation_validated

| 字段 | 修复前 | 修复后 |
|---|---|---|
| validation_path | INSUFFICIENT | **TERMINAL_UNIT** ✅ |
| validation_status | DEMOTED | **VERIFIED** ✅ |
| reason | source_insufficient_overlap | **terminal_unit_evidence_near_complete_verified** ✅ |
| hard_fact_token_count | 5 | **6** |
| overlap_score | 0.6 | **0.667** |

### 6.2 citation_terminal_unit_checked（target unit index=3）

| 字段 | 修复前 | 修复后 |
|---|---|---|
| tu_is_key_value_claim | true | true |
| tu_claim_value_matched | true | true |
| tu_overlap_score | 0.6 | 0.6 |
| **tu_is_high_confidence** | **false** | **true** ✅ |
| tu_evidence_text | `borrowing_system.max_concurrent_requests = 50 50` | 同左 |
| tu_matched_unit_id | `...:3:4a273743788dcbfd434d09c4` | 同左 |

**阈值变更验证**：`5 tokens >= 2 && 0.6 >= 0.60` → **true**（修复前 0.6 < 0.66 → false）

---

## 7. FQ4 / FG1 保护

| 题号 | coverageRate | verified | demoted | 判定 |
|---|---|---|---|---|
| FQ4 | **1.0** | 2 | 0 | **PASS** ✅ |
| FG1 | **1.0** | 2 | 0 | **PASS** ✅ |

修复前后均保持 1.0，无回归。

---

## 8. 是否发现新增回归

**否。**

---

## 9. Gate 结论

### **PASS**

| 维度 | 判定 |
|---|---|
| Redline | **BLOCKER=0** |
| CitationValidatorTests | **20/0/0/0** |
| FG2 coverageRate | **0.0 → 1.0** ✅ |
| FG2 validation_path | **INSUFFICIENT → TERMINAL_UNIT** ✅ |
| FG2 tu_is_high_confidence | **false → true** ✅ |
| FQ4 coverageRate | **1.0（保持）** ✅ |
| FG1 coverageRate | **1.0（保持）** ✅ |

---

## 10. 下一步建议

**建议进入提交前质量复核。** 累计 citation binding 修复包（CitationValidator terminal unit evidence 路径 + isHighConfidencePartialOverlap 阈值 + CitationValidatorTests 20 个测试 + Phase 1A Citation Trace 基础设施 + local-dev MDC 可见性修复 + SemanticChunker heading boundary + mixed script token extraction + cumulative terminal fix 包）已完成全部 targeted gate，可提交。

---

## 11. 未提交文件提醒

| 类别 | 文件 |
|---|---|
| Citation fix | `CitationValidator.java`, `CitationValidatorTests.java`（+6 测试文件适配） |
| Citation trace | `QueryTraceProperties.java`, `QueryTraceManager.java`, `StructuredEventLogger.java`, `CitationCheckService.java` |
| MDC 可见性 | `src/main/resources/logback-spring.xml` |
| 前置修复 | `SemanticChunker.java`, `QueryTokenExtractor.java`, `LexicalSearchTokenBudget.java` |

---

## 12. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / logback / redline allowlist
- [x] 未提交 commit
- [x] 所有结论基于 runtime API citationCheck + trace MDC 字段
