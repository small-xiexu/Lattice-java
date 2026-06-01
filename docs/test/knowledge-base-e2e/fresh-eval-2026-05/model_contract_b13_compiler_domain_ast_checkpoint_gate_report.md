# B13 Compiler Domain + AST Checkpoint 门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B13a + B13b — compiler/domain（7）+ compiler/ast/domain（7）
状态：**PASS — 所有核查项通过，无阻塞问题**

---

## 1. 当前 Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| 生产代码（B13a compiler/domain） | 7 | 不可变对象 @Getter + Javadoc |
| 生产代码（B13b compiler/ast/domain） | 7 | @Data 降级 + 枚举 Javadoc + 累加器特殊处理 |
| **生产代码 B13 合计** | **14** | — |
| 计划文档（治理台账） | 1 | 台账更新（B13→已完成），符合预期 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（不得纳入）** |
| special_cases_report | 1 | **已知 out-of-scope dirty（不得纳入）** |
| 批次报告（untracked B13） | 3 | 可纳入 |

### 1.2 生产代码详细清单

#### B13a: compiler/domain（7 个不可变领域对象）

| 文件 | 类数（含嵌套） | diff 规模 | 变更 |
|---|---|---|---|
| `AnalyzePayload.java` | 4（外层+3嵌套） | +176/-? | 4 @Getter，删除 12 getter，12 字段 Javadoc |
| `AnalyzedConcept.java` | 1 | +163/-? | 1 @Getter，删除 9 getter，9 字段 Javadoc |
| `ConceptSection.java` | 1 | +61/-? | 1 @Getter，删除 3 getter，3 字段 Javadoc |
| `IncrementalMatchPayload.java` | 3（外层+2嵌套） | +143/-? | 3 @Getter，删除 10 getter，10 字段 Javadoc |
| `MergedConcept.java` | 1 | +137/-? | 1 @Getter，删除 9 getter，9 字段 Javadoc |
| `RawSource.java` | 1 | +282/-? | 1 @Getter，删除 10 getter（getContent @JsonIgnore 别名保留），11 字段 Javadoc |
| `SourceBatch.java` | 1 | +44/-? | 1 @Getter，删除 3 getter，3 字段 Javadoc |

#### B13b: compiler/ast/domain（7 个 AST 领域对象）

| 文件 | 类型 | diff 规模 | 变更 |
|---|---|---|---|
| `AstEntityType.java` | 枚举 | +8/-? | 4 枚举值 Javadoc |
| `AstRelation.java` | 可变模型 | +18/-? | @Data→@Getter @Setter，8 字段 Javadoc |
| `AstFact.java` | 可变模型 | +20/-? | @Data→@Getter @Setter，9 字段 Javadoc |
| `AstGraphExtractReport.java` | 可变模型 | +15/-? | @Data→@Getter @Setter，4 字段 Javadoc |
| `AstEntity.java` | 可变模型 | +20/-? | @Data→@Getter @Setter，9 字段 Javadoc |
| `AstSourceFile.java` | 可变模型 | +19/-? | @Data→@Getter @Setter，4 字段 Javadoc |
| `AstExtractionResult.java` | 累加器 | +77/-? | 3 字段级 @Getter，所有业务方法保留 |

---

## 2. 核查项逐项结果

### 2.1 生产代码仅限 B13 14 个文件

| 包 | 预期 | 实际 | 匹配 |
|---|---|---|---|
| `compiler/domain/` | 7 | 7 | ✅ |
| `compiler/ast/domain/` | 7 | 7 | ✅ |
| **合计** | **14** | **14** | ✅ |

越界检查：
```
git diff --name-only -- src/main/java/com/xbk/lattice/compiler/ | grep -v "compiler/domain/\|compiler/ast/domain/\|compiler/config/"
→ (无输出)
```

**结果：PASS。** 除 B12 已完成的 config 文件外，compiler/ 下无其他文件被修改。

### 2.2 AstGraphExtractService 未修改

```
git diff --name-only -- src/main/java/com/xbk/lattice/compiler/ast/service/AstGraphExtractService.java
→ (无输出)
```

**结果：PASS。**

### 2.3 B13a — 不可变对象 @Getter 核查

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| @Data/@Setter/@Builder | 0 | **0**（无输出） | ✅ |
| @Getter 数量 | 12 | **12**（AnalyzePayload:4, AnalyzedConcept:1, ConceptSection:1, IncrementalMatchPayload:3, MergedConcept:1, RawSource:1, SourceBatch:1） | ✅ |
| @JsonCreator 保留 | 全部 7 类 | **7/7**（含嵌套共 12 个） | ✅ |
| @JsonIgnore 保留 | RawSource.getContent() | **第 139 行** | ✅ |
| equals/hashCode 保留 | ConceptSection | **第 47 行 equals, 第 61 行 hashCode** | ✅ |
| static factory 保留 | RawSource.text/extracted/parsed | **3/3**（第 108/115/125 行） | ✅ |
| getContent() 别名保留 | RawSource | Javadoc + @JsonIgnore 标注，手动保留 | ✅ |

