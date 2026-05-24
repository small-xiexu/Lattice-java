# Admin 处理历史 Tab 设计方案

- 生成时间：2026-05-22
- 执行 Agent：agentB（只读设计）
- 分支：`codex/qa-polish`
- 本轮是否修改代码：**否**

---

## 结论先行

1. **处理历史 Tab 放在哪里**：与 `资料导入 / 当前处理任务 / 已入库内容 / 结果反馈` **同级**，作为第 5 个一级 Tab `处理历史`，使用现有 `data-tab-panel` + `data-tab-trigger` 架构。
2. **边界**：`当前处理任务` 只保留 `RUN_BOARD_FOCUS_STATUSES`（运行中/排队中/失败/暂停/待确认），`处理历史` 展示所有终态任务（已入库/已完成/已驳回/已跳过/失败）。
3. **后端最小改动**：`GET /api/v1/admin/processing-tasks` 增加可选参数 `status`（终态/活跃/全部），无需新建 API 端点。约 10 行 Java。
4. **前端最小改动**：`admin/index.html` 增加 Tab 按钮 + 面板，新建 `management-history-part.js` 渲染历史列表。约 120 行 JS + 15 行 HTML。
5. **失败任务留在当前页**（需人工关注），同时进入历史页（可追溯）。
6. **点击历史记录 → 跳转到对应资料源详情**（`knowledge-articles` tab + 自动选中对应 source），不作为本页内抽屉。
7. **本轮不做**：分页、关键词搜索、批量操作、历史清理、导出。

---

## 1. 当前入口问题

### 1.1 用户痛点

`phase_current_workspace_pending_fixes.md` 第 A 项描述：

> 完成任务历史入口不清晰：
> - 当前页说明"完整历史放在对应资料源里查看"
> - 但页面没有明确入口，用户不知道已完成编译记录去哪里找
> - 顶部 tab 也没有 `处理历史` / `编译记录` 这类一级入口

### 1.2 现有入口位置

经代码探查，当前"处理历史"的入口藏在 `knowledge-runs` Tab 内的第三个子区域"资料源与文件"中（`admin/index.html:345-353`）：

```
当前处理任务 tab
  ├── 当前处理任务（任务卡片列表）
  ├── 待人工确认（审查队列）
  └── 资料源与文件
        ├── 资料源列表（左侧）
        └── 资料源详情（右侧）
              ├── 处理历史  ← 只有先选中某个资料源才能看到
              ├── 文件解析方式
              └── 资料源配置
```

**问题**：这个"处理历史"是**按资料源筛选**的（调 `GET /api/v1/admin/sources/{sourceId}/processing-tasks`），用户必须先找到对应资料源，点击后才能看到该资料源的历史。没有一个全局的处理历史视图。

---

## 2. 现有接口与数据结构

### 2.1 API 端点现状

| 端点 | 方法 | 用途 | 局限性 |
|------|------|------|--------|
| `/api/v1/admin/processing-tasks?limit=N` | GET | 全局处理任务列表 | limit 仅 1-50，无 status 过滤，按 source 折叠 |
| `/api/v1/admin/sources/{id}/processing-tasks?limit=N` | GET | 按资料源查处理历史 | 需先知道 sourceId |
| `/api/v1/admin/jobs` | GET | 原始 compile_jobs 全量 | 无分页，无 source_sync_run 上下文 |

### 2.2 数据来源

`AdminProcessingTaskService.listProcessingTasks(limit)` 聚合两张表：

| 表 | 模型 | 任务类型 |
|----|------|---------|
| `source_sync_runs` | `SourceSyncRun` | 资料同步任务（导入 + 编译） |
| `compile_jobs` | `CompileJobRecord` | 独立编译任务（`source_sync_run_id IS NULL`） |

### 2.3 当前折叠逻辑（关键限制）

`listProcessingTasks()` 对同一 source 的多个 sync run 会**折叠为一条**（`collapseCurrentSourceRuns()`），只保留最新的一条。这意味着：

- 同一资料源的多次历史编译在 `processing-tasks` 接口中**被折叠隐藏**
- 只有 `listProcessingTasksBySourceId(sourceId)` 不做折叠，能返回该 source 的全部历史

