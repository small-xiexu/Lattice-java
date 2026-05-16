# Compile Review Observability 质量台账更新报告

更新时间：2026-05-17

执行 Agent：agentC

## 修改了哪些文档

- `docs/quality-progress-and-lessons.md`：更新了以下章节：
  - **当前阶段**：新增 compile review observability 已完成 API + UI 验证。
  - **当前 Gate**：新增 compile review observability 行，注明 fix result report 曾显示通过，提交前仍需最终复核。
  - **多 Agent 当前职责**：更新 agentC、agentD 状态。
  - **已验证结论**：新增 rule-based review 后台标识确认、fix 未触发原因可见、本轮 observability 不改变治理行为。
  - **踩坑记录**：新增"compile review 成功不等于 LLM 内容审查成功"和"可观测性解决'看不清'但不改变治理行为"两条。
  - **下一步计划**：新增 compile review observability 已完成；当前交给 agentD 做提交前质量复核；后续 LLM reviewer 需单独设计 persist/query 可见性门禁。

## 是否修改源码

**否。** 本轮未修改 `src/main/java/**` 下任何文件。

## 是否修改测试

**否。** 本轮未修改 `src/test/java/**` 下任何文件。

## 是否修改配置/脚本

**否。** 本轮未修改 `src/main/resources/**`、`scripts/**`、`AGENTS.md`、`CLAUDE.md`、`special_cases_report.md` 及任何 baseline/eval 题集。

## 本轮已记录的关键结论

1. **rule-based review 已在后台明确标识为"不是 LLM 内容审查"**：API 与 UI 均展示 `reviewRoute=rule-based`、`reviewModeLabel=规则审查（不是 LLM 内容审查）`。
2. **fix 未触发原因已可见**：`fixDisplayMessage=未触发自动修复：无 fixable issue`。
3. **acceptedCount / pendingReviewCount / needsHumanReviewCount 均可在后台查看**。
4. **compile review 成功不等于 LLM 内容审查成功**：当前 compile review 只是 rule-based 结构兜底，没有 LLM 内容审查能力。
5. **可观测性解决了"看不清是否审查"的问题，但没有改变审查治理行为**：未启用 LLM reviewer，未修改 persist/query 可见性过滤。

## 剩余风险

- 当前 Gate 表中 compile review observability 行注明"提交前仍需最终复核"——本轮未重新跑 redline / mvn test，依赖 fix result report 的历史结果。
- UI 当前只展示 processing task 的步骤详情文本，不展示 `reviewSummary` 全量结构化字段面板。
- 未验证 LLM reviewer route 或存在 `fix_review_issues` 步骤时的 UI 展示。
- 当前数据库仍是 SWIP clean 状态，未验证主 baseline 库场景。

## 下一步建议

交给 **agentD** 做提交前质量复核：
- 确认 redline BLOCKER=0
- 确认 mvn test 通过
- 确认工作区只含允许变更
- 后续如需启用 LLM reviewer，必须单独设计并验证 persist/query 可见性门禁
