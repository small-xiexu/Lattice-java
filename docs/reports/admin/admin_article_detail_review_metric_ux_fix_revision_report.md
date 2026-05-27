# 文章详情 / 治理指标 / 关键词降噪 UX 修复修正报告

- 生成时间：2026-05-22
- 分支：`codex/qa-polish`
- 修正轮次：第 2 轮（修正第 1 轮遗留的 4 个 UX 漏点）

---

## 修正总览

第 1 轮修复报告 (`admin_article_detail_review_metric_ux_fix_result_report.md`) 遗留以下漏点：

| # | 问题 | 根因 | 本轮修正 |
|---|------|------|----------|
| 1 | Review issue 列表未真正可滚动 | CSS 写了但 DOM 没加 class | JS 添加 `review-issue-list-scroll` class + 标题显示数量 |
| 2 | 关键词从 DOM 扫描生成 | 实现方式错误 | 从原始字段 `detail.referentialKeywords/dependsOn/related` 生成 |
| 3 | 复核历史空状态不可读 | 已 CSS 修复 | 确认无需额外修改 |
| 4 | 治理指标卡片缺少点击反馈 | setStatus 调用不完整 | 补全 `setStatus()` + `resolveArticleMetricFilterMessage()` |

---

## 修复内容详情

### 1. Review Issue 列表滚动 + 问题计数（compile-review-queue.js）

**问题**：第 1 轮只在 CSS 添加了 `.review-issue-list-scroll` 样式规则，但 `compile-review-queue.js` 渲染的 DOM 没有挂载这个 class，导致列表不滚动。标题也只写死"待人工确认说明"，没有显示问题数量。

**修复**：
- 新增 `resolveReviewIssueCount(reviewIssuesJson)` —— 解析 `reviewIssuesJson` 并返回数组长度
- 标题改为动态：`待人工确认说明（共 N 个问题）`
- 列表 div 挂载 `review-issue-list-scroll` class
- 函数导出到 `__LATTICE_ADMIN_TEST__.compileReviewQueue`

**文件**：`compile-review-queue.js`

### 2. 文章详情关键词从原始字段生成

**问题**：第 1 轮 `normalizeArticleKeywords()` 扫描 DOM 中 `.pill` 元素来提取关键词，这依赖页面已渲染的 HTML，时机脆弱，且无法区分"关键词"前缀和纯文本。

**修复**：

**part-03.js**（`loadArticleDetail()` 渲染处）：
- 不再构建带"关键词:"前缀的 `<span class='pill'>` HTML
- 改为将原始数据存入 `state._articleKeywordData = { keywords, dependsOn, related }` 
- `#article-relations` 容器初始置空，等待 `normalizeArticleKeywords()` 填充

**part-05.js**（`normalizeArticleKeywords()` 重写）：
- 从 `state._articleKeywordData` 读取原始数组，而非扫描 DOM
- 合并 keywords + dependsOn + related，去重
- 通过 `isTechKeyword()` 分类：普通关键词 vs 技术/调试关键词
- 可见区最多 6 个普通关键词（无"关键词:"前缀）
- 超出的普通关键词 + 所有技术关键词放入 `<details class='article-keyword-toggle'>` 折叠区
- dependsOn/related 以"关联信息"辅助区展示（低调样式：小字号、muted 颜色）
- 全部为空时显示"暂无"

**文件**：`management-runtime-part-03.js`、`management-runtime-part-05.js`

### 3. 复核历史空状态样式

**确认**：第 1 轮 CSS 已将 `.review-history-empty` 改为浅色背景 `rgba(255, 252, 247, 0.94)`，文字颜色改为 `var(--text)`。本轮无需额外修改。

**文件**：`admin.css`（第 1 轮已完成）

### 4. 治理指标卡片点击反馈

**问题**：第 1 轮 `handleMetricCardAction()` 已实现切换 tab + 设置筛选，但 `setStatus()` 调用不完整，部分 Tab 缺少状态提示。筛选消息也较模糊。

**修复**：

