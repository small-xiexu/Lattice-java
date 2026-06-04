# Terminal Fix 中间报告清理结果

执行时间：2026-06-05
执行人：agentC（文档/报告治理 Agent）
执行依据：`terminal_fix_report_cleanup_plan.md`（二阶修订版）

## 1. 执行前检查

| 检查项 | 结果 |
|---|---|
| 未跟踪 `.md` 总数 | 49（与 plan 一致） |
| Commit Candidates 存在性 | 33/33 ✓ |
| Delete Candidates 存在性 | 13/13 ✓ |
| 二阶引用确认 | 零命中 ✓ |
| `special_cases_report.md` 未暂存 | ✓ |
| `docs/模型绑定配置参考.md` 未暂存 | ✓ |

## 2. 删除清单（13 个）

### 临时 trace（7 个）

1. `fq4_fallback_candidate_score_trace_report.md`
2. `fq4_fg1_default_reviewmode_fallback_trace_report.md`
3. `fq4_fg1_fallback_candidate_score_runtime_trace_report.md`
4. `fq4_fg1_terminal_builder_runtime_trace_report.md`
5. `fq4_fg1_terminal_builder_slf4j_trace_fix_report.md`
6. `fq4_fg1_terminal_candidate_runtime_score_trace_report.md`
7. `fq4_fg1_terminal_candidate_runtime_score_trace_actual_report.md`

### 被后续修复完全覆盖的 fix result（3 个）

8. `fq4_fg1_terminal_channel_candidate_supply_fix_result_report.md`
9. `fq4_field_alias_fix_restore_result_report.md`
10. `fq4_field_alias_fix_comment_cleanup_report.md`

### 被提交最终 gate 覆盖的 runtime gate / verification（3 个）

11. `fg1_field_alias_enricher_bootstrap_guard_runtime_gate_report.md`
12. `fq4_fg1_terminal_candidate_supply_root_cause_analysis_report.md`
13. `fq4_fg1_terminal_entity_context_runtime_gate_report.md`

## 3. 归档提交清单（33 个）

### 根因分析报告（6 个）

1. `fg1_terminal_unit_consumption_root_cause_analysis_report.md`
2. `fg1_field_alias_enricher_candidate_supply_readonly_analysis_report.md`
3. `fq4_fg1_fallback_runtime_breakpoint_analysis_report.md`
4. `fq4_fg1_multi_target_terminal_conclusion_analysis_report.md`
5. `fq4_fg1_terminal_entity_context_metadata_design_report.md`
6. `fq4_fg1_terminal_channel_limit_root_cause_analysis_report.md`

### fix result 报告（8 个）

7. `fg1_field_alias_enricher_bootstrap_guard_fix_result_report.md`
8. `fg1_fq4_conclusion_builder_terminal_unit_consumption_fix_result_report.md`
9. `fg1_ftmc_zero_builder_fix_result_report.md`
10. `fg1_qf_false_builder_fix_result_report.md`
11. `fg1_raw_query_entity_context_match_fix_result_report.md`
12. `fq4_field_alias_json_array_consumption_fix_result_report.md`
13. `fq4_terminal_tie_break_fix_result_report.md`
14. `fq4_fg1_terminal_channel_candidate_supply_fix_revision_report.md`

### runtime gate / verification 报告（16 个）

15. `fg1_raw_query_entity_context_match_runtime_gate_report.md`
16. `fg1_fq4_conclusion_builder_terminal_unit_consumption_verification_report.md`
17. `fg1_ftmc_zero_builder_runtime_gate_report.md`
18. `fg1_qf_false_builder_runtime_gate_report.md`
19. `fg1_field_alias_binding_runtime_verification_report.md`
20. `fq4_tie_break_runtime_gate_report.md`
21. `fq4_fg1_controlled_fallback_candidate_score_trace_report.md`
22. `fq4_fg1_field_alias_enricher_runtime_audit_report.md`
23. `fq4_fg1_forced_restart_runtime_verification_report.md`
24. `fq4_fg1_terminal_builder_slf4j_trace_runtime_gate_report.md`
25. `fq4_fg1_terminal_channel_candidate_supply_runtime_gate_report.md`
26. `fq4_field_alias_fix_final_runtime_gate_report.md`
27. `fq4_field_alias_fix_full_public_eval_gate_report.md`
28. `fq4_field_alias_json_array_consumption_verification_report.md`
29. `fq4_fg1_multi_target_terminal_conclusion_fix_result_report.md`
30. `fq4_fg1_multi_target_terminal_context_guard_fix_result_report.md`

### 跨阶段 gate 报告（3 个）

31. `fq4_fg1_terminal_entity_context_metadata_fix_result_report.md`
32. `public_eval_gate_after_human_review_approval_report.md`
33. `two_public_eval_clean_schema_gate_report.md`

## 4. 保持未跟踪（2 个）

| 文件 | 原因 |
|---|---|
| `fg1_terminal_unit_current_breakpoint_analysis_report.md` | 含运行时数据库快照与断点现场；可能交叉后续 S2/FS2 分析 |
| `fresh_eval_post_cleanup_remaining_failure_analysis_report.md` | 跨阶段边界审查；不确定后续是否引用 |

## 5. 排除项声明

- `special_cases_report.md` — 未提交（redline 输出，永远排除）
- `docs/模型绑定配置参考.md` — 未提交（私有配置，永远排除）
- 未修改 `src/**`、`scripts/**`、`README.md`、`docs/quality-progress-and-lessons.md`
- 未修改四份核心流水线文档

## 6. 数量汇总

| 项目 | 数量 |
|---|---|
| 删除 | 13 |
| 归档提交 | 33 |
| 保持未跟踪 | 2 |
| 本 result + plan | 2 |
| **合计** | **50**（含已经存在的 plan） |
