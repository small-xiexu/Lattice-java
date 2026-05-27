# Q6 fallback runtime trace analysis report

## 执行信息

- 执行时间：2026-05-26 23:36:46 +0800
- 本轮角色：agentD，验证/测试 Agent
- 最终结论：`RUNTIME_GATE_FOUND`
- 只读边界：未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、redline 脚本、allowlist、`AGENTS.md`、`CLAUDE.md`、模型配置、题集或验收答案；未清库、未重导资料、未重建向量；未提交 commit。
- 允许写入：本报告；因 gate 与下一步计划变化，同步更新 `docs/quality-progress-and-lessons.md`。

## 已读取文件

- `AGENTS.md`
- `docs/quality-progress-and-lessons.md`
- `docs/test/knowledge-base-e2e/q6_fallback_second_root_cause_analysis_report.md`
- `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_fix_result_report.md`
- `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_verification_report.md`
- `.claude/t1.md`

摘要：

- 历史报告确认 Q6 的资料、source chunk、path-aware fact card 与 retrieval fused rank 均已到位。
- agentA 前两轮修复均集中在 fact card / structured path value 进入 snippet 后的选择逻辑。
- 上轮真实 API 仍走 `generationMode=FALLBACK`，最终回答同一 YAML 里的 `image: registry.k8s.io/goproxy:0.1`。
- `.claude/t1.md` 复核的模型基线为 local OpenAI-compatible `http://127.0.0.1:8888` + `gpt-5.5`，embedding 为 `zhipu_embedding` / `embedding-3` / `2000` 维。API key 已读取但未输出。

## Redline 与测试

- redline 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- redline 结果：`BLOCKER=0 / REVIEW=1998 / ALLOWLIST=259`
- 定向测试命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests test`
- 定向测试结果：通过，`Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`

## 服务与依赖

- 源码时间：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java` = `2026-05-26 21:25:12 +0800`
- class 时间：`target/classes/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.class` = `2026-05-26 23:02:34 +0800`
- 判断：class 晚于源码修复时间，且本轮 Maven 定向测试重新编译过。
- `18082` 启动前未监听；按标准入口 `./scripts/run-local-dev.sh` 启动。
- 服务 PID：`66763`
- 服务启动时间：`Tue May 26 23:03:37 2026`
- 命令行包含 `target/classes` 与 `--spring.profiles.active=local-dev`
- health：`{"status":"UP"}`
- 验证后处理：本轮启动的本地服务已停止，未保留 `18082` 长期监听进程。
- PostgreSQL：复用既有 `vector_db`，状态 `running healthy`
- Redis：复用既有 `redis`，`redis-cli PING` 返回 `PONG`
- 未启动新 PostgreSQL / Redis 容器。

## 模型配置摘要

- LLM connections：
  - `local_openai_compatible`：`openai_compatible`，`http://127.0.0.1:8888`，enabled，API key 仅显示 mask。
  - `zhipu_embedding`：`openai_compatible`，`https://open.bigmodel.cn/api/paas/v4`，enabled，API key 仅显示 mask。
- LLM models：`gpt-5.5` enabled；`embedding-3` enabled。
- Query bindings：`query/answer`、`query/reviewer`、`query/rewrite` 均 enabled。
- Vector status：`configuredModelName=embedding-3`，`schemaDimensions=2000`，`dimensionsConsistent=true`，`articleCount=6`，`indexedArticleCount=6`。
- 未切换模型，未做模型 A/B。

## 当前库复用状态

核心表数量：

| 表 | 数量 |
|---|---:|
| `source_files` | 6 |
| `source_file_chunks` | 6 |
| `articles` | 6 |
| `article_chunks` | 13 |
| `fact_cards` | 11 |
| `article_vector_index` | 6 |
| `article_chunk_vector_index` | 13 |
| `fact_card_vector_index` | 0 |

6 个验收文件仍完整存在：

- `01_markdown/probe-and-incident-operations.md`
- `02_structured/grpc-liveness.yaml`
- `02_structured/http-liveness.yaml`
- `02_structured/tcp-liveness-readiness.yaml`
- `03_pdf/incident-response-reference-lite.pdf`
- `04_office/incident-response-checklists-lite.xlsx`

目标证据：

- 目标 source chunk：`source_files.id=4`，`source_file_chunks.id=4`，`chunk_index=0`，包含 `readinessProbe`、`tcpSocket`、`port: 8080`，也包含 `image: registry.k8s.io/goproxy:0.1`。
- fact card 9：`review_status=valid`，包含 `fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080` 与 `port: 8080`。
- fact card 9 同卡噪声：包含 `fieldPath: spec.containers[0].image = registry.k8s.io/goproxy:0.1` 与 `image: registry.k8s.io/goproxy:0.1`。

