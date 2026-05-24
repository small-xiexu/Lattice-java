# 当前工作区待修复清单状态更新报告

- 生成时间：2026-05-22
- 任务：整理 `phase_current_workspace_pending_fixes.md`，更新为准确台账

---

## 变更摘要

### 状态变更

| 编号 | 问题 | 旧状态 | 新状态 | 依据 |
|------|------|--------|--------|------|
| A | 当前处理任务展示语义 P1-P4 | 待修复 | 已修复，待 runtime 验证 | `admin_processing_task_status_copy_fix_result_report.md` |
| G | 待人工确认说明结构化与中文化 | 待修复 | 已修复，待 runtime 验证 | `admin_review_queue_issue_explanation_fix_result_report.md` + revision |
| B | 已入库内容详情展示 | 待修复 | 进行中（agentA） | 报告尚未生成 |
| 处理历史 | 处理历史入口 | 未独立列出 | 待设计（agentB） | 报告尚未生成 |
| F | 治理指标处理入口 | 待修复 | 待设计 | 报告尚未生成 |
| C | Compile Fixer 性能 | "尚未完成 runtime 验证" | 已完成/已提交 | `f9fe20d` |
| D | Query 当前修复线 | "尚未提交" | 已完成/已提交 | `9db6f64`, `9622f22` |
| E | AGENTS.md 文档小修 | "后续单独提交" | 已完成/已提交 | `e0beaa7` |

### 过期描述清理

- **D Query 修复线**：原写"当前 Query 修复线尚未提交"，实际 `9db6f64` 和 `9622f22` 已提交，已移除
- **E AGENTS.md**：原写"后续单独作为文档治理提交"，实际 `e0beaa7` 已提交，已移除
- **C Compile Fixer**：原写"尚未完成真实 Fixer 场景 runtime 验证"，已改为"已完成/已提交，若需复验另开专项"

### 结构简化

- 将 7 个问题拆为 4 组：待 runtime 验证（A、G）、进行中（B）、待设计（处理历史、F）、已完成（C、D、E）
- 添加状态总览表
- 移除已修复问题的详细产品口径描述（保留在各自修复报告中）
- 移除"当前不要做"清单（大部分已过期）

### 执行顺序更新

旧：A → G → B → 处理历史 → F → Fixer → Query
新：B（agentA 完成）→ 处理历史设计（agentB）→ 处理历史实现（agentA）→ F 设计（agentB）→ F 实现（agentA）→ 统一 runtime 验证（agentD）

---

## 未修改的文件

- `src/main/java/**`
- `src/test/java/**`
- `src/main/resources/static/**`
- 所有业务代码、prompt、schema、模型配置
- 所有已有报告文件（仅读取，未删除）
