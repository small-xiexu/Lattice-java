# PE5 FQ3 最小修复 Runtime Gate 报告

验收时间：2026-06-07 21:00 ~ 21:10
HEAD：`8fe2b0d`
执行人：agentD
前置修复：agentA `StructuredQueryPlanner.extractFilters()` LinkedHashMap→独立列表

---

## 1. 环境

| 项 | 值 |
|---|---|
| PE5 数据 | 复用（2 articles, SUCCEEDED） |
| Redline | BLOCKER=0 |
| mvn test | 1018/0/0/0（agentA 报告） |
| DevTools reload | 已热加载 |

---

## 2. 验证结果

| 题号 | 前轮（planner fix 回退前） | 上轮（planner fix 回退后） | 本轮（fq3 minimal fix） | 判定 |
|---|---|---|---|---|
| FQ3 | **PARTIAL** ✅ | INSUFFICIENT | **INSUFFICIENT** | **未恢复** ❌ |
| FQ6 | FAIL | FAIL | FAIL | 不变 |
| FQ7 | FAIL | FAIL | FAIL | 不变 |
| FQ11 | PASS | INSUFFICIENT | **INSUFFICIENT** | 未恢复 ❌ |
| FQ12 | PASS | INSUFFICIENT | **INSUFFICIENT** | 未恢复 ❌ |

**FQ3 仍为 INSUFFICIENT_EVIDENCE。** LinkedHashMap 去重修复未解决 Planner 对 LLM 路径的拦截问题。FQ11/FQ12 的回退同样未恢复。

---

## 3. 分析

`extractFilters()` 的 LinkedHashMap→独立列表修复解决了单字段多值覆盖问题，但未能恢复 FQ3。这说明 Planner 在更早的阶段就拦截了 query：

- Planner 判断 FQ3 为"结构化查询" → 走 COUNT 聚合路径
- 但 YAML 数据未被 Planner/Executor 正确消费 → 返回空结果
- LLM 模式被跳过 → 原本可用的 LLM 路径（前轮 structured recall fix 已验证）不再被执行

**Planner 对 LLM 模式 query 的过度拦截是核心问题，不是 filter 提取的 bug。** 仅修 `extractFilters()` 不足以收口。

---

## 4. 结论

### **FAIL — FQ3 未恢复**

| 维度 | 状态 |
|---|---|
| FQ3 | 未恢复 ❌ |
| FQ11/FQ12 | 回退未恢复 ❌ |
| FQ6/FQ7 | 不变 |
| FS4c | 不变（独立问题） |

---

## 5. 下一步

agentA 需要在 Planner 层增加 guard：当 query 存在 LLM 可达路径时，Planner 应回退（返回空计划或 fallthrough），不应拦截并产生错误的 COUNT 计划替代 LLM 答案。

FQ6/FQ7/FS4c 仍为独立问题，不纳入 Planner 范围。

---

## 6. 明确声明

- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未修改 source、question-set
- [x] 未提交 commit
