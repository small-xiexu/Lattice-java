# Q6 fallback path shape gate 独立验证报告

## 执行信息

- 执行时间：2026-05-26 22:44:06 +0800
- 本轮角色：agentD，验证/测试 Agent
- 最终结论：`FAIL`
- 只读边界：未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、redline 脚本、allowlist、`AGENTS.md`、`CLAUDE.md`、模型配置、题集或验收答案。
- 允许写入：本验证报告；因结论改变当前 Q6 阻塞状态，同步更新 `docs/quality-progress-and-lessons.md`。
- 数据边界：未清库、未重导资料、未重建向量；复用当前 Q6 clean 库。

## 已读取文件

1. `AGENTS.md`
2. `docs/quality-progress-and-lessons.md`
3. `docs/test/knowledge-base-e2e/q6_end_to_end_verification_report.md`
4. `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_fix_result_report.md`
5. `docs/test/knowledge-base-e2e/q6_fallback_structured_evidence_verification_report.md`
6. `docs/test/knowledge-base-e2e/q6_fallback_second_root_cause_analysis_report.md`
7. `docs/test/knowledge-base-e2e/q6_fallback_path_shape_gate_fix_result_report.md`
8. `.claude/t1.md`

摘要：

- 前序端到端报告确认 Q6 资料、目标 source chunk、目标 path-aware fact card、检索召回均已到位，失败点在 Answer deterministic fallback。
- 二次根因报告判断最小修复点是 `AnswerGenerationFallbackSnippetSelectionSupport.addBestCandidateForRequiredShape` 的 `path` shape 补位逻辑。
- agentA fix report 显示已按最小范围实现 path shape gate 修复，并通过 redline、定向测试与全量测试。
- `.claude/t1.md` 确认本轮模型基线：Chat provider 为 local OpenAI-compatible，Chat base_url 为 `http://127.0.0.1:8888`，Chat model 为 `gpt-5.5`；Embedding provider 为 `zhipu_embedding`，Embedding model 为 `embedding-3`，Embedding dimensions 为 `2000`。API key 已读取但全程未输出。

## Redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0 / REVIEW=1998 / ALLOWLIST=259`
- 结论：未触发 redline blocker，允许继续验证。

## Maven 测试

- `AnswerGenerationServiceTests`
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests test`
  - 结果：通过，`Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`
- `FactCardGenerationServiceTests`
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`
  - 结果：通过，`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
- 全量测试
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：通过，`Tests run: 906, Failures: 0, Errors: 0, Skipped: 0`
  - 说明：全量测试日志中出现编译超时、临时目录缺失、模拟 embedding 失败等 ERROR/WARN，均为对应异常路径测试的预期日志；最终 Surefire 汇总为全绿。

## 服务与 class 加载判断

- 源码文件时间：`2026-05-26 21:25:12 +0800`
  - `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- class 文件时间：`2026-05-26 22:33:01 +0800`
  - `target/classes/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.class`
- 判断：class 时间晚于源码修复时间，满足加载最新修复 class 的前置要求。
- 启动前 `18082`：未监听。
- 启动方式：`./scripts/run-local-dev.sh`
- 服务进程：
  - PID：`45241`
  - 启动时间：`Tue May 26 22:40:07 2026`
  - 命令行包含 `target/classes` 与 `--spring.profiles.active=local-dev`
- 启动后健康检查：`curl -sS http://127.0.0.1:18082/actuator/health` 返回 `{"status":"UP"}`。
- 结论：服务启动时间晚于 class 编译时间，真实 API 已加载本轮修复后的 class。

## 依赖与模型配置

- PostgreSQL：复用既有 Docker 容器 `vector_db`，状态 `healthy true`。
- Redis：复用既有 Docker 容器 `redis`，容器 running，无 healthcheck；`redis-cli PING` 返回 `PONG`。
- 未启动新 PostgreSQL / Redis 容器。
- 运行态模型配置只读复核：
  - Chat provider：`openai_compatible`
  - Chat base_url：`http://127.0.0.1:8888`
  - Chat model：`gpt-5.5`
  - Query bindings：`answer/reviewer/rewrite` 均绑定 `gpt-5.5`
  - Embedding provider：`openai_compatible` connection code 为 `zhipu_embedding`
  - Embedding base_url：`https://open.bigmodel.cn/api/paas/v4`
  - Embedding model：`embedding-3`
  - Embedding dimensions：`2000`
  - `/api/v1/admin/vector/status`：`vectorEnabled=true`，`configuredModelName=embedding-3`，`schemaDimensions=2000`，`dimensionsConsistent=true`，`articleCount=6`，`indexedArticleCount=6`
- API key：已脱敏，报告不输出明文。

## 当前库复用检查

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

资料完整性：以下 6 个验收文件均仍在 `source_files` 中：

- `01_markdown/probe-and-incident-operations.md`
- `02_structured/grpc-liveness.yaml`
- `02_structured/http-liveness.yaml`
- `02_structured/tcp-liveness-readiness.yaml`
- `03_pdf/incident-response-reference-lite.pdf`
- `04_office/incident-response-checklists-lite.xlsx`

发布状态：

- `articles`：`ACTIVE/passed=6`
- `compile_article_review_queue`：2 条，均为 `review_status=published`，分别已发布到 `default-source--02-structured-grpc-liveness` 与 `default-source--02-structured-http-liveness`

结论：当前 Q6 clean 库可复用；未触发清库、重导或向量重建。

## 目标证据只读核验

