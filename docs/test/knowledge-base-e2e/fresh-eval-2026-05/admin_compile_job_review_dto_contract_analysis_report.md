# B7 Compile Job / Review DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B7 — `api/admin` compile job / compile review DTO

---

## 一、B7 纳入文件清单（10 个类）

| # | 类名 | 类型 | Lombok 现状 | 手写 getter 数 | 手写 setter 数 | 构造器 | 备注 |
|---|---|---|---|---|---|---|---|
| 1 | `AdminCompileJobRequest` | Request | 无 | 5 | 5 | 默认无参 | isAsync() 为计算 getter，不可替换 |
| 2 | `AdminCompileJobResponse` | Response | 无 | 23 | 0 | 手写全参 | 嵌套 AdminCompileReviewSummaryResponse |
| 3 | `AdminCompileJobListResponse` | Response | 无 | 2 | 0 | 手写全参 | 列表容器 |
| 4 | `AdminCompileReviewConfigRequest` | Request | `@Data` `@NoArgsConstructor` `@AllArgsConstructor` | 0（Lombok 生成） | 0（Lombok 生成） | Lombok 全参 | 需降级 @Data，operator 为审计字段 |
| 5 | `AdminCompileReviewConfigResponse` | Response | 无 | 9 | 0 | 手写全参 | getter 缺少 Javadoc，与项目风格不一致 |
| 6 | `AdminCompileReviewQueueActionRequest` | Request | 无 | 3 | 3 | 默认无参 | reviewedBy/comment 含审计信息 |
| 7 | `AdminCompileReviewQueueActionResponse` | Response | 无 | 3 | 0 | 手写全参 | 嵌套 item + 状态变更 + 审计 ID |
| 8 | `AdminCompileReviewQueueItemResponse` | Response | 无 | 22 | 0 | 手写全参 | content 可能很大，reviewComment/reviewedBy 为审计字段 |
| 9 | `AdminCompileReviewQueueListResponse` | Response | 无 | 2 | 0 | 手写全参 | 列表容器 |
| 10 | `AdminCompileReviewSummaryResponse` | Response | 无 | 15 | 0 | 手写全参 | 审查统计摘要，被 AdminCompileJobResponse 嵌套引用 |

**统计**：3 个 Request + 7 个 Response。手写简单 getter 共 83 个（可用 `@Getter` 替代），1 个计算 getter（`isAsync()` 不可替代），手写 setter 共 8 个（可用 `@Setter` 替代），1 个 `@Data` 需降级。

---

## 二、明确排除文件清单及理由

| 排除文件 | 理由 | 归属批次 |
|---|---|---|
| `AdminCompileController.java` | Controller 本体 | 不纳入 |
| `AdminCompileReviewConfigController.java` | Controller 本体 | 不纳入 |
| `AdminCompileReviewQueueController.java` | Controller 本体 | 不纳入 |
| `AdminArticleReviewRequest.java` | article review DTO | B8 |
| `AdminArticleReviewResponse.java` | article review DTO | B8 |
| `AdminArticleReviewAuditResponse.java` | article review audit DTO | B8 |
| `AdminArticleReviewAuditListResponse.java` | article review audit list DTO | B8 |
| `AdminArticleCorrectionRequest.java` | article correction DTO | B8 |
| `AdminArticleDetailResponse.java` | article detail DTO | B8 |
| `AdminArticleListResponse.java` | article list DTO | B8 |
| `AdminArticleHotspotRefreshRequest.java` | article hotspot DTO | B8 |
| `AdminArticleHotspotRefreshResponse.java` | article hotspot DTO | B8 |
| `AdminArticleRollbackRequest.java` | article rollback DTO | B8 |
| `AdminArticleSnapshotListResponse.java` | article snapshot DTO | B8 |
| `AdminArticleSummaryResponse.java` | article summary DTO | B8 |
| `AdminArticleTitleProfile.java` | article title profile DTO | B8 |
| `AdminArticleUsageStatsResponse.java` | article usage stats DTO | B8 |
| `AdminFactCardItemResponse.java` | fact card DTO | B8 |
| `AdminFactCardListResponse.java` | fact card DTO | B8 |
| `AdminFactCardSummaryResponse.java` | fact card DTO | B8 |
| `AdminQualityResponse.java` | quality DTO | B8 |
| 所有 B6 文件（7 个） | 已完成 | B6 |
| 所有 B9 文件（6 个） | retrieval audit/channel DTO | B9 |
| 所有 B10 文件（7 个） | overview/pending/processing task DTO | B10 |
| `CompileArticleReviewQueueActionRequest` | 位于 `admin/service` 包，非 `api/admin` | B4（已完成） |
| `CompileArticleReviewQueueActionResult` | 位于 `admin/service` 包，非 `api/admin` | B4（已完成） |

