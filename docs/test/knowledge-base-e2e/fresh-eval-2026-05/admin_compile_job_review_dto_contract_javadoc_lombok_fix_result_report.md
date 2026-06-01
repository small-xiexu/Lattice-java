# api/admin Compile Job / Review DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-06-01
改造人：agentA（代码执行 Agent）
批次：B7

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminCompileJobRequest.java` | 可变 Request | `@Getter/@Setter`（async 字段 `AccessLevel.NONE` 排除），保留手写 `isAsync()`+`setAsync()`，删除 4 getter+4 setter，5 字段 Javadoc |
| `AdminCompileReviewConfigRequest.java` | 可变 Request | `@Data`→`@Getter/@Setter`，保留 `@NoArgsConstructor`+`@AllArgsConstructor`，5 字段 Javadoc |
| `AdminCompileReviewQueueActionRequest.java` | 可变 Request | 新增 `@Getter/@Setter`，删除 3 getter+3 setter，3 字段 Javadoc |
| `AdminCompileJobResponse.java` | 不可变 Response | 类级 `@Getter`，删除 23 getter，23 字段 Javadoc，保留构造器 |
| `AdminCompileJobListResponse.java` | 不可变 Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc，保留构造器 |
| `AdminCompileReviewConfigResponse.java` | 不可变 Response | 类级 `@Getter`，删除 9 getter（原无 Javadoc），9 字段 Javadoc，保留构造器 |
| `AdminCompileReviewQueueActionResponse.java` | 不可变 Response | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc，保留构造器 |
| `AdminCompileReviewQueueItemResponse.java` | 不可变 Response | 类级 `@Getter`，删除 22 getter，22 字段 Javadoc，保留构造器 |
| `AdminCompileReviewQueueListResponse.java` | 不可变 Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc，保留构造器 |
| `AdminCompileReviewSummaryResponse.java` | 不可变 Response | 类级 `@Getter`，删除 15 getter，15 字段 Javadoc，保留构造器 |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B7 状态回写 + "当前下一步" → B8 |

---

## 2. 各文件详细变更

### 2.1 AdminCompileJobRequest（5 字段，@Getter/@Setter + async 排除）

**特殊处理**：`async` 字段标注 `@Getter(AccessLevel.NONE)` + `@Setter(AccessLevel.NONE)`，保留手写 `isAsync()` 和 `setAsync()`。

`isAsync()` 计算逻辑（不可被 Lombok 替代）：
```java
public boolean isAsync() {
    return async == null || async.booleanValue();  // null-coalescing
}
```

| 字段 | 注释要点 |
|---|---|
| `sourceDir` | 源目录路径。为空时编译无输入源。服务端应做规范化和存在性校验。 |
| `incremental` | 是否增量编译。true=仅处理变更文件，快但可能遗漏级联影响；false=全量重编。 |
| `async` | 是否异步执行，默认 true。null 时 isAsync() 按 true 处理。Lombok 已排除此字段。 |
| `orchestrationMode` | 编排模式。影响 parse→review→fix→persist 步骤顺序和并发度。为空时使用默认编排。 |
| `reviewMode` | 审查模式。none=跳过审查，full=完整审查+自动修复。为空时使用默认。 |

### 2.2 AdminCompileReviewConfigRequest（5 字段，@Data→@Getter/@Setter）

降级理由：`operator` 审计字段不应参与 `toString()`。

| 字段 | 注释要点 |
|---|---|
| `autoFixEnabled` | 自动修复总开关。关闭后所有问题直接进入人工复核，review queue 可能快速积压。 |
| `maxFixRounds` | 自动修复最大轮次。过小修复不充分，过大 LLM 成本激增。null 时使用默认值。 |
| `allowPersistNeedsHumanReview` | 是否允许 needs_human_review 文章落库。false 时阻止写入，编译产出可能为零。 |
| `humanReviewSeverityThreshold` | 人工复核严重度阈值。>=此阈值的审查问题触发人工复核。必须非空。 |
| `operator` | 配置操作人标识，用于审计日志。 |

### 2.3 AdminCompileReviewQueueActionRequest（3 字段，新增 @Getter/@Setter）

| 字段 | 注释要点 |
|---|---|
| `reviewedBy` | 人工复核人标识。用于审计追踪。 |
| `comment` | 人工复核意见文本。驳回时建议填写原因。含人工主观评价，禁止 toString()。 |
| `expectedReviewStatus` | 期望的当前队列状态（乐观锁）。与实际不匹配时操作被拒绝。 |

### 2.4 AdminCompileJobResponse（23 字段，类级 @Getter）

按语义分组的高风险标注：
- **derivedStatus**：由 CompileJobDerivedStatusResolver 计算，前端应优先使用而非原始 status
- **errorMessage**：可能含异常栈，仅管理侧排查用，不应展示给终端用户
- **lastHeartbeatAt / runningExpiresAt**：租约过期后其他 worker 可抢占

### 2.5 AdminCompileReviewConfigResponse（9 字段，类级 @Getter）

原 9 个 getter 均无 Javadoc，本轮迁移到字段级。`allowPersistNeedsHumanReview=false` 时标注前端应展示阻止提示。

### 2.6 AdminCompileReviewQueueItemResponse（22 字段，类级 @Getter）

大文本/审计风险字段专项标注：
- **content**：可能为长文本，仅管理侧预览，禁止 toString()
- **metadataJson / reviewIssuesJson**：JSON 字符串可能极大，影响序列化性能
- **reviewedBy / reviewComment**：审计字段，禁止 toString()

### 2.7 AdminCompileReviewSummaryResponse（15 字段，类级 @Getter）

4 个 Integer 包装类型（acceptedCount/pendingReviewCount/needsHumanReviewCount/fixAttemptCount）的 Lombok getter 行为与手写一致。`needsHumanReviewCount > 0` 时标注前端应展示醒目提示。

---

## 3. Lombok 使用统计

| 类 | 注解变更 | 替代 getter/setter 数 |
|---|---|---|
| `AdminCompileJobRequest` | 新增 `@Getter/@Setter`（async 排除） | 4 getter + 4 setter |
| `AdminCompileReviewConfigRequest` | `@Data` → `@Getter/@Setter` | — |
| `AdminCompileReviewQueueActionRequest` | 新增 `@Getter/@Setter` | 3 getter + 3 setter |
| `AdminCompileJobResponse` | 新增 `@Getter` | 23 getter |
| `AdminCompileJobListResponse` | 新增 `@Getter` | 2 getter |
| `AdminCompileReviewConfigResponse` | 新增 `@Getter` | 9 getter |
| `AdminCompileReviewQueueActionResponse` | 新增 `@Getter` | 3 getter |
| `AdminCompileReviewQueueItemResponse` | 新增 `@Getter` | 22 getter |
| `AdminCompileReviewQueueListResponse` | 新增 `@Getter` | 2 getter |
| `AdminCompileReviewSummaryResponse` | 新增 `@Getter` | 15 getter |
| **合计** | | **80 getter + 7 setter** |

**未使用：** `@Data`、`@Builder`、`@AllArgsConstructor`（仅保留 AdminCompileReviewConfigRequest 已有的）

---

## 4. 风险标注汇总

| 类 | 字段 | 风险类型 | 标注方式 |
|---|---|---|---|
| `AdminCompileJobRequest` | `sourceDir` | 路径遍历 | 服务端应做规范化和存在性校验 |
| `AdminCompileJobRequest` | `async` | 同步阻塞 | false 时同步等待→HTTP 超时风险 |
| `AdminCompileJobRequest` | `reviewMode` | 质量保障 | none=跳过审查，无质量保障 |
| `AdminCompileReviewConfigRequest` | `autoFixEnabled` | 队列积压 | false 时所有问题进入人工复核 |
| `AdminCompileReviewConfigRequest` | `maxFixRounds` | LLM 成本 | 过大→修复死循环，成本激增 |
| `AdminCompileReviewConfigRequest` | `allowPersistNeedsHumanReview` | 编译产出 | false 时阻止未审核文章落库 |
| `AdminCompileReviewQueueActionRequest` | `expectedReviewStatus` | 并发冲突 | 乐观锁不匹配→操作被拒绝 |
| `AdminCompileJobResponse` | `errorMessage` | 信息泄露 | 可能含异常栈，禁止展示给终端用户 |
| `AdminCompileReviewQueueItemResponse` | `content` | toString 风险 | 长文本，禁止 @Data |
| `AdminCompileReviewQueueItemResponse` | `reviewIssuesJson` / `metadataJson` | 响应体积 | JSON 极大，影响序列化性能 |
| `AdminCompileReviewSummaryResponse` | `needsHumanReviewCount` | 前端告警 | >0 时前端应展示醒目提示 |

**未修改任何业务行为。** compile job 编排、review 状态机、auto-fix 流程均保持原样。

---

## 5. 测试与 Redline

### 编译
```
mvn compile: BUILD SUCCESS (907 source files)
```

### 定向测试
```
mvn test -Dtest="AdminCompileReviewConfigControllerTests,AdminCompileReviewQueueControllerTests,AdminCompileJobControllerTests"
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Redline
```
bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

### 自查
- `AdminCompileJobRequest.isAsync()` 保留在第 72 行，null-coalescing 逻辑不变 ✓
- `@Getter(AccessLevel.NONE)` + `@Setter(AccessLevel.NONE)` 标注在 async 字段第 42-43 行 ✓
- `@Data` 已从 `AdminCompileReviewConfigRequest` 移除 ✓
- 无字段翻译式空泛注释 ✓
- 未修改 B7 外任何文件 ✓
- 大文本字段已标注禁止 `@Data`（content/metadataJson/reviewIssuesJson/errorMessage） ✓

---

## 6. B0-B7 累计统计

| 批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter |
|---|---|---|---|---|
| B0-B6 | api/query + compiler + admin (source/credential/vault/repo/lifecycle + vector/retrieval config) | 46 | 253 | 195 |
| B7 | admin (compile job/review) | 10 | 89 | 80 |
| **合计** | | **56** | **342** | **275** |

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 10 个目标文件 | 通过 |
| 1 个 @Data 降级为 @Getter/@Setter（AdminCompileReviewConfigRequest） | 通过 |
| AdminCompileJobRequest async 字段 Lombok 排除 + isAsync() 保留 | 通过（已自查确认） |
| AdminCompileReviewQueueActionRequest @Getter/@Setter + 删除手写 getter/setter | 通过 |
| 7 个 Response 类级 @Getter + 删除 80 手写 getter | 通过 |
| 保留全部构造器签名和逻辑 | 通过 |
| 未使用 @Data / @Builder | 通过 |
| 未修改 controller/service/domain/infra/config/persistence/test | 通过 |
| 未修改 compile job 编排/review 状态机/auto-fix 流程 | 通过 |
| 未修改字段类型/名称/默认值/校验逻辑 | 通过 |
| 未混入 B8/B9/B10 或非 api/admin DTO | 通过 |
| 未 stage/commit/push | 通过 |

---

## 8. 残留风险

无代码层风险。以下为标注性关注点（不在本轮修改范围）：

- **源目录路径**：`sourceDir` 已标注"服务端应做规范化"，但校验逻辑未变。与 B5b 的 `vaultDir` 同类风险。
- **乐观锁并发**：`expectedReviewStatus` 已标注乐观锁语义，但 controller 中的并发冲突处理逻辑未变。
- **toString 泄露**：`AdminCompileReviewConfigRequest` 的 `@Data` 已降级，但若未来有人重新加回 `@Data`，`operator` 会参与 `toString()`。类级 Javadoc 已有标注。
- **AdminCompileReviewConfigResponse 原 getter 无 Javadoc**：已通过字段级 Javadoc 覆盖，问题解决。
