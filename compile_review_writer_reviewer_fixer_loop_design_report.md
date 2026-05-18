# Compile Review Writer -> Reviewer -> Fixer -> Reviewer 闭环设计报告

生成时间：2026-05-17  
执行角色：agentB  
任务类型：只读架构设计  
任务边界：不改源码、不改测试、不改配置、不运行 compile、不清库、不跑 baseline。

## 1. 总体结论

当前 StateGraph 主链已经支持有限闭环骨架：

```text
Writer 生成草稿
  -> Reviewer 审查
  -> 不通过且可修复时进入 Fixer
  -> Fixer 修复后回到 Reviewer
  -> 最终 passed 才进入 persist
  -> 多轮仍不通过进入 needs_human_review
```

但它还不等于“生产可控 LLM 审查闭环”：

| 判断项 | 结论 |
|---|---|
| 当前主链是否有 `writer -> reviewer -> fixer -> reviewer` 回边 | 有，StateGraph 已显式配置 `fix_review_issues -> review_articles` |
| 当前是否能控制单个 job 走 LLM reviewer | 还不能；当前 LLM reviewer 仍主要依赖全局 `lattice.llm.review-enabled` |
| 当前 rule-based reviewer 是否等于事实审查 | 不是，只能称为结构审查 / 基础格式审查 |
| 当前 Fixer 后是否必须复审 | StateGraph 主链是；旧式 `CompileArticleNode.compile(...)` 不是 |
| 当前未通过文章是否会入库 | 主链 persist gate 只保留 `review_status=passed`，不会持久化非 passed |
| 当前非 passed article-backed 查询是否可见 | Query hard filter 已要求 `review_status='passed'` 且 `lifecycle='ACTIVE'` |

下一轮不建议直接“重写 review loop”。推荐先实现 **per-job `reviewMode`**，因为闭环结构已经存在，真正缺的是按 job 安全选择 LLM reviewer，避免全局误开。

## 2. 当前实现位置

| 角色 / 环节 | 当前实现 | 说明 |
|---|---|---|
| Writer | `DefaultWriterAgent`、`ArticleCompileSupport.compileDraftArticles(...)`、`CompileArticleNode.compileDraft(...)` | 生成 `ArticleRecord` 草稿，初始 `review_status=pending` |
| Reviewer 节点 | `ReviewArticlesNode` | 从 working set 读取草稿或修复后文章，调用 `ArticleCompileSupport.reviewDraftArticles(...)` |
| Reviewer Agent | `DefaultReviewerAgent`、`ArticleReviewerGateway`、`RuleBasedArticleReviewer`、`ReviewResultParser` | `review-enabled=false` 时走 rule-based；启用后调用 LLM reviewer 并解析 JSON |
| Fixer 节点 | `FixReviewIssuesNode` | 只处理当前 `ReviewPartition.fixable` 子集 |
| Fixer Agent | `DefaultFixerAgent`、`ReviewFixService` | 根据 review issues、原文章和源文生成完整修复后文章 |
| Review 决策 | `ReviewDecisionPolicy` | pass -> `passed`；有 issues 且未超过修复轮次 -> `pending/fixable`；否则 -> `needs_human_review` |
| 闭环边 | `CompileGraphDefinitionFactory` | `compile_new_articles -> review_articles`，`fix_review_issues -> review_articles` |
| 自动修复配置 | `CompileReviewProperties`、`CompileReviewConfigService`、`compile_review_settings` | `autoFixEnabled` 默认 true，`maxFixRounds` 默认 1，配置服务限制 0 到 5 |
| 持久化门禁 | `PersistArticlesNode.retainPassedArticles(...)` | 只保留 `review_status=passed` 的 envelope 进入正式落库 |

旧式 `CompileArticleNode.compile(...)` 仍存在，它在 fixer 成功后会直接把文章置为 `passed`，没有第二次 reviewer。它不是当前 StateGraph 主链闭环，但后续治理应避免把这个旧入口作为真实编译入口继续扩展。

