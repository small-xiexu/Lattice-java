# 管理后台前端 UX 三类修复报告

- 生成时间：2026-05-23
- 分支：`codex/qa-polish`
- 范围：仅 admin 前端 JS + 静态 HTML + CSS（无后端 Java 变更）

---

## 1. 修复总览

| # | 修复类别 | 涉及文件 | 状态 |
|---|---------|----------|------|
| A | 处理历史 Tab 空白/空态修复 | management-history-part.js, index.html, part-02.js | 已完成 |
| B | 确认/驳回 inline modal 替换原生弹窗 | compile-review-queue.js, admin.css, part-05.js | 已完成 |
| C | 热点/抽检/验证术语文案收口 | part-02.js, part-05.js, index.html | 已完成 |
| Test | ManagementJsRuntimeTests 断言更新 | ManagementJsRuntimeTests.java | 已完成 |

---

## 2. A 类：处理历史 Tab 修复

### 2.1 问题
- 切换到"处理历史"Tab 时，如果之前已加载过，不会重新加载数据（`_historyLoaded` 守卫）
- 加载失败时无错误提示和重试机制
- 空态文案不够清晰

### 2.2 修复内容

| 变更 | 说明 |
|------|------|
| 移除 `_historyLoaded` 守卫 | Tab 切换时始终调用 `loadProcessingHistory()` |
| 添加 loading 状态 | `<div class='history-loading'><p class='item-summary'>正在加载处理历史...</p></div>` |
| 添加 error 状态 + 重试 | `<div class='history-error'>... <button id='history-retry-btn'>重试</button></div>` |
| 更新空态文案 | "暂无已结束的处理任务。完成一次编译、入库或失败后会出现在这里。" |
| `renderHistoryList()` 修正 | 先检查 `historyState.loading` 再决定是否渲染空态 |

### 2.3 index.html 帮助文案更新
- 旧文案被替换为："展示已结束的处理任务，用于追溯和审计。点击刷新可获取最新历史。"

---

## 3. B 类：Inline Modal 替换原生弹窗

### 3.1 问题
- 待人工确认草稿的"确认入库"使用 `window.prompt` 获取操作人
- "驳回"使用 `window.confirm` 确认操作
- 错误反馈使用 `window.alert`
- 体验粗糙，无法展示草稿详情

### 3.2 修复内容

**compile-review-queue.js 新增：**
- `openReviewActionModal(action)` — 打开确认/驳回 modal
- `closeReviewActionModal()` — 关闭 modal（ESC 支持）
- `buildReviewActionModalHtml(detail, action)` — 构建 modal HTML
- `bindReviewActionModalEvents(overlay, detail, action)` — 绑定事件
- 确认 modal：标题、草稿信息、问题数、操作人输入、确认按钮
- 驳回 modal：同上 + 驳回原因 textarea（必填）
- 提交 loading 态防止重复点击
- 已移除所有 `window.confirm` / `window.prompt` / `window.alert`

**admin.css 新增样式（~100 行）：**
- `.modal-overlay` — 固定遮罩 + `backdrop-filter: blur()`
- `.modal-card` — 居中卡片 + fadeIn 动画
- `.modal-header` / `.modal-body` / `.modal-footer`
- `.modal-detail-info` / `.modal-hint` / `.modal-field`
- 响应式适配：移动端全宽 + 按钮纵向排列

**测试导出更新：**
- 新增导出：`buildReviewActionModalHtml`, `openReviewActionModal`, `closeReviewActionModal`, `approveSelectedReviewQueueItem`, `rejectSelectedReviewQueueItem`, `submitReviewQueueAction`

---

## 4. C 类：术语文案收口

### 4.1 映射表

| 旧术语（内部） | 新文案（用户向） | 影响范围 |
|---------------|-----------------|---------|
| 热点待抽检 | 热点内容提醒 → 关注内容 | part-02.js |
| 待验证 | 需关注 | part-02.js |
| 刷新热点 | 重新分析关注内容 | index.html, part-02.js |
| 热点未验证 | 高频问题相关 | part-05.js |
| 热点待结果抽检 | 有高频问题待关注 | part-02.js |
| 技术元数据 / 技术信息 | 开发诊断信息 | part-03.js, index.html |

### 4.2 文件变更

| 文件 | 变更 |
|------|------|
| part-02.js | metric label "热点内容提醒"→"关注内容"，help state "待验证或...热点"→"需关注或...高频问题相关"，各种 help/note 文案调整 |
| part-05.js | `getBadgeLabel` HOTSPOT_UNVERIFIED: "热点未验证"→"高频问题相关" |
| index.html | hotspot status pill + button + filter options 文案全部更新 |
| part-03.js | "技术元数据"/"技术信息"→"开发诊断信息"，默认折叠 |
| index.html | "技术元数据"→"开发诊断信息"，"暂无元数据"→"暂无开发诊断信息" |

### 4.3 保留不变
- 后端参数 `requiresResultVerification` 和 `isHotspot` 作为 API filter 值完整保留
- `resolveArticleMetricFilterMessage` 中 `isHotspot` 仍在 `allowedKeys` 中

### 4.4 已知限制
- `management-runtime-part-04.js` 中的 `buildHotspotRefreshStatusText` 函数仍包含"热点未刷新"和"热点刷新中"文案（该文件不在本次允许修改范围）
- 浏览器刷新热点按钮点击后 JS 会覆盖 index.html 中的初始文案，需后续修复 part-04.js

