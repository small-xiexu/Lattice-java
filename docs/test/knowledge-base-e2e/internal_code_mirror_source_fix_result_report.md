# 内部代码镜像源（INTERNAL_MIRROR）最小闭环实现结果报告

时间：2026-06-07
执行人：agentA（代码执行 Agent）
设计依据：`internal_code_mirror_source_design_report.md`（agentB）

---

## 1. 本轮目标

实现 INTERNAL_MIRROR 资料源类型的最小可用闭环：公司内部私有 Java 项目代码先同步到服务器固定镜像目录，Lattice 能安全、可追溯、可增量地扫描并导入知识库。

## 2. 实现概览

| 层 | 实现 | 状态 |
|---|---|---|
| 镜像根配置 | `SourceAdminProperties.mirrorRoots` Map 配置 | 已实现 |
| 资料源类型 | `INTERNAL_MIRROR` 加入 `ALLOWED_SOURCE_TYPES` | 已实现 |
| 资料源创建 | `POST /api/v1/admin/sources/internal-mirror` | 已实现 |
| 路径安全 | canonicalize + allowlist 校验 + 拒绝 `..`/绝对路径/越界 | 已实现 |
| 目录扫描 | 递归遍历 + 默认排除/纳入过滤 | 已实现 |
| 物化复制 | 扫描 → 过滤 → 复制到 staging | 已实现 |
| 增量跳过 | 复用 `BundleFeatureExtractor` manifest hash + `SKIPPED_NO_CHANGE` | 已实现 |
| 编译链路 | 复用 `SourceUploadService.acceptMaterializedSource()` → compile job | 已实现 |
| 后台展示 | 资料源列表/详情中可见 INTERNAL_MIRROR 类型 | 已实现 |

## 3. 修改文件清单

### 3.1 `src/main/java/com/xbk/lattice/source/config/SourceAdminProperties.java`

新增 `mirrorRoots` 字段（`Map<String, String>`）：

```java
private Map<String, String> mirrorRoots = Collections.emptyMap();
```

- key = 镜像根引用名（mirrorRootRef），API 中只能传引用名
- value = 服务器上的绝对规范路径
- 默认空映射——未配置时禁止创建 INTERNAL_MIRROR 资料源

### 3.2 `src/main/resources/config/lattice-source.yml`

新增 `mirror-roots` 配置占位：

```yaml
lattice:
  source:
    admin:
      mirror-roots: {}
```

生产环境示例：

```yaml
mirror-roots:
  prod-mirror: /data/mirrors/prod-code
  dev-mirror: /data/mirrors/dev-code
```

### 3.3 `src/main/java/com/xbk/lattice/api/admin/AdminSourceCreateRequest.java`

新增 INTERNAL_MIRROR 专用字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `mirrorRootRef` | `String` | 镜像根引用名，对应 `mirror-roots` 中的 key |
| `projectPath` | `String` | 镜像根下的相对项目路径，禁止含 `..` |

### 3.4 `src/main/java/com/xbk/lattice/api/admin/AdminSourceController.java`

- `ALLOWED_SOURCE_TYPES` 新增 `"INTERNAL_MIRROR"`
- 新增 `POST /api/v1/admin/sources/internal-mirror` 端点 → `createInternalMirrorSource()`
- 响应类注释更新：`UPLOAD / GIT / INTERNAL_MIRROR`

### 3.5 `src/main/java/com/xbk/lattice/source/service/SourceSyncWorkflowService.java`

- 新增 `createInternalMirrorSource()` 方法，调用 `createSource(request, "INTERNAL_MIRROR")`
- `buildConfigJson()` 新增 INTERNAL_MIRROR 分支：存储 `mirrorRootRef` + `projectPath`

### 3.6 `src/main/java/com/xbk/lattice/source/service/SourceMaterializationService.java`（核心）

新增 INTERNAL_MIRROR 的 `validate()` 和 `materialize()` 分支，以及以下私有方法：

| 方法 | 职责 |
|------|------|
| `validateInternalMirrorSource()` | 解析并校验 mirrorRootRef + projectPath |
| `materializeInternalMirrorSource()` | 扫描 → 过滤 → 复制 → 返回物化元数据 |
| `resolveMirrorProjectDir()` | 路径 canonicalize + 越界校验 |
| `shouldIncludeMirrorFile()` | 文件级 include/exclude 判断 |

**默认排除规则**：

