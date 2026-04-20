# OpenAI 配置回归改造清单

## 1. 目标

基于 **2026-04-20** 这轮真实回归结果，围绕当前默认验收口径补齐一轮可上线的改造与优化，确保项目在 **只使用 OpenAI/Codex + 向量检索、不开 OCR、不开 Claude 专项验证** 的前提下，能够稳定完成：

- 管理端配置
- 非 OCR 资料上传与编译
- reviewer 参与的 compile / query 主链路
- Web / CLI / MCP 统一可用
- 中文问题下的稳定检索与问答
- 管理端与问答端的前端体验优化

## 2. 本轮基线结论

本轮已经真实确认：

- OpenAI 新配置可用，`gpt-5.4` chat 与 `BAAI/bge-m3` embedding 测试成功。
- 非 OCR 主链已跑通，Web / CLI / MCP 均可访问。
- reviewer 已真实参与 compile，不是空跑。

本轮需要优先处理的问题：

- P0：向量检索当前不可用。
  - 现状：embedding profile 维度为 `1024`，库表列为 `vector(1536)`，`indexedArticleCount=0`。
  - 影响：向量索引无法建立，相关问答实际退化为非向量召回。
- P1：架构原因类中文问答召回不稳。
  - 现状：明明存在 `ADR-012-order-to-inventory-via-mq.md`，但“为什么订单服务不直接同步调用库存服务，而要走消息队列？”仍回答“证据不足”。
  - 影响：复杂原因解释类问题命中率不稳定，尤其是 ADR / 架构决策类知识。
- P1：前端信息架构仍有优化空间。
  - 现状：`/admin` 首屏 FAQ 比重偏高，`/admin/ask` 的答案与引用联动偏弱，`/admin/developer-access` 页面定位还不够独立，移动端首屏核心动作偏靠下。

## 3. 范围

### 3.1 后端范围

- 配置与 schema：
  - `src/main/resources/application.yml`
  - `src/main/resources/db/migration/V1__baseline_schema.sql`
- 向量与检索：
  - `src/main/java/com/xbk/lattice/query/service/VectorSchemaInspector.java`
  - `src/main/java/com/xbk/lattice/query/service/QueryVectorConfigService.java`
  - `src/main/java/com/xbk/lattice/query/service/ArticleVectorIndexService.java`
  - `src/main/java/com/xbk/lattice/query/service/ArticleChunkVectorIndexService.java`
  - `src/main/java/com/xbk/lattice/query/service/KnowledgeSearchService.java`
  - `src/main/java/com/xbk/lattice/query/service/QueryRetrievalSettingsService.java`
  - `src/main/java/com/xbk/lattice/query/service/FtsSearchService.java`
  - `src/main/java/com/xbk/lattice/query/service/RefKeySearchService.java`
  - `src/main/java/com/xbk/lattice/query/graph/QueryGraphDefinitionFactory.java`
- 管理端接口：
  - `src/main/java/com/xbk/lattice/admin/service/AdminVectorIndexMaintenanceService.java`
  - `src/main/java/com/xbk/lattice/api/admin/AdminVectorIndexController.java`
  - `src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalConfigController.java`

### 3.2 前端范围

- `src/main/resources/static/admin/index.html`
- `src/main/resources/static/admin/management.js`
- `src/main/resources/static/admin/ask.html`
- `src/main/resources/static/admin/ask.js`
- `src/main/resources/static/admin/settings.html`
- `src/main/resources/static/admin/settings-page.js`
- `src/main/resources/static/admin/developer-access.js`
- `src/main/resources/static/admin/admin.css`

### 3.3 验收约束

- 默认使用 JDK `21`
- 默认复用现有 Docker 容器 `vector_db` 与 `redis`
- 默认使用 OpenAI 路由完成 compile / query / reviewer 回归
- 本轮不启 OCR，不将图片资料纳入主验收样本
- 本轮不做 Claude 专项回归，除非后续另开任务明确要求

## 4. 实施清单

