# Markdown 文档收口 — 第 4 批迁移结果报告

**执行时间**：2026-05-28
**执行 Agent**：agentC（文档治理 Agent）
**依据**：`docs/reports/markdown_docs_consolidation_design_report.md` 第 4 批
**约束声明**：仅 `git mv` 14 个文件 + 更新设计报告。未 stage、未 commit、未 push。

---

## 1. 设计报告修正（迁移前）

| 修正项 | 位置 | 旧值 | 新值 |
|---|---|---|---|
| 删除草稿段 | 第 4 节 query 映射表后 | `Wait, that's 6 files for query:` 及下面重复列 query 文件的 10 行 | 已删除 |
| 页脚口径 | 报告末尾 | "第 1 批（archive+runtime）、第 2 批（e2e/phase/rebuild）迁移已完成。剩余 3 批待执行" | "第 1-3 批迁移已完成。剩余 2 批（compile-review、admin）待执行" |
| 根目录标题 | 第 0 节 | "根目录 46 个文件分类" | "根目录 32 个文件分类" |

---

## 2. 已迁移文件（14/14）

| # | 源文件（根目录） | 目标路径 | 状态 |
|---|---|---|---|
| 1 | `compile_fixer_payload_slimming_fix_result_report.md` | `docs/reports/compile-review/compile_fixer_payload_slimming_fix_result_report.md` | `R` |
| 2 | `compile_review_fix_loop_performance_analysis_report.md` | `docs/reports/compile-review/compile_review_fix_loop_performance_analysis_report.md` | `R` |
| 3 | `compile_review_phase_report_cleanup_result.md` | `docs/reports/compile-review/compile_review_phase_report_cleanup_result.md` | `R` |
| 4 | `compile_review_phase_status.md` | `docs/reports/compile-review/compile_review_phase_status.md` | `R` |
| 5 | `compile_review_queue_approve_idempotency_fix_result_report.md` | `docs/reports/compile-review/compile_review_queue_approve_idempotency_fix_result_report.md` | `R` |
| 6 | `compile_review_queue_approve_idempotency_runtime_verification_report.md` | `docs/reports/compile-review/compile_review_queue_approve_idempotency_runtime_verification_report.md` | `R` |
| 7 | `compile_review_queue_dedup_fix_result_report.md` | `docs/reports/compile-review/compile_review_queue_dedup_fix_result_report.md` | `R` |
| 8 | `compile_review_queue_dedup_runtime_verification_report.md` | `docs/reports/compile-review/compile_review_queue_dedup_runtime_verification_report.md` | `R` |
| 9 | `compile_reviewer_payload_slimming_fix_result_report.md` | `docs/reports/compile-review/compile_reviewer_payload_slimming_fix_result_report.md` | `R` |
| 10 | `compile_reviewer_payload_slimming_runtime_verification_report.md` | `docs/reports/compile-review/compile_reviewer_payload_slimming_runtime_verification_report.md` | `R` |
| 11 | `compile_writer_payload_budget_slimming_fix_result_report.md` | `docs/reports/compile-review/compile_writer_payload_budget_slimming_fix_result_report.md` | `R` |
| 12 | `compile_writer_payload_budget_slimming_runtime_verification_report.md` | `docs/reports/compile-review/compile_writer_payload_budget_slimming_runtime_verification_report.md` | `R` |
| 13 | `compile_writer_unit_routing_gate_fix_result_report.md` | `docs/reports/compile-review/compile_writer_unit_routing_gate_fix_result_report.md` | `R` |
| 14 | `compile_writer_unit_routing_gate_full_runtime_verification_report.md` | `docs/reports/compile-review/compile_writer_unit_routing_gate_full_runtime_verification_report.md` | `R` |

全部使用 `git mv` 执行，git 正确跟踪为 `R`（rename）。

---

## 3. 迁移后设计报告同步

| 更新项 | 旧值 | 新值 |
|---|---|---|
| 约束声明 | 第 1-3 批已完成，剩余 2 批 | 第 1-4 批已完成，剩余 1 批（admin） |
| 根目录 Markdown 总量 | 46 | 32 |
| compile-review 分类 | 14 | 0（已迁移至 `docs/reports/compile-review/`） |
| 第 4 批状态 | 零引用风险 | ✅ 已完成 |
| 结论待迁移文件数 | 42 | 28 |
| 结论剩余批次 | 2 批（compile-review、admin） | 1 批（admin） |
| 结论建议 | 第 4 批 | 第 5 批（admin） |
| 页脚 | 剩余 2 批（compile-review、admin）待执行 | 剩余 1 批（admin）待执行 |

---

## 4. 引用完整性校验

### 4.1 引用来源分类

