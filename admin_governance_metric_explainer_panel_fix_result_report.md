# admin 治理指标说明面板修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

## 测试结论

全部 13 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests` : 7 个用例通过（含新增 `shouldRenderGovernanceExplainPanelWithFourPartExplanation`）
- `AdminProcessingTaskControllerTests` : 6 个用例通过

## 问题

"已入库内容"页顶部的治理状态区只显示了"关注内容"指标数值（候选 N · 已更新 N · 阈值 N），但用户看不懂这些指标的含义。需要一个轻量、紧凑、暖色的说明面板来解释。

## 设计决策

- **方案 A（顶部状态条 + 下方说明卡）**：说明面板放在状态条下方，默认隐藏，有数据时自动展开
- 不在每个指标旁边永久挂长段注释
- 每个说明覆盖 4 部分：这是什么、你需要关注什么、你现在能做什么、你现在还不能做什么
- 说明面板与"需关注"筛选联动——面板内有快捷筛选按钮
- 桌面端放在状态区下方，移动端自动折行
- 无后端改动

## 实际改动

### 1. HTML（`index.html`）

在 `knowledge-articles` tab 的 `.article-detail-top-bar` 下方新增 `#governance-explain-panel`：

```html
<div id="governance-explain-panel" class="governance-explain-panel" hidden>
    <div class="governance-explain-header">
        <span class="governance-explain-icon">?</span>
        <span class="governance-explain-title">理解"关注内容"指标</span>
        <button id="governance-explain-dismiss" ...>×</button>
    </div>
    <div id="governance-explain-body" class="governance-explain-body"></div>
</div>
```

### 2. CSS（`admin.css`）

新增约 100 行暖色紧凑样式：

| 类 | 用途 |
|---|---|
| `.governance-explain-panel` | 卡片容器：暖色渐变背景、细边框、弱阴影、max-width 720px |
| `.governance-explain-header` | 标题栏：图标 + 标题 + 关闭按钮 |
| `.governance-explain-icon` | 圆形绿色问号图标 |
| `.governance-explain-body` | 四段式网格布局：`grid-template-columns: repeat(auto-fit, minmax(260px, 1fr))` |
| `.governance-explain-item` | 单个说明条目：微白底卡片 |
| `.governance-explain-action` | 内联操作按钮（"用'需关注'筛选"），绿色下划线样式 |
| `.governance-explain-dismiss` | 关闭按钮，hover 时显色 |

### 3. JS（`management-runtime-part-04.js`）

新增两个函数：

**`buildGovernanceExplainContent(response)`** — 纯函数，基于 hotspot 响应生成四段式 HTML：
1. 这是什么：解释"关注内容"是系统基于近期提问数据自动识别的高频问题关联内容，展现实时数值
2. 你需要关注什么：候选数越大说明越多条目被频繁提问，建议优先核对
3. 你现在能做什么：用筛选器只看这些内容，逐条核对（含"用'需关注'筛选"快捷按钮）
4. 你现在还不能做什么：系统提示不会自动修改答案，需人工复核收口

**`renderGovernanceExplainPanel(response)`** — DOM 操作函数：
- 无数据或 loading 时隐藏面板
- 有数据时展示面板，填充说明内容
- 绑定关闭按钮和筛选联动按钮事件

**集成点**：`renderHotspotRefreshStatus` 在设置状态条后自动调用 `renderGovernanceExplainPanel`。

### 4. 测试（`ManagementJsRuntimeTests.java`）

**新增 `shouldRenderGovernanceExplainPanelWithFourPartExplanation`**：
- 验证 `index.html` 中存在 `#governance-explain-panel`、`#governance-explain-body`、关闭按钮
- 验证 `admin.css` 中存在 `.governance-explain-panel`、`.governance-explain-body`、`.governance-explain-item`
- 验证 `part-04.js` 中存在 `buildGovernanceExplainContent` 和 `renderGovernanceExplainPanel`
- 验证四段式结构：这是什么、你需要关注什么、你现在能做什么、你现在还不能做什么
- 验证无旧术语：抽检、待验证、热点刷新、热点未验证
- 验证与"需关注"筛选的联动：`requiresResultVerification:true`

**原有 6 个用例全部通过，无回归。**

## 是否改动后端

**否**。本次仅修改前端文件：

- `src/main/resources/static/admin/index.html`（新增 8 行 DOM 结构）
- `src/main/resources/static/admin/admin.css`（新增约 100 行样式）
- `src/main/resources/static/admin/modules/management-runtime-part-04.js`（新增 2 个函数 + 集成点）
- `src/main/resources/static/admin/modules/management-runtime-part-05.js`（测试 API 导出注册）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（新增 1 个测试方法）

后端代码（`src/main/java/**`）、数据库、API 接口均未触碰。

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 进入"已入库内容"页，当关注内容分析完成后，状态条下方出现暖色说明卡
2. 说明卡展示四段式内容：这是什么 / 你需要关注什么 / 你现在能做什么 / 你现在还不能做什么
3. 点击"用'需关注'筛选"按钮，筛选器自动切换为"需关注"并触发查询
4. 点击说明卡右上角 × 可关闭面板
5. 点击"重新分析关注内容"后，loading 期间说明面板自动隐藏，完成后重新出现
6. 移动端视口下说明面板自动折行，不溢出
7. 说明面板中无抽检、待验证、热点刷新、热点未验证等旧术语
8. 已有功能无回归：指标卡片、状态条、列表、详情、复核历史等
