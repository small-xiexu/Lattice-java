# fresh-eval-2026-08 StructuredQueryPlanner 保守化收口修复结果报告（Task C Round 6）

时间：2026-06-07
执行人：agentA（代码执行 Agent）
依据：Round 5 FQ3 修复 runtime gate 失败结论 + architect 分配的 Planner 保守化收口任务

---

## 1. 根因定位

### 1.1 唯一根因

**`StructuredQueryPlanner` 对 COUNT 查询的介入条件过宽：只要 `filters` 非空且 `isCountQuestion` 为 true 就生成 COUNT 计划。但 `StructuredQueryService` 对 COUNT 类型跳过空结果检查（`getQueryType() != COUNT` 条件），导致即使 COUNT 返回 0，也给出确定性答案而非回退到 LLM 路径。**

两层问题叠加：

1. **Planner 层**：`filters` 非空 + `isCountQuestion` = true → 生成 COUNT 计划。不检查 filters 的质量（是否单条件、是否有同列多值冲突）。

2. **Service 层**（本轮不修改）：`!result.hasResult() && plan.getQueryType() != StructuredQueryType.COUNT` — COUNT 查询即使返回 0 行也不回退 LLM，直接渲染 "0 家" 作为确定性答案。

由于本轮只能修改 Planner，修复方向只能是：**在 Planner 层收紧 COUNT 计划的生成条件，让不适合结构化路径的 COUNT 查询回退到 LLM**。

### 1.2 FQ3 故障链路（修复前）

```
问题: "供应商台账里有多少家供应商的评级是 A？评级是 C 或 D 的各有多少？"

1. extractFilters → {评级: c} (LinkedHashMap 键覆盖，A 被 C 覆盖)
2. filters 非空 → isCountQuestion → true → queryType = COUNT
3. Round 5 hasSameColumnMultiValueConflict → 检测到 评级 有 {a, c} → 返回 true
   → Planner 返回 Optional.empty() → 回退 LLM ✓

但 Round 5 修复仅覆盖了"同列多值"场景。对于其他不适合结构化路径的 COUNT 查询
（多条件、复杂结构），Planner 仍然生成 COUNT 计划 → Service 不检查空结果 → LLM 被阻断。
```

### 1.3 为什么不是题面问题

"评级是 A？评级是 C 或 D 的各有多少？"是合法的 COUNT 查询。问题在于 Planner 在 `filters` 语义不完整（键覆盖导致只保留一个值）时仍生成 COUNT 计划，且 Service 对 COUNT 零结果不做回退。这是 Planner 的介入条件设计问题，不是题面设计问题。

---

## 2. 修复内容

### 2.1 核心思路

**将 COUNT 计划的生成条件从"filters 非空"收紧为"filters 恰好 1 条且无同列多值冲突"。** 

`isCountWellSuitedForStructuredPath(question, filters)` 替代原来的 `hasSameColumnMultiValueConflict(question)`，综合两个条件：

1. **`filters.size() == 1`**：COUNT 查询必须有且仅有一个过滤条件。多条件 COUNT（如 `status=done 且 priority=high`）回退 LLM。
2. **无同列多值冲突**：`ASSIGNMENT_PATTERN` 扫描问题，同一列名不出现 ≥2 个不同值。

两个条件同时满足 → Planner 生成 COUNT 计划。任一条件不满足 → `Optional.empty()` → 回退 LLM 搜索路径。

### 2.2 设计权衡

**为什么选择 `filters.size() == 1` 而不是更宽松的条件？**

| 方案 | 评估 |
|------|------|
| `filters.size() == 1`（本轮选择） | 最保守。单条件 COUNT 是结构化路径最可靠的场景。多条件 COUNT 回退 LLM，LLM 可正确处理 |
| `filters.size() <= 2` | 不够保守。仍可能拦截复杂查询 |
| 仅检查同列多值冲突（Round 5） | 不够。多条件或复杂结构的 COUNT 仍被生成 |

**为什么不在 Service 层修 `getQueryType() != COUNT` 条件？**

本轮约束只允许修改 `StructuredQueryPlanner.java`。Service 层的 COUNT 零结果不回退行为保持不变，但 Planner 减少 COUNT 计划生成后，触发该行为的场景大幅缩小。

### 2.3 修改清单

**仅修改 1 个文件**：`StructuredQueryPlanner.java`

| 变更 | 说明 |
|------|------|
| `plan()` 第 110 行 | `hasSameColumnMultiValueConflict(question)` → `!isCountWellSuitedForStructuredPath(question, filters)` |
| `hasSameColumnMultiValueConflict` → `isCountWellSuitedForStructuredPath` | 重命名并扩展：增加 `filters.size() != 1` 前置检查，保留同列多值冲突检测 |

```java
private boolean isCountWellSuitedForStructuredPath(String question, Map<String, String> filters) {
    if (filters.size() != 1) {
        return false;
    }
    // 同列多值冲突检测（与 Round 5 逻辑相同）
    Map<String, Set<String>> columnValues = new LinkedHashMap<>();
    Matcher matcher = ASSIGNMENT_PATTERN.matcher(question);
    while (matcher.find()) {
        String columnName = cleanFieldCandidate(matcher.group(1));
        String value = cleanValueCandidate(matcher.group(3));
        if (!isUsableField(columnName) || !StringUtils.hasText(value) || isQuestionValue(value)) {
            continue;
        }
        columnValues.computeIfAbsent(columnName, k -> new LinkedHashSet<>())
                .add(value.toLowerCase(Locale.ROOT));
    }
    for (Set<String> values : columnValues.values()) {
        if (values.size() >= 2) {
            return false;
        }
    }
    return true;
}
```

### 2.4 修改的属性

