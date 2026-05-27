# admin 删除"已入库内容"页关注内容治理 UI 修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

## 测试结论

全部 14 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests` : 8 个用例通过（含改写 `shouldNotContainGovernanceAttentionUiInKnowledgeArticlesPage`）
- `AdminProcessingTaskControllerTests` : 6 个用例通过

## 从产品角度，为什么当前阶段应该删掉而不是继续解释

"关注内容"治理提示（候选/阈值/更新数/说明卡/需关注筛选）试图让用户在"已入库内容"页理解一套内部治理概念，但它存在三个结构性问题：

1. **没有形成完整处理闭环**：用户看到"候选 N 篇"和"阈值 N"的统计后，没有下一步引导——说明卡只解释了概念，筛选联动只窄化了列表，但没有收口到"然后怎么办"。用户看懂了数字但不知道要做什么。

2. **干扰主流程**：这套 UI 占据"已入库内容"页首屏顶部，把用户的注意力从"搜索→看详情→看风险→复核"这条核心路径拉到一个独立治理子任务上。治理是运维视角，不是内容运营视角。

3. **概念膨胀**：候选、阈值、高频问题关联、需关注——这些内部热度计算概念不需要暴露给内容运营角色。只需要在风险提示里告诉用户"这篇内容被频繁提问，结果可能不够稳定"就够了。

**做减法比继续修补说明卡更正确**：把页面收回到用户真正能操作的核心路径（搜索、详情、风险、来源、复核、去提问），让治理能力在内部继续运行，不打断主流程。

## 删除了哪些用户可见元素

### 1. HTML 删除（`index.html`）

| 删除元素 | DOM ID / 内容 |
|---|---|
| 关注内容状态条 | `#hotspot-refresh-status` + `.article-detail-status-bar`："关注内容未分析" / "关注内容：候选 N · 已更新 N · 阈值 N" |
| "重新分析关注内容"按钮 | `#refresh-hotspots` |
| 治理说明卡整块 | `#governance-explain-panel`（含 `#governance-explain-body`、`#governance-explain-dismiss`、标题"理解"关注内容"指标"、关闭按钮） |
| 说明卡内筛选联动按钮 | `[data-governance-explain-action='filter-need-review']`（"用'需关注'筛选"） |
| 筛选下拉"需关注"选项 | `<option value="requiresResultVerification:true">需关注</option>` |
| `.article-detail-top-bar` 包装层 | 状态条 + 按钮组的 flex 容器（因仅含治理 UI，整体移除） |

**保留的按钮**：`去提问`（`a.article-ask-link`），内嵌在 `.article-detail-button-group` 中。

**保留的筛选选项**：全部风险、高风险内容、高频问题相关、来源冲突、用户反馈。

### 2. CSS 删除（`admin.css`）

删除全部 `.governance-explain-*` 样式规则（约 110 行）：

| 删除的 CSS 类 | 用途 |
|---|---|
| `.governance-explain-panel` | 说明卡片容器（暖色渐变、阴影、max-width） |
| `.governance-explain-panel[hidden]` | 隐藏状态 |
| `.governance-explain-header` | 标题栏 flex 布局 |
| `.governance-explain-icon` | 圆形绿色问号图标 |
| `.governance-explain-title` | 标题文字 |
| `.governance-explain-dismiss` | 关闭按钮 |
| `.governance-explain-dismiss:hover` | 关闭按钮 hover |
| `.governance-explain-body` | 四段式网格布局 |
| `.governance-explain-item` | 单个说明条目卡片 |
| `.governance-explain-item dt` / `dd` | 说明条目标题/内容 |
| `.governance-explain-action` | 内联操作按钮 |
| `.governance-explain-action:hover` | 操作按钮 hover |

同时清除了一段孤儿 CSS（无选择器的残留属性，line ~5853-5857）。

### 3. JS 删除

#### `management-runtime-part-01.js`（1 处删除 + 1 处解绑）

- 删除 `async function refreshHotspots()` 函数（整个函数体，约 30 行）
- 删除事件绑定 `bindIfPresent("refresh-hotspots", "click", refreshHotspots)`

#### `management-runtime-part-04.js`（6 处删除）

| 删除的函数/逻辑 | 说明 |
|---|---|
| `buildHotspotRefreshStatusText(response)` | 生成"关注内容：候选 N · 已更新 N · 阈值 N"状态文本 |
| `renderHotspotRefreshStatus(response)` | 更新 `#hotspot-refresh-status` DOM，含 loading/data/null 三态 |
| `renderGovernanceExplainPanel(response)` | 展示/隐藏治理说明卡，绑定关闭按钮和筛选联动 |
| `buildGovernanceExplainContent(response)` | 生成四段式说明 HTML |
| `syncGovernanceExplainPanel()` | Tab 切换时自动同步说明卡状态 |
| `_originalActivateKnowledgeTab` 覆盖 | 拦截 `activateKnowledgeTab` 以触发 `syncGovernanceExplainPanel` |

