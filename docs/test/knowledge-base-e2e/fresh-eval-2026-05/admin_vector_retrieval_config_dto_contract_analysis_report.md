# B6 向量/检索配置 DTO 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B6 — `api/admin` vector / retrieval config DTO

---

## 一、B6 纳入文件清单（7 个类）

| # | 类名 | 类型 | Lombok 现状 | 手写 getter 数 | 手写 setter 数 | 构造器 |
|---|---|---|---|---|---|---|
| 1 | `AdminVectorConfigRequest` | Request | `@Data` `@NoArgsConstructor` `@AllArgsConstructor` | 0（Lombok 生成） | 0（Lombok 生成） | Lombok 生成全参 |
| 2 | `AdminVectorConfigResponse` | Response | 无 | 12 | 0 | 手写全参 |
| 3 | `AdminVectorIndexRebuildRequest` | Request | 无 | 2 | 2 | 无显式（默认无参） |
| 4 | `AdminVectorIndexRebuildResponse` | Response | 无 | 9 | 0 | 手写全参 |
| 5 | `AdminVectorIndexStatusResponse` | Response | 无 | 18 | 0 | 手写全参 |
| 6 | `AdminQueryRetrievalConfigRequest` | Request | `@Data` `@NoArgsConstructor` `@AllArgsConstructor` | 0（Lombok 生成） | 0（Lombok 生成） | Lombok 生成全参 |
| 7 | `AdminQueryRetrievalConfigResponse` | Response | 无 | 14 | 0 | 手写全参 |

**统计**：3 个 Request + 4 个 Response。手写 getter 总计 55 个（全部为简单字段访问，可用 `@Getter` 替代）。2 个 Request 有 `@Data` 需要降级。

---

## 二、明确排除文件清单及理由

| 排除文件 | 理由 | 归属批次 |
|---|---|---|
| `AdminVectorIndexController.java` | Controller 本体，不在 DTO 治理范围 | 不纳入 |
| `AdminQueryRetrievalConfigController.java` | Controller 本体，不在 DTO 治理范围 | 不纳入 |
| `AdminQueryRetrievalAuditController.java` | retrieval audit Controller | B9 |
| `AdminQueryRetrievalAuditDetailResponse.java` | retrieval audit 明细 DTO | B9 |
| `AdminQueryRetrievalAuditListResponse.java` | retrieval audit 列表 DTO | B9 |
| `AdminQueryRetrievalAuditRunResponse.java` | retrieval audit 运行 DTO | B9 |
| `AdminQueryRetrievalChannelHitResponse.java` | retrieval channel hit DTO | B9 |
| `AdminQueryRetrievalChannelRunResponse.java` | retrieval channel run DTO | B9 |
| `AdminCompileReviewConfigController.java` | compile review 控制器 | B7 |
| `AdminCompileReviewConfigRequest.java` | compile review 配置请求 | B7 |
| `AdminCompileReviewConfigResponse.java` | compile review 配置响应 | B7 |
| `AdminCompileReviewQueueActionRequest.java` | compile review 队列操作 | B7 |
| `AdminCompileReviewQueueActionResponse.java` | compile review 队列操作 | B7 |
| `AdminCompileReviewQueueItemResponse.java` | compile review 队列项 | B7 |
| `AdminCompileReviewQueueListResponse.java` | compile review 队列列表 | B7 |
| `AdminCompileReviewSummaryResponse.java` | compile review 摘要 | B7 |
| 其他 `api/admin` 下所有文件 | 分属 B5a/b、B7、B8、B9、B10、B11a/b/c | 各自批次 |

---

## 三、每个纳入类的 Lombok/Javadoc 改造建议

### 3.1 AdminVectorConfigRequest（Request）

**现状**：`@Data` `@NoArgsConstructor` `@AllArgsConstructor`，3 个字段无 Javadoc。

**改造**：
- **替换 `@Data` 为 `@Getter` + `@Setter`**：Spring `@RequestBody` 绑定依赖 setter + 无参构造，保留 `@NoArgsConstructor`。`@Data` 生成的 `toString()` 无敏感字段风险（operator 是普通用户名），但仍应降级以符合项目规范。
- **保留 `@AllArgsConstructor`**：service 层可能使用全参构造。
- **字段 Javadoc 升级**：

| 字段 | 当前注释 | 需补充 |
|---|---|---|
| `vectorEnabled` | 无 | 向量检索总开关；false 时运行期检索退化到非向量模式，召回质量和排序均受影响；修改后可能触发 rebuildRecommended |
| `embeddingModelProfileId` | 无 | embedding 模型配置主键；切换模型会导致向量维度变化，现有索引全部失效；必须 > 0 |
| `operator` | 无 | 操作人标识，用于审计日志；非空 |

