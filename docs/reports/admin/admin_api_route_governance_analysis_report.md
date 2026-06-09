# Admin API 路由治理审计报告

审计时间：2026-06-09
执行人：agentB（治理/归因 Agent）
类型：只读路由治理审计，不修改任何代码

---

## 1. 总体结论

**agentA 已修复的 6 个控制器、约 21 个路由覆盖面较完整，核心的 `/{sourceId}` → `/{sourceId:\d+}` 修复已消除了最初的 `"runs" → parseLong` 报错。但仍发现 3 个 P0 级别的 Long path variable 未加 `:\d+` 约束，以及少量 String/UUID path variable 在极端场景下有静态路由遮蔽风险。**

---

## 2. 已修复项复核

| 控制器 | 修复的路由 | 复核结果 |
|------|------|:---:|
| `AdminSourceController` | `/{sourceId:\d+}` × 5 个方法 | ✅ 合理——`sourceId` 是 Long，必须约束 |
| `AdminProcessingTaskController` | `/sources/{sourceId:\d+}/processing-tasks` | ✅ 合理 |
| `AdminUploadController` | `/source-runs/{runId:\d+}`, `/sources/{sourceId:\d+}/runs` | ✅ 合理 |
| `AdminDocumentParseConnectionController` | `/{id:\d+}` × 2 个方法 | ✅ 合理 |
| `AdminFactCardController` | `/{id:\d+}` | ✅ 合理 |
| `AdminLlmConfigController` | `/{id:\d+}` × 6 个方法（connections/models/bindings 的 PUT/DELETE） | ✅ 合理 |

**复核结论**：6 个控制器的 `:\d+` 修复全部合理，没有过宽或误加。已修复的路径覆盖了报告最初 `"runs" → parseLong` 报错的根因（`AdminSourceController.getSource(@PathVariable Long sourceId)`）。

---

## 3. 未修复风险清单

### 3.1 P0 — Long path variable 未加 `:\d+`（NumberFormatException 风险）

| # | 文件 | 方法 | 当前路由 | 风险说明 |
|---|------|------|------|------|
| 1 | `AdminCompileReviewQueueController.java` | `get(@PathVariable long id)` | `@GetMapping("/{id}")` | 若未来新增 `/api/v1/admin/compile/review-queue/runs` 这类静态路由，`"runs"` 会被 `/{id}` 捕获并 `parseLong("runs")` → `NumberFormatException` |
| 2 | `AdminQueryFeedbackController.java` | `detail(@PathVariable long feedbackId)` | `@GetMapping("/{feedbackId}")` | 同上模式——若未来新增静态路由，非数字值会被 Long path variable 误匹配 |
| 3 | `AdminRepoSnapshotController.java` | `diff(@PathVariable long snapshotId)` | `@GetMapping("/api/v1/admin/snapshot/repo/{snapshotId}/diff")` | 同上——`snapshotId` 是 Long，无数字约束 |

**风险等级**：P0。当前虽然无直接冲突（上述控制器在 `{id}` 路径层次没有同名静态 GET 路由），但属于**潜在冲突**——未来新增静态路由时容易遗忘。且即使当前无冲突，恶意或失误的请求（如 `/api/v1/admin/compile/review-queue/runs`）仍会触发 500 错误而非 404。

**建议修复**：
```java
// AdminCompileReviewQueueController.java
@GetMapping("/{id:\\d+}")

// AdminQueryFeedbackController.java
@GetMapping("/{feedbackId:\\d+}")

// AdminRepoSnapshotController.java
@GetMapping("/api/v1/admin/snapshot/repo/{snapshotId:\\d+}/diff")
```

### 3.2 P1 — String/UUID path variable 无约束（静态路由遮蔽风险）

