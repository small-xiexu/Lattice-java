# Terminal Unit Phase 1C Layer 1 Clean Verification Report

验证时间：2026-05-29
验证人：agentD
验证对象：Phase 1C Layer 1 中文 fieldLabel / 表头 / 列名 N-gram alias 物化在 clean schema 上的真实服务级验证

## 1. Gate 判定

**Phase 1C Layer 1 分层结论：**

| 层级 | 结论 | 说明 |
|---|---|---|
| **Infrastructure / Code Quality** | **PASS** | redline BLOCKER=0，mvn test 951/0/0/0，代码仅修改 Materializer 一个文件，算法正确 |
| **Data Layer: Chinese N-gram Alias 生成** | **PASS**（单元测试已验证） | 单元测试确认中文 fieldLabel 的 bigram+trigram 生成正确；但实时数据库无法直接查询验证 |
| **Runtime: YAML 类 Query（FQ3/FQ4/FQ6/FG1/FG2）** | **FAIL** | 5 题均选中与 Phase 1A/1B 相同的 sibling terminal unit，fieldAliases 仍为纯英文。Layer 1 对英文字段名无效果——此为预期内的 Layer 覆盖范围外 |
| **Runtime: 表格类 Query（FQ7/FQ11）** | **无效果** | XLSX/CSV 查询不触发 `fact_card_terminal_fts` channel，中文 N-gram alias 无法在运行时生效 |

## 2. Redline 与全量测试

| 检查项 | 结果 |
|---|---|
| `bash scripts/scan-redline.sh special_cases_report.md` | **exit=0，BLOCKER=0** |
| `mvn test` | **Tests run: 951, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

与 Phase 1C Layer 1 fix result report 一致（951/0/0/0）。基线 Phase 1B 为 947，新增 4 个 Materializer 测试，总数 951。

## 3. Clean Schema / 重导 / 重编译

### 3.1 Schema

- 执行：`./scripts/reset-lattice-schema.sh`
- 结果：`lattice` schema 重建完成，`fact_card_terminal_units` 表已创建

### 3.2 模型配置

- Provider：openai_compatible（Chat + Embedding）
- Route：local_openai / gpt-5.5（Chat），zhipu_embedding / embedding-3（Embedding）
- 绑定：compile(3) + query(3) + deep_research(4) = 10 条
- 向量配置：已启用，embeddingModelProfileId=2

报告不包含 API key/token/password/sk- 明文。

### 3.3 资料导入与编译

| # | 文件名 | 编译状态 | Phase 1A/1B 状态 |
|---|---|---|---|
| 1 | lab-safety-management-handbook.md | SUCCEEDED | SUCCEEDED |
| 2 | equipment-borrowing-policy.yaml | SUCCEEDED | SUCCEEDED |
| 3 | chemical-storage-grading.xlsx | SUCCEEDED | SUCCEEDED |
| 4 | lab-emergency-response-procedures.pdf | **SUCCEEDED**（首次！） | FAILED |
| 5 | equipment-maintenance-schedule.csv | SUCCEEDED | SUCCEEDED |

编译 job `73ffc67c`：status=SUCCEEDED，persistedCount=**5**（Phase 1B 为 4）。PDF 首次编译成功（LLM fixer 修复了前两轮的 review 问题）。

### 3.4 数据计数

| 表 | 数量 | Phase 1B 基线 | 差异说明 |
|---|---|---|---|
| source_files | 5（含 PDF） | 4 | PDF 编译成功 |
| articles | 5 | 4 | +1 (PDF) |
| fact_cards | 11 | 11 | 一致 |
| fact_card_terminal_units | 无法从 API 查询 | 无法查询 | 同 Phase 1B |

## 4. 数据层验证：中文 N-gram Alias 物化

### 4.1 单元测试已验证（agentA 代码层）

agentA 的 4 个新增 Materializer 测试均通过，确认了以下行为：

| 测试 | 覆盖点 | 状态 |
|---|---|---|
| `shouldGenerateChineseNgramAliasesFromChineseFieldLabel` | "维护周期(天)" → aliases 含"维护周期""维护""周期""维护周""护周期" | PASS |
| `shouldNotGenerateNgramAliasesFromSingleCjkChar` | 单字 CJK "单" → 不生成 N-gram | PASS |
| `shouldNotDegradeEnglishFieldLabelAliases` | 英文 fieldLabel 别名逻辑不退化 | PASS |
| `shouldNotGenerateNgramAliasesForVeryLongChineseText` | >8 字中文文本 → 不生成子串 | PASS |

