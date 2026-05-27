# Q6 fallback 二次根因分析报告

## 执行信息

- 执行时间：2026-05-26 21:07:19 +0800
- 本轮角色：agentB / 治理与链路分析 Agent
- 本轮边界：只读分析生产代码、报告与 git 状态；未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`；未清库、未重导、未重建向量、未调用会改变数据库状态的 API。
- 唯一写入：本分析报告。

## 已读取文件

- `AGENTS.md`
- `docs/quality-progress-and-lessons.md`
- `docs/test/knowledge-base-e2e/q6_end_to_end_verification_report.md`
- `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_fix_result_report.md`
- `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_verification_report.md`
- `docs/test/knowledge-base-e2e/q6_readiness_port_analysis_report.md`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupGroundingSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackAggregationSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackMarkdownBuilder.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationReferenceIdentifierSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationReferencePathSupport.java`
- 辅助只读：`QueryTokenExtractor.java`、`QueryEvidenceRelevanceSupport.java`、`AnswerGenerationBaseSupport.java`、`AnswerGenerationQuestionTypeBasicSupport.java`、`AnswerGenerationQuestionTypeSupport.java`、`AnswerGenerationCoreSignalSupport.java`、`FactCardFtsSearchService.java`、`FactCardMapper.xml`

## Redline 状态

