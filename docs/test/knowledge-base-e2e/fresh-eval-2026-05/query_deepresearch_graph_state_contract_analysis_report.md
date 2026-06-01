# B18 DeepResearch Domain + Graph State 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B18 — `deepresearch/domain`（11）+ graph state（3）= **14 个类**

---

## 一、拆分建议：B18a + B18b（按复杂度拆分）

| 子批次 | 候选数 | 范围 | @Data 数 | 风险 |
|---|---|---|---|---|
| **B18a** | **10** | deepresearch domain（除 EvidenceLedger） | **8 个 @Data** | 中：纯 @Data→@Getter/@Setter 机械降级 |
| **B18b** | **4** | EvidenceLedger + 3 graph state | **3 个 @Data** | **高**：EvidenceLedger 20+ 业务方法；graph state 需保持框架 setter 注入 |

---

## 二、全局发现：11/14 使用 @Data（79%）

- **仅 2 个干净**：`DeepResearchAuditSnapshot`（不可变 @JsonCreator）、`ResearchTaskType`（enum）
- **11 个使用 @Data**，其中 EvidenceLedger 是可变累加器、3 个 graph state 是框架注入容器
- 无手写 getter 需删除（全部由 @Data 生成），但 EvidenceLedger 的 @Data setter 会破坏累加器模式
- 全部字段无 Javadoc（仅类级有简短中文描述）

---

## 三、B18a — DeepResearch Domain（10 个）

### 3.1 枚举（1 个）

| # | 类 | 值数 | 处置 |
|---|---|---|---|
| 1 | `ResearchTaskType` | 5（FACT_LOOKUP/COMPARE/CAUSE/POLICY/SYNTHESIS） | 枚举值 Javadoc |

### 3.2 不可变对象（1 个）

| # | 类 | 字段 | 构造器 | 处置 |
|---|---|---|---|---|
| 2 | `DeepResearchAuditSnapshot` | 2 final | @JsonCreator | @Getter + Javadoc，删除 2 getter |

仅 2 字段（runId、evidenceCardCount），是 B18 中最简单的类。

### 3.3 @Data → @Getter @Setter（8 个）

| # | 类 | 字段数 | 默认值 | 大文本字段 | 业务方法 |
|---|---|---|---|---|---|
| 3 | `ResearchLayer` | 2 | tasks=new ArrayList | — | — |
| 4 | `EvidenceCard` | 11 | 7 个 List=new ArrayList | — | — |
| 5 | `LayerSummary` | 5 | 2 个 List=new ArrayList | `summaryMarkdown` | — |
| 6 | `InternalAnswerDraft` | 4 | 3 个 List=new ArrayList | `draftMarkdown` | — |
| 7 | `DeepResearchSynthesisResult` | 7 | — | `answerMarkdown` | — |
| 8 | `ResearchTask` | 8 | taskType=FACT_LOOKUP，3 个 List=new ArrayList | — | — |
| 9 | `ResearchTaskHit` | 12 | — | `contentExcerpt` | — |
| 10 | `LayeredResearchPlan` | 2 | layers=new ArrayList | — | **layerCount() / taskCount()** |

#### @Data 降级理由

| 类型 | 风险 |
|---|---|
| 大文本 toString | LayerSummary.summaryMarkdown、InternalAnswerDraft.draftMarkdown、DeepResearchSynthesisResult.answerMarkdown、ResearchTaskHit.contentExcerpt 参与 @Data toString() |
| List 默认值被覆盖 | @Data setter 允许外部传 null 覆盖 `new ArrayList<>()` 默认值 |
| LayeredResearchPlan | `layerCount()` 和 `taskCount()` 是计算方法（非 getter），不可被 Lombok 覆盖；且 @Data 不生成同名方法，安全 |

#### LayeredResearchPlan 特殊处理

该类有 2 个业务方法 `layerCount()` 和 `taskCount()`，命名不含 "get" 前缀。Lombok @Getter 会生成 `getLayers()`，不会与 `layerCount()` 冲突。`@Data → @Getter @Setter` 安全。

---

## 四、B18b — EvidenceLedger + Graph State（4 个，高风险）

### 4.1 EvidenceLedger（最严重）⛔

- **当前**：`@Data`，8 字段 + **22 个业务方法**（478 行）
- **字段**：cards、cardsByTaskId、findingsByFactKey、anchorsById、projectionCandidates、conflicts、complements、coverageState
- **@Data 风险**：
  1. **setter 破坏累加器模式**：@Data 为 8 个字段生成 setter，允许外部直接替换整个 cards/findingsByFactKey/anchorsById 等内部 Map/List。这些字段应仅通过 addCard()/addFactFinding()/addAnchor() 等方法追加
  2. **toString() 灾难**：@Data toString() 会递归输出所有 cards、findings、anchors — 可能产生数 MB 的日志
  3. **equals/hashCode**：@Data 的 equals 尝试比较 8 个 Map/List 字段，性能灾难且语义无意义
