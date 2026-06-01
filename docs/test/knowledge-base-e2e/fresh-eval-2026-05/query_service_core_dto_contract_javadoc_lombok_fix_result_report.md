# query/service 检索核心 DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）
批次：B0.5

---

## 1. 修改文件清单

| 文件 | 变更 |
|---|---|
| `src/main/java/com/xbk/lattice/query/service/QueryArticleHit.java` | 类级 @Getter + 10 字段 Javadoc + 删除 10 手写 getter |
| `src/main/java/com/xbk/lattice/query/service/RetrievalStrategy.java` | 7 字段 Javadoc（未使用 @Getter） |
| `src/main/java/com/xbk/lattice/query/service/RetrievalChannelRun.java` | 类级 @Getter + 6 字段 Javadoc + 删除 6 手写 getter |
| `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` | B0.5 状态回写 |

**无调用点迁移。** 三个类的所有构造器、static factory 方法、`@JsonCreator`、`@JsonProperty` 均未修改。

---

## 2. QueryArticleHit（10 字段）

### Lombok 改造

- 增加 `import lombok.Getter;`
- 类级 `@Getter`，替代 10 个手写 getter 方法（均为简单字段访问，无防御性拷贝）

### 字段注释要点

| 字段 | 注释重点 |
|---|---|
| `evidenceType` | 标识命中来自哪个通道（ARTICLE/FACT_CARD/SOURCE 等），RRF 融合、citation 组装和 fallback 选择据此分流 |
| `sourceId` | 原始资料 ID，FACT_CARD/SOURCE 有值，纯编译文章可能为空 |
| `articleKey` | 文章业务标识，RRF 融合用它做 chunk 去重和身份合并，chunk 级命中按 chunkIdentity 融合 |
| `conceptId` | 概念标识，用于按概念聚合命中 |
| `title` | 命中标题，检索展示和响应 projection 消费 |
| `content` | 命中正文，RRF 评分、LLM prompt 证据组装和 citation 校验的直接输入 |
| `metadataJson` | 附加上下文（chunkIdentity、fieldPath、terminalUnit 等），citation 和审计链路解析获取溯源信息 |
| `reviewStatus` | compile review 审查结论，只有 passed+ACTIVE 才会被 query visibility hard filter 放行 |
| `sourcePaths` | 原始文件路径，用于 citation 溯源展示和文件链接 |
| `score` | RRF 融合后最终评分，决定排序位置，fallback 证据选择和 conclusion 构建优先取高分命中 |

### 保留内容

- 8 个构造器（含 1 个 `@JsonCreator` 全参构造器）全部保留，参数和委托链逻辑未变

---

## 3. RetrievalStrategy（7 字段）

### Lombok 策略

**未使用 `@Getter`。** `getChannelWeights()` 和 `getEnabledChannels()` 返回防御性拷贝（`new LinkedHashMap<>(channelWeights)` / `new LinkedHashSet<>(enabledChannels)`），用 `@Getter` 会破坏这一行为。

### 字段注释要点

| 字段 | 注释重点 |
|---|---|
| `retrievalQuestion` | Query Rewrite 后的标准化查询文本，各通道以此作为检索输入 |
| `queryIntent` | 用户查询意图分类（GENERAL/STRUCTURED_QUERY 等），决定通道参与策略 |
| `answerShape` | 预期答案结构类型（GENERAL/STRUCTURED 等），影响通道选择和证据组装 |
| `parallelEnabled` | 是否并行多路召回，由后台配置决定 |
| `rrfK` | RRF 融合分数衰减速度，由后台配置决定，被 RrfFusionService 消费 |
| `channelWeights` | 通道名→权重映射，参与加权 RRF 融合，权重为 0 的通道不参与融合 |
| `enabledChannels` | 本轮实际参与的通道集合，由 RetrievalStrategyResolver 综合决定 |

### 保留内容

- 2 个构造器（含 1 个 `@JsonCreator` 全参）全部保留
- 7 个 getter + 2 个辅助方法（`isChannelEnabled`、`weightOf`）全部保留

---

## 4. RetrievalChannelRun（6 字段）

### Lombok 改造

- 增加 `import lombok.Getter;`
- 类级 `@Getter`，替代 6 个手写 getter 方法（均为简单字段访问）

### 字段注释要点

| 字段 | 注释重点 |
|---|---|
| `channelName` | 通道名称（fts/refkey/fact_card_lexical/article_vector 等），用于 audit 标识 |
| `status` | SUCCESS/SKIPPED/FAILED/TIMEOUT，审计系统据此判断通道可用性 |
| `durationMillis` | 执行耗时，用于 audit 性能分析和慢通道识别，构造器保证 ≥0 |
| `hitCount` | 候选命中数（通道截断后、RRF 融合前），SKIPPED/FAILED 时为 0，构造器保证 ≥0 |
| `skippedReason` | 跳过原因，仅 SKIPPED 时有意义 |
| `errorSummary` | 错误摘要（异常类名/错误消息/超时阈值），仅 FAILED/TIMEOUT 时有意义 |

### 保留内容

- 1 个 `@JsonCreator` 构造器（字段归一化逻辑未变）
- 5 个 static factory 方法（`success`、`skipped`、`failed`、`timeout` × 2）

---

## 5. 测试结果

### 全量测试

```
mvn test
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Redline 扫描

```
bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

---

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 3 个生产类 | 通过 |
| 未修改 retrieval/fallback/answer 行为逻辑 | 通过 |
| 未修改 rerank/RRF/SQL/prompt/fallback/citation 主链 | 通过 |
| RetrievalStrategy 未用 @Getter 替换防御性拷贝 getter | 通过 |
| 构造器/@JsonCreator/@JsonProperty 未改 | 通过 |
| 无调用点迁移 | 通过 |
| 未使用 @Data/@Setter/@AllArgsConstructor/@Builder | 通过 |
| 未修改 docs/模型绑定配置参考.md 和 special_cases_report.md | 通过 |
| 未 stage/commit/push | 通过 |

---

## 7. 残留风险评估

无残留风险。本轮仅修改字段 Javadoc 和简单 getter 的 Lombok 替换，未触及任何业务逻辑、构造器链或序列化行为。RetrievalStrategy 的防御性拷贝 getter 保持原样。