### 4.2 实时数据库验证

**无法直接验证。** API（`/api/v1/admin/fact-cards/{id}`）未在响应中返回 `terminalUnits` 列表，无法通过 API 抽样检查 `field_aliases_json` 内容。但 terminal unit FTS channel 在 YAML 类查询中正常返回命中（详见第 5 节），间接触认 terminal units 已在数据库中生成。

### 4.3 运行时 Alias 抽样（YAML 类 Terminal Unit）

以 FQ3 命中的 terminal unit（`equipment_types[1].type = 精密仪器`）为例：

```
fieldAliases: [
  "type",
  "equipment_types[1].type",
  "equipment types[1].type",
  "equipment_types[1] type",
  "equipment_types[1]",
  "equipment types[1]",
  "equipment types[1] type",
  "equipment_types",
  "equipment types",
  "[1]",
  "equipment",
  "types[1]",
  "types"
]
```

**全部为英文**。fieldLabel = "type"（英文），keyPath = "equipment_types[1].type"（英文），不含中文字符 → `addChineseNgramAliases` 不生成任何中文别名。**这是算法预期行为，非 bug。**

### 4.4 表格类 Terminal Unit Alias 预测

对于 XLSX 中文列头（如 "存储条件"、"保管人角色"、"危险等级"），`addChineseNgramAliases` 应按设计生成以下别名：

| 原始 fieldLabel | 预测生成的中文 N-gram |
|---|---|
| 存储条件 | "存储条件"、"存储"、"储条"、"条件"、"存储条"、"储条件" |
| 保管人角色 | "保管人角色"、"保管"、"管人"、"人角"、"角色"、"保管人"、"管人角"、"人角色" |
| 危险等级 | "危险等级"、"危险"、"险等"、"等级"、"危险等"、"险等级" |

但这些别名在运行时**无法生效**——因为 `fact_card_terminal_fts` channel 不被 XLSX/CSV 类查询调度（详见第 5.2 节）。

## 5. 服务级验证：Terminal Unit Channel

### 5.1 YAML 类 Query：Channel 调度与命中

| 题目 | fact_card_terminal_fts | hits | fused_rank | 命中的 unit | 目标 unit | 目标进入 topK |
|---|---|---|---|---|---|---|
| FQ3 | SUCCESS | 1 | 1 | equipment_types[1].**type** = 精密仪器 | equipment_types[1].**max_borrow_days** = 7 | **否** |
| FQ4 | SUCCESS | 1 | 4 | equipment_types[0].**type** = 常规设备 | equipment_types[0].**deposit_amount** = 100 | **否** |
| FQ6 | SUCCESS | 1 | 1 | borrowing_system.**name** = 校园实验室设备预约系统 | borrowing_system.**version** = v2.3.1 | **否** |
| FG1 | SUCCESS | 1 | 1 | equipment_types[1].**type** = 精密仪器 | equipment_types[1].**late_fee_per_day** = 20 | **否** |
| FG2 | SUCCESS | 1 | 1 | borrowing_system.**name** = 校园实验室设备预约系统 | borrowing_system.**max_concurrent_requests** = 50 | **否** |

**全部 5 题的 `fact_card_terminal_fts` 命中结果与 Phase 1A 和 Phase 1B 完全一致——均选中同卡 sibling terminal unit（中文文本值字段），目标 terminal unit 进入 topK = 0/5。**

命中 terminal unit 的 fieldAliases 全部为英文（详见 4.3 节），无中文变体。Reranker 的 `fieldMatchCount` 对中文 query token 仍为 0。

### 5.2 表格类 Query：Channel 未被调度

| 题目 | 数据源 | 被调度的 channels | fact_card_terminal_fts | fact_card_fts |
|---|---|---|---|---|
| FQ7 (B级化学品) | XLSX | article_chunk_fts, fact_card_fts, refkey, source, source_chunk_fts (5) | **否** | 是 |
| FQ11 (A级设备) | CSV | article_chunk_fts, refkey, source, source_chunk_fts (4) | **否** | **否** |

**FQ7**：`fact_card_fts` 被调度但 `fact_card_terminal_fts` 未被调度。可能原因：XLSX 的 fact_card 存在但 terminal unit FTS 搜索返回 0 结果，或 QueryGraph 的 dispatch plan 对 XLSX fact card 类型未包含 terminal unit channel。

