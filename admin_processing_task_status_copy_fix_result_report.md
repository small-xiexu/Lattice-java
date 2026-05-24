# Admin 处理任务展示文案修复结果报告

- 生成时间：2026-05-22
- 执行 Agent：agentA
- 任务类型：最小展示文案修正（P1-P4，不含 P5 处理历史 Tab）
- 基干设计：`admin_processing_task_status_and_history_design_report.md`

---

## 1. 修改了哪些文件和方法

| 文件 | 变更 | 说明 |
|---|---|---|
| `CompileJobProgressMessageFormatter.java:24-36` | 修改 `format()` | 格式从 `action（cur/total）：target` → `action第 cur / total 篇文章：target` |
| `ArticleCompileSupport.java:425,442,459` | 修改 action 字符串 | "正在生成文章"→"正在生成", "正在审查文章"→"正在检查", "正在修复文章"→"正在修正" |
| `ArticlePersistSupport.java:345` | 修改 action 字符串 | "正在落库文章"→"正在写入" |
| `AdminProcessingTaskPresentationResolver.java:537-540` | 修改 `resolveProgressText()` | 去掉 `progressCurrent / progressTotal · ` 前缀，直接返回 `compactDisplayMessage(progressMessage)` |
| `AdminProcessingTaskPresentationResolver.java:567-609` | 修改 `buildReasonSummary()` | 新增 `currentStep` 参数；RUNNING 状态使用 `resolveSpecificStateLabel` 生成进行时文案；STALLED 改回"任务暂停，等待重试" |
| `AdminProcessingTaskPresentationResolver.java:826-848` | 修改 `resolveSpecificStateLabel()` | REVIEW_ARTICLES→"正在检查内容质量"，FIX_REVIEW_ISSUES→"正在根据检查结果修正内容"，CAPTURE_REPO_SNAPSHOT→"正在生成资料快照"，FINALIZE_JOB→"正在完成入库" |
| `CompileGraphLifecycleListener.java:281-302` | 修改 `touchCurrentStep()` | 新增 `resolveNodeLabel()` 映射 11 个节点名到中文进行时文案，fallback 兜底为 `"正在执行节点：" + nodeId` |
| `AdminProcessingTaskControllerTests.java` | 更新 | 测试数据与断言同步到新文案格式 |

**未修改的文件（符合禁止范围）：**
- `admin/index.html`：零修改
- `management-runtime-part-*.js`：零修改
- compile Writer/Reviewer/Fixer 主链：零修改
- Query / AnswerGeneration：零修改
- approve/reject/publish：零修改
- schema / model config：零修改
- processing-history API：零修改

---

## 2. P1 修复：进度比例重复展示

### 根因
`resolveProgressText()` 在 `CompileJobProgressMessageFormatter` 已含 `（current/total）` 的情况下，又额外拼了 `progressCurrent + " / " + progressTotal + " · "` 前缀。

### 修复
`resolveProgressText()` 去掉前缀，直接返回 `compactDisplayMessage(progressMessage)`。进度数值由进度条（`buildRunProgressStrip`）独立展示。

### 同步调整
`CompileJobProgressMessageFormatter.format()` 格式从：
```
正在修复文章（1/4）：SomeArticle
```
改为：
```
正在修正第 1 / 4 篇文章：SomeArticle
```

对应 action 字符串同步调整：生成→"正在生成"、检查→"正在检查"、修正→"正在修正"、写入→"正在写入"。

---

## 3. P2/P3 修复：运行中阶段文案不再显示最终结论

### 根因
`buildReasonSummary()` 对 RUNNING 状态直接返回 `progressMessage`，而 `progressMessage` 中包含来自 `AdminCompileReviewSummaryService.buildStepDetail()` 生成的审查结论文本（"未发现需要修复的问题"、"已根据检查结果修正内容"）。

### 修复
`buildReasonSummary()` 新增 `currentStep` 参数，RUNNING 状态时调用 `resolveSpecificStateLabel()` 生成进行时文案：

| currentStep | 旧（透传 progressMessage） | → | 新（进行时文案） |
|---|---|---|---|
| review_articles | 未发现需要修复的问题 | → | 正在检查内容质量 |
| fix_review_issues | 已根据检查结果修正内容 | → | 正在根据检查结果修正内容 |
| compile_new_articles | （进度消息） | → | 正在生成文章草稿 |
| persist_articles | （进度消息） | → | 正在写入知识库 |
| 其他 | （进度消息） | → | 对应 resolveSpecificStateLabel 映射 |