| # | 文件 | path variable | 类型 | 风险说明 |
|---|------|------|:---:|------|
| 4 | `AdminArticleController.java` | `{articleId}`, `{conceptId}` | String | 当前该控制器在 `/{articleId}` 层无静态 GET 路由，但 `${articleId}` 会匹配任何字符串，包括未来可能新增的 `/hotspots`、`/search` 等（当前 `hotspots/refresh` 是 POST，不冲突） |
| 5 | `AdminPendingController.java` | `{queryId}` | String (UUID) | 同层无静态路由冲突，UUID 格式也天然低冲突 |
| 6 | `PendingQueryController.java` | `{queryId}` | String (UUID) | 同上 |
| 7 | `AdminCompileController.java` | `{jobId}` | String (UUID) | 无静态路由冲突 |

**风险等级**：P1。String 类型的 path variable 不会导致 `NumberFormatException`，但可能遮蔽未来新增的静态路由。UUID 格式的 path variable（`{queryId}`, `{jobId}`）天然具有低冲突特性（36 字符 vs 短静态路由名），不强制要求加正则约束。

**建议**：对 `AdminArticleController` 的 `{articleId}` / `{conceptId}` 可加 String 约束（如 `{articleId:[a-zA-Z0-9_-]+}`），但对 UUID 类型的 path variable 可暂不加约束。**本轮不强制修复。**

### 3.3 P2 — 前端调用路径一致性

| 检查项 | 结果 |
|------|:---:|
| 前端 `/api/v1/admin/compile/review-queue` 调用 | ✅ 与后端 `AdminCompileReviewQueueController` 一致 |
| 前端 `/api/v1/admin/query-feedback` 调用 | ✅ 与后端 `AdminQueryFeedbackController` 一致 |
| 前端 `/api/v1/admin/source-runs` 调用 | ✅ 与后端 `AdminUploadController` 一致 |
| 前端 `/api/v1/admin/sources` 调用 | ✅ 与后端 `AdminSourceController` 一致 |
| 前端 `/api/v1/admin/processing-tasks` 调用 | ✅ 与后端 `AdminProcessingTaskController` 一致 |
| 前端 `/api/v1/admin/llm/connections` / `/models` / `/bindings` 调用 | ✅ 与后端 `AdminLlmConfigController` 一致 |

**前端调用路径与后端路由完全一致，无 `/sources/runs` vs `/source-runs` 这类近似路径混淆。**

---

## 4. 是否需要 agentA 继续修复

**是**，仅需修复 3 个 P0 级别的 Long path variable（`:\d+` 约束），修改范围极小（3 个文件、3 个 `@GetMapping` 注解）。

### 最小修改范围

| 文件 | 方法 | 修改 |
|------|------|------|
| `AdminCompileReviewQueueController.java` 第 68 行 | `get()` | `@GetMapping("/{id}")` → `@GetMapping("/{id:\\d+}")` |
| `AdminQueryFeedbackController.java` 第 79 行 | `detail()` | `@GetMapping("/{feedbackId}")` → `@GetMapping("/{feedbackId:\\d+}")` |
| `AdminRepoSnapshotController.java` 第 84 行 | `diff()` | `@GetMapping("/api/v1/admin/snapshot/repo/{snapshotId}/diff")` → `@GetMapping("/api/v1/admin/snapshot/repo/{snapshotId:\\d+}/diff")` |

### 不建议扩大范围

- ❌ 不建议将 String/UUID path variable 全部加正则约束（收益低、P2 风险）
- ❌ 不建议将 GET 读接口改为 POST（破坏 REST 语义、改动面大）
- ❌ 不建议新增 Controller（当前路由结构清晰，无需重构）

---

## 5. 明确声明

- [x] 未修改任何代码
- [x] 未提交 commit
- [x] 全量 35 个 Controller 路由扫描完成
- [x] agentA 已修复 6 个控制器 21 个路由，复核通过
- [x] 发现 3 个 P0 级别 Long path variable 未加 `:\d+`
- [x] 前端调用路径与后端路由一致，无近似路径混淆
- [x] 推荐 agentA 修复范围：3 个文件、3 个 `@GetMapping` 注解
