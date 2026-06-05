# Mixed Script Token Extraction — 提交后文档收口报告

执行时间：2026-06-05
执行人：agentC（文档/报告治理 Agent）
范围：`062d391` 提交后的台账同步与未跟踪报告处置

## 1. 本轮修改文件

| 文件 | 操作 | 说明 |
|---|---|---|
| `docs/quality-progress-and-lessons.md` | 修改 | 新增 mixed script gate 记录、已验证结论、禁止事项、下一步计划；更新 redline/mvn test 基线 |
| `fg1_terminal_unit_current_breakpoint_analysis_report.md` | 删除 | 被 bootstrap guard fix（549f0e3）覆盖 |
| `fresh_eval_post_cleanup_remaining_failure_analysis_report.md` | 删除 | 被 terminal fix（549f0e3）+ mixed script fix（062d391）覆盖 |
| `mixed_script_post_commit_doc_cleanup_report.md` | 新建 | 本报告 |

## 2. quality-progress 更新摘要

### 2.1 当前阶段

新增条目：mixed script token extraction 已提交 `062d391`，记录修复范围、门禁结果、runtime 结果。

### 2.2 当前 Gate

- redline：`BLOCKER=0`，REVIEW 从 2077 更新到 2096
- mvn test：从 `995/0/0/0` 更新到 `1004/0/0/0`
- 新增 mixed script token extraction gate 条目（PASS）

### 2.3 已验证结论

- Mixed script 修复是通用 Unicode script 规则，非 case 特判
- FS4b 不再是 open issue

### 2.4 踩坑记录

新增 Han+Latin 混合脚本 token 未被提取的踩坑条目。

### 2.5 当前禁止事项

新增两条：
- 禁止继续在 mixed script token 上叠加 case 特判
- 后续 Query/Search 修复必须先 redline、mvn test、baseline/runtime gate

### 2.6 下一步计划

新增 item 58：mixed script token extraction 已提交。

## 3. 两个未跟踪报告的处置

### 3.1 fg1_terminal_unit_current_breakpoint_analysis_report.md

| 项目 | 内容 |
|---|---|
| 日期 | 2026-06-01 |
| 分析人 | agentB |
| 内容 | FG1 terminal unit 断点：LLM alias enricher 缺少 `field-alias-enricher` 绑定 |
| 处置 | **删除** |
| 依据 | 根因（enricher bootstrap guard 缺失）已在 549f0e3 中修复并提交；诊断数据（123 个 terminal unit，0 条中文别名）是运行时快照，不可重建但修复链路已完整归档在 committed reports 中。后续分析不再需要该历史快照。 |

### 3.2 fresh_eval_post_cleanup_remaining_failure_analysis_report.md

| 项目 | 内容 |
|---|---|
| 日期 | 2026-06-01 |
| 分析人 | agentB |
| 内容 | B0-B20 后两套 public eval 剩余失败复盘，推荐 FG1 为下一最小根因 |
| 处置 | **删除** |
| 依据 | 报告中提及的 FG1 已通过 549f0e3 修复，FS4b 已通过 062d391 修复。S2/FS2 仍为 open issue 但已有独立的 committed 分析报告（`search_failures_s2_fs2_fs4b_analysis_report.md`）。本报告的建议（"先让 agentB 只读归因 FG1"）已执行完毕，文档已无独立引用价值。 |

## 4. 明确排除文件列表

| 文件 | 状态 | 原因 |
|---|---|---|
| `docs/模型绑定配置参考.md` | 已修改，未暂存 | 私有配置，永远排除 |
| `special_cases_report.md` | 已修改，未暂存 | redline 输出，永远排除 |

## 5. 当前 git status 摘要

```
M docs/quality-progress-and-lessons.md
M docs/模型绑定配置参考.md       ← 排除
M special_cases_report.md         ← 排除
```

工作区无未跟踪文件。

## 6. 是否建议提交本轮文档收口

**建议提交。** 提交范围：

- `docs/quality-progress-and-lessons.md`
- `docs/test/knowledge-base-e2e/fresh-eval-2026-05/mixed_script_post_commit_doc_cleanup_report.md`

必须排除：
- `docs/模型绑定配置参考.md`
- `special_cases_report.md`

建议 commit message：
```
docs(test): record mixed script search gate status
```