---

## 三、每个纳入类的 Lombok/Javadoc 改造建议

### 3.1 AdminCompileJobRequest（Request）

**现状**：无 Lombok，5 个字段，手写 getter + setter。isAsync() 是计算 getter。

**改造**：
- **添加 `@Getter` + `@Setter`**：替代 4 个简单 getter/setter。
- **保留手写 `isAsync()`**：该方法是计算 getter（`async == null || async.booleanValue()`），不可被 Lombok 生成的 `getAsync()` 替代。需对 `async` 字段加 `@Getter(AccessLevel.NONE)` 和 `@Setter(AccessLevel.NONE)` 排除，或直接用 Lombok 生成 `getAsync()` 再保留手写 `isAsync()` 作为额外方法。推荐方案：类级 `@Getter` + `@Setter`，但 `async` 字段标注排除，保留手写 `isAsync()` 和 `setAsync()`。

> **关键细节**：Controller 调用 `compileJobRequest.isAsync()`（:81），不调用 `getAsync()`。字段 `async` 是 `Boolean` 包装类型，默认值 `Boolean.TRUE`。Lombok `@Getter` 会生成 `getAsync()` 返回 `Boolean`，而业务代码用 `isAsync()` 带有 null-coalescing 防御。两者语义不同，不可用 Lombok 生成版本替代手写 `isAsync()`。

- **字段 Javadoc 升级**：

| 字段 | 当前注释 | 需补充 |
|---|---|---|
| `sourceDir` | 无 | 源目录路径；可以是绝对路径或上传暂存目录；为空时编译无输入源 |
| `incremental` | 无 | 是否增量编译；true 时仅处理变更文件，false 时全量重编；影响编译耗时和结果集范围 |
| `async` | 无 | 是否异步执行；默认 true；false 时同步等待编译完成；null 时按 true 处理（isAsync() 防御性逻辑） |
| `orchestrationMode` | 无 | 编排模式标识（如 sequential / parallel）；影响 compile 步骤的执行顺序和并发度；为空时使用默认编排 |
| `reviewMode` | 无 | 审查模式标识（如 full / lite / none）；影响 compile 后是否触发 LLM review 步骤及审查深度 |

### 3.2 AdminCompileJobResponse（Response）

**现状**：无 Lombok，23 个 final 字段，手写全参构造器 + 23 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 23 个 getter 均为简单字段访问，可直接删除手写 getter。注意 `isIncremental()` 对应 boolean 字段，Lombok 生成一致。
- **保留构造器**：含 `@param` Javadoc，不变。
- **字段 Javadoc 升级**（按风险分组）：

