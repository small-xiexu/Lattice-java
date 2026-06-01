# api/admin Vector / Retrieval Config DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-06-01
改造人：agentA（代码执行 Agent）
批次：B6

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminVectorConfigRequest.java` | 可变 Request | `@Data`→`@Getter/@Setter`，保留 `@NoArgsConstructor`+`@AllArgsConstructor`，3 字段 Javadoc |
| `AdminVectorConfigResponse.java` | 不可变 Response | 类级 `@Getter`，删除 12 手写 getter，12 字段 Javadoc，保留构造器 |
| `AdminVectorIndexRebuildRequest.java` | 可变 Request | 新增 `@Getter/@Setter`，删除 2 手写 getter+2 手写 setter，2 字段 Javadoc |
| `AdminVectorIndexRebuildResponse.java` | 不可变 Response | 类级 `@Getter`，删除 9 手写 getter，9 字段 Javadoc，保留构造器 |
| `AdminVectorIndexStatusResponse.java` | 不可变 Response | 类级 `@Getter`，删除 18 手写 getter，18 字段 Javadoc，保留构造器 |
| `AdminQueryRetrievalConfigRequest.java` | 可变 Request | `@Data`→`@Getter/@Setter`，保留 `@NoArgsConstructor`+`@AllArgsConstructor`，14 字段 Javadoc |
| `AdminQueryRetrievalConfigResponse.java` | 不可变 Response | 类级 `@Getter`，删除 14 手写 getter，14 字段 Javadoc，保留构造器 |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B6 状态回写 + "当前下一步" → B7 |

**无调用点迁移。** 构造器签名、getter/setter 方法名、Spring/Jackson 绑定方式均未修改。

---

## 2. 各文件详细变更

### 2.1 AdminVectorConfigRequest（3 字段，@Data→@Getter/@Setter）

| 字段 | 注释要点 |
|---|---|
| `vectorEnabled` | 向量检索总开关。false 时退化到非向量模式，召回质量和排序均受影响。修改后可能触发 rebuildRecommended。null 时行为由服务端决定。 |
| `embeddingModelProfileId` | embedding 模型配置主键。切换模型导致维度变化→现有索引全部失效→需触发重建。null 表示未配置。 |
| `operator` | 操作人标识，用于审计日志。服务端应校验非空。 |

### 2.2 AdminVectorConfigResponse（12 字段，类级 @Getter）

| 字段 | 注释要点 |
|---|---|
| `vectorEnabled` | 当前向量检索是否启用。false 时前端应展示禁用态并提示影响范围。 |
| `embeddingModelProfileId` | 当前生效的 embedding 模型配置主键。null 表示未配置。与索引内模型名不一致时 rebuildRecommended=true。 |
| `providerType` | 当前 provider 类型（openai/local）。仅管理侧展示，不参与检索路径决策。 |
| `modelName` | 当前 embedding 模型名。索引内模型名不一致时触发 rebuildRecommended。 |
| `profileDimensions` | profile 配置的向量维度。与 schemaDimensions 不一致时触发 rebuildRecommended。null 表示 profile 未就绪。 |
| `configSource` | 配置来源标识（manual/auto）。用于管理侧追溯配置变更路径。 |
| `rebuildRecommended` | 是否建议重建向量索引。维度不匹配或模型切换后为 true。忽略此建议继续使用不匹配模型→检索质量下降。 |
| `rebuildReason` | 建议重建的原因说明。rebuildRecommended=false 时可为空。 |
| `createdBy` / `updatedBy` | 配置创建人/最后更新人。审计追踪用途。 |
| `createdAt` / `updatedAt` | 配置创建/更新时间（ISO-8601）。 |

### 2.3 AdminVectorIndexRebuildRequest（2 字段，新增 @Getter/@Setter）

| 字段 | 注释要点 |
|---|---|
| `truncateFirst` | 是否先清空旧索引再重建。true 时存在索引空窗期（线上检索暂时无向量通道）。false 时增量追加，但切换模型后旧维度向量残留。 |
| `operator` | 操作人标识。用于审计日志。 |

### 2.4 AdminVectorIndexRebuildResponse（9 字段，类级 @Getter）

| 字段 | 注释要点 |
|---|---|
| `targetArticleCount` | 本次重建目标文章数（启动时快照）。 |
| `previousIndexedArticleCount` / `indexedArticleCount` | 重建前/后已索引文章数。对比可知增量或减量。 |
| `previousIndexedChunkCount` / `indexedChunkCount` | 重建前/后已索引分块数。对比可知分块粒度变化。 |
| `truncateFirst` | 回显请求中的 truncateFirst 值，便于管理侧确认实际执行模式。 |
| `configuredModelName` | 重建使用的 embedding 模型名。 |
| `operator` | 操作人。 |
| `rebuiltAt` | 重建完成时间（ISO-8601）。 |

### 2.5 AdminVectorIndexStatusResponse（18 字段，类级 @Getter）

四级可用性检查（vectorEnabled → vectorTypeAvailable → vectorIndexTableAvailable → indexingAvailable）已逐级说明。

关键标注：
- `dimensionsMatch`（Boolean）：配置维度与 schema 维度是否精确匹配。null=无法判断，false=向量检索可能异常。**Lombok 生成 getDimensionsMatch()，与手写一致。**
- `dimensionsConsistent`（boolean）：三维度综合一致性判断，比 dimensionsMatch 更宽泛。
- `annIndexReady`：ANN 索引是否就绪。false 时向量相似度退化为全表扫描，延迟飙升。
- `indexedModelNames`（List\<String\>）：多模型共存说明历史上线过不同模型，切换后旧向量不会自动清理。

### 2.6 AdminQueryRetrievalConfigRequest（14 字段，@Data→@Getter/@Setter）

11 个 RRF 权重字段均标注：0=关闭该通道；值越大在最终排序中占比越高。高风险字段重点标注：

| 字段 | 风险说明 |
|---|---|
| `parallelEnabled` | true=并行执行降低延迟但增加数据库连接池压力；false=串行延迟叠加但资源可控 |
| `rewriteEnabled` | true=LLM 改写 query 后再检索，召回结果可能与原始 query 存在语义偏移 |
| `intentAwareVectorEnabled` | true=动态选择向量通道，策略准确性依赖意图识别模型 |
| `rrfK` | RRF K 参数。过小（1）→排名断层；过大（120+）→排名趋同。必须>0。 |

### 2.7 AdminQueryRetrievalConfigResponse（14 字段，类级 @Getter）

与 Request 字段对应，补充"当前生效值"语义。11 个权重字段均标注 0=通道关闭。

---

## 3. Lombok 使用统计

| 类 | 注解变更 | 替代 getter/setter 数 |
|---|---|---|
| `AdminVectorConfigRequest` | `@Data` → `@Getter`+`@Setter`，保留 `@NoArgsConstructor`+`@AllArgsConstructor` | — |
| `AdminVectorConfigResponse` | 新增 `@Getter` | 12 getter |
| `AdminVectorIndexRebuildRequest` | 新增 `@Getter`+`@Setter` | 2 getter + 2 setter |
| `AdminVectorIndexRebuildResponse` | 新增 `@Getter` | 9 getter |
| `AdminVectorIndexStatusResponse` | 新增 `@Getter` | 18 getter |
| `AdminQueryRetrievalConfigRequest` | `@Data` → `@Getter`+`@Setter`，保留 `@NoArgsConstructor`+`@AllArgsConstructor` | — |
| `AdminQueryRetrievalConfigResponse` | 新增 `@Getter` | 14 getter |
| **合计** | | **53 getter + 2 setter** |

**未使用：** `@Data`、`@Builder`、`@AllArgsConstructor`（仅保留已有的）

---

## 4. 风险标注汇总

| 类 | 字段 | 风险类型 | 标注方式 |
|---|---|---|---|
| `AdminVectorConfigRequest/Response` | `vectorEnabled` | 检索退化 | false 时退化为纯 lexical/图谱，召回质量下降 |
| `AdminVectorConfigRequest` | `embeddingModelProfileId` | 模型切换 | 切换→维度变化→索引失效→需重建 |
| `AdminVectorIndexRebuildRequest/Response` | `truncateFirst` | 索引空窗 | true 时清空所有索引→线上暂时无向量通道 |
| `AdminVectorIndexStatusResponse` | `indexingAvailable` | 四级检查 | false 时重建/增量等写操作被拒绝 |
| `AdminVectorIndexStatusResponse` | `dimensionsMatch` | 维度不匹配 | false=向量检索可能异常；null=无法判断 |
| `AdminVectorIndexStatusResponse` | `annIndexReady` | 性能退化 | false=全表扫描，延迟飙升 |
| `AdminQueryRetrievalConfigRequest/Response` | `parallelEnabled` | 资源压力 | true=数据库连接池压力翻倍 |
| `AdminQueryRetrievalConfigRequest/Response` | `rewriteEnabled` | 语义偏移 | LLM 改写后召回结果与原始 query 可能不一致 |
| `AdminQueryRetrievalConfigRequest` | `rrfK` | 排序质量 | 过小=排名断层，过大=排趋同 |

**未修改任何业务行为。** 验证逻辑、映射逻辑、默认值均保持原样。

---

## 5. 测试与 Redline

### 编译
```
mvn compile: BUILD SUCCESS (907 source files)
mvn test-compile: BUILD SUCCESS (239 test files)
```

### 定向测试
```
mvn test -Dtest="AdminVectorConfigControllerTests,AdminVectorIndexControllerTests,AdminQueryRetrievalConfigControllerTests"
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Redline
```
bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

### 全量测试
全量 `mvn test` 因预存的 `ClassNotFoundException: com.xbk.lattice.compiler.service.FactCardReviewerTests` 失败——该测试类在 `src/test` 中不存在，与本次 7 个 api/admin DTO 改动无关。定向测试已充分验证。

### 自查
- `dimensionsMatch`（Boolean 包装类型）→ Lombok `@Getter` 生成 `getDimensionsMatch()`，行为不变 ✓
- `@Data` 已从 2 个 Request 类移除 ✓
- 无字段翻译式空泛注释 ✓
- 未修改 B6 外任何文件 ✓
- `indexedModelNames` 无防御性拷贝改造 ✓

---

## 6. B0-B6 累计统计

| 批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter |
|---|---|---|---|---|
| B0-B5b | api/query + compiler + admin (source/credential/vault/repo/lifecycle) | 39 | 181 | 142 |
| B6 | admin (vector/retrieval config) | 7 | 72 | 53 |
| **合计** | | **46** | **253** | **195** |

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 7 个目标文件 | 通过 |
| 2 个 @Data 降级为 @Getter/@Setter | 通过 |
| AdminVectorIndexRebuildRequest 补 @Getter/@Setter | 通过 |
| 4 个 Response 类级 @Getter + 删除 53 手写 getter | 通过 |
| dimensionsMatch（Boolean）Lombok 生成 getDimensionsMatch() 行为不变 | 通过 |
| 保留全部构造器签名和逻辑 | 通过 |
| 未使用 @Data / @Builder | 通过 |
| 未修改 controller/service/domain/infra/config/test | 通过 |
| 未调整向量参数/检索权重/RRF/默认值 | 通过 |
| 未混入 B7/B8/B9/B10 类 | 通过 |
| indexedModelNames 未做防御性拷贝改造 | 通过 |
| 未 stage/commit/push | 通过 |

---

## 8. 残留风险

无代码层风险。以下为标注性关注点（不在本轮修改范围）：

- **全量测试不可用**：`FactCardReviewerTests` 类缺失导致 `mvn test` 整体失败。建议在独立任务中修复或移除该测试引用，但不在本轮范围。
- **索引空窗期**：`truncateFirst` 已标注空窗期风险，但未改变实际重建行为。如果服务端未做优雅降级，空窗期仍会影响线上检索。
- **RRF 权重边界**：11 个权重值已有注释说明行为，但后台保存时的校验逻辑未变。用户可传入任意值（包括负数），服务端应做范围校验。
- **dimensionsConsistent 语义宽度**：其判断逻辑比 dimensionsMatch 更宽泛（允许可接受偏差），具体边界在 service 层实现，未在本轮审核。
