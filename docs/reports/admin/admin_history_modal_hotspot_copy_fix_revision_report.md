# 管理后台热点文案收口 & Modal ESC 清理修订报告

- 生成时间：2026-05-23
- 分支：`codex/qa-polish`
- 基于：`admin_history_modal_hotspot_copy_fix_result_report.md` 的遗留问题

---

## 1. 修复总览

| # | 修复项 | 涉及文件 | 状态 |
|---|--------|----------|------|
| 1 | part-04.js 热点/抽检/验证旧文案全部替换 | management-runtime-part-04.js | 已完成 |
| 2 | Modal ESC keydown handler 生命周期修正 | compile-review-queue.js | 已完成 |
| 3 | ManagementJsRuntimeTests 覆盖新文案和 ESC 清理 | ManagementJsRuntimeTests.java | 已完成 |

**是否改动后端：否。** 零 Java 后端变更。

---

## 2. part-04.js 遗留旧文案清理清单

### 2.1 buildHotspotRefreshStatusText

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| 无 response (null) | 热点未刷新 | 关注内容未分析 |
| loading | 热点刷新中 | 正在分析关注内容 |
| 完成 | 候选 N · 更新 N · 阈值 N | 关注内容：候选 N · 已更新 N · 阈值 N |

### 2.2 buildArticleRiskSummary

| 标记 | 修复前 | 修复后 |
|------|--------|--------|
| isHotspot flag | 高频热点 | 高频问题相关 |
| requiresResultVerification flag | 需要结果抽检 | 需关注 |
| 低风险无标记 | 低风险，暂无额外抽检原因 | 低风险，暂无额外关注原因 |

### 2.3 buildArticleTechnicalInfo

| 标签 | 修复前 | 修复后 |
|------|--------|--------|
| isHotspot 字段 | 热点内容 | 关注内容 |
| requiresResultVerification 字段 | 结果抽检 | 需关注 |

### 2.4 保留不变

- `isHotspot`、`requiresResultVerification`、`HOTSPOT_UNVERIFIED` 等后端字段完整保留
- `getBadgeLabel` 中 `HOTSPOT_UNVERIFIED: "高频问题相关"` （上轮已完成，本轮验证未变动）
- 所有 API 参数和 filter 值不变

### 2.5 全量旧词验证结果

所有允许修改文件中，以下用户可见旧词已清零：

| 旧术语 | part-02 | part-04 | part-05 | index.html |
|--------|---------|---------|---------|------------|
| 热点未刷新 | - | 已清除 | - | - |
| 热点刷新中 | - | 已清除 | - | - |
| 结果抽检 | - | 已清除 | - | - |
| 需要结果抽检 | - | 已清除 | - | - |
| 暂无额外抽检原因 | - | 已清除 | - | - |
| 高频热点 | - | 已清除 | - | - |

---

## 3. Modal ESC 事件清理

### 3.1 问题

`bindReviewActionModalEvents` 中 keydown handler 只在按下 ESC 时自清理。若通过遮罩点击、关闭按钮、取消按钮关闭 modal，keydown handler 残留在 `document` 上，多次打开 modal 会累积重复监听器。

### 3.2 修复方式

**closeReviewActionModal** 统一清理：
```javascript
function closeReviewActionModal() {
    state.modalAction = null;
    if (state._modalKeydownHandler) {
        document.removeEventListener("keydown", state._modalKeydownHandler);
        state._modalKeydownHandler = null;
    }
    const overlay = document.getElementById("review-action-modal-overlay");
    if (overlay) { overlay.remove(); }
}
```

**openReviewActionModal** 防御性清理残留 handler：
```javascript
if (state._modalKeydownHandler) {
    document.removeEventListener("keydown", state._modalKeydownHandler);
    state._modalKeydownHandler = null;
}
```

**bindReviewActionModalEvents** 存储 handler 引用：
```javascript
var modalKeydownHandler = function (event) {
    if (event.key === "Escape") { closeReviewActionModal(); }
};
state._modalKeydownHandler = modalKeydownHandler;
document.addEventListener("keydown", modalKeydownHandler);
```

### 3.3 覆盖的关闭路径

| 关闭方式 | 清理 keydown | 
|----------|-------------|
| ESC 键 | closeReviewActionModal → 清理 |
| 遮罩点击 | closeReviewActionModal → 清理 |
| 关闭按钮 (×) | closeReviewActionModal → 清理 |
| 取消按钮 | closeReviewActionModal → 清理 |
| 提交成功后 | closeReviewActionModal → 清理 |
| 重复打开 modal | openReviewActionModal → 防御清理 |

