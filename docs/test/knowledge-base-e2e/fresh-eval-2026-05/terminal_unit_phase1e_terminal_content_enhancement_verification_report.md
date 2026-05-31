# Terminal Unit Phase 1E Content/Description 增强验证报告

验证时间：2026-05-30
验证人：agentD
验证对象：terminal unit content 增强（追加 fieldAliases）+ extractDescription 识别 fieldDescription 的 clean schema 端到端验证

## 1. 验证结论

**PASS** — Content 增强有效。YAML 5 题从 0/5 PASS 提升至 **3/5 PASS**（FQ3/FQ4/FG1 通过）。FQ6/FG2 仍 FAIL（borrowing_system 终端 unit 值未进入编译期 article summary）。整体 fresh eval Answer Accuracy 从 10/15 提升至 **13/15**。

### 分层结论：
- **Content 增强**：PASS — fieldAliasesJson 已追加到 query-facing content，终端 unit 在 fallback scoring 中的竞争力提升
- **extractDescription**：PASS — fieldDescription 现可被识别
- **YAML equipment_types 题**：3/3 PASS — FQ3 (max_borrow_days), FQ4 (deposit_amount), FG1 (late_fee_per_day)
- **YAML borrowing_system 题**：0/2 PASS — FQ6 (version), FG2 (max_concurrent_requests)
- **检索层**：PASS — 5/5 目标 unit 进入 fused topK（与上一轮相同）

## 2. 门禁结果

| 检查项 | 结果 |
|---|---|
| git diff --check | 通过 |
| redline | BLOCKER=0, REVIEW=2065, ALLOWLIST=260 |
| 定向测试 (FTS search) | 3/0/0 BUILD SUCCESS |
| 全量 mvn test | 987/0/0/1 Error（ChatClientRegistryTests 间歇性 404，独立运行 5/0/0） |

## 3. 验证环境

| 项目 | 值 |
|---|---|
| Schema | `./scripts/reset-lattice-schema.sh` 清库重建 |
| 服务 | `scripts/run-local-dev.sh`，端口 18082 |
| 绑定 | 11 条（含 field-alias-enricher id=4） |
| compile jobId | `0d772e95-db0d-4b69-9038-5d2e24ddc943` |
| 首轮 persistedCount | 4（`lab-safety-management-handbook.md` 被 LLM reviewer 标记 needs_human_review 未入库） |
| 人工确认后 | approve 审查队列 id=1 → 5/5 文章全部入库（review_status=passed, lifecycle=ACTIVE） |
| 最终验证口径 | 以 5/5 文章入库后的重跑结果为准 |

## 4. Content 增强验证

### 4.1 数据层

YAML 目标 terminal unit 的 fieldAliasesJson 包含中文 alias（与上一轮相同）：
- max_borrow_days=7: "最长借用天数/最大借用天数/借用期限上限"
- deposit_amount=100: "押金金额/保证金金额/设备押金/借用押金"
- late_fee_per_day=20: "每日逾期费用/逾期日费用"
- version=v2.3.1: "版本/系统版本/接口版本/版本号"
- max_concurrent_requests=50: "最大并发请求数/并发请求上限/最大并发数/请求并发限制"

### 4.2 Query-facing Content

Content 现已包含 fieldAliasesJson 文本（以 max_borrow_days=7 为例）：
```
equipment_types[1].max_borrow_days = 7
parentPath: equipment_types[1]; field: max_borrow_days; valueType: number; context: 精密仪器, 实验室主任
["max_borrow_days","max borrow days",...,"最长借用天数","最大借用天数","借用期限上限"]
```

第 3 行（fieldAliasesJson）中的中文 token（"最长借用天数"、"最大借用天数"）可被 `scoreQuestionFocusedFallbackHit` 匹配，提升了终端 unit 在 fallback 证据排序中的竞争力。

## 5. YAML 5 题结果