## Q6 API 响应摘要

请求：

```bash
curl -sS -X POST http://127.0.0.1:18082/api/v1/query \
  -H 'Content-Type: application/json' \
  --data '{"question":"tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？"}'
```

响应摘要：

- `queryId=99d7a2c4-e5dd-4b59-8817-59908216f235`
- `answerOutcome=SUCCESS`
- `generationMode=FALLBACK`
- `modelExecutionStatus=DEGRADED`
- `reviewStatus=PASSED`
- `fallbackReason=DETERMINISTIC_EXACT_LOOKUP_PREFERRED`
- `structuredEvidence=null`
- `citationCheck=verifiedCount=1 / demotedCount=0 / skippedCount=0 / claimCount=1 / unsupportedClaimCount=0 / coverageRate=1.0`

最终答案核心 claim：

```text
当前可确认的信息是：image: registry.k8s.io/goproxy:0.1
```

判断：真实 API 仍未回答 readiness probe 端口事实 `8080`，仍把同一 YAML 的 image 行当成核心答案。

## Retrieval Audit

- `runId=3`
- `strategyTag=intent=CONFIGURATION|shape=GENERAL|mode=parallel|rewrite=off|graph=off|vector=off`
- `questionTypeTag=CONFIGURATION`
- `answerShape=GENERAL`
- `retrievalMode=parallel`
- `rewriteApplied=false`
- `fusedHitCount=5`
- `channelCount=7`
- `factCardHitCount=11`
- `sourceChunkHitCount=5`
- `coverageStatus=not_applicable`

进入 fused 的前 5：

| fusedRank | channel | evidenceType | title | factCardId | 说明 |
|---:|---|---|---|---:|---|
| 1 | `source_chunk_fts` | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | | 目标 source chunk |
| 2 | `fact_card_fts` | `FACT_CARD` | `结构化键值条目 - 02_structured/tcp-liveness-readiness.yaml#0` | 9 | 目标 path-aware fact card |
| 3 | `fact_card_fts` | `FACT_CARD` | `结构化列表条目 - 02_structured/tcp-liveness-readiness.yaml#0` | 8 | low confidence |
| 4 | `source` | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | | 目标源文件全文 |
| 5 | `article_chunk_fts` / `refkey` | `ARTICLE` | `tcp liveness readiness` | | article chunk / refkey |

结论：检索层没有丢正确证据；fact card 9 已召回到 fused rank 2。

## Answer / Citation Audit

- `audit_id=3`
- `answerOutcome=SUCCESS`
- `generationMode=FALLBACK`
- `reviewStatus=PASSED`
- `citation_coverage=1.0000`
- `unsupported_claim_count=0`
- `verified_citation_count=1`
- `demoted_citation_count=0`
- `skipped_citation_count=0`
- `route_type=query`
- claim：`当前可确认的信息是：image: registry.k8s.io/goproxy:0.1`
- claim status：`VERIFIED`
- citation literal：`[→ 02_structured/tcp-liveness-readiness.yaml]`
- source type：`SOURCE_FILE`
- target key：`02_structured/tcp-liveness-readiness.yaml`
- matched excerpt：`apiVersion: v1`
- reason：`source_direct_line_match_verified`

Citation 判断：

- `source_direct_line_match_verified` 的 verified 来自 source file 中存在与 claim 直接匹配的 image 行。
- `matchedExcerpt=apiVersion: v1` 是 `extractMatchedExcerpt` 按 hard fact token 扫描时返回的第一条命中行，不是端口事实支撑。
- 因此 citation audit 只是证明错误 claim 在同源文件中可校验，不证明 readiness probe 端口事实被支撑。

## 只读运行态复盘方法

- 使用当前 `target/classes` 与 Maven dependency classpath，在 JShell 中通过反射调用现有 package-private 方法。
- 输入命中列表按 retrieval audit fused 前 5 构造，content 从当前数据库只读查询得到：
  - source chunk id 4
  - fact card 9
  - fact card 8
  - source file id 4
  - article chunk 0
- 调用的方法包括：
  - `selectFallbackEvidenceHits`
  - `filterFallbackEvidenceHits`
  - `sortFallbackEvidenceHits`
  - `deduplicateSortedFallbackEvidenceHits`
  - `selectComplementaryEvidenceByQuestionTokens`
  - `retainDirectStructuredEvidence`
  - `selectQuestionFocusedFallbackSnippets`
  - `selectExactPathCandidateLines`
  - `buildExactStructuredListConclusionLines`
  - `buildAggregatedEvidenceConclusionLines`
  - `buildExactPathConclusionLines`
  - `buildEvidenceConclusionLines`
  - `buildEvidenceMarkdown`
