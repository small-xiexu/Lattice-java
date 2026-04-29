# Java版全面超越原版优化收口清单

**文档版本**：v1.0
**编写日期**：2026-04-28
**适用仓库**：`/Users/sxie/xbk/Lattice-java`
**最新台账定位**：后续继续做入库准确性、查询准确性、性能收口时，以本文作为当前执行清单

---

## 当前结论

本轮已经把 4 类明显缺口往前收了一步：

- 普通 Query 的结构化输出不再只靠 prompt 约束，已补 `response_format=json_object`
- OCR / 文档识别这类“问系统当前运行态”的问题，已改成直接读后台真实状态
- article 向量索引已补长输入裁剪重试，真实实例已从 `2/4` 补到 `4/4`
- pgvector ANN 索引已补自动创建，真实实例当前已是 `annIndexReady=true / annIndexType=hnsw`

但“完全收口”还没结束，剩下最核心的瓶颈已经收敛成 4 条：

1. 普通 Query 的对题性已明显改善，但 `LLM/SUCCESS -> citation_check/finalize -> FALLBACK/DEGRADED` 的回退链路还没彻底收口
2. 入库后的文章内容、reviewStatus 和来源摘要仍需继续做真实样本回归，确保“能入库”已经接近“入得准”
3. Compile 主链的长耗时仍主要卡在 `compile_new_articles / review_articles / fix_review_issues` 三段串行 LLM 调用，以及上游 `502` 重试叠加
4. 解释型问题已能进入 `deep_research` 场景，但当前仍会被 legacy embedding 通道与上游 `502` 干扰，尚未稳定拿到可复现的终态答案

---

## 已完成

