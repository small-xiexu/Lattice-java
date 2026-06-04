# 两套 Public Eval 清库端到端验证 Gate 报告

验证时间：2026-06-01 ~ 2026-06-02
执行人：agentD（验证 Agent）
代码 HEAD：`741647f test(eval): record post-cleanup public eval gate`

---

## 1. 验证环境

| 项 | 值 |
|---|---|
| JDK | 21 |
| Maven | `.codex/maven-settings.xml`，本地仓库 `/Users/sxie/maven/repository` |
| PostgreSQL | Docker `vector_db` (0.0.0.0:5432, healthy) |
| Redis | Docker `redis` (0.0.0.0:6379, healthy) |
| 服务端口 | `18082` |
| Schema | `ai-rag-knowledge.lattice` |
| 启动方式 | `./scripts/run-local-dev.sh` |
| Chat 模型 | gpt-5.5 (profile id=1, local_openai 连接) |
| Embedding 模型 | embedding-3 (profile id=2, zhipu_embedding 连接) |

---

## 2. git status 摘要

```
M  "docs/模型绑定配置参考.md"   (私有配置，不提交)
M  special_cases_report.md     (redline 输出，不提交)
?? docs/test/knowledge-base-e2e/fresh-eval-2026-05/fg1_field_alias_binding_runtime_verification_report.md
?? docs/test/knowledge-base-e2e/fresh-eval-2026-05/fg1_terminal_unit_consumption_root_cause_analysis_report.md
?? docs/test/knowledge-base-e2e/fresh-eval-2026-05/fg1_terminal_unit_current_breakpoint_analysis_report.md
?? docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_post_cleanup_remaining_failure_analysis_report.md
```

工作区干净，无生产代码、测试代码、配置、脚本或 prompt 修改。

---

## 3. 红线扫描结果

```
bash scripts/scan-redline.sh special_cases_report.md
BLOCKER=0
REVIEW=2072
ALLOWLIST=262
```

**BLOCKER=0，通过。**

---

## 4. mvn test 结果