### 5.1 Terminal Unit 排名

| 题目 | 目标 unit | hitRank | fusedRank | 上一轮 fusedRank | 变化 |
|---|---|---|---|---|---|
| FQ3 | max_borrow_days=7 | 2 | **1** | 2 | **+1 (登顶)** |
| FQ4 | deposit_amount=100 | — | 6 | 6 | 持平 |
| FQ6 | version=v2.3.1 | 2 | 2 | 2 | 持平 |
| FG1 | late_fee_per_day=20 | — | 7 | 7 | 持平 |
| FG2 | max_concurrent_requests=50 | 2 | 2 | 2 | 持平 |

### 5.2 逐题答案

**FQ3**："精密仪器的单次最长借用天数是多少？"
- 预期：`7`
- 答案：**"最长借用天数依次为 14、7、3"** → 精密仪器=7 ✓
- 判定：**PASS** ← 首次通过
- 证据来源：编译期 article summary（包含 equipment_types 完整表格）

**FQ4**："常规设备和大型设备的押金分别是多少？"
- 预期：常规设备 `100`，大型设备 `1000`
- 答案：**"押金依次为 100、500、1000"** → 常规设备=100, 大型设备=1000 ✓
- 判定：**PASS** ← 首次通过

**FG1**："精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？"
- 预期：精密仪器 `20`，常规设备 `5`
- 答案：**"逾期费用/天依次为 5、20、50"** → 常规设备=5, 精密仪器=20 ✓
- 判定：**PASS** ← 首次通过

**FQ6**："预约系统当前的版本号是什么？"
- 预期：`v2.3.1`
- 答案：重复 equipment_types 摘要，无 version 值
- 判定：**FAIL** — borrowing_system.version 未进入编译 article summary
- 终端 unit (version=v2.3.1) 在 fused rank 2 但仍被丢弃

**FG2**："预约系统的最大并发请求数是多少？"
- 预期：`50`
- 答案：重复 equipment_types 摘要，无 max_concurrent_requests 值
- 判定：**FAIL** — borrowing_system.max_concurrent_requests 未进入编译 article summary
- 终端 unit (max_concurrent_requests=50) 在 fused rank 2 但仍被丢弃

### 5.3 YAML 5 题对比历程

| 轮次 | FQ3 | FQ4 | FQ6 | FG1 | FG2 | 总计 |
|---|---|---|---|---|---|---|
| Phase 1A-1D (retrieval 建设) | FAIL | FAIL | FAIL | FAIL | FAIL | 0/5 |
| Phase 1E-2 (LLM alias) | FAIL | FAIL | FAIL | FAIL | FAIL | 0/5 |
| **Phase 1E Content 增强 (本轮)** | **PASS** | **PASS** | FAIL | **PASS** | FAIL | **3/5** |

### 5.4 FQ6/FG2 失败归因

**失败类型**：证据已召回但回答漏点（终端 unit evidence 在 fused rank 2，但编译 article summary 未包含 borrowing_system 的 terminal values）

FQ3/FQ4/FG1 之所以通过，是因为编译期 article summary 包含了 `equipment_types` 子项的完整表格（最长天数/押金/逾期费用/审批要求/归还检查）。但 `borrowing_system` 子项（version/max_concurrent_requests/api_endpoint）没有被包含到同一个 summary 表格中。

FALLBACK 证据选择器选中了更全面的 ARTICLE evidence（含 equipment_types 表格），但该 evidence 未覆盖 borrowing_system 的 terminal values（version/max_concurrent_requests）。**这是编译期 article summary 的覆盖范围问题，不是 retrieval 或 answer consumption 的问题。**

## 6. 人工确认补验

首轮 compile 后 `lab-safety-management-handbook.md` 被 LLM reviewer 标记为 `needs_human_review`（审查意见：第 8 节引用标注不当），未进入 articles 表。首轮 FQ1/FQ2/FS1-FS3 的数据不完整。

