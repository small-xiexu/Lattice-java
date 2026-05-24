# Admin 治理指标处理入口设计方案

- 生成时间：2026-05-22
- 执行 Agent：agentB（只读设计）
- 分支：`codex/qa-polish`
- 本轮是否修改代码：**否**

---

## 结论先行

1. **所有治理指标均来自 `GET /api/v1/admin/overview` → `StatusSnapshot`**，已有完整后端数据。
2. **所有指标的目标页面均已存在**：`已入库内容` Tab 已支持 `riskLevel`/`reviewStatus`/`isHotspot`/`requiresResultVerification`/`riskReason` 筛选；`结果反馈` Tab 已支持 `status=PENDING` 筛选。**零后端改动。**
3. **最小方案**：仅改前端，将治理指标卡片从 `<div>` 改为可点击的 `<button>`，点击后跳转到对应 Tab 并预填筛选条件。约 40 行 JS + 10 行 CSS。

---

## 1. 指标来源映射表

所有指标由 `StatusService.snapshot()` 计算，通过 `GET /api/v1/admin/overview` 返回。

### 1.1 一级指标（始终可见，4 张卡片）

| 卡片标签 | API 字段 | 数据来源 | 0 值行为 |
|---------|---------|---------|---------|
| 待人工确认草稿 | `status.humanReviewDraftPendingCount` | `compile_article_review_queue` 表 `needs_human_review` 计数 | 隐藏或灰显 |
| 知识条目 | `status.articleCount` | `articles` 表 COUNT | 始终展示 |
| 资料文件 | `status.sourceFileCount` | 资料源文件计数 | 始终展示 |
| 答案反馈待处理 | `status.answerFeedbackPendingCount` | `answer_feedback` 表 `PENDING` 计数 | >0 时才可点击 |

### 1.2 二级指标（折叠面板内，7 张卡片）

| 卡片标签 | API 字段 | 数据来源 | 0 值行为 |
|---------|---------|---------|---------|
| 资料源 | `state.sources.length` | 前端 sources 数组 | 始终展示 |
| 已确认修正 | `status.contributionCount` | `contributions` 表计数 | 始终展示 |
| 待分析提问 | `status.pendingQueryCount` | pending queries 计数 | >0 时才可点击 |
| 已入库待复核 | `status.reviewPendingArticleCount` | articles 表 `review_status != "passed"` | >0 时才可点击 |
| 高风险内容 | `status.highRiskArticleCount` | articles 表 `risk_level = "high"` | >0 时才可点击 |
| 热点待抽检 | `status.hotspotPendingVerificationCount` | articles 表 `is_hotspot = true AND requires_result_verification = true` | >0 时才可点击 |
| 用户反馈风险 | `status.userReportedAnswerCount` | articles 表 `risk_reasons` 包含 `"user_reported"` | >0 时才可点击 |

### 1.3 当前渲染位置

`management-runtime-part-01.js` 的 `renderSummary()` 函数：
- 一级卡片 → `.summary-primary-grid`（`#summary-cards > .summary-card-stack > .summary-primary-grid`）
- 二级卡片 → `.summary-secondary-grid`（`#summary-cards > .summary-card-stack > details.summary-secondary-panel > .summary-secondary-grid`）

### 1.4 当前卡片渲染函数

`management-runtime-part-05.js` 的 `renderMetricCard()`：
```javascript
function renderMetricCard(item) {
    return "<div class='metric-card" + toneClass + "'>"
            + "<span class='label'>" + escapeHtml(label) + "</span>"
            + "<span class='value'>" + escapeHtml(String(item.value)) + "</span>"
            + note
            + "</div>";
}
```

卡片是 `<div>`，无任何点击行为。`item` 对象形状：`{label, value, note, tone}`，**无跳转目标字段**。

---

## 2. 推荐跳转/处理路径表

### 2.1 可点击指标（有明确承接页面）

