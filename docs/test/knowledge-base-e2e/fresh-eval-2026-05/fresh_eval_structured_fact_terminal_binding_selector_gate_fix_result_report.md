# Fresh Eval Structured Fact Terminal Binding Selector Gate Fix Result Report

## 1. Scoped Restore

已按本轮要求先 scoped restore 上轮无效改动，回退文件：

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationQuestionTypeSupport.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`

未使用 `git reset --hard`。

## 2. 本轮修改文件

本轮最终修改范围：

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java`
- `special_cases_report.md`：redline 脚本产物更新
- `docs/test/knowledge-base-e2e/fresh-eval-2026-05/fresh_eval_structured_fact_terminal_binding_selector_gate_fix_result_report.md`：本报告

工作区另有既有脏改动 `docs/模型绑定配置参考.md`，非本轮修改。

## 3. 修复点

修复落点限定在 `AnswerFallbackEvidenceSelector.selectComplementaryEvidenceByQuestionTokens` 的 early return 前。

原问题是互补证据已经选中 `SOURCE` / `ARTICLE` 后，方法可能直接返回，导致已召回且同源的 direct structured `FACT_CARD` 没有机会进入 fallback evidence，后续 citation projection 也拿不到该结构化事实证据。

本轮新增的 gate 会在 early return 前从已召回候选中选择最合适的 direct structured `FACT_CARD`，并通过 `addDistinctFallbackHit` 加入已选 fallback evidence。选择条件依赖通用结构信号：同源、结构化路径/字段信号、结构化事实类型信号、问题焦点覆盖、检索分数与已选摘要的相对竞争力。

## 4. 非特判说明

该修复没有引入 fresh eval 业务域、文件名、题面、答案片段或 case id 判断。生产代码只识别通用结构化证据信号，例如 structured path、direct assignment、fact card 类型元数据、同源路径与通用 score/rank 关系。

测试使用 synthetic 配置样例，只断言同源 direct structured `FACT_CARD` 能在 early return 前被保留进入 fallback evidence，不断言具体业务答案。

## 5. Redline

已运行：

`bash scripts/scan-redline.sh special_cases_report.md`

`special_cases_report.md` 汇总：

- 总命中：2300
- 高风险：0
- 中风险：2041
- 低风险：259
- `BLOCKER`：0
- `REVIEW`：2041
- `ALLOWLIST`：259

本轮未修改 redline 脚本、allowlist 或扫描规则。

## 6. 定向测试

已通过：

`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackEvidenceSelectorTests,AnswerGenerationServiceTests,AnswerFallbackConclusionBuilderTests test`

结果：86 个测试通过，0 失败，0 错误。

## 7. 全量测试

已通过：

`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`

结果：922 个测试通过，0 失败，0 错误，0 跳过；`BUILD SUCCESS`；耗时 06:16。

## 8. 硬编码扫描

已运行用户指定的全量 diff 敏感/特判扫描。扫描命中 6 行敏感密钥样式片段，全部来自既有脏改动 `docs/模型绑定配置参考.md`，不在本轮允许修改范围内，且不属于生产代码改动。

随后限定本轮两个允许代码文件再次扫描：

`git diff -- src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java | rg ...`

结果：无命中。

## 9. 残余风险

- 本轮只修 selector gate，未运行真实 fresh eval 端到端 query 回归；真实链路仍需后续验证 `FACT_CARD` 是否稳定进入最终 citation projection。
- direct structured `FACT_CARD` 的纳入依赖当前通用结构信号与 score/rank 阈值，极端低分或缺少结构元数据的 fact card 仍可能不会被选入，这是本轮为避免过度提升 evidence outcome 保留的边界。
- 本轮未改变 fallback outcome、answer audit、prompt 或 citation binding 语义。

## 10. Git 与文档边界

未 stage、未 commit、未 push。

未修改 `docs/模型绑定配置参考.md`。未主动打开该文件全文；但全量 `git diff` 硬编码扫描受工作区既有脏改动影响，输出过该文件的敏感片段命中，因此这里明确记录为：未修改、未做文件级读取，扫描输出被既有 diff 命中污染。