| 字段 | 需补充 |
|---|---|
| **核心标识** | |
| `jobId` | 编译作业唯一标识 |
| `sourceDir` | 编译源目录；null 时表示作业未初始化 |
| `sourceNames` | 编译源文件名列表；仅上传编译时有值；目录编译时为空列表或 null |
| **编译参数** | |
| `incremental` | 本次编译是否增量模式 |
| `orchestrationMode` | 本次编译编排模式 |
| `reviewMode` | 本次编译审查模式 |
| **状态与进度** | |
| `status` | 作业原始状态（如 RUNNING / SUCCESS / FAILED）；由作业引擎写入 |
| `derivedStatus` | 派生展示状态；由 CompileJobDerivedStatusResolver 根据 status + reviewSummary 计算，供前端展示用 |
| `workerId` | 当前执行 worker 标识；null 表示无 worker 认领 |
| `currentStep` | 当前执行步骤名（如 parsing / reviewing / persisting） |
| `progressCurrent` | 当前进度计数 |
| `progressTotal` | 总进度计数；0 表示无法估算 |
| `progressMessage` | 进度提示文案 |
| **租约与心跳** | |
| `lastHeartbeatAt` | 最近心跳时间（ISO 字符串）；超过租约无心跳则作业被认为失活 |
| `runningExpiresAt` | 运行租约到期时间；过期后其他 worker 可抢占 |
| **错误信息** | |
| `errorCode` | 错误码；null 表示无错误 |
| `errorMessage` | 错误详情文本；可能包含编译异常栈或 LLM 返回错误；仅用于管理侧排查，不应展示给终端用户 |
| `persistedCount` | 本次编译已持久化的文章数 |
| `attemptCount` | 重试次数（含当前执行） |
| **审查摘要** | |
| `reviewSummary` | 编译审查摘要；null 表示无审查步骤或审查未执行 |
| **时间戳** | |
| `requestedAt` | 作业提交时间（ISO 字符串） |
| `startedAt` | 作业开始执行时间；null 表示尚未开始 |
| `finishedAt` | 作业完成时间；null 表示未完成 |

### 3.3 AdminCompileJobListResponse（Response）

**现状**：无 Lombok，2 个 final 字段，2 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：替代 2 个手写 getter。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `count` | 当前返回的作业数；等于 items.size() |
| `items` | 编译作业列表；按提交时间倒序 |

### 3.4 AdminCompileReviewConfigRequest（Request）

**现状**：`@Data` `@NoArgsConstructor` `@AllArgsConstructor`，5 个字段无 Javadoc。

**改造**：
- **替换 `@Data` 为 `@Getter` + `@Setter`**：`@Data` 生成的 `toString()` 包含 `operator`（审计字段），意外打印到日志会造成信息泄露。降级为 `@Getter/@Setter`。
- **保留 `@NoArgsConstructor` + `@AllArgsConstructor`**：Spring `@RequestBody` 绑定依赖无参构造 + setter。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `autoFixEnabled` | 自动修复总开关；true 时 LLM 对审查问题自动尝试修复（最多 maxFixRounds 轮）；false 时所有问题直接进入人工复核队列 |
| `maxFixRounds` | 自动修复最大轮次；每轮修复后重新审查，超过此次数仍未通过则标记 needs_human_review |
| `allowPersistNeedsHumanReview` | 是否允许"需人工复核"状态的文章落库；false 时阻止所有 needs_human_review 文章写入，仅 accepted 文章落库 |
| `humanReviewSeverityThreshold` | 人工复核严重度阈值；审查问题严重度 >= 此阈值时触发人工复核；非空非 blank |
| `operator` | 配置操作人标识；用于审计日志追踪；非空 |

### 3.5 AdminCompileReviewConfigResponse（Response）

**现状**：无 Lombok，9 个 final 字段，手写全参构造器 + 9 个手写 getter（**无 Javadoc**）。

**改造**：
- **添加类级 `@Getter`**：替代 9 个手写 getter。这 9 个 getter 当前缺少 Javadoc，替换后需补字段 Javadoc。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `autoFixEnabled` | 当前是否启用自动修复 |
| `maxFixRounds` | 当前自动修复最大轮次 |
| `allowPersistNeedsHumanReview` | 当前是否允许需人工复核文章落库；false 时前端应展示阻止提示 |
| `humanReviewSeverityThreshold` | 当前人工复核严重度阈值 |
| `configSource` | 配置来源标识（如 manual / auto）；用于管理侧追溯配置变更路径 |
| `createdBy` | 配置创建人 |
| `updatedBy` | 配置最后更新人 |
| `createdAt` | 配置创建时间（ISO 字符串） |
| `updatedAt` | 配置最后更新时间（ISO 字符串） |

