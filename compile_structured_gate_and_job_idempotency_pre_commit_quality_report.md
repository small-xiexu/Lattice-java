# Structured Gate And Job Idempotency Pre-Commit Quality Review

## 结论

建议提交。当前门禁通过，生产改动集中在 StructuredTable Writer Gate 与 Compile Job active 提交幂等两条独立主线；未发现业务硬编码、case 特判、eval 污染或禁止范围变更。

建议拆成两个 commit，便于回滚和归因：

1. `feat(compiler): gate large structured tables before article writer`
2. `fix(compiler): deduplicate active compile job submissions`

## 门禁结果

| 项目 | 结果 |
| --- | --- |
| redline BLOCKER | 0 |
| redline REVIEW | 1860 |
| redline ALLOWLIST | 244 |
| mvn test | 834 / 0 / 0 / 0 |
| Maven 结论 | BUILD SUCCESS |
| Maven 命令 | `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test` |
| 完成时间 | 2026-05-19 13:16:03 +08:00 |

`REVIEW` 和 `ALLOWLIST` 数量来自全仓红线报告，属于既有人工复核候选；本轮变更文件定向扫描未命中 `scenarios`、`SWIP`、`支付`、`银行`、`caseId`、`expected`、`query-regression`、具体文件名或具体答案片段。

## 当前工作区摘要

`git status --short --branch` 显示当前分支 `codex/qa-polish` ahead 3。除本轮代码/测试文件外，工作区还有多份未跟踪分析/验证报告；`special_cases_report.md` 已由 redline 刷新。

`git diff --stat` 跟踪文件摘要：

| 文件组 | 摘要 |
| --- | --- |
| `special_cases_report.md` | redline 输出刷新，非生产逻辑 |
| `AnalyzeNode.java` | 接入结构化表 Writer Gate |
| `CompileJobService.java` | 提交前查找 active job 并复用 jobId |
| `CompileJobJdbcRepository.java` / `CompileJobMapper.java` / `CompileJobMapper.xml` | 增加 active submission target 查询 |
| `CompileJobJdbcRepositoryTests.java` | 增加 repository 层幂等查询覆盖 |

另有未跟踪但属于本轮范围的新文件：

| 类型 | 文件 |
| --- | --- |
| 生产代码 | `src/main/java/com/xbk/lattice/compiler/node/StructuredTableWriterGatePolicy.java` |
| 测试 | `src/test/java/com/xbk/lattice/compiler/node/AnalyzeNodeStructuredTableWriterGateTests.java` |
| 测试 | `src/test/java/com/xbk/lattice/compiler/service/CompileJobServiceTests.java` |

## 变更范围复核

### 生产代码

| 文件 | 是否在本轮允许范围 | 复核结论 |
| --- | --- | --- |
| `src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java` | 是 | 只在分析阶段接入 gate，未绕过 Review / Persist |
| `src/main/java/com/xbk/lattice/compiler/node/StructuredTableWriterGatePolicy.java` | 是 | 新增通用结构化表 gate 策略 |
| `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java` | 是 | active job 提交幂等，默认仍允许终态重提 |
| `src/main/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepository.java` | 是 | 新增查询封装，无 schema 变更 |
| `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.java` | 是 | 新增 mapper 方法 |
| `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.xml` | 是 | 只查 `QUEUED` / `RUNNING` active job |

未发现其他生产代码、配置、schema、前端、脚本、redline allowlist 变更。

### 测试

| 文件 | 是否在本轮允许范围 | 覆盖点 |
| --- | --- | --- |
| `src/test/java/com/xbk/lattice/compiler/node/AnalyzeNodeStructuredTableWriterGateTests.java` | 是 | 大表生成 overview、小表不 gate、普通 Markdown 不受影响 |
| `src/test/java/com/xbk/lattice/compiler/service/CompileJobServiceTests.java` | 是 | service 层复用 active job、default-source 不做 sourceId 全局互斥 |
| `src/test/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepositoryTests.java` | 是 | `sourceSyncRunId`、规范化 `sourceDir`、managed source id-only、终态 job 不拦截 |

