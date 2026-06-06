# ELK-ready Query Debug Trace 全链路观测设计报告

设计时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读架构设计，无代码修改

---

## 1. 背景与目标

### 1.1 当前状态

项目已有基础的 MDC 追踪设施（traceId/spanId/rootTraceId）和 `StructuredEventLogger`。`QueryGraphLifecycleListener` 在每个 graph 节点的开始/结束/失败时发出结构化事件。查询入口 `QueryFacadeService` 发出 `query_received` 和 `query_completed` 事件。

### 1.2 核心缺口

| 缺口 | 影响 |
|------|------|
| **5 个关键查询链路类零日志**：`CitationValidator`、`FactCardTerminalUnitFtsSearchService`、`FactCardTerminalUnitIntentReranker`、`AnswerFallbackEvidenceSelector`、`RrfFusionService` 均无任何观测 | 每轮排查都需要 agentA 临时加 `[TU_TRACE]` 日志，agentD 跑 gate，agentB 分析，然后 agentA 删除日志 |
| **中游管道操作无结构化事件**：FTS 搜索、rerank、RRF 融合、evidence 选择、citation 验证全部静默执行 | FG2 citation binding 问题花费了 3 轮 agent 协作仍无法定位根因 |
| **TU_TRACE 是孤立的约定**：仅 `AnswerFallbackConclusionBuilder` 使用，无统一 trace 开关、无结构化字段、无法接 ELK |
| **Micrometer Observation 被禁用**：`ChatClientRegistry` 和 `EmbeddingClientFactory` 硬编码 `ObservationRegistry.NOOP` | 无分布式追踪 span |

### 1.3 目标

设计一套**可长期保留、默认可关闭详细 trace、可接 ELK/OpenSearch/Loki 的结构化日志体系**，使后续 public eval、runtime gate、baseline 或线上问题出现时，能通过 traceId、stage、decision_reason 等字段**一步定位失败阶段和失败原因**，不再依赖临时 debug 日志循环。

---

## 2. 当前排查痛点

| 痛点 | 根因 | 本设计如何解决 |
|------|------|---------------|
| FG2 citation binding 根因需 3 轮 agent 协作仍无法定位 | `CitationValidator.validate()` 零日志，无法确认真实执行路径 | citation trace 记录每步 guard 决策、tokens、overlap、最终路径 |
| 每次排查都需 agentA 临时加 log → agentD 跑 gate → agentB 分析 → agentA 删 log | 无持久化的结构化 trace 开关 | 模块级 trace 开关，默认关闭，eval 时打开，长期保留在代码中 |
| `AnswerFallbackEvidenceSelector` 的选择决策不可见 | 零日志，无法知道哪个 gate 被触发、为什么某些证据被丢弃 | evidence selector trace 记录 gate、筛选前后数量、丢弃原因 |
| RRF 融合结果无法追溯 | `RrfFusionService.fuse()` 无日志 | RRF trace 记录通道权重、身份 key、merged score |
| 无法区分"检索未召回"和"rerank 排序低" | 检索和 rerank 均无观测 | retrieval trace 记录每通道命中数；rerank trace 记录输入/输出排序变化 |

---

## 3. 全链路 Stage Map

```
┌──────────┐   ┌──────────┐   ┌──────────────┐   ┌──────────┐   ┌───────────┐
│  QUERY   │ → │ REWRITE  │ → │  RETRIEVAL   │ → │  RERANK  │ → │  EVIDENCE │
│  ENTRY   │   │          │   │  (12 channels)│   │          │   │  SELECTOR │
└──────────┘   └──────────┘   └──────────────┘   └──────────┘   └───────────┘
                                                                       │
                                                                       ▼
┌──────────┐   ┌──────────┐   ┌──────────────┐   ┌──────────┐   ┌───────────┐
│FINALIZE  │ ← │CITATION  │ ← │  CITATION    │ ← │ FALLBACK │ ← │    RRF    │
│          │   │ REPAIR   │   │  CHECK       │   │ OUTCOME  │   │  FUSION   │
└──────────┘   └──────────┘   └──────────────┘   └──────────┘   └───────────┘
      │
      ▼
┌──────────┐
│ RESPONSE │
│ (END)    │
└──────────┘
```

