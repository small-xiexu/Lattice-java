# 知识库查询验证目标与进度

更新时间：2026-05-28

本文件只跟踪 knowledge-base-e2e 查询质量验证目标、题集进度、当前失败桶和下一步。质量打磨全局台账仍以 `docs/quality-progress-and-lessons.md` 为准；涉及具体实施计划时，以 `docs/plans/**` 指定计划为准。

## 目标

| 类型 | 目标数量 | 用途 | 可见性 |
|---|---:|---|---|
| Public eval | 5 套 | 暴露能力缺口、做失败归因、驱动通用修复 | 可放入 `docs/test/**`，允许 AI 读取 |
| Hidden eval | 1-2 套 | 最终泛化验收、防 public eval 过拟合 | 不放入仓库，不暴露题目/答案/关键词/文件名 |

## 通过门槛

| 指标 | Public eval 稳定目标 | Hidden eval 最终目标 |
|---|---:|---:|
| Answer Accuracy | >= 85% | >= 80% |
| Recall@10 | >= 90% | >= 85% |
| Citation Accuracy | >= 75% | >= 70% |
| Abstain Accuracy | >= 95% | >= 95% |
| Hallucination Count | <= 1 | <= 1 |

附加门槛：
- redline 必须 `BLOCKER=0`。
- `mvn test` 必须通过。
- 旧题集回归不得明显下降。
- 修复必须是通用能力修复，不得写题集、文件名、业务词、case id、答案值特判。

## 长期路线策略

长期目标不是无限修题，而是形成闭环：

```
public eval 发现问题 → 修通用能力 → 旧题集保护回归 → 新 public eval 泛化检查 → hidden eval 最终验收
```

### 三层目标

| 层级 | 目标 | 说明 |
|---|---|---|
| 短期 | terminal unit Phase 1A | 让 structured terminal assignment 生成独立 evidence unit，并进入 FTS / RRF。这是 **evidence 粒度建设**，不是 query fallback 补丁。 |
| 中期 | Query 主链复杂度治理 | 降低 AnswerGeneration 继承链深度，治理 `.contains()` 规则分流，减少 query fallback 主链的 selector / conclusion / snippet gate 式补丁累积。**独立开线，不与 terminal unit Phase 1A 并行改代码。** |
| 长期 | 5+2 eval 闭环 | 完成 5 套 public eval + 1-2 套 hidden eval，形成持续泛化验收能力。 |

### 关键原则

- **public eval**：用于暴露能力缺口、驱动通用能力修复。每套设计聚焦一个领域维度（结构化字段、标题搜索、多文档冲突、表格/CSV、跨文档组合等），通过后作为回归保护集保留。
- **hidden eval**：只用于最终泛化验收，AI 不得读取题目、答案、关键词、文件名、case id。hidden eval 不用于指导代码特判，只记录指标、失败类型分布和 gate 结果。
- **禁止无限修题**：禁止为了单题 PASS 修改题集预期或验收口径；修复必须是通用能力修复，不得写题集、文件名、业务词、case id、答案值特判。
- **禁止 query fallback 叠 gate**：禁止继续在 query fallback 主链叠加 selector / conclusion / snippet gate 来追 fresh eval 结构化题通过；应优先修 evidence unit 粒度。
- **terminal unit 是 evidence 粒度建设**：不是 query fallback 补丁，目标是让检索返回单字段证据而非整卡 sibling 折叠。
- **Query 复杂度治理独立开线**：不与 terminal unit Phase 1A 并行改代码；待 Phase 1A 完成并 agentD 验证通过后，单开一轮进行。
- **hidden eval 防污染**：structured terminal unit 的 `fieldLabel` / `fieldAliases` / `fieldDescription` 只能来自源文件内容与通用结构规则，不得来自 public / hidden 题集、标准答案、expected citation、case id 或 query 日志。

## Public Eval 规划

