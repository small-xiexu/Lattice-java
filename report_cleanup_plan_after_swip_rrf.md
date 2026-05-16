# SWIP/RRF 后报告清理规划

## 扫描结论

- 扫描范围：根目录命中 `*report*.md`、`*baseline*.md`、`*gate*.md`、`*status*.md`、`special_cases_report.md` 的当前文件。
- 扫描时报告总数：18 个，不含本规划文件。
- 本规划文件生成后报告类 Markdown 总数：19 个。
- 本轮删除文件：否。
- 本轮修改代码：否。
- 本轮未运行：`mvn test`、query regression、清库、提交。
- 分类依据：文件名、`git status` 状态、当前 SWIP/RRF 上下文；未读取所有报告正文。

## 当前 git status 摘要

- 分支：`codex/qa-polish...origin/codex/qa-polish`
- 已有跟踪文件修改：
  - `special_cases_report.md`
  - `docs/test/swip-query-eval-candidates.json`
  - `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`
- 已有未跟踪报告/状态文档：11 个。
- 已有工作区删除标记的根目录报告：9 个。
- 本轮新增：`report_cleanup_plan_after_swip_rrf.md`。

已有工作区删除标记的报告不计入本次 `rg --files` 扫描总数；这些删除不是本轮产生，后续实际 cleanup 前需要单独确认是继续删除还是恢复。

## 必须长期保留

| 文件 | 当前状态 | 处理建议 |
|---|---:|---|
| `special_cases_report.md` | tracked modified | 长期保留；提交前确认本次修改来源和内容。 |
| `final_query_baseline_gate_report.md` | tracked clean | 长期保留。 |
| `phase12_final_clean_rebuild_gate_report.md` | tracked clean | 长期保留。 |
| `pre_commit_quality_review_report.md` | tracked clean | 长期保留。 |
| `cleanup_before_commit_report.md` | tracked clean | 长期保留。 |
| `phase12_pre_commit_cleanup_report.md` | tracked clean | 长期保留。 |
| `test_database_isolation_fix_result_report.md` | tracked clean | 长期保留。 |

## 当前 SWIP/RRF 链路临时保留

| 文件 | 当前状态 | 保留原因 |
|---|---:|---|
| `swip_clean_rebuild_eval_report.md` | untracked | SWIP clean rebuild 评测上下文，RRF retained content 修复期间可能被引用。 |
| `swip_eval_schema_alignment_report.md` | untracked | SWIP eval schema 对齐上下文。 |
| `swip_eval_expectation_review_report.md` | untracked | SWIP 预期审查上下文。 |
| `swip_eval_expectation_adjustment_report.md` | untracked | SWIP 预期调整上下文。 |
| `swip_answer_grounding_failure_analysis_report.md` | untracked | answer grounding 失败归因上下文。 |
| `swip_question_focused_evidence_fix_design_report.md` | untracked | question focused evidence 修复设计上下文。 |
| `swip_question_focused_evidence_fix_result_report.md` | untracked | question focused evidence 修复结果上下文。 |
| `swip_fused_hit_retained_content_analysis_report.md` | untracked | RRF retained content 当前直接分析材料。 |
| `swip_rrf_retained_content_fix_result_report.md` | 未发现 | RRF agent 如生成，应先临时保留，等修复验收完成后再判断删除。 |

治理分析临时保留：

| 文件 | 当前状态 | 保留原因 |
|---|---:|---|
| `compile_article_review_flow_runtime_audit_report.md` | untracked | compile/article review flow 治理分析材料，当前建议临时保留。 |
| `compile_review_governance_design_report.md` | 未发现 | 如后续生成，应先临时保留到治理结论收口。 |

## RRF 修复完成后可删除

删除前置条件：

- RRF retained content 修复结果已产出。
- `swip_rrf_retained_content_fix_result_report.md` 如存在，已被读取并完成必要结论沉淀。
- final gate / clean rebuild / query 回归所需结论已进入长期保留报告或提交说明。
- 用户确认不需要保留 SWIP 中间诊断流水。

满足条件后可删除的当前文件：

- `swip_clean_rebuild_eval_report.md`
- `swip_eval_schema_alignment_report.md`
- `swip_eval_expectation_review_report.md`
- `swip_eval_expectation_adjustment_report.md`
- `swip_answer_grounding_failure_analysis_report.md`
- `swip_question_focused_evidence_fix_design_report.md`
- `swip_question_focused_evidence_fix_result_report.md`
- `swip_fused_hit_retained_content_analysis_report.md`
- `compile_article_review_flow_runtime_audit_report.md`

满足条件后如存在，也可纳入删除候选：

- `swip_rrf_retained_content_fix_result_report.md`
- `compile_review_governance_design_report.md`

## 已可立即删除

| 文件 | 当前状态 | 理由 |
|---|---:|---|
| `cleanup_old_reports_result.md` | untracked | 不属于长期保留、SWIP/RRF 临时链路、治理临时链路或当前状态文档；仅作为旧清理结果记录，默认可删除。 |

本轮不执行删除；这里只给出候选。

## 需要用户确认

| 文件/变更 | 当前状态 | 需要确认的问题 |
|---|---:|---|
| `current_project_status_after_phase12.md` | untracked | 是否提交为当前状态文档，还是在后续 cleanup 中删除。 |
| `cleanup_old_reports_result.md` | untracked | 是否需要保留旧清理过程记录；默认建议删除。 |
| `compile_article_review_flow_runtime_audit_report.md` | untracked | 治理分析是否需要归档；否则 RRF/治理收口后删除。 |
| `special_cases_report.md` | tracked modified | 该长期保留报告的修改是否为预期变更。 |

已有删除标记但当前工作树不存在的报告，需要确认继续删除还是恢复：

- `deep_research_fact_card_anchor_fix_result_report.md`
- `deep_research_graph_fact_projection_fix_result_report.md`
- `query_baseline_exact_path_grounding_fix_result_report.md`
- `query_baseline_ocr_eval_expectation_update_report.md`
- `query_baseline_ocr_runtime_source_fix_result_report.md`
- `swip_baseline_report.md`
- `swip_compile_coverage_analysis_report.md`
- `swip_docx_extraction_comparison_report.md`
- `swip_embedding_regression_case_analysis_report.md`

非报告类已有变更，仅记录状态，不纳入本清理规划：

- `docs/test/swip-query-eval-candidates.json`
- `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`

## 下一步建议

1. 等 RRF retained content 修复结果出来后，确认是否生成 `swip_rrf_retained_content_fix_result_report.md`。
2. 再执行一次只读扫描：`git status --short --branch` 和报告文件列表。
3. 若 RRF 修复已通过必要验收，按“RRF 修复完成后可删除”清单执行实际 cleanup。
4. cleanup 前先让用户确认：`current_project_status_after_phase12.md` 的去留、已有 9 个删除标记报告是否继续删除、`special_cases_report.md` 修改是否保留。