### 2.4 终态与活跃状态定义

基于 `AdminProcessingTaskDisplayStatus` 枚举：

| 分类 | displayStatus | 说明 |
|------|-------------|------|
| **活跃** | `MATCHING` | 匹配资料中 |
| **活跃** | `MATERIALIZING` | 资料处理中 |
| **活跃** | `COMPILE_QUEUED` | 编译排队中 |
| **活跃** | `RUNNING` | 编译运行中 |
| **活跃** | `QUEUED` | 排队中 |
| **特殊** | `WAIT_CONFIRM` | 待人工确认（非活跃，需人工动作） |
| **终态-成功** | `SUCCEEDED` | 已完成 |
| **终态-成功** | `SKIPPED_NO_CHANGE` | 无变更跳过 |
| **终态-失败** | `FAILED` | 失败 |
| **终态-失败** | `STALLED` | 暂停/卡住 |

### 2.5 前端当前筛选

`management-runtime-part-01.js` 中 `RUN_BOARD_FOCUS_STATUSES` 定义了"需要在当前页展示"的状态集合：

```
RUNNING, QUEUED, MATCHING, MATERIALIZING, COMPILE_QUEUED,
PENDING, FAILED, STALLED, WAIT_CONFIRM
```

`SUCCEEDED` 和 `SKIPPED_NO_CHANGE` 不在其中——它们被渲染为轻量 `completionNotice`（一行提示），用户几乎注意不到。

---

## 3. 推荐信息架构

### 3.1 Tab 结构调整

**调整前**（4 个 Tab）：
```
资料导入 | 当前处理任务 | 已入库内容 | 结果反馈
```

**调整后**（5 个 Tab）：
```
资料导入 | 当前处理任务 | 处理历史 | 已入库内容 | 结果反馈
```

`处理历史` 位于 `当前处理任务` 右侧，语义上"当前 → 历史"形成自然的时间流。

### 3.2 "当前处理任务"与"处理历史"的边界

| 展示内容 | 当前处理任务 | 处理历史 |
|---------|:-----------:|:-------:|
| 运行中任务（RUNNING） | ✅ | ❌ |
| 排队中任务（QUEUED/COMPILE_QUEUED） | ✅ | ❌ |
| 匹配/处理中（MATCHING/MATERIALIZING） | ✅ | ❌ |
| 暂停/卡住（STALLED） | ✅ 需关注 | ✅ 可追溯 |
| 失败（FAILED） | ✅ 需关注 | ✅ 可追溯 |
| 待人工确认（WAIT_CONFIRM） | ✅ 需动作 | ✅ 可追溯 |
| 已完成（SUCCEEDED） | ❌ | ✅ |
| 无变更跳过（SKIPPED_NO_CHANGE） | ❌ | ✅ |
| 已驳回（REJECTED） | ❌ | ✅ |

**原则**：`当前处理任务` = 需要用户关注或等待的任务；`处理历史` = 已有最终结果、用于追溯和审计的任务。

### 3.3 失败/暂停任务的双重展示

`STALLED` 和 `FAILED` **同时出现在两个 Tab**：
- 当前处理任务：用户能立即看到异常，采取重试或排查
- 处理历史：用户能回溯某个资料源的历史失败模式

这不矛盾——当前页突出"需要关注"，历史页提供"完整审计线索"。

### 3.4 历史记录列表字段

| 字段 | 来源 | 示例 |
|------|------|------|
| 资料名 | `sourceName` 或 `title` | "IoT桥接器技术文档" |
| 来源类型 | 资料导入 / 独立编译 | 标签区分 |
| 提交时间 | `requestedAt` | 2026-05-22 14:30 |
| 完成时间 | `updatedAt`（终态时） | 2026-05-22 14:35 |
| 最终状态 | `displayStatus` → 中文 | 已入库 / 失败 / 已驳回 |
| 处理耗时 | `updatedAt - requestedAt` | 5 分 23 秒 |
| 生成文章数 | `persistedArticleCount` | 5 篇 |
| 操作 | 按钮 | 查看详情 |

### 3.5 状态中文映射