## 3. 当前材料流

| 材料 | Reviewer 当前是否拿到 | Fixer 当前是否拿到 | 说明 |
|---|---:|---:|---|
| 文章草稿 | 是 | 是 | Reviewer prompt 含 `COMPILED ARTICLE`；Fixer prompt 含原始文章 |
| 原始 source 正文 | 是，截断到 12000 字符 | 是，截断到 10000 字符 | 来源来自 `CompileArticleNode.buildSourceContents(...)`，按 `sourcePaths/sourceId` 拼接 |
| 文章内引用标记 | 是 | 是 | 如果 Writer 写进正文，Reviewer/Fixer 可见；不是单独结构化 citation binding |
| reviewer issues | 不适用 | 是 | Fixer 接收 `ReviewResult.issues`，`ReviewFixService` 按严重度最多保留前 5 条 |
| fact cards | 否 | 否 | 当前 review/fix prompt 未显式注入 fact card |
| source_file_chunks | 否 | 否 | 已有 source chunk 入库通道，但 reviewer/fixer 当前不是按 chunk 组装证据 |
| article chunks / vector hits | 否 | 否 | review 发生在 article persist/chunk/vector 之前 |

结论：当前 Reviewer/Fixer 具备“对照原始资料审查和修复”的基础输入，但不是完整结构化证据闭环。长文截断、fact card 未注入、citation binding 未单独展开，都是后续增强点，不建议和第一轮 per-job reviewMode 混做。

## 4. 当前闭环状态判断

| 问题 | 判断 |
|---|---|
| Writer / Reviewer / Fixer 分别在哪里实现 | 见第 2 节 |
| 是否已有 auto-fix / max-fix-rounds 配置 | 有：`autoFixEnabled` 与 `maxFixRounds`，默认 true / 1，配置服务允许 0 到 5 |
| Reviewer 是否拿到原始资料、草稿、引用、结构化证据 | 拿到草稿和截断原始资料；引用只以内嵌正文形式可见；未拿到 fact cards/source chunks 等结构化证据 |
| Fixer 是否拿到 reviewer issues 和原始资料 | 是，拿到 issues、原文章、截断 source 正文 |
| 修复后文章是否再次进入 reviewer | StateGraph 主链是，`fix_review_issues` 后回到 `review_articles` |
| 中间草稿是否可能被入库 | StateGraph 主链不会；草稿和修复中间态在 working set，persist 只读 accepted/passed |
| persist gate 是否只允许最后 passed 版本入库 | 是，`PersistArticlesNode` 当前只保留 `review_status=passed` |
| 多轮仍不通过是否会 needs_human_review | 是，超过 `maxFixRounds` 或无可修 issues 时进入 `needs_human_review` |

需要注意：`allowPersistNeedsHumanReview` 配置字段仍存在，配置文件和 DB 默认值也还在，但当前 `PersistArticlesNode` 已不再用它放行非 passed 文章。治理语义应以当前 persist gate 为准。

## 5. 缺口清单

| 缺口 | 影响 | 优先级 |
|---|---|---|
| 缺 per-job `reviewMode` | 只能靠全局开关启用 LLM reviewer，扩流粒度太粗 | 高 |
| `ArticleReviewerGateway` 不接收 job 级 review mode | 无法让单个 job 覆盖全局默认 | 高 |
| `CompileJobRecord` / `compile_jobs` 未持久化 reviewMode | 异步执行、重试、审计无法稳定复现当时意图 | 高 |
| `CompileGraphState` / `ReviewTask` 未携带 reviewMode | StateGraph 内 reviewer 无法按 job 决策 | 高 |
| 缺显式 `reviewPolicy` | 目前只能用全局 `autoFixEnabled/maxFixRounds` 表达是否自动修复，不能按 job 选择 review-only 或 review-and-fix | 中 |
| 每轮 review/fix 缺 per-article 持久化审计 | `compile_job_steps` 有步骤日志，但缺逐篇、逐轮 issues/fix/result 追踪 | 中 |
| Reviewer/Fixer evidence 有截断且未用结构化证据 | 长文或关键事实靠后时，LLM 审查可能看不到完整证据 | 中 |
| 旧式 direct compile 方法没有二次 reviewer | 若被新入口复用，可能绕过严格闭环语义 | 中 |

