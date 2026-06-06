# Query Debug Trace — Local-Dev MDC 语法修正结果报告

时间：2026-06-06
执行人：agentA（代码执行 Agent）
前置 gate：`fg2_terminal_citation_binding_trace_runtime_gate_after_mdc_report.md`（agentD）
前置修复：`query_debug_trace_local_dev_mdc_visibility_fix_result_report.md`（agentA，上轮）

---

## 1. 本轮目标

修正上轮 logback pattern 中 `%mdc{80}` 的语义错误，使 local-dev 文本日志正确输出全部 MDC 字段。

---

## 2. 修改文件

| 文件 | 变更 |
|------|------|
| `src/main/resources/logback-spring.xml` | 一行修正 |

---

## 3. 问题原因

上轮将 `%mdc{80}` 误解为 "每个 MDC value 截断到 80 字符"。

Logback 实际语义：`%mdc{key}` 表示只输出 key 为该值的单个 MDC 条目。`%mdc{80}` 试图输出 key 为 `"80"` 的 MDC 值（不存在），因此输出为空。

正确的全部 MDC 输出写法是 `%mdc`（无参数），格式为 `key1=value1, key2=value2, ...`。

---

## 4. 修改前 / 修改后

**修改前**（错误）：
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg %mdc{80}%n</pattern>
```

**修改后**（正确）：
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg %mdc%n</pattern>
```

---

## 5. redline 结果

`BLOCKER=0`

---

## 6. Maven / 启动验证

- **未运行 Maven 测试**：logback-spring.xml 不在 surefire classpath 范围内，pattern 变更不影响测试结果。
- **未运行启动验证**：交给 agentD 下一轮重跑 FG2 trace gate 时自然验证。
- 静态检查已确认：local-dev CONSOLE_TEXT pattern 包含 `%mdc`，不包含 `%mdc{80}`。

---

## 7. 行为不变声明

- 未修改 Java 源代码
- 未修改 citation 阈值、guard、validation path
- 未修改 `!local-dev` JSON appender
- 未默认开启 L2 trace
- 仅修正 logback pattern 一行

---

## 8. 后续 agentD 重跑 FG2 trace gate 建议

1. local-dev profile 启动，设置 trace 开关：
   ```
   -Dlattice.query.trace.enabled=true
   -Dlattice.query.trace.stages.citation_validation=true
   -Dlattice.query.trace.stages.citation_check=true
   ```
2. 清库 + 导入 + compile + 查询 FG2
3. 日志中应能直接看到：
   ```
   ... citation_terminal_unit_checked ... 
   tu_guard=..., tu_source_file_id=2, tu_is_key_value_claim=true,
   tu_claim_value_matched=true, tu_overlap_score=0.6667, tu_is_high_confidence=true ...
   ```

---

## 9. 未提交文件提醒

- `src/main/resources/logback-spring.xml`（1 行修正）