### 2.4 B13b — AST 可变模型核查

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| @Data 残留 | 0（5 个全降级） | **0**（无输出） | ✅ |
| @Getter @Setter（可变模型） | 5 对 | **5/5**（AstRelation/AstFact/AstGraphExtractReport/AstEntity/AstSourceFile） | ✅ |
| AstExtractionResult @Getter | 3 字段级 | **3**（entities:20, facts:24, relations:28） | ✅ |
| AstExtractionResult warnings 字段 | 无 @Getter | **无 @Getter**（第 32 行 private） | ✅ |
| AstExtractionResult.warnings() | 保留 | **第 83 行** `public List<String> warnings()` | ✅ |
| AstExtractionResult.getWarnings() | 不存在 | **不存在** | ✅ |
| addEntity/addFact/addRelation/addWarning | 全部保留 | **第 41/47/53/59 行** | ✅ |
| empty() | 保留 | **第 37 行** | ✅ |
| merge() | 保留 | **第 65 行** | ✅ |
| isEmpty() | 保留 | **第 76 行** | ✅ |
| AstEntityType 枚举 Javadoc | 4 枚举值 | ✅ | — |

### 2.5 计划台账状态

| 批次 | 台账状态 | 验证 |
|---|---|---|
| B13 | 已完成（拆 B13a/B13b，14 类） | mvn compile PASS |
| B14 | 待开始 | — |

**结果：PASS。** B13 已完成，下一步正确指向 B14。

### 2.6 Out-of-scope 文件

| 文件 | 状态 |
|---|---|
| `docs/模型绑定配置参考.md` | 仍为 dirty（已知，不纳入） |
| `special_cases_report.md` | 仍为 dirty（已知，不纳入） |

### 2.7 报告敏感信息检查

```
rg -n "sk-[A-Za-z0-9_-]{12,}" <3个B13报告>
→ (无输出)
```

**结果：PASS。** B13 报告无 API key 泄露。

---

## 3. 可纳入本次 Checkpoint 的文件清单

### 3.1 计划台账（1 个）

- `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`

### 3.2 B13a 生产代码（7 个）

| # | 文件 |
|---|---|
| 1 | `src/main/java/com/xbk/lattice/compiler/domain/AnalyzePayload.java` |
| 2 | `src/main/java/com/xbk/lattice/compiler/domain/AnalyzedConcept.java` |
| 3 | `src/main/java/com/xbk/lattice/compiler/domain/ConceptSection.java` |
| 4 | `src/main/java/com/xbk/lattice/compiler/domain/IncrementalMatchPayload.java` |
| 5 | `src/main/java/com/xbk/lattice/compiler/domain/MergedConcept.java` |
| 6 | `src/main/java/com/xbk/lattice/compiler/domain/RawSource.java` |
| 7 | `src/main/java/com/xbk/lattice/compiler/domain/SourceBatch.java` |

### 3.3 B13b 生产代码（7 个）

| # | 文件 |
|---|---|
| 8 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstEntityType.java` |
| 9 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstRelation.java` |
| 10 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstFact.java` |
| 11 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstGraphExtractReport.java` |
| 12 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstEntity.java` |
| 13 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstSourceFile.java` |
| 14 | `src/main/java/com/xbk/lattice/compiler/ast/domain/AstExtractionResult.java` |

### 3.4 B13 批次报告（3 个 untracked）

| 文件名 | 类型 |
|---|---|
| `compiler_domain_ast_contract_analysis_report.md` | 边界审查 |
| `compiler_domain_contract_javadoc_lombok_fix_result_report.md` | B13a 修复报告 |
| `compiler_ast_domain_contract_javadoc_lombok_fix_result_report.md` | B13b 修复报告 |

---

## 4. 必须排除的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 变更 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |

---

## 5. B13 汇总

### 5.1 B13a（7 个不可变领域对象）

| 指标 | 数量 |
|---|---|
| 类数（含嵌套） | 12（7 外层 + 5 嵌套） |
| @Getter | 12 |
| 删除手写 getter | 58 |
| @Data/@Setter/@Builder | **0** |
| @JsonCreator 保留 | 12 |
| 防御性拷贝保留 | 全部 |
| equals/hashCode 保留 | ConceptSection |
| @JsonIgnore 保留 | RawSource.getContent() |
| static factory 保留 | RawSource 3 个 |

