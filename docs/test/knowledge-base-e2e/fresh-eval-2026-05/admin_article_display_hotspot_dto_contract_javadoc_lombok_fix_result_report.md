# B8a1: api/admin Article 展示、热点、Usage DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-06-01
改造人：agentA（代码执行 Agent）
批次：B8a1（B8 第 1 子批次，7/18 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminArticleDetailResponse.java` | 不可变 Response | 类级 `@Getter`，hotspot+requiresResultVerification `AccessLevel.NONE` 排除，保留手写 `getIsHotspot()`+`getRequiresResultVerification()`，删除 22 getter，24 字段 Javadoc |
| `AdminArticleSummaryResponse.java` | 不可变 Response | 类级 `@Getter`，hotspot+requiresResultVerification `AccessLevel.NONE` 排除，保留手写 `getIsHotspot()`+`getRequiresResultVerification()`，删除 16 getter，18 字段 Javadoc |
| `AdminArticleTitleProfile.java` | 嵌套 DTO | 类级 `@Getter`，删除 4 getter（原无 Javadoc），4 字段 Javadoc |
| `AdminArticleListResponse.java` | 不可变 Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminArticleUsageStatsResponse.java` | 不可变 Response | 类级 `@Getter`，删除 9 getter，保留 `List.copyOf` 防御性拷贝，9 字段 Javadoc |
| `AdminArticleHotspotRefreshRequest.java` | 可变 Request | `@Getter/@Setter`，删除 2 getter+2 setter，保留 Jakarta `@Min/@Max`，2 字段 Javadoc |
| `AdminArticleHotspotRefreshResponse.java` | 不可变 Response | 类级 `@Getter`，删除 5 getter，保留 `List.copyOf` 防御性拷贝，5 字段 Javadoc |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B8 状态更新为"B8a1 已完成，待 B8a2/B8b" |

---

## 2. 关键阻断问题处置

### Boolean getter 命名不一致

Lombok 对 `boolean hotspot` 默认生成 `isHotspot()`，但当前 JSON 序列化依赖 `getIsHotspot()` → 属性名 `"isHotspot"`。若替换为 Lombok 版本，JSON 属性名会从 `"isHotspot"` 变为 `"hotspot"`，破坏前端契约。

**处置**：`hotspot` 和 `requiresResultVerification` 字段标注 `@Getter(AccessLevel.NONE)`，保留手写 getter。

| 类 | 字段 | 排除注解 | 保留方法 | JSON 属性名 |
|---|---|---|---|---|
| `AdminArticleDetailResponse` | `hotspot` | `@Getter(AccessLevel.NONE)` | `getIsHotspot()` | `"isHotspot"` ✓ |
| `AdminArticleDetailResponse` | `requiresResultVerification` | `@Getter(AccessLevel.NONE)` | `getRequiresResultVerification()` | `"requiresResultVerification"` ✓ |
| `AdminArticleSummaryResponse` | `hotspot` | `@Getter(AccessLevel.NONE)` | `getIsHotspot()` | `"isHotspot"` ✓ |
| `AdminArticleSummaryResponse` | `requiresResultVerification` | `@Getter(AccessLevel.NONE)` | `getRequiresResultVerification()` | `"requiresResultVerification"` ✓ |

**自查确认**：`rg -n 'getIsHotspot|getRequiresResultVerification'` 返回 12 行，4 个手写 getter 均在。

---

## 3. 各文件详细变更

### 3.1 AdminArticleDetailResponse（24 字段，含 2 个 AccessLevel.NONE）

| 字段分组 | 字段 | 注释要点 |
|---|---|---|
| 标识 | `sourceId`/`articleKey`/`conceptId` | sourceId null=多源；articleKey 跨 source 稳定；conceptId 用于去重 |
| 内容 | `title`/`content`/`summary` | content 长文本禁止 toString()；summary null=未生成 |
| 生命周期 | `lifecycle` | active/deprecated/archived，影响前端可用操作 |
| 审查 | `reviewStatus`/`riskLevel`/`riskReasons` | reviewStatus 驱动标签颜色；riskLevel 影响警示颜色 |
| 热点/抽检 | `hotspot`/`requiresResultVerification` | **AccessLevel.NONE**，手写 getter 保留 |
| 质量 | `confidence` | 编译/生成质量评估 |
| 来源 | `sourceCount`/`primarySourcePath`/`sourcePaths` | sourceCount=sourcePaths.size() |
| 关系图 | `referentialKeywords`/`dependsOn`/`related` | 文章关联关系 |
| 扩展 | `metadataJson`/`titleProfile` | metadataJson 大文本禁止 toString()；titleProfile null 时降级展示 title |

### 3.2 AdminArticleSummaryResponse（18 字段，含 2 个 AccessLevel.NONE）

与 Detail 共享字段语义一致，差异：无 `content`（列表不传输正文），新增 `primarySourceName`（首个来源文件名，仅 Summary 有）。

### 3.3 AdminArticleTitleProfile（4 字段）

| 字段 | 注释要点 |
|---|---|
| `sourceTitle` | 来源文档原始标题，null=未提取 |
| `anchorTitle` | 文档切分锚点标题，null=未切分 |
| `representativeTitle` | 代表标题，综合 sourceTitle+title 选取，用于列表展示 |
| `titleGenerationMode` | LLM_GENERATED/SOURCE_EXTRACTED/LEGACY_UNSET |

