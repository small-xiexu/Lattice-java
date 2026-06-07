# INTERNAL_MIRROR Controller 端点补齐修复结果报告

时间：2026-06-07
执行人：agentA（代码执行 Agent）
Gate 依据：`internal_code_mirror_source_runtime_gate_report.md`（agentD，结论 BLOCKED）

---

## 1. 修复前问题

agentD runtime gate 验证发现两项 Controller 层遗漏：

| 问题 | 影响 |
|------|------|
| `ALLOWED_SOURCE_TYPES` 仅含 `UPLOAD`/`GIT`，缺少 `INTERNAL_MIRROR` | 创建请求被白名单校验拒绝 |
| `POST /api/v1/admin/sources/internal-mirror` 端点不存在 | 返回 405 Method Not Allowed |

同时 `SourceSyncWorkflowService.buildConfigJson()` 也缺少 INTERNAL_MIRROR 分支，创建时会抛 `unsupported source type`。

后端服务代码（`SourceMaterializationService`、`SourceAdminProperties`、`AdminSourceCreateRequest`、`lattice-source.yml`）已就绪，仅网关层缺失。

## 2. 修改文件

### 2.1 `src/main/java/com/xbk/lattice/api/admin/AdminSourceController.java`

**变更 1**：`ALLOWED_SOURCE_TYPES` 新增 `INTERNAL_MIRROR`

```java
// 修复前
private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("UPLOAD", "GIT");

// 修复后
private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("UPLOAD", "GIT", "INTERNAL_MIRROR");
```

**变更 2**：新增 `POST /api/v1/admin/sources/internal-mirror` 端点

```java
@PostMapping("/internal-mirror")
public AdminKnowledgeSourceDetailResponse createInternalMirrorSource(
        @RequestBody AdminSourceCreateRequest request) {
    return toDetailResponse(sourceSyncWorkflowService.createInternalMirrorSource(request));
}
```

参照现有 `POST /git` 端点风格，调用 `SourceSyncWorkflowService.createInternalMirrorSource(request)`。

**变更 3**：响应类注释更新

- `AdminKnowledgeSourceSummaryResponse.sourceType` 注释：`UPLOAD / GIT` → `UPLOAD / GIT / INTERNAL_MIRROR`
- `AdminKnowledgeSourceDetailResponse.sourceType` 注释：同上

### 2.2 `src/main/java/com/xbk/lattice/source/service/SourceSyncWorkflowService.java`

**变更 4**：`buildConfigJson()` 新增 INTERNAL_MIRROR 分支

```java
else if ("INTERNAL_MIRROR".equals(sourceType)) {
    configNode.put("mirrorRootRef", requireText(request.getMirrorRootRef(), "mirrorRootRef"));
    configNode.put("projectPath", requireText(request.getProjectPath(), "projectPath"));
}
```

此前只有 GIT 分支和一个兜底 `throw`，创建 INTERNAL_MIRROR 时会直接失败。

## 3. 修改面分析

| 修改 | 类型 | 风险 |
|------|------|------|
| `ALLOWED_SOURCE_TYPES` 加一项 | 常量扩展 | 无——仅影响白名单校验 |
| 新增 `@PostMapping` 端点 | 新增 API | 无——独立路由，不影响现有端点 |
| `buildConfigJson` 加 `else if` 分支 | 分支扩展 | 无——不影响 GIT 和 fallback throw 路径 |
| 响应类注释更新 | 文档 | 无 |

全部变更为网关层补齐，不改变任何已有行为。

## 4. redline 结果

| 指标 | 值 |
|------|-----|
| BLOCKER | 0 |
| 高风险 | 0 |
| 结论 | PASS |

## 5. mvn test 结果

| 指标 | 值 |
|------|-----|
| 总数 | 1018 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 结论 | BUILD SUCCESS |

## 6. 端点可用性确认

编译通过，`/api/v1/admin/sources/internal-mirror` 路由已注册（`@PostMapping` 注解已添加），不再返回 405。

## 7. agentD 重跑 runtime gate 建议

gate 报告中所有 BLOCKED 验证项现在应可执行：

| 验证项 | 对应端点 |
|--------|---------|
| 创建 INTERNAL_MIRROR source | `POST /api/v1/admin/sources/internal-mirror` |
| validate | `POST /api/v1/admin/sources/{id}/validate` |
| sync / compile | `POST /api/v1/admin/sources/{id}/sync` |
| 过滤规则验证 | sync 后 `GET /api/v1/admin/sources/{id}/files` |
| 路径安全验证 | 创建时传入非法 `mirrorRootRef`/`projectPath` |
| manifest 跳过 | 两次 sync 无变化 → `SKIPPED_NO_CHANGE` |
| 修改触发重编译 | 修改源文件后 sync |

建议 agentD 从 gate 报告的环境状态直接继续（镜像根配置与合成项目 fixture 已就绪），重跑 4.1-4.2 端点存在性检查后，按 gate 报告第 6 节清单逐项验证。

## 8. 未提交文件提醒

本轮修改未提交 commit，包括：

- `src/main/java/com/xbk/lattice/api/admin/AdminSourceController.java`
- `src/main/java/com/xbk/lattice/source/service/SourceSyncWorkflowService.java`
- `docs/test/knowledge-base-e2e/internal_code_mirror_source_controller_endpoint_fix_result_report.md`

## 9. 明确声明

- [x] 未修改 `src/main/java/com/xbk/lattice/query/**`
- [x] 未修改 `src/main/java/com/xbk/lattice/compiler/**`
- [x] 未修改 `src/main/java/com/xbk/lattice/documentparse/**`
- [x] 未修改 `scripts/**` / `config/**` / `AGENTS.md`
- [x] 未修改 redline allowlist
- [x] 未修改 hidden eval
- [x] 未恢复旧 `SERVER_DIR`
- [x] 未写任何项目名、业务名、题集名特判
- [x] 未提交 commit
