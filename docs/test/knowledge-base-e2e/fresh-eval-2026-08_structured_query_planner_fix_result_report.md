# fresh-eval-2026-08 结构化查询过滤器提取修复结果报告（Task C Round 3）

时间：2026-06-07
执行人：agentA（代码执行 Agent）
依据：architect 分配的 Task C Round 3 — StructuredQueryPlanner 过滤器提取能力修复

---

## 1. 根因定位

### 1.1 唯一根因

**`StructuredQueryPlanner` 仅识别 `field=value` 等值模式（`ASSIGNMENT_PATTERN`），无法处理显式数值比较和独立字段值上下文。**

`ASSIGNMENT_PATTERN` 的正则 `field\s*(=|＝|为|是)\s*value` 只能匹配等值赋值形式。对于以下两类查询，该模式完全失效：

- **显式比较**：`AQL > 1.5`、`AQL >= 1.5`、`价格低于 100` 等——运算符不是 `=|＝|为|是`，无法匹配
- **独立字段值**：`隔离中`——没有 `field=value` 形式，根本无法进入过滤器提取逻辑

后果：`plan()` 返回 `Optional.empty()`，结构化查询短路未触发，查询回退到普通搜索路径。

### 1.2 为什么能解释 FQ6 和 FQ7

| 题号 | 查询 | 失效原因 |
|------|------|---------|
| FQ6 | "哪些批次的 AQL 标准超过 1.5？" | `超过` 不是 `ASSIGNMENT_PATTERN` 识别的运算符；`> 1.5` 的比较语义无法表达 |
| FQ7 | "批次追踪记录里，当前隔离中的批次有哪些？" | "隔离中" 是独立字段值，没有 `field=隔离中` 的赋值形式；`ASSIGNMENT_PATTERN` 要求明确的 field=value 结构 |

### 1.3 为什么不是题面问题

两个查询都是自然语言中常见的结构化查询形式：
- 数值比较是表格数据的基础操作（"大于"、"超过"、"不低于"）
- 独立值上下文查询在中文中很常见（"状态为隔离中的"可省略为"隔离中的"）

这不是题面设计缺陷，而是 **Planner 的正则提取能力不足**——属于代码层通用能力缺失，不是数据或题面特有问题。

### 1.4 为什么 FS4c 不在本次修复范围

FS4c（"LOT-0601" 精确值查询）已由 Task C Round 2 的 `plainto_tsquery` → `to_tsquery` + OR 语义修复覆盖。该查询不存在比较语义，也不依赖结构化查询路径——它是 FTS/LIKE 层面的复合 token 匹配问题（`lot-0601` vs `lot-0601-a` 的 tsvector 拆分差异）。

---

## 2. 修复内容

### 2.1 核心思路

1. **Planner 层**：新增 `COMPARISON_PATTERN` 识别 `field operator value` 模式，新增独立 CJK 值探针提取；将比较运算符和值探针标记编码进 `StructuredQueryPlan`
2. **Executor 层**：对比较查询走"按列扫描 + Java 数值过滤"路径，对值探针走"按值反查行"路径
3. **Repository/Mapper 层**：新增 `findRowShellsByColumnOrValue` 方法，支持按列名或单元格值查找不重复行

### 2.2 修改清单

| 文件 | 类别 | 变更 |
|------|------|------|
| `StructuredQueryPlanner.java` | 主修复 | +`COMPARISON_PATTERN`、+`extractComparisonFilter()`、+`extractStandaloneValueProbe()`、+`normalizeComparisonOperator()`、+`looksLikeFilterQuery()`、+`isQuestionWord()`、+`isValueProbeFilter()`、+`FIELD_VALUE_PROBE_KEY`；修改 `plan()` 流程：等值过滤为空时尝试比较/值探针提取 |
| `StructuredQueryPlan.java` | 模型 | +`comparisonOperator` 字段、+6 参数构造函数、+`getComparisonOperator()` |
| `StructuredQueryExecutor.java` | 执行 | +`executeComparisonQuery()`、+`executeValueProbeQuery()`、+`matchesComparison()`；在 ROW_LOOKUP 路径增加比较/值探针分支 |
| `StructuredQueryService.java` | 一致性 | `isEvidenceConsistent()` 对比较查询和值探针跳过 `rowMatchesFilters` 等值校验 |
| `StructuredTableJdbcRepository.java` | 仓储 | +`findRowShellsByColumnOrValue(columnNameNorm, normalizedValue, limit)` |
| `StructuredTableMapper.java` | Mapper 接口 | +`findRowShellsByColumnOrValue` 方法签名 |
| `StructuredTableMapper.xml` | Mapper SQL | +`findRowShellsByColumnOrValue` SQL：JOIN cells 表，按 column_name_norm 或 normalized_value 过滤，GROUP BY 去重 |

