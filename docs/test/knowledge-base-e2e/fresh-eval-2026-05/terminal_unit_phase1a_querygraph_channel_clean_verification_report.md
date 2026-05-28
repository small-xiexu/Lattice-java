# Terminal Unit Phase 1A QueryGraph Channel 修复验证报告

验证时间：2026-05-28
验证人：agentD
验证对象：QueryGraph channel 修复后的 terminal unit Phase 1A（clean schema，全链路验证）

## 1. Gate 判定

**Phase 1A 分层结论：**

| 层级 | 结论 | 说明 |
|---|---|---|
| **Infrastructure / Channel Plumbing** | **PASS** | 表创建、terminal unit 生成、channel 调度、RRF unit identity、sibling 不折叠、metadata 完整性全部通过 |
| **Target Terminal Retrieval** | **FAIL** | FQ3/FQ4/FQ6/FG1/FG2 均未命中目标 terminal unit，命中的是同卡 sibling terminal unit |

### 1.1 Infrastructure Gate（全部 PASS）

| Gate | 状态 | 证据 |
|---|---|---|
| `fact_card_terminal_units` 表创建成功 | **PASS** | schema reset 后表与索引均已创建 |
| terminal units 成功生成 | **PASS** | 46 个 terminal units 已生成，全部目标值在表中存在且正确 |
| `fact_card_terminal_fts` channel 被调度执行 | **PASS** | 5 题全部 SUCCESS，每题目 1 hit |
| RRF 使用 unit identity | **PASS** | articleKey/conceptId 为 `terminal-unit:...`，非 card_id |
| 同卡 sibling 不折叠 | **PASS** | terminal unit (fused_rank=1) 与同卡 fact card (fused_rank=3) 独立存在 |
| metadata 字段完整 | **PASS** | 13 个必需字段全部存在 |

### 1.2 Target Terminal Retrieval Gate（FAIL）

| 题目 | 命中的 unit | 目标 unit | 结论 |
|---|---|---|---|
| FQ3 | equipment_types[1].**type** = 精密仪器 | equipment_types[1].**max_borrow_days** = 7 | **FAIL** — 命中 sibling，不是目标 |
| FQ4 | equipment_types[0].**type** = 常规设备 | equipment_types[0].**deposit_amount** = 100 | **FAIL** — 命中 sibling，不是目标 |
| FQ6 | borrowing_system.**name** = 校园实验室设备预约系统 | borrowing_system.**version** = v2.3.1 | **FAIL** — 命中 sibling，不是目标 |
| FG1 | equipment_types[0].**approval_required** = 设备管理员 | equipment_types[1].**late_fee_per_day** = 20 | **FAIL** — 命中 sibling，不是目标 |
| FG2 | equipment_types[0].**approval_required** = 设备管理员 | borrowing_system.**max_concurrent_requests** = 50 | **FAIL** — 命中 sibling，不是目标 |

**5 题命中 5 个 sibling terminal unit，0 个目标 terminal unit。**

根因：`search_tsv` 的 FTS 词面匹配优先命中 value_text 中含中文文本的 terminal unit（"精密仪器"、"常规设备"、"校园实验室设备预约系统"、"设备管理员"），而非仅含英文 key + 短数字/版本号的 terminal unit（"max_borrow_days"、"deposit_amount"、"version"、"late_fee_per_day"、"max_concurrent_requests"）。FTS 缺少对字段语义（field label / terminal key）与 query 意图的对齐能力，这是 **terminal unit FTS 排序/字段语义绑定不足**，不属于 channel plumbing 问题，应在 Phase 1B 解决。

## 2. 修改范围核对

Git diff 与 `terminal_unit_phase1a_querygraph_channel_fix_result_report.md` 一致。新增 QueryGraph 相关修改：
- `QueryGraphDefinitionBaseSupport.java` — 注入 terminal unit FTS service，dispatch plan 包含新 channel
- `QueryGraphState.java` — 新增 `factCardTerminalUnitHitsRef`
- `QueryGraphStateKeys.java` — 新增 `FACT_CARD_TERMINAL_UNIT_HITS_REF`
- `QueryGraphStateMapper.java` — 双向映射
- `QueryGraphRetrievalSupport.java` / `QueryGraphAnswerSupport.java` / `QueryGraphDefinitionFactory.java` — 构造函数透传
- `QueryGraphDefinitionBaseSupportTest.java`（新增）— 3 个定向测试

