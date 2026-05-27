# Q6 fallback structured evidence 独立验证报告

## 执行信息

- 执行时间：2026-05-26 18:11:00 +0800
- 本轮角色：agentD，验证/测试 Agent
- 边界：未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、redline 脚本、allowlist、`AGENTS.md`、`CLAUDE.md`
- 允许写入：本报告；因 Q6 gate 结论变化，需同步更新 `docs/quality-progress-and-lessons.md`
- 额外数据操作：按用户追加要求，查看并确认 `compile_article_review_queue` 中的 2 条 `needs_human_review` 草稿，使完整验收资料全部发布为 `passed` article

## Redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0 / REVIEW=1996 / ALLOWLIST=259`
- 结论：redline 未阻塞后续验证。

## Maven 测试

- `AnswerGenerationServiceTests`：`67/0/0`，通过。
- `FactCardGenerationServiceTests`：`21/0/0`，通过。
- 全量 `mvn test`：`902/0/0`，通过。

## 运行时配置

- Chat provider：local OpenAI-compatible
- Chat base_url：`http://127.0.0.1:8888`
- Chat model：`gpt-5.5`
- Embedding provider：`zhipu_embedding`
- Embedding model：`embedding-3`
- Embedding dimensions：`2000`
- 配置数量：connections `2`，models `2`，bindings `10`
- 连通性：chat connection/model 测试成功；embedding connection/model 测试成功，embedding 返回 `2000` 维向量
- API key：已按 `.claude/t1.md` 读取使用，报告不输出明文。

## 环境与清库

- PostgreSQL：复用既有 Docker 容器 `vector_db`，`0.0.0.0:5432->5432`，容器 healthy。
- Redis：复用既有 Docker 容器 `redis`，`0.0.0.0:6379->6379`，`PONG`。
- 应用健康：`/actuator/health` 为 `UP`。
- 清库命令：`./scripts/reset-lattice-schema.sh`
- 清库结果：`ai-rag-knowledge.lattice` 已按 `src/main/resources/db/schema.sql` 重建。

## 完整资料导入

- 导入目录：`docs/test/knowledge-base-e2e/sources`
- Compile job：`044267c6-d71f-4c7f-b9f4-be3369bd665c`
- 请求参数：`orchestrationMode=state_graph`，`reviewMode=LLM`，`incremental=false`
- job 结果：`SUCCEEDED`
- 初始 reviewSummary：`acceptedCount=4 / needsHumanReviewCount=2 / pendingReviewCount=0`
- 人工确认处理：确认并发布队列项 `1`、`2`，对应 `grpc-liveness.yaml`、`http-liveness.yaml`
- 确认后队列：`needs_human_review=0`，`published=2`

## 导入文件清单

- `01_markdown/probe-and-incident-operations.md`
- `02_structured/grpc-liveness.yaml`
- `02_structured/http-liveness.yaml`
- `02_structured/tcp-liveness-readiness.yaml`
- `03_pdf/incident-response-reference-lite.pdf`
- `04_office/incident-response-checklists-lite.xlsx`

## 核心表数量

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

向量状态：`vectorEnabled=true`，`schemaDimensions=2000`，`dimensionsConsistent=true`，`indexedArticleCount=6`。

## 目标证据核验

- `source_file_chunks` 中目标 YAML 存在完整 chunk，包含：
  - `readinessProbe:`
  - `tcpSocket:`
  - `port: 8080`
- `fact_cards` 中目标 path-aware key-value card 存在，`review_status=valid`。
- 该 fact card 的结构化字段包含：
  - `spec.containers[0].readinessProbe.tcpSocket.port = 8080`
  - 同卡内也包含无关字段 `spec.containers[0].image = registry.k8s.io/goproxy:0.1`

## Q6 API 请求与响应摘要

- 请求：`tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？`
- Query ID：`cb72513d-346c-428c-b5ff-341686c0ae8b`
- 最终答案：未回答 `8080`，仍输出 `image: registry.k8s.io/goproxy:0.1`
- `answerOutcome`：`SUCCESS`
- `generationMode`：`FALLBACK`
- `modelExecutionStatus`：`DEGRADED`
- `reviewStatus`：`PASSED`
- `fallbackReason`：`DETERMINISTIC_EXACT_LOOKUP_PREFERRED`
- `structuredEvidence`：`null`

## Citation Coverage

- `verifiedCount=1`
- `demotedCount=0`
- `skippedCount=0`
- `claimCount=1`
- `unsupportedClaimCount=0`
- `coverageRate=1.0`

说明：citation 校验通过的是错误 claim：`当前可确认的信息是：image: registry.k8s.io/goproxy:0.1`。因此 citation coverage 表面为 1.0，但没有支撑用户问题所需的 readiness probe 端口答案。

## Retrieval Audit 摘要

- `runId=1`
- `strategyTag=intent=CONFIGURATION|shape=GENERAL|mode=parallel|rewrite=off|graph=off|vector=off`
- `fusedHitCount=5`
- `factCardHitCount=11`
- `sourceChunkHitCount=5`
- 命中情况：
  - fused rank 1：`source_chunk_fts`，`02_structured/tcp-liveness-readiness.yaml`
  - fused rank 2：`fact_card_fts`，`结构化键值条目 - 02_structured/tcp-liveness-readiness.yaml#0`
  - fused rank 3：`fact_card_fts`，`结构化列表条目 - 02_structured/tcp-liveness-readiness.yaml#0`
  - fused rank 4：`source`，`02_structured/tcp-liveness-readiness.yaml`
  - fused rank 5：article/refkey/chunk 均指向 `tcp liveness readiness`

结论：资料、source chunk、path-aware fact card、检索召回均已到位；失败不在资料缺失、编译抽取缺失、chunk 切分、检索召回或 rerank 排序。

## 最终结论

结论：`FAIL`

Q6 不能从“端到端待复验”推进为“端到端通过”。agentA 的 fallback structured evidence 修复未在真实 API 端到端闭环。

唯一失败类型：`证据已召回但回答漏点`

下一步建议：回到 Answer deterministic fallback 证据选择/grounding 链路继续做通用修复。修复重点应限制在 fallback 消费已召回结构化字段路径取值证据时的选择与输出，不得为 Q6 文件名、字段名、端口值、题面或答案片段写特判。
