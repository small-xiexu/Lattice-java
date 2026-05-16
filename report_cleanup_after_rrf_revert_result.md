# Report Cleanup After RRF Revert Result

- **执行时间**: 2026-05-16
- **分支**: `codex/qa-polish`
- **执行依据**: `report_cleanup_plan_after_swip_rrf.md`
- **前置条件**: RRF retained content 已回退，三轮稳定性校验已通过（`swip_rrf_revert_stability_verification_report.md`），RRF 主线已收口。

## 1. 删除了哪些报告

共删除 10 个过期中间报告：

| 文件 | 删除原因 |
|---|---|
| `swip_rrf_retained_content_fix_result_report.md` | RRF fix 尝试，已被 revert 报告覆盖 |
| `swip_clean_rebuild_eval_report.md` | 早期 SWIP clean rebuild eval，已被 grounding analysis 覆盖 |
| `swip_eval_schema_alignment_report.md` | 早期 SWIP schema 对齐，已被 expectation adjustment 覆盖 |
| `swip_eval_expectation_review_report.md` | SWIP 预期审查，已被 expectation adjustment 覆盖 |
| `swip_question_focused_evidence_fix_design_report.md` | QFE fix 设计，已被 grounding analysis 覆盖 |
| `swip_question_focused_evidence_fix_result_report.md` | QFE fix 结果，已被 grounding analysis 覆盖 |
| `swip_fused_hit_retained_content_analysis_report.md` | RRF retained content 融合分析，已被 revert/stability 覆盖 |
| `cleanup_old_reports_result.md` | 旧清理结果记录，已过期 |
| `quality_progress_doc_update_report.md` | 质量文档更新中间报告，已过期 |
| `current_project_status_after_phase12.md` | Phase 12 后状态快照，已过期 |

## 2. 保留了哪些报告

### RRF/SWIP 当前决策链路（untracked）

| 文件 | 保留原因 |
|---|---|
| `swip_rrf_retained_content_revert_report.md` | RRF revert 执行记录与回退边界确认 |
| `swip_rrf_revert_stability_verification_report.md` | RRF 收口前的三轮稳定性校验证据 |
| `swip_eval_expectation_adjustment_report.md` | SWIP expect 机械断言修正决策依据 |
| `swip_answer_grounding_failure_analysis_report.md` | SWIP answer grounding 失败归因，下一条候选主线的分析基础 |

### 治理分析（untracked）

| 文件 | 保留原因 |
|---|---|
| `compile_article_review_flow_runtime_audit_report.md` | compile review 链路运行时审计，compile review 治理的决策依据 |
| `compile_review_governance_design_report.md` | compile review 治理设计方案 |

### 长期 Gate / Baseline（tracked clean）

| 文件 | 保留原因 |
|---|---|
| `final_query_baseline_gate_report.md` | 主 query baseline gate 通过记录 |
| `phase12_final_clean_rebuild_gate_report.md` | Phase 12 clean rebuild gate 通过记录 |
| `pre_commit_quality_review_report.md` | 提交前质量审查记录 |
| `cleanup_before_commit_report.md` | 提交前清理记录 |
| `phase12_pre_commit_cleanup_report.md` | Phase 12 提交前清理记录 |
| `test_database_isolation_fix_result_report.md` | 测试库隔离修复记录 |

### 规范/配置/台账（tracked modified 或 untracked）

| 文件 | 保留原因 |
|---|---|
| `special_cases_report.md` | redline 长期规则文件（tracked modified） |
| `docs/quality-progress-and-lessons.md` | 质量打磨进度台账 |
| `docs/multi-agent-model-routing-guide.md` | 多 agent 模型路由参考 |
| `report_cleanup_plan_after_swip_rrf.md` | 本轮清理的规划文件，待确认后可删除 |

## 3. 为什么保留这些报告

- **RRF revert + stability** 两份报告构成 RRF 主线收口的完整证据链：回退执行 → 稳定性验证 → 收口结论。
- **expectation adjustment + grounding analysis** 是 SWIP answer grounding 下一条候选主线的直接分析基础，不可删除。
- **compile review 治理两份报告** 是 compile review 治理落地的决策依据，尚未收口。
- **长期 gate/baseline 报告** 是项目阶段性质量门的不可替代记录。
- **special_cases_report.md** 是 redline 规则文件，当前 tracked modified，长期保留。
- **quality-progress-and-lessons.md** 是质量打磨阶段的唯一进度台账，必须保留并持续更新。

## 4. docs/quality-progress-and-lessons.md 更新了哪些结论

| 更新项 | 旧内容 | 新内容 |
|---|---|---|
| 更新时间 | `2026-05-16` | `2026-05-16（RRF 主线收口后更新）` |
| 当前阶段 - SWIP eval | "未达到稳定收口" | "已确认稳定区间 13-14/23，11/23 偶发波动未复现" |
| 当前阶段 - RRF 主线 | "需回退或确认不保留" | "已回退，RRF 主线收口" |
| 当前阶段 - 报告 cleanup | "待 RRF 收口后再动" | "已完成" |
| 当前阶段 - 下一步 | 暂停状态 | "SWIP answer grounding、compile review 治理、阶段性提交" |
| Gate - SWIP strict eval | "未收口" | "已收口，稳定区间 13-14/23" |
| Agent 职责 - agentA | "正在 RRF retained content 尝试" | "RRF 已收口，待下一条主线分配" |
| 已验证结论 - RRF | "需回退或确认不保留" | "已回退且确认不保留，RRF 主线已收口" |
| 踩坑 - RRF retained content | "不准保留负收益 RRF 改动；先回退或确认不保留" | "已回退且确认不保留；回退后 REPRINT-001 回归已恢复" |
| 禁止事项 | 含"不准保留负收益 RRF 改动" | 该条已移除（已完成） |
| 下一步计划 | 全部待做 | 前三项标记已完成，第四项明确候选优先级 |

## 5. 是否修改源码

**否。** 本轮未修改任何 `src/main/java/**` 文件。

## 6. 是否修改测试

**否。** 本轮未修改任何 `src/test/java/**` 文件。

## 7. 是否修改题集

**否。** `docs/test/swip-query-eval-candidates.json` 未修改。

## 8. 下一步

**只推荐一个动作：阶段性提交前质量复核。**

理由：RRF 主线已收口，当前工作区有 `docs/quality-progress-and-lessons.md`、`special_cases_report.md` 等修改待提交，报告清理已完成。建议在下一条代码主线（SWIP answer grounding 或 compile review 治理）启动前，先做一次阶段性提交并完成 `mvn test` + redline 复核。
