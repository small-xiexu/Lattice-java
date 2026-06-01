# B8 Article / Fact Card / Quality DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B8 — `api/admin` article / fact card / quality DTO

---

## 一、拆分建议：B8 → B8a + B8b

18 个候选类远超每轮 10 个的上限，必须拆分。拆分策略按业务聚合度和嵌套引用关系：

| 子批次 | 候选数 | 范围 | 拆分理由 |
|---|---|---|---|
| **B8a** | **14** | Article DTO（含 AdminArticleTitleProfile） | article 为核心域，Detail/Summary/Review/Rollback/Hotspot/Snapshot/UsageStats 共享嵌套类型，不宜跨批次切割 |
| **B8b** | **4** | Fact Card + Quality DTO | Fact card 和 quality 是独立子域，与 article 无嵌套引用关系 |

---

## 二、B8a 纳入文件清单（14 个类）

| # | 类名 | 类型 | Lombok 现状 | 手写 getter | 手写 setter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|---|
| 1 | `AdminArticleCorrectionRequest` | Request | 无 | 1 | 1 | 默认无参 | — |
| 2 | `AdminArticleDetailResponse` | Response | 无 | 24 | 0 | 手写全参 | **2 个 boolean getter 命名与 Lombok 不一致** |
| 3 | `AdminArticleHotspotRefreshRequest` | Request | 无 | 2 | 2 | 默认无参 | Jakarta `@Valid` + `@Min`/`@Max` |
| 4 | `AdminArticleHotspotRefreshResponse` | Response | 无 | 5 | 0 | 手写全参 | 构造器有 `List.copyOf` 防御性拷贝 |
| 5 | `AdminArticleListResponse` | Response | 无 | 2 | 0 | 手写全参 | 列表容器 |
| 6 | `AdminArticleReviewAuditListResponse` | Response | 无 | 2 | 0 | 手写全参 | 列表容器 |
| 7 | `AdminArticleReviewAuditResponse` | Response | 无 | 11 | 0 | 手写全参 | `reviewedBy`/`comment` 审计字段 |
| 8 | `AdminArticleReviewRequest` | Request | 无 | 5 | 5 | 默认无参 | `reviewedBy`/`comment` 审计字段 |
| 9 | `AdminArticleReviewResponse` | Response | 无 | 8 | 0 | 手写全参 | `reviewedBy` 审计字段 |
| 10 | `AdminArticleRollbackRequest` | Request | 无 | 4 | 4 | 默认无参 | **`getArticleId()` 为计算 getter（articleId fallback conceptId）** |
| 11 | `AdminArticleSnapshotListResponse` | Response | 无 | 3 | 0 | 手写全参 | getter 无 Javadoc；items 直接暴露 `ArticleSnapshotRecord`（持久层类型） |
| 12 | `AdminArticleSummaryResponse` | Response | 无 | 18 | 0 | 手写全参 | **2 个 boolean getter 命名与 Lombok 不一致** |
| 13 | `AdminArticleTitleProfile` | 嵌套 DTO | 无 | 4 | 0 | 手写全参 | getter 无 Javadoc |
| 14 | `AdminArticleUsageStatsResponse` | Response | 无 | 9 | 0 | 手写全参 | 构造器有 `List.copyOf` 防御性拷贝 |

**B8a 统计**：4 Request + 10 Response/嵌套 DTO。简单 getter 可删除 94 个（已扣除 4 个不可替换的计算/命名不一致 getter），setter 可删除 12 个。

---

## 三、B8b 纳入文件清单（4 个类）

| # | 类名 | 类型 | Lombok 现状 | 手写 getter | 手写 setter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|---|
| 1 | `AdminFactCardItemResponse` | Response | 无 | 18 | 0 | 手写全参 | `itemsJson`/`evidenceText` 可能很大 |
| 2 | `AdminFactCardListResponse` | Response | 无 | 2 | 0 | 手写全参 | 列表容器 |
| 3 | `AdminFactCardSummaryResponse` | Response | 无 | 5 | 0 | 手写全参 | 含 `Map<String, Integer>` |
| 4 | `AdminQualityResponse` | Response | 无 | 2 | 0 | 手写全参 | getter 无 Javadoc；包装 domain 类型 |

