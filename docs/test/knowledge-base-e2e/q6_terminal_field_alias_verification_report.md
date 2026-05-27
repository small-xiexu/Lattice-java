# Q6 terminal field alias 配置化修复验证报告

## 1. 验证时间与角色

- 验证时间：2026-05-27 13:49:16 CST
- 验证角色：agentD，验证/测试 Agent
- 验证范围：Q6 terminal field alias 配置化修复后的全面查询回归与验收
- 本轮性质：只读验收；除新增本报告外，不修改代码、测试、配置、脚本、题集或模型配置

## 2. 读取的上下文文件

- `/Users/sxie/xbk/Lattice-java/AGENTS.md`
- `/Users/sxie/xbk/Lattice-java/docs/quality-progress-and-lessons.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/eval/question-set.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/README.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/acceptance-report.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_fix_result_report.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_verification_report.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/q6_fallback_complementary_evidence_gate_verification_report.md`

## 3. redline 结果

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：PASS
- 关键汇总：
  - `BLOCKER=0`
  - `REVIEW=2028`
  - `ALLOWLIST=259`
  - 总命中：2287
  - 高风险：0
  - 中风险：2028
  - 低风险：259
- 说明：命令按验收要求刷新 `special_cases_report.md`；未修改扫描脚本或 allowlist。

## 4. 全量 mvn test 结果

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：PASS
- 关键汇总：
  - `Tests run: 915, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`
  - Total time：`06:36 min`
  - Finished at：`2026-05-27T13:34:45+08:00`
- 说明：测试日志包含若干预期失败路径日志，例如连接拒绝、模拟检索失败、超时重试等，但 surefire 汇总全绿。

## 5. 硬编码/禁词扫描结果

- 命令：

```bash
rg -n '"端口"|端口|readinessProbe|tcpSocket|tcp-liveness-readiness|8080|registry\.k8s|goproxy' \
  src/main/java/com/xbk/lattice/query \
  src/main/resources
```

- 结果：PASS
- 命中项：
  - `src/main/resources/db/schema.sql:46`：既有注释，内容为“文章中的明确性关键词数组（业务码、枚举值、端口等）”
  - `src/main/resources/config/lattice-query-semantic.yml:81`：允许的通用 alias `"端口"`
- 判定：
  - `src/main/java/com/xbk/lattice/query/**` 未命中中文字段语义硬编码或 Q6 特判。
  - `src/main/resources/config/lattice-query-semantic.yml` 中的 `"端口" -> port` 属于本轮预期的配置化 alias。
  - `schema.sql` 注释命中为既有非主链内容，非本轮风险。

## 6. 运行态说明

- 服务启动方式：`./scripts/run-local-dev.sh`
- profile：`local-dev`
- 端口：`18082`
- 健康检查：`GET /actuator/health` 返回 `{"status":"UP"}`
- 运行进程：
  - PID：`95857`
  - 启动时间：`Wed May 27 13:36:30 2026`
  - classpath 包含：`/Users/sxie/xbk/Lattice-java/target/classes`
- 最新编译产物确认：
  - `target/classes/com/xbk/lattice/query/service/TerminalFieldAliasRules.class`：`2026-05-27 13:28:15 +0800`
  - `target/classes/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.class`：`2026-05-27 13:28:15 +0800`
  - `target/classes/config/lattice-query-semantic.yml`：`2026-05-27 13:04:04 +0800`
  - `src/main/resources/config/lattice-query-semantic.yml`：`2026-05-27 13:02:07 +0800`
- 配置确认：
  - `lattice-query-semantic.yml` 包含 `terminal-field-aliases`
  - `canonical: "port"`
  - `aliases: "端口"`
- 本轮未清库、未重导、未重建向量。

## 7. 当前数据库只读计数

- `/api/v1/admin/overview`：
  - `articleCount=6`
  - `sourceFileCount=6`
  - `reviewPendingArticleCount=0`
  - `humanReviewDraftPendingCount=0`
  - `passedArticles=6`
  - `pendingQueryCount=24`