```
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**全量测试通过。**

---

## 5. Public Eval 1（knowledge-base-e2e 保护集）

### 5.1 清库与编译

| 项 | 值 |
|---|---|
| 清库时间 | 2026-06-01 23:41 |
| 编译 jobId | `5624d6bd-3157-41e7-a268-b14c1f1d15c6` |
| 编译结果 | SUCCEEDED |
| persistedCount | 6 |

### 5.2 模型绑定摘要

全部 11 条绑定 enabled=true，均指向 gpt-5.5 (profile id=1)：

| Scene | Roles |
|---|---|
| compile | writer, reviewer, fixer, field-alias-enricher |
| query | answer, reviewer, rewrite |
| deep_research | planner, researcher, synthesizer, reviewer |

向量配置：vectorEnabled=true, embeddingModelProfileId=2

### 5.3 数据计数

| 表 | 计数 |
|---|---|
| source_files | 6 |
| articles | 6 |
| article_chunks | 13 |
| fact_cards | 11 |
| fact_card_terminal_units | 103 |
| agent_model_bindings | 11 |

### 5.4 Q1-Q12 逐题结果

| 题号 | queryId | answerOutcome | generationMode | modelExecutionStatus | 判定 | 说明 |
|---|---|---|---|---|---|---|
| Q1 | e69f95d2 | PARTIAL_ANSWER | LLM | SUCCESS | **PARTIAL** | 答出了"打磨知识库、验证probe职责/严重级别/角色分工稳定性"等方向，但未提及"最小场景落地"和"人工演练"；citations 为空 |
| Q2 | 0cb2bc79 | SUCCESS | LLM | SUCCESS | **PASS** | 三类probe职责区分清晰，表格化呈现 |
| Q3 | fbe3b869 | SUCCESS | LLM | SUCCESS | **PASS** | SL=组织节奏/升级判断/关键决策，TL=技术定位/事实核查/修复路径 |
| Q4 | 42561c2b | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 正确拒答：资料中无绩效奖金定义 |
| Q5 | 83620c7d | SUCCESS | LLM | SUCCESS | **PASS** | /healthz + 8080，正确 |
| Q6 | ef961390 | SUCCESS | FALLBACK | DEGRADED | **PASS** | spec.containers[0].readinessProbe.tcpSocket.port=8080，正确 |
| Q7 | d01825f6 | SUCCESS | LLM | SUCCESS | **PASS** | grpc-liveness.yaml，正确 |
| Q8 | 8fe50782 | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 正确拒答：无数据库用户名定义 |
| Q9 | 4808c7ee | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | initiate/assess/contain/remediate/retrospect 全部覆盖 |
| Q10 | 26efe43b | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 高=核心服务大面积不可用/数据泄露；中=局部能力下降。区分合理 |
| Q11 | 6ee2d1b7 | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | Scribe，正确（标注"当前证据不足"但不影响答案正确性） |
| Q12 | c641e8d8 | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | Extended，正确 |

### 5.5 S1-S4 搜索结果

| 题号 | 搜索词 | Top5 摘要 | 判定 | 说明 |
|---|---|---|---|---|
| S1 | Kubernetes 探针与事件响应协同手册 | 首位命中主文章"Kubernetes 探针与事件响应协同手册" | **PASS** | sourceTitle 搜索正常 |
| S2 | 下一步计划 | Top5 含"角色分工"chunk、"协同手册"、fact card（含"下一步计划"内容），但无明确标注为"下一步计划"的 chunk 条目 | **FAIL** | anchorTitle 搜索未返回目标 chunk 条目（已知 S2 chunk identity 问题） |
| S3 | 探针与事件响应协同手册 角色分工 | 首位命中自身 chunk 条目 | **PASS** | representativeTitle 搜索正常 |
| S4a | Situation Lead | 命中 incident checklist、协同手册/角色分工 等 | **PASS** | 关键词可定位 |
| S4b | /healthz | 首位命中 http liveness 条目 | **PASS** | 关键词可定位 YAML |
| S4c | Extended | 首位命中 incident checklist 条目 | **PASS** | 关键词可定位 XLSX |

### 5.6 Public Eval 1 指标汇总

| 指标 | 值 |
|---|---|
| Answer Accuracy | **11/12** (Q1 PARTIAL，其余 PASS) |
| Search Accuracy | **3/4** (S2 FAIL) |
| Recall@5 | 3/4 search queries 首位或前5命中正确条目 |
| Recall@10 | N/A（search 仅 Top5） |
| Citation Accuracy | Q1 citations 为空；Q6 fallback citation 正确；多数 LLM 模式题目 citation 正常 |
| Abstain Accuracy | **2/2** (Q4, Q8 正确拒答) |
| Hallucination Count | **0** |
| 失败类型 | S2: chunk 切分/检索身份折叠（已知问题，S2 chunk identity 修复已就绪但本轮未回归） |

---

## 6. Public Eval 2（fresh-eval-2026-05 泛化集）

### 6.1 清库与编译

| 项 | 值 |
|---|---|
| 清库时间 | 2026-06-02 00:02 |
| 编译 jobId | `e1c90945-1c3b-47e8-a45b-68954be1e65f` |
| 编译结果 | SUCCEEDED |
| persistedCount | 4 |

### 6.2 模型绑定摘要

同 Public Eval 1（两次清库后均重新恢复，配置一致）。11 条绑定全部 enabled=true。

### 6.3 field-alias-enricher snapshot 核验

execution_llm_snapshots 中确认包含：

| binding_id | route_label | model_name |
|---|---|---|
| 4 | compile.field-alias-enricher.gpt-5-5 | gpt-5.5 |

snapshot 包含 field-alias-enricher，binding_id 非空，model_name 非 fallback/unknown，route label 正常。

### 6.4 late_fee_per_day alias 核验

terminal unit 确认存在且包含中文别名：

| terminal unit | parent_path | value | 中文别名 |
|---|---|---|---|
| 12 | equipment_types[0] (常规设备) | 5 | 每日逾期费, 逾期日费 |
| 19 | equipment_types[1] (精密仪器) | 20 | 每日逾期费用, 逾期日费 |
| 26 | equipment_types[2] (大型设备) | 50 | 每日逾期费用, 逾期日费用 |

field_aliases_json 包含中文别名，fts_text 同步包含中文别名。确认 field-alias-enricher 生效。

### 6.5 数据计数

| 表 | 计数 |
|---|---|
| source_files | 5 |
| articles | 4 |
| article_chunks | 6 |
| fact_cards | 13 |
| fact_card_terminal_units | 123 |
| agent_model_bindings | 11 |

### 6.6 FQ1-FQ12 逐题结果

| 题号 | queryId | answerOutcome | generationMode | modelExecutionStatus | 判定 | 说明 |
|---|---|---|---|---|---|---|
| FQ1 | 9f7ee19b | PARTIAL_ANSWER | LLM | SUCCESS | **PARTIAL** | 内容正确覆盖 A/B/C/D 分级及存储条件，但"当前证据不足"、confidence 低 |
| FQ2 | 80d5d80d | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 安全员（安全检查/隐患排查/应急演练）vs 设备管理员（设备台账/借用审批/维护）区分清晰 |
| FQ3 | d1d9b348 | SUCCESS | FALLBACK | DEGRADED | **PASS** | equipment_types[1].max_borrow_days=7，精确正确 |
| FQ4 | 62b68d11 | PARTIAL_ANSWER | FALLBACK | DEGRADED | **FAIL** | 问的是 deposit_amount（押金），但回答的是 approval_required（审批人），选错 sibling 字段 |
| FQ5 | 165acd82 | SUCCESS | FALLBACK | DEGRADED | **PASS** | borrowing_system.api_endpoint=https://lab-equip.campus.edu/api/v2/borrow，正确 |
| FQ6 | b24f5df4 | SUCCESS | FALLBACK | DEGRADED | **PASS** | borrowing_system.version=v2.3.1，正确 |
| FQ7 | 740edcc0 | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | B级：丙酮→通风橱/防火柜/设备管理员；氢氧化钠→防潮柜/密封/设备管理员。内容正确 |
| FQ8 | 74384bbe | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 跨文档组合正确：处置流程（6步）+ 存储要求，分别引用 PDF 和 XLSX |
| FQ9 | 93173385 | NO_RELEVANT_KNOWLEDGE | LLM | SUCCESS | **PASS** | 正确拒答：资料中无餐饮服务管理规定 |
| FQ10 | 0820ad12 | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 6步处置流程正确列出，citation 指向 PDF |
| FQ11 | 0e7c1c89 | SUCCESS | LLM | SUCCESS | **PASS** | EQ-001 气相色谱仪，正确 |
| FQ12 | aaf7bcae | PARTIAL_ANSWER | LLM | SUCCESS | **PASS** | 指导教师审批→设备管理员审批→实验室主任审批，3阶段顺序正确 |

### 6.7 FS1-FS4 搜索结果

| 题号 | 搜索词 | Top5 摘要 | 判定 | 说明 |
|---|---|---|---|---|
| FS1 | 校园实验室安全管理手册 | rank2=SOURCE "01_markdown/lab-safety-management-handbook.md"，但 rank1 为 PDF article | **PARTIAL** | sourceTitle 可命中但未排在首位 |
| FS2 | 化学品分类存储 | "化学品存储分级表"（XLSX article）在 rank3-4，markdown chunk 未出现 | **FAIL** | anchorTitle 搜索未命中目标 chunk |
| FS3 | 实验室化学品分级存储管理规范 | rank1="化学品存储分级表"（XLSX），但期望的 markdown 条目未出现 | **PARTIAL** | representativeTitle 命中了 XLSX 而非 markdown |
| FS4a | 安全员 | Top5 含 XLSX 和 PDF article，无 markdown 条目 | **FAIL** | 关键词未命中 markdown |
| FS4b | B级 | count=0，无结果 | **FAIL** | "B级" 搜索完全无结果 |
| FS4c | 精密仪器 | Top5 含 equipment-borrowing-policy.yaml terminal units | **PASS** | 关键词可定位 YAML |

### 6.8 FG1-FG3 逐题结果

| 题号 | queryId | answerOutcome | generationMode | modelExecutionStatus | 判定 | 说明 |
|---|---|---|---|---|---|---|
| FG1 | 2b8c3851 | SUCCESS | FALLBACK | DEGRADED | **FAIL** | 问"精密仪器逾期罚金(20)和常规设备逾期罚金(5)"，但答案给出 borrowing_system.api_endpoint（URL），完全答错。terminal unit 存在正确值但未被 conclusion builder 消费 |
| FG2 | 3b480995 | PARTIAL_ANSWER | FALLBACK | DEGRADED | **PASS** | borrowing_system.max_concurrent_requests=50，正确 |
| FG3 | 321dfead | INSUFFICIENT_EVIDENCE | LLM | SUCCESS | **PASS** | 正确拒答：资料中无灭火器更换周期定义 |

### 6.9 Public Eval 2 指标汇总

| 指标 | 值 |
|---|---|
| Answer Accuracy | **11/15** (FQ1 PARTIAL, FQ4 FAIL, FG1 FAIL) |
| Search Accuracy | **1.5/4** (FS1 PARTIAL, FS2 FAIL, FS3 PARTIAL, FS4 1/3 PASS) |
| Recall@5 | 3/6 search sub-queries 命中目标条目 |
| Recall@10 | N/A（search 仅 Top5） |
| Citation Accuracy | FQ4 citation 指向错误 sibling 字段；FG1 citation 指向无关字段（api_endpoint） |
| Abstain Accuracy | **2/2** (FQ9, FG3 正确拒答) |
| Hallucination Count | **0**（FQ4 和 FG1 是选错 sibling 字段，不是编造不存在的内容） |
| 失败类型 | FQ4: 证据已召回但回答漏点（选错 sibling）；FG1: 结论构建器未消费（terminal unit 存在但未被选中）；FS2/FS4a/FS4b: 检索未召回 |

---

## 7. FG1 断点定位

**唯一根因：结论构建器未消费（conclusion builder did not consume terminal unit）**

证据链：
1. field-alias-enricher binding 存在（id=4, route=compile.field-alias-enricher.gpt-5-5）
2. terminal unit 已正确生成：equipment_types[0].late_fee_per_day=5, equipment_types[1].late_fee_per_day=20
3. field_aliases_json 包含中文别名（"每日逾期费"、"逾期日费"）
4. fts_text 同步包含中文别名
5. 但 query 返回的结论使用了 borrowing_system.api_endpoint 而非 late_fee_per_day terminal unit

**不是** field-alias-enricher 绑定缺失，**不是** alias 生成失败，**不是** terminal unit 未生成，**不是** fused 排序低。是 fallback conclusion builder 选错了候选——未消费已存在的 late_fee_per_day terminal unit。

---

## 8. 两套题集是否整体通过

**不通过。**

- Public Eval 1：Answer Accuracy 11/12，但 Search Accuracy 3/4（S2 标题/anchor 搜索仍 FAIL）
- Public Eval 2：Answer Accuracy 11/15，Search Accuracy 1.5/4

两套题集的 Search Accuracy 均未达标；Public Eval 2 Answer Accuracy 11/15 低于通过线（>=12/15）。主要 blocker：
- FQ4: sibling 字段误选
- FG1: conclusion builder 未消费 terminal unit
- FS2/FS4a/FS4b: 中文关键词检索未命中

---

## 9. 下一轮唯一允许处理的根因（按优先级）

1. **FG1 conclusion builder terminal unit 消费修复**：terminal unit 已正确生成且包含中文别名，但 fallback conclusion builder 未选中。需在 conclusion builder 中优先消费 question-focused terminal unit 候选。
2. **FQ4 sibling 字段区分**：与 FG1 同根——fallback 选中了错误的 sibling 字段。修复 conclusion builder 的 terminal unit 选择逻辑应同时覆盖 FQ4。
3. **FS2/FS4 中文关键词检索**：S2 chunk identity 修复已就绪（agentA 已完成代码层修复），但本轮未回归；FS4b "B级" 完全无结果属于检索缺口。

---

## 10. 明确声明

- [x] 未修改生产代码（src/main/java）
- [x] 未修改测试代码（src/test/java）
- [x] 未修改 prompt/config/schema/scripts
- [x] 未读取 hidden eval
- [x] 未输出密钥（报告仅含脱敏字段：profile id、binding id、route label、model name）
- [x] 未提交 docs/模型绑定配置参考.md
- [x] 未把两套资料混在同一个清库状态里验证
- [x] 两次清库之间已完成独立验证

---

## 11. 对比：Phase 1I 后历史基线

| 指标 | Phase 1I 后基线 | 本轮 Public Eval 2 | 变化 |
|---|---|---|---|
| Answer Accuracy | 12/15 | 11/15 | **-1** (FG1 倒退) |
| Search Accuracy | 1/4 | 1.5/4 | +0.5 |
| Citation Accuracy | 2/15 | 2/15 | 持平 |
| Abstain Accuracy | 2/2 | 2/2 | 持平 |
| Hallucination | 2 | 0 | **-2** (改善) |

FG1 从 Phase 1I 后的 PARTIAL 变为本轮 FAIL——之前的 FG1 绑定验证（`fg1_field_alias_binding_runtime_verification_report.md`）是在只编译 `02_structured` 目录下的环境中测试的，而本轮全量 Fresh Eval 编译后 FG1 回归为 FAIL。原因可能是全量编译的 fused order 或 evidence 竞争环境与单目录编译不同。

Hallucination 从 2 降到 0——本轮 FAIL 是 sibling 字段误选（证据层问题），不是无证据编造。
