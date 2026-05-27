# Markdown 文档清理审计报告

**审计时间**：2026-05-27
**审计 Agent**：agentC（文档治理 Agent）
**审计模式**：只读盘点 + 引用检查 + 逐文件分类
**约束声明**：本轮仅分类，未修改、未删除、未移动、未 stage、未 commit、未 push。

---

## 0. 审计范围与排除清单

### 审计范围

| 范围 | 文件数 |
|---|---|
| 根目录 `*.md`（排除受保护文件） | 64 |
| `docs/**/*.md`（排除受保护文件） | 41 |
| `archived_reports/`（参考审计） | 103 |
| **合计** | **208** |

### 明确排除（不审计、不触碰）

| 文件 | 原因 |
|---|---|
| `README.md`、`AGENTS.md`、`CLAUDE.md` | 项目入口，永远保留 |
| `docs/quality-progress-and-lessons.md` | 活跃质量台账 |
| `docs/项目全流程真实验收手册.md` | 项目级手册 |
| `docs/项目启动配置清单.md` | 启动手册 |
| `docs/模型绑定配置参考.md` | 私有配置，永远排除 |
| `special_cases_report.md` | redline 输出，永远排除 |

---

## 1. 分类标准

| 分类 | 定义 |
|---|---|
| **DELETE** | 已被后续报告完全替代、无反向引用、一次性中间产物、过期快照 |
| **ARCHIVE** | 有审计/历史价值，但非当前入口；或仍被引用但不活跃 |
| **KEEP** | 当前台账引用、最新验证报告、设计总纲、计划文档、项目级手册 |

### 引用检查方法

对每个候选文件执行 `rg -l --fixed-strings <filename> .`：
- 排除 `docs/reports/markdown_docs_consolidation_design_report.md` 的引用（该报告仅为迁移清单，不构成实质引用关系）
- 排除文件自引用
- 重点关注 `docs/quality-progress-and-lessons.md` 的引用（质量台账引用 = 当前证据链）

---

## 2. DELETE 候选清单（共 6 个）

### 2.1 被 fix_revision 替代且无外部引用的 fix_result（4 个）

这些文件均有对应的 `_fix_revision_report.md`（内容更正的最终版），且除 fix_revision 自身外无其他文件引用：

| # | 文件 | 替代者 | 引用检查 |
|---|---|---|---|
| 1 | `admin_article_detail_review_metric_ux_fix_result_report.md` | `admin_article_detail_review_metric_ux_fix_revision_report.md` | 仅 fix_revision 引用 |
| 2 | `admin_governance_metric_action_entry_fix_result_report.md` | `admin_governance_metric_action_entry_fix_revision_report.md` | 仅 fix_revision 引用 |
| 3 | `admin_governance_metric_explainer_panel_fix_result_report.md` | `admin_governance_metric_explainer_panel_fix_revision_report.md` | 仅 fix_revision 引用 |
| 4 | `admin_history_modal_hotspot_copy_fix_result_report.md` | `admin_history_modal_hotspot_copy_fix_revision_report.md` | 仅 fix_revision 引用 |

**删除理由**：fix_revision 是经过修正的最终版本，完全覆盖 fix_result 的内容。无其他文件引用这些旧版 fix_result，删除不会产生断裂引用。

### 2.2 过期一次性快照（2 个）

| # | 文件 | 删除理由 | 引用检查 |
|---|---|---|---|
| 5 | `current_runtime_version_check_report.md` | 运行时版本检查的一次性快照，日期 2026-05-24，已无时效性 | 零引用 |
| 6 | `current_workspace_pending_fixes_status_update_report.md` | 工作区待修复项的状态快照，`phase_current_workspace_pending_fixes.md`（ARCHIVE）已覆盖同主题 | 零引用（仅 consolidation design report） |

---

## 3. ARCHIVE 候选清单

### 3.1 根目录 — 被替代但有外部引用的 fix_result（4 个）

这些文件被 fix_revision 替代，但因其他报告仍引用它们，不能直接删除（引用方需先更新指向 fix_revision）：

| # | 文件 | 引用方 | 替代者 |
|---|---|---|---|
| 1 | `admin_article_detail_keyword_metadata_display_fix_result_report.md` | `admin_processing_task_status_and_history_design_report.md`、`phase_current_workspace_pending_fixes.md` | `_fix_revision_report.md` |
| 2 | `admin_processing_history_tab_fix_result_report.md` | `admin_processing_history_tab_design_report.md` | `_fix_revision_report.md` |
| 3 | `admin_review_queue_issue_explanation_fix_result_report.md` | `phase_current_workspace_pending_fixes.md` | `_fix_revision_report.md` |
| 4 | `query_citation_quality_terminal_fallback_fix_result_report.md` | `query_fallback_citation_quality_root_cause_report.md` | `_fix_revision_report.md` |