共 11 个 stage，每个 stage 有独立 trace 开关。

---

## 4. traceId / requestId / evalRunId 传播方案

### 4.1 现有基础设施（复用）

- `QueryGraphState` 已有 `traceId`、`spanId`、`rootTraceId` 字段
- `QueryGraphLifecycleListener` 已实现 graph 节点间的 MDC 注入/恢复
- `StructuredEventLogger` 已实现从 Micrometer `Tracer` 和 MDC 读取 traceId
- `logstash-logback-encoder` 已在非 local-dev profile 下启用 JSON 输出

### 4.2 新增：evalRunId

当请求来自 eval/baseline/benchmark runner 时，通过 HTTP header `X-Lattice-Eval-Run-Id` 传入。`QueryFacadeService` 在 `query_received` 事件中记录此字段。

```
eval runner 发请求 → header: X-Lattice-Eval-Run-Id: eval-20260606-pe2
  → QueryFacadeService 读取 header → 写入 MDC("evalRunId")
    → 后续所有 StructuredEventLogger 调用自动携带 evalRunId
```

**传播链**：`evalRunId` → MDC → StructuredEventLogger → JSON log → ELK 索引。

### 4.3 traceId 生命周期

```
QueryFacadeService.query()
  → queryId = UUID.randomUUID()                        (查询级唯一标识)
  → traceId = MDC.get("traceId") ?? queryId            (链路追踪标识)
  → MDC.put("traceId", traceId)
  → QueryGraphState.setTraceId(traceId)
  → QueryGraphLifecycleListener 在每个节点注入 MDC
  → StructuredEventLogger 所有调用自动携带 traceId
  → 返回 response 中包含 traceId（便于 agentD 从 API 响应直接拿到）
```

### 4.4 传播矩阵

| 标识 | 来源 | 注入点 | 生命周期 | ELK 字段名 |
|------|------|--------|----------|-----------|
| `queryId` | `UUID.randomUUID()` | `QueryFacadeService` | 单次查询 | `query_id` |
| `traceId` | MDC / Micrometer Tracer | `QueryGraphOrchestrator` | 单次查询 | `trace_id` |
| `spanId` | 每个 stage 生成 | 各 stage trace 方法 | 单个 stage | `span_id` |
| `rootTraceId` | 首个 traceId | `QueryGraphOrchestrator` | 整个请求链 | `root_trace_id` |
| `evalRunId` | HTTP header `X-Lattice-Eval-Run-Id` | `QueryFacadeService` | 单次 eval run | `eval_run_id` |

---

## 5. 日志分层策略

### 5.1 两层模型

| 层级 | 名称 | 默认状态 | 适用场景 | 日志级别 | ELK 索引 |
|------|------|:---:|------|:---:|------|
| **L1** | 生产安全日志 | **开启** | 生产、线上、日常开发 | INFO | `lattice-query-*` |
| **L2** | Eval/Debug 详细 Trace | **关闭** | 本地 eval、runtime gate、问题复现 | DEBUG | `lattice-query-debug-*` |

### 5.2 L1 生产安全日志规范

- 每个 stage 记录一条 INFO 级别结构化事件
- 只记录**数量和结果**：hitCount、verifiedCount、coverageRate、outcome
- 不记录候选内容、分数、阈值比较细节
- 不记录 token、prompt、文档原文
- 单条日志体积 < 2KB

### 5.3 L2 Eval/Debug Trace 规范

- 通过模块开关控制（默认全部关闭）
- 记录候选证据的核心字段、分数、阈值、保留/丢弃原因
- 记录 citation 验证的每步 guard 决策
- 记录 rerank 前后的排序变化
- 单条日志体积 < 16KB（超出部分截断）

---

## 6. 模块开关设计

### 6.1 配置位置

`application.yml` 或 `lattice-query.yml`：

```yaml
lattice:
  query:
    trace:
      enabled: false                    # 总开关，默认关闭
      stages:
        query_entry: false
        rewrite: false
        retrieval: false
        rerank: false
        rrf_fusion: false
        evidence_selector: false
        fallback_outcome: false
        citation_check: false
        citation_validation: false
        answer_generation: false
        finalize: false
```