目标 source chunk：

- `source_files.id=4`
- `relative_path=02_structured/tcp-liveness-readiness.yaml`
- `source_file_chunks.id=4`
- `chunk_index=0`
- chunk 内容包含：
  - `readinessProbe:`
  - `tcpSocket:`
  - `port: 8080`

目标 fact card：

| factCardId | cardId | reviewStatus | has readiness port | has image noise |
|---:|---|---|---|---|
| 8 | `fact-card:4:0:fact_enum:c740723c633ebfaf` | `low_confidence` | 否 | 否 |
| 9 | `fact-card:4:0:fact_enum:6d7a24677543bd90` | `valid` | 是 | 是 |

fact card 9 的结构化字段路径事实仍存在：

- `fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`
- `port: 8080`

同卡内仍存在机器标识符噪声：

- `fieldPath: spec.containers[0].image = registry.k8s.io/goproxy:0.1`
- `image: registry.k8s.io/goproxy:0.1`

## Q6 真实 API 验证

请求：

```bash
curl -sS -X POST http://127.0.0.1:18082/api/v1/query \
  -H 'Content-Type: application/json' \
  --data '{"question":"tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？"}'
```

响应摘要：

- `queryId`：`07b865e1-87d7-4217-8770-bbf7feb62437`
- `answerOutcome`：`SUCCESS`
- `generationMode`：`FALLBACK`
- `modelExecutionStatus`：`DEGRADED`
- `reviewStatus`：`PASSED`
- `fallbackReason`：`DETERMINISTIC_EXACT_LOOKUP_PREFERRED`
- `structuredEvidence`：`null`
- `answerMarkdown`：API 响应中该投影字段为 `null`；完整答案位于 `answer` 字段。

最终答案核心内容：

```text
当前可确认的信息是：image: registry.k8s.io/goproxy:0.1
```

判断：

- 未直接回答 readiness probe 使用的端口事实 `8080`。
- 仍把同一 YAML 中的无关机器标识符行 `image: registry.k8s.io/goproxy:0.1` 当作核心答案。
- 因此 Q6 未通过端到端验收。

## Citation / Claim 判断

`citationCheck`：

- `verifiedCount=1`
- `demotedCount=0`
- `skippedCount=0`
- `claimCount=1`
- `unsupportedClaimCount=0`
- `coverageRate=1.0`

answer/citation audit：

- `audit_id=2`
- `answerOutcome=SUCCESS`
- `generationMode=FALLBACK`
- `reviewStatus=PASSED`
- `citation_coverage=1.0000`
- claim：`当前可确认的信息是：image: registry.k8s.io/goproxy:0.1`
- claim status：`VERIFIED`
- citation literal：`[→ 02_structured/tcp-liveness-readiness.yaml]`
- source type：`SOURCE_FILE`
- target key：`02_structured/tcp-liveness-readiness.yaml`
- validation status：`VERIFIED`
- matched excerpt：`apiVersion: v1`
- reason：`source_direct_line_match_verified`

结论：citation coverage 只支撑了错误 claim 存在于同源文件中，且 matched excerpt 甚至不是端口事实；它没有支撑用户所问的 readiness probe 端口。不能把 coverage 作为通过依据。

## Retrieval Audit 摘要

- `runId=2`
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

前 5 个 fused hit：

| fusedRank | channel | evidenceType | title | factCardId | sourcePaths |
|---:|---|---|---|---:|---|
| 1 | `source_chunk_fts` | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | | `02_structured/tcp-liveness-readiness.yaml` |
| 2 | `fact_card_fts` | `FACT_CARD` | `结构化键值条目 - 02_structured/tcp-liveness-readiness.yaml#0` | 9 | `02_structured/tcp-liveness-readiness.yaml` |
| 3 | `fact_card_fts` | `FACT_CARD` | `结构化列表条目 - 02_structured/tcp-liveness-readiness.yaml#0` | 8 | `02_structured/tcp-liveness-readiness.yaml` |
| 4 | `source` | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | | `02_structured/tcp-liveness-readiness.yaml` |
| 5 | `article_chunk_fts` / `refkey` | `ARTICLE` | `tcp liveness readiness` | | `02_structured/tcp-liveness-readiness.yaml` |

召回判断：

- 目标 source chunk 已召回：是，fused rank 1。
- 目标 path-aware fact card 已召回：是，fact card 9，fused rank 2。
- 检索层没有退化；失败仍发生在 fallback 最终答案选择 / grounding。

## 最终结论

结论：`FAIL`

Q6 不能从“fallback 修复未端到端闭环”推进为“端到端通过”。本轮 path shape gate 修复后的真实 API 仍输出同一 YAML 中的无关机器标识符行，未回答 readiness probe 端口事实。

唯一失败类型：`证据已召回但回答漏点`

更具体地说：目标 source chunk 与 path-aware fact card 均已进入 fused 前 2，但 Answer deterministic fallback 最终仍选择 `image: registry.k8s.io/goproxy:0.1` 作为核心结论，citation 也只验证了该错误 claim 的同源存在性，未支撑端口事实。

下一步建议：

由 agentB 先做只读三次根因分析，限定在 Answer deterministic fallback 的最终候选消费 / exact path conclusion / conclusion line assembly 链路，确认 path shape gate 修复为何没有改变真实 API 输出；随后再交给 agentA 做一个最小通用修复。下一轮仍禁止为 Q6 文件名、字段名、端口值、题面或答案片段写任何特判。