- 数据库只读计数：
  - `source_files=6`
  - `articles=6`
  - `article_chunks=13`
  - `fact_cards=11`
  - `article_vector_index=6`
  - `article_chunk_vector_index=13`
- 判定：当前库仍为 Q6 clean KB，可用于本轮逻辑回归；未执行清库、重导或重建向量。

## 8. Q1-Q12 逐题结果

| 题号 | queryId | outcome | generationMode | 判定 | 说明 |
|---|---|---:|---:|---|---|
| Q1 | `069ba5da-015c-4bea-a70d-9045fe715555` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确回答“下一步计划”主要是先验证知识库/手册能否稳定回答关键问题，不急着补规则。 |
| Q2 | `a7d3fdec-0b1d-4be3-92df-2baa1f5e6ac4` | `SUCCESS` | `LLM` | PASS | 正确区分 startup/readiness/liveness 职责。 |
| Q3 | `9d156879-fd9a-4722-bea1-cae79b42e52f` | `SUCCESS` | `LLM` | PASS | 正确区分 Situation Lead 与 Technical Lead。 |
| Q4 | `78e6e1ef-a15d-499d-8854-135984e23e75` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确拒答绩效奖金问题，未编造奖金公式。 |
| Q5 | `758dd274-9657-416f-9f18-a2924d410d80` | `SUCCESS` | `LLM` | PASS | 正确返回 `/healthz` 与 `8080`。 |
| Q6 | `0655d840-f60e-4f04-9900-9494b22272ef` | `SUCCESS` | `FALLBACK` | PASS | 正确返回 `spec.containers[0].readinessProbe.tcpSocket.port = 8080`。 |
| Q7 | `33133ee4-891b-4f63-9f13-28a06a94c010` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确识别 `grpc liveness`、`etcd-with-grpc`、`livenessProbe.grpc.port=2379`。 |
| Q8 | `8804e800-b71f-4c53-b036-25fb5b126ebd` | `SUCCESS` | `LLM` | PASS | 正确说明没有 database username。 |
| Q9 | `aefeba06-f8c6-4f2c-92af-c447ccb7da93` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确覆盖 `Initiate / Assess / Contain / Remediate / Retrospect`。 |
| Q10 | `a8887ae8-2edc-49b4-9d7f-07e646e631e5` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确区分 High 与 Medium incident 的影响范围、业务状态和响应强度。 |
| Q11 | `c3b4e6f5-7117-4d93-801f-c9fd114bf65a` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确回答 `Scribe`；citation 偏弱，记录为残余质量风险。 |
| Q12 | `79331b41-eec3-475d-aa4a-59ad22d9b143` | `PARTIAL_ANSWER` | `LLM` | PASS | 正确回答 `Extended`；citation 偏弱，记录为残余质量风险。 |

- Q1-Q12 总体：`12/12 PASS`
- 判定：满足至少 `10/12` 通过的验收标准。

## 9. S1-S4 搜索结果

| 编号 | 检查项 | Top 结果摘要 | 判定 | 说明 |
|---|---|---|---|---|
| S1 | `Kubernetes 探针与事件响应协同手册` | Top1 为 `Kubernetes 探针与事件响应协同手册`，source `01_markdown/probe-and-incident-operations.md` | PASS | 标题搜索命中目标文档。 |
| S2 | `下一步计划` | Top1 为 `Kubernetes 探针与事件响应协同手册`，Top2 为 `incident checklist`，未稳定命中目标 anchor/chunk | FAIL | 仍是标题/anchor 搜索链路问题，不能归因到 Q6。 |
| S3 | `incident checklist` | Top1 为 `incident checklist` | PASS | 命中目标工作表/文章。 |
| S4 | `Situation Lead`、`/healthz`、`Extended` | `Situation Lead` 命中 `incident checklist`；`/healthz` 命中 `http liveness`；`Extended` 命中 `incident checklist` / `incident response reference lite` | PASS | 多关键词搜索整体稳定。 |

