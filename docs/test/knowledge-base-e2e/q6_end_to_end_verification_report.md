# Q6 端到端验证报告

执行时间：2026-05-26 14:50 +0800
执行角色：agentD / 验证测试 Agent
结论：Q6 端到端未通过；fact card 路径修复已生效，失败点后移到 Answer deterministic fallback 证据选择层。

## 1. 任务边界

- 本轮只做验证与只读归因。
- 未修改 `src/main/java/**`、`src/test/java/**`。
- 未修改 Query、AnswerGeneration、fallback、RRF、chunk fusion、fact card 生成逻辑。
- 未处理 S2，未修改 eval/question-set、redline allowlist、scan 脚本或 AGENTS。
- 未把 Q6 文件名、端口值或样例词写入生产代码、配置、prompt 或规则。

## 2. 前置文档

已按要求读取：

1. `AGENTS.md`
2. `docs/quality-progress-and-lessons.md`
3. `docs/multi-agent-model-routing-guide.md`
4. `docs/test/knowledge-base-e2e/q6_structured_fact_path_fix_result_report.md`
5. `docs/test/knowledge-base-e2e/q6_readiness_port_analysis_report.md`
6. `docs/test/knowledge-base-e2e/acceptance-report.md`
7. `docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md`
8. `docs/项目启动配置清单.md`
9. `README.md`
10. `docs/项目全流程真实验收手册.md`
11. `docs/模型绑定配置参考.md`

## 3. 验证前门禁

- 红线：`bash scripts/scan-redline.sh special_cases_report.md`
  - 结果：通过，`BLOCKER=0 / REVIEW=1977 / ALLOWLIST=259`
- Q6 fact card 定向测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`
  - 结果：通过，`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`

## 4. 验收资料与题集

固定资料目录：`docs/test/knowledge-base-e2e/sources`

导入前文件清单：

```text
docs/test/knowledge-base-e2e/sources/01_markdown/probe-and-incident-operations.md
docs/test/knowledge-base-e2e/sources/02_structured/grpc-liveness.yaml
docs/test/knowledge-base-e2e/sources/02_structured/http-liveness.yaml
docs/test/knowledge-base-e2e/sources/02_structured/tcp-liveness-readiness.yaml
docs/test/knowledge-base-e2e/sources/03_pdf/incident-response-reference-lite.pdf
docs/test/knowledge-base-e2e/sources/04_office/incident-response-checklists-lite.xlsx
```

确认项：

- 目标结构化资料存在：`docs/test/knowledge-base-e2e/sources/02_structured/tcp-liveness-readiness.yaml`
- 固定题集：`docs/test/knowledge-base-e2e/eval/question-set.md`
- Q6 仍存在：`tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？`
- 期望答案仍为：`8080`

## 5. 环境与模型配置

运行环境：

- PostgreSQL：复用现有 Docker 容器 `vector_db`
- Redis：复用现有 Docker 容器 `redis`
- 数据库：`ai-rag-knowledge`
- schema：`lattice`
- 服务端口：`18082`
- Profile：`local-dev`
- 启动入口：`./scripts/run-local-dev.sh`

模型配置：

- Chat baseUrl：`http://127.0.0.1:8888/v1`
- Chat model：`gpt-5.5`
- Embedding provider：按 `docs/模型绑定配置参考.md` 配置
- Embedding model：`embedding-3`
- Embedding dimensions：`2000`
- 密钥：已配置并脱敏，未写入报告

模型中心只读校验：

- provider connections：`2`
- model profiles：`2`
- agent model bindings：`10`
- query vector settings：`1`
- compile writer/reviewer/fixer 与 query answer/reviewer/rewrite 均绑定到 `gpt-5.5`
- embedding 测试成功：返回 `2000` 维向量
- chat 测试成功：`gpt-5.5` 返回对话结果

## 6. 清库与导入

清库命令：

```bash
./scripts/reset-lattice-schema.sh
```

清库后核心业务表计数：

```text
source_files                    0
source_file_chunks              0
articles                        0
article_chunks                  0
fact_cards                      0
compile_article_review_queue    0
```

导入方式：通过现有后台编译 API 导入整个 `sources` 目录。

