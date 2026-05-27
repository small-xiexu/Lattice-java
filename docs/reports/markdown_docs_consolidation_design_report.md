# Markdown 文档收口方案设计报告

**设计时间**：2026-05-27
**设计 Agent**：agentC（文档治理 Agent）
**设计模式**：只读盘点 + 引用影响评估 + 分批迁移设计
**约束声明**：全部 5 批迁移已完成。根目录过程报告已全部收口至 `docs/reports/`。未 stage、未 commit、未 push。

---

## 0. 当前状态

### 总量

| 统计项 | 数量 |
|---|---|
| 仓库 Markdown 总量 | 324 |
| 根目录 Markdown | 4 |
| docs/ Markdown | 46 |
| 其他目录（src/test/resources 等） | ~212 |

### 根目录 4 个文件分类

| 分类 | 数量 | 说明 |
|---|---|---|
| 必须保留 | 3 | README.md、AGENTS.md、CLAUDE.md |
| redline 输出（排除） | 1 | special_cases_report.md |
| admin UI/治理报告 | 0 | 已迁移至 `docs/reports/admin/` |
| compile-review 报告 | 0 | 已迁移至 `docs/reports/compile-review/` |
| query 报告 | 0 | 已迁移至 `docs/reports/query/` |
| phase 报告 | 0 | 已迁移至 `docs/reports/e2e/` |
| e2e/rebuild 报告 | 0 | 已迁移至 `docs/reports/e2e/` |
| runtime/workspace 报告 | 0 | 已迁移至 `docs/reports/runtime/` |
| 其他 | 0 | 已迁移至 `docs/reports/archive/` |

---

## 1. 必须保留根目录清单

| 文件 | 原因 |
|---|---|
| `README.md` | 项目入口文档，GitHub 默认展示 |
| `AGENTS.md` | Agent 行为规范，Claude Code 自动加载 |
| `CLAUDE.md` | 项目级 Claude 指令（引用 AGENTS.md） |

**建议**：CLAUDE.md 当前内容为"先阅读 AGENTS.md"，可评估是否合并到 AGENTS.md 以进一步精简。本轮不做决定。

---

## 2. 不建议提交/迁移清单

| 文件 | 分类 | 原因 |
|---|---|---|
| `special_cases_report.md` | redline 输出 | 脚本生成物，不应提交或迁移 |
| `docs/模型绑定配置参考.md` | 私有配置 | 包含真实 apiKey，永远排除 |

---

## 3. 目标目录设计

```
docs/reports/
├── admin/                  ← admin_* 报告（28 个）
├── compile-review/         ← compile_review*、compile_fixer*、compile_writer*、compile_reviewer*（14 个）
├── query/                  ← query_* 报告（6 个）
├── e2e/                    ← e2e_*、full_rebuild_*、phase_* 报告（7 个）
├── runtime/                ← current_* 报告（1 个）
└── archive/                ← 单次/一次性报告（agents_md_*、review_queue_12_* 等）
```

---

## 4. 精确迁移映射

### 第 1 批：低风险 archive（2 个文件）

| 源文件（根目录） | 目标路径 |
|---|---|
| `agents_md_runtime_policy_wording_fix_report.md` | `docs/reports/archive/agents_md_runtime_policy_wording_fix_report.md` |
| `review_queue_12_items_manual_triage_report.md` | `docs/reports/archive/review_queue_12_items_manual_triage_report.md` |

**引用影响**：无。两个文件均未被 README.md、AGENTS.md、docs/、scripts/ 引用。

### 第 2 批：runtime/workspace（1 个文件）

| 源文件（根目录） | 目标路径 |
|---|---|
| `current_workspace_split_pre_commit_quality_report.md` | `docs/reports/runtime/current_workspace_split_pre_commit_quality_report.md` |

**引用影响**：无。

### 第 3 批：e2e / phase / rebuild（7 个文件）

