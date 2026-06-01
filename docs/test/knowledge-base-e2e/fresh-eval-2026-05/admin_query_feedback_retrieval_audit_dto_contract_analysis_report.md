# B9 Query Feedback / Retrieval Audit DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B9 — `api/admin` query feedback / retrieval audit DTO

---

## 一、拆分建议：B9 → B9a + B9b

11 个候选类略超 10 个上限，按业务子域自然拆分：

| 子批次 | 候选数 | 范围 | 拆分理由 |
|---|---|---|---|
| **B9a** | **6** | Query Feedback DTO | 答案反馈创建、处理、列表、详情、审计，形成完整闭环 |
| **B9b** | **5** | Retrieval Audit DTO | 检索审计 run 列表/详情 + channel run/hit，独立诊断子域 |

两个子域之间无嵌套引用关系（DetailResponse 包含 AuditResponse，但都在 B9a 内；RetrievalAuditDetailResponse 包含 RunResponse+ChannelHitResponse，但都在 B9b 内）。

---

## 二、B9a 纳入文件清单（6 个类）

| # | 类名 | 类型 | Lombok | 手写 getter | 手写 setter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|---|
| 1 | `AdminQueryFeedbackCreateRequest` | Request | 无 | 8 | 8 | 默认无参 | `comment`/`reportedBy` 用户反馈文本 |
| 2 | `AdminQueryFeedbackHandleRequest` | Request | 无 | 2 | 2 | 默认无参 | `handledBy`/`comment` 处理审计字段 |
| 3 | `AdminQueryFeedbackResponse` | Response | 无 | 15 | 0 | 手写全参 | `comment`/`resolutionComment`/`reportedBy`/`handledBy` 审计字段 |
| 4 | `AdminQueryFeedbackListResponse` | Response | 无 | 2 | 0 | 手写全参 | 列表容器 |
| 5 | `AdminQueryFeedbackDetailResponse` | Response | 无 | 2 | 0 | 手写全参 | 嵌套 FeedbackResponse + AuditResponse 列表 |
| 6 | `AdminQueryFeedbackAuditResponse` | Response | 无 | 9 | 0 | 手写全参 | `comment`/`operatedBy`/`metadataJson` 审计+大文本 |

**B9a 统计**：2 Request + 4 Response。简单 getter 可删除 38 个，setter 可删除 10 个。

---

## 三、B9b 纳入文件清单（5 个类）

| # | 类名 | 类型 | Lombok | 手写 getter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|
| 1 | `AdminQueryRetrievalAuditListResponse` | Response | 无 | 2 | 手写全参 | 列表容器 |
| 2 | `AdminQueryRetrievalAuditDetailResponse` | Response | 无 | 7 | 手写全参 | 嵌套 RunResponse + ChannelHitResponse |
| 3 | `AdminQueryRetrievalAuditRunResponse` | Response | 无 | 21 | 手写全参 | `channelRunSummaryJson` 大 JSON；构造器有 `List.copyOf` 防御性拷贝 |
| 4 | `AdminQueryRetrievalChannelRunResponse` | Response | 无 | 8 | 手写全参 | `errorSummary` 可能含错误详情 |
| 5 | `AdminQueryRetrievalChannelHitResponse` | Response | 无 | 20 | 手写全参 | `metadataJson`/`sourceChunkIdsJson`/`sourcePathsJson` 大 JSON |

**B9b 统计**：0 Request + 5 Response。简单 getter 可删除 58 个。

---

## 四、明确排除文件清单及理由

| 排除文件 | 理由 | 归属 |
|---|---|---|
| `AdminQueryFeedbackController.java` | Controller 本体 | 不纳入 |
| `AdminQueryRetrievalAuditController.java` | Controller 本体 | 不纳入 |
| 所有 B10 文件 | overview / pending / processing task DTO | B10 |
| 所有 B11 文件 | controller 内部 DTO | B11 |
| `AnswerFeedbackRequest` / `AnswerFeedbackHandleRequest` | 位于 `governance` 包 | B19 |
| `AnswerFeedbackRecord` / `AnswerFeedbackAuditRecord` | 位于 `infra/persistence` 包 | 明确排除 |
| `QueryRetrievalRunView` / `QueryRetrievalChannelHitView` | 位于 `infra/persistence` 包 | 明确排除 |
| `RetrievalAuditSnapshot` / `RetrievalChannelRun` / `RetrievalChannelRunStatus` | 位于 `query/service` 包 | B0.5/B12b |