```bash
curl -sS -X POST http://127.0.0.1:18082/api/v1/admin/compile/jobs \
  -H 'Content-Type: application/json' \
  --data '{"sourceDir":"/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/sources","incremental":false,"async":true,"orchestrationMode":"state_graph"}'
```

compile job：

- jobId：`1cffe9d3-f5cd-487d-9eec-2c3b895408a6`
- reviewMode：`LLM`
- status：`SUCCEEDED`
- persistedCount：`6`
- startedAt：`2026-05-26T06:08:23.914346Z`
- finishedAt：`2026-05-26T06:30:45.758750Z`
- reviewer route：`compile.reviewer.gpt-5-5`
- fixer route：`compile.fixer.gpt-5-5`
- acceptedCount：`6`
- pendingReviewCount：`0`
- needsHumanReviewCount：`0`
- fixAttemptCount：`1`

编译后核心表计数：

```text
source_files                    6
source_file_chunks              6
articles                        6
article_chunks                  14
fact_cards                      11
compile_article_review_queue    0
```

文章状态：

```text
lifecycle=ACTIVE, review_status=passed, count=6
```

人工确认：

- 本轮无 `needs_human_review` 草稿。
- 发布前队列数量：`0`
- 发布后正式文章数量：`6`
- 发布后待确认队列数量：`0`
- 因队列为空，未调用 approve/reject API。

向量刷新状态：

```text
article_vector_index          6
article_chunk_vector_index    14
fact_card_vector_index        0
```

`/api/v1/admin/vector/status` 显示：

- vectorEnabled：`true`
- configuredModelName：`embedding-3`
- schemaDimensions：`2000`
- dimensionsConsistent：`true`
- articleCount：`6`
- indexedArticleCount：`6`

## 7. 目标 fact card 检查

目标来源：

- `source_files.id=4`
- `relative_path=02_structured/tcp-liveness-readiness.yaml`
- `format=yaml`
- `source_file_chunks.id=4`
- `chunk_index=0`

原始 chunk 可回指到目标行，包含：

```text
readinessProbe:
  tcpSocket:
    port: 8080
```

目标来源生成了 2 张 fact card：

| factCardId | cardId | reviewStatus | path aware |
|---:|---|---|---|
| 8 | `fact-card:4:0:fact_enum:c740723c633ebfaf` | `low_confidence` | 否 |
| 9 | `fact-card:4:0:fact_enum:6d7a24677543bd90` | `valid` | 是 |

关键 fact card `9` 检查结果：

- `itemsJson` 包含 `pathAware=true`
- 包含 `keyPath`
- 包含 `parentPath`
- 包含 `pathSegments`
- 包含 `displayText`
- `evidenceText` 包含 `fieldPath: ... = ...`
- readiness 端口证据存在：

```text
fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080
port: 8080
```

对应 `itemsJson` 中的目标条目：

```json
{
  "key": "port",
  "raw": "port: 8080",
  "value": "8080",
  "keyPath": "spec.containers[0].readinessProbe.tcpSocket.port",
  "lineIndex": 14,
  "parentPath": "spec.containers[0].readinessProbe.tcpSocket",
  "contextPath": "spec.containers[0].readinessProbe.tcpSocket",
  "displayText": "spec.containers[0].readinessProbe.tcpSocket.port = 8080",
  "pathSegments": ["spec", "containers", "[0]", "readinessProbe", "tcpSocket", "port"]
}
```

结论：fact card 生成层的路径字段修复已在干净库重导后生效。

## 8. Q6 API 验证

调用：

```bash
curl -sS -X POST http://127.0.0.1:18082/api/v1/query \
  -H 'Content-Type: application/json' \
  --data '{"question":"tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？"}' | jq .
```

响应摘要：

- queryId：`54854d1a-1b6f-4776-b95d-ccba0b5586ab`
- answerOutcome：`SUCCESS`
- generationMode：`FALLBACK`
- modelExecutionStatus：`DEGRADED`
- reviewStatus：`ISSUES_FOUND`
- fallbackReason：`DETERMINISTIC_EXACT_LOOKUP_PREFERRED`
- citation coverage：`1.0`

实际回答核心内容：

```text
当前可确认的信息是：image: registry.k8s.io/goproxy:0.1
```

判定：