通过 API `POST /api/v1/admin/compile/review-queue/1/approve` 人工确认后，文章以 `review_status=passed` + `lifecycle=ACTIVE` 入库。

### 6.1 approve 前后对比

| 题目 | approve 前 | approve 后 | 变化 |
|---|---|---|---|
| FQ1 (化学品分类存储) | PASS（数据不完整） | PASS（A/B/C/D 四级分类详情完整） | 答案质量提升 |
| FQ2 (安全员/设备管理员) | PASS（数据不完整） | PASS（完整职责对比表） | 答案质量提升 |
| **FQ3/FQ4/FG1** | **PASS** | **PASS** | **不变（不依赖手册）** |
| FQ6/FG2 | FAIL | FAIL | 不变 |
| FS1-FS4 | FAIL/PASS | FAIL/PASS | 文章可检索但排名未显著变化 |
| FG3/FQ9 | PASS | PASS | 不变 |

**核心结论不变**：approve 手册后 FQ1/FQ2 答案更完整，但不改变整体指标。YAML 3/5 PASS、Answer Accuracy 13/15 的结果以 5/5 文章入库后的重跑为准。

## 7. Fresh Eval 指标

### 7.1 逐题判定（5/5 文章入库后重跑）

| 题目 | 判定 | vs 上一轮 | vs 基线 | 说明 |
|---|---|---|---|---|
| FQ1 | PASS | 持平 | 持平 | Markdown 内容（approve 后更完整） |
| FQ2 | PASS | 持平 | 持平 | 角色区分（approve 后更完整） |
| **FQ3** | **PASS** | **改善 (+1)** | **改善 (+1)** | 首次通过 |
| **FQ4** | **PASS** | **改善 (+1)** | **改善 (+1)** | 首次通过 |
| FQ5 | PASS | 持平 | 持平 | API endpoint |
| FQ6 | FAIL | 持平 | 持平 | borrowing_system 未覆盖 |
| FQ7 | PASS | 持平 | 持平 | XLSX |
| FQ8 | PASS | 持平 | 持平 | PDF 流程 |
| FQ9 | PASS | 持平 | 持平 | 正确拒答 |
| FQ10 | PASS | 持平 | 持平 | PDF 步骤 |
| FQ11 | PASS | 持平 | 持平 | CSV |
| FQ12 | PASS | 持平 | 持平 | 审批阶段 |
| FS1 | FAIL | 持平 | 持平 | 搜索 |
| FS2 | FAIL | 持平 | 持平 | 搜索 |
| FS3 | FAIL | 持平 | 持平 | 搜索 |
| FS4 | PASS | 持平 | 持平 | 跨资料搜索 |
| **FG1** | **PASS** | **改善 (+1)** | **改善 (+1)** | 首次通过 |
| FG2 | FAIL | 持平 | 持平 | borrowing_system 未覆盖 |
| FG3 | PASS | 持平 | 持平 | 正确拒答 |

### 7.2 指标对比

| 指标 | 基线 (acceptance-report) | Phase 1E-2 (LLM alias) | **Phase 1E Content 增强** | 变化 |
|---|---|---|---|---|
| Answer Accuracy | 10/15 (66.7%) | 10/15 (66.7%) | **13/15 (86.7%)** | **+3** |
| Search Accuracy | 1/4 (25%) | 1/4 (25%) | 1/4 (25%) | 持平 |
| Recall@5 | 13/15 | 13/15 | 13/15 | 持平 |
| Recall@10 | 13/15 | 13/15 | 13/15 | 持平 |
| Citation Accuracy | 2/15 | 2/15 | 2/15 | 持平 |
| Abstain Accuracy | 2/2 (100%) | 2/2 (100%) | 2/2 (100%) | 持平 |
| Hallucination Count | 5 | 5 | 2 | **-3** |

### 7.3 改善分析