- **处置**：`@Data` → **仅 `@Getter`**（不加 @Setter！）。8 个字段必须通过业务方法追加，不可直接 set。3 个 static 常量（ANCHOR_VALIDATOR、MIN_FINDING_CONFIDENCE、MIN_PROJECTABLE_ANCHOR_SCORE）保留。
- **22 个业务方法全部保留**：addCard/addCards/cardCount/findingCount/addFactFinding/addFactFindings/addAnchor/addAnchors/addProjectionCandidate/addProjectionCandidates/markCoverage/registerMustResolveFactKeys/refreshCoverageState/hasConflicts + 12 个 private 方法

### 4.2 QueryGraphState / CompileGraphState / DeepResearchState

| 类 | 字段数 | 特征 | 处置 |
|---|---|---|---|
| `QueryGraphState` | ~50 | 大量 `*Ref` String 字段（Redis key 引用） | @Data → @Getter @Setter |
| `CompileGraphState` | ~45 | 含 `List<String> stepSummaries/errors`、`Map<String,Long> sourceFileIdsByPath` | @Data → @Getter @Setter |
| `DeepResearchState` | 17 | 含 `List<String> taskResultRefs/layerSummaryRefs` | @Data → @Getter @Setter |

**Graph state 降级理由**：
- 这三个类是 Graph 框架（StateGraph）的运行时容器。框架通过 setter 注入状态字段，**必须保留 @Setter**
- 但 @Data 生成的 toString() 对 45-50 字段的对象会输出超长日志条目
- @Data 生成的 equals/hashCode 对 50 字段的对象毫无意义且性能开销大
- **处置**：`@Data → @Getter @Setter`（保留 getter/setter，移除 toString/equals/hashCode）

**CompileGraphState 特殊字段**：
- `sourceFileIdsByPath` 使用 `new java.util.LinkedHashMap<>()` 初始化
- `stepSummaries`、`errors`、`persistedArticleIds` 使用 `new ArrayList<>()` 初始化
- @Data setter 可覆盖这些默认值，但 graph 框架通过 setter 注入时需要此能力，所以 setter 必须保留

---

## 五、排除清单

| 排除 | 理由 |
|---|---|
| deepresearch service/graph 节点 | 服务/编排层 |
| deepresearch validator | 校验逻辑 |
| graph state mapper/key 类 | 持久化/序列化层（可读但不可改） |
| `infra/persistence/*` | 明确排除 |

---

## 六、字段风险汇总

### 大文本字段（不应参与 toString）

| 字段 | 所属类 | 批次 |
|---|---|---|
| `summaryMarkdown` | LayerSummary | B18a |
| `draftMarkdown` | InternalAnswerDraft | B18a |
| `answerMarkdown` | DeepResearchSynthesisResult | B18a |
| `contentExcerpt` | ResearchTaskHit | B18a |
| `question` | QueryGraphState, DeepResearchState | B18b |

### 证据/事实字段

| 字段 | 风险 |
|---|---|
| `EvidenceLedger.cards/findingsByFactKey/anchorsById` | 累加器内部状态，不可直接 set |
| `EvidenceCard.factFindings/evidenceAnchors` | 引用 B17b 的 FactFinding/EvidenceAnchor（@Data 已降级） |
| `DeepResearchSynthesisResult.answerProjectionBundle` | 引用 B17b 的 AnswerProjectionBundle |
| `DeepResearchSynthesisResult.citationCheckReport` | 引用 CitationCheckReport |

---

## 七、给 agentA 的下一轮提示词草案（B18a）