- Q6 期望回答 readiness probe 端口 `8080`
- 本轮 API 最终答案未直接回答 `8080`
- 因此 Q6 端到端仍失败

## 9. 只读归因

### 9.1 不是资料或编译缺失

- 整套 `sources` 目录已导入，6 个来源文件全部入库。
- 目标 YAML 已入 `source_files` 与 `source_file_chunks`。
- 6 篇文章均为 `ACTIVE / passed`。
- 目标 source chunk 可回指到原始 readiness 端口行。

### 9.2 不是 fact card 路径生成失败

- 目标 fact card `9` 为 `valid`。
- `itemsJson` 已包含 path-aware 字段。
- `evidenceText` 已包含 readiness 与 liveness 两条可区分的完整路径。
- readiness 目标证据明确存在：`spec.containers[0].readinessProbe.tcpSocket.port = 8080`。

### 9.3 不是检索未召回

retrieval audit：

- runId：`1`
- strategyTag：`intent=CONFIGURATION|shape=GENERAL|mode=parallel|rewrite=off|graph=off|vector=off`
- fusedHitCount：`5`
- factCardHitCount：`11`
- sourceChunkHitCount：`5`

融合结果中目标证据已召回：

| fusedRank | channel | evidenceType | title | factCardId |
|---:|---|---|---|---:|
| 1 | `source_chunk_fts` | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | |
| 2 | `fact_card_fts` | `FACT_CARD` | `结构化键值条目 - 02_structured/tcp-liveness-readiness.yaml#0` | `9` |
| 3 | `fact_card_fts` | `FACT_CARD` | `结构化列表条目 - 02_structured/tcp-liveness-readiness.yaml#0` | `8` |
| 4 | `source` | `SOURCE` | `02_structured/tcp-liveness-readiness.yaml` | |
| 5 | `article_chunk_fts/refkey` | `ARTICLE` | `tcp liveness readiness` | |

说明：

- 正确 fact card `9` 是 `fact_card_fts` 第 1 名、融合第 2 名。
- `fact_card_vector`、`article_vector`、`chunk_vector` 本次被策略禁用，但 FTS 通道已经召回正确证据，因此不是本轮 Q6 失败的主因。

### 9.4 失败发生在 Answer deterministic fallback

运行日志显示：

- answer prompt audit 中 `STRUCTURED FACT CARD EVIDENCE` 存在，长度约 `2599`，被截断但未省略。
- answer LLM 与 query reviewer 各出现过一次网关超时，均由重试恢复。
- 随后触发：

```text
query_exact_lookup_deterministic_preferred reason: GROUNDING_MISMATCH, groundingStatus: MISSING_PATH_SHAPE
```

最终响应：

- `generationMode=FALLBACK`
- `modelExecutionStatus=DEGRADED`
- `fallbackReason=DETERMINISTIC_EXACT_LOOKUP_PREFERRED`

answer audit 中唯一 claim：

```text
当前可确认的信息是：image: registry.k8s.io/goproxy:0.1
```

citation verifier 将该 claim 作为目标 YAML 中存在的文本行验证通过，但它并不回答 Q6 的 readiness 端口问题。

归因结论：

- 当前 Q6 失败类型为：正确证据已召回，但 Answer/fallback 在精确查值场景中选错证据句。
- 具体表现为：fact card 中已有 `readinessProbe.tcpSocket.port = 8080`，但 deterministic fallback 最终采用了同一 YAML 中的 `image` 行。
- 因本轮 agentD 禁止改生产代码，未做修复。

## 10. 最终结论

Q6 尚未端到端通过。

本轮验证确认：

1. 工程门禁通过。
2. 干净库重建成功。
3. 模型绑定已按用户指定与参考文档恢复：chat=`gpt-5.5`，embedding=`embedding-3/2000`。
4. 整套验收资料已重新导入，6 篇文章全部 `passed/ACTIVE`。
5. 目标 YAML 的 path-aware fact card 已正确生成。
6. Q6 查询正确召回目标 source chunk 与 path-aware fact card。
7. 最终 API 答案仍未回答 `8080`，而是 fallback 到错误的 `image` 行。

下一步建议由非 agentD 修复轮处理 Answer deterministic fallback 的通用证据句选择 / grounding 逻辑；本报告不包含代码修复。