### 3.2 AdminVectorConfigResponse（Response）

**现状**：无 Lombok，12 个 final 字段，手写全参构造器 + 12 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 12 个 getter 均为简单字段访问（`isVectorEnabled()` 对应 boolean，其余 `getXxx()`），可直接删除手写 getter。
- **保留构造器**：含 `@param` Javadoc，不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `vectorEnabled` | 当前向量检索是否启用；false 时前端应展示禁用态并提示影响范围 |
| `embeddingModelProfileId` | 当前生效的 embedding 模型配置主键；null 表示未配置 |
| `providerType` | 当前 embedding provider 类型（如 openai / local）；仅用于管理侧展示 |
| `modelName` | 当前 embedding 模型名称；索引内模型名与此不一致时触发 rebuildRecommended |
| `profileDimensions` | profile 配置的向量维度；与 schemaDimensions 不一致时触发 rebuildRecommended |
| `configSource` | 配置来源标识（如 manual / auto）；用于管理侧追溯配置变更路径 |
| `rebuildRecommended` | 是否建议重建向量索引；维度不匹配或模型切换后为 true |
| `rebuildReason` | 建议重建的原因说明；rebuildRecommended=false 时可为空 |
| `createdBy` | 配置创建人 |
| `updatedBy` | 配置最后更新人 |
| `createdAt` | 配置创建时间（ISO 字符串） |
| `updatedAt` | 配置最后更新时间（ISO 字符串） |

### 3.3 AdminVectorIndexRebuildRequest（Request）

**现状**：无 Lombok，2 个字段，手写 getter + setter。

**改造**：
- **添加 `@Getter` + `@Setter`**：替代手写 getter/setter。Spring `@RequestBody(required = false)` 绑定需要 setter，保留。
- **字段 Javadoc 升级**：

| 字段 | 当前注释 | 需补充 |
|---|---|---|
| `truncateFirst` | 无 | 是否先清空旧向量索引再重建；true 时存在索引空窗期（线上检索暂时无向量通道）；false 时增量追加，旧索引保留 |
| `operator` | 无 | 操作人标识，用于审计日志 |

### 3.4 AdminVectorIndexRebuildResponse（Response）

**现状**：无 Lombok，9 个 final 字段，手写全参构造器 + 9 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 9 个 getter 均为简单字段访问，可直接删除手写 getter。注意 `isTruncateFirst()` 对应 boolean 字段，Lombok 生成一致。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：

| 字段 | 需补充 |
|---|---|
| `targetArticleCount` | 本次重建目标文章数 |
| `previousIndexedArticleCount` | 重建前已索引文章数；与 indexedArticleCount 对比可知增减量 |
| `indexedArticleCount` | 重建后已索引文章数 |
| `previousIndexedChunkCount` | 重建前已索引分块数 |
| `indexedChunkCount` | 重建后已索引分块数；与 previousIndexedChunkCount 对比可知分块粒度变化 |
| `truncateFirst` | 是否先清空旧索引；影响索引空窗期时长 |
| `configuredModelName` | 重建使用的 embedding 模型名 |
| `operator` | 操作人 |
| `rebuiltAt` | 重建完成时间（ISO 字符串） |

### 3.5 AdminVectorIndexStatusResponse（Response）

**现状**：无 Lombok，18 个 final 字段（含 1 个 `List<String>`），手写全参构造器 + 18 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 18 个 getter 均为简单字段访问。注意混合类型：
  - 基本类型 boolean（`isVectorEnabled` 等）→ Lombok 生成 `isXxx()`
  - 包装类型 Boolean（`dimensionsMatch`）→ Lombok 生成 `getXxx()`
  - 当前代码已一致，替换后行为不变
- **保留构造器**：不变。
- **字段 Javadoc 升级**（重点是有运行影响的字段）：

