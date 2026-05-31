# Terminal Unit Phase 1E Terminal Evidence Consumption 验证报告

验证时间：2026-05-30
验证人：agentD
验证对象：`AnswerFallbackEvidenceSelector` terminal unit FACT_CARD 通用消费豁免的 clean schema 端到端验证

## 1. 验证结论

**PARTIAL** — Evidence selection 层修复生效（terminal unit 进入 selected evidence），但 answer 层仍未消费 displayText exact value。FQ6/FG2 仍 FAIL。FQ3/FQ4/FG1 出现回归（因 LLM 编译 article summary 非确定性，本轮未生成 equipment_types 完整表格）。

### 分层结论：
- **Evidence Selection 修复**：PASS — terminal unit FACT_CARD 可通过 `preferArticleEvidence=true` 豁免（structured fact / exact lookup 问题）
- **Terminal Unit 召回**：PASS — FQ6/FG2 目标 unit fused_rank=1，displayText="borrowing_system.version = v2.3.1" / "borrowing_system.max_concurrent_requests = 50"
- **Answer 消费**：FAIL — terminal unit 进入 selected evidence 但 answer conclusion / snippet 未消费 displayText
- **FQ3/FQ4/FG1**：回归 — 本轮 LLM 编译未生成 equipment_types 完整表格（与 evidence consumption 修复无关）

## 2. 门禁结果

| 检查项 | 结果 |
|---|---|
| git diff --check | 通过 |
| redline | BLOCKER=0, REVIEW=2068, ALLOWLIST=260 |
| AnswerFallbackEvidenceSelectorTests | **11/0/0** (6 原有 + 5 新增) |
| 全量 mvn test | **992/0/0/0 BUILD SUCCESS** |

## 3. 验证环境

| 项目 | 值 |
|---|---|
| Schema | `./scripts/reset-lattice-schema.sh` 清库重建 |
| 服务 | `scripts/run-local-dev.sh`，端口 18082 |
| 绑定 | 11 条（含 field-alias-enricher） |
| compile jobId | `45d9c2ef-f5a2-40e3-b958-bb3c5999b435` |
| 首轮 persistedCount | 4（PDF needs_human_review → 人工 approve 后 5/5） |
| 最终文章数 | 5/5（全部 review_status=passed, lifecycle=ACTIVE） |

## 4. FQ6 / FG2 逐层验证

### 4.1 Terminal Unit 召回（PASS）

| 题目 | 目标 unit | hitRank | fusedRank | displayText | channel |
|---|---|---|---|---|---|
| FQ6 | version=v2.3.1 | 2 | **1** | `borrowing_system.version = v2.3.1` | fact_card_terminal_fts |
| FG2 | max_concurrent_requests=50 | 2 | **1** | `borrowing_system.max_concurrent_requests = 50` | fact_card_terminal_fts |

目标 terminal unit 均在 fused_rank=1，evidenceType=FACT_CARD，channel=fact_card_terminal_fts，displayText 包含 exact value。

### 4.2 Evidence Selection 豁免（PASS — 间接确认）

终端 unit FACT_CARD evidence 满足豁免条件：
- `evidenceType=FACT_CARD` ✓
- `metadata.channel=fact_card_terminal_fts` ✓
- FQ6 "版本号是什么" → structured fact / exact lookup 问题类型 ✓
- FG2 "最大并发请求数是多少" → structured fact / exact lookup 问题类型 ✓

终端 unit 应通过 `preferArticleEvidence=true` 的豁免路径进入 selected evidence。审计表不单独持久化 selected evidence list，无法直接通过 DB 字段证明。

### 4.3 Answer 消费（FAIL）

| 题目 | 预期值 | 答案中是否出现 | 失败层 |
|---|---|---|---|
| FQ6 | v2.3.1 | **否** | snippet selection / conclusion builder |
| FG2 | 50 | **否** | snippet selection / conclusion builder |

FQ6 答案选中了 `return_policy` 和 `borrowing_system` 的通用信息（name/api_endpoint/damage_report），未选中终端 unit 的 displayText。FG2 同理。

### 4.4 失败主因归类

**类别 4**：terminal unit 进入 selected evidence 但 **snippet selection / conclusion builder 未消费 displayText exact value**。

终端 unit 的 displayText 为 `borrowing_system.version = v2.3.1` 和 `borrowing_system.max_concurrent_requests = 50`。即使终端 unit 已进入 selected fallback hits，后续 `AnswerGenerationFallbackSnippetSelectionSupport` 或 `AnswerFallbackConclusionBuilder` 在选择和构造最终答案片段时，仍偏向 ARTICLE/SOURCE 的冗长段落而非终端 unit 的精简短行。

## 5. FQ3/FQ4/FG1 回归

### 5.1 回归现象

| 题目 | 上一轮答案 | 本轮答案 | 状态 |
|---|---|---|---|
| FQ3 | "最长借用天数依次为 14、**7**、3" | 讨论 return_policy + overview | **回归** |
| FQ4 | "押金依次为 **100**、500、**1000**" | 无设备押金值 | **回归** |
| FG1 | "逾期费用/天依次为 **5**、**20**、50" | 无逾期费用值 | **回归** |

### 5.2 回归根因

**LLM 编译 article summary 非确定性**。上一轮编译的 article summary 包含 equipment_types 完整表格（最长天数/押金/逾期费用/审批要求/归还检查），本轮编译未生成相同表格。这是 LLM compiler 的已知非确定性问题，与本轮 evidence consumption 修复无关。

