# B18a: Deep Research Domain 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B18a（B18 第 1 子批次，10/14 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 字段 | 处置 |
|---|---|---|---|
| `ResearchTaskType.java` | 枚举 | 5 | 枚举值 Javadoc（FACT_LOOKUP/COMPARE/CAUSE/POLICY/SYNTHESIS） |
| `DeepResearchAuditSnapshot.java` | 不可变 | 2 | @Getter，删除 2 getter，2 字段 Javadoc，保留 @JsonCreator |
| `ResearchLayer.java` | 可变 | 2 | @Data→@Getter @Setter，2 字段 Javadoc |
| `EvidenceCard.java` | 可变 | 11 | @Data→@Getter @Setter，11 字段 Javadoc（引用 B17 FactFinding/EvidenceAnchor） |
| `LayerSummary.java` | 可变 | 5 | @Data→@Getter @Setter，5 字段 Javadoc（summaryMarkdown 大文本） |
| `InternalAnswerDraft.java` | 可变 | 4 | @Data→@Getter @Setter，4 字段 Javadoc（draftMarkdown 大文本） |
| `DeepResearchSynthesisResult.java` | 可变 | 7 | @Data→@Getter @Setter，7 字段 Javadoc（引用 B17 AnswerProjectionBundle） |
| `ResearchTask.java` | 可变 | 9 | @Data→@Getter @Setter，9 字段 Javadoc（taskType 默认 FACT_LOOKUP） |
| `ResearchTaskHit.java` | 可变 | 13 | @Data→@Getter @Setter，13 字段 Javadoc（contentExcerpt 大文本） |
| `LayeredResearchPlan.java` | 可变 | 2 | @Data→@Getter @Setter，2 字段 Javadoc，保留 layerCount/taskCount |

---

## 2. @Data 降级汇总

| 文件 | 降级前 | 降级后 |
|---|---|---|
| `ResearchLayer` | @Data | @Getter @Setter |
| `EvidenceCard` | @Data | @Getter @Setter |
| `LayerSummary` | @Data | @Getter @Setter |
| `InternalAnswerDraft` | @Data | @Getter @Setter |
| `DeepResearchSynthesisResult` | @Data | @Getter @Setter |
| `ResearchTask` | @Data | @Getter @Setter |
| `ResearchTaskHit` | @Data | @Getter @Setter |
| `LayeredResearchPlan` | @Data | @Getter @Setter |

**8 个 @Data 全部降级。0 残留。**

---

## 3. 保留方法

| 方法 | 类 |
|---|---|
| `layerCount()` | LayeredResearchPlan |
| `taskCount()` | LayeredResearchPlan |
| `@JsonCreator` 构造器 | DeepResearchAuditSnapshot |

---

## 4. 大文本标注

| 字段 | 标注 |
|---|---|
| `LayerSummary.summaryMarkdown` | 层级摘要大文本，禁止日志型 toString |
| `InternalAnswerDraft.draftMarkdown` | 内部草稿大文本 |
| `DeepResearchSynthesisResult.answerMarkdown` | 最终综合答案大文本 |
| `ResearchTaskHit.contentExcerpt` | 检索片段摘录，可能较长 |

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
@Data: 0/8 ✓
layerCount/taskCount: 保留 ✓
EvidenceLedger/graph state files: 无 diff ✓
B17 文件未触碰 ✓
```

---

## 6. B18 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B18a** | **已完成** | 10 |
| B18b | 待开始 | 4 (EvidenceLedger + 3 graph state) |

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 10 个目标文件 | 通过 |
| 8 个 @Data 全量降级 | 通过 |
| DeepResearchAuditSnapshot @Getter + @JsonCreator 保留 | 通过 |
| layerCount/taskCount 保留 | 通过 |
| EvidenceLedger/graph state 未触碰 | 通过 |
| 未 stage/commit/push | 通过 |
