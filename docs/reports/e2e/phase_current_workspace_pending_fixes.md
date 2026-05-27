# 当前工作区待修复清单

最后更新：2026-05-23

## 状态总览

| 编号 | 问题 | 状态 | 负责人 |
|------|------|------|--------|
| A | 当前处理任务展示语义 P1-P4 | 已修复，待 runtime 验证 | agentA 已修复 |
| G | 待人工确认说明结构化与中文化 | 已修复，待 runtime 验证 | agentA 已修复+revision |
| B | 已入库内容详情展示 | 已修复，待 runtime 验证 | agentA 已修复 |
| 处理历史 | 处理历史入口设计与实现 | 待设计 | agentB（设计）→ agentA（实现） |
| F | 治理指标处理入口 | 待设计 | 待分配 |
| H | 已入库内容详情页空指针报错 | 待修复 | 待分配 |
| I | 当前处理任务 Writer 阶段进度数量丢失 | 待归因/待修复 | 待分配 |
| C | Compile Fixer 性能 | 已完成/已提交 | — |
| D | Query 当前修复线 | 已完成/已提交 | — |
| E | AGENTS.md 文档小修 | 已完成/已提交 | — |

---

## 待 runtime 验证

### A. 当前处理任务展示语义 P1-P4

- 状态：**已修复，待统一 runtime 验证**
- 修复报告：`admin_processing_task_status_copy_fix_result_report.md`
- 修复范围：P1 进度比例重复、P2/P3 运行中终结案文案、P4 内部节点名中文化

### G. 待人工确认说明结构化与中文化

- 状态：**已修复，待统一 runtime 验证**
- 修复报告：`admin_review_queue_issue_explanation_fix_result_report.md`
- 修订报告：`admin_review_queue_issue_explanation_fix_revision_report.md`

---

## 进行中

### B. 已入库内容详情展示

- 状态：**已修复，待统一 runtime 验证**
- 修复报告：`admin_article_detail_keyword_metadata_display_fix_result_report.md`
- 说明：关键词默认折叠前 8 个、技术元数据 JSON 包裹在 details 中，agentA 已修复

---

## 待设计

### 处理历史入口

- 状态：**待设计（agentB）**
- 期望报告：`admin_processing_history_tab_design_report.md`（尚未生成）
- 说明：agentB 先完成只读设计，再由 agentA 实现

### F. 治理指标处理入口

- 状态：**待设计**
- 期望报告：`admin_governance_metric_action_entry_design_report.md`（尚未生成）
- 说明：首页治理指标需可点击进入处理列表，先设计后实现

---

## 新增待修复

### H. 已入库内容详情页空指针报错

- 状态：**待修复**
- 现象：页面顶部报错 `加载入库内容失败：Cannot set properties of null (setting 'innerHTML')`
- 当前判断：前端回归，文章详情已把 `article-technical-info` 合并进 `开发诊断信息` 折叠区，但清空详情时仍无条件写入旧节点，触发空指针
- 影响：进入“已入库内容”页时会出现明显错误提示，干扰详情查看
- 建议处理顺序：**高优先级，先修**

### I. 当前处理任务 Writer 阶段进度数量丢失

- 状态：**待归因/待修复**
- 现象：
  - `质量检查` 阶段仍显示 `正在检查第 7 / 22 篇文章`
  - `Writer` 阶段仅显示 `正在调用 Writer 生成草稿：...`，缺少 `1 / N` 这类数量信息
- 当前判断：不是所有阶段都丢数量，更像 `Writer` 阶段单独走了原始 message 展示链路，没有统一走带数量的 `progressText` / presentation 格式化
- 影响：当前处理任务可观测性下降，用户无法判断 Writer 整体进度
- 建议处理顺序：在 H 修复后，单独做一轮只读归因，再决定修复点落在前端展示层还是后端 presentation formatter

---

## 已完成/已提交

### C. Compile Fixer 性能

- 状态：**已完成/已提交**
- 提交：`f9fe20d perf(compile): slim fixer payload for review repair loop`
- 说明：Fixer payload slimming 已合入，若需 runtime 复验另开专项

### D. Query 当前修复线

- 状态：**已完成/已提交**
- 提交：
  - `9db6f64 fix(query): improve multi-point partial answer expansion`
  - `9622f22 fix(query): prevent terminal fallback from replacing synthesized answers`
- 说明：terminal fallback 从 4/4 降到 0/5，多点答案完整度已改善，已提交

### E. AGENTS.md 文档小修

- 状态：**已完成/已提交**
- 提交：`e0beaa7 docs: document local runtime and governance conventions`
- 说明：Deep Research 绑定策略已收成项目级约定

---

## 建议执行顺序

1. 修复 H（已入库内容详情页空指针报错）
2. 归因并修复 I（Writer 阶段进度数量丢失）
3. agentD 做一轮当前前端回归确认（重点覆盖“已入库内容”和“当前处理任务”）
4. 落地 E2E suite 并进入干净库全链路验证
