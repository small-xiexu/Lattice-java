# 导入进度 / 处理历史 前端合并调研报告

调研时间：2026-06-08
执行人：agentB（治理/归因 Agent）
类型：只读前端+后端调研，不改代码

---

## 1. 结论：可以合并，不需要后端改动

**处理历史可以直接并入"导入进度"页（knowledge-runs tab），不需要任何后端接口改动。** 两个面板已经共享同一个 API 端点（`/api/v1/admin/processing-tasks`），只是请求参数不同（`status=active` vs `status=terminal`）。前端只需改为调用 `status=all`，将活跃任务和已结束任务分别渲染在同一页面的不同区域即可。

---

## 2. 当前架构盘点

### 2.1 前端组件结构

| 组件 | 文件 | 定位 |
|------|------|------|
| 导入进度面板（活跃任务） | `management-runtime-part-01.js` | `knowledge-runs` tab 主区域，3s 轮询刷新，展示 RUNNING/QUEUED/FAILED/WAIT_CONFIRM 任务 |
| 处理历史面板（已结束任务） | `management-history-part.js` | `knowledge-runs` tab 底部 `<details>` 折叠面板，需手动展开，展示 SUCCEEDED/FAILED/SKIPPED 的已结束任务 |

### 2.2 API 调用对比

| 面板 | API 端点 | status 参数 | 作用 |
|------|------|:---:|------|
| 导入进度 | `/api/v1/admin/processing-tasks?limit=50` | 默认 `active` | 获取活跃任务（含 RUNNING/QUEUED/FAILED/WAIT_CONFIRM/PENDING/STALLED） |
| 处理历史 | `/api/v1/admin/processing-tasks?limit=50&status=terminal` | `terminal` | 获取已结束任务（SUCCEEDED/SKIPPED_NO_CHANGE/FAILED/STALLED） |

**合并方案**：调用 `/api/v1/admin/processing-tasks?limit=50&status=all`，在同一次请求中获取全部任务，前端按 `processingActive` 字段分流渲染。

### 2.3 单个任务条目的可用字段

`AdminProcessingTaskItemResponse` 已包含全部所需字段（约 30+ 字段）：

| 类别 | 字段 | 已有？ |
|------|------|:---:|
| 任务标识 | `taskId`, `taskType`, `title` | ✅ |
| 来源 | `sourceId`, `sourceName`, `sourceType` | ✅ |
| 状态 | `status`, `displayStatus`, `displayStatusLabel`, `displayTone` | ✅ |
| 编译 | `compileJobId`, `compileJobStatus`, `compileCurrentStep`, `compileProgressCurrent`, `compileProgressTotal`, `compileProgressMessage` | ✅ |
| 结果 | `publishedCount`（已入库文章数）, `pendingHumanReviewCount` | ✅ |
| 错误 | `errorMessage`, `reasonSummary`, `operationalNote` | ✅ |
| 时间 | `createdAt`, `updatedAt`（通过 `compileLastHeartbeatAt`） | ✅ |
| 操作 | `actions`（可用操作列表，含"再次同步"/"查看详情"等） | ✅ |
| 轮询 | `processingActive`（是否需要继续轮询）, `requiresManualAction` | ✅ |
| 步骤 | `progressSteps`（完整步骤链） | ✅ |

**不需要后端补字段。** 现有响应模型已完整覆盖导入进度和历史记录的全部展示需求。

---

## 3. 当前 UI 最大问题

### 不是接口数据不足，是信息层级

| 问题 | 说明 |
|------|------|
| 处理历史被藏在折叠面板里 | `<details id="processing-history-panel">` 默认折叠，用户需手动点击展开 |
| 活跃任务和历史任务分离调用 | 两个面板分别发 API 请求（`status=active` vs `status=terminal`），虽然调用同一个端点 |
| 历史记录不能滚动查看 | 展开后全部渲染，无"加载更多"或虚拟滚动 |
| 缺少"查看失败原因"的一键入口 | 目前只有"查看已入库内容"按钮跳转到 knowledge-articles tab |

---

## 4. 推荐最小前端改造方案

### 4.1 布局调整

```
┌─────────────────────────────────────────┐
│  导入进度                                │
│  ┌─────┐ ┌─────┐ ┌─────┐              │
│  │运行中│ │失败  │ │待确认│  ← 统计卡片  │
│  └─────┘ └─────┘ └─────┘              │
│                                         │
│  [活跃任务列表]                          │
│  ┌─────────────────────────────────┐    │
│  │ 任务1: 编译中 (3/5)              │    │
│  │ 任务2: 排队中                    │    │
│  └─────────────────────────────────┘    │
│                                         │
│  处理历史  [全部|已入库|失败|已跳过]      │
│  ┌─────────────────────────────────┐    │
│  │ SUCCEEDED  资料同步  5篇文章      │    │
│  │ FAILED     资料同步  错误原因     │    │  ← 可滚动
│  │ SKIPPED    资料同步  无变化       │    │
│  │ ...                             │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### 4.2 具体改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 1. 统一 API 调用 | `management-runtime-part-01.js` | 将 `loadProcessingTasks` 的请求改为 `status=all`，一次获取全部任务 |
| 2. 分流渲染 | `management-runtime-part-01.js` | 按 `processingActive` 分流：`true` → 渲染到活跃任务区；`false` → 渲染到历史列表 |
| 3. 历史列表改为可滚动 | `management-history-part.js` 或合并到 `management-runtime-part-01.js` | 设置 `max-height` + `overflow-y: auto`，显示最近 20 条 |
| 4. 去掉折叠面板 | `index.html` | 将 `<details>` 改为普通 `<section>`，始终可见 |
| 5. 历史条目增加操作入口 | `management-history-part.js` | "查看失败原因"按钮、"再次同步"按钮（复用现有 `actions` 字段） |

### 4.3 不需要改动的

- 后端 `AdminProcessingTaskController`：不变
- 后端 `AdminProcessingTaskService`：不变
- `AdminProcessingTaskItemResponse`：不变
- `AdminProcessingTaskListResponse`：不变
- 其他 tab（资料导入、已入库内容、结果反馈）：不变

---

## 5. 后续建议

### 交给 agentA（前端实现）

**修改范围**：
- `management-runtime-part-01.js`：API 调用改为 `status=all`，增加历史列表渲染逻辑
- `management-history-part.js`：可保留但简化（或合并到 runtime-part-01）
- `index.html`：处理历史区域从 `<details>` 改为普通 `<section>`

**不修改**：
- 后端任何 Java 文件
- 其他前端模块
- CSS（现有样式已够用）

### 验证建议
1. 确认活跃任务和历史记录在同一页面可见
2. 确认历史记录可滚动，默认显示最近 20 条
3. 确认"查看已入库内容"按钮仍可跳转
4. 确认"查看失败原因"可展示 `errorMessage` / `reasonSummary`
5. 确认无活跃任务时轮询自动停止

---

## 6. 明确声明

- [x] 未修改任何代码
- [x] 未提交 commit
- [x] 调研基于 `index.html` + `management-runtime-part-01.js` + `management-history-part.js` + `AdminProcessingTaskController.java` + `AdminProcessingTaskItemResponse.java` 源码只读分析
- [x] 后端接口字段充足，不需要补字段
- [x] 两个面板共享同一 API，合并只需改前端调用参数
- [x] 当前最大问题是信息层级（历史被折叠），不是接口数据不足
