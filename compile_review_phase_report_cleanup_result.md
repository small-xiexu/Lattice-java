# Compile Review Phase 报告清理结果

- **清理日期**：2026-05-22
- **清理执行者**：agentC
- **清理范围**：仅根目录 `.md` 报告文件，不涉及代码

---

## 1. 保留了哪些报告（根目录，11 份）

以下为阶段关键结论报告，保留在项目根目录：

| # | 文件名 | 说明 |
|---|--------|------|
| 1 | `compile_review_queue_approve_idempotency_fix_result_report.md` | approve 幂等修复报告 |
| 2 | `compile_review_queue_approve_idempotency_runtime_verification_report.md` | approve 幂等运行时验证 |
| 3 | `compile_review_queue_dedup_fix_result_report.md` | 去重修复报告 |
| 4 | `compile_review_queue_dedup_runtime_verification_report.md` | 去重运行时验证 |
| 5 | `compile_writer_payload_budget_slimming_fix_result_report.md` | Writer 预算修复报告 |
| 6 | `compile_writer_payload_budget_slimming_runtime_verification_report.md` | Writer 预算运行时验证 |
| 7 | `compile_reviewer_payload_slimming_fix_result_report.md` | Reviewer 裁剪修复报告 |
| 8 | `compile_reviewer_payload_slimming_runtime_verification_report.md` | Reviewer 裁剪运行时验证 |
| 9 | `compile_writer_unit_routing_gate_fix_result_report.md` | Writer Gate 修复报告 |
| 10 | `compile_writer_unit_routing_gate_full_runtime_verification_report.md` | Writer Gate 运行时验证 |
| 11 | `phase_compile_query_stage_acceptance_report.md` | 阶段性整体验收报告 |

项目文件（非报告）保留在根目录：
- `AGENTS.md`、`CLAUDE.md`、`README.md`、`special_cases_report.md`

---

## 2. 归档了哪些报告（移至 `archived_reports/`，103 份）

### 2.1 当前阶段中间报告（13 份）

| 文件名 | 归档原因 |
|--------|----------|
| `compile_pipeline_performance_analysis_report.md` | 性能分析中间报告 |
| `compile_pipeline_second_bottleneck_analysis_report.md` | 瓶颈分析中间报告 |
| `compile_pipeline_third_bottleneck_analysis_report.md` | 瓶颈分析中间报告 |
| `compile_review_queue_dedup_design_report.md` | 去重设计文档 |
| `compile_writer_budget_and_redis_fix_pre_commit_quality_report.md` | 提交前质量复核 |
| `compile_review_queue_approve_idempotency_pre_commit_quality_report.md` | 提交前质量复核 |
| `compile_review_queue_dedup_pre_commit_quality_report.md` | 提交前质量复核 |
| `compile_reviewer_payload_slimming_pre_commit_quality_report.md` | 提交前质量复核 |
| `compile_writer_unit_routing_gate_pre_commit_quality_report.md` | 提交前质量复核 |
| `compile_redis_interrupt_fix_result_report.md` | Redis 中断修复（未在核心保留清单） |
| `compile_redis_interrupt_root_cause_analysis_report.md` | Redis 中断根因分析 |
| `compile_redis_interrupt_runtime_verification_report.md` | Redis 中断运行时验证 |
| `compile_normal_doc_failure_triage_report.md` | 普通文档失败分类 |

### 2.2 前序阶段 Compile Human Review Queue 报告（11 份）

compile human review queue 后端/前端/发布语义相关，前序阶段已完成。

### 2.3 前序阶段 Compile Review LLM/Governance/Observability 报告（19 份）

LLM Reviewer enablement、fail-closed、observability、persist gate 等早期 compile review 治理报告。

### 2.4 前序阶段 Compile Persist/Query Visibility/Prompt 报告（20 份）

persist gate、query visibility filter、prompt externalization 等中期报告。

### 2.5 前序阶段 Compile Job/Structured/Semantics 报告（13 份）

job idempotency、progress display、structured table gate、publish semantics 等报告。

### 2.6 SWIP 相关报告（21 份）

answer grounding、bank settlement、focus snippet、RRF revert、IP suffix 等 SWIP 线报告。

### 2.7 Phase 12 / Quality / 杂项报告（6 份）

phase12 gate、pre-commit quality、final query baseline、test database isolation 等。

---

## 3. 删除了哪些报告（22 份）

### 3.1 Admin Dashboard 报告（16 份）

与当前 compile/human review 主线无关的管理后台 UI 报告：

- `admin_dashboard_review_queue_summary_analysis_report.md`
- `admin_dashboard_review_queue_summary_fix_result_report.md`
- `admin_dashboard_review_queue_summary_pre_commit_quality_report.md`
- `admin_dashboard_review_queue_summary_runtime_verification_report.md`
- `admin_dashboard_runtime_reviewer_residual_fix_result_report.md`
- `admin_dashboard_runtime_reviewer_residual_runtime_verification_report.md`
- `admin_dashboard_usability_final_runtime_gate_report.md`
- `admin_dashboard_usability_fix_result_report.md`
- `admin_dashboard_usability_pre_commit_quality_report.md`
- `admin_dashboard_usability_review_report.md`
- `admin_dashboard_usability_reviewer_wording_fix_result_report.md`
- `admin_dashboard_usability_reviewer_wording_runtime_verification_report.md`
- `admin_dashboard_usability_runtime_verification_report.md`
- `admin_dashboard_usability_wording_alignment_fix_result_report.md`
- `admin_review_queue_count_filter_visual_fix_result_report.md`
- `admin_review_queue_count_filter_visual_runtime_verification_report.md`

### 3.2 已被本轮覆盖的旧清理报告（6 份）

- `compile_review_report_cleanup_result.md`
- `compile_review_legacy_direct_compile_report_cleanup_result.md`
- `post_human_review_queue_commit_cleanup_report.md`
- `report_cleanup_after_bank_settlement_focus_snippet_result.md`
- `report_cleanup_plan_after_bank_settlement_focus_snippet.md`
- `cleanup_before_commit_report.md`

---

## 4. 当前工作区是否已足够干净

**是。** 根目录现在仅保留：

| 类别 | 文件数 |
|------|:--:|
| 项目文件 | 4（AGENTS.md、CLAUDE.md、README.md、special_cases_report.md） |
| 阶段关键结论报告 | 11 |
| 阶段状态文档（本轮新增） | 2（compile_review_phase_status.md、本报告） |
| **根目录 .md 合计** | **17** |

归档目录 `archived_reports/` 包含 103 份历史报告，结构清晰、可按需查阅。

---

## 5. 本轮是否修改代码

**否。** 本轮操作仅限于：
- 创建 `archived_reports/` 目录
- 移动 103 份报告至归档目录
- 删除 22 份不再需要的报告
- 创建 2 份新文档（阶段状态台账 + 本清理结果报告）

未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、脚本、数据库或任何配置文件。