| 类别 | 排除项 |
|------|--------|
| VCS | `.git`, `.svn`, `.hg` |
| Java 产物 | `target`, `build`, `out`, `.gradle` |
| Node 产物 | `node_modules`, `dist`, `coverage` |
| IDE | `.idea`, `.vscode` |
| 系统 | `.DS_Store`, `Thumbs.db`, `Desktop.ini` |
| 二进制产物 | `.class`, `.jar`, `.war`, `.ear`, `.zip`, `.tar`, `.gz`, `.7z` |
| 临时文件 | `.tmp`, `.temp`, `.swp`, `.bak`, `.log` |
| 密钥文件 | `.env*`, `.pem`, `.p12`, `.jks`, `id_rsa`, `id_dsa` |

**默认纳入规则**：

| 类别 | 纳入项 |
|------|--------|
| 源码 | `.java` |
| 配置 | `.xml`, `.yml`, `.yaml`, `.properties`, `.json` |
| 数据库 | `.sql` |
| 文档 | `.md`, `.txt` |
| 脚本 | `.sh` |
| 前端 | `.js`, `.ts`, `.vue`, `.css`, `.html` |
| 构建 | `pom.xml`, `build.gradle`, `settings.gradle`, `gradle.properties`, `.gradle` |
| 容器 | `Dockerfile`, `.dockerignore`, `.gitignore` |

### 3.7 未修改但需说明的现有代码路径

以下现有代码路径无需修改即可兼容 INTERNAL_MIRROR：

| 路径 | 说明 |
|------|------|
| `SourceUploadService.acceptMaterializedSource()` | 接收 `sourceType` 字符串参数，天然兼容 |
| `SourceUploadWorkflowSupport.buildSourceSyncActions()` | `!"UPLOAD".equalsIgnoreCase()` 对 INTERNAL_MIRROR 返回 true，RESYNC 按钮可用 |
| `SourceDecisionPolicy.filterCandidates()` | `!"UPLOAD".equals()` 跳过非 UPLOAD 源，对 INTERNAL_MIRROR 行为与 GIT 一致 |
| `BundleFeatureExtractor.extract()` | 对任意 staging 目录计算 manifest hash |
| `IngestNode` | 支持所有纳入文件类型的编译摄入 |
| `ExtractAstGraphNode` | `.java` 文件走 AST 抽取 |

## 4. 路径安全控制

在 `resolveMirrorProjectDir()` 中实现多层防护：

1. **allowlist 强制**：未配置 `mirror-roots` 时直接拒绝
2. **引用校验**：`mirrorRootRef` 必须在 allowlist 中存在
3. **canonicalize**：`mirrorRoot.toRealPath()` + `projectDir.toRealPath()` 解析软链和相对路径
4. **.. 拒绝**：`projectPath` 包含 `..` 直接抛出异常
5. **越界校验**：`projectDir.startsWith(mirrorRoot)` 确保项目目录在镜像根内
6. **目录校验**：确保 `projectDir` 是真实存在的目录

## 5. 增量策略

- 每次 `materialize()` → `acceptMaterializedSource()` 调用时，`BundleFeatureExtractor` 对整个 staging 目录计算 manifest hash
- `routeExplicitSource()` 中比较 `manifestHash` 与 `latestManifestHash`，相同则 `SKIPPED_NO_CHANGE`
- 变化则重新编译入库
- 当前为**全量比较**（所有文件 hash 变化触发全量），不做文件级差异增量

## 6. 更新机制

MVP 仅支持**手动刷新**：

- 用户通过 `POST /api/v1/admin/sources/{sourceId}/sync` 触发
- `SourceSyncWorkflowService.syncSource()` → `SourceMaterializationService.materialize()` → `SourceUploadService.acceptMaterializedSource()`
- 定时轮询和 Webhook 字段预留但未实现

## 7. 明确限制

| 限制项 | 说明 |
|--------|------|
| 删除 reconciliation | **未实现**。删除源文件后，旧 source_files/chunks/articles 不会自动清理。API/报告已明确标记此限制。 |
| 定时轮询 | 未实现。需后续基于 `refreshPolicy` 配置与调度器实现。 |
| Webhook | 未实现。 |
| 敏感内容扫描 | 仅做文件名级排除（`.env`、`.pem` 等），不做文件内容扫描。 |
| 符号链接跟随 | 默认不跟随（`Files.walkFileTree` 不跟随软链）。 |
| 扫描限额 | 无文件数/字节数/深度上限。大项目无保护。 |
| Include/exclude globs | 未实现用户自定义 glob 扩展。仅使用默认常量。 |
| 稳定标记/两次快照一致性 | 未实现。 |
| 文件级差异清单 | 未实现。manifest 变化时全量重新编译。 |