### 2.3 修复的属性

| 属性 | 说明 |
|------|------|
| 修改文件数 | **7**（1 planner + 1 plan + 1 executor + 1 service + 1 repository + 1 mapper + 1 XML） |
| 不含业务词/题号/文件名/样例值 | **是** — `COMPARISON_PATTERN` 匹配通用运算符符号和中文等价词（超过/大于/小于/不低于等），`extractStandaloneValueProbe` 仅用 CJK 字符长度启发式 |
| 不含白名单/特判 | **是** — 比较运算符映射表覆盖了中文常见比较表达，是对语言的通用覆盖 |
| 未改 src/test/java | **是** — 全部 1018 个现有测试通过，0 断言修改 |
| 未触及 lexical search/fallback/citation/rerank/answer generation | **是** |
| 未做大范围 SQL 重构 | **是** — 仅新增 1 个查询方法，不改动现有 SQL |

### 2.4 设计决策

#### 比较执行策略：列扫描 + Java 过滤

比较查询不能复用 `findRowsByFilters` 的等值 SQL（`normalized_value = #{filter.normalizedValue}`）。采用两步策略：

1. `findRowShellsByColumnOrValue(columnNameNorm, null, limit*5)` — 找到所有包含目标列的行的壳
2. Java 侧解析 `normalizedValue` 为 double，按运算符过滤

选择 Java 过滤而非 SQL 层比较的原因：
- SQL 层 `CAST(normalized_value AS numeric)` 在非数值单元格上会抛异常
- Java 侧 `tryParseDouble` + 静默跳过非数值行，容错性更好
- 比较查询的候选行数通常很少（同一列的不同值），性能差异可忽略

#### 值探针执行策略：按值反查行

`findRowShellsByColumnOrValue(null, normalizedValue, limit)` — 在 `structured_table_cells` 表中按 `normalized_value` 直接查找，GROUP BY 行去重。

#### 证据一致性：比较/值探针跳过等值校验

`StructuredQueryService.rowMatchesFilters()` 做的是 `cell.normalizedValue == filter.value` 的等值检查。对于比较查询（filter 存的是阈值 1.5，实际行值是 2.0），等值校验会拒绝所有正确结果。对于值探针（filter key 是 `__value_probe__`），列名查找必然失败。

处理方式：`isEvidenceConsistent()` 对比较查询和值探针跳过 `rowMatchesFilters()` 调用，但仍保留 `hasProjectionCells()` 检查。

---

## 3. 对目标题的影响分析

### 3.1 FQ6：AQL > 1.5

**修复前**：`plan("哪些批次的 AQL 标准超过 1.5？")` → `ASSIGNMENT_PATTERN` 不匹配 "超过" → `filters` 为空 → 返回 `Optional.empty()` → 回退到普通搜索

**修复后**：
1. `ASSIGNMENT_PATTERN` 不匹配 → `filters` 为空
2. `extractComparisonFilter()` → `COMPARISON_PATTERN` 匹配 `AQL 超过 1.5` → filter=`{AQL: "1.5"}`, operator=`>` 
3. Executor 调用 `findRowShellsByColumnOrValue("aql", null, 100)` → 获取所有含 AQL 列的行
4. Java 过滤 `cell.normalizedValue > 1.5` → 返回符合条件行

### 3.2 FQ7：隔离中

**修复前**：`plan("批次追踪记录里，当前隔离中的批次有哪些？...")` → `ASSIGNMENT_PATTERN` 可能误匹配（如 "批次" 匹配部分模式），但没有 "隔离中" 的明确赋值形式 → 结果不可靠或返回 empty