**B8b 统计**：0 Request + 4 Response。简单 getter 可删除 27 个。

---

## 四、明确排除文件清单及理由

| 排除文件 | 理由 | 归属批次 |
|---|---|---|
| `AdminArticleController.java` | Controller 本体 | 不纳入 |
| `AdminFactCardController.java` | Controller 本体 | 不纳入 |
| `AdminQualityController.java` | Controller 本体 | 不纳入 |
| `AdminArticleReviewRequest.java` 对应的 B7 compile review 队列 DTO | 已归属 B7 | B7 |
| 所有 B9 文件 | query feedback / retrieval audit DTO | B9 |
| 所有 B10 文件 | overview / pending / processing task DTO | B10 |
| 所有 B11 文件 | controller 内部 DTO | B11 |
| `LifecycleTransitionResult` | 位于 `governance/domain` 包 | B19 |
| `ArticleManualReviewRequest` / `ArticleManualReviewResult` | 位于 `governance` 包 | B19 |
| `ArticleCorrectionResult` | 位于 `governance` 包 | B19 |
| `ArticleHotspotRefreshResult` | 位于 `governance` 包 | B19 |
| `QualityMetricsReport` / `QualityMetricsTrend` | 位于 `governance` 包 | B19 |
| `ArticleRecord` / `ArticleReviewAuditRecord` / `ArticleSnapshotRecord` / `ArticleUsageStatsRecord` / `FactCardRecord` | 位于 `infra/persistence` 包 | 明确排除（JPA/entity-like） |

---

## 五、每个纳入类的 Lombok/Javadoc 改造建议

### 5.1 关键阻断问题：Boolean getter 命名不一致

以下 4 个 getter 的方法名与 Lombok 默认生成**不一致**，不可被 Lombok 替代：

| 类 | 字段名 | 字段类型 | 当前 getter | Lombok 会生成 | Jackson 属性名变化 |
|---|---|---|---|---|---|
| `AdminArticleDetailResponse` | `hotspot` | `boolean` | `getIsHotspot()` | `isHotspot()` | `"isHotspot"` → `"hotspot"` |
| `AdminArticleDetailResponse` | `requiresResultVerification` | `boolean` | `getRequiresResultVerification()` | `isRequiresResultVerification()` | `"requiresResultVerification"` → `"requiresResultVerification"`（可能相同） |
| `AdminArticleSummaryResponse` | `hotspot` | `boolean` | `getIsHotspot()` | `isHotspot()` | `"isHotspot"` → `"hotspot"` |
| `AdminArticleSummaryResponse` | `requiresResultVerification` | `boolean` | `getRequiresResultVerification()` | `isRequiresResultVerification()` | 同上 |

**处置方案（推荐）**：在这两个类上使用类级 `@Getter`，但对 `hotspot` 和 `requiresResultVerification` 两个字段添加 `@Getter(AccessLevel.NONE)`，保留手写的 `getIsHotspot()` 和 `getRequiresResultVerification()` 方法。这样既不会改变 JSON 序列化行为，也能让其余 22（Detail）/ 16（Summary）个简单 getter 被 Lombok 覆盖。

### 5.2 关键阻断问题：AdminArticleRollbackRequest.getArticleId() 计算 getter

`getArticleId()` 包含 fallback 逻辑（`articleId` 为空时返回 `conceptId`），不可被 Lombok 替代。处置方案：对 `articleId` 字段加 `@Getter(AccessLevel.NONE)`，保留手写 `getArticleId()`。

### 5.3 B8a 各类详细改造建议