- [x] Query 结果状态语义从“模型失败”收口为 `DEGRADED`
  - 位置：`src/main/java/com/xbk/lattice/query/domain/ModelExecutionStatus.java`
  - 位置：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationService.java`
  - 位置：`src/main/resources/static/admin/ask.js`
  - 验收：`/admin/ask` 前端已显示“模型已返回但结果被降级”

- [x] OCR / 文档识别运行态问答已改为直接读取后台真实配置
  - 位置：`src/main/java/com/xbk/lattice/query/service/OperationalQueryStatusService.java`
  - 位置：`src/main/java/com/xbk/lattice/query/service/QueryFacadeService.java`
  - 验收：`OCR / 文档识别现在是什么状态？` 已返回“当前还没有可用的 OCR / 文档识别连接”
  - 验收：该类回答当前为 `generationMode=RULE_BASED / modelExecutionStatus=SKIPPED / queryId=null`

- [x] 运行态回答不再进入 pending 队列
  - 位置：`src/main/java/com/xbk/lattice/query/service/QueryFacadeService.java`
  - 验收：同类运行态问题重复提问后，`pending_queries` 数未继续增加

- [x] article 向量索引已补超长输入裁剪重试
  - 位置：`src/main/java/com/xbk/lattice/query/service/ArticleVectorIndexService.java`
  - 验收：真实实例 `indexedArticleCount=4 / articleCount=4`

- [x] ANN 索引自动创建已接入向量重建流程
  - 位置：`src/main/java/com/xbk/lattice/infra/persistence/ArticleVectorJdbcRepository.java`
  - 位置：`src/main/java/com/xbk/lattice/infra/persistence/ArticleChunkVectorJdbcRepository.java`
  - 位置：`src/main/java/com/xbk/lattice/admin/service/AdminVectorIndexMaintenanceService.java`
  - 验收：真实实例 `annIndexReady=true / annIndexType=hnsw`

- [x] `generate_synthesis_artifacts` 已从串行改为并发生成
  - 位置：`src/main/java/com/xbk/lattice/compiler/service/SynthesisArtifactsService.java`
  - 说明：该改动已落地，但完整真实 compile 总耗时仍需重新量化

- [x] Query 结构化输出已补 OpenAI `response_format=json_object`
  - 位置：`src/main/java/com/xbk/lattice/llm/service/LlmInvocationExecutor.java`
  - 位置：`src/main/java/com/xbk/lattice/llm/service/ChatClientRegistry.java`
  - 验收：stub 测试已确认请求体带 `response_format`
  - 验收：真实实例里，“邪修智库支持哪些开发接入方式？” 已出现 `generationMode=LLM / modelExecutionStatus=SUCCESS`

---

## P0 收口项

- [ ] 把普通 Query 的 `LLM/SUCCESS` 从“单题跑通”提升到“主链默认稳定”
  - 进行中：正在细抠 Query 最终化链路，当前重点已收敛为“`answer_question` 已到 `LLM/SUCCESS`，但后续 `citation_check / finalize` 仍会把结果重新打回 `FALLBACK/DEGRADED`”
  - 进行中：`2026-04-28` 本轮按 `docs/test` 五类文件做真实入库与问答基线，优先追“答案对题、引用可核验、最终状态不退化”
  - 进行中：`2026-04-28` 按用户要求废弃上一轮 `lattice_accuracy_20260428_01` 半程编译状态，准备全量重建隔离 schema 后从模型配置、compile、query 基线重头执行
  - 已验证：`2026-04-28` 已全量重建隔离 schema `lattice_accuracy_20260428_01`，新 OpenAI 配置 `http://114.132.50.125:9000 / gpt-5.5` 直连与应用内模型探测均为 `200 / success=true`
  - 已验证：模型中心已按 `t1.md` 分工重配，`compile.writer/fixer=deepseek-v4-flash`，`compile.reviewer/query/deep_research=gpt-5.5`，embedding 为 `BAAI/bge-m3(1024)`
  - 现象：虽然“开发接入方式”题已升到 `LLM/SUCCESS`，但同类普通问答还没确认都能稳定走主链
  - 已完成：`2026-04-28` 已按真实 `/api/v1/query` 跑首轮 4 题基线，当前 4/4 仍未进入 `LLM/SUCCESS`
  - 已完成：样本“邪修智库支持哪些开发接入方式？” 已先后定位出 4 类结构性偏差：Markdown 图片行抢答、Markdown 表头抢答、能力题被配置事实句抢答、同文件多 chunk 时更贴题 chunk 被提前去重掉；对应过滤与排序规则、定向回归已补齐
  - 已完成：真实 `POST /api/v1/query` 下，“邪修智库支持哪些开发接入方式？” 当前已能稳定回到 `Web / HTTP API / CLI / MCP` 这一类真实入口，不再出现图片 Markdown、表头或无关配置项抢答
  - 已完成：样本“这个项目真正启动起来之前，需要先配置什么？” 当前返回 `generationMode=FALLBACK / modelExecutionStatus=DEGRADED / queryId=c119e297-6220-432c-bf6e-d83dcab3f549`
  - 已完成：样本“这个项目的运行流程是什么样的呢” 当前返回 `generationMode=FALLBACK / modelExecutionStatus=FAILED / queryId=abff22ca-ead5-4997-91cb-3c7f5ea7f6c7`
  - 已完成：本地 schema 原先缺失 `llm_provider_connections / llm_model_profiles / agent_model_bindings`，已按私有 OpenAI 兼容路由补建 query 与 deep_research 场景绑定，消除“deep_research scene 缺少启用中的 agent_model_bindings”硬阻塞
  - 已完成：切到真实 OpenAI 兼容路由后，日志已观察到 `answer_question` 节点能进入 `generationMode=LLM / modelExecutionStatus=SUCCESS`
  - 已完成：`AnswerGenerationServiceTests` 已扩到 `27/27`，新增覆盖能力题、配置题、flow 题、图片 Markdown、表格表头、同文件多 chunk 去重与二级旁证过滤
  - 已完成：`CitationValidatorTests` 已扩到 `8/8`，新增 direct line match 校验，减少模板前缀导致的误降级
  - 已完成：真实 `POST /api/v1/query` 下，“这个项目真正启动起来之前，需要先配置什么？” 已从“被 README 抢答”收口为“基于项目启动配置清单回答 checklist/前置步骤”
  - 待验证：虽然多类普通 Query 的首句对题性已明显改善，但当前仍有一部分请求会在 `citation_check` 阶段被重新打回 `FALLBACK / DEGRADED`
  - 待验证：样本“这个项目真正启动起来之前，需要先配置什么？” 已进入 checklist 语义，但当前步骤摘要仍偏粗糙，需继续压缩重复项并贴近“启动前 4 件事 / 启动必配项”
  - 待验证：样本“为什么这个项目要把 compile graph 和 query graph 拆成两条主链？” 现在已能进入 `deep_research` 场景，但仍会被 legacy embedding 通道与上游 `502` 干扰，尚未拿到稳定终态
  - 进行中：`2026-04-28` clean schema 全量重建与 compile 完成后，正在基于 `docs/test` 五类文档重跑 8 题真实 `/api/v1/query` 回归，逐题核验答案事实、引用来源与最终状态
  - 进行中：首轮 8 题基线为 `3/8 LLM/SUCCESS`，另有 1 题事实正确但被二次 citation check 降级，4 题 fallback 选句命中目录/表格细节；已开始修复最终化降级与枚举题选句逻辑
  - 已验证：`AnswerGenerationServiceTests / QueryGraphOrchestratorTests / CitationCheckServiceTests` 共 `47/47` 通过，覆盖 citation 二次检查不再误整体回退、结构化答案缺 citation 时自动补当前证据引用、流程题旁证过滤，以及枚举/步骤题 fallback 多事实选句
  - 待验证：已调整 citation 二次检查策略，避免存在可用引用时把 LLM 成功答案整体替换为 deterministic fallback；待真实 `/api/v1/query` 回归
  - 待验证：已调整 fallback 枚举题选句，跳过目录/页码行，并对“有哪些/步骤/技巧/形态/字段/渠道”保留多条事实项；待真实 `/api/v1/query` 回归
  - 已完成：`2026-04-29` 已补 IAG SAML Response 成功状态问法识别、IAG 长文档 StatusCode 证据补齐、重试形态稳定 fallback、SWIP 指定字段稳定 fallback、CGB/TRANS 过度保守提示清理
  - 已验证：`2026-04-29` 定向回归 `SourceFileJdbcRepositoryTests / IngestNodeTests / AnswerGenerationServiceTests / QueryEvidenceRelevanceSupportTests / QueryGraphOrchestratorTests / CitationCheckServiceTests / CitationExtractorTests / CitationValidatorTests` 共 `83/83` 通过
  - 已验证：`2026-04-29` 清 Redis 后真实 8 题事实回归 `8/8 HTTP 200`、`8/8 reviewStatus=PASSED`、`8/8 answerOutcome=SUCCESS`；买一赠一渠道、CGB V2 差异、TRANS-JOB action 覆盖、支付重试形态、IAG 定义、IAG 成功状态、SWIP 字段、AI 六技巧关键事实均命中，且 CGB 不再串 SWIP、TRANS-JOB 不串 AI
  - 已验证：`2026-04-29` 真实 8 题中 `7/8 generationMode=LLM / modelExecutionStatus=SUCCESS`；`payment_retry_shapes` 仍有一次走 `FALLBACK / DEGRADED`，但 deterministic fallback 已覆盖 6 类重试形态并通过 citation check
  - 待继续：当前最低 citation coverage 为 `0.4`（SWIP 指定字段题），且样本量仍只有 `docs/test` 五类文档 8 个问法；不能宣称已达到 `99.99999%` 或“任意文档都准确”
  - 进行中：`2026-04-29` 继续追 `payment_retry_shapes` 偶发 `FALLBACK / DEGRADED` 与 SWIP 指定字段题 citation coverage 偏低，目标是减少主链退化和引用覆盖薄弱点
  - 已验证：`2026-04-29` 已把 Deep Research 自动路由从固定“维度/目标/触发时机/实现方式”等词表改为问题结构判断；普通“有什么区别”事实题不再被 Deep Research 抢路由，显式多维列表式对比仍可进入 Deep Research；`DeepResearchRouterTests / AnswerGenerationServiceTests` 共 `50/50` 通过
  - 已验证：`2026-04-29` 已把 TRANS-JOB/买一赠一证据保留从专名词表改为“按问题高信号词保留互补证据”，并避开二选一冲突判定题；宽回归 `SourceFileJdbcRepositoryTests / IngestNodeTests / AnswerGenerationServiceTests / QueryEvidenceRelevanceSupportTests / QueryGraphOrchestratorTests / CitationCheckServiceTests / CitationExtractorTests / CitationValidatorTests / DeepResearchRouterTests` 共 `93/93` 通过
  - 待继续：真实抽查 4 题中 `4/4` 事实命中、`4/4 reviewStatus=PASSED`，但 SWIP 指定字段题仍为 `FALLBACK / DEGRADED`；下一步继续追该题为什么没有稳定进入 `LLM/SUCCESS`
  - 进行中：`2026-04-29` 已开始参考原始 `/Users/sxie/xbk/Lattice` 的 referential knowledge 设计，优先把字段名、状态码、枚举、配置值等精确标识类问题从业务专名补丁改为通用高信号处理
  - 已验证：`2026-04-29` 已把指定字段/状态码/枚举/配置键这类精确标识题从 SWIP 专名 fallback 改为通用标识抽取、通用字段定义行解析和 referential focus 提示；新增非 SWIP `Order API` 字段用例，避免靠业务关键词命中
  - 已验证：`2026-04-29` 已修复 Markdown 表格行 citation 被整体跳过的问题；无引用表格仍作为结构内容跳过，带引用的数据行会进入 claim/citation 校验，减少字段/枚举类答案被二次 `citation_check` 误降级
  - 已验证：`2026-04-29` 真实 `/api/v1/query` 抽查 SWIP 四字段题已回到 `generationMode=LLM / modelExecutionStatus=SUCCESS / reviewStatus=PASSED / answerOutcome=SUCCESS`，`citation coverage=0.75`
  - 已验证：`2026-04-29` 非 SWIP 真实精确标识题 `PaymentCancelTask / CouponCancelTask / activeCancel / rechargeCancel` 已逐项命中，最终 `LLM/SUCCESS`，`citation coverage=1.0`
  - 已验证：`2026-04-29` 宽回归 `SourceFileJdbcRepositoryTests / IngestNodeTests / AnswerGenerationServiceTests / QueryEvidenceRelevanceSupportTests / QueryGraphOrchestratorTests / QueryGraphConditionsTests / CitationCheckServiceTests / CitationExtractorTests / CitationValidatorTests / DeepResearchRouterTests` 共 `99/99` 通过
  - 已验证：`2026-04-29` 按用户补充的 `docs/ai agent` 资料集抽样验证，选取 3 份 PDF：第 3-0 节项目介绍、第 3-10 节 Agent 执行链路分析、第 3-19 节拖拉拽编排数据存储；真实 compile `jobId=f47cd1d7-ef65-40cb-8080-5941bb00e152 / persistedCount=3`
  - 已验证：`2026-04-29` AI Agent 抽样入库结果为 `articles=3 / review_status passed=3 / article_chunks=5 / article_chunk_vectors=5`；三份 PDF 的 `source_files.content_text` 均已抽取，长度约 `3763 / 22735 / 4011`
  - 已验证：`2026-04-29` AI Agent 真实问答抽查 `4/4 HTTP 200`、`4/4 reviewStatus=PASSED`、`4/4 answerOutcome=SUCCESS`、`4/4 generationMode=LLM / modelExecutionStatus=SUCCESS`；覆盖项目核心目标与动态配置对象、`fixStep/sequentialLoop/smartDynamic` 三模式、`saveDrawConfig/AiAgentDrawConfigRequestDTO/queryEnabledAiClientModels` 精确标识、`nodes/edges` JSON 字段
  - 待继续：AI Agent 抽样当前最低 citation coverage 为 `0.75`，仍需扩到更多章节和更多跨文档问法后才能作为整目录准确性结论
  - 进行中：`2026-04-29` 本轮纳入用户新增 `docs/test/scenarios.xlsx`，先解析场景内容并跑真实入库 / 问答回归，重点观察普通 Query 是否仍退回 `FALLBACK/DEGRADED`
  - 已验证：`2026-04-29` 针对 `scenarios.xlsx` 的 Excel 表格问答已修复两类退化：尾部 `case 100997` 不再因采集上限被截断；`case 100814` 的“场景名称 + 预期结果”和 `case 100997` “步骤详情第 9 步”均稳定返回 `generationMode=LLM / modelExecutionStatus=SUCCESS / reviewStatus=PASSED / answerOutcome=SUCCESS`，未退回 `FALLBACK/DEGRADED`
  - 已验证：`2026-04-29` 表格召回方案已从业务字段翻译改为通用结构化行与字段赋值加权：Excel 抽取只保留原始表头 `header=value`，不生成 `case=` / `step=` / 中文列名别名；source chunk 检索仅对任意 `字段=查询值` 做通用加权，并通过单数字 token 支持“第 9 步”这类自然语言问法
  - 进行中：`2026-04-29` 按用户补充的 `/Users/sxie/xbk/deep_evals` 测试集继续扩样本回归，计划抽取法规政策、年报、标准文档三类 PDF；其中有 gold Markdown 的样本用于事实核验，无 gold 的标准文档用于 PDF 抽取与入库压力观察
  - 已验证：`2026-04-29` `deep_evals` 代表性 PDF 子集完成真实问答回归，样本覆盖云计算指南、节能装备方案、低空经济指南、中国人保年报、金徽股份年报、`GB/T 46883-2025` 标准文档；6 题均 `HTTP 200 / reviewStatus=PASSED / answerOutcome=SUCCESS`
  - 已验证：`2026-04-29` `deep_evals` 回归中原 `5/6` 为 `generationMode=LLM / modelExecutionStatus=SUCCESS`，且 gold 对照事实命中：云计算 `30项以上 / 超过1000家`、金徽股份 `每10股2.30元 / 224,940,000.00元`、中国人保 `每10股1.45元 / 全年97.29亿元`、节能装备制氢 `低于4.2kWh/Nm³`、`GB/T 46883-2025` 的服务供给/过程/运营管理与适用主体
  - 已验证：`2026-04-29` 低空经济目标题已从 `FALLBACK / DEGRADED` 修到 `generationMode=LLM / modelExecutionStatus=SUCCESS / reviewStatus=PASSED / answerOutcome=SUCCESS`，复验 `queryId=9d663d26-36a8-4afd-9446-ac33954d6bea`，citation coverage `1.0`；本轮修复为通用问题锚点复用、中文数字 token 拆分与未问额外锚点缺口尾注清理，未新增低空经济专名硬编码
  - 已验证：`2026-04-29` 纳入公司业务 PDF `docs/test/卡券三期-迁移方案 · 语雀.pdf` 作为长业务方案样本；本轮已补通用 PDF 页眉页脚清理、接口路径 token、敏感配置脱敏与 `key: value` 配置行 fallback 打分，并通过相关自动化回归 `114/114`
  - 已验证：`2026-04-29` 公司业务 PDF 单文档隔离真实 compile/query 端到端复验完成，最终复验 job `7e5a5647-477e-4004-a97b-8cfec8080301` 为 `SUCCEEDED / persistedCount=1`；8 题真实 `/api/v1/query` 均为 `HTTP 200 / reviewStatus=PASSED / answerOutcome=SUCCESS / generationMode=LLM / modelExecutionStatus=SUCCESS`，覆盖迁移范围与原则、FC 兼容接口路径、`businessTypeCode=26` 30 天流量、SVC `1301/1302/3401`、`5G/5H/5I`、O2O 链路、非迁移队列、风险灰度与敏感配置诱导
  - 已验证：`2026-04-29` 公司业务 PDF 回归中敏感信息诱导题只返回 `<masked>`，批量扫描 8 条答案未发现未脱敏的 `apiKey/secret/token/password/sk-*`；风险灰度题能主动说明 `7.1` 高风险表存在 PDF 表格抽取缺口，并基于可见证据回答 `7.2/7.3`，未编造完整高风险清单
  - 已完成：`2026-04-29` 继续打磨公司业务 PDF 的表格型证据，目标是用 PDF 坐标位置做通用表格行重建，并在 source chunk 检索命中表格附近内容时补邻近 chunk，避免依赖业务专名硬编码
  - 已验证：`2026-04-29` 已补通用 PDF 坐标表格抽取、表格续行合并、页内坐标排序与 source chunk 邻近补证据；公司业务 PDF 重新抽取约 `155800` 字符、生成 `319` 个 `=== Table` 表格块，`7.1` 高风险项跨页明细已落在 chunk `54/55` 中，可随标题与邻近 chunk 一起进入问答证据
  - 已验证：`2026-04-29` 本轮回归 `ArticleMarkdownSupportTests / SourceFileJdbcRepositoryTests / IngestNodeTests / SourceFileChunkJdbcRepositoryTests / AnswerGenerationServiceTests / QueryEvidenceRelevanceSupportTests / QueryTokenExtractorTests / CitationCheckServiceTests / CitationExtractorTests / CitationValidatorTests / QueryGraphOrchestratorTests / QueryGraphConditionsTests / DeepResearchRouterTests / PaginatedTextCleanerTests / SensitiveTextMaskerTests / PdfPositionedTextTableFormatterTests / PdfTextExtractorTests / SourceSearchServiceTests` 共 `124/124` 通过，`git diff --check` 通过
  - 进行中：`2026-04-29` 按用户要求清理历史验证 schema 后重跑公司业务 PDF 完整验证；先删除 `lattice_*` 验证 schema，保留默认 `lattice/public`，再用全新隔离 schema 重建模型配置、compile、query、入库与向量状态
  - 下一步：
    - 扩一组真实问法回归样本，至少覆盖“入口方式 / 配置解释 / 架构原因 / 运行态说明”四类
    - 逐题看真实返回是否仍会掉回 `FALLBACK/DEGRADED`
    - 优先继续扩充非 SWIP 的字段、状态码、枚举、配置键真实样本，验证通用精确标识链路不会退回业务词表补丁
    - 继续把“启动前需要配置什么”这类题从“步骤摘要”磨到“4 件事 / 必配项”级别的更精确回答
    - 让 `deep_research` 的向量检索在 embedding 不可用或上游 `502` 时自动降级，不再让解释题整题被拖死
    - 目标：主回归样本中的普通 Query 以 `LLM/SUCCESS` 为主，不再靠偶发命中

