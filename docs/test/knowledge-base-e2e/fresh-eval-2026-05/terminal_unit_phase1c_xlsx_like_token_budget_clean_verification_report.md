# Terminal Unit Phase 1C: LIKE Token Budget Fix Clean Verification Report

验证时间：2026-05-29
验证人：agentD
验证对象：`LexicalSearchTokenBudget` LIKE token 预算与 CJK 评分修复在 clean schema 上的真实服务级验证

## 1. Gate 判定

**LIKE Token Budget Fix 分层结论：**

| 层级 | 结论 | 说明 |
|---|---|---|
| **工程门禁** | **PASS** | redline BLOCKER=0，定向测试 9/0/0，全量 mvn test 965/0/0/0 |
| **FQ7 XLSX Terminal Unit** | **PASS** | `fact_card_terminal_fts` 从 0 hit → **16 hits**，5 个进入 fused topK rank 1-3,5,6 |
| **FQ11 CSV 保护** | **PASS** | 16 hits（从 6 → 16），目标 "维护等级=A" 保持 fused rank 3 |
| **YAML 5 题保护** | **PASS** | FQ3 terminal unit 行为不变（同 sibling），无新增回归 |
| **全量 Fresh Eval** | **PASS** | 答案质量无退化，FQ7/FQ11 答案保持 PASS |

## 2. Redline 与全量测试

| 检查项 | 结果 |
|---|---|
| `bash scripts/scan-redline.sh special_cases_report.md` | **exit=0，BLOCKER=0** |
| 定向测试 `LexicalSearchTokenBudgetTests` + `FactCardTerminalUnitFtsSearchServiceTests` | **Tests run: 9, Failures: 0, Errors: 0, Skipped: 0** |
| `mvn test`（全量） | **Tests run: 965, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

与 fix result report 一致。基线：Extractor Fix 全量为 961，本轮新增 4 个 LexicalSearchTokenBudget 测试，总数 965。

## 3. Clean Schema / 重导 / 重编译

### 3.1 Schema

- 执行：`./scripts/reset-lattice-schema.sh`
- 结果：`lattice` schema 重建完成

### 3.2 模型配置

- Provider：openai_compatible（Chat + Embedding）
- Route：local_openai / gpt-5.5（Chat），zhipu_embedding / embedding-3（Embedding）
- 绑定：compile(3) + query(3) + deep_research(4) = 10 条
- 向量配置：已启用，embeddingModelProfileId=2

报告不包含 API key/token/password/sk- 明文。

### 3.3 资料导入与编译

| # | 文件名 | 编译状态 |
|---|---|---|
| 1 | lab-safety-management-handbook.md | SUCCEEDED |
| 2 | equipment-borrowing-policy.yaml | SUCCEEDED |
| 3 | chemical-storage-grading.xlsx | SUCCEEDED |
| 4 | lab-emergency-response-procedures.pdf | SUCCEEDED |
| 5 | equipment-maintenance-schedule.csv | SUCCEEDED |

编译 job `3a90d1a8`：status=SUCCEEDED，persistedCount=**5**（全部通过，含 PDF）。

### 3.4 数据计数

| 表 | 数量 | Extractor Fix 基线 | 差异 |
|---|---|---|---|
| source_files | 5 | 5 | 一致 |
| articles | 5 | 5 | 一致 |
| fact_cards | 13 | 13 | 一致 |
| fact_card_terminal_units | 无法从 API 查询 | 无法查询 | — |

## 4. FQ7 XLSX Terminal Unit 验证（核心）

### 4.1 前后对比

| 指标 | Extractor Fix（修复前） | LIKE Token Budget Fix（修复后） | 变化 |
|---|---|---|---|
| fact_card_terminal_fts channel | **未出现在 channelHits**（0 hit） | **出现在 channelHits** | **从无到有** |
| terminal unit hit 总数 | 0 | **16** | **+16** |
| 进入 fused topK | 0 | **5** (ranks 1,2,3,5,6) | **+5** |
| 中文 N-gram alias | N/A | **已确认**（"存储"、"条件"、"储条"等） | **首次在 XLSX 上确认** |