| 序号 | 领域 | 状态 | 当前结论 | 下一步 |
|---:|---|---|---|---|
| 1 | Kubernetes / 探针 / 事件响应 | 已闭环 | Q6 结构化 exact path 修复闭环；S2 chunk/anchor identity 修复已提交并归档 | 作为老题集回归保护 |
| 2 | 校园实验室安全 / 设备借用 | 进行中 | 正式基线仍以 `acceptance-report.md` 为准，当前未通过；结构化 terminal value 题 `FQ3/FQ4/FQ6/FG1/FG2` 三轮 query fallback 实验后仍为 `0/5 PASS` | 转向 compile/index 层 structured terminal assignment evidence unit materialization，第一阶段先生成 terminal units 并进入 FTS 检索 |
| 3 | 采购合同 / 售后 SLA / 付款条款 | 待设计 | 覆盖合同 PDF、金额、期限、责任方、多文档冲突 | 第 2 套稳定后设计 |
| 4 | 医疗设备维护 / 巡检 / 故障工单 | 待设计 | 覆盖表格、CSV、流程、等级、拒答 | 第 3 套后设计 |
| 5 | 教学课程 / 考务安排 / 学籍规则 | 待设计 | 覆盖规章制度、结构化配置、跨文档组合、角色权限 | 第 4 套后设计 |

## 当前进度

### Public Eval 1

| 项 | 状态 |
|---|---|
| 资料与题集 | `docs/test/knowledge-base-e2e/` |
| 关键修复 | Q6 exact path / sibling 字段；S2 chunk/anchor identity |
| 最新代码提交 | `4d5e8bc`、`aca4302` |
| 结论 | 作为后续回归保护集，不再围绕该题集继续叠加规则 |

### Public Eval 2

| 项 | 状态 |
|---|---|
| 资料包 | `docs/test/knowledge-base-e2e/fresh-eval-2026-05/` |
| 设计报告 | `docs/test/knowledge-base-e2e/fresh_eval_design_report.md` |
| 生成报告 | `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_assets_generation_report.md` |
| 验收报告 | `docs/test/knowledge-base-e2e/fresh-eval-2026-05/acceptance-report.md` |
| 根因报告 | `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_root_cause_analysis_report.md` |
| 三轮 query fallback 实验 | structured fact terminal binding / selector gate / conclusion gate 均服务级失败，`FQ3/FQ4/FQ6/FG1/FG2 = 0/5 PASS` |
| terminal unit 设计 | `docs/test/knowledge-base-e2e/fresh-eval-2026-05/structured_terminal_evidence_unit_materialization_design_report.md` |
| redline | `BLOCKER=0`，`REVIEW=2030`，`ALLOWLIST=259` |
| mvn test | `921/0/0` |
| 导入状态 | 5 份资料均 `SUCCEEDED` |
| 验收结论 | 未通过；正式指标仍以 `acceptance-report.md` 为准 |

失败桶：

| 桶 | Case | 归因 | 下一步 |
|---|---|---|---|
| 结构化字段回答 | `FQ3`、`FQ4`、`FQ6`、`FG1`、`FG2` | exact `FACT_ENUM` 已召回，问题已从 retrieval 召回转向 evidence unit 粒度；整张 FACT_CARD 内多个 sibling 共用卡级身份和分数，fallback 容易选中 `type/name/return_policy` 等近邻字段 | 不再继续叠 query fallback selector/conclusion/snippet gate；下一步做 terminal unit 第一阶段：生成 terminal units 并进入 FTS 检索 |
| 标题/弱标题搜索 | `FS1`、`FS2`、`FS3` | sourceTitle 身份稳定性不足；弱标题与 representativeTitle 未物化为独立标题身份 | 后续单独修，预计需清库重建或重建索引 |

失败实验归档：

| 方向 | 结果 | 结论 |
|---|---|---|
| structured fact terminal binding | 服务级 `0/5 PASS` | 不建议提交对应代码 |
| selector gate | 服务级 `0/5 PASS`，FACT_CARD 未进入最终 answer / citation | 不建议提交对应代码 |
| conclusion gate | 服务级 `0/5 PASS`，FACT_CARD 已召回但仍选错 sibling | 不建议提交对应代码 |

## 当前下一步