---

## 4. 测试结果

### 4.1 测试命令

```bash
mvn test -pl . \
  -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### 4.2 测试结果

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| ManagementJsRuntimeTests | 3 | 全部通过 |
| AdminProcessingTaskControllerTests | 6 | 全部通过 |

### 4.3 本轮新增/更新断言

**shouldVerifyRunFallbackAndErrorPresentationViaNode**:

- `buildHotspotRefreshStatusText(null)` → 含"关注内容未分析"，不含"热点未刷新"
- `buildHotspotRefreshStatusText({loading:true})` → 含"正在分析关注内容"，不含"热点刷新中"
- `buildHotspotRefreshStatusText({hotspotCandidateCount:2,...})` → 含"关注内容：候选 2 · 已更新 1 · 阈值 3"
- `buildArticleRiskSummary` with hotspot → 含"高频问题相关"，不含"高频热点"
- `buildArticleRiskSummary` with requiresVerification → 含"需关注"，不含"需要结果抽检"
- `buildArticleRiskSummary` low risk → 含"暂无额外关注原因"，不含"暂无额外抽检原因"
- `buildArticleRiskSummary` source check → 不含"高频热点"/"需要结果抽检"/"抽检"
- part-04 函数源码综合检查 → 不含 6 个旧术语
- part-04 函数源码 → 含"关注内容未分析"/"正在分析关注内容"/"高频问题相关"/"暂无额外关注原因"
- `renderArticleDetail` source → 含"article-technical-info"（验证技术信息段渲染）
- getBadgeLabel 间接验证（through buildArticleRiskSummary with hotspot flag） → 输出"高频问题相关"

**shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime**:

- `closeReviewActionModal` source → 含 `removeEventListener` 和 `_modalKeydownHandler`
- `openReviewActionModal` source → 含 `_modalKeydownHandler`（防御清理）
- `approveSelectedReviewQueueItem` source → 不含 `window.confirm`/`window.prompt`

---

## 5. 修复过程中遇到的问题

### 5.1 `sandbox.getBadgeLabel` 不可访问
函数虽在 sloppy mode 下通过 `new Function()` 执行，但 `sandbox.getBadgeLabel` 仍为 undefined（可能有隐式作用域或 VM 上下文限制）。
**解决**：通过 `buildArticleRiskSummary` 间接验证 getBadgeLabel 的 HOTSPOT_UNVERIFIED 映射。

### 5.2 `sandbox.buildArticleTechnicalInfo` 不可访问
同上，无法从 sandbox 直接调用。
**解决**：通过文件级 Python 静态验证确认标签正确，并在测试中验证 `renderArticleDetail` source 引用 `article-technical-info`（间接证明渲染链路完整）。

### 5.3 元数据 section mock 隔离
`buildArticleTechnicalInfo` 输出到 `#article-technical-info`，而非 `#article-metadata` 所在的 `.detail-section`。
**解决**：改为静态源码验证，避免复杂的 DOM mock 链路。

---

## 6. 未覆盖项（需人工浏览器验收）

| # | 验证项 | 状态 |
|---|--------|------|
| 1 | 刷新热点按钮点击后，状态文字显示"关注内容未分析"→"正在分析关注内容"→"关注内容：候选 N · 已更新 N · 阈值 N" | 未覆盖 |
| 2 | 文章详情页"开发诊断信息"中 isHotspot 字段显示"关注内容"标签 | 未覆盖 |
| 3 | 文章详情页 requiresResultVerification 字段显示"需关注"标签 | 未覆盖 |
| 4 | 文章风险摘要中 isHotspot 标记显示"高频问题相关" | 未覆盖 |
| 5 | 文章风险摘要中"低风险，暂无额外关注原因" | 未覆盖 |
| 6 | Modal 多次打开关闭后，ESC 键行为正常，不会触发多次关闭 | 未覆盖 |
| 7 | Modal 遮罩点击、关闭按钮、取消按钮均正常关闭且不留残 listener | 未覆盖 |

---

## 7. 结论

part-04.js 中 6 个用户可见旧术语全部清零，modal ESC keydown handler 生命周期修正完成（6 条关闭路径全部覆盖清理）。9/9 自动化测试通过，BUILD SUCCESS。后端零变更。可进入人工浏览器验收阶段。