**归档理由**：内容已被 fix_revision 覆盖，但外部引用尚未更新。如果将来更新这些引用指向 fix_revision，这 4 个文件可降级为 DELETE。

### 3.2 根目录 — 其他过程报告（54 个）

所有其他根目录过程报告（admin 28 个 + compile 14 个 + query 5 个 + phase 4 个 + e2e/rebuild 3 个）：

- **admin `_fix_revision_report`（7 个）**：最终修正版本，有审计价值
- **admin `_runtime_verification_report`（1 个）**：运行时验证，有审计价值
- **admin `_design_report`（4 个）**：设计文档，有历史参考价值
- **admin 独立 fix_result（11 个）**：无 revision 的单一修复报告
- **compile `_fix_result_report`（几有 runtime_verification 配套）（5 个）**：与验证报告配套的修复证据
- **compile `_runtime_verification_report`（5 个）**：运行时验证证据
- **compile 独立分析报告（2 个）**：`compile_review_fix_loop_performance_analysis_report.md`、`compile_fixer_payload_slimming_fix_result_report.md`
- **compile 阶段状态报告（2 个）**：`compile_review_phase_status.md`、`compile_review_phase_report_cleanup_result.md`（被多个 compile 报告引用）
- **query 独立报告（5 个）**：fix_revision、runtime_verification、root_cause、completeness_analysis、multi_point_expansion
- **phase 验收报告（2 个）**：`phase_compile_query_rebuild_acceptance_report.md`、`phase_compile_query_stage_acceptance_report.md`
- **phase 工作区报告（2 个）**：`phase_current_workspace_existing_cases_acceptance_report.md`、`phase_current_workspace_pending_fixes.md`（被多个 admin 报告引用）
- **e2e/rebuild（3 个）**：`e2e_clean_rebuild_suite_creation_report.md`、`full_rebuild_e2e_validation_asset_design_report.md`（被 `docs/test/e2e-clean-rebuild-suite.json` 引用）、`full_rebuild_e2e_validation_runtime_report.md`
- **一次性报告（2 个）**：`agents_md_runtime_policy_wording_fix_report.md`、`review_queue_12_items_manual_triage_report.md`
- **运行时快照（1 个）**：`current_workspace_split_pre_commit_quality_report.md`（被 `query_citation_quality_terminal_fallback_fix_revision_report.md` 引用）

**归档理由**：均未被 quality-progress 引用，也非当前开发入口。但作为历史证据链的一部分，有保留价值。建议按 `docs/reports/markdown_docs_consolidation_design_report.md` 方案迁移到 `docs/reports/` 子目录。

### 3.3 docs/ — 历史参考文档（3 个）

| 文件 | 引用情况 | 归档理由 |
|---|---|---|
| `docs/卡券三期-迁移方案.md` | 被 23 处引用（均为根目录/archived_reports 旧报告） | 历史迁移方案，被旧报告大量引用，但非当前入口 |
| `docs/数据库表结构详解.md` | 被 README.md、plans 引用（6 处） | 数据库参考文档，仍有被 README 引用 |
| `docs/文档识别与OCR运行态说明.md` | 被 e2e 报告引用（9 处） | OCR 说明，被旧报告引用 |

### 3.4 docs/test/ — 未被 quality-progress 引用的测试报告（7 个）

| 文件 | 引用检查 | 归档理由 |
|---|---|---|
| `docs/test/admin/admin_api_ui_bucket_verification_report.md` | 零引用 | Admin 桶验证，未被台账引用 |
| `docs/test/knowledge-base-e2e/fresh_eval_design_report.md` | 零引用 | 评测设计报告，未被引用 |
| `docs/test/knowledge-base-e2e/q6_structured_fact_path_fix_result_report.md` | 仅被 q6_end_to_end 引用 | Q6 结构化事实路径修复 |
| `docs/test/knowledge-base-e2e/q6_terminal_field_alias_verification_report.md` | 零引用 | Q6 terminal field 最终验证 |
| `docs/test/knowledge-base-e2e/s2_anchor_title_search_analysis_report.md` | 零引用 | S2 分析报告 |
| `docs/test/knowledge-base-e2e/s2_chunk_anchor_identity_verification_report.md` | 零引用 | S2 验证报告 |
| `docs/test/knowledge-base-e2e/sources/01_markdown/probe-and-incident-operations.md` | 被多个 Q6/S2 报告引用（20 处） | 知识库测试样本，有参考价值 |

