# B18 DeepResearch Domain + Graph State Checkpoint 门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B18a + B18b — deepresearch/domain（11）+ graph state（3）
状态：**PASS — 所有核查项通过，无阻塞问题**

---

## 1. Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| B17 历史变更（已独立 gate PASS） | 22 | query/domain(9) + evidence/domain(13)，已通过 B17 gate |
| B18a 生产代码（deepresearch domain） | 10 | 1 enum + 1 不可变 @Getter + 8 @Data→@Getter/@Setter |
| B18b 生产代码（EvidenceLedger + graph state） | 4 | EvidenceLedger @Getter only + 3 graph state @Getter/@Setter |
| **B18 生产代码合计** | **14** | — |
| 计划文档（治理台账） | 1 | B18→已完成，B19 待开始 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（不得纳入）** |
| special_cases_report | 1 | **已知 out-of-scope dirty（不得纳入）** |
| B17/B18 报告文件（untracked） | 7 | 可纳入 |

### 1.2 B18a 生产代码（10 个文件）

| 文件 | 类型 | diff | 变更 |
|---|---|---|---|
| `ResearchTaskType.java` | 枚举 | +9 | 5 枚举值 Javadoc |
| `DeepResearchAuditSnapshot.java` | 不可变 | +23 | @Getter，删除 2 getter，保留 @JsonCreator/@JsonProperty |
| `ResearchLayer.java` | 可变 | +13 | @Data→@Getter/@Setter，2 字段 Javadoc |
| `EvidenceCard.java` | 可变 | +32 | @Data→@Getter/@Setter，11 字段 Javadoc |
| `LayerSummary.java` | 可变 | +23 | @Data→@Getter/@Setter，summaryMarkdown 大文本 |
| `InternalAnswerDraft.java` | 可变 | +17 | @Data→@Getter/@Setter，draftMarkdown 大文本 |
| `DeepResearchSynthesisResult.java` | 可变 | +24 | @Data→@Getter/@Setter，answerMarkdown 大文本 |
| `ResearchTask.java` | 可变 | +27 | @Data→@Getter/@Setter，9 字段 Javadoc |
| `ResearchTaskHit.java` | 可变 | +39 | @Data→@Getter/@Setter，contentExcerpt 大文本 |
| `LayeredResearchPlan.java` | 可变 | +23 | @Data→@Getter/@Setter，layerCount/taskCount 保留 |

### 1.3 B18b 生产代码（4 个文件）

| 文件 | 类型 | diff | 变更 |
|---|---|---|---|
| `EvidenceLedger.java` | 累加器 | +566/-? | @Data→**@Getter only**，8 字段 Javadoc，22+ 业务方法保留 |
| `QueryGraphState.java` | Graph 状态 | +120/-? | @Data→@Getter/@Setter，55 字段分组 Javadoc |
| `CompileGraphState.java` | Graph 状态 | +82/-? | @Data→@Getter/@Setter，39 字段分组 Javadoc |
| `DeepResearchState.java` | Graph 状态 | +51/-? | @Data→@Getter/@Setter，19 字段分组 Javadoc |

---

## 2. 核查项逐项结果

### 2.1 @Data 清零核查

```
rg -n "@Data" <B18 14个文件>
→ (无输出)
```

**结果：PASS。** B18a 8 个 @Data + B18b 4 个 @Data = 12 个全部降级，0 残留。

### 2.2 编译验证

```
mvn compile → BUILD SUCCESS (6.596s)
```

**结果：PASS。**

### 2.3 B18a 核查

#### 枚举 Javadoc

| 类 | 值数 | 结果 |
|---|---|---|
| `ResearchTaskType` | 5（FACT_LOOKUP/COMPARE/CAUSE/POLICY/SYNTHESIS） | ✅ |

#### DeepResearchAuditSnapshot（不可变）

| 检查项 | 结果 |
|---|---|
| @Getter（类级） | ✅（第 14 行） |
| @JsonCreator 保留 | ✅（第 22 行） |
| @JsonProperty 保留 | ✅（第 24-25 行） |
| 2 个 getter 已删除 | ✅ |

#### 8 个 @Data→@Getter/@Setter 可变类

| 类 | @Getter | @Setter | 结果 |
|---|---|---|---|
| `ResearchLayer` | 1 | ✅ | ✅ |
| `EvidenceCard` | 1 | ✅ | ✅ |
| `LayerSummary` | 1 | ✅ | ✅ |
| `InternalAnswerDraft` | 1 | ✅ | ✅ |
| `DeepResearchSynthesisResult` | 1 | ✅ | ✅ |
| `ResearchTask` | 1 | ✅ | ✅ |
| `ResearchTaskHit` | 1 | ✅ | ✅ |
| `LayeredResearchPlan` | 1 | ✅ | ✅ |