未修改 eval 题集、query baseline、SWIP runner、`docs/test/**`。

## 代码风险评审

### StructuredTable Writer Gate

结论：属于通用能力，不是业务特判。

依据：

- 触发信号来自通用结构化表元数据：`contentType=structured_tables`、表/行/列数量、表结构。
- 阈值按行数触发，不绑定文件名、业务词、题集 case 或答案片段。
- gate 后生成表级 overview concept，仍进入 Writer -> Reviewer -> Fixer -> Re-review -> Persist 主链。
- 原始 structured tables / rows / fact cards / source chunks 不被删除，不绕过结构化查询能力。

剩余风险：

- `ROW_COUNT_THRESHOLD=200` 是代码内通用阈值，当前可接受；后续若要按环境调参，可再外部化。
- overview 只概括 schema / 行列规模，不逐行生成 article；这是本轮性能目标，但需要依赖 structured evidence 通道承接精确查询。

### Compile Job Active 提交幂等

结论：策略边界符合当前设计。

依据：

- 只拦截 `QUEUED` / `RUNNING`。
- 不拦截 `SUCCEEDED` / `FAILED`，允许完成后再次提交。
- `default-source` 不做 sourceId-only 全局互斥，避免不同 `sourceDir` 被错误合并。
- 路径归一化在 service 层完成，未修改数据库 schema。

剩余风险：

- 当前是 service + SQL 查询式幂等，不是数据库唯一约束；极端并发下仍可能存在竞态窗口。考虑到本轮目标是 active 提交去重的最小修复，暂不阻塞提交。
- XML 中 `source_dir` 分支与 `source_id + source_dir` 分支存在轻微冗余，不影响结果正确性。

## 禁区检查

| 禁区 | 是否触碰 |
| --- | --- |
| `src/main/java/**` 之外的非允许生产代码 | 否 |
| 非允许测试文件 | 否 |
| `src/main/resources/**` 配置或 schema | 否；仅 mapper XML |
| `scripts/scan-redline.sh` / redline allowlist | 否 |
| 前端展示 | 否 |
| structured table 入库去重 | 否 |
| fact card 生成逻辑 | 否 |
| Writer / Reviewer / Persist gate 绕过 | 否 |
| clean rebuild / SWIP eval / query baseline | 否 |

## 提交前清理建议

建议提交前先决定报告是否入 commit，避免把临时排查材料混入功能提交。

推荐保留但不一定随代码提交：

- `compile_structured_table_writer_gate_fix_result_report.md`
- `compile_structured_table_writer_gate_runtime_verification_report.md`
- `compile_job_submission_idempotency_fix_result_report.md`
- `compile_job_submission_idempotency_runtime_verification_report.md`
- 本报告

建议不要混入本次功能 commit，除非用户明确要保留全量过程材料：

- `compile_performance_current_job_snapshot_report.md`
- `compile_performance_slow_job_stop_report.md`
- `post_prompt_externalization_*`
- 早期设计/triage 报告

## 推荐提交拆分

### Commit 1

Message:

```text
feat(compiler): gate large structured tables before article writer
```

建议包含：

- `src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java`
- `src/main/java/com/xbk/lattice/compiler/node/StructuredTableWriterGatePolicy.java`
- `src/test/java/com/xbk/lattice/compiler/node/AnalyzeNodeStructuredTableWriterGateTests.java`
- 可选：对应 fix / runtime verification 报告

### Commit 2

Message:

```text
fix(compiler): deduplicate active compile job submissions
```

建议包含：

- `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java`
- `src/main/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.xml`
- `src/test/java/com/xbk/lattice/compiler/service/CompileJobServiceTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepositoryTests.java`
- 可选：对应 fix / runtime verification 报告

## 最终判断

可以提交，建议拆成两个 commit。提交前唯一建议动作：整理 staged 文件边界，确保两个 commit 各自只包含对应代码、测试和可选报告，不把临时性能/验收过程报告混入。

本轮是否修改代码：否。  
本轮是否修改测试：否。  
本轮新增文件：仅本质量复核报告。
