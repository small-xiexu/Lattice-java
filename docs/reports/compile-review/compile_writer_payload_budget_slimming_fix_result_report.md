# compile_writer_payload_budget_slimming_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
  - 新增 Writer payload 预算常量：
    - `WRITER_TOTAL_PAYLOAD_MAX_CHARS = 12000`
    - `WRITER_STRUCTURED_SECTIONS_MAX_CHARS = 4000`
    - `WRITER_SOURCE_SNIPPET_MAX_CHARS = 3000`
  - 修改 `buildCompilePrompt(...)`
  - 新增 `buildStructuredSectionsPayload(...)`
  - 新增 `buildWriterSourceContents(...)`
  - 新增 `appendBoundedStructuredSection(...)`
  - 扩展 `buildRelevantSourceContents(...)` / `calculateSourceContentBudget(...)` 支持可配置的 per-source 预算
- `src/test/java/com/xbk/lattice/compiler/service/SchemaAwarePromptsTests.java`
  - 新增 Writer payload 预算控制回归测试

## 2. Writer 之前的 payload 构造方式

修改前，Writer payload 的构造方式是：

- 先无界追加全部 `Structured concept sections`
- 再按 `sourcePaths` 遍历每个 source
- 每个 source 使用现有相关性选择：
  - `sourceRef` 对应章节优先
  - 否则回退到 `DocumentSectionSelector.select(...)`
- 但每个 source 只受 `4000` 字符上限约束，**没有整次 Writer payload 的总量上限**

结果是：

- source 数一多，prompt 体积会近似线性膨胀
- structured sections 也可能整体放大 prompt
- Writer 单次调用成本在多 source / 多 section 场景下偏高

## 3. Writer 现在的 payload 预算策略

现在 Writer payload 改为“两段预算、统一控总量”：

- 总预算：`12000` 字符
- 结构化章节预算：`4000` 字符
- source 正文预算：`12000 - structuredSectionsPayload.length()` 的剩余预算

具体行为：

- 结构化章节先进入 prompt，但不再无界追加，只能在 `4000` 字符内按顺序写入
- source 正文再进入 prompt，并共享剩余预算
- 多 source 场景下，source 内容预算按“剩余总预算 / 剩余 source 数”动态均分
- 单个 source 仍有上限：`3000` 字符

因此，多 source 时不再是“每个 source 最多 4000，一路叠加”，而是：

- 先扣除 structured budget
- 再按剩余总量给每个 source 分摊预算
- 每个 source 实际可用预算为：
  - `min(3000, 剩余总预算 / 剩余 source 数)`

## 4. structured sections 是否进入预算控制

是。

本轮把 structured sections 也纳入预算控制，不再无界拼接：

- 统一通过 `buildStructuredSectionsPayload(...)` 构造
- 每个 section 在写入时预留：
  - section header
  - source refs 行
  - section footer
- `contentLines` 会按剩余可用字符数做 `boundText(...)` 截断
- 超出 `4000` 总预算后，后续 structured sections 不再继续追加

也就是说，structured sections 和 source payload 现在都受显式预算控制。

## 5. 是否复用了 Reviewer / Writer 现有的 section 选择能力

是。

本轮没有重写相关性逻辑，而是复用了现有能力：

- `sourceRef` 优先：`selectContentBySourceRefs(...)`
- 相关 section 选择：`DocumentSectionSelector.select(...)`
- bounded selection：`boundText(...)`
- Reviewer/Fixer 已有的按剩余 source 数动态分摊预算思路，也在 Writer 侧复用到了 `buildRelevantSourceContents(...)`

本轮修改的是 **budgeting / payload construction**，不是相关性路由本身。

## 6. 是否减少 Writer 覆盖面

否。

- Writer 仍然会对原本进入该阶段的 concept 执行
- 本轮只缩小单次输入体积，不改变 Writer 是否触发

## 7. 是否新增业务特判

否。

- 没有按具体文档名、业务词、表名或 case 做特判
- 只使用通用预算控制与现有相关片段选择机制

## 8. redline BLOCKER 是否仍为 0

- 已再次运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`

## 9. 测试是否通过

- 定向测试：
  - `SchemaAwarePromptsTests`
  - `CompileArticleReviewFlowTests`
- 结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`
- 全量 `mvn test`：`Tests run: 858, Failures: 0, Errors: 0, Skipped: 0`

## 10. 下一轮是否建议交给 agentD 做性能复验

建议。

下一轮建议 agentD 做 runtime 性能复验，重点关注：

- Writer 单次 prompt 大小是否明显下降
- 多 source concept 的 Writer 调用耗时是否下降
- 总 compile 耗时是否继续下降
- Writer 覆盖面是否保持不变