连带删除的 state 字段引用：`state.lastHotspotResponse`、`state.governanceExplainDismissed`。

#### `management-runtime-part-05.js`（5 处导出移除）

从 `__LATTICE_ADMIN_TEST__` 的 `article` 导出中移除：
- `buildHotspotRefreshStatusText`
- `renderHotspotRefreshStatus`
- `renderGovernanceExplainPanel`
- `buildGovernanceExplainContent`
- `syncGovernanceExplainPanel`

## 是否保留了任何内部实现

| 保留项 | 说明 |
|---|---|
| `requiresResultVerification` filter key 内部处理 | `part-02.js` 中的 `allowedKeys` 列表、filter 值映射逻辑完整保留，只是不再在 UI 下拉中暴露选项。如果 URL 参数或编程方式设置该 filter，查询仍可正常执行 |
| workspace 首页"关注内容" metric card | `part-02.js` 中 `renderSummary` 的 hotspot metric card 保留，它展示在工作台概览区（不在"已入库内容"页），提供轻量状态感知。用户点击"查看内容"仍可导航到已入库内容页 |
| workspace 首页 help-state-card 中的 hotspot 提示 | `deriveKnowledgeHelpState` 中的 `hotspotPendingCount > 0` 分支保留，它在工作台右侧卡提供状态引导 |
| 后端 `/api/v1/admin/articles/hotspots/refresh` 接口 | 未删除（前端调用已移除，接口定义完好） |

## 是否改动后端

**否**。仅修改前端文件：

- `src/main/resources/static/admin/index.html`（HTML DOM 删除）
- `src/main/resources/static/admin/admin.css`（CSS 规则删除 + 残留清理）
- `src/main/resources/static/admin/modules/management-runtime-part-01.js`（函数 + 绑定删除）
- `src/main/resources/static/admin/modules/management-runtime-part-04.js`（6 个函数/逻辑删除）
- `src/main/resources/static/admin/modules/management-runtime-part-05.js`（5 个导出移除）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（测试重写 + harness 清理）

后端代码（`src/main/java/**`）、数据库、API 接口、ask-runtime、compile-review-queue 均未触碰。

## 测试扩展（`ManagementJsRuntimeTests.java`）

**重写 `shouldNotContainGovernanceAttentionUiInKnowledgeArticlesPage`**（原 `shouldRenderGovernanceExplainPanelWithFourPartExplanation`）：

- 验证 index.html 不再包含：`hotspot-refresh-status`、`governance-explain-panel`、`governance-explain-body`、`governance-explain-dismiss`、`理解"关注内容"指标`、`重新分析关注内容`、`关注内容未分析`、`<option>需关注</option>`
- 验证 index.html 仍保留：`去提问`、`已入库内容`、`article-risk-filter`、`搜索`、`article-list`
- 验证 admin.css 不再包含：`.governance-explain-panel`、`.governance-explain-body`、`.governance-explain-item`、`.governance-explain-action`
- 验证 part-04.js 不再包含：`renderGovernanceExplainPanel`、`buildGovernanceExplainContent`、`syncGovernanceExplainPanel`、`renderHotspotRefreshStatus`、`buildHotspotRefreshStatusText`、`governanceExplainDismissed`、`lastHotspotResponse`、`_originalActivateKnowledgeTab`、`你现在还不能做什么`
- 旧术语回归断言保留：不包含抽检、待验证、热点刷新、热点未验证

**harness 脚本清理**：

- 移除 `buildHotspotRefreshStatusText`、`renderHotspotRefreshStatus` 运行时断言（约 20 行）
- 移除 hotspot 文案的 term-check 断言（约 17 行）

**已有测试适配**：

- `shouldUseHumanReadableQualityCheckCopyInReviewQueuePlaceholder`：移除 `article-detail-top-bar` 和 `article-detail-status-bar` 的存在断言，保留 `article-detail-button-group` 断言

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. "已入库内容"页首屏不再显示"关注内容未分析"状态条
2. 不再显示"重新分析关注内容"按钮
3. 不再显示治理说明卡（理解"关注内容"指标）
4. 筛选下拉"全部风险"中不再包含"需关注"选项
5. `去提问` 按钮仍然可用，点击跳转到知识问答页
6. 文章列表、详情、风险提示、来源、复核面板无回归
7. 搜索、状态筛选、复核状态筛选、风险筛选（剩余选项）正常工作
8. workspace 首页右侧"当前状态"卡不受影响，仍展示知识库状态摘要
9. workspace 首页 metric card 中的"关注内容"指标卡不受影响
10. 移动端视口下已入库内容页布局无异常
