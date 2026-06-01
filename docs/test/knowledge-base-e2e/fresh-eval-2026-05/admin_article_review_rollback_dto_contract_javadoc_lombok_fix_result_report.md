# B8a2: api/admin Article 审查、回滚、Correction、Snapshot DTO 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B8a2（B8 第 2 子批次，7/18 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminArticleCorrectionRequest.java` | Request | `@Getter/@Setter`，删除 1 getter+1 setter，1 字段 Javadoc |
| `AdminArticleReviewRequest.java` | Request | `@Getter/@Setter`，删除 5 getter+5 setter，禁止 `@Data`，5 字段 Javadoc |
| `AdminArticleReviewResponse.java` | Response | 类级 `@Getter`，删除 8 getter，8 字段 Javadoc |
| `AdminArticleReviewAuditResponse.java` | Response | 类级 `@Getter`，删除 11 getter，禁止 `@Data`，11 字段 Javadoc |
| `AdminArticleReviewAuditListResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminArticleRollbackRequest.java` | Request | `@Getter/@Setter`，`articleId` `@Getter(AccessLevel.NONE)`，保留手写 `getArticleId()` fallback，删除 3 getter+4 setter，4 字段 Javadoc |
| `AdminArticleSnapshotListResponse.java` | Response | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc，`items` 分层问题标注 |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B8 状态更新 |

---

## 2. 关键阻断问题处置

### AdminArticleRollbackRequest.getArticleId() 计算 getter

```java
public String getArticleId() {
    if (articleId != null && !articleId.isBlank()) {
        return articleId;
    }
    return conceptId;  // fallback
}
```

**处置**：`articleId` 字段标注 `@Getter(AccessLevel.NONE)`，保留手写 getter。其余 3 个字段的 getter/setter 由 Lombok 生成。

**自查确认**：`getArticleId()` 保留在第 59 行，fallback 逻辑不变。`@Getter(AccessLevel.NONE)` 在第 25 行。

---

## 3. 各类详细变更

### 3.1 AdminArticleCorrectionRequest（1 字段）

| 字段 | 注释要点 |
|---|---|
| `correctionSummary` | 纠错摘要文本，描述事实错误/表述问题及修正建议，可能被持久化到审计记录 |

### 3.2 AdminArticleReviewRequest（5 字段，禁止 @Data）

| 字段 | 注释要点 |
|---|---|
| `sourceId` | 资料源主键，可选 |
| `reviewedBy` | 复核人标识，审计字段，禁止 toString() |
| `comment` | 复核意见，request-changes 时应填写，禁止 toString() |
| `expectedReviewStatus` | 乐观锁期望状态，与实际不一致时操作被拒绝 |
| `correctionSummary` | 修正建议摘要，request-changes 时说明需修正内容 |

### 3.3 AdminArticleReviewResponse（8 字段）

| 字段 | 注释要点 |
|---|---|
| `previousReviewStatus` / `reviewStatus` | 对比可知本次状态流转路径 |
| `reviewedBy` / `reviewedAt` | 操作人和时间 |
| `auditId` | 审计记录主键，可查询完整审计历史 |

### 3.4 AdminArticleReviewAuditResponse（11 字段，禁止 @Data）

审计字段专项标注：
- `comment`：含人工主观评价，禁止 toString()
- `reviewedBy`：用于审计追溯和责任认定，禁止 toString()
- `metadataJson`：可能含附加信息，可能较大，禁止 toString()

### 3.5 AdminArticleReviewAuditListResponse（2 字段）

| 字段 | 注释要点 |
|---|---|
| `count` | 当前返回审计记录数 |
| `items` | 审计记录列表，按复核时间倒序 |

### 3.6 AdminArticleRollbackRequest（4 字段，含 1 个 AccessLevel.NONE）

| 字段 | 注释要点 |
|---|---|
| `articleId` | **AccessLevel.NONE**，getArticleId() fallback→conceptId |
| `conceptId` | articleId 为空时的回滚目标标识 |
| `snapshotId` | 目标快照主键，错误 ID 导致不可逆回滚，服务端应校验归属 |

### 3.7 AdminArticleSnapshotListResponse（3 字段）

| 字段 | 注释要点 |
|---|---|
| `items` | **已知分层问题**：直接暴露 `ArticleSnapshotRecord`（persistence 层类型），后续应引入专用 Snapshot DTO |

---

## 4. Lombok 统计

| 类 | 注解 | 替代 |
|---|---|---|
| `AdminArticleCorrectionRequest` | `@Getter/@Setter` | 1 getter + 1 setter |
| `AdminArticleReviewRequest` | `@Getter/@Setter` | 5 getter + 5 setter |
| `AdminArticleReviewResponse` | `@Getter` | 8 getter |
| `AdminArticleReviewAuditResponse` | `@Getter` | 11 getter |
| `AdminArticleReviewAuditListResponse` | `@Getter` | 2 getter |
| `AdminArticleRollbackRequest` | `@Getter/@Setter`（articleId NONE） | 3 getter + 4 setter |
| `AdminArticleSnapshotListResponse` | `@Getter` | 3 getter |
| **合计** | | **33 getter + 10 setter** |

**B8a1+B8a2 累计**：93 getter + 12 setter 已删除。

---

## 5. 测试与验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: (clean)
mvn test -Dtest="AdminArticleQueryServiceTests": 3/0/0/0
```

`AdminArticleManualReviewServiceTests` 不存在于 test 目录。已跑可用的 article 服务层测试。无 B8a2 接口层定向测试。

getArticleId() 保留确认：`rg -n getArticleId` → 第 59 行，含 `articleId != null && !articleId.isBlank()` fallback。

---

## 6. B8 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| B8a1 | 已完成 | 7 |
| **B8a2** | **已完成** | **7** |
| B8b | 待开始 | 4 |

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 7 个目标文件 | 通过 |
| AdminArticleRollbackRequest.getArticleId() fallback 保留 | 通过（已自查） |
| AdminArticleReviewRequest 禁止 @Data | 通过 |
| AdminArticleReviewAuditResponse 禁止 @Data | 通过 |
| SnapshotListResponse.items 类型未改 | 通过 |
| 构造器签名/逻辑未改 | 通过 |
| 未混入 B8a1/B8b/B9/B10 | 通过 |
| 未 stage/commit/push | 通过 |
