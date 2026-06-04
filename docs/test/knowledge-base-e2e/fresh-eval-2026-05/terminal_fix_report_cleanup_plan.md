# Terminal Fix 中间报告清理计划（二阶修订版）

生成时间：2026-06-04
修订时间：2026-06-04（二阶引用检查）
执行人：agentC（文档/报告治理 Agent）
范围：`docs/test/knowledge-base-e2e/fresh-eval-2026-05/` 下 FG1/FQ4 未跟踪中间报告

## 1. 背景

- 生产代码 terminal fix 已提交 `549f0e3`，包含 5 份最终 gate/verification report
- `57c34ac` 已提交 README 与四份核心流水线文档
- `quality-progress-and-lessons.md` 已随 `549f0e3` 同步台账
- 当前 fresh-eval 目录下共 **49 个未跟踪 `.md`**
- 扣除本 plan 自身：**48 个待治理报告**

## 2. 分类依据

| 类别 | 判定规则 |
|---|---|
| **Commit** | 被已提交文档引用（防断链）；被 Commit Candidate 引用（防二阶断链）；agentB 根因分析报告解释关键诊断转折点；用户明确指定保留 |
| **Keep untracked** | 含运行时环境快照且可能交叉后续 S2/FS2 分析；无法判断引用价值；内容不确定 |
| **Delete** | 同时满足以下全部条件（缺一不可）：未被已提交文档引用、未被 Commit Candidate 引用、未被 commit message 引用、被后续最终 gate/verification 完全覆盖、删除后不造成任何已提交或本轮将提交报告断链 |

**两层引用规则**：

| 层级 | 规则 |
|---|---|
| 一阶引用（已提交文档 → 未跟踪报告） | 被已提交文档（`quality-progress-and-lessons.md` 或已提交 gate report）引用的报告 → 不能放入 Delete；优先放入 Commit；内容不确定则放入 Keep 并标"需人工确认引用处理" |
| 二阶引用（Commit Candidate → Delete Candidate） | 被 Commit Candidate 引用的报告 → 不能放入 Delete（否则提交后 Commit Candidate 内部断链）；优先移入 Commit Candidates；内容不确定则放入 Keep |

## 3. 引用检查结果

### 3.1 一阶引用（已提交文档 → 未跟踪报告）

以下 **22 个**未跟踪报告被 `quality-progress-and-lessons.md`（549f0e3 提交版本）引用：

| # | 报告 | 原始分类 | 修订后分类 |
|---|---|---|---|
| 1 | `fg1_field_alias_enricher_bootstrap_guard_fix_result_report.md` | Delete (fix) | **Commit** |
| 2 | `fg1_fq4_conclusion_builder_terminal_unit_consumption_verification_report.md` | Delete (gate) | **Commit** |
| 3 | `fg1_ftmc_zero_builder_fix_result_report.md` | Delete (fix) | **Commit** |
| 4 | `fg1_ftmc_zero_builder_runtime_gate_report.md` | Delete (gate) | **Commit** |
| 5 | `fg1_qf_false_builder_fix_result_report.md` | Delete (fix) | **Commit** |
| 6 | `fg1_qf_false_builder_runtime_gate_report.md` | Delete (gate) | **Commit** |
| 7 | `fq4_fg1_controlled_fallback_candidate_score_trace_report.md` | Delete (trace) | **Commit** |
| 8 | `fq4_fg1_field_alias_enricher_runtime_audit_report.md` | Delete (trace) | **Commit** |
| 9 | `fq4_fg1_forced_restart_runtime_verification_report.md` | Delete (gate) | **Commit** |
| 10 | `fq4_fg1_multi_target_terminal_conclusion_fix_result_report.md` | Keep | **Commit** |
| 11 | `fq4_fg1_multi_target_terminal_context_guard_fix_result_report.md` | Keep | **Commit** |
| 12 | `fq4_fg1_terminal_builder_slf4j_trace_runtime_gate_report.md` | Delete (gate) | **Commit** |
| 13 | `fq4_fg1_terminal_channel_candidate_supply_fix_revision_report.md` | Delete (fix) | **Commit** |
| 14 | `fq4_fg1_terminal_channel_candidate_supply_runtime_gate_report.md` | Delete (gate) | **Commit** |
| 15 | `fq4_fg1_terminal_channel_limit_root_cause_analysis_report.md` | Commit | **Commit** *(不变)* |
| 16 | `fq4_fg1_terminal_entity_context_metadata_fix_result_report.md` | Keep | **Commit** |
| 17 | `fq4_field_alias_fix_final_runtime_gate_report.md` | Delete (gate) | **Commit** |
| 18 | `fq4_field_alias_fix_full_public_eval_gate_report.md` | Delete (gate) | **Commit** |
| 19 | `fq4_field_alias_json_array_consumption_verification_report.md` | Delete (gate) | **Commit** |
| 20 | `fq4_terminal_tie_break_fix_result_report.md` | Delete (fix) | **Commit** |
| 21 | `public_eval_gate_after_human_review_approval_report.md` | Keep | **Commit** |
| 22 | `two_public_eval_clean_schema_gate_report.md` | Keep | **Commit** |

