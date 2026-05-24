# admin 文章详情空指针报错修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

## 测试结论

全部 14 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests` : 8 个用例通过（含新增 runtime 断言：`clearArticleDetail` 在 `article-technical-info` 缺失时不抛错 + null guard 源码检测）
- `AdminProcessingTaskControllerTests` : 6 个用例通过

## 根因

`clearArticleDetail()`（`management-runtime-part-03.js`）无条件写入 `article-technical-info` 节点：

```javascript
// 修复前 — 无 null guard，直接写入
document.getElementById("article-technical-info").innerHTML = "";
```

但 `article-technical-info` 是一个**动态创建的节点**——它只在 `renderArticleDetail()` 内部被创建，且仅当 `article-metadata` 元素位于 `.detail-section` 容器中时才会生成：

```javascript
// renderArticleDetail 内部 — 条件创建
if (_metadataSection) {
    _metadataSection.innerHTML = "...<div id='article-technical-info'...>...";
}
```

**触发链路**：

1. 页面首次加载 → `clearArticleDetail()` 被调用 → `article-technical-info` 尚未被创建（`renderArticleDetail` 未运行过）
2. `document.getElementById("article-technical-info")` 返回 `null`
3. `null.innerHTML = ""` 抛出 `TypeError: Cannot set properties of null (setting 'innerHTML')`
4. 错误被上层 `loadArticles()` 的 catch 块捕获并显示为 `加载入库内容失败：...`

同样的问题也会在以下场景触发：
- 文章详情切换时先 `clearArticleDetail` 再 `renderArticleDetail`
- 从其他 tab 切回"已入库内容"时触发 `loadArticles` 刷新

## 改动点

### 1. `management-runtime-part-03.js` — `clearArticleDetail` null guard（核心修复）

```javascript
// 修复前
document.getElementById("article-technical-info").innerHTML = "";

// 修复后
var _techInfo = document.getElementById("article-technical-info");
if (_techInfo) {
    _techInfo.innerHTML = "";
}
```

仅 1 处修改，在 `clearArticleDetail` 函数末尾对 `article-technical-info` 写操作增加 null guard。

### 2. `management-runtime-part-05.js` — 测试 API 导出（1 行新增）

新增 `clearArticleDetail: clearArticleDetail` 导出到 `__LATTICE_ADMIN_TEST__` 对象，使测试 harness 能引用该函数进行 runtime 验证。

### 3. `ManagementJsRuntimeTests.java` — runtime 测试（harness 新增 15 行）

在 `buildHarnessScript()` 中新增 runtime 断言：

- **动态 mock `getElementById`**：对 `article-technical-info` 返回 `null`，其他 ID 正常返回 mock 元素
- **try/catch 包裹 `clearArticleDetail()` 调用**：验证不抛错
- **源码静态检测**：`clearArticleDetail` 函数源码中包含 `_techInfo` 变量（即 null guard 变量名）

```javascript
sandbox.document.getElementById = function (id) {
    if (id === "article-technical-info") {
        return null;  // 模拟节点不存在
    }
    // ... 其他正常返回
};
var clearErr = null;
try {
    articleUi.clearArticleDetail();
} catch (e) {
    clearErr = e;
}
assert(clearErr === null,
    "clearArticleDetail should not throw when article-technical-info is missing");
```

## 是否改动后端

**否**。仅修改前端文件：

- `src/main/resources/static/admin/modules/management-runtime-part-03.js`（1 处 null guard）
- `src/main/resources/static/admin/modules/management-runtime-part-05.js`（1 行测试导出）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（harness 新增 15 行运行时断言）

后端代码（`src/main/java/**`）、数据库、API 接口、index.html、CSS 均未触碰。

## 未回退"开发诊断信息"合并方案

`article-technical-info` 仍然在 `renderArticleDetail` 内部动态创建，作为 `开发诊断信息` details 折叠区的子元素。本轮仅在 `clearArticleDetail` 中增加对它的 null guard，未改动合并方案本身。

## 其他详情节点安全检查结果

| 元素 ID | 来源 | 风险 |
|---|---|---|
| `article-detail-title` | HTML 静态节点 | 无（始终存在） |
| `article-detail-meta` | HTML 静态节点 | 无 |
| `article-detail-summary` | HTML 静态节点 | 无 |
| `article-risk-summary` | HTML 静态节点 | 无 |
| `article-primary-source` | HTML 静态节点 | 无 |
| `article-source-overview` | HTML 静态节点 | 无 |
| `article-source-note` | HTML 静态节点 | 无 |
| `article-content` | HTML 静态节点 | 无 |
| `article-metadata` | HTML 静态节点 | 无（`renderArticleDetail` 已有 `if (_metadataPre)` guard） |
| `article-sources` | HTML 静态节点 | 无 |
| `article-relations` | HTML 静态节点 | 无 |
| `article-technical-info` | **动态创建**（`renderArticleDetail` 内） | **已修复**（`clearArticleDetail` 增加 null guard） |

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 进入"已入库内容"页，页面顶部不再出现 `加载入库内容失败` 红色错误提示
2. 点击文章列表中的文章，详情正常展示（标题、摘要、风险提示、来源、正文、开发诊断信息）
3. 在文章之间切换，详情正确更新，不报错
4. 从"已入库内容"切到其他 tab（如"资料导入"）再切回来，页面正常加载
5. 清空文章选择（若有此操作），详情区显示占位文案，不报错
6. "开发诊断信息"折叠区正常工作：默认折叠，点击展开后能看到技术信息和原始 metadata JSON
7. 已有功能无回归：搜索、筛选、去提问、人工复核面板