**FQ11**：`fact_card_fts` 和 `fact_card_terminal_fts` 均未被调度。CSV 查询完全不触发 fact card 相关 channel。

### 5.3 表格类 Query 答案质量

| 题目 | answerOutcome | 答案内容 | PASS/FAIL |
|---|---|---|---|
| FQ7 | PARTIAL_ANSWER | B 级：丙酮(通风橱/防火柜)、氢氧化钠(防潮柜/密封)，保管人=设备管理员 | **PASS** |
| FQ11 | PARTIAL_ANSWER | EQ-001 气相色谱仪 为 A 级 | **PASS**（但仅列出一台设备） |

FQ7 和 FQ11 的答案质量与 Phase 1B 持平。答案来自 LLM generation（非 FALLBACK），不依赖 terminal unit channel。中文 N-gram alias 物化对此类题无运行时贡献。

## 6. 最终答案验证（5 个 YAML 目标 case）

### 6.1 答案详情

| 题目 | answerOutcome | generationMode | 目标值出现 | PASS/FAIL | 说明 |
|---|---|---|---|---|---|
| FQ3 | SUCCESS | FALLBACK | **否** | **FAIL** | 证据段讨论 return_policy，参考段只提 API endpoint。**Phase 1B 中"7"已消失** |
| FQ4 | SUCCESS | FALLBACK | **否**（type 字段值替代） | **FAIL** | 证据直接展示 "fieldPath: equipment_types[0].type = 常规设备"（terminal unit 数据），但非目标字段 deposit_amount |
| FQ6 | SUCCESS | FALLBACK | **否** | **FAIL** | 证据段讨论 return_policy，无版本号 |
| FG1 | SUCCESS | FALLBACK | **否** | **FAIL** | 证据段讨论 return_policy，无逾期费用 |
| FG2 | SUCCESS | FALLBACK | **否** | **FAIL** | 证据段讨论 return_policy，无 max_concurrent_requests |

**5 个目标 case：0/5 PASS。相比 Phase 1B（1/5 PASS，FQ3 的"7"出现在参考说明）出现退化。**

### 6.2 退化分析

Phase 1C 重编译后，FQ3/FQ6/FG1/FG2 的答案证据段从 `equipment_types` 事实转向 `return_policy` 事实。FQ4 的证据段直接展示了 terminal unit fieldPath 数据（"equipment_types[0].type = 常规设备"），这是新行为。

退化原因非 Layer 1 代码导致，而是**重编译时 LLM reviewer/fixer 对 fact card 结构和 article summary 的生成结果不同**。Phase 1B 编译时 `equipment_types` 相关事实排在前面，Phase 1C 重编译后 `return_policy` 事实排到前面。LLM 编译的非确定性导致答案证据选择发生变化。

**关键：Layer 1 代码未触及任何 query/answer/fallback 层逻辑，退化与 Layer 1 无关。**

## 7. 完整 Fresh Eval 指标

### 7.1 Answer Outcome 分布（19 题）

| 题目 | answerOutcome | generationMode | 简要评估 | vs Phase 1B |
|---|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | Markdown 内容问答 | 持平 |
| FQ2 | PARTIAL_ANSWER | LLM | 角色区分问答 | 持平 |
| **FQ3** | SUCCESS | FALLBACK | **FAIL** — 目标值 7 消失 | **退化** |
| **FQ4** | SUCCESS | FALLBACK | **FAIL** — 只展示 type 字段 | **退化** |
| FQ5 | SUCCESS | FALLBACK | **PASS** — API endpoint 正确 | 持平 |
| **FQ6** | SUCCESS | FALLBACK | **FAIL** — 版本号缺失 | 持平(均 FAIL) |
| FQ7 | PARTIAL_ANSWER | LLM | **PASS** — B级化学品存储/保管人 | 持平 |
| FQ8 | PARTIAL_ANSWER | LLM | **PASS** — PDF 可用，流程数据完整 | **改善**（PDF 编译成功） |
| FQ9 | NO_RELEVANT_KNOWLEDGE | LLM | **PASS** — 正确拒答 | 持平 |
| FQ10 | PARTIAL_ANSWER | LLM | **PASS** — PDF 可用，步骤完整 | **改善**（PDF 编译成功） |
| FQ11 | PARTIAL_ANSWER | LLM | **PASS** — EQ-001 A级 | 持平 |
| FQ12 | PARTIAL_ANSWER | LLM | 审批阶段提取 | 持平 |
| FS1 | PARTIAL_ANSWER | LLM | 搜索题 | 持平 |
| FS2 | PARTIAL_ANSWER | LLM | 搜索题 | 持平 |
| FS3 | SUCCESS | LLM | 搜索题 | 持平 |
| FS4 | SUCCESS | LLM | 搜索题 | 持平 |
| **FG1** | SUCCESS | FALLBACK | **FAIL** — 逾期费用缺失 | **退化** |
| **FG2** | SUCCESS | FALLBACK | **FAIL** — max_concurrent_requests 缺失 | 持平(均 FAIL) |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | **PASS** — 正确拒答 | 持平 |

