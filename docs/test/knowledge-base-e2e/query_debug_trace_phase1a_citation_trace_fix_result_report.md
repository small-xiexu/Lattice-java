# Query Debug Trace Phase 1A：Citation Trace 基础设施 — 修复结果报告

时间：2026-06-06
执行人：agentA（代码执行 Agent）
设计依据：`query_debug_trace_elk_ready_design_report.md`（agentB）

---

## 1. 本轮目标

实现 ELK-ready Query Debug Trace 的 Phase 1A：Citation Trace 基础设施 + CitationValidator / CitationCheckService 观测。

不改变任何业务行为，仅建立可长期保留、默认关闭详细 trace、可接 ELK/OpenSearch/Loki 的结构化观测能力。

---

## 2. 修改文件清单

### 新增文件

| 文件 | 职责 |
|------|------|
| `QueryTraceProperties.java` | `@ConfigurationProperties("lattice.query.trace")` 总开关 + 11 个 stage 独立开关 |
| `QueryTraceManager.java` | 统一 L1/L2 trace 事件输出入口，封装截断与 null-safe |

### 修改文件

| 文件 | 修改类型 |
|------|----------|
| `CitationValidator.java` | 新增 L1 trace（`citation_validated`）+ L2 trace（`citation_terminal_unit_checked`） |
| `CitationCheckService.java` | 新增 L1 trace（`citation_check_completed`） |
| `config/lattice-query.yml` | 新增 `lattice.query.trace` 配置段，默认全部关闭 |
| 8 个测试文件 | 构造器参数适配（CitationValidator +1, CitationCheckService +1） |

---

## 3. 新增配置说明

```yaml
lattice:
  query:
    trace:
      enabled: false                    # 总开关，默认关闭
      stages:
        citation_check: false           # CitationCheckService L2
        citation_validation: false      # CitationValidator L2
        # ... 其余 9 个 stage 默认 false
```

- **L1**：生产安全日志，默认开启（`enabled=false` 时仍不输出 L2，L1 通过 `traceManager.logL1Event` 无条件输出）
- **L2**：Debug 详细 trace，需要 `enabled=true` + 对应 `stages.*= true` 才输出

---

## 4. 新增 trace 事件与字段清单

### 4.1 `citation_check_completed`（L1）

| 字段 | 说明 |
|------|------|
| `claim_segment_count` | claim 段落数 |
| `total_citation_count` | 总 citation 数 |
| `verified_count` | 验证通过数 |
| `demoted_count` | 降级数 |
| `skipped_count` | 跳过数 |
| `coverage_rate` | 覆盖率 |
| `demotion_reason_distribution` | demotion 原因分布（Map<reason, count>） |
| `unused_projection_count` | 未使用 projection 数 |
| `projection_mismatch_count` | projection 不匹配数 |

### 4.2 `citation_validated`（L1，每条 citation 一条）

| 字段 | 说明 |
|------|------|
| `citation_ordinal` | citation 序号 |
| `source_type` | SOURCE_FILE / ARTICLE |
| `target_key` | 文件路径或文章 key |
| `validation_status` | VERIFIED / DEMOTED / SKIPPED / NOT_FOUND |
| `reason` | terminal_unit_evidence_verified / source_insufficient_overlap / ... |
| `validation_path` | TERMINAL_UNIT / DIRECT_LINE / RULE_OVERLAP / CONTEXT_WINDOW / INSUFFICIENT / NOT_FOUND |
| `hard_fact_token_count` | 硬事实 token 数 |
| `overlap_score` | 重叠分 |
| `matched_excerpt` | 匹配摘录（截断 80 字符） |

### 4.3 `citation_terminal_unit_checked`（L2，terminal unit 路径专用）