### 6.2 动态开关

通过 Spring `@ConfigurationProperties` 绑定，支持运行时通过 `/api/v1/admin/trace/config` 查看和修改（无需重启）。

### 6.3 eval runner 自动开启

当 `evalRunId` 存在时（通过 HTTP header 传入），自动将 `lattice.query.trace.enabled` 设为 `true`，并将所有 stage 开关设为 `true`。eval 结束后由 runner 通过 API 关闭。

---

## 7. ELK-ready 字段规范

### 7.1 通用字段（每个事件均携带）

| 字段名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `@timestamp` | date | ISO8601 | `2026-06-06T10:30:00.123+08:00` |
| `service` | keyword | 服务名 | `lattice-java` |
| `event_name` | keyword | 事件名 | `retrieval_channel_completed` |
| `event_level` | keyword | 层级 | `L1` / `L2` |
| `trace_id` | keyword | 链路追踪 ID | `abc123-def456` |
| `span_id` | keyword | Stage span ID | `retrieval-001` |
| `root_trace_id` | keyword | 根追踪 ID | `abc123-def456` |
| `query_id` | keyword | 查询 ID | `550e8400-e29b-...` |
| `eval_run_id` | keyword | Eval run ID（可选） | `eval-20260606-pe2` |
| `stage` | keyword | 阶段名 | `retrieval` |
| `duration_ms` | long | 阶段耗时 | `245` |

### 7.2 字段命名规范

- 全小写 + 下划线：`field_name`
- keyword 类型用于枚举/ID：`status`、`reason`、`channel`
- text 类型用于可搜索文本：`question`（脱敏后）
- long/integer 用于计数：`hit_count`、`token_count`
- double 用于分数：`overlap_score`、`fused_score`
- 嵌套对象用 `_` 前缀区分层级：无，扁平化设计

### 7.3 禁止记录的字段

| 禁止字段 | 原因 |
|----------|------|
| API key / token / password | 安全 |
| 完整 LLM prompt | 安全 + 体积 |
| 完整文档原文（> 200 字符） | 体积 + 安全 |
| hidden eval 题目 / 标准答案 / case id / expected citation | eval 安全 |

---

## 8. 分阶段 Trace 点清单

### 8.1 Stage: QUERY_ENTRY

| 项 | 值 |
|---|-----|
| event_name | `query_received`（已有）/ `query_routed` |
| 所在类/方法 | `QueryFacadeService.routeAndExecute()` |
| 为什么需要 | 确认请求进入哪个执行路径、queryId 生成 |
| L1 必须字段 | `query_id`, `question_length`, `selected_route`, `force_deep` |
| L2 可选字段 | `preflight_hit_count`, `preflight_top_score`, `deep_research_escalation_evaluated` |
| 脱敏/截断 | question 截断至 100 字符 |
| 可定位失败类型 | 路由错误（应走 graph 却走了 structured） |
| 日志级别 | INFO |
| 默认开启 | 是（L1） |

### 8.2 Stage: REWRITE

| 项 | 值 |
|---|-----|
| event_name | `query_rewrite_completed` |
| 所在类/方法 | `QueryRewriteService.rewrite()` |
| 为什么需要 | 确认 query rewrite 是否生效、哪些规则触发 |
| L1 必须字段 | `query_id`, `is_applied`, `matched_rule_count` |
| L2 可选字段 | `original_question`（截断 80 字符）, `rewritten_question`（截断 120 字符）, `matched_rule_codes` |
| 脱敏/截断 | question 截断 |
| 可定位失败类型 | rewrite 过度/不足导致检索偏离 |
| 日志级别 | INFO |
| 默认开启 | 是（L1） |

### 8.3 Stage: RETRIEVAL

| 项 | 值 |
|---|-----|
| event_name | `retrieval_channel_completed`（每通道一条） |
| 所在类/方法 | `RetrievalDispatcher.dispatch()` |
| 为什么需要 | 确认每通道是否执行、命中数、耗时 |
| L1 必须字段 | `query_id`, `channel`, `status`, `hit_count`, `duration_ms` |
| L2 可选字段 | `skipped_reason`, `error_summary`, `search_input`（脱敏）, `ts_config` |
| 脱敏/截断 | search_input 截断至 100 字符 |
| 可定位失败类型 | 检索未召回（某通道 hit_count=0）、通道超时/禁用 |
| 日志级别 | INFO |
| 默认开启 | 是（L1） |