STALLED 状态同步修改为："任务暂停，等待重试"。

---

## 4. P4 修复：内部节点名中文化

### 根因
`CompileGraphLifecycleListener.touchCurrentStep()` 直接使用 `"正在执行节点：" + nodeId` 写入 step，`resolveSpecificStateLabel()` 未覆盖所有节点名。

### 修复
在 `CompileGraphLifecycleListener` 中新增 `resolveNodeLabel()` 方法，11 个节点全覆盖：

| nodeId | → | 中文文案 |
|---|---|---|
| compile_new_articles | → | 正在生成文章草稿 |
| review_articles | → | 正在检查内容质量 |
| fix_review_issues | → | 正在根据检查结果修正内容 |
| persist_articles | → | 正在写入知识库 |
| refresh_vector_index | → | 正在刷新向量索引 |
| rebuild_article_vectors | → | 正在刷新向量索引 |
| rebuild_source_vectors | → | 正在刷新向量索引 |
| rebuild_article_chunks | → | 正在重建知识切片 |
| generate_synthesis_artifacts | → | 正在整理知识库概览 |
| capture_repo_snapshot | → | 正在生成资料快照 |
| finalize_job | → | 正在完成入库 |
| 未匹配（fallback） | → | 正在执行节点：{nodeId} |

同时 `AdminProcessingTaskPresentationResolver.resolveSpecificStateLabel()` 的 REVIEW_ARTICLES/FIX_REVIEW_ISSUES/CAPTURE_REPO_SNAPSHOT/FINALIZE_JOB 文案已同步。

---

## 5. redline BLOCKER 是否仍为 0

**是。** 扫描结果：
- 总命中：2174
- BLOCKER：**0**
- REVIEW：1917
- ALLOWLIST：257

---

## 6. 测试是否通过

**是。** 全量 `mvn test`：
- Tests run: **874**
- Failures: **0**
- Errors: **0**
- Skipped: **0**

定向测试：
- `AdminProcessingTaskPresentationResolverTests`：5/5 通过
- `AdminProcessingTaskControllerTests`：5/5 通过
- `CompileGraphLifecycleListenerTests`：2/2 通过

`AdminProcessingTaskControllerTests.shouldExposeSourceSyncAndStandaloneCompileTasksWithoutDuplicatingLinkedCompileJob` 的测试数据和断言已同步更新至新文案格式。

---

## 7. 不涉及的范围（已确认零修改）

- 不碰 `admin/index.html` 和前端 JS（`management-runtime-part-*.js`）
- 不碰 compile Writer/Reviewer/Fixer 主链
- 不碰 Query / AnswerGeneration / AnswerPromptBuilder / AnswerParagraphPostProcessor
- 不碰 approve / reject / publish 流程
- 不碰 schema / model config
- 不碰 `AdminCompileReviewSummaryService.buildStepDetail()`（其 stepDetail 结论文案用于已完成阶段，语义正确；运行中阶段不再经过 reasonSummary 透出）
- 不新增 processing-history API

---

## 8. 代码改动统计

| 文件 | 行数变化 |
|---|---|
| `CompileJobProgressMessageFormatter.java` | ~6 行格式模板调整 |
| `ArticleCompileSupport.java` | 3 处 action 字符串（各 1 行） |
| `ArticlePersistSupport.java` | 1 处 action 字符串（1 行） |
| `AdminProcessingTaskPresentationResolver.java` | ~25 行（resolveProgressText 条件、buildReasonSummary 逻辑、resolveSpecificStateLabel 文案） |
| `CompileGraphLifecycleListener.java` | ~22 行（nodeId→中文映射 switch） |
| `AdminProcessingTaskControllerTests.java` | ~8 行（测试数据+断言更新） |
| **总计** | **~45 行 Java + ~8 行测试** |

---

## 9. 是否建议 agentD 做 runtime 验证

**是。** 建议验证：
1. 管理后台"当前处理任务"页：运行中任务不再出现 `1 / 4 · 正在修复文章（1/4）：...` 的重复进度
2. 运行中任务 reasonSummary 显示进行时描述（如"正在检查内容质量"），不再显示终结案（如"未发现需要修复的问题"）
3. `refresh_vector_index` 节点的中文名称在进度条和前端的显示
4. STALLED 任务的 reasonSummary 显示"任务暂停，等待重试"