**part-02.js**（`handleMetricCardAction()` 重写 + 新增 `resolveArticleMetricFilterMessage()`）：
- 新增 `resolveArticleMetricFilterMessage(filters)` —— 根据筛选条件返回具体中文提示：
  - `riskLevel:high` → "已切换到已入库内容，并筛选高风险内容"
  - `requiresResultVerification:true` → "已切换到已入库内容，并筛选热点待抽检"
  - `riskReason:user_reported` → "已切换到已入库内容，并筛选用户反馈风险"
  - `article-review-status` → "已切换到已入库内容，并应用复核状态筛选"
  - 默认/null → "已切换到已入库内容"
  - 所有消息均追加 "如列表为空则当前没有匹配记录。"
- `knowledge-runs` tab → "已切换到当前处理任务，并定位到待人工确认草稿列表"
- `knowledge-feedback` tab → "已切换到结果反馈，并应用筛选条件。如列表为空则当前没有匹配记录。"
- 所有三个 Tab（articles/feedback/runs）均正确调用 `setStatus()`

**文件**：`management-runtime-part-02.js`

---

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

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| `ManagementJsRuntimeTests` | 3 | 全部通过 |
| `AdminProcessingTaskControllerTests` | 6 | 全部通过 |

### ManagementJsRuntimeTests 断言覆盖

| 测试方法 | 覆盖内容 |
|----------|----------|
| `shouldUseHumanReadableQualityCheckCopyInReviewQueuePlaceholder` | index.html 不含 Reviewer 文案 |
| `shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime` | `resolveReviewIssueCount`（2→2, 空→0, null→0）、`review-issue-list-scroll` class 存在、标题含"共 3 个问题" |
| `shouldVerifyRunFallbackAndErrorPresentationViaNode` | `resolveArticleMetricFilterMessage` 5 种场景（含空结果提示）、`handleMetricCardAction` 3 种筛选器、`pendingQueryCount=27` 无 `data-metric-action`、`normalizeArticleKeywords` 行为（关键词段存在、无"关键词:"前缀、toggle 存在、辅助区含"关联信息"） |

---

## 改动文件总览

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `compile-review-queue.js` | 修改 | 添加 `resolveReviewIssueCount()`、动态标题计数、DOM class |
| `management-runtime-part-03.js` | 修改 | 移除 DOM 关键词 HTML，改为存储原始数据到 state |
| `management-runtime-part-05.js` | 重写 | `normalizeArticleKeywords()` 从 state 读取、最多 6 个可见、tech 关键词折叠、"关联信息"辅助区、修复 JSON 转义换行 |
| `management-runtime-part-02.js` | 修改 | `handleMetricCardAction()` 补全 setStatus、新增 `resolveArticleMetricFilterMessage()` |
| `admin.css` | 修改 | `.metric-card` button 样式、`.clickable` hover/focus 动效、`.action-hint` 颜色、`.article-relations-aux` 辅助区样式 |
| `ManagementJsRuntimeTests.java` | 新增 | `resolveReviewIssueCount` 测试、`review-issue-list-scroll` class 测试、标题计数测试、`resolveArticleMetricFilterMessage` 5 场景测试、`handleMetricCardAction` 筛选器测试、`pendingQueryCount` 无 action 测试、`normalizeArticleKeywords` 行为测试 |

---

## 修复过程中发现并修正的额外问题

1. **part-05.js JSON 转义换行符**：`handleMetricCardAction` 导出行存在原始 LF 字符（`0x0A`），导致 Node.js `JSON.parse()` 报 `Bad control character in string literal`。已替换为 `\n` 转义序列。此问题仅影响测试（测试依赖 `JSON.parse` 解析 ES module 字符串），不影响浏览器运行时（浏览器通过 `import` 原生加载 ES module）。

---

## 剩余需人工浏览器验收项

1. 关键词降噪：验证有 >6 个普通关键词 + 技术关键词的文章详情页，可见区只显示 6 个，其余折叠在"还有 N 个关键词（含技术/调试）"中
2. 关联信息：验证 dependsOn/related 以低调度辅助文字展示
3. 治理指标点击反馈：点击各指标卡片，确认状态栏出现对应的中文提示
4. Review issue 列表滚动：验证长列表有内部滚动条，标题显示正确问题数
5. Review history 空状态：确认浅色背景文字可读（第 1 轮 CSS 已修）
6. "待分析提问"卡片：确认无"去处理 →"且不可点击（即使 count > 0）
