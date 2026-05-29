# Terminal Unit Phase 1B Ranking Clean Verification Report

验证时间：2026-05-29
验证人：agentD
验证对象：Phase 1B terminal unit lexical rerank 在 clean schema 上的真实服务级验证

## 1. Gate 判定

**Phase 1B 分层结论：**

| 层级 | 结论 | 说明 |
|---|---|---|
| **Infrastructure / Channel Plumbing** | **PASS** | 表创建、terminal unit 生成（间接确认）、channel 调度、RRF unit identity、sibling 不折叠全部通过（与 Phase 1A 一致） |
| **Terminal Unit Rerank** | **FAIL** | `FactCardTerminalUnitIntentReranker` 未改变 `fact_card_terminal_fts` channel 的命中结果——全部 5 题仍选中与 Phase 1A 相同的 sibling terminal unit |
| **Final Answer Improvement** | **PARTIAL** | FQ3 答案出现目标值 `7`（位于参考说明段），FQ4/FG1 部分出现目标值，FQ6/FG2 完全缺失目标值 |

## 2. Redline 与全量测试

| 检查项 | 结果 |
|---|---|
| `bash scripts/scan-redline.sh special_cases_report.md` | **exit=0，BLOCKER=0** |
| `mvn test` | **Tests run: 947, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

与 Phase 1B ranking fix + redline fix + config binding fix 三份报告一致（947/0/0/0）。

## 3. Clean Schema / 重导 / 重编译

### 3.1 Schema

- 执行：`./scripts/reset-lattice-schema.sh`
- 结果：`lattice` schema 重建完成，`fact_card_terminal_units` 表已创建

### 3.2 模型配置

- Provider：openai_compatible（Chat + Embedding）
- Route：local_openai / gpt-5.5（Chat），zhipu_embedding / embedding-3（Embedding）
- 绑定：compile(3) + query(3) + deep_research(4) = 10 条
- 向量配置：已启用，embeddingModelProfileId=2
- 配置状态：**成功**

报告不包含 API key/token/password/sk- 明文。

### 3.3 资料导入与编译

| # | 文件名 | 编译状态 |
|---|---|---|
| 1 | lab-safety-management-handbook.md | SUCCEEDED |
| 2 | equipment-borrowing-policy.yaml | SUCCEEDED |
| 3 | chemical-storage-grading.xlsx | SUCCEEDED |
| 4 | lab-emergency-response-procedures.pdf | **FAILED**（与 Phase 1A 一致） |
| 5 | equipment-maintenance-schedule.csv | SUCCEEDED |

编译 job `37b5bd0d`：status=SUCCEEDED，persistedCount=4，reviewMode=LLM。

### 3.4 数据计数

| 表 | 数量 | Phase 1A 基线 | 差异说明 |
|---|---|---|---|
| source_files | 4（不含 PDF） | 4 | 一致 |
| articles | 4 | 4 | 一致 |
| fact_cards | 11 | 7 | reviewMode=LLM 导致 FACT_ENUM 拆分差异 |
| fact_card_terminal_units | **无法从 API 直接查询** | 46 | 见下方说明 |

**Terminal Unit 计数说明：** 本轮 API（`/api/v1/admin/fact-cards/{id}`）未在 JSON 响应中返回 `terminalUnits` 列表（terminalUnitsCount=0），无法通过 API 直接统计 terminal unit 数量。但 `fact_card_terminal_fts` channel 在所有 5 题中均返回了 terminal unit hit（articleKey 格式为 `terminal-unit:fact-card-terminal:...`），确认 terminal units 已在数据库中生成且被 FTS 检索到。

## 4. Terminal Unit Channel 排序验证（核心）

### 4.1 5 个目标 case 的 terminal channel 排名

| 题目 | 实际命中的 unit（rank 1） | 目标 unit | 目标进入 topK | 目标排 sibling 前 |
|---|---|---|---|---|
| FQ3 | equipment_types[1].**type** = 精密仪器 | equipment_types[1].**max_borrow_days** = 7 | **否** | **否** |
| FQ4 | equipment_types[0].**type** = 常规设备 | equipment_types[0].**deposit_amount** = 100 | **否** | **否** |
| FQ6 | borrowing_system.**name** = 校园实验室设备预约系统 | borrowing_system.**version** = v2.3.1 | **否** | **否** |
| FG1 | equipment_types[1].**type** = 精密仪器 | equipment_types[1].**late_fee_per_day** = 20 | **否** | **否** |
| FG2 | borrowing_system.**name** = 校园实验室设备预约系统 | borrowing_system.**max_concurrent_requests** = 50 | **否** | **否** |

