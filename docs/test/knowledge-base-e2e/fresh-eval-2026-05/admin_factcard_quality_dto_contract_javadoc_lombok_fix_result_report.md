# B8b: api/admin Fact Card + Quality DTO 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B8b（B8 第 3/最后子批次，4/18 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminFactCardItemResponse.java` | Response | 类级 `@Getter`，删除 18 getter，18 字段 Javadoc（itemsJson/evidenceText 大文本标注禁止 @Data） |
| `AdminFactCardListResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminFactCardSummaryResponse.java` | Response | 类级 `@Getter`，删除 5 getter，5 字段 Javadoc（Map key 语义说明） |
| `AdminFactCardSummaryResponse.java` | Response | 保留 `Map<String, Integer>` 类型，说明 key 语义 |
| `AdminQualityResponse.java` | Response | 类级 `@Getter`，删除 2 getter（原无 Javadoc），2 字段 Javadoc（分层问题标注） |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B8 状态→已完成，"当前下一步"→B9 |

---

## 2. 各类详细变更

### 2.1 AdminFactCardItemResponse（18 字段）

| 字段分组 | 字段 | 注释要点 |
|---|---|---|
| 标识 | `id`/`cardId` | cardId 跨 source 稳定，基于内容哈希 |
| 来源 | `sourceId`/`sourceFileId`/`sourceFilePath` | 追溯 Fact Card 的来源文件 |
| 类型 | `cardType` | 枚举名（STRUCTURED_ITEM/SUMMARY），驱动前端卡片布局 |
| 形态 | `answerShape` | 影响回答呈现方式（单值/列表/表格） |
| 内容 | `title`/`claim` | 卡片标题和事实结论 |
| 证据 | `itemsJson`/`evidenceText` | **大文本字段**，禁止 toString() |
| 关联 | `sourceChunkIds`/`articleIds` | 关联的 source chunk 和 article 主键列表 |
| 质量 | `confidence`（0.0-1.0）/`reviewStatus`/`contentHash` | confidence 反映可信度；contentHash 用于检测内容变更 |

### 2.2 AdminFactCardSummaryResponse（5 字段）

Map key 语义已说明：
- `countByCardType` — key 为 FactCardType 枚举名
- `countByReviewStatus` — key 为数据库审查状态值

质量指标：
- `sourceReferenceMissingCount > 0` → 数据完整性问题
- `lowConfidenceCount > 0` → 事实准确性关注

### 2.3 AdminQualityResponse（2 字段）

| 字段 | 注释要点 |
|---|---|
| `report` | **已知分层问题**：直接暴露 `QualityMetricsReport`（governance 领域对象），后续应引入专用 DTO |
| `trend` | **已知分层问题**：直接暴露 `QualityMetricsTrend`（governance 领域对象） |

原 2 个 getter 无 Javadoc，本轮迁移到字段级并标注分层问题。

---

## 3. Lombok 统计

| 类 | 注解 | 替代 |
|---|---|---|
| `AdminFactCardItemResponse` | `@Getter` | 18 getter |
| `AdminFactCardListResponse` | `@Getter` | 2 getter |
| `AdminFactCardSummaryResponse` | `@Getter` | 5 getter |
| `AdminQualityResponse` | `@Getter` | 2 getter |
| **B8b 合计** | | **27 getter** |

**B8 总计（B8a1 + B8a2 + B8b = 18 类）**：120 getter + 12 setter 已删除，109 字段 Javadoc 已补充。

---

## 4. 测试与验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: (clean)
mvn test -Dtest="AdminFactCardControllerTests": 3/0/0/0
```

无 Quality controller 测试。B8 三子批次定向测试合计 **9/0/0/0**。

---

## 5. B8 完整汇总

| 子批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter | 删除 setter | AccessLevel.NONE |
|---|---|---|---|---|---|---|
| B8a1 | article 展示/热点/usage | 7 | 48 | 60 | 2 | 4 (hotspot×2类×2字段) |
| B8a2 | article 审查/回滚/correction/snapshot | 7 | 34 | 33 | 10 | 1 (articleId) |
| B8b | fact card + quality | 4 | 27 | 27 | 0 | 0 |
| **合计** | | **18** | **109** | **120** | **12** | **5** |

---

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 4 个 B8b 目标文件 | 通过 |
| 禁止 @Data（itemsJson/evidenceText 大文本风险） | 通过 |
| QualityResponse report/trend 分层问题标注 | 通过 |
| 构造器签名/逻辑未改 | 通过 |
| 未混入 B8a1/B8a2/B9/B10 | 通过 |
| 未 stage/commit/push | 通过 |