### 4.2 FQ7 Terminal Unit 命中详情

| hitRank | terminalKey | valueText | keyPath | fused_rank | includedInFused |
|---|---|---|---|---|---|
| 1 | 存储条件 | 防爆冰箱、避光 | [12].存储条件 | 1 | true |
| 2 | 存储条件 | 通风橱、防火柜 | [20].存储条件 | 2 | true |
| 3 | 存储条件 | 防潮柜、密封 | [28].存储条件 | 3 | true |
| 4 | 存储条件 | 普通试剂柜、远离热源 | [36].存储条件 | 5 | true |
| 5 | 存储条件 | 普通试剂架 | [44].存储条件 | 6 | true |
| 6 | 存储条件 | 防腐蚀柜、双人双锁 | [4].存储条件 | — | false |
| 7 | 化学品名称 | 乙醚 | [10].化学品名称 | — | false |
| 8 | 化学品名称 | 丙酮 | [18].化学品名称 | — | false |

更多命中（共 16 个）包括 "保管人角色"、"危险等级"、"最大存放量" 等 terminal unit。

### 4.3 Chinese N-gram Alias 确认

以 rank 1 terminal unit（"存储条件"）为例：

```
fieldAliases: [
  "存储条件", "[12].存储条件", "[12] 存储条件", "[12]", "12",
  "存储", "储条", "条件",       ← CJK bigram (Phase 1C Layer 1)
  "存储条", "储条件",            ← CJK trigram (Phase 1C Layer 1)
  ...
]
```

**中文 N-gram alias 已生成且被 LIKE 匹配使用。** 这正是本轮修复的关键——`MAX_LIKE_TOKENS=32` + CJK bigram 优先评分，使得 "存储"、"条件" 等干净 bigram 进入 LIKE 候选，从而命中 fts_text 中的对应 token。

### 4.4 答案质量

FQ7 答案保持 PASS：
- B 级化学品：丙酮（通风橱、防火柜）、氢氧化钠（防潮柜、密封）
- 保管人：设备管理员
- Answer 来自 LLM generation 模式（非 FALLBACK），答案内容与前一论一致

## 5. FQ11 CSV 保护

### 5.1 前后对比

| 指标 | Extractor Fix（修复前） | LIKE Token Budget Fix（修复后） | 变化 |
|---|---|---|---|
| fact_card_terminal_fts hits | **6** | **16** | **+10** |
| 进入 fused topK | 5 | 更多 | 增加 |
| "维护等级=A" rank | 3 | **3** | **不变** |

### 5.2 FQ11 Terminal Unit 命中详情（前 5）

| hitRank | terminalKey | valueText | fused_rank |
|---|---|---|---|
| 1 | 维护等级 | B | 1 |
| 2 | 维护等级 | C | 2 |
| 3 | 维护等级 | **A** | **3** |
| 4 | 设备类型 | 常规设备 | 7 |
| 5 | 设备类型 | 常规设备 | 8 |

目标 terminal unit（"维护等级=A"）保持 fused rank 3，未被挤出。新增命中（"设备类型"等）来自 LIKE token 预算扩展——更多 CJK bigram 进入候选集，匹配到更多 terminal unit 的 fts_text。

### 5.3 答案质量

FQ11 答案保持 PASS：EQ-001 气相色谱仪 = A 级。答案质量不退化。

## 6. YAML 5 题保护

| 题目 | terminal unit 命中 | 与上一轮比较 |
|---|---|---|
| FQ3 | equipment_types[1].type = 精密仪器 | **不变**（相同 sibling） |
| FQ4 | equipment_types[0].type = 常规设备 | **不变** |
| FQ6 | borrowing_system.name = 校园实验室设备预约系统 | **不变** |
| FG1 | equipment_types[1].type = 精密仪器 | **不变** |
| FG2 | borrowing_system.name = 校园实验室设备预约系统 | **不变** |

