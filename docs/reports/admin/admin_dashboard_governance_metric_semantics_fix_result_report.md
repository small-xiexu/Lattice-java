# 管理后台治理指标产品语义收口修复报告

- 生成时间：2026-05-23
- 分支：`codex/qa-polish`
- 范围：仅 admin 前端 JS runtime + 静态 HTML + CSS（无后端 Java 变更）

---

## 1. 修复总览

| # | 修复项 | 涉及文件 | 状态 |
|---|--------|----------|------|
| 1 | "待分析提问"纯只读统计 | part-02.js | 已完成 |
| 2 | "热点待抽检"→"热点内容提醒"语义修正 | part-02.js | 已完成 |
| 3 | actionHint 差异化：去确认/查看反馈/查看内容/去处理 | part-02.js | 已完成 |
| 4 | handleMetricCardAction 支持 3 个 tab 的 scrollTo | part-02.js | 已完成 |
| 5 | "技术元数据"→"开发诊断信息"并默认折叠 | part-03.js, index.html | 已完成 |
| 6 | ManagementJsRuntimeTests 覆盖新语义 | ManagementJsRuntimeTests.java | 已完成 |

---

## 2. 各修复项详情

### 2.1 "待分析提问"只读统计

**修复前**：`pendingQueryCount > 0` 时 action 值为 `"后台记录的待分析提问，处理入口待开放"`，UI 显示"去处理 →"但不可点击。

**修复后**：
- `action` 设为 `undefined`，`actionHint` 设为 `undefined`
- help 文本改为 `"后台已记录的待分析提问，当前仅展示统计"`
- 卡片渲染为 `<div>`（无 `data-metric-action`，无 `clickable` class，不可点击）
- 不再出现"待开放"等开发向文案

### 2.2 "热点待抽检"→"热点内容提醒"

| 属性 | 修复前 | 修复后 |
|------|--------|--------|
| label | 热点待抽检 | 热点内容提醒 |
| note | 优先核对高频问题结果 | 高频问题关联的内容，建议后续核对答案和引用 |
| actionHint | 去处理 → | 查看内容 → |
| help state（有积压） | 有 N 个高频热点待结果抽检 | 有 N 个高频内容提醒待验证 |
| help state（无积压） | 当前没有热点抽检积压 | 当前没有高频问题提醒 |
| resolveArticleMetricFilterMessage | 已切换到已入库内容，并筛选热点待抽检 | 已切换到已入库内容，并筛选高频问题相关内容；当前仅用于查看 |

### 2.3 actionHint 差异化

七张指标卡片的 actionHint 语义：

| 指标卡片 | actionHint | 说明 |
|----------|-----------|------|
| 待人工确认草稿 | 去确认 → | 跳转 runs tab，scrollTo review-queue-list |
| 答案反馈待处理 | 查看反馈 → | 跳转 feedback tab，scrollTo query-feedback-list |
| 待分析提问 | 无 | 只读统计，不可点击 |
| 已入库待复核 | 去处理 → | 有后端闭环处理 |
| 高风险内容 | 去处理 → | 有后端闭环处理 |
| 热点内容提醒 | 查看内容 → | 跳转 articles tab，scrollTo article-list |
| 用户反馈风险 | 去处理 → | 有后端闭环处理 |

其中仅"已入库待复核"、"高风险内容"、"用户反馈风险"三张有真实后端闭环的卡片保留"去处理 →"。

### 2.4 handleMetricCardAction scrollTo 增强

修复前仅 `knowledge-runs` tab 有 `scrollTo` 逻辑。修复后三个 tab 全部支持：

| tab | scrollTo target | 触发指标 |
|-----|----------------|----------|
| knowledge-runs | review-queue-list | 待人工确认草稿 |
| knowledge-articles | article-list | 热点内容提醒, 已入库待复核, 高风险内容, 用户反馈风险 |
| knowledge-feedback | query-feedback-list | 答案反馈待处理 |

实现方式：`setTimeout(() => el.scrollIntoView({ behavior: 'smooth', block: 'start' }), 200)`。

### 2.5 "技术元数据"→"开发诊断信息"

