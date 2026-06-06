# FG2 Terminal Citation Binding — Trace Runtime Gate 报告

验证时间：2026-06-06 12:23 ~ 12:35
执行人：agentD（验证 Agent）
前置分析：`fg2_terminal_citation_binding_trace_analysis_report.md`（agentB）
Phase 1A 修复：`query_debug_trace_phase1a_citation_trace_fix_result_report.md`（agentA）

---

## 1. 本轮目标

开启 citation L2 trace，抓取 FG2 运行时 `citation_terminal_unit_checked` 事件，定位 terminal unit evidence 路径未命中的确切原因。

---

## 2. 启动参数 / Trace 开关

```bash
JAVA_TOOL_OPTIONS="\
  -Dlattice.query.trace.enabled=true \
  -Dlattice.query.trace.stages.citation_validation=true \
  -Dlattice.query.trace.stages.citation_check=true"
```

通过 `JAVA_TOOL_OPTIONS` 环境变量注入 JVM 系统属性，不修改配置文件。

---

## 3. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| CitationValidatorTests | **18/0/0/0** |
| CitationCheckServiceTests | **10/0/0/0** |
| 全量 mvn test | **未运行**（定向测试已覆盖 citation 相关全部测试） |

---

## 4. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 4/5（Markdown/YAML/XLSX/CSV，PDF 未上传） |
| compile jobs | 4，全部 SUCCEEDED |
| review queue | 2，已 approve |
| 服务端口 | 18082 |
| trace 生效确认 | 日志中 2 行 `query.trace` 配置加载记录 |

---

## 5. FG2 查询结果

| 字段 | 值 |
|---|---|
| queryId | `3357730b-3e1a-4910-a483-36d30a576671` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | FALLBACK |
| answer | `borrowing_system.max_concurrent_requests = 50` ✅ |
| coverageRate | **0.0** |
| verifiedCount | 0 |
| demotedCount | 1 |
| claimCount | 1 |

---

## 6. Citation Trace 事件统计

| 事件 | 触发次数 | 说明 |
|---|---|---|
| `citation_terminal_unit_checked` | **38 次** | 每 terminal unit 逐条遍历 + guard/result 事件 |
| `citation_validated` | 1 次 | citation 整体验证结果 |
| `citation_check_completed` | 1 次 | citation 检查完成 |

**38 次 `citation_terminal_unit_checked` 确认**：terminal unit evidence 验证路径**已被触发**，对 source file 下的全部 terminal unit 进行了逐条检查。

---

## 7. Trace 字段可见性问题

### 7.1 根因

