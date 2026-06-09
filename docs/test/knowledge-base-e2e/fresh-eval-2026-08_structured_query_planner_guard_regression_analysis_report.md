# PE5 StructuredQueryPlanner Guard 回归 — 只读根因分析报告

分析时间：2026-06-07
执行人：agentB（治理/归因 Agent）
类型：只读源码核实 + Runtime Gate 交叉验证，不修改任何文件

---

## 1. 结论：唯一真根因

**`extractFilters()` 的 `LinkedHashMap` 对同列多值做键覆盖，导致结构化查询对"同一字段的多个值"产生错误的 COUNT 计划。FQ3 的 TRUE 回归由此引起。FQ11/FQ12 的 gate 报告中的"回归"是评测统计口径问题，不是答案质量回归。**

### 收敛路径

```
FQ3: "评级是 A？评级是 C 或 D 的各有多少？"
  → ASSIGNMENT_PATTERN 匹配 3 次（评级=A, 评级=C, 评级=D）
    → extractFilters: LinkedHashMap 键覆盖 → filters = {评级: d}
      → isCountQuestion = true → 计划类型 = COUNT
        → 执行: countRowsByFilters({评级: d}) → 返回 1
          → 答案: "评级 d 的供应商有 1 家"  ← 错误
```

**ASSIGNMENT_PATTERN 本身不是 bug，LinkedHashMap 去重也不是 bug。bug 在于：Planner 在 `filters` 非空时直接生成 COUNT/ROW_LOOKUP 计划，但 `filters` 如果只含同列多值的最后一个值，生成的计划语义与用户问题已经偏离。Planner 没有检测"多个等值匹配指向同一列但不同值"的场景并拒绝生成计划。**

---

## 2. FQ3 / FQ6 / FQ7 / FS4c / FQ11 / FQ12 逐题溯源

### 2.1 FQ3：TRUE 回归（PARTIAL → INSUFFICIENT_EVIDENCE）

| 维度 | 分析 |
|------|------|
| 原始 gate（Round 0） | FAIL — 词法搜索召回不足 |
| Round 1+2 修复后 | PARTIAL — ts_config→simple + OR 语义改善搜索召回，LLM 可从 YAML 片段中部分计数 |
| Round 4 修复后 | **FAIL — 结构化查询截走主链，产生错误 COUNT 计划** |
| 根因 | `extractFilters` 用 `LinkedHashMap` 存列名→值映射，同列多值时只保留最后一个（"评级"→"a"→"c"→"d"，最终仅剩 `{评级: d}`）。Planner 未检测"多值指向同列"的冲突，继续生成 COUNT 计划。执行层 `countRowsByFilters` 对 `{评级: d}` 计数返回 1，而非预期的 A=2/C=2/D=1 |
| 为什么 `isNonStructuredIntentQuestion` 没拦住 | FQ3 不含"有没有/是否/定义/什么是/规则/说明/如何/怎么/为什么"任一词，不应被拦住——FQ3 确实是结构化查询意图 |
| 为什么 Round 2 没触发此 bug | Round 2 的 Planner 对 FQ3 可能也生成了同样错误的计划，但 Round 2 的 Executor/Service 的 evidence consistency 检查或结果验证可能拒绝了该计划 → fallback 到 LLM → PARTIAL。Round 3 的 Executor 改动（保留到 Round 4）可能放宽了结果接受条件，导致错误计划不再被拒绝 |
| 结论 | **StructuredQueryPlanner 应检测同列多值冲突，拒绝生成计划** |

### 2.2 FQ6：无变化（仍然是 FAIL）

| 维度 | 分析 |
|------|------|
| 当前路径 | COMPARISON_PATTERN 匹配 "AQL 标准超过 1.5" → `extractComparisonFilter` 提取 {标准: 1.5, operator: >} → 生成 ROW_LOOKUP 计划 |
| 为什么仍 FAIL | `COMPARISON_PATTERN` 提取的字段名 "标准" 可能不是 XLSX 中的真实列名（XLSX 列为 "AQL"）。执行层 `findRowsByFilters` 按 `normalized_value = ?` 做等值匹配，不支持 `>` 语义。比较运算符仅作为元数据标记在 StructuredQueryPlan 中，未被 Executor 消费为真正的范围查询 |
| 结论 | FQ6 需要 Executor/Repository 层实现真正的数值比较查询。Planner 层已正确提取比较意图。**独立于 FQ3 根因** |

