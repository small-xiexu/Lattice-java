# Markdown 文档收口 — 第 5 批迁移结果报告

**执行时间**：2026-05-28
**执行 Agent**：agentC（文档治理 Agent）
**依据**：`docs/reports/markdown_docs_consolidation_design_report.md` 第 5 批
**约束声明**：仅 `git mv` 28 个文件 + 更新设计报告。未 stage、未 commit、未 push。

---

## 1. 设计报告修正（迁移前）

| 修正项 | 位置 | 旧值 | 新值 |
|---|---|---|---|
| 精确迁移映射批次编号 | 第 4 节 | `第 6 批：admin（28 个文件）` | `第 5 批：admin（28 个文件）` |
| 分批迁移计划索引引用 | 第 6 节 | `第 4 节第 6 批` | `第 4 节第 5 批` |

---

## 2. 已迁移文件（28/28）

| # | 源文件（根目录） | 目标路径 | 状态 |
|---|---|---|---|
| 1 | `admin_article_detail_and_ask_visual_polish_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 2 | `admin_article_detail_keyword_metadata_display_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 3 | `admin_article_detail_keyword_metadata_display_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 4 | `admin_article_detail_layout_visual_polish_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 5 | `admin_article_detail_null_guard_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 6 | `admin_article_detail_review_metric_ux_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 7 | `admin_article_detail_review_metric_ux_runtime_verification_report.md` | `docs/reports/admin/` | `R` |
| 8 | `admin_article_detail_visual_final_polish_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 9 | `admin_current_workspace_frontend_static_and_small_e2e_gate_report.md` | `docs/reports/admin/` | `R` |
| 10 | `admin_dashboard_governance_metric_semantics_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 11 | `admin_frontend_manual_acceptance_startup_report.md` | `docs/reports/admin/` | `R` |
| 12 | `admin_governance_metric_action_entry_design_report.md` | `docs/reports/admin/` | `R` |
| 13 | `admin_governance_metric_action_entry_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 14 | `admin_governance_metric_explainer_panel_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 15 | `admin_history_modal_hotspot_copy_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 16 | `admin_processing_history_tab_design_report.md` | `docs/reports/admin/` | `R` |
| 17 | `admin_processing_history_tab_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 18 | `admin_processing_history_tab_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 19 | `admin_processing_task_quality_stage_copy_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 20 | `admin_processing_task_status_and_history_design_report.md` | `docs/reports/admin/` | `R` |
| 21 | `admin_processing_task_status_copy_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 22 | `admin_remove_governance_attention_ui_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 23 | `admin_review_queue_decision_modal_visual_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 24 | `admin_review_queue_issue_explanation_design_report.md` | `docs/reports/admin/` | `R` |
| 25 | `admin_review_queue_issue_explanation_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 26 | `admin_review_queue_issue_explanation_fix_revision_report.md` | `docs/reports/admin/` | `R` |
| 27 | `admin_review_queue_triage_and_modal_ux_fix_result_report.md` | `docs/reports/admin/` | `R` |
| 28 | `admin_workspace_right_status_card_visual_fix_result_report.md` | `docs/reports/admin/` | `R` |

全部使用 `git mv` 执行，git 正确跟踪为 `R`（rename）。

---

## 3. 迁移后设计报告同步（含最终收口）

| 更新项 | 旧值 | 新值 |
|---|---|---|
| 约束声明 | 第 1-4 批已完成，剩余 1 批 | 全部 5 批迁移已完成，根目录已收口 |
| 根目录 Markdown 总量 | 32 | 4 |
| admin 分类 | 28 | 0（已迁移至 `docs/reports/admin/`） |
| 分类表标题 | 根目录 32 个文件分类 | 根目录 4 个文件分类 |
| 第 5 批状态 | 零引用风险 | ✅ 已完成 |
| 结论待迁移文件数 | 28 | 0 |
| 结论描述 | 剩余 1 批待执行 | 全部收口完成，建议 commit 前审计 |
| 页脚 | 剩余 1 批（admin）待执行 | 全部 5 批迁移已完成 |

---

## 4. 引用完整性校验

### 4.1 引用来源汇总

| 引用来源类型 | 涉及文件数 | 判定 |
|---|---|---|
| `stale_docs_cleanup_audit_report.md` | 28/28 | 审计报告清单条目，预期内 |
| `markdown_docs_consolidation_design_report.md` | 28/28 | 设计报告映射表条目，预期内 |
| 同批次 admin 文件互引用 | 16/28 | 裸文件名。所有文件同目录（`docs/reports/admin/`），自然解析，无断裂 |
| `docs/reports/e2e/phase_current_workspace_pending_fixes.md` | 6/28 | 反引号包裹的软引用（纯文本文件名提及），非 Markdown 超链接，无断裂 |

### 4.2 软引用验证（e2e → admin）