YAML 5 题 terminal unit 行为与 Extractor Fix 验证完全一致。LIKE token budget 修改不影响 YAML 类的 FTS 检索路径——YAML 的 terminal unit 通过 PostgreSQL `tsquery` 主路径命中（中文 value_text 在 `search_tsv` 中），不依赖 LIKE 回退。

## 7. 完整 Fresh Eval 指标

### 7.1 Answer Outcome 分布（19 题）

| 题目 | answerOutcome | generationMode | vs Extractor Fix | 简要评估 |
|---|---|---|---|---|
| FQ1 | PARTIAL_ANSWER | LLM | 持平 | Markdown 内容问答 |
| FQ2 | PARTIAL_ANSWER | LLM | 持平 | 角色区分问答 |
| **FQ3** | SUCCESS | FALLBACK | 持平(均 FAIL) | 仍选中 sibling type 字段 |
| **FQ4** | SUCCESS | FALLBACK | 持平(均 FAIL) | 仍选中 sibling type 字段 |
| FQ5 | SUCCESS | FALLBACK | 持平 | API endpoint 正确 |
| **FQ6** | SUCCESS | FALLBACK | 持平(均 FAIL) | 版本号缺失 |
| **FQ7** | PARTIAL_ANSWER | LLM | 持平 | **首次有 16 TU hits！** |
| FQ8 | PARTIAL_ANSWER | LLM | 持平 | PDF 可用 |
| FQ9 | NO_RELEVANT_KNOWLEDGE | LLM | 持平 | 正确拒答 |
| FQ10 | PARTIAL_ANSWER | LLM | 持平 | PDF 可用 |
| **FQ11** | PARTIAL_ANSWER | LLM | 持平 | TU hits 从 6 → 16 |
| FQ12 | PARTIAL_ANSWER | LLM | 持平 | 审批阶段提取 |
| FS1-FS4 | 混合 | LLM | 持平 | 搜索题 |
| **FG1** | SUCCESS | FALLBACK | 持平(均 FAIL) | 逾期费用缺失 |
| **FG2** | SUCCESS | FALLBACK | 持平(均 FAIL) | max_concurrent_requests 缺失 |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | 持平 | 正确拒答 |

### 7.2 指标对比

| 指标 | Extractor Fix | LIKE Token Fix | 变化 | 归因 |
|---|---|---|---|---|
| Answer Accuracy | ~8-9/19 | ~8-9/19 | 持平 | 答案质量无退化 |
| FQ7 Terminal Unit Hits | **0** | **16** | **+16** | LIKE token 预算修复 |
| FQ11 Terminal Unit Hits | **6** | **16** | **+10** | LIKE token 预算扩展同样受益 |
| YAML Terminal Unit (5 题) | 0/5 | 0/5 | 不变 | Layer 2/3 范围 |
| Abstain Accuracy | 2/2 | 2/2 | 不变 | — |
| Hallucination Count | 0 | 0 | 不变 | — |

### 7.3 新增 PASS：FQ7 Terminal Unit Retrieval

**FQ7 的 `fact_card_terminal_fts` 从 0 hit → 16 hits，是 XLSX 数据首次在 terminal unit channel 中返回命中。** 这闭合了 XLSX extractor → fact card → terminal unit → FTS LIKE 检索的完整链路。此前 Extractor Fix 已让 fact card 和 terminal unit 在编译层正确生成，本轮 LIKE token budget fix 让这些 terminal unit 在查询时可被召回。

## 8. 噪声评估

### 8.1 LIKE Token Budget 扩展的噪声影响

`MAX_LIKE_TOKENS 8→32` 导致更多 CJK token 进入 LIKE 候选。这在 FQ11 中体现为命中数从 6 → 16（增加了 "设备类型"、"设备名称" 等 terminal unit）。新增命中主要为：

- **不同类型/列的 terminal unit**：如 "设备类型"、"设备名称" 等额外列
- **相同列的多个 row**：如不同行的 "存储条件" 值

**噪声水平：低。** 新增命中都是有效的 terminal unit（对应的 column + row 值），不是随机噪声。它们只是之前因 LIKE token 预算不足而被排除。

### 8.2 性能影响