### 5.2 B13b（7 个 AST 领域对象）

| 指标 | 数量 |
|---|---|
| @Data 降级 | 5→**0** |
| @Getter @Setter（可变模型） | 5 对 |
| @Getter（字段级，累加器） | 3 |
| 枚举 Javadoc | 4 枚举值 |
| AstExtractionResult 业务方法保留 | 8（empty/add×4/merge/isEmpty/warnings） |

### 5.3 编译验证

| 子批次 | mvn compile |
|---|---|
| B13a | BUILD SUCCESS |
| B13b | BUILD SUCCESS |

### 5.4 领域语义覆盖

| 领域概念 | 生命周期 | 核心不变量 |
|---|---|---|
| AnalyzePayload | LLM 输出→反序列化 | concepts 防御性拷贝 |
| AnalyzedConcept | 分析后→合并前 | conceptId 唯一 |
| ConceptSection | 章节提取→去重 | heading+contentLines+sourceRefs 决定 equals |
| MergedConcept | 合并后→编译输入 | 语义为最终版 |
| RawSource | 文件采集→编译输入 | relativePath+contentHash 唯一 |
| SourceBatch | 分组切分→分析输入 | batchId 唯一 |

---

## 6. 给下一轮 /code-commit 的 Staging 建议

```bash
# === B13a: compiler/domain（7 个文件）===
git add src/main/java/com/xbk/lattice/compiler/domain/AnalyzePayload.java
git add src/main/java/com/xbk/lattice/compiler/domain/AnalyzedConcept.java
git add src/main/java/com/xbk/lattice/compiler/domain/ConceptSection.java
git add src/main/java/com/xbk/lattice/compiler/domain/IncrementalMatchPayload.java
git add src/main/java/com/xbk/lattice/compiler/domain/MergedConcept.java
git add src/main/java/com/xbk/lattice/compiler/domain/RawSource.java
git add src/main/java/com/xbk/lattice/compiler/domain/SourceBatch.java

# === B13b: compiler/ast/domain（7 个文件）===
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstEntityType.java
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstRelation.java
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstFact.java
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstGraphExtractReport.java
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstEntity.java
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstSourceFile.java
git add src/main/java/com/xbk/lattice/compiler/ast/domain/AstExtractionResult.java

# === B13 批次报告 ===
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_domain_ast_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_domain_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_ast_domain_contract_javadoc_lombok_fix_result_report.md

# === 本门禁报告 ===
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/model_contract_b13_compiler_domain_ast_checkpoint_gate_report.md
```

---

## 7. 是否可以进入 B14

**可以。** 所有核查项通过：

- [x] 14 个文件与指定清单完全匹配
- [x] B13a 7 个不可变对象 @Getter only，无 @Data/@Setter/@Builder
- [x] B13b 5 个 @Data 全量降级，0 残留
- [x] @JsonCreator/@JsonIgnore/equals/factory 全部保留
- [x] AstExtractionResult warnings() 保留，无 getWarnings() 冲突
- [x] AstExtractionResult 8 个业务方法保留
- [x] 计划台账 B13→已完成，下一步 B14
- [x] 报告无 API key 泄露
- [x] 2 个 out-of-scope 文件已排除
- [x] mvn compile PASS

### 提交建议

当前累积 B0-B13 共 **154 个类**未提交。B13 是第一个接触领域模型（domain model）的批次，涉及 `@JsonCreator`、防御性拷贝、equals/hashCode 等需要特别验证的结构——建议在进入 B14 之前先提交固化。

---

## 附录：全量治理进度（截至 B13）

| 阶段 | 批次 | 类数 | 处置方式 | 状态 |
|---|---|---|---|---|
| 试点 | B0 | 3 | @Getter + Javadoc | 已提交 |
| API 边界 DTO | B0.5-B10 | 95 | @Getter/@Setter + Javadoc | 已完成 |
| Controller 内部 DTO | B11 | 29 | @Data 降级 + @ToString.Exclude | 已完成 |
| Config + State | B12 | 16 | Javadoc / @Getter | 已完成 |
| Compiler Domain + AST | B13 | 14 | @Getter + @Data 降级 | 已完成 |
| **累计** | | **157** | | **154 未提交** |
| DocumentParse Domain | B14 | ~10 | 待定 | 待开始 |
| Source Domain | B15 | ~9 | 待定 | 待开始 |
| LLM Domain | B16 | ~4 | 待定 | 待开始 |
| Query Domain/Evidence | B17 | ~23 | 待定 | 待开始 |
| DeepResearch/Graph | B18 | ~17 | 待定 | 待开始 |
| Governance Domain | B19 | ~5 | 待定 | 待开始 |
| 全局复扫 | B20 | — | — | 待开始 |