5 份已提交 gate report 均未引用任何未跟踪报告。

### 3.2 二阶引用（Commit Candidate → Delete Candidate）

扫描范围：28 个 Commit Candidate × 18 个 Delete Candidate = 504 次交叉 grep。

命中 **5 个 Delete Candidate** 被 Commit Candidate 引用：

| Delete Candidate | 被哪个 Commit Candidate 引用 | 处置 |
|---|---|---|
| `fg1_fq4_conclusion_builder_terminal_unit_consumption_fix_result_report.md` | `fg1_fq4_conclusion_builder_terminal_unit_consumption_verification_report.md` | **→ Commit** |
| `fg1_raw_query_entity_context_match_fix_result_report.md` | `fg1_raw_query_entity_context_match_runtime_gate_report.md` | **→ Commit** |
| `fq4_field_alias_json_array_consumption_fix_result_report.md` | `fq4_field_alias_json_array_consumption_verification_report.md` | **→ Commit** |
| `fg1_field_alias_binding_runtime_verification_report.md` | `two_public_eval_clean_schema_gate_report.md` | **→ Commit** |
| `fq4_tie_break_runtime_gate_report.md` | `fq4_fg1_multi_target_terminal_conclusion_analysis_report.md` + `fq4_fg1_multi_target_terminal_conclusion_fix_result_report.md` | **→ Commit** |

以上 5 个全部为 `fix_result → verification/runtime_gate` 配对或 `runtime_gate → analysis/fix` 引用链，是典型的前置修复报告被后续验证报告引用的关系。无敏感内容，全部移入 Commit Candidates。

剩余 13 个 Delete Candidate 未被任何 Commit Candidate 引用。

## 4. 修订后分类表

### 4.1 Commit Candidates（33 个）

包含：22 个一阶引用 + 5 个原关键根因分析（未被一阶引用）+ 1 个用户指定 + 5 个二阶引用。

