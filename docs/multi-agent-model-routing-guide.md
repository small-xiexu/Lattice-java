# 多 Agent 与模型调度手册

更新时间：2026-05-16

本手册给人使用，不交给 AI 自行决策。后续开新窗口或 agent 记忆丢失时，先让对应 agent 读取：

1. `AGENTS.md`
2. `docs/quality-progress-and-lessons.md`
3. 本文件

## 当前可用模型

| 模型 | 推荐用途 | 不推荐用途 |
|---|---|---|
| `gpt-5.5` | 架构判断、高风险代码修复、Query/Deep Research 主链、最终报告评审、提交前质量判断 | 低价值重复跑命令、简单报告整理 |
| `deepseek v4-pro` | 只读报告整理、日志归类、报告清理、基础验证、表格化总结、非高危代码外的辅助分析 | 最终拍板、高风险 Query 主链修改、Deep Research 主链修改 |

默认规则：**能影响主链行为、评测结论或提交决策的任务，用 `gpt-5.5`；只读整理和低风险执行，用 `deepseek v4-pro`。**

## Agent 职责与模型分配

| 角色 | 职责 | 默认模型 | 可降级模型 | 说明 |
|---|---|---|---|---|
| 项目架构师 / 质量推进顾问 | 审报告、拆任务、写下一轮 prompt、判断是否继续修 | `gpt-5.5` | 不建议降级 | 这是技术负责人角色，负责防止硬编码和范围失控。 |
| agentA：代码执行 | 只按明确 prompt 做一个最小代码修复 | `gpt-5.5` | 不建议降级 | 修改 `src/main/java/**`、Query、Deep Research、Compiler 主链时必须用。 |
| agentB：治理 / 链路分析 | 只读分析、设计报告、流程审计、风险判断 | `gpt-5.5` | `deepseek v4-pro` | 分析 Query/Deep Research/compile review 主链时用 `gpt-5.5`；普通日志归类可用 DeepSeek。 |
| agentC：文档 / 报告治理 | 清理报告、维护进度台账、整理文档、生成状态说明 | `deepseek v4-pro` | `gpt-5.5` | 默认不改生产代码；需要重写 AGENTS 或关键规范时可升级。 |
| agentD：验证 / 测试 | 跑 redline、`mvn test`、baseline、SWIP eval，输出验证报告 | `deepseek v4-pro` | `gpt-5.5` | 只跑命令和汇总用 DeepSeek；解释复杂回归原因时升级。 |

## 常见任务怎么分派

| 任务 | 给谁 | 模型 | 是否允许改代码 |
|---|---|---|---|
| 审一份修复报告，决定下一步 | 项目架构师 | `gpt-5.5` | 否 |
| 写下一轮强约束 prompt | 项目架构师 | `gpt-5.5` | 否 |
| 修 Query / AnswerGeneration / Citation / Deep Research | agentA | `gpt-5.5` | 是，但每轮只修一个根因 |
| 分析 compile review 是否真实生效 | agentB | `gpt-5.5` | 否 |
| 分析 SWIP eval 失败归因 | agentB | `gpt-5.5` | 否 |
| 跑 redline + `mvn test` + baseline | agentD | `deepseek v4-pro` | 否 |
| 清理旧报告 | agentC | `deepseek v4-pro` | 否，除报告删除 |
| 更新进度台账 | agentC | `deepseek v4-pro` | 否，除文档 |
| 提交前质量复核 | 项目架构师 | `gpt-5.5` | 否 |
| 生成 commit message 并提交 | 专门提交 agent / `/code-commit` | `gpt-5.5` | 只做提交 |

## 升级到 GPT-5.5 的条件

以下任一条件满足时，不要用 DeepSeek 直接拍板：

- 涉及 `src/main/java/com/xbk/lattice/query/**`
- 涉及 `AnswerGeneration`、fallback、citation、rerank、RRF、Deep Research
- 涉及编译入库审查、fact card、evidence anchor、projection
- 报告显示新增回归或指标负收益
- 需要判断测试断言是否过期
- 需要判断是否可以修改 eval / baseline
- 需要判断是否回滚某个代码修复
- 需要最终决定能否提交

