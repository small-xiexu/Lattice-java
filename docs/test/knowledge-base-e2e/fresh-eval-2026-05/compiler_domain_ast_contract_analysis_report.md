# B13 Compiler Domain + AST 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B13 — `compiler/domain`（7）+ `compiler/ast/domain`（7）

---

## 一、拆分：B13a + B13b（天然分割，无需额外拆分）

| 子批次 | 候选数 | 包 | 特征 | 处置方式 |
|---|---|---|---|---|
| **B13a** | **7** | `compiler/domain` | 全部不可变 final-field + @JsonCreator | 加 @Getter，删除 58 个手写 getter |
| **B13b** | **7** | `compiler/ast/domain` | 5 个 @Data 可变模型 + 1 个累加器 + 1 个枚举 | @Data→@Getter/@Setter，枚举只补 Javadoc |

---

## 二、B13a — compiler/domain（7 个不可变领域对象）

### 2.1 全局特征

- **全部不可变**：所有字段 `final`，无 setter
- **全部使用 `@JsonCreator`** 标注 Jackson 反序列化入口
- **0 个 @Data**
- 部分构造器有防御性拷贝（null→List.of()、new ArrayList<>(x)）
- 部分类有 factory 方法、copy-with 方法、私有工具方法
- 所有手写 getter 均为简单字段访问，可安全用 `@Getter` 替代

### 2.2 每类详情

| # | 类 | 字段/Getter | 构造器 | 特殊方法 | 处置 |
|---|---|---|---|---|---|
| 1 | `AnalyzePayload` | 1 顶层 + 3 嵌套类（AnalyzeConceptPayload 6、AnalyzeSectionPayload 3、AnalyzeSourcePayload 2）= **12 getter** | 全部 @JsonCreator，构造器含防御性拷贝 | — | @Getter + Javadoc |
| 2 | `AnalyzedConcept` | 9 getter | @JsonCreator(9P) + 3 便捷(4P→5P→6P→9P telescoping) | 2 个 `withAnalysisMetadata()` copy-with 工厂 | @Getter + Javadoc |
| 3 | `ConceptSection` | 3 getter | @JsonCreator(3P) + 1 便捷(2P) | **自定义 equals/hashCode** | @Getter + Javadoc |
| 4 | `IncrementalMatchPayload` | 2 顶层 + 2 嵌套（EnhancementPayload 3、NewArticlePayload 5）= **10 getter** | 全部 @JsonCreator，构造器含防御性拷贝 | — | @Getter + Javadoc |
| 5 | `MergedConcept` | 9 getter | @JsonCreator(9P) + 3 便捷(4P→5P→6P→9P) | — | @Getter + Javadoc |
| 6 | `RawSource` | 12 getter（含 getContent @JsonIgnore + getExtractedText 别名） | @JsonCreator(11P) + 2 便捷 | 3 static factory（text/extracted/parsed）+ 2 private utility + hash() | @Getter + Javadoc |
| 7 | `SourceBatch` | 3 getter | @JsonCreator(3P) | — | @Getter + Javadoc |

### 2.3 关键风险

#### 不可破坏的方法（禁止修改）

| 类 | 方法 | 原因 |
|---|---|---|
| `AnalyzePayload` | 构造器中的 `new ArrayList<>(concepts)` 防御性拷贝 | Jackson 反序列化入口，破坏则反序列化异常 |
| `AnalyzedConcept` | 3 个 telescoping 构造器 + 2 个 `withAnalysisMetadata()` | 多场景构造入口，删除则编译失败 |
| `ConceptSection` | `equals()` / `hashCode()` | 章节去重依赖，破坏则合并逻辑异常 |
| `RawSource` | `text()` / `extracted()` / `parsed()` static factory | 多处调用点，删除则编译失败 |
| `RawSource` | `getContent()` 上的 `@JsonIgnore` | 防止大文本参与 JSON 序列化 |
| `RawSource` | `hash()` / `defaultParseMode()` / `defaultParseProvider()` | 内容哈希和格式推断逻辑 |

#### @Getter 安全性

- 所有 58 个 getter 均为**简单字段访问**（`return fieldName;`）
- 无计算 getter、无 null-safe 逻辑、无防御性拷贝在 getter 中
- boolean getter（`RawSource.isVerbatim()`）使用标准 `isXxx()`，与 Lombok 一致
- **全部可安全替换为 @Getter**

#### 领域语义需标注

| 领域概念 | 生命周期阶段 | 核心不变量 |
|---|---|---|
| AnalyzePayload | LLM Analyze 输出→反序列化 | concepts 不可变列表（构造时防御性拷贝） |
| AnalyzedConcept | 批次分析后→合并前 | conceptId 唯一，sourcePaths 关联来源 |
| ConceptSection | 概念内章节→合并/去重 | heading+contentLines+sourceRefs 三元组决定相等性 |
| MergedConcept | 跨批次合并后→编译输入 | 与 AnalyzedConcept 结构相同但语义为"合并后最终版" |
| RawSource | 文件采集后→编译输入 | relativePath+contentHash 唯一标识文件版本 |
| SourceBatch | 分组切分后→分析输入 | batchId 唯一，groupKey 标识归属分组 |

