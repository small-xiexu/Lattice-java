# Admin 当前处理任务展示语义与历史入口设计方案

- 生成时间：2026-05-22
- 执行 Agent：agentB（只读设计）
- 分支：`codex/qa-polish`
- 本轮是否修改代码：**否**

---

## 1. 当前问题清单

基于 `phase_current_workspace_pending_fixes.md` 第 A 项，经代码探查确认如下：

| # | 问题 | 严重度 | 确认状态 |
|---|------|--------|---------|
| P1 | 进度比例重复展示（`1 / 4 · 正在修复文章（1/4）：...`） | 高 | **已定位根因** |
| P2 | 运行中阶段副文案像最终结论（"未发现需要修复的问题"、"已根据检查结果修正内容"） | 高 | **已定位根因** |
| P3 | 阶段文案与当前动作冲突（阶段卡显示"已修正"，但进度仍在审查） | 高 | P2 的衍生表现 |
| P4 | 内部节点名直出（`refresh_vector_index`） | 中 | **已定位根因** |
| P5 | 完成任务历史入口不清晰 | 高 | **已确认缺失** |

---

## 2. 根因定位

### 2.1 P1 — 进度比例重复

**根因文件**：`AdminProcessingTaskPresentationResolver.java:531-554` (`resolveProgressText()`)

```
当前逻辑：
  progressMessage = "正在修复文章（1/4）：SomeArticle"   ← CompileJobProgressMessageFormatter 已含 (current/total)
  resolveProgressText 再拼: progressCurrent + " / " + progressTotal + " · " + progressMessage
  最终产出: "1 / 4 · 正在修复文章（1/4）：SomeArticle"
```

`CompileJobProgressMessageFormatter` 格式为 `action + "（" + current + "/" + total + "）：" + targetLabel`，而 `resolveProgressText` 在拼接时又额外加了 `progressCurrent + " / " + progressTotal + " · "`，造成双重进度。

**修复方向**：`resolveProgressText` 只拼 `progressMessage`，不再追加 `progressCurrent / progressTotal` 前缀。由于进度条（`buildRunProgressStrip()`）已独立展示 4 段进度链，顶部无需再重复数值。

### 2.2 P2 — 运行中显示最终结论

**根因有两处**：

**根因 1**：`AdminProcessingTaskPresentationResolver.java:567-607` (`buildReasonSummary()`)

```java
// RUNNING 状态时：
if (status == RUNNING || status == STALLED) {
    return progressMessage;  // ← 直接把进度消息当"原因摘要"展示
}
```

`progressMessage` 中包含来自 `AdminCompileReviewSummaryService.buildStepDetail()` 生成的审查结论文本（"未发现需要修复的问题"、"已根据检查结果修正内容"），这些文本本应是**阶段完成后**的结论，但在运行中就被注入 step detail，再经 `buildReasonSummary()` 透出到前端卡片副文案。

**根因 2**：`AdminCompileReviewSummaryService.buildStepDetail()` 在 `review_articles` 或 `fix_review_issues` 节点**执行期间**就写入了 stepDetail。该方法的调用时机是 Graph 节点完成时（`GraphLifecycleListener.after()`），但写入的文本语义是"审查完成后的结论"，而执行期间 `buildReasonSummary()` 把它当作当前状态描述展示。

**修复方向**：`buildReasonSummary()` 对 RUNNING 状态不使用 `progressMessage` 作为 reason summary，改为根据 `currentStep` 生成进行时描述。

### 2.3 P3 — 阶段文案与当前动作冲突

这是 P2 的衍生表现。阶段卡（`buildProgressSteps()` 产出的 4 段进度链）展示的是 stepDetail（已完成的阶段结论），但当前进度仍在执行后续阶段。例如：`review_articles` 完成时 stepDetail 写入"已根据检查结果修正内容"，阶段卡显示为完成态（✅），但当前进度条实际在 `fix_review_issues`。

**修复方向**：stepDetail 仅用于已完成阶段的展示；运行中阶段的 stepDetail 应为进行时描述，而非完成时结论。

### 2.4 P4 — 内部节点名直出

**根因文件**：`CompileJobLeaseManager.touchCurrentStep()` 调用方 `CompileGraphLifecycleListener.before()`

```java
// 当进入 refresh_vector_index 节点时：
compileJobLeaseManager.touchCurrentStep(jobId, "refresh_vector_index", "正在执行节点：refresh_vector_index");
```