| 项 | 值 |
|---|-----|
| event_name | `retrieval_dispatch_completed`（汇总一条） |
| 所在类/方法 | `RetrievalDispatcher.dispatch()` |
| L1 必须字段 | `query_id`, `total_channels`, `active_channels`, `total_hits`, `total_duration_ms`, `parallel_mode` |
| L2 可选字段 | `cancelled_channel_count`, `max_concurrency` |

### 8.4 Stage: RERANK

| 项 | 值 |
|---|-----|
| event_name | `rerank_completed` |
| 所在类/方法 | `FactCardTerminalUnitIntentReranker.rerank()` |
| 为什么需要 | 确认 rerank 是否改变排序、哪些信号主导 |
| L1 必须字段 | `query_id`, `input_count`, `output_count`, `hits_with_field_intent` |
| L2 可选字段 | `top3_before`（key_path + score）, `top3_after`（key_path + adjustedScore）, `question_has_numeric_intent`, `field_match_distribution`（0/1-2/3+ 各多少条）, `sibling_boost_applied_count` |
| 脱敏/截断 | key_path 截断至 80 字符 |
| 可定位失败类型 | rerank 排序低（目标候选 rerank 后排名下降） |
| 日志级别 | L1=INFO, L2=DEBUG |
| 默认开启 | L1 是, L2 否 |

### 8.5 Stage: RRF_FUSION

| 项 | 值 |
|---|-----|
| event_name | `rrf_fusion_completed` |
| 所在类/方法 | `RrfFusionService.fuse()` |
| 为什么需要 | 确认融合后候选 identity、score、结构化保护是否生效 |
| L1 必须字段 | `query_id`, `channel_count`, `total_merged_hits`, `output_limit`, `rrf_k` |
| L2 可选字段 | `channel_weights`, `top8_keys`（identity + fused_score）, `structured_guardrail_active`, `replaced_background_count`, `evidence_tier_breakdown`（tier0/tier1/tier2/tier3 各多少）, `answer_shape` |
| 脱敏/截断 | identity key 截断至 120 字符 |
| 可定位失败类型 | 身份折叠（同 articleKey 合并）、结构化证据被背景压制 |
| 日志级别 | L1=INFO, L2=DEBUG |
| 默认开启 | L1 是, L2 否 |

### 8.6 Stage: EVIDENCE_SELECTOR

| 项 | 值 |
|---|-----|
| event_name | `evidence_selection_completed` |
| 所在类/方法 | `AnswerFallbackEvidenceSelector.selectFallbackEvidenceHits()` |
| 为什么需要 | 确认 fallback 证据筛选的 gate 决策、证据类型分布 |
| L1 必须字段 | `query_id`, `input_hit_count`, `output_hit_count`, `selected_gate` |
| L2 可选字段 | `evidence_type_breakdown`（ARTICLE/SOURCE/FACT_CARD/CONTRIBUTION/GRAPH 各多少）, `prefer_article_evidence`, `comparison_option_count`, `high_signal_token_count`, `path_contract_enrichment_applied`, `direct_structured_retained` |
| 脱敏/截断 | 无敏感字段 |
| 可定位失败类型 | 证据选择 gate 错误（应选 article 却选了 mixed）、terminal unit 证据被过滤、path contract 未补强 |
| 日志级别 | L1=INFO, L2=DEBUG |
| 默认开启 | L1 是, L2 否 |

### 8.7 Stage: FALLBACK_OUTCOME