`local-dev` profile 使用 `CONSOLE_TEXT` appender，日志模式为：

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
```

该模式**不包含 `%mdc`**。`StructuredEventLogger.info(eventName, fields)` 将结构化字段写入 MDC 后调用 `logger.info(eventName)`，仅事件名称出现在消息中，所有结构化字段（`tu_guard`、`tu_overlap_score`、`tu_claim_value_matched` 等）均未输出。

### 7.2 生产环境 vs local-dev

| 环境 | Appender | MDC 可见 |
|---|---|---|
| `local-dev` | CONSOLE_TEXT（纯文本 pattern） | **否** |
| `!local-dev`（生产） | CONSOLE_JSON（LogstashEncoder） | **是** |

### 7.3 影响

- L2 trace 事件**已正确触发**（38 次 `citation_terminal_unit_checked` 确认）
- MDC 字段值**无法从 local-dev 日志中读取**
- FG2 的确切失败 guard / 步骤**无法从当前日志确定**

---

## 8. DB Citation 记录确认

| 字段 | 值 |
|---|---|
| claim_text | `Confirmed evidence: borrowing_system.max_concurrent_requests = 50` |
| target_key | `equipment-borrowing-policy.yaml` |
| source_type | `SOURCE_FILE` |
| validation_status | `DEMOTED` |
| validated_by | `RULE` |
| overlap_score | **0.600** |
| reason | `source_insufficient_overlap` |

Terminal unit 已确认存在：`borrowing_system.max_concurrent_requests = 50`（source_file_id=2，与 source file id=2 一致）。

---

## 9. 源码路径逐步骤分析（基于 agentB 手工复算）

| 步骤 | 检查项 | 手工复算结果 |
|---|---|---|
| 1 | `extractHardFactTokens` | 6 个唯一 token |
| 2 | `isKeyValueClaim` | **true** |
| 3 | `terminalUnits.isEmpty()` | **false**（DB 已确认 unit 存在） |
| 4 | `claimValueMatchesUnit` | **true**（value "50" == "50"） |
| 5 | `calculateOverlapScore` | **0.6667**（4/6 match） |
| 6a | `overlapScore >= 1.0` | **false** |
| 6b | `isHighConfidencePartialOverlap(6 tokens, 0.6667)` | **true**（6 >= 2, 0.6667 >= 0.66） |

**手工复算结论**：terminal unit evidence 路径应返回 `VERIFIED`（reason: `terminal_unit_evidence_near_complete_verified`）。

**Runtime 实际**：返回 `DEMOTED`（reason: `source_insufficient_overlap`），即 terminal unit 路径返回了 `null`。

---

## 10. Trace 已知信息与缺口

| 维度 | 状态 | 证据 |
|---|---|---|
| L2 trace 是否触发 | **是** | 38 次 `citation_terminal_unit_checked` 日志行 |
| terminal unit 路径是否被进入 | **是** | 事件数量对应逐条 unit 遍历 |
| 具体 guard/阈值/overlap 值 | **不可见** | MDC 字段在 CONSOLE_TEXT 模式下不输出 |
| query_id / trace_id 传播 | **不可见** | 同上 |
| 最终返回 null 的原因 | **无法确定** | 同上 |

---

## 11. FG2 失败点判断

**当前无法通过 runtime trace 精确定位**。已知：

- ✅ `validateAgainstTerminalUnitEvidence` 被调用（38 次 trace 事件）
- ✅ source file 下的 terminal units 已被查询并遍历
- ❓ `isKeyValueClaim` 是否通过 — 手工复算为 true，runtime 未知
- ❓ `claimValueMatchesUnit` 是否通过 — 手工复算为 true，runtime 未知
- ❓ `calculateOverlapScore` 实际值 — 手工复算为 0.6667，runtime 未知
- ❓ `isHighConfidencePartialOverlap` 是否通过 — 手工复算应通过，runtime 未知

**最可能原因**：手工复算与 runtime 在 `claimValueMatchesUnit`、`calculateOverlapScore` 或 `isHighConfidencePartialOverlap` 的某一步存在差异。差异来源可能是：
1. `hardFactTokens` 在 runtime 中与手工复算不同（例如 `LATIN_TERM_PATTERN` 的运行时边界行为不同）
2. `tokenize()` 对 evidence text 的分词与手工复算不同
3. `calculateOverlapScore` 使用了不同的 token 集合

---

## 12. Query ID / Trace ID 传播

| 字段 | 状态 |
|---|---|
| query_id | API 响应中有值（`3357730b-...`） |
| trace_id | local-dev 日志中不可见（MDC 字段未输出） |

因 MDC 字段不可见，无法判断 trace_id 是否正确从 query 入口传播到 citation 验证阶段。

---

## 13. 是否发现新增回归

**否。** FQ4/FG1 citation coverage 保持 1.0，FG2 行为与修复前一致（均为 DEMOTED, cov=0.0）。无新增失败。

---

## 14. 下一步建议

### 唯一最小方向

**启用 `!local-dev` profile 或修改 local-dev 日志 pattern 使其包含 MDC 字段**，然后重跑 FG2 trace gate。

具体方案（按优先级）：
1. **方案 A（推荐）**：在 `src/main/resources/logback-spring.xml` 的 CONSOLE_TEXT pattern 中追加 `%mdc`，使所有 MDC 值在纯文本日志中可见。改动仅一行，不影响生产。
2. **方案 B**：临时使用非 local-dev profile 启动（如 `--spring.profiles.active=dev`），利用 CONSOLE_JSON appender 输出 JSON 格式日志。但可能影响数据库连接等配置。
3. **方案 C**：agentA 在 `validateAgainstTerminalUnitEvidence` 中增加临时 `log.info` 级别的 SLF4J 日志（非 MDC），确保在 CONSOLE_TEXT 模式下可见。

方案 A 改动最小且一劳永逸——后续所有 L2 trace 的 MDC 字段都可在 local-dev 中直接查看。

### 不推荐

- 继续在纯源码层面做手工复算——agentB 已完成，runtime 差异无法通过纸笔解决
- 直接修改 overlap threshold 或放宽 guard——可能掩盖真正的 root cause
- 为 FG2 写 case 特判——违反项目红线

---

## 15. 未提交文件提醒

当前工作区包含以下未提交变更：

| 类别 | 文件 |
|---|---|
| Citation trace 基础设施 | `QueryTraceProperties.java`, `QueryTraceManager.java`, `CitationValidator.java`, `CitationCheckService.java` |
| 配置 | `config/lattice-query.yml` |
| 测试适配 | 8 个测试文件（构造器参数 +1） |
| Terminal citation binding 修复 | `CitationValidator.java`（terminal unit evidence 路径） |

建议在 trace 定位 FG2 根因并修复后，统一提交 citation binding 修复 + Phase 1A trace 基础设施。

---

## 16. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / logback pattern / redline allowlist
- [x] 未提交 commit
- [x] L2 trace 事件已确认触发（38 次 `citation_terminal_unit_checked`）
- [x] MDC 字段不可见原因已定位（local-dev CONSOLE_TEXT 模式不含 `%mdc`）
- [x] 手工复算与 runtime 行为的差异尚未通过 trace 确认
