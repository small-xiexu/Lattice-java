# 轻量文档捕获 + Admin 处理历史修复 — 预提交范围审查报告

审查时间：2026-06-06
执行人：项目架构师 / 治理复核 Agent
类型：只读预提交范围审查，不修改代码，不提交
前置报告：
- `pe1_q2_lightweight_small_doc_content_lines_fix_result_report.md`（agentA）
- `pe1_q2_lightweight_small_doc_runtime_gate_report.md`（agentD）
- `admin_processing_history_nested_card_fix_result_report.md`（agentA）
- `latest_two_public_eval_full_recall_citation_gate_report.md`（agentD）
- `pe1_q2_acronym_solution_redline_review_report.md`（架构师）

---

## 1. 本轮目标

对工作区中两条独立修复线进行预提交范围审查，输出：
- 每条修复线的质量判定
- 两条修复线是否可以同时提交
- 推荐的提交策略（单次提交还是分开提交）
- 每笔提交的文件清单（include/exclude）
- 每笔提交的 commit message
- 最终 YES/NO 建议

---

## 2. 当前 Git 状态摘要

```
 M special_cases_report.md                                          ← 必须排除
 M src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java
 M src/main/resources/static/admin/modules/management-history-part.js
 M src/test/resources/admin/management-js-runtime-test.js
?? docs/reports/admin/admin_processing_history_nested_card_fix_result_report.md
?? docs/test/knowledge-base-e2e/current_accuracy_status_and_next_gap_analysis_report.md
?? docs/test/knowledge-base-e2e/latest_two_public_eval_full_recall_citation_gate_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_acronym_general_solution_design_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_acronym_query_retrieval_analysis_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_acronym_solution_redline_review_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_lightweight_small_doc_content_lines_fix_result_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_lightweight_small_doc_runtime_gate_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_pdf_probe_role_failure_analysis_report.md
?? docs/test/knowledge-base-e2e/pe1_q2_writer_acronym_preservation_fix_result_report.md
```

共 4 个 modified 文件 + 9 个 untracked 报告文件。其中 `special_cases_report.md` 是 redline 输出，必须排除。

---

## 3. 未提交变更分组

### 组 A：Compiler — 轻量文档内容捕获行数修复

| 文件 | 变更类型 | 变更量 |
|------|----------|--------|
| `src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java` | 参数值调整 | 1 行 |

```diff
- private int lightweightMaxContentLines = 8;
+ private int lightweightMaxContentLines = 24;
```

### 组 B：Admin — 处理历史嵌套卡片修复

| 文件 | 变更类型 | 变更量 |
|------|----------|--------|
| `src/main/resources/static/admin/modules/management-history-part.js` | HTML 结构修复 | 1 行（+1 `</div>`） |
| `src/test/resources/admin/management-js-runtime-test.js` | 新增 DOM 测试 | +46 行（3 个断言） |

### 必须排除

| 文件 | 原因 |
|------|------|
| `special_cases_report.md` | redline 输出文件，不可提交 |

---

## 4. 修复线 A：CompilerProperties 轻量文档修复 — 质量判定

### 4.1 变更内容

`lightweightMaxContentLines: 8 → 24`

将 LIGHTWEIGHT_SMALL_DOC 路径（不满足 Topic gate 长文档阈值的小型文档）的内容捕获行数从最多 8 行扩展到最多 24 行。

### 4.2 根因

小型 PDF（< 12000 字符）走 LIGHTWEIGHT_SMALL_DOC 分析路径时只捕获前 8 行内容（约 1920 字符），PDF 中后段的关键实体定义未被纳入 Writer prompt 的 structured sections，导致 Writer 生成的文章缺少可检索的关键词。

### 4.3 通用性审查

| 检查项 | 判定 | 理由 |
|--------|:---:|------|
| 绑定特定业务域 | **否** | 参数名为 `lightweightMaxContentLines`，对所有小型文档生效 |
| 绑定特定文件名 | **否** | 不区分文件名或内容主题 |
| 绑定特定术语 | **否** | 不包含任何 SL/TL/IM/Q2 等业务词 |
| 绑定特定问题样式 | **否** | 编译期参数，不感知查询内容 |
| 绑定特定样例字符串 | **否** | 纯数值参数变更 |
| 是通用能力改善 | **是** | 所有小型文档的 structured sections 捕获更完整 |

### 4.4 Runtime Gate 验证结果（agentD 已验证）

| 维度 | 结果 |
|------|:---:|
| PDF Writer 输出改善 | content 1920 → 3969 字符，含完整角色定义表格 |
| Q2 全名查询 | **PASS**（cov=1.0） |
| Q2 缩略词查询 | FAIL（FTS tokenization 问题，非 Writer 问题，见第 6 节） |
| PE1 Q1/Q3-Q12 回归 | **无新增 FAIL** |
| PE1 S1-S4 搜索回归 | **6/6 PASS** |

### 4.5 风险评估