#### 大文本字段标注

| 字段 | 类 | 结果 |
|---|---|---|
| `summaryMarkdown` | LayerSummary | ✅ |
| `draftMarkdown` | InternalAnswerDraft | ✅ |
| `answerMarkdown` | DeepResearchSynthesisResult | ✅ |
| `contentExcerpt` | ResearchTaskHit | ✅ |

#### LayeredResearchPlan 业务方法保留

| 方法 | 行号 | 结果 |
|---|---|---|
| `layerCount()` | 25 | ✅ |
| `taskCount()` | 29 | ✅ |

### 2.4 B18b 核查

#### EvidenceLedger — 关键高风险检查

| 检查项 | 结果 |
|---|---|
| @Getter | ✅（第 18 行） |
| @Setter | **0（正确：禁止 setter）** |
| 3 static 常量保留 | ✅（ANCHOR_VALIDATOR:21, MIN_FINDING_CONFIDENCE:22, MIN_PROJECTABLE_ANCHOR_SCORE:23） |
| 22+ 业务方法保留 | ✅（addCard/addCards/cardCount/findingCount/addFactFinding/addAnchor/addProjectionCandidate/markCoverage/refreshCoverageState/hasConflicts + private helpers） |
| 默认值保留（new ArrayList/new LinkedHashMap） | ✅ |

#### EvidenceLedger 外部 setter 调用检查

```
rg -n "setCards|setCardsByTaskId|setFindingsByFactKey|setAnchorsById|setProjectionCandidates|setConflicts|setComplements|setCoverageState" src/main/java
→ (无输出)
```

**结果：PASS。** 无外部代码直接调用 EvidenceLedger 的内部集合 setter。累加器模式完整保留。

#### 3 个 Graph State

| 类 | @Getter | @Setter | 字段数 | 默认初始化 |
|---|---|---|---|---|
| `QueryGraphState` | ✅（14） | ✅（15） | 55 | ✅ |
| `CompileGraphState` | ✅（16） | ✅（17） | 39 | ✅（LinkedHashMap:62, ArrayList:64/104/106） |
| `DeepResearchState` | ✅（17） | ✅（18） | 19 | ✅（ArrayList:37/43） |

#### Graph State 默认值保留

**CompileGraphState**：
- `sourceFileIdsByPath = new LinkedHashMap<>()` ✅
- `persistedArticleIds = new ArrayList<>()` ✅
- `stepSummaries = new ArrayList<>()` ✅
- `errors = new ArrayList<>()` ✅

**DeepResearchState**：
- `taskResultRefs = new ArrayList<>()` ✅
- `layerSummaryRefs = new ArrayList<>()` ✅

**结果：PASS。** 3 个 graph state 字段名、类型、默认值未被改坏。框架注入需要的 @Setter 已保留。

### 2.5 越界检查

```
git diff --name-only -- query/deepresearch/ | grep -v "deepresearch/domain/" | grep -v "deepresearch/graph/"
→ (无输出)
```
deepresearch service/graph node/validator/mapper/keys 均未修改。

```
git diff --name-only -- query/service/ query/retrieval/ query/answer/
→ (无输出)
```
query/retrieval/answer/fallback/citation 主链行为未修改。

**结果：PASS。** B18 实现严格限制在 deepresearch/domain + graph state 文件范围内。

### 2.6 计划台账核查

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| B18 状态 | 已完成 | "已完成" | ✅ |
| B18a/B18b 汇总 | 有 | 台账第 90 行 | ✅ |
| 验证 | mvn compile PASS | 已确认 | ✅ |
| 下一步 | B19: governance/domain | "待开始" | ✅ |

**结果：PASS。** 计划台账状态正确，B18 已完成，B19 待开始。

### 2.7 Out-of-scope 文件确认

| 文件 | 状态 |
|---|---|
| `docs/模型绑定配置参考.md` | 仍为 dirty（不纳入） |
| `special_cases_report.md` | 仍为 dirty（不纳入） |

---

## 3. 可纳入本次 Checkpoint 的文件清单

### 3.1 B18a 生产代码（10 个）

| # | 文件 |
|---|---|
| 1 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/ResearchTaskType.java` |
| 2 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/DeepResearchAuditSnapshot.java` |
| 3 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/ResearchLayer.java` |
| 4 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/EvidenceCard.java` |
| 5 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/LayerSummary.java` |
| 6 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/InternalAnswerDraft.java` |
| 7 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/DeepResearchSynthesisResult.java` |
| 8 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/ResearchTask.java` |
| 9 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/ResearchTaskHit.java` |
| 10 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/LayeredResearchPlan.java` |

