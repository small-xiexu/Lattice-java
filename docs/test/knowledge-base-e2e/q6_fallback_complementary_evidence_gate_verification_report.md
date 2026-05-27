# Q6 fallback complementary evidence gate 独立验证报告

日期：2026-05-27

角色：agentD

## 结论

本轮复验结论：FAIL。

complementary evidence gate 修复已经改变真实链路：Q6 不再回答 `image: registry.k8s.io/goproxy:0.1`，且 path-aware fact card 9 已进入最终 fallback 证据上下文。

但 Q6 仍未端到端通过。真实 API 最终答案从原先的机器标识符误答迁移为 sibling 字段误答：

- 用户问题：`tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？`
- 期望答案：`8080`
- 实际答案：`fieldPath: spec.containers[0].readinessProbe.periodSeconds = 10`
- `generationMode`：`FALLBACK`
- `answerOutcome`：`SUCCESS`
- `reviewStatus`：`PASSED`
- `citation_coverage`：`1.0000`

因此 Q6 不能标记为通过；`citation_coverage=1.0` 也不能作为通过依据。

## 范围约束

本轮只做复验与报告：

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改 `src/main/resources/**`
- 未修改 `scripts/**`
- 未清库、未重导、未重建向量
- 未修改模型配置、prompt、retrieval、RRF、rerank、citation、fallback 主链或 redline allowlist
- 未提交代码

唯一新增文件为本报告。

## 门禁结果

redline：

- `BLOCKER=0`
- `REVIEW=2006`
- `ALLOWLIST=259`

定向测试：

- `AnswerFallbackEvidenceSelectorTests`：`6/0/0/0`
- `FactCardGenerationServiceTests`：`21/0/0/0`
- 合计：`27/0/0/0`

全量测试：

- `mvn test` surefire 汇总：`909/0/0/0`

运行态：

- 服务曾由 `./scripts/run-local-dev.sh` 以 `local-dev` profile 在 `18082` 启动
- `AnswerFallbackEvidenceSelector.class` 时间晚于源码，服务从当前 `target/classes` 启动
- 复验完成后已停止本轮 `18082` 服务，当前无监听进程

## 数据状态

本轮复用当前 Q6 clean 库，未做清库或重建：

- `source_files=6`
- `articles=6`
- `fact_cards=11`
- `article_vector_index=6`
- `article_chunk_vector_index=13`

目标证据仍存在：

- 目标 source chunk：`02_structured/tcp-liveness-readiness.yaml#0`
- 目标 fact card：`fact_card_id=9`

fact card 9 中同时存在正确端口事实与 sibling 字段：

- 正确事实：`fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`
- sibling 字段：`fieldPath: spec.containers[0].readinessProbe.periodSeconds = 10`

## 真实 API 与审计

最新真实 query：

- `query_id=fcad2017-eeb0-427c-b0ce-31ea22b0b30e`
- `retrieval run_id=5`
- `answer audit_id=5`

检索审计：

- fused rank 1：`SOURCE`，`02_structured/tcp-liveness-readiness.yaml#0`
- fused rank 2：`FACT_CARD`，`fact_card_id=9`
- fused rank 3：`FACT_CARD`，`fact_card_id=8`
- fused rank 4：`SOURCE`，`02_structured/tcp-liveness-readiness.yaml`
- fused rank 5：`ARTICLE`，`default-source--02-structured-tcp-liveness-readiness`

答案审计：

- claim：`当前可确认的信息是：fieldPath: spec.containers[0].readinessProbe.periodSeconds = 10`
- claim status：`VERIFIED`
- citation literal：`[→ 02_structured/tcp-liveness-readiness.yaml]`
- source type：`SOURCE_FILE`
- target key：`02_structured/tcp-liveness-readiness.yaml`
- validation status：`VERIFIED`
- matched excerpt：`spec:`

citation 校验只证明错误 claim 所在源文件可被引用，并没有支撑用户问题所需的 readiness probe 端口事实。

## fallbackHits 证据

当前没有单独持久化的 `fallbackHits` 审计字段。本轮使用 deterministic fallback Markdown 的 `## 参考说明` 作为等价运行态 trace，因为该段由 `AnswerFallbackMarkdownBuilder.appendEvidenceReferenceSection` 遍历最终 `fallbackHits` 生成。

`answer_markdown` 的参考说明包含：

- `SOURCE`：`image: registry.k8s.io/goproxy:0.1`
- `ARTICLE`：`apiVersion = v1`
- `FACT_CARD`：`结构化键值条目 - 02_structured/tcp-liveness-readiness.yaml#0`，内容为 `fieldPath: spec.containers[0].readinessProbe.periodSeconds = 10`

因此可以确认：fact card 9 已进入最终 fallback 证据上下文。上一轮 runtime trace 中的 `selectComplementaryEvidenceByQuestionTokens` early return 丢卡问题已经不再是当前唯一故障点。

## 唯一下一层根因

唯一下一层根因定位为：`buildExactPathConclusionLines` 的 exact path 结论候选选择/排序选中了同一 fact card 内的 sibling 字段。

排除项：

- 不是资料缺失：源文件与 fact card 均含 `readinessProbe.tcpSocket.port = 8080`
- 不是检索召回缺失：目标 source chunk rank 1，fact card 9 rank 2
- 不是 complementary selector 仍丢卡：最终参考说明已出现 fact card 9
- 不是 claim binding：最终 claim 文本就是被选中的错误 sibling 字段
- 不是 citation binding 的首要问题：citation 对错误 claim 做了弱校验，但错误在 citation 前已经形成

更具体地说，当前 exact path 结论逻辑已经能识别结构化路径取值候选，但在同一个 `readinessProbe` 下没有把用户问的“端口/port”绑定到 `tcpSocket.port` 这个终端字段，导致同样包含 `readinessProbe`、数值、结构化 fieldPath 的 `periodSeconds = 10` 被选为最终 claim。

## 指标

- Answer Accuracy：FAIL
- Recall@5：PASS
- Recall@10：PASS
- Citation Accuracy：FAIL
- Abstain Accuracy：N/A
- Hallucination Count：0

说明：本次不是无证据编造，而是已召回正确证据后的 sibling 字段误选。

## 下一步建议

交给 agentB 先做只读设计/根因细化，或交给 agentA 做单变量最小修复时，范围应限定在 exact path structured value 的通用候选选择/排序：

- 优先把问题中的通用字段语义绑定到结构化路径终端字段或原始 key，例如端口类问题应优先匹配 `port` / `tcpSocket.port`
- 同时要求父级路径覆盖上下文焦点，例如 `readinessProbe`
- 对同父级下不回答目标字段的 sibling 字段降权，例如 delay、period、timeout 这类时序字段不应抢答端口问题
- 不得为 Q6 文件名、题面、端口值、Kubernetes、`readinessProbe` 或当前资料写任何特判

Q6 保持失败状态，未经下一轮真实 API 复验不得标记通过。
