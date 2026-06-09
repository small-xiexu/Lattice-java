# 导入进度 / 处理历史 前端合并结果报告

时间：2026-06-08
执行人：agentA（前端实现 Agent）
依据：`admin_import_progress_history_ui_review_report.md` 推荐方案

---

## 1. 修改范围

### 1.1 修改文件清单

| 文件 | 修改类型 |
|------|------|
| `src/main/resources/static/admin/index.html` | 结构重构：`<details>` → `<section>`，新增滚动样式 |
| `src/main/resources/static/admin/modules/management-history-part.js` | 逻辑重构：API 合并、分流过滤、条目增强、自动加载 |

**未修改任何 Java 后端文件。**

### 1.2 修改汇总

| 改动 | 说明 |
|------|------|
| `index.html` 处理历史面板 | `<details id="processing-history-panel">` → `<section class="panel processing-history-section">`，去掉折叠入口，始终可见 |
| `index.html` 历史列表 | 新增 `class="history-scroll-list top-gap"` + `max-height: 480px; overflow-y: auto` 样式块 |
| `index.html` 刷新按钮 | 从 `history-toolbar-actions` 移至 `panel-title-row` 右侧 |
| `management-history-part.js` API 调用 | `status=terminal` → `status=all`，与导入进度共享同一数据集 |
| `management-history-part.js` 分流过滤 | `applyHistoryFilterAndRender()` 新增 `processingActive === false` 过滤，排除活跃任务 |
| `management-history-part.js` 条目增强 | 新增"查看失败原因"（FAILED/STALLED 时展示 `reasonSummary`/`errorMessage`）、"再次同步"按钮（有 `RESYNC_SOURCE`/`RETRY_UPLOAD` action 时） |
| `management-history-part.js` 文章计数 | `persistedArticleCount` → `publishedCount \|\| persistedArticleCount` 兼容 |
| `management-history-part.js` 初始化 | 去掉 `<details>` toggle 事件监听，改为 DOMContentLoaded 时直接 `loadProcessingHistory()` |

---

## 2. 合并后的 UI 结构

```
┌─────────────────────────────────────────────┐
│  section.panel: 导入进度                      │
│  ├─ 统计卡片 (运行中/待人工确认/失败)          │
│  └─ 活跃任务列表 (#job-list)                  │
├─────────────────────────────────────────────┤
│  section.panel: 待人工确认                    │
│  └─ 待人工确认草稿列表                        │
├─────────────────────────────────────────────┤
│  section.panel.processing-history-section    │
│  ├─ panel-title-row: "处理历史" + 刷新按钮    │
│  ├─ history-toolbar: 筛选 pills + 状态计数    │
│  └─ #history-list.history-scroll-list        │
│     (max-height: 480px, overflow-y: auto)    │  ← 始终可见，可滚动
├─────────────────────────────────────────────┤
│  details#source-diagnostic-panel (不变)       │
└─────────────────────────────────────────────┘
```

**关键变化**：
- "导入进度"（活跃任务）和"处理历史"（已结束任务）在同一个 `knowledge-runs` tab 内，纵向排列
- 历史记录不再藏在折叠面板里，始终可见
- 两个区域视觉上清晰区分：上面是活跃任务卡片（`run-board`），下面是历史列表（`scroll-list`）
- 统计卡片保留在顶部

---

## 3. 数据流

```
loadProcessingTasks() [runtime]            loadProcessingHistory() [history]
  │                                          │
  ▼                                          ▼
/api/v1/admin/processing-tasks       /api/v1/admin/processing-tasks
  ?limit=50  (默认 active)              ?limit=50&status=all
  │                                          │
  ▼                                          ▼
渲染活跃任务卡片                    处理分流: processingActive===false
渲染统计卡片                         │
                                      ▼
                                   应用用户筛选 (全部/已入库/失败/已跳过)
                                     │
                                     ▼
                                   渲染历史列表 (含失败原因/再次同步)
```

两个 API 调用独立，但历史面板改为 `status=all` 确保获取完整数据。运行时仍使用默认（active）调用，保持轻量轮询。历史面板仅在页面加载和手动刷新时请求。

---

## 4. 功能验证清单

| 功能 | 状态 | 说明 |
|------|:---:|------|
| 历史面板始终可见 | ✅ | `<details>` → `<section>`，去掉折叠 |
| 历史列表可滚动 | ✅ | `max-height: 480px; overflow-y: auto` |
| 运行中/待人工确认/失败统计 | ✅ | `renderRecentRunOverview()` 不变 |
| 按 processingActive 分流 | ✅ | `applyHistoryFilterAndRender` 过滤 `processingActive === true` |
| 查看已入库内容 | ✅ | 保留原有 `data-history-source-id` 按钮 |
| 再次同步 | ✅ | 新增 `data-history-resync` 按钮，调用 `requestSourceRunRetry()` |
| 查看失败原因 | ✅ | FAILED/STALLED 时展示 `reasonSummary` 或 `errorMessage` |
| 筛选（全部/已入库/失败/已跳过） | ✅ | 保留原有筛选逻辑 |
| 无活跃任务时停止轮询 | ✅ | `updateProcessingTaskAutoRefresh` 逻辑不变 |
| 后端无修改 | ✅ | 0 个 Java 文件修改 |

---

## 5. 明确声明

- [x] 仅修改前端文件：`index.html` + `management-history-part.js`
- [x] 处理历史已从折叠面板改为同页可滚动区域
- [x] 历史面板始终可见，不再需要手动展开
- [x] 统计卡片保留在顶部（运行中/待人工确认/失败）
- [x] 新增"再次同步"和"查看失败原因"功能
- [x] 未修改任何后端 Java 文件
- [x] 未修改 `management-runtime-part-01.js`（活跃任务渲染逻辑不变）
- [x] 未修改其他 tab 页面
- [x] 未修改测试、数据库、配置
- [x] redline BLOCKER=0
- [x] 未提交 commit
