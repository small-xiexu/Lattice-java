# Compile Needs Human Review Persist Behavior Analysis Report

## 1. 结论

`allowPersistNeedsHumanReview=true` 当前没有让 `needs_human_review` 文章进入正式 `articles` 表。原因不是运行时偶发，也不是 Repository 写库失败，而是 `PersistArticlesNode` 的硬门禁只读取 `acceptedArticlesRef`，并再次过滤 `reviewStatus=passed`。

因此 Job `a65ac60b-f893-4dfb-aeb7-932ede916feb` 的表现是确定性的：

| 阶段 | 状态 |
| --- | --- |
| R2 review | accepted=1，needsHumanReview=3 |
| persist_articles | 只落库 1 篇 passed article |
| articles 表 | 1 篇，`review_status=passed` |
| Redis working set | 仍保留 3 篇 needsHumanReview 草稿/审查结果，TTL 内可追踪 |

当前行为符合“只有 passed 文章进入正式 persist gate”的治理目标，但不符合 `allowPersistNeedsHumanReview` 字段名、schema 注释和 yml 配置项的字面语义。这个配置目前可视为已接入配置层、已写入 GraphState，但未被 persist 行为消费。

## 2. Redline 与本轮修改

| 项目 | 结果 |
| --- | --- |
| redline BLOCKER | 0 |
| redline REVIEW | 1860 |
| redline ALLOWLIST | 244 |
| 本轮是否修改代码 | 否 |
| 本轮是否修改配置/数据库 | 否 |
| 本轮是否运行 SWIP eval / clean rebuild | 否 |

## 3. allowPersistNeedsHumanReview 当前真实值和来源

### 3.1 当前运行时 DB 值

只读查询 `compile_review_settings`：

| config_scope | auto_fix_enabled | max_fix_rounds | allow_persist_needs_human_review | threshold | updated_by |
| --- | --- | ---: | --- | --- | --- |
| default | true | 1 | true | HIGH | agentD |

当前有效来源是数据库。`CompileReviewConfigService#getCurrentState()` 优先读取 `compile_review_settings`，存在 default 行时返回 `configSource=database`，并在启动时通过 `apply(...)` 写入 `CompileReviewProperties`。

### 3.2 默认来源

| 来源 | 默认值 |
| --- | --- |
| `src/main/resources/config/lattice-compiler.yml` | `${LATTICE_COMPILER_REVIEW_ALLOW_PERSIST_NEEDS_HUMAN_REVIEW:true}` |
| `src/main/resources/db/schema.sql` | `allow_persist_needs_human_review BOOLEAN NOT NULL DEFAULT TRUE` |
| `CompileReviewProperties` Java 字段初值 | false |

实际运行以 yml/env 和 DB 覆盖为准。当前 DB 已明确为 `true`。

## 4. Job a65ac60b 运行链路

| step | 结果摘要 |
| --- | --- |
| initialize_job | reviewMode=LLM |
| compile_new_articles | conceptCount=4 |
| review_articles R1 | pendingReviewCount=4，accepted=0，needsHumanReview=0 |
| fix_review_issues F1 | fixAttemptCount=1，pendingReviewCount=4 |
| review_articles R2 | pendingReviewCount=0，accepted=1，needsHumanReview=3 |
| persist_articles | persistedCount=1 |
| rebuild_article_chunks | 基于 persisted=1 重建 2 个 chunk |
| refresh_vector_index | article_vector_index=1，article_chunk_vector_index=2 |

正式 DB 当前只有 1 篇文章：

| articles | 数量 |
| --- | ---: |
| `review_status=passed` | 1 |
| `needs_human_review` | 0 |
| article_chunks | 2 |
| article_vector_index | 1 |
| article_chunk_vector_index | 2 |

## 5. needsHumanReview 的精确过滤点

### 5.1 ReviewDecisionPolicy 只负责分区

`ReviewDecisionPolicy#partition(...)` 的语义：

| 条件 | 分区 | reviewStatus |
| --- | --- | --- |
| reviewer pass | accepted | passed |
| 未通过且 `autoFixEnabled=true` 且 `fixAttemptCount < maxFixRounds` 且有 issues | fixable | pending |
| 其他未通过 | needsHumanReview | needs_human_review |

Job a65ac60b 在 F1 后 `fixAttemptCount=1`，`maxFixRounds=1`，所以 R2 未通过的 3 篇不再进入 fixable，而是进入 `needs_human_review`。

### 5.2 ReviewArticlesNode 把 accepted 与 needsHumanReview 分开保存

R2 后：

| ref | 内容 |
| --- | --- |
| `acceptedArticlesRef` | 1 篇 passed |
| `needsHumanReviewArticlesRef` | 3 篇 needs_human_review |
| `reviewPartitionRef` | accepted=1，fixable=0，needsHumanReview=3 |

Redis working set 当前仍能读到 3 篇 needsHumanReview：

| 状态 | 数量 | issueCount |
| --- | ---: | --- |
| `needs_human_review` | 3 | 5 / 13 / 9 |

这些草稿、review issue、fix 后内容仍在 Redis 工作集 TTL 内可追踪；当前没有写入 `article_review_audits`，该表 count=0。

### 5.3 PersistArticlesNode 是正式过滤点

`PersistArticlesNode#execute(...)` 实际流程：

1. `loadAcceptedArticles(state.getAcceptedArticlesRef())`
2. `retainPassedArticles(acceptedArticles)`
3. `articleAtomicWriteService.persistArticlesAtomic(...)`