| 风险 | 评估 |
|------|------|
| Writer prompt 体积增加 | 24 行 × 240 字符 = 5760 字符上限，在 LLM context 窗口内完全可接受 |
| 对已 PASS 题目的影响 | 捕获更多内容不会让已 PASS 的题 FAIL |
| cleanup 行逻辑保护 | `buildLightweightContentLines()` 第 2 步清理空行和短行（< 40 chars），不会把无意义行计入 24 行配额 |
| 对中大型文档的影响 | 无 — Topic gate 通过的长文档走完全不同的分析路径 |

### 4.6 质量判定：**通过**

理由：
- 1 行纯数值参数变更，无逻辑修改
- 对所有小型文档通用生效，零业务绑定
- Runtime gate 验证通过，无回归
- Redline BLOCKER=0
- mvn test 全量通过（1018 tests）

---

## 5. 修复线 B：Admin 处理历史嵌套卡片修复 — 质量判定

### 5.1 变更内容

`management-history-part.js` 的 `renderHistoryItem()` 函数末尾缺少外层 `<div>` 的闭合标签 `</div>`，导致多条历史记录拼接时卡片嵌套而非同级排列。

修复：在返回字符串末尾增加一个 `</div>`。

### 5.2 根因

div 结构：
```html
<div class='list-item history-list-item'>          ← 打开但从未闭合
  <div class='history-list-item-main'>
    <div class='history-list-item-top'>...</div>
    <div class='history-list-item-body'>...</div>
    <div class='history-list-item-side'>...</div>
  </div>                                            ← 只闭合了 main
```

当 `items.map(renderHistoryItem).join("")` 拼接多条时，item2 的开标签被浏览器解析为 item1 的子节点，两条卡片嵌套而非同级排列。

### 5.3 通用性审查

| 检查项 | 判定 | 理由 |
|--------|:---:|------|
| 绑定特定业务域 | **否** | 前端 HTML 结构修复 |
| 绑定特定文件名 | **否** | 不涉及后端业务逻辑 |
| 绑定特定术语 | **否** | 纯 DOM 结构修复 |
| 绑定特定问题样式 | **否** | 不涉及查询/检索 |
| 绑定特定样例字符串 | **否** | 不涉及任何数据内容 |

### 5.4 影响范围

| 维度 | 影响 |
|------|------|
| JS 逻辑 | **未修改**（loadProcessingHistory、applyHistoryFilterAndRender 等函数未变更） |
| 后端 Admin API | **未修改**（无 Controller/Service/Repository 变更） |
| CSS 布局 | **未修改** |
| 数据加载 | **未修改**（fetchJson 调用、状态管理未变更） |
| 筛选/刷新/详情跳转 | **未修改** |

### 5.5 测试覆盖

新增 3 个 DOM 结构断言：

| 测试 | 覆盖点 |
|------|--------|
| `<div>` 与 `</div>` 数量相等 | 标签平衡性 |
| 两条记录 joined HTML 中 `list-item history-list-item` 出现 2 次 | 同级节点 |
| item2 不在 item1 的 HTML 内部 | 无嵌套 |

ManagementJsRuntimeTests: 8/8 PASS（原有 5 个 + 新增 3 个）。

### 5.6 质量判定：**通过**

理由：
- 1 行 HTML 结构修复，根因明确
- 仅前端变更，不涉及后端
- 3 个新测试覆盖平衡性、同级性、无嵌套
- 全量测试通过
- Redline BLOCKER=0

---

## 6. PE1 Q2 缩略词红线结论摘要

本轮预提交审查中的 CompilerProperties 修复与 PE1 Q2 缩略词问题**有关联但独立**。为完整呈现上下文，这里摘要前置红线审查结论：

### 6.1 前置报告关键事实

| 事实 | 来源 |
|------|------|
| 源 PDF 不含缩略词 SL/TL/IM | agentA PDF 文本提取已确认（`pe1_q2_writer_acronym_preservation_fix_result_report.md`） |
| Writer 正确输出了全部角色定义 | agentD runtime gate 已确认 |
| Q2 全名查询 PASS（cov=1.0） | agentD runtime gate 已确认 |
| Q2 缩略词查询 FAIL | 因查询 token 空间与索引 token 空间缺少桥接机制 |

### 6.2 方案 B（DB query_rewrite_rules）红线审查结论

在 `pe1_q2_acronym_solution_redline_review_report.md` 中已完成的红线逐条审查：

| 红线禁止项 | SL/TL/IM 规则是否命中 |
|------------|:---:|
| 特定业务域 | **命中** |
| 特定术语 | **命中** |
| 特定问题样式 | **命中** |
| 特定样例字符串 | **命中** |

结论：**NEEDS_REDESIGN**。不接受方案 B（向 `query_rewrite_rules` 插入 SL/TL/IM 规则）作为修复路径。Q2 缩略词 FAIL 应标记为评测口径问题——源文档不含缩略词，系统在"给定正确查询词"时工作正常。

### 6.3 与本次提交的关系