### 3.5 archived_reports/（参考标注）

`archived_reports/` 目录已有 103 个文件处于归档状态。其中：
- **21 个被 quality-progress 引用**（见下方 KEEP 清单），属于活跃证据链，建议保留在当前位置
- **约 82 个未被 quality-progress 引用**，部分被根目录 compile 报告引用（通过 `compile_review_phase_report_cleanup_result.md`），其余无引用

对 archived_reports 的建议：**维持现状**，不增删。这些文件已在归档目录中，不阻塞根目录清理。

---

## 4. KEEP 清单

### 4.1 docs/plans/ — 实施计划（4 个）

| 文件 | 引用情况 |
|---|---|
| `docs/plans/2026-05-05-当前剩余工作总清单.md` | 被架构治理计划引用 |
| `docs/plans/2026-05-07-架构治理实施计划.md` | 零外部引用，但为活跃计划 |
| `docs/plans/2026-05-24-知识条目标题生成优化实施计划.md` | 被 title-generation 测试报告和 remaining_docs 引用 |
| `docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md` | 被大量 Q6/title-generation 报告引用 |

### 4.2 docs/ 项目指南（2 个）

| 文件 | 引用情况 |
|---|---|
| `docs/multi-agent-model-routing-guide.md` | 被 AGENTS.md 引用（9 处），Claude Code 自动加载 |
| `docs/oh-my-codex-agent-orchestration-guide.md` | 被多个 archived_reports 引用（3 处） |

### 4.3 docs/test/ — 被 quality-progress 引用的报告（13 个）

这些文件出现在质量台账 `docs/quality-progress-and-lessons.md` 中，是当前证据链的组成部分：

| 文件 | 所属桶 |
|---|---|
| `docs/test/knowledge-base-e2e/q6_end_to_end_verification_report.md` | Q6 |
| `docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_fix_result_report.md` | Q6 terminal field |
| `docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_fix_result_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_verification_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_fix_result_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_verification_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_runtime_trace_analysis_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_second_root_cause_analysis_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_fix_result_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_verification_report.md` | Q6 fallback |
| `docs/test/knowledge-base-e2e/s2_chunk_anchor_identity_fix_result_report.md` | S2 |
| `docs/test/knowledge-base-e2e/s2_title_anchor_search_root_cause_analysis_report.md` | S2 |
| `docs/test/remaining_docs_reports_commit_plan.md` | 文档审计 |

### 4.4 docs/test/ — 有持续引用价值的报告（8 个）

| 文件 | 保留理由 |
|---|---|
| `docs/test/knowledge-base-e2e/README.md` | 知识库 e2e 入口，被 29 处引用 |
| `docs/test/knowledge-base-e2e/acceptance-report.md` | 验收报告，被 plans 和 Q6 报告引用 |
| `docs/test/knowledge-base-e2e/eval/question-set.md` | 评测问题集，仍在使用的测试资产 |
| `docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md` | Q6 根因分析，被 remaining_docs 和 terminal_field 引用 |
| `docs/test/knowledge-base-e2e/q6_readiness_port_analysis_report.md` | Q6 就绪分析，被 end_to_end 和 fallback 报告引用 |
| `docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md` | be4d216 审计链路 |
| `docs/test/llm/execution_llm_snapshot_pre_commit_verification_report.md` | be4d216 审计链路 |
| `docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md` | be4d216 审计链路 |

### 4.5 docs/test/title-generation/ — 标题生成测试资产（4 个）

| 文件 | 保留理由 |
|---|---|
| `docs/test/title-generation/title_generation_bucket_verification_report.md` | 桶验证报告，被 plans 引用 |
| `docs/test/title-generation/documentparse_metadata_bucket_verification_report.md` | documentparse 桶验证 |
| `docs/test/title-generation/title-generation-sample-set.md` | 测试样本集 |
| `docs/test/title-generation/title-profile-contract.md` | 画像契约 |

### 4.6 archived_reports/ — 被 quality-progress 引用的归档报告（21 个）

这些文件虽然已在 archived_reports 目录，但仍被质量台账引用，**不建议删除或移动**：

