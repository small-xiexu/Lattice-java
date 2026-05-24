# 治理指标卡片"去处理"入口 — 实施报告

## 概述

基于 `admin_governance_metric_action_entry_design_report.md` 设计方案，为后台治理概览的 7 个可操作指标卡片添加"去处理"点击入口。实现方式：纯前端最小改造，复用已有 Tab 和筛选机制，不新增后端接口。

## 改动文件清单

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `src/main/resources/static/admin/modules/management-runtime-part-05.js` | 修改 | `renderMetricCard()` 支持 `action`/`actionHint`，导出 `renderMetricCard`/`handleMetricCardAction` |
| `src/main/resources/static/admin/modules/management-runtime-part-02.js` | 修改 | `renderSummary()` 中 7 个卡片增加 `action`/`actionHint`；新增 `handleMetricCardAction()` |
| `src/main/resources/static/admin/modules/management-runtime-part-01.js` | 修改 | `bindEvents()` 中 `#summary-cards` 添加事件委托 |
| `src/main/resources/static/admin/admin.css` | 修改 | 新增 `.metric-card.clickable` 悬停/聚焦样式和 `.action-hint` 样式 |
| `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java` | 修改 | 新增 8 项指标卡片运行时断言 |

## 7 个可点击指标及其跳转目标

| 指标卡片 | 目标 Tab | 筛选/定位 |
|---------|---------|----------|
| 待人工确认草稿 | knowledge-runs | 滚动到 `#review-queue-list` |
| 答案反馈待处理 | knowledge-feedback | `query-feedback-status-filter` = PENDING |
| 待分析提问 | knowledge-feedback | `query-feedback-status-filter` = PENDING |
| 已入库待复核 | knowledge-articles | `article-review-status` = pending |
| 高风险内容 | knowledge-articles | `article-risk-filter` = riskLevel:high |
| 热点待抽检 | knowledge-articles | `article-risk-filter` = requiresResultVerification:true |
| 用户反馈风险 | knowledge-articles | `article-risk-filter` = riskReason:user_reported |

## `handleMetricCardAction()` 交互流程

1. 解析 `data-metric-action` 中的 JSON 配置
2. 若 `config.filters` 存在，将 filter 值写入对应 DOM 元素（`article-review-status`、`article-risk-filter`、`query-feedback-status-filter`）
3. 调用 `activateKnowledgeTab()` 激活目标 Tab
4. 根据 Tab 类型触发数据加载：
   - `knowledge-articles` → 模拟点击 `#search-articles` 按钮
   - `knowledge-feedback` → 模拟点击 `#refresh-query-feedback` 按钮
   - `knowledge-runs` + `scrollTo` → 150ms 后平滑滚动到目标元素

## 非点击指标

以下 4 个指标仅展示数据，无点击行为：知识条目、资料文件、资料源、已确认修正。

## 测试验证

- `ManagementJsRuntimeTests` (3 个测试) — 全部通过
  - `renderMetricCard` 有/无 action 时的输出验证
  - `actionHint` 渲染验证
  - `renderSummary` 输出中 `data-metric-action` 数量精确为 7
  - 7 个可点击标签均存在于 summary 输出中
  - `handleMetricCardAction` 正确设置 filter 值
  - `handleMetricCardAction` 对无效 JSON 和空配置的容错
- `AdminProcessingTaskControllerTests` (6 个测试) — 全部通过，无回归

## 样式要点

- 可点击卡片：`cursor: pointer`，hover/focus 时上移 2px + 边框发光
- `action-hint` 文字使用 `--primary` 色，hover 时加深
- 过渡动画 180ms ease
