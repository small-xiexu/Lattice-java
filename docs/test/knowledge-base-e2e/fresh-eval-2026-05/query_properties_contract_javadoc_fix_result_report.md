# B12b1: Query 侧 @ConfigurationProperties 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B12b1（B12b 第 1 子批次，6/9 类）

---

## 1. 修改文件清单

| 文件 | 配置前缀 | 字段数 | 变更 |
|---|---|---|---|
| `QueryWorkingSetProperties.java` | `lattice.query.working-set` | 3 | 字段 Javadoc（store/keyPrefix/ttlSeconds 语义） |
| `DeepResearchWorkingSetProperties.java` | `lattice.deep-research.working-set` | 3 | 字段 Javadoc |
| `QueryCacheProperties.java` | `lattice.query.cache` | 2 | 字段 Javadoc（ttlSeconds 成本/陈旧权衡） |
| `QueryReviewProperties.java` | `lattice.query.review` | 2 | 字段 Javadoc（rewriteEnabled fail-open） |
| `QuerySearchProperties.java` | `lattice.query.search` | 14（顶层3+嵌套11） | 字段 Javadoc（FTS/向量/调度风险标注） |
| `QuerySemanticRules.java` | `lattice.query.semantic` | 14 | 字段 Javadoc（信号分类器/路由器用途） |

**未改**：Lombok（0 添加）、`@ConfigurationProperties` prefix、默认值、null-safe setter、业务方法。

---

## 2. 关键安全/风险标注

| 字段 | 所属文件 | 标注 |
|---|---|---|
| `vector.enabled` | QuerySearchProperties | 默认 false（安全默认，需显式开启，pgvector 扩展+索引就绪） |
| `vector.expectedDimensions` | QuerySearchProperties | 与实际模型维度不一致→检索异常，切换模型需重建索引 |
| `dispatch.channelTimeoutMillis` | QuerySearchProperties | 过小→正常慢通道被误杀，召回不足 |
| `dispatch.totalDeadlineMillis` | QuerySearchProperties | 过小→多数通道未完成被截断；应显著大于 channelTimeoutMillis |
| `fts.enabled` | QuerySearchProperties | fail-open：false 时回退 PostgreSQL 默认分词，中文分词质量下降 |
| `rewriteEnabled` | QueryReviewProperties | fail-open：false 时检索仍可用但召回质量下降 |
| `cache.ttlSeconds` | QueryCacheProperties | 过长→答案陈旧；过短→LLM 成本和延迟上升 |
| `working-set.ttlSeconds` | QueryWorkingSet/DeepResearch | 过短→多轮对话上下文丢失 |

### QuerySemanticRules 信号分类

14 个信号列表按分类/路由用途标注：
- 意图路由：countSignals、comparisonSignals、enumSignals、statusSignals、numericValueIntentSignals
- 流程路由：deepResearchSignals、sequenceSignals、flowSignals
- 架构路由：architectureSignals、configIdentifierSignals、policySignals、capabilitySignals
- 解析控制：multiFocusSignals（多主题分隔）、boilerplateSectionSignals（附录跳过）

---

## 3. 排除文件

| 文件 | 理由 | 去向 |
|---|---|---|
| `QueryRetrievalSettingsState.java` | 运行时 state 快照，非 @ConfigurationProperties | B12b2 |
| `QueryVectorConfigState.java` | 运行时 state 快照 | B12b2 |
| `CompileReviewConfigState.java` | 运行时 state 快照 | B12b2 |

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
rg -n "lombok|@Data|@Getter|@Setter" (6 文件): (无结果) ✓
expectedDimensions 标注: ✓
totalDeadlineMillis 标注: ✓
deepResearchSignals 标注: ✓
ttlSeconds 标注: ✓
```

QuerySemanticRules 的 14 个 null-safe setter 和 11 个业务方法未改。

---

## 5. B12b 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B12b1** | **已完成** | 6 |
| B12b2 | 待开始 | 3 (state 快照) |

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 6 个目标文件 | 通过 |
| 未添加 Lombok | 通过 |
| 未改 getter/setter | 通过 |
| 未改 null-safe setter | 通过（QuerySemanticRules 14 个） |
| 未改业务方法 | 通过（QuerySemanticRules 11 个） |
| 未混入 B12b2/B11/B12a | 通过 |
| 未 stage/commit/push | 通过 |
