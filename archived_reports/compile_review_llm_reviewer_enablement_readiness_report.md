# Compile Review LLM Reviewer 启用前置条件只读分析报告

生成时间：2026-05-17  
执行角色：agentB  
任务边界：只读分析；不改源码、不改配置、不启用 LLM reviewer、不运行测试、不运行 compile。

## 1. 结论

等 agentA 的 fail-closed 修复完成并通过验证后，当前系统才具备“小流量验证 LLM reviewer”的基本前提，但仍不建议直接全局开启。

当前最关键判断：

| 项目 | 判断 |
|---|---|
| `lattice.llm.review-enabled` 默认值 | 默认 `false`，来源为 `src/main/resources/config/lattice-llm.yml` 和 `LlmProperties.reviewEnabled=false` |
| 当前关闭时行为 | `ArticleReviewerGateway` 走 `RuleBasedArticleReviewer`，只能称为规则 / 结构审查，不是 LLM 内容审查 |
| 开启后调用身份 | scene=`compile`，agentRole=`reviewer`，purpose=`compile-review` |
| reviewer 输入材料 | 草稿 article 全文 + 原始 source material 采样，source 在 gateway 内截断到 12000 字符 |
| reviewer 输出要求 | JSON：`approved`、`rewriteRequired`、`riskLevel`、`issues`、`userFacingRewriteHints`、`cacheWritePolicy` |
| reviewer binding | 历史治理报告曾观察到 `compile.reviewer.gpt-5.5`；本轮未做 live DB/API 核验，启用前必须重新确认当前后台绑定 |
| fail-closed | agentA 报告显示已修：启用状态下 LLM 异常 / 解析失败不再静默 rule-based pass；但本轮未复测，需等待其结果进入验证基线 |
| persist/query gate | 质量台账显示已完成：persist 只入 `passed`；article-backed query 只查 `passed + ACTIVE` |

## 2. 当前 reviewer 配置现状

### 2.1 配置来源

`src/main/resources/config/lattice-llm.yml` 当前关键项：

| 配置 | 当前默认 |
|---|---|
| `lattice.llm.compile-model` | `${LATTICE_LLM_COMPILE_MODEL:openai}` |
| `lattice.llm.reviewer-model` | `${LATTICE_LLM_REVIEWER_MODEL:anthropic}` |
| `lattice.llm.config-source` | `${LATTICE_LLM_CONFIG_SOURCE:hybrid}` |
| `lattice.llm.bootstrap-enabled` | `${LATTICE_LLM_BOOTSTRAP_ENABLED:true}` |
| `lattice.llm.review-enabled` | `${LATTICE_LLM_REVIEW_ENABLED:false}` |
| `lattice.llm.chat-client.compile-review-enabled` | `${LATTICE_LLM_CHAT_CLIENT_COMPILE_REVIEW_ENABLED:true}` |
| `lattice.llm.chat-client.governance-json-enabled` | `${LATTICE_LLM_CHAT_CLIENT_GOVERNANCE_JSON_ENABLED:true}` |

代码默认值与配置一致：`LlmProperties.reviewEnabled=false`、`reviewerModel=anthropic`、`configSource=hybrid`、`bootstrapEnabled=true`。

### 2.2 `.claude/t1.md` 模型建议

`.claude/t1.md` 中包含凭据类信息，本报告只引用非敏感模型选择结论，不复述任何 key / token / base URL。

| 来源 | 对 reviewer 的影响 |
|---|---|
| `usage_routing.review` | 推荐 `mimo-v2.5-pro` 作为 review 模型 |
| `current_acceptance_baseline_models.query_reviewer` | 推荐 `gpt-5.5` |
| 模型规则 | 日常 query / answer / reviewer / rewrite 默认 OpenAI-compatible `gpt-5.5`；后台配置为空时只允许按 acceptance baseline 恢复 |
| JSON schema 能力 | `.claude/t1.md` 记录 OpenAI-compatible `gpt-5.5/gpt-5.4/gpt-5.3-codex/...` 已验证支持 JSON schema |
| 风险提示 | Mimo 与 OpenAI-compatible 通道都记录过本机 TLS 默认校验可能失败；诊断绕过证书校验不能作为生产方案 |

启用 LLM reviewer 前，不能只看 `.claude/t1.md` 的推荐模型名，还必须确认后台当前绑定、连接、模型 profile、TLS、JSON 输出稳定性都可用。

### 2.3 后台 model binding 能力

后台 LLM 配置接口支持：

| 能力 | 读取结果 |
|---|---|
| 列出绑定 | `GET /api/v1/admin/llm/config/bindings` |
| 新增绑定 | `POST /api/v1/admin/llm/config/bindings` |
| 更新绑定 | `PUT /api/v1/admin/llm/config/bindings/{id}` |
| 删除绑定 | `DELETE /api/v1/admin/llm/config/bindings/{id}` |
| compile 场景角色 | `writer`、`reviewer`、`fixer` |

