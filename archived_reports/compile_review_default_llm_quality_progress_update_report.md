# Compile Review 默认 LLM 模式质量台账更新报告

更新时间：2026-05-18
执行 Agent：agentC

## 修改了哪些文档

- `docs/quality-progress-and-lessons.md`：更新了以下章节：
  - **时间戳**：更新为 compile review 默认 LLM 模式代码实现 + runtime 验证通过后。
  - **当前阶段**：新增 compile review 默认 LLM 模式完成记录（代码实现 + runtime 验证 + 入口闭环审计），移除旧的"仍未生产默认启用"和"下阶段：小范围启用策略设计"。
  - **当前 Gate**：
    - redline 更新为 `BLOCKER=0 / REVIEW=1858 / ALLOWLIST=242`（REVIEW +5、ALLOWLIST +3 来自新增测试类名匹配与测试配置类）。
    - mvn test 更新为 `825/0/0`（从 816 增至 825，新增 9 个 case）。
    - 新增 compile review 默认 LLM 模式行：代码实现 + runtime 验证通过。
  - **多 Agent 职责**：更新 agentC、agentD 状态。
  - **已验证结论**：新增 5 条——
    - 默认 LLM 模式代码实现已完成。
    - 默认 LLM 模式 runtime 验证通过。
    - 用户 compile 入口全部收敛到 StateGraph 闭环。
    - LLM approved 正向 canary 未自然触发；Fixer→Re-reviewer loop 未触发。
    - per-job reviewMode 实现已完成。
  - **踩坑记录**：新增两条——
    - 默认 LLM 模式后非 reviewer 测试变成 LLM 可用性测试。
    - 旧式 direct compile 路径绕过 StateGraph 闭环。
  - **下一步计划**：将原 12-13 项标记为已完成，新增 14-18 项（当前 pre-commit quality review + 4 个后续项）。

## 是否只修改台账文档

**是。** 本轮仅修改 `docs/quality-progress-and-lessons.md`，并新增本报告。

## 当前 Gate

| 检查项 | 结果 |
|---|---|
| redline BLOCKER | 0 |
| redline REVIEW | 1858 |
| redline ALLOWLIST | 242 |
| 全量 mvn test | 825/0/0 |
| 默认不传 reviewMode → API response.reviewMode=LLM | 通过 |
| 默认不传 reviewMode → compile_jobs.review_mode=LLM | 通过 |
| 默认不传 reviewMode → reviewRoute=anthropic（非 rule-based） | 通过 |
| 显式 RULE_BASED → reviewMode=RULE_BASED | 通过 |
| 显式 RULE_BASED → reviewRoute=rule-based | 通过 |
| LLM non-pass → persistedCount=0（不入库） | 通过 |
| 未触碰生产主库 | 通过 |
| 未被测试 approved reviewer 掩盖 | 通过 |
| 用户 compile 入口全部收敛到 StateGraph 闭环 | 通过 |

## 当前已完成结论

1. 默认 compile job 走 LLM reviewer（route=anthropic），不再依赖 `LATTICE_LLM_REVIEW_ENABLED` 环境变量作为主决策。
2. 显式 `RULE_BASED` 仍可用，不受全局默认值影响。
3. LLM non-pass 不入库：persist gate 正确阻断 `needs_human_review`，articles/article_chunks 表无新增。
4. 用户可触发 compile 入口（admin compile、upload compile、source sync、公开 API、CLI、MCP）全部收敛到 `CompileJobService → StateGraphCompileOrchestrator`，覆盖 Writer→Reviewer→Fixer→Reviewer→Persist gate 闭环。
5. Retry 正确沿用已落库 reviewMode，不重新读取新建默认值。
6. per-job reviewMode 实现已完成：支持 job 级 `LLM` / `RULE_BASED` 选择。

## 当前未完成后续项

| # | 后续项 | 说明 |
|---|---|---|
| 1 | pre-commit quality review | agentD 执行：redline BLOCKER=0 确认、mvn test=825/0/0 确认、工作区变更范围复核、提交 |
| 2 | LLM approved 正向 canary | 当前 Reviewer 为严格 fail-closed 模式，LLM approved 未自然触发；正式 rollout 后用真实高质量文档观察 approved 率 |
| 3 | Fixer→Re-reviewer loop runtime 验证 | 当前未触发 fixable issue 路径；后续设计专门测试 case 或降低 Reviewer fixable 阈值后验证 |
| 4 | legacy direct compile 封存审计 | `CompilePipelineService` / `IncrementalCompileService` 中旧式 direct compile 路径需做最小可达性防护 |
| 5 | prompt 文件化 | Writer/Reviewer/Fixer prompt 从代码内嵌迁移到外部文件管理 |

## 报告清理建议

当前工作区 compile_review 报告共 35 份（详见 `compile_review_default_llm_report_cleanup_plan.md`）。本轮新增 3 份报告均为必须保留：

| # | 文件 | 保留理由 |
|---|---|---|
| 1 | `compile_review_default_llm_mode_fix_result_report.md` | 本轮核心修复：默认 LLM 模式实现 |
| 2 | `compile_review_default_llm_mode_runtime_verification_report.md` | 本轮 runtime 验证：默认 LLM 行为端到端确认 |
| 3 | `compile_review_entrypoint_loop_coverage_analysis_report.md` | 本轮入口闭环审计：用户入口覆盖确认 |

**建议**：pre-commit quality review 通过前不删除任何报告。10 份可删除旧中间报告（D1-D10）等待提交后按清理计划执行。

## 是否修改代码

**否。**

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改 `src/main/resources/**`
- 未修改 `scripts/**`
- 未修改 redline allowlist
- 未提交代码

## 下一步建议

**进行 pre-commit quality review。** 由 agentD 执行：
- 确认 redline BLOCKER=0
- 确认 mvn test=825/0/0
- 确认工作区变更范围仅含默认 LLM 模式相关文件
- 提交代码 + 本轮报告

## 确认清单

- [x] 是否修改源码：**否**
- [x] 是否修改测试：**否**
- [x] 是否修改配置/脚本：**否**
- [x] 是否修改 `special_cases_report.md`：**否**（仅由 redline 扫描刷新）
- [x] 是否启用 LLM reviewer：**否**（agentD runtime 验证已恢复）
- [x] 是否运行 compile：**否**
- [x] 是否提交代码：**否**
- [x] 是否更新台账：**是**（`docs/quality-progress-and-lessons.md`）
