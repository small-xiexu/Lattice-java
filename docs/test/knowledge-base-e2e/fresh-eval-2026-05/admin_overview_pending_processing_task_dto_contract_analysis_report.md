# B10 Overview / Pending / Processing Task DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B10 — `api/admin` overview / pending / processing task DTO

---

## 一、拆分建议：B10 → B10a + B10b

13 个候选类超过 10 个上限，按 Dashboard 聚合度自然拆分：

| 子批次 | 候选数 | 范围 | 拆分理由 |
|---|---|---|---|
| **B10a** | **5** | Overview + Pending DTO | Dashboard 总览 + pending 列表，低字段数，轻量 |
| **B10b** | **8** | Processing Task + KnowledgeHelp DTO | 工作台任务列表/步骤/摘要/帮助卡，聚合度高，嵌套密集 |

`AdminKnowledgeHelpStateResponse` 和 `AdminKnowledgeHelpActionResponse` 因被 `AdminProcessingTaskSummaryResponse` 嵌套引用，归入 B10b。

---

## 二、B10a 纳入文件清单（5 个类）

| # | 类名 | 类型 | Lombok | 手写 getter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|
| 1 | `AdminOverviewResponse` | Response | 无 | 3 | 手写全参 | 直接包装 domain 类型（`StatusSnapshot`、`QualityMetricsReport`） |
| 2 | `AdminOverviewPendingResponse` | Response | 无 | 2 | 手写全参 | 列表容器 |
| 3 | `AdminOverviewPendingItemResponse` | Response | 无 | 3 | 手写全参 | `question` 用户查询文本 |
| 4 | `AdminPendingResponse` | Response | 无 | 2 | 手写全参 | 列表容器 |
| 5 | `AdminPendingItemResponse` | Response | 无 | 8 | 手写全参 | `question`/`answer` 用户数据 |

**B10a 统计**：0 Request + 5 Response。简单 getter 可删除 18 个。

---

## 三、B10b 纳入文件清单（8 个类）

| # | 类名 | 类型 | Lombok | 手写 getter | 构造器 | 特殊风险 |
|---|---|---|---|---|---|---|
| 1 | `AdminProcessingTaskActionResponse` | Response | 无 | 8 | 手写全参 | **getter 无 Javadoc** |
| 2 | `AdminProcessingTaskItemResponse` | Response | 无 | 45 | 手写双构造器（小→大委托） | **45 字段超大 DTO**；`errorMessage`/`evidenceJson` 大文本；嵌套 B7 类型 |
| 3 | `AdminProcessingTaskListResponse` | Response | 无 | 2 | 手写全参 | **getter 无 Javadoc** |
| 4 | `AdminProcessingTaskStepResponse` | Response | 无 | 4 | 手写全参 | **getter 无 Javadoc** |
| 5 | `AdminProcessingTaskSummaryCardResponse` | Response | 无 | 4 | 手写全参 | **getter 无 Javadoc** |
| 6 | `AdminProcessingTaskSummaryResponse` | Response | 无 | 7 | 手写全参 | **getter 无 Javadoc**；嵌套 `AdminKnowledgeHelpStateResponse` |
| 7 | `AdminKnowledgeHelpStateResponse` | Response | 无 | 5 | 手写全参 | **getter 无 Javadoc**；嵌套 `AdminKnowledgeHelpActionResponse` |
| 8 | `AdminKnowledgeHelpActionResponse` | Response | 无 | 3 | 手写全参 | **getter 无 Javadoc** |

**B10b 统计**：0 Request + 8 Response。简单 getter 可删除 78 个。

---

## 四、明确排除文件清单及理由

### 4.1 Controller 排除

| 排除文件 | 理由 |
|---|---|
| `AdminOverviewController.java` | Controller 本体 |
| `AdminPendingController.java` | Controller 本体 |
| `AdminProcessingTaskController.java` | Controller 本体 |

### 4.2 admin/service 层排除