CompilerProperties 的 `lightweightMaxContentLines 8→24` 修复**不涉及**缩略词方案 B：
- 不向 `query_rewrite_rules` 插入任何规则
- 不在 Java 代码中写入任何 SL/TL/IM 硬编码
- 不修改 Writer prompt
- 是纯通用编译能力改善

---

## 7. 两条修复线的独立性判定

| 维度 | 判定 |
|------|:---:|
| 是否共享修改文件 | **否**（CompilerProperties.java 与 management-history-part.js 无交集） |
| 是否共享逻辑/依赖 | **否**（compiler 编译链 vs admin 前端展示） |
| 是否共享测试文件 | **否**（compiler 无新增测试，admin 测试独立） |
| 是否相互依赖 | **否**（任一修复独立生效，不依赖另一修复） |
| 回滚独立性 | **是**（任一修复可独立回滚，不影响另一修复） |

两条修复线完全独立，**应分开提交**。

---

## 8. 推荐提交策略

### 两笔独立提交

**理由**：
1. 两条修复线的变更文件零交集
2. 功能域完全隔离（compiler vs admin frontend）
3. 独立回滚能力（任一笔出问题不影响另一笔）
4. 符合 AGENTS.md 的"每次提交只做一件事"原则

---

## 9. Commit 1：Compiler 轻量文档内容捕获行数修复

### 包含文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java` | `lightweightMaxContentLines: 8→24` |

### 排除文件

| 文件 | 原因 |
|------|------|
| `special_cases_report.md` | redline 输出文件 |
| `src/main/resources/static/admin/modules/management-history-part.js` | 归属 Commit 2 |
| `src/test/resources/admin/management-js-runtime-test.js` | 归属 Commit 2 |
| 所有 untracked `docs/` 报告文件 | 中间过程报告，不提交 |

### Commit Message

```
fix(compiler): increase lightweight small doc content capture from 8 to 24 lines

Small documents (<12000 chars) that fall through the Topic gate to the
LIGHTWEIGHT_SMALL_DOC path previously captured only 8 content lines as
structured sections. This left mid-to-late document content (e.g. role
definition tables in single-page PDFs) unrepresented in Writer prompts.
Increasing to 24 lines ensures structured sections cover the full document
body without meaningfully affecting Writer context budget.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## 10. Commit 2：Admin 处理历史嵌套卡片修复

### 包含文件

| 文件 | 说明 |
|------|------|
| `src/main/resources/static/admin/modules/management-history-part.js` | 修复 `renderHistoryItem()` 缺失的 `</div>` |
| `src/test/resources/admin/management-js-runtime-test.js` | 新增 DOM 结构验证测试 |

### 排除文件

| 文件 | 原因 |
|------|------|
| `special_cases_report.md` | redline 输出文件 |
| `src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java` | 归属 Commit 1 |
| 所有 untracked `docs/` 报告文件 | 中间过程报告，不提交 |

### Commit Message

```
fix(admin): close unclosed div in processing history list items

The renderHistoryItem function's outer <div class='list-item
history-list-item'> was never closed, causing the browser to nest
subsequent history cards inside the first one. Add the missing </div>
and three DOM-structure assertions to prevent regression.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## 11. 最终建议：YES

**两条修复线均建议提交，分为两笔独立 commit。**

| 维度 | 判定 |
|------|:---:|
| Redline | Commit 1 BLOCKER=0, Commit 2 BLOCKER=0 |
| Runtime Gate | Commit 1 已验证通过（agentD），Commit 2 测试通过（8/8） |
| 回归风险 | Commit 1 无回归（PE1 全量保护），Commit 2 无回归（后端不变） |
| 修改量 | Commit 1: 1 行，Commit 2: 1 行 + 46 行测试 |
| 通用性 | Commit 1: 通用参数变更，Commit 2: 通用 HTML 修复 |

---

## 12. 后续事项

| 事项 | 状态 |
|------|:---:|
| PE1 Q2 缩略词 FAIL | 已标记为评测口径问题。全名查询 PASS（cov=1.0），系统无缺陷。编译期 acronym extraction（方案 A）留待后续独立评估。 |
| PE2 FQ10 PDF BLOCKED | 与本次提交无关，`latest_two_public_eval` 报告中已标注。需确认 PDF 文件是否已就绪。 |

---

## 13. 明确声明

- [x] 本轮未修改任何代码
- [x] 本轮未修改任何测试
- [x] 本轮未修改任何配置文件
- [x] 本轮未读取 hidden eval 内容
- [x] 本轮未提交 commit
- [x] 本轮未向 `query_rewrite_rules` 插入任何规则
- [x] 本轮未处理任何 SL/TL/IM DB 规则
- [x] `special_cases_report.md` 已明确排除，不会包含在任何提交中
- [x] 所有报告文件（`docs/test/knowledge-base-e2e/*.md`、`docs/reports/admin/*.md`）均不纳入提交
- [x] 两条修复线完全独立，推荐分开提交
- [x] Commit 1 为通用编译能力改善，不包含任何业务特判
- [x] Commit 2 为纯前端 HTML 结构修复，不涉及后端变更