## DeepSeek 适合做的事

- 读取报告并按模板摘出指标
- 列出失败 case 清单
- 跑命令并记录结果
- 清理过期报告
- 更新进度台账
- 做低风险文档整理
- 检查文件是否存在、报告是否齐全

DeepSeek 的输出必须经过项目架构师或用户确认后，才能进入代码修复或提交决策。

## 并行规则

- 同一时间只能有一个 agent 修改 `src/main/java/**`。
- agentA 改代码时，agentB 只能做不相交的只读分析，agentC 只能做清理计划，agentD 只能在代码修复完成后验证。
- 不允许 agentA 和另一个 agent 同时改 Query、prompt、runner、题集或模型配置。
- 如果两个 agent 的结论冲突，以项目架构师评审为准，不让执行 agent 自己合并判断。

## 新窗口恢复流程

新开 agent 窗口时，把下面这段放进提示词开头：

```text
你现在接手本项目的指定 agent 职责。

开始前必须先读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md

读取后只确认当前职责、当前禁止事项、当前下一步，不要直接修代码。
```

然后再追加该 agent 的具体任务 prompt。

## 项目架构师恢复提示词

如果当前“项目架构师 / 质量推进顾问”窗口不可用，重新开一个 `gpt-5.5` agent，并直接投喂下面这段：

```text
你现在不是代码修复执行者，而是本项目的“项目架构师 / 质量推进顾问 / 报告评审助手”。

你的职责：
1. 阅读报告、git diff 摘要、baseline 结果和测试结果。
2. 判断当前项目状态是否正确。
3. 判断本轮修复是否有效、是否有新增风险。
4. 检查是否违反 AGENTS.md、redline、Query/Answer 修复禁令。
5. 拆分下一步，只允许推荐一个最小动作。
6. 给出可直接复制给执行 agent 的强约束 prompt。
7. 防止执行 agent 为了准确率写硬编码、case 特判、固定答案、测试断言漂移或污染 Query 主链。

开始前必须先读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md

读取后先输出：
- 当前项目阶段
- 当前 gate 状态
- 当前 agent 分工
- 当前禁止事项
- 当前下一步建议

角色边界：
- 不直接修代码。
- 不擅自提交。
- 不建议一次性修所有失败。
- 不建议放宽 redline、eval gate 或 citation threshold。
- 不鼓励修改测试断言，除非用户明确确认预期过期。
- 遇到 Query / Answer / Retrieval / Citation / Deep Research / Compiler 主链问题，默认先分析再修。

输出格式：
1. 当前状态判断
2. 本轮报告 / 修复评价
3. 剩余问题分组
4. 下一步建议
5. 下一步禁止事项
6. 可直接复制给对应 agent 的提示词

如果报告信息不足，必须明确写：
“这部分需要让代码 AI 读取源码 / surefire 日志 / git diff / baseline 输出后再判断。”
```

这个角色只负责判断和调度；代码执行交给 agentA，治理分析交给 agentB，文档清理交给 agentC，验证测试交给 agentD。

## 模型使用硬规则

- 不让 AI 自己决定换模型；由用户按本手册分配。
- 不因为“快”就让低成本模型改高危主链。
- 不因为“聪明”就让高成本模型跑重复命令。
- 不把模型配置、API key、hidden eval、业务答案写进代码。
- 模型选择只影响执行者，不改变 AGENTS 红线。

## 推荐日常流程

1. 项目架构师审报告，给出下一步 prompt。
2. 用户按本手册选择 agent 和模型。
3. agentA 只在明确允许时改一个最小代码点。
4. agentD 跑 redline、`mvn test`、baseline 或业务 eval。
5. 项目架构师复核结果。
6. agentC 更新 `docs/quality-progress-and-lessons.md` 并清理过期报告。

## 当前推荐分配

| 角色 | 模型 |
|---|---|
| 项目架构师 | `gpt-5.5` |
| agentA | `gpt-5.5` |
| agentB | 默认 `gpt-5.5`，简单只读归类可用 `deepseek v4-pro` |
| agentC | `deepseek v4-pro` |
| agentD | `deepseek v4-pro`，复杂回归解释升级到 `gpt-5.5` |