- [x] D1 新增改造清单文档并作为唯一进度台账
  - 验收：已创建 `docs/plans/OpenAI配置回归改造清单.md`，后续执行与验收统一在此回写。

- [x] D2 固化本轮真实回归结论与改造优先级
  - 验收：已将 OpenAI 配置可用、reviewer 已真实参与、向量维度不一致、中文 ADR 召回不稳、前端体验问题等结论写入本清单。

- [x] D3 修复向量维度不一致，恢复可用向量索引
  - 范围：收敛 embedding profile 维度、数据库向量列维度与管理端校验口径。
  - 包含：
    - 明确首版默认 embedding 维度基线。
    - 修正向量索引表结构与当前默认 embedding profile 的一致性策略。
    - 完成索引重建并确认 `indexedArticleCount > 0`。
  - 验收：
    - 已在代码中补齐“重建前自动对齐向量列维度”的修复逻辑，覆盖 `article_vector_index` 与 `article_chunk_vector_index`。
    - 已新增集成测试覆盖 `1024` 维 embedding profile + `1536` 维 schema 的修复路径，并验证重建后状态切换为 `dimensionsMatch=true`。
    - 已在真实运行实例 `http://127.0.0.1:18090` 上手动验证：向量列修正为 `1024` 后，`indexedArticleCount=7`、`indexedChunkCount=7`。
  - 验收：向量状态页不再出现“embedding profile 维度与 schema 维度不一致”，且文章级 / chunk 级向量索引均可成功建立。

- [x] D4 补齐向量配置防呆与重建引导
  - 范围：避免后续再次出现“配置能保存，但索引永远建不起来”的静默失败。
  - 包含：
    - 保存向量配置前增加维度兼容性校验或明确阻断。
    - 在管理端状态中给出可操作的修复提示，而不只是技术报错。
    - 当 embedding profile 切换后，明确提示是否需要重建索引。
  - 已完成：已补“向量配置变更 / 向量索引重建后自动清空 query cache”，避免向量修复后继续命中旧缓存答案。
  - 验收：管理员设置页已补齐向量状态摘要、兼容性提示、profile 预览、重建按钮与检索调参区；`mvn -q -s .codex/maven-settings.xml -Dtest=AdminPageControllerTests,AdminVectorIndexControllerTests,AdminQueryRetrievalConfigControllerTests test` 已通过。
  - 验收：已在 `18090` 实例的 `/admin/settings` 桌面端 / 移动端真实页面检查中确认向量维护区可直接看到兼容性提示与下一步动作；当前 `/api/v1/admin/vector/status` 返回 `configuredProviderType=openai`、`configuredModelName=BAAI/bge-m3`、`embeddingColumnType=vector(1024)`、`schemaDimensions=1024`、`dimensionsMatch=true`、`indexedArticleCount=9`。
  - 验收：管理员在 UI 上即可看懂“是否兼容、为什么不兼容、下一步该做什么”。

