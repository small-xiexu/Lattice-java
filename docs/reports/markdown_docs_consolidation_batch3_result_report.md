# Markdown 文档收口 — 第 3 批迁移结果报告

**执行时间**：2026-05-28
**执行 Agent**：agentC（文档治理 Agent）
**依据**：`docs/reports/markdown_docs_consolidation_design_report.md` 第 3 批
**约束声明**：仅 `git mv` 6 个文件，未 stage、未 commit、未 push。

---

## 1. 已迁移文件（6/6）

| # | 源文件（根目录） | 目标路径 | 状态 |
|---|---|---|---|
| 1 | `query_citation_quality_terminal_fallback_fix_result_report.md` | `docs/reports/query/query_citation_quality_terminal_fallback_fix_result_report.md` | `R` |
| 2 | `query_citation_quality_terminal_fallback_fix_revision_report.md` | `docs/reports/query/query_citation_quality_terminal_fallback_fix_revision_report.md` | `R` |
| 3 | `query_citation_quality_terminal_fallback_runtime_verification_report.md` | `docs/reports/query/query_citation_quality_terminal_fallback_runtime_verification_report.md` | `R` |
| 4 | `query_fallback_citation_quality_root_cause_report.md` | `docs/reports/query/query_fallback_citation_quality_root_cause_report.md` | `R` |
| 5 | `query_partial_answer_completeness_analysis_report.md` | `docs/reports/query/query_partial_answer_completeness_analysis_report.md` | `R` |
| 6 | `query_partial_answer_multi_point_expansion_fix_result_report.md` | `docs/reports/query/query_partial_answer_multi_point_expansion_fix_result_report.md` | `R` |

全部使用 `git mv` 执行，git 正确跟踪为 `R`（rename）。

---

## 2. 引用完整性校验

| 已迁移文件 | 外部引用源 | 判定 |
|---|---|---|
| `query_citation_quality_terminal_fallback_fix_result_report.md` | `stale_docs_cleanup_audit_report.md`、`markdown_docs_consolidation_design_report.md` | 预期内（审计/设计报告清单条目） |
| `query_citation_quality_terminal_fallback_fix_revision_report.md` | `审计报告`、`设计报告`、`current_workspace_split_pre_commit_quality_report.md`（软引用，反引号文本提及） | 无断裂。软引用不构成超链接 |
| `query_citation_quality_terminal_fallback_runtime_verification_report.md` | `设计报告` | 预期内 |
| `query_fallback_citation_quality_root_cause_report.md` | `审计报告`、`设计报告`、`query_citation_quality_terminal_fallback_fix_result_report.md`（同批次文件，同目录内裸文件名引用） | 无断裂。同目录裸文件名自然解析 |
| `query_partial_answer_completeness_analysis_report.md` | `设计报告`、`batch2_result_report.md`（引用完整性记录） | 预期内 |
| `query_partial_answer_multi_point_expansion_fix_result_report.md` | `设计报告` | 预期内 |

**判定**：零引用断裂。所有外部引用均为审计/设计报告的清单条目或同批次文件间的裸文件名引用（已自然解析）。

---

## 3. git status 快照

```
R  query_citation_quality_terminal_fallback_fix_result_report.md -> docs/reports/query/...
R  query_citation_quality_terminal_fallback_fix_revision_report.md -> docs/reports/query/...
R  query_citation_quality_terminal_fallback_runtime_verification_report.md -> docs/reports/query/...
R  query_fallback_citation_quality_root_cause_report.md -> docs/reports/query/...
R  query_partial_answer_completeness_analysis_report.md -> docs/reports/query/...
R  query_partial_answer_multi_point_expansion_fix_result_report.md -> docs/reports/query/...
```

累计：6 个 ` D`（第 0 批）+ 3 个 `R`（第 1 批）+ 7 个 `R`（第 2 批）+ 6 个 `R`（第 3 批）。

---

## 4. 当前剩余可迁移文件：42 个

| 批次 | 类别 | 文件数 | 引用风险 | 状态 |
|---|---|---|---|---|
| ~~第 1 批~~ | ~~archive + runtime~~ | ~~3~~ | — | ✅ 已完成 |
| ~~第 2 批~~ | ~~e2e / phase / rebuild~~ | ~~7~~ | — | ✅ 已完成 |
| ~~第 3 批~~ | ~~query~~ | ~~6~~ | — | ✅ 已完成 |
| 第 4 批 | compile-review | 14 | 零引用 | 待执行 |
| 第 5 批 | admin | 28 | 零引用 | 待执行 |
| **合计** | | **42** | | |

---

## 5. 下一步建议

**第 4 批（compile-review，14 个文件）**，零引用风险，无需更新引用即可直接执行 `git mv`。建议 commit message：

```
docs: 迁移 compile-review 报告到 docs/reports/compile-review/
```

---

## 6. 未触碰范围

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
| `batch2_result_report.md` | 未修改 |
| `src/**`、`scripts/**` | 未触碰 |

---

*本报告由 agentC 生成。仅 `git mv` 6 个文件，未 stage、未 commit、未 push。*