| 排除文件 | 类型 | 排除理由 |
|---|---|---|
| `AdminProcessingTaskDisplayStatus.java` | Enum（admin/service） | 不属于 api/admin DTO；现有 Javadoc 完备（code/label/tone/processingActive/requiresManualAction/noticeTone 均有说明）；7 个静态工具方法覆盖解析/判断/规范化 |
| `AdminProcessingTaskPresentation.java` | Service 展示模型 | 不属于 api/admin DTO；是 service 层向 controller 传递的中间模型，字段直接映射到 `AdminProcessingTaskItemResponse` |
| `AdminProcessingTaskStep.java` | Enum（admin/service） | 不属于 api/admin DTO；29 个步骤码 + 标签，现有 Javadoc 完备；含 2 个阶段判断工具方法 |
| `AdminProcessingTaskStepStatus.java` | Enum（admin/service） | 不属于 api/admin DTO；简单 4 值枚举（PENDING/ACTIVE/COMPLETED/FAILED） |

> **说明**：以上 4 个 admin/service 文件的枚举值和字段值直接影响 `AdminProcessingTaskItemResponse` 的 `displayStatus`、`progressSteps[].status`、`progressSteps[].key` 等字段的取值和前端渲染行为。本轮不做修改，但 agentA 在写字段 Javadoc 时应引用这些枚举的语义。

### 4.3 其他批次排除

| 排除文件 | 归属 |
|---|---|
| B11a/b/c 所有文件 | controller 内部 DTO |
| 已完成的 B0-B9 所有文件 | 各自批次 |

---

## 五、每个纳入类的 Lombok/Javadoc 改造建议

### 5.1 关键发现汇总

- **无 `@Data`**：B10 数据集干净，0 个 `@Data`
- **无 boolean getter 命名不一致**：所有 boolean 字段使用标准 `isXxx()`，Lombok 生成一致
- **6 个类 getter 缺少 Javadoc**：`AdminProcessingTaskActionResponse`、`AdminProcessingTaskListResponse`、`AdminProcessingTaskStepResponse`、`AdminProcessingTaskSummaryCardResponse`、`AdminProcessingTaskSummaryResponse`、`AdminKnowledgeHelpStateResponse`、`AdminKnowledgeHelpActionResponse`（全部在 B10b）
- **1 个超大 DTO**：`AdminProcessingTaskItemResponse` 有 45 个字段，是项目中最复杂的单个 DTO
- **1 个双构造器**：`AdminProcessingTaskItemResponse` 有小→大委托模式，需保留两个构造器
- **2 个 domain 类型直接暴露**：`AdminOverviewResponse` 直接包装 `StatusSnapshot` 和 `QualityMetricsReport`

### 5.2 B10a — Overview + Pending DTO

#### AdminOverviewResponse（Response）
- 添加类级 `@Getter`，删除 3 个手写 getter
- 保留全参构造器
- 字段 Javadoc：
  - `status` — 系统状态快照（`StatusSnapshot` 领域对象）；含服务健康、数据完整性等 Dashboard 顶部状态指示
  - `quality` — 当前质量指标报告（`QualityMetricsReport` 领域对象）；含知识库整体质量评分与分类指标
  - `pending` — 待确认查询汇总；count=0 时前端不展示 pending 区块或展示"全部已确认"
- **注意**：`status` 和 `quality` 直接暴露 domain 类型，是已知分层问题，本轮不修复

#### AdminOverviewPendingResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段 Javadoc：
  - `count` — 待确认查询总数
  - `items` — 待确认查询摘要列表（截断，非全量）

#### AdminOverviewPendingItemResponse（Response）
- 添加类级 `@Getter`，删除 3 个手写 getter
- 字段 Javadoc：
  - `queryId` — 查询会话标识
  - `question` — 用户原始问题文本；用于 Dashboard 快速预览
  - `reviewStatus` — 审查状态；驱动前端展示待处理标签颜色

#### AdminPendingResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter
- 字段：`count`（待确认数）、`items`（pending 条目列表）

#### AdminPendingItemResponse（Response）
- 添加类级 `@Getter`，删除 8 个手写 getter
- 保留全参构造器
- 字段 Javadoc：
  - `queryId` — 查询会话标识
  - `question` — 用户原始问题全文
  - `answer` — 系统生成答案全文；**可能为长文本**
  - `reviewStatus` — 审查状态
  - `selectedConceptIds` — 用户选择的概念标识列表
  - `sourceFilePaths` — 关联来源文件路径
  - `createdAt` — 创建时间
  - `expiresAt` — 过期时间；过期后 pending 条目可能被自动丢弃