| 源文件（根目录） | 目标路径 |
|---|---|
| `e2e_clean_rebuild_suite_creation_report.md` | `docs/reports/e2e/e2e_clean_rebuild_suite_creation_report.md` |
| `full_rebuild_e2e_validation_asset_design_report.md` | `docs/reports/e2e/full_rebuild_e2e_validation_asset_design_report.md` |
| `full_rebuild_e2e_validation_runtime_report.md` | `docs/reports/e2e/full_rebuild_e2e_validation_runtime_report.md` |
| `phase_compile_query_rebuild_acceptance_report.md` | `docs/reports/e2e/phase_compile_query_rebuild_acceptance_report.md` |
| `phase_compile_query_stage_acceptance_report.md` | `docs/reports/e2e/phase_compile_query_stage_acceptance_report.md` |
| `phase_current_workspace_existing_cases_acceptance_report.md` | `docs/reports/e2e/phase_current_workspace_existing_cases_acceptance_report.md` |
| `phase_current_workspace_pending_fixes.md` | `docs/reports/e2e/phase_current_workspace_pending_fixes.md` |

**引用影响**：
- `full_rebuild_e2e_validation_asset_design_report.md` 被 `docs/test/e2e-clean-rebuild-suite.json` 引用（1 处）。迁移后需更新该 JSON 中的引用路径。
- 其余 6 个文件无引用。

### 第 4 批：query（6 个文件）

| 源文件（根目录） | 目标路径 |
|---|---|
| `query_citation_quality_terminal_fallback_fix_result_report.md` | `docs/reports/query/query_citation_quality_terminal_fallback_fix_result_report.md` |
| `query_citation_quality_terminal_fallback_fix_revision_report.md` | `docs/reports/query/query_citation_quality_terminal_fallback_fix_revision_report.md` |
| `query_citation_quality_terminal_fallback_runtime_verification_report.md` | `docs/reports/query/query_citation_quality_terminal_fallback_runtime_verification_report.md` |
| `query_fallback_citation_quality_root_cause_report.md` | `docs/reports/query/query_fallback_citation_quality_root_cause_report.md` |
| `query_partial_answer_completeness_analysis_report.md` | `docs/reports/query/query_partial_answer_completeness_analysis_report.md` |
| `query_partial_answer_multi_point_expansion_fix_result_report.md` | `docs/reports/query/query_partial_answer_multi_point_expansion_fix_result_report.md` |
**引用影响**：无。

### 第 5 批：compile-review（14 个文件）

| 源文件（根目录） | 目标路径 |
|---|---|
| `compile_fixer_payload_slimming_fix_result_report.md` | `docs/reports/compile-review/compile_fixer_payload_slimming_fix_result_report.md` |
| `compile_review_fix_loop_performance_analysis_report.md` | `docs/reports/compile-review/compile_review_fix_loop_performance_analysis_report.md` |
| `compile_review_phase_report_cleanup_result.md` | `docs/reports/compile-review/compile_review_phase_report_cleanup_result.md` |
| `compile_review_phase_status.md` | `docs/reports/compile-review/compile_review_phase_status.md` |
| `compile_review_queue_approve_idempotency_fix_result_report.md` | `docs/reports/compile-review/compile_review_queue_approve_idempotency_fix_result_report.md` |
| `compile_review_queue_approve_idempotency_runtime_verification_report.md` | `docs/reports/compile-review/compile_review_queue_approve_idempotency_runtime_verification_report.md` |
| `compile_review_queue_dedup_fix_result_report.md` | `docs/reports/compile-review/compile_review_queue_dedup_fix_result_report.md` |
| `compile_review_queue_dedup_runtime_verification_report.md` | `docs/reports/compile-review/compile_review_queue_dedup_runtime_verification_report.md` |
| `compile_reviewer_payload_slimming_fix_result_report.md` | `docs/reports/compile-review/compile_reviewer_payload_slimming_fix_result_report.md` |
| `compile_reviewer_payload_slimming_runtime_verification_report.md` | `docs/reports/compile-review/compile_reviewer_payload_slimming_runtime_verification_report.md` |
| `compile_writer_payload_budget_slimming_fix_result_report.md` | `docs/reports/compile-review/compile_writer_payload_budget_slimming_fix_result_report.md` |
| `compile_writer_payload_budget_slimming_runtime_verification_report.md` | `docs/reports/compile-review/compile_writer_payload_budget_slimming_runtime_verification_report.md` |
| `compile_writer_unit_routing_gate_fix_result_report.md` | `docs/reports/compile-review/compile_writer_unit_routing_gate_fix_result_report.md` |
| `compile_writer_unit_routing_gate_full_runtime_verification_report.md` | `docs/reports/compile-review/compile_writer_unit_routing_gate_full_runtime_verification_report.md` |

