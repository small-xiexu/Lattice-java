# api/query 结构化证据 DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）
批次：B2

---

## 1. 修改文件清单

| 文件 | 变更 |
|---|---|
| `QueryStructuredEvidenceResponse.java` | 类级 @Getter + 3 字段 Javadoc + 删除 3 手写 getter |
| `QueryStructuredGroupEvidenceResponse.java` | 类级 @Getter + 5 字段 Javadoc + 删除 5 手写 getter |
| `QueryStructuredRowEvidenceResponse.java` | 类级 @Getter + 5 字段 Javadoc + 删除 5 手写 getter |
| `QueryStructuredCellEvidenceResponse.java` | 类级 @Getter + 5 字段 Javadoc + 删除 5 手写 getter |
| `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` | B2 状态回写 + "当前下一步" 更新 |

**无调用点迁移。** 构造器、`@JsonCreator`、`@JsonProperty` 均未修改。

---

## 2. 层级语义说明

四个类形成 `Evidence → Row → Cell` 的三层证据结构：

```
QueryStructuredEvidenceResponse     （顶层，含 queryType + rows + groups）
  ├── QueryStructuredRowEvidenceResponse  （行级，含 sourcePath + tableName + cells）
  │     └── QueryStructuredCellEvidenceResponse （单元格级，含 columnName + cellValue + role）
  └── QueryStructuredGroupEvidenceResponse（聚合级，含 groupByField + count + filters）
```

每个字段的 Javadoc 都说明了它在对应层级中的角色、调用方如何消费、为空时的含义。

### 2.1 QueryStructuredEvidenceResponse（3 字段，顶层容器）

| 字段 | 注释要点 |
|---|---|
| `queryType` | 查询类型标识（structured_query/table_lookup），调用方据此选择展示布局 |
| `rows` | 行级证据列表，每条对应一行原始数据，构造器 null→空列表 |
| `groups` | 分组聚合统计，展示数据分布概览，构造器 null→空列表 |

### 2.2 QueryStructuredGroupEvidenceResponse（5 字段，聚合统计）

| 字段 | 注释要点 |
|---|---|
| `groupByField` | 分组字段名 |
| `groupValue` | 分组原始值（未归一化） |
| `normalizedGroupValue` | 归一化后的值，用于跨数据源一致对比 |
| `count` | 该分组下匹配行数 |
| `filters` | 过滤条件集合，构造器 null→空 Map |

### 2.3 QueryStructuredRowEvidenceResponse（5 字段，行级溯源）

| 字段 | 注释要点 |
|---|---|
| `sourcePath` | 来源文件路径，调用方生成可点击文件链接 |
| `tableName` | 表名或结构化数据标识 |
| `sheetName` | XLSX 的 sheet 名，单表为空 |
| `rowNumber` | 原始行号，帮助用户在源文件中定位 |
| `cells` | 该行所有单元格证据，构造器 null→空列表 |

### 2.4 QueryStructuredCellEvidenceResponse（5 字段，最细粒度）

| 字段 | 注释要点 |
|---|---|
| `columnName` | 列名/字段名，标识单元格语义含义 |
| `columnIndex` | 列序号（从 0 开始） |
| `cellValue` | 原始单元格值（未归一化） |
| `normalizedValue` | 归一化值，利于程序化比较 |
| `role` | 证据角色（primary/context/reference），决定展示优先级和样式 |

---

## 3. Lombok 使用

| 类 | 注解 | 替代 getter 数 |
|---|---|---|
| `QueryStructuredEvidenceResponse` | 类级 `@Getter` | 3 |
| `QueryStructuredGroupEvidenceResponse` | 类级 `@Getter` | 5 |
| `QueryStructuredRowEvidenceResponse` | 类级 `@Getter` | 5 |
| `QueryStructuredCellEvidenceResponse` | 类级 `@Getter` | 5 |
| **合计** | | **18** |

**未使用：** `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Builder`

---

## 4. 保留内容

- 全部 `@JsonCreator` 构造器（参数名、`@JsonProperty` 注解未变）
- 构造器内 null 归一化逻辑（rows/groups/cells→空列表，filters→空 Map）
- Jackson 序列化/反序列化语义

---

## 5. 测试与 Redline

```
mvn test
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

---

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 api/query 下 4 个结构化证据 DTO | 通过 |
| 未修改 query/retrieval/answer/fallback/citation 主链 | 通过 |
| @JsonCreator/@JsonProperty/构造器语义未改 | 通过 |
| 未修改 src/test/java | 通过 |
| 未修改 scripts/scan-redline.sh、special_cases_report.md | 通过 |
| 未使用 @Data/@Setter/@AllArgsConstructor | 通过 |
| 未扩大到 B3 或 api/admin | 通过 |
| 未 stage/commit/push | 通过 |

---

## 7. 残留风险

无。所有 getter 均为简单字段访问，Lombok 生成行为与原手写一致。构造器 null 归一化逻辑保留在构造器中，不受 `@Getter` 影响。