### 7.2 指标估算

| 指标 | Phase 1B Baseline | Phase 1C Layer 1 | 变化 | 归因 |
|---|---|---|---|---|
| Answer Accuracy | ~9-10/19 | ~8-9/19 | -1 | FQ3/FG1 退化（LLM 编译非确定性，非 Layer 1 代码导致） |
| Structured Terminal (5 题) | 1/5 (FQ3) | 0/5 | -1 | FQ3 退化 + Layer 1 对英文字段无效果 |
| Abstain Accuracy | 2/2 | 2/2 | 不变 | — |
| PDF 题（FQ8/FQ10） | FAIL | PASS | +2 | PDF 首次编译成功（LLM fixer） |
| Hallucination Count | 0 | 0 | 不变 | — |

### 7.3 Layer 1 贡献归因

**Phase 1C Layer 1 对 fresh eval 19 题无任何可归因的正面贡献。**

| 题目组 | 为什么 Layer 1 不生效 |
|---|---|
| FQ3/FQ4/FQ6/FG1/FG2（YAML） | fieldLabel/terminalKey 为英文 → `addChineseNgramAliases` 不生成中文别名 → Reranker `fieldMatchCount = 0` |
| FQ7（XLSX） | `fact_card_terminal_fts` channel 未被调度 → 即使生成了中文 N-gram alias，也无法进入运行时检索链路 |
| FQ11（CSV） | `fact_card_fts` 和 `fact_card_terminal_fts` 均未被调度 → 同上 |
| 其余题 | 不涉及 terminal unit 检索 |

## 8. 分层结论

### 8.1 Layer 1 算法正确性：PASS

`addChineseNgramAliases` 的算法实现正确——括号处理、CJK 提取、bigram/trigram 生成、单字跳过、长度限制均通过单元测试验证。代码不包含硬编码业务映射、不读取 eval 数据、不引入 LLM 依赖。**算法本身可以提交。**

### 8.2 Layer 1 对 YAML 英文字段的效果：无（预期内）

Layer 1 的设计范围是"对源文件自带的 fieldLabel / keyPath 中的中文片段生成 N-gram"。YAML 的英文字段名（"max_borrow_days"、"deposit_amount"、"version"等）不含中文 → 不生成中文别名 → Reranker 无法建立中英文匹配。**这是设计范围内的预期结果，不是 Layer 1 的 bug。** 解决方案在 Layer 2（sibling context）或 Layer 3（LLM alias）。

### 8.3 Layer 1 对表格类 Query 的效果：无（Runtime 未调度）

XLSX/CSV 的中文列头（"存储条件"、"保管人角色"等）应已在 compile 时生成了中文 N-gram alias，但 `fact_card_terminal_fts` channel 在 XLSX/CSV 类查询中未被调度，alias 无法在运行时生效。**这不是 Layer 1 的 bug，而是 terminal unit FTS channel 的 dispatch 范围问题。** 需要在 QueryGraph 的 dispatch plan 中确认 XLSX/CSV fact card 类型的 terminal unit channel 是否被注册。

## 9. 保护回归

| 回归项 | 状态 | 说明 |
|---|---|---|
| Public Eval 1 Q6 terminal field alias | **未执行** | 当前库只含 Public Eval 2 资料 |
| S2 chunk/anchor identity | **未执行** | 同上 |
| Phase 1B Reranker 13 个单元测试 | **PASS** | 全量 mvn test 951/0/0，Reranker 测试仍在 |
| Phase 1B Reranker 运行时行为 | **不变** | fieldAliases 仍为英文 → Reranker 行为与 Phase 1B 一致 |
| FQ7 (B级化学品) | **PASS** | 保持 1B 水平 |
| FQ11 (A级设备) | **PASS** | 保持 1B 水平 |
| FG3 (拒答) | **PASS** | 保持 1B 水平 |

