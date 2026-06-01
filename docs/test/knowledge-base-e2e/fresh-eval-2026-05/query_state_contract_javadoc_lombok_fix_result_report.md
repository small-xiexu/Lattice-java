# B12b2: Query State 快照 @Getter 降级 + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B12b2（B12b 第 2/最后子批次，3/9 类）

---

## 1. 修改文件清单

| 文件 | 字段数 | 变更 |
|---|---|---|
| `QueryRetrievalSettingsState.java` | 14 | 类级 `@Getter`，删除 14 getter，14 字段 Javadoc，保留 3 构造器+14 常量+DEFAULT_* |
| `QueryVectorConfigState.java` | 12 | 类级 `@Getter`，删除 12 getter，12 字段 Javadoc，保留构造器 |
| `CompileReviewConfigState.java` | 9 | 类级 `@Getter`，删除 9 getter，9 字段 Javadoc，保留构造器 |

---

## 2. Lombok 统计

| 文件 | @Getter | 删除 getter | 保留构造器 |
|---|---|---|---|
| `QueryRetrievalSettingsState` | 1 | 14 | 3（telescoping 委托） |
| `QueryVectorConfigState` | 1 | 12 | 1 |
| `CompileReviewConfigState` | 1 | 9 | 1 |
| **合计** | **3** | **35** | **5** |

**未使用**：`@Data`、`@Setter`、`@Builder`

**B12b 合计（B12b1 + B12b2 = 9 类）**：6 properties 字段 Javadoc + 3 state @Getter + 35 getter 删除。

---

## 3. 关键字段 Javadoc 标注

### QueryRetrievalSettingsState
- `parallelEnabled`：并行/串行对延迟和 DB 连接池的影响
- `rewriteEnabled`：LLM 改写后召回与原始 query 的语义偏移
- `rrfK`：排名平滑度权衡，过小→断层，过大→趋同
- 11 个 RRF 权重：0=关闭对应通道

### QueryVectorConfigState
- `vectorEnabled`：false 时退回纯 lexical/图谱模式
- `modelName`：与索引内模型不一致→rebuildRecommended
- `profileDimensions`：与 schemaDimensions 不一致→rebuildRecommended

### CompileReviewConfigState
- `autoFixEnabled`：false 时所有问题直接进入人工复核队列
- `maxFixRounds`：过大导致 LLM 调用次数激增
- `allowPersistNeedsHumanReview`：false 时阻止未审核文章落库

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
rg -n '@Data|@Setter|@Builder' (3 文件): (无结果) ✓
rg -n '@Getter' (3 文件): 3/3 ✓
QueryRetrievalSettingsState 构造器: 3 ✓
DEFAULT_RRF_K / DEFAULT_*_WEIGHT 常量: 未改 ✓
```

---

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 3 个目标文件 | 通过 |
| 仅 @Getter，无 @Data/@Setter/@Builder | 通过 |
| 3 个 telescoping 构造器保留 | 通过 |
| 14 个 DEFAULT_* 常量保留 | 通过 |
| boolean getter 命名与 Lombok 一致 | 通过 |
| 未修改 B12b1/B12a/B11 | 通过 |
| 未 stage/commit/push | 通过 |