### 5.3 B10b — Processing Task + KnowledgeHelp DTO

#### AdminProcessingTaskActionResponse（Response）
- 添加类级 `@Getter`，删除 8 个手写 getter（当前无 Javadoc）
- 保留全参构造器
- 字段 Javadoc：
  - `actionKey` — 动作键（前端路由标识）；如 `confirm-upload` / `retry-compile` / `view-source`
  - `label` — 按钮展示文案
  - `buttonClass` — 按钮 CSS 样式类（如 `btn-primary` / `btn-warning`）
  - `runId` — 关联的同步/编译 run 主键；null 表示无关联运行实例
  - `sourceId` — 关联资料源主键；null 表示无关联
  - `decision` — 预设的确认决策值；null 表示需用户手动选择
  - `decisionSourceId` — 决策目标资料源主键；用于确认合并场景
  - `uploadRetry` — 是否为上传重试动作；true 时前端应引导用户重新上传文件

#### AdminProcessingTaskItemResponse（Response）⚠️ 超大 DTO
- 添加类级 `@Getter`，删除 45 个手写 getter
- **保留两个构造器**（小构造器委托到大构造器，传递 pendingHumanReviewCount=0, publishedCount=0, rejectedCount=0）
- `isProcessingActive()` / `isRequiresManualAction()` 对应 boolean，Lombok 生成一致
- 字段 Javadoc 按语义分组：

**任务标识与类型**：
| 字段 | 需补充 |
|---|---|
| `taskId` | 任务唯一标识 |
| `taskType` | 任务类型（如 `source_sync` / `standalone_compile`） |
| `title` | 任务展示标题 |
| `runId` | 同步/编译 run 主键 |
| `sourceId` | 资料源主键 |
| `sourceName` | 资料源名称 |
| `sourceType` | 资料源类型（如 `git` / `upload`） |

**任务主状态**：
| 字段 | 需补充 |
|---|---|
| `status` | 任务底层主状态（原始值） |
| `resolverMode` | 资料源识别模式（如 `auto_match` / `manual`） |
| `resolverDecision` | 识别决策结果 |
| `syncAction` | 同步动作（如 `create` / `update` / `skip`） |
| `matchedSourceId` | 自动匹配的候选资料源主键 |

**编译关联状态**：
| 字段 | 需补充 |
|---|---|
| `compileJobId` | 关联编译作业标识；null 表示无关联编译 |
| `compileJobStatus` | 编译作业原始状态 |
| `compileDerivedStatus` | 编译派生展示状态 |
| `compileCurrentStep` | 编译当前执行步骤 |
| `compileProgressCurrent` / `compileProgressTotal` | 编译进度（当前/总）；total=0 表示无法估算 |
| `compileProgressMessage` | 编译进度提示文案 |
| `compileLastHeartbeatAt` | 编译最近心跳时间 |
| `compileRunningExpiresAt` | 编译运行租约到期时间 |
| `compileErrorCode` | 编译错误码 |
| `manifestHash` | 资料 manifest 哈希；用于变更检测 |

**提示与错误**：
| 字段 | 需补充 |
|---|---|
| `message` | 通用提示文案 |
| `errorMessage` | 错误详情文案；**可能含异常信息** |

**展示字段**（由 `AdminProcessingTaskPresentation` 映射）：
| 字段 | 需补充 |
|---|---|
| `displayStatus` | 展示状态码（`AdminProcessingTaskDisplayStatus` 枚举值）；驱动前端状态标签颜色和图标 |
| `displayStatusLabel` | 展示状态中文文案 |
| `currentStepLabel` | 当前步骤中文文案 |
| `nextStepHint` | 下一步操作提示 |
| `progressText` | 当前进度描述文案 |
| `reasonSummary` | 任务停滞/失败原因摘要 |
| `operationalNote` | 操作线索说明（如"请检查资料源配置"） |
| `progressSteps` | 完整步骤链列表（`AdminProcessingTaskStepResponse`） |
| `displayTone` | 展示色调（`success` / `warning` / `danger` / `info`）；驱动前端卡片边框颜色 |
| `processingActive` | 是否仍在处理中；true 时前端应继续轮询 |
| `requiresManualAction` | 是否需要人工介入；true 时前端应展示操作按钮 |
| `noticeTone` | 通知语气色调 |
| `completionNotice` | 任务完成后的提示文案 |

