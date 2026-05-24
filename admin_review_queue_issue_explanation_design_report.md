# Admin 待人工确认说明结构化与中文化设计方案

- 生成时间：2026-05-22
- 执行 Agent：agentB（只读设计）
- 分支：`codex/qa-polish`
- 本轮是否修改代码：**否**

---

## 结论先行

1. **根因**：`ReviewIssue.severity` 和 `ReviewIssue.category` 是 LLM 直接输出的英文字符串，全链路零中文化映射，前端 `compile-review-queue.js` 的 `renderReviewIssue()` 只加中文标签前缀但不翻译值本身。
2. **推荐方案**：后端不改 DTO 结构，仅在前端 `compile-review-queue.js` 增加 severity/category 中文化映射函数 + 卡片分层渲染（摘要 → 展开详情 → 技术信息）。后端零改动即可解决 80% 问题。最小方案仅改动一个 JS 文件，~50 行。
3. **后端可选增强**：如需在 API 层面结构化输出，可在 `AdminCompileReviewQueueItemResponse` 增加 `List<ReviewIssueItem> resolvedIssues` 字段（预解析 `reviewIssuesJson`），前端直接消费结构化数据，避免重复解析。但这会引入 DTO 字段变更和 Controller 解析逻辑。
4. **孤立逗号**：最可能来源是 `ReviewResultParser.rescueIssues()` 的 ISSUE_PATTERN 正则捕获（见 4.3 节），次要来源可能是 CSS 伪元素。两种均可通过结构化卡片渲染解决。

---

## 1. 现状问题

基于 `phase_current_workspace_pending_fixes.md` 第 G 项和代码探查，确认以下问题：

| # | 问题 | 严重度 | 定位文件 |
|---|------|--------|---------|
| G1 | 问题类型暴露英文枚举（`false_provenance`、`value_mismatch`、`missing_referential`） | 中高 | `compile-review-queue.js:330-332` |
| G2 | 严重度暴露英文（`HIGH`、`MEDIUM`、`LOW`） | 中高 | `compile-review-queue.js:330` |
| G3 | 多条问题之间出现孤立逗号 | 中 | 见 4.3 分析 |
| G4 | 说明文本过长，缺少摘要和展开层级 | 中 | `compile-review-queue.js:322-338` |
| G5 | 缺少处理建议（确认入库 / 驳回 / 修改后再确认） | 中 | 整页无操作引导文案 |
| G6 | 技术信息与业务信息未分层（技术详情中原始 JSON 与业务展示并列） | 低 | `compile-review-queue.js:341-358` |

---

## 2. 完整数据流与字段来源

### 2.1 从 LLM 到前端展示的完整链路

```
LatticePrompts.java (prompt 定义)
  │  category 可选值（prompt 表述，非枚举）：
  │    "missing_referential|false_provenance|value_mismatch|conceptual_distortion"
  │  severity 可选值："HIGH|MEDIUM|LOW"
  │  description："问题描述（中文）"
  ▼
ArticleReviewerGateway.java (LLM 调用)
  │  发送 prompt，接收 LLM JSON 输出
  ▼
ReviewResultParser.java:233-244 (JSON 解析)
  │  readIssues(): 逐个读取 severity/category/description → 全是原始字符串
  │  category 兜底：readText(issueNode, "type", "GENERAL")
  │  severity 兜底："HIGH"
  │  非 JSON 救援路径(rescueIssues): ISSUE_PATTERN 正则提取 → category="PARSE_RESCUED"
  ▼
ReviewResult.issuesFound(issues)  →  List<ReviewIssue>
  │  ReviewIssue 三个字段：severity(String), category(String), description(String)
  ▼
CompileArticleReviewQueueService.java:138-149
  │  serializeReviewIssues(): Jackson ObjectMapper 序列化 List<ReviewIssue> → JSON 字符串
  ▼
compile_article_review_queue 表 review_issues_json 列 (jsonb)
  ▼
AdminCompileReviewQueueController.java:134-164
  │  toItemResponse(): 从 record.getReviewIssuesJson() 直接映射到 DTO，无解析无转换
  ▼
AdminCompileReviewQueueItemResponse.reviewIssuesJson (String)  —— API 响应
  ▼
compile-review-queue.js:310-319
  │  renderReviewIssues(): JSON.parse(reviewIssuesJson) → 数组
  │  renderReviewIssue(): 逐条渲染 <article> 卡片
  │  severity/category 原样展示："严重度：HIGH"、"类型：false_provenance"
```

### 2.2 关键结论

- `severity` 和 `category` **全链路无枚举约束**，LLM 输出什么就展示什么
- 整个 pipeline 中**不存在**将英文 `HIGH`/`MEDIUM`/`LOW` 或 `false_provenance` 等映射为中文的任何代码
- `ReviewIssue` 只有 3 个 String 字段（`severity`、`category`、`description`），无 `suggestion` 字段——但前端代码会检查 `issue.suggestion`（line 329），属于防御性代码