| displayStatus | 历史页展示 | 色调 |
|--------------|-----------|------|
| `SUCCEEDED` | 已入库 | 绿色 |
| `SKIPPED_NO_CHANGE` | 无变更（已跳过） | 灰色 |
| `FAILED` | 失败 | 红色 |
| `STALLED` | 已暂停 | 红色 |
| `WAIT_CONFIRM`（已确认入库） | 已入库 | 绿色 |
| `WAIT_CONFIRM`（已驳回） | 已驳回 | 橙色 |

### 3.6 点击历史记录后的跳转

**推荐：跳转到对应资料源详情页**（`knowledge-articles` Tab + 自动选中 source）。

理由：
1. 用户查看历史的最终目的是了解"这个资料源到底处理出了什么结果"
2. `已入库内容` 页已有按资料源筛选的完整功能
3. 当前后端没有"编译任务独立详情页"（`/api/v1/admin/jobs/{id}` 返回的是原始 CompileJobRecord，缺少展示层格式化）
4. 避免重复建设详情页

**不推荐**：
- 本页内抽屉：需额外建设详情组件，增加本轮复杂度
- 编译任务原始 JSON：不适合普通用户阅读

**实现方式**：点击历史记录 → 前端调用 `window.AdminTabs.activate("knowledge-console", "knowledge-articles", { sourceId: item.sourceId })` → 已入库内容页接收 sourceId 参数，自动筛选。

---

## 4. 后端改动方案

### 4.1 所需改动

**文件**：`AdminProcessingTaskController.java`

在现有 `GET /api/v1/admin/processing-tasks` 增加可选参数 `status`：

```
GET /api/v1/admin/processing-tasks?limit=50&status=terminal
```

| status 值 | 含义 | 返回 |
|-----------|------|------|
| （不传） | 当前行为不变 | 合并后按 limit 截断 |
| `active` | 仅活跃任务 | MATCHING/MATERIALIZING/COMPILE_QUEUED/RUNNING/QUEUED |
| `terminal` | 仅终态任务 | SUCCEEDED/SKIPPED_NO_CHANGE/FAILED/STALLED/WAIT_CONFIRM |
| `all` | 全部 | 不做折叠，返回全部原始记录 |

**文件**：`AdminProcessingTaskService.java`

`listProcessingTasks()` 方法增加 `status` 参数：
- `status=terminal` 或 `all` 时：**不做 `collapseCurrentSourceRuns()` 折叠**，返回每次 sync run 的独立记录
- `status=active` 时：保持当前行为（折叠 + 仅活跃状态）
- 终态任务按 `updatedAt DESC` 排序

**改动量估算**：Controller ~5 行，Service ~20 行，总计约 25 行 Java。

### 4.2 不需要的改动

- **不新建 API 端点**：复用 `processing-tasks`，加参数即可
- **不改造数据库查询**：现有 `listRecentRuns(limit)` 和 `listRecentStandaloneJobs(limit)` 已够用，只是需要调整 limit 和折叠逻辑
- **不新增分页**：本轮 `limit` 参数（最大 50）足够历史首页展示；真分页后续增强
- **不新增 DTO 字段**：`AdminProcessingTaskItemResponse` 现有的 `sourceId`、`sourceName`、`requestedAt`、`displayStatus`、`persistedArticleCount` 等字段已覆盖历史列表需求

---

## 5. 前端改动方案

### 5.1 文件改动清单

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `admin/index.html` | 修改 | 新增 Tab 按钮 + 面板 HTML |
| `management.js` | 修改 | 引入新 JS 片段 |
| `management-history-part.js` | **新建** | 处理历史列表渲染逻辑 |
| `management-runtime-part-01.js` | 修改 | `normalizeKnowledgeTab()` 增加 `knowledge-history`；新增 `loadProcessingHistory()` |

### 5.2 index.html 改动

**Tab 按钮**（在 `knowledge-tab-runs` 之后插入）：

```html
<button id="knowledge-tab-history" data-tab-trigger="knowledge-history">处理历史</button>
```

**Tab 面板**（在 `knowledge-runs` 面板之后插入）：