## 6. 推荐最小闭环语义

第一版闭环不拆 `review_status`，仍使用当前 gate 状态：

| 状态 | 语义 | 是否可入库 |
|---|---|---:|
| `pending` | 草稿待审查，或 fixer 修复后待复审 | 否 |
| `passed` | 当前 reviewMode 与 reviewPolicy 下最终通过 | 是 |
| `needs_human_review` | 自动审查 / 修复不能证明安全通过 | 否 |

推荐执行语义：

| 步骤 | 规则 |
|---|---|
| Writer | 只产出 `pending` 草稿，写 working set，不写正式 articles |
| Reviewer | 每一轮都按 job 的 `reviewMode` 执行；`LLM` 模式下不允许 rule-based 静默替代为 pass |
| Fixer | 只有 non-pass 且有 issues、`autoFixEnabled=true`、`fixAttemptCount < maxFixRounds` 时触发 |
| Re-review | Fixer 输出必须重新进入 Reviewer；不能由 Fixer 直接置 `passed` |
| Loop stop | 达到 `maxFixRounds`、没有 issues、fixer 失败、reviewer 异常或解析失败时进入 `needs_human_review` |
| Persist | 只持久化最终 `passed` envelope |
| Query | article-backed 查询继续只召回 `passed + ACTIVE` |

## 7. reviewMode / reviewPolicy 定义建议

### 7.1 reviewMode

`reviewMode` 应只表达“Reviewer 用什么审查实现”，不要表达是否自动修复。

| reviewMode | 语义 | 默认建议 |
|---|---|---|
| `RULE_BASED` | 使用规则审查，只能称为结构审查 / 格式审查 | 默认 |
| `LLM` | 每轮 reviewer 必须调用 LLM reviewer；异常或解析失败 fail-closed | 小范围显式指定 |
| `DISABLED` | 不建议第一版实现 | 不做 |

`reviewMode=LLM` 不应被定义为“自动启用 fixer loop”。它只保证 review step 走 LLM。是否 fixer loop，应由现有 `autoFixEnabled/maxFixRounds` 或后续 `reviewPolicy` 控制。

### 7.2 reviewPolicy

第一轮不建议新增 `reviewPolicy` 字段。原因是当前已有 `autoFixEnabled/maxFixRounds`，足以表达最小自动修复闭环，新增 job 级 policy 会扩大 schema、API、UI、测试范围。

但产品语义上建议预留两个 policy 名称：

| reviewPolicy | 语义 |
|---|---|
| `REVIEW_ONLY` | Reviewer 不通过即 `needs_human_review`，不触发 Fixer |
| `REVIEW_AND_FIX` | Reviewer 不通过且可修时进入 Fixer，修复后必须复审 |

最小落地建议：

1. 下一轮先实现 per-job `reviewMode`。
2. 暂时沿用全局 `autoFixEnabled/maxFixRounds` 作为 loop policy。
3. 等需要按 job 控制“只审不修 / 审后自动修”时，再新增 `reviewPolicy`。

## 8. 每轮审查和修复审计设计

当前已有两类可观测信息：

| 载体 | 当前能力 | 缺口 |
|---|---|---|
| `compile_job_steps` | 记录每次 graph step，重复 `review_articles` / `fix_review_issues` 可体现轮次 | 缺逐篇 issues、review result、fix result 的结构化字段 |
| `ArticleReviewEnvelope` working set | 有 reviewerRoute、fixerRoute、reviewAttemptCount、fixAttemptCount、reviewResult | 主要是运行中 / 工作集形态，不是面向后台长期追溯的 per-round audit |
| `article_review_audits` | 已有人工复核审计表 | 表注释和当前语义偏人工复核，未明确承载自动 review/fix round |