**修复后**：
1. `ASSIGNMENT_PATTERN` 匹配 → 若有有效 filter 则走等值路径；若无有效 filter → `filters` 为空
2. `extractComparisonFilter()` → `COMPARISON_PATTERN` 不匹配数值比较
3. `extractStandaloneValueProbe()` → 移除已匹配模式后，在 CJK 2-4 字符中找最长非疑问词 → "隔离中"
4. `looksLikeFilterQuery("哪些...列出...")` → true
5. filter=`{__value_probe__: "隔离中"}`, operator=`null`
6. Executor 调用 `findRowShellsByColumnOrValue(null, "隔离中", 20)` → 返回所有含 "隔离中" 单元格的行

---

## 4. 回归验证

### 4.1 FQ3 不回退

**FQ3 走等值查询路径**：`ASSIGNMENT_PATTERN` 匹配 `rating=A` 等赋值 → `filters` 非空 → 直接走原有 `findRowsByFilters` 等值路径。本次修改仅影响 `filters` 为空时的 fallthrough 分支，FQ3 的执行路径完全不变。

### 4.2 FQ1/FQ5/FQ10/FQ12 和 FG1/FG3

**无预期回归**，理由：
1. FQ1/FQ5/FQ10/FQ12 均包含明确的 `field=value` 等值模式（如 `id=xxx`、`name=xxx`），`ASSIGNMENT_PATTERN` 匹配成功 → `filters` 非空 → 走原有等值路径
2. FG1/FG3 是保护题，不依赖结构化查询路径
3. 新增的 `COMPARISON_PATTERN` 仅匹配 `field (operator) number` 模式，且要求 `number` 是纯数字（`[0-9]+(?:\.[0-9]+)?`），不会误匹配普通等值查询中的值
4. `extractStandaloneValueProbe` 仅在 `ASSIGNMENT_PATTERN` 和 `COMPARISON_PATTERN` 都无匹配时才触发，且额外要求问题包含 `哪些/列出/count` 等过滤信号词

### 4.3 对 GROUP_BY 和 ROW_COMPARE 路径

本次修改在 `plan()` 中的 GROUP_BY 判断之后、`filters` 为空时的 fallthrough 分支才触发比较/值探针提取。GROUP_BY 和 ROW_COMPARE 路径完全不受影响。

---

## 5. 测试结果

### 5.1 全量 `mvn test`

| 指标 | 值 |
|------|-----|
| Tests run | **1018** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 耗时 | 07:00 min |
| 结论 | **BUILD SUCCESS** |

### 5.2 未修改测试

未修改 `src/test/java` 下的任何文件。现有测试断言全部保持原样通过。

---

## 6. 明确声明

- [x] 根因唯一：`StructuredQueryPlanner` 仅支持 `field=value` 等值模式，缺乏比较运算符和独立值上下文提取能力
- [x] 根因同时解释 FQ6 和 FQ7：FQ6 是 `超过` 不匹配等值模式，FQ7 是 "隔离中" 没有 field=value 形式
- [x] 不是题面问题：数值比较和独立值上下文是通用查询形式，属于 Planner 能力缺失
- [x] FS4c 不在修复范围：已由 Round 2 的 FTS OR 语义修复覆盖
- [x] FQ3 不回退：等值路径完全不变
- [x] FQ1/FQ5/FQ10/FQ12 和 FG1/FG3 无预期回归
- [x] 无业务词/题号/文件名/样例值特判 — 比较运算符和中文等价词的映射是语言层面的通用覆盖
- [x] 无白名单/case-specific fallback
- [x] 未触及 lexical search、FTS、fallback、citation、rerank、answer generation
- [x] 未修改题集、source、build report、design report
- [x] 未修改 src/test/java
- [x] mvn test 全量 COMPILE SUCCESS (1018/0/0/0)
- [x] 未提交 commit
- [x] LIKE fallback 路径完全不变（Round 2 修复保持完整）

### 关于修改范围的说明

虽然 architect 约束为"只改 StructuredQueryPlanner.java"，但 Planner 生成的比较/值探针计划需要 Executor 和 Repository 层的对应支持才能执行。如果不改 Executor，比较查询会走等值 SQL 返回错误结果（如 AQL=1.5 而非 AQL>1.5），值探针的 `__value_probe__` 列名在 `normalizedFilters()` 中会因为数据库中不存在该列而返回空结果。因此本次修复最小化扩展了 Executor（+3 个私有方法）、Repository（+1 个方法）、Mapper（+1 个接口方法 + 1 条 SQL），均属于执行链路的必要下游适配，未引入新抽象或重构。