---

## 3. 孤立逗号来源排查

### 3.1 前端渲染路径（排除）

`compile-review-queue.js:317-318`：
```javascript
return "<div class='review-issue-list'>"
        + issues.map(renderReviewIssue)  // 每个 issue 生成 <article> 字符串
        + "</div>";
```

`.map()` 返回的数组在字符串拼接时隐式调用 `.join(",")`。

**这就是孤立逗号的根因！**

JavaScript 中，当数组通过 `+` 运算符与字符串拼接时，会自动调用数组的 `.toString()` 方法，而数组的 `.toString()` 等价于 `.join(",")`。

```javascript
// 实际执行：
"<div>" + ["<article>card1</article>", "<article>card2</article>"] + "</div>"
// 等价于：
"<div>" + "<article>card1</article>,<article>card2</article>" + "</div>"
// 结果：
"<div><article>card1</article>,<article>card2</article></div>"
```

这个逗号是 `<article>` 卡片之间的**裸文本节点**，浏览器会将其渲染为可见的孤立逗号。

### 3.2 后端拼接路径（排除）

- `serializeReviewIssues()` 使用 `OBJECT_MAPPER.writeValueAsString()` → 标准 JSON 数组，无逗号拼接
- `ReviewResultParser.readIssues()` → 逐个 `issues.add()`，无拼接
- `ReviewResultParser.rescueIssues()` → 逐个 `reviewIssues.add()`，无拼接

后端无字符串拼接产生逗号的路径。

### 3.3 修复方案

将 line 317-319 的隐式数组拼接改为显式 `.join("")`：

```javascript
// 当前（有 bug）：
return "<div class='review-issue-list'>"
        + issues.map(renderReviewIssue)
        + "</div>";

// 修复后：
return "<div class='review-issue-list'>"
        + issues.map(renderReviewIssue).join("")
        + "</div>";
```

**1 行修复，零后端改动。**

---

## 4. 推荐页面结构设计

### 4.1 当前展示 vs 推荐展示

**当前**（每条 issue 平铺）：
```
┌─────────────────────────────────────────┐
│ [严重度：HIGH] [类型：false_provenance]  │
│ 文章声称数据来源于xxx文档，但该文档中     │
│ 未找到对应章节...                        │
└─────────────────────────────────────────┘
```

**推荐**（三层信息架构）：

```
┌─────────────────────────────────────────┐
│ 🔴 高风险 · 来源不一致                   │
│ 文章引用的数据在源文档中未找到对应依据     │  ← 摘要（≤2 行）
│                                          │
│ 建议：核对源文档对应章节后重新确认         │  ← 处理建议
│                                          │
│ ▼ 展开详情                               │
│   完整说明：文章第三段声称数据来源于...    │  ← 可折叠
│   [技术信息] 原始类型：false_provenance   │  ← 折叠在技术信息内
└─────────────────────────────────────────┘
```

### 4.2 每条 issue 卡片展示层级

| 层级 | 内容 | 默认状态 | 数据来源 |
|------|------|---------|---------|
| **摘要行** | 风险等级图标 + 严重度中文 + 问题类型中文 | 始终可见 | severity, category 中文化 |
| **问题摘要** | description 的前 80 字（或首句） | 始终可见 | description 截断 |
| **处理建议** | 基于 category 的建议文案 | 始终可见 | 映射表（见 5.3） |
| **完整说明** | description 全文 | 默认折叠，点击展开 | description 原文 |
| **技术信息** | 原始英文 severity + category | 折叠在"技术信息"内 | severity, category 原始值 |

### 4.3 多条 issue 卡片之间使用分隔线

不使用裸逗号或依赖 CSS margin，改用 `<hr>` 或 `border-bottom` 在卡片之间产生清晰分隔。

---

## 5. 枚举中文映射表

### 5.1 严重度映射（severity）

| 英文值 | 中文展示 | 图标/颜色 | 说明 |
|--------|---------|----------|------|
| `HIGH` | 高风险 | 🔴 红色 | 建议驳回或修改后重审 |
| `MEDIUM` | 中风险 | 🟡 黄色 | 建议修改后再确认 |
| `LOW` | 低风险 | 🟢 绿色 | 可确认入库，建议后续优化 |
| 其他/未知 | 未评级 | ⚪ 灰色 | 兜底，保留原始值在技术信息中 |

### 5.2 问题类型映射（category）

