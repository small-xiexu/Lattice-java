# 当前剩余工作与报告归档状态

审计时间：2026-05-31（同步 `2888796` QueryResponse 构造器收敛与 DTO 审计提交后状态）
审计 Agent：agentC（文档/报告治理 Agent）
约束声明：本轮未修改生产代码、测试代码、配置、脚本，未 stage、未 commit、未 push。

---

## 1. 当前 HEAD 与最近提交

```
2888796 (HEAD) refactor(api): 收敛 QueryResponse 构造器并补齐字段契约注释
305bfc6 docs(test): 记录拆分提交后的最终门禁结果
35bf769 refactor(admin): 移除管理页 SERVER_DIR 操作入口
fa8b883 refactor(source): 移除 SERVER_DIR source 支持
90ad165 feat(compiler): 增加 terminal unit 字段别名增强器
56b0274 fix(query): 使用原始 fused order 选择 terminal unit conclusion 候选
21e25e9 feat(query): 为 terminal unit 物化 sibling context
a9b1092 fix(search): 优化中文 LIKE token 预算与排序
03ae48c fix(documentparse): 输出表格结构化行以生成 terminal unit
```

---

## 2. 当前 Gate 状态

| 检查项 | 结果 | 来源 |
|---|---|---|
| redline | **BLOCKER=0** | `post_split_commits_final_gate_report.md` |
| mvn test | **995/0/0/0** | `post_split_commits_final_gate_report.md` |
| 拆分提交后工程门禁 | **PASS** | `post_split_commits_final_gate_report.md` |

**重要**：Gate PASS 仅指工程门禁（redline + mvn test），不代表 fresh eval 2 或知识库端到端验收通过。

---

## 3. 已完成项

### 3.1 最近四个拆分提交

| Commit | 描述 | 验证结论 |
|---|---|---|
| `56b0274` | Phase 1I fused order conclusion fix | pre-commit 复核通过，YAML 5 题 0/5→4/5，Answer Accuracy 10→12/15，Hallucination 5→2 |
| `90ad165` | Field alias enricher (LLM) | 接口+实现+prompt 外置+Materializer 集成 |
| `fa8b883` | SERVER_DIR source 支持移除 | 纯基础设施清理 |
| `35bf769` | Admin SERVER_DIR 操作入口移除 | 纯前端清理 |

### 3.2 其余已提交项

| Commit | 描述 | 备注 |
|---|---|---|
| `a9b1092` | fix(search): 优化中文 LIKE token 预算与排序 | Phase 1C LIKE Token Budget Fix。MAX_LIKE_TOKENS 8→32 + CJK 评分倒置。此前被误标为"待提交"，经核对已确认提交 |
| `21e25e9` | feat(query): 为 terminal unit 物化 sibling context | Phase 1D sibling context |
| `305bfc6` | docs(test): 记录拆分提交后的最终门禁结果 | 门禁报告归档 |
| `2888796` | refactor(api): 收敛 QueryResponse 构造器并补齐字段契约注释 | DTO 审计试点。@Getter + @JsonCreator + @Builder，删除历史短构造器，调用点迁移 builder。定向测试 34/0/0/0。分析报告已随提交归档 |

### 3.3 质量复核

- `terminal_unit_phase1i_pre_commit_quality_review_report.md`：确认工作区 7 组变更需拆分为 4 个独立 commit，拆分建议已被采纳并执行。

---

## 4. 仍待做项

### 4.1 代码/功能项

| 编号 | 事项 | 状态 | 优先级 |
|---|---|---|---|
| T1 | FG1 terminal unit 未消费独立分析 | Phase 1I 后 YAML 5 题中 FG1 仍 PARTIAL | 中 |
| T2 | S2 agentD 完整知识库端到端验收 | 清库/重建/导入后回归 Q1-Q12、S1-S4、Q6 保护 | 中 |
| T3 | Public Eval 1 Q6/S2 保护回归 | 当前库仅含 Public Eval 2 资料，需补充资料后验证 | 中 |
| T4 | LLM approved 正向 canary 观察 | Reviewer 为严格 fail-closed 模式，LLM approved 未自然触发 | 低（需 rollout 后观察） |
| T5 | Fixer→Re-reviewer loop runtime 验证 | 当前未触发 fixable issue 路径 | 低（需构造测试条件） |
| T6 | legacy direct compile 封存审计 | `CompilePipelineService`/`IncrementalCompileService` 旧路径可达性防护 | 低 |
| T7 | Query 主链复杂度治理 | AnswerGeneration 继承链深度、`.contains()` 规则分流 | 低（独立线，待 Phase 1 验收后启动） |