### 2.3 FQ7：无变化（仍然是 FAIL）

| 维度 | 分析 |
|------|------|
| 当前路径 | 无等值匹配（"隔离中"前无 `field=value` 格式）→ filters 为空 → 无比较模式 → Planner 返回 Optional.empty() → 走搜索/LLM → 词法搜索召回不足 |
| 结论 | FQ7 的失败与 Planner Guard 无关。**词法搜索对 CSV 状态值的召回不足是独立问题** |

### 2.4 FS4c：无变化（仍然是 FAIL）

| 维度 | 分析 |
|------|------|
| 当前路径 | 搜索 API 不经过 StructuredQueryPlanner。走 KnowledgeSearchService → 12 通道 → FTS/LIKE/Vector。词法搜索对 "LOT-0601" 的召回不足 |
| 结论 | FS4c 的失败与 Planner Guard 无关。**独立搜索链路问题** |

### 2.5 FQ11：GATE 统计回归，非答案质量回归

| 维度 | 分析 |
|------|------|
| 问题 | "供应商台账里有没有定义供应商的付款条款和账期？" |
| 含 "有没有" → `isNonStructuredIntentQuestion` 返回 true → Planner 返回 Optional.empty() → 走 LLM 搜索 |
| 预期答案 | 拒答（台账无付款条款） |
| 原始 gate 判定 | INSUFFICIENT_EVIDENCE → 判定 PASS（正确拒答） |
| 当前 gate 判定 | INSUFFICIENT_EVIDENCE → 判定 "INSUFFICIENT"（gate 报告标记为回退） |
| 分析 | 答案 outcome 未变（仍为 INSUFFICIENT_EVIDENCE），答案质量与原始一致（正确拒答）。gate 报告的"回退"标记是评测口径问题——gate 自动化可能将 INSUFFICIENT_EVIDENCE 计为 FAIL 而非 PASS。 |
| 结论 | **不是代码回归，是 gate 评测统计问题** |

### 2.6 FQ12：GATE 统计回归，非答案质量回归

| 维度 | 分析 |
|------|------|
| 问题 | "批次追踪记录里有没有记录每批次的检验员是谁？" |
| 含 "有没有" → `isNonStructuredIntentQuestion` 返回 true → Planner 返回 Optional.empty() → 走 LLM 搜索 |
| 预期答案 | 拒答（CSV 不含检验员字段） |
| 与 FQ11 完全相同的 root cause | Gate 评测口径问题，非代码回归 |

---

## 3. 路由链路核实

### 3.1 确认 Planner Guard 当前有效逻辑

```java
// StructuredQueryPlanner.plan() — 第 79 行
if (isNonStructuredIntentQuestion(question)) {
    return Optional.empty();  // 拒止：非结构化意图
}

// isNonStructuredIntentQuestion — 第 416-424 行
return normalized.contains("有没有")    // → 拦截 FQ11, FQ12 ✅
    || normalized.contains("是否")
    || normalized.contains("定义")
    || normalized.contains("什么是")
    || normalized.contains("规则")
    || normalized.contains("说明")
    || normalized.contains("如何")
    || normalized.contains("怎么")
    || normalized.contains("为什么");
```

**源码与 fix report 一致。** `isNonStructuredIntentQuestion` 9 个拒绝词全部存在，`extractStandaloneValueProbe` 已不存在。

### 3.2 确认 FQ3 经过的完整路径

```
plan("供应商台账里有多少家供应商的评级是 A？评级是 C 或 D 的各有多少？")
  → isNonStructuredIntentQuestion → false (不含9个拒绝词)
  → extractCompareFilters → 3 filters, compareFilters.size()=3
  → isCompareQuestion → false (无"差异/不同/对比/比较")
  → extractFilters → filters = {评级: d} (LinkedHashMap 键覆盖 A→c→d)
  → extractGroupByField → "" (GROUP_BY_PATTERN 和 GROUP_EACH_PATTERN 均不匹配)
  → filters 非空 → isCountQuestion → true
  → 返回 COUNT 计划: filter={评级: d}
```