#### AdminArticleCorrectionRequest（Request）
- 添加 `@Getter` + `@Setter`，删除 1 个手写 getter + 1 个手写 setter
- 字段 Javadoc：`correctionSummary` — 纠错摘要文本；描述文章存在的事实或表述问题及修正建议

#### AdminArticleDetailResponse（Response）⚠️
- 添加类级 `@Getter`
- `hotspot` 字段加 `@Getter(AccessLevel.NONE)`，保留手写 `getIsHotspot()`
- `requiresResultVerification` 字段加 `@Getter(AccessLevel.NONE)`，保留手写 `getRequiresResultVerification()`
- 删除其余 22 个手写 getter
- 保留全参构造器
- 关键字段 Javadoc：

| 字段 | 需补充 |
|---|---|
| `sourceId` | 资料源主键；null 表示多源或无固定 source |
| `articleKey` | 文章唯一键（编译生成，跨 source 稳定） |
| `conceptId` | 概念标识（编译输入，用于跨 source 去重） |
| `title` | 文章标题 |
| `content` | 文章正文全文；可能为长文本；仅管理侧预览用 |
| `lifecycle` | 文章生命周期状态（如 active / deprecated / archived） |
| `compiledAt` | 最近编译时间；null 表示原始录入 |
| `createdAt` | 首次入库时间 |
| `updatedAt` | 最近入库时间 |
| `summary` | 文章摘要；null 表示未生成 |
| `reviewStatus` | 审查状态（如 accepted / needs_human_review / published）；驱动前端展示审查标签 |
| `riskLevel` | 风险等级（如 low / medium / high）；影响前端展示的警示颜色 |
| `riskReasons` | 风险原因列表；与 riskLevel 配合解释风险来源 |
| `hotspot` | 是否热点文章；基于 usage stats 热度分动态计算；**getter 保留手写 `getIsHotspot()`** |
| `requiresResultVerification` | 是否需要结果抽检；**getter 保留手写 `getRequiresResultVerification()`** |
| `confidence` | 置信度标签；反映编译/生成质量评估 |
| `sourceCount` | 来源文件数；由 sourcePaths.size() 计算 |
| `primarySourcePath` | 首个来源文件路径 |
| `sourcePaths` | 全部来源文件路径列表 |
| `referentialKeywords` | 文章关联的明确性关键词 |
| `dependsOn` | 依赖的文章 conceptId 列表 |
| `related` | 相关的文章 conceptId 列表 |
| `metadataJson` | 扩展元数据 JSON；可能较大 |
| `titleProfile` | 标题画像（来源标题/切分标题/代表标题/生成模式）；null 时前端降级展示 title |

#### AdminArticleHotspotRefreshRequest（Request）
- 添加 `@Getter` + `@Setter`，删除 2 个手写 getter + setter
- 保留 Jakarta `@Min`/`@Max` 注解
- 字段 Javadoc：
  - `heatScoreThreshold` — 热度阈值；usage stats heatScore >= 此值视为热点候选；null 时 controller 使用 DEFAULT_HEAT_SCORE_THRESHOLD
  - `limit` — 返回候选数量上限（1-200）；用于热点标记和抽检队列生成

#### AdminArticleHotspotRefreshResponse（Response）
- 添加类级 `@Getter`，删除 5 个手写 getter
- 保留构造器（含 `List.copyOf` 防御性拷贝）
- 字段 Javadoc：
  - `rebuiltStatsCount` — 本次重建的 usage stats 数量
  - `hotspotCandidateCount` — 满足热度阈值的候选数量
  - `updatedArticleCount` — 实际更新 hotspot 标记的文章数
  - `heatScoreThreshold` — 本次刷新使用的热度阈值
  - `candidates` — 热点候选列表（usage stats 详情）；不可变

#### AdminArticleListResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段：`count`（当前返回条目数）、`items`（文章摘要列表）

#### AdminArticleReviewAuditListResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段：`count`（审计记录数）、`items`（审计条目列表）

