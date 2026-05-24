# Admin 处理历史 Tab 作用域修复修订报告

- 生成时间：2026-05-22
- 任务类型：最小 revision（修复 IIFE 作用域问题 + 补 JS runtime 测试）
- 上一轮报告：`admin_processing_history_tab_fix_result_report.md`

---

## 1. 修改了哪些文件

| 文件 | 变更 | 说明 |
|------|------|------|
| `management.js` | **修改 1 行** | `runtimeParts` 数组中 `partHistory` 从 `part05` 之后移到 `part05` 之前 |
| `management-history-part.js` | **修改 ~1 行 + 新增 ~10 行** | 修正 export 末尾分号；新增 `__LATTICE_ADMIN_TEST__` 测试导出 guard |
| `ManagementJsRuntimeTests.java` | **修改 ~25 行 + 新增 ~35 行** | harness 加入 history 模块解析；新增 15 条断言 |

**未修改的文件：**
- `src/main/java/**`：零修改
- `AdminProcessingTaskController.java` / `AdminProcessingTaskService.java`：零修改
- `index.html`：零修改
- `management-runtime-part-01.js` ~ `part-05.js`：零修改

---

## 2. 是否只做最小 revision

**是。** 本轮仅做了三件事：
1. 将 `partHistory` 移到 `part05` 之前，使其在主 IIFE 内部运行
2. 在 history 模块中补 `__LATTICE_ADMIN_TEST__` 测试导出 guard
3. 在 `ManagementJsRuntimeTests` 中补 history 模块断言和 URL 验证

---

## 3. IIFE 作用域问题根因

### 3.1 拼接结构

`management.js` 将 `runtimeParts` 数组用 `\n` 拼接后通过 `new Function(...)()` 执行：

```
part01: (function () {\n    const PROCESSING_TASK_LIST_LIMIT = 50; ...
part02: ...
part03: ...
part04: ...
part05: ... })();\n
partHistory:     var historyState = ...    ← 在 IIFE 外部！
```

`part05` 末尾包含 `})();` 关闭了整个主 IIFE。第一版将 `partHistory` 放在 `part05` 后面，导致 history 模块运行在 IIFE 外部，无法访问 `fetchJson`、`escapeHtml`、`formatDateTime`、`renderBadge`、`getBadgeLabel`、`parseOptionalInteger`、`activateKnowledgeTab` 等内部函数。

### 3.2 修复方案

将 `partHistory` 移到 `part05` 之前：

```
part01: (function () {\n    const PROCESSING_TASK_LIST_LIMIT = 50; ...
part02: ...
part03: ...
part04: ...
partHistory:     var historyState = ...      ← 在 IIFE 内部，可访问所有内部函数
part05: ... })();\n
```

### 3.3 关键设计点

- **不修改 `part05`**：`part05` 末尾的 `})();` 不变，仅是 history 模块在它之前执行
- **history 模块不用 `})();` 闭合**：history 模块不包含 IIFE 闭合，只是 IIFE 内部的又一代码片段
- **测试导出放在 history 模块内部**：在 IIFE 内部通过 `globalThis.__LATTICE_ADMIN_TEST__` 导出，与 `part05` 的导出模式一致

---

## 4. 此前修复是否回退

**否。** 以下修复全部保持：

| 修复项 | 状态 |
|--------|------|
| 后端 `status=terminal` 不过滤 WAIT_CONFIRM | 保持 |
| 后端 `terminal`/`all` 不做 collapse | 保持 |
| Tab 按钮 + 面板 HTML | 保持 |
| 筛选栏（全部/已入库/失败/已跳过） | 保持 |
| 历史列表渲染 + 耗时计算 | 保持 |
| 点击查看详情跳转到已入库内容 | 保持 |
| `normalizeKnowledgeTab` 注册 knowledge-history | 保持 |
| `management.js` import 声明 | 保持 |

---

## 5. 测试补充

### 5.1 harness 改造

原 harness 仅读取 `management-runtime-part-*` 文件。现修改为：
- 读取 runtime parts 01–04
- 读取 `management-history-part.js` 并插入
- 读取 runtime part-05 放在最后

拼接顺序与真实 `management.js` 完全一致：`part01, part02, part03, part04, partHistory, part05`

### 5.2 新增断言

在 `shouldVerifyRunFallbackAndErrorPresentationViaNode` 中追加：

| 断言 | 说明 |
|------|------|
| `__LATTICE_ADMIN_TEST__.history` 存在 | 验证 history 模块测试导出 |
| 4 个函数导出类型为 function | `loadProcessingHistory` / `applyHistoryFilterAndRender` / `renderHistoryItem` / `formatElapsed` |
| `formatElapsed(null, null) === "—"` | 空值返回 em-dash |
| `renderHistoryItem` 包含资料名 | SUCCEEDED 任务正确渲染 source name |
| `renderHistoryItem` 包含来源类型 pill | 资料同步 / 独立编译标签 |
| `renderHistoryItem` 包含 `data-history-source-id` | 有 sourceId 时渲染查看详情按钮 |
| 无 sourceId 时不渲染按钮 | DIRECT_COMPILE 任务无 sourceId 的场景 |
| `loadProcessingHistory` 源码含正确 URL | `/api/v1/admin/processing-tasks` + `status=terminal` + `limit=50` |

### 5.3 测试结果

- `ManagementJsRuntimeTests`（3 个测试）全部通过，新增断言全部通过
- `AdminProcessingTaskControllerTests`（6 个测试）全部通过，无回归

---

## 6. 建议 agentD 做 runtime 验证

重点验证：
1. 打开管理后台 → 点击"处理历史"Tab → 确认不再出现 ReferenceError
2. 确认历史列表正常加载（资料名、状态、耗时、文章数、查看详情按钮）
3. 切换筛选按钮（全部/已入库/失败/已跳过）→ 确认前端筛选正常
4. 点击"查看详情"→ 确认跳转到已入库内容 Tab 并自动筛选对应资料源
5. 点"刷新历史"按钮 → 确认重新加载
