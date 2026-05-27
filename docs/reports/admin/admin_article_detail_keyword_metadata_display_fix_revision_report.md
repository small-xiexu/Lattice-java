# Admin 文章详情关键词与元数据展示修复修订报告

- 生成时间：2026-05-22
- 任务类型：最小 revision（修复二次渲染风险 + 修正报告表述 + 补回归断言）
- 上一轮报告：`admin_article_detail_keyword_metadata_display_fix_result_report.md`

---

## 1. 修改了哪些文件

| 文件 | 变更 | 说明 |
|---|---|---|
| `modules/management-runtime-part-03.js` | **修改 7 行** | `renderArticleDetail()`: 用 `closest(".detail-section")` 替代 `parentElement` |
| `ManagementJsRuntimeTests.java` | **新增 ~40 行** | 增强 mock 支持 `closest`，补连续两次渲染断言 |
| `admin_article_detail_keyword_metadata_display_fix_result_report.md` | **修改 1 行** | 修正文件范围表述 |

**未修改的文件：**
- `admin.css`：本轮无变更
- `index.html`：零修改
- `src/main/java/**`：零修改
- `compile-review-queue.js`：零修改

---

## 2. 是否只做最小 revision

**是。** 本轮仅做了三件事：
1. 将技术元数据区域重建从 `parentElement` 改为 `closest(".detail-section")`（核心修复）
2. 在 `ManagementJsRuntimeTests` 中补连续两次 `renderArticleDetail` 调用断言
3. 修正修复报告中 1 处文件范围表述不准确

---

## 3. 二次渲染风险修复详情

### 3.1 问题根因

上一轮的代码：

```javascript
var _metadataPre = document.getElementById("article-metadata");
if (_metadataPre && _metadataPre.parentElement) {
    _metadataPre.parentElement.innerHTML = "...";
}
```

**第一次渲染**：`#article-metadata` 是 `<section class="detail-section">` 的直接子元素，`parentElement` 指向 `<section>`，正确替换整个区域。

**第二次渲染**（切换到不同文章）：第一次渲染后，DOM 变为 `<section><h4>技术元数据</h4><details><summary>技术信息</summary><pre id="article-metadata">...</pre></details></section>`。此时 `#article-metadata` 在 `<details>` 内部，`parentElement` 指向 `<details>` 而非 `<section>`。结果是往 `<details>` 内部再次写入 `<h4>` + `<details>`，产生 **details 嵌套、h4 重复**。

### 3.2 修复方案

```javascript
var _metadataPre = document.getElementById("article-metadata");
if (_metadataPre) {
    var _metadataSection = (typeof _metadataPre.closest === "function")
        ? _metadataPre.closest(".detail-section")
        : null;
    if (_metadataSection) {
        _metadataSection.innerHTML = "<h4>技术元数据</h4><details class='article-metadata-toggle'><summary>技术信息</summary><pre id='article-metadata' class='code-view'>" + escapeHtml(_metadataJson) + "</pre></details>";
    } else {
        _metadataPre.textContent = _metadataJson;
    }
}
```

关键设计点：

- **`closest(".detail-section")`**：始终向上查找外层 `<section class="detail-section">`，无论 `#article-metadata` 嵌套在 `<details>` 里多少层，都能找到同一个稳定容器。
- **`typeof ... === "function"` 守卫**：`closest` 是 Element 原型方法，在 Node.js 沙箱（测试环境）的 plain object mock 上不存在。守卫确保测试环境回退到 `.textContent` 路径，不抛 TypeError。
- **`_metadataSection` 为空时回退**：`closest` 匹配不到时（如页面结构异常），回退到直接设置 `textContent`。

### 3.3 为什么不用 parentElement 加循环

`parentElement` 逐层上溯需要在每次渲染时判断当前层级，增加循环逻辑反而脆弱。`closest` 是标准 DOM API，一行定位到目标容器，语义更清晰。

---

## 4. 此前修复是否回退

**否。** 以下修复全部保持：

| 修复项 | 状态 |
|---|---|
| 关键词前 8 个默认展示 + "还有 N 个关键词" 按钮 | 保持 |
| 点击展开/收起关键词 | 保持 |
| 技术元数据 JSON 默认折叠在 `<details>` 中 | 保持（修复了二次渲染路径） |
| `summary` 展开/收起文案（技术信息） | 保持 |
| CSS 样式（`article-metadata-toggle`、`article-relations-toggle`） | 保持 |
| `clearArticleDetail()` 重置逻辑 | 不变 |

---

## 5. 测试补充

### 5.1 新增断言

在 `ManagementJsRuntimeTests.shouldVerifyRunFallbackAndErrorPresentationViaNode` 中追加：

1. **增强 mock**：为 `article-metadata` 元素注入 `closest` 方法，返回共享的 `metadataSectionState` 容器
2. **第一次渲染**：调用 `renderArticleDetail` 验证 `metadataSectionState.innerHTML` 包含 `article-metadata-toggle`
3. **第二次渲染**：再次调用 `renderArticleDetail` 验证：
   - 仍然包含 `article-metadata-toggle`
   - `article-metadata-toggle` 出现次数为 1（非 2，即无嵌套）
   - 新的 metadata 文本出现在 HTML 中
   - `elementState["article-metadata"]` 仍存在

### 5.2 测试结果

`ManagementJsRuntimeTests`（3 个测试）全部通过，新增的二次渲染断言全部通过。

---

## 6. 报告中矛盾表述的修正

| 位置 | 修正前 | 修正后 |
|---|---|---|
| 第 5 节第 9 条 | "不修改 `compile-review-queue.js` 之外的管理后台 JS" | "本轮仅修改 `modules/management-runtime-part-03.js` 和 `admin.css`" |

说明：上一轮修复实际修改的是 `management-runtime-part-03.js`，原表述错误套用了 review-queue 修复中的措辞。

---

## 7. 是否仍建议 agentD 做 runtime 验证

**是。** 重点验证：
1. 打开管理后台 → 知识库 → 点击文章 A，确认技术元数据折叠面板正常
2. 切换到文章 B，确认技术元数据折叠面板仍正常（无嵌套、无 h4 重复）
3. 反复切换文章 A → B → A，确认每次渲染都正确
4. 关键词折叠/展开行为在切换文章后正确重置
