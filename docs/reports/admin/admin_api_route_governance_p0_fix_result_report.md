# Admin API 路由治理 P0 补修报告

## 背景

agentB 在 `docs/reports/admin/admin_api_route_governance_analysis_report.md` 中复核发现，此前 P0 修复（6 个控制器、21 个路由）遗漏了 3 个 Long 路径变量，仍有被非数字静态段（如 `runs`）误匹配的风险。

## 修改清单（P0 补修）

| # | 文件 | 路由 | 修改 |
|---|---|---|---|
| 1 | `AdminCompileReviewQueueController.java` | `GET /api/v1/admin/compile/review-queue/{id}` | `{id}` → `{id:\d+}` |
| 2 | `AdminQueryFeedbackController.java` | `GET /api/v1/admin/query-feedback/{feedbackId}` | `{feedbackId}` → `{feedbackId:\d+}` |
| 3 | `AdminRepoSnapshotController.java` | `GET /api/v1/admin/snapshot/repo/{snapshotId}/diff` | `{snapshotId}` → `{snapshotId:\d+}` |

共修改 **3 个控制器，3 个路由**。

## 未扩大修改范围说明

严格遵循本轮 P0 补修范围，未修改以下任何内容：

- 同一控制器中的 POST 路由 (`/{id}/approve`, `/{id}/reject`, `/{feedbackId}/resolve`, `/{feedbackId}/dismiss`)：POST 请求需要携带 JSON body，前端静态资源轮询不会触发
- 其他 src/main/java 文件
- 前端代码、测试代码、scripts

## 编译验证

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -DskipTests package
# exit code: 0，编译通过
```

## 受保护路径验证

添加 `:\d+` 约束后，以下非数字路径不再匹配 Long 路径变量：

| 路径 | 之前 | 之后 |
|---|---|---|
| `/api/v1/admin/compile/review-queue/runs` | 匹配 `/{id}` → NumberFormatException | 不匹配 `/{id:\d+}` → 404 |
| `/api/v1/admin/query-feedback/runs` | 匹配 `/{feedbackId}` → NumberFormatException | 不匹配 `/{feedbackId:\d+}` → 404 |
| `/api/v1/admin/snapshot/repo/runs/diff` | 匹配 `/{snapshotId}` → NumberFormatException | 不匹配 `/{snapshotId:\d+}` → 404 |

正常数字路径（如 `/api/v1/admin/compile/review-queue/1`）不受影响，仍正确匹配。

## 后续治理建议

### P1 — 同控制器 POST 路由（低风险，但建议补全）

| 文件 | 路由 | 原因 |
|---|---|---|
| `AdminCompileReviewQueueController.java` | `POST /{id}/approve`, `POST /{id}/reject` | 仍可被非数字路径匹配 |
| `AdminQueryFeedbackController.java` | `POST /{feedbackId}/resolve`, `POST /{feedbackId}/dismiss` | 同上 |

POST 路由需要 JSON body，被前端 `fetchJson` 轮询误触发的概率极低，但为了一致性和防御性，建议加上。

### P2 — 其他包中 String/UUID 路径变量的静态段冲突风险

不在本轮范围内，建议专项排查。