| 引用来源类型 | 涉及文件 | 判定 |
|---|---|---|
| `stale_docs_cleanup_audit_report.md` | 14/14 | 审计报告清单条目，预期内 |
| `markdown_docs_consolidation_design_report.md` | 14/14 | 设计报告映射表条目，预期内 |
| 同批次 compile-review 文件互引用 | 10/14 | 裸文件名。所有文件同目录（`docs/reports/compile-review/`），裸文件名自然解析，无断裂 |
| `markdown_docs_consolidation_batch2_result_report.md` | `compile_review_phase_report_cleanup_result` | 引用完整性记录，预期内 |
| `archived_reports/` 历史文件 | 8/14 | 裸文件名提及。archived_reports 为历史文件，不构成功能性依赖 |

### 4.2 未受影响的引用

| 范围 | 结论 |
|---|---|
| `docs/quality-progress-and-lessons.md` | 22 处 compile-review 引用均指向已不存在的陈旧文件名，与本次迁移无关。未触碰 |
| `README.md`、`AGENTS.md`、`CLAUDE.md` | 零引用 |
| `scripts/` | 零引用 |
| `docs/test/` | 零引用（batch2 报告中的提及为引用记录，非功能性引用） |

**判定**：零引用断裂。

---

## 5. git status 快照

```
R  compile_fixer_payload_slimming_fix_result_report.md -> docs/reports/compile-review/...
R  compile_review_fix_loop_performance_analysis_report.md -> docs/reports/compile-review/...
R  compile_review_phase_report_cleanup_result.md -> docs/reports/compile-review/...
R  compile_review_phase_status.md -> docs/reports/compile-review/...
R  compile_review_queue_approve_idempotency_fix_result_report.md -> docs/reports/compile-review/...
R  compile_review_queue_approve_idempotency_runtime_verification_report.md -> docs/reports/compile-review/...
R  compile_review_queue_dedup_fix_result_report.md -> docs/reports/compile-review/...
R  compile_review_queue_dedup_runtime_verification_report.md -> docs/reports/compile-review/...
R  compile_reviewer_payload_slimming_fix_result_report.md -> docs/reports/compile-review/...
R  compile_reviewer_payload_slimming_runtime_verification_report.md -> docs/reports/compile-review/...
R  compile_writer_payload_budget_slimming_fix_result_report.md -> docs/reports/compile-review/...
R  compile_writer_payload_budget_slimming_runtime_verification_report.md -> docs/reports/compile-review/...
R  compile_writer_unit_routing_gate_fix_result_report.md -> docs/reports/compile-review/...
R  compile_writer_unit_routing_gate_full_runtime_verification_report.md -> docs/reports/compile-review/...
```

累计：6 个 ` D`（第 0 批）+ 3 个 `R`（第 1 批）+ 7 个 `R`（第 2 批）+ 6 个 `R`（第 3 批）+ 14 个 `R`（第 4 批）= 30 个 `R`。

---

## 6. 当前剩余可迁移文件：28 个

| 批次 | 类别 | 文件数 | 引用风险 | 状态 |
|---|---|---|---|---|
| ~~第 1 批~~ | ~~archive + runtime~~ | ~~3~~ | — | ✅ 已完成 |
| ~~第 2 批~~ | ~~e2e / phase / rebuild~~ | ~~7~~ | — | ✅ 已完成 |
| ~~第 3 批~~ | ~~query~~ | ~~6~~ | — | ✅ 已完成 |
| ~~第 4 批~~ | ~~compile-review~~ | ~~14~~ | — | ✅ 已完成 |
| 第 5 批 | admin | 28 | 零引用 | **下一步** |
| **合计** | | **28** | | |

---

## 7. 下一步建议

**第 5 批（admin，28 个文件）**，零引用风险，无需更新任何外部引用即可直接执行 `git mv`。建议 commit message：

```
docs: 迁移 admin 报告到 docs/reports/admin/
```

至此，全部 5 批迁移将完成，根目录仅保留 3 个必须文件（README.md、AGENTS.md、CLAUDE.md）+ 1 个 redline 输出（special_cases_report.md），共 4 个 .md 文件。

---

## 8. 未触碰范围

| 范围 | 状态 |
|---|---|
| 所有 ARCHIVE/KEEP 候选 | 未触碰 |
| `docs/quality-progress-and-lessons.md` | 未触碰 |
| `docs/模型绑定配置参考.md` | 未触碰、未读取 |
| `special_cases_report.md` | 未触碰 |
| `archived_reports/` | 未触碰 |
| `stale_docs_cleanup_audit_report.md` | 未修改 |
| `stale_docs_cleanup_delete_result_report.md` | 未修改 |
| `batch1_result_report.md` | 未修改 |
| `batch2_result_report.md` | 未修改 |
| `batch3_result_report.md` | 未修改 |
| `src/**`、`scripts/**` | 未触碰 |

---

*本报告由 agentC 生成。仅 `git mv` 14 个文件 + 更新设计报告。未 stage、未 commit、未 push。*
