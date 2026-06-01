# B13b: AST Domain 领域对象 @Data 降级 + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B13b（B13 第 2/最后子批次，7/14 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 字段数 | 变更 |
|---|---|---|---|
| `AstEntityType.java` | 枚举 | 4 | 枚举值 Javadoc（CLASS/INTERFACE/ENUM/METHOD 语义） |
| `AstRelation.java` | 可变模型 | 8 | @Data→@Getter @Setter，8 字段 Javadoc |
| `AstFact.java` | 可变模型 | 9 | @Data→@Getter @Setter，9 字段 Javadoc |
| `AstGraphExtractReport.java` | 可变模型 | 4 | @Data→@Getter @Setter，4 字段 Javadoc |
| `AstEntity.java` | 可变模型 | 9 | @Data→@Getter @Setter，9 字段 Javadoc |
| `AstSourceFile.java` | 可变模型 | 4 | @Data→@Getter @Setter，4 字段 Javadoc（content 大文本标注） |
| `AstExtractionResult.java` | 累加器 | 4 | 字段级 @Getter（3 个 list），保留所有业务方法+warnings() |

---

## 2. @Data 降级汇总

| 类 | 降级前 | 降级后 |
|---|---|---|
| `AstRelation` | @Data | @Getter @Setter |
| `AstFact` | @Data | @Getter @Setter |
| `AstGraphExtractReport` | @Data | @Getter @Setter |
| `AstEntity` | @Data | @Getter @Setter |
| `AstSourceFile` | @Data | @Getter @Setter |

**5 个 @Data 全部降级为 @Getter @Setter**。0 残留。

---

## 3. AstExtractionResult 特殊处理

| 字段 | 处置 | 原因 |
|---|---|---|
| `entities` | `@Getter`（字段级） | 需要 getEntities()，Lombok 生成 |
| `facts` | `@Getter`（字段级） | 需要 getFacts()，Lombok 生成 |
| `relations` | `@Getter`（字段级） | 需要 getRelations()，Lombok 生成 |
| `warnings` | 无 @Getter | 已存在 `warnings()` 方法（非 get 前缀），免冲突 |

**保留方法**：`empty()`、`addEntity()`、`addFact()`、`addRelation()`、`addWarning()`、`merge()`、`isEmpty()`、`warnings()`。

---

## 4. Lombok 统计

| 注解 | 数量 | 用途 |
|---|---|---|
| `@Getter`（类级） | 5 | 可变模型 |
| `@Setter`（类级） | 5 | 可变模型 |
| `@Getter`（字段级） | 3 | AstExtractionResult entities/facts/relations |
| **合计** | **13** | |

**B13 合计（B13a + B13b = 14 类）**：12+13=25 @Getter，0+5=5 @Setter，58+0=58 getter 删除，5 @Data 降级。

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data' ast/domain: (无结果) ✓
@Getter @Setter: 5 个可变模型 ✓
AstExtractionResult @Getter: 字段级 3 个（entities/facts/relations） ✓
warnings() 保留: 第 83 行，无 getWarnings() ✓
merge() 保留: 第 65 行 ✓
```

---

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 7 个目标文件 | 通过 |
| 5 个 @Data 全量降级 | 通过（0 残留） |
| AstExtractionResult add/merge/warnings 保留 | 通过 |
| AstSourceFile.content 大文本标注 | 通过 |
| 未修改 B13a | 通过 |
| 未 stage/commit/push | 通过 |