| 指标 | 跳转目标 Tab | 预填筛选 | 说明 |
|------|------------|---------|------|
| 待人工确认草稿 | `knowledge-runs` | 滚动到审查队列区域 | 审查队列已在当前页，无需跳 Tab |
| 答案反馈待处理 | `knowledge-feedback` | `status=PENDING` | 已有筛选，默认即 PENDING |
| 待分析提问 | `knowledge-feedback` | `status=PENDING` | 与答案反馈共用结果反馈页 |
| 已入库待复核 | `knowledge-articles` | `reviewStatus=pending` | 已入库内容 Tab 已有此筛选 |
| 高风险内容 | `knowledge-articles` | `riskLevel=high` | 已入库内容 Tab 已有此筛选 |
| 热点待抽检 | `knowledge-articles` | `requiresResultVerification=true` | 已入库内容 Tab 已有此筛选 |
| 用户反馈风险 | `knowledge-articles` | `riskReason=user_reported` | 已入库内容 Tab 已有此筛选 |

### 2.2 不可点击指标（信息展示型）

| 指标 | 原因 |
|------|------|
| 知识条目 | 纯统计数字，无独立处理入口 |
| 资料文件 | 纯统计数字，无独立处理入口 |
| 资料源 | 已有资料源列表可交互 |
| 已确认修正 | contribution 暂无独立管理页面（后续增强） |

### 2.3 跳转机制

利用现有 `activateKnowledgeTab()` 函数（`management-runtime-part-01.js`）：

```javascript
// 当前已有的跳转方法：
function activateKnowledgeTab(tabName, options) {
    window.AdminTabs.activate("knowledge-console", tabName, options);
}
```

`options` 可传递筛选参数，目标 Tab 的渲染函数通过读取 `state` 中的 filter 值来响应。

具体方式：跳转前设置 `state` 中的 filter 字段，然后调 `activateKnowledgeTab`，目标 Tab 激活后自动按 `state` 中的值发起 API 请求。

---

## 3. 每个指标的处理路径详情

### 3.1 待分析提问（`pendingQueryCount`）

**跳转目标**：`结果反馈` Tab（`knowledge-feedback`）

**当前状态**：`结果反馈` 页已有 `status=PENDING` 筛选，默认即为 PENDING。该 Tab 已支持完整的列表 + 处理操作。

**跳转后展示**：
| 字段 | 说明 |
|------|------|
| 问题内容 | `question` |
| 最近提问时间 | `createdAt` |
| 当前反馈类型 | `feedbackType` |
| 状态 | PENDING / RESOLVED / DISMISSED |
| 操作 | 标记已处理 / 忽略（已有） |

**本轮是否新增操作**：**否。** `结果反馈` 页已有的 `resolve`/`dismiss` 操作够用。"转为知识补充""转为回答改进"等高级操作留到后续增强。

**实现方式**：
```
点击"待分析提问"卡片 → 设置 state.queryFeedbackStatusFilter = "PENDING"
→ activateKnowledgeTab("knowledge-feedback") → 结果反馈页自动加载 PENDING 列表
```

### 3.2 热点待抽检（`hotspotPendingVerificationCount`）

**跳转目标**：`已入库内容` Tab（`knowledge-articles`）

**当前状态**：该 Tab 已有 `requiresResultVerification=true` 筛选（前端 select 值为 `requiresResultVerification:true`）。

**跳转后展示**：
- 复用现有文章列表渲染（`renderArticleList`）
- 每条显示：标题、来源、reviewStatus、riskLevel、热点标记、操作按钮

**本轮是否新增独立"热点抽检"列表**：**否。** 已入库内容 Tab 的文章列表 + 筛选已覆盖。独立抽检列表涉及新的 API 和交互设计，后续增强。

**实现方式**：
```
点击"热点待抽检"卡片 → 设置 state.articleRiskFilter = "requiresResultVerification:true"
→ activateKnowledgeTab("knowledge-articles") → 已入库内容页自动加载筛选结果
```

### 3.3 高风险内容（`highRiskArticleCount`）

**跳转目标**：`已入库内容` Tab（`knowledge-articles`）

**筛选**：`riskLevel=high`

**实现方式**：
```
点击"高风险内容"卡片 → 设置 state.articleRiskFilter = "riskLevel:high"
→ activateKnowledgeTab("knowledge-articles")
```

### 3.4 用户反馈风险（`userReportedAnswerCount`）