**编译审查关联**：
| 字段 | 需补充 |
|---|---|
| `compileReviewSummary` | 编译审查摘要（B7 `AdminCompileReviewSummaryResponse`）；null 表示无审查步骤 |
| `pendingHumanReviewCount` | 待人工确认数量 |
| `publishedCount` | 已发布数量 |
| `rejectedCount` | 已驳回数量 |

**来源与数据**：
| 字段 | 需补充 |
|---|---|
| `sourceNames` | 来源文件名预览列表 |
| `actions` | 当前可执行的动作列表（`AdminProcessingTaskActionResponse`） |
| `evidenceJson` | 证据摘要 JSON；**可能较大** |

**时间戳**：
| 字段 | 需补充 |
|---|---|
| `requestedAt` | 任务提交时间 |
| `updatedAt` | 最后更新时间 |
| `startedAt` | 开始执行时间 |
| `finishedAt` | 完成时间 |

#### AdminProcessingTaskListResponse（Response）
- 添加类级 `@Getter`，删除 2 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `summary` — 工作台顶部概览卡片汇总
  - `items` — 处理任务列表

#### AdminProcessingTaskStepResponse（Response）
- 添加类级 `@Getter`，删除 4 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `key` — 步骤码（`AdminProcessingTaskStep` 枚举值）；用于前端图标/样式选择
  - `label` — 步骤展示文案
  - `status` — 步骤状态（`AdminProcessingTaskStepStatus` 枚举值：`PENDING` / `ACTIVE` / `COMPLETED` / `FAILED`）
  - `detail` — 步骤详细说明；null 表示无需额外说明

#### AdminProcessingTaskSummaryCardResponse（Response）
- 添加类级 `@Getter`，删除 4 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `label` — 卡片标题（如"运行中""待确认""已完成"）
  - `value` — 卡片数值
  - `note` — 卡片补充说明
  - `tone` — 卡片语气色调（`success` / `warning` / `danger` / `info`）

#### AdminProcessingTaskSummaryResponse（Response）
- 添加类级 `@Getter`，删除 7 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `runningCount` — 运行中任务数
  - `waitingCount` — 等待人工确认任务数
  - `stalledCount` — 疑似卡住任务数
  - `succeededCount` — 已成功完成任务数
  - `failedCount` — 已失败任务数
  - `cards` — 前端概览卡片列表（`AdminProcessingTaskSummaryCardResponse`）
  - `helpState` — "现在该怎么做"帮助卡

#### AdminKnowledgeHelpStateResponse（Response）
- 添加类级 `@Getter`，删除 5 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `tone` — 帮助卡语气色调
  - `title` — 帮助卡标题
  - `description` — 帮助卡描述文案
  - `faqKey` — 关联 FAQ 锚点标识
  - `actions` — 可执行帮助动作列表

#### AdminKnowledgeHelpActionResponse（Response）
- 添加类级 `@Getter`，删除 3 个手写 getter（当前无 Javadoc）
- 字段 Javadoc：
  - `label` — 动作展示文案
  - `action` — 前端动作标识（路由/事件名）
  - `className` — 按钮 CSS 样式类

---

## 六、字段风险与运行影响说明

### 6.1 高风险字段

| 字段 | 所属类 | 风险说明 |
|---|---|---|
| `question` | OverviewPendingItemResponse, PendingItemResponse | 用户原始问题文本，可能含 PII 或敏感查询内容 |
| `answer` | PendingItemResponse | 系统生成答案全文，可能很长且含生成内容 |
| `errorMessage` | ProcessingTaskItemResponse | 错误详情，可能含异常栈或内部信息；不应参与 toString() |
| `evidenceJson` | ProcessingTaskItemResponse | 证据 JSON，可能较大；不应参与 toString() |
| `displayStatus` / `displayStatusLabel` | ProcessingTaskItemResponse | 驱动前端任务卡片的状态标签，取值语义需与 `AdminProcessingTaskDisplayStatus` 枚举一致 |
| `progressSteps[].status` | ProcessingTaskStepResponse | 驱动进度条步骤状态图标，取值需与 `AdminProcessingTaskStepStatus` 枚举一致 |

### 6.2 中等风险字段