`progressMessage` 直接使用节点枚举名，`resolveSpecificStateLabel()` 未覆盖 `refresh_vector_index` → 前端展示英文节点名。

**修复方向**：`resolveSpecificStateLabel()` 已覆盖大部分节点，但有两类漏网：
1. 直接通过 `touchCurrentStep` 写入的非标准节点名（LifecycleListener 中 `before()` 写入的 `"正在执行节点：" + nodeId`）
2. 如 `resolveSpecificStateLabel` 已覆盖但调用链未走该方法的情况

最干净的方案是在 `CompileGraphLifecycleListener.before()` 中将 nodeId 映射为中文后再调用 `touchCurrentStep`，而不是依赖后端的兜底映射。

### 2.5 P5 — 处理历史入口缺失

**现状**：
- `admin/index.html` 有 `data-tab-panel="knowledge-runs"` 作为"当前处理任务"页
- 顶部 tab trigger 列表中有 `data-tab-trigger="knowledge-runs"`（"知识运行"）
- 无独立的"处理历史"tab
- 现有 API：`GET /api/v1/admin/processing-tasks?limit=50`（混合返回活跃+历史任务）
- `GET /api/v1/admin/sources/{sourceId}/processing-tasks` 提供 per-source 历史

当前页面说明文字："完整历史放在对应资料源里查看"——但用户找不到这个入口。

---

## 3. 展示文案映射设计

### 3.1 节点名 → 中文进行时（运行中阶段副文案）

| 内部节点 | 当前展示 | → | 设计文案（进行时） |
|---------|---------|---|-----------------|
| `compile_new_articles` | 正在生成文章草稿 | → | **正在生成文章草稿**（维持） |
| `review_articles` | 正在审查文章草稿 | → | **正在检查内容质量** |
| `fix_review_issues` | 正在修复审查问题 | → | **正在根据检查结果修正内容** |
| `persist_articles` | 正在写入知识库 | → | **正在写入知识库**（维持） |
| `refresh_vector_index` | `refresh_vector_index`（英文直出） | → | **正在刷新向量索引** |
| `rebuild_article_vectors` | 正在刷新向量索引 | → | **正在刷新向量索引**（维持） |
| `rebuild_source_vectors` | 正在刷新向量索引 | → | **正在刷新向量索引**（维持） |
| `rebuild_article_chunks` | 正在重建知识切片 | → | **正在重建知识切片**（维持） |
| `generate_synthesis_artifacts` | 正在整理知识库概览 | → | **正在整理知识库概览**（维持） |
| `capture_repo_snapshot` | 入库完成 | → | **正在生成资料快照**（进行时，与完成时区分） |
| `finalize_job` | 入库完成 | → | **正在完成入库**（进行时，与完成时区分） |

### 3.2 阶段完成时结论文案

| 阶段 | 进行时 | → | 完成时结论 |
|------|--------|---|-----------|
| 内容生成 | 正在生成文章草稿 | → | 文章草稿生成完成 |
| 质量检查（无问题） | 正在检查内容质量 | → | **质量检查完成，未发现需要修复的问题** |
| 质量检查（已修正） | 正在修正内容 | → | **质量检查完成，内容已修正** |
| 质量检查（待确认） | 正在检查内容质量 | → | **质量检查完成，需人工确认后发布** |
| 写入知识库 | 正在写入知识库 | → | 知识库写入完成 |
| 刷新向量 | 正在刷新向量索引 | → | 向量索引刷新完成 |
| 最终入库 | 正在完成入库 | → | 入库完成 |

### 3.3 进度消息格式调整

**当前格式**（`CompileJobProgressMessageFormatter`）：
```
正在修复文章（1/4）：SomeArticle
```

**调整后格式**：
```
正在修正第 1 / 4 篇文章：SomeArticle
```

同步调整：
- `正在审查文章（3/5）：xxx` → `正在检查第 3 / 5 篇文章：xxx`
- `正在生成文章草稿（2/5）：xxx` → `正在生成第 2 / 5 篇文章：xxx`

`resolveProgressText()` **去掉** `progressCurrent + " / " + progressTotal + " · "` 前缀，直接返回 `progressMessage`。进度数值已由进度条（`buildRunProgressStrip`）独立展示。