| 项 | 值 |
|---|-----|
| event_name | `fallback_outcome_determined` |
| 所在类/方法 | `AnswerFallbackConclusionBuilder.buildEvidenceConclusionLines()` / 现有 `[TU_TRACE]` 升级 |
| 为什么需要 | 确认 fallback 选择 winner 的逻辑、多目标聚合是否触发 |
| L1 必须字段 | `query_id`, `fallback_type`（"terminal_unit"/"general"/"comparison"/"exact_path"/"aggregated"/"none"）, `conclusion_line_count` |
| L2 可选字段 | `winner_terminal_key`, `winner_parent_path`, `winner_ftmc`, `winner_atmc`, `winner_fs`, `additional_candidate_count`, `entity_context_guard_passed_count` |
| 脱敏/截断 | terminal_key/parent_path 截断至 80 字符 |
| 可定位失败类型 | 证据已召回但回答漏点（winner 选错字段）、多目标聚合缺失 |
| 日志级别 | L1=INFO, L2=DEBUG |
| 默认开启 | L1 是, L2 否 |

### 8.8 Stage: CITATION_CHECK

| 项 | 值 |
|---|-----|
| event_name | `citation_check_completed` |
| 所在类/方法 | `CitationCheckService.check()` |
| 为什么需要 | 确认 citation 检查的覆盖率、demotion 原因分布 |
| L1 必须字段 | `query_id`, `claim_segment_count`, `total_citation_count`, `verified_count`, `demoted_count`, `coverage_rate` |
| L2 可选字段 | `demotion_reason_distribution`（projection_literal_not_found: N, source_insufficient_overlap: M, ...）, `active_projection_count`, `unused_projection_count`, `should_repair` |
| 脱敏/截断 | 无敏感字段 |
| 可定位失败类型 | 引用错误（demotion 原因分布）、projection 不匹配 |
| 日志级别 | L1=INFO, L2=DEBUG |
| 默认开启 | L1 是, L2 否 |

### 8.9 Stage: CITATION_VALIDATION

| 项 | 值 |
|---|-----|
| event_name | `citation_validated`（每条 citation 一条） |
| 所在类/方法 | `CitationValidator.validate()` |
| 为什么需要 | **FG2 类问题的核心观测点**——确认每条 citation 走哪条验证路径、每步 guard 决策、overlap 计算 |
| L1 必须字段 | `query_id`, `citation_ordinal`, `source_type`, `validation_status`, `reason` |
| L2 可选字段 | `validation_path`（"TERMINAL_UNIT"/"DIRECT_LINE"/"RULE_OVERLAP"/"NEAR_COMPLETE"/"CONTEXT_WINDOW"/"INSUFFICIENT"/"NOT_FOUND"）, `hard_fact_token_count`, `hard_fact_tokens`（截断）, `overlap_score`, `matched_excerpt`（截断 80 字符）, `target_key` |
| L2 terminal_unit 子字段（仅当 path=TERMINAL_UNIT） | `tu_source_file_id`, `tu_candidate_count`, `tu_matched_unit_id`, `tu_is_key_value_claim`, `tu_claim_value_matched`, `tu_evidence_text`（截断）, `tu_overlap_score`, `tu_is_high_confidence` |
| 脱敏/截断 | hard_fact_tokens 截断至 200 字符, matched_excerpt 截断至 80 字符, tu_evidence_text 截断至 200 字符 |
| 可定位失败类型 | 引用错误（具体到哪条 citation、哪个验证步骤失败） |
| 日志级别 | L1=INFO, L2=DEBUG |
| 默认开启 | L1 是, L2 否 |

### 8.10 Stage: CITATION_REPAIR

| 项 | 值 |
|---|-----|
| event_name | `citation_repair_applied` |
| 所在类/方法 | `QueryFinalizationGraphFragment.citationRepair()` |
| 为什么需要 | 确认 citation repair 是否触发、修复了几次 |
| L1 必须字段 | `query_id`, `repair_applied`, `repair_attempt_count`, `outcome_changed` |
| L2 可选字段 | `repair_reason`, `answer_outcome_before`, `answer_outcome_after` |
| 脱敏/截断 | 无敏感字段 |
| 可定位失败类型 | citation repair 过度触发、无限循环 |
| 日志级别 | INFO |
| 默认开启 | 是（L1） |

### 8.11 Stage: FINALIZE

