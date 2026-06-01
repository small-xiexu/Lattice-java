# B12b Query Config/State 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B12b — query 侧 config/state 类（6 个 @ConfigurationProperties + 3 个 state）

---

## 一、拆分建议：B12b → B12b1 + B12b2

9 个候选类在 10 个上限以内，但按处置方式差异巨大，强烈建议拆分：

| 子批次 | 候选数 | 类型 | 处置方式 | 拆分理由 |
|---|---|---|---|---|
| **B12b1** | **6** | `@ConfigurationProperties` | 仅补字段 Javadoc，不引入 Lombok | 保持 Spring Boot 绑定方式，与 B12a 一致 |
| **B12b2** | **3** | 运行时 state 快照 | 可加 `@Getter` 删除手写 getter，+ 字段 Javadoc | 不可变 final-field 模型，getter 全部简单访问，适合 Lombok |

---

## 二、B12b1 — @ConfigurationProperties 类（6 个，仅 Javadoc）

| # | 类名 | 配置前缀 | 字段数 | 嵌套类 | Lombok | 特殊处理 |
|---|---|---|---|---|---|---|
| 1 | `QueryWorkingSetProperties` | `lattice.query.working-set` | 3 | — | 无 | — |
| 2 | `DeepResearchWorkingSetProperties` | `lattice.deep-research.working-set` | 3 | — | 无 | — |
| 3 | `QueryCacheProperties` | `lattice.query.cache` | 2 | — | 无 | — |
| 4 | `QueryReviewProperties` | `lattice.query.review` | 2 | — | 无 | — |
| 5 | `QuerySearchProperties` | `lattice.query.search` | 3 顶层 + 3 嵌套 | FtsProperties(3)、VectorProperties(4)、RetrievalDispatchProperties(4) | 无 | setRetrievalDispatch 有 null-safe |
| 6 | `QuerySemanticRules` | `lattice.query.semantic` | 14 | — | 无 | **11 个业务方法 + null-safe setter** |

### 2.1 每个 Properties 类的详细分析

#### QueryWorkingSetProperties / DeepResearchWorkingSetProperties（结构相同）

- 3 字段：`store`（默认 `"redis"`）、`keyPrefix`、`ttlSeconds`（默认 86400）
- 字段 Javadoc：
  - `store` — 存储模式（如 `redis` / `inmemory`）；`inmemory` 时 working set 不跨请求持久化
  - `keyPrefix` — Redis Key 前缀；用于隔离不同环境的 working set 数据
  - `ttlSeconds` — working set 条目 TTL（秒）；过期后自动清理，影响跨轮次对话的上下文保留时长

#### QueryCacheProperties

- 2 字段：`keyPrefix`（默认 `"llm:query:cache:"`）、`ttlSeconds`（默认 3600）
- 字段 Javadoc：
  - `keyPrefix` — LLM 查询缓存 Redis Key 前缀
  - `ttlSeconds` — 缓存 TTL（秒）；过期后缓存失效，相同 query 重新调用 LLM，影响成本和延迟

#### QueryReviewProperties

- 2 字段：`rewriteEnabled`（默认 true）、`maxRewriteRounds`（默认 1）
- 字段 Javadoc：
  - `rewriteEnabled` — Query 重写开关；false 时跳过 LLM 重写步骤，原始 query 直接检索（fail-open：不影响检索可用性，但召回质量可能下降）
  - `maxRewriteRounds` — 最大重写轮次；每轮重写后重新评估，超过后使用最后一轮结果

#### QuerySearchProperties ⚠️ 大文件

- 3 嵌套类 + 14 属性
- `setRetrievalDispatch()` 有 null-safe 逻辑：`retrievalDispatch == null ? new RetrievalDispatchProperties() : retrievalDispatch`
- 关键字段风险：

