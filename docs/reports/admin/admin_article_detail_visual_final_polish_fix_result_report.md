# admin 文章详情页视觉打磨最终修复报告

**日期**: 2026-05-23

**分支**: `codex/qa-polish`

**上一轮报告**: `admin_article_detail_layout_visual_polish_fix_revision_report.md`

## 测试结论

全部 6 个测试用例通过，0 失败 0 错误 0 跳过。

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- 原有 5 个用例通过
- 新增 `shouldUseUnifiedDevDiagnosticCopyInDescriptionListEmptyState` 通过

## 上一轮报告为何通过但页面仍有冷蓝残留

上一轮 `extractCssBlock` 通过 `indexOf` 提取每个选择器的**第一个** CSS 规则块，三个 toggle 块（`.article-metadata-toggle`、`.article-relations-toggle`、`.article-keyword-toggle`）的背景和边框确实已改为暖色。但以下两个冷蓝残留点在上一轮未被测试覆盖：

1. **`.article-relations-toggle` 的 `color` 属性使用了 `var(--primary-strong)`**

   `:root` 中 `--primary-strong` 的值为 `#4aaef7`（冷蓝），Starbucks polish 段重新定义了 `:root` 中的 `--text`、`--muted`、`--muted-strong`、`--border`、`--border-strong`、`--shadow`、`--shadow-soft`，但**没有覆盖 `--primary` 和 `--primary-strong`**。因此 `.article-relations-toggle { color: var(--primary-strong); }` 渲染出来的文字颜色仍然是 `#4aaef7`（冷蓝）。此外，由于 `<summary>` 继承父 `<details>` 的颜色，`.article-relations-toggle` 内的 `<summary>` 文字也是冷蓝。

2. **`.article-metadata-toggle .code-view` 没有显式背景**

   该规则只设置了 `margin`、`max-height`、`overflow`，没有设置 `background`。浏览器会回退到通用的 `.code-view` 规则。由于暗色主题的 `.code-view` 定义在文件较后位置（line ~2593），其 `background: rgba(8, 15, 29, 0.86)` 会覆盖前面 Starbucks polish 段的暖色定义，导致开发诊断信息折叠区内的 JSON 代码块仍为深蓝底色。

## 实际改动

### 1. `.article-relations-toggle` 冷蓝文字修复（`admin.css`）

| 变更 | 旧值 | 新值 |
|---|---|---|
| `.article-relations-toggle` color | `var(--primary-strong)` → `#4aaef7`（冷蓝） | `#1e3928`（深绿） |
| `.article-relations-toggle summary` color | 继承自父级 `var(--primary-strong)`（冷蓝） | `#1e3928`（深绿，新增显式规则） |

### 2. `.article-metadata-toggle .code-view` 显式暖色背景（`admin.css`）

| 属性 | 新值 |
|---|---|
| `border-color` | `rgba(30, 57, 50, 0.16)` |
| `background` | `linear-gradient(180deg, rgba(41, 67, 56, 0.98), rgba(29, 49, 40, 0.99))` |
| `color` | `#f8f1e7` |

此修改与页面其余 `.code-view` 暖色定义（Starbucks polish 段 line 349-352）保持一致，通过更高优先级的选择器显式覆盖，不再回退到暗色 `.code-view`。

### 3. 空态文案统一（`management-runtime-part-04.js`）

| 函数 | 旧文案 | 新文案 |
|---|---|---|
| `renderDescriptionList()` 空态 | `暂无技术信息` | `暂无开发诊断信息` |

页面中涉及开发诊断元数据的空态表述已全部统一为"开发诊断信息"命名，不再混用"技术信息"。

### 4. 测试补充（`ManagementJsRuntimeTests.java`）

**扩展 `shouldNotUseColdBlueBackgroundInArticleDetailToggles`**：

- 新增断言：`.article-metadata-toggle .code-view` 规则块包含 `linear-gradient` 和 `rgba(41, 67, 56`（暖色深绿），不含 `rgba(8, 15, 29`（旧暗色底）
- 新增断言：`.article-relations-toggle` 规则块不含 `var(--primary-strong)`（不再间接引用冷蓝）

**新增 `shouldUseUnifiedDevDiagnosticCopyInDescriptionListEmptyState`**：

- 验证 `management-runtime-part-04.js` 中不含 `暂无技术信息`
- 验证 `management-runtime-part-04.js` 中包含 `暂无开发诊断信息`

## 是否改动后端

**否**。本次仅修改前端文件：

- `src/main/resources/static/admin/admin.css`（3 处 CSS 规则修改/新增）
- `src/main/resources/static/admin/modules/management-runtime-part-04.js`（1 处字符串替换）
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`（扩展 1 个已有测试 + 新增 1 个测试方法）

后端代码（`src/main/java/**`）、数据库、API 接口均未触碰。

## 测试命令

```
mvn test -pl . -Dtest="com.xbk.lattice.api.admin.ManagementJsRuntimeTests" -Dsurefire.failIfNoSpecifiedTests=false
```

## 仍需用户浏览器肉眼验收的项目

1. `.article-relations-toggle` 按钮文字不再是冷蓝色 `#4aaef7`，而是深绿色
2. "开发诊断信息"折叠区展开后，JSON 元数据的 `<pre class="code-view">` 背景为深绿暖色，不再是暗蓝底
3. 当文章详情页技术字段为空时，"开发诊断信息"折叠区内不再出现"暂无技术信息"，改为"暂无开发诊断信息"
4. `.article-metadata-toggle`、`.article-keyword-toggle` 的背景、边框仍保持浅暖色体系，无回归
5. 复核历史 compact timeline、风险提示、正文等区域不受影响
6. 问答页行为无回归