推荐后续审计语义：

| 字段 | 建议内容 |
|---|---|
| jobId | 所属 compile job |
| articleKey / conceptId / sourceId | 定位文章 |
| roundNo | 第几轮 reviewer |
| action | `AUTO_REVIEW` / `AUTO_FIX` |
| reviewMode | `RULE_BASED` / `LLM` |
| reviewerRoute / fixerRoute | 实际模型或 rule-based route |
| previousStatus / nextStatus | 本轮状态变化 |
| issues | reviewer 输出的问题摘要 |
| fixApplied | fixer 是否产生修复 |
| failureReason | LLM 异常、JSON 解析失败、轮次耗尽、无可修 issue 等 |

第一轮 per-job reviewMode 不必同时做这张审计表；可以先依赖 `compile_job_steps` 和后台 summary。等 LLM rollout 扩大前，再做 per-article round audit。

## 9. 如何避免无限循环与证据外编造

### 9.1 避免无限循环

当前已有硬边界：

| 机制 | 当前行为 |
|---|---|
| `maxFixRounds` | 默认 1，配置服务限制 0 到 5 |
| `fixAttemptCount` | `FixReviewIssuesNode` 每次执行后递增 |
| route 决策 | 只有 `fixAttemptCount < maxFixRounds` 且 fixable 非空才回到 Fixer |
| 无 issue / fixer 失败 | 不再进入 fixer，最终进入 `needs_human_review` |

下一轮实现不得引入无上限 while loop；必须继续使用 StateGraph 回边 + `maxFixRounds`。

### 9.2 避免 fixer 编造证据外内容

当前已有保护：

| 保护 | 说明 |
|---|---|
| Fixer prompt | 要求只修审查员指出的问题、数值以源文件为准、证据不足时删除或说明未给出 |
| Source 输入 | Fixer 拿到原文章、issues、截断 source 正文 |
| 二次 reviewer | StateGraph 主链要求 fix 后重新 reviewer |
| Persist gate | 只有最终 reviewer `passed` 才能落库 |

仍存在的风险：

1. source 截断可能导致 Fixer 看不到完整证据。
2. Reviewer 也看同类截断 source，可能漏掉 Fixer 编造。
3. 当前没有 deterministic claim-to-source diff。

因此第一版严格闭环的安全边界应表述为“LLM reviewer/fixer 加 gate 的治理闭环”，不能宣传为形式化事实证明。后续可增强 source evidence 选择、citation binding、per-claim 审计。

## 10. 如何保证 failed / needs_human_review 不入库、不可 query

| 层级 | 当前保障 |
|---|---|
| working set | 草稿、待修复、needs_human_review 都保存在 working set，不是正式 articles 表 |
| persist gate | `PersistArticlesNode` 只保留 `review_status=passed` |
| chunks/vector | 未 persist 的 article 不会进入 article chunks / article vector index |
| article-backed query | FTS、refKey、article chunk lexical、article vector、article chunk vector 均已 hard filter `passed + ACTIVE` |
| raw source / fact card | 不属于 article review_status 控制范围，需要独立治理，不应被用来证明文章已通过 reviewer |

结论：对 article-backed 正式知识链路，当前已能保证 non-passed 不入库、不可见。source/source_chunk/fact_card 是独立证据通道，不能直接套 article review_status；若后续要治理，需要单独定义 raw evidence visibility policy。

## 11. 下一轮建议：先 per-job reviewMode，不直接改 loop

建议下一轮先实现 **per-job `reviewMode`**。

理由：

1. StateGraph 闭环已经存在，不需要先重写 loop。
2. 当前生产启用 LLM reviewer 的最大风险是全局开关粒度过粗。
3. per-job mode 可以让单个 job 安全走 `LLM`，默认 job 继续 `RULE_BASED`。
4. 它能支撑后续正向 canary：指定 job LLM approved -> `passed` -> persist。
5. 它也是后续 source allowlist、reviewPolicy、审计扩展的稳定基础。