## 8. redline 结果

| 指标 | 值 |
|------|-----|
| BLOCKER | 0 |
| 高风险 | 0 |
| 结论 | PASS |

## 9. 全量 mvn test 结果

| 指标 | 值 |
|------|-----|
| 总数 | 1018 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 结论 | BUILD SUCCESS |

## 10. 验收建议

agentD 可执行以下验证（使用合成 Java 项目 fixture，不使用真实私有项目）：

### 10.1 配置准备

在 `application.yml` 或环境变量中配置镜像根：

```yaml
lattice:
  source:
    admin:
      mirror-roots:
        test-mirror: /tmp/lattice-mirror-test
```

创建测试项目：

```bash
mkdir -p /tmp/lattice-mirror-test/sample-java-project/src/main/java/com/example
echo 'package com.example; public class Hello { }' > /tmp/lattice-mirror-test/sample-java-project/src/main/java/com/example/Hello.java
mkdir -p /tmp/lattice-mirror-test/sample-java-project/target
echo 'compiled' > /tmp/lattice-mirror-test/sample-java-project/target/ignored.class
echo 'secret' > /tmp/lattice-mirror-test/sample-java-project/.env
```

### 10.2 API 验收清单

| 验收项 | API / 方法 | 预期结果 |
|--------|-----------|----------|
| 创建 INTERNAL_MIRROR 源 | `POST /api/v1/admin/sources/internal-mirror` | 201，返回 source 详情 |
| 拒绝未配置 root | `mirrorRootRef: "unknown"` | 400，提示未在 allowlist |
| 拒绝 `..` 越界 | `projectPath: "../etc"` | 400，提示路径不合法 |
| 拒绝绝对路径 | `projectPath: "/etc"` | 400（canonicalize 后不在 root 内） |
| validate | `POST /api/v1/admin/sources/{id}/validate` | 200，`valid: true` |
| sync | `POST /api/v1/admin/sources/{id}/sync` | 200，返回 run detail |
| 过滤验证 | 检查 source files | `.java` 应出现，`.class`/`.env`/`target/` 不应出现 |
| 二次 sync 无变化 | 再 sync 一次 | `SKIPPED_NO_CHANGE` |
| 修改后 sync 变化 | 修改 Hello.java 再 sync | 触发新 compile job |
| 列表/详情可见 | `GET /api/v1/admin/sources` | INTERNAL_MIRROR 类型可见 |
| UPLOAD/GIT 无回归 | 创建/同步 UPLOAD 和 GIT 源 | 无行为变化 |

### 10.3 删除未同步验证

源目录中删除文件后 sync → 旧 source_file 仍可检索（预期行为——删除 reconciliation 未实现）。

## 11. 未提交文件提醒

本轮修改未提交 commit，包括：

- `src/main/java/com/xbk/lattice/source/config/SourceAdminProperties.java`
- `src/main/resources/config/lattice-source.yml`
- `src/main/java/com/xbk/lattice/api/admin/AdminSourceCreateRequest.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminSourceController.java`
- `src/main/java/com/xbk/lattice/source/service/SourceSyncWorkflowService.java`
- `src/main/java/com/xbk/lattice/source/service/SourceMaterializationService.java`
- `docs/test/knowledge-base-e2e/internal_code_mirror_source_fix_result_report.md`（本报告）

## 12. 明确声明

- [x] 未修改 `src/main/java/com/xbk/lattice/query/**`
- [x] 未修改 `src/main/java/com/xbk/lattice/compiler/**`
- [x] 未修改 `src/main/java/com/xbk/lattice/documentparse/**`
- [x] 未修改 `scripts/**`
- [x] 未修改 `AGENTS.md` / `README.md` / `special_cases_report.md`
- [x] 未修改 redline allowlist
- [x] 未读取 hidden eval
- [x] 未恢复旧 `SERVER_DIR`
- [x] 未写任何具体项目名、业务名、文件名、题集名特判
- [x] 未修改 query/answer/rerank/fallback 主链
- [x] 未提交 commit