### 3.4 卡片副文案（reasonSummary）设计

| displayStatus | 当前问题 | → | 设计方案 |
|--------------|---------|---|---------|
| RUNNING | 透出 progressMessage（可能含阶段结论） | → | 根据 currentStep 生成进行时描述，不展示阶段结论 |
| RUNNING + review_articles | "未发现需要修复的问题"（像结论） | → | "正在检查内容质量" |
| RUNNING + fix_review_issues | "已根据检查结果修正内容"（像结论） | → | "正在根据检查结果修正内容" |
| STALLED | 同 RUNNING | → | "任务暂停，等待重试" |
| SUCCEEDED | 正常 | → | 维持（展示最终结论） |
| FAILED | 正常 | → | 维持（展示失败原因） |

---

## 4. 处理历史入口设计

### 4.1 方案对比

| 维度 | 方案 A：页面底部加"最近完成" | 方案 B：新增独立"处理历史"Tab |
|------|---------------------------|---------------------------|
| 入口发现性 | 低（需滚动到底部） | **高（顶部一级 Tab）** |
| 信息承载量 | 受限（与活跃任务共用页面高度） | **充裕**（独立页面，可带搜索/筛选） |
| 与现有架构契合度 | 改动小 | 符合现有 Tab 模式（已有 知识运行/用户反馈 等 Tab） |
| 长期可维护性 | 差（页面职责混杂） | **好**（关注点分离） |
| 实现工作量 | 前端 ~1d | 前端 ~2d + 后端 ~1d |
| 推荐度 | 临时方案 | **正式方案** |

### 4.2 推荐：方案 B — 新增"处理历史"Tab

**理由**：
1. 现有 Tab 架构已成熟（`data-tab-panel` + `data-tab-trigger`），新增 Tab 改造成本可控
2. "当前处理任务"专注需要关注的任务（运行中/失败/待确认），"处理历史"专注已完成/已入库/已驳回
3. 长期不会因页面信息混杂而需要二次改造

### 4.3 "当前处理任务"页职责收缩

| 展示内容 | 当前 | → | 调整后 |
|---------|------|---|-------|
| 运行中任务 | ✅ | → | ✅ 维持 |
| 失败任务 | ✅ | → | ✅ 维持 |
| 待人工确认 | ✅ | → | ✅ 维持 |
| 排队中任务 | ✅ | → | ✅ 维持 |
| 已完成任务 | ✅ | → | ❌ **移入"处理历史"** |
| 已驳回任务 | ✅ | → | ❌ **移入"处理历史"** |

### 4.4 "处理历史"Tab 内容设计

**列表项**：
- 资料名
- 提交时间
- 最终结果（已入库 / 已驳回 / 失败 / 已取消）
- 处理耗时
- 文章数（生成 N 篇 / 入库 M 篇）
- 操作：查看详情

**筛选/排序**：
- 按结果筛选：全部 / 已入库 / 已驳回 / 失败
- 按时间排序：默认最新在前
- 搜索：按资料名搜索

**详情入口**：
- 点击列表项 → 展开/跳转处理详情（复用现有 job detail 组件或 modal）

### 4.5 是否需要新增后端 API

**建议新增一个轻量 API**：

```
GET /api/v1/admin/processing-history?status=succeeded,rejected,failed&page=0&size=20&keyword=
```

与现有 `GET /api/v1/admin/processing-tasks` 的区别：
- `processing-tasks`：返回所有非终态任务（RUNNING/STALLED/QUEUED/WAIT_CONFIRM）+ 最近 N 条终态任务
- `processing-history`：仅返回终态任务（SUCCEEDED/FAILED/REJECTED/SKIPPED_NO_CHANGE），支持分页和关键词搜索

如果不想新增 API，可以在现有 `processing-tasks` 上加 `status` 过滤参数，前端分别调用：
- 当前处理：`GET /api/v1/admin/processing-tasks?status=running,stalled,queued,wait_confirm`
- 处理历史：`GET /api/v1/admin/processing-tasks?status=succeeded,failed,rejected&page=0&size=20`

**推荐方案**：在现有 API 加 `status` 过滤参数，避免新建端点。工作量更小，且过滤参数本身也是合理的 API 演进。

---

## 5. 后端改动范围

### 5.1 `AdminProcessingTaskPresentationResolver.java`

