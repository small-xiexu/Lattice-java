# PE5 报告清理与状态同步计划

分析时间：2026-06-08
执行人：agentC
HEAD：`34394bd fix(search): use token OR query for FTS channels`

## 一、当前工作区状态

### 1.1 已修改跟踪文件

| 文件 | 状态 | 处置 |
|---|---|---|
| `docs/quality-progress-and-lessons.md` | 待更新 | 本轮同步最新状态 |
| `special_cases_report.md` | redline 输出 | 永远排除 |

### 1.2 未跟踪报告（~45 个）

全部在 `docs/test/knowledge-base-e2e/` 下。

## 二、报告分桶

### 桶 A：最终保留（建议提交，14 个）

#### A1：PE5 资产包与设计（4 个）

| 文件 | 理由 |
|---|---|
| `fresh-eval-2026-08/` 目录 | PE5 资料包（5 source + eval + README） |
| `fresh-eval-2026-08_design_report.md` | agentB 设计报告 |
| `fresh-eval-2026-08_build_report.md` | agentC 构建报告 |
| `fresh-eval-2026-08_question_set_consistency_fix_result_report.md` | 题集一致性修复 |

#### A2：线 B gate 闭环（5 个）

| 文件 | 理由 |
|---|---|
| `pe5_line_b_fts_or_query_isolated_gate_report.md` | 线 B 隔离验证（worktree 独立 gate） |
| `pe5_line_b_fts_or_query_pre_commit_quality_review_report.md` | 线 B 提交前质量复核 |
| `pe5_line_b_fts_or_query_clean_rebuild_gate_report.md` | PE5 线 B 清库重建 gate |
| `pe4_post_line_b_fts_or_query_regression_gate_report.md` | PE4 线 B 提交后保护回归 |
| `pe4_post_line_b_fts_or_query_clean_rebuild_long_wait_gate_report.md` | PE4 线 B 提交后清库重建 gate |

#### A3：治理与基线（5 个）

| 文件 | 理由 |
|---|---|
| `hidden_eval_governance_protocol.md` | Hidden eval 治理协议（已设计，待落地） |
| `hidden_eval_gates/hidden_eval_2026_06_desensitized_gate_report.md` | Hidden eval 脱敏 gate 报告 |
| `hidden_eval_failure_abstraction_analysis_report.md` | Hidden eval 失败抽象分析 |
| `java_codebase_public_eval_full_runtime_gate_report.md` | Java Codebase Eval 全量 gate |
| `pe5_workspace_cleanup_plan.md` | 上一轮收口计划（历史参照） |

### 桶 B：可归档（暂不提交，后续单独处理，7 个）

#### B1：线 A（StructuredQueryPlanner）实验报告（7 个）

线 A 未提交，当前不在主链。这些报告记录了结构化查询增强的实验过程，如果后续恢复线 A，可作为参照。

| 文件 | 说明 |
|---|---|
| `fresh-eval-2026-08_structured_query_planner_fix_result_report.md` | Planner 主修复结果 |
| `fresh-eval-2026-08_structured_query_planner_fq3_fix_result_report.md` | FQ3 专项修复 |
| `fresh-eval-2026-08_structured_query_planner_fq3_runtime_gate_report.md` | FQ3 runtime gate |
| `fresh-eval-2026-08_structured_query_planner_guard_fix_result_report.md` | Guard 修复 |
| `fresh-eval-2026-08_structured_query_planner_guard_regression_analysis_report.md` | Guard 回归分析 |
| `fresh-eval-2026-08_structured_query_planner_guard_runtime_gate_report.md` | Guard runtime gate |
| `fresh-eval-2026-08_structured_query_planner_runtime_gate_report.md` | Planner 全量 runtime gate |

**建议**：不要删除，但也不要随线 B 提交。等线 A 恢复上线时，与线 A 代码一起评估是否需要归档。

#### B2：YAML retrieval 分析（2 个）

| 文件 | 说明 |
|---|---|
| `fresh-eval-2026-08_yaml_retrieval_recall_followup_analysis_report.md` | YAML 召回追踪分析 |
| `fresh-eval-2026-08_yaml_retrieval_recall_log_analysis_report.md` | YAML 召回日志分析 |

**建议**：PE5 专项分析，与线 A 相关。保留但暂不提交。

### 桶 C：可删除（已被覆盖/已回滚/过期，10 个）

#### C1：Evidence Packing 实验（已回滚，5 个）

| 文件 | 删除理由 |
|---|---|
| `fresh-eval-2026-08_answer_generation_evidence_packing_design_report.md` | 设计方案，但实验已回滚 |
| `fresh-eval-2026-08_answer_generation_evidence_packing_fix_result_report.md` | 修复结果，但实验已回滚 |
| `fresh-eval-2026-08_answer_generation_evidence_packing_runtime_gate_report.md` | Runtime gate，但实验已回滚 |
| `fresh-eval-2026-08_answer_generation_evidence_packing_rollback_result_report.md` | 回滚结果记录 |
| `fresh-eval-2026-08_answer_generation_evidence_packing_post_rollback_runtime_gate_report.md` | 回滚后验证 |

删除依据：线 B 提交后已不需要 evidence packing 方案。回滚已确认，这些报告不会作为未来决策依据。

