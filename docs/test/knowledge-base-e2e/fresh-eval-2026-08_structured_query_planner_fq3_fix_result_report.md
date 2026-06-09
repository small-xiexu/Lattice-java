# fresh-eval-2026-08 FQ3 同列多值冲突修复结果报告（Task C Round 5）

时间：2026-06-07
执行人：agentA（代码执行 Agent）
依据：agentB 只读根因分析报告 + architect 分配的最小修复任务

---

## 1. 根因定位

### 1.1 唯一根因

**`extractFilters()` 使用 `LinkedHashMap` 存储列名→值的映射，当 `ASSIGNMENT_PATTERN` 对同一字段匹配到多个不同值时，后续值会覆盖先前值，导致生成错误的 COUNT 计划。Planner 缺少"同列多值冲突"检测。**

### 1.2 FQ3 完整故障链路

```
问题: "供应商台账里有多少家供应商的评级是 A？评级是 C 或 D 的各有多少？"

1. ASSIGNMENT_PATTERN 匹配:
   - "评级是 A" → columnName="评级", value="A"
   - "评级是 C" → columnName="评级", value="C"

2. extractFilters() → LinkedHashMap.put("评级", "a") → put("评级", "c")
   → filters = {评级: c}  ← "A" 被覆盖丢失

3. isCountQuestion → true → queryType = COUNT

4. Executor.countRowsByFilters({评级: c}) → 仅统计 评级=c 的行数

5. 答案只报告 评级=C 的计数，遗漏 评级=A 的计数
```

**`ASSIGNMENT_PATTERN` 的匹配没有错，`LinkedHashMap` 的去重行为也没有错。错误在于 Planner 没有检测"多个等值匹配指向同一列但不同值"的场景，在语义已偏离用户意图的情况下仍然生成了 COUNT 计划。**

### 1.3 为什么不是题面问题

"评级是 A？评级是 C 或 D 的各有多少？"是自然的 COUNT 查询形式——先问单一值计数，再问多值的各自计数。问题本身是清晰的，问题出在 Planner 无法表达"同列多值"的过滤语义。这是 Planner 能力边界问题，不是题面设计问题。

---

## 2. 修复内容

### 2.1 核心思路

**检测并拒绝，而非尝试修复。** 在生成 COUNT 计划前，加一个通用检测：如果 `ASSIGNMENT_PATTERN` 命中同一字段出现 ≥2 个不同值，则 Planner 返回 `Optional.empty()`，让查询回退到 LLM 搜索路径处理。

选择"拒绝"而非"修复"的理由：
- 支持同列多值需要改 Executor/Repository/SQL 整个下游链，改动面过大
- FQ3 这类同列多值 COUNT 查询在 LLM 搜索路径（Round 2 FTS OR 语义修复后）可以得到 PARTIAL 级别的答案
- 拒绝策略改动最小（仅 Planner 内加一个检测方法），风险可控

### 2.2 修改清单

**仅修改 1 个文件**：`StructuredQueryPlanner.java`

#### 修改 1：`plan()` 方法增加同列多值冲突检测

```java
if (!filters.isEmpty()) {
    StructuredQueryType queryType = isCountQuestion(question)
            ? StructuredQueryType.COUNT
            : StructuredQueryType.ROW_LOOKUP;
    if (queryType == StructuredQueryType.COUNT && hasSameColumnMultiValueConflict(question)) {
        return Optional.empty();  // 同列多值冲突，拒绝生成 COUNT 计划
    }
    // ... 原有逻辑不变
}
```

仅在 `queryType == COUNT` 时触发检测。ROW_LOOKUP、GROUP_BY、ROW_COMPARE 路径不受影响。

#### 修改 2：新增 `hasSameColumnMultiValueConflict` 方法

```java
private boolean hasSameColumnMultiValueConflict(String question) {
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
            return true;
        }
    }
    return false;
}
```

通用检测逻辑：
- 用 `ASSIGNMENT_PATTERN` 重新扫描问题（与 `extractFilters` 使用相同的模式匹配）
- 用与 `extractFilters` 完全相同的 `cleanFieldCandidate` / `cleanValueCandidate` / `isQuestionValue` / `isUsableField` 过滤逻辑
- 按列名聚合所有不同值到 `LinkedHashSet`
- 任一列名出现 ≥2 个不同值 → 返回 true

### 2.3 修改的属性