| 引用源文件 | 提及的 admin 文件 | 引用格式 | 判定 |
|---|---|---|---|
| `phase_current_workspace_pending_fixes.md` | `admin_article_detail_keyword_metadata_display_fix_result_report.md` | `` `...` `` 反引号 | 软引用 |
| 同上 | `admin_governance_metric_action_entry_design_report.md` | `` `...` `` 反引号 | 软引用 |
| 同上 | `admin_processing_history_tab_design_report.md` | `` `...` `` 反引号 | 软引用 |
| 同上 | `admin_processing_task_status_copy_fix_result_report.md` | `` `...` `` 反引号 | 软引用 |
| 同上 | `admin_review_queue_issue_explanation_fix_result_report.md` | `` `...` `` 反引号 | 软引用 |
| 同上 | `admin_review_queue_issue_explanation_fix_revision_report.md` | `` `...` `` 反引号 | 软引用 |

均为反引号文本提及（如 `修复报告：\`xxx.md\``），非 `[text](path)` 功能链接，不构成引用断裂。

### 4.3 未受影响的引用

| 范围 | 结论 |
|---|---|
| `docs/quality-progress-and-lessons.md` | 未触碰。admin 相关引用不在迁移范围内 |
| `README.md`、`AGENTS.md`、`CLAUDE.md` | 零引用 |
| `scripts/` | 零引用 |
| `docs/test/` | 零引用 |

**判定**：零引用断裂。

---

## 5. git status 快照

```
R  admin_article_detail_and_ask_visual_polish_fix_result_report.md -> docs/reports/admin/...
R  admin_article_detail_keyword_metadata_display_fix_result_report.md -> docs/reports/admin/...
R  admin_article_detail_keyword_metadata_display_fix_revision_report.md -> docs/reports/admin/...
... (共 28 个 admin R)
```

累计：6 个 ` D`（第 0 批）+ 3 个 `R`（第 1 批）+ 7 个 `R`（第 2 批）+ 6 个 `R`（第 3 批）+ 14 个 `R`（第 4 批）+ 28 个 `R`（第 5 批）= **58 个 `R`**。

---

## 6. 迁移完成汇总

| 批次 | 类别 | 文件数 | 目标目录 | 引用断裂 | 状态 |
|---|---|---|---|---|---|
| 第 0 批 | DELETE | 6 | — | 无 | ✅ |
| 第 1 批 | archive + runtime | 3 | `docs/reports/archive/`, `docs/reports/runtime/` | 无 | ✅ |
| 第 2 批 | e2e / phase / rebuild | 7 | `docs/reports/e2e/` | 无（JSON 引用已同步更新） | ✅ |
| 第 3 批 | query | 6 | `docs/reports/query/` | 无 | ✅ |
| 第 4 批 | compile-review | 14 | `docs/reports/compile-review/` | 无 | ✅ |
| 第 5 批 | admin | 28 | `docs/reports/admin/` | 无 | ✅ |
| **合计** | | **58 迁移 + 6 删除** | | | |

---

## 7. 根目录最终状态

仅剩 4 个 .md 文件：

| 文件 | 说明 |
|---|---|
| `README.md` | 项目入口文档 |
| `AGENTS.md` | Agent 行为规范 |
| `CLAUDE.md` | 项目级 Claude 指令 |
| `special_cases_report.md` | redline 脚本输出（不纳入迁移） |

`docs/模型绑定配置参考.md` 不在根目录，不纳入根目录统计。

---

## 8. 剩余可迁移文件：0

全部迁移批次已完成。根目录过程报告 58 个文件已全部收口至 `docs/reports/` 的 6 个子目录中。

---

## 9. 下一步：commit 前审计

所有 `git mv` 已完成，引用完整性已校验。建议 commit 前确认：

1. `git status` 显示 64 个变更（6 `D` + 58 `R`），全部为预期内的删除和重命名
2. `docs/test/e2e-clean-rebuild-suite.json` 中 JSON 引用已在第 2 批同步更新
3. 无意外引用断裂
4. 所有迁移结果报告（batch1-5）均在 `docs/reports/` 中

可以按批次独立提交，或合并为一个 commit：

```
docs: 收口全部过程报告至 docs/reports/

- 删除 6 个过期文件
- 迁移 58 个过程报告到 docs/reports/ 的 6 个子目录
- 更新 e2e-clean-rebuild-suite.json 中的引用路径
- 根目录仅保留 README.md、AGENTS.md、CLAUDE.md、special_cases_report.md
```

---

## 10. 未触碰范围

| 范围 | 状态 |
|---|---|
| 所有 ARCHIVE/KEEP 候选 | 未触碰 |
| `docs/quality-progress-and-lessons.md` | 未触碰 |
| `docs/模型绑定配置参考.md` | 未触碰、未读取 |
| `special_cases_report.md` | 未触碰 |
| `archived_reports/` | 未触碰 |
| `stale_docs_cleanup_audit_report.md` | 未修改 |
| `stale_docs_cleanup_delete_result_report.md` | 未修改 |
| `batch1-4_result_report.md` | 未修改 |
| `src/**`、`scripts/**` | 未触碰 |

---

*本报告由 agentC 生成。全部 5 批迁移已完成。仅 `git mv` 28 个文件 + 更新设计报告。未 stage、未 commit、未 push。*