#### AdminArticleReviewAuditResponse（Response）
- 添加类级 `@Getter`，删除 11 个手写 getter
- **禁止引入 `@Data`**：含 `reviewedBy`（审计人员）、`comment`（审计意见）
- 关键字段 Javadoc：
  - `id` — 审计记录主键
  - `action` — 复核动作（approve / request_changes）
  - `previousReviewStatus` — 操作前审查状态
  - `nextReviewStatus` — 操作后审查状态；与 previousReviewStatus 对比可知状态流转
  - `comment` — 复核意见；审批或驳回时填写的原因
  - `reviewedBy` — 复核人标识；用于审计追溯
  - `reviewedAt` — 复核时间
  - `metadataJson` — 扩展上下文 JSON；可能包含复核时的附加信息

#### AdminArticleReviewRequest（Request）
- 添加 `@Getter` + `@Setter`，删除 5 个手写 getter + 5 个手写 setter
- **禁止引入 `@Data`**：含 `reviewedBy`、`comment`
- 字段 Javadoc：
  - `sourceId` — 资料源主键；可选，用于限定文章范围
  - `reviewedBy` — 复核人标识；用于审计
  - `comment` — 复核意见；approve 时可选，request-changes 时应填写修改要求
  - `expectedReviewStatus` — 乐观锁期望状态；与当前记录不一致时操作被拒绝
  - `correctionSummary` — 修正建议摘要；request-changes 时说明需修正的内容

#### AdminArticleReviewResponse（Response）
- 添加类级 `@Getter`，删除 8 个手写 getter
- 字段 Javadoc：
  - `previousReviewStatus` — 操作前审查状态
  - `reviewStatus` — 操作后审查状态
  - `reviewedBy` — 操作人
  - `reviewedAt` — 操作时间
  - `auditId` — 审计记录主键；可用于查询完整审计历史

#### AdminArticleRollbackRequest（Request）⚠️
- 添加类级 `@Getter` + `@Setter`
- `articleId` 字段加 `@Getter(AccessLevel.NONE)`，**保留手写 `getArticleId()`**（计算 getter：articleId 为空时 fallback conceptId）
- 删除其余 3 个简单 getter + 4 个简单 setter
- 字段 Javadoc：
  - `articleId` — 文章唯一键或概念标识；**getter 含 fallback 逻辑**
  - `conceptId` — 概念标识；articleId 为空时作为回滚目标标识
  - `sourceId` — 资料源主键；可选
  - `snapshotId` — 目标快照主键；回滚会将文章恢复到该快照版本

#### AdminArticleSnapshotListResponse（Response）
- 添加类级 `@Getter`，删除 3 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `conceptId` — 概念标识
  - `count` — 快照数量
  - `items` — 快照条目列表；元素类型为 `ArticleSnapshotRecord`（持久层记录，未经 DTO 包装）
- **注意**：`items` 直接暴露 `ArticleSnapshotRecord`（infra/persistence 层类型），违反分层原则。本轮不做修复，仅标注。

#### AdminArticleSummaryResponse（Response）⚠️
- 添加类级 `@Getter`
- `hotspot` 和 `requiresResultVerification` 字段加 `@Getter(AccessLevel.NONE)`，保留手写 getter
- 删除其余 16 个手写 getter
- 保留全参构造器
- 字段 Javadoc：与 DetailResponse 对应字段相同，注意 `content` 不在 Summary 中。`primarySourceName` 为 Summary 特有（首个来源文件名）。

#### AdminArticleTitleProfile（嵌套 DTO）
- 添加类级 `@Getter`，删除 4 个手写 getter（当前无 Javadoc）
- 保留全参构造器
- 字段 Javadoc：
  - `sourceTitle` — 来源文档中的原始标题；null 表示未提取
  - `anchorTitle` — 文档切分时的锚点标题；null 表示未切分或无锚点
  - `representativeTitle` — 代表标题（综合 sourceTitle 和 title 选取）；用于列表展示
  - `titleGenerationMode` — 标题生成模式（如 LLM_GENERATED / SOURCE_EXTRACTED / LEGACY_UNSET）

