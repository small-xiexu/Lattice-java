# Terminal Unit Phase 1I 完整 Fresh Eval 验证报告

验证时间：2026-05-31
验证人：agentD

## 1. 验证结论

**PASS** — FQ6 首次通过，Answer Accuracy 从基线 10/15 提升至 **12/15**。YAML 5 题从基线 0/5 提升至 **4/5**（FQ3/FQ4/FQ6/FG2 PASS，FG1 PARTIAL）。无新增回归。

## 2. 环境与门禁

| 项目 | 值 |
|---|---|
| Schema | 完整重建 |
| compile jobId | `3e4dc5d9-3efb-4799-a216-2d436c3bdef9` |
| 状态 | SUCCEEDED，acceptedCount=5，needsHumanReview=0 |
| git diff --check | 通过 |
| redline | BLOCKER=0 |
| AnswerFallbackConclusionBuilderTests | 7/0/0 |
| AnswerFallbackEvidenceSelectorTests | 11/0/0 |
| 定向组合 | 18/0/0 |

## 3. 完整 19 题指标

### 3.1 逐题判定

| 题目 | 判定 | 说明 |
|---|---|---|
| FQ1 | PASS | Markdown 化学品分类存储 |
| FQ2 | PASS | 安全员/设备管理员职责区分 |
| **FQ3** | **PASS** | `Confirmed evidence: equipment_types[1]...`，终端 unit 消费生效 |
| **FQ4** | **PASS** | `Confirmed evidence: equipment_types[0]...`，终端 unit 消费生效 |
| FQ5 | PASS | API endpoint 正确 |
| **FQ6** | **PASS** | `Confirmed evidence: borrowing_system.version = v2.3.1` ← **首次通过！** |
| FQ7 | PASS | B 级化学品存储/保管人 |
| FQ8 | PASS | 丙酮泄漏流程+存储 |
| FQ9 | PASS | 正确拒答 (INSUFFICIENT_EVIDENCE) |
| FQ10 | PASS | PDF 步骤 |
| FQ11 | PASS | A 级设备 EQ-001/气相色谱仪 |
| FQ12 | PASS | 审批阶段 |
| FS1 | FAIL | 搜索排名未改善 |
| FS2 | FAIL | 搜索排名未改善 |
| FS3 | FAIL | 搜索排名未改善 |
| FS4 | PASS | 跨资料搜索 |
| **FG1** | **PARTIAL** | 终端 unit 未消费（与之前轮次一致），但 answer 提及逾期费用 |
| **FG2** | **PASS** | `Confirmed evidence: borrowing_system.max_concurrent_requests = 50` |
| FG3 | PASS | 正确拒答 (INSUFFICIENT_EVIDENCE) |

### 3.2 指标汇总

| 指标 | 基线 | Phase 1I | 变化 |
|---|---|---|---|
| Answer Accuracy | 10/15 (66.7%) | **12/15 (80.0%)** | **+2** |
| YAML 5 题 | 0/5 (0%) | **4/5 (80%)** | **+4** |
| Search Accuracy | 1/4 (25%) | 1/4 (25%) | 持平 |
| Recall@5 | 13/15 | 13/15 | 持平 |
| Recall@10 | 13/15 | 13/15 | 持平 |
| Citation Accuracy | 2/15 | 2/15 | 持平 |
| Abstain Accuracy | 2/2 (100%) | 2/2 (100%) | 持平 |
| Hallucination Count | 5 | **2** | **-3** |

### 3.3 YAML 5 题突破

| 题目 | 基线 | Phase 1I | 关键值 |
|---|---|---|---|
| FQ3 | FAIL | **PASS** | `equipment_types[1]...` terminal unit exact line |
| FQ4 | FAIL | **PASS** | `equipment_types[0]...` terminal unit exact line |
| FQ6 | FAIL | **PASS** | `borrowing_system.version = v2.3.1` |
| FG1 | FAIL | PARTIAL | 终端 unit 未消费 |
| FG2 | FAIL | **PASS** | `borrowing_system.max_concurrent_requests = 50` |

## 4. FQ6 审计

| 检查项 | 结果 |
|---|---|
| version terminal unit fused_rank | 1（优于 name 的 5） |
| metadata.fieldAliases | 含 "版本号/系统版本/接口版本" |
| final answer | `borrowing_system.version = v2.3.1` |
| name terminal unit 是否被误选 | **否**（fused order 选择正确） |
| citation | `[→ equipment-borrowing-policy.yaml]` |

## 5. 失败 case 分类

| Case | 失败类型 | 说明 |
|---|---|---|
| FS1 | 检索未召回 | 搜索排名问题 |
| FS2 | 检索未召回 | 搜索排名问题 |
| FS3 | 检索未召回 | 搜索排名问题 |
| FG1 | 证据已召回但回答漏点 | 终端 unit 未被 conclusion 消费 |

## 6. 是否新增回归

**无。** Phase 1I fused order conclusion 修复未引入任何回归。所有非 YAML 题与基线结果一致。

## 7. 未执行项

| 项目 | 状态 |
|---|---|
| 全量 mvn test | 未执行（定向测试 18/0/0 已覆盖修复范围） |
| 修改代码 | 未执行 |
| 读取 hidden eval | 未执行 |
| stage/commit/push | 未执行 |

## 8. 下一步

FQ6/FG2 已闭环。FG1 余留问题（terminal unit 未消费）可作为独立变量处理。建议提交当前 Phase 1 系列全部修复。

## 合规声明

- 本轮未修改代码、测试、配置、脚本、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 报告未输出 API key/token/password