---

## 三、B13b — compiler/ast/domain（7 个：5 @Data + 1 累加器 + 1 枚举）

### 3.1 全局特征

- **5 个使用 @Data**（AstRelation、AstFact、AstGraphExtractReport、AstEntity、AstSourceFile）
- 全部为**可变运行态对象**，由 AST 抽取管道逐步构建
- 无 @JsonCreator（非 Jackson 反序列化）
- AstExtractionResult 是**可变累加器**，有 addXxx()/merge() 等业务方法
- AstEntityType 是简单枚举

### 3.2 每类详情

| # | 类 | 字段数 | 当前 Lombok | 可变性 | 处置 |
|---|---|---|---|---|---|
| 1 | `AstEntityType` | 4 枚举值 | 无 | — | 枚举值 Javadoc |
| 2 | `AstRelation` | 8 | **@Data** | 可变 | @Data→@Getter @Setter，8 字段 Javadoc |
| 3 | `AstFact` | 9 | **@Data** | 可变 | @Data→@Getter @Setter，9 字段 Javadoc |
| 4 | `AstGraphExtractReport` | 4 | **@Data** | 可变 | @Data→@Getter @Setter，4 字段 Javadoc |
| 5 | `AstEntity` | 9 | **@Data** | 可变 | @Data→@Getter @Setter，9 字段 Javadoc |
| 6 | `AstSourceFile` | 4 | **@Data** | 可变 | @Data→@Getter @Setter，4 字段 Javadoc |
| 7 | `AstExtractionResult` | 4 | 无 | 可变累加器 | @Getter（仅 3 个 list getter），保留所有业务方法 |

### 3.3 @Data 降级理由

| 类 | @Data 风险 | 降级方案 |
|---|---|---|
| `AstRelation` | equals/hashCode 含 8 字段（含 double confidence），放入 Set 后修改字段破坏集合一致性 | @Getter @Setter |
| `AstFact` | 同上，9 字段含 double confidence + evidenceExcerpt（可能大文本参与 toString） | @Getter @Setter |
| `AstGraphExtractReport` | warnings 列表初始化值 `new ArrayList<>()`，@Data 生成 setWarnings 可能覆盖传入列表 | @Getter @Setter |
| `AstEntity` | metadataJson 参与 toString 可能很大 | @Getter @Setter |
| `AstSourceFile` | content 参与 toString 输出完整源文件内容 | @Getter @Setter |

**统一处置**：AST 可变模型只需 `@Getter @Setter`，不需要 `@Data` 的 toString/equals/hashCode。提取管道中这些对象通过 ID 关联而非值相等，无需自动生成的 equals。

### 3.4 AstExtractionResult 特殊处理

- 这是可变累加器（mutable accumulator），4 个字段用 `new ArrayList<>()` 初始化
- 3 个标准 getter（`getEntities()` / `getFacts()` / `getRelations()`）→ 可用 @Getter
- `warnings()` **不是标准 getter**（方法名不含 "get" 前缀）→ 不可用 Lombok 替代
- 5 个业务方法必须保留：`empty()` / `addEntity()` / `addFact()` / `addRelation()` / `addWarning()` / `merge()` / `isEmpty()`
- **禁止引入 @Data 或 @Setter**：列表字段通过 addXxx() 追加，不应通过 setter 整体替换

### 3.5 AstEntityType 枚举

- 4 个值：CLASS / INTERFACE / ENUM / METHOD
- 每个值应标注在 AST 图中的作用和对应的代码语义类别

---

## 四、排除清单

| 排除文件 | 理由 |
|---|---|
| `compiler/ast/service/AstGraphExtractService.java` | 服务类，不属于 domain 治理 |
| 所有 `infra/persistence` 下的 Record 类 | 明确排除（43 个 MyBatis 记录类） |

---

## 五、给 agentA 的下一轮提示词草案（B13a）

