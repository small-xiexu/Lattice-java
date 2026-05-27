# Markdown 文档收口 — 第 2 批迁移结果报告

**执行时间**：2026-05-28
**执行 Agent**：agentC（文档治理 Agent）
**依据**：`docs/reports/markdown_docs_consolidation_design_report.md` 第 2 批
**约束声明**：仅 `git mv` 7 个文件 + 更新 1 处 JSON 引用 + 更新设计报告口径。未 stage、未 commit、未 push。

---

## 1. 引用更新

| 文件 | 更新内容 |
|---|---|
| `docs/test/e2e-clean-rebuild-suite.json` | `full_rebuild_e2e_validation_asset_design_report.md` → `docs/reports/e2e/full_rebuild_e2e_validation_asset_design_report.md`（line 4） |

已确认 JSON 中引用路径指向新位置。

---

## 2. 已迁移文件（7/7）

| # | 源文件（根目录） | 目标路径 | 状态 |
|---|---|---|---|
| 1 | `e2e_clean_rebuild_suite_creation_report.md` | `docs/reports/e2e/e2e_clean_rebuild_suite_creation_report.md` | `R` |
| 2 | `full_rebuild_e2e_validation_asset_design_report.md` | `docs/reports/e2e/full_rebuild_e2e_validation_asset_design_report.md` | `R` |
| 3 | `full_rebuild_e2e_validation_runtime_report.md` | `docs/reports/e2e/full_rebuild_e2e_validation_runtime_report.md` | `R` |
| 4 | `phase_compile_query_rebuild_acceptance_report.md` | `docs/reports/e2e/phase_compile_query_rebuild_acceptance_report.md` | `R` |
| 5 | `phase_compile_query_stage_acceptance_report.md` | `docs/reports/e2e/phase_compile_query_stage_acceptance_report.md` | `R` |
| 6 | `phase_current_workspace_existing_cases_acceptance_report.md` | `docs/reports/e2e/phase_current_workspace_existing_cases_acceptance_report.md` | `R` |
| 7 | `phase_current_workspace_pending_fixes.md` | `docs/reports/e2e/phase_current_workspace_pending_fixes.md` | `R` |

全部使用 `git mv` 执行，git 正确跟踪为 `R`（rename）。

---

## 3. 设计报告同步修正

| 修正项 | 旧值 | 新值 |
|---|---|---|
| 根目录 Markdown 总量 | 62 | 52 |
| 分类表 phase 报告 | 4 | 0（已迁移） |
| 分类表 e2e/rebuild 报告 | 3 | 0（已迁移） |
| 分类表 runtime/workspace | 1 | 0（第 1 批已迁移） |
| 分类表 其他 | 2 | 0（第 1 批已迁移） |
| 根目录过程报告待迁移数 | 58 | 48 |
| 结论 剩余批次 | 5+1 批 | 3 批 |
| 第 1 批状态 | 待执行 | ✅ 已完成 |
| 第 2 批状态 | 待执行 | ✅ 已完成 |
| 下一步建议 | 第 1 批 | 第 3 批（query） |

---

## 4. 引用完整性校验

| 已迁移文件 | JSON 引用 | 根目录互引用 | archived_reports 引用 |
|---|---|---|---|
| `e2e_clean_rebuild_suite_creation_report.md` | — | 无 | 无 |
| `full_rebuild_e2e_validation_asset_design_report.md` | ✅ 已更新 | 无 | 无 |
| `full_rebuild_e2e_validation_runtime_report.md` | — | 无 | 无 |
| `phase_compile_query_rebuild_acceptance_report.md` | — | 无 | 无 |
| `phase_compile_query_stage_acceptance_report.md` | — | 2 处（`compile_review_phase_report_cleanup_result.md`、`query_partial_answer_completeness_analysis_report.md`） | 3 处（`archived_reports/compile_review_queue_*`） |
| `phase_current_workspace_existing_cases_acceptance_report.md` | — | 1 处（`admin_current_workspace_frontend_static_and_small_e2e_gate_report.md`） | 无 |
| `phase_current_workspace_pending_fixes.md` | — | 5 处（`admin_*` 根目录报告） | 无 |

**判定**：所有引用均为其他待迁移根目录文件（后续批次）或 `archived_reports/` 历史文件。这些引用使用裸文件名（无路径前缀），当所有根目录文件迁移到 `docs/reports/` 后，相对路径将自然保持有效。不存在意外引用断裂。

---

## 5. git status 快照

```
R  e2e_clean_rebuild_suite_creation_report.md -> docs/reports/e2e/...
R  full_rebuild_e2e_validation_asset_design_report.md -> docs/reports/e2e/...
R  full_rebuild_e2e_validation_runtime_report.md -> docs/reports/e2e/...
R  phase_compile_query_rebuild_acceptance_report.md -> docs/reports/e2e/...
R  phase_compile_query_stage_acceptance_report.md -> docs/reports/e2e/...
R  phase_current_workspace_existing_cases_acceptance_report.md -> docs/reports/e2e/...
R  phase_current_workspace_pending_fixes.md -> docs/reports/e2e/...
```

累计：6 个 ` D`（第 0 批删除）+ 3 个 `R`（第 1 批）+ 7 个 `R`（第 2 批）。

---

## 6. 当前剩余可迁移文件：48 个

| 批次 | 类别 | 文件数 | 引用风险 | 状态 |
|---|---|---|---|---|
| ~~第 1 批~~ | ~~archive + runtime~~ | ~~3~~ | — | ✅ 已完成 |
| ~~第 2 批~~ | ~~e2e / phase / rebuild~~ | ~~7~~ | — | ✅ 已完成 |
| 第 3 批 | query | 6 | 零引用 | **下一步** |
| 第 4 批 | compile-review | 14 | 零引用 | 待执行 |
| 第 5 批 | admin | 28 | 零引用 | 待执行 |
| **合计** | | **48** | | |

---

## 7. 下一步建议

**第 3 批（query，6 个文件）**，零引用风险，无需更新任何引用即可直接执行 `git mv`。建议 commit message：

```
docs: 迁移 query 报告到 docs/reports/query/
```

---

## 8. 未触碰范围

| 范围 | 状态 |
|---|---|
| 所有 DELETE/ARCHIVE/KEEP 候选 | 未触碰 |
| `docs/quality-progress-and-lessons.md` | 未触碰 |
| `docs/模型绑定配置参考.md` | 未触碰、未读取 |
| `special_cases_report.md` | 未触碰 |
| `archived_reports/` | 未触碰 |
| `stale_docs_cleanup_audit_report.md` | 未修改 |
| `stale_docs_cleanup_delete_result_report.md` | 未修改 |
| `batch1_result_report.md` | 未修改 |
| `src/**`、`scripts/**` | 未触碰 |

---

*本报告由 agentC 生成。仅 `git mv` 7 个文件 + 更新 1 处 JSON 引用 + 更新设计报告口径。未 stage、未 commit、未 push。*