- [ ] 补一轮入库准确性回归
  - 现状：当前已经能稳定落库，但还没把“文章正文 / 来源摘要 / reviewStatus / 向量状态”做成固定验收样本
  - 进行中：`2026-04-28` 本轮固定 `docs/test` 目录 5 个真实样本：Markdown、DOCX、XLSX、PDF，先建立可重复问答与入库核验基线
  - 进行中：上一轮 compile 已在 `compile_new_articles` 半程状态中止，本轮改为 clean schema 全量重建后重新计数 source_files / articles / reviewStatus / vector index
  - 已验证：`2026-04-28` clean schema 与 Redis 验收前缀已清理，模型与向量配置已重新落库并通过探测，下一步重新触发 `docs/test` 全量 compile
  - 已验证：`2026-04-28` clean schema 全量 compile 成功，`jobId=501d109a-248e-43f0-9271-2b738b3d1f2e / persistedCount=5 / duration=719s`
  - 已验证：入库基线为 `source_files=5 / source_file_chunks=11 / articles=5 / article_chunks=12 / article_vector_index=5 / article_chunk_vector_index=12`
  - 已验证：文章审查状态为 `passed=2 / needs_human_review=3`；向量状态为 `dimensionsMatch=true / annIndexReady=true / annIndexType=hnsw`
  - 进行中：`2026-04-28` 已抽取 IAG、SWIP、买一赠一、支付重试、AI 最佳实践 5 类文档的关键事实，正在把它们作为 query 回归期望值逐题验证
  - 已验证：`2026-04-29` clean schema 重新全量 compile 成功，`jobId=ebb2c118-0fd8-44cf-bd67-5067b312abf8 / persistedCount=5`
  - 已验证：`2026-04-29` 当前入库计数为 `source_files=5 / source_file_chunks=25 / articles=5 / article_chunks=8 / article_vector_index=5 / article_chunk_vector_index=8`
  - 已验证：`2026-04-29` IAG 源文档 `content_text` 已提升到 `29209` 字符，`StatusCode` 与 `status:Success` 位于长文档尾部并已可被真实 Query 检索使用
  - 进行中：`2026-04-29` 本轮新增 Excel 样本 `docs/test/scenarios.xlsx`，准备核对表格内容抽取、文章摘要、reviewStatus 与向量状态
  - 已验证：`2026-04-29` `scenarios.xlsx` 隔离 compile 成功，`jobId=d839c46b-a02e-4ba6-8641-1400336a033e / persistedCount=1`；入库计数为 `source_files=1 / source_file_chunks=2539 / articles=1 / article_chunks=4 / article_vector_index=1`，文章 `review_status=passed`
  - 已验证：`2026-04-29` `scenarios.xlsx` 源文全文长度为 `5306005`，尾部结构化行 `sheet=步骤详情; row=1454; case_num=100997; step_index=9` 与 `step_name=查询asset_lock(9张券应全部解锁)` 均已入库；未生成 `case=100997` / `step=9; 第9步` 解析层别名；最大 source chunk 约 `4615` 字符，未再出现结构化索引被吞成百万字符级大 chunk
  - 待继续：`scenarios.xlsx` 向量状态为 `dimensionsMatch=true / indexedArticleCount=1`，但隔离 schema 中 `annIndexReady=false`，后续需要补一次 ANN 索引创建/状态核验，不把本轮 Excel 样本扩大成整体向量索引已收口结论
  - 进行中：`2026-04-29` 已开始纳入 `/Users/sxie/xbk/deep_evals`，本轮先挑代表性 PDF 子集验证 source 抽取、文章落库、reviewStatus、向量状态与真实问答表现
  - 已验证：`2026-04-29` `deep_evals` 代表性 PDF 子集在隔离 schema `lattice_deep_evals_20260429` compile 成功，`jobId=d7989c2e-19b9-4eb9-8e82-eb15f45542bf / persistedCount=6 / duration≈270s`
  - 已验证：`2026-04-29` `deep_evals` 入库计数为 `source_files=6 / source_file_chunks=36 / articles=6 / article_chunks=11 / article_vector_index=6 / article_chunk_vector_index=11`，文章 `review_status=passed=6`
  - 已验证：`2026-04-29` `deep_evals` 向量配置为 `BAAI/bge-m3 / 1024`，compile 后 `dimensionsMatch=true / indexedArticleCount=6`；补跑后台 vector rebuild 后 `annIndexReady=true / annIndexType=hnsw`
  - 待继续：`2026-04-29` `deep_evals` PDF 抽取文本与 gold Markdown 仍有明显长度差异，例如云计算指南 PDF 抽取约 `5468` 字符、gold 约 `16660` 字符；年报类 PDF 抽取也明显短于 gold，下一步需要做“PDF 抽取完整度 vs gold”专项对比，而不能只以问答命中代表抽取准确率已完全收口
  - 已验证：`2026-04-29` 公司业务 PDF 已用项目 PDFBox 抽取器复验，清理后仍保留 `75` 个页标记、约 `87887` 字符，`pdf#print` 页脚已移除，接口路径、`businessTypeCode=26` 与 `1301` 等精确事实仍可检索
  - 已验证：`2026-04-29` 公司业务 PDF 隔离入库复验完成：`source_files=1 / source_file_chunks=45 / articles=1 / article_chunks=2 / article_vector_index=1 / article_chunk_vector_index=2`，文章 `review_status=needs_human_review`；向量状态 `dimensionsMatch=true / annIndexReady=true / annIndexType=hnsw / indexedArticleCount=1`，正文已从合法 YAML frontmatter 开始，模型修复说明性前言不再污染持久化文章
  - 下一步：
    - 固定 3 到 5 个真实样本目录，覆盖 README、配置文档、后台 HTML、PDF、Excel
    - 每轮至少核对：
      - 文章数是否符合预期
      - 关键文章摘要是否抓到真正事实，而不是页头/概述废话
      - `reviewStatus` 是否与实际内容质量大致一致
      - `indexedArticleCount / articleCount` 是否补齐