```
交给 agentA。

本轮任务：对 B13a 的 7 个 compiler/domain 不可变领域对象做 @Getter + 领域语义 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_domain_ast_contract_analysis_report.md

## 修改范围（7 个文件，全部添加 @Getter + 字段 Javadoc）

1. AnalyzePayload.java（含 3 个嵌套类：AnalyzeConceptPayload/SectionPayload/SourcePayload）
   - 类级 @Getter，删除 12 手写 getter
   - 保留所有 @JsonCreator 构造器（含防御性拷贝 new ArrayList<>(x) / List.of()）

2. AnalyzedConcept.java
   - 类级 @Getter，删除 9 手写 getter
   - 保留 @JsonCreator(9P) + 3 个便捷构造器(4P/5P/6P)
   - 保留 2 个 withAnalysisMetadata() factory 方法

3. ConceptSection.java
   - 类级 @Getter，删除 3 手写 getter
   - 保留 @JsonCreator(3P) + 便捷构造器(2P)
   - **保留 equals()/hashCode()**（章节去重依赖，禁止修改）

4. IncrementalMatchPayload.java（含 EnhancementPayload/NewArticlePayload）
   - 类级 @Getter，删除 10 手写 getter
   - 保留所有 @JsonCreator 构造器

5. MergedConcept.java
   - 类级 @Getter，删除 9 手写 getter
   - 保留 @JsonCreator(9P) + 3 个便捷构造器（telescoping 4P→5P→6P→9P）

6. RawSource.java ⚠️
   - 类级 @Getter，删除 12 手写 getter
   - 保留 @JsonCreator(11P) + 2 便捷构造器
   - 保留 3 static factory（text/extracted/parsed）
   - **保留 getContent() 上的 @JsonIgnore**（getContent 和 getExtractedText 返回同一字段，Lombok 只能生成一个 getExtractedText()，需手动添加 getContent() 别名方法并标注 @JsonIgnore）
   - 保留 private static hash()/defaultParseMode()/defaultParseProvider()

7. SourceBatch.java
   - 类级 @Getter，删除 3 手写 getter
   - 保留 @JsonCreator(3P)

## 禁止事项
- 禁止修改任何构造器签名或逻辑
- 禁止修改 @JsonCreator/@JsonProperty/@JsonIgnore 注解
- 禁止修改 equals/hashCode
- 禁止修改 static factory 方法
- 禁止修改 private static 工具方法
- 禁止引入 @Data/@Setter（不可变对象）
- 禁止修改字段类型、名称、final 修饰符

## 完成后：回写 B13a → "已完成"，输出 B13a_fix_result_report.md
```

---

## 六、给 agentA 的下一轮提示词草案（B13b）

```
交给 agentA。

本轮任务：对 B13b 的 7 个 AST 领域对象做 @Data 降级 + 字段 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_domain_ast_contract_analysis_report.md

## 修改范围（7 个文件）

### @Data→@Getter @Setter（5 个，全部为可变 AST 提取模型）

1. AstRelation.java
   - @Data → @Getter @Setter
   - 8 字段 Javadoc：srcId/dstId/edgeType 图关系三元组，sourceRef/sourceStartLine/sourceEndLine 源码定位，confidence 置信度，extractor 抽取器标识

2. AstFact.java
   - @Data → @Getter @Setter
   - 9 字段 Javadoc：entityId/predicate/value 事实三元组，evidenceExcerpt 证据原文

3. AstGraphExtractReport.java
   - @Data → @Getter @Setter
   - 4 字段 Javadoc：entityUpsertCount/factUpsertCount/relationUpsertCount upsert 统计，warnings 告警列表

4. AstEntity.java
   - @Data → @Getter @Setter
   - 9 字段 Javadoc：id/canonicalName/simpleName/entityType 实体标识，sourceFileId 关联源文件

5. AstSourceFile.java
   - @Data → @Getter @Setter
   - 4 字段 Javadoc：注意 content 为完整源文件内容（可能很大）

### 累加器（保留现有结构，仅加 @Getter）

6. AstExtractionResult.java
   - fields 保留 `new ArrayList<>()` 初始化
   - 对 entities/facts/relations 字段加 @Getter（@Getter 在字段级或类级均可，但注意 warnings() 不是标准 getter，故推荐仅 3 个 List 字段级 @Getter）
   - 或类级 @Getter 但对 warnings 字段加 @Getter(AccessLevel.NONE)
   - **保留 empty()/addEntity()/addFact()/addRelation()/addWarning()/merge()/isEmpty()/warnings() 所有业务方法**
   - 禁止引入 @Data/@Setter

### 枚举

7. AstEntityType.java
   - 4 枚举值补 Javadoc：CLASS（类）/INTERFACE（接口）/ENUM（枚举）/METHOD（方法）在 AST 图中的语义

## 禁止事项
- 禁止修改 AstExtractionResult 的 addXxx/merge/isEmpty/empty/warnings 方法
- 禁止修改字段初始化值（如 new ArrayList<>()）
- 禁止引入 @Data
- 禁止混入 B13a 或 service 文件

## 完成后：回写 B13b → "已完成"，输出 B13b_fix_result_report.md
```

---

## 七、审查结论

- B13 共 14 个类，自然拆分为 **B13a（7 个 compiler/domain 不可变对象）** + **B13b（7 个 compiler/ast/domain，含 5 个 @Data）**。
- **B13a**：全部不可变 final-field @JsonCreator 类，可安全加 @Getter 删除 58 个手写 getter。构造器防御性拷贝、@JsonIgnore、factory 方法、equals/hashCode 均须保留。
- **B13b**：5 个 @Data 需降级为 @Getter @Setter（AST 可变提取模型，@Data 的 equals/hashCode/toString 在集合操作和大字段场景下有风险）。1 个累加器仅加 @Getter，保留所有业务方法。1 个枚举补 Javadoc。
- **RawSource 特殊**：`getContent()` 和 `getExtractedText()` 返回同一字段，Lombok @Getter 只生成 `getExtractedText()`。需手动保留 `getContent()` 别名方法并保留 `@JsonIgnore`。
- **AstExtractionResult.warnings()** 不是标准 getter（缺少 "get" 前缀），不可被 Lombok 覆盖。需 `@Getter(AccessLevel.NONE)` 排除。