```html
<section class="tab-panel" data-tab-panel="knowledge-history" hidden>
    <!-- 状态筛选栏 -->
    <div class="history-filter-bar">
        <button data-history-filter="all" class="active">全部</button>
        <button data-history-filter="succeeded">已入库</button>
        <button data-history-filter="failed">失败</button>
        <button data-history-filter="rejected">已驳回</button>
    </div>
    <!-- 历史列表 -->
    <div id="history-list"></div>
    <!-- 状态提示 -->
    <p id="history-status" class="inline-status"></p>
</section>
```

### 5.3 management-history-part.js 核心逻辑

```
// 状态管理
const historyState = {
    items: [],
    filter: "all",       // all | succeeded | failed | rejected
    loading: false
};

// 加载历史（仅在切到 Tab 时触发，不做轮询）
async function loadProcessingHistory() {
    const response = await fetchJson("/api/v1/admin/processing-tasks?limit=50&status=terminal");
    historyState.items = response.items || [];
    applyFilterAndRender();
}

// 前端筛选（无需额外 API 调用）
function applyFilterAndRender() {
    let items = historyState.items;
    if (historyState.filter === "succeeded") {
        items = items.filter(isSucceeded);
    } else if (historyState.filter === "failed") {
        items = items.filter(isFailed);
    } else if (historyState.filter === "rejected") {
        items = items.filter(isRejected);
    }
    renderHistoryList(items);
}

// 渲染历史列表
function renderHistoryList(items) {
    // 每条记录渲染为：资料名 | 标签 | 提交时间 | 耗时 | 文章数 | 状态 | 查看详情
}

// 点击查看详情 → 跳转到已入库内容并自动筛选该资料源
function viewHistoryDetail(item) {
    window.AdminTabs.activate("knowledge-console", "knowledge-articles", {
        sourceId: item.sourceId
    });
}
```

### 5.4 与当前处理任务的关键差异

| 维度 | 当前处理任务 | 处理历史 |
|------|------------|---------|
| 数据来源 | `processing-tasks` 默认 | `processing-tasks?status=terminal` |
| 轮询 | ✅ 3 秒 | ❌ 手动刷新 |
| 折叠同 source | ✅ 折叠 | ❌ 不折叠（展示每次运行） |
| 展示形式 | 任务卡片 + 进度条 | 简洁列表行 |
| DOM 动画 | 软刷新 reconcile | 静态替换 |

### 5.5 前端筛选实现

使用**前端筛选**而非后端过滤。理由：
1. 终态任务量短期内不会超过 50 条（`limit=50`），前端筛选无性能问题
2. 减少 API 请求次数（一次加载，多次切换筛选）
3. 如果未来历史量增长，再改为后端分页 + 后端筛选

### 5.6 无需轮询

历史任务状态不会变化（已终态），切到 Tab 时加载一次即可。提供手动"刷新"按钮（复用 `#refresh-jobs` 或新增）。

---

## 6. 最小实施方案

### 6.1 后端改动

| 文件 | 改动 | 行数 |
|------|------|------|
| `AdminProcessingTaskController.java` | `listProcessingTasks()` 增加 `@RequestParam status` 参数 | ~5 行 |
| `AdminProcessingTaskService.java` | `listProcessingTasks()` 增加 status 参数，terminal 时不折叠 | ~20 行 |
| **总计** | **2 个文件** | **~25 行 Java** |

### 6.2 前端改动

| 文件 | 改动 | 行数 |
|------|------|------|
| `admin/index.html` | 新增 Tab 按钮（1 行）+ 面板 HTML（~15 行） | ~16 行 |
| `management.js` | 新增 history-part 的 import + 拼接 | ~3 行 |
| `management-history-part.js` | **新建**：状态管理、加载、筛选、渲染、跳转 | ~120 行 |
| `management-runtime-part-01.js` | `normalizeKnowledgeTab()` 增加 `knowledge-history` | ~1 行 |
| `management-runtime-part-03.js` | 移除当前页底部 completionNotice 的渲染（终态任务已移走） | ~5 行 |
| **总计** | **4 个修改 + 1 个新建** | **~145 行** |

### 6.3 允许修改的文件

- `AdminProcessingTaskController.java`
- `AdminProcessingTaskService.java`
- `admin/index.html`
- `management.js`
- `management-runtime-part-01.js`
- `management-runtime-part-03.js`
- `management-history-part.js`（新建）

