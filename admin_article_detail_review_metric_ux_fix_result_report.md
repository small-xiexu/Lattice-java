# 文章详情 / 治理指标 / 关键词降噪 UX 修复结果报告

- 生成时间：2026-05-22
- 分支：`codex/qa-polish`
- 修复范围：4 个前端 UX 问题

---

## 修复内容

### 1. Review Issue 列表内部滚动（CSS-only）

**问题**：已入库内容详情中 review issue 列表过长，撑开页面。

**修复**：在 `admin.css` 中新增：
- `.review-issue-list-scroll` — `max-height: min(60vh, 640px)` + `overflow-y: auto` + `overscroll-behavior: contain`
- 移动端 `@media (max-width: 768px)` — `max-height: 55vh`
- `.issue-description` — `max-height: 240px` + `overflow-y: auto`

**文件**：`admin.css`（只读验证报告中已确认）

### 2. 关键词展示降噪

**问题**：关键词标签冗余（每个标签重复"关键词："前缀），技术/调试型关键词铺满界面。

**修复**：
- `part-05.js` 新增 `isTechKeyword(keyword)` — 检测文件扩展名、路径分隔符、snake_case、dot-separated config key、URL、key=value 等
- `part-05.js` 新增 `normalizeArticleKeywords()` — 限制可见为 5 个普通关键词，将技术关键词 + 超出关键词移入 `<details>` 折叠区
- `part-02.js` 中 `loadArticleDetail()` 在 `renderArticleDetail()` 后调用 `normalizeArticleKeywords()`

**关键词分类规则**：

| 类型 | 示例 | 判定为 tech |
|------|------|-----------|
| 文件路径 | `docs/readme.md` | 是 |
| 配置键 | `app.config.key` | 是 |
| snake_case | `my_variable_name` | 是 |
| URL | `https://example.com` | 是 |
| key=value | `foo=bar` | 是 |
| 中文文本 | `机器学习` | 否 |
| 简单单词 | `payment` | 否 |
| kebab-case | `order-processing` | 否 |

**文件**：`management-runtime-part-02.js`、`management-runtime-part-05.js`

### 3. Review History 空状态样式

**问题**：深灰色背景导致文字不可读。

**修复**：`admin.css` 中 `.review-history-empty` 改为浅色背景：
- `background: rgba(255, 252, 247, 0.94)`
- `color: var(--text)`
- `.review-history-empty .item-summary { color: var(--muted-strong) }`

**文件**：`admin.css`（只读验证报告中已确认）

### 4. 治理指标"待分析提问" + 点击反馈

**问题**：
- "待分析提问" 错误链接到 `query-feedback` PENDING，但数据源是 `/api/v1/admin/pending`，无对应前端处理器
- 所有指标卡片点击后无 `setStatus` 反馈

**修复**：
- `part-02.js` — "待分析提问" 的 `action` 和 `actionHint` 设为 `undefined`，`note` 改为"后台记录的待分析提问，处理入口待开放"
- `part-02.js` — `handleMetricCardAction()` 中为 `knowledge-articles`、`knowledge-feedback`、`knowledge-runs` 三个 Tab 均添加 `setStatus()` 调用

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

### 新增断言覆盖

| 断言类别 | 数量 | 说明 |
|---------|------|------|
| `isTechKeyword` 正向检测 | 6 | 文件扩展名、dotted key、snake_case、路径、key=value、URL |
| `isTechKeyword` 负向检测 | 4 | 中文文本、简单单词、kebab-case、空字符串 |
| `normalizeArticleKeywords` 导出 | 1 | 函数存在性 |
| `handleMetricCardAction` setStatus | 4 | articles tab 和 feedback tab 均触发 page-notice |

---

## 改动文件总览

| 文件 | 改动 | 说明 |
|------|------|------|
| `management-runtime-part-02.js` | 修改 | `handleMetricCardAction` 添加 setStatus 反馈；待分析提问移除 action |
| `management-runtime-part-05.js` | 新增 | `isTechKeyword()` + `normalizeArticleKeywords()` 函数及测试导出 |
| `admin.css` | 修改 | review issue 滚动、review history 空状态、关键词区块样式（前轮已修） |
| `ManagementJsRuntimeTests.java` | 新增 | isTechKeyword 10 项断言 + normalizeArticleKeywords 导出断言 + setStatus 4 项断言 |

---

## 剩余需人工浏览器验收项

1. 关键词降噪：验证有 >5 个普通关键词 + 技术关键词的文章详情展示
2. 治理指标点击反馈：点击各指标卡片，确认状态栏出现中文提示
3. Review issue 列表滚动：验证长列表有内部滚动条
4. Review history 空状态：验证空态卡片文字可读
5. "待分析提问" 卡片无"去处理 →"且不可点击