关键点：

| 判断 | 当前实现 |
| --- | --- |
| 是否读取 `needsHumanReviewArticlesRef` | 否 |
| 是否读取 `state.isAllowPersistNeedsHumanReview()` | 否 |
| 是否允许 `reviewStatus=needs_human_review` 进入 persist | 否 |
| 最终 pass 条件 | `reviewStatus.equalsIgnoreCase("passed")` |

因此 3 篇 needsHumanReview 并不是在 `ArticleCompileSupport`、`ArticleRepository` 或 SQL upsert 阶段被挡下，而是在 `PersistArticlesNode` 进入写库前就没有进入 `articlesToPersist`。

### 5.4 下游写库层本身不再过滤

`ArticleAtomicWriteService` 与 `ArticlePersistSupport.persistArticles(...)` 会写入传入的所有 envelope；`ArticleMapper.xml` 也只是把 `record.reviewStatus` 写入 `articles.review_status`。

也就是说，如果 `PersistArticlesNode` 把 needsHumanReview envelope 传给下游，Repository 层并没有第二道 `passed-only` 过滤。

## 6. allowPersistNeedsHumanReview 当前语义判断

当前实现语义不是“允许 needs_human_review 文章落库”。

实际语义更接近：

| 语义候选 | 是否符合当前实现 |
| --- | --- |
| 允许入库但标记需要人工复核 | 否 |
| 只允许进入待确认/工作集队列 | 部分符合，但不是由该 flag 控制 |
| 当前实现未接入 persist 行为 | 是 |

进一步证据：`PersistArticlesNodeTests` 已有测试明确覆盖“即使 `allowPersistNeedsHumanReview=true`，正式 persist 仍只接收 passed 文章”。这说明当前代码和测试口径都把 `allowPersistNeedsHumanReview` 排除在正式 persist gate 之外。

## 7. 当前行为是否符合配置语义

不符合字段字面语义。

| 维度 | 判断 |
| --- | --- |
| 字段名 / schema comment / yml | 暗示允许 needs_human_review 继续落库 |
| Runtime state | 确实携带 `allowPersistNeedsHumanReview=true` |
| PersistArticlesNode 行为 | 完全不消费该状态 |
| 现有测试 | 明确要求 true 时仍只落库 passed |
| 治理目标 | 符合“未通过审查不入正式 query-facing persist” |

所以用户可解释口径应分清两件事：

1. “为什么没入库”：因为正式 persist gate 现在只允许 `passed`，`needs_human_review` 只保留在工作集等待人工复核。
2. “为什么配置是 true 还没入库”：因为这个配置项当前是历史/误导性字段，已被严格 persist gate 实际覆盖，没有接入落库分支。

## 8. 如果允许 needsHumanReview 入库，query 是否可见

不应默认 query 可见。

当前 query visibility hard filter 已要求 article-backed 通道只召回 `review_status='passed'` 且 `lifecycle='ACTIVE'`。因此即使未来把 needsHumanReview 持久化到 `articles`，也应该继续保持：

| 状态 | 是否进入 articles | 是否 query 可见 |
| --- | --- | --- |
| passed | 是 | 是 |
| needs_human_review | 如产品要求可入人工复核队列 | 否 |
| rejected / failed | 否，或仅进入单独审计/人工队列 | 否 |

更稳妥的设计是把待人工复核内容持久化到单独 review queue / draft 表，而不是直接混入正式 `articles` 表；如果复用 `articles`，也必须依赖现有 query hard filter，并在后台明确展示“未进入正式知识库 / 不可 query”。

## 9. 是否建议 SWIP eval 在修复前继续跑

不建议把它作为 answer quality 评估继续跑。

理由：

1. 当前 4 篇候选文章只入库 1 篇，SWIP eval 会混入“知识缺失/未通过审查未入库”变量。
2. 当前 query 只能看到 passed article，这是治理门禁预期；用完整 SWIP eval 直接追准确率，会把 reviewer/fixer 通过率和 query answer 能力混在一起。
3. 若目标是验证门禁安全性，可以保留只读/小范围检查；若目标是评估 SWIP 答案质量，应先处理 reviewer 通过率或 needsHumanReview 持久化/人工确认路径。

## 10. 下一轮唯一最小修复点

建议下一轮不要先让 `needs_human_review` 进入正式 `articles` 表。优先做一个最小语义修复：

**将 `allowPersistNeedsHumanReview` 从“允许落库”语义改为废弃/隐藏/只读说明，或从后台配置中移除，明确当前正式 persist gate 只允许 `passed`。**

最小范围应围绕配置展示与配置模型，不改 Writer/Reviewer/Fixer，不改 query/answer，不改 persist gate：

| 范围 | 目的 |
| --- | --- |
| Admin compile review config DTO / response / controller 展示语义 | 避免后台继续承诺 true 会落库 |
| `CompileReviewProperties` / `CompileReviewConfigState` 注释或弃用标记 | 避免工程语义误导 |
| 相关测试 | 固化“正式 persist 只允许 passed；needsHumanReview 留待人工确认” |

如果产品明确要求 needsHumanReview 可持久化，则应另开一轮设计“人工复核持久队列”，不要在本轮直接把 `PersistArticlesNode` 放宽为 true 即入正式 `articles`。

## 11. 本轮修改声明

本轮是否修改代码：否。

本轮是否修改测试：否。

本轮是否修改配置/数据库：否。

本轮是否清库、重新导入或运行 SWIP eval：否。
