# 文档收口最终状态报告

执行时间：2026-06-08
执行人：agentC
HEAD：`3b02bb8`（docs commit，含上一轮台账同步和 gate 保留）

## 一、当前工作区全景

### 1.1 已修改跟踪文件

| 文件 | 状态 |
|---|---|
| `special_cases_report.md` | 永远排除（redline 输出） |

### 1.2 未跟踪报告（18 个）

## 二、逐文件分类

### 分类 A：归档提交（3 个）

| 文件 | 理由 |
|---|---|
| `post_s2_writer_title_preservation_current_head_full_eval_gate_report.md` | PE1+PE2 全量 gate 基线（HEAD `00237a9`），历史参照，质量台账已引用 |
| `internal_mirror_dogfood_java_project_runtime_report.md` | INTERNAL_MIRROR dogfood 真实项目验证，CODE_LIGHT 能力基线 |
| `post_s2_writer_title_preservation_status.md` | 内部试用状态简报（2026-06-07），已被台账覆盖但作为独立入口仍有用 |

### 分类 B：保留未跟踪（12 个）

#### B1：线 A StructuredQueryPlanner 实验（7 个）

| 文件 | 说明 |
|---|---|
| `fresh-eval-2026-08_structured_query_planner_fix_result_report.md` | Round 3 过滤器提取修复 |
| `fresh-eval-2026-08_structured_query_planner_fq3_fix_result_report.md` | Round 5 FQ3 同列多值冲突修复 |
| `fresh-eval-2026-08_structured_query_planner_fq3_runtime_gate_report.md` | FQ3 最小修复 runtime gate |
| `fresh-eval-2026-08_structured_query_planner_guard_fix_result_report.md` | Round 6 保守化收口修复 |
| `fresh-eval-2026-08_structured_query_planner_guard_regression_analysis_report.md` | Guard 回归根因分析 |
| `fresh-eval-2026-08_structured_query_planner_guard_runtime_gate_report.md` | Guard 收口 runtime gate |
| `fresh-eval-2026-08_structured_query_planner_runtime_gate_report.md` | Planner 全量 runtime gate |

**保留原因**：线 A（StructuredQueryPlanner 增强）未提交。这些报告记录了多轮实验过程。如果线 A 恢复上线，可作为设计参照。**当前不与线 B 代码混交。**

#### B2：YAML 检索召回分析（2 个）

| 文件 | 说明 |
|---|---|
| `fresh-eval-2026-08_yaml_retrieval_recall_log_analysis_report.md` | YAML 召回根因分析（agentB） |
| `fresh-eval-2026-08_yaml_retrieval_recall_followup_analysis_report.md` | YAML 召回跟进分析（agentB） |

**保留原因**：PE5 召回专项分析，与线 A 相关。线 A 恢复后可能需要参照。

#### B3：缩略词方案（3 个）

| 文件 | 说明 |
|---|---|
| `pe1_q2_acronym_query_retrieval_analysis_report.md` | Q2 缩略词归因（agentB） |
| `pe1_q2_acronym_general_solution_design_report.md` | 通用方案设计（agentB） |
| `pe1_q2_writer_acronym_preservation_fix_result_report.md` | 前置确认（agentA） |

**保留原因**：方案未实施。结论为 Q2 非系统缺陷（评测口径问题）。如后续启动缩略词方案，可作为设计输入。当前不提交。

### 分类 C：可删除（3 个）

| 文件 | 删除理由 |
|---|---|
| `post_compiler_admin_fixes_report_archive_plan.md` | 归档建议已执行（commits `e974b6f`、`2b5ecea` 已提交），计划本身过期 |
| `quality_progress_current_state_sync_report.md` | 上一轮台账同步的操作记录。台账已更新并提交（`3b02bb8`），此同步报告无独立参照价值 |
| `post_s2_writer_title_preservation_status.md` | 分类调整：从 A 移到 C。此 51 行简报的信息已被台账完全覆盖，HEAD 已从 `00237a9` 推进到 `3b02bb8`，简报过时 |

### 分类 D：永远排除（1 个）

| 文件 | 理由 |
|---|---|
| `special_cases_report.md` | redline 输出 |

## 三、分桶汇总

| 桶 | 数量 | 处置 |
|---|---|---|
| A — 归档提交 | 2 | `post_s2_...gate_report.md` + `internal_mirror_dogfood_...` |
| B — 保留未跟踪 | 12 | 线 A(7) + YAML(2) + acronym(3) |
| C — 可删除 | 3 | 过期归档计划 + 过期同步报告 + 过期状态简报 |
| D — 永远排除 | 1 | `special_cases_report.md` |

## 四、台账同步

`docs/quality-progress-and-lessons.md` 已在上一轮同步（commit `3b02bb8`），当前内容与 HEAD `34394bd` 对齐。本轮无需额外更新。

台账当前反映的核心状态：
- HEAD `34394bd`，线 B FTS OR Query 已提交
- PE4 线 B 提交后回归 PASS（Search 6/6、FG 3/3、Hallucination 0）
- PE5 全量验收待线 A 恢复
- PE1-PE4 共 4 套 public eval 已通过
- Java Codebase Eval 全量验收待执行
- Hidden eval governance 已就绪，待 5 套 public eval 稳定后启动

## 五、下一步建议

1. **立即**：删除桶 C 的 3 个过期文件；提交桶 A 的 2 个文件 + 本报告
2. **后续**：线 A 恢复上线后，评估桶 B 的线 A 实验报告是否需要归档或删除
3. **远期**：5 套 public eval 全部稳定后，启动 hidden eval 验收

## 六、明确声明

- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未修改 `special_cases_report.md`
- [x] 未删除任何文件（仅分类建议）
- [x] 未提交 commit