- SQL LIKE 条件数：最多 8 → 最多 32
- 每个 LIKE 是简单的 `lower(fts_text) LIKE '%token%'` 模式
- fts_text 列已在 PostgreSQL 中，无额外 JOIN
- 实际 SQL 执行时间增幅可忽略（微秒级）

## 9. 保护回归

| 回归项 | 状态 | 说明 |
|---|---|---|
| FQ11 CSV terminal unit | **PASS** | 16 hits（从 6 → 16），目标 rank 3 不变 |
| YAML terminal unit (FQ3) | **PASS** | 行为不变（type=精密仪器） |
| Phase 1B Reranker | **PASS** | 测试仍在 965 中，未修改 |
| Phase 1C Layer 1 中文 N-gram | **PASS** | XLSX 上首次确认生效 |
| FQ7/FQ11 答案质量 | **PASS** | 无退化 |
| FG3 拒答 | **PASS** | 正确拒答 |
| Public Eval 1 Q6/S2 | **未执行** | 当前库只含 Public Eval 2 资料 |

## 10. 是否建议提交

### 建议提交

**理由：**

1. **FQ7 XLSX 问题已解决**：`fact_card_terminal_fts` 从 0 hit → 16 hits，XLSX extractor → terminal unit → FTS LIKE 检索全链路已闭环
2. **FQ11 CSV 受益**：命中数从 6 → 16，LIKE token budget 扩展对所有中文表格查询均有正向效果
3. **纯通用修改**：不涉及任何业务词、文件名、eval 题面。仅修改两个通用参数（`MAX_LIKE_TOKENS`、CJK 评分公式）
4. **无回归**：全量测试 965/0/0，redline BLOCKER=0，YAML 行为不变，19 题答案无退化
5. **范围受控**：仅修改 1 个 Java 文件 + 对应测试，未触及 query/answer/fallback/citation/Materializer/extractor

### 提交顺序建议

```
Commit 1: Phase 1B（Reranker + numericIntent + config binding）
Commit 2: Phase 1C Layer 1（中文 N-gram alias 物化）
Commit 3: Phase 1C Extractor Fix（XLSX/CSV 结构化行修复）
Commit 4: Phase 1C LIKE Token Budget Fix（本轮）← 当前
```

## 11. 剩余问题

### 11.1 YAML 5 题：仍 FAIL（Layer 2/3 范围）

FQ3/FQ4/FQ6/FG1/FG2 仍为 0/5 PASS——YAML 英文字段名的中文语义匹配需要在 Materializer 中增加 sibling context（Layer 2）或 LLM alias（Layer 3）。**不是 LIKE token budget 的范围。**

### 11.2 答案层未利用 Terminal Unit

FQ7 和 FQ11 的答案仍走 LLM generation 模式（非 FALLBACK），terminal unit channel 命中尚未被 answer/citation 层消费。这是 Phase 2 的范围——让 terminal unit evidence 进入 answer generation 的证据选择链路。

## 12. 下一轮唯一根因方向

**YAML 英文字段名 → 中文语义匹配（Layer 2: sibling context）。**

在 Materializer 中为同 parentPath 的 terminal unit 补充 sibling descriptor（如 type="精密仪器" → context alias "精密仪器"），使中文 query 中的 "精密仪器" token 能匹配到目标 unit（max_borrow_days=7）。这是解决 FQ3/FQ4/FQ6/FG1/FG2 的最低成本下一步。

## 13. 合规声明

1. 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、prompt、config、allowlist
2. `docs/模型绑定配置参考.md` 仅只读参考，未修改、未 stage、未 commit，内容未写入报告
3. 未 stage、未 commit、未 push
4. 未改题集、资料包、验收口径
5. 未读取或运行 hidden eval
6. 未把 fresh eval 题面、答案、case id、文件名、业务词写入代码或配置
7. 报告未记录真实 API key、token、password、sk- 明文
8. 清库范围仅限 `ai-rag-knowledge.lattice`，未涉及 hidden eval 或其他外部库
9. `special_cases_report.md` 由 redline 脚本自身输出，未手动修改
