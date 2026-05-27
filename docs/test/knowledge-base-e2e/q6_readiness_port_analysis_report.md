# Q6 readiness probe 端口失败根因分析报告

## 1. 问题复现

- 题目来源：[`docs/test/knowledge-base-e2e/eval/question-set.md`](../eval/question-set.md) 第 62-68 行，Q6 为“`tcp-liveness-readiness.yaml` 里 `readiness probe` 使用了哪个端口？”，期望答案是 `8080`。
- 复现命令：

```bash
curl -sS -X POST http://127.0.0.1:18082/api/v1/query \
  -H 'Content-Type: application/json' \
  --data '{"question":"tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？"}' | jq .
```

- 本次复现 `queryId`：`e2307788-88ef-4d5c-b1dd-b737eb95e26d`

## 2. 现象与实际返回

- `/api/v1/query` 实际返回：
  - `answerOutcome = SUCCESS`
  - `generationMode = FALLBACK`
  - `modelExecutionStatus = DEGRADED`
  - `fallbackReason = DETERMINISTIC_EXACT_LOOKUP_PREFERRED`
  - `sources[0].articleKey = 01-markdown--02-structured-tcp-liveness-readiness`
  - `sources[0].sourcePaths[0] = 02_structured/tcp-liveness-readiness.yaml`
- 实际答案没有回答 `8080`，而是退化成：

```text
当前可确认的信息是：image: registry.k8s.io/goproxy:0.1
```

- 这说明：
  - 不是“整题查不到资料”，因为 `sources` 已指向正确 YAML / article。
  - 是“命中了正确资料，但最终选出来的证据句不对”。
- 与对照题 Q5 相比，`http-liveness.yaml` 同类问题返回：
  - `generationMode = LLM`
  - `modelExecutionStatus = SUCCESS`
  - 正确回答 `/healthz` 和 `8080`
- 验收报告也记录了同一现象：[`docs/test/knowledge-base-e2e/acceptance-report.md`](./acceptance-report.md) 第 112-115、158-174 行已明确写出 Q6 失败且表现为 fallback 退化。

## 3. 源文件证据

- 源 YAML 原文见 [`docs/test/knowledge-base-e2e/sources/02_structured/tcp-liveness-readiness.yaml`](./sources/02_structured/tcp-liveness-readiness.yaml) 第 13-20 行：
  - `readinessProbe.tcpSocket.port: 8080`
  - `livenessProbe.tcpSocket.port: 8080`
- 数据库 `source_files` 与 `source_file_chunks` 只读查询结果也确认：
  - `relative_path = 02_structured/tcp-liveness-readiness.yaml`
  - 原文完整入库，`readinessProbe -> tcpSocket -> port: 8080` 没有丢失。
- 结论：
  - YAML 抽取层不是主故障点。
  - 源材料里明确有正确答案。

## 4. 正式文章证据

- 正式文章已存在且状态正常：
  - `article_key = 01-markdown--02-structured-tcp-liveness-readiness`
  - `title = tcp liveness readiness`
  - `review_status = passed`
  - `lifecycle = ACTIVE`
- `articles.summary` 明确写到：
  - `readinessProbe` 和 `livenessProbe` 都检查 `8080` 端口。
- `articles.content` 中还存在更直接的正文：
  - `## TCP readinessProbe 配置`
  - ``该 `readinessProbe` 的 `tcpSocket.port` 配置为 `8080`。``
  - ``spec.containers[0].readinessProbe.tcpSocket.port = 8080``
- `article_chunks` 查询结果显示：
  - `chunk_index = 0/1` 中已经包含上述 readiness 端口事实。
  - 也包含 “`readinessProbe` 与 `livenessProbe` 都检查同一个端口 `8080`” 的对比段落。
- 结论：
  - article 成文层不是主故障点。
  - 正式文章里已经有可直接作答的证据。

## 5. 检索与证据链证据

- 检索审计 `query_retrieval_runs` 最新记录：
  - `run_id = 42`
  - `strategy_tag = intent=CONFIGURATION|shape=GENERAL|mode=parallel|rewrite=off|graph=off|vector=off`
  - `fused_hit_count = 5`
  - `fact_card_hit_count = 11`
  - `source_chunk_hit_count = 5`
  - `coverage_status = not_applicable`
- `query_retrieval_channel_hits` 显示正确资料已被召回：
  - `source_chunk_fts` Top1 / `fused_rank = 1`：`02_structured/tcp-liveness-readiness.yaml#0`
  - `fact_card_fts` Top1 / `fused_rank = 2`：fact card `8`
  - `fact_card_fts` Top2 / `fused_rank = 3`：fact card `9`
  - `source` / `fused_rank = 4`：正确 YAML
  - `article_chunk_fts` / `fused_rank = 5`：正确正式文章
- 这可以排除“检索未召回”。

- 真正的问题出在结构化事实卡：
  - fact card `8` 只有：
    - `name: goproxy`
    - `containerPort: 8080`
  - fact card `9` 虽然有两个 `port: 8080`，但都只是扁平键值：
    - 没有 `readinessProbe.tcpSocket.port`
    - 没有 `livenessProbe.tcpSocket.port`
    - 只剩两个无父路径语义的 `port: 8080`
- 代码层也能解释这个结果为什么会发生：
  - [`src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationBaseSupport.java`](../../../../src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationBaseSupport.java) 第 338-350 行，`findKeyValueItems(...)` 是逐行匹配 `key: value`，只提取当前行的 `key/value/raw`。
  - 同文件第 49-50 行的 `KEY_VALUE_PATTERN` 也是纯行级模式，不保留 YAML 父层级路径。
  - [`src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationListSupport.java`](../../../../src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationListSupport.java) 第 83-110 行，把这些行级 `KeyValueItem` 直接组装成 `key_value_list` fact card。
  - [`src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationModels.java`](../../../../src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationModels.java) 第 131-170 行可见 `KeyValueItem` 只有 `key/value/raw`，没有“父路径/层级上下文”字段。