| 属性 | 说明 |
|------|------|
| 修改文件数 | **1**（仅 `StructuredQueryPlanner.java`） |
| 新增方法 | **1**（`hasSameColumnMultiValueConflict`，纯只读检测） |
| 新增 imports | `LinkedHashSet`、`Set` |
| 不含业务词/题号/文件名/样例值 | **是** — 检测逻辑基于 ASSIGNMENT_PATTERN 匹配结果的列名-值聚合，不涉及任何具体字段名、评级值、文档名 |
| 不含白名单/特判 | **是** |
| 未删除现有逻辑 | **是** — comparison、group-by、value probe guard、非结构化意图拒止均完整保留 |
| 未改 Executor/Service/Repository/Mapper | **是** |
| 未改 src/test/java | **是** |

---

## 3. 对目标题的影响分析

### 3.1 FQ3：恢复 PARTIAL

**修复前**：Planner 生成 `COUNT {评级: c}` 错误计划 → 执行返回错误答案 → user 看到错误计数

**修复后**：
1. `extractFilters` → `{评级: c}`（同前）
2. `isCountQuestion` → true
3. `hasSameColumnMultiValueConflict` → 检测到 "评级" 有 2 个不同值（a, c）→ 返回 true
4. Planner 返回 `Optional.empty()`
5. 查询回退到 LLM 搜索路径 → Round 2 FTS OR 语义可召回 YAML 中的评级信息 → LLM 可从证据中部分计数

**预期结果**：恢复到 Round 2 的 PARTIAL 水平（LLM 可能无法精确计数，但不会给出错误的单一值计数）。

### 3.2 FQ11/FQ12：无影响

两个问题均含"有没有" → `isNonStructuredIntentQuestion` 返回 true → Planner 在入口处返回 `Optional.empty()`。不会执行到 `extractFilters` 和 `hasSameColumnMultiValueConflict`。

### 3.3 FQ6/FQ7/FS4c：无影响

- FQ6：走 COMPARISON_PATTERN → `extractComparisonFilter` 路径（filters 为空时触发），不经过 COUNT 检测
- FQ7：filters 为空 → 无等值匹配 → 走 `extractComparisonFilter`（返回 null）→ Planner 返回 empty
- FS4c：不经过结构化查询路径

### 3.4 对其他题的回归验证

`hasSameColumnMultiValueConflict` 仅在 `queryType == COUNT` 时调用。对以下场景无影响：
- **单值 COUNT 查询**（如 "评级是 A 的有多少家？"）：同列仅 1 个值 → `values.size() == 1` → 不触发冲突检测 → COUNT 计划正常生成
- **ROW_LOOKUP 查询**：不触发检测
- **GROUP_BY 查询**：在 `plan()` 中 GROUP_BY 分支在 filters 非空分支之前，独立处理
- **ROW_COMPARE 查询**：在 `isCompareQuestion` 分支处理，不经过 COUNT 检测

---

## 4. 测试结果

### 4.1 redline

```
BLOCKER=0
```

### 4.2 全量 `mvn test`

| 指标 | 值 |
|------|-----|
| Tests run | **1018** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 结论 | **BUILD SUCCESS** |

### 4.3 未修改测试

未修改 `src/test/java` 下的任何文件。现有测试断言全部保持原样通过。

---

## 5. 明确声明

- [x] 根因唯一：`extractFilters` 的 `LinkedHashMap` 对同列多值做键覆盖 → COUNT 计划语义偏离
- [x] 修复策略：检测同列多值冲突后拒绝生成 COUNT 计划，回退到 LLM 搜索路径
- [x] 仅修改 1 个文件：`StructuredQueryPlanner.java`
- [x] 仅新增 1 个只读检测方法 + 2 个 import
- [x] 仅在 COUNT 场景触发，不影响 ROW_LOOKUP/GROUP_BY/ROW_COMPARE
- [x] 无业务词/题号/文件名/样例值特判
- [x] 无白名单/case-specific fallback
- [x] 未删除现有 comparison / group-by / value probe guard / 非结构化意图拒止逻辑
- [x] 未改 Executor / Service / Repository / Mapper / QueryFacadeService
- [x] 未改 src/test/java
- [x] 未改 prompt / config / schema / scripts / 题集
- [x] redline BLOCKER=0
- [x] mvn test 全量 COMPILE SUCCESS (1018/0/0/0)
- [x] 未提交 commit