**跳转目标**：`已入库内容` Tab（`knowledge-articles`）

**筛选**：`riskReason=user_reported`

**实现方式**：
```
点击"用户反馈风险"卡片 → 设置 state.articleRiskFilter = "riskReason:user_reported"
→ activateKnowledgeTab("knowledge-articles")
```

### 3.5 已入库待复核（`reviewPendingArticleCount`）

**跳转目标**：`已入库内容` Tab（`knowledge-articles`）

**筛选**：`reviewStatus=pending`

**实现方式**：
```
点击"已入库待复核"卡片 → 设置 state.articleReviewStatusFilter = "pending"
→ activateKnowledgeTab("knowledge-articles")
```

### 3.6 待人工确认草稿（`humanReviewDraftPendingCount`）

**跳转目标**：`当前处理任务` Tab（`knowledge-runs`）

**滚动行为**：跳转后自动滚动到审查队列区域（`#review-queue-list`），该区域已在 `knowledge-runs` 面板内。

**实现方式**：
```
点击"待人工确认草稿"卡片 → activateKnowledgeTab("knowledge-runs")
→ 滚动到 document.getElementById("review-queue-list")
```

### 3.7 答案反馈待处理（`answerFeedbackPendingCount`）

**跳转目标**：`结果反馈` Tab（`knowledge-feedback`）

**与"待分析提问"共用同一目标页**。结果反馈页展示两条数据源：pending queries + answer feedback，通过 status 筛选区分。"待分析提问"和"答案反馈待处理"点击后进入同一 Tab，用户可在筛选栏中切换。

**实现方式**：
```
点击"答案反馈待处理"卡片 → 设置 state.queryFeedbackStatusFilter = "PENDING"
→ activateKnowledgeTab("knowledge-feedback")
```

---

## 4. 指标卡 UI 改造设计

### 4.1 可点击卡片样式

```
┌─────────────────────────┐
│ 高风险内容        🔗    │  ← hover 时出现箭头图标
│     3                  │  ← 数值
│ 去处理 →               │  ← 底部操作提示（仅 >0 时显示）
└─────────────────────────┘
```

### 4.2 状态区分

| 条件 | 样式 | 交互 |
|------|------|------|
| 值 > 0 + 有跳转目标 | 正常色 + hover 效果 + cursor:pointer | **可点击** |
| 值 > 0 + 有 warning tone | warning/danger 色 + hover 效果 | **可点击** |
| 值 = 0 + 有跳转目标 | 灰显（opacity: 0.6）+ cursor:default | **不可点击** |
| 无跳转目标（如"知识条目"） | 正常色 + cursor:default | **不可点击** |

### 4.3 `renderMetricCard()` 改造

`item` 对象新增可选字段 `action`：

```javascript
// item 对象扩展示例：
{
    label: "高风险内容",
    value: 3,
    tone: "danger",
    action: {
        tab: "knowledge-articles",
        filter: { riskFilter: "riskLevel:high" }
    }
}
```

渲染逻辑：
```javascript
function renderMetricCard(item) {
    const isClickable = item.action && Number(item.value) > 0;
    const tag = isClickable ? "button" : "div";
    const typeAttr = isClickable ? " type='button'" : "";
    const clickAttr = isClickable
        ? " data-metric-action='" + JSON.stringify(item.action) + "'"
        : "";
    const actionHint = isClickable
        ? "<span class='metric-action-hint'>去处理 →</span>"
        : "";

    return "<" + tag + " class='metric-card" + toneClass + "'"
            + typeAttr + clickAttr + ">"
            + "<span class='label'>" + escapeHtml(label) + "</span>"
            + "<span class='value'>" + escapeHtml(String(item.value)) + "</span>"
            + (item.note ? "<span class='note'>" + escapeHtml(compactMetricNote(item.note)) + "</span>" : "")
            + actionHint
            + "</" + tag + ">";
}
```

### 4.4 事件委托

在 `#summary-cards` 容器上挂事件委托（`management-runtime-part-01.js` 的初始化中）：

