# 报告清理规划（BANK-SETTLEMENT focus snippet 后）

- **生成时间**: 2026-05-16
- **分支**: `codex/qa-polish`
- **执行 Agent**: agentC
- **规划类型**: 只做清理计划，不实际删除文件

## 1. 当前工作区是否有未提交代码改动

**是。** 当前工作区存在未提交的 Query 主链改动：

| 文件 | 状态 |
|---|---|
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPayloadOrchestrator.java` | M (已修改) |
| `src/main/java/com/xbk/lattice/query/service/AnswerGenerationPromptEvidenceSupport.java` | M (已修改) |
| `special_cases_report.md` | M (redline 扫描刷新) |

此外，BANK-SETTLEMENT focus snippet patch 还在等待副作用复核，代码主线尚未收口。

## 2. 为什么本轮只做清理计划、不直接删除

三个原因：

1. **代码主线未收口**：`AnswerGenerationPayloadOrchestrator.java` 和 `AnswerGenerationPromptEvidenceSupport.java` 仍有未提交改动，BANK-SETTLEMENT focus snippet patch 副作用复核未完成。按 AGENTS.md 多 Agent 协作规范，代码修复未收口前不得删除仍可能被引用的报告。
2. **BANK-SETTLEMENT 链仍在活跃**：当前 6 份 BANK-SETTLEMENT 相关报告是活跃分析材料，删除任何一份都可能打断正在进行的归因。
3. **本轮 agentC 职责边界**：按 `docs/multi-agent-model-routing-guide.md`，agentC 在代码修复进行中只能做清理规划，不直接删除。

## 3. 必须保留清单

### 长期 Gate / Baseline（tracked clean，不可删除）

| 文件 | 保留原因 |
|---|---|
| `final_query_baseline_gate_report.md` | 主 query baseline gate 通过记录 |
| `phase12_final_clean_rebuild_gate_report.md` | Phase 12 clean rebuild gate 通过记录 |
| `test_database_isolation_fix_result_report.md` | 测试库隔离修复记录 |
| `cleanup_before_commit_report.md` | 提交前清理记录 |
| `phase12_pre_commit_cleanup_report.md` | Phase 12 提交前清理记录 |
| `pre_commit_quality_review_report.md` | 提交前质量审查记录 |
| `pre_commit_after_rrf_cleanup_quality_report.md` | RRF 清理后质量审查记录 |

### 规范/配置/台账

| 文件 | 保留原因 |
|---|---|
| `special_cases_report.md` | redline 长期规则文件（tracked modified） |
| `docs/quality-progress-and-lessons.md` | 质量打磨进度台账 |
| `docs/multi-agent-model-routing-guide.md` | 多 agent 模型路由参考 |

### SWIP 基础（不可替代的决策依据）

| 文件 | 保留原因 |
|---|---|
| `swip_eval_expectation_adjustment_report.md` | SWIP expect 机械断言修正决策，整个 SWIP eval 链的起点 |
| `swip_answer_grounding_failure_analysis_report.md` | answer grounding 初始 9 个稳定失败归因，后续修复的根因基础 |

### RRF 历史证据

| 文件 | 保留原因 |
|---|---|
| `swip_rrf_retained_content_revert_report.md` | RRF revert 执行记录，回退边界确认 |
| `swip_rrf_revert_stability_verification_report.md` | RRF 收口前三轮稳定性校验证据 |

### 治理分析

| 文件 | 保留原因 |
|---|---|
| `compile_article_review_flow_runtime_audit_report.md` | compile review 链路运行时审计 |
| `compile_review_governance_design_report.md` | compile review 治理设计方案 |

## 4. 暂时保留清单

### BANK-SETTLEMENT 当前活跃链（代码未收口，必须保留）

| 文件 | 保留原因 |
|---|---|
| `swip_bank_settlement_focus_snippet_fix_result_report.md` | focus snippet patch 修复结果，当前活跃 |
| `swip_bank_settlement_prompt_evidence_truncation_analysis_report.md` | prompt evidence 截断根因分析 |
| `swip_answer_prompt_audit_instrumentation_result_report.md` | answer prompt 审计插桩结果 |
| `swip_bank_settlement_prompt_evidence_runtime_analysis_report.md` | prompt evidence 运行时分析 |
| `swip_bank_settlement_prompt_evidence_fix_result_report.md` | prompt evidence 修复结果 |
| `swip_bank_settlement_outcome_guard_analysis_report.md` | outcome guard 过度降级归因分析 |

### Answer grounding 提交前保留（建议提交后再评估删除）

| 文件 | 保留原因 |
|---|---|
| `swip_answer_grounding_pre_commit_quality_review_report.md` | 提交前质量复核结论，当前最重要的门禁报告 |
| `swip_answer_grounding_quality_progress_update_report.md` | 质量台账同步记录 |
| `swip_answer_grounding_patch_stability_verification_report.md` | answer grounding 三轮稳定性验证证据 |
| `swip_answer_grounding_current_patch_stability_report.md` | 当前 patch 稳定性分析（已被 verification 覆盖，但建议提交前保留） |

### Answer grounding 修复链路证据（被 pre_commit_quality_review 引用）

| 文件 | 保留原因 |
|---|---|
| `swip_stable_answer_missing_terms_analysis_report.md` | answer grounding 初始 9 个稳定失败归因 |
| `swip_structured_exact_lookup_leadin_fix_result_report.md` | lead-in / structured body 裁剪修复结果 |
| `swip_unanswerable_regression_analysis_report.md` | 无答案回归根因分析 |
| `swip_unanswerable_outcome_guard_fix_result_report.md` | outcome guard 修复结果 |
| `swip_ip_suffix_regression_analysis_report.md` | IP-SUFFIX 稳定回归根因 |
| `swip_ip_suffix_postprocessor_fix_result_report.md` | IP-SUFFIX 后处理修复结果 |

## 5. 可删除候选清单

| 文件 | 删除理由 |
|---|---|
| `report_cleanup_plan_after_swip_rrf.md` | 旧清理规划，已被本轮 `report_cleanup_plan_after_bank_settlement_focus_snippet.md` 取代。保留最近一次即可。 |
| `report_cleanup_after_rrf_revert_result.md` | 旧清理执行结果，已被后续 answer grounding 和 BANK-SETTLEMENT 轮次的报告体系覆盖。保留最近一次即可。 |
| `swip_answer_grounding_current_patch_stability_report.md` | 已被 `swip_answer_grounding_patch_stability_verification_report.md`（三轮完整验证）覆盖。两者均记录 answer grounding patch 稳定性，verification 报告更完整且包含最终三轮数据。 |
| `swip_outcome_guard_side_effect_analysis_report.md` | outcome guard 副作用分析。其结论已被 `swip_bank_settlement_outcome_guard_analysis_report.md`（BANK-SETTLEMENT 专项 outcome guard 归因）和 `swip_answer_grounding_pre_commit_quality_review_report.md`（提交前复核中 outcome guard 风险评估）吸收。 |

## 6. 每个可删除候选的删除理由

### 6.1 `report_cleanup_plan_after_swip_rrf.md`

- **生成时间**: 2026-05-16 09:19
- **内容**: RRF 收口后的报告清理规划
- **被谁覆盖**: 本轮生成的 `report_cleanup_plan_after_bank_settlement_focus_snippet.md` 是更新的清理规划，覆盖了更大的报告集合
- **删除风险**: 低。旧规划已执行完毕（`report_cleanup_after_rrf_revert_result.md` 为其执行结果），不再作为当前决策依据

### 6.2 `report_cleanup_after_rrf_revert_result.md`

- **生成时间**: 2026-05-16 10:23
- **内容**: RRF revert 后的报告清理执行结果
- **被谁覆盖**: 后续 answer grounding 和 BANK-SETTLEMENT 轮次产生了新的报告体系和清理需求，该文件记录的历史清理动作已被 `docs/quality-progress-and-lessons.md` 中的"报告 cleanup"条目吸收
- **删除风险**: 低。清理历史已记入质量台账

### 6.3 `swip_answer_grounding_current_patch_stability_report.md`

- **生成时间**: 2026-05-16 13:04
- **内容**: answer grounding patch 稳定性分析
- **被谁覆盖**: `swip_answer_grounding_patch_stability_verification_report.md`（2026-05-16 17:53）包含完整三轮 strict eval 验证，数据更全、结论更新
- **删除风险**: 低。verification 报告已包含所有关键指标和逐 case 矩阵

### 6.4 `swip_outcome_guard_side_effect_analysis_report.md`

- **生成时间**: 2026-05-16 12:33
- **内容**: outcome guard 副作用分析（聚焦 answer grounding patch 引入的 outcome guard 对 BANK-SETTLEMENT 等 case 的副作用）
- **被谁覆盖**:
  - `swip_bank_settlement_outcome_guard_analysis_report.md`（2026-05-16 18:30）做了更深入的 BANK-SETTLEMENT outcome guard 过度降级专项归因
  - `swip_answer_grounding_pre_commit_quality_review_report.md`（2026-05-16 18:08）已在"4.2 Outcome guard 风险"章节吸收了副作用分析的核心结论
- **删除风险**: 低。核心结论已被两份后继报告吸收

## 7. 建议何时执行删除

三个可选时间点，按保守程度排序：

| 时机 | 说明 | 推荐度 |
|---|---|---|
| **focus snippet 副作用复核通过后** | BANK-SETTLEMENT 链收口，确认 4 个可删除候选不再被引用后执行 | 推荐，最安全 |
| **提交前 cleanup 阶段** | 在提交 answer grounding + BANK-SETTLEMENT patch 前统一清理 | 可接受，但注意 4 个候选中有 2 个是旧 cleanup 文件，与代码提交无关 |
| **提交后 cleanup 阶段** | 提交完成后再清理 | 也可接受，旧 cleanup 文件无害 |

推荐在 **focus snippet 副作用复核通过后、提交前** 执行实际删除，原因：
- 此时 BANK-SETTLEMENT 链已稳定，不会再有新报告引用旧报告
- 提交前清理可以让 git 工作区更干净
- 旧 cleanup plan/result 的删除与代码提交解耦，但一起做可以减少工作区碎片

## 8. 本轮是否删除文件

**否。** 本轮只生成清理规划，未删除任何文件。

## 9. 本轮是否修改代码

**否。** 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`docs/test/**`、`scripts/**`、`.claude/**`、`AGENTS.md`、`CLAUDE.md`、`pom.xml`、`special_cases_report.md`。

## 10. 后续建议

1. 等待 BANK-SETTLEMENT focus snippet patch 副作用复核完成。
2. 复核通过后，由 agentC 按本规划执行实际删除（4 个文件）。
3. 删除后更新 `docs/quality-progress-and-lessons.md` 的记录。
4. 进入提交前 cleanup 阶段。