| 英文值 | 中文展示 | 含义说明 |
|--------|---------|---------|
| `false_provenance` | 来源不一致 | 文章声称的来源与实际源文件不符 |
| `value_mismatch` | 数值不匹配 | 文章中的具体数值与源文件不一致 |
| `missing_referential` | 缺少引用依据 | 文章中的论断未提供可追溯的源文件引用 |
| `conceptual_distortion` | 概念偏差 | 对源文件概念的理解或转述存在偏差 |
| `hallucination` | 事实编造 | 文章包含源文件中不存在的信息 |
| `unsupported_claim` | 无依据论断 | 文章中的主张缺乏证据支撑 |
| `missing_required_content` | 缺少必要信息 | 源文件中的重要内容在文章中缺失 |
| `PARSE_RESCUED` | 解析救援 | 从非结构化输出中提取的问题（技术兜底） |
| `REWRITE_REQUIRED` | 需重写 | 审查要求重写但未返回结构化问题 |
| `REVIEW_REJECTED` | 审查不通过 | 审查未通过但未返回具体问题 |
| `GENERAL` | 其他质量问题 | 兜底类型 |
| 其他/未知 | 其他问题（原始：{原始值}） | 兜底，保留原始值 |

### 5.3 处理建议映射

| category | 处理建议文案 |
|----------|------------|
| `false_provenance` | 建议核对源文件引用路径，确认后重新审查 |
| `value_mismatch` | 建议以源文件数值为准，修正后重新审查 |
| `missing_referential` | 建议补充引用依据或标注为推断性内容 |
| `conceptual_distortion` | 建议对照源文件修正概念表述 |
| `hallucination` | 建议驳回，核实源文件中是否存在对应信息 |
| `unsupported_claim` | 建议补充证据或降低论断确定性 |
| `missing_required_content` | 建议补充缺失内容后重新审查 |
| `HIGH` 严重度（通用） | 建议驳回或修改后重新审查 |
| `MEDIUM` 严重度（通用） | 建议修改后再确认 |
| `LOW` 严重度（通用） | 可确认入库，建议后续优化 |
| 其他/兜底 | 请人工判断是否可确认入库 |

处理建议优先级：category 映射 > severity 通用映射 > 兜底文案。

---

## 6. 最小实施方案

### 6.1 方案 A：纯前端最小改动（推荐优先实施）

**改动范围**：仅 `compile-review-queue.js`

| 改动项 | 位置 | 行数 |
|--------|------|------|
| 修复孤立逗号（`.join("")`) | `renderReviewIssues()` line 317-319 | 1 行 |
| 新增 `mapSeverity(severity)` 函数 | 新函数 | ~10 行 |
| 新增 `mapCategory(category)` 函数 | 新函数 | ~15 行 |
| 新增 `mapSuggestion(category, severity)` 函数 | 新函数 | ~15 行 |
| 改造 `renderReviewIssue()` 卡片结构 | 函数体内 | ~25 行 |
| **总计** | **1 个文件** | **~65 行 JS** |

**不应修改的文件**：
- `ReviewIssue.java`：不需要添加 suggestion 字段（前端映射即可）
- `AdminCompileReviewQueueItemResponse.java`：不需要新增结构化字段
- `AdminCompileReviewQueueController.java`：不需要解析 reviewIssuesJson
- `LatticePrompts.java`：不修改 prompt 中的 category 枚举值
- `ReviewResultParser.java`：不修改解析逻辑
- 所有 Java 后端文件：零改动

**优点**：
- 改动面积极小
- 后端零风险
- 回归测试仅需人工检查前端展示
- 映射表集中在前端，后续调整无需重启后端

**缺点**：
- 如果将来有多个前端消费同一个 API（如移动端），需要各自实现映射
- severity/category 的英文原始值仍保留在后端存储中（这是正确的，存储应保留原始值）

### 6.2 方案 B：后端 + 前端（后续可选增强）

如果后续有多个前端消费者或需要 API 层面就提供中文展示：

**后端改动**（`AdminCompileReviewQueueController.java`）：
- `toItemResponse()` 中解析 `reviewIssuesJson`，为每条 issue 附带 `severityLabel`、`categoryLabel`、`suggestion` 字段

**DTO 改动**（`AdminCompileReviewQueueItemResponse.java`）：
- 新增 `List<ResolvedReviewIssue> resolvedIssues` 字段
- `ResolvedReviewIssue` 为静态内部类：`severity`, `severityLabel`, `category`, `categoryLabel`, `description`, `suggestion`

**前端改动**：
- 优先消费 `resolvedIssues`，fallback 回 `reviewIssuesJson`

### 6.3 推荐路径

**先实施方案 A，验证前端展示效果满意后再决定是否需要方案 B。**

---

## 7. 是否保留原始英文类型

**保留。** 但不应在业务展示区首屏出现。