| 项 | 值 |
|---|-----|
| event_name | `query_finalized` |
| 所在类/方法 | `QueryFinalizationGraphFragment.finalizeResponse()` / `QueryFacadeService` 现有 `query_completed` 扩展 |
| 为什么需要 | 最终响应摘要 |
| L1 必须字段 | `query_id`, `answer_outcome`, `generation_mode`, `model_execution_status`, `fallback_reason`, `source_count`, `article_count`, `has_fused_hits`, `citation_coverage_rate`, `total_duration_ms` |
| L2 可选字段 | `citation_repair_attempt_count`, `cache_hit` |
| 脱敏/截断 | 无敏感字段 |
| 可定位失败类型 | 综合——与其他 stage trace 交叉引用定位全链路断点 |
| 日志级别 | INFO |
| 默认开启 | 是（L1） |

---

## 9. Citation / Terminal Unit 专项 Trace 设计

### 9.1 背景

FG2 问题的排查暴露了 `CitationValidator.validateAgainstTerminalUnitEvidence()` 的完全不可观测性。手工复算表明该路径应通过（overlapScore=0.6667 >= 0.66），但 runtime 却走回了 source overlap 路径（0.600）。无法确定是哪一步 guard 返回了 null。

### 9.2 专项 Trace 点

在 `CitationValidator.validateAgainstTerminalUnitEvidence()` 的每个 guard 决策点插入 L2 trace：

| trace_point | event_name 后缀 | 记录字段 |
|-------------|---------------|----------|
| 方法入口 | `_enter` | `source_file_id`, `hard_fact_token_count`, `claim_text`（截断 120 字符） |
| `isKeyValueClaim` 判定 | `_is_kv_claim` | `is_key_value_claim`, `eq_index` |
| terminal units 查询结果 | `_tu_query` | `tu_candidate_count` |
| 每条 unit 遍历 | `_tu_unit` | `unit_index`, `display_text`（截断）, `value_text`, `evidence_text_length` |
| `claimValueMatchesUnit` | `_tu_value_match` | `claim_value_matched`, `claim_value`, `unit_value_text` |
| `calculateOverlapScore` | `_tu_overlap` | `overlap_score`, `claim_token_count`, `evidence_token_count`, `matched_count` |
| `isHighConfidencePartialOverlap` | `_tu_threshold` | `is_high_confidence`, `token_count`, `overlap_score`, `threshold_met_4_75`, `threshold_met_2_66` |
| 最终返回 | `_result` | `result_status`（"VERIFIED"/"null"）, `reason` |

### 9.3 示例 L2 trace 事件（JSON）

```json
{
  "@timestamp": "2026-06-06T10:30:00.123+08:00",
  "service": "lattice-java",
  "event_name": "citation_validated",
  "event_level": "L2",
  "trace_id": "abc123",
  "query_id": "65999750-...",
  "eval_run_id": "eval-20260606-pe2",
  "stage": "citation_validation",
  "citation_ordinal": 0,
  "source_type": "SOURCE_FILE",
  "target_key": "equipment-borrowing-policy.yaml",
  "validation_status": "DEMOTED",
  "reason": "source_insufficient_overlap",
  "validation_path": "INSUFFICIENT",
  "hard_fact_token_count": 6,
  "hard_fact_tokens": ["50","borrowing_system","max_concurrent_requests","confirmed","evidence","borrowing_system.max_concurrent_requests"],
  "overlap_score": 0.6,
  "matched_excerpt": "borrowing_system:",
  "tu_source_file_id": 2,
  "tu_candidate_count": 1,
  "tu_is_key_value_claim": true,
  "tu_claim_value_matched": true,
  "tu_overlap_score": 0.6667,
  "tu_is_high_confidence": true,
  "tu_threshold_met_2_66": true
}
```

---

## 10. FG2 问题需要的最小 Trace 字段

回顾 FG2 排查过程，如果当时 `citation_validated` 事件已经存在，只需要在 ELK 中查询：

```
event_name: "citation_validated"
AND query_id: "65999750"
AND stage: "citation_validation"
```