### 3.6 AdminCompileReviewQueueActionRequest（Request）

**现状**：无 Lombok，3 个字段，手写 getter + setter。

**改造**：
- **添加 `@Getter` + `@Setter`**：替代 3 个手写 getter/setter。
- **无需 `@Data`**：当前无 Lombok，`reviewedBy` 和 `comment` 含审计信息，不应引入 `@Data`。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `reviewedBy` | 人工复核人标识；用于审计追踪；request 为 null 时 controller 创建空的默认实例 |
| `comment` | 人工复核意见文本；可为空；驳回时建议填写原因 |
| `expectedReviewStatus` | 期望的当前队列状态（乐观锁）；用于防并发覆盖；与当前记录状态不匹配时操作被拒绝 |

### 3.7 AdminCompileReviewQueueActionResponse（Response）

**现状**：无 Lombok，3 个 final 字段，3 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：替代 3 个手写 getter。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `item` | 操作后的队列条目当前快照 |
| `previousReviewStatus` | 操作前队列状态；与 expectedReviewStatus 一致时操作成功；用于前端确认状态流转 |
| `auditId` | 操作审计记录主键；可用于追溯本次人工确认的完整审计链路 |

### 3.8 AdminCompileReviewQueueItemResponse（Response）

**现状**：无 Lombok，22 个 final 字段（含 2 个 `List<String>`），手写全参构造器 + 22 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 22 个 getter 均为简单字段访问，可直接删除手写 getter。
- **保留构造器**：不变。
- **字段 Javadoc 升级**（按语义分组）：

| 字段 | 需补充 |
|---|---|
| **队列标识** | |
| `id` | 队列记录主键 |
| `jobId` | 所属编译作业标识 |
| **来源信息** | |
| `sourceId` | 资料源主键；null 表示无关联 source |
| `sourceCode` | 资料源编码 |
| `conceptId` | 被编译的概念标识 |
| **文章内容** | |
| `articleKey` | 文章唯一键（编译生成） |
| `title` | 文章标题 |
| `content` | 文章正文；可能为长文本；仅用于管理侧预览 |
| `metadataJson` | 文章元数据 JSON 字符串；可能较大 |
| **审查状态** | |
| `reviewStatus` | 当前队列状态（如 needs_human_review / accepted / published / rejected） |
| `reviewRoute` | 审查模型路由（如 auto / manual / hybrid） |
| `reviewerModel` | 执行审查的 LLM 模型标识 |
| `reviewIssuesJson` | 审查发现的全部问题 JSON；可能较大 |
| **自动修复** | |
| `fixAttemptCount` | 自动修复已执行轮数 |
| `maxFixRounds` | 自动修复最大轮次上限 |
| **来源路径** | |
| `sourcePaths` | 编译输入文件的相对路径列表 |
| **时间戳** | |
| `createdAt` | 队列记录创建时间 |
| `updatedAt` | 队列记录最后更新时间 |
| **人工复核** | |
| `reviewedBy` | 人工复核人标识；null 表示尚未人工处理 |
| `reviewedAt` | 人工复核时间；null 表示尚未人工处理 |
| `reviewComment` | 人工复核意见；null 表示未填写 |
| `publishedArticleKey` | 人工确认发布后生成的文章唯一键；null 表示尚未发布 |

### 3.9 AdminCompileReviewQueueListResponse（Response）

**现状**：无 Lombok，2 个 final 字段，2 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：替代 2 个手写 getter。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `total` | 当前返回的队列条目数；受 limit 参数限制，不等于数据库总数 |
| `items` | 队列条目列表；按创建时间排序 |

### 3.10 AdminCompileReviewSummaryResponse（Response）

