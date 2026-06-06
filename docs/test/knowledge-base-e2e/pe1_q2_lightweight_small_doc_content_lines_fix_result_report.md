# PE1 Q2 LIGHTWEIGHT_SMALL_DOC 内容捕获行数修复结果报告

时间：2026-06-06
执行人：agentA（代码执行 Agent）
前置归因：`pe1_q2_pdf_probe_role_failure_analysis_report.md`（agentB）
前置 gate：`latest_two_public_eval_full_recall_citation_gate_report.md`（agentD）

---

## 1. 本轮目标

提高 `AnalyzeNode` LIGHTWEIGHT_SMALL_DOC 路径的内容捕获行数上限，使小型 PDF 的 structured sections 覆盖文档中后段内容，修复因编译抽取缺失导致的 PE1 Q2 `NO_RELEVANT_KNOWLEDGE`。

---

## 2. 根因摘要

PE1 的 2.7KB 单页 PDF 因不满足 Topic gate 长文档阈值（12000 字符），走 LIGHTWEIGHT_SMALL_DOC 分析路径。该路径只捕获前 8 行内容作为 structured sections（~1920 字符），PDF 中后段的关键实体定义（SL/TL/IM 角色）未被纳入 Writer prompt 的 structured sections，Writer 生成的文章缺少可检索的关键词。

---

## 3. 修改文件

| 文件 | 修改 |
|------|------|
| `src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java` | 参数值调整 |

---

## 4. 修改前 / 修改后

**修改前**：
```java
private int lightweightMaxContentLines = 8;
```

**修改后**：
```java
private int lightweightMaxContentLines = 24;
```

其他 LIGHTWEIGHT 参数不变：`lightweightMinMultiLineChars=40`、`lightweightMaxContentScanLines=60`、`lightweightMaxDescriptionChars=220`、`lightweightMaxLineChars=240`。

---

## 5. 为什么是通用修复，不是 Q2 特判

- 参数名 `lightweightMaxContentLines` 为通用配置，对所有小型文档生效
- 不依赖文件名、PDF 格式、具体内容
- 不写入 SL/TL/IM、Q2、incident-response-reference-lite 等任何业务词
- 仅改变"捕获多少行内容"的通用上限

---

## 6. 测试说明

无新增测试。`CompilerProperties` 是 POJO 配置类，参数值变更无需定向测试。全量 mvn test 通过即确认无回归。

---

## 7. redline 结果

`BLOCKER=0`

---

## 8. 全量 mvn test 结果

```
Tests run: 1018, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 9. 行为影响范围

- 所有走 LIGHTWEIGHT_SMALL_DOC 路径的小型文档（< 12000 字符）的 structured sections 从最多 8 行扩展到最多 24 行
- Writer prompt 体积略微增加（+16 行 × 240 字符 = +3840 字符上限）
- 不改变中大型文档（Topic gate 通过）的分析路径
- 不改变 query/retrieval/answer 主链

---

## 10. 风险与回归关注点

| 风险 | 评估 |
|------|------|
| Writer prompt 体积增加 | 24 行 × 240 字符 = 5760 字符上限，在 LLM context 窗口内完全可接受 |
| 对已 PASS 题目的影响 | 捕获更多内容不会让已 PASS 的题 FAIL |
| cleanup 行逻辑 | `AnalyzeNode.buildLightweightContentLines()` 的第 2 步会清理空行和短行（< 40 chars），不会把无意义的空行计入 24 行配额 |

---

## 11. 后续 agentD runtime gate 建议

1. PE1 清库（`bash scripts/reset-lattice-schema.sh`）
2. 导入全部 PE1 资料（含 `incident-response-reference-lite.pdf`）
3. compile（确认 PDF 编译成功，review_status=passed）
4. Q2 查询：应返回 SL/TL 角色区分答案（非 `NO_RELEVANT_KNOWLEDGE`）
5. Q1/Q3-Q12 全部回归
6. S1-S4 搜索回归
7. 如可能，验证 PE2 无回归

---

## 12. 未提交文件提醒

- `src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java`（1 行参数调整）