### 6.4 禁止修改的文件

- `AdminProcessingTaskPresentationResolver.java`：不改展示层解析逻辑
- `AdminProcessingTaskDisplayStatus.java`：不改枚举定义
- `CompileJobService.java` / `SourceUploadService.java`：不改数据查询层
- `admin/compile-review-queue.js`：agentA 可能正在修改审查队列展示
- `management-runtime-part-02.js` / `part-04.js` / `part-05.js`：文章详情渲染，agentA 正在修改关键词折叠
- `admin-tabs.js`：不改 Tab 框架
- `AdminCompileReviewSummaryService.java`：不改审查摘要
- 所有 `src/test/java/**`：除非测试因 status 参数而需要更新

---

## 7. 风险与测试建议

### 7.1 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| `status=terminal` 返回过多记录 | 低 | limit 上限 50，短期够用；长期加后端分页 |
| 历史 Tab 空白（无终态任务） | 低 | 显示"暂无处理历史"空状态 |
| 点击"查看详情"跳转到已入库内容，但该 source 无已入库文章 | 低 | 跳转后已入库内容页自动显示该 source 的文章列表（可能为空），用户可理解 |
| 与 agentA 的文件冲突 | 中 | 明确禁止修改 `compile-review-queue.js` / `management-runtime-part-02.js` / `part-04.js` / `part-05.js`，历史 Tab 只操作独立新文件和明确列出的文件 |
| `normalizeKnowledgeTab()` 增加新 tab 名 | 低 | 1 行改动，不影响现有 tab |
| 终态任务状态展示不一致 | 低 | 复用现有 `AdminProcessingTaskDisplayStatus` 的 label/tone，确保与当前页一致 |

### 7.2 测试建议

**人工验收（无需自动化测试）**：
1. 打开管理后台 → 确认顶部出现"处理历史"Tab
2. 点击"处理历史"Tab → 确认加载终态任务列表
3. 确认列表包含：资料名、来源类型、提交时间、完成时间、状态、文章数、查看详情按钮
4. 切换筛选按钮（全部/已入库/失败/已驳回）→ 确认前端筛选正确
5. 点击某条历史的"查看详情"→ 确认跳转到已入库内容 Tab 并自动筛选对应资料源
6. 切回"当前处理任务"Tab → 确认不再出现 SUCCEEDED 的 completionNotice
7. 确认失败/暂停任务同时出现在"当前处理任务"和"处理历史"中
8. 确认历史 Tab 不做自动轮询（打开浏览器 DevTools Network 面板观察）

**边界测试**：
- 新环境无任何终态任务 → 确认展示空状态
- 终态任务接近 50 条 → 确认不卡顿

**回归测试**：
- 当前处理任务 Tab 的轮询、卡片渲染、进度条无退化
- 资料导入、已入库内容、结果反馈 Tab 无退化
- `GET /api/v1/admin/processing-tasks` 不带 status 参数时行为不变

---

## 8. 未来增强（本轮不做）

| 增强项 | 说明 | 优先级 |
|--------|------|--------|
| 后端分页（offset/page） | 历史量大时分页加载 | 中 |
| 关键词搜索 | 按资料名搜索历史 | 中 |
| 时间范围筛选 | 按日期范围过滤 | 低 |
| 批量重试失败任务 | 多选后批量重试 | 低 |
| 历史清理/归档 | 删除 N 天前的历史记录 | 低 |
| 导出 CSV | 导出历史记录为 CSV | 低 |
| 编译任务独立详情页 | 不跳转已入库内容，而是弹窗/侧边栏展示任务详情 JSON | 低 |
| 历史统计图表 | 成功率趋势、耗时分布等 | 低 |
| 资料源维度聚合 | 按资料源分组展示历史摘要 | 低 |

---

## 9. 本轮确认

- **是否修改了 `src/main/java/**`**：否
- **是否修改了 `src/main/resources/static/**`**：否
- **是否修改了任何配置/文档/脚本**：否
- **是否提交了任何代码**：否
- **仅执行**：代码探查、Tab 架构分析、API 结构分析、数据流追踪、方案设计