| 顺序 | Agent | 动作 | 是否改代码 | 是否清库 |
|---:|---|---|---|---|
| 1 | agentA | terminal unit 第一阶段：从 FACT_ENUM / key_value_list / path-aware items 生成 terminal units，并接入 FTS 检索；RRF 使用 unit identity，避免同卡 sibling 折叠 | 是，compile/index/检索最小范围 | 视 schema / 索引实现需要 |
| 2 | agentD | 先验证 terminal unit 是否进入 topK 且 `FQ3/FQ4/FQ6/FG1/FG2` answer claim 命中目标 unit；通过后再跑完整 Public Eval 2 | 否，除报告 | 视实现层级 |
| 3 | agentD | 回归 Public Eval 1 Q6 terminal field alias / path exact lookup 与 S2 chunk/anchor identity 保护场景 | 否，除报告 | 视实现层级 |
| 4 | agentB | 若 terminal unit 仍失败，只读定位 unit 生成、FTS 召回、RRF 身份或 citation binding 哪一层失效 | 否 | 否 |
| 5 | agentA | 标题/anchor/representativeTitle 物化或搜索身份问题另开独立轮次处理 | 是，需另开一轮 | 可能需要 |

## Query 主链复杂度治理（独立线）

此线独立于 terminal unit Phase 1A，**不与终端单价段并行改代码**。启动条件：terminal unit Phase 1A 完成并 agentD 验证通过后，单开一轮进行。

### 治理目标

| 方向 | 问题 | 目标 |
|---|---|---|
| AnswerGeneration 继承链 | 当前继承深度大，新增行为靠子类重写，回归面广 | 降低继承深度，收敛为显式策略/组合模式 |
| `.contains()` 规则分流 | 大量分支靠字符串 contains 判断分流，可维护性差 | 收敛为显式类型/枚举/策略分发 |
| query fallback 补丁累积 | selector / conclusion / snippet gate 式补丁持续叠加 | 冻结 query fallback 主链，不再接受新 gate 式补丁 |

### 治理节奏

1. terminal unit Phase 1A 完成且 agentD 验证通过后，单开治理轮次。
2. 先做只读审计：列出 AnswerGeneration 继承链全图、所有 `.contains()` 分支点、所有 fallback gate。
3. 按影响面从窄到宽逐步重构，每步必须通过旧题集回归。
4. 重构完成后，作为后续所有 eval 修复的基线。

## Hidden Eval 规则

- Hidden eval 不能放入 `docs/test/**` 或任何 AI 可读仓库路径。
- Hidden eval 不得在报告中泄露题目、标准答案、关键词、文件名、case id、expected citation。
- Hidden eval 结果只记录指标、失败类型分布和是否通过 gate。
- 若 hidden eval 明显低于 public eval，优先排查 public eval 过拟合或测试集污染。
- Hidden eval 不用于直接指导代码写特判，只用于最终泛化验收。
- structured terminal unit 的 `fieldLabel` / `fieldAliases` / `fieldDescription` 只能来自源文件内容与通用结构规则，不得来自 public / hidden 题集、标准答案、expected citation、case id 或 query 日志。

## 红线

- 禁止把 public / hidden eval 的题目、答案、关键词、文件名、case id 写入 `src/main/java/**`、`src/main/resources/**`、prompt、配置、SQL、脚本、allowlist。
- 禁止为了单题 PASS 修改题集预期或验收口径。
- 禁止同一轮同时修多个根因。
- 禁止在 Java 主链硬编码中文字段语义；如确需字段语义配置，必须短小、通用、可审计，并单独评审。
- 禁止继续在 query fallback 主链叠加 selector / conclusion / snippet gate 来追 fresh eval 结构化题通过；应优先修 evidence unit 粒度。
- 禁止 terminal unit Phase 1A 与 Query 复杂度治理并行改代码；两条线必须串行，治理线待 Phase 1A 验收通过后单开。
- terminal unit 是 evidence 粒度建设，不是 query fallback 补丁；不得在 terminal unit 实现中向 query fallback 主链追加新 gate。
- redline `BLOCKER>0` 时停止准确率调优。
- `mvn test` 失败时停止业务 eval。

## 更新规则

- 每新增一套 public eval，先补本文件的规划与状态。
- 每次 agentD 验收后，更新对应指标、失败桶和下一步。
- 每次 agentA 修复后，只在验证通过后更新状态为闭环。
- hidden eval 只更新指标和 gate 结果，不记录题目细节。