- [x] D5 提升架构原因类中文问答的召回稳定性
  - 范围：优先解决 ADR / 架构决策类问题在中文自然表达下召回不足的问题。
  - 包含：
    - 复盘当前 query rewrite、FTS、ref key、向量召回与融合权重。
    - 针对“为什么 / 原因 / 为什么不直接”类问题补齐更稳定的召回策略。
    - 重点验证 ADR、runbook、设计说明等解释性资料的命中率。
  - 已验证：原先失败的问法“为什么订单服务不直接同步调用库存服务，而要走消息队列？”在向量恢复、query cache 清理并恢复 LLM 连接后，已能在 `18090` 实例上返回基于 ADR 的正确回答。
  - 已完成：已确认“有召回但回答当前证据不足/暂无法确认”的负向结果仍可能被缓存；本轮已补“负向问答结果不缓存 + compile 成功后自动失效 query cache”。
  - 验收：已在 Query Graph 缓存写回节点增加负向答案拦截；`CompilePipelineService` 与 `ArticlePersistSupport` 在成功写入文章后会自动清空 query cache。
  - 验证：`mvn -q -s .codex/maven-settings.xml -Dtest=QueryGraphOrchestratorTests,CompilePipelineServiceTests,CompilePipelineVectorIndexingTests test` 已通过。
  - 验收：已在 HTTP `/api/v1/query` 与 CLI remote `query` 下重复验证 ADR 问题“为什么订单服务不直接同步调用库存服务，而要走消息队列？”，均能稳定返回引用 `ADR-012-order-to-inventory-via-mq.md` 的正确回答。
  - 验收：已补充验证 `incident-runbook.md + postmortem.md`、`PaymentRetryPolicy.java + analyze.json + gateway-config.yaml`、`warehouse-sync.md + 两份 xlsx` 三类复杂中文问题，确认当前回归口径下复杂解释类 / 联合资料类问答均能给出与证据一致的回答，不再停留在“证据不足”的错误稳态。
  - 验收：对 `ADR-012-order-to-inventory-via-mq.md` 相关中文问法能稳定命中并给出有引用的回答。

- [x] D6 优化 `/admin/ask` 的答案与证据联动体验
  - 范围：增强用户对“回答从哪来、证据够不够、该不该信”的判断力。
  - 包含：
    - 强化答案区与引用区的视觉联动。
    - 优化引用卡片的信息层级，让用户更容易看懂“哪条引用支撑了哪段回答”。
    - 在无稳定证据时，明确区分“回答失败”“证据不足”“建议换问法”。
  - 验收：`/admin/ask` 已切到“结果态 + 回答依据 + 证据分层来源”结构，并补齐 `reviewStatus`、引用数量、证据不足提示与 `answer-markdown/source-section/source-card-primary` 样式；`AdminPageControllerTests` 已通过。
  - 验收：已在 `18090` 实例上用复杂问题“仓储补推要用哪个模板，固定 topic 和 SLA 分别是什么？两份 xlsx 与 warehouse-sync.md 的信息是否一致？”完成真实问答，并补齐结果态截图 `/tmp/lattice-screens/ask-warehouse-desktop.png` 与 `/tmp/lattice-screens/ask-warehouse-mobile.png`；页面可直接看到结果类型、证据状态、复核状态、回答依据、直接来源卡片与补充检索命中分层。
  - 验收：普通用户在问答结果页可以快速判断回答可信度，并知道下一步操作。

- [x] D7 优化 `/admin/developer-access` 与 `/admin` 首页信息架构
  - 范围：让普通用户页聚焦核心动作，让开发者页体现独立定位。
  - 包含：
    - 降低 `/admin` 首页 FAQ 首屏权重，突出上传、编译、查看状态等主动作。
    - 将 `/admin/developer-access` 从“设置子页感”提升为更独立的开发者接入页。
    - 统一普通用户入口与开发者入口的页面定位与文案边界。
  - 验收：已使用 Playwright + 本机 Chrome 对 `/admin` 与 `/admin/developer-access` 完成真实桌面 / 窄屏截图检查；普通用户首页首屏已聚焦“导入资料即可开始问答”，开发者页首屏明确承载 CLI / HTTP API / MCP 接入，而不是设置页附属区。
  - 验收：普通用户进入 `/admin` 后首屏先看到核心动作；开发者进入 `/admin/developer-access` 后能明确这是接入页而非设置页补充区。

- [x] D8 优化移动端首屏与导航占用
  - 范围：减少移动端首屏被导航、说明卡和辅助信息挤压的问题。
  - 包含：
    - 收敛移动端顶部导航与说明区高度。
    - 提升首屏主动作与核心状态的可见性。
    - 校验 `/admin`、`/admin/ask`、`/admin/settings`、`/admin/developer-access` 在窄屏下的可用性。
  - 验收：已使用 Playwright + 本机 Chrome 以 `390x844` 窄屏视口对 `/admin`、`/admin/ask`、`/admin/settings`、`/admin/developer-access` 完成真实截图检查；四个页面首屏均可直接看到主动作或核心结果态，无需先明显下滑。
  - 验收：移动端进入各主页面后，无需明显下滑即可看到核心动作或核心结果。

