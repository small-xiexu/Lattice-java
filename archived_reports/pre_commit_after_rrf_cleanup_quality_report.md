# Pre-Commit Quality Report — RRF Cleanup 收口后

- **生成时间**: 2026-05-16 10:45 +0800
- **分支**: `codex/qa-polish`
- **Git commit**: d450796
- **本轮是否修改代码**: 否

## 1. Redline

| 指标 | 值 |
|------|----|
| BLOCKER | 0 |
| REVIEW | 已标记 |
| ALLOWLIST | 已标记 |
| Exit code | 0 |
| 结论 | 通过 |

## 2. mvn test

| 指标 | 值 |
|------|----|
| Tests run | 811 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Build | SUCCESS |
| 耗时 | 06:03 min |

## 3. Git diff 分类

### 3.1 已跟踪文件变更（12 个）

| 状态 | 文件 | 分类 | 说明 |
|------|------|------|------|
| M | `AGENTS.md` | 文档 | +49 行，新增多 Agent 协作规范、角色分工、并行规则、提示词分发规则、失败处理、质量打磨台账引用 |
| D | `deep_research_fact_card_anchor_fix_result_report.md` | 报告删除 | 过期中间报告 |
| D | `deep_research_graph_fact_projection_fix_result_report.md` | 报告删除 | 过期中间报告 |
| D | `query_baseline_exact_path_grounding_fix_result_report.md` | 报告删除 | 过期中间报告 |
| D | `query_baseline_ocr_eval_expectation_update_report.md` | 报告删除 | 过期中间报告 |
| D | `query_baseline_ocr_runtime_source_fix_result_report.md` | 报告删除 | 过期中间报告 |
| D | `swip_baseline_report.md` | 报告删除 | 过期中间报告 |
| D | `swip_compile_coverage_analysis_report.md` | 报告删除 | 过期中间报告 |
| D | `swip_docx_extraction_comparison_report.md` | 报告删除 | 过期中间报告 |
| D | `swip_embedding_regression_case_analysis_report.md` | 报告删除 | 过期中间报告 |
| M | `docs/test/swip-query-eval-candidates.json` | 题集增强 | +333/-1 行，为多个 case 补充 `expect` 块（requiredAnswerTerms / expectedRetrievalTargets / forbiddenAnswerTerms） |
| M | `special_cases_report.md` | redline 刷新 | 仅扫描时间戳变化 (2026-05-15 → 2026-05-16)，规则和 allowlist 未变 |

### 3.2 未跟踪文件（??）

| 文件 | 类型 |
|------|------|
| `compile_article_review_flow_runtime_audit_report.md` | 只读审计报告 |
| `compile_review_governance_design_report.md` | 治理设计报告 |
| `docs/multi-agent-model-routing-guide.md` | 文档 |
| `docs/quality-progress-and-lessons.md` | 质量台账文档 |
| `report_cleanup_after_rrf_revert_result.md` | 清理执行报告 |
| `report_cleanup_plan_after_swip_rrf.md` | 清理规划 |
| `swip_answer_grounding_failure_analysis_report.md` | 失败归因报告 |
| `swip_eval_expectation_adjustment_report.md` | 评估调整报告 |
| `swip_rrf_retained_content_revert_report.md` | RRF 回退报告 |
| `swip_rrf_revert_stability_verification_report.md` | 稳定性验证报告 |

### 3.3 生产代码 diff

**无。** `src/main/java/**` 零变更。

## 4. RrfFusionService.java

`git diff -- src/main/java/com/xbk/lattice/query/service/RrfFusionService.java` 输出为空。

**确认无 diff。**

## 5. scripts/scan-redline.sh

`git diff -- scripts/scan-redline.sh` 输出为空。

**确认无 diff。**

## 6. special_cases_report.md 变更内容

仅有一处变更：

```diff
-- 扫描时间：2026-05-15 17:38:59 +0800
+- 扫描时间：2026-05-16 09:56:09 +0800
```

**确认只是扫描结果刷新（时间戳更新），不是规则或 allowlist 修改。**

## 7. 是否可以进入阶段性提交

**可以进入提交。**

无阻塞项：
- Redline BLOCKER = 0
- mvn test 811/0/0/0
- 生产代码零变更
- RrfFusionService.java 无 diff
- scripts/scan-redline.sh 无 diff
- special_cases_report.md 仅时间戳刷新
- 所有变更均为文档、题集增强、过期报告删除、redline 扫描刷新

## 8. 阻塞项清单

无。
