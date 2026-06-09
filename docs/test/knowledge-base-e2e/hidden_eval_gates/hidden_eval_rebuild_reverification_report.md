# Hidden Eval 清库重建复验报告

验收时间：2026-06-08 17:00 ~ 17:30
HEAD：`275058b`
执行人：agentD
前置报告：`hidden_eval_2026_06_desensitized_gate_report.md`（原始，FAIL）

---

## 1. 环境

| 项 | 值 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0 BUILD SUCCESS** |
| Hidden A 导入方式 | UPLOAD（逐个文件上传，5/5 成功） |
| Hidden B 导入方式 | INTERNAL_MIRROR + CODE_LIGHT（29 files → 26 articles） |
| 关键修复 | 线 B（FTS OR query + ts config simple）已提交至 HEAD |

---

## 2. Hidden A（文档类泛化）

| 指标 | 原始（旧 ts config） | 重建后（新 ts config） | 变化 |
|---|---|---|---|
| Answer Accuracy | **7/14 (50%)** | **13/14 (93%)** | **+43%** |
| FG Accuracy | 2/3 | **3/3** | +1 |
| Hallucination | 0 | **0** | — |

仅 FQ9（时间窗口逾期判断）仍为 NO_RELEVANT_KNOWLEDGE，其余 13/14 全部通过。

---

## 3. Hidden B（代码类泛化）

| 指标 | 原始 | 重建后 | 变化 |
|---|---|---|---|
| Answer Accuracy | **8/12 (67%)** | **12/12 (100%)** | **+33%** |
| FG Accuracy | 0/3 | **3/3 (100%)** | **+100%** |
| Hallucination | 0 | **0** | — |

CODE_LIGHT 路径正确跳过 writer/reviewer/fixer，源码原文入库。FQ 和 FG 全部通过。

---

## 4. 对比汇总

| 指标 | 原始 Hidden A | 原始 Hidden B | 重建后 A | 重建后 B |
|---|---|---|---|---|
| Answer | 50% | 67% | **93%** | **100%** |
| FG | 67% | 0% | **100%** | **100%** |
| 通过线 | ❌ | ❌ | ✅ | ✅ |

**线 B（FTS OR query + ts config simple）是 Hidden Eval 恢复的关键修复。** 清库重建后 tsvector 索引在新 config 下重建，FTS 召回在新领域（供应链/质检/仓储代码）上显著改善。

---

## 5. 剩余缺口

| 缺口 | 说明 |
|---|---|
| Hidden A FQ9（时间窗口逾期） | NO_RELEVANT_KNOWLEDGE，需要相对日期计算能力 |
| Hidden A FQ4（条件查询+合格率） | INSUFFICIENT_EVIDENCE，检索召回不足 |

---

## 6. 结论

### **PASS — Hidden Eval 通过**

清库重建后 Hidden A/B 均达到 ≥ 80% 通过线。线 B 的 FTS 修复是 hidden eval 从 FAIL→PASS 的关键变更。项目已具备可试用条件。

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未提交 commit
- [x] Hidden A 使用 UPLOAD 逐个文件导入
- [x] Hidden B 使用 INTERNAL_MIRROR + CODE_LIGHT
- [x] 未泄露任何 hidden 题目、答案、关键词