- 临时文件：`/tmp/lattice-q6-cp.txt` 与 `/tmp/q6_hit_contents.tsv`，本轮结束前已删除。

## Runtime 复盘结果

### 1. `fallbackHits` 最终列表

`selectFallbackEvidenceHits` 最终返回 2 条：

| order | evidenceType | title | articleKey | question-focused score |
|---:|---|---|---|---:|
| 1 | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | `02_structured/tcp-liveness-readiness.yaml` | 87 |
| 2 | `ARTICLE` | `tcp liveness readiness` | `default-source--02-structured-tcp-liveness-readiness` | 159 |

fact card 9 没有进入最终 `fallbackHits`。它在中间候选中存在，且分值高：

| 阶段 | fact card 9 状态 |
|---|---|
| retrieval fused | rank 2，存在 |
| `filterFallbackEvidenceHits(..., false)` | 存在 |
| `sortFallbackEvidenceHits(..., false)` | rank 2，score 155，仅低于 article score 159 |
| `retainDirectStructuredEvidence` | 若执行 allRelevant 路径，会保留 fact card 9 |
| 最终 `fallbackHits` | 不存在 |

关键原因：`selectComplementaryEvidenceByQuestionTokens` 在 `selectFallbackEvidenceHits` 早期返回。它先加入 `firstSourceHit`，再按 high-signal token 从排序候选里取首个命中；因为 article 排在 fact card 9 前面且也包含 `tcp-liveness-readiness` / `readiness` / `probe` 等 token，所以返回 `SOURCE + ARTICLE`。该早退发生在 allRelevant / retainDirectStructuredEvidence 路径之前。

### 2. `buildEvidenceConclusionLines` 实际分支

实际分支：

- `exactStructuredListLines=[]`
- `aggregatedConclusionLines=[]`
- 第二次 `buildExactPathConclusionLines` 触发并返回：
  - `当前可确认的信息是：image: registry.k8s.io/goproxy:0.1 [→ 02_structured/tcp-liveness-readiness.yaml]`
- setup checklist 未触发。
- 普通 `primarySnippets` 未触发。

对应源码位置：

- `AnswerFallbackConclusionBuilder` 先尝试 exact structured list、aggregation，再调用第二次 exact path conclusion。
- `AnswerGenerationFallbackConclusionSupport.buildExactPathConclusionLines` 只在最终 `fallbackHits` 内找 path/path-contract/structured-path snippet。

### 3. fact card 9 端口事实状态

真实链路状态：fact card 9 已进入 retrieval 与中间排序候选，但被 `selectComplementaryEvidenceByQuestionTokens` 的 early return 排除，未进入最终 `fallbackHits`，所以 `buildExactPathConclusionLines` 没消费它。

隔离复盘 fact card 9：

- `selectQuestionFocusedFallbackSnippets(..., factCard9, limit=3)` 返回：
  - `fieldPath: spec.containers[0].readinessProbe.periodSeconds = 10`
  - `fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`
  - `fieldPath: spec.containers[0].readinessProbe.initialDelaySeconds = 15`
- 端口事实进入了 fact-card 自身 snippet 候选，但不是第 1 条。
- 三条 readinessProbe sibling 的分值均为 155；当前逻辑能识别 readinessProbe 结构路径，但没有把中文问题中的“端口”稳定映射到 `port` 末级字段。

这一点不是本轮真实 API 输出 `image` 的直接 gate，但说明下一轮修复后必须用端到端测试保护 sibling 字段选择，避免从 `image` 变成 `periodSeconds`。

### 4. image 行成为唯一 claim 的原因

最终 `fallbackHits` 的 primary hit 是源文件全文。对该 hit：

- `selectQuestionFocusedFallbackSnippets(..., SOURCE, limit=3)` 返回：
  - `image: registry.k8s.io/goproxy:0.1`，score 63，`pathSignal=true`
  - `port: 8080`，score 23，`pathSignal=false`
  - `apiVersion: v1`，score 23，`pathSignal=false`
- `buildExactPathConclusionLines` 对 path question 过滤候选时要求 `containsPathSignal` / path contract / structured path snippet。
- source 里的 `image: registry.k8s.io/goproxy:0.1` 因 `/goproxy` 被 `containsPathSignal` 识别为 path-like。
- source 里的 `port: 8080` 没有 path signal，也不是 structured path snippet，被 exact path 分支过滤。
- article 中正确句 `readinessProbe.tcpSocket.port = 8080，Readiness 探针通过 TCP Socket 检查 8080 端口。` score 87，但 `pathSignal=false` 且 `structuredPathSnippet=false`，也没有成为 exact path primary match。

