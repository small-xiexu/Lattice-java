# PE5 StructuredQueryPlanner 保守化收口 Runtime Gate 报告

验收时间：2026-06-08 00:05 ~ 00:15
HEAD：`8fe2b0d`
执行人：agentD
前置修复：agentA `StructuredQueryPlanner` COUNT 计划保守化（filters.size()==1 guard）

---

## 1. 验证结果

| 题号 | 前轮（planner fix 回退前） | 本轮（guard fix） | 判定 |
|---|---|---|---|
| FQ3 | **PARTIAL** ✅ | **INSUFFICIENT** | **未恢复** ❌ |
| FQ6 | FAIL | FAIL | 不变 |
| FQ7 | FAIL | FAIL | 不变 |
| FQ11 | PASS | **INSUFFICIENT** | 未恢复 ❌ |
| FQ12 | PASS | **INSUFFICIENT** | 未恢复 ❌ |

---

## 2. 分析

保守化 guard（filters.size()==1）不足以恢复 FQ3。重要新发现：

- FQ3 的 mode 为 **LLM**（非结构化路径），说明 Planner 未再拦截此查询
- LLM 回答提到"supplier-registry.yaml 中共有 8 条供应商记录"，说明系统**知道**数据存在
- 但 LLM 无法从 YAML 中提取评级值 → **核心瓶颈是 YAML 结构化数据的 retrieval/recall，不是 Planner**

FQ11/FQ12 的回退（PASS→INSUFFICIENT）也是 LLM 模式下的 retrieval 问题，与 Planner 无关。

**结论：Planner 修复系列已到达能力边界。** FQ3 的 INSUFFICIENT 不是 Planner 拦截所致，是 YAML 评级字段未被检索召回。

---

## 3. 结论

### **FAIL — Planner guard 未恢复 FQ3**

Planner 修改只能控制 COUNT 计划生成，不能改善 YAML 结构化数据的 retrieval recall。FQ3/FQ11/FQ12 的根因在检索层，不在计划层。

---

## 4. 下一步

**停止在 Planner 上继续修。** 转向检索/召回层，优先处理 YAML 字段值的 LIKE/FTS/Vector 召回。FQ6/FQ7/FS4c 同属检索召回缺口，可合并处理。Planner 修复系列到此收口。

---

## 5. 明确声明

- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未修改 source、question-set
- [x] 未提交 commit
