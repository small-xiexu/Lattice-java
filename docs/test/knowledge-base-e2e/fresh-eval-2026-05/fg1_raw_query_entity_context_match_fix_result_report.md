# FG1 Raw Query Entity Context Match Fix Result Report

执行时间：2026-06-04 15:04 ~ 15:11
执行人：agentA（代码执行 Agent）

## 1. 失败归因确认

FG1 多目标失败是 query 原文实体上下文匹配缺口，不是 metadata 缺失：

- 上一轮 runtime gate 已确认 terminal unit metadata 中存在 `contextDisplayValues`，且第二个目标实体的 display value 已在 metadata 中。
- 失败点发生在 `AnswerFallbackConclusionBuilder` 的 Phase 2 附加候选收集：同 `terminalKey` sibling candidate 已进入候选，但 `entityContextMatchesQuery` 只依赖 query tokens。
- CJK tokenizer 未覆盖 query 后半句的实体文本，导致原始 query 中明确出现的第二个 entity context display value 没有进入 token 匹配路径。

因此本轮只修复 entity context 的通用匹配入口：在现有 token 匹配之外，允许用归一化后的原始 question 文本匹配归一化后的 `contextDisplayValues`。

## 2. 修改文件与最小 diff 摘要

修改文件：

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`

本轮增量摘要：

- `buildTerminalUnitExactConclusionLines` 增加 `question` 参数，用于附加候选 entity context guard。
- `entityContextMatchesQuery` 保留既有 query token + entity context haystack 匹配逻辑。
- 当 token 匹配未命中时，新增 `entityContextDisplayValueMatchesRawQuestion`：
  - 仅读取 `metadataJson.contextDisplayValues`。
  - 对原始 question 和 display value 做通用归一化。
  - 若归一化后的原始 question 包含归一化后的 display value，则判定 entity context 命中。
- 新增 `normalizeEntityContextText`：
  - 小写化。
  - 仅保留 `Character.isLetterOrDigit` 字符。
  - 去除空白、标点等格式差异。

说明：接手时 `AnswerFallbackConclusionBuilder.java` 已存在未提交改动；本报告只描述本轮新增的 raw query display value 匹配增量，不把接手前已有 terminal-unit 多目标聚合、trace、排序等改动归为本轮。

## 3. 通用修复点说明

本轮修复的是 entity context 匹配的通用信息源覆盖：

- 原逻辑只消费 query tokens，依赖 tokenizer 覆盖所有实体。
- 新逻辑在 token 匹配失败后，补充消费原始 question 文本。
- 匹配对象限定为结构化 metadata 中的 entity-level `contextDisplayValues`，不读取 hit content，不读取字段语义，不改变检索、重排、RRF、EvidenceSelector 或 Materializer。

该修复使“原始问题明确点名多个实体，而 tokenizer 漏掉其中一个实体”的场景可被 Phase 2 附加候选收集消费。

## 4. 为什么不是 case 特判

- 生产代码未加入任何具体题目、case id、文档名、文件名、业务词或答案值判断。
- 未加入特定实体名、字段名、题面短语、数值或 YAML 文件名判断。
- 匹配逻辑只依赖通用 metadata 字段名 `contextDisplayValues` 和通用文本归一化。
- 仍要求候选先通过既有同 `terminalKey`、不同 `parentPath`、field-token 阈值等通用 gate；raw question 匹配只作为 entity context guard 的补充信号。

## 5. Redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：通过，退出码 0。

`special_cases_report.md` 汇总：

- 总命中：2358
- 高风险：0
- 中风险：2096
- 低风险：262
- `BLOCKER=0`
- `REVIEW=2096`
- `ALLOWLIST=262`

## 6. mvn test 结果

命令：

```bash
mvn test
```

结果：通过。

Maven 汇总：

- Tests run: 995
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS
- Total time: 06:40 min
- Finished at: 2026-06-04T15:11:49+08:00

## 7. 未执行项说明

按本轮约束，未执行以下操作：

- 未清库。
- 未重建 schema。
- 未运行完整 Public Eval。
- 未执行 runtime gate。
- 未修改 `src/test/java/**`。
- 未修改 `scripts/**`。
- 未修改 prompt、config、schema、题集或 redline allowlist。
- 未提交 commit。

## 8. 下一步建议

建议交给 agentD 做 runtime gate 验证，至少覆盖：

- FG1 多目标同时返回两个目标 terminal values。
- FQ3 单目标仍只返回单个目标 terminal value。
- 单问 FG1 仍只返回单个目标 terminal value，不带出 sibling 值。
- FQ4 多目标仍返回两个目标值，且不误选 sibling approval 字段。

agentD 验证时应输出独立 `*_verification_report.md` 或 `*_gate_report.md`，并明确记录 runtime trace 中 Phase 2 additional candidates 的消费情况。