| 字段 | 所属类 | 默认值 | 运行影响 |
|---|---|---|---|
| `fts.enabled` | FtsProperties | true | FTS 增强开关；false 时回退到 PostgreSQL 默认分词（fail-open：降级但不阻塞检索） |
| `fts.preferredTsConfig` | FtsProperties | `"jiebacfg"` | 首选分词配置；不可用时回退到 fallbackTsConfig |
| `vector.enabled` | VectorProperties | **false** | 向量检索开关；**默认关闭**（安全默认，需显式开启） |
| `vector.embeddingModel` | VectorProperties | `"embedding-3"` | 默认 embedding 模型名 |
| `vector.expectedDimensions` | VectorProperties | 2000 | 期望向量维度；与实际模型维度不一致时检索异常 |
| `dispatch.maxConcurrency` | RetrievalDispatch | 4 | 检索最大并发数；影响数据库连接池压力和检索延迟 |
| `dispatch.channelTimeoutMillis` | RetrievalDispatch | 8000 | 单通道超时（毫秒）；超时通道结果被丢弃 |
| `dispatch.totalDeadlineMillis` | RetrievalDispatch | 12000 | 总截止时间（毫秒）；超时后未完成的通道被取消，部分结果参与 RRF |

#### QuerySemanticRules ⚠️ 特殊类

- `@ConfigurationProperties` 但有 11 个业务方法（`containsAnyXxxSignal`、`startsWithAnyBoilerplateSectionSignal`、`containsAnySignal`）
- 14 个 `List<String>` 字段，每个都有 null-safe setter（`signals == null ? List.of() : signals`）
- 这些 setter **不可被 Lombok `@Setter` 替代**（有防御性 null 处理）
- **处置**：仅补字段 Javadoc，不引入 Lombok。标注每个信号列表的分类器/路由器/规划器用途。

| 字段 | 用途 |
|---|---|
| `countSignals` | 计数类 query 信号（如"多少条""数量"）；触发计数型答案生成 |
| `comparisonSignals` | 对比类 query 信号（如"比较""区别"）；触发对比型答案生成 |
| `deepResearchSignals` | Deep Research 触发信号（如"排查""为什么"）；触发深度研究流程 |
| `configIdentifierSignals` | 精确配置查询信号（如"接口路径""在哪里配置"）；触发精确检索 |
| `sequenceSignals` | 顺序/流程类信号（如"步骤""先后"）；触发步骤型答案 |
| `architectureSignals` | 架构类信号（如"架构""解耦""服务边界"）；触发架构分析型答案 |
| `enumSignals` | 枚举/清单类信号（如"有哪些""列表"）；触发列表型答案 |
| `statusSignals` | 状态类信号（如"是否启用""进展"）；触发状态查询型答案 |
| `policySignals` | 规则/策略类信号（如"必须""禁止"）；触发规则型答案 |
| `capabilitySignals` | 能力/接入类信号（如"接入方式""支持哪些"）；触发能力介绍型答案 |
| `flowSignals` | 流程/流转类信号（如"运行流程""链路"） |
| `multiFocusSignals` | 多焦点分隔信号（如"和"）；用于拆分复合 query |
| `numericValueIntentSignals` | 数值意图信号（如"多少""最大""上限"） |
| `boilerplateSectionSignals` | 附录/结构标记信号（如"附录"）；用于识别文档样板章节 |

---

## 三、B12b2 — 运行时 State 类（3 个，可加 @Getter）

| # | 类名 | 字段数 | 构造器 | 手写 getter | boolean 命名 | 处置 |
|---|---|---|---|---|---|---|
| 1 | `QueryRetrievalSettingsState` | 14 | **3 个 telescoping**（8→12→14） | 14 | 标准 isXxx() | 类级 @Getter + 字段 Javadoc，保留 3 个构造器 |
| 2 | `QueryVectorConfigState` | 12 | 1 个全参 | 12 | 标准 isXxx() | 类级 @Getter + 字段 Javadoc，保留构造器 |
| 3 | `CompileReviewConfigState` | 9 | 1 个全参 | 9 | 标准 isXxx() | 类级 @Getter + 字段 Javadoc，保留构造器 |

### 3.1 每个 State 类的详细分析

#### QueryRetrievalSettingsState ⚠️ 最复杂