| # | 文件 | 来源 | 依据 |
|---|---|---|---|
| 1 | `fg1_terminal_unit_consumption_root_cause_analysis_report.md` | 原 Commit（非引用） | FG1 初始根因：terminal unit 未被 conclusion 消费 |
| 2 | `fg1_field_alias_enricher_candidate_supply_readonly_analysis_report.md` | 原 Commit（非引用） | 候选供给侧只读审计 |
| 3 | `fq4_fg1_fallback_runtime_breakpoint_analysis_report.md` | 原 Commit（非引用） | 关键断点：builder 内非 retrieval/reranker |
| 4 | `fq4_fg1_multi_target_terminal_conclusion_analysis_report.md` | 原 Commit（非引用） | 多目标结论聚合分析与通用修复设计 |
| 5 | `fq4_fg1_terminal_entity_context_metadata_design_report.md` | 原 Commit（非引用） | 实体上下文元数据最小数据模型设计 |
| 6 | `fq4_fg1_terminal_channel_limit_root_cause_analysis_report.md` | 原 Commit（一阶） | 每通道 top10 截断发现 |
| 7 | `fg1_field_alias_enricher_bootstrap_guard_fix_result_report.md` | 原 Delete *fix*（一阶） | 一阶引用 |
| 8 | `fq4_fg1_controlled_fallback_candidate_score_trace_report.md` | 原 Delete *trace*（一阶） | 一阶引用 |
| 9 | `fq4_field_alias_fix_full_public_eval_gate_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 10 | `fg1_raw_query_entity_context_match_runtime_gate_report.md` | 原 Delete *gate*（用户指定） | 用户明确指定保留 |
| 11 | `fg1_fq4_conclusion_builder_terminal_unit_consumption_verification_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 12 | `fg1_ftmc_zero_builder_fix_result_report.md` | 原 Delete *fix*（一阶） | 一阶引用 |
| 13 | `fg1_ftmc_zero_builder_runtime_gate_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 14 | `fg1_qf_false_builder_fix_result_report.md` | 原 Delete *fix*（一阶） | 一阶引用 |
| 15 | `fg1_qf_false_builder_runtime_gate_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 16 | `fq4_fg1_field_alias_enricher_runtime_audit_report.md` | 原 Delete *trace*（一阶） | 一阶引用 |
| 17 | `fq4_fg1_forced_restart_runtime_verification_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 18 | `fq4_fg1_multi_target_terminal_conclusion_fix_result_report.md` | 原 Keep（一阶） | 一阶引用 |
| 19 | `fq4_fg1_multi_target_terminal_context_guard_fix_result_report.md` | 原 Keep（一阶） | 一阶引用 |
| 20 | `fq4_fg1_terminal_builder_slf4j_trace_runtime_gate_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 21 | `fq4_fg1_terminal_channel_candidate_supply_fix_revision_report.md` | 原 Delete *fix*（一阶） | 一阶引用 |
| 22 | `fq4_fg1_terminal_channel_candidate_supply_runtime_gate_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 23 | `fq4_fg1_terminal_entity_context_metadata_fix_result_report.md` | 原 Keep（一阶） | 一阶引用 |
| 24 | `fq4_field_alias_fix_final_runtime_gate_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 25 | `fq4_field_alias_json_array_consumption_verification_report.md` | 原 Delete *gate*（一阶） | 一阶引用 |
| 26 | `fq4_terminal_tie_break_fix_result_report.md` | 原 Delete *fix*（一阶） | 一阶引用 |
| 27 | `public_eval_gate_after_human_review_approval_report.md` | 原 Keep（一阶） | 一阶引用 |
| 28 | `two_public_eval_clean_schema_gate_report.md` | 原 Keep（一阶） | 一阶引用 |
| 29 | `fg1_fq4_conclusion_builder_terminal_unit_consumption_fix_result_report.md` | 原 Delete *fix*（二阶） | 二阶引用（verification → fix_result 链） |
| 30 | `fg1_raw_query_entity_context_match_fix_result_report.md` | 原 Delete *fix*（二阶） | 二阶引用（runtime_gate → fix_result 链） |
| 31 | `fq4_field_alias_json_array_consumption_fix_result_report.md` | 原 Delete *fix*（二阶） | 二阶引用（verification → fix_result 链） |
| 32 | `fg1_field_alias_binding_runtime_verification_report.md` | 原 Delete *gate*（二阶） | 二阶引用（two_public_eval_gate → this 链） |
| 33 | `fq4_tie_break_runtime_gate_report.md` | 原 Delete *gate*（二阶） | 二阶引用（analysis + fix → runtime_gate 链） |

### 4.2 Delete Candidates（13 个）

全部满足：未被已提交文档引用 + 未被 Commit Candidate 引用 + 被后续最终 gate 完全覆盖 + 删除后不造成任何断链。

**临时 trace 报告（7 个）：**

| # | 文件 |
|---|---|
| 1 | `fq4_fallback_candidate_score_trace_report.md` |
| 2 | `fq4_fg1_default_reviewmode_fallback_trace_report.md` |
| 3 | `fq4_fg1_fallback_candidate_score_runtime_trace_report.md` |
| 4 | `fq4_fg1_terminal_builder_runtime_trace_report.md` |
| 5 | `fq4_fg1_terminal_builder_slf4j_trace_fix_report.md` |
| 6 | `fq4_fg1_terminal_candidate_runtime_score_trace_report.md` |
| 7 | `fq4_fg1_terminal_candidate_runtime_score_trace_actual_report.md` |

**被后续修复完全覆盖的 fix result（3 个）：**

| # | 文件 |
|---|---|
| 8 | `fq4_fg1_terminal_channel_candidate_supply_fix_result_report.md` |
| 9 | `fq4_field_alias_fix_restore_result_report.md` |
| 10 | `fq4_field_alias_fix_comment_cleanup_report.md` |

**被提交最终 gate 覆盖的 runtime gate / verification（3 个）：**

| # | 文件 |
|---|---|
| 11 | `fg1_field_alias_enricher_bootstrap_guard_runtime_gate_report.md` |
| 12 | `fq4_fg1_terminal_candidate_supply_root_cause_analysis_report.md` |
| 13 | `fq4_fg1_terminal_entity_context_runtime_gate_report.md` |

### 4.3 Keep Untracked（2 个）

| # | 文件 | 原因 |
|---|---|---|
| 1 | `fg1_terminal_unit_current_breakpoint_analysis_report.md` | 含运行时数据库快照与断点现场；未被一阶/二阶引用；可能交叉后续 S2/FS2 分析 |
| 2 | `fresh_eval_post_cleanup_remaining_failure_analysis_report.md` | 跨阶段边界审查，含后续搜索分析输入；未被一阶/二阶引用；不确定后续是否引用 |

### 4.4 待治理报告排除