| 文件 |
|---|
| `compile_human_review_queue_pre_commit_quality_report.md` |
| `compile_review_default_llm_mode_fix_result_report.md` |
| `compile_review_default_llm_mode_runtime_verification_report.md` |
| `compile_review_entrypoint_loop_coverage_analysis_report.md` |
| `compile_review_llm_reviewer_fail_closed_fix_result_report.md` |
| `compile_review_llm_reviewer_fail_closed_verification_report.md` |
| `compile_review_llm_reviewer_small_flow_reverification_report.md` |
| `compile_review_observability_fix_result_report.md` |
| `compile_review_observability_verification_report.md` |
| `compile_review_per_job_review_mode_fix_result_report.md` |
| `compile_review_persist_gate_fix_result_report.md` |
| `compile_review_persist_gate_runtime_verification_report.md` |
| `compile_review_persist_gate_test_result_report.md` |
| `compile_review_prompt_externalization_final_runtime_gate_report.md` |
| `compile_review_prompt_externalization_pre_commit_quality_report.md` |
| `compile_review_query_visibility_filter_verification_report.md` |
| `final_query_baseline_gate_report.md` |
| `phase12_final_clean_rebuild_gate_report.md` |
| `pre_commit_quality_review_report.md` |
| `swip_answer_grounding_pre_commit_quality_review_report.md` |
| `swip_focus_snippet_patch_side_effect_review_report.md` |

---

## 5. 引用影响汇总

| 引用源 | 影响范围 | 说明 |
|---|---|---|
| `docs/quality-progress-and-lessons.md` | 34 个文件（13 docs/test + 21 archived_reports） | 这些文件不可删除，为当前证据链 |
| `AGENTS.md` | `docs/multi-agent-model-routing-guide.md` | 项目指南，不可删除 |
| `README.md` | `docs/数据库表结构详解.md` | 参考文档 |
| `docs/test/e2e-clean-rebuild-suite.json` | `full_rebuild_e2e_validation_asset_design_report.md` | JSON 引用 |
| 根目录报告间互引用 | 多个 admin/compile/phase 报告 | DELETE 候选（4 个 superseded fix_result）无外部引用，可安全删除 |

---

## 6. 建议执行顺序

### 第 1 步：安全删除（6 个文件，零引用风险）

```
git rm admin_article_detail_review_metric_ux_fix_result_report.md
git rm admin_governance_metric_action_entry_fix_result_report.md
git rm admin_governance_metric_explainer_panel_fix_result_report.md
git rm admin_history_modal_hotspot_copy_fix_result_report.md
git rm current_runtime_version_check_report.md
git rm current_workspace_pending_fixes_status_update_report.md
```

这 6 个文件：
- 4 个被 fix_revision 完全替代且无外部引用
- 2 个为过期一次性快照

### 第 2 步：根目录报告迁移（58 个文件 → `docs/reports/`）

按 `docs/reports/markdown_docs_consolidation_design_report.md` 方案执行 5 批迁移。迁移后根目录仅保留 `README.md`、`AGENTS.md`、`CLAUDE.md`、`special_cases_report.md`。

### 第 3 步（可选）：修复引用后降级 4 个 ARCHIVE

当前 SECTION 3.1 的 4 个 superseded fix_result，如果将其引用方更新为指向 fix_revision，则可降级为 DELETE。

### 不建议执行

- **不要删除任何被 quality-progress 引用的文件**（34 个）
- **不要删除/移动 `archived_reports/` 中被 quality-progress 引用的 21 个文件**
- **不要删除 `docs/plans/` 中的任何计划文件**
- **不要删除 `docs/test/title-generation/` 中的测试资产**

---

## 7. 最终判定汇总

| 分类 | 数量 | 说明 |
|---|---|---|
| **DELETE** | **6** | 4 个 superseded fix_result + 2 个过期快照 |
| **ARCHIVE** | **168** | 58 个根目录报告 + 10 个 docs/ 报告 + ~100 个 archived_reports（含约 82 个无引用） |
| **KEEP** | **34** | 4 个 plans + 2 个指南 + 5 个有引用价值的测试报告 + 13 个 quality-progress 引用 + 4 个 title-generation + 21 个 archived_reports（quality-progress 引用） + remaining_docs + acceptance-report + README + question-set |
| **排除（不审计）** | **8** | README、AGENTS、CLAUDE、special_cases_report、quality-progress、项目全流程真实验收手册、项目启动配置清单、模型绑定配置参考 |

---

*本报告由 agentC 生成。未修改、未删除、未移动、未 stage、未 commit、未 push。*
