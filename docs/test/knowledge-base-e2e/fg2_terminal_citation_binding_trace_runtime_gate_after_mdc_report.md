# FG2 Terminal Citation Binding — Trace Runtime Gate 复验报告（MDC 修复后）

验证时间：2026-06-06 13:04 ~ 13:18
执行人：agentD（验证 Agent）
前置报告：`fg2_terminal_citation_binding_trace_runtime_gate_report.md`（上一轮，MDC 不可见）
MDC 修复：`query_debug_trace_local_dev_mdc_visibility_fix_result_report.md`（agentA）

---

## 1. 本轮目标

验证 local-dev MDC 可见性修复是否生效，并通过 MDC 字段钉死 FG2 terminal unit citation binding 的真实失败点。

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
| 导入资料 | 4/5（PDF 未上传） |
| compile jobs | 4， 2 SUCCEEDED（Markdown + YAML），其余 RUNNING/QUEUED |
| review queue | 0 |
| 服务端口 | 18082 |

---

## 5. Local-Dev MDC 可见性验证

### 5.1 日志格式

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg %mdc{80}%n</pattern>
```

### 5.2 实际输出

```
2026-06-06 13:14:01.455 INFO  [http-nio-18082-exec-7] c.x.l.o.StructuredEventLogger - citation_validated ↵
```

MDC 内容出现在 `%mdc{80}` 位置（`%msg` 之后、`%n` 之前），但**输出为空**（仅一个空格后换行）。

### 5.3 根因定位

**`%mdc{80}` 在 Logback 中的语义是"输出 MDC key 为 `"80"` 的 value"，而非"限制每个 value 为 80 字符"。**

当前 trace 事件写入的 MDC key 为 `eventName`、`query_id`、`trace_id`、`tu_guard`、`tu_overlap_score` 等，不存在 key 为 `"80"` 的条目。因此 `%mdc{80}` 始终输出空字符串。

### 5.4 修复建议

将 `%mdc{80}` 改为裸 `%mdc`（输出所有 MDC 键值对），或将 `{80}` 改为实际的长度限制写法（如 Logback 的 `%replace(%mdc){'(.{80})[^,]*', '$1'}` 等变通方案）。

**最简单修复**：`%mdc` 即可（输出所有 MDC 条目，不加长度限制）。负面影响仅为日志行变长——trace 事件在 L2 模式下是低频操作，可接受。

### 5.5 验证

| 检查项 | 结果 |
|---|---|
| L2 trace 事件已触发 | **是**（38 次 `citation_terminal_unit_checked`） |
| MDC 字段可见 | **否**（`%mdc{80}` 语法错误） |
| Logback 级修复是否有效 | **否** |

---

## 6. FG2 查询结果

| 字段 | 值 |
|---|---|
| queryId | `6a755c29-8344-4be3-8212-051634665164` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | FALLBACK |
| coverageRate | **0.0** |
| verifiedCount | 0 |
| demotedCount | 1 |

---

## 7. Trace 事件统计

| 事件 | 次数 | MDC 可见 |
|---|---|---|
| `citation_terminal_unit_checked` | 38 | **否** |
| `citation_validated` | 1 | **否** |
| `citation_check_completed` | 1 | **否** |

---

## 8. FG2 失败点判断

**仍无法通过 trace 定位。** trace 事件已触发（确认 terminal unit evidence 路径被执行），但关键字段（`tu_guard`、`tu_is_key_value_claim`、`tu_claim_value_matched`、`tu_overlap_score`、`tu_is_high_confidence`、`tu_result`）因 `%mdc{80}` 语法错误均不可见。

手工复算（`fg2_terminal_citation_binding_trace_analysis_report.md`）仍是最可信的根因参照：terminal unit evidence 路径理论上应通过（overlap=0.6667, 6 tokens, 0.6667 >= 0.66），但 runtime 走回了 source overlap 路径。

---

## 9. Query ID / Trace ID 传播

| 字段 | 状态 |
|---|---|
| query_id | API 响应中有值 |
| trace_id | MDC 不可见（`%mdc{80}` 不输出 `traceId` key 的值） |

---

## 10. 是否发现新增回归

**否。** 与上一轮行为一致。

---

## 11. 下一步建议

### 唯一最小方向

**修正 logback-spring.xml 的 MDC 语法**：将 `%mdc{80}` 改为 `%mdc`（或正确的长度限制写法），然后重跑 FG2 trace gate。

改动：`src/main/resources/logback-spring.xml` 一行变更。

### 不推荐

继续在 MDC 不可见的状态下做 trace gate——实质数据无法获取，每轮只能确认"38 次事件触发"，不能定位根因。

---

## 12. 未提交文件提醒

| 类别 | 文件 |
|---|---|
| MDC 修复（待修正） | `src/main/resources/logback-spring.xml` |
| Trace 基础设施 | `QueryTraceProperties.java`, `QueryTraceManager.java`, `StructuredEventLogger.java` |
| Citation 修复 | `CitationValidator.java`, `CitationCheckService.java` |

---

## 13. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / logback
- [x] 未提交 commit
- [x] MDC 语法错误已定位：`%mdc{80}` → 应改为 `%mdc`
- [x] FG2 精确失败点仍未定位（trace 字段不可读）
