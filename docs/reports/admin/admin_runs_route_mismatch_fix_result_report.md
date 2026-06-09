# `Compile request rejected: For input string: "runs"` 路由误匹配修复报告

## 故障现象

应用日志中每约 3 秒出现一次：
```
Compile request rejected: For input string: "runs"
```

来源：`CompileExceptionHandler.handleIllegalArgumentException` (CompileExceptionHandler.java:52-56)。

## 根因分析

Spring MVC 路径变量类型转换链路：

1. 请求 `/api/v1/admin/sources/runs`（前端轮询资料源的同步历史）
2. Spring MVC 将其匹配到 `AdminSourceController.getSource(@PathVariable Long sourceId)`（`@GetMapping("/{sourceId}")`）
3. Spring 尝试 `Long.parseLong("runs")` → `NumberFormatException`
4. `NumberFormatException` 继承自 `IllegalArgumentException`
5. `CompileExceptionHandler.handleIllegalArgumentException` 捕获并打印日志

**根本原因**：`{sourceId}` 路径变量没有数字约束，任何非数字路径段都会匹配到 `Long` 类型参数的端点，导致类型转换失败。

## 修复方案

给所有 admin 控制器中的 `Long` 路径变量添加 `:\d+` 正则约束，防止非数字值误匹配。

## 修改清单

| 文件 | 路由 | 修改 |
|---|---|---|
| `AdminSourceController.java` | `/sources` | 5 个路由的 `{sourceId}` → `{sourceId:\d+}` |
| `AdminUploadController.java` | `/admin` | 6 个路由的 `{runId}`, `{sourceId}` → 加 `:\d+` |
| `AdminProcessingTaskController.java` | `/admin` | 1 个路由的 `{sourceId}` → `{sourceId:\d+}` |
| `AdminDocumentParseConnectionController.java` | `/document-parse/connections` | 2 个路由的 `{id}` → `{id:\d+}` |
| `AdminFactCardController.java` | `/fact-cards` | 1 个路由的 `{id}` → `{id:\d+}` |
| `AdminLlmConfigController.java` | `/admin` | 6 个路由的 `{id}` → `{id:\d+}` |

共修改 **6 个控制器，21 个路由**。

## 效果

添加 `:\d+` 约束后，`/api/v1/admin/sources/runs` 不再匹配 `/{sourceId:\d+}`，不会触发 `Long` 类型转换异常。`GET /api/v1/admin/sources/runs` 的行为变为：
- 如果没有匹配的静态路由，Spring 返回 404
- 如果存在精确匹配的静态路由（`/source-runs`），正常处理

本次修复不涉及任何异常吞噬或日志级别降低，不影响 `CompileExceptionHandler` 的原有行为。

## 编译验证

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -DskipTests package
# exit code: 0，编译通过
```
