# Admin 待人工确认说明修复修订报告

- 生成时间：2026-05-22
- 执行 Agent：agentA
- 任务类型：最小 revision（恢复兼容兜底 + 修正报告矛盾）
- 上一轮报告：`admin_review_queue_issue_explanation_fix_result_report.md`

---

## 1. 修改了哪些文件

| 文件 | 变更 | 说明 |
|---|---|---|
| `compile-review-queue.js:408,412-413` | **修改 3 行** | 恢复 description 和 suggestion 的字段兜底链 |
| `admin_review_queue_issue_explanation_fix_result_report.md:28,164` | **修改 2 行** | 修正"不修改 compile-review-queue.js 之外的前端文件"的矛盾表述 |

**未修改的文件：**
- `admin.css`：本轮无变更
- `src/main/java/**`：零修改
- `src/test/java/**`：零修改

---

## 2. 是否只做最小 revision

**是。** 本轮仅做了两件事：
1. 在 `renderReviewIssue()` 中恢复了 2 条字段兜底链（共 3 行代码变更）
2. 修正了修复报告中 2 处文件范围表述不准确的地方

未改动中文化映射函数、CSS 样式、卡片结构、任何后端代码、任何测试。

---

## 3. 兼容兜底恢复详情

### 3.1 description 字段兜底

**旧代码（第一版修复前）**：
```javascript
const summary = issue.description || issue.message || issue.reason || issue.issue || "未说明的问题";
```

**第一版修复（丢失了兜底）**：
```javascript
var description = issue.description || "";
```

**本轮恢复后**：
```javascript
var description = issue.description || issue.message || issue.reason || issue.issue || "";
```

效果：当 `ReviewIssue.description` 为空时，依次回退到 `message`、`reason`、`issue` 字段。如果所有字段都为空，`summary` 显示"审查未提供详细说明"。

### 3.2 suggestion 字段兜底

**旧代码（第一版修复前）**：
```javascript
const suggestion = issue.suggestion || issue.fixSuggestion || issue.recommendation || "";
// 展示："建议：" + suggestion（可为空，为空时不渲染）
```

**第一版修复（丢失了兜底）**：
```javascript
var suggestion = mapSuggestion(category, severity);
// 总是使用映射建议，忽略了 issue 自带的 suggestion/fixSuggestion/recommendation
```

**本轮恢复后**：
```javascript
var rawSuggestion = issue.suggestion || issue.fixSuggestion || issue.recommendation || "";
var suggestion = rawSuggestion || mapSuggestion(category, severity);
```

效果：
- 优先展示 issue 自带建议（`suggestion` > `fixSuggestion` > `recommendation`）
- 当 issue 没有自带建议时，使用 `mapSuggestion(category, severity)` 映射建议
- `mapSuggestion` 永远不会返回空字符串（有最终兜底"请人工判断是否可确认入库"），所以 suggestion 始终有值

---

## 4. 此前修复是否回退

**否。** 以下修复全部保持：

| 修复项 | 状态 |
|---|---|
| `renderReviewIssues()` 使用 `.join("")` 消除孤立逗号 | 保持 |
| severity 中文化（HIGH→高风险 等） | 保持 |
| category 中文化（false_provenance→来源不一致 等） | 保持 |
| 首屏不裸露英文原始值 | 保持 |
| 原始 severity/category 仅在 `<details>` 技术信息区展示 | 保持 |
| 卡片三层结构（header + summary + suggestion + details） | 保持 |
| CSS 样式（issue-header, issue-severity 四色变体等） | 保持 |
| 确认入库、驳回按钮行为 | 不变 |
| `admin.css` 样式规则 | 不变 |

---

## 5. 报告中矛盾表述的修正

| 位置 | 修正前 | 修正后 |
|---|---|---|
| 第 5 节第 8 条 | "不修改 `compile-review-queue.js` 之外的前端文件" | "不修改 `compile-review-queue.js` 和 `admin.css`（review-queue 相关样式）之外的前端文件" |
| 第 1 节末尾 | "其他前端文件（...）：零修改"（未提及 admin.css） | 追加"本轮仅修改 `compile-review-queue.js` 和 `admin.css`" |

---

## 6. 测试结果

`ManagementJsRuntimeTests`（3 个测试）全部通过：
- `shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime`
- `shouldVerifyRunFallbackAndErrorPresentationViaNode`
- `shouldVerifyRunHelpStateAndPublishOutcomeDisplayViaNode`

未运行全量 `mvn test`，原因：本轮仅修改 3 行 JS 和 2 行报告文字，无后端变更，定向 JS 运行时测试已覆盖。

---

## 7. 是否仍建议 agentD 做 runtime 验证

**是。** 理由与上一轮相同：
1. description/suggestion 字段兜底链在真实 LLM 输出的数据上是否能正确取值
2. 卡片在真实浏览器中的渲染效果
3. 中文化映射在非标准 category 值上的兜底表现