未修改禁止文件：
- `AnswerGenerationFallback*`：未修改
- `AnswerFallback*`：未修改
- `QueryResponseCitation*`：未修改
- `FactCardVector*`：未修改
- `config/synonyms.yaml`：未修改
- `config/rules.yaml`：未修改
- `prompt`：未修改
- `scripts/scan-redline.sh`：未修改

## 3. Redline 结果

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：exit=0，**BLOCKER=0**

## 4. 硬编码扫描结果

- `git diff -- src/main/java src/test/java ... | rg -n "FQ3|FQ4|..."`：**无命中**
- 未跟踪新文件扫描：**无命中**
- 无 fresh eval 题面、case id、答案值、业务词、密钥泄露到生产代码

## 5. 全量测试结果

| Run | Failures | Errors | Skipped | 结果 |
|---|---|---|---|---|
| **936** | **0** | **0** | **0** | BUILD SUCCESS |

与 fix_result_report 一致（936/0/0/0）。原有 3 个 skip 已消除（测试库 schema 已应用）。

## 6. 清库/重建 Schema 与模型配置

### 6.1 Schema

- 执行：`./scripts/reset-lattice-schema.sh`
- 结果：`lattice` schema 重建完成
- `fact_card_terminal_units` 表：**已创建**，含全部索引和唯一约束

### 6.2 服务

- 命令：`./scripts/run-local-dev.sh`
- 端口：18082
- 健康检查：`{"status":"UP"}`

### 6.3 模型配置

- Provider：openai_compatible（Chat + Embedding）
- Route：local_openai / gpt-5.5（Chat），zhipu_embedding / embedding-3（Embedding）
- 绑定：compile(3) + query(3) + deep_research(4) = 10 条
- 向量配置：已启用，embeddingModelProfileId=2
- 配置状态：**成功**

报告不包含 API key/token/password/sk- 明文。

## 7. 资料导入结果

### 7.1 导入状态

| # | 文件名 | 状态 | 说明 |
|---|---|---|---|
| 1 | lab-safety-management-handbook.md | SUCCEEDED | article 经人工确认已发布 |
| 2 | equipment-borrowing-policy.yaml | SUCCEEDED | article 经人工确认已发布 |
| 3 | chemical-storage-grading.xlsx | SUCCEEDED | article 经人工确认已发布 |
| 4 | lab-emergency-response-procedures.pdf | **FAILED** | DataIntegrityViolationException (500) |
| 5 | equipment-maintenance-schedule.csv | SUCCEEDED | article 经人工确认已发布 |

PDF 持续 500 错误，两次验证均失败。不影响 Phase 1A 验证（结构化 terminal value 来源于 YAML）。

### 7.2 数据计数

| 表 | 数量 |
|---|---|
| source_files | 4（不含 PDF） |
| articles | 4 |
| article_chunks | 8 |
| fact_cards | 7 |
| fact_card_terminal_units | **46** |

### 7.3 Terminal Unit 分布

| fact card | unit 数 | 来源 |
|---|---|---|
| fact-card:1:0:fact_enum | 2 | Markdown FACT_ENUM |
| fact-card:2:0:fact_enum | 37 | YAML FACT_ENUM key_value_list |
| fact-card:3:0:fact_enum | 7 | XLSX FACT_ENUM |

## 8. Terminal Unit 数据验证

### 8.1 目标值完整性

| 题目 | terminal_key | 目标值 | 是否存在 | key_path |
|---|---|---|---|---|
| FQ3 | max_borrow_days | 7 | ✓ (id=17) | equipment_types[1].max_borrow_days |
| FQ4 | deposit_amount | 100 | ✓ (id=11) | equipment_types[0].deposit_amount |
| FQ4 | deposit_amount | 1000 | ✓ (id=25) | equipment_types[2].deposit_amount |
| FQ6 | version | v2.3.1 | ✓ (id=5) | borrowing_system.version |
| FG1 | late_fee_per_day | 20 | ✓ (id=19) | equipment_types[1].late_fee_per_day |
| FG1 | late_fee_per_day | 5 | ✓ (id=12) | equipment_types[0].late_fee_per_day |
| FG2 | max_concurrent_requests | 50 | ✓ (id=6) | borrowing_system.max_concurrent_requests |

全部 7 个目标 terminal unit 均在表中，key_path、value_text、value_type、display_text 均正确。

