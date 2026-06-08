# PE5 报告清理与状态同步结果

执行时间：2026-06-08
执行人：agentC
执行依据：`pe5_report_cleanup_and_status_sync_plan.md`
HEAD：`34394bd fix(search): use token OR query for FTS channels`

## 1. 桶 C 删除清单（12 个已删除）

### C1 — Evidence Packing 已回滚实验（5 个）
- `fresh-eval-2026-08_answer_generation_evidence_packing_design_report.md`
- `fresh-eval-2026-08_answer_generation_evidence_packing_fix_result_report.md`
- `fresh-eval-2026-08_answer_generation_evidence_packing_runtime_gate_report.md`
- `fresh-eval-2026-08_answer_generation_evidence_packing_rollback_result_report.md`
- `fresh-eval-2026-08_answer_generation_evidence_packing_post_rollback_runtime_gate_report.md`

### C2 — PE5 中间 gate 报告（3 个）
- `fresh-eval-2026-08_runtime_gate_report.md`
- `fresh-eval-2026-08_runtime_gate_failure_analysis_report.md`
- `fresh-eval-2026-08_runtime_gate_recheck_report.md`

### C3 — Structured Source Recall 中间实验（4 个）
- `fresh-eval-2026-08_structured_source_recall_fix_result_report.md`
- `fresh-eval-2026-08_structured_source_recall_residual_fix_result_report.md`
- `fresh-eval-2026-08_structured_source_recall_residual_root_cause_analysis_report.md`
- `fresh-eval-2026-08_structured_source_recall_runtime_gate_report.md`

## 2. 桶 A 保留文件（未被删除）

- PE5 资产包：`fresh-eval-2026-08/` 目录 + `_design_report.md` + `_build_report.md` + `_question_set_consistency_fix_result_report.md`
- 线 B gate：`pe5_line_b_fts_or_query_isolated_gate_report.md` + `_pre_commit_quality_review_report.md` + `_clean_rebuild_gate_report.md`
- PE4 回归：`pe4_post_line_b_fts_or_query_regression_gate_report.md` + `_clean_rebuild_long_wait_gate_report.md`
- 治理：`hidden_eval_governance_protocol.md` + `hidden_eval_gates/` + `hidden_eval_failure_abstraction_analysis_report.md`
- 基线：`java_codebase_public_eval_full_runtime_gate_report.md` + `pe5_workspace_cleanup_plan.md`

## 3. 桶 B 暂不提交文件（未被删除）

- 线 A StructuredQueryPlanner 实验报告 7 个
- YAML retrieval analysis 2 个

## 4. `docs/quality-progress-and-lessons.md` 更新摘要

| 位置 | 更新 |
|---|---|
| 时间戳 | → 2026-06-08, HEAD `34394bd` |
| 当前阶段 | 新增线 B 已提交 + PE4 线 B 回归 PASS；修正 PE5 条目（删除过期报告引用，替换为当前状态） |
| 工作区未提交 | 更新为当前实际未跟踪文件清单 |
| 当前 Gate | 更新 redline/mvn test HEAD 引用；新增线 B gate + PE4 回归 gate 条目 |

## 5. 明确排除文件（未提交、未删除）

| 文件 | 状态 |
|---|---|
| `special_cases_report.md` | 永远排除 |
| 桶 B（线 A/YAML 分析 9 个） | 保留未跟踪 |
| 桶 D（acronym×3 等 6 个） | 保留未跟踪 |

## 6. 明确声明

- [x] 删除 12 个桶 C 过期报告
- [x] 更新 `docs/quality-progress-and-lessons.md`
- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未恢复线 A 或继续修功能
- [x] 未提交 commit
- [x] 桶 A / 桶 B / 桶 D 文件未被误删