**现状**：无 Lombok，15 个 final 字段（含 4 个 `Integer` 包装类型），手写全参构造器 + 15 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 15 个 getter 均为简单字段访问。注意包装类型 Integer（`acceptedCount`、`pendingReviewCount`、`needsHumanReviewCount`、`fixAttemptCount`）的 getter 手写为 `getXxx()`，Lombok 生成一致。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| **审查步骤** | |
| `reviewStepPresent` | 编译编排中是否包含 review 步骤 |
| `reviewStepName` | review 步骤名称 |
| `reviewAgentRole` | 执行审查的 Agent 角色（如 reviewer / auditor） |
| `requestedReviewMode` | 编译请求时指定的审查模式 |
| `reviewRoute` | 实际审查模型路由 |
| `reviewModeLabel` | 审查模式前端展示文案 |
| **审查计数** | |
| `acceptedCount` | 审查通过的文章数；null 表示无统计 |
| `pendingReviewCount` | 待审查的文章数；null 表示无统计 |
| `needsHumanReviewCount` | 需要人工复核的文章数；> 0 时前端应展示醒目的待处理提示 |
| **自动修复步骤** | |
| `fixStepPresent` | 编译编排中是否包含 auto-fix 步骤 |
| `fixStepName` | auto-fix 步骤名称 |
| `fixAttemptCount` | 自动修复实际尝试次数；null 表示无修复步骤 |
| `fixRoute` | 自动修复使用的模型路由 |
| **展示文案** | |
| `fixDisplayMessage` | 自动修复展示文案；由服务端生成，前端直接展示 |
| `reviewDisplayWarning` | 审查展示警示文案；含 needs_human_review > 0 时的警告信息；null 表示无警示 |

---

## 四、配置字段风险与运行影响说明

### 4.1 高风险字段（修改会立即影响 compile 流程行为）

| 字段 | 所属类 | 影响链路 | 风险说明 |
|---|---|---|---|
| `autoFixEnabled` | Config Request/Response | 自动修复全链路 | 关闭后所有审查问题直接进入人工复核队列，review queue 可能快速积压；开启后 LLM 反复调用（最多 maxFixRounds 轮），成本上升 |
| `maxFixRounds` | Config Request/Response、QueueItem | 修复轮次控制 | 过小（如 1）导致修复不充分，大量文章落入 needs_human_review；过大（如 10+）可能修复死循环，LLM 成本激增 |
| `allowPersistNeedsHumanReview` | Config Request/Response | 文章落库控制 | false 时阻止所有 needs_human_review 文章写入，编译实际产出为零；true 时未经人工确认的文章也会落库 |
| `humanReviewSeverityThreshold` | Config Request/Response | 人工复核触发条件 | 控制哪些严重级别的审查问题需要人工介入；设置为最低级别时几乎所有问题都需人工处理 |
| `reviewMode` | Job Request/Response | 审查深度与路由 | none 时跳过全部审查，编译最快但无质量保障；full 时执行完整审查 + 自动修复 |
| `orchestrationMode` | Job Request/Response | 编译步骤编排 | 影响 parse→review→fix→persist 步骤的执行顺序和并发度；错误配置可能导致步骤死锁 |
| `incremental` | Job Request/Response | 编译范围 | false 时全量重编所有源文件，耗时长；true 时仅处理变更，但可能遗漏依赖变更导致的级联影响 |
| `expectedReviewStatus` | Queue Action Request | 并发控制（乐观锁） | 与当前记录状态不匹配时操作被拒绝；错误值导致审批操作失败，需重试 |

### 4.2 中等风险字段（影响管理侧展示与决策，不直接影响编译流程）

| 字段 | 所属类 | 影响链路 | 风险说明 |
|---|---|---|---|
| `sourceDir` | Job Request/Response | 编译输入、路径安全 | 用户可控路径，有路径遍历风险；为空时编译无输入源，作业失败（已在 B5b 标注类似风险） |
| `async` | Job Request | 同步/异步执行 | true 时立即返回 jobId，前端轮询状态；false 时同步等待，可能导致 HTTP 超时 |
| `status` / `derivedStatus` | Job Response | 前端状态展示 | derivedStatus 由服务端计算，与原始 status 不同；前端应根据 derivedStatus 展示 |
| `needsHumanReviewCount` | Review Summary | 前端告警 | > 0 时前端应展示醒目提示；关联 review queue 的积压程度 |
| `errorMessage` | Job Response | 排障 | 可能包含异常栈或 LLM 返回原文；应仅展示给管理侧，不应透出给终端用户或记录到公开日志 |
| `reviewIssuesJson` / `metadataJson` | Queue Item Response | 响应体积 | JSON 字符串可能极大，影响序列化性能和网络传输 |

