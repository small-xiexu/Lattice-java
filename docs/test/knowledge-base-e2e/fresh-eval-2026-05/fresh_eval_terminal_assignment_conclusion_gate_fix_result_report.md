# fresh eval terminal assignment conclusion gate 修复报告

## 1. 已回退的 selector gate 无效改动

- 已 scoped 回退上一轮对 `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java` 的 selector gate 改动。
- 已 scoped 回退上一轮对 `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java` 的配套测试改动。
- 当前复核结果：上述两个文件在本轮最终 diff 中无残留改动。

## 2. 本轮最终修改文件

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilderTests.java`
- `special_cases_report.md` 由 redline 扫描命令重新生成。
- 本报告：`docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_terminal_assignment_conclusion_gate_fix_result_report.md`

工作树中还存在本轮未处理的历史/外部脏文件与未跟踪报告，例如 `docs/模型绑定配置参考.md` 及若干 `docs/test/knowledge-base-e2e/**` 报告文件；本轮未对这些文件做业务修复。

## 3. 最小修复点

修复点限定在 `AnswerGenerationFallbackConclusionSupport.buildTerminalFieldExactPathConclusionLines`：

- 在原 terminal path 保护逻辑前增加 structured FACT_CARD terminal assignment gate。
- 只从 `QueryEvidenceType.FACT_CARD` 的结构化候选行中识别明确的 `path = value` assignment。
- 当候选能够被问题 token、path segment、同父 sibling context、值形态与 FACT_CARD 元数据共同支持，并且分数满足唯一性 margin 时，直接复用现有 `appendAggregatedConclusionLine` 生成单条 `answer_markdown` 结论。
- 若候选不唯一、分数不足、缺少结构化 assignment，返回空并交还原聚合/摘要分支。
- 未修改 outcome、generationMode、modelExecutionStatus、citation schema、prompt 或资源配置。

## 4. 为什么不是 fresh eval 特判

本轮 gate 只依赖通用结构信号：

- `QueryEvidenceType.FACT_CARD`
- `fieldPath` / `keyPath` / `contextPath` / `displayText` 等结构化 assignment 形态
- 通用 `path = value` 解析
- path segment 与 query token 的覆盖度
- 同 FACT_CARD 内 sibling assignment 上下文
- 数字、路径、布尔等通用 value shape
- FACT_CARD 元数据中通用 card/type/shape 信号

本轮生产代码与新增测试均未写入 fresh eval 题面、case id、文件名、业务词、答案值、字段中文白名单或中文字段语义映射，也未通过 prompt、答案模板、兜底文案或配置词表硬补答案。

## 5. redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

- 退出码：0
- `BLOCKER=0`
- `REVIEW=2064`
- `ALLOWLIST=259`

## 6. 定向测试结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackConclusionBuilderTests,AnswerGenerationServiceTests,AnswerFallbackEvidenceSelectorTests test
```

结果：通过。

- `AnswerGenerationServiceTests`：77 run, 0 failures, 0 errors
- `AnswerFallbackConclusionBuilderTests`：6 run, 0 failures, 0 errors
- `AnswerFallbackEvidenceSelectorTests`：6 run, 0 failures, 0 errors

新增 synthetic 中性样例覆盖：

- structured FACT_CARD terminal assignment 能生成 conclusion line
- 无 structured assignment 时不抢答
- 多 sibling assignment 时优先选择更贴近问题 token / value shape 的一行
- path/port 类 terminal 保护不回归

## 7. 全量 mvn test 结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：未通过，退出码 1。

- 汇总：925 run, 0 failures, 2 errors, 0 skipped
- `VectorJdbcRepositoryOperatorTests.shouldSearchArticleVectorsWhenOperatorLivesInPublicSchema`：数据库中已有 `articles.id=1`，测试更新主键时触发 `articles_pkey` duplicate key。
- `SourceDomainIntegrationTests.shouldCreateDefaultSourceAndPersistSyncRuns`：`NoSuchElementException: No value present`。

这两个失败不在本轮允许修改范围内，且本轮遵守约束未清库、未重建 schema、未修改相关测试或生产代码。

## 8. 硬编码扫描结果

全局扫描：已执行用户指定的 `git diff | rg` 硬编码扫描命令。

结果：有命中。命中来自工作树中既有的 `docs/模型绑定配置参考.md` diff，内容为密钥字段和 secret-like 值片段；该文件不属于本轮允许修改范围，本轮未编辑该文件。

本轮 Java scoped diff 复查：已对本轮两个 Java 文件 diff 执行相同硬编码扫描。

结果：无命中，退出码 1。

## 9. 残余风险

- structured terminal assignment gate 使用启发式评分与唯一性 margin；若 FACT_CARD 内存在多个高度相似 sibling 且问题 token 无法区分，gate 会保守返回空，交由原聚合路径处理。
- 全量 `mvn test` 当前受共享数据库状态影响失败，尚不能作为全绿门禁；需要在允许清理测试数据库或修复测试隔离后复跑。
- redline 仍有 REVIEW / ALLOWLIST 候选，需要按既有治理流程人工复核；本轮没有新增 BLOCKER。

## 10. 交付状态

- 未 stage、未 commit、未 push。
- 未主动读取或修改 `docs/模型绑定配置参考.md`；仅按用户要求执行的全局 `git diff | rg ...` 硬编码扫描被动命中了该文件既有 diff 片段。
- 未清库、未重建 schema、未重新导入 fresh eval 资料。
- 未修改 selector、builder、snippet selection、question type、exact lookup、resources、scripts、prompt、redline allowlist 或 citation schema。
