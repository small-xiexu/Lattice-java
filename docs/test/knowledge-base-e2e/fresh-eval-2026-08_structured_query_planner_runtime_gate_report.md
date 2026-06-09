# PE5 StructuredQueryPlanner 修复后 Runtime Gate 报告

验收时间：2026-06-07 20:30 ~ 20:40
HEAD：`8fe2b0d`
执行人：agentD
前置修复：agentA StructuredQueryPlanner / StructuredQueryExecutor 过滤器提取修复

---

## 1. 环境

| 项 | 值 |
|---|---|
| 编译 | 复用 PE5 数据（2 articles, 2 source_files, SUCCEEDED） |
| Redline | BLOCKER=0 |
| mvn test | 1018/0/0/0（agentA 报告） |
| DevTools reload | 32 次（代码已热加载） |

---

## 2. 重点修复效果

| 题号 | 前轮（structured recall fix） | 本轮（planner fix） | 变化 |
|---|---|---|---|
| FQ3 | **PARTIAL** ✅ | **INSUFFICIENT_EVIDENCE** | **↓ 回退** |
| FQ6 | FAIL | **FAIL** | 不变 |
| FQ7 | FAIL | **FAIL** | 不变 |
| FS4c | FAIL | **FAIL** | 不变 |

**FQ3 回退为 FAIL。** StructuredQueryPlanner 拦截了前轮结构化源召回修复已生效的 YAML 计数查询路径。

---

## 3. 全量回归

| 题号 | 前轮 | 本轮 | 变化 |
|---|---|---|---|
| FQ1 | PARTIAL | PARTIAL | — |
| FQ2 | PASS | PASS | — |
| **FQ3** | **PARTIAL** | **INSUFFICIENT** | **↓回退** |
| FQ4 | PASS | PASS | — |
| FQ5 | PARTIAL | PARTIAL | — |
| FQ6 | FAIL | FAIL | — |
| FQ7 | FAIL | FAIL | — |
| FQ8 | PASS | PASS | — |
| FQ9 | PASS | PASS | — |
| FQ10 | PARTIAL | PARTIAL | — |
| **FQ11** | **PASS** | **INSUFFICIENT** | **↓回退** |
| **FQ12** | **PASS** | **INSUFFICIENT** | **↓回退** |
| FG1 | PARTIAL | PARTIAL | — |
| FG2 | PARTIAL | PARTIAL | — |
| FG3 | PARTIAL | PARTIAL | — |

**3 题回退**（FQ3/FQ11/FQ12）。StructuredQueryPlanner 对 LLM 模式的 query 介入过宽，破坏了上一轮已生效的 YAML 结构化召回和拒答行为。

---

## 4. 结论

### **FAIL — StructuredQueryPlanner 引入回归**

| 维度 | 状态 |
|---|---|
| FQ3 修复 | **回退**（PARTIAL→INSUFFICIENT） |
| FQ6/FQ7 修复 | 未改善 |
| FS4c | 未改善（独立搜索链路问题） |
| 回归 | FQ11/FQ12 回退（PASS→INSUFFICIENT） |

---

## 5. 下一步

**交给 agentA 修复 StructuredQueryPlanner 对 LLM 模式 query 的过度拦截。**

前轮结构化源召回修复已验证 YAML 计数查询（FQ3）可通过 LLM 路径处理。本轮 StructuredQueryPlanner 应只在确定需要结构化的场景（如 exact value match）介入，不应覆盖所有 YAML/CSV 查询。建议新增 guard 条件：当 LLM 模式或已有其他 retrieval 路径可达时，planner 回退或返回空计划。

FS4c（LOT-0601 批次号搜索）应单独拆为搜索链路优化，不纳入 planner 范围。

---

## 6. 明确声明

- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未修改 source、question-set
- [x] 未提交 commit