- [x] D9 完成真实回归并回写验收结果
  - 验收范围至少覆盖：
    - OpenAI 连接、chat model、embedding model 测试
    - 非 OCR 样本上传与 compile
    - reviewer 参与 compile / query
    - 向量索引建立与重建
    - 中文 ADR 原因类问答
    - `/admin`、`/admin/ask`、`/admin/settings`、`/admin/developer-access` 桌面端与移动端检查
    - CLI remote 与 MCP 基础可用性
  - 验收标准：
    - 主链路可重复跑通
    - 不再依赖人工解释“为什么这里其实没坏、只是降级”
    - 前端首屏主动作、证据感与页面边界较当前基线更清晰
  - 已完成：确认失败作业的 compile 三角色快照均冻结为 OpenAI `gpt-5.4`，当前阻塞不属于绑定漂移或误走 Claude。
  - 已完成：已重加密回归 schema 的 OpenAI / SiliconFlow 连接并恢复 `18090` 实例，`/actuator/health`、`/api/v1/admin/llm/connections`、`/api/v1/admin/llm/models`、`/api/v1/admin/llm/bindings` 与 chat/embedding 测试均已通过，确认仍只走 OpenAI `gpt-5.4` + SiliconFlow `BAAI/bge-m3`。
  - 已完成：`OpenAiCompatibleLlmClient` 已从 `HttpURLConnection` 切到 JDK `HttpClient`，并把可恢复 EOF/5xx 重试上限从 3 次提升到 5 次；`mvn -q -s .codex/maven-settings.xml -Dtest=OpenAiCompatibleLlmClientTests test` 已通过，覆盖“三次断流后第四次成功”场景。
  - 已完成：失败作业在补齐 OpenAI snapshot 路径的稳定重试后，第 3 次真实复跑已成功跑完整条 compile 主链；作业 `jobId=532a6eee-758c-434e-863a-a73453021b11` 最终 `status=SUCCEEDED`、`persistedCount=9`，完整经过 `compile_new_articles -> review_articles -> fix_review_issues -> review_articles -> persist_articles -> rebuild_article_chunks -> refresh_vector_index -> finalize_job`。
  - 已完成：向量索引已在真实实例上恢复，`POST /api/v1/admin/vector/rebuild {"truncateFirst":true,"operator":"codex"}` 返回 `indexedArticleCount=9`、`indexedChunkCount=9`；当前 `/api/v1/admin/vector/status` 返回 `embeddingColumnType=vector(1024)`、`schemaDimensions=1024`、`dimensionsMatch=true`、`indexedArticleCount=9`。
  - 已完成：四类最复杂非 OCR 问答已完成真实验收，覆盖 `ADR` 原因类、`incident-runbook.md + postmortem.md` 联合题、`PaymentRetryPolicy.java + analyze.json + gateway-config.yaml` 联合题、`warehouse-sync.md + 两份 xlsx` 联合题；reviewer 已真实参与 compile / query，问答结果与证据一致，`reviewStatus` 分别为 `ISSUES_FOUND / ISSUES_FOUND / ISSUES_FOUND / PASSED`。
  - 已完成：`/admin`、`/admin/ask`、`/admin/settings`、`/admin/developer-access` 已完成桌面端 / 移动端真实截图检查；其中 `/admin/ask` 额外补齐复杂问答结果态截图 `/tmp/lattice-screens/ask-warehouse-desktop.png` 与 `/tmp/lattice-screens/ask-warehouse-mobile.png`。
  - 已完成：CLI remote 已真实跑通 `status / search / query / vault-export`；本轮额外修复了 `search` 远程反序列化 `SearchHitResponse` 失败问题，并新增 `LatticeHttpClientTests.shouldDeserializeSearchResponse` 作为回归保护。
  - 已完成：MCP raw HTTP 已按 `initialize -> tools/list -> tools/call(lattice_status) -> tools/call(lattice_query)` 完整验收，`Accept` 与 `Mcp-Session-Id` 头均按要求携带；当前 `tools/list` 返回 `31` 个工具，`lattice_query` 可真实返回结构化 `answer / queryId / reviewStatus / sourceCount`。
  - 结论：本轮 OpenAI/Codex chat + SiliconFlow embedding 口径下，最复杂非 OCR 主链已完成 compile / query / reviewer / 向量重建 / Web / CLI / MCP 全量验收，且明确未启 OCR、未走 Claude。