就能直接看到：
1. `validation_path` = "INSUFFICIENT"（不是 TERMINAL_UNIT）→ terminal unit 路径未命中
2. `tu_is_key_value_claim` = true → guard 1 通过
3. `tu_candidate_count` = 1 → terminal unit 被查到
4. `tu_claim_value_matched` = true → guard 3 通过
5. `tu_overlap_score` = 0.6667 → guard 4 得分
6. `tu_is_high_confidence` = ? → **关键字段**：如果为 false，则定位到 `isHighConfidencePartialOverlap` 的浮点/边界问题；如果为 true，则定位到更上游的 guard

**一步定位，不需要 3 轮 agent 协作。**

---

## 11. 脱敏、截断、采样与日志量控制

### 11.1 脱敏规则

| 数据类别 | 处理方式 |
|----------|----------|
| API key / token / password | **完全禁止记录** |
| LLM prompt 全文 | **完全禁止记录**（仅记录 prompt 字节数） |
| 文档原文 | 截断至 200 字符 |
| 用户问题 | L1 截断至 100 字符，L2 截断至 200 字符 |
| file_path | 记录（非敏感） |
| terminal_key / key_path | 截断至 80 字符 |
| display_text / value_text | 截断至 120 字符 |
| hidden eval 题目/答案/case id | **完全禁止记录** |

### 11.2 截断规则

统一使用 `StringUtils.truncate(text, maxLength)` 或等价方法，在日志方法内部截断，不修改原始数据。

### 11.3 采样策略

| 环境 | 策略 |
|------|------|
| 生产 | L1 100% 采样，L2 关闭 |
| 本地 eval | L1+L2 100% 采样（eval run 通常 < 20 条 query） |
| runtime gate | L1+L2 100% 采样 |
| 线上 debug | L2 按 `trace_id` hash 采样 1% |

### 11.4 日志量估算

| 场景 | query 数 | L1 事件数/query | L2 事件数/query | 总事件数 |
|------|:---:|:---:|:---:|:---:|
| PE2 eval (14 题) | 14 | ~10 | ~50 | L1=140, L2=700 |
| 完整 gate (PE1+PE2) | 26 | ~10 | ~50 | L1=260, L2=1300 |
| 生产单次查询 | 1 | ~10 | 0（关闭） | 10 |

L2 全开时每 query 约 50 条 debug 事件，每条 < 2KB，共 < 100KB/query。对于 eval 场景完全可接受。

---

## 12. ELK / OpenSearch / Loki 查询示例

### 12.1 按 evalRunId 查看某次 eval 全部阶段

```
eval_run_id: "eval-20260606-pe2" AND event_level: "L1"
| sort @timestamp asc
| table stage, event_name, duration_ms, hit_count, coverage_rate
```

### 12.2 定位 citation coverage=0 的 query

```
event_name: "citation_check_completed" AND coverage_rate: 0.0
| table query_id, total_citation_count, verified_count, demoted_count
```

### 12.3 钻取某 query 的全部 citation 验证细节

```
query_id: "65999750" AND stage: "citation_validation" AND event_level: "L2"
| sort citation_ordinal asc
| table citation_ordinal, validation_status, reason, validation_path, 
        tu_is_key_value_claim, tu_claim_value_matched, tu_overlap_score, tu_is_high_confidence
```

### 12.4 检索未召回分析

```
stage: "retrieval" AND event_name: "retrieval_channel_completed" 
AND hit_count: 0
| table query_id, channel, status, skipped_reason
```

### 12.5 RRF 身份折叠检测

```
stage: "rrf_fusion" AND event_level: "L2"
| table query_id, top8_keys, structured_guardrail_active, evidence_tier_breakdown
```

### 12.6 按失败类型聚合

```
event_name: "query_finalized" AND answer_outcome: "PARTIAL_ANSWER"
| stats count by fallback_reason
```

---

## 13. agentA 后续实现批次建议

### Phase 1：基础设施 + Citation Trace（最高优先级）

**理由**：FG2 问题直接暴露了 citation 验证的不可观测性；traceId/spanId 是后续所有 trace 的基础。