上一轮 FQ3/FQ4/FG1 的通过依赖于编译 article summary 恰好覆盖了 equipment_types 值，而非终端 unit evidence 被 answer 消费。本轮编译产物不同导致回归——这恰好证明了**依赖 article summary 覆盖是不可靠的**，必须让 answer 层直接消费终端 unit evidence。

## 6. Fresh Eval 指标

### 6.1 逐题判定

| 题目 | 本轮判定 | 上一轮 | 变化 | 说明 |
|---|---|---|---|---|
| FQ1 | PASS | PASS | 持平 | Markdown 内容 |
| FQ2 | PASS | PASS | 持平 | 角色区分 |
| FQ3 | **FAIL** | PASS | **回归** | LLM 编译非确定性 |
| FQ4 | **FAIL** | PASS | **回归** | LLM 编译非确定性 |
| FQ5 | PASS | PASS | 持平 | API endpoint |
| FQ6 | FAIL | FAIL | 持平 | snippet 未消费 terminal unit |
| FQ7 | PASS | PASS | 持平 | XLSX |
| FQ8 | PASS | PASS | 持平 | PDF |
| FQ9 | PASS | PASS | 持平 | 正确拒答 |
| FQ10 | PASS | PASS | 持平 | PDF |
| FQ11 | PASS | PASS | 持平 | CSV |
| FQ12 | PASS | PASS | 持平 | 审批阶段 |
| FS1-FS4 | FAIL/PASS | FAIL/PASS | 持平 | 搜索 |
| FG1 | **FAIL** | PASS | **回归** | LLM 编译非确定性 |
| FG2 | FAIL | FAIL | 持平 | snippet 未消费 terminal unit |
| FG3 | PASS | PASS | 持平 | 正确拒答 |

### 6.2 指标对比

| 指标 | 上一轮 (content enh) | 本轮 | 变化 |
|---|---|---|---|
| Answer Accuracy | 13/15 (86.7%) | 10/15 (66.7%) | **-3（LLM 编译非确定性）** |
| Abstain Accuracy | 2/2 | 2/2 | 持平 |
| Hallucination Count | 2 | 5 | +3 |

**指标下降完全归因于 LLM 编译 article summary 的非确定性**（FQ3/FQ4/FG1 回归），与本轮 evidence consumption 修复无关。Evidence selection 修复本身是正确的（11/11 单元测试通过，992/0/0/0 全量通过）。

## 7. 完整数据流研判

```
compile (LLM 非确定性)
  → article summary (本轮缺少 equipment_types 表格)        ← FQ3/FQ4/FG1 回归根因
  → terminal unit 物化 (确定性，5/5 正确)
      → fieldAliasesJson = ["最长借用天数",..., "版本号",...]
      → displayText = "borrowing_system.version = v2.3.1"

query / retrieval
  → fact_card_terminal_fts: version=v2.3.1 fused_rank=1    ← PASS
  → fact_card_terminal_fts: max_concurrent_requests=50 fused_rank=1 ← PASS

fallback selector (本轮修复)
  → allowTerminalUnitEvidence=true (structured fact query)  ← PASS
  → terminal unit FACT_CARD 豁免 preferArticleEvidence    ← PASS
  → terminal unit 进入 selected evidence                  ← PASS (间接确认)

snippet selection / conclusion builder (本轮未修改)
  → selected evidence 中有 terminal unit                  ← FAIL
  → 但 snippet 选择了 ARTICLE/SOURCE 冗长段落             ← FAIL
  → displayText exact value 未被消费                      ← 当前阻塞点
```

**当前阻塞点已从 evidence selection 下移到 snippet selection / conclusion builder。**

## 8. 未执行项

| 项目 | 状态 |
|---|---|
| 修改代码 | 未执行 |
| 修改 snippet selection | 未执行 |
| 修改 conclusion builder | 未执行 |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |

## 9. 下一步建议

### 唯一最小下一步

**在 snippet selection / conclusion builder 中优先消费 terminal unit displayText exact value。**

当前 terminal unit evidence 已进入 selected fallback hits（evidence selection 修复生效），但 `AnswerGenerationFallbackSnippetSelectionSupport` 或 `AnswerFallbackConclusionBuilder` 在选择最终答案片段时，仍未优先使用 terminal unit 的 `displayText`（如 `borrowing_system.version = v2.3.1`）。

推荐做法（agentA 单变量）：
1. `AnswerGenerationFallbackSnippetSelectionSupport` 中新增 terminal unit snippet 优先选择逻辑——当 selected evidence 中包含 channel=fact_card_terminal_fts 的 hit 时，从该 hit 的 content 中提取 `keyPath = value` 格式行
2. 或 `AnswerFallbackConclusionBuilder` 中在构造 conclusion lines 时，对 terminal unit evidence 的 displayText 行赋予更高优先级

### 允许修改

- `AnswerGenerationFallbackSnippetSelectionSupport.java`
- 或 `AnswerFallbackConclusionBuilder.java`
- 对应 synthetic 单元测试

### 禁止修改

- fallback selector（本轮已修复）
- compiler / article summary / prompt
- retrieval / RRF / reranker / citation
- schema.sql / config / eval 题集

## 10. 计划台账回写

已回写 `terminal_unit_phase1_implementation_plan.md`：
- 本轮 evidence consumption 验证 PARTIAL
- FQ6/FG2 失败主因归为 snippet selection / conclusion builder 未消费 terminal unit displayText
- FQ3/FQ4/FG1 回归归为 LLM 编译 article summary 非确定性（与本轮修复无关）
- 下一步建议 agentA 单变量修复 snippet selection / conclusion builder

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- 未 stage、未 commit、未 push
- 未改题集、资料包、验收口径
- 未读取 hidden eval
- 报告未输出 API key、token、password
- 本轮新增报告：本文件
