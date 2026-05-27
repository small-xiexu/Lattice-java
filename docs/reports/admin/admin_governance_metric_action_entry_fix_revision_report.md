# 治理指标卡片"去处理"入口 — 修订报告

## 修订原因

基于 `admin_governance_metric_action_entry_fix_result_report.md` 的初版实现，发现两个前端细节问题：

1. **value=0 仍可点击**：治理指标卡片即使待处理数为 0 也会渲染 `data-metric-action` 和"去处理 →"提示，误导用户点击后跳转到空列表。
2. **键盘不可达**：可点击卡片使用 `<div>` 承载点击行为，无法通过 Tab 键聚焦，违反无障碍访问标准。

## 修订内容

### 1. `management-runtime-part-02.js` — renderSummary 条件传 action

7 个可点击卡片的 `action` 和 `actionHint` 改为条件赋值：

```javascript
// 修改前（始终传 action）
action: '{"tab":"knowledge-runs","scrollTo":"review-queue-list"}',
actionHint: "去处理 →"

// 修改后（仅 count > 0 时传 action）
action: humanReviewDraftPendingCount > 0
    ? '{"tab":"knowledge-runs","scrollTo":"review-queue-list"}'
    : undefined,
actionHint: humanReviewDraftPendingCount > 0 ? "去处理 →" : undefined
```

各卡片绑定的 count 变量：

| 卡片 | 条件变量 |
|------|---------|
| 待人工确认草稿 | `humanReviewDraftPendingCount > 0` |
| 答案反馈待处理 | `answerFeedbackPendingCount > 0` |
| 待分析提问 | `pendingQueryCount > 0` |
| 已入库待复核 | `manualReviewCount > 0` |
| 高风险内容 | `highRiskCount > 0` |
| 热点待抽检 | `hotspotPendingCount > 0` |
| 用户反馈风险 | `userReportedCount > 0` |

### 2. `management-runtime-part-05.js` — renderMetricCard button/div 分流

```javascript
// 核心变更
const hasAction = !!item.action;

// 有 action → 渲染 <button type="button">
if (hasAction) {
    return "<button type='button' class='metric-card" + toneClass + clickableClass + "'"
            + actionAttr + ">" + inner + "</button>";
}
// 无 action → 渲染 <div>
return "<div class='metric-card" + toneClass + clickableClass + "'"
        + actionAttr + ">" + inner + "</div>";
```

关键点：
- 使用 `!!item.action` 判断，`undefined` 和空字符串均视为无 action
- `<button type="button">` 避免在表单内误触提交
- 保留 `metric-card`、`clickable`、`tone` 等 class，视觉完全一致
- 无 action 的卡片仍为 `<div>`，不可聚焦

### 3. `admin.css` — button.metric-card reset

在 `.metric-card.clickable` 之前新增：

```css
button.metric-card {
    font: inherit;
    text-align: left;
    width: 100%;
    appearance: none;
    -webkit-appearance: none;
}
```

保证 `<button>` 渲染效果与 `<div>` 完全一致，不引入浏览器默认按钮样式。

### 4. `management-runtime-part-01.js` — 事件委托无需修改

现有委托代码 `event.target.closest("[data-metric-action]")` 对 `<button>` 和 `<div>` 均生效，无需改动。

## 测试补充

### `ManagementJsRuntimeTests.java` 变更

| 新增/修改 | 说明 |
|----------|------|
| 新增断言 | `renderMetricCard` 有 action → 输出包含 `<button type='button'` |
| 新增断言 | `renderMetricCard` 无 action → 输出包含 `<div`、不含 `<button` |
| 新增断言 | `renderMetricCard` value=0 且 action=undefined → 不含 button/data-metric-action/clickable/action-hint |
| 修改断言 | summary 中 `data-metric-action` 从 7 个改为 6 个（因 mock 数据 `pendingQueryCount=0`，待分析提问不计入） |
| 重命名 | `clickableLabels` → `expectedLabels`，语义更准确 |

## 测试结果

```bash
mvn test -pl . \
  -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests`: 3/3 通过
- `AdminProcessingTaskControllerTests`: 6/6 通过

## 改动文件总览

| 文件 | 改动 | 行数 |
|------|------|------|
| `management-runtime-part-02.js` | action/actionHint 改为条件赋值（7 处） | ~14 行 |
| `management-runtime-part-05.js` | renderMetricCard 分解为 button/div 两条路径 | ~12 行 |
| `admin.css` | 新增 `button.metric-card` reset 规则 | +6 行 |
| `ManagementJsRuntimeTests.java` | 新增 9 项断言、修改 2 项断言 | ~25 行 |

## 剩余需 agentD runtime 验证项

1. **浏览器 Tab 键导航**：验证可点击卡片可通过 Tab 键聚焦，聚焦态有 `focus-visible` 样式。
2. **value=0 时不可点击**：构造各指标均为 0 的场景，确认无"去处理 →"显示、无 clickable 样式。
3. **value>0 时点击跳转**：验证 7 个指标在 count>0 时均正确跳转到对应 Tab 并应用筛选。
4. **CSS 视觉回归**：button 卡片与 div 卡片在 hover/focus/普通态下视觉完全一致（padding、border、圆角、渐变背景）。
5. **移动端触摸**：确认 button 卡片在移动端点击区域正确，无意外缩放或双击行为。
