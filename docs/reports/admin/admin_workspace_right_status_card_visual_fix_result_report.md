# admin 工作台右侧状态卡视觉层级修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

## 测试结论

全部 14 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `ManagementJsRuntimeTests` : 8 个用例通过（含新增 `shouldRenderRightStatusCardAsLightSummaryNotDarkHero`）
- `AdminProcessingTaskControllerTests` : 6 个用例通过

## 为什么当前深色大卡会破坏页面层级

`#knowledge-help-card`（`.help-state-card`）在工作台首屏中位于 `workbench-hero-side` 右侧区，是用户第一眼看到的辅助状态摘要卡。但它的 CSS 基色为深色整块背景：

```css
/* 修复前 — .help-state-card 基色 */
background:
    linear-gradient(180deg, rgba(14, 24, 44, 0.92), rgba(9, 18, 34, 0.92)),
    radial-gradient(circle at top right, rgba(121, 210, 255, 0.08), transparent 34%);
border: 1px solid rgba(143, 190, 255, 0.12);
```

这导致三个问题：

1. **右侧卡与左侧 hero 区形成双 CTA 竞争**：左侧已经是 `hero-eyebrow` + `hero-title` + `hero-description` + 三个按钮的完整引导区，视觉权重最高；右侧再用大面积深底冷色卡，从"辅助状态摘要"变成了"第二个 hero"，用户在两个重心之间摇摆。

2. **"info" 基调卡落入深色基底的漏洞**：`.help-state-card[data-help-tone="success"]` / `warning` / `danger` 在文件尾部（line ~5860–5880）有暖色覆盖，但 `info` 基调没有覆盖规则，直接暴露深海军蓝基底。而 `info` 正是页面首次加载时的默认基调。

3. **Eyebrow 文本是 CTA 语言**：`现在该怎么做` 暗示用户必须立即行动，而非先理解当前状态。在右侧轻量状态摘要中，这进一步强化了"第二个主面板"的错觉。

## 如何把右侧卡降级成辅助状态摘要

### 核心思路

不改 DOM 结构，不改后端数据流，只改两处：

1. **CSS**：`.help-state-card` 全部基调从深底冷色改为浅底暖色，与页面其他 panel（治理说明卡、metric card、panel-title-row）保持同一浅暖色体系
2. **JS 文案**：Eyebrow 从 CTA 式 `现在该怎么做` 改为状态摘要式 `当前状态`

### CSS 改动（`admin.css`，4 处修改）

| 选择器 | 旧（深色） | 新（暖色浅底） |
|---|---|---|
| `.help-card-eyebrow` | `color: rgba(143, 190, 255, 0.88)`（冷蓝） | `color: var(--primary)`（暖绿 `#00754a`），`letter-spacing` 从 `0.12em` 降为 `0.08em` |
| `.help-state-card` 基色 | `rgba(14, 24, 44, 0.92)` 深海军蓝 + 冷蓝放射光 | `rgba(249, 245, 238, 0.96)` 暖米色 + 微绿放射光，边框 `rgba(143, 190, 255, 0.12)` → `rgba(67, 79, 68, 0.1)` |
| `[data-help-tone="success"]` | `rgba(11, 33, 30, 0.92)` 深绿底 | `rgba(238, 247, 242, 0.98)` 浅绿底 |
| `[data-help-tone="warning"]` | `rgba(41, 29, 13, 0.94)` 深棕底 | `rgba(252, 244, 236, 0.98)` 浅暖底 |
| `[data-help-tone="danger"]` | `rgba(43, 15, 19, 0.94)` 深红底 | `rgba(252, 239, 237, 0.98)` 浅粉底 |

所有变体边框也从高饱和冷/亮色改为低饱和暖色（`0.14` 透明度 + 暖色调）。

### JS 文案改动（`management-runtime-part-02.js`，1 处修改）

`renderKnowledgeHelpCard` 中 eyebrow 文本：

```javascript
// 修复前
"<p class='help-card-eyebrow'>现在该怎么做</p>"

// 修复后
"<p class='help-card-eyebrow'>当前状态</p>"
```

### 视觉效果变化

| 维度 | 修复前 | 修复后 |
|---|---|---|
| 卡片背景 | 深海军蓝整块底色 | 浅米色暖底 + 微绿放射光 |
| 文字色 | 白/浅色文字在深底上 | 深绿文字在浅底上（`var(--text)` / `var(--primary-strong)`） |
| Eyebrow | 冷蓝色 "现在该怎么做" | 暖绿色 "当前状态" |
| 视觉权重 | 与左侧 hero 争夺第一注意力 | 辅助性摘要，不抢左侧主引导 |
| 与页面其他 panel 关系 | 唯一使用深底的面板，突兀 | 与治理说明卡、metric card 统一暖色体系 |

## 是否改动后端

**否**。仅修改前端文件：

- `src/main/resources/static/admin/admin.css`（4 处 .help-state-card 相关规则修改）
- `src/main/resources/static/admin/modules/management-runtime-part-02.js`（1 处文案修改）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（新增 1 个测试方法 + 1 个 CSS 提取辅助方法）

后端代码（`src/main/java/**`）、数据库、API 接口、ask-runtime、compile-review-queue 均未触碰。

## 测试扩展（`ManagementJsRuntimeTests.java`）

**新增 `shouldRenderRightStatusCardAsLightSummaryNotDarkHero`**，验证：

- `.help-state-card` 基色不再包含深海军蓝/深绿/深棕/深红背景色
- 不再出现冷蓝色边框（`rgba(143, 190, 255` 等）
- 基色使用暖色边框（`rgba(67, 79, 68`）
- success / warning / danger 三个变体的第二组规则也全部转为暖色
- `.help-card-eyebrow` 不再使用冷蓝色，改为 `var(--primary)`
- part-02.js 中包含 `当前状态`，不包含 `现在该怎么做`
- 右侧区不包含左侧主引导文案（`把资料放进来`、`先导入资料`）

**新增 `extractCssBlockFrom`**：`extractCssBlock` 的 offset 版本，支持从指定位置开始搜索 CSS 块，解决同一选择器在文件中有多组规则时的精确匹配问题。

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests,com.xbk.lattice.api.admin.AdminProcessingTaskControllerTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. 工作台首页加载后，右侧状态卡为浅米色暖底，不再是深海军蓝大色块
2. 右侧卡标题为 `当前状态`（非 `现在该怎么做`）
3. 右侧卡视觉权重明显低于左侧主引导区（hero-eyebrow + hero-title + 三按钮）
4. 右侧卡与页面中其他浅色 panel（治理说明卡、metric card）保持同一暖色体系
5. 不同状态基调下卡片颜色变化正常：
   - info（初始加载）：浅米色暖底
   - success（知识库可用）：浅绿色暖底
   - warning（待处理）：浅暖橙底
   - danger：浅粉底
6. 移动端视口下右侧卡自然下折，不溢出
7. 左侧 hero 区文案、按钮、布局无回归
8. 服务健康指示器（hero-metric）区域无回归
9. 已有功能无回归：Tab 切换、资料导入、处理进度、已入库内容等
