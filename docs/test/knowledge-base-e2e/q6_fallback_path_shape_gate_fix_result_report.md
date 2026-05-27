# Q6 fallback path shape gate fix result report

- 执行时间：2026-05-26 22:20:19 CST
- 执行角色：agentA，代码修复执行 Agent

## 修改文件

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`
- `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_fix_result_report.md`

## 唯一根因

coverage-aware fallback snippet 在补齐 `path` 形态证据时，只使用原有
`matchesStructuredEvidenceShape(candidate, "path")` 判定。该判定偏向 slash / URL / path
signal，未优先消费已经通过通用判定的、贴合问题焦点的 structured path value candidate。

结果是 source chunk 中的 slash-like 机器标识符可能先补入 snippet，而 fact card 中的结构化字段路径取值没有进入最终 fallback 摘句。

## 最小修复点

仅在 `addBestCandidateForRequiredShape` 内处理 `shape == "path"` 的补位优先级：

- 当需要补 `path` 形态时，先遍历 `rankedCandidates`。
- 优先选择 `looksLikeQuestionFocusedStructuredPathValueCandidate(question, candidate, extractQueryTokens(question))` 命中的候选。
- 选择前仍检查 `selectedCandidates` 未包含该 candidate，且 `snippets.size() < limit`。
- 找不到上述候选时，继续走原有 `matchesStructuredEvidenceShape(candidate, shape)` 逻辑。

未修改 `containsPathSignal`，未把 dotted path 全局等同为 URL/path，未修改 exact lookup、fallback conclusion、fallback outcome、citation coverage、retrieval、rerank、prompt、LLM binding 或 fact card 生成链路。

## 为什么不是硬编码

修复只基于通用结构信号：

- structured path metadata
- dotted field path / assignment-like mapping
- question focus token overlap
- `path` shape 补位上下文

生产代码没有写入验收文件名、题面、答案片段、字段名特判、业务域特判或 Kubernetes 专属判断。

## 测试覆盖

新增/保留的 `AnswerGenerationServiceTests` 覆盖：

- source hit 排在前且包含 slash-like 机器标识符，fact card 排在后且包含贴题 structured field path value 时，fallback 答案选择 structured field path value。
- 单个 fact card 内 structured path value 不被机器标识符压过。
- 问题明确询问 URL / endpoint 时，仍返回真实 URL / endpoint。
- 问题焦点询问 image / artifact / version 这类机器标识符时，仍允许机器标识符作为答案。
- 不含 structured path metadata 的普通 `key: number` / `key = number` 保持原有 fallback 行为。

## 生产代码红线自查结果

对 `AnswerGenerationFallbackSnippetSelectionSupport.java` 执行硬编码关键词自查：

- 未命中验收文件名。
- 未命中验收题面。
- 未命中验收答案片段。
- 未命中禁止字段名、镜像名、域名、端口值或 Kubernetes 专属判断。

## redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

- BLOCKER：0
- REVIEW：1998
- ALLOWLIST：259
- 总命中：2257

## Maven 测试结果

定向测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests test
```

结果：通过，`Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`。

fact card 定向测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test
```

结果：通过，`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`。

全量测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：通过，`Tests run: 906, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

## git diff --check 结果

命令：

```bash
git diff --check
```

结果：通过，无空白错误。

## 未做事项

- 未清库。
- 未重导资料。
- 未重建向量。
- 未修改模型配置。
- 未跑端到端 Q6；本轮只完成最小代码修复和单元/门禁验证。

## 下一步

交给 agentD 做独立端到端复验，确认真实 Q6 链路在不清库、不重导、不改模型配置的前提下返回端口值，并继续输出独立 verification report。