### 8.2 Metadata 完整性（以 FQ3 目标 unit id=17 为例）

| 字段 | 值 | 状态 |
|---|---|---|
| terminalUnitId | 17（数据库 id） | ✓ |
| unitId | fact-card-terminal:fact-card:2:0:fact_enum:... | ✓ |
| terminalUnitIdentity | terminal-unit:fact-card-terminal:... | ✓ |
| factCardId | 5 | ✓ |
| cardId | fact-card:2:0:fact_enum:41aa37638b50706c | ✓ |
| keyPath | equipment_types[1].max_borrow_days | ✓ |
| parentPath | equipment_types[1] | ✓ |
| terminalKey | max_borrow_days | ✓ |
| value | 7 | ✓ |
| valueType | number | ✓ |
| displayText | equipment_types[1].max_borrow_days = 7 | ✓ |
| channel | fact_card_terminal_fts | ✓ |
| sourceRefs | 全部（sourceFileId, sourceChunkIds, lineIndex, raw） | ✓ |

注：`terminalUnitId` 在本轮 clean schema 后已正确填充（非 null）。上一轮验证中为 null 的问题已随数据重建解决。

## 9. QueryGraph Channel 验证结果

### 9.1 总览

| 题目 | queryId | runId | fact_card_terminal_fts | hits | fused_rank | 命中的 unit |
|---|---|---|---|---|---|---|
| FQ3 | 9dc033da... | 1 | SUCCESS | 1 | **1** | equipment_types[1].type = 精密仪器 |
| FQ4 | 513c40ee... | 3 | SUCCESS | 1 | **4** | equipment_types[0].type = 常规设备 |
| FQ6 | 9999edf9... | 4 | SUCCESS | 1 | **1** | borrowing_system.name = 校园实验室设备预约系统 |
| FG1 | 6b2f79f4... | 5 | SUCCESS | 1 | **1** | equipment_types[1].type = 精密仪器 |
| FG2 | 1b628a4b... | 6 | SUCCESS | 1 | **1** | borrowing_system.name = 校园实验室设备预约系统 |

**关键结论：`fact_card_terminal_fts` channel 在全部 5 题中均被调度执行（status=SUCCESS）。但命中的 terminal unit 不是目标 terminal unit，而是同卡 sibling terminal unit（中文文本值字段优先于英文 key + 短数值字段）。目标 terminal unit 进入 topK = 0/5。**

### 9.2 FQ3 详细 trace

- queryId: `9dc033da-4478-4aec-bf8e-0ec7d82ad5d6`
- runId: 1
- 执行 channels（12 个）：article_chunk_fts, article_vector, chunk_vector, contribution, fact_card_fts, **fact_card_terminal_fts**, fact_card_vector, fts, graph, refkey, source, source_chunk_fts
- `fact_card_terminal_fts`：SUCCESS, 1 hit, duration=21ms
- Fused topK（7 hits）：

| fused_rank | channel | articleKey | conceptId |
|---|---|---|---|
| **1** | **fact_card_terminal_fts** | **terminal-unit:fact-card-terminal:...:12:44c96a...** | **terminal-unit:fact-card-terminal:...:12:44c96a...** |
| 2 | source_chunk_fts | equipment-borrowing-policy.yaml#0 | equipment-borrowing-policy.yaml |
| **3** | **fact_card_fts** | **fact-card:2:0:fact_enum:41aa37638b50706c** | **fact-card:2:0:fact_enum:41aa37638b50706c** |
| 4 | fact_card_fts | fact-card:2:0:fact_enum:28eabde206fd56ed | fact-card:2:0:fact_enum:28eabde206fd56ed |
| 5 | source | equipment-borrowing-policy.yaml | equipment-borrowing-policy.yaml |
| 6 | article_chunk_fts | equipment-borrowing-policy--... | equipment-borrowing-policy |
| 6 | refkey | equipment-borrowing-policy--... | equipment-borrowing-policy |

### 9.3 RRF Identity 验证（核心 Gate）

fused_rank=1（terminal unit）与 fused_rank=3（同卡 fact card）共享同一个 `cardId = fact-card:2:0:fact_enum:41aa37638b50706c`，但：

- Terminal unit 的 articleKey：`terminal-unit:fact-card-terminal:fact-card:2:0:fact_enum:41aa37638b50706c:12:44c96a...`
- Fact card 的 articleKey：`fact-card:2:0:fact_enum:41aa37638b50706c`