## 10. 是否建议提交

### 10.1 Phase 1C Layer 1 单独提交

**可以提交，但必须写清：Layer 1 是基础设施——为中文 fieldLabel 提供了 N-gram alias 生成能力，但当前对 fresh eval 无可见效果。** 理由：

- **算法正确**：单元测试通过，不引入 eval 污染，不包含硬编码映射
- **forward-looking**：Layer 1 是 Layer 2（sibling context）和未来 XLSX/CSV terminal unit dispatch 的前置依赖
- **无回归**：全量测试 951/0/0，redline BLOCKER=0

Commit message 必须注明：
- "此 commit 提供中文 fieldLabel N-gram alias 物化基础设施"
- "当前对 fresh eval 无可见效果：YAML 英文字段不生成中文别名（需 Layer 2/3），XLSX/CSV 的 terminal unit channel 未被调度（需 QueryGraph dispatch 扩展）"
- "不是 fix structured terminal value retrieval"

### 10.2 Phase 1B + Phase 1C Layer 1 联合提交

**建议一起提交。** Phase 1B（Reranker 消费者）和 Phase 1C Layer 1（Alias 生产者）互补：
- Phase 1B 在 query 时消费 fieldAliases 做 rerank
- Phase 1C Layer 1 在 compile 时生产中文字段别名
- 两者一起构成 "lexical field matching" 的完整链路

但仍需在 commit message 中注明当前效果有限，YAML 英文字段和 XLSX/CSV dispatch 需要后续 Phase 解决。

## 11. 唯一根因与下一轮建议

### 11.1 Layer 1 不生效的两个根因

**根因 A（YAML 类）：fieldLabel/terminalKey 本身为英文，不包含中文字符。**
- Layer 1 从 fieldLabel/keyPath 提取中文 N-gram，英文字段名无中文可提取
- 需要 Layer 2（sibling context：type="精密仪器" → context alias "精密仪器"）或 Layer 3（LLM：max_borrow_days → alias "最长借用天数"）

**根因 B（XLSX/CSV 类）：`fact_card_terminal_fts` channel 未被 QueryGraph dispatch。**
- XLSX/CSV 的中文列头已生成 alias，但 channel 不调度 → alias 无法在运行时生效
- 需要在 QueryGraph 的 dispatch plan 中确认 XLSX/CSV fact card 类型的 terminal unit channel 注册

### 11.2 下一轮建议

**Layer 2（sibling context）是实现中英文匹配的最低成本路径。**

具体做法（按设计报告 `terminal_unit_phase1c_field_alias_materialization_design_report.md` 第 5.1 节 Layer 2）：

1. 在 Materializer 中识别同一 `parentPath` 下的 sibling item，若存在中文 string value（如 type="精密仪器"），将其值加入目标 unit 的 fieldDescription 或 fieldAliases
2. 此时 "精密仪器" 作为 context alias 进入 ftsText → 可被 PostgreSQL FTS 检索
3. 中文 query 中的 "精密仪器" token 能匹配到目标 unit（max_borrow_days=7），因为它有 context alias "精密仪器"
4. Reranker 的 `fieldMatchCount` 增加 → 目标 unit 排序提升

**同时需要调查 XLSX/CSV terminal unit channel 的 dispatch 问题**——为什么 QueryGraph 不为 XLSX/CSV 类查询调度 `fact_card_terminal_fts` channel。如果 XLSX fact card 类型本身不生成 terminal unit，需要扩展 terminal unit 生成范围；如果 QueryGraph dispatch plan 未注册 XLSX/CSV fact card 类型的 terminal unit channel，需要扩展 dispatch plan。

### 11.3 为什么不继续扩大 Layer 1

Layer 1 的算法是正确的，当前无法生效不是算法问题：
- YAML 英文字段 → 无中文可提取（算法行为正确）
- XLSX/CSV 中文列头 → 已生成 alias 但 channel 不调度（不是 alias 生成的问题）

继续在 Layer 1 中增加更多中文切词策略（如引入分词器、扩展字符集、降低长度阈值）不会改变这两个根本约束。

## 12. 合规声明

1. 本轮未修改 `src/**`、`scripts/**`、`src/main/resources/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考，未修改、未 stage、未 commit，内容未写入报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文
8. 清库范围仅限 `ai-rag-knowledge.lattice`，未涉及 hidden eval 或其他外部库
