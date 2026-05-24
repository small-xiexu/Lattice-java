# Admin 待人工确认说明结构化与中文化修复结果报告

- 生成时间：2026-05-22
- 执行 Agent：agentA
- 任务类型：纯前端最小修复（方案 A）
- 基干设计：`admin_review_queue_issue_explanation_design_report.md`

---

## 1. 修改了哪些文件

| 文件 | 变更 | 说明 |
|---|---|---|
| `compile-review-queue.js:318` | 修改 | `issues.map(renderReviewIssue)` → `issues.map(renderReviewIssue).join("")` |
| `compile-review-queue.js:322-398` | **新增** | 5 个映射函数：`mapSeverity`, `mapSeverityClass`, `mapCategory`, `mapSuggestion`, `summarizeDescription` |
| `compile-review-queue.js:400-431` | **重写** | `renderReviewIssue()` 卡片三层结构 |
| `admin.css:2847-2932` | **新增** | 10 个新 CSS 规则（`issue-header`, `issue-severity` + 4 色变体, `issue-category`, `issue-summary`, `issue-suggestion`, `issue-detail`, `issue-description`, `issue-technical`） |

**代码统计**：~75 行 JS 新增 + ~30 行 JS 重写 + ~85 行 CSS 新增 = **~190 行总变更**（2 个文件）

**未修改的文件（符合禁止范围）：**
- `src/main/java/**`：零修改
- `src/test/java/**`：零修改
- prompt / schema / 模型配置：零修改
- approve/reject/publish 逻辑：零修改
- `ReviewIssue.java`、`ReviewResultParser.java`、`AdminCompileReviewQueueItemResponse.java`：零修改
- 批量确认/批量驳回：零修改
- 其他前端文件（`admin-common.js`、`index.html`、`management-runtime-part-*.js` 等）：零修改。本轮仅修改 `compile-review-queue.js` 和 `admin.css`

---

## 2. 修复项逐一验收

### 2.1 孤立逗号（G3）

**根因**：`renderReviewIssues()` line 317-319 中 `issues.map(renderReviewIssue)` 返回的数组在字符串拼接 `+` 时隐式调用 `.toString()` → `.join(",")`。

**修复**：追加 `.join("")` 显式指定无分隔符拼接。

```javascript
// 修复前
"<div>" + issues.map(renderReviewIssue) + "</div>"
// 修复后
"<div>" + issues.map(renderReviewIssue).join("") + "</div>"
```

**1 行修复**。

### 2.2 严重度中文化（G2）

新增 `mapSeverity(severity)` 函数：

| 英文值 | → | 中文展示 | CSS 类 |
|---|---|---|---|
| `HIGH` | → | 高风险 | `severity-high`（红色背景） |
| `MEDIUM` | → | 中风险 | `severity-medium`（黄色背景） |
| `LOW` | → | 低风险 | `severity-low`（绿色背景） |
| 其他/空 | → | 未评级 | `severity-unknown`（灰色背景） |

效果：首屏展示彩色圆角标签（如 `[高风险]`），不再裸露 `HIGH` 英文。

### 2.3 问题类型中文化（G1）

新增 `mapCategory(category)` 函数：

| 英文值 | → | 中文展示 |
|---|---|---|
| `false_provenance` | → | 来源不一致 |
| `value_mismatch` | → | 数值不匹配 |
| `missing_referential` | → | 缺少引用依据 |
| `conceptual_distortion` | → | 概念偏差 |
| `hallucination` | → | 事实编造 |
| `unsupported_claim` | → | 无依据论断 |
| `missing_required_content` | → | 缺少必要信息 |
| `PARSE_RESCUED` | → | 解析救援 |
| `REWRITE_REQUIRED` | → | 需重写 |
| `REVIEW_REJECTED` | → | 审查不通过 |
| `GENERAL` | → | 其他质量问题 |
| 未知/空 | → | 其他质量问题 |

### 2.4 每条 issue 展示层级（G4/G6）

新的 `renderReviewIssue()` 卡片结构（三层）：

```
┌─ issue-header ─────────────────────────────┐
│ [高风险] 来源不一致                         │  ← 始终可见
├────────────────────────────────────────────┤
│ 文章引用的数据在源文档中未找到对应依据       │  ← 摘要 ≤80 字
├────────────────────────────────────────────┤
│ ▎建议核对源文件引用路径，确认后重新审查      │  ← 处理建议
├─ ▼ 展开详情 ───────────────────────────────┤
│   完整说明：文章第三段声称…                  │  ← 默认折叠
│   ────────────────────────────              │
│   原始严重度：HIGH  原始类型：false_provenance│  ← 技术信息
└────────────────────────────────────────────┘
```