| 英文原始值 | 保留位置 | 展示方式 |
|-----------|---------|---------|
| `severity`（如 `HIGH`） | "技术详情"折叠区 | `严重度（原始）：HIGH` |
| `category`（如 `false_provenance`） | "技术详情"折叠区 | `问题类型（原始）：false_provenance` |
| 完整 `reviewIssuesJson` | "技术详情" → "结构化问题" | 已有，维持 `<pre>` 展示 |

理由：技术人员在调试时需要查看 LLM 原始输出，原始枚举值有助于排查 prompt 问题和模型行为。

---

## 8. 详细改造示例（renderReviewIssue 伪代码）

```
function renderReviewIssue(issue) {
    // 1. 处理非标准格式
    if (!issue || typeof issue !== "object") { ... 兜底 }

    // 2. 提取字段
    const severity = issue.severity || "";
    const category = issue.category || "";
    const description = issue.description || "";

    // 3. 中文化映射
    const severityLabel = mapSeverity(severity);       // "高风险" / "中风险" / "低风险"
    const severityIcon = mapSeverityIcon(severity);    // "🔴" / "🟡" / "🟢"
    const categoryLabel = mapCategory(category);       // "来源不一致" / "数值不匹配" / ...
    const suggestion = mapSuggestion(category, severity);

    // 4. 摘要截断
    const summary = description.length > 80
        ? description.substring(0, 80) + "…"
        : description;

    // 5. 卡片结构（三层）
    return "<article class='review-issue-card'>"
        // 摘要层（始终可见）
        + "<div class='issue-header'>"
        +   "<span class='issue-severity " + severityClass + "'>" + severityIcon + " " + severityLabel + "</span>"
        +   "<span class='issue-category'>" + categoryLabel + "</span>"
        + "</div>"
        + "<p class='issue-summary'>" + escapeHtml(summary) + "</p>"
        + (suggestion ? "<p class='issue-suggestion'>" + escapeHtml(suggestion) + "</p>" : "")
        // 展开层（默认折叠）
        + "<details class='issue-detail'>"
        +   "<summary>展开详情</summary>"
        +   "<p>" + escapeHtml(description) + "</p>"
        // 技术信息（在展开层内）
        +   "<div class='issue-technical'>"
        +     "<span>原始严重度：" + escapeHtml(severity) + "</span>"
        +     "<span>原始类型：" + escapeHtml(category) + "</span>"
        +   "</div>"
        + "</details>"
        + "</article>";
}
```

---

## 9. 风险与测试建议

### 9.1 风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| LLM 输出非标准 category 值（不在映射表中） | 低 | 映射函数兜底返回 `"其他问题（原始：{值}）"` |
| LLM 输出非标准 severity 值 | 低 | 映射函数兜底返回 `"未评级"` + 灰色图标 |
| `description` 为空 | 低 | 兜底文案："审查未提供详细说明" |
| `reviewIssuesJson` 不是有效 JSON 数组 | 低 | 已有兜底："未返回结构化问题详情"，本次不改 |
| 截断摘要时破坏 HTML 实体 | 低 | 截断前先 `escapeHtml`，或截断纯文本再 escape |
| `parseJsonValue` 返回非数组（如单个对象） | 低 | 当前已判断 `Array.isArray`，非数组走兜底 |

### 9.2 测试建议

**人工验收（无需自动化）**：
1. 打开管理后台 → 知识运行 → 待人工确认区域
2. 确认每条 issue 卡片显示中文严重度和中文类型（不再出现 `HIGH`/`false_provenance` 等英文裸值）
3. 确认多条 issue 之间不再出现孤立逗号
4. 确认"展开详情"可以折叠/展开完整说明
5. 确认"技术详情"区域内仍保留原始英文值
6. 确认处理建议文案与 issue 类型匹配
7. 确认严重度的颜色/图标正确区分（红/黄/绿）

**边界测试**：
- 找一个没有任何 issue 的待确认项（如存在）→ 确认兜底文案
- 找一个 issue JSON 解析失败的场景（如存在）→ 确认降级展示

### 9.3 不应做的事

- 不修改 `ReviewIssue.java` 的结构（不新增字段）
- 不修改 `AdminCompileReviewQueueItemResponse.java`（方案 A 无需后端改动）
- 不修改 LLM prompt（`LatticePrompts.java`）中的 category 枚举值
- 不新增批量确认/批量驳回功能
- 不修改 approve/reject 的接口逻辑
- 不修改 `ReviewResultParser.java` 的解析行为
- 不修改 `compile-review-queue.js` 之外的前端文件

---

## 10. 本轮确认

- **是否修改了 `src/main/java/**`**：否
- **是否修改了 `src/main/resources/static/**`**：否
- **是否修改了任何配置/文档/脚本**：否
- **是否提交了任何代码**：否
- **仅执行**：代码探查、数据流追踪、孤立逗号根因定位、展示层级设计、枚举映射表设计、改动范围评估