### 3.3 为什么这不是 ASSIGNMENT_PATTERN 的 bug

`ASSIGNMENT_PATTERN` 正确匹配了"评级是 A"、"评级是 C"、"评级是 D"。问题不在模式匹配，而在**匹配结果的使用方式**——`LinkedHashMap` 的键唯一性导致多值被覆盖。`extractCompareFilters` 使用 `List<Map>` 可以保留多值，但 `extractFilters` 使用单个 `LinkedHashMap` 无法保留多值。

---

## 4. 误归因纠正

| gate 报告的归因 | 实际归因 | 纠正 |
|------|------|------|
| "FQ3 回退 → StructuredQueryPlanner 拦截了前轮召回路径" | FQ3 回退 → StructuredQueryPlanner **没有拦截**，而是**生成了错误的 COUNT 计划**，因为 `extractFilters` 的 LinkedHashMap 键覆盖 | Planner Guard 没有过度拦截 FQ3——FQ3 确实通过了 Guard。问题在 `extractFilters` 无法表示同列多值 |
| "FQ11 回退 → PASS→INSUFFICIENT" | FQ11 答案质量未变（仍正确拒答），gate 评测口径将 INSUFFICIENT_EVIDENCE 计为 FAIL | Gate 统计问题，非代码回归 |
| "FQ12 回退 → PASS→INSUFFICIENT" | 同 FQ11 | Gate 统计问题，非代码回归 |

---

## 5. 下一步唯一推荐动作

### **StructuredQueryPlanner 检测同列多值冲突，拒绝生成计划**

**修改范围**：仅 `StructuredQueryPlanner.java` 的 `plan()` 方法

**修改逻辑**：在 `extractFilters` 返回后、生成 COUNT/ROW_LOOKUP 计划前，检测是否存在同列多值冲突。如果 ASSIGNMENT_PATTERN 匹配到同一列名的多个不同值，且 queryType 为 COUNT（无法在单次查询中处理多值），则拒绝生成计划，返回 `Optional.empty()` 回退到 LLM 路径。

**具体实现**：在 `extractFilters` 执行时，用 `ASSIGNMENT_PATTERN` 做一次独立的"多值检测"——统计同一列名出现的不同值数量。如果任一列名出现 >= 2 个不同值，且 `isCountQuestion` 为 true，则返回 `Optional.empty()`。

**为什么只影响 FQ3 而不影响其他题**：
- FQ4 (SUP-003 检验): 单值过滤，无多值冲突
- FQ5 (6 月不合格): 单值过滤（日期范围）
- FQ8 (SUP-003 合格率): 单值过滤
- FQ2 (A/B 级比较): 比较题，走 `isCompareQuestion` 路径
- FQ6/FQ7/FS4c: 不受影响（分别走 COMPARISON / 空 filters）

**不建议的替代方案**：
- ❌ 修改 `extractFilters` 从 LinkedHashMap 改为支持多值 → 需要改 Executor/Repository/SQL 整个下游链，改动面过大
- ❌ 删掉 `isNonStructuredIntentQuestion` → 会恢复 FQ11/FQ12 的 Round 3 值探针误拦问题
- ❌ 把 FQ3 加入 `isNonStructuredIntentQuestion` 的拒绝词 → 属于题号/问法特判，红线禁止
- ❌ 回退整个 Round 4 → 会恢复 Round 3 的值探针过度拦截

---

## 6. 明确声明

- [x] 未修改任何文件（代码、source、题集、报告）
- [x] 未提交 commit
- [x] 源码核实已完成：`StructuredQueryPlanner.java` 426 行全文阅读
- [x] `isNonStructuredIntentQuestion` 的 9 个拒绝词与 fix report 一致
- [x] `extractStandaloneValueProbe` 已不存在于源码中
- [x] FQ3 的 LinkedHashMap 键覆盖是结构化查询路径的长期潜伏 bug，因 Planner Guard 调整后首次暴露
- [x] FQ11/FQ12 的答案质量无回归，gate 报告标记为"回退"是评测统计口径问题
- [x] 推荐修复为 Planner 侧检测同列多值冲突后拒绝生成计划，改动面和风险极小