| 字段 | 所属类 | 影响 |
|---|---|---|
| `processingActive` | ProcessingTaskItemResponse | true 时前端持续轮询，影响服务器负载 |
| `requiresManualAction` | ProcessingTaskItemResponse | true 时前端展示操作按钮；false 时隐藏，用户无法操作 |
| `reviewStatus` | OverviewPendingItemResponse, PendingItemResponse | 驱动 pending 条目的处理状态展示 |
| `compileDerivedStatus` / `compileJobStatus` | ProcessingTaskItemResponse | 编译子任务状态，影响前端编译进度展示 |
| `runningCount` / `waitingCount` / `stalledCount` | ProcessingTaskSummaryResponse | Dashboard 概览卡片的核心数值；stalledCount > 0 时须告警 |
| `actions` | ProcessingTaskItemResponse | 驱动前端操作按钮的渲染和可用性 |

### 6.3 低风险字段（展示/标识/时间戳）

`taskId`、`taskType`、`runId`、`sourceId`、`sourceName`、`sourceType`、`title`、`manifestHash`、`message`、`resolverMode`、`resolverDecision`、`syncAction`、`matchedSourceId`、`compileJobId`、`compileProgressCurrent`/`Total`、`compileProgressMessage`、`compileLastHeartbeatAt`、`compileRunningExpiresAt`、`compileErrorCode`、`currentStepLabel`、`nextStepHint`、`progressText`、`reasonSummary`、`operationalNote`、`displayTone`、`noticeTone`、`completionNotice`、`compileReviewSummary`、`pendingHumanReviewCount`、`publishedCount`、`rejectedCount`、`sourceNames`、`requestedAt`/`updatedAt`/`startedAt`/`finishedAt`、`selectedConceptIds`、`sourceFilePaths`、`createdAt`/`expiresAt`、`queryId`、`count`、`items`、`status`/`quality`/`pending`、`actionKey`/`label`/`buttonClass`/`runId`/`decision`/`decisionSourceId`/`uploadRetry`、`key`/`detail`、`tone`/`title`/`description`/`faqKey`、`action`/`className`

### 6.4 @Data 风险

B10 全部 13 个类当前均无 `@Data`。以下类因含用户数据或大文本字段，未来禁止引入 `@Data`：

| 类 | 风险字段 |
|---|---|
| `AdminOverviewPendingItemResponse` | `question` |
| `AdminPendingItemResponse` | `question`, `answer` |
| `AdminProcessingTaskItemResponse` | `errorMessage`, `evidenceJson` |

---

## 七、给 agentA 的下一轮提示词草案（B10a）

```
交给 agentA。

本轮任务：对 B10a 的 5 个 overview + pending DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_overview_pending_processing_task_dto_contract_analysis_report.md

## 修改范围（5 个文件，全部为 Response）

1. AdminOverviewResponse.java
   - 添加类级 @Getter，删除 3 个手写 getter
   - 3 字段补 Javadoc（审查报告 5.2 节），标注 status/quality 直接暴露 domain 类型
   - 保留全参构造器

2. AdminOverviewPendingResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

3. AdminOverviewPendingItemResponse.java
   - 添加类级 @Getter，删除 3 个手写 getter
   - 3 字段补 Javadoc（question 标注用户数据）
   - 禁止引入 @Data

4. AdminPendingResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter
   - 2 字段补 Javadoc

5. AdminPendingItemResponse.java
   - 添加类级 @Getter，删除 8 个手写 getter
   - 8 字段补 Javadoc（question/answer 标注用户数据）
   - 禁止引入 @Data
   - 保留全参构造器

## 禁止事项

- 禁止修改 controller / service / domain / infra 文件
- 禁止修改构造器签名或逻辑
- 禁止修改字段类型、名称、访问修饰符
- 禁止给任何类引入 @Data
- 禁止修改 Dashboard 统计逻辑、pending 计算逻辑
- 禁止修改 AdminOverviewResponse 中 StatusSnapshot/QualityMetricsReport 的暴露方式（已知分层问题）
- 禁止混入 B10b 或 B11 文件

## 完成后：回写 B10a → "已完成"，输出 B10a_fix_result_report.md
```

---

## 八、给 agentA 的下一轮提示词草案（B10b）