- [x] D10 产出 ChatClient + Advisor 渐进式改造技术方案
  - 范围：面向当前 LLM 调用体系，输出一份可供架构评审与后续实施直接使用的详细方案文档。
  - 包含：
    - 现状架构与问题拆解
    - 目标架构、组件职责与 Advisor 链设计
    - Query / reviewer / compile / governance 分阶段迁移策略
    - 缓存、结构化输出、日志、重试、观测与回滚方案
  - 验收：已新增技术方案文档 `docs/LLM调用迁移到ChatClient-Advisor渐进式改造技术方案.md`，覆盖现状分析、目标架构、Advisor 链、结构化输出、缓存语义、分阶段迁移、风险与回滚，可直接交由 Claude 审查。
  - 补充：已按本轮评审前要求补齐 `ChatClient` 与 Spring AI / Spring AI Alibaba 的归属说明，并新增《全链路日志与追踪设计》章节，明确 `compileJobId / sourceId / queryId / traceId` 的职责、日志字段规范、编译/问答排障 SOP 与标准日志样例。
  - 修订：已根据 Claude 评审补强双层缓存、`queryId` 回传事实、ChatClient 动态构造 Spike、日志独立 phase、Advisor 上下文传递与新旧执行路径分支设计。
  - 二轮复审：已继续吸收二次复审剩余项，补齐并收敛了 L1 write-suppression 的路径 B、reviewer 路径缓存治理、fallback `answerOutcome` 归属、`queryId` phase 归属统一、Phase 0.5 可执行验收、`traceId` 技术来源与 Spike 失败后备路线。
  - 三轮复审：已按 Claude 第三轮建议补齐 3 处最小修订，明确 `PromptCacheCoordinator` 在 `chatClientExecutor` 的调用位置、Phase 0 Spike 的真正验证重点，以及 `QueryResponse.queryId` 与日志链路一起在 Phase 0.5 发布的原因。
  - 三轮补充：已继续补齐 `AnswerGenerationService` 三条 `non-LLM` 路径的 `answerOutcome / generationMode / modelExecutionStatus` 映射，避免 Phase 1 实施时出现部分分支无结构化语义的问题。
  - 当前状态：主方案文档已可发起 Claude 三次复审，本轮额外产出复审提示词文件 `docs/Claude三次复审提示词.md`。

