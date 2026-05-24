# admin 文章详情页布局与视觉打磨修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

## 测试结论

全部 11 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests` : 5 个用例通过（含新增 `shouldNotUseColdBlueBackgroundInArticleDetailToggles`）
- `AdminProcessingTaskControllerTests` : 6 个用例通过

## 修复内容

### 1. 合并"技术信息"和"开发诊断信息"

**问题**：文章详情页同时存在两处技术元数据展示：
- 详情中部双栏里的"技术信息"（`detail-section-grid` 右侧栏，`#article-technical-info`）
- 页面后部单独一块"开发诊断信息"（`#article-metadata`，JSON 元数据）

两者信息层级重叠，用户不知道看哪个。

**修复**：
- **`index.html`**：删除 `detail-section-grid` 双栏结构中的"技术信息"列。原位置只保留"关联信息"独立 `section`。"正文"和"开发诊断信息"段保持不变。
- **`management-runtime-part-03.js`**（`renderArticleDetail`）：将 `buildArticleTechnicalInfo` 的输出从独立的 `#article-technical-info` 注入改为注入到"开发诊断信息"折叠区 `<details>` 内部。技术字段（资料源 ID、文章键、概念 ID、审查标记、风险等级、风险原因、关注内容、需关注、技术置信度）现在作为 `<div id="article-technical-info" class="description-list">` 渲染在 `<details class="article-metadata-toggle">` 内部、`<pre id="article-metadata">` 之前。

**结果**：页面只保留一个"开发诊断信息"折叠区，内部统一展示技术字段 + JSON 元数据，不再出现"技术信息"标题。

### 2. 去除强制双栏等高副作用

**问题**：原来的 `detail-section-grid` 使用 `grid-template-columns: repeat(2, minmax(0, 1fr))`，导致"关联信息"被右侧"技术信息"撑高，大片空白。

**修复**：
- **`index.html`**：将双栏 `<section class="detail-section detail-section-grid">` 替换为独立 `<section class="detail-section">`，关联信息按内容自然收缩高度。
- 开发诊断信息放在后面单独折叠，不参与与关联信息并排等高。

### 3. 颜色系统统一到浅色体系

**问题**：`.article-metadata-toggle`、`.article-relations-toggle`、`.article-keyword-toggle` 三个折叠/切换控件仍使用旧冷蓝深底风格：
- 深蓝半透明底色 `rgba(23, 39, 65, ...)`
- 亮蓝 hover `rgba(41, 69, 112, ...)` / `#b4d6ff`
- 冷调边框 `rgba(143, 190, 255, ...)`

与页面已有的浅色 Starbucks 体系格格不入。

**修复**（`admin.css`）：

| 选择器 | 旧值 | 新值 |
|---|---|---|
| `.article-metadata-toggle` background | `rgba(23, 39, 65, 0.24)` | `linear-gradient(rgba(249,244,237,0.99), rgba(241,233,221,0.97))` |
| `.article-metadata-toggle` border | `rgba(143, 190, 255, 0.12)` | `rgba(67, 79, 68, 0.14)` |
| `.article-metadata-toggle` 新增 | — | `box-shadow: inset 0 1px 0 rgba(255,255,255,0.54)` |
| `.article-relations-toggle` background | `rgba(41, 69, 112, 0.56)` | `linear-gradient(rgba(242,235,223,0.98), rgba(234,227,215,0.96))` |
| `.article-relations-toggle` border | `rgba(126, 186, 255, 0.32)` | `rgba(67, 79, 68, 0.16)` |
| `.article-relations-toggle` color | `#b4d6ff` | `var(--primary-strong)` |
| `.article-relations-toggle:hover` background | `rgba(58, 96, 150, 0.64)` | `linear-gradient(rgba(255,252,247,0.96), rgba(247,242,232,0.96))` |
| `.article-relations-toggle:hover` border | `rgba(126, 186, 255, 0.48)` | `rgba(30, 57, 50, 0.18)` |
| `.article-relations-toggle:hover` color | `#d0e4ff` | `#1e3928` |
| `.article-keyword-toggle` background | `rgba(23, 39, 65, 0.18)` | `linear-gradient(rgba(249,244,237,0.99), rgba(241,233,221,0.97))` |
| `.article-keyword-toggle` border | `rgba(143, 190, 255, 0.12)` | `rgba(67, 79, 68, 0.14)` |
| `.article-keyword-toggle` 新增 | — | `box-shadow: inset 0 1px 0 rgba(255,255,255,0.54)` |

### 4. 已有成果保留

- 复核历史 compact timeline（`review-history-row` / `review-history-action` / `review-history-meta`）
- 热点/关注内容术语收口（`高频问题相关`、`需关注`、`关注内容`、`暂无额外关注原因` 等）
- 开发诊断信息默认折叠（`<details>` 无 `open` 属性）
- 关键词折叠（`.article-keyword-toggle`）与关联信息辅助区（`.article-relations-aux`）
- 未将"开发诊断信息"改回"技术元数据"或"技术信息"

## 是否改动后端

**否**。本次仅修改前端文件：
- `src/main/resources/static/admin/index.html`
- `src/main/resources/static/admin/admin.css`
- `src/main/resources/static/admin/modules/management-runtime-part-03.js`
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`

后端代码（`src/main/java/**`）、数据库、API 接口均未触碰。

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 文章详情页不再出现"技术信息"独立标题，所有技术字段归入"开发诊断信息"折叠区
2. "开发诊断信息"默认折叠，展开后技术字段在上、JSON 元数据在下
3. "关联信息"独立成块，不再被右侧强制撑高产生大片空白
4. 三个 toggle 控件（开发诊断信息折叠、关键词折叠、关联信息切换）背景改为浅暖色，与页面整体浅色体系一致
5. 复核历史 compact timeline、风险提示、正文等区域不受影响
6. 问答页行为无回归