- 14 个 final 字段 + 14 个 `public static final DEFAULT_*` 常量
- **3 个 telescoping 构造器**：8-param → 12-param → 14-param，每个委托到更全的版本
- 14 个 getter 全部为简单字段访问，可安全用 `@Getter` 替代
- boolean getter 全部为 `isXxx()`，与 Lombok 一致
- **风险**：RRF 权重数组直接影响最终检索排序。`DEFAULT_RRF_K = 60`、各权重默认值（1.0-1.45）已经过调优
- 字段 Javadoc：与 B6 `AdminQueryRetrievalConfigRequest/Response` 对应的检索语义一致，需补充 state 特有语义（"运行时配置快照，由 database 配置或 properties 默认值计算得出"）

| 关键字段 | 默认值（常量） | 运行影响 |
|---|---|---|
| `parallelEnabled` | 无默认常量 | 并行召回开关；影响检索延迟和资源消耗 |
| `rewriteEnabled` | DEFAULT=true | Query 改写开关 |
| `intentAwareVectorEnabled` | DEFAULT=true | 意图感知向量开关 |
| `rrfK` | 60 | RRF K 参数；影响融合排序平滑度 |
| `ftsWeight` | 1.0 | 全文检索 RRF 权重 |
| `refkeyWeight` | 1.45 | RefKey RRF 权重（最高默认权重） |
| `articleChunkWeight` | 1.25 | 文章分块 RRF 权重 |
| `sourceWeight` | 1.0 | Source RRF 权重 |
| `sourceChunkWeight` | 1.30 | Source Chunk RRF 权重 |
| `factCardWeight` | 1.40 | Fact Card RRF 权重（第二高） |
| `contributionWeight` | 1.0 | Contribution RRF 权重 |
| `graphWeight` | 1.20 | Graph RRF 权重 |
| `articleVectorWeight` | 1.0 | 文章向量 RRF 权重 |
| `chunkVectorWeight` | 1.35 | Chunk 向量 RRF 权重 |

#### QueryVectorConfigState

- 12 个 final 字段，1 个全参构造器，12 个简单 getter
- 与 B6 `AdminVectorConfigResponse` 语义镜像（state → response 映射）
- 可安全使用 `@Getter`
- 字段 Javadoc 需含 `vectorEnabled` 四级检查语义、`rebuildRecommended` 触发条件、`profileDimensions` 与 `schemaDimensions` 一致性

#### CompileReviewConfigState（从 B12a 移入）

- 9 个 final 字段，1 个全参构造器，9 个简单 getter
- 由 `CompileReviewConfigService` 从 database 或 properties 构建
- 可安全使用 `@Getter`
- 字段 Javadoc 与 B7 `AdminCompileReviewConfigResponse` 语义镜像

---

## 四、排除清单

无额外排除。CompileReviewConfigState 已在 B12a 审查中明确排除至 B12b，现确认纳入 B12b2。

---

## 五、配置字段风险总结

### fail-open（自动降级）
| 字段 | 风险 |
|---|---|
| `fts.enabled=false` → 回退到 PostgreSQL 默认分词 | 中文分词质量下降，但检索不阻塞 |
| `rewriteEnabled=false` → 跳过 LLM 重写 | 召回可能不精确，但不影响可用性 |
| `vector.enabled=false` → 默认关闭 | 向量通道不可用，退化为纯 lexical+图谱检索 |

### fail-closed（阻塞）
| 字段 | 风险 |
|---|---|
| `dispatch.totalDeadlineMillis` 过小 | 大量通道超时取消，召回结果严重不足 |
| `dispatch.channelTimeoutMillis` 过小 | 单通道频繁超时，命中数锐减 |
| `vector.expectedDimensions` 与模型不匹配 | 向量检索结果完全异常 |

### 缓存一致性
| 字段 | 风险 |
|---|---|
| `QueryCacheProperties.ttlSeconds` 过长 | LLM 响应过期，答案可能基于陈旧知识 |
| `QueryCacheProperties.ttlSeconds` 过短 | 缓存命中率低，LLM 成本上升 |
| `*WorkingSetProperties.ttlSeconds` 过短 | 多轮对话上下文丢失 |

### 成本
| 字段 | 风险 |
|---|---|
| `maxRewriteRounds` > 1 | 每增加一轮就多一次 LLM 调用 |
| `vector.enabled=true` | 每次检索加载 embedding 模型计算向量 |

---

## 六、给 agentA 的下一轮提示词草案（B12b1）