```javascript
document.getElementById("summary-cards").addEventListener("click", function (event) {
    const card = event.target.closest("[data-metric-action]");
    if (!card) return;

    const action = JSON.parse(card.dataset.metricAction);
    handleMetricCardAction(action);
});

function handleMetricCardAction(action) {
    // 设置 state 中的筛选值
    if (action.filter) {
        if (action.filter.riskFilter) {
            state.articleRiskFilter = action.filter.riskFilter;
        }
        if (action.filter.reviewStatus) {
            state.articleReviewStatusFilter = action.filter.reviewStatus;
        }
        if (action.filter.feedbackStatus) {
            state.queryFeedbackStatusFilter = action.filter.feedbackStatus;
        }
    }
    // 跳转到目标 Tab
    activateKnowledgeTab(action.tab);
    // 如果是 knowledge-runs，滚动到审查队列
    if (action.scrollTo) {
        setTimeout(function () {
            const el = document.querySelector(action.scrollTo);
            if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
        }, 200);
    }
}
```

### 4.5 目标 Tab 响应筛选参数

**已入库内容 Tab**（`knowledge-articles`）：
- `loadArticles()` 函数已从 `state` 读取 `articleQuery`、`articleLifecycle`、`articleSourceId`、`articleReviewStatus`、`articleRiskFilter`
- 跳转前设置 `state.articleRiskFilter`，`loadArticles()` 会自然读取并应用

**结果反馈 Tab**（`knowledge-feedback`）：
- `loadQueryFeedback()` 函数读取 `#query-feedback-status-filter` 的当前值
- 需要在跳转前同步设置该 `<select>` 的值，或让 `loadQueryFeedback()` 也支持从 `state` 读取

**优化**：让 `loadQueryFeedback()` 优先读取 `state.queryFeedbackStatusFilter`（如果设置），再 fallback 到 DOM select 的值。这样跳转时只需设置 state 即可。

---

## 5. CSS 改动

### 5.1 可点击卡片样式

```css
/* 可点击的指标卡片 */
.metric-card[data-metric-action] {
    cursor: pointer;
    transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.metric-card[data-metric-action]:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
    transform: translateY(-1px);
}

.metric-card[data-metric-action]:active {
    transform: translateY(0);
}

/* 值为 0 时禁用点击 */
.metric-card[data-metric-action][data-metric-zero="true"] {
    cursor: default;
    opacity: 0.6;
}

.metric-card[data-metric-action][data-metric-zero="true"]:hover {
    box-shadow: none;
    transform: none;
}

/* 底部"去处理"提示 */
.metric-action-hint {
    display: block;
    font-size: 0.75rem;
    color: var(--color-primary, #4a90d9);
    margin-top: 4px;
    opacity: 0;
    transition: opacity 0.15s ease;
}

.metric-card[data-metric-action]:hover .metric-action-hint {
    opacity: 1;
}
```

---

## 6. 最小实施方案

### 6.1 前端改动清单

| 文件 | 改动 | 行数 |
|------|------|------|
| `management-runtime-part-01.js` | `renderSummary()` 中为可点击卡片增加 `action` 字段；新增 `handleMetricCardAction()` 函数；`#summary-cards` 事件委托绑定 | ~35 行 |
| `management-runtime-part-05.js` | `renderMetricCard()` 增加 `data-metric-action` 渲染 + `actionHint` | ~10 行 |
| `admin.css` | 新增可点击卡片 hover/focus/disabled 样式 | ~15 行 |
| **总计** | **3 个文件** | **~60 行** |

### 6.2 允许修改的文件

- `management-runtime-part-01.js`（`renderSummary()` + 事件委托）
- `management-runtime-part-05.js`（`renderMetricCard()`）
- `admin.css`（指标卡片交互样式）

### 6.3 禁止修改的文件

- 所有 `src/main/java/**`：零后端改动
- `admin/index.html`：零 HTML 结构改动
- `admin-tabs.js`：不改 Tab 框架
- `management-runtime-part-02.js` / `part-03.js` / `part-04.js`：不改文章列表/反馈列表渲染
- `management-history-part.js`：不改历史 Tab
- `compile-review-queue.js`：不改审查队列
- 所有 API Controller / Service / DTO

### 6.4 是否需要后端改动