### 4.3 低风险字段（纯信息展示/审计）

| 字段 | 所属类 | 说明 |
|---|---|---|
| `jobId` / `workerId` / `id` / `auditId` | 多处 | 系统标识符，只读展示 |
| `progressCurrent` / `progressTotal` / `progressMessage` / `currentStep` | Job Response | 进度展示，只读 |
| `lastHeartbeatAt` / `runningExpiresAt` | Job Response | 租约展示，只读 |
| `requestedAt` / `startedAt` / `finishedAt` / `createdAt` / `updatedAt` / `reviewedAt` | 多处 | 时间戳展示 |
| `errorCode` / `attemptCount` / `persistedCount` | Job Response | 统计展示 |
| `sourceId` / `sourceCode` / `conceptId` / `articleKey` / `publishedArticleKey` | Queue Item | 关联标识展示 |
| `title` / `content` | Queue Item | 只读预览 |
| `reviewRoute` / `reviewerModel` / `reviewStepName` / `fixStepName` / `fixRoute` | 多处 | 路由和步骤信息展示 |
| `reviewModeLabel` / `fixDisplayMessage` / `reviewDisplayWarning` | Review Summary | 前端展示文案，只读 |
| `acceptedCount` / `pendingReviewCount` / `fixAttemptCount`（Summary） | Review Summary | 统计计数 |
| `configSource` / `createdBy` / `updatedBy` / `operator` | Config | 审计追踪 |
| `reviewedBy` / `reviewComment` | Queue Action/Item | 审计追踪 |

### 4.4 @Data/toString 泄露风险专项

| 类 | 当前注解 | 含敏感/审计字段 | 风险 | 处置 |
|---|---|---|---|---|
| `AdminCompileReviewConfigRequest` | `@Data` | `operator` | `toString()` 输出操作人标识；若被日志框架（如 Logback）调用 toString()，operator 会被记录到日志 | 降级为 `@Getter` + `@Setter` |
| `AdminCompileJobRequest` | 无 | `sourceDir` | 无 `@Data`，当前安全；`sourceDir` 可能包含用户路径信息 | 不引入 `@Data` |
| `AdminCompileReviewQueueActionRequest` | 无 | `reviewedBy`, `comment` | 无 `@Data`，当前安全；`comment` 可能含人工主观评价 | 不引入 `@Data` |
| `AdminCompileReviewQueueItemResponse` | 无 | `reviewedBy`, `reviewComment`, `content`, `errorMessage` | 无 `@Data`，当前安全；若未来加 `@Data` 会导致大文本字段参与 toString() | 禁止引入 `@Data` |
| `AdminCompileJobResponse` | 无 | `errorMessage` | 无 `@Data`，当前安全 | 禁止引入 `@Data` |

### 4.5 本轮约束

以上所有字段**本轮只做契约注释，不改行为**。具体而言：
- 不调整任何字段的类型、默认值或验证规则
- 不修改 controller 中 `toResponse()`、`toItemResponse()`、`toServiceRequest()` 等映射方法
- 不修改 `validateRequest()` 验证逻辑
- 不修改 compile job 编排、review 状态机、auto-fix 流程
- 不触碰 `CompileJobRecord`、`CompileArticleReviewQueueRecord`、`CompileReviewConfigState` 等 domain/persistence 层对象

---

## 五、特殊关注点

### 5.1 AdminCompileJobRequest.isAsync() 计算 getter

该 getter 是本次审查发现的**唯一不可被 Lombok 替代的 getter**：

```java
// 字段: private Boolean async = Boolean.TRUE;
// 计算 getter:
public boolean isAsync() {
    return async == null || async.booleanValue();
}
```

