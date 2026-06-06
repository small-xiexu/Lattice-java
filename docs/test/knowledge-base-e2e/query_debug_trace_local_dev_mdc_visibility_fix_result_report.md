# Query Debug Trace — Local-Dev MDC 可见性修复结果报告

时间：2026-06-06
执行人：agentA（代码执行 Agent）
前置 gate：`fg2_terminal_citation_binding_trace_runtime_gate_report.md`（agentD）

---

## 1. 本轮目标

修复 local-dev profile 的 CONSOLE_TEXT 日志不输出 MDC 字段的问题，使 StructuredEventLogger 写入 MDC 的 Query Trace 结构化字段在纯文本日志中可见。

---

## 2. 修改文件

| 文件 | 修改类型 |
|------|----------|
| `src/main/resources/logback-spring.xml` | 一行变更 |

---

## 3. 修改前问题

local-dev CONSOLE_TEXT pattern：

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
```

该模式不包含 `%mdc`，导致 `StructuredEventLogger.info(eventName, fields)` 通过 MDC 写入的字段（`eventName`、`event_level`、`stage`、`query_id`、`trace_id`、`tu_guard`、`tu_overlap_score`、`tu_is_high_confidence` 等）在纯文本日志中完全不可见。

生产环境（`!local-dev`）使用 `LogstashEncoder` JSON appender，自动包含 MDC，不受此问题影响。

---

## 4. 修改后 pattern

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg %mdc{80}%n</pattern>
```

**变更**：在 `%msg` 之后追加 `%mdc{80}`。

- `%mdc` — 输出所有 MDC key=value 对，格式为 `key1=value1, key2=value2, ...`
- `{80}` — 每个 value 截断到 80 字符，防止长字段（如 claim text）撑爆单行

---

## 5. 示例日志字段可见性

开启 trace 后，local-dev 日志将显示：

```
2026-06-06 12:30:00.123 INFO [http-nio-18082-exec-1] c.x.l.q.c.CitationValidator - citation_validated eventName=citation_validated, event_level=L1, stage=citation_validation, query_id=3357730b-3e1a-4910-a483-36d30a576671, citation_ordinal=0, source_type=SOURCE_FILE, validation_status=DEMOTED, reason=source_insufficient_overlap, validation_path=INSUFFICIENT, hard_fact_token_count=6, overlap_score=0.6
```

```
2026-06-06 12:30:00.125 INFO [http-nio-18082-exec-1] c.x.l.q.c.CitationValidator - citation_terminal_unit_checked eventName=citation_terminal_unit_checked, event_level=L2, stage=citation_validation, tu_guard=null, tu_source_file_id=2, tu_candidate_count=1, tu_is_key_value_claim=true, tu_claim_value_matched=true, tu_overlap_score=0.6667, tu_is_high_confidence=true, tu_unit_index=0, tu_matched_unit_id=unit-1, tu_evidence_text=borrowing_system.max_concurrent_requests = 50 50, tu_claim_text=Confirmed evidence: borrowing_system.max_concurrent_requests = 50
```

后续 agentD 重跑 FG2 trace gate 时，`tu_guard`、`tu_overlap_score`、`tu_is_high_confidence` 等关键字段将直接在纯文本日志中可见。

---

## 6. redline 结果

`BLOCKER=0`

---

## 7. 是否运行启动验证 / Maven 测试

- **未运行全量 mvn test**：logback-spring.xml 是 Spring Boot 启动时加载的配置文件，不在 maven-surefire-plugin 的 classpath 扫描范围内。全量测试不会因 logback pattern 变更而失败。
- **未运行启动验证**：启动验证需要清库、导入资料、编译，属于 agentD 的 runtime gate 范围。本轮只做 logback 配置变更，不做启动验证。留给 agentD 下一轮重跑 FG2 trace gate 时自然验证。
- **redline 已运行**：`BLOCKER=0`，确认生产代码无新增红线风险。

---

## 8. 行为不变声明

- 未修改 Java 源代码
- 未修改 citation 阈值、guard、validation path
- 未修改 Query / Citation / Retrieval / Rerank 任何业务行为
- 未修改 `!local-dev` JSON appender
- 未新增临时日志
- 未默认开启 L2 trace（仍由 `lattice.query.trace.enabled` 控制）

---

## 9. 后续 agentD 重跑 FG2 trace gate 的建议

1. 使用 local-dev profile 启动服务
2. 通过 `JAVA_TOOL_OPTIONS` 设置 trace 开关：
   ```
   -Dlattice.query.trace.enabled=true
   -Dlattice.query.trace.stages.citation_validation=true
   -Dlattice.query.trace.stages.citation_check=true
   ```
3. 清库 + 导入资料 + compile + 查询 FG2
4. 在日志中搜索 `citation_terminal_unit_checked`，观察 `tu_guard` 字段
   - 如果 `tu_guard=null`，`tu_overlap_score=0.6667`，`tu_is_high_confidence=true` → terminal unit 路径应通过，问题在上层
   - 如果 `tu_guard=value_mismatch` 等 → guard 在某步返回了 null
   - 如果 `tu_claim_text` 与预期不同 → claimText 在 runtime 被修改
5. 日志中同时可见 `query_id`、`trace_id`，可关联到具体请求

---

## 10. 未提交文件提醒

- `src/main/resources/logback-spring.xml`（1 行变更）
