# B18b: EvidenceLedger + Graph State 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B18b（B18 第 2/最后子批次，4/14 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 字段 | @Data→ | 特殊处置 |
|---|---|---|---|---|
| `EvidenceLedger.java` | 账本（不可变引用） | 8 | @Getter | **无 @Setter**。addXxx/markCoverage 等业务方法全部保留 |
| `QueryGraphState.java` | Graph 状态 | 55 | @Getter @Setter | 分组 Javadoc（上下文/检索/答案/路由/轮次） |
| `CompileGraphState.java` | Graph 状态 | 39 | @Getter @Setter | 分组 Javadoc（任务/模式/工作集/审查/图谱） |
| `DeepResearchState.java` | Graph 状态 | 19 | @Getter @Setter | 分组 Javadoc（上下文/计划/综合/控制） |

---

## 2. @Data 降级

| 文件 | 降级前 | 降级后 |
|---|---|---|
| `EvidenceLedger` | @Data | **@Getter**（禁止 setter） |
| `QueryGraphState` | @Data | @Getter @Setter |
| `CompileGraphState` | @Data | @Getter @Setter |
| `DeepResearchState` | @Data | @Getter @Setter |

**4 个 @Data 全部降级。0 残留。**

---

## 3. EvidenceLedger 特殊处置

**@Getter 无 @Setter**：ledger 内部的集合/Map 通过业务方法（addCard/addFactFinding/addAnchor/addProjectionCandidate/markCoverage/registerMustResolveFactKeys）维护，外部不得直接替换集合引用。

**保留**：
- 3 static 常量：ANCHOR_VALIDATOR/MIN_FINDING_CONFIDENCE/MIN_PROJECTABLE_ANCHOR_SCORE
- 所有 20+ 个业务方法和 private helper
- 字段默认值：new ArrayList/new LinkedHashMap
- 领域语义：cardsByTaskId(任务索引)、findingsByFactKey(factKey索引)、conflicts(冲突)、complements(互补)、coverageState(覆盖度)

**验证**：`rg setCards\(|setCardsByTaskId\(` 等 8 个 setter 无外部调用。

---

## 4. 关键语义标注

| 文件 | 关键字段 | 标注 |
|---|---|---|
| `EvidenceLedger` | conflicts/complements/coverageState | 分别用于冲突识别、互补事实标记、投影覆盖度 |
| `QueryGraphState` | `*Ref` 字段（28 个） | 工作集/缓存/审计对象引用键，非大对象本体 |
| `CompileGraphState` | sourceFileIdsByPath | new LinkedHashMap，路径→ID 映射 |
| `CompileGraphState` | graph*UpsertCount | AST 图谱入库结果计数 |
| `DeepResearchState` | llmCallBudgetRemaining | LLM 调用预算剩余次数 |
| `DeepResearchState` | projectionRetryCount | 投影修复重试次数 |

---

## 5. B18 完整汇总（14 类）

| 子批次 | 类数 | @Data 降级 | getter 删除 |
|---|---|---|---|
| B18a | 10 | 8 | 2 |
| B18b | 4 | 4 | — |
| **合计** | **14** | **12** | **2** |

---

## 6. 验证

```
mvn compile: BUILD SUCCESS
@Data (4 文件): 0/4 ✓
EvidenceLedger @Setter: 0（无） ✓
Graph state @Getter/@Setter: 3/3（各 1+1） ✓
EvidenceLedger setCards/setFindings 等外部调用: 0 ✓
B18a/B17 文件未触碰 ✓
```

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 4 个目标文件 | 通过 |
| EvidenceLedger 无 @Setter | 通过 |
| Graph state 保留 @Setter | 通过 |
| 业务方法/常量/默认值 全部保留 | 通过 |
| 未修改 B18a/B17/service/mapper | 通过 |
| 未 stage/commit/push | 通过 |