Lombok `@Getter` 会生成 `getAsync()` 返回 `Boolean`（可能为 null），而业务代码通过 `isAsync()` 获取 null-safe 的 boolean 值。Controller 在 `:81` 调用 `compileJobRequest.isAsync()`。改造方案：

- **方案 A（推荐）**：类级 `@Getter` + `@Setter`，对 `async` 字段加 `@Getter(AccessLevel.NONE)` + `@Setter(AccessLevel.NONE)`，保留手写 `isAsync()` 和 `setAsync()`。其他 4 个字段的 getter/setter 用 Lombok 生成。
- **方案 B**：不用 Lombok，仅补 Javadoc。保持手写所有 getter/setter。

推荐方案 A，因为其余 4 个字段的 getter/setter 都是简单访问，替换无风险。

### 5.2 AdminCompileReviewConfigResponse getter 缺少 Javadoc

该类的 9 个 getter（行 65-99）均无 Javadoc 注释，与项目其他 Response 类的风格不一致（其他 Response 类的手写 getter 都有 `@return` 描述）。替换为 `@Getter` 后应将注释迁移到字段级 Javadoc。

### 5.3 AdminCompileReviewQueueActionRequest 无参构造

Controller `toServiceRequest()` 在 `request == null` 时调用 `new AdminCompileReviewQueueActionRequest()`。该类当前只有默认无参构造（隐式），添加 Lombok 时不应引入 `@AllArgsConstructor` 破坏该行为。只需 `@Getter` + `@Setter`。

---

## 六、给 agentA 的下一轮提示词草案

> 以下是给 agentA 的完整任务提示词，可直接用于下一轮执行：