| 字段 | 需补充 |
|---|---|
| `vectorEnabled` | 向量检索是否启用；四级可用性检查的第一级 |
| `vectorTypeAvailable` | 数据库 vector 类型是否可用（如 pgvector 扩展是否安装）；四级检查第二级 |
| `vectorIndexTableAvailable` | 向量索引表是否存在且可访问；四级检查第三级 |
| `indexingAvailable` | 当前是否可执行索引操作；四级检查第四级，综合前三项 + 运行时锁状态 |
| `embeddingModelProfileId` | 当前配置的 embedding 模型主键；null 表示未配置 |
| `configuredProviderType` | 当前配置的 provider 类型 |
| `configuredModelName` | 当前配置的 embedding 模型名；与 indexedModelNames 不一致时说明历史索引模型已变 |
| `configuredExpectedDimensions` | 配置期望的向量维度 |
| `profileDimensions` | profile 记录的实际维度；null 表示 profile 未就绪 |
| `embeddingColumnType` | 向量列的数据库类型（如 vector(1536)） |
| `schemaDimensions` | 数据库 schema 中的向量维度；null 表示无法读取 |
| `dimensionsMatch` | 配置维度与 schema 维度是否精确匹配；null 表示无法判断；false 时向量检索可能异常 |
| `dimensionsConsistent` | 配置、profile、schema 三维度是否一致；综合一致性判断，比 dimensionsMatch 更宽泛 |
| `annIndexReady` | ANN 近似最近邻索引是否就绪；false 时向量检索性能严重退化（退化为全表扫描） |
| `annIndexType` | ANN 索引类型（如 ivfflat / hnsw）；用于管理侧确认索引算法 |
| `articleCount` | 文章总数 |
| `indexedArticleCount` | 已向量索引的文章数；与 articleCount 对比可知索引覆盖率 |
| `indexedModelNames` | 当前索引中出现的所有模型名；多模型共存提示，切换模型后旧向量可能仍在索引中但维度不匹配 |
| `latestUpdatedAt` | 索引最近更新时间 |

### 3.6 AdminQueryRetrievalConfigRequest（Request）

**现状**：`@Data` `@NoArgsConstructor` `@AllArgsConstructor`，14 个字段无 Javadoc。

**改造**：
- **替换 `@Data` 为 `@Getter` + `@Setter`**：Spring `@RequestBody` 绑定依赖 setter + 无参构造。`@Data` 生成的 `toString()` 无敏感字段，但含 11 个 double 权重值，意外打印到日志会污染。降级为 `@Getter/@Setter` 后可配合 `@ToString` 按需控制。
- **保留 `@NoArgsConstructor` + `@AllArgsConstructor`**。
- **字段 Javadoc 升级**（每个字段均直接影响检索行为）：

| 字段 | 需补充 |
|---|---|
| `parallelEnabled` | 并行召回开关；true 时多个检索通道并行执行，降低延迟但增加资源消耗；false 时串行执行 |
| `rewriteEnabled` | 查询改写开关；true 时对用户原始 query 做 LLM 改写/扩展后再检索；影响最终召回结果集 |
| `intentAwareVectorEnabled` | 意图感知向量通道开关；true 时根据 query 意图动态选择向量通道组合；false 时使用固定通道 |
| `ftsWeight` | 全文检索通道在 RRF 融合时的权重；0 表示关闭全文通道；越大在最终排序中占比越高 |
| `refkeyWeight` | RefKey 引用键通道在 RRF 融合时的权重 |
| `articleChunkWeight` | 文章分块 lexical 通道在 RRF 融合时的权重 |
| `sourceWeight` | Source（知识源）通道在 RRF 融合时的权重 |
| `sourceChunkWeight` | Source 分块 lexical 通道在 RRF 融合时的权重 |
| `factCardWeight` | Fact Card lexical 通道在 RRF 融合时的权重 |
| `contributionWeight` | Contribution（贡献度）通道在 RRF 融合时的权重 |
| `graphWeight` | Graph（知识图谱）通道在 RRF 融合时的权重 |
| `articleVectorWeight` | 文章级别向量通道在 RRF 融合时的权重 |
| `chunkVectorWeight` | 分块级别向量通道在 RRF 融合时的权重 |
| `rrfK` | RRF（Reciprocal Rank Fusion）算法的 K 参数；控制排名平滑度，值越大排名越平滑但区分度越低；必须 > 0 |

### 3.7 AdminQueryRetrievalConfigResponse（Response）

**现状**：无 Lombok，14 个 final 字段，手写全参构造器 + 14 个手写 getter。

**改造**：
- **添加类级 `@Getter`**：所有 14 个 getter 均为简单字段访问（3 个 `isXxx()` + 11 个 `getXxx()`），可直接删除手写 getter。
- **保留构造器**：不变。
- **字段 Javadoc 升级**：字段含义与 Request 一致，补充“当前生效值”语义 + 空值含义：