#### AdminArticleUsageStatsResponse（Response）
- 添加类级 `@Getter`，删除 9 个手写 getter
- 保留构造器（含 `List.copyOf` 防御性拷贝）
- 字段 Javadoc：
  - `retrievalHitCount` — 检索命中次数；反映文章在 query 检索中的曝光度
  - `citationCount` — 答案引用次数；反映文章在最终回答中的被引用频率
  - `answerFeedbackCount` — 答案反馈次数；含正负反馈
  - `manualMarkCount` — 人工标记次数；管理侧操作（如纠错、复核）的累计计数
  - `heatScore` — 综合热度分；由上述四个指标加权计算，用于热点判定
  - `sourcePaths` — 来源文件路径列表；不可变

### 5.4 B8b 各类详细改造建议

#### AdminFactCardItemResponse（Response）
- 添加类级 `@Getter`，删除 18 个手写 getter
- 保留全参构造器
- 关键字段 Javadoc：
  - `cardType` — Fact Card 类型（枚举名）；驱动前端展示样式
  - `answerShape` — 答案形态（枚举名）；影响卡片的回答呈现方式
  - `claim` — 事实结论文本
  - `itemsJson` — 结构化证据条目 JSON；**可能较大**
  - `evidenceText` — 证据文本全文；**可能为长文本**
  - `confidence` — 置信度（0.0-1.0）
  - `reviewStatus` — 审查状态（数据库值；非枚举名）
  - `contentHash` — 内容哈希；用于检测内容变更
  - `sourceChunkIds` / `articleIds` — 关联 source chunk 和 article 主键列表

#### AdminFactCardListResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter

#### AdminFactCardSummaryResponse（Response）
- 添加类级 `@Getter`，删除 5 个手写 getter
- 字段 Javadoc：
  - `countByCardType` — 按 Fact Card 类型分组的计数；key 为 FactCardType 枚举名
  - `countByReviewStatus` — 按审查状态分组的计数；key 为数据库状态值
  - `sourceReferenceMissingCount` — source chunk 回指缺失的 card 数；> 0 表示数据完整性有问题
  - `lowConfidenceCount` — 低置信度 card 数（confidence < 阈值）

#### AdminQualityResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter（当前无 Javadoc）
- 保留全参构造器
- 字段 Javadoc：
  - `report` — 当前质量指标报告（QualityMetricsReport 领域对象）；含各类计数、比率、状态
  - `trend` — 指定时间窗质量趋势（QualityMetricsTrend）；含多日指标序列
- **注意**：两个字段直接暴露 domain 层类型，但本轮不做包装改造

---

## 六、配置字段风险与运行影响说明

### 6.1 高风险字段（修改会立即影响 article 审查/回滚/热点判定）

| 字段 | 所属类 | 影响链路 | 风险说明 |
|---|---|---|---|
| `expectedReviewStatus` | ReviewRequest | 乐观锁并发控制 | 与实际状态不匹配时操作被拒绝；错误值导致审批失败 |
| `reviewedBy` | ReviewRequest, ReviewAuditResponse | 审计追踪 | 记录人工操作者；用于事后追溯和责任认定 |
| `comment` | ReviewRequest, ReviewAuditResponse | 审计追踪 | 审批/驳回原因；可能含人工主观评价 |
| `snapshotId` | RollbackRequest | 回滚目标 | 错误快照 ID 导致文章回滚到错误版本；不可逆操作 |
| `articleId` (getter fallback) | RollbackRequest | 回滚目标识别 | getArticleId() 的 fallback 逻辑使得传入 null articleId 时自动使用 conceptId；影响回滚目标选择 |
| `heatScoreThreshold` | HotspotRefreshRequest | 热点判定阈值 | 过低导致几乎所有文章被标记为热点；过高导致无文章触发 |
| `correctionSummary` | CorrectionRequest, ReviewRequest | 纠错内容 | 描述文章需要修正的事实问题；可能被持久化到审计记录 |

