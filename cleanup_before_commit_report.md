# 提交前文件清理报告

生成时间：2026-05-14
分支：`codex/qa-polish`

## 1. 删除的中间报告

| # | 文件名 | 删除原因 |
|---|---|---|
| 1 | `citation_validator_q_mq_fix_design_correction_report.md` | CitationValidator 设计讨论文档，修复已落地到代码，结论已纳入 gate 报告 |
| 2 | `cleanup_reports_result_report.md` | 报告清理的元报告，内容已并入 pre_commit_quality_review_report |
| 3 | `deep_research_fact_card_anchor_design_report.md` | Deep Research FACT_CARD 设计文档，修复已落地，设计要点在 fix_result_report 中已覆盖 |
| 4 | `deep_research_graph_fact_projection_design_report.md` | Deep Research 图事实投影设计文档，设计讨论性质 |
| 5 | `query_baseline_exact_path_clean_rebuild_regression_analysis_report.md` | 中间分析报告，结论已纳入 exact_path_grounding_fix_result_report |
| 6 | `query_baseline_ocr_runtime_data_analysis_report.md` | 中间分析报告，结论已纳入 ocr_runtime_source_fix_result_report |
| 7 | `query_baseline_q_mq_citation_latin_token_verification_report.md` | Citation Latin token 验证报告，结论已纳入 gate 报告 |
| 8 | `query_baseline_remaining_failures_analysis_report.md` | 中间分析报告，结论已分发到各最终修复报告 |

**共删除 8 个中间报告。**

## 2. 保留的报告与文件

### 2.1 门禁与审查报告

| # | 文件名 | 说明 |
|---|---|---|
| 1 | `final_query_baseline_gate_report.md` | 最终门禁报告（redline + mvn test + baseline gate + source files 污染检查） |
| 2 | `pre_commit_quality_review_report.md` | 提交前质量审查（变更分组、风险评估、越界检查、报告清理建议） |
| 3 | `special_cases_report.md` | Redline 扫描基线（tracked, modified） |

### 2.2 修复结果报告

| # | 文件名 | 说明 |
|---|---|---|
| 4 | `query_baseline_exact_path_grounding_fix_result_report.md` | Q-EXACT-PATH-001 exact lookup grounding 修复 |
| 5 | `query_baseline_ocr_eval_expectation_update_report.md` | Q-RUNTIME-OCR-001 eval 预期更新 |
| 6 | `query_baseline_ocr_runtime_source_fix_result_report.md` | OCR 运行态源文件新增与数据覆盖修复 |
| 7 | `deep_research_fact_card_anchor_fix_result_report.md` | Deep Research FACT_CARD anchor 修复 |
| 8 | `deep_research_graph_fact_projection_fix_result_report.md` | Deep Research 图事实投影修复 |
| 9 | `test_database_isolation_fix_result_report.md` | 测试数据库隔离修复 |

### 2.3 知识库源文件

| # | 文件名 | 说明 |
|---|---|---|
| 10 | `docs/文档识别与OCR运行态说明.md` | Q-RUNTIME-OCR-001 数据覆盖基础（untracked，需 git add） |

### 2.4 SWIP 候选题集（单独决策）

| # | 文件名 | 说明 |
|---|---|---|
| 11 | `docs/test/swip-query-eval-candidates.json` | SWIP 候选题集草案，不属于本轮 Query baseline 修复闭环 |

## 3. 禁止修改项合规检查

| 检查项 | 是否修改 |
|---|---|
| `src/main/java/**` | **否** ✅ |
| `src/test/java/**` | **否** ✅ |
| `src/main/resources/**` | **否** ✅ |
| `docs/test/query-regression-suite.json` | **否** ✅ |
| `scripts/**` | **否** ✅ |
| `AGENTS.md` / `CLAUDE.md` | **否** ✅ |
| `pom.xml` | **否** ✅ |

## 4. 关键确认

| 确认项 | 结果 |
|---|---|
| OCR 源文档是否保留 | **是** ✅ — `docs/文档识别与OCR运行态说明.md` 保留 |
| 是否修改源码 | **否** ✅ |
| 是否修改测试 | **否** ✅ |
| 是否修改 eval | **否** ✅（`docs/test/query-regression-suite.json` 未修改） |
| 是否修改配置 | **否** ✅ |
| Redline BLOCKER | **0** ✅ |
| mvn test | **811/0/0** ✅ |

## 5. SWIP 候选题集说明

`docs/test/swip-query-eval-candidates.json` 是 SWIP 候选题集草案，目前处于 untracked 状态。该文件：

- **不属于**本轮 Query baseline 修复闭环。
- **没有接入** `query-regression-suite.json` 的 case 列表。
- **不建议**与本轮 QA Polish 变更合并提交。
- 如果决定提交，应在**单独 commit** 中提交，并在 commit message 中说明其作为候选题集草案的性质和用途。

**建议**：暂不提交，留在 untracked 状态，待 SWIP 需求明确后单独处理。

## 6. 当前 untracked 文件总览

清理后，仓库根目录及 docs/ 下的 untracked 文件如下：

| 文件 | 类别 | 提交建议 |
|---|---|---|
| `final_query_baseline_gate_report.md` | 门禁报告 | 随本轮提交 |
| `pre_commit_quality_review_report.md` | 质量审查 | 随本轮提交 |
| `cleanup_before_commit_report.md` | 本报告 | 随本轮提交 |
| `query_baseline_exact_path_grounding_fix_result_report.md` | 修复报告 | 随本轮提交 |
| `query_baseline_ocr_eval_expectation_update_report.md` | 修复报告 | 随本轮提交 |
| `query_baseline_ocr_runtime_source_fix_result_report.md` | 修复报告 | 随本轮提交 |
| `deep_research_fact_card_anchor_fix_result_report.md` | 修复报告 | 随本轮提交 |
| `deep_research_graph_fact_projection_fix_result_report.md` | 修复报告 | 随本轮提交 |
| `test_database_isolation_fix_result_report.md` | 修复报告 | 随本轮提交 |
| `docs/文档识别与OCR运行态说明.md` | 知识库源文件 | **必须**随本轮提交 |
| `docs/test/swip-query-eval-candidates.json` | SWIP 候选题集 | **建议暂不提交**，单独决策 |

---

**清理结论**：8 个中间报告已删除，10 个必须保留的文件完整无损，无越界修改。仓库根目录已从清理前的 19 个 untracked `.md` 文件精简为 11 个（含本报告）。可以进入提交阶段。