- 因此，到了 query 侧，系统拿到的是两个“同样叫 `port`、值同样是 `8080`”的事实，已经无法仅靠结构化卡片区分“这是 readiness 的端口，还是 liveness 的端口”。

- 为什么最后会走到 fallback：
  - 从返回字段 `fallbackReason = DETERMINISTIC_EXACT_LOOKUP_PREFERRED`，结合 [`src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupSupport.java`](../../../../src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupSupport.java) 第 54-95 行，可推断系统在 exact lookup 判定阶段没有接受模型答案，而是强制切到了 deterministic fallback。
  - 这里我用“可推断”而不是“已直接观测到 LLM 初答内容”，因为当前只读信息里没有保存本次 LLM 初答全文。

- 为什么 fallback 会选错成 `image`：
  - [`src/main/java/com/xbk/lattice/query/service/AnswerGenerationQuestionTypeBasicSupport.java`](../../../../src/main/java/com/xbk/lattice/query/service/AnswerGenerationQuestionTypeBasicSupport.java) 第 66-74、267-273 行表明：题目里带文件名这类精确标识时，会被识别成 `exact lookup` / `path question`。
  - [`src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`](../../../../src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java) 第 156-159 行表明：只要问题里存在显式标识符，就会开启“机器标识符补足”逻辑。
  - [`src/main/java/com/xbk/lattice/query/service/AnswerGenerationCoreSignalSupport.java`](../../../../src/main/java/com/xbk/lattice/query/service/AnswerGenerationCoreSignalSupport.java) 第 107-112 行里，带 `-` / `_` / `.` 的字符串会被视为“机器标识符”。
  - 同一个 snippet 打分函数在 [`src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`](../../../../src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java) 第 363-476 行中，会给这类候选额外加分。
  - `image: registry.k8s.io/goproxy:0.1` 恰好满足“机器标识符”规则，而扁平化后的 `port: 8080` 又没有 `readinessProbe` 这一层级语义可用，所以无关的 `image` 行被错误抬到了前面。
- 结论：
  - 检索召回层没有丢证据。
  - 在 answer generation 前，正确证据其实已经存在于 source chunk / article chunk 中。
  - 失败发生在“结构化事实丢层级”之后的 evidence 绑定与 fallback 选择阶段。

## 6. 主根因

- 唯一主根因：**结构化概念生成层**
- 更具体地说，是 **YAML 结构化事实卡生成时把层级字段压平，丢失了 `readinessProbe` / `livenessProbe` 的父路径语义**。
- 直接证据：
  - 源 YAML 原文有 `readinessProbe.tcpSocket.port: 8080`
  - 正式文章也有 `spec.containers[0].readinessProbe.tcpSocket.port = 8080`
  - 检索也召回了正确 YAML / article
  - 只有 fact card 被压平成两个无上下文的 `port: 8080`
- 所以 Q6 失败并不是“没抽到 `8080`”，而是“抽到了两个 `8080`，但没保留它们分别属于哪个 probe”。

## 7. 次根因

- 次根因：**fallback 降级层**
- 更具体地说，是 **evidence 选择 / snippet 打分在正确证据已召回时，仍然错误输出了无关的 `image` 行**。
- 直接表现：
  - 正确 source chunk 是 `fused_rank = 1`
  - 正确 article chunk 也在融合结果里
  - 但最终 fallback 没有优先使用“`readinessProbe` 的 `tcpSocket.port` 为 `8080`”这类直接事实句
  - 而是给出 `image: registry.k8s.io/goproxy:0.1`
- 这说明 fallback 层在“同一 YAML 内存在多个结构化字段，且 fact card 缺少路径语义”的场景下不稳。

## 8. 不踩红线的修复方向

- 通用 YAML 结构化提取增强：
  - 为 YAML / JSON 生成带完整字段路径的结构化事实，如 `spec.containers[0].readinessProbe.tcpSocket.port = 8080`
  - 不要只保留扁平 `port = 8080`
- 通用 evidence binding 修复：
  - exact lookup / structured fact 题中，优先匹配“字段路径或邻近上下文同时覆盖问题焦点”的候选
  - 例如优先识别“`readinessProbe` + `port` + `8080`”是同一事实，而不是仅看到 `port`
- 通用 fallback grounding 修复：
  - 当 source chunk / article chunk 中已经存在可直接回答的句子时，fallback 应优先输出那条直接事实句
  - 不应让无关但格式上像“机器标识符”的字段压过真正的答案字段
- 通用问题类型识别修复：
  - 带文件名的 YAML 配置问答，不应被过度当成“路径契约题”
  - 应优先识别成“结构化字段取值题”

## 9. 暂不建议的错误修法

- 不要对 `tcp-liveness-readiness.yaml` 文件名写硬编码分支。
- 不要写“问题里出现 `readiness` 就返回 `8080`”这种 case 特判。
- 不要只在 query 主链里给 `readinessProbe` / `livenessProbe` 增加字符串白名单命中。
- 不要通过修改测试预期、验收结论或题集来掩盖失败。
- 不要简单关闭 `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` 或整体下调 fallback 阈值；这样只会掩盖当前 case，不能修复 YAML 结构化语义缺失。
- 不要只补 article 文案。因为正式文章本来就已经有正确答案，问题不在文章缺失，而在结构化证据绑定与 fallback 选择。

是否已确认根因：是

是否建议进入代码修复：是