```
交给 agentA。

本轮任务：对 B10b 的 8 个 processing task + knowledge help DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_overview_pending_processing_task_dto_contract_analysis_report.md

## 修改范围（8 个文件，全部为 Response）

1. AdminProcessingTaskActionResponse.java
   - 添加类级 @Getter，删除 8 个手写 getter（当前无 Javadoc）
   - 8 字段补 Javadoc（审查报告 5.3 节）
   - 保留全参构造器

2. AdminProcessingTaskItemResponse.java ⚠️ 超大 DTO（45 字段）
   - 添加类级 @Getter，删除 45 个手写 getter
   - 保留两个构造器（小→大委托模式不可删除）
   - 45 字段补 Javadoc（按任务标识/主状态/编译关联/提示错误/展示字段/审查关联/来源数据/时间戳分组）
   - 禁止引入 @Data（errorMessage/evidenceJson 大文本）
   - isProcessingActive()/isRequiresManualAction() 与 Lombok 生成一致

3. AdminProcessingTaskListResponse.java
   - 添加类级 @Getter，删除 2 个手写 getter（当前无 Javadoc）
   - 2 字段补 Javadoc

4. AdminProcessingTaskStepResponse.java
   - 添加类级 @Getter，删除 4 个手写 getter（当前无 Javadoc）
   - 4 字段补 Javadoc，标注 key 取自 AdminProcessingTaskStep 枚举，status 取自 AdminProcessingTaskStepStatus 枚举

5. AdminProcessingTaskSummaryCardResponse.java
   - 添加类级 @Getter，删除 4 个手写 getter（当前无 Javadoc）
   - 4 字段补 Javadoc

6. AdminProcessingTaskSummaryResponse.java
   - 添加类级 @Getter，删除 7 个手写 getter（当前无 Javadoc）
   - 7 字段补 Javadoc

7. AdminKnowledgeHelpStateResponse.java
   - 添加类级 @Getter，删除 5 个手写 getter（当前无 Javadoc）
   - 5 字段补 Javadoc

8. AdminKnowledgeHelpActionResponse.java
   - 添加类级 @Getter，删除 3 个手写 getter（当前无 Javadoc）
   - 3 字段补 Javadoc

## 禁止事项

- 禁止修改 controller / service / domain / infra 文件
- 禁止修改构造器签名或逻辑（含双构造器委托模式）
- 禁止修改字段类型、名称、访问修饰符
- 禁止给任何类引入 @Data
- 禁止修改 AdminProcessingTaskDisplayStatus / AdminProcessingTaskStep / AdminProcessingTaskStepStatus 枚举
- 禁止修改 AdminProcessingTaskPresentation 映射逻辑
- 禁止修改 processing task 状态机、操作行为
- 禁止混入 B10a 或 B11 文件

## 验收门槛

- mvn compile -pl . -q 通过
- 全量 mvn test 通过
- redline 无新增 BLOCKER
- 自查：AdminProcessingTaskItemResponse 双构造器保留且委托逻辑不变

## 完成后：回写 B10b → "已完成"，输出 B10b_fix_result_report.md
```

---

## 九、审查结论

- B10 共 13 个候选 DTO（含 2 个嵌套依赖 KnowledgeHelp 类），拆分为 **B10a（5 个 overview/pending）** 和 **B10b（8 个 processing task/knowledge help）**。
- **4 个 admin/service 文件排除**：`AdminProcessingTaskDisplayStatus`、`AdminProcessingTaskPresentation`、`AdminProcessingTaskStep`、`AdminProcessingTaskStepStatus` — 均非 api/admin DTO，但 agentA 写字段 Javadoc 时应引用这些枚举的语义。
- **好消息**：0 个 `@Data`，boolean getter 全部标准命名，无 B8a 式 Lombok 不一致问题。
- **坏消息**：B10b 的 7 个类（56 个 getter）当前**完全无 Javadoc**，是全部已完成批次中注释缺失最严重的子批次。
- `AdminProcessingTaskItemResponse` 是项目最大 DTO（45 字段 + 双构造器），需特别谨慎。
- 总可删除手写 getter：B10a 18 个 + B10b 78 个 = **96 个**。0 个 setter（全为 Response）。
- 高风险字段（`question`、`answer`、`errorMessage`、`evidenceJson`、`displayStatus`、`progressSteps[].status`）本轮仅标注契约语义。