### 6.2 中等风险字段（影响管理侧展示与决策）

| 字段 | 所属类 | 影响链路 | 风险说明 |
|---|---|---|---|
| `content` | DetailResponse | 响应体积、日志 | 文章全文可能很大；序列化到 JSON 响应显著增加网络传输；不应参与 toString() |
| `metadataJson` | DetailResponse, ReviewAuditResponse | 响应体积 | 可能较大的 JSON 字符串；不应参与 toString() |
| `itemsJson` / `evidenceText` | FactCardItemResponse | 响应体积 | JSON 和文本字段可能很大 |
| `reviewStatus` | DetailResponse, SummaryResponse, FactCardItemResponse | 前端展示 | 驱动前端审查标签颜色和交互按钮 |
| `riskLevel` / `riskReasons` | DetailResponse, SummaryResponse | 前端告警 | 影响文章风险警示展示 |
| `lifecycle` | DetailResponse, SummaryResponse | 生命周期展示 | 影响文章是否在前端可用 |
| `hotspot` / `heatScore` | Detail/Summary/UsageStats | 热点标记 | 影响文章在列表中的热点标识展示 |

### 6.3 低风险字段（纯信息展示/统计）

| 字段 | 所属类 | 说明 |
|---|---|---|
| `sourceId` / `articleKey` / `conceptId` / `cardId` / `id` | 多处 | 标识符，只读展示 |
| `title` / `summary` / `claim` | 多处 | 只读文本展示 |
| `compiledAt` / `createdAt` / `updatedAt` / `reviewedAt` | 多处 | 时间戳展示 |
| `sourceCount` / `sourcePaths` / `primarySourcePath` / `primarySourceName` | Detail/Summary | 来源信息展示 |
| `referentialKeywords` / `dependsOn` / `related` | DetailResponse | 关系图展示 |
| `retrievalHitCount` / `citationCount` / `answerFeedbackCount` / `manualMarkCount` | UsageStatsResponse | 统计指标展示 |
| `confidence` / `contentHash` | FactCardItemResponse | 质量指标展示 |
| `totalCount` / `countByCardType` / `countByReviewStatus` / `sourceReferenceMissingCount` / `lowConfidenceCount` | FactCardSummaryResponse | 统计摘要展示 |
| `report` / `trend` | QualityResponse | 质量报告（domain 对象） |

### 6.4 @Data/toString 泄露风险

B8 所有 18 个类当前均**无 `@Data`**，数据集干净。但以下类如果未来错误引入 `@Data` 会产生风险：

| 类 | 风险字段 | 后果 |
|---|---|---|
| `AdminArticleReviewAuditResponse` | `comment`, `reviewedBy`, `metadataJson` | toString() 输出审计意见、操作人、大文本 |
| `AdminArticleReviewRequest` | `reviewedBy`, `comment` | toString() 输出审计人员标识和意见 |
| `AdminArticleDetailResponse` | `content`, `metadataJson` | toString() 输出文章全文和元数据 JSON |
| `AdminFactCardItemResponse` | `itemsJson`, `evidenceText` | toString() 输出大文本 JSON |
| `AdminArticleRollbackRequest` | `articleId`, `conceptId` | toString() 输出回滚目标路径 |

**本轮禁止给任何 B8 类引入 `@Data`**。

---

## 七、给 agentA 的下一轮提示词草案（B8a）