- 搜索检查总体：`3/4 PASS`
- 判定：未满足 S1-S4 全部通过的整体最小验收标准。
- 特别说明：S2 失败应单独归类为标题/anchor 搜索链路未闭环，不应与 Q6 terminal field alias 修复混为同一根因。

## 10. Q6 详细结果

- 问题：`在 tcp-liveness-readiness.yaml 里，readiness probe 的端口是多少？`
- queryId：`0655d840-f60e-4f04-9900-9494b22272ef`
- HTTP 状态：`200`
- answer outcome：`SUCCESS`
- generationMode：`FALLBACK`
- modelExecutionStatus：`DEGRADED`
- reviewStatus：`PASSED`
- fallbackReason：`DETERMINISTIC_EXACT_LOOKUP_PREFERRED`
- 关键回答：

```text
fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080
```

- citationCheck：
  - `verifiedCount=1`
  - `demotedCount=0`
  - `coverageRate=1`
  - `claimCount=1`
  - `unsupportedClaimCount=0`
- citation marker：
  - claimText：`当前可确认的信息是：fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`
  - citationLiteral：`[→ 02_structured/tcp-liveness-readiness.yaml]`
- 源文件人工核验：
  - `docs/test/knowledge-base-e2e/sources/02_structured/tcp-liveness-readiness.yaml:13` 为 `readinessProbe:`
  - `docs/test/knowledge-base-e2e/sources/02_structured/tcp-liveness-readiness.yaml:14` 为 `tcpSocket:`
  - `docs/test/knowledge-base-e2e/sources/02_structured/tcp-liveness-readiness.yaml:15` 为 `port: 8080`
  - sibling `periodSeconds: 10` 位于第 17 行，未被误选为端口答案。
- 判定：Q6 PASS。真实 API 返回目标端口事实 `8080`，并且没有再把同父级 sibling 字段 `periodSeconds=10` 当成正确答案。

## 11. citation 支撑性判断

- Q6 citation 自动校验为 PASS：`coverageRate=1`、`unsupportedClaimCount=0`。
- 人工支撑性判断为 PASS：
  - citation 指向 `02_structured/tcp-liveness-readiness.yaml`。
  - 答案参考说明包含结构化 fact card：`fieldPath: spec.containers[0].readinessProbe.tcpSocket.port = 8080`。
  - 源文件第 13-15 行确实支撑 readiness probe 的 `tcpSocket.port=8080`。
- 注意事项：
  - 自动 `matchedExcerpt` 粒度偏粗，曾出现仅落到 `spec:` 的情况；但本轮 Q6 的 claim、引用目标源文件、answer reference 与源文件人工核验一致，因此不构成 Q6 失败。
  - Q11/Q12 的 XLSX 类回答 citation 偏弱，属于残余 citation 质量风险，但本轮未观察到明显编造。

## 12. 保护场景结果