### 3.2 B18b 生产代码（4 个）

| # | 文件 |
|---|---|
| 11 | `src/main/java/com/xbk/lattice/query/deepresearch/domain/EvidenceLedger.java` |
| 12 | `src/main/java/com/xbk/lattice/query/graph/QueryGraphState.java` |
| 13 | `src/main/java/com/xbk/lattice/compiler/graph/CompileGraphState.java` |
| 14 | `src/main/java/com/xbk/lattice/query/deepresearch/graph/DeepResearchState.java` |

### 3.3 批次报告（4 个 untracked）

| 文件名 | 类型 |
|---|---|
| `query_deepresearch_graph_state_contract_analysis_report.md` | 边界审查 |
| `query_deepresearch_domain_b18a_contract_javadoc_lombok_fix_result_report.md` | B18a 修复报告 |
| `query_deepresearch_graph_state_b18b_contract_javadoc_lombok_fix_result_report.md` | B18b 修复报告 |
| （本报告） | B18 门禁报告 |

---

## 4. 必须排除的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 变更 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |
| B17 生产代码（22 个文件） | 已在 B17 gate 独立 PASS，不在本轮 checkpoint 范围内 |

---

## 5. B18 汇总

### 5.1 B18a（10 个 deepresearch domain 对象）

| 指标 | 数量 |
|---|---|
| 枚举 | 1（ResearchTaskType 5 值） |
| 不可变对象 | 1（DeepResearchAuditSnapshot） |
| @Data→@Getter/@Setter | 8 |
| @Data 残留 | **0** |
| 删除 getter | 2（DeepResearchAuditSnapshot） |
| @JsonCreator 保留 | 1 |
| 业务方法保留 | 2（layerCount + taskCount） |
| 大文本标注 | 4 |

### 5.2 B18b（4 个高风险对象）

| 指标 | 数量 |
|---|---|
| @Data→@Getter only（EvidenceLedger） | 1 |
| @Data→@Getter/@Setter（graph state） | 3 |
| @Data 残留 | **0** |
| EvidenceLedger 静态常量 | 3（ANCHOR_VALIDATOR/MIN_FINDING_CONFIDENCE/MIN_PROJECTABLE_ANCHOR_SCORE） |
| EvidenceLedger 业务方法保留 | 22+ |
| EvidenceLedger 外部 setter 调用 | **0** |
| Graph state @Setter 保留 | 3/3（框架注入需要） |
| Graph state 默认初始化保留 | 全部 |

### 5.3 B18 编译验证

```
mvn compile → BUILD SUCCESS (6.596s)
```

---

## 6. 是否可以进入 B19

**可以。** 所有核查项通过：

- [x] B18 14 个文件与指定清单完全匹配
- [x] B18a 8 个 @Data + B18b 4 个 @Data = 12 个全量降级，0 残留
- [x] EvidenceLedger 仅 @Getter（无 @Setter），累加器模式完整保留
- [x] EvidenceLedger 3 个 static 常量 + 22+ 个业务方法全部保留
- [x] 3 个 graph state @Getter/@Setter（框架注入 @Setter 已保留）
- [x] Graph state 默认值未被改坏
- [x] deepresearch service/graph node/validator + query 主链均未修改
- [x] 计划台账 B18→已完成，下一步 B19
- [x] mvn compile BUILD SUCCESS
- [x] B17 历史变更已在独立 gate PASS，不阻塞本轮

---

## 附录：全量治理进度（截至 B18）

| 阶段 | 批次 | 类数 | 处置方式 | 状态 |
|---|---|---|---|---|
| 试点 | B0 | 3 | @Getter + Javadoc | 已提交 |
| API 边界 DTO | B0.5-B10 | 95 | @Getter/@Setter + Javadoc | 已完成 |
| Controller 内部 DTO | B11 | 29 | @Data 降级 + @ToString.Exclude | 已完成 |
| Config + State | B12 | 16 | Javadoc / @Getter | 已完成 |
| Compiler Domain + AST | B13 | 14 | @Getter + @Data 降级 | 已完成 |
| DocumentParse Domain | B14 | 10 | @Getter | 已完成 |
| Source Domain | B15 | 9 | @Getter + 安全标注 | 已完成 |
| LLM Domain | B16 | 4 | @Getter + 安全标注 | 已完成 |
| Query + Evidence Domain | B17 | 23 | @Getter + @Data 降级 | 已完成 |
| DeepResearch + Graph State | B18 | 14 | @Data 降级（含 EvidenceLedger 无 @Setter） | 已完成 |
| **累计** | | **217** | | **214 未提交** |
| Governance Domain | B19 | ~5 | 待定 | 待开始 |
| 全局复扫 | B20 | — | — | 待开始 |
