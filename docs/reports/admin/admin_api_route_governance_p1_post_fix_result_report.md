# Admin API 路由治理 P1 POST 一致性补修报告

## 背景

P0 补修已给 `AdminCompileReviewQueueController` 和 `AdminQueryFeedbackController` 的 GET 路由添加了 `:\d+` 约束。同一控制器中仍有 4 个 POST 路由使用 Long path variable 但未加数字约束。虽然 POST 路由被前端轮询误触发的概率极低，但为保持路由治理一致性，本轮补齐。

## 修改清单（P1 补修）

| # | 文件 | 路由 | 修改 |
|---|---|---|---|
| 1 | `AdminCompileReviewQueueController.java` | `POST /{id}/approve` | `{id}` → `{id:\d+}` |
| 2 | `AdminCompileReviewQueueController.java` | `POST /{id}/reject` | `{id}` → `{id:\d+}` |
| 3 | `AdminQueryFeedbackController.java` | `POST /{feedbackId}/resolve` | `{feedbackId}` → `{feedbackId:\d+}` |
| 4 | `AdminQueryFeedbackController.java` | `POST /{feedbackId}/dismiss` | `{feedbackId}` → `{feedbackId:\d+}` |

共修改 **2 个控制器，4 个 POST 路由**。

## 控制器路由完整性

修复后，两个控制器的所有 Long path variable 路由均已补齐数字约束：

**AdminCompileReviewQueueController** (`/api/v1/admin/compile/review-queue`):
- `GET /{id:\d+}` — P0 已修复
- `POST /{id:\d+}/approve` — 本轮修复
- `POST /{id:\d+}/reject` — 本轮修复

**AdminQueryFeedbackController** (`/api/v1/admin/query-feedback`):
- `GET /{feedbackId:\d+}` — P0 已修复
- `POST /{feedbackId:\d+}/resolve` — 本轮修复
- `POST /{feedbackId:\d+}/dismiss` — 本轮修复

## 未扩大修改范围说明

- 仅修改 `AdminCompileReviewQueueController.java` 和 `AdminQueryFeedbackController.java`
- 仅修改指定的 4 个 POST 路由注解
- 未修改业务逻辑、未修改前端、未修改测试、未修改配置

## 编译验证

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -DskipTests package
# exit code: 0，编译通过
```

## 受保护路径验证

添加 `:\d+` 约束后，非数字 POST 路径不再触发 NumberFormatException：

| 路径 | 之前 | 之后 |
|---|---|---|
| `POST /api/v1/admin/compile/review-queue/runs/approve` | 匹配 `/{id}` → NumberFormatException | 不匹配 → 404 |
| `POST /api/v1/admin/compile/review-queue/runs/reject` | 匹配 `/{id}` → NumberFormatException | 不匹配 → 404 |
| `POST /api/v1/admin/query-feedback/runs/resolve` | 匹配 `/{feedbackId}` → NumberFormatException | 不匹配 → 404 |
| `POST /api/v1/admin/query-feedback/runs/dismiss` | 匹配 `/{feedbackId}` → NumberFormatException | 不匹配 → 404 |

正常数字路径（如 `POST /api/v1/admin/compile/review-queue/1/approve`）不受影响。

## 后续治理建议

### P2 — String/UUID 路径变量的静态段冲突风险

当前 admin API 路由中，Long 数字 ID 的 P0/P1 治理已全部完成。其余路径变量为 String/UUID（如 `jobId`、`cardId`），不会被 `Long.parseLong` 触发，但理论上仍可能与静态路由段冲突。建议：
- 排查 `src/main/java/com/xbk/lattice/api/` 下非 admin 包中的路由
- 评估是否需要给 String/UUID 路径变量添加长度/格式约束
