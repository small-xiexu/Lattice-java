# Terminal Fix Pre-Commit Quality Review Report

复核时间：2026-06-04
复核人：项目架构师

## 1. 复核范围

本次复核覆盖累计 terminal 修复包的 5 个生产文件：

| 文件 | 结论 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | 功能有效，但提交前需处理日志与注释风险 |
| `FactCardTerminalUnitMaterializer.java` | 通过 |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | 通过 |
| `FactCardTerminalUnitFtsSearchService.java` | 基本通过，有非阻断清理项 |
| `FactCardTerminalUnitIntentReranker.java` | 通过 |

已参考验证报告：

- `fg1_raw_query_entity_context_match_runtime_gate_report.md`
- `full_public_eval_after_fg1_raw_query_match_gate_report.md`
- `public_eval1_protection_after_fg1_raw_query_match_gate_report.md`

## 2. 验证证据

| Gate | 结果 |
|---|---|
| Redline | `BLOCKER=0` |
| Maven | `995/0/0/0, BUILD SUCCESS` |
| Targeted runtime gate | FG1/FQ4/FQ3/单问 FG1 全部 PASS |
| Public Eval 2 | Answer Accuracy `11/15 -> 13/15`，FG1/FQ4 FAIL -> PASS，无新增回归 |
| Public Eval 1 保护 | Q6 PASS，S2 PARTIAL 但无新增回归 |

功能效果证据充分：本轮修复解决了 FG1/FQ4 terminal multi-target 问题，没有破坏 Q6 与 S2 保护场景。

## 3. 阻断项

### P1. `TU_TRACE` 以 `info` 级别输出 query tokens 与 evidence exact line

文件：`src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

当前 terminal fallback 每次执行都会输出：

- query token 列表
- terminal exact line
- candidate scoring
- selected / additional candidate 信息

这些日志对 runtime gate 很有用，但不适合以 `info` 级别长期留在生产主链。风险包括：

- 泄露用户 query token 与知识库精确事实值
- 高频 query 场景下日志噪音明显
- 后续排查时难以区分诊断日志和业务日志

提交前建议将这些日志改为 `debug`，或使用显式诊断开关控制。保留 trace 能力可以接受，但不能默认 `info` 常开。

### P2. 生产注释中出现与本轮 eval 场景高度贴近的中文示例

文件：`src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

`hasCjkOverlap` 注释使用了类似中文碎片 token 与字段别名示例。虽然逻辑本身是通用 CJK bigram overlap，并未硬编码这些词，但生产主链注释不应携带与当前失败样例过近的业务语义示例。

提交前建议改成中性示例，或直接删除示例短语，仅保留通用说明。

## 4. 非阻断清理项

### P3. `FactCardTerminalUnitFtsSearchService.safeLimit` 已不再被使用

文件：`src/main/java/com/xbk/lattice/query/service/FactCardTerminalUnitFtsSearchService.java`

`search` 方法已直接计算 `requestedLimit` 与 `rawLimit`，旧的 `safeLimit` 方法变成未使用私有方法。该问题不影响功能与 gate，但提交前建议删除，降低后续误读成本。

## 5. 红线与硬编码检查

机械检查未发现生产逻辑中写入题号、case id、文件名、答案值或具体 eval 分支。当前能力点整体仍属于通用 terminal unit 能力：

- `contextDisplayValues` 来自编译期结构上下文
- raw query match 只消费 `metadataJson.contextDisplayValues`
- 附加候选仍受同 `terminalKey`、不同 `parentPath`、field-token threshold、entity context guard 限制
- FTS candidate supply 与 intent rerank 仍基于通用候选池和字段意图信号

需要注意：注释中的中文示例虽然不是运行逻辑，但建议按提交前清洁项处理。

## 6. 逐文件结论

### `AnswerFallbackConclusionBuilder.java`

功能方向正确，runtime 与 Public Eval 证据充分。多目标聚合的 gate 组合基本稳健，raw query context match 没有绕过同字段、不同父路径与字段 token 阈值。

提交前必须处理：

1. `TU_TRACE` 日志级别或诊断开关
2. CJK overlap 注释示例去业务化

### `FactCardTerminalUnitMaterializer.java`

`contextDisplayValues` 写入来自 sibling descriptor，属于结构化上下文，不来自 query/eval。通过。

### `LlmFactCardTerminalUnitFieldAliasEnricher.java`

bootstrap guard 移除后，route 可用性判断依然保留 modelName 与 fallback 保护。通过。

### `FactCardTerminalUnitFtsSearchService.java`

扩大 DB raw candidate pool 后再 rerank + 截断，是通用 candidate supply 修复。建议删除未使用的 `safeLimit`。基本通过。

### `FactCardTerminalUnitIntentReranker.java`

优先保留有字段意图信号的 hit，再按 adjustedScore 排序。未发现 case 特判。通过。

## 7. 遗留问题

以下问题不阻塞本 terminal 修复包，但不得写成已解决：

- Public Eval 2 FQ10：PDF source name 超长导致资料缺失，属于独立 infra 问题
- Public Eval 2 FS2 / FS4b：搜索侧召回问题仍存在
- Public Eval 1 S2：chunk identity 有改善，但 title/anchor 搜索仍 PARTIAL

## 8. 最终建议

**暂缓提交。**

本轮修复功能上可以保留，但提交前需要先做一个极小的 cleanup 轮：

1. 将 `AnswerFallbackConclusionBuilder` 的 `TU_TRACE` 默认 `info` 日志降为 `debug` 或诊断开关控制
2. 去掉或中性化 `hasCjkOverlap` 注释中的业务语义示例
3. 删除 `FactCardTerminalUnitFtsSearchService.safeLimit` 未使用私有方法

cleanup 后建议只跑：

- `bash scripts/scan-redline.sh special_cases_report.md`
- `mvn test`

若两项通过，可进入 `/code-commit`。