`ExecutionLlmSnapshotService` 会在 compile job scope 下冻结 `agent_model_bindings`。因此小流量验证时应优先确认 scope snapshot 命中的实际 reviewer route，而不是只看 properties bootstrap 默认值。

## 3. 是否已有 reviewer model binding

基于历史报告，曾观察到当前有效绑定：

| 字段 | 历史观察值 |
|---|---|
| route label | `compile.reviewer.gpt-5.5` |
| provider | `openai_compatible` |
| model | `gpt-5.5` |

但本轮按用户要求没有做 DB/API live 核验，所以结论只能是：

1. 代码和后台接口层面已经支持 compile reviewer binding。
2. 历史报告显示曾有明确 reviewer binding。
3. 小流量启用前必须重新读取当前后台绑定，确认 `scene=compile`、`agentRole=reviewer`、`enabled=true`，且 primary model profile 与 provider connection 均启用。
4. 不建议依赖 `lattice.llm.reviewer-model=anthropic` 这个 bootstrap 默认值上线；它只是兜底属性，不等于已验证的 reviewer route。

## 4. reviewer 调用链路

当前 StateGraph 主链已有闭环骨架：

```text
compile_new_articles
  -> review_articles
      -> passed: acceptedArticlesRef
      -> issues + autoFix 未超轮次: fix_review_issues
      -> 无可修 issue / 超轮次 / fail-closed: needs_human_review
  -> fix_review_issues
      -> review_articles
  -> persist_articles
      -> 仅 persist review_status=passed
```

关键代码语义：

| 环节 | 当前语义 |
|---|---|
| `DefaultReviewerAgent` | 调用 `ArticleReviewerGateway`，route role 固定为 `reviewer` |
| `ArticleReviewerGateway.review(...)` | `review-enabled=false` 时走 rule-based；开启时调用 LLM |
| LLM 调用参数 | scene 默认 `compile`，agentRole 默认 `reviewer`，purpose=`compile-review` |
| prompt 材料 | `COMPILED ARTICLE` + `ORIGINAL SOURCE MATERIALS (sample)` |
| source 截断 | gateway 对 source material 截断到 12000 字符 |
| 解析器 | `ReviewResultParser` 先解析 JSON，再做文本 issue 救援，最后 parse failed |
| 分区策略 | pass -> `passed`；有 issue 且可修 -> `pending/fixable`；否则 -> `needs_human_review` |
| fixer 后 | `fix_review_issues` 后会回到 `review_articles` 重新 review |
| persist gate | `PersistArticlesNode` 只保留 `review_status=passed` |

补充风险：`compile-review` 目的会走 ChatClient 路径，但当前严格 JSON response_format 的自动注入只针对 query structured 目的；reviewer 仍主要依赖 prompt 要求模型输出 JSON。因此模型的 JSON 稳定性必须在小流量里单独验证。

## 5. 启用前置条件清单

### 5.1 必须满足

| 条件 | 必须检查什么 |
|---|---|
| fail-closed 已完成并验证 | `review-enabled=true` 时 LLM 异常、超时、解析失败不得回退成 rule-based pass；应产生非 pass 并进入 `needs_human_review` 或 fixable |
| reviewer binding 明确 | 后台当前存在 enabled 的 `compile/reviewer` binding，primary model profile 与 provider connection 都 enabled |
| 模型能稳定输出 JSON | 至少验证 reviewer prompt 返回可被 `ReviewResultParser` 解析的 JSON；非 JSON 应 fail-closed |
| TLS / 连接稳定 | 不能使用“跳过证书校验”的诊断方式作为生产启用前提 |
| route 可观测 | 后台 / compile job step 能看出 `reviewRoute` 不是 `rule-based`，并能区分 LLM route 与 fallback |
| status 可观测 | 后台可见 `acceptedCount/pendingReviewCount/needsHumanReviewCount`、fix attempts、最终 review status |
| persist gate 通过 | 非 `passed` article 不进入正式 `articles/article_chunks/vector` 链路 |
| query gate 通过 | article-backed query 只召回 `review_status='passed' AND lifecycle='ACTIVE'` |
| 小流量隔离 | 使用临时库或隔离源文件，不清主库、不污染当前 clean 验收库 |
| 成本预算 | reviewer input/output pricing、timeout、max tokens、预算上限可解释 |

### 5.2 任一不满足则不能开启

| 不能开启的情况 | 原因 |
|---|---|
| agentA fail-closed 未通过验证 | LLM 异常可能被误报为审查通过 |
| reviewer binding 缺失或指向禁用模型 | scope snapshot 可能落到 bootstrap 或直接失败 |
| 模型不支持稳定 JSON / 经常输出不可解析文本 | 会造成大量 parse failed 或误判 |
| 当前后台看不到实际 route 或 fallback reason | 无法向用户解释“是不是 LLM 审查” |
| persist gate / query visibility gate 未在当前基线通过 | 未审查通过内容可能入库或被 query 召回 |
| 使用 Mimo 但 TLS / 客户端兼容性未解决 | `.claude/t1.md` 已记录相关风险 |
| 在主 clean 库上直接做首次启用验证 | 会污染归因，且不利于回滚 |
| 通过改 repo 配置全局打开 | 不符合小流量、可回退、可归因原则 |

