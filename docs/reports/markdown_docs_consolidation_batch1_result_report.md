# Markdown 文档收口 — 第 1 批迁移结果报告

**执行时间**：2026-05-27
**执行 Agent**：agentC（文档治理 Agent）
**依据**：`docs/reports/markdown_docs_consolidation_design_report.md` 第 1 批
**约束声明**：仅 `git mv` 3 个文件，未 stage、未 commit、未 push。

---

## 1. 设计报告口径修正（迁移前已完成）

上一轮已将 `markdown_docs_consolidation_design_report.md` 同步为删除 6 个过期文件后的准确版本。本轮无需额外修改设计报告。

| 修正项 | 旧值 | 新值 |
|---|---|---|
| 根目录 Markdown 总量 | 68 | 62（迁移前）/ 59（迁移后） |
| admin 报告数 | 32 | 28 |
| runtime/workspace 报告数 | 3 | 1 |
| 第 1 批文件数 | 5 | 3 |
| 第 5 批文件数 | 32 | 28 |
| 根目录过程报告总数 | 64 | 58 |

---

## 2. 已迁移文件（3/3）

| # | 源文件（根目录） | 目标路径 | 状态 |
|---|---|---|---|
| 1 | `agents_md_runtime_policy_wording_fix_report.md` | `docs/reports/archive/agents_md_runtime_policy_wording_fix_report.md` | `R` (renamed) |
| 2 | `review_queue_12_items_manual_triage_report.md` | `docs/reports/archive/review_queue_12_items_manual_triage_report.md` | `R` (renamed) |
| 3 | `current_workspace_split_pre_commit_quality_report.md` | `docs/reports/runtime/current_workspace_split_pre_commit_quality_report.md` | `R` (renamed) |

全部使用 `git mv` 执行，git 自动跟踪重命名（`R` 状态）。

---

## 3. 引用完整性校验

| 已迁移文件 | 意外引用断裂 |
|---|---|
| `agents_md_runtime_policy_wording_fix_report.md` | 无 |
| `review_queue_12_items_manual_triage_report.md` | 无 |
| `current_workspace_split_pre_commit_quality_report.md` | 无 |

三个文件均未被 quality-progress、plans、README、AGENTS 或其他 KEEP 级文件引用。迁移不产生断裂引用。

---

## 4. git status 快照

```
 R agents_md_runtime_policy_wording_fix_report.md -> docs/reports/archive/...
 R review_queue_12_items_manual_triage_report.md -> docs/reports/archive/...
 R current_workspace_split_pre_commit_quality_report.md -> docs/reports/runtime/...
```

6 个预存 ` D`（第 0 批删除）与 2 个预存 ` M` 与本次迁移无关。

---

## 5. 当前剩余可迁移文件：55 个

| 批次 | 类别 | 文件数 | 引用风险 | 状态 |
|---|---|---|---|---|
| ~~第1批~~ | ~~archive + runtime~~ | ~~3~~ | — | **已完成** |
| 第 2 批 | e2e / phase / rebuild | 7 | 1 处 JSON 引用需更新 | 待执行 |
| 第 3 批 | query | 6 | 零引用 | 待执行 |
| 第 4 批 | compile-review | 14 | 零引用 | 待执行 |
| 第 5 批 | admin | 28 | 零引用 | 待执行 |
| **合计** | | **55** | | |

---

## 6. 下一步建议

**第 2 批（e2e/phase/rebuild，7 个文件）**，需先更新 `docs/test/e2e-clean-rebuild-suite.json` 中 1 处引用路径：

```
full_rebuild_e2e_validation_asset_design_report.md
→ docs/reports/e2e/full_rebuild_e2e_validation_asset_design_report.md
```

更新 JSON 引用后即可执行 `git mv`。

---

## 7. 未触碰范围

| 范围 | 状态 |
|---|---|
| 所有 ARCHIVE/KEEP 候选 | 未触碰 |
| `docs/quality-progress-and-lessons.md` | 未触碰 |
| `docs/模型绑定配置参考.md` | 未触碰、未读取 |
| `special_cases_report.md` | 未触碰 |
| `archived_reports/` | 未触碰 |
| `stale_docs_cleanup_audit_report.md` | 未修改 |
| `stale_docs_cleanup_delete_result_report.md` | 未修改 |
| `src/**`、`scripts/**` | 未触碰 |

---

*本报告由 agentC 生成。仅 `git mv` 3 个文件，未 stage、未 commit、未 push。*