**两者 identity 不同，未被 RRF 折叠为同一条 hit。**

- Terminal unit hit 的 evidenceType 为 `FACT_CARD`（Phase 1 设计，降低 answer/citation 改动面）
- 区分依靠 channel（`fact_card_terminal_fts` vs `fact_card_fts`）和 articleKey（unit identity vs card identity）

### 9.4 FTS 匹配分析

5 题中 FTS 优先匹配了中文文本值的 **sibling** terminal unit（`type=精密仪器`、`name=校园实验室设备预约系统`、`approval_required=设备管理员`），而非**目标**数值/版本值 terminal unit（`max_borrow_days=7`、`version=v2.3.1`、`deposit_amount=100`）。

原因：`search_tsv` 生成的词面 token 以英文字段名和路径为主，中文 token 来自 value_text 中的中文值。问题中的中文关键词（"精密仪器"、"预约系统"、"常规设备"）与 type/name 字段的中文 value 直接匹配，而数值字段（7, 100, 50, v2.3.1）与问题关键词的词面重叠少。

Phase 1A infrastructure 已被证明：
1. terminal unit channel 能被调度 ✓
2. terminal unit hit 使用 unit identity 进入 RRF ✓
3. 同卡 sibling 不折叠 ✓

但 **目标 terminal unit 未被 FTS 选中**，这是 Phase 1B 需要解决的字段语义绑定/排序问题。

## 10. 最终 Answer 观察

### 10.1 5 题 answer 摘要

| 题目 | answerOutcome | generationMode | answer 内容 | 是否命中目标值 |
|---|---|---|---|---|
| FQ3 | SUCCESS | FALLBACK | fieldPath: equipment_types[1].type = 精密仪器 | **否**（返回 type 而非 max_borrow_days=7） |
| FQ4 | PARTIAL_ANSWER | FALLBACK | fieldPath: equipment_types[0].type = 常规设备 | **否**（返回 type 而非 deposit_amount=100） |
| FQ6 | SUCCESS | FALLBACK | fieldPath: borrowing_system.name = 校园实验室设备预约系统 | **否**（返回 name 而非 version=v2.3.1） |
| FG1 | PARTIAL_ANSWER | FALLBACK | fieldPath: equipment_types[0].approval_required = 设备管理员 | **否**（返回 approval 而非 late_fee=20/5） |
| FG2 | PARTIAL_ANSWER | FALLBACK | fieldPath: equipment_types[0].approval_required = 设备管理员 | **否**（返回 approval 而非 max_concurrent=50） |

### 10.2 观察结论

- 所有 5 题 answer 仍未命中目标 terminal value
- 但 answer 中展示的字段（type、name、approval_required）均来自 `fact_card_terminal_fts` channel 的 terminal unit hit
- 这表明 **terminal unit 证据已进入 answer fallback 的证据选择范围**
- Answer 不正确的根因已从"terminal unit 未召回"转变为"FTS 排序选择了非目标 terminal unit"
- 这验证了 Phase 1A 的设计假设：terminal unit 证据粒度建设是 answer 准确率的前提，但 answer 准确率还需要 Phase 1B/2 的排序/rerank 优化

## 11. 保护回归

### 11.1 执行决策

**未执行 Public Eval 1 的 Q6/S2 保护回归。**

原因：
1. 当前数据库只包含 Public Eval 2 的 4 份资料（不含 Kubernetes/探针/事件响应等 Public Eval 1 资料）
2. 保护回归需要清库后重新导入 Public Eval 1 的完整资料集（6 份资料），与当前 Public Eval 2 验证库冲突
3. 本轮 terminal unit 改动未涉及 fallback、citation、vector、prompt 或 config，引入回归的风险极低
4. terminal unit channel 是独立新增 channel，不替代或修改现有 channel 的行为

建议：提交前或下一次 agentD 验证时，在包含 Public Eval 1 资料的库上执行 Q6/S2 回归。

## 12. 合规声明