```
交给 agentA。

本轮任务：对 B12b1 的 6 个 @ConfigurationProperties 类做字段契约 Javadoc 升级（仅补注释，不改 Lombok/绑定）。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_config_state_contract_analysis_report.md

## 修改范围（6 个文件，仅补字段 Javadoc）

### 核心原则
- **不引入任何 Lombok 注解**
- **不删除任何手写 getter/setter**
- **不修改字段类型、默认值、@ConfigurationProperties 前缀**
- 字段 Javadoc 必须包含：默认值、开关/阈值语义、fail-open/fail-closed、影响检索/缓存/工作集链路

### 文件列表

1. QueryWorkingSetProperties.java（3 字段）
2. DeepResearchWorkingSetProperties.java（3 字段，与 Query 结构相同）
3. QueryCacheProperties.java（2 字段，标注缓存过期对 LLM 成本和延迟的影响）
4. QueryReviewProperties.java（2 字段，标注 rewriteEnabled fail-open）
5. QuerySearchProperties.java（14 字段含嵌套类，标注 vector.enabled 默认 false 安全默认、dispatch 超时 fail-closed 风险）
6. QuerySemanticRules.java（14 信号列表字段，标注每个信号列表的分类器意图；**禁止修改 11 个业务方法**；setter 有 null-safe 逻辑不可用 Lombok 替代）

## 禁止事项
- 禁止添加 Lombok
- 禁止修改 getter/setter/业务方法
- 禁止修改 QuerySemanticRules 的 containsAnyXxxSignal 方法

## 完成后：回写 B12b1 → "已完成"，输出 B12b1_fix_result_report.md
```

---

## 七、给 agentA 的下一轮提示词草案（B12b2）

```
交给 agentA。

本轮任务：对 B12b2 的 3 个运行时 state 类做 @Getter 替代手写 getter + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_config_state_contract_analysis_report.md

## 修改范围（3 个文件）

1. QueryRetrievalSettingsState.java ⚠️ 最复杂
   - 添加类级 @Getter
   - 删除 14 个手写 getter（全部简单字段访问）
   - 保留 3 个 telescoping 构造器（8→12→14 参数委托）
   - 保留 14 个 public static final DEFAULT_* 常量
   - 14 字段补 Javadoc（审查报告 3.1 节），含默认值常量引用和 RRF 权重语义

2. QueryVectorConfigState.java
   - 添加类级 @Getter
   - 删除 12 个手写 getter
   - 保留全参构造器
   - 12 字段补 Javadoc（vectorEnabled 四级检查、rebuildRecommended 触发条件）

3. CompileReviewConfigState.java
   - 添加类级 @Getter
   - 删除 9 个手写 getter
   - 保留全参构造器
   - 9 字段补 Javadoc（与 B7 AdminCompileReviewConfigResponse 语义对应）

## 禁止事项
- 禁止修改构造器签名或委托逻辑
- 禁止修改 DEFAULT_* 常量
- 禁止修改字段类型、名称、final 修饰符
- 禁止引入 @Data/@Setter（state 类不可变）

## 验收门槛
- mvn compile -pl . -q 通过
- 自查：QueryRetrievalSettingsState 3 个构造器保留且委托逻辑不变

## 完成后：回写 B12b2 → "已完成"，输出 B12b2_fix_result_report.md
```

---

## 八、审查结论

- B12b 共 9 个类，拆分为 **B12b1（6 个 Properties，仅 Javadoc）** + **B12b2（3 个 State，@Getter + Javadoc）**。
- **B12b1 全部 6 个 Properties 类不引入 Lombok**（保持 Spring Boot relaxed binding），仅补约 38 个字段的配置语义 Javadoc。
- **B12b2 全部 3 个 State 类可安全加 @Getter**：所有 boolean getter 为 `isXxx()` 标准命名，所有 getter 为简单字段访问。可删除 35 个手写 getter。
- **QuerySemanticRules 特殊**：14 个 setter 有 null-safe 防御逻辑，11 个业务方法有完整 Javadoc，不引入任何 Lombok。
- **QueryRetrievalSettingsState 最复杂**：3 个 telescoping 构造器 + 14 个 DEFAULT_* 常量 + 14 个 final 字段，是 B12b2 的核心工作量。