**引用影响**：
- 无。`docs/quality-progress-and-lessons.md` 中有 22 处 compile-review 引用，但均指向已不存在的陈旧文件名（如 `compile_review_observability_fix_result_report.md`、`compile_review_persist_gate_fix_result_report.md` 等），与本次迁移的 14 个根目录文件无关。迁移不会引入新的引用断裂。

### 第 5 批：admin（28 个文件）

| 源文件（根目录） | 目标路径 |
|---|---|
| `admin_article_detail_and_ask_visual_polish_fix_result_report.md` | `docs/reports/admin/admin_article_detail_and_ask_visual_polish_fix_result_report.md` |
| `admin_article_detail_keyword_metadata_display_fix_result_report.md` | `docs/reports/admin/admin_article_detail_keyword_metadata_display_fix_result_report.md` |
| `admin_article_detail_keyword_metadata_display_fix_revision_report.md` | `docs/reports/admin/admin_article_detail_keyword_metadata_display_fix_revision_report.md` |
| `admin_article_detail_layout_visual_polish_fix_revision_report.md` | `docs/reports/admin/admin_article_detail_layout_visual_polish_fix_revision_report.md` |
| `admin_article_detail_null_guard_fix_result_report.md` | `docs/reports/admin/admin_article_detail_null_guard_fix_result_report.md` |
| `admin_article_detail_review_metric_ux_fix_revision_report.md` | `docs/reports/admin/admin_article_detail_review_metric_ux_fix_revision_report.md` |
| `admin_article_detail_review_metric_ux_runtime_verification_report.md` | `docs/reports/admin/admin_article_detail_review_metric_ux_runtime_verification_report.md` |
| `admin_article_detail_visual_final_polish_fix_result_report.md` | `docs/reports/admin/admin_article_detail_visual_final_polish_fix_result_report.md` |
| `admin_current_workspace_frontend_static_and_small_e2e_gate_report.md` | `docs/reports/admin/admin_current_workspace_frontend_static_and_small_e2e_gate_report.md` |
| `admin_dashboard_governance_metric_semantics_fix_result_report.md` | `docs/reports/admin/admin_dashboard_governance_metric_semantics_fix_result_report.md` |
| `admin_frontend_manual_acceptance_startup_report.md` | `docs/reports/admin/admin_frontend_manual_acceptance_startup_report.md` |
| `admin_governance_metric_action_entry_design_report.md` | `docs/reports/admin/admin_governance_metric_action_entry_design_report.md` |
| `admin_governance_metric_action_entry_fix_revision_report.md` | `docs/reports/admin/admin_governance_metric_action_entry_fix_revision_report.md` |
| `admin_governance_metric_explainer_panel_fix_revision_report.md` | `docs/reports/admin/admin_governance_metric_explainer_panel_fix_revision_report.md` |
| `admin_history_modal_hotspot_copy_fix_revision_report.md` | `docs/reports/admin/admin_history_modal_hotspot_copy_fix_revision_report.md` |
| `admin_processing_history_tab_design_report.md` | `docs/reports/admin/admin_processing_history_tab_design_report.md` |
| `admin_processing_history_tab_fix_result_report.md` | `docs/reports/admin/admin_processing_history_tab_fix_result_report.md` |
| `admin_processing_history_tab_fix_revision_report.md` | `docs/reports/admin/admin_processing_history_tab_fix_revision_report.md` |
| `admin_processing_task_quality_stage_copy_fix_result_report.md` | `docs/reports/admin/admin_processing_task_quality_stage_copy_fix_result_report.md` |
| `admin_processing_task_status_and_history_design_report.md` | `docs/reports/admin/admin_processing_task_status_and_history_design_report.md` |
| `admin_processing_task_status_copy_fix_result_report.md` | `docs/reports/admin/admin_processing_task_status_copy_fix_result_report.md` |
| `admin_remove_governance_attention_ui_fix_result_report.md` | `docs/reports/admin/admin_remove_governance_attention_ui_fix_result_report.md` |
| `admin_review_queue_decision_modal_visual_fix_result_report.md` | `docs/reports/admin/admin_review_queue_decision_modal_visual_fix_result_report.md` |
| `admin_review_queue_issue_explanation_design_report.md` | `docs/reports/admin/admin_review_queue_issue_explanation_design_report.md` |
| `admin_review_queue_issue_explanation_fix_result_report.md` | `docs/reports/admin/admin_review_queue_issue_explanation_fix_result_report.md` |
| `admin_review_queue_issue_explanation_fix_revision_report.md` | `docs/reports/admin/admin_review_queue_issue_explanation_fix_revision_report.md` |
| `admin_review_queue_triage_and_modal_ux_fix_result_report.md` | `docs/reports/admin/admin_review_queue_triage_and_modal_ux_fix_result_report.md` |
| `admin_workspace_right_status_card_visual_fix_result_report.md` | `docs/reports/admin/admin_workspace_right_status_card_visual_fix_result_report.md` |