```
交给 agentA。

本轮任务：对 B8a 的 14 个 article DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_article_factcard_quality_dto_contract_analysis_report.md

## 修改范围（14 个文件）

### Request 类（添加 @Getter + @Setter，保留无参构造，禁止引入 @Data）

1. AdminArticleCorrectionRequest.java
   - 添加 @Getter + @Setter，删除 1 个手写 getter + setter
   - 1 字段补 Javadoc（审查报告 5.3 节）

2. AdminArticleHotspotRefreshRequest.java
   - 添加 @Getter + @Setter，删除 2 个手写 getter + setter
   - 保留 Jakarta @Min/@Max 注解
   - 2 字段补 Javadoc

3. AdminArticleReviewRequest.java
   - 添加 @Getter + @Setter，删除 5 个手写 getter + setter
   - 禁止引入 @Data（reviewedBy/comment 审计字段）
   - 5 字段补 Javadoc

4. AdminArticleRollbackRequest.java ⚠️
   - 添加 @Getter + @Setter
   - articleId 字段加 @Getter(AccessLevel.NONE)
   - 保留手写 getArticleId()（计算 getter：articleId→conceptId fallback）
   - 删除 3 个简单 getter + 4 个 setter
   - 4 字段补 Javadoc

### Response 类（添加 @Getter，删除手写 getter，保留构造器）

5. AdminArticleDetailResponse.java ⚠️ 关键
   - 添加类级 @Getter
   - hotspot 字段加 @Getter(AccessLevel.NONE)，保留手写 getIsHotspot()
   - requiresResultVerification 字段加 @Getter(AccessLevel.NONE)，保留手写 getRequiresResultVerification()
   - 删除其余 22 个手写 getter
   - 24 字段补 Javadoc（审查报告 5.3 节）
   - 保留全参构造器

6. AdminArticleHotspotRefreshResponse.java
   - 添加类级 @Getter，删除 5 个手写 getter
   - 保留构造器（含 List.copyOf 防御性拷贝）
   - 5 字段补 Javadoc

7. AdminArticleListResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

8. AdminArticleReviewAuditListResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

9. AdminArticleReviewAuditResponse.java
   - 添加类级 @Getter，删除 11 个手写 getter
   - 禁止引入 @Data（含 reviewedBy/comment/metadataJson）
   - 11 字段补 Javadoc

10. AdminArticleReviewResponse.java
    - 添加类级 @Getter，删除 8 个手写 getter
    - 8 字段补 Javadoc

11. AdminArticleSnapshotListResponse.java
    - 添加类级 @Getter，删除 3 个手写 getter（当前无 Javadoc）
    - 3 字段补 Javadoc
    - 注意：items 直接暴露 ArticleSnapshotRecord 是已知分层问题，本轮不修复

12. AdminArticleSummaryResponse.java ⚠️ 关键
    - 添加类级 @Getter
    - hotspot 和 requiresResultVerification 字段加 @Getter(AccessLevel.NONE)，保留手写 getter
    - 删除其余 16 个手写 getter
    - 18 字段补 Javadoc
    - 保留全参构造器

13. AdminArticleTitleProfile.java
    - 添加类级 @Getter，删除 4 个手写 getter（当前无 Javadoc）
    - 4 字段补 Javadoc
    - 保留全参构造器

14. AdminArticleUsageStatsResponse.java
    - 添加类级 @Getter，删除 9 个手写 getter
    - 保留构造器（含 List.copyOf 防御性拷贝）
    - 9 字段补 Javadoc

## 禁止事项

- 禁止修改任何 controller 文件
- 禁止修改任何 service / domain / infra / config / governance 文件
- 禁止修改 test 文件
- 禁止修改构造器签名或逻辑（含防御性拷贝）
- 禁止修改字段类型、名称、访问修饰符
- 禁止修改 Jakarta validation 注解
- 禁止修改 ArticleSnapshotListResponse.items 的类型（已知分层问题）
- 禁止替换 AdminArticleDetailResponse.getIsHotspot() / getRequiresResultVerification() 为 Lombok 版本
- 禁止替换 AdminArticleSummaryResponse.getIsHotspot() / getRequiresResultVerification() 为 Lombok 版本
- 禁止替换 AdminArticleRollbackRequest.getArticleId() 为 Lombok 版本
- 禁止给任何类引入 @Data（审计字段/大文本字段风险）
- 禁止修改 article 审查状态机、生命周期流转、热点判定逻辑
- 禁止修改 scripts/scan-redline.sh、special_cases_report.md、redline allowlist
- 禁止混入 B8b 或 B9/B10 文件

## 验收门槛

- 编译通过（mvn compile -pl . -q）
- 全量 mvn test 通过
- redline 无新增 BLOCKER
- 自查：AdminArticleDetailResponse.hotspot getter 方法名仍为 getIsHotspot()（非 isHotspot()）
- 自查：AdminArticleSummaryResponse.hotspot getter 方法名仍为 getIsHotspot()
- 自查：AdminArticleRollbackRequest.getArticleId() fallback 逻辑不变
- 自查：无字段翻译式空泛注释

## 完成后

1. 回写计划文件：B8a 状态 → "已完成"，备注实际修改
2. 输出 B8a_fix_result_report.md
3. 不 stage、不 commit、不 push
```