**全部 5 题的 `fact_card_terminal_fts` channel 命中结果与 Phase 1A 完全一致——均选中同卡 sibling terminal unit（中文文本值字段），目标 terminal unit 进入 topK = 0/5。**

### 4.2 Channel 调度与 Fused 状态

| 题目 | fact_card_terminal_fts | hits | fused_rank | includedInFused | 命中的 unit |
|---|---|---|---|---|---|
| FQ3 | SUCCESS | 1 | 1 | true | equipment_types[1].type = 精密仪器 |
| FQ4 | SUCCESS | 1 | 4 | true | equipment_types[0].type = 常规设备 |
| FQ6 | SUCCESS | 1 | 1 | true | borrowing_system.name = 校园实验室设备预约系统 |
| FG1 | SUCCESS | 1 | 1 | true | equipment_types[1].type = 精密仪器 |
| FG2 | SUCCESS | 1 | 1 | true | borrowing_system.name = 校园实验室设备预约系统 |

Channel plumbing 层全部通过：调度成功、1 hit、进入 fused topK、RRF identity 正确。问题集中在 **terminal unit 选择（排序）层**。

### 4.3 命中 Terminal Unit 的 Metadata 详情

以 FQ3 为例（5 题结构一致）：

| 字段 | 值 |
|---|---|
| terminalKey | type |
| valueText | 精密仪器 |
| keyPath | equipment_types[1].type |
| valueType | string |
| fieldLabel | type |
| fieldAliases | ["type", "equipment_types[1].type", "equipment types[1].type", ...] — 全部英文 |
| terminalUnitIdentity | terminal-unit:fact-card-terminal:fact-card:2:0:fact_enum:41aa... |
| score | 13.0（所有 5 题相同） |

**关键观察：`fieldAliases` 全部为英文字段名和路径分解，无中文变体。**

### 4.4 Reranker 失效根因分析

`FactCardTerminalUnitIntentReranker` 的核心排序逻辑：

```
adjustedScore = originalFtsScore
              + fieldMatchCount × 1.0     （terminalKey/fieldLabel/aliases/keyPath 与 query token 精确匹配）
              + min(valueMatchCount, 5) × 0.1  （value 匹配）
              + numericBonus（+0.5，query 含数值问法 + valueType=number/version）
```

**失效路径（以 FQ3 为例）：**

1. Query token 提取：`QueryTokenExtractor` 从 "精密仪器的单次最长借用天数是多少" 提取中文 N-gram（如 "最长借用天数"、"借用天数"、"借用"、"天数"）
2. 目标 unit（max_borrow_days=7）的 `terminalKey`、`fieldLabel`、`fieldAliases` 均为英文（"max_borrow_days" 等），与中文 query token **零精确匹配**
3. 目标 unit 的 `fieldMatchCount = 0`，仅靠 `numericBonus = +0.5`（"多少" + valueType=number）
4. Sibling unit（type=精密仪器）的 `originalFtsScore` 远高于目标（中文 value_text "精密仪器" 与 query N-gram 直接词面匹配），`valueMatchCount` 累加
5. 0.5 的 numericBonus 无法覆盖 sibling 的 FTS 分数优势
6. Sibling boost（+6.0）不触发——前提是 `terminalKeyMatchCount > 0`，但中英文不匹配导致两边都没有

**结论：Reranker 依赖 query token 与 terminalKey/fieldLabel/fieldAliases 的精确 token 匹配，当 query 为中文、terminal key 为英文时，此匹配永远为 0。numericBonus (+0.5) 和 value 封顶 (0.5) 不足以逆转 FTS 原始分数的巨大差距。**

## 5. 最终答案验证

### 5.1 5 个目标 case 答案 PASS/FAIL