| 方法 | 改动 | 说明 |
|------|------|------|
| `resolveProgressText()` | 去掉 `progressCurrent / progressTotal` 前缀 | 修复 P1 |
| `buildReasonSummary()` | RUNNING/STALLED 时根据 currentStep 生成进行时文案 | 修复 P2/P3 |
| `resolveSpecificStateLabel()` | 补充 `capture_repo_snapshot`/`finalize_job` 的进行时文案 | 修复 P4 兜底 |
| `buildStepDetail()` (如有) | 确保进行时/完成时文案分离 | 修复 P3 |

### 5.2 `CompileJobProgressMessageFormatter.java`

| 方法 | 改动 | 说明 |
|------|------|------|
| `format()` | 格式从 `action（current/total）：target` 改为 `action第 current / total 篇文章：target` | 文案调整 |

### 5.3 `CompileGraphLifecycleListener.java`

| 方法 | 改动 | 说明 |
|------|------|------|
| `before()` | `"正在执行节点：" + nodeId` → 使用中文映射 | 修复 P4 根因 |

### 5.4 `AdminCompileReviewSummaryService.java`

| 方法 | 改动 | 说明 |
|------|------|------|
| `buildStepDetail()` | 增加 `isRunning` 参数区分进行时/完成时文案 | 修复 P2/P3 |

### 5.5 `AdminProcessingTaskController.java`（估计）

| 方法 | 改动 | 说明 |
|------|------|------|
| `getProcessingTasks()` | 增加可选的 `status` 过滤参数 | 支持方案 B 历史 API |

### 5.6 前端文件

| 文件 | 改动 | 说明 |
|------|------|------|
| `admin/index.html` | 新增 `data-tab-panel="processing-history"` Tab | 方案 B 入口 |
| `management-runtime-part-03.js` | `buildRunOperationalNote()` 调整副文案渲染 | 修复 P2/P3 |
| `management-runtime-part-01.js` | 新增 `loadProcessingHistory()` 方法 | 历史列表加载 |
| 新文件 `management-history-part-01.js` | 处理历史列表渲染 | 方案 B 主体 |

---

## 6. 不涉及的范围（明确排除）

- 不碰文章详情页关键词折叠（属于 `admin_article_detail_keyword_metadata_display_fix_result_report.md`）
- 不碰治理指标处理入口（属于 `admin_governance_metric_action_entry_design_report.md`）
- 不修改 compile 图节点编排逻辑
- 不修改 `AdminProcessingTaskStep` 枚举定义
- 不修改 `AdminProcessingTaskDisplayStatus` 枚举定义

---

## 7. 最小安全实现范围

### 7.1 第一优先级（必须修）

1. **P1 进度重复**：`resolveProgressText()` 去前缀（1 行改动）
2. **P2/P3 运行中结论**：`buildReasonSummary()` 对 RUNNING 走进行时文案（约 15 行）
3. **P4 节点名中文化**：`CompileGraphLifecycleListener.before()` 映射（约 10 行）

**这 3 项改动预计 <50 行 Java，前端不改也能显著改善展示。**

### 7.2 第二优先级（建议修）

4. **P5 处理历史 Tab**：后端加 `status` 过滤 + 前端新增 Tab（预计 1-2d）

### 7.3 可选优化

5. `CompileJobProgressMessageFormatter` 文案微调（`正在审查文章（3/5）` → `正在检查第 3 / 5 篇文章`）
6. `AdminCompileReviewSummaryService.buildStepDetail()` 区分进行时/完成时

---

## 8. 建议下一轮执行顺序

1. **agentA 实施 P1-P4**（后端最小改动，<50 行）→ 产出 `admin_processing_task_status_copy_fix_result_report.md`
2. **agentD 运行时验证** → 确认展示不再有进度重复、运行中不显示最终结论、节点名已中文化
3. **agentA 实施 P5**（处理历史 Tab）→ 独立 commit
4. 最后再处理文章详情关键词折叠（B 项）和治理指标入口（F 项）

---

## 9. 本轮确认

- **是否修改了 `src/main/java/**`**：否
- **是否修改了 `src/test/java/**`**：否
- **是否修改了前端文件**：否
- **是否修改了任何配置/文档/脚本**：否
- **是否提交了任何代码**：否
- **仅执行**：代码探查、根因定位、文案映射设计、API 设计、方案对比