- [x] D11 启动 `ChatClient + Advisor` Phase 0 Spike
  - 范围：在不升级 Spring AI 基线、不中断现有主链的前提下，验证 OpenAI/Codex 路径是否可在运行时动态构造 `OpenAiChatModel + ChatClient`，并验证最小 Advisor 上下文传递。
  - 目标：
    - 收敛 bootstrap 路径与 snapshot 路径的当前差异，不重复验证已经成立的前提。
    - 最小化验证“运行时多路由动态构造”这一真正未知点。
    - 为 Phase 0 结束时形成 go / no-go 结论与后备路线切换依据。
  - 拆解：
    - D11-1 核对当前调用路径与 Spike 边界
      - 验收：明确记录 `bootstrap = ChatModelLlmClient(OpenAiChatModel)`、`snapshot-backed = OpenAiCompatibleLlmClient` 的当前事实，确认 Spike 核心问题收敛为“能否绕过 Spring Boot AutoConfig 动态实例化多个 `OpenAiChatModel`”。
    - D11-2 完成最小动态构造样例
      - 验收：在 Spring AI `1.1.2` 基线下，以运行时 `baseUrl + apiKey + model + timeout/options` 手工构造 `OpenAiChatModel`，再构造 `ChatClient`，并成功完成一次真实调用。
    - D11-3 验证最小 Advisor 链与 `LlmInvocationContext`
      - 验收：至少 1 个自定义 Advisor 能读取到 `scene / purpose / scopeId`，并确认 per-request context 不是静态写死在 registry 中。
    - D11-4 验证多实例隔离与 registry 设计是否成立
      - 验收：至少验证两组不同运行时路由参数不会错误复用同一个 `ChatModel/ChatClient`；若无法完整做双 provider，至少完成双 `baseUrl/model` 隔离验证。
    - D11-5 形成 Spike 结论与后续决策
      - 验收：输出明确结论：
        - 是否可进入 Phase 0.5 / Phase 1
        - 若可进入，后续应落在哪个组件承接动态构造与 Advisor 链
        - 若不可进入，是否切回文档定义的 legacy executor 后备路线
  - 验收：已核对当前路径事实：`LlmGateway` bootstrap 构造器直接注入 `OpenAiChatModel` 并包装为 `new ChatModelLlmClient(openAiChatModel)`；`LlmClientFactory` 的 snapshot 路径仍动态返回 `OpenAiCompatibleLlmClient`。
  - 验收：已新增 `src/test/java/com/xbk/lattice/llm/service/ChatClientDynamicSpikeTests.java`，通过本地 OpenAI stub 验证运行时 `OpenAiApi -> OpenAiChatModel -> ChatClient` 动态构造可行。
  - 验收：已在 Spike 中验证最小 `RecordingAdvisor` 可读取 `ChatClientRequest.context()` 中的 `scene / purpose / scopeId`，且同一个 ChatClient 在两次调用中可透传不同 per-request context。
  - 验收：已验证相同运行时路由可复用同一动态 ChatClient 句柄，不同 `baseUrl/apiKey/model/temperature/maxTokens` 会生成隔离实例，不会串用。
  - 验证：`mvn -q -s .codex/maven-settings.xml -Dtest=ChatClientDynamicSpikeTests test` 已通过。
  - 结论：Phase 0 Spike 可进入 go 路线，后续可在独立动态 `ChatClient` registry / executor 组件中承接运行时构造与 Advisor 链；若后续实施受阻，仍保留当前 `OpenAiCompatibleLlmClient` 作为 legacy executor 后备路线。

## 5. 建议执行顺序

建议按以下顺序推进：

1. 先做 D3-D4，先把向量恢复到真正可用。
2. 再做 D5，解决中文架构原因类问答命中不稳。
3. 然后做 D6-D8，完成问答页、首页与开发者页的前端优化。
4. 最后做 D9，重新跑一轮完整真实回归并沉淀最终结论。

原因：

- D3-D5 直接决定“问答是否真的可用”，优先级高于页面美化。
- D6-D8 影响用户理解成本与产品感知，适合在检索链路稳定后统一打磨。
- D9 必须放在最后，避免每改一点就重复跑整套全链路。

## 6. 当前断点

- 当前阶段：D1-D11 已完成，本轮清单已全部执行完毕
- 已完成：本轮已按 OpenAI/Codex chat + SiliconFlow embedding 口径完成最复杂非 OCR 主链真实回归，覆盖配置恢复、compile / reviewer / query、向量重建、复杂中文问答、`/admin` / `/admin/ask` / `/admin/settings` / `/admin/developer-access` 桌面端与移动端、CLI remote、MCP raw HTTP；过程中补齐了 `OpenAiCompatibleLlmClient` EOF/5xx 稳态重试与 CLI remote `search` 的 Jackson 反序列化兼容修复，并已通过对应测试
- 下一步：若继续推进新一轮迭代或收口提交，请以本清单为完成记录，另建新的执行清单后再开始