| 题目 | answerOutcome | generationMode | 目标值出现 | PASS/FAIL | 说明 |
|---|---|---|---|---|---|
| FQ3 | SUCCESS | FALLBACK | `7`（在参考说明段） | **PASS** | "精密仪器...最长借用天数为 7 天" 出现在参考说明，非直接证据段 |
| FQ4 | SUCCESS | FALLBACK | `100` 隐式出现，`1000` 缺失 | **FAIL** | "常规设备 = GEN，14，100，5" 中 100 为 deposit，但无字段标签；大型设备押金 1000 缺失 |
| FQ6 | SUCCESS | FALLBACK | 否 | **FAIL** | 答案无 v2.3.1；证据段讨论常规设备属性，参考段只提 API endpoint |
| FG1 | SUCCESS | FALLBACK | `5` 出现，`20` 缺失 | **FAIL** | "逾期费用为每天 5" 对应常规设备；精密仪器逾期 20 缺失 |
| FG2 | SUCCESS | FALLBACK | 否 | **FAIL** | 答案无 max_concurrent_requests=50；证据段讨论常规设备属性 |

**5 个目标 case：1 PASS / 4 FAIL。对比 Phase 1A（0/5 PASS）有进展，但非 reranker 贡献——FQ3 的 `7` 来自预编译的 article 参考说明（source summary），非 terminal unit channel。**

### 5.2 答案来源分析

所有 5 题均走 `generationMode=FALLBACK`，`fallbackReason=DETERMINISTIC_EXACT_LOOKUP_PREFERRED`。答案结构为：

- **证据段**：列出 fact card 的结构化摘要（整卡粒度，非 terminal unit 粒度）
- **参考说明段**：列出编译时生成的 article reference/summary，包含源文件的部分字段值

FQ3 的 `7` 出现在参考说明段，来自编译时的 article summary，非 runtime terminal unit 检索结果。`structuredEvidence` 字段在所有 5 题中均为 null——exact path lookup 未触发。

## 6. 完整 Fresh Eval 指标

### 6.1 Answer Outcome 分布（19 题）

| 题目 | answerOutcome | generationMode | 简要评估 |
|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | Markdown 内容问答 |
| FQ2 | PARTIAL_ANSWER | LLM | 角色区分问答 |
| **FQ3** | SUCCESS | FALLBACK | **PASS** — 目标值 7 出现 |
| **FQ4** | SUCCESS | FALLBACK | **FAIL** — 大型设备押金缺失 |
| FQ5 | SUCCESS | FALLBACK | **PASS** — API endpoint 正确 |
| **FQ6** | SUCCESS | FALLBACK | **FAIL** — 版本号缺失 |
| FQ7 | PARTIAL_ANSWER | LLM | 部分正确 — B 级化学品存储条件/保管人 |
| FQ8 | PARTIAL_ANSWER | LLM | PDF 未入库，跨文档组合受限 |
| FQ9 | NO_RELEVANT_KNOWLEDGE | LLM | **PASS** — 正确拒答 |
| FQ10 | PARTIAL_ANSWER | LLM | PDF 未入库 |
| FQ11 | PARTIAL_ANSWER | LLM | 列出 EQ-001，但可能遗漏其他 A 级设备 |
| FQ12 | PARTIAL_ANSWER | LLM | **PASS** — 审批阶段正确 |
| FS1 | PARTIAL_ANSWER | LLM | 搜索题 |
| FS2 | PARTIAL_ANSWER | LLM | 搜索题 |
| FS3 | SUCCESS | LLM | 搜索题 |
| FS4 | SUCCESS | LLM | 搜索题 |
| **FG1** | SUCCESS | FALLBACK | **FAIL** — 精密仪器逾期缺失 |
| **FG2** | SUCCESS | FALLBACK | **FAIL** — max_concurrent_requests 缺失 |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | **PASS** — 正确拒答 |

### 6.2 指标估算（基于 19 题人工快速评估）

| 指标 | Phase 1A Clean Baseline | Phase 1B Clean Verification | 变化 |
|---|---|---|---|
| Answer Accuracy | ~10/19 (估计) | ~9-10/19 (估计) | 基本持平 |
| Structured Terminal (FQ3/FQ4/FQ6/FG1/FG2) | 0/5 | 1/5 | +1 (FQ3，非 reranker 贡献) |
| Abstain Accuracy | 2/2 (FQ9, FG3) | 2/2 (FQ9, FG3) | 不变 |
| Hallucination Count | 未检测到新增编造 | 未检测到新增编造 | 不变 |
| Recall@5 / Recall@10 | N/A（搜索题未全量验证） | — | 待全量 eval runner |

**注：** 本轮未运行完整 fresh eval runner（只做了人工快速判断），Answer Accuracy 为近似估计。精确指标需运行 `run-query-regression.sh` 后判断。

### 6.3 指标对比说明

