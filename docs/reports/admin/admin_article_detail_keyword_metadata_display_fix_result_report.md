# Admin 文章详情关键词与元数据展示折叠修复结果报告

- 生成时间：2026-05-22
- 任务类型：纯前端最小修复（方案 A）
- 基干设计：`phase_current_workspace_pending_fixes.md` 第 B 项

---

## 1. 修改了哪些文件

| 文件 | 变更 | 说明 |
|---|---|---|
| `modules/management-runtime-part-03.js` | **修改 2 处** | `renderArticleDetail()`: 关键词折叠 + 元数据 details 包裹 |
| `admin.css` | **新增 28 行** | `.article-metadata-toggle` 系列 + `.article-relations-toggle` 按钮样式 |

**代码统计**：~30 行 JS 变更 + ~28 行 CSS 新增 = **~58 行总变更**（2 个文件）

**未修改的文件（符合禁止范围）：**
- `src/main/java/**`：零修改
- `src/test/java/**`：零修改
- `index.html`：零修改
- prompt / schema / 模型配置：零修改
- approve/reject/publish 逻辑：零修改
- `admin-common.js`、其他 `admin-runtime-part-*.js`：零修改

---

## 2. 修复项逐一验收

### 2.1 关键词默认只展示前 8 个（B 项）

在 `renderArticleDetail()` 中，将原来直接 `renderTagGroup(relations)` 改为条件分支：

```javascript
var _maxVisible = 8;
if (relations.length > _maxVisible) {
    // 前 8 个可见 + 其余隐藏 + 切换按钮
    document.getElementById("article-relations").innerHTML =
        renderTagGroup(_visibleItems)
        + "<span id='article-relations-hidden' style='display:none'>" + _hiddenHtml + "</span>"
        + " <button id='article-relations-toggle' class='pill article-relations-toggle' type='button'>还有 N 个关键词</button>";
    // 按钮点击切换显示/隐藏
} else {
    document.getElementById("article-relations").innerHTML = renderTagGroup(relations);
}
```

切换行为：
- 初始：展示前 8 个 pill，按钮文案"还有 N 个关键词"
- 点击展开：隐藏的关键词变为可见，按钮文案变为"收起关键词"
- 再次点击：隐藏关键词，按钮文案恢复"还有 N 个关键词"
- 关键词总数 ≤8 时：不显示按钮，全部铺开（与旧行为一致）

### 2.2 技术元数据 JSON 默认折叠（B 项）

在 `renderArticleDetail()` 中，将原来直接 `.textContent = prettyJson(...)` 改为包裹在 `<details>` 中：

```javascript
var _metadataJson = prettyJson(detail.metadataJson, "暂无技术元数据");
var _metadataPre = document.getElementById("article-metadata");
if (_metadataPre && _metadataPre.parentElement) {
    _metadataPre.parentElement.innerHTML =
        "<h4>技术元数据</h4>"
        + "<details class='article-metadata-toggle'>"
        + "<summary>技术信息</summary>"
        + "<pre id='article-metadata' class='code-view'>" + escapeHtml(_metadataJson) + "</pre>"
        + "</details>";
} else if (_metadataPre) {
    _metadataPre.textContent = _metadataJson;
}
```

关键设计点：
- 使用 `parentElement.innerHTML` 替换整个 `<section>` 内容，避免二次渲染时 `<details>` 嵌套问题
- 保留 `<pre id='article-metadata' class='code-view'>` 的 id 和 class，`clearArticleDetail()` 中 `.textContent = "暂无技术元数据"` 仍然生效
- `escapeHtml()` 对 JSON 文本做 HTML 转义，防止 XSS
- `parentElement` 为空时回退到 `.textContent`（兼容测试沙箱等无 DOM 环境）

---

## 3. CSS 样式说明

新增样式全部集中在 `admin.css` 的可复用模块区域末尾：

### 3.1 `.article-metadata-toggle`（元数据折叠面板）

| 属性 | 值 | 说明 |
|---|---|---|
| `border` | `1px solid rgba(143,190,255,0.12)` | 淡色边框，比 `.advanced-toggle` 更轻 |
| `border-radius` | `16px` | 与页面圆角一致 |
| `background` | `rgba(23,39,65,0.24)` | 半透明深色背景 |
| `summary` 样式 | 复用 `::after` 展开/收起文案模式 | "展开" / "收起" |
| `.code-view` 内边距 | `margin: 0 14px 14px` | 与 summary 对齐 |
| `.code-view` 最大高度 | `max-height: 360px; overflow: auto` | 超长 JSON 可滚动 |

### 3.2 `.article-relations-toggle`（关键词切换按钮）

| 属性 | 值 | 说明 |
|---|---|---|
| `cursor` | `pointer` | 表明可点击 |
| `border-color` | `rgba(126,186,255,0.32)` | 蓝色调边框 |
| `background` | `rgba(41,69,112,0.56)` | 蓝色调背景 |
| `color` | `#b4d6ff` | 浅蓝文字 |
| `:hover` | 边框加深 + 背景加深 | 交互反馈 |

---

## 4. 测试验证

`ManagementJsRuntimeTests`（3 个测试）全部通过：
- `shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime`
- `shouldVerifyRunFallbackAndErrorPresentationViaNode`
- `shouldVerifyRunHelpStateAndPublishOutcomeDisplayViaNode`

未运行全量 `mvn test`，原因：本轮仅修改 ~30 行 JS 和 ~28 行 CSS，无后端变更，定向 JS 运行时测试已覆盖。

---

## 5. 不涉及的范围（已确认零修改）

- 不修改 `src/main/java/**`
- 不修改 `src/test/java/**`
- 不修改 `index.html`
- 不修改 LLM prompt（`LatticePrompts.java`）
- 不修改 approve/reject 接口逻辑
- 不修改后端 DTO/API
- 不做关键词业务词/工程词硬编码过滤
- 不删除任何关键词
- 本轮仅修改 `modules/management-runtime-part-03.js` 和 `admin.css`
- 不修改 `admin.css` 中已有的任何样式规则

---

## 6. 建议 agentD 人工验收

1. 打开管理后台 → 知识库 → 点击一条有关键词的文章
2. 确认关键词超过 8 个时，默认只展示前 8 个，并显示"还有 N 个关键词"按钮
3. 点击按钮确认全部关键词展开，按钮文案变为"收起关键词"
4. 再次点击确认关键词收起
5. 确认关键词 ≤8 个时，全部直接展示，无切换按钮
6. 确认"技术元数据"区域默认折叠，显示"技术信息 ▶ 展开"
7. 点击展开确认 JSON 格式化展示，summary 变为"技术信息 ▶ 收起"
8. 确认切换不同文章时，折叠状态正确重置
