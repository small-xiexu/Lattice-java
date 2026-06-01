# B13a: Compiler Domain 领域对象 @Getter + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B13a（B13 第 1 子批次，7/14 类）

---

## 1. 修改文件清单

| 文件 | 类数 | 字段数 | @Getter | 删除 getter | 保留特殊方法 |
|---|---|---|---|---|---|
| `AnalyzePayload.java` | 4（外层+3嵌套） | 12 | 4 | 12 | @JsonCreator×4，防御性拷贝×4 |
| `AnalyzedConcept.java` | 1 | 9 | 1 | 9 | @JsonCreator，3 telescoping 构造器，2 withAnalysisMetadata() |
| `ConceptSection.java` | 1 | 3 | 1 | 3 | @JsonCreator，equals/hashCode |
| `IncrementalMatchPayload.java` | 3（外层+2嵌套） | 10 | 3 | 10 | @JsonCreator×3，防御性拷贝×3 |
| `MergedConcept.java` | 1 | 9 | 1 | 9 | @JsonCreator，3 telescoping 构造器 |
| `RawSource.java` | 1 | 11 | 1 | 10 | getContent() @JsonIgnore 保留，3 static factory，hash/defaultParse |
| `SourceBatch.java` | 1 | 3 | 1 | 3 | @JsonCreator |

**合计**：12 个 @Getter，58 个 getter 删除，0 @Data/@Setter/@Builder。

---

## 2. 关键处置

### RawSource.getContent() 别名保留
`getContent()` 是 `extractedText` 的 `@JsonIgnore` 兼容别名（防止大文本参与 JSON 序列化）。Lombok 不会为不存在的 `content` 字段生成此方法——手写保留。

### 领域语义标注

| 类 | 生命周期阶段 | 核心不变量 |
|---|---|---|
| `AnalyzePayload` | LLM Analyze 输出→反序列化 | concepts 不可变列表（构造时防御性拷贝） |
| `AnalyzedConcept` | 批次分析后→合并前 | conceptId 唯一，withAnalysisMetadata 创建不可变副本 |
| `ConceptSection` | 章节提取→合并去重 | heading+contentLines+sourceRefs 三元组决定 equals |
| `MergedConcept` | 跨批次合并后→编译输入 | 语义为"合并后最终版"，结构同 AnalyzedConcept |
| `RawSource` | 文件采集后→编译输入 | relativePath+contentHash 唯一标识文件版本 |
| `SourceBatch` | 分组切分后→分析输入 | batchId 唯一，groupKey 标识归属分组 |

---

## 3. 验证

```
mvn compile: BUILD SUCCESS
@Getter 计数: 12（4+1+1+3+1+1+1） ✓
@JsonCreator 保留: 全部 12 个 ✓
防御性拷贝保留: ArrayList(list) / List.of() ✓
equals/hashCode 保留: ConceptSection ✓
getContent() @JsonIgnore 保留: RawSource ✓
static factory 保留: text/extracted/parsed ✓
```

---

## 4. B13 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B13a** | **已完成** | 7 |
| B13b | 待开始 | 7 (AST domain: @Data→@Getter/@Setter) |

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 7 个目标文件 | 通过 |
| 仅 @Getter，无 @Data/@Setter/@Builder | 通过 |
| @JsonCreator 全保留 | 通过 |
| 防御性拷贝保留 | 通过 |
| equals/hashCode 保留 | 通过 |
| RawSource.getContent() @JsonIgnore 保留 | 通过 |
| static factory 保留 | 通过 |
| 未 stage/commit/push | 通过 |