---

## 五、每个纳入类的 Lombok/Javadoc 改造建议

### 5.1 关键发现：B9 无 boolean getter 命名不一致问题

与 B8a 不同，B9 所有 boolean 字段的 getter 命名均与 Lombok 默认生成一致（`isXxx()` 格式），无任何需要 `@Getter(AccessLevel.NONE)` 排除的字段。改造安全性高。

### 5.2 B9a — Query Feedback DTO

#### AdminQueryFeedbackCreateRequest（Request）
- 添加 `@Getter` + `@Setter`，删除 8 个手写 getter + setter
- **禁止引入 `@Data`**：含 `comment`（用户反馈文本）、`reportedBy`（反馈提交人）
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `queryId` | 关联的查询会话标识；用于回溯原始问答上下文 |
| `question` | 用户原始问题文本 |
| `answerSummary` | 系统给出的答案摘要文本 |
| `feedbackType` | 反馈类型（如 positive / negative / correction）；驱动反馈分类和处理优先级 |
| `comment` | 用户提交的反馈说明文本；可能含主观评价或具体纠错 |
| `articleKeys` | 反馈关联的文章唯一键列表；用于快速定位问题文章 |
| `sourcePaths` | 反馈关联的来源文件路径列表 |
| `reportedBy` | 反馈提交人标识；用于审计追踪 |

#### AdminQueryFeedbackHandleRequest（Request）
- 添加 `@Getter` + `@Setter`，删除 2 个手写 getter + setter
- **禁止引入 `@Data`**：含 `handledBy`、`comment`
- 字段 Javadoc：
  - `handledBy` — 处理人标识；用于审计
  - `comment` — 处理说明；resolve 时填写处理措施，dismiss 时填写忽略原因

#### AdminQueryFeedbackResponse（Response）
- 添加类级 `@Getter`，删除 15 个手写 getter
- **禁止引入 `@Data`**：含 `comment`/`resolutionComment`/`reportedBy`/`handledBy`
- 保留全参构造器
- 关键字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `id` | 反馈记录主键 |
| `queryId` | 关联查询会话标识 |
| `question` | 用户原始问题 |
| `answerSummary` | 答案摘要；可能与完整答案不同 |
| `feedbackType` | 反馈类型 |
| `comment` | 用户提交的反馈说明原文 |
| `articleKeys` | 关联文章唯一键 |
| `sourcePaths` | 关联来源路径 |
| `reportedBy` | 反馈提交人 |
| `status` | 处理状态（如 pending / resolved / dismissed）；驱动前端展示处理标签 |
| `resolutionComment` | 处理结果说明；resolved/dismissed 时填写 |
| `handledBy` | 处理人标识；null 表示尚未处理 |
| `handledAt` | 处理时间（ISO 字符串）；null 表示尚未处理 |
| `createdAt` | 反馈创建时间 |
| `updatedAt` | 最后更新时间 |

#### AdminQueryFeedbackListResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段：`count`（返回条目数）、`items`（反馈列表）

#### AdminQueryFeedbackDetailResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段 Javadoc：
  - `feedback` — 反馈详情（AdminQueryFeedbackResponse）
  - `audits` — 处理审计历史列表；按时间倒序

#### AdminQueryFeedbackAuditResponse（Response）
- 添加类级 `@Getter`，删除 9 个手写 getter
- **禁止引入 `@Data`**：含 `comment`/`operatedBy`/`metadataJson`
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `id` | 审计记录主键 |
| `feedbackId` | 关联反馈主键 |
| `action` | 处理动作（如 resolve / dismiss / create） |
| `previousStatus` | 操作前状态 |
| `nextStatus` | 操作后状态；与 previousStatus 对比可知状态流转 |
| `comment` | 操作时填写的说明 |
| `operatedBy` | 操作人标识 |
| `operatedAt` | 操作时间 |
| `metadataJson` | 扩展上下文 JSON；可能含操作时的额外快照信息 |

### 5.3 B9b — Retrieval Audit DTO

#### AdminQueryRetrievalAuditListResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段：`count`、`items`（retrieval audit run 列表）

#### AdminQueryRetrievalAuditDetailResponse（Response）
- 添加类级 `@Getter`，删除 7 个手写 getter
- 注意：`isFound()` 对应 `boolean found` 字段，Lombok 生成一致
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `queryId` | 查询标识 |
| `found` | 是否命中审计记录；false 表示该 queryId 无检索审计数据 |
| `latestRun` | 最新一次检索 run 详情；null 表示无 run 记录 |
| `historyCount` | 历史 run 数量 |
| `runHistory` | 历史 run 列表 |
| `channelHitCount` | 通道命中总数量 |
| `channelHits` | 通道命中明细列表；含各通道 hit rank / fused rank / score 详情 |

#### AdminQueryRetrievalAuditRunResponse（Response）
- 添加类级 `@Getter`，删除 21 个手写 getter
- 保留构造器（含 `List.copyOf` 防御性拷贝）
- `isRewriteApplied()` 对应 `boolean rewriteApplied`，Lombok 生成一致
- 关键字段 Javadoc（按语义分组）：

| 字段 | 需补充 |
|---|---|
| **标识与版本** | |
| `runId` | run 主键；null 表示无记录 |
| `queryId` | 查询标识 |
| `versionTag` | 版本标签；标识检索使用的代码/配置版本 |
| `strategyTag` | 策略标签；标识检索策略组合 |
| **Query 处理链** | |
| `question` | 用户原始问题 |
| `normalizedQuestion` | query 归一化后的文本 |
| `retrievalQuestion` | 实际发送给检索引擎的文本；可能经过改写/扩展 |
| `rewriteApplied` | 是否对 query 执行了 LLM 改写 |
| `rewriteAuditRef` | 改写审计引用；可追溯到具体改写记录 |
| `questionTypeTag` | 问题类型分类标签（如 factual / reasoning / comparison） |
| **检索模式** | |
| `answerShape` | 答案形态（如 text / table / mixed） |
| `retrievalMode` | 检索模式（如 parallel / sequential） |
| `retrievalStrategyRef` | 检索策略引用；可追溯策略配置版本 |
| **命中统计** | |
| `fusedHitCount` | RRF 融合后的最终命中数 |
| `channelCount` | 实际参与检索的通道数 |
| `factCardHitCount` | Fact Card 通道命中数 |
| `sourceChunkHitCount` | Source Chunk 通道命中数 |
| `coverageStatus` | 检索覆盖状态（如 sufficient / partial / empty）；驱动诊断展示 |
| **通道详情** | |
| `channelRunSummaryJson` | 通道运行摘要原始 JSON；**可能较大** |
| `channelRuns` | 各通道运行详情列表；不可变 |
| `createdAt` | run 创建时间 |

#### AdminQueryRetrievalChannelRunResponse（Response）
- 添加类级 `@Getter`，删除 8 个手写 getter
- `isTimeout()` / `isZeroHit()` 对应 boolean 字段，Lombok 生成一致
- 字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `channelName` | 通道名称（如 fts / article_vector / chunk_vector 等） |
| `status` | 运行状态（如 SUCCESS / TIMEOUT / SKIPPED / ERROR） |
| `durationMillis` | 通道耗时（毫秒）；用于性能诊断和瓶颈定位 |
| `hitCount` | 通道命中数；0 表示该通道未贡献结果 |
| `skippedReason` | 通道跳过原因；非空时表示通道被策略跳过而未执行 |
| `errorSummary` | 错误摘要；非空时表示通道执行异常，含错误信息 |
| `timeout` | 是否超时；由 controller 根据 status==TIMEOUT 计算，非数据库字段 |
| `zeroHit` | 是否零命中；由 controller 根据 status==SUCCESS && hitCount==0 计算，非数据库字段 |

> **注意**：`timeout` 和 `zeroHit` 是计算字段，由 `AdminQueryRetrievalAuditController.toChannelRunResponse()` 从 `RetrievalChannelRunStatus` 枚举推导，非持久化字段。它们的 getter 是简单的 `return timeout/zeroHit;`，可使用 Lombok @Getter 生成。