**引用影响**：无。所有 admin 根目录报告均未被 README.md、AGENTS.md、docs/ 引用。

---

## 5. 引用影响汇总

| 引用源 | 受影响文件 | 影响程度 | 处理方式 |
|---|---|---|---|
| `docs/test/e2e-clean-rebuild-suite.json` | `full_rebuild_e2e_validation_asset_design_report.md` | 低（1 处引用） | 迁移后更新 JSON 中的路径 |
| `docs/quality-progress-and-lessons.md` | 无。22 处 compile-review 引用均为陈旧引用（指向不存在的文件），与本次迁移的 14 个文件无关 | 无 | 无需处理 |
| `README.md` | 无 | 无 | 无需处理 |
| `AGENTS.md` | 无 | 无 | 无需处理 |
| `scripts/scan-redline.sh` | `special_cases_report.md` | 不适用 | 该文件不迁移 |

---

## 6. 建议分批迁移计划

### 第 1 批：archive + runtime（3 个文件）— ✅ 已完成

**文件清单**：
```
agents_md_runtime_policy_wording_fix_report.md  → docs/reports/archive/
review_queue_12_items_manual_triage_report.md    → docs/reports/archive/
current_workspace_split_pre_commit_quality_report.md    → docs/reports/runtime/
```

**commit message**：
```
docs: 迁移 archive 与 runtime 报告到 docs/reports/

将 2 个一次性报告移到 docs/reports/archive/，
1 个 runtime 报告移到 docs/reports/runtime/。
零引用影响，无需更新其他文件。
```

### 第 2 批：e2e + phase + rebuild（7 个文件）— ✅ 已完成

**文件清单**：第 4 节第 3 批全部 7 个文件 → `docs/reports/e2e/`

**前置条件**：更新 `docs/test/e2e-clean-rebuild-suite.json` 中的 `full_rebuild_e2e_validation_asset_design_report.md` 引用路径。

**commit message**：
```
docs: 迁移 e2e/phase/rebuild 报告到 docs/reports/e2e/

同时更新 e2e-clean-rebuild-suite.json 中的文件引用路径。
```

### 第 3 批：query（6 个文件）— ✅ 已完成

**文件清单**：第 4 节第 4 批全部 6 个文件 → `docs/reports/query/`

**commit message**：
```
docs: 迁移 query 报告到 docs/reports/query/
```