#### C2：PE5 中间 gate 报告（被线 B gate 覆盖，3 个）

| 文件 | 删除理由 |
|---|---|
| `fresh-eval-2026-08_runtime_gate_report.md` | 线 B 提交前的旧 baseline，被 `pe5_line_b_fts_or_query_clean_rebuild_gate_report.md` 覆盖 |
| `fresh-eval-2026-08_runtime_gate_failure_analysis_report.md` | 失败分析，根因已被线 B 解决 |
| `fresh-eval-2026-08_runtime_gate_recheck_report.md` | 重检报告，被线 B gate 覆盖 |

删除依据：这些报告的基线已过期（线 B 提交前），结论不再代表当前状态。

#### C3：Structured source recall（PE5 中间实验，2 个）

| 文件 | 删除理由 |
|---|---|
| `fresh-eval-2026-08_structured_source_recall_fix_result_report.md` | PE5 中间实验 |
| `fresh-eval-2026-08_structured_source_recall_residual_fix_result_report.md` | PE5 中间实验 |
| `fresh-eval-2026-08_structured_source_recall_residual_root_cause_analysis_report.md` | PE5 中间实验 |
| `fresh-eval-2026-08_structured_source_recall_runtime_gate_report.md` | PE5 中间实验 |

删除依据：这些是 PE5 多轮实验中的中间产物，根因最终归到 FTS OR query（线 B），这些报告不再有独立参照价值。

### 桶 D：不纳入提交（永远排除，6 个）

| 文件 | 理由 |
|---|---|
| `special_cases_report.md` | redline 输出 |
| `pe1_q2_acronym_*` (3 个) | 缩略词方案未实施，保留未跟踪 |
| `post_compiler_admin_fixes_report_archive_plan.md` | 过期归档建议 |
| `post_s2_writer_title_preservation_status.md` | 被后续提交覆盖 |

### 桶 E：状态同步报告（本轮新建 + 保留，3 个）

| 文件 | 理由 |
|---|---|
| `quality_progress_current_state_sync_report.md` | 上一轮台账同步报告（保留作为参照） |
| `pe5_report_cleanup_and_status_sync_plan.md` | 本轮清理计划（新建） |
| `internal_mirror_dogfood_java_project_runtime_report.md` | dogfood 验证报告，已有独立价值 |
| `post_s2_writer_title_preservation_current_head_full_eval_gate_report.md` | S2 全量 gate 基线 |

## 三、分桶汇总

| 桶 | 数量 | 处置 |
|---|---|---|
| A — 最终保留（建议提交） | 14 | 随下一轮 docs commit 提交 |
| B — 可归档（暂不提交） | 9 | 保留未跟踪，等线 A 恢复时处理 |
| C — 可删除 | 15 | 删除（已被覆盖/已回滚/过期） |
| D — 不纳入提交 | 6 | 永远排除 |
| E — 本轮新建 | 1 | 本计划 |

## 四、`docs/quality-progress-and-lessons.md` 状态同步建议

### 4.1 必须更新

| 位置 | 更新内容 |
|---|---|
| 时间戳 | → 2026-06-08，HEAD `34394bd` |
| 当前阶段 | 新增：线 B FTS OR Query 已提交 `34394bd`；PE4 线 B 提交后保护回归 PASS（Search 6/6、FG 3/3、Hallucination 0） |
| 当前 Gate | 更新 redline/mvn test 基线；新增线 B gate 条目；新增 PE4 post-line-B regression gate |
| 已验证结论 | 新增：线 B `buildFtsQueryText` 是通用 FTS tsquery 优化，非 PE5 特判；PE4 清库重建确认无回归 |
| 下一步计划 | 新增 item：线 B 已提交 + PE4 回归已通过；PE5 全量验收待线 A 恢复后继续 |

### 4.2 必须删除/修正

| 位置 | 修正 |
|---|---|
| 时间戳中的 "PE5 已完成运行时验收但未通过" | 改为反映线 B 已提交后的当前状态 |
| 任何关于 evidence packing 实验的中间结论 | 不保留（已回滚） |
| 任何关于 structured query planner 处于"进行中"的描述 | 下移为"待恢复"——线 A 未提交 |

### 4.3 不建议写入

- 不要在线 B gate 中写"线 A 预计何时完成"
- 不要把 PE5 的 structured query planner 实验报告写成最终结论
- 不要把 evidence packing 回滚写成"失败"，写成"线 B 替代方案已覆盖"即可

## 五、执行建议

### 本轮执行（agentC）

1. 删除桶 C 的 15 个过期报告
2. 更新 `docs/quality-progress-and-lessons.md`
3. 生成本轮清理报告（本文件）

### 下一轮（用户确认后）

1. 提交桶 A 的 14 个最终保留报告 + `quality-progress-and-lessons.md` + 本清理计划
2. 桶 B 的 9 个报告保持未跟踪或移至 `_b1_line_a_experiments/` 子目录

## 六、明确声明

- [x] 本轮只输出清理计划，不删除文件，不修改代码
- [x] 不改测试、prompt、config、schema、scripts、题集
- [x] 不恢复线 A 或继续修功能
- [x] 未读取 hidden eval
- [x] 桶 C 的 15 个删除候选已逐一核实覆盖关系