| 字段 | 需补充（在 Request 注释基础上） |
|---|---|
| `parallelEnabled` | 当前是否启用并行召回 |
| `rewriteEnabled` | 当前是否启用查询改写 |
| `intentAwareVectorEnabled` | 当前是否启用意图感知向量通道 |
| `ftsWeight` | 当前全文检索 RRF 权重 |
| `refkeyWeight` | 当前 RefKey RRF 权重 |
| `articleChunkWeight` | 当前文章分块 RRF 权重 |
| `sourceWeight` | 当前 Source RRF 权重 |
| `sourceChunkWeight` | 当前 Source 分块 RRF 权重 |
| `factCardWeight` | 当前 Fact Card RRF 权重 |
| `contributionWeight` | 当前 Contribution RRF 权重 |
| `graphWeight` | 当前 Graph RRF 权重 |
| `articleVectorWeight` | 当前文章向量 RRF 权重 |
| `chunkVectorWeight` | 当前分块向量 RRF 权重 |
| `rrfK` | 当前 RRF K 参数 |

---

## 四、配置字段风险与运行影响说明

### 4.1 高风险字段（修改会立即影响线上检索/索引行为）

| 字段 | 所属类 | 影响链路 | 风险说明 |
|---|---|---|---|
| `vectorEnabled` | Request/Response/Status | 全链路 | 关闭后向量检索通道完全失效，召回退回纯 lexical/图谱模式，Top-K 结果集显著变化 |
| `embeddingModelProfileId` | Request/Response/Status | 向量化全链路 | 切换模型→维度变化→现有索引全部失效→`rebuildRecommended=true`；未重建前向量检索结果不可用 |
| `parallelEnabled` | Request/Response | 检索延迟与资源 | 并行模式下所有通道同时发起，数据库连接池压力翻倍；串行模式延迟叠加 |
| `rewriteEnabled` | Request/Response | 查询意图链路 | 改写由 LLM 执行，结果不可完全预测；关闭后召回更贴近原始 query |
| `intentAwareVectorEnabled` | Request/Response | 向量通道选择 | 动态选择策略的准确性依赖意图识别模型；关闭后行为确定但可能漏召回 |
| `ftsWeight` ~ `chunkVectorWeight` | Request/Response | RRF 排序主链 | 11 个权重直接决定各通道结果在最终排序中的占比；任一权重设为 0 等价于关闭该通道 |
| `rrfK` | Request/Response | RRF 排名平滑 | 过小（如 1）导致排名断层（只有 top-1 获得区分度）；过大（如 120+）排名趋同失去区分 |
| `truncateFirst` | Request/Response | 索引重建范围 | true 时清空所有索引→线上检索出现向量通道空窗期（时长取决于文章量）；false 时增量追加但旧维度向量残留 |

### 4.2 中等风险字段（影响管理侧决策但不直接影响检索）

| 字段 | 所属类 | 影响链路 | 风险说明 |
|---|---|---|---|
| `dimensionsMatch` / `dimensionsConsistent` | Status | 管理侧告警 | false 时前端应展示警告并引导重建；不影响当前检索但指示潜在问题 |
| `annIndexReady` | Status | 向量检索性能 | false 时无 ANN 索引→向量相似度计算退化为全表扫描→延迟飙升 |
| `rebuildRecommended` / `rebuildReason` | Response | 管理侧决策 | 提示但不强制；管理侧可忽略，但继续使用不匹配的模型会导致检索质量下降 |
| `indexedModelNames` | Status | 多模型共存 | 多个模型名共存说明历史上线过不同模型；切换模型后的旧向量不会被自动清理 |

### 4.3 低风险字段（纯信息展示/审计）

| 字段 | 所属类 | 说明 |
|---|---|---|
| `operator` / `createdBy` / `updatedBy` | Request/Response | 审计追踪，不影响检索行为 |
| `createdAt` / `updatedAt` / `rebuiltAt` / `latestUpdatedAt` | Response/Status | 时间戳展示 |
| `providerType` / `modelName` / `configuredModelName` | Response/Status | 只读展示，用于管理侧确认 |
| `profileDimensions` / `schemaDimensions` / `embeddingColumnType` | Response/Status | 只读展示 |
| `articleCount` / `indexedArticleCount` / `indexedChunkCount` / `targetArticleCount` | Status/Rebuild | 索引统计展示 |
| `configSource` / `annIndexType` | Response/Status | 元数据展示 |

### 4.4 本轮约束

以上所有字段**本轮只做契约注释，不改行为**。具体而言：
- 不调整任何字段的类型、默认值或验证规则
- 不修改 `AdminVectorIndexController.validateConfigRequest()` 的验证逻辑
- 不修改 `AdminQueryRetrievalConfigController.validateRequest()` / `validateWeight()` 的验证逻辑
- 不修改 controller 中 `toConfigResponse()` / `toResponse()` 的映射逻辑
- 不触碰 `QueryVectorConfigState`、`QueryRetrievalSettingsState` 等 domain 层对象