### 4.2 前端/Dashboard 项

| 编号 | 事项 | 状态 |
|---|---|---|
| T9 | Dashboard 状态摘要接入人工确认队列 | 待做 |
| T10 | 审查/修复轮次展示 | 待做（后端数据已就绪，前端未接入） |

### 4.3 文档/报告项

| 编号 | 事项 | 状态 |
|---|---|---|
| T11 | Phase 1D/1E/1F/1G 历史报告归档 | 已审计，22 个全部建议归档提交。详见 `phase1d_1g_report_archive_review_report.md` |
| T12 | DTO 字段注释/Lombok 改造推广 | QueryResponse 试点已完成（`2888796`），下一步推广 QuerySourceResponse / QueryArticleResponse |
| T13 | SWIP 两文档 clean rebuild 验收 | 待做 |

---

## 5. Fresh Eval 2 真实状态（不得写成整体通过）

| 指标 | 原始基线 | Phase 1I 后 | 结论 |
|---|---|---|---|
| Answer Accuracy | 10/15 (66.7%) | **12/15 (80.0%)** | 改善但未通过 |
| YAML 5 题 | 0/5 (0%) | **4/5 (80%)** | FG1 仍 PARTIAL |
| Search Accuracy | 1/4 (25%) | 1/4 (25%) | FS1/FS2/FS3 未改善 |
| Recall@10 | 13/15 | 13/15 | 持平 |
| Citation Accuracy | 2/15 (13.3%) | 2/15 (13.3%) | 未改善 |
| Abstain Accuracy | 2/2 (100%) | 2/2 (100%) | 持平 |
| Hallucination Count | 5 | **2** | 显著改善（-3） |

**剩余问题**：
- FS1/FS2/FS3：搜索排名问题，Phase 1 系列未覆盖
- FG1：terminal unit 未被 conclusion 消费，需独立归因
- Citation Accuracy 2/15：citation binding 精度未提升

---

## 6. 工作区残留分类

### 6.1 永远排除提交

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | 私有配置，含真实 apiKey/provider 连接信息 |
| `special_cases_report.md` | redline 脚本输出产物，非源文档 |

### 6.2 不建议提交（中间失败报告）

| 文件 | 原因 |
|---|---|
| `q6_exact_path_terminal_field_verification_report.md` | 中间 FAIL 验证，已被最终修复报告覆盖（见 `remaining_docs_reports_commit_plan.md`） |
| `execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md` | 含真实 API 密钥，需脱敏后再议（见 `remaining_docs_reports_commit_plan.md`） |

### 6.3 建议归档提交（untracked 历史报告，已审计）

Phase 1D/1E/1F/1G 共 22 个报告：

**Phase 1D（3 个）**：
- `terminal_unit_phase1d_reranker_context_clean_verification_report.md`
- `terminal_unit_phase1d_reranker_context_fix_result_report.md`
- `terminal_unit_phase1d_reranker_context_scope_fix_result_report.md`

**Phase 1E（10 个）**：
- `terminal_unit_phase1e_answer_consumption_analysis_report.md`
- `terminal_unit_phase1e_borrowing_system_failure_analysis_report.md`
- `terminal_unit_phase1e_clean_schema_e2e_verification_report.md`
- `terminal_unit_phase1e_terminal_conclusion_consumption_clean_runtime_verification_report.md`
- `terminal_unit_phase1e_terminal_conclusion_consumption_fix_result_report.md`
- `terminal_unit_phase1e_terminal_content_enhancement_fix_result_report.md`
- `terminal_unit_phase1e_terminal_content_enhancement_verification_report.md`
- `terminal_unit_phase1e_terminal_evidence_consumption_fix_result_report.md`
- `terminal_unit_phase1e_terminal_evidence_consumption_test_result_report.md`
- `terminal_unit_phase1e_terminal_evidence_consumption_verification_report.md`