```
交给 agentA。

本轮任务：对 B7 的 10 个 compile job / compile review DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_compile_job_review_dto_contract_analysis_report.md

## 修改范围（10 个文件）

### Request 类（保留 setter + 无参构造，替换或新增 @Getter/@Setter）

1. AdminCompileJobRequest.java
   - 添加 @Getter + @Setter
   - 对 `async` 字段加 @Getter(AccessLevel.NONE) + @Setter(AccessLevel.NONE)
   - 保留手写 isAsync() 方法（计算 getter：null-coalescing 逻辑）
   - 保留手写 setAsync() 方法（或确认 Lombok @Setter 不影响行为——async 是 Boolean，Lombok 生成 setAsync(Boolean) 与手写一致，可让 Lombok 生成 setter）
   - 删除 4 个简单 getter（getSourceDir、isIncremental、getOrchestrationMode、getReviewMode）
   - 删除 4 个简单 setter（setSourceDir、setIncremental、setOrchestrationMode、setReviewMode）
   - 5 个字段补 Javadoc（参考审查报告 3.1 节）

2. AdminCompileReviewConfigRequest.java
   - 替换 @Data 为 @Getter + @Setter（防止 toString() 输出 operator 审计字段）
   - 保留 @NoArgsConstructor + @AllArgsConstructor
   - 5 个字段补 Javadoc（参考审查报告 3.4 节）

3. AdminCompileReviewQueueActionRequest.java
   - 添加 @Getter + @Setter
   - 删除 3 个手写 getter + 3 个手写 setter
   - 不引入 @Data（reviewedBy/comment 含审计信息）
   - 3 个字段补 Javadoc（参考审查报告 3.6 节）

### Response 类（添加 @Getter，删除手写 getter，保留构造器）

4. AdminCompileJobResponse.java
   - 添加类级 @Getter
   - 删除 23 个手写 getter（全部为简单字段访问）
   - 23 个字段补 Javadoc（参考审查报告 3.2 节）
   - 保留全参构造器及 @param Javadoc

5. AdminCompileJobListResponse.java
   - 添加类级 @Getter
   - 删除 2 个手写 getter
   - 2 个字段补 Javadoc（参考审查报告 3.3 节）
   - 保留全参构造器及 @param Javadoc

6. AdminCompileReviewConfigResponse.java
   - 添加类级 @Getter
   - 删除 9 个手写 getter（当前无 Javadoc）
   - 9 个字段补 Javadoc（参考审查报告 3.5 节）
   - 保留全参构造器及 @param Javadoc

7. AdminCompileReviewQueueActionResponse.java
   - 添加类级 @Getter
   - 删除 3 个手写 getter
   - 3 个字段补 Javadoc（参考审查报告 3.7 节）
   - 保留全参构造器及 @param Javadoc

8. AdminCompileReviewQueueItemResponse.java
   - 添加类级 @Getter
   - 删除 22 个手写 getter
   - 22 个字段补 Javadoc（参考审查报告 3.8 节）
   - 保留全参构造器及 @param Javadoc

9. AdminCompileReviewQueueListResponse.java
   - 添加类级 @Getter
   - 删除 2 个手写 getter
   - 2 个字段补 Javadoc（参考审查报告 3.9 节）
   - 保留全参构造器及 @param Javadoc

10. AdminCompileReviewSummaryResponse.java
    - 添加类级 @Getter
    - 删除 15 个手写 getter
    - 15 个字段补 Javadoc（参考审查报告 3.10 节）
    - 保留全参构造器及 @param Javadoc

## 禁止事项

- 禁止修改任何 controller 文件
- 禁止修改任何 service / domain / infra / config / persistence 文件
- 禁止修改 test 文件
- 禁止修改构造器签名或逻辑
- 禁止修改字段类型、名称、访问修饰符、默认值
- 禁止修改 validation 逻辑
- 禁止修改 toResponse() / toItemResponse() / toServiceRequest() 等映射方法
- 禁止修改 compile job 编排、review 状态机、auto-fix 流程
- 禁止修改 scripts/scan-redline.sh、special_cases_report.md、redline allowlist
- 禁止混入 B8/B9/B10 或其他非 api/admin DTO
- 禁止给 AdminCompileJobRequest.async 字段的 isAsync() 计算 getter 加 Lombok 覆盖
- 禁止给任何 Response 引入 @Data
- 禁止给 AdminCompileReviewQueueActionRequest 引入 @Data
- 禁止给 AdminCompileJobResponse 引入 @Data（errorMessage 字段不应参与 toString()）

## 验收门槛

- 编译通过（mvn compile -pl . -q）
- 全量 mvn test 通过
- redline 无新增 BLOCKER
- 自查：AdminCompileJobRequest.isAsync() 行为不变（仍返回 null-safe boolean）
- 自查：无字段翻译式空泛注释，每个字段注释回答"影响什么链路、为空的含义"

## 完成后

1. 回写计划文件：B7 状态 → "已完成"，备注实际修改内容
2. 输出 B7_fix_result_report.md
3. 不 stage、不 commit、不 push
```

---

## 七、审查结论

- B7 范围清晰，10 个 DTO（3 Request + 7 Response）无越界耦合。
- 1 个 `@Data`（`AdminCompileReviewConfigRequest`）需降级为 `@Getter/@Setter`，理由：`operator` 审计字段不应参与 `toString()`。
- `AdminCompileJobRequest.isAsync()` 是**唯一计算 getter**，不可被 Lombok 替代；需用 `@Getter(AccessLevel.NONE)` 排除 `async` 字段。
- 7 个 Response 均可安全使用类级 `@Getter` 替代手写 getter（共 76 个简单 getter 可删除）。
- `AdminCompileReviewConfigResponse` 的 9 个 getter 当前缺少 Javadoc，需补到字段级。
- 高风险字段（`autoFixEnabled`、`maxFixRounds`、`allowPersistNeedsHumanReview`、`humanReviewSeverityThreshold`、`reviewMode`、`orchestrationMode`、`incremental`、`expectedReviewStatus`）本轮仅标注契约语义，不改运行行为。
- `AdminCompileReviewQueueActionRequest` 和 `AdminCompileReviewQueueItemResponse` 中的 `reviewedBy`、`comment`/`reviewComment` 为审计字段，禁止引入 `@Data`。
- `AdminCompileJobResponse.errorMessage` 和 `AdminCompileReviewQueueItemResponse.content` 可能为大文本字段，禁止引入 `@Data`。
