# Q6 fallback complementary evidence gate fix result report

- 执行时间：2026-05-27 00:41 CST
- 执行角色：agentA，代码修复执行 Agent

## 修改文件

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java`
- `docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_fix_result_report.md`
- `docs/quality-progress-and-lessons.md`

## 本轮根因

`selectComplementaryEvidenceByQuestionTokens` 在 `SOURCE + ARTICLE` 已覆盖问题高信号词时提前返回，导致同候选池内高分、贴题、结构化路径取值的 `FACT_CARD` 没进入最终 fallback 证据集合。

## 最小修复点

仅在 `AnswerFallbackEvidenceSelector` 的 complementary evidence 选择路径内补一个窄门：

- 保留原有 `SOURCE` / `ARTICLE` / `CONTRIBUTION` 互补选择行为。
- 仅当问题是 exact lookup，且已选集合同时包含 `SOURCE` 与 `ARTICLE` 或 `CONTRIBUTION` 时，才尝试补充结构化事实卡片。
- 只补入 `QueryEvidenceType.FACT_CARD`。
- 候选必须同时满足 question-focused score 不低于 `80`，且候选行命中通用的 `looksLikeQuestionFocusedStructuredPathValueCandidate`。
- 找不到符合条件的 fact card 时保持原行为。
- 保留 canonical key 去重，不批量加入所有 fact card。

未修改 retrieval、RRF、rerank、citation、prompt、LLM binding、fact card 生成、fallback outcome、fallback conclusion 或 snippet shape gate。

## 为什么不是硬编码

修复只基于通用信号：

- evidence type：`FACT_CARD` / `SOURCE` / `ARTICLE` / `CONTRIBUTION`
- exact lookup 问题形态
- question-focused score
- 结构化字段路径与取值形态
- 查询 token 与结构化路径候选的焦点重合

生产代码和新增测试没有写入 Q6 文件名、题面、答案端口值、Kubernetes 专属概念、具体字段名、镜像名、域名或样例答案片段。

## 未写入的硬编码内容

本轮未在生产代码、测试 fixture 或配置中写入以下内容：

- Q6 题号或原始题面
- 验收 YAML 文件名
- readiness / liveness / probe 相关字段名
- TCP socket 字段名
- 端口答案值
- goproxy / registry.k8s 相关镜像或域名
- Kubernetes / k8s 专属判断
- 面向某份资料的答案模板或兜底文案

## 测试覆盖

新增/保留的 `AnswerFallbackEvidenceSelectorTests` 覆盖：

- `SOURCE + ARTICLE` 已被 complementary 命中时，仍会补入高分、贴题、结构化路径取值的 `FACT_CARD`。
- 不覆盖问题焦点的结构化 fact card 不会被强行加入。
- endpoint、image/version 等机器标识符问题不会被无关结构化 fact card 抢占。
- 原有空输入、直接 source 优先、path contract companion 行为保持不变。

## 验证结果

redline：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：通过，`BLOCKER=0 / REVIEW=2006 / ALLOWLIST=259 / 总命中=2265`。

定向测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackEvidenceSelectorTests,FactCardGenerationServiceTests test
```

结果：通过，`Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`。

全量测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：通过，`Tests run: 909, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

空白检查：

```bash
git diff --check
```

结果：通过，无空白错误。

## 未做事项

- 未清库。
- 未重导资料。
- 未重建向量。
- 未修改模型配置。
- 未运行端到端 Q6 API 验收。
- 未提交 commit。

## 剩余风险

- 本轮只保证 high-score question-focused structured path fact card 不再被 complementary early return 屏蔽，不保证 Q6 已端到端通过。
- runtime trace 曾提示同一 fact card 内 sibling 字段可能同分，端到端复验需要确认最终答案没有从机器标识符错误变成 sibling 字段错误。
- 多主题问题仍依赖 complementary evidence 保留旁证；本轮用 exact lookup、证据类型、分数和结构化路径焦点收窄影响面，但仍需通过真实链路观察。

## 下一步

交给 agentD 做独立端到端复验：复用当前 Q6 clean 库和模型配置，不清库、不重导、不重建向量，调用真实 Query API，确认最终 `fallbackHits`、答案 claim 与 citation 是否消费了 path-aware fact card，并输出独立 verification report。Q6 在 agentD 复验通过前不得标记完成。