**Phase 1F（7 个）**：
- `terminal_unit_phase1f_alias_consumption_fix_result_report.md`
- `terminal_unit_phase1f_conclusion_gate_correction_clean_runtime_verification_report.md`
- `terminal_unit_phase1f_conclusion_gate_correction_fix_result_report.md`
- `terminal_unit_phase1f_metadata_alias_sync_clean_runtime_verification_report.md`
- `terminal_unit_phase1f_metadata_alias_sync_fix_result_report.md`
- `terminal_unit_phase1f_terminal_channel_json_parse_clean_runtime_verification_report.md`
- `terminal_unit_phase1f_terminal_channel_json_parse_fix_result_report.md`

**Phase 1G（2 个）**：
- `terminal_unit_phase1g_terminal_candidate_precision_clean_runtime_verification_report.md`
- `terminal_unit_phase1g_terminal_candidate_precision_fix_result_report.md`

**其他**：`dto_field_javadoc_lombok_refactor_analysis_report.md` — 已随 `2888796` 提交归档，不再 untracked。

### 6.4 已提交但可考虑后续清理

| 文件 | 说明 |
|---|---|
| `terminal_unit_phase1i_pre_commit_quality_review_report.md` | Phase 1I 质量复核，已随 `305bfc6` 附近的报告归档 |

---

## 7. 建议下一步 Agent 分工

### 推荐顺序：先提交文档状态，再派 agentA 推广 DTO 改造

| 优先级 | 任务 | 建议 Agent | 理由 |
|---|---|---|---|
| 1（当前建议） | 文档状态提交 | agentC | 质量台账 + 本报告 + 归档审查报告已更新，22 个历史报告已审计（全部建议归档），建议本次一起提交固化状态 |
| 2 | DTO 改造推广 | agentA | QueryResponse 试点已完成，下一步推广 QuerySourceResponse / QueryArticleResponse |
| 3 | S2 端到端验收 | agentD | 清库/重建/导入资料后回归 Q1-Q12、S1-S4、Q6 保护 |
| 4 | FG1 独立归因 | agentB | 只读分析 terminal unit 未被 conclusion 消费的根因 |
| 5 | Phase 1D-1G 历史报告归档 | agentC | 已审计，22 个报告全部建议归档提交。详见 `phase1d_1g_report_archive_review_report.md` |

**具体建议**：当前最干净的下一步是先让 agentC 统一提交全部文档状态（质量台账 + 剩余工作报告 + 归档审查报告 + 22 个历史报告），然后 agentA 推广 DTO 改造到 QuerySourceResponse / QueryArticleResponse。LIKE Token Budget Fix（`a9b1092`）和 DTO 审计（`2888796`）均已完成提交，不再阻塞。

### S2 端到端验收的前置条件

- 数据库需含完整知识库验收资料（当前库仅含 Public Eval 2 资料）
- 需清库、重建 schema、导入资料
- 至少覆盖：Q1-Q12（12 题）、S1-S4（4 题，含 S2 `下一步计划` 标题/anchor 搜索）、Q6 保护场景

---

## 8. 合规声明

- 本轮未修改 `src/main/java/**`
- 本轮未修改 `src/test/java/**`
- 本轮未修改 `src/main/resources/**`
- 本轮未修改 `scripts/**`
- 本轮未修改 redline 脚本或 allowlist
- 本轮未读取 `docs/模型绑定配置参考.md` 内容
- 本轮未提交 `special_cases_report.md`
- 本轮未清库、未重建 schema、未导入资料、未运行业务 eval
- 本轮未 stage、未 commit、未 push
- 所有结论基于只读审计 git log、git status、既有报告与质量台账
