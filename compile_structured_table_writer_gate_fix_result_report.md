# compile structured table writer gate fix result report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java`
  - 新增 `structuredTableWriterGatePolicy` 字段。
  - 构造函数中初始化 `StructuredTableWriterGatePolicy`。
  - `analyze(...)` 中在结构化 AnalyzePayload 解析后、长文档专题拆分前调用 gate。
- `src/main/java/com/xbk/lattice/compiler/node/StructuredTableWriterGatePolicy.java`
  - 新增结构化大表 Writer gate 策略。
  - 读取 `RawSource.metadataJson.structuredContentJson`。
  - 基于 `contentType`、`tables`、`rowCount`、`columns` 生成表级 overview concept。
- `src/test/java/com/xbk/lattice/compiler/node/AnalyzeNodeStructuredTableWriterGateTests.java`
  - 新增大表触发 gate 测试。
  - 新增小型 structured table 不触发 gate 测试。
  - 新增普通 Markdown 长文档不受影响测试。
- `special_cases_report.md`
  - 由 redline 扫描命令刷新，不是业务代码修改。

## 2. gate 放在哪个位置

位置在 `AnalyzeNode.analyze(...)`：

1. `analyzeStructuredConcepts(...)` 之后；
2. `DocumentTopicConceptExtractor.extract(...)` 之前；
3. 命中后直接返回少量 overview concepts，不再进入长文档专题拆分；
4. overview concept 后续仍进入 Writer -> Reviewer -> Fixer -> Re-review -> Persist gate 主链。

## 3. 触发条件是什么

同时满足：

- `metadataJson` 中存在 `structuredContentJson`；
- `structuredContentJson.contentType == "structured_tables"`；
- `tables` 是数组；
- 表格存在有效 `columns`；
- 单表或源内表格总 `rowCount` 达到阈值。

未满足条件时，继续走原有 Markdown / LLM / fallback 分析路径。

## 4. 阈值是多少，为什么选择这个阈值

- 阈值：`200` 行。
- 原因：设计报告建议先用 `100` 或 `200` 行。这里选择更保守的 `200`，避免误伤小型 structured table，同时能覆盖本轮瓶颈源的 `1542` 行规模。

## 5. 大表格源预期 Writer concepts 从多少降到多少

根据 `compile_performance_bottleneck_analysis_report.md` 和设计报告：

- 当前瓶颈源贡献：`1640` 个 analyzed concepts，merge 后 `55` 个 Writer units。
- 该源已有 `structured_tables = 2`。
- 本轮 gate 命中后预期输出：每个 table 1 个 overview concept，即约 `2` 个 Writer concepts / Writer units。
- 预期收敛：`55 -> 2`。

## 6. 普通 Markdown 是否不受影响

是。

新增测试 `shouldKeepMarkdownTopicExtractionUnchanged` 验证普通 Markdown 长文档仍进入既有长文档专题拆分，并输出原有 topic concepts。

## 7. 小型 structured table 是否不受影响

是。

新增测试 `shouldNotGateSmallStructuredTable` 验证 `rowCount=12` 的 structured table 不触发 gate，仍走原有分析路径。

## 8. structured_tables / source chunks / fact cards 是否不受影响

是。

本轮只修改 `AnalyzeNode` 的 analyzed concept 生成入口；未修改 `PersistSourceFilesNode`、source file chunk、structured table、fact card、review、persist gate、query 等链路。

## 9. 是否新增业务硬编码

否。

触发条件只依赖通用结构信号：`structuredContentJson`、`contentType`、`rowCount`、`columns`。没有写 `scenarios.xlsx`、SWIP、银行、支付、case id、题目文本或答案片段特判。

## 10. redline BLOCKER 是否为 0

是。

最终 redline 结果：

- `BLOCKER=0`
- `REVIEW=1859`
- `ALLOWLIST=244`

新增 `StructuredTableWriterGatePolicy` 仅产生 2 条 `ALLOWLIST` 候选，均为结构化表格 / 工程默认值相关，不是业务答案硬编码。

## 11. mvn test 结果

- 定向测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest='*Analyze*' test`
  - 结果：`15 / 0 / 0`
- 全量测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`827 / 0 / 0`

## 12. 是否建议下一轮运行小流量 clean rebuild 验证

建议。

下一轮可由验证 agent 用小流量 clean rebuild 验证真实 job 中 `scenarios.xlsx` 或同类大表源的 `analyzed concepts / Writer units / compile duration` 是否按预期下降；本轮未清库、未跑完整 baseline、未跑 SWIP eval。