**不需要。** 所有跳转目标的 API 筛选参数已就绪：

| 跳转目标 | API | 现有筛选参数 |
|---------|-----|------------|
| 已入库内容 | `/api/v1/admin/articles` | `riskLevel`, `reviewStatus`, `isHotspot`, `requiresResultVerification`, `riskReason` |
| 结果反馈 | `/api/v1/admin/query-feedback` | `status=PENDING` |
| 当前处理任务 | 无 API 调用 | 页面内滚动到 `#review-queue-list` |

### 6.5 是否需要测试

**建议人工验收以下场景**（无需自动化测试）：

1. 点击"高风险内容"卡片（值>0）→ 确认跳转到已入库内容 Tab，risk filter 自动选中"高风险内容"
2. 点击"热点待抽检"卡片（值>0）→ 确认跳转到已入库内容 Tab，risk filter 自动选中"待结果抽检"
3. 点击"用户反馈风险"卡片（值>0）→ 确认跳转到已入库内容 Tab，risk filter 自动选中"用户反馈"
4. 点击"已入库待复核"卡片（值>0）→ 确认跳转到已入库内容 Tab，review status 自动选中"待复核"
5. 点击"待分析提问"卡片（值>0）→ 确认跳转到结果反馈 Tab，status 自动选中"待处理"
6. 点击"答案反馈待处理"卡片（值>0）→ 确认跳转到结果反馈 Tab
7. 点击"待人工确认草稿"卡片（值>0）→ 确认跳转到当前处理任务 Tab，滚动到审查队列
8. 值为 0 的卡片 → 确认不可点击（cursor:default，无 hover 效果）
9. 无跳转目标的卡片（如"知识条目""资料文件"）→ 确认不可点击
10. 卡片 hover 时 → 确认出现"去处理 →"提示 + 阴影效果

---

## 7. 本轮不做（明确排除）

| 排除项 | 原因 |
|--------|------|
| 批量操作（批量确认/批量忽略/批量修正） | 超出最小方案范围 |
| 自动生成修正任务 | 需后端 workflow 支持 |
| 热点抽检独立页面/独立 API | 复用已入库内容筛选即可 |
| 待分析提问独立分析页 | 复用结果反馈页即可 |
| 复杂统计图表（趋势图/饼图） | 超出治理入口范围 |
| 后端分页/搜索 | 现有 limit=50 足够 |
| 指标趋势对比（环比/同比） | 属于质量仪表盘，非处理入口 |
| contribution（已确认修正）独立管理页 | 暂无此页面，后续增强 |
| `AdminPendingController` 的 correct/confirm/discard 操作 | 这些是已有操作，不属本轮范围 |

---

## 8. 风险与测试建议

### 8.1 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 跳转后筛选未生效 | 低 | 在 `handleMetricCardAction` 中设置 state 后，目标 Tab 的 `load*()` 函数会读取 state 中的筛选值 |
| 0 值卡片仍可点击 | 低 | 渲染时检查 `Number(value) > 0`，0 值不渲染 `data-metric-action` |
| 事件委托冲突 | 低 | 使用 `data-metric-action` 精确匹配，不会影响卡片内其他元素 |
| 与现有筛选状态冲突 | 低 | 跳转时覆盖 state 中的筛选值，用户手动切换筛选后 state 随之更新 |
| hover 效果在移动端无意义 | 低 | `:hover` 在移动端自动降级，不影响功能 |

### 8.2 人工验收步骤

1. 打开管理后台首页，确认"更多治理指标"面板展开
2. 逐一验证每个可点击指标的跳转行为和筛选预填
3. 验证不可点击指标（知识条目/资料文件/资料源/已确认修正）不响应点击
4. 验证值为 0 的指标卡片灰显且不可点击
5. 验证跳转后用户仍可手动切换筛选，不受预填限制

---

## 9. 本轮确认

- **是否修改了 `src/main/java/**`**：否
- **是否修改了 `src/main/resources/static/**`**：否
- **是否修改了任何配置/文档/脚本**：否
- **是否提交了任何代码**：否
- **仅执行**：API 响应结构分析、前端渲染链路追踪、跳转路径设计、CSS 交互方案设计
