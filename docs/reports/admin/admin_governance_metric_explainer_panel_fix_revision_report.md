# admin 治理指标说明面板自动显示修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

**上一轮报告**: `admin_governance_metric_explainer_panel_fix_result_report.md`

## 测试结论

全部 13 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests` : 7 个用例通过（原有 6 个 + 扩展 `shouldRenderGovernanceExplainPanelWithFourPartExplanation`）
- `AdminProcessingTaskControllerTests` : 6 个用例通过

## 为什么上一版说明卡不够

上一版说明卡的 `renderGovernanceExplainPanel(response)` **仅在 `renderHotspotRefreshStatus(response)` 内部被调用**，而 `renderHotspotRefreshStatus` **仅在用户点击"重新分析关注内容"按钮后被调用**（由 `refreshHotspots()` 触发）。

这导致：
- 页面首次加载时，即使用户切到"已入库内容"tab，状态条数据可能存在（来自本次会话中之前的刷新），但说明卡不会自动出现
- 用户必须先主动点击"重新分析关注内容"，才能看到说明卡
- 产品期望是"首屏即可理解指标"，而不是"先操作再理解"

链路断点：
```
页面加载 → refreshSummary() → loadArticles() → 状态条显示"关注内容未分析"
                                          ↓ (无 hotspot 数据，说明卡不展示，正确)
                                          
用户点击"重新分析关注内容" → refreshHotspots() → renderHotspotRefreshStatus(response)
                                          → renderGovernanceExplainPanel(response) ← 说明卡出现

用户切到其他 tab 再切回来 → ??? （无任何代码触发说明卡同步）
                           → 状态条仍显示上次的"关注内容：候选 N · 已更新 N · 阈值 N"
                           → 但说明卡不见了（被 dismiss 或未重新渲染）
```

## 本轮如何实现"首次进入已入库内容页即可看到解释"

### 核心思路

不改变说明卡的渲染逻辑本身，而是引入三个轻量机制：

1. **状态持久化**：将最后一次有效的 hotspot response 存入 `state.lastHotspotResponse`
2. **dismiss 追踪**：用 `state.governanceExplainDismissed` 记录用户是否已手动关闭
3. **Tab 感知同步**：Override `activateKnowledgeTab`，在切换到 `knowledge-articles` 时自动调用 `syncGovernanceExplainPanel()`

### 具体改动（`management-runtime-part-04.js`）

#### 1. `renderHotspotRefreshStatus` — 存取 lastHotspotResponse

| 场景 | 旧行为 | 新行为 |
|---|---|---|
| loading | 调用 `renderGovernanceExplainPanel(null)` | 额外：`state.lastHotspotResponse = null`，`state.governanceExplainDismissed = false` |
| 有数据 | 调用 `renderGovernanceExplainPanel(response)` | 额外：`state.lastHotspotResponse = response` |
| null（错误） | 调用 `renderGovernanceExplainPanel(null)` | 不变（lastHotspotResponse 保留旧值，不覆盖） |

#### 2. `renderGovernanceExplainPanel` — dismiss 标记

关闭按钮点击时，额外设置 `state.governanceExplainDismissed = true`。

```javascript
dismissBtn.addEventListener("click", function () {
    panel.hidden = true;
    state.governanceExplainDismissed = true;  // 新增
});
```

#### 3. 新增 `syncGovernanceExplainPanel()`

```javascript
function syncGovernanceExplainPanel() {
    if (state.lastHotspotResponse && !state.governanceExplainDismissed) {
        renderGovernanceExplainPanel(state.lastHotspotResponse);
    }
}
```

纯函数式同步：有数据且未 dismiss → 展示；否则不操作（不影响 loading 等已有隐藏逻辑）。

#### 4. Override `activateKnowledgeTab`

```javascript
var _originalActivateKnowledgeTab = activateKnowledgeTab;
activateKnowledgeTab = function (tabName, options) {
    _originalActivateKnowledgeTab(tabName, options);
    if (tabName === "knowledge-articles") {
        syncGovernanceExplainPanel();
    }
};
```

每次切到"已入库内容"tab 时自动同步说明卡状态。

### dismiss 与重新出现的规则

| 用户动作 | lastHotspotResponse | governanceExplainDismissed | 说明卡 |
|---|---|---|---|
| 页面刚加载，未曾刷新 | `undefined` | `false` | 隐藏（无数据可解释） |
| 点击"重新分析关注内容"成功 | 设为 response | 重置为 `false` | 展示 |
| 点击 × 关闭 | 保留 | 设为 `true` | 隐藏 |
| 切到其他 tab 再切回来 | 保留 | `true`（未被重置） | 隐藏（已 dismiss） |
| 再次点击"重新分析关注内容" | 更新为新 response | 重置为 `false` | 展示（新一轮结果） |
| 刷新中（loading） | 设为 `null` | 重置为 `false` | 隐藏（loading 中） |

### 测试扩展（`ManagementJsRuntimeTests.java`）

**扩展 `shouldRenderGovernanceExplainPanelWithFourPartExplanation`**，新增 6 个静态断言：

- `state.lastHotspotResponse` 存在于源码中
- `state.governanceExplainDismissed` 存在于源码中
- `syncGovernanceExplainPanel` 函数存在
- `_originalActivateKnowledgeTab` 覆盖模式存在
- `governanceExplainDismissed = true`（dismiss 标记）
- `governanceExplainDismissed = false`（重新分析时重置）

## 是否改动后端

**否**。仅修改前端文件：

- `src/main/resources/static/admin/modules/management-runtime-part-04.js`（4 处修改：状态存取、dismiss 标记、sync 函数、tab override）
- `src/main/resources/static/admin/modules/management-runtime-part-05.js`（测试 API 导出 `syncGovernanceExplainPanel`）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（扩展已有测试方法 6 个新断言）

后端代码（`src/main/java/**`）、数据库、API 接口、CSS 均未触碰。

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 页面加载后，首次切到"已入库内容"，若从未执行过"重新分析关注内容"，说明卡不出现（正常——无数据可解释）
2. 点击"重新分析关注内容"，分析完成后说明卡出现
3. 点击说明卡 × 关闭
4. 切到其他 tab（如"资料导入"），再切回"已入库内容"，说明卡**不再出现**（已 dismiss）
5. 再次点击"重新分析关注内容"，分析完成后说明卡**重新出现**（新一轮结果，dismiss 重置）
6. loading 期间说明卡仍然隐藏
7. "用'需关注'筛选"按钮联动仍然有效
8. 无旧术语回归（抽检、待验证、热点刷新、热点未验证）
9. 已有功能无回归：指标卡片、状态条、列表、详情、复核历史等