不建议下一轮同时实现：

| 暂不做 | 原因 |
|---|---|
| sourceCode allowlist | 依赖 per-job mode，可后置 |
| reviewPolicy 入库 | 会扩大 API/schema/UI 范围 |
| fact card 注入 reviewer | 会改变 reviewer evidence 变量 |
| source evidence 重排 | 会改变 reviewer 判断变量 |
| review_status enum 拆分 | 会牵动 persist/query/admin/schema 多处 |
| legacy direct compile 大清理 | 可作为后续治理，不应混入 per-job mode |

## 12. 下一轮 agentA 最小实现范围

如果下一轮确认进入代码实现，只允许围绕 per-job `reviewMode` 做最小闭环打通。

建议允许文件范围：

| 范围 | 文件 / 模块 |
|---|---|
| API 输入输出 | `src/main/java/com/xbk/lattice/api/admin/AdminCompileJobRequest.java`、`AdminCompileJobResponse.java`、`AdminCompileController.java` |
| job 记录与执行请求 | `CompileJobService.java`、`CompileExecutionRequest.java`、`CompileJobRecord.java`、`CompileJobJdbcRepository.java`、`CompileJobMapper.xml` |
| schema | `src/main/resources/db/schema.sql`，仅新增 `compile_jobs.review_mode` 默认 `RULE_BASED` |
| graph state | `CompileGraphState.java`、`CompileGraphStateKeys.java`、`CompileGraphStateMapper.java`、`InitializeJobNode.java` |
| reviewer 传参 | `ReviewTask.java`、`DefaultReviewerAgent.java`、`ArticleCompileSupport.java`、`ArticleReviewerGateway.java`、`AgentModelRouter.java` |
| 后台展示 | 仅在已有 summary response/service 中展示 job reviewMode，不新增复杂 UI 策略 |
| 测试 | 只覆盖 reviewMode 默认、LLM 指定、fail-closed、persist gate、fix 后复审 |

不允许下一轮扩大到：

1. 修改 query answer 链路。
2. 修改 source/fact_card visibility policy。
3. 新增业务 case 特判。
4. 放宽 persist gate。
5. 让 `needs_human_review` 落库。
6. 修改 redline allowlist。

## 13. 下一轮必须验证的测试清单

| 测试 | 目的 |
|---|---|
| 默认未传 reviewMode 时仍为 `RULE_BASED` | 保证默认行为不变 |
| 指定 `reviewMode=LLM` 时 reviewer 实际走 LLM route | 验证 per-job mode 生效 |
| 全局 env 开启时 `RULE_BASED` job 仍走 rule-based | 防止全局误开污染所有 job |
| 全局 env 关闭时 `LLM` job 仍按 job mode 尝试 LLM | 验证 job mode 覆盖全局默认 |
| LLM reviewer 异常 / JSON 解析失败 fail-closed | 不能 rule-based pass |
| LLM reviewer `passed=false` 且有 issues 时进入 fixer | 验证 review -> fix 路由 |
| Fixer 成功后必须再次进入 reviewer | 验证 `fix_review_issues -> review_articles` |
| 修复后 reviewer passed 才 persist | 防止 fixer 直接发布 |
| 达到 `maxFixRounds` 后进入 `needs_human_review` | 验证有限循环 |
| `needs_human_review` / `pending` 不 persist | 验证 persist gate |
| `passed` article persist 后仍进入 chunks/vector | 验证正向路径未破坏 |
| 后台 / API 能看到 requested reviewMode 与 actual route | 防止把 rule-based 当 LLM 内容审查 |

不建议下一轮跑全量 query baseline，除非 per-job reviewMode 改动意外触碰 query mapper 或回答链路。

## 14. 本轮是否修改代码

否。

本轮未修改源码、测试、配置、脚本、数据库，也未运行 compile、baseline 或清库操作。仅新增本设计报告。