---

## 5. 测试结果

### 5.1 测试命令

```bash
mvn test -pl . \
  -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### 5.2 测试结果

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| ManagementJsRuntimeTests | 3 | 全部通过 |
| AdminProcessingTaskControllerTests | 6 | 全部通过 |

### 5.3 ManagementJsRuntimeTests 断言覆盖

**shouldUseHumanReadableQualityCheckCopyInReviewQueuePlaceholder** — HTML 静态文案检查

**shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime** — compile-review-queue runtime 测试：
- `renderEmptyDetail` / `renderReviewQueueList` / `renderReviewQueueDetail` 不含 Reviewer 文案
- `buildDetailMeta` 使用质量检查文案
- `resolveReviewIssueCount`（2→2, 空→0, null→0）
- `review-issue-list-scroll` class 存在
- 标题含"共 3 个问题"
- **新增**：modal 函数导出（buildReviewActionModalHtml, openReviewActionModal, closeReviewActionModal）
- **新增**：`approveSelectedReviewQueueItem` / `rejectSelectedReviewQueueItem` 源码不含 `window.prompt`/`window.confirm`/`window.alert`
- **新增**：approve modal HTML 含确认文案、草稿标题、modal-operator、确认入库按钮
- **新增**：reject modal HTML 含驳回文案、modal-reject-reason、驳回原因标签、确认驳回按钮

**shouldVerifyRunFallbackAndErrorPresentationViaNode** — management runtime 综合测试：
- `renderMetricCard`：button vs div 渲染、actionHint 可见性、零值卡片
- `renderSummary`：7 张卡片标签覆盖、6 个 `data-metric-action`（pendingQuery 无 action）
- `resolveArticleMetricFilterMessage`：5 种场景含空结果提示
- `handleMetricCardAction`：3 种筛选器 + 状态栏消息
- `pendingQueryCount=27`：无 `data-metric-action`，无"去处理"，无"待开放"
- actionHint 语义：3 张后端闭环卡片使用"去处理 →"，草稿"去确认 →"，反馈"查看反馈 →"，热点"查看内容 →"
- scrollTo：articles 和 feedback tab 的 scrollTo 不抛异常
- **新增**：`_historyLoaded` 不在 `loadProcessingHistory` 源码中
- **新增**：history 空态文案验证
- `normalizeArticleKeywords`：关键词段存在、无"关键词:"前缀、toggle 存在、辅助区含"关联信息"
- "开发诊断信息"替换"技术元数据"验证
- details 默认折叠验证
- 旧热点术语不在源码中（抽检、待验证、刷新热点、待结果抽检）
- 新术语存在（关注内容/需关注/高频问题相关）
- 后端参数 `requiresResultVerification:true` 在渲染输出中保留

---

## 6. 修复过程中遇到的问题

### 6.1 compile-review-queue 断言放错 harness
原将 modal 断言写入 `buildHarnessScript()`（用于 `shouldVerifyRunFallbackAndErrorPresentationViaNode`），但该 harness 不加载 `compile-review-queue.js`。编译队列 modal 断言需放在 `buildCompileReviewQueueHarnessScript()`（用于 `shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime`）中。
**修复**：将 modal 相关断言整体迁移到正确的 harness。

### 6.2 `isHotspot:true` 不在 renderSummary 源码中
断言要求 `renderSummary` 源码含 `isHotspot:true`，但热点卡片实际使用的 filter 值为 `requiresResultVerification:true`。`isHotspot:true` 仅出现在 index.html 的 `<option>` 中，不在 renderSummary 输出中。
**修复**：改为验证渲染输出 contains `requiresResultVerification:true`，移除对 `isHotspot:true` 的错误断言。

### 6.3 part-04.js 热点文案无法修改
`buildHotspotRefreshStatusText` 仍含"热点未刷新"和"热点刷新中"，但 part-04.js 不在本次允许修改范围。
**处置**：记录为已知限制，浏览器刷新热点按钮点击后会覆盖初始文案。

---

## 7. 未覆盖项（需人工浏览器验收）

| # | 验证项 | 状态 |
|---|--------|------|
| 1 | 处理历史 Tab：切换后自动加载，显示 loading/数据/空态/错误+重试 | 未覆盖 |
| 2 | 确认入库 modal：显示草稿信息，填写操作人，点击确认后提交 | 未覆盖 |
| 3 | 驳回 modal：显示草稿信息，必填驳回原因，点击确认驳回后提交 | 未覆盖 |
| 4 | Modal ESC 关闭，点击遮罩关闭 | 未覆盖 |
| 5 | Modal 提交 loading 态防重复点击 | 未覆盖 |
| 6 | "关注内容"卡片显示"查看内容 →"，点击跳转 articles tab | 未覆盖 |
| 7 | 所有术语文案在浏览器实际渲染中与产品语义一致 | 未覆盖 |
| 8 | "开发诊断信息"默认折叠，点击展开 | 未覆盖 |
| 9 | 刷新热点按钮点击后文案（受 part-04.js 限制，仍为旧术语） | 已知限制 |

---

## 8. 结论

三类前端 UX 修复全部完成，9/9 自动化测试通过，BUILD SUCCESS。已知限制为 part-04.js 中热点刷新状态文案未修改（不在允许范围内），浏览器刷新按钮点击后会覆盖初始文案。可进入人工浏览器验收阶段。
