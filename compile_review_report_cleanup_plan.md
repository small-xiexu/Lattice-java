# Compile Review 报告清理计划

制定时间：2026-05-17
制定 Agent：agentC
执行状态：**仅计划，不执行删除**

## 约束声明

- 本轮只输出计划，不删除任何文件。
- 不修改源码、测试、配置、脚本、台账、题集。
- 不提交代码。
- 实际删除动作必须在 agentA/agentD 当前收口完成后，由用户明确指示执行。

## 当前 compile review 报告清单（共 9 份）

| # | 文件 | Git 状态 | 类型 |
|---|---|---|---|
| 1 | `compile_review_governance_design_report.md` | 已提交 | 治理分析 |
| 2 | `compile_review_governance_next_steps_report.md` | 已提交 | 治理决策 |
| 3 | `compile_review_observability_fix_result_report.md` | 已提交 | 修复记录 |
| 4 | `compile_review_observability_pre_commit_quality_report.md` | 已提交 | 质量复核 |
| 5 | `compile_review_observability_quality_progress_update_report.md` | 已提交 | 台账更新（中间过程） |
| 6 | `compile_review_observability_verification_report.md` | 已提交 | 验证记录 |
| 7 | `compile_review_persist_gate_fix_result_report.md` | 未跟踪 | 修复记录 |
| 8 | `compile_review_persist_gate_runtime_verification_report.md` | 未跟踪 | 验证记录 |
| 9 | `compile_review_persist_visibility_governance_analysis_report.md` | 未跟踪 | 治理分析 |

## 分类一：提交前必须保留（5 份）

这些报告是当前分支代码变更的直接佐证，提交前 agentD 仍需引用。

| # | 文件 | 保留理由 |
|---|---|---|
| 3 | `compile_review_observability_fix_result_report.md` | 记录 observability 改了什么文件、redline/mvn test 结果、行为影响确认；提交时作为 fix 证据。 |
| 4 | `compile_review_observability_pre_commit_quality_report.md` | agentD 的提交前质量复核结论；提交时必须附。 |
| 6 | `compile_review_observability_verification_report.md` | agentD 的 API + UI 验证记录；证明后台可观测性真实可见。 |
| 7 | `compile_review_persist_gate_fix_result_report.md` | 记录 persist gate 修复了什么、行为影响确认；提交时作为 fix 证据。 |
| 8 | `compile_review_persist_gate_runtime_verification_report.md` | agentD 的 persist gate 运行时验证记录；证明修复真实生效。 |

## 分类二：提交后可删除（1 份）

内容已被上级文档完全吸收，无独立保留价值。

| # | 文件 | 删除理由 |
|---|---|---|
| 5 | `compile_review_observability_quality_progress_update_report.md` | 纯 agentC 台账更新过程记录。所有关键结论已写入 `docs/quality-progress-and-lessons.md`（当前阶段、Gate 表、已验证结论、踩坑记录、下一步计划）。报告本身无增量信息，提交后即为冗余。 |

## 分类三：必须长期保留作为治理决策依据（3 份）

这些是 compile review 治理体系的决策基础，后续启用 LLM reviewer 或调整 persist/query 可见性时仍需引用。

| # | 文件 | 保留理由 |
|---|---|---|
| 1 | `compile_review_governance_design_report.md` | 分析了 draft→review→persist→query 完整链路，确认当前无 draft 直接进入 query 可见面的实际样本；是"当前不需要紧急修 query 可见性"结论的决策依据。 |
| 2 | `compile_review_governance_next_steps_report.md` | 设计了方案 A（后台可观测性）/ B（persist gate）/ C（query 可见性）三步走路线；当前方案 A 和 B 已落地，方案 C 尚未执行，后续决策仍需引用此报告。 |
| 9 | `compile_review_persist_visibility_governance_analysis_report.md` | 分析了 persist 可见性治理的完整链路和风险点；是 persist gate 修复的决策依据，后续如需调整可见性过滤仍需引用。 |

## 执行时序建议

```
当前状态：agentA persist gate 修复代码未提交，agentD 复核未完成
    │
    ▼
第一步（当前阻塞）：agentD 完成 persist gate 最终复核
    │
    ▼
第二步：提交代码（含分类一的 5 份报告作为佐证）
    │
    ▼
第三步：删除分类二的 1 份报告（#5）
    │
    ▼
第四步：分类三的 3 份报告随分支长期保留，后续启用 LLM reviewer 时作为决策引用
```

## 删除命令（仅备忘，不执行）

```bash
# 提交后执行（当前不执行）：
rm compile_review_observability_quality_progress_update_report.md
```

## 确认清单

- [x] 是否修改代码：**否**
- [x] 是否删除文件：**否**（本轮仅计划）
- [x] 是否修改 docs/quality-progress-and-lessons.md：**否**
- [x] 是否修改 special_cases_report.md：**否**
- [x] 是否修改 scripts/**：**否**
- [x] 是否修改 eval/baseline 题集：**否**
- [x] 是否提交代码：**否**
