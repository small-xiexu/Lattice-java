# query_partial_answer_multi_point_expansion_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/query/service/AnswerPromptBuilder.java`
  - 增强 `SYSTEM_QUERY_ANSWER` 的多点答案展开约束
- `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java`
  - 修改 `compressStructuredExactLookupAnswer(...)`
  - 新增 `shouldKeepExpandedMultiPointAnswer(...)`
  - 新增 `countStructuredKeyValueLines(...)`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`
  - 新增多焦点结构化答案展开回归测试

## 2. 多点答案展开之前为什么不完整

本轮定位到的单一根因是：

- 多点题（例如“分别是什么”“A、B、C 各自是什么”）即使 evidence 足够、citation 足够，最终答案仍可能被收敛成一句总述或过度压缩的短答。

原因主要有两层：

1. **Prompt 约束不够强**
   - 之前 Prompt 虽然要求“显式点名多个标识时逐项回答”
   - 但对“多焦点解释/枚举题必须展开，而不是压成一句摘要”的约束还不够直接

2. **段落后处理对精确查值题有压缩逻辑**
   - `AnswerParagraphPostProcessor.compressStructuredExactLookupAnswer(...)`
   - 会把结构化答案收敛成更短的直接回答
   - 这对单点精确查值是好事，但对已正确展开的多焦点答案，会有被压扁的风险

所以当前 `PARTIAL_ANSWER` 的一个关键来源，不是 retrieval 断层，而是 **多点信息在 answer generation 阶段没有稳定保留展开形态**。

## 3. 现在如何约束展开

本轮只做了通用多点展开约束增强：

1. **Prompt 层增强**
   - 明确要求：
     - 如果问题显式点名多个并列焦点，必须逐项展开覆盖
     - 不要把多个焦点压缩成一句总述
   - 对多点枚举 / 多焦点解释题，优先使用逐项列表、表格或分段形式展开

2. **后处理层保护**
   - 在 `compressStructuredExactLookupAnswer(...)` 前增加保护判断
   - 如果满足：
     - 问题存在多个结构化焦点
     - 问题具有多焦点/枚举信号
     - 当前答案已经覆盖多个焦点
     - 且答案已经是列表 / 键值行 / 多段展开形态
   - 则**不再继续压缩**，直接保留已展开答案

这样修完以后：

- 单点精确查值题仍可继续压缩，保持简洁
- 多点题在 evidence 足够时，会更稳定地逐项展开，不再轻易被压回一句摘要

## 4. 是否新增业务特判

否。

- 没有按文档名、业务词、case 字符串做特判
- 使用的都是已有通用信号：
  - 多焦点分隔符
  - 结构化焦点提取
  - 枚举题判断
  - 列表 / 键值行 / 多段结构判断

## 5. redline BLOCKER 是否仍为 0

- 已运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`，`REVIEW=1912`，`ALLOWLIST=246`

## 6. 测试是否通过

- 定向测试通过：
  - `AnswerGenerationServiceTests`
  - `AnswerParagraphPostProcessorTests`
- 结果：`Tests run: 67, Failures: 0, Errors: 0, Skipped: 0`
- 全量 `mvn test`：`Tests run: 866, Failures: 0, Errors: 0, Skipped: 0`

## 7. 下一轮是否建议交给 agentD 做 query runtime 复验

建议。

下一轮建议 agentD 重点做 query runtime 复验，关注：

- `PARTIAL_ANSWER` 场景里，多点答案是否比之前展开得更完整
- retrieval / citation 指标是否保持不变
- fallback outcome 主语义是否未被影响