| 文件 | 变更内容 |
|------|----------|
| part-03.js | `<h4>技术元数据</h4>` → `<h4>开发诊断信息</h4>` |
| part-03.js | `<summary>技术信息</summary>` → `<summary>开发诊断信息</summary>` |
| part-03.js | `"暂无技术元数据"` → `"暂无开发诊断信息"`（2 处） |
| index.html | `<h4>技术元数据</h4>` → `<h4>开发诊断信息</h4>` |
| index.html | `暂无元数据` → `暂无开发诊断信息` |

`<details>` 元素默认无 `open` 属性，即默认折叠。

---

## 3. 测试结果

### 3.1 测试命令

```bash
mvn test -pl . \
  -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### 3.2 测试结果

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| ManagementJsRuntimeTests | 3 | 全部通过 |
| AdminProcessingTaskControllerTests | 6 | 全部通过 |

### 3.3 ManagementJsRuntimeTests 断言覆盖

**shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime**:
- `resolveReviewIssueCount`（2→2, 空→0, null→0）
- `review-issue-list-scroll` class 存在
- 标题含"共 3 个问题"

**shouldVerifyRunFallbackAndErrorPresentationViaNode**:
- `renderMetricCard`：button vs div 渲染、actionHint 可见性、零值卡片
- `renderSummary`：7 张卡片标签全覆盖、6 个 `data-metric-action`（pendingQuery 无 action）
- `resolveArticleMetricFilterMessage`：5 种场景含空结果提示
- `handleMetricCardAction`：3 种筛选器 + 状态栏消息
- `pendingQueryCount=27`：无 `data-metric-action`，无"去处理"，无"待开放"
- actionHint 语义：3 张后端闭环卡片使用"去处理 →"（精确计数 3），草稿"去确认 →"，反馈"查看反馈 →"，热点"查看内容 →"
- scrollTo：articles 和 feedback tab 的 scrollTo 不抛异常
- `normalizeArticleKeywords`：关键词段存在、无"关键词:"前缀、toggle 存在、辅助区含"关联信息"

---

## 4. 修复过程中遇到的问题

### 4.1 编码格式差异

part-02.js 中中文存储为 `\\uXXXX`（双反斜杠 Unicode 转义），而 part-03.js 和 index.html 中为原始 UTF-8 字节。修复脚本需要使用不同的编码函数：

- part-02.js：`'\\\\u{:04x}'.format(ord(c))`（双反斜杠 ASCII）
- part-03.js/index.html：`s.encode('utf-8')`（原始 UTF-8 字节）

第一次尝试用单反斜杠 `\\uXXXX` 替换 part-02.js 时匹配失败，排查后确认文件实际为 `\\\\uXXXX` 格式。

### 4.2 测试断言过于宽泛

原断言 `!summaryHtml.includes("去处理 →")` 检查整个 summary HTML 不含"去处理 →"，但 3 张有后端闭环的卡片（已入库待复核、高风险内容、用户反馈风险）合法使用该文案，导致断言失败。

**修复**：改为精确计数 — `(summaryHtml.match(/去处理 →/g) || []).length === 3`，既验证 3 张需要后端闭环的卡片保留了"去处理 →"，又确保热点卡片没有错误使用。

---

## 5. 未覆盖项（需人工浏览器验收）

| # | 验证项 | 状态 |
|---|--------|------|
| 1 | "待分析提问"卡片：即使 count > 0 也不可点击，无"去处理 →" | 未覆盖浏览器视觉验收 |
| 2 | "热点内容提醒"卡片：显示"查看内容 →"，点击后跳转 articles tab 并滚动到列表 | 未覆盖浏览器视觉验收 |
| 3 | "待人工确认草稿"卡片：显示"去确认 →"，点击跳转 runs tab | 未覆盖浏览器视觉验收 |
| 4 | "答案反馈待处理"卡片：显示"查看反馈 →"，点击跳转 feedback tab | 未覆盖浏览器视觉验收 |
| 5 | "开发诊断信息"默认折叠，点击 details/summary 展开 | 未覆盖浏览器视觉验收 |
| 6 | button 卡片与 div 卡片在 hover/focus/普通态下视觉一致 | 未覆盖浏览器视觉验收 |
| 7 | 7 张指标卡片标签、数值、note、actionHint 文案与产品语义一致 | 未覆盖浏览器视觉验收 |

---

## 6. 结论

所有代码修改和自动化测试已完成，9/9 测试通过，BUILD SUCCESS。静态检查确认所有中文文案替换准确无误。可进入人工浏览器验收阶段。