| 字段 | 说明 |
|------|------|
| `tu_guard` | guard 名称（repo_unavailable / no_terminal_units / not_key_value_claim） |
| `tu_source_file_id` | 源文件 ID |
| `tu_candidate_count` | terminal unit 候选数 |
| `tu_is_key_value_claim` | 是否为 key=value claim |
| `tu_claim_value_matched` | claim 值是否匹配 unit |
| `tu_overlap_score` | terminal unit overlap 分 |
| `tu_is_high_confidence` | 是否高置信度 |
| `tu_unit_index` | unit 遍历序号 |
| `tu_matched_unit_id` | 匹配的 unit ID |
| `tu_evidence_text` | evidence text（截断 200 字符） |
| `tu_claim_text` | claim 文本（截断 120 字符） |
| `tu_result` | 最终结果（"VERIFIED" / "null"） |

---

## 5. L1 / L2 默认开关状态

| 层级 | 默认 | 触发条件 |
|------|:---:|------|
| L1 | **开启**（traceManager 非 null 时无条件输出） | 无额外配置 |
| L2 | **关闭** | `lattice.query.trace.enabled=true` + `stages.citation_validation=true` 或 `stages.citation_check=true` |

---

## 6. 行为不变说明

- 未修改 `validate()` 的任何 guard、threshold、return 路径
- 未修改 `validateAgainstTerminalUnitEvidence()` 的任何判定逻辑
- 未修改 `check()` 的任何计数与 coverage 计算
- 未修改 citation validation 原有通过/降级结果
- trace 代码均为纯观测：null-safe，不影响业务执行路径

---

## 7. redline 结果

`BLOCKER=0`

---

## 8. 定向测试结果

```
CitationValidatorTests: 18/0/0/0
CitationCheckServiceTests: 10/0/0/0
合计: 28/0/0/0, BUILD SUCCESS
```

---

## 9. 全量 mvn test 结果

```
Tests run: 1016, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 10. FG2 后续验证建议

### 开启 citation L2 trace

在 `application-local-dev.yml` 或运行时环境变量中设置：

```yaml
lattice:
  query:
    trace:
      enabled: true
      stages:
        citation_validation: true
        citation_check: true
```

或在启动参数中：

```
-Dlattice.query.trace.enabled=true
-Dlattice.query.trace.stages.citation_validation=true
-Dlattice.query.trace.stages.citation_check=true
```

### agentD 应抓取的字段

1. 清库 + 重编译 + 查询 FG2
2. 在日志中搜索 `"eventName":"citation_validated"` 或 `event_name: citation_validated`
3. 关注 `validation_path` 字段——应为 `TERMINAL_UNIT` 或 `INSUFFICIENT`
4. 如果是 `INSUFFICIENT`，查找同 queryId 的 `citation_terminal_unit_checked` 事件
5. 关键字段：
   - `tu_guard` — 定位哪个 guard 返回了 null
   - `tu_is_key_value_claim` / `tu_claim_value_matched` — guard 决策
   - `tu_overlap_score` — 实际 overlap 值
   - `tu_is_high_confidence` — threshold 判定
   - `tu_claim_text` — validator 实际收到的 claimText

---

## 11. 风险与注意事项

| 风险 | 缓解 |
|------|------|
| 生产环境 L2 误开启导致日志量增加 | 默认全部关闭；总开关 `enabled: false` |
| trace 字段包含敏感信息 | claim_text 截断 120 字符；不记录 prompt/API key |
| traceManager 为 null 时 NPE | 所有 trace 方法 null-safe |
| 截断可能丢失关键信息 | 截断长度参考设计报告，后续可调整 |

---

## 12. 未提交文件提醒

以下文件为新增/修改，尚未提交：

- `src/main/java/com/xbk/lattice/query/citation/QueryTraceProperties.java`（新增）
- `src/main/java/com/xbk/lattice/query/citation/QueryTraceManager.java`（新增）
- `src/main/java/com/xbk/lattice/query/citation/CitationValidator.java`（修改）
- `src/main/java/com/xbk/lattice/query/citation/CitationCheckService.java`（修改）
- `src/main/resources/config/lattice-query.yml`（修改）
- 8 个测试文件（构造器适配）