---

## 八、给 agentA 的下一轮提示词草案（B8b）

```
交给 agentA。

本轮任务：对 B8b 的 4 个 fact card + quality DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_article_factcard_quality_dto_contract_analysis_report.md

## 修改范围（4 个文件，全部为 Response）

1. AdminFactCardItemResponse.java
   - 添加类级 @Getter，删除 18 个手写 getter
   - 禁止引入 @Data（itemsJson/evidenceText 可能为大文本）
   - 18 字段补 Javadoc（审查报告 5.4 节）
   - 保留全参构造器

2. AdminFactCardListResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

3. AdminFactCardSummaryResponse.java
   - 添加类级 @Getter，删除 5 个手写 getter
   - 5 字段补 Javadoc

4. AdminQualityResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter（当前无 Javadoc）
   - 2 字段补 Javadoc
   - 保留全参构造器

## 禁止事项

- 禁止修改 controller / service / domain / infra 文件
- 禁止修改构造器签名或逻辑
- 禁止修改字段类型
- 禁止给任何类引入 @Data
- 禁止修改 fact card 生成逻辑、质量指标计算
- 禁止混入 B8a 或 B9/B10 文件

## 完成后

1. 回写计划文件：B8b 状态 → "已完成"
2. 输出 B8b_fix_result_report.md
3. 不 stage、不 commit、不 push
```

---

## 九、审查结论

- B8 共 18 个候选 DTO，必须拆分为 **B8a（14 个 article DTO）** 和 **B8b（4 个 fact card + quality DTO）**。
- **关键阻断问题**：`AdminArticleDetailResponse` 和 `AdminArticleSummaryResponse` 中 `hotspot`/`requiresResultVerification` 的 getter 命名（`getIsHotspot()`/`getRequiresResultVerification()`）与 Lombok 默认生成不一致，需 `@Getter(AccessLevel.NONE)` 排除后保留手写 getter。
- **计算 getter**：`AdminArticleRollbackRequest.getArticleId()` 含 fallback 逻辑，同样需排除 Lombok。
- 0 个 `@Data` 需降级（B8 数据集干净），但 8 个类因含审计/大文本字段被标记为未来禁止引入 `@Data`。
- 总可删除手写 getter：B8a 94 个 + B8b 27 个 = **121 个**；总可删除手写 setter：**12 个**（全部在 B8a）。
- 高风险字段（`expectedReviewStatus`、`snapshotId`、`heatScoreThreshold`、`reviewedBy`/`comment`、`correctionSummary`）本轮仅标注契约语义，不改行为。
- `AdminArticleSnapshotListResponse.items` 直接暴露 `ArticleSnapshotRecord`（持久层类型），是已知分层问题，本轮标注但不修复。