| 文件 | 处置 |
|---|---|
| `terminal_fix_report_cleanup_plan.md` | 本 plan 自身，不纳入待治理计数 |

## 5. 数量汇总

| 分类 | 数量 |
|---|---|
| 未跟踪 `.md` 总数 | 49 |
| 本 plan 自身（排除） | 1 |
| **待治理报告** | **48** |
| Commit Candidates | 33 |
| Delete Candidates | 13 |
| Keep Untracked | 2 |
| **合计** | **48** ✓ |

### 5.1 Delete Candidates 子类明细

| 子类 | 数量 | 文件 |
|---|---|---|
| 临时 trace | 7 | 均为仅含 `trace`/`score_trace` 关键词、无交叉引用的纯中间产物 |
| 被覆盖 fix result | 3 | 均未被一阶/二阶引用，被 549f0e3 最终修复完全覆盖 |
| 被覆盖 runtime gate | 3 | 均未被一阶/二阶引用，被已提交最终 gate 覆盖 |

## 6. 执行计划

1. **本轮**：仅修订 plan 文件，不执行 cleanup
2. **本轮不执行**：不删除任何报告、不 git add、不 commit、不 push
3. **下一轮执行 cleanup 前必须**：
   - 再次运行 `git status --short`，确认未跟踪文件列表未变化
   - 重新跑一阶引用检查（确认无新增已提交文档引用指向 Delete Candidates）
   - 重新跑二阶引用检查（确认 Commit Candidates 间无新增交叉引用指向 Delete Candidates）
4. **执行 cleanup 时**：
   - 删除 13 个 Delete Candidates
   - `git add` 33 个 Commit Candidates + `terminal_fix_report_cleanup_plan.md` + `terminal_fix_report_cleanup_result.md`
   - 保持 2 个 Keep Untracked 不变
5. **提交约束**：
   - 必须排除：`special_cases_report.md`、`docs/模型绑定配置参考.md`、`src/**`、`scripts/**`、`README.md`、`docs/quality-progress-and-lessons.md`、四份核心流水线文档
   - 推荐提交信息：`docs(test): clean up terminal fix intermediate reports`
   - 提交后不编辑 `docs/quality-progress-and-lessons.md`
6. **提交后输出**：commit hash、删除清单（13）、归档清单（33）、keep untracked 清单（2）、排除项声明

## 7. 修订记录

| 项目 | v1 (原始) | v2 (一阶修订) | v3 (二阶修订) | 原因 |
|---|---|---|---|---|
| 待治理报告数 | 47 | 48 | 48 | v1 统计遗漏 |
| Commit | 6 | 28 | **33** | +22 一阶引用 + 5 二阶引用 |
| Delete | 35 | 18 | **13** | -22 一阶引用移出 - 5 二阶引用移出 |
| Keep | 7（写 6） | 2 | 2 | 数量修正 |
| 二阶引用规则 | — | — | **新增** | 防止 Commit 提交后内部断链 |
| Delete "fix result" 标签 | "（5 个）"实际 6 | 同上（未修正） | **"（3 个）"** | 3/6 移入 Commit，修正数字与内容一致 |
| Delete "runtime gate" 标签 | "（5 个）" | "（5 个）" | **"（3 个）"** | 2/5 移入 Commit |

### 7.1 二阶引用移出明细

| 文件 | 引用者（Commit Candidate） | 新分类 |
|---|---|---|
| `fg1_fq4_conclusion_builder_terminal_unit_consumption_fix_result_report.md` | `fg1_fq4_conclusion_builder_terminal_unit_consumption_verification_report.md` | Commit |
| `fg1_raw_query_entity_context_match_fix_result_report.md` | `fg1_raw_query_entity_context_match_runtime_gate_report.md` | Commit |
| `fq4_field_alias_json_array_consumption_fix_result_report.md` | `fq4_field_alias_json_array_consumption_verification_report.md` | Commit |
| `fg1_field_alias_binding_runtime_verification_report.md` | `two_public_eval_clean_schema_gate_report.md` | Commit |
| `fq4_tie_break_runtime_gate_report.md` | `fq4_fg1_multi_target_terminal_conclusion_analysis_report.md` + `fq4_fg1_multi_target_terminal_conclusion_fix_result_report.md` | Commit |

## 8. 仍待人工确认

- `fresh_eval_post_cleanup_remaining_failure_analysis_report.md`：跨阶段内容，后续搜索分析轮次可能引用。如确认不再需要，可改入 Delete
- `fg1_terminal_unit_current_breakpoint_analysis_report.md`：含运行时 DB 快照。如确认后续 S2/FS2 分析不需要，可改入 Delete