#### AdminQueryRetrievalChannelHitResponse（Response）
- 添加类级 `@Getter`，删除 20 个手写 getter
- `isIncludedInFused()` 对应 `boolean includedInFused`，Lombok 生成一致
- 关键字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| **标识** | |
| `hitId` | 命中记录主键 |
| `runId` | 所属 run 主键 |
| `channelName` | 来源通道名称 |
| **排序与融合** | |
| `hitRank` | 通道内排序位置（1-based） |
| `fusedRank` | RRF 融合后的排序位置；null 表示未进入融合 |
| `includedInFused` | 是否进入最终融合结果；false 时 fusedRank 为 null，该命中被 RRF 淘汰 |
| `channelWeight` | 该通道在 RRF 融合时的权重 |
| **内容** | |
| `evidenceType` | 证据类型（如 article / source_chunk / fact_card） |
| `articleKey` | 关联文章唯一键 |
| `conceptId` | 概念标识 |
| `title` | 文章标题 |
| `score` | 通道内打分（原始分数） |
| `factCardId` | Fact Card 主键；evidenceType 非 fact_card 时为 null |
| `cardType` | Fact Card 类型；null 表示非 fact_card |
| `reviewStatus` | 文章/Fact Card 的审查状态 |
| `confidence` | Fact Card 置信度；null 表示非 fact_card 或无置信度 |
| **JSON 字段** | |
| `sourceChunkIdsJson` | Source Chunk ID JSON 数组；**可能较大** |
| `sourcePathsJson` | 来源路径 JSON 数组；**可能较大** |
| `metadataJson` | 扩展元数据 JSON；**可能较大** |
| `createdAt` | 记录创建时间 |

---

## 六、字段风险与运行影响说明

### 6.1 高风险字段（用户数据 / 审计 / 大文本）

| 字段 | 所属类 | 风险说明 |
|---|---|---|
| `question` | CreateRequest, FeedbackResponse, AuditRunResponse | 用户原始问题文本，可能含 PII 或敏感查询内容 |
| `answerSummary` | CreateRequest, FeedbackResponse | 系统答案摘要，可能含生成内容 |
| `comment`（用户） | CreateRequest, FeedbackResponse | 用户自由填写的反馈说明，不可控内容 |
| `comment`（处理） | HandleRequest, FeedbackResponse, FeedbackAuditResponse | 管理员处理说明，审计字段 |
| `reportedBy` | CreateRequest, FeedbackResponse | 反馈提交人标识，审计字段 |
| `handledBy` | HandleRequest, FeedbackResponse | 处理人标识，审计字段 |
| `operatedBy` | FeedbackAuditResponse | 操作人标识，审计字段 |
| `resolutionComment` | FeedbackResponse | 处理结果说明，审计字段 |
| `errorSummary` | ChannelRunResponse | 通道错误摘要，可能含异常栈或后端错误信息 |
| `channelRunSummaryJson` | AuditRunResponse | 通道运行摘要原始 JSON，可能很大 |
| `metadataJson` | FeedbackAuditResponse, ChannelHitResponse | 扩展元数据 JSON，可能很大 |
| `sourceChunkIdsJson` / `sourcePathsJson` | ChannelHitResponse | JSON 数组字符串，可能较大 |

### 6.2 中等风险字段（影响诊断和前端展示）

| 字段 | 所属类 | 影响 |
|---|---|---|
| `feedbackType` | CreateRequest, FeedbackResponse | 驱动反馈分类、优先级和前端展示样式 |
| `status` | FeedbackResponse | 驱动处理标签和操作按钮；pending→resolved/dismissed 状态流转 |
| `action` | FeedbackAuditResponse | 记录操作类型，用于审计历史展示 |
| `previousStatus` / `nextStatus` | FeedbackAuditResponse | 状态流转记录 |
| `coverageStatus` | AuditRunResponse | 检索覆盖状态诊断：empty 表示检索完全失败 |
| `status` (channel) | ChannelRunResponse | 通道运行状态诊断 |
| `fusedHitCount` / `channelCount` | AuditRunResponse | 检索质量诊断指标 |
| `hitRank` / `fusedRank` / `includedInFused` | ChannelHitResponse | 排序和融合诊断；fusedRank=null 时表示该命中被淘汰 |
| `channelWeight` | ChannelHitResponse | 通道权重对排序的影响 |
| `timeout` / `zeroHit` | ChannelRunResponse | 计算字段，用于通道诊断高亮 |
| `rewriteApplied` | AuditRunResponse | 是否执行了 query 改写 |