| 任务 | 涉及文件 | 风险 |
|------|----------|:---:|
| 1.1 `QueryTraceProperties` 配置类 | 新增 `QueryTraceProperties.java` | 低 |
| 1.2 `QueryTraceManager` 统一 trace 入口 | 新增 `QueryTraceManager.java` | 低 |
| 1.3 `CitationValidator` 加 L1+L2 trace | `CitationValidator.java` | 中 |
| 1.4 `CitationCheckService` 加 L1 trace | `CitationCheckService.java` | 低 |
| 1.5 在 `QueryResponse` 中返回 `traceId` | `QueryResponse.java` + `QueryFinalizationGraphFragment.java` | 低 |
| 1.6 `evalRunId` 从 HTTP header 注入 MDC | `QueryFacadeService.java` | 低 |

**验证**：PE2 全部 FALLBACK 题目 → 检查 `citation_validated` 事件中 `validation_path`、`tu_is_high_confidence` 字段 → FG2 问题可一步定位。

### Phase 2：Retrieval + Rerank + RRF Trace

| 任务 | 涉及文件 | 风险 |
|------|----------|:---:|
| 2.1 `RetrievalDispatcher` 加 L1 trace | `RetrievalDispatcher.java` | 低 |
| 2.2 `FactCardTerminalUnitIntentReranker` 加 L1+L2 trace | `FactCardTerminalUnitIntentReranker.java` | 低 |
| 2.3 `RrfFusionService` 加 L1+L2 trace | `RrfFusionService.java` | 中 |
| 2.4 `AnswerFallbackEvidenceSelector` 加 L1 trace | `AnswerFallbackEvidenceSelector.java` | 中 |

**验证**：PE2 FS1-FS4 搜索 → 检查 `retrieval_channel_completed` 每通道 hit_count → FS4b "B级" 可确认 mixed script channel 是否命中。

### Phase 3：Fallback + Answer Generation + Grounding Trace

| 任务 | 涉及文件 | 风险 |
|------|----------|:---:|
| 3.1 `AnswerFallbackConclusionBuilder` 现有 `[TU_TRACE]` 升级为 L2 trace | `AnswerFallbackConclusionBuilder.java` | 低 |
| 3.2 `QueryFinalizationGraphFragment` 加 L1 trace | `QueryFinalizationGraphFragment.java` | 低 |
| 3.3 `QueryRewriteService` 加 L1 trace | `QueryRewriteService.java` | 低 |

**验证**：PE2 fallback 题目 → 检查 fallback type、conclusion_line_count、winner 字段。

### Phase 4：Eval Runner 集成 + 日志归档

| 任务 | 涉及文件 | 风险 |
|------|----------|:---:|
| 4.1 eval runner 自动传 `X-Lattice-Eval-Run-Id` header | eval runner 脚本/测试 | 低 |
| 4.2 eval runner 结束后自动关 L2 trace | `QueryTraceManager` + eval runner | 低 |
| 4.3 日志归档脚本（按 evalRunId 导出 JSON 日志） | 新增 scripts | 低 |

---

## 14. 风险与注意事项

| 风险 | 缓解措施 |
|------|----------|
| L2 trace 在生产环境误开启导致日志爆炸 | 总开关 `lattice.query.trace.enabled` 默认 false；生产配置中显式关闭 |
| trace 字段包含敏感信息（API key 等） | 严格脱敏规范 + Code Review checklist |
| trace 日志体积影响 eval 性能 | L2 仅在 eval（< 20 query）时开启，日志量 < 2MB/run |
| 与现有 `[TU_TRACE]` 并存的混乱 | Phase 3 将现有 `[TU_TRACE]` 升级为统一 L2 trace，移除旧日志 |
| Micrometer Observation 集成冲突 | 本设计不依赖 Micrometer Observation（当前已 NOOP），使用独立的 SLF4J + StructuredEventLogger |
| 字段命名不一致导致 ELK 查询困难 | 严格遵循第 7 节字段规范 + Code Review |

---

## 15. 本轮未修改代码声明

- [x] 未修改生产代码（`src/main/java/**`）
- [x] 未修改测试代码（`src/test/java/**`）
- [x] 未修改 `src/main/resources/**`
- [x] 未修改 `scripts/**`
- [x] 未修改 prompt / config / schema / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未读取 hidden eval
- [x] 本报告为纯架构设计，不包含任何 case 特判或硬编码
- [x] 所有 stage/trace 点设计基于通用管线组件，不绑定具体业务域、文件名、题号或答案