原 4 个 getter 无 Javadoc，本轮迁移到字段级。

### 3.4 AdminArticleUsageStatsResponse（9 字段，保留 List.copyOf）

构造器 `List.copyOf` 防御性拷贝未变。热度指标链路说明：
- `retrievalHitCount` → 检索曝光度
- `citationCount` → LLM 回答引用频率
- `answerFeedbackCount` → 含正负反馈
- `manualMarkCount` → 人工操作累计
- `heatScore` → 四个指标加权综合分，用于热点判定

### 3.5 AdminArticleHotspotRefreshRequest（2 字段，@Getter/@Setter + 保留 Jakarta）

Jakarta `@Min/@Max` 注解保持不变。字段级 Javadoc 补充 null 时 controller 使用默认值的行为说明。

### 3.6 AdminArticleHotspotRefreshResponse（5 字段，保留 List.copyOf）

构造器 `List.copyOf` 防御性拷贝未变。`hotspotCandidateCount >= updatedArticleCount` 的语义关系已标注。

---

## 4. Lombok 使用统计

| 类 | 注解变更 | 替代 getter/setter 数 |
|---|---|---|
| `AdminArticleDetailResponse` | 新增 `@Getter`（2 字段 NONE） | 22 getter |
| `AdminArticleSummaryResponse` | 新增 `@Getter`（2 字段 NONE） | 16 getter |
| `AdminArticleTitleProfile` | 新增 `@Getter` | 4 getter |
| `AdminArticleListResponse` | 新增 `@Getter` | 2 getter |
| `AdminArticleUsageStatsResponse` | 新增 `@Getter` | 9 getter |
| `AdminArticleHotspotRefreshRequest` | 新增 `@Getter/@Setter` | 2 getter + 2 setter |
| `AdminArticleHotspotRefreshResponse` | 新增 `@Getter` | 5 getter |
| **合计** | | **60 getter + 2 setter** |

**未使用：** `@Data`、`@Builder`、`@AllArgsConstructor`

---

## 5. 风险标注汇总

| 类 | 字段 | 风险类型 | 标注方式 |
|---|---|---|---|
| `AdminArticleDetailResponse` | `content` | toString/响应体积 | 长文本，禁止 toString() |
| `AdminArticleDetailResponse` | `metadataJson` | toString/响应体积 | 大文本，禁止 toString() |
| `AdminArticleDetail/Summary` | `hotspot` | JSON 契约 | AccessLevel.NONE，保留 getIsHotspot() |
| `AdminArticleDetail/Summary` | `requiresResultVerification` | JSON 契约 | AccessLevel.NONE，保留 getRequiresResultVerification() |
| `AdminArticleHotspotRefreshRequest` | `heatScoreThreshold` | 热点判定 | 过低→全标记，过高→无触发 |

**未修改任何业务行为。** 热点判定逻辑、生命周期流转、审查状态机均保持原样。

---

## 6. 测试与 Redline

### 编译
```
mvn compile: BUILD SUCCESS (907 source files)
```

### 定向测试
```
mvn test -Dtest="AdminArticleQueryServiceTests"
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
未发现 B8a1 接口层定向测试（`api/admin` 下无 article controller 测试类），以编译 + 服务层测试 + redline 为准。

### Redline
```
bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

### 自查
- `AdminArticleDetailResponse.getIsHotspot()` 保留在第 242 行 ✓
- `AdminArticleDetailResponse.getRequiresResultVerification()` 保留在第 254 行 ✓
- `AdminArticleSummaryResponse.getIsHotspot()` 保留在第 194 行 ✓
- `AdminArticleSummaryResponse.getRequiresResultVerification()` 保留在第 206 行 ✓
- 构造器中 `List.copyOf` 防御性拷贝未变（UsageStats 第 63 行，HotspotRefresh 第 44 行） ✓
- Jakarta `@Min/@Max` 保留 ✓
- 无字段翻译式空泛注释 ✓
- 未修改 B8a1 外任何文件 ✓

---

## 7. B0-B8a1 累计统计

| 批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter |
|---|---|---|---|---|
| B0-B7 | api/query + compiler + admin (source/credential/vault/repo/lifecycle + vector/retrieval config + compile job/review) | 56 | 342 | 275 |
| B8a1 | admin (article display/hotspot/usage) | 7 | 48 | 60 |
| **合计** | | **63** | **390** | **335** |

---

## 8. B8 剩余工作

| 子批次 | 状态 | 范围 | 类数 |
|---|---|---|---|
| **B8a1** | **已完成** | article 展示、热点、usage DTO | 7 |
| **B8a2** | 待开始 | article 审查、回滚、correction、snapshot DTO | 7 |
| **B8b** | 待开始 | fact card + quality DTO | 4 |

## 9. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 7 个 B8a1 目标文件 | 通过 |
| hotspot/requiresResultVerification AccessLevel.NONE + 手写 getter 保留 | 通过（已自查确认） |
| List.copyOf 防御性拷贝未变 | 通过 |
| Jakarta @Min/@Max 保留 | 通过 |
| 未使用 @Data | 通过 |
| 未修改 controller/service/domain/infra/config/governance/test | 通过 |
| 未修改构造器签名/逻辑 | 通过 |
| 未修改字段类型/名称 | 通过 |
| 未混入 B8a2/B8b/B9/B10 文件 | 通过 |
| 未 stage/commit/push | 通过 |
