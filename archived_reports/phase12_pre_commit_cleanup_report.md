# Phase 12 提交前清理与最终质量复核报告

**日期**: 2026-05-15
**分支**: `codex/qa-polish`
**目标**: 提交前清理过期中间产物、复核代码质量、确认可提交
**本轮是否修改业务代码**: **否**

---

## 1. 删除的过期中间报告（12 个）

| # | 文件名 | 删除原因 |
|---|---|---|
| 1 | `phase12_gpt54_json_schema_single_case_probe_report.md` | 早期单 case 探针，已被全量报告覆盖 |
| 2 | `phase12_gpt55_zhipu_full_query_baseline_probe_report.md` | probe 轮，已被最终报告覆盖 |
| 3 | `phase12_main_query_baseline_clean_rebuild_report.md` | DeepSeek Flash 轮，已被 gpt-5.5 最终轮覆盖 |
| 4 | `phase12_main_query_baseline_deepseek_zhipu_recheck_report.md` | 中间复验，已被最终报告覆盖 |
| 5 | `phase12_main_query_baseline_t1_model_recheck_report.md` | 中间复验，已被最终报告覆盖 |
| 6 | `swip_textutil_rebuild_eval_report.md` | SWIP 中间评测，已结项 |
| 7 | `swip_ingest_text_truncation_analysis_report.md` | SWIP 中间截断分析，已结项 |
| 8 | `swip_embedding_docx_rebuild_eval_report.md` | SWIP 中间评测，已结项 |
| 9 | `swip_retrieval_settings_rrf_weight_fix_report.md` | SWIP 中间修正，已结项 |
| 10 | `swip_structured_guardrail_candidate_loss_analysis_report.md` | SWIP 中间分析，已结项 |
| 11 | `swip_structured_guardrail_vector_channel_fix_report.md` | SWIP 中间修正，已结项 |
| 12 | `swip_phase12_side_effect_review_report.md` | Phase 12 副作用审查草稿，已被最终报告覆盖 |

---

## 2. 保留的报告（5 个）

| # | 文件名 | 保留原因 |
|---|---|---|
| 1 | `phase12_final_clean_rebuild_gate_report.md` | Phase 12 最终验收报告，必须保留 |
| 2 | `swip_baseline_report.md` | SWIP 基线，可归档参考 |
| 3 | `swip_compile_coverage_analysis_report.md` | SWIP 编译覆盖率分析，可归档参考 |
| 4 | `swip_docx_extraction_comparison_report.md` | SWIP 文档提取对比，可归档参考 |
| 5 | `swip_embedding_regression_case_analysis_report.md` | SWIP embedding 回归分析，可归档参考 |

---

## 3. Redline 扫描

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

| 指标 | 值 |
|---|---|
| BLOCKER | **0** |
| REVIEW | 1830 |
| ALLOWLIST | 219 |
| EXIT_CODE | **0** |

BLOCKER=0，无业务逻辑硬编码违规。

---

## 4. Maven 测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

| 指标 | 值 |
|---|---|
| Tests run | **811** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| BUILD | **SUCCESS** (5 分 41 秒) |

**本轮 test 结果说明**：与之前 clean rebuild 轮不同（当时 Errors=219，全部为 `CannotGetJdbcConnectionException` 和 `RedisConnectionFailure`），本轮在本地 PostgreSQL + Redis 基础设施就绪的环境下运行，Errors=0。上次的 219 Errors 确认为**本地基础设施缺失导致**，非代码逻辑错误。

---

## 5. 当前待提交文件清单

### 已修改（tracked, unstaged）

| 文件 | 改动行数 | 说明 |
|---|---|---|
| `special_cases_report.md` | +6 / -5 | redline 扫描结果更新 |
| `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java` | +2 / -0 | Phase 12: article_vector/chunk_vector 纳入主证据白名单 |
| `src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java` | +8 / -5 | Phase 12: 更新测试断言匹配新证据通道行为 |

### 新增未跟踪（untracked）

| 文件 | 说明 |
|---|---|
| `phase12_final_clean_rebuild_gate_report.md` | Phase 12 最终 gate 验收报告 |
| `phase12_pre_commit_cleanup_report.md` | 本报告（提交前清理与质量复核） |
| `swip_baseline_report.md` | SWIP 基线报告（归档） |
| `swip_compile_coverage_analysis_report.md` | SWIP 编译覆盖率（归档） |
| `swip_docx_extraction_comparison_report.md` | SWIP 文档提取对比（归档） |
| `swip_embedding_regression_case_analysis_report.md` | SWIP embedding 回归（归档） |

---

## 6. 过期报告残留检查

**无残留**。12 个建议删除的过期中间报告已全部删除。保留的 5 个报告（1 个 Phase 12 最终 + 4 个 SWIP 归档）均为有意保留。

当前磁盘上的 phase12/swip 相关 md 文件：

```
phase12_final_clean_rebuild_gate_report.md    ← 保留（最终验收）
swip_baseline_report.md                       ← 保留（归档）
swip_compile_coverage_analysis_report.md      ← 保留（归档）
swip_docx_extraction_comparison_report.md     ← 保留（归档）
swip_embedding_regression_case_analysis_report.md ← 保留（归档）
```

---

## 7. 禁止修改范围检查

| 检查项 | 是否修改 | 说明 |
|---|---|---|
| RrfFusionService 新逻辑 | **否** | 仅保留已有 Phase 12 改动 |
| WeightedRrfFusionTest 新断言 | **否** | 仅保留已有 Phase 12 改动 |
| Q-DEEP 修复 | **否** | 未修改 |
| OCR 文档补充 | **否** | 未修改 |
| `docs/test/query-regression-suite.json` | **否** | 未修改 |
| `scripts/scan-redline.sh` | **否** | 未修改 |
| redline allowlist | **否** | 未修改 |
| Query 主链 | **否** | 未修改 |
| 其他 src/main/java/** | **否** | 未修改 |
| 其他 src/test/java/** | **否** | 未修改 |

---

## 8. 提交建议

| 判断项 | 结论 |
|---|---|
| redline BLOCKER | **0** |
| mvn test | **811 run, 0 Failures, 0 Errors, 0 Skipped** |
| 代码改动范围 | **仅 Phase 12 已有改动，无新增** |
| 过期报告清理 | **12 个已删除，无残留** |
| 禁止修改范围 | **零违规** |
| **是否建议提交** | **是，建议提交** |

**建议提交清单**（推荐按此分组提交）：

**commit 1** — Phase 12 代码变更：
- `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`
- `src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java`

**commit 2** — 报告与清理：
- `phase12_final_clean_rebuild_gate_report.md`
- `phase12_pre_commit_cleanup_report.md`
- `special_cases_report.md`（redline 结果更新）
- 可选：4 个 SWIP 归档报告（`swip_baseline_report.md`, `swip_compile_coverage_analysis_report.md`, `swip_docx_extraction_comparison_report.md`, `swip_embedding_regression_case_analysis_report.md`）

---

## 9. 本轮修改汇总

| 操作 | 数量 |
|---|---|
| 删除过期中间报告 | 12 |
| 新增/更新报告 | 1（本报告） |
| 修改业务代码 | 0 |
| 修改测试代码 | 0 |
| 修改 redline allowlist | 0 |
| 运行 redline | 1（EXIT_CODE=0，BLOCKER=0） |
| 运行 mvn test | 1（BUILD SUCCESS） |

**结论**：代码和测试均为已有 Phase 12 改动，无新增变更。清理完成，质量复核通过，建议提交。