### 6.3 低风险字段（标识/时间戳/只读展示）

| 字段 | 所属类 | 说明 |
|---|---|---|
| `id` / `queryId` / `runId` / `hitId` / `feedbackId` | 多处 | 标识符，只读 |
| `articleKeys` / `sourcePaths` | CreateRequest, FeedbackResponse | 关联标识列表 |
| `createdAt` / `updatedAt` / `handledAt` / `operatedAt` | 多处 | 时间戳 |
| `normalizedQuestion` / `retrievalQuestion` | AuditRunResponse | Query 处理链产物展示 |
| `versionTag` / `strategyTag` / `questionTypeTag` / `answerShape` / `retrievalMode` / `retrievalStrategyRef` | AuditRunResponse | 标签和引用展示 |
| `channelName` / `durationMillis` / `hitCount` / `skippedReason` | ChannelRunResponse | 通道运行指标展示 |
| `evidenceType` / `articleKey` / `conceptId` / `title` / `score` / `factCardId` / `cardType` / `reviewStatus` / `confidence` | ChannelHitResponse | 命中内容摘要展示 |

### 6.4 @Data/toString 泄露风险

B9 所有 11 个类当前均**无 `@Data`**。但以下类如果未来错误引入 `@Data` 会产生严重隐私/安全风险：

| 类 | 风险字段 | 后果 |
|---|---|---|
| `AdminQueryFeedbackCreateRequest` | `question`, `answerSummary`, `comment`, `reportedBy` | toString() 输出用户查询内容、答案、反馈文本、提交人 |
| `AdminQueryFeedbackHandleRequest` | `handledBy`, `comment` | toString() 输出处理人、处理意见 |
| `AdminQueryFeedbackResponse` | 同上 + `resolutionComment` | toString() 输出完整反馈内容和处理记录 |
| `AdminQueryFeedbackAuditResponse` | `comment`, `operatedBy`, `metadataJson` | toString() 输出审计意见、操作人、大 JSON |
| `AdminQueryRetrievalAuditRunResponse` | `question`, `channelRunSummaryJson` | toString() 输出用户问题和通道摘要 JSON |
| `AdminQueryRetrievalChannelHitResponse` | `metadataJson`, `sourceChunkIdsJson`, `sourcePathsJson` | toString() 输出大 JSON 字段 |

**本轮禁止给任何 B9 类引入 `@Data`。**

---

## 七、给 agentA 的下一轮提示词草案（B9a）

```
交给 agentA。

本轮任务：对 B9a 的 6 个 query feedback DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_query_feedback_retrieval_audit_dto_contract_analysis_report.md

## 修改范围（6 个文件）

### Request 类（添加 @Getter + @Setter，保留无参构造）

1. AdminQueryFeedbackCreateRequest.java
   - 添加 @Getter + @Setter，删除 8 个手写 getter + setter
   - 禁止引入 @Data（question/comment/reportedBy 用户数据/审计字段）
   - 8 字段补 Javadoc（审查报告 5.2 节）

2. AdminQueryFeedbackHandleRequest.java
   - 添加 @Getter + @Setter，删除 2 个手写 getter + setter
   - 禁止引入 @Data（handledBy/comment 审计字段）
   - 2 字段补 Javadoc

### Response 类（添加 @Getter，删除手写 getter，保留构造器）

3. AdminQueryFeedbackResponse.java
   - 添加类级 @Getter，删除 15 个手写 getter
   - 禁止引入 @Data（含 comment/resolutionComment/reportedBy/handledBy）
   - 15 字段补 Javadoc
   - 保留全参构造器

4. AdminQueryFeedbackListResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

5. AdminQueryFeedbackDetailResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

6. AdminQueryFeedbackAuditResponse.java
   - 添加类级 @Getter，删除 9 个手写 getter
   - 禁止引入 @Data（含 comment/operatedBy/metadataJson）
   - 9 字段补 Javadoc

## 禁止事项

- 禁止修改 controller / service / domain / infra / governance 文件
- 禁止修改构造器签名或逻辑
- 禁止修改字段类型、名称、访问修饰符
- 禁止给任何类引入 @Data（所有类都含用户数据或审计字段）
- 禁止修改 feedback 处理流程、状态流转
- 禁止混入 B9b 或 B10 文件

## 验收门槛

- mvn compile -pl . -q 通过
- 全量 mvn test 通过
- redline 无新增 BLOCKER
- 自查：所有 boolean getter 命名与 Lombok 一致（无 B8a 式的 getIsHotspot 问题）

## 完成后

1. 回写计划文件：B9a 状态 → "已完成"
2. 输出 B9a_fix_result_report.md
3. 不 stage、不 commit、不 push
```