---

## 五、给 agentA 的下一轮提示词草案

> 以下是给 agentA 的完整任务提示词，可直接用于下一轮执行：

```
交给 agentA。

本轮任务：对 B6 的 7 个 vector/retrieval config DTO 做 Lombok 治理 + 字段契约 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_vector_retrieval_config_dto_contract_analysis_report.md

## 修改范围（7 个文件）

### Request 类（保留 setter + 无参构造，仅降级 @Data + 补 Javadoc）

1. AdminVectorConfigRequest.java
   - 替换 @Data 为 @Getter + @Setter
   - 保留 @NoArgsConstructor + @AllArgsConstructor
   - 3 个字段补 Javadoc（参考审查报告 3.1 节）

2. AdminVectorIndexRebuildRequest.java
   - 添加 @Getter + @Setter
   - 删除 2 个手写 getter + 2 个手写 setter
   - 2 个字段补 Javadoc（参考审查报告 3.3 节）

3. AdminQueryRetrievalConfigRequest.java
   - 替换 @Data 为 @Getter + @Setter
   - 保留 @NoArgsConstructor + @AllArgsConstructor
   - 14 个字段补 Javadoc（参考审查报告 3.6 节）

### Response 类（添加 @Getter，删除手写 getter，保留构造器）

4. AdminVectorConfigResponse.java
   - 添加类级 @Getter
   - 删除 12 个手写 getter
   - 12 个字段补 Javadoc（参考审查报告 3.2 节）
   - 保留全参构造器及 @param Javadoc

5. AdminVectorIndexRebuildResponse.java
   - 添加类级 @Getter
   - 删除 9 个手写 getter
   - 9 个字段补 Javadoc（参考审查报告 3.4 节）
   - 保留全参构造器及 @param Javadoc

6. AdminVectorIndexStatusResponse.java
   - 添加类级 @Getter
   - 删除 18 个手写 getter
   - 18 个字段补 Javadoc（参考审查报告 3.5 节）
   - 保留全参构造器及 @param Javadoc（注意 List<String> import 保留）

7. AdminQueryRetrievalConfigResponse.java
   - 添加类级 @Getter
   - 删除 14 个手写 getter
   - 14 个字段补 Javadoc（参考审查报告 3.7 节）
   - 保留全参构造器及 @param Javadoc

## 禁止事项

- 禁止修改任何 controller 文件
- 禁止修改任何 service / domain / infra / config 文件
- 禁止修改 test 文件
- 禁止修改构造器签名或逻辑
- 禁止修改字段类型、名称、访问修饰符
- 禁止修改 validation 逻辑
- 禁止修改 toConfigResponse() / toResponse() 等映射方法
- 禁止修改 scripts/scan-redline.sh、special_cases_report.md、redline allowlist
- 禁止混入 B7/B8/B9/B10 或 query/config/state 类
- 禁止调整向量参数、检索权重、RRF、SQL、prompt、fallback 默认值
- 禁止修改 query/retrieval/answer/fallback 主链行为

## 验收门槛

- 编译通过（mvn compile -pl . -q）
- 全量 mvn test 通过（995/0/0/0）
- redline 无新增 BLOCKER
- 自查：无字段翻译式空泛注释，每个字段注释回答“影响什么链路、为空的含义”

## 完成后

1. 回写计划文件：B6 状态 → "已完成"，备注实际修改内容
2. 输出 B6_fix_result_report.md
3. 不 stage、不 commit、不 push
```
```

---

## 六、审查结论

- B6 范围清晰，7 个 DTO 无越界耦合。
- 2 个 Request（`AdminVectorConfigRequest`、`AdminQueryRetrievalConfigRequest`）的 `@Data` 需降级为 `@Getter/@Setter`，理由：未来可能加敏感字段 + 权重值 `toString()` 污染日志。
- 1 个 Request（`AdminVectorIndexRebuildRequest`）需补 `@Getter/@Setter` 替代手写方法。
- 4 个 Response 均可安全使用类级 `@Getter` 替代手写 getter（共 53 个）。
- `dimensionsMatch`（Boolean 包装类型）在当前代码中手写为 `getDimensionsMatch()`，Lombok `@Getter` 生成一致，无行为差异。
- `AdminVectorIndexStatusResponse.indexedModelNames` 是 `List<String>`，Lombok `@Getter` 生成标准 getter，与手写一致，无防御性拷贝问题（该类是不可变 Response，构造后不再修改）。
- 所有高风险字段（向量开关、模型 ID、权重数组、RRF K、truncateFirst）本轮仅标注契约语义，不改运行行为。