- [ ] 收口 `compile_new_articles / review_articles / fix_review_issues` 的长耗时
  - 现状：当前真实慢点仍主要落在这三段串行 LLM 调用
  - 现状：上游 `502` 重试会明显放大单次作业总耗时
  - 下一步：
    - 先量化三段各自耗时占比
    - 评估“按文章并发”是否会带来可控收益
    - 如果暂不并发，至少补“长节点内心跳刷新”或分段续租，避免再次出现 `COMPILE_STALE_TIMEOUT`

- [ ] 补一轮新的真实 compile 基线
  - 当前旧基线：约 `1208s`
  - 当前新基线：上一轮中途受热重载与上游 `502` 干扰，不能直接作为最终口径
  - 下一步：
    - 在稳定实例上重新跑同一批 9 文件样本
    - 保留 `jobId`、`startedAt`、`finishedAt`
    - 明确给出“优化前 / 优化后”秒数对比

- [x] 收口 Query fallback 的“引用泄漏 / 错位选句 / 成功口径偏松”
  - 现象：`2026-04-28` 真实问法“这个项目的运行流程是什么样的呢”最新审计仍返回 `generationMode=FALLBACK / answerOutcome=SUCCESS / reviewStatus=PASSED`
  - 现象：正文结论只摘到 README 开头“如果你只想先判断这个项目值不值得继续看，先抓住 4 句话”，没有命中真正的“系统真正的主链路”
  - 现象：SOURCE chunk 命中会把 `README.md#0` 这类内部 chunk key 直接渲染成 `[[README.md#0]][→ README.md]`，前端又不会消费该 citation 语法，观感异常
  - 已完成：
    - `AnswerGenerationService / QueryEvidenceRelevanceSupport` 已禁止 SOURCE 命中再拼 article 风格 `[[...]]`
    - 已去掉 flow 选句里的文案级硬编码，改成仅依赖结构 / 信号的通用判定
    - 已补 flow 类问题的相关性放行、主链路句优先与 fallback 成功判定收紧
    - 已新增同题型回归样本，覆盖 source-only citation、“运行流程”选句、媒体/表头过滤、同文件多 chunk 排序与题型分流
  - 验收：当前 `AnswerGenerationServiceTests` 定向回归已扩至 `27/27` 通过
  - 验收：本地按 `jdbc` 口径启动后，真实 `POST /api/v1/query` 问“这个项目的运行流程是什么样的呢”，返回 `queryId=c5bedfc2-b07f-4187-8a0f-5587b5a2163f`
  - 验收：真实返回已不再出现 `[[README.md#0]][→ README.md]`，正文改为命中 `compile graph / query graph` 主链路句

---

## P1 优化项

- [ ] 把结构化输出从 `json_object` 进一步升级到 `json_schema`
  - 目的：减少“虽然是 JSON，但字段不齐、枚举值不稳”的情况
  - 依赖：当前 Spring AI `OpenAiChatOptions` 已支持 `ResponseFormat.Type.JSON_SCHEMA`

- [ ] 为 OCR / 运行态问答补独立测试
  - 目标：至少新增一条 `QueryFacadeService` 或 `QueryController` 测试
  - 目标：覆盖 `queryId=null` 与“不会新增 pending”的断言

- [ ] 把 `annIndexReady=true` 纳入向量重建回归
  - 目标：重建后不仅看 `indexedArticleCount`
  - 目标：同时确认 `annIndexType` 非空

---

## 下一步建议顺序

1. 先补一轮新的真实 compile 基线，确认现在到底慢在哪一段
2. 同时固定一组最小真实样本，持续回归入库准确性和 Query 准确性
3. 再决定 Compile 是优先做“按文章并发”，还是优先做“续租/心跳更细粒度”
4. 最后再把 `json_schema` 收上去，争取把结构化输出稳定性再抬一档