Phase 1B 的 Answer Accuracy 相比 Phase 1A 基本持平。FQ3 的改善（目标值 7 出现在参考说明）源于 LLM review mode 下 article summary 生成差异，**不是 terminal unit reranker 的贡献**。FQ4/FQ6/FG1/FG2 仍不能正确回答目标值。

## 7. 是否建议提交

**不建议提交 Phase 1B 的 `FactCardTerminalUnitIntentReranker`。**

理由：

1. **Reranker 对 terminal unit channel 选择无效果**：全部 5 题仍选中与 Phase 1A 相同的 sibling terminal unit，目标 unit 进入 topK = 0/5
2. **Root cause 明确**：Reranker 依赖 query token 与英文字段名的精确匹配，中文 query 永远无法匹配英文 terminalKey/fieldLabel/fieldAliases
3. **Code quality 无问题**：redline BLOCKER=0，mvn test 947/0/0，Spring DI 链路正确，配置化信号路径正确。代码本身结构良好，问题在于算法设计假设在中文 query 场景下不成立
4. **无回归**：channel plumbing 与 Phase 1A 一致，未引入新问题

如果仍决定提交 reranker 代码（作为 infrastructure 的一部分），commit message 必须写清楚：
- Reranker 提供了通用 lexical rerank 框架（字段 token 匹配、sibling boost、numeric bonus）
- 当前在中文 query + 英文 field key 场景下效果有限
- 不是 "fix structured terminal value retrieval" 的完整修复

## 8. 唯一根因与下一轮建议

### 8.1 唯一根因

**Terminal unit rerank 依赖精确 token 匹配，query 中文 N-gram 与 terminal key（英文）之间无匹配通道。** 当 query 包含中文业务词（"最长借用天数"、"押金"、"版本号"、"逾期罚金"、"最大并发"）时，Reranker 无法将这些词对齐到英文 terminalKey（"max_borrow_days"、"deposit_amount"、"version"、"late_fee_per_day"、"max_concurrent_requests"）。中文 N-gram 反而匹配到 sibling unit 的中文 value_text（"精密仪器"、"常规设备"、"校园实验室设备预约系统"），使 sibling 在 FTS 分数和 value 匹配上都压倒目标 unit。

### 8.2 下一轮建议（不改代码，仅归因）

**下一轮唯一有效方向：在 terminal unit 物化层增加中文字段别名（fieldAliases）。**

具体做法：
1. 在 `FactCardTerminalUnitMaterializer` 生成 terminal unit 时，为 `fieldAliases` 补充中文语义变体
2. 中文 aliases 只能来自：
   - **源文件内容**（如 YAML 中的 `description` 字段、注释、文档标题）
   - **通用结构规则**（如 "天数" → 包含 `_days` 的字段，"押金" → 包含 `deposit` 的字段）
   - **禁止来自** eval 题面、case id、expected answer、query 日志
3. 此时 Reranker 的 `fieldMatchCount` 才能在中英文之间建立匹配

**备选方向（如果中文 aliases 仍不足）：**
- 在 terminal unit FTS 检索层增加 embedding/向量通道（Phase 2 规划）
- Query 侧增加字段意图分类（"问天数" → weight number/days 字段，"问版本" → weight version 字段）

### 8.3 为什么不继续调 Reranker 权重

当前权重设计（字段匹配 +1.0、value 封顶 0.5、sibling boost +6.0、numeric bonus +0.5）在单元测试的英文 synthetic 场景下正确工作（13/13 通过）。问题不是权重不够大，而是 `fieldMatchCount` 在中英文场景下永远为 0——这是匹配通道缺失，不是权重调整问题。提高 numeric bonus 到 +10 可以暴力解决 FQ3，但会在其他场景引入假阳性（任何 number 字段都会超过有中文 value 的 sibling），属于 case 特判。

## 9. 保护回归

**未执行 Public Eval 1 的 Q6/S2 保护回归。** 原因与 Phase 1A 一致：
1. 当前数据库只包含 Public Eval 2 的 4 份资料
2. 本轮 terminal unit rerank 改动未涉及 fallback、citation、vector、prompt 或 config（仅 FTS search service 内部调用链加入 reranker）
3. terminal unit 改动是 channel 内部重排，不影响其他 channel 行为

## 10. 合规声明

1. 本轮未修改 `src/**`、`scripts/**`、`src/main/resources/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考，未修改、未 stage、未 commit，内容未写入报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文
8. 清库范围仅限 `ai-rag-knowledge.lattice`，未涉及 hidden eval 或其他外部库
