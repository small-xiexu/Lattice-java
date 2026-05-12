# 过期报告清理结果报告

| 项目 | 值 |
|---|---|
| 清理时间 | 2026-05-12 |
| 清理前 untracked report 文件数 | 22 |
| 清理后 untracked report 文件数 | 2 |

---

## 1. 已删除（22 个）

| 文件名 |
|---|
| `admin_governance_isolation_fix_result_report.md` |
| `answer_generation_capability_evidence_selection_analysis_report.md` |
| `answer_generation_capability_evidence_selection_fix_result_report.md` |
| `answer_generation_d1_enumeration_candidate_design_report.md` |
| `answer_generation_d1_focused_chinese_label_fix_result_report.md` |
| `answer_generation_d2_multi_focus_source_analysis_report.md` |
| `answer_generation_d2_multi_focus_source_fix_result_report.md` |
| `answer_generation_flow_snippet_selection_analysis_report.md` |
| `answer_generation_flow_snippet_selection_fix_result_report.md` |
| `answer_generation_focused_field_format_fix_result_report.md` |
| `answer_generation_group_d_snippet_coverage_analysis_report.md` |
| `answer_generation_remaining_15_grouped_analysis_report.md` |
| `answer_generation_remaining_7_grouped_analysis_report.md` |
| `answer_generation_setup_checklist_fix_result_report.md` |
| `answer_generation_single_article_selection_analysis_report.md` |
| `answer_generation_single_article_selection_fix_result_report.md` |
| `answer_generation_single_article_selection_runtime_score_report.md` |
| `answer_generation_spreadsheet_field_definitions_fix_result_report.md` |
| `answer_generation_structured_table_current_values_fix_result_report.md` |
| `current_workspace_gate_after_structured_table_fix_diagnosis_report.md` |
| `query_graph_topk_citation_repair_fix_result_report.md` |
| `redline_multifocus_separator_fix_result_report.md` |

## 2. 已保留（3 个）

| 文件名 | 保留原因 |
|---|---|
| `special_cases_report.md` | redline 追踪文件，已纳入版本控制 |
| `answer_generation_structured_json_fragment_filter_fix_result_report.md` | 最新有效修复报告 |
| `answer_generation_remaining_3_grouped_analysis_report.md` | 当前剩余失败的分析基准 |

## 3. 合规检查

| 检查项 | 状态 |
|---|---|
| 是否修改源码 | ✅ 否 |
| 是否修改测试 | ✅ 否 |
| 是否修改配置 | ✅ 否 |
| 是否修改脚本 | ✅ 否 |
| 是否修改 `special_cases_report.md` | ✅ 否 |

## 4. 下一步建议

当前剩余 2 个失败，建议下一步只处理 `shouldKeepUnsupportedDetailCaveatInAnsweredDiffQuestion`。
