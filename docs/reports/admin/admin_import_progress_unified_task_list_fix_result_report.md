# 导入进度统一任务列表 结果报告

时间：2026-06-08
执行人：agentA
依据：用户直接指令 — "彻底移除工作台里独立的'处理历史 / 已结束任务 / 历史任务'区域"

---

## 1. 修改范围

### 1.1 修改文件清单

| 文件 | 修改类型 |
|------|------|
| `src/main/resources/static/admin/index.html` | 结构重构：删除 `.embedded-run-history` 及样式，更新描述文案 |
| `src/main/resources/static/admin/modules/management-runtime-part-01.js` | API 参数：`?status=all` |
| `src/main/resources/static/admin/modules/management-runtime-part-03.js` | 核心逻辑：`selectRecentRunBoardItems` 返回全部、卡片统一渲染、FAQ 文案 |
| `src/main/resources/static/admin/modules/management-history-part.js` | 清理：移除 DOMContentLoaded 自动加载 |

**未修改任何 Java 后端文件。**

### 1.2 改动详情

| 改动 | 文件 | 说明 |
|------|------|------|
| 删除 `.embedded-run-history` 区域 | `index.html` | 彻底移除独立的"处理历史" section，含 `#history-list`、筛选栏、刷新按钮 |
| 删除 `.embedded-run-history` 相关样式 | `index.html` | 移除 `<style>` 块中的 `.embedded-run-history` 和 `.history-scroll-list` |
| 更新面板描述 | `index.html` | 导入进度描述改为"所有处理任务按时间排列：运行中、待确认和失败的排前面，已完成的往下滑就能看到" |
| API 参数改为 `status=all` | `management-runtime-part-01.js` | `loadProcessingTasks` 请求从默认 active 改为 `?status=all&limit=...` |
| `selectRecentRunBoardItems` 返回全部任务 | `management-runtime-part-03.js` | 活跃任务排前面、已结束任务排后面，不再只返回 board-focus 子集 |
| `renderRecentRunBoardItem` 统一卡片渲染 | `management-runtime-part-03.js` | 始终使用 `renderRecentRunCard`，不再区分卡片/紧凑通知 |
| 空态文案更新 | `management-runtime-part-03.js` | 不再提及"处理历史"或"归档"，改为"往下滚动可以看到..." |
| 完成标记文案 | `management-runtime-part-03.js` | "完整记录在资料源处理历史" → "已完成" |
| 移除 DOMContentLoaded 初始化 | `management-history-part.js` | 删除引用已删除 DOM 元素的自动加载逻辑，保留函数供其他模块调用 |

---

## 2. 合并后的 UI 结构

```
┌─────────────────────────────────────────────┐
│  section.panel: 导入进度                      │
│  ├─ 统计卡片 (运行中/待人工确认/失败)          │
│  └─ #job-list.run-board                     │
│     (max-height + overflow:auto)             │
│     ├─ 活跃任务卡片 (RUNNING/QUEUED/...)     │
│     ├─ 活跃任务卡片 (FAILED/STALLED)          │
│     ├─ 活跃任务卡片 (WAIT_CONFIRM)            │
│     ├─ 已结束任务卡片 (SUCCEEDED)             │  ← 同一列表，往下滚动
│     └─ 已结束任务卡片 (SKIPPED_NO_CHANGE)     │
├─────────────────────────────────────────────┤
│  section.panel: 待人工确认 (不变)              │
├─────────────────────────────────────────────┤
│  details#source-diagnostic-panel (不变)       │
└─────────────────────────────────────────────┘
```

**关键变化**：
- "处理历史"不再作为独立区域存在
- 所有任务（活跃 + 已结束）在同一个 `#job-list` 中按时间排列
- 活跃任务始终排在最前面，已结束的排在后面
- 所有条目统一使用卡片展示（`run-spotlight-card`），不再有紧凑通知
- `.run-board` 已有 `overflow: auto` 滚动支持

---

## 3. 数据流

```
loadProcessingTasks() [runtime]
  │
  ▼
/api/v1/admin/processing-tasks
  ?limit=50&status=all
  │
  ▼
selectRecentRunBoardItems()
  ├─ activeItems  (shouldRenderRunAsBoardFocus → true)
  └─ terminalItems (其他所有)
  │
  ▼
renderRecentRunBoardItem() → renderRecentRunCard()  统一卡片
  │
  ▼
reconcileRecentRunCollection() → #job-list
```

单次 API 调用获取全部任务，前端按 `processingActive` 分流排序，统一卡片渲染到同一个可滚动列表。

---

## 4. 功能验证清单

| 功能 | 状态 | 说明 |
|------|:---:|------|
| 独立处理历史区域已删除 | ✅ | `.embedded-run-history` 完全移除 |
| 统一 API 调用 `status=all` | ✅ | `management-runtime-part-01.js` |
| 活跃任务排前面 | ✅ | `selectRecentRunBoardItems` 先 active 后 terminal |
| 已结束任务在同一列表 | ✅ | `terminalItems` 追加在 activeItems 后面 |
| 统一卡片展示 | ✅ | `renderRecentRunBoardItem` 始终用 `renderRecentRunCard` |
| `#job-list` 可滚动 | ✅ | `.run-board` 已有 `overflow:auto` + `max-height` |
| 空态文案更新 | ✅ | 不再提"处理历史"/"归档" |
| history-part.js DOM 初始化已移除 | ✅ | 不再自动 bind 已删除元素 |
| 后端无修改 | ✅ | 0 个 Java 文件修改 |
| 编译通过 | ✅ | `mvn package -DskipTests` |

---

## 5. 明确声明

- [x] 仅修改前端文件：`index.html` + `management-runtime-part-01.js` + `management-runtime-part-03.js` + `management-history-part.js`
- [x] 独立的"处理历史"区域已彻底移除
- [x] 所有任务在同一个 `#job-list` 可滚动列表中展示
- [x] 活跃任务排前面，已结束任务排后面
- [x] 所有条目统一使用卡片渲染
- [x] 未修改任何后端 Java 文件
- [x] 未修改测试、数据库、配置
- [x] redline BLOCKER=0
- [x] 未提交 commit