---

## 八、给 agentA 的下一轮提示词草案（B9b）

```
交给 agentA。

本轮任务：对 B9b 的 5 个 retrieval audit DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_query_feedback_retrieval_audit_dto_contract_analysis_report.md

## 修改范围（5 个文件，全部为 Response）

1. AdminQueryRetrievalAuditListResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

2. AdminQueryRetrievalAuditDetailResponse.java
   - 添加类级 @Getter，删除 7 个手写 getter
   - 7 字段补 Javadoc（审查报告 5.3 节）

3. AdminQueryRetrievalAuditRunResponse.java
   - 添加类级 @Getter，删除 21 个手写 getter
   - 保留构造器（含 List.copyOf 防御性拷贝）
   - 21 字段补 Javadoc（含 query 处理链、检索模式、命中统计、通道详情各分组语义）
   - 禁止引入 @Data（question/channelRunSummaryJson 大文本字段）

4. AdminQueryRetrievalChannelRunResponse.java
   - 添加类级 @Getter，删除 8 个手写 getter
   - 8 字段补 Javadoc，注意标注 timeout/zeroHit 为计算字段（非持久化）
   - 禁止引入 @Data（errorSummary 可能含错误详情）

5. AdminQueryRetrievalChannelHitResponse.java
   - 添加类级 @Getter，删除 20 个手写 getter
   - 20 字段补 Javadoc（含排序/融合语义、JSON 大字段标注）
   - 禁止引入 @Data（metadataJson/sourceChunkIdsJson/sourcePathsJson 大 JSON）

## 禁止事项

- 禁止修改 controller / service / domain / infra 文件
- 禁止修改构造器签名或逻辑（含防御性拷贝）
- 禁止修改字段类型、名称
- 禁止给任何类引入 @Data
- 禁止修改 retrieval audit 采集逻辑、RRF、query 改写、通道调度
- 禁止混入 B9a 或 B10 文件

## 验收门槛

- mvn compile -pl . -q 通过
- 全量 mvn test 通过
- redline 无新增 BLOCKER
- 自查：AdminQueryRetrievalAuditRunResponse.channelRuns 防御性拷贝保留

## 完成后

1. 回写计划文件：B9b 状态 → "已完成"
2. 输出 B9b_fix_result_report.md
3. 不 stage、不 commit、不 push
```

---

## 九、审查结论

- B9 共 11 个候选 DTO，拆分为 **B9a（6 个 query feedback DTO）** 和 **B9b（5 个 retrieval audit DTO）**。
- **好消息**：B9 所有 boolean getter 均使用标准 `isXxx()` 命名，**无 B8a 式的 Lombok 命名不一致问题**，所有字段均可安全使用类级 `@Getter`。
- 0 个 `@Data`（B9 数据集干净），但 **全部 11 个类**都因用户数据/审计/大文本字段被标记为未来禁止引入 `@Data`。
- 总可删除手写 getter：B9a 38 个 + B9b 58 个 = **96 个**；总可删除手写 setter：**10 个**（全部在 B9a）。
- `AdminQueryRetrievalAuditRunResponse` 构造器有 `channelRuns` 的 `List.copyOf` 防御性拷贝，需保留。
- `AdminQueryRetrievalChannelRunResponse.timeout` 和 `zeroHit` 是计算字段（由 controller 从枚举推导），但 getter 是简单的字段返回，可用 Lombok `@Getter` 生成。
- 高风险字段（用户问题、答案、反馈文本、审计人、错误摘要、大 JSON）本轮仅标注契约语义，不改行为。