| 场景 | 问题 | queryId | 判定 | 结果摘要 |
|---|---|---|---|---|
| endpoint / URL | `grpc-liveness.yaml 里的 etcd command 的 --listen-client-urls 是什么 URL？` | `8f49354e-821c-413b-82e0-3982269c819a` | PASS | 正确返回 `http://0.0.0.0:2379`，citation coverage `1`。 |
| endpoint / URL | `grpc-liveness.yaml 里的 --advertise-client-urls 是什么 URL？` | `4bada9a8-efd3-4b15-957e-11a2b7ae1235` | PASS | 正确返回 `http://127.0.0.1:2379`，citation coverage `1`。 |
| image | `grpc-liveness.yaml 里的镜像是什么？` | `cb61a531-b45a-4a99-bc4b-8347f2f8b19b` | PASS | 正确返回 `registry.k8s.io/etcd:3.5.1-0`，citation coverage `1`。 |
| version | `grpc-liveness.yaml 里的镜像版本是多少？` | `6ce1f34e-fcc5-42a3-8522-349c2bfb4670` | PASS | 正确返回 `3.5.1-0`；citation 偏弱但答案事实正确。 |
| 普通数值字段 | `grpc-liveness.yaml 里的 liveness probe initialDelaySeconds 是多少？` | `a255bc1e-d1d0-4d98-bbdb-e480c76a8644` | PASS | 正确返回 `spec.containers[0].livenessProbe.initialDelaySeconds = 3`，citation coverage `1`。 |
| 同父级 sibling 字段 | `tcp-liveness-readiness.yaml 里 readiness probe 的 periodSeconds 是多少？` | `1bb1d39d-37a8-4d68-b20a-ab6de18e439a` | PASS | 正确返回 `periodSeconds: 10`，没有与 `port=8080` 混淆。 |
| 普通 `key:number` | `tcp-liveness-readiness.yaml 里的 containerPort 是多少？` | `ff12595e-a871-4faf-985f-353c14719148` | PASS | 正确返回 `spec.containers[0].ports[0].containerPort = 8080`，citation coverage `1`。 |

- 判定：endpoint / URL / image / version / 普通数值字段未被本轮 terminal field alias 修复误伤。
- sibling 语义判定：
  - Q6 查询“readiness probe 的端口”时正确选择 `readinessProbe.tcpSocket.port=8080`。
  - sibling 查询“readiness probe 的 periodSeconds”时正确选择 `readinessProbe.periodSeconds=10`。
  - 未观察到 sibling 字段抢占叶子字段。

## 13. PASS / FAIL 结论

- Q6 terminal field alias 配置化修复：PASS
- redline：PASS，`BLOCKER=0`
- 全量 `mvn test`：PASS
- Java 主链中文字段语义硬编码 / Q6 特判扫描：PASS
- Q1-Q12：PASS，`12/12`
- 保护场景：PASS
- S1-S4 搜索：FAIL，`3/4`，S2 未通过
- 整体知识库最小验收：FAIL

最终判定：

```text
Q6 query/fallback 修复已闭环；
但本轮整体知识库最小验收仍因 S2 标题/anchor 搜索失败未完全通过。
```

## 14. FAIL 根因拆分

- 已解决根因：Q6 terminal field alias 配置化已生效，`端口 -> port` 能在结构化 exact path terminal field 链路中命中目标叶子字段。
- 未解决根因：S2 `下一步计划` 搜索仍未稳定命中目标 anchor/chunk，属于标题/anchor 搜索链路问题。
- 根因边界：
  - S2 失败不是 Q6 terminal field alias 修复的失败。
  - 未观察到 endpoint / URL / image / version / 普通数值字段回归。
  - 未观察到 sibling 字段误选当成 Q6 正确答案。

## 15. 未做事项

- 未清库。
- 未重导资料。
- 未重建向量。
- 未修改 `src/main/java/**`。
- 未修改 `src/test/java/**`。
- 未修改 `src/main/resources/**`。
- 未修改 `scripts/**`。
- 未修改模型配置。
- 未修改 redline allowlist。
- 未修改题集或验收答案。
- 未 staging。
- 未 commit。
- 未 push。
- 未更新 `docs/quality-progress-and-lessons.md`。

## 16. 是否建议进入提交阶段

- 从 Q6 修复范围看：建议进入 Q6 terminal field alias 配置化修复的提交阶段。
- 提交说明必须明确：Q6 已端到端通过，`端口 -> port` 配置化 alias 已生效，且没有观察到 endpoint / URL / image / version / 普通数值字段回归。
- 不建议把本轮写成“知识库最小验收整体通过”，因为 S2 标题/anchor 搜索仍未闭环。
- 建议将 S2 作为独立后续问题处理，根因方向为标题/anchor 搜索链路，而不是继续叠加 Q6 规则。