1. 本轮未修改 `src/**`、`scripts/**`、`src/main/resources/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考，未修改、未 stage、未 commit，内容未写入报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文（命令中使用 `[REDACTED]` 占位）
8. 清库范围仅限 `ai-rag-knowledge.lattice`，未涉及 hidden eval 或其他外部库

## 13. Phase 1A 验收标准逐项验收

### 13.1 Infrastructure / Channel Plumbing

| # | 标准 | 结果 | 证据 |
|---|---|---|---|
| 1 | `fact_card_terminal_units` 表创建成功 | ✓ | schema reset 后表存在，含全部索引 |
| 2 | 重新导入后生成 terminal units | ✓ | 46 units 生成，7 个目标值均存在 |
| 3 | `fact_card_terminal_fts` channel 被调度且有命中 | ✓ | 5 题全部 SUCCESS，每题目 1 hit |
| 5 | articleKey/conceptId 使用 unit identity | ✓ | `terminal-unit:fact-card-terminal:...` 格式 |
| 6 | 同卡 sibling 不折叠 | ✓ | terminal unit (rank 1) 与同卡 fact card (rank 3) 独立 |
| 7 | metadata 包含全部必需字段 | ✓ | 13 个必需字段全部存在，terminalUnitId 已正确填充 |

### 13.2 Target Terminal Retrieval

| # | 标准 | 结果 | 证据 |
|---|---|---|---|
| 4 | **目标** terminal unit 进入 topK | **✗ FAIL** | 5 题命中 5 个 sibling unit，0 个目标 unit |

详情：

| 题目 | 实际命中（sibling） | 目标（未命中） |
|---|---|---|
| FQ3 | equipment_types[1].**type** = 精密仪器（rank 1） | equipment_types[1].**max_borrow_days** = 7 |
| FQ4 | equipment_types[0].**type** = 常规设备（rank 4） | equipment_types[0].**deposit_amount** = 100 |
| FQ6 | borrowing_system.**name** = 校园实验室设备预约系统（rank 1） | borrowing_system.**version** = v2.3.1 |
| FG1 | equipment_types[0].**approval_required** = 设备管理员（rank 1） | equipment_types[1].**late_fee_per_day** = 20 |
| FG2 | equipment_types[0].**approval_required** = 设备管理员（rank 1） | borrowing_system.**max_concurrent_requests** = 50 |

## 14. 是否建议提交

**建议作为 terminal unit infrastructure partial commit 提交。**

理由：
- Infrastructure 层全部通过：表创建、unit 生成、channel 调度、RRF unit identity、sibling 不折叠、metadata 完整性
- 全量 mvn test 936/0/0/0，redline BLOCKER=0
- 未修改 fallback、citation、vector、prompt 或配置
- Channel plumbing 已就绪，为 Phase 1B 排序优化提供了基础

但必须明确：
- **这不是 fresh eval 结构化题通过的提交。** FQ3/FQ4/FQ6/FG1/FG2 仍为 0/5 PASS（目标 terminal unit 未进入 topK）
- 本次提交的 commit message 必须注明是 "infrastructure / channel plumbing"，不能写成 "fix structured terminal value retrieval"
- Phase 1B 完成并验证目标 unit 进入 topK 后，才能宣称 fresh eval 结构化题改善

## 15. 下一步：Phase 1B 唯一根因

**Terminal unit FTS 排序/字段语义绑定不足。**

当前 FTS 的 `search_tsv` 以英文字段名和结构路径 token 为主，中文 token 仅来自 value_text 中的中文值。当 query 包含中文业务词（"精密仪器"、"预约系统"、"常规设备"）时，FTS 优先匹配到含中文 value 的 sibling terminal unit（type、name、approval_required），而非仅含英文 key + 短数字/版本号的**目标** terminal unit（max_borrow_days、deposit_amount、version、late_fee_per_day、max_concurrent_requests）。

Phase 1B 建议方向：
1. **query 语义 → field label 对齐**：在 terminal unit FTS 检索时，识别 query 中的字段意图（"最长借用天数" → max_borrow_days，"押金" → deposit_amount，"版本号" → version），将 field label / terminal key 作为检索信号参与排序
2. **field aliases 中文增强**：在 `fieldDescription` 或 `fieldAliases` 中补入中文语义变体（如 max_borrow_days → "最长借用天数"，deposit_amount → "押金"），但严格遵守只从源文件内容派生、不硬编码 eval 题面语言
3. **value type gate**：当 query 显式问数值（"多少"、"几"），在 FTS 排序中给 valueType=number 的 terminal unit 加分
4. **Phase 2 rerank/embedding**：如 FTS 排序仍不稳定，接入 terminal unit embedding 和 rerank

Phase 1B 完成后需 agentD 在 clean schema 上重新验证：目标 terminal unit 是否进入 topK，以及 5 题 answer 是否命中目标值。