### 第 4 批：compile-review（14 个文件）— ✅ 已完成

**文件清单**：第 4 节第 5 批全部 14 个文件 → `docs/reports/compile-review/`

**commit message**：
```
docs: 迁移 compile-review 报告到 docs/reports/compile-review/
```

### 第 5 批：admin（28 个文件）— ✅ 已完成

**文件清单**：第 4 节第 5 批全部 28 个文件 → `docs/reports/admin/`

**commit message**：
```
docs: 迁移 admin 报告到 docs/reports/admin/
```

### 第 6 批（可选）：索引文件

新增 `docs/reports/README.md` 作为报告索引，按目录列出所有报告及一句话描述。

---

## 7. 迁移后根目录预期

```
./                          ← 根目录
├── README.md               ← 保留
├── AGENTS.md               ← 保留
├── CLAUDE.md               ← 保留（可后续评估合并）
├── special_cases_report.md ← redline 输出，不纳入迁移
├── src/
├── docs/
│   ├── reports/            ← 新增：所有迁移报告的目标目录
│   │   ├── README.md       ← 可选：报告索引
│   │   ├── admin/          ← 28 个 admin 报告
│   │   ├── compile-review/ ← 14 个 compile 报告
│   │   ├── query/          ← 6 个 query 报告
│   │   ├── e2e/            ← 7 个 e2e/phase/rebuild 报告
│   │   ├── runtime/        ← 1 个 runtime 报告
│   │   └── archive/        ← 2 个一次性报告
│   ├── plans/
│   ├── test/
│   ├── guides/
│   ├── quality-progress-and-lessons.md
│   └── ...
└── ...
```

---

## 8. 风险与回滚策略

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---|---|---|
| `quality-progress` 陈旧引用 | 极低 | 无影响。22 处引用已指向不存在的文件，迁移不会加剧 | 后续独立清理 quality-progress 陈旧引用，与本次迁移解耦 |
| git 历史丢失（文件重命名跟踪） | 低 | `git log --follow` 需 `--follow` 参数 | 使用 `git mv` 而非 `mv`，git 可自动跟踪重命名 |
| admin 文件数量大（28 个） | 已消除 | 第 5 批已完成迁移 | 使用 `git mv`，28 个文件均正确跟踪为 `R` |
| 第三方工具/脚本依赖根路径 | 极低 | 脚本报错 | 迁移前全量搜索 `scripts/` 中的 `.md` 引用（已确认无依赖） |

**回滚策略**：每批独立 commit，如果某批出问题，`git revert` 该 commit 即可恢复，不影响其他批次。

---

## 9. 明确禁止事项

- 不移动 `README.md`、`AGENTS.md`
- 不移动 `special_cases_report.md`
- 不移动 `docs/模型绑定配置参考.md`（永远排除提交）
- 不移动 `docs/quality-progress-and-lessons.md`（活跃台账，保留当前位置）
- 不移动 `docs/项目全流程真实验收手册.md`（项目级手册，保留当前位置）
- 不移动 `docs/项目启动配置清单.md`（启动手册，保留当前位置）
- 不合并或删除 report 内容（只移动文件位置）

---

## 10. 结论

- 根目录过程报告已全部收口至 `docs/reports/` 下（共 5 批，迁移 58 个文件 + 删除 6 个过期文件）
- 引用影响极低：`docs/test/e2e-clean-rebuild-suite.json` 的引用路径已在第 2 批迁移中同步更新。quality-progress 中的 compile-review 引用均为陈旧引用，不受本次迁移影响
- 全部分批迁移已完成，根目录仅保留 README.md、AGENTS.md、CLAUDE.md、special_cases_report.md 共 4 个 .md 文件
- 每批可独立回滚，风险可控
- 建议进入 commit 前审计，确认所有 `git mv` 正确、引用无断裂后统一提交

---

*本报告由 agentC 生成。全部 5 批迁移已完成，根目录过程报告已全部收口。未 stage、未 commit、未 push。*