- 已运行：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0 / REVIEW=1996 / ALLOWLIST=259`
- 结论：redline 当前不阻塞本轮只读归因；`REVIEW` 与 `ALLOWLIST` 仍需人工复核，不代表自动放行。

## 失败链路步骤

1. `preferDeterministicExactLookupPayload` 进入 exact lookup 偏好逻辑。前序报告已记录 `reason=GROUNDING_MISMATCH`、`groundingStatus=MISSING_PATH_SHAPE`，因此 LLM 初答被替换为 deterministic fallback。
2. `buildEvidencePayload` 重新构造 fallback payload；`buildEvidenceMarkdown` 再次调用 `selectFallbackEvidenceHits`。
3. `selectFallbackEvidenceHits` 不是主故障点。retrieval fused rank 1 是目标 source chunk，rank 2 是 path-aware fact card；exact lookup 下 `canonicalKey(question, hit)` 会按 evidence type 和 identity 保留同源 `SOURCE` / `FACT_CARD`，没有证据显示 fact card 在候选池被整体丢弃。
4. `appendEvidenceConclusion` 调用 `buildEvidenceConclusionLines`。
5. `AnswerFallbackConclusionBuilder.buildGeneralFallbackConclusionLines` 先跳过 spreadsheet / comparison / exact structured list。Q6 没有 slash path identifier，第一次 `containsRequestedExactPathIdentifier` 分支不触发；`shouldAggregateEvidenceConclusion` 也不应触发。
6. 随后兜到第二次 `buildExactPathConclusionLines`。Q6 因文件名 / 配置标识被 `looksLikePathQuestion` 识别为 path-like exact lookup，进入 exact path conclusion gate。
7. `buildExactPathConclusionLines` 对每个 fallback hit 调 `selectExactPathCandidateLines`；该方法先取 `selectQuestionFocusedFallbackSnippets(..., limit=3)`，再按 path 信号过滤。
8. `selectQuestionFocusedFallbackSnippets` 在 `limit > 1` 且 structured fact question 时进入 coverage-aware 分支。这里的关键 gate 是 `addBestCandidateForRequiredShape(..., "path")` -> `matchesStructuredEvidenceShape(candidate, "path")`。
9. 当前 `matchesStructuredEvidenceShape("path")` 只接受 `containsPathSignal(candidate)`。`containsPathSignal` 识别 slash / URL / HTTP method；它不把 dotted structured field path value 当作 path shape。
10. 结果是：带 slash 的机器标识符行可以满足 path shape；path-aware fact card 里的 dotted field path value 虽然在打分函数中被 boost，但在 coverage-aware 的 path shape 补位 gate 里没有被优先选为 path 事实。
11. `buildExactPathConclusionLines` 最后通过 `appendAggregatedConclusionLine` 输出首条结论，因此最终出现 `当前可确认的信息是：...` 前缀。这个前缀来自 deterministic fallback 结论构造，不是 LLM 生成。

## 唯一根因判断

唯一主链 gate：`AnswerGenerationFallbackSnippetSelectionSupport.addBestCandidateForRequiredShape` 的 path shape 补位逻辑。

根因不是资料缺失、编译抽取缺失、fact card 生成缺失、检索召回缺失，也不是 citation coverage。正确 path-aware fact card 已召回；失败发生在 deterministic fallback 的 coverage-aware snippet 选择阶段：结构化字段路径取值候选没有被当作 path shape 的一等候选，而 slash-like 机器标识符行被当成 path 事实进入 exact path conclusion。

这也解释了错误答案的形态：`appendAggregatedConclusionLine` 负责拼出 `当前可确认的信息是：`，后面的内容来自被选中的候选行。citation coverage=1.0 只说明该错误行确实存在于证据中，不说明它回答了用户问题。

## 四类候选判断

| 判断项 | 结论 | 依据 |
|---|---|---|
| 候选池没收进来 | 否 | retrieval fused rank 已包含 source chunk 与 path-aware fact card；fallback selector 对 exact lookup 保留同源多类型证据。 |
| 候选打分不够 | 局部不是主因 | agentA 已给 question-focused structured path value 加高分；问题在 coverage-aware path shape 选择未把这类候选作为 path shape 优先消费。 |
| exact identifier/path gate 过滤 | 是，属于本轮唯一 gate | Q6 被文件名/配置标识推入 exact path conclusion；path shape gate 只识别 slash/URL，导致 dotted field path value 未稳定主导最终结论。 |
| aggregation 又选回 source chunk | 不是普通 aggregation；是 exact path conclusion 复用 aggregation append | `appendAggregatedConclusionLine` 产生前缀，但入口应是 `buildExactPathConclusionLines`，不是 `buildAggregatedEvidenceConclusionLines` 的多事实聚合。 |

## 为什么 agentA 首轮修复未闭环

agentA 首轮修复主要覆盖了单条候选行打分和单 FACT_CARD 场景：当 fact card 内同时有结构化字段路径行和机器标识符行时，结构化字段路径行分值更高。

真实 API 路径多了三层单测未覆盖的条件：

1. fused hits 中 source chunk 排在 fact card 前面，source chunk 内存在 slash-like 机器标识符行。
2. 文件名 / 配置标识让问题进入 exact path conclusion，而不是普通单 fact card fallback。
3. `selectQuestionFocusedFallbackSnippets(..., limit=3)` 进入 coverage-aware 分支；该分支按证据形态补位，`path` 形态仍只认 slash/URL，不认 dotted structured field path value。

因此，单元测试证明“打分函数能偏向结构化字段路径”，但没有证明“真实多命中 exact path fallback 会把结构化字段路径作为最终结论”。

## 最小安全修复点

只建议一个修复点：

- 类：`AnswerGenerationFallbackSnippetSelectionSupport`
- 方法：`addBestCandidateForRequiredShape`
- 修复方式：当 `shape` 为 `path` 时，先在 `rankedCandidates` 中选择 `looksLikeQuestionFocusedStructuredPathValueCandidate(question, candidate, extractQueryTokens(question))` 命中的候选；没有命中时，再走现有 `matchesStructuredEvidenceShape(candidate, "path")` 逻辑。

为什么只修这一处：

- 这是最终错误进入结论前的最窄 gate：coverage-aware snippet 的 path shape 补位。
- 不需要改 retrieval、fact card 生成、LLM prompt、citation、redline 或 fallback outcome。
- 不需要扩大 `buildExactPathConclusionLines` 的全文扫描，也不需要改通用 `containsPathSignal`，避免影响普通 URL / endpoint / HTTP path 题。
- 现有 `scoreQuestionFocusedFallbackLine` 已经对 question-focused structured path value 给出通用高分；补上 shape gate 后，已有排序即可发挥作用。

## 为什么不是硬编码

建议修复只使用通用信号：

- 候选行是否是结构化路径元数据字段；
- 候选值是否是 dotted field path 或 assignment-like mapping；
- 候选是否覆盖问题中的通用焦点 token；
- 当前补位形态是否为 `path`。

不建议、也不允许在生产代码中写入任何具体文件名、字段名、端口值、题面文本、答案片段、业务域词或验收样例分支。

## 影响面评估

可能正向影响：

- YAML / JSON / properties 等结构化配置查值题。
- 同一结构化文档中存在多个 sibling 字段，且问题需要依靠字段路径区分上下文的查值题。
- source chunk 中存在 URL、镜像、版本号等机器标识符噪声，但 fact card 中已有更精确结构化字段路径的场景。

需要重点防护：

- 真正的 URL / endpoint / HTTP path 问题不能被无关 field path 抢走。
- 询问机器标识符、镜像、版本、artifact id 的问题仍应能返回机器标识符。
- 普通数值题、状态题、枚举题不能因为存在 dotted path 元数据而额外扩展答案。
- path contract / API contract 类问题仍应保留现有 slash path 与强约束候选。

## 建议 agentA 下一轮允许修改范围

- 生产代码：仅允许修改 `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- 测试代码：仅允许修改或新增 `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`
- 允许运行：
  - `bash scripts/scan-redline.sh special_cases_report.md`
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests test`
  - 修复后按需运行全量 `mvn test`

## 建议 agentA 下一轮禁止修改范围

- 禁止修改 `src/main/resources/**`
- 禁止修改 `scripts/**`
- 禁止修改 `AGENTS.md`、`CLAUDE.md`、redline 脚本或 allowlist
- 禁止修改 retrieval / RRF / rerank / citation / prompt / LLM binding / fact card 生成链路
- 禁止修改测试题集、验收题答案、eval hidden/public 数据
- 禁止清库、重导、重建向量
- 禁止加入任何面向 Q6 文件名、字段名、端口值、题面、答案片段的规则或模板

## 必须新增或保留的保护测试

1. 新增真实链路形态单测：同一问题下 `SOURCE` 命中在前、`FACT_CARD` 命中在后；source 含 slash-like 机器标识符行，fact card 含 question-focused structured field path value；断言最终 fallback 结论选择结构化字段路径取值，而不是 source 机器标识符。
2. 新增 path shape 单元保护：`selectQuestionFocusedFallbackSnippets(..., limit > 1)` 在 path shape 补位时优先选择 question-focused structured path value。
3. 保留 agentA 首轮测试：单 FACT_CARD 内 structured path value 不被机器标识符行压过。
4. 新增 URL / endpoint 回归：真实 URL 或 slash path 问题仍返回 URL / endpoint，不被无关 dotted field path 抢占。
5. 新增机器标识符回归：当问题焦点本身询问镜像、artifact、版本或其他机器标识符时，仍允许机器标识符作为答案。
6. 新增普通数值回归：不含 structured path metadata 的 `key: number` / `key = number` 仍保持原有 fallback 行为。

## 端到端验证建议

修复后由 agentD 独立验证：

1. 运行 redline，要求 `BLOCKER=0`。
2. 运行 `AnswerGenerationServiceTests`。
3. 运行全量 `mvn test`。
4. 在完整知识库验收资料已发布的环境中重新调用 Q6 API。
5. 验证最终答案直接回答目标数值，且 citation 支撑的是回答问题的字段路径或同源原文，而不是同一文档中的无关机器标识符行。
6. 报告中分别记录 Answer Accuracy、Citation Accuracy、Recall@5 / Recall@10、Hallucination Count；citation coverage 不单独作为通过依据。

## 最终结论

ROOT_CAUSE_FOUND

唯一最小修复点：`AnswerGenerationFallbackSnippetSelectionSupport.addBestCandidateForRequiredShape` 的 `path` shape 补位逻辑。