因此 image 行成为唯一 claim，不是因为 retrieval rank 低，也不是因为 citation 后处理替换，而是因为最终 `fallbackHits` 丢掉 fact card 9 后，exact path 分支只剩 source/article 可消费；source image 行是唯一满足 pathSignal 的高可用候选。

## 关键 Gate 结论

| Gate | 结论 |
|---|---|
| `selectFallbackEvidenceHits` 是否包含 fact card 9 | 否。最终只有 source file 与 article。 |
| `retainDirectStructuredEvidence` 是否丢掉 fact card 9 | 否。若走 allRelevant 路径，fact card 9 会保留；真实链路在此之前被 complementary early return 截走。 |
| `selectComplementaryEvidenceByQuestionTokens` 是否提前返回 source-only/source-dominant hit | 是。它返回 `SOURCE + ARTICLE`，fact card 9 未进入最终 fallbackHits。 |
| `buildExactStructuredListConclusionLines` 是否触发 | 否，返回空。 |
| `buildAggregatedEvidenceConclusionLines` 是否触发 | 否，返回空。 |
| `buildExactPathConclusionLines` 是否触发 | 是，第二次 exact path 分支触发并输出 image 行。 |
| 普通 `primarySnippets` 是否触发 | 否。exact path 分支已提前返回。 |
| `fieldPath ... port = 8080` 是否进入 `scoredFactCandidates` | 在 fact card 9 隔离复盘中进入，并作为第 2 条 snippet；真实最终分支没有消费 fact card 9。 |
| `image` 行是否进入 exact identifier/path candidate | 是。source 中 image 行因 `/goproxy` 命中 `containsPathSignal`，进入 exact path primary matches，并以 score 63 胜过同 source 的 `port: 8080`。 |

## 为什么前两轮修复单测通过但真实 API 不变

前两轮修复都在 fact card / structured path value 已进入 snippet 选择后才发挥作用：

- 首轮修复提高 structured path value candidate 的问题贴合分。
- 二轮修复让 path shape 补位优先消费 question-focused structured path value candidate。

真实 API 中，fact card 9 在 `selectFallbackEvidenceHits` 阶段被 `selectComplementaryEvidenceByQuestionTokens` early return 排除，没有进入最终 `fallbackHits`。后续 snippet shape gate 和 exact path structured path value gate 都没有机会看到 fact card 9。因此单元测试能证明“fact card 进入 snippet 后选择更好”，但不能改变真实 API 的最终 source/article-only fallback markdown。

## 唯一失败 Gate

唯一失败 gate：`AnswerFallbackEvidenceSelector.selectComplementaryEvidenceByQuestionTokens` 对单源结构化查值题过早返回 `SOURCE + ARTICLE`，屏蔽了 fused rank 2 的高分 path-aware fact card 9。

这不是资料、编译、fact card、chunk、retrieval、rerank、class 加载或 citation 问题；正确证据已召回，并在中间排序候选中分值很高，但没有进入最终 fallback markdown 的证据集合。

## 下一轮最小修复点

只建议一个修复点：

- 类：`AnswerFallbackEvidenceSelector`
- 方法：`selectComplementaryEvidenceByQuestionTokens`
- 修复方向：当问题是结构化查值 / exact lookup，且排序候选中存在高分 `FACT_CARD` 或 question-focused structured path value 证据时，complementary 选择不能只返回 `firstSourceHit + first token hit`；应把最佳结构化事实候选纳入最终 `fallbackHits`，或避免在这种场景早退，让后续 allRelevant / retainDirectStructuredEvidence 路径继续执行。

为什么不是硬编码：

- 依据是通用证据类型、问题形态、question-focused score、structured path value / fact candidate 信号，不依赖 Q6 文件名、端口值、字段名、题面或答案片段。
- 目标是让已召回的结构化事实证据进入 deterministic fallback，而不是教系统回答某个样例。

影响面：

- 正向影响：结构化 YAML/JSON/properties 等查值题；同源 source/article 与 fact card 同时命中时的 fallback grounding。
- 风险：多主题问题原本依赖 complementary 保留 source/article 旁证；修复需避免破坏真正的多主题互补证据选择。

建议保护测试：

- 构造 source、fact card、article 同时命中，article 分数略高于 fact card，但 fact card 含 question-focused structured path value；断言最终 `fallbackHits` 包含 fact card。
- 构造多主题问题，仍能保留各主题互补 source/article，不被单个 fact card 抢占。
- 构造 Q6 形态保护：最终不得把 slash-like machine identifier 当作核心答案。
- 同步覆盖 sibling 字段选择：当 fact card 中同一 parent path 下有 `port`、`periodSeconds`、`initialDelaySeconds`，问题问端口时应选端口事实，避免后续从 `image` 错误变成 sibling 字段错误。