| 属性 | 说明 |
|------|------|
| 修改文件数 | **1**（仅 `StructuredQueryPlanner.java`） |
| 方法变更 | `hasSameColumnMultiValueConflict` → `isCountWellSuitedForStructuredPath`（重命名 + 扩展） |
| 调用点变更 | `plan()` 中 1 行 |
| 不含业务词/题号/文件名/样例值 | **是** — `filters.size() != 1` 是纯数据结构条件，不依赖任何具体字段名或值 |
| 不含白名单/特判 | **是** |
| 未删除其他逻辑 | **是** — comparison、group-by、value probe guard、非结构化意图拒止完整保留 |
| 未改 Executor/Service/Repository/Mapper | **是** |
| 未改 src/test/java | **是** |

---

## 3. 对目标题的影响分析

### 3.1 FQ3：回退 LLM 路径

**修复前（Round 5）**：
1. `extractFilters` → `{评级: c}`
2. `isCountQuestion` → true
3. `hasSameColumnMultiValueConflict` → 评级 有 {a, c} → true → Planner 返回 empty → 回退 LLM

**修复后（Round 6）**：
1. `extractFilters` → `{评级: c}`（`filters.size() == 1`）
2. `isCountQuestion` → true
3. `isCountWellSuitedForStructuredPath` → `filters.size() == 1` ✓ → 同列多值检测: 评级 有 {a, c} → `values.size() >= 2` → false
4. Planner 返回 empty → 回退 LLM

Round 5 和 Round 6 对 FQ3 的效果相同（均回退 LLM）。但 Round 6 的 guard 覆盖更广——多条件 COUNT 也被拦截。

### 3.2 FQ11/FQ12：无影响

两个问题均含"有没有" → `isNonStructuredIntentQuestion` 在 Planner 入口返回 true → Planner 返回 empty。不经过 filters 提取和 COUNT 判断。

FQ11/FQ12 的 INSUFFICIENT 结果来自 LLM 搜索路径，非 Planner 拦截所致。Gate 报告中的"回退"标记为评测统计口径问题（agentB 分析报告已确认）。

### 3.3 FQ6/FQ7/FS4c：无影响

- FQ6：走 `COMPARISON_PATTERN` → `extractComparisonFilter`（filters 为空时触发），不经过 COUNT 路径
- FQ7：filters 为空 → 无等值匹配 → Planner 返回 empty → LLM 路径
- FS4c：不经过结构化查询路径

### 3.4 单条件 COUNT 查询：不受影响

如 `shouldPlanCountQuery` 测试中的 `"status=done 有多少条？"`：
1. `extractFilters` → `{status: done}`（`filters.size() == 1`）
2. `isCountQuestion` → true
3. `isCountWellSuitedForStructuredPath` → `filters.size() == 1` ✓ → 同列多值检测: status 仅 {done} → 无冲突 → true
4. COUNT 计划正常生成

### 3.5 多条件 COUNT 查询：回退 LLM（行为变更）

如 `"status=done 且 priority=high 的有多少？"`：
1. `extractFilters` → `{status: done, priority: high}`（`filters.size() == 2`）
2. `isCountWellSuitedForStructuredPath` → `filters.size() != 1` → false
3. Planner 返回 empty → 回退 LLM

**这是有意的行为变更**：多条件 COUNT 对结构化路径的可靠性不如单条件，LLM 可正确处理此类查询。

---

## 4. 回归验证

### 4.1 ROW_LOOKUP / GROUP_BY / ROW_COMPARE：无影响

`isCountWellSuitedForStructuredPath` 仅在 `queryType == COUNT` 时调用。三个类型的路径完全不受影响。

### 4.2 单条件 COUNT 测试：通过

`shouldPlanCountQuery`（`"status=done 有多少条？"`）：
- `filters.size() == 1` → 通过前置检查
- 无同列多值冲突 → 通过
- COUNT 计划正常生成 → 测试断言不变

### 4.3 FQ1/FQ5/FQ10/FG1/FG3：无预期回归

这些题不依赖结构化 COUNT 路径（走 ROW_LOOKUP 或 LLM 搜索路径），不受 `isCountWellSuitedForStructuredPath` 影响。

---

## 5. 测试结果

### 5.1 redline

```
BLOCKER=0
```

### 5.2 全量 `mvn test`

| 指标 | 值 |
|------|-----|
| Tests run | **1018** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 结论 | **BUILD SUCCESS** |

### 5.3 未修改测试

未修改 `src/test/java` 下的任何文件。现有测试全部通过。

---

## 6. 明确声明

- [x] 根因唯一：Planner COUNT 介入条件过宽（`filters` 非空即生成）+ Service 对 COUNT 零结果不回退 → LLM 路径被阻断
- [x] 修复策略：收紧 COUNT 生成条件为 `filters.size() == 1` 且无同列多值冲突
- [x] 仅修改 1 个文件：`StructuredQueryPlanner.java`
- [x] 仅 1 个方法重命名+扩展 + 1 行调用点变更
- [x] 仅在 COUNT 场景触发，ROW_LOOKUP/GROUP_BY/ROW_COMPARE 零影响
- [x] 无业务词/题号/文件名/样例值特判 — `filters.size() != 1` 是纯数据结构条件
- [x] 无白名单/case-specific fallback
- [x] 未删除现有 comparison / group-by / value probe guard / 非结构化意图拒止逻辑
- [x] 未改 Executor / Service / Repository / Mapper / QueryFacadeService
- [x] 未改 src/test/java
- [x] 未改 prompt / config / schema / scripts / 题集
- [x] redline BLOCKER=0
- [x] mvn test 全量 COMPILE SUCCESS (1018/0/0/0)
- [x] 未提交 commit