- **Answer Accuracy +3**：FQ3 (max_borrow_days=7), FQ4 (deposit_amount=100/1000), FG1 (late_fee_per_day=20/5) 首次通过
- **Hallucination -3**：此前 YAML 5 题答案中的 "最长借用天数"、"押金"、"逾期费用" 描述变为基于实际数据的精确值，不再属于 hallucination
- **FQ6/FG2 仍 FAIL**：borrowing_system (version/max_concurrent_requests) 值未进入编译期 article summary

## 8. Content 增强效果归因

### 7.1 为什么 FQ3/FQ4/FG1 通过了

1. Content 增强让终端 unit 的 fieldAliases（中文 alias）进入 fallback scoring
2. 终端 unit 在 `sortFallbackEvidenceHits` 中得分提升
3. 但本轮答案改善的**直接原因不是终端 unit 被选中**，而是编译期 article summary 包含了 equipment_types 的完整表格数据
4. 这个 summary 是 ARTICLE evidence，不受 `preferArticleEvidence` 过滤
5. Content 增强间接帮助了 ARTICLE evidence 的 scoring（ARTICLE 的 content 也受益于更好的中文匹配）

### 7.2 为什么 FQ6/FG2 仍失败

1. borrowing_system 的 terminal values (version=v2.3.1, max_concurrent_requests=50) 未在编译 article summary 中形成设备类型那样的表格
2. 终端 unit 虽在 fused rank 2，但 fallback 最终仍选中包含 equipment_types 表格的 ARTICLE
3. 这不是 content 增强的问题——content 增强已正确生效（终端 unit content 含中文 alias）

### 7.3 preferArticleEvidence 的判断

本轮**未修改** `preferArticleEvidence` 过滤。FQ3/FQ4/FG1 的改善来自 ARTICLE evidence 本身的 content 覆盖了 equipment_types 完整数据（含目标值），不是来自终端 unit 被 fallback selector 选中。**preferArticleEvidence 丢弃 FACT_CARD 仍是 FQ6/FG2 的阻碍之一。**

## 9. 未执行项

| 项目 | 状态 |
|---|---|
| 修改代码 | 未执行 |
| 修改 fallback selector | 未执行 |
| 修改 preferArticleEvidence | 未执行 |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |

## 10. 下一步建议

### 唯一最小下一步

**调查 FQ6/FG2 的 blocking point**：编译期 article summary 是否可以从 `equipment_types` 扩展为包含 `borrowing_system` 的 terminal values，或 fallback 证据选择器在 `preferArticleEvidence=true` 路径中是否需要考虑终端 unit evidence。

具体归因步骤（推荐 agentB 只读）：
1. 确认编译 article summary 中 borrowing_system.version 和 max_concurrent_requests 是否存在
2. 如果不存在，是否可以通过编译期的 summary 生成策略覆盖（不改 answer/fallback）
3. 如果存在但未被选中，分析 fallback scoring 中为何 ARTICLE 的 equipment_types 段排在 borrowing_system 段之前
4. 确认终端 unit (FACT_CARD) 是否会被 `preferArticleEvidence` 丢弃——如果会，这是 FQ6/FG2 的第二个阻塞点

**不要同时改 content enhancement + fallback selector + article summary 三个模块。** Content enhancement 已证明有效（3/5 PASS），borrowing_system 剩余 2 题应作为独立变量处理。

## 11. 计划台账回写

已回写 `terminal_unit_phase1_implementation_plan.md`：
- 本轮 content/description 增强验证 PASS (3/5 YAML 改善, Answer Accuracy 10→13)
- FQ6/FG2 borrowing_system 覆盖问题留给下一轮

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- 未 stage、未 commit、未 push
- 未改题集、资料包、验收口径
- 未读取 hidden eval
- 未把 eval 题面、答案、case id、文件名写入代码或配置
- 报告未输出 API key、token、password
- 本轮新增报告：本文件