```
交给 agentA。

本轮任务：对 B18a 的 10 个 deepresearch domain 对象做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_deepresearch_graph_state_contract_analysis_report.md

## 修改范围（10 个文件）

### 枚举（1 个）
1. ResearchTaskType.java — 5 值 Javadoc（FACT_LOOKUP/COMPARE/CAUSE/POLICY/SYNTHESIS）

### 不可变（1 个）
2. DeepResearchAuditSnapshot.java — @Getter + 2 字段 Javadoc，删除 2 getter，保留 @JsonCreator

### @Data → @Getter @Setter（8 个）
3. ResearchLayer.java — 2 字段 Javadoc
4. EvidenceCard.java — 11 字段 Javadoc（factFindings/evidenceAnchors 标注引用 B17b 类型）
5. LayerSummary.java — 5 字段 Javadoc，**summaryMarkdown 标注大文本**
6. InternalAnswerDraft.java — 4 字段 Javadoc，**draftMarkdown 标注大文本**
7. DeepResearchSynthesisResult.java — 7 字段 Javadoc，**answerMarkdown 标注大文本**
8. ResearchTask.java — 8 字段 Javadoc（taskType 默认 FACT_LOOKUP）
9. ResearchTaskHit.java — 12 字段 Javadoc，**contentExcerpt 标注大文本**
10. LayeredResearchPlan.java — 2 字段 Javadoc + **保留 layerCount()/taskCount() 业务方法**

## 禁止事项
- 禁止修改 LayeredResearchPlan.layerCount()/taskCount()
- 禁止修改 DeepResearchAuditSnapshot @JsonCreator 构造器
- 禁止修改字段默认值（new ArrayList/枚举默认）
- 引入 @Getter @Setter 时保留 @NoArgsConstructor（如原 @Data 隐含，需显式添加）或直接使用 @Getter @Setter（不含 @NoArgsConstructor，Java 默认无参构造仍存在因为无显式构造器）
- 注意：原 @Data 隐含 @NoArgsConstructor（当无显式构造器时 Lombok 不会生成，但类默认有无参构造）。由于这些类无显式构造器，Java 编译器已提供默认无参构造，替换 @Data 为 @Getter @Setter 不影响

## 完成后：回写 B18a → "已完成"，输出 B18a_fix_result_report.md
```

---

## 八、给 agentA 的下一轮提示词草案（B18b）

```
交给 agentA。

本轮任务：对 B18b 的 4 个高风险对象做 @Data 降级 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_deepresearch_graph_state_contract_analysis_report.md

## 修改范围（4 个文件）

### 1. EvidenceLedger.java ⛔ 最高风险（478 行）
- **@Data → @Getter** （NOT @Setter！）
- @Data 生成的 setter 允许外部直接替换 cards/findingsByFactKey/anchorsById 等内部 Map/List，破坏累加器模式
- 8 字段 Javadoc（标注"通过 addXxx() 方法追加，不可直接 set"）
- 保留 3 static 常量：ANCHOR_VALIDATOR/MIN_FINDING_CONFIDENCE/MIN_PROJECTABLE_ANCHOR_SCORE
- **保留全部 22 个业务方法**：
  - addCard/addCards/cardCount/findingCount
  - addFactFinding/addFactFindings
  - addAnchor/addAnchors
  - addProjectionCandidate/addProjectionCandidates
  - markCoverage/registerMustResolveFactKeys/refreshCoverageState
  - hasConflicts
  - resolveCardFactFindings/resolveCardAnchors/buildProjectionCandidates/buildProjectionCandidate
  - hasProjectionCandidate/projectionCandidateId/findByMergeIdentity/mergeAnchorIds
  - registerConflict/registerComplements/addComplement/passesQualityGate/hasRegisteredAnchor
  - isActiveProjection/findFactKeysByAnchorId/normalize/isBlank

### 2-4. Graph State（@Data → @Getter @Setter）

2. QueryGraphState.java（~50 字段）
3. CompileGraphState.java（~45 字段，含 List stepSummaries/errors、Map sourceFileIdsByPath）
4. DeepResearchState.java（17 字段）

- Graph 框架通过 setter 注入状态，**必须保留 @Setter**
- 移除 @Data 的 toString/equals/hashCode（50 字段的 toString 是日志灾难）
- 字段 Javadoc 需说明每个 *Ref 字段的引用类型（Redis key → 对应 working set 对象）

## 禁止事项
- **禁止给 EvidenceLedger 加 @Setter**（破坏累加器模式）
- 禁止修改 EvidenceLedger 的任何业务方法
- 禁止修改 graph state 的字段名称（graph 框架/mapper 可能依赖字段名）
- 禁止修改字段默认值（new ArrayList/new LinkedHashMap）
- 必须保留 @Setter（graph state 框架注入需要）

## 验收门槛
- mvn compile -pl . -q 通过
- 自查：EvidenceLedger 无 setCards/setFindingsByFactKey 等方法

## 完成后：回写 B18b → "已完成"，输出 B18b_fix_result_report.md
```

---

## 九、审查结论

- B18 共 14 个类，按复杂度拆分为 **B18a（10 个 deepresearch domain）** + **B18b（4 个高风险对象）**。
- **11/14 使用 @Data（79%）**，仅 DeepResearchAuditSnapshot（不可变 @JsonCreator）和 ResearchTaskType（enum）干净。
- **B18a**：1 enum + 1 不可变 + 8 个 @Data → @Getter @Setter。纯机械降级，但需保护 LayeredResearchPlan 的 2 个业务方法和大文本字段标注。
- **B18b 核心风险**：
  - **EvidenceLedger**（最严重）：@Data setter 破坏累加器模式，必须降级为仅 @Getter（无 @Setter）。22 个业务方法不可触碰。
  - **3 个 graph state**：@Data → @Getter @Setter（必须保留 setter 供 graph 框架注入）。移除 toString（避免 50 字段日志灾难）。