## 6. 小流量验证建议

下一轮不应直接改源码。建议分两步，先只读确认，再隔离验证。

### 6.1 最小只读确认

目标：不启用 reviewer、不跑 compile，只确认“如果启用，路由是否准备好”。

检查项：

1. 读取 agentA fail-closed fix result report，并确认验证报告已通过。
2. 读取后台 bindings：确认 `compile/reviewer` 当前 enabled，模型 profile 为 CHAT，连接 enabled。
3. 读取后台 model profile extra options：确认是否已有 JSON response_format；没有也不阻塞，但小流量必须验证 JSON 稳定。
4. 读取当前配置：确认 `review-enabled=false` 仍未全局开启。
5. 读取质量台账：确认 persist gate、query visibility hard filter 仍是最新通过基线。

### 6.2 最小小流量运行验证

仅在用户明确允许“验证轮可运行隔离 compile”后执行。

| 验证项 | 建议 |
|---|---|
| 数据环境 | 优先临时库 / 测试库；不要清主库，不要复用 SWIP clean 主库 |
| 输入资料 | 单个很小源文件，1-2 条明确事实，避免大文档和多源交叉 |
| 开关方式 | 仅运行时环境变量打开 `LATTICE_LLM_REVIEW_ENABLED=true`，不要提交配置变更 |
| 正向验证 | reviewer route 应为非 `rule-based`；LLM 返回 approved 后 article 为 `passed`，persist 成功 |
| fail-closed 验证 | 在隔离环境构造不可用 reviewer route 或不可解析输出；期望非 pass、`needs_human_review`、persistedCount=0 |
| fix loop 验证 | 若 reviewer 返回 issue 且 autoFix 开启，应进入 `fix_review_issues`，修复后重新 `review_articles` |
| 可观测验证 | 后台显示 reviewRoute、accepted/pending/needs count、fix attempts、最终状态 |
| 查询可见性验证 | 非 passed 不应进入 article-backed query 候选；passed+ACTIVE 仍可召回 |

通过标准：

1. `review_articles` step 实际 route 是 compile reviewer LLM route，不是 rule-based。
2. LLM approved 且无 issue 时才进入 `passed`。
3. LLM 异常 / 解析失败不会 persist。
4. fix 后必须二次 review，不能 fixer 直接发布。
5. 后台可以解释本次是 LLM reviewer、rule-based disabled fallback 还是 fail-closed。

## 7. 当前不能做的事

本轮以及 agentA fail-closed 验证完成前，不应做以下动作：

1. 不要设置 `LATTICE_LLM_REVIEW_ENABLED=true`。
2. 不要修改 `src/main/resources/config/lattice-llm.yml`。
3. 不要修改 `.claude/t1.md` 或复制其中敏感凭据。
4. 不要在主库清库、重建或跑 compile。
5. 不要为了启用 reviewer 修改 persist gate 或 query visibility gate。
6. 不要把 rule-based pass 宣称为 LLM 内容审查通过。
7. 不要在没有 route / status 可观测性的情况下做用户验收。

## 8. 下一步是否等待 agentA 结果

是。下一步必须先等待 agentA 的 fail-closed 修复结果被验证并纳入当前基线。

虽然当前工作区已有 agentA 对 `ArticleReviewerGateway` 和相关测试的未提交改动，且 `compile_review_llm_reviewer_fail_closed_fix_result_report.md` 报告称 redline 与测试通过，但本轮不运行测试、不做复核。因此启用 readiness 结论必须带条件：

```text
只有在 fail-closed 修复通过验证后，才允许进入 LLM reviewer 小流量验证。
```

## 9. 下一轮 prompt 建议

建议交给 agentD 做验证，不改代码：

```text
交给：agentD

任务类型：
LLM reviewer 启用前只读 + 隔离验证设计复核。不改代码，不改配置，不提交。

目标：
1. 读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md。
2. 读取 compile_review_llm_reviewer_enablement_readiness_report.md 与 agentA fail-closed fix result report。
3. 运行 redline 与必要的现有测试，确认 fail-closed 修复进入可验证基线。
4. 只读确认后台当前 compile/reviewer binding、model profile、provider connection 是否 enabled。
5. 不启用 LLM reviewer；如需小流量 compile，必须先得到用户明确授权，并使用临时库/隔离库。
6. 输出 compile_review_llm_reviewer_enablement_gate_report.md。

禁止：
- 不准修改 src/main/java/**
- 不准修改 src/main/resources/**
- 不准修改 .claude/t1.md
- 不准清主库
- 不准在未授权情况下运行 compile
- 不准提交代码
```

## 10. 本轮修改说明

本轮是否修改代码：否。  
本轮是否修改配置：否。  
本轮是否启用 LLM reviewer：否。  
本轮是否运行测试：否。  
本轮是否运行 compile：否。  
本轮仅新增本报告：`compile_review_llm_reviewer_enablement_readiness_report.md`。