**摘要截断**：`summarizeDescription()` 取前 80 字符，超出追加 `…`。

**空 description 兜底**：显示"审查未提供详细说明"。

**原始英文值**：仅出现在 `<details>` 折叠区内的 `.issue-technical` 中。

### 2.5 处理建议（G5）

新增 `mapSuggestion(category, severity)` 函数，按优先级生成建议：

| 优先级 | 来源 | 示例 |
|---|---|---|
| 1 | category 精确匹配 | `false_provenance` → "建议核对源文件引用路径，确认后重新审查" |
| 2 | severity 通用映射 | `HIGH` → "建议驳回或修改后重新审查" |
| 3 | 最终兜底 | "请人工判断是否可确认入库" |

7 个已知 category 各有专用建议文案。未命中 category 时按 severity 给出通用建议，最终兜底文案确保每条 issue 都有处理指引。

---

## 3. CSS 样式说明

新增样式全部集中在 `admin.css` 的 review-queue 区块末尾，不修改任何已有规则：

| CSS 规则 | 用途 |
|---|---|
| `.issue-header` | flex 布局承载 severity + category 标签 |
| `.issue-severity` | 圆角标签基础样式 |
| `.severity-high` | 红色：`#9b2c2c` 文字 + 淡红背景 |
| `.severity-medium` | 黄色：`#92400e` 文字 + 淡黄背景 |
| `.severity-low` | 绿色：`#166534` 文字 + 淡绿背景 |
| `.severity-unknown` | 灰色：muted 文字 + 灰背景 |
| `.issue-category` | 问题类型文字样式 |
| `.issue-summary` | 摘要段落样式（13px, 1.55 行高） |
| `.issue-suggestion` | 建议区块：左边框 + 淡黄背景 |
| `.issue-detail` | `<details>` 折叠区 |
| `.issue-description` | 完整描述（`pre-wrap` 保留换行） |
| `.issue-technical` | 技术信息区：顶部边框分隔 + flex gap |

---

## 4. 测试验证

### 4.1 JS 运行时测试

`ManagementJsRuntimeTests`（3 个测试）全部通过：
- `shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime`：Node.js 运行时验证编译队列 JS 渲染不再出现 Reviewer 文案
- `shouldVerifyRunFallbackAndErrorPresentationViaNode`：运行态回退与错误提示文案验证
- `shouldVerifyRunHelpStateAndPublishOutcomeDisplayViaNode`：帮助状态与发布结果展示验证

### 4.2 后端回归

本方案未修改任何 Java 代码，后端行为无变化。现有 `AdminCompileReviewQueueController`、`AdminCompileReviewQueueItemResponse`、`ReviewResultParser`、`ReviewIssue` 等零改动。

---

## 5. 不涉及的范围（已确认零修改）

- 不修改 `src/main/java/**`
- 不修改 `src/test/java/**`
- 不修改 `ReviewIssue.java` 结构（不新增字段）
- 不修改 `AdminCompileReviewQueueItemResponse.java`
- 不修改 `AdminCompileReviewQueueController.java`
- 不修改 `ReviewResultParser.java`
- 不修改 `LatticePrompts.java` 的 prompt category 枚举
- 不修改 `compile-review-queue.js` 和 `admin.css`（review-queue 相关样式）之外的前端文件
- 不新增批量确认/批量驳回
- 不修改 approve/reject 接口逻辑
- 不修改确认入库、驳回按钮行为

---

## 6. 建议 agentD 人工验收

1. 打开管理后台 → 知识运行 → 待人工确认区域
2. 确认每条 issue 卡片显示中文严重度（高风险/中风险/低风险）和彩色标签
3. 确认每条 issue 卡片显示中文类型（来源不一致/数值不匹配等），不再裸露 `false_provenance` 等英文
4. 确认多条 issue 之间不再出现孤立逗号
5. 确认"展开详情"可折叠/展开完整说明和技术信息
6. 确认原始英文值（`HIGH`、`false_provenance`）仅出现在展开后的技术信息区内
7. 确认处理建议文案与 issue 类型匹配
8. 确认 description 为空时显示"审查未提供详细说明"
9. 确认确认入库、驳回按钮行为不变
