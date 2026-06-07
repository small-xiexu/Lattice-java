# INTERNAL_MIRROR 内部代码镜像源 — Runtime Gate 验证报告

验证时间：2026-06-07 03:40 ~ 04:00
HEAD：`00237a9`
执行人：agentD（验证 Agent）
修复报告：`internal_code_mirror_source_fix_result_report.md`（agentA）
设计报告：`internal_code_mirror_source_design_report.md`（agentB）

---

## 1. 验证范围

验证 INTERNAL_MIRROR 资料源类型的后端实现是否完整、安全、可 runtime 运行。

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| HEAD | `00237a9` |
| git diff 生产代码 | `AdminSourceCreateRequest.java`, `SourceAdminProperties.java`, `SourceMaterializationService.java`, `SourceSyncWorkflowService.java`, `lattice-source.yml` |
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0, BUILD SUCCESS** |

确认未修改 query/answer/rerank/fallback 主链，未恢复旧 SERVER_DIR。

---

## 3. Runtime 环境

| 项 | 值 |
|---|---|
| 服务端口 | 18082 |
| 镜像根配置 | `JAVA_TOOL_OPTIONS=-Dlattice.source.admin.mirror-roots.test-mirror=/tmp/lattice-internal-mirror-gate` |
| 合成项目 | `/tmp/lattice-internal-mirror-gate/sample-java-project/` |
| 合成文件 | 6 个源码文件（.java×2, .yml, .xml, pom.xml, README.md） |
| 排除文件 | `.env`, `secret.pem`, `.class×2`, `.log`, `node_modules/`, `.git/`, `target/`, `build/` |

---

## 4. API 验证

### 4.1 端点状态

| 端点 | 预期 | 实际 |
|---|---|---|
| `POST /api/v1/admin/sources/internal-mirror` | 应存在 | **不存在**（405 Method Not Allowed） |
| `ALLOWED_SOURCE_TYPES` 含 INTERNAL_MIRROR | 应含 | **不含**（仅 UPLOAD/GIT） |

### 4.2 阻塞确认

`AdminSourceController.java` 工作区无任何 diff。修复报告声称的两项 controller 变更：
1. `ALLOWED_SOURCE_TYPES` 新增 `"INTERNAL_MIRROR"` — **未实现**
2. `POST /api/v1/admin/sources/internal-mirror` 端点 — **未实现**

后端服务代码（`SourceMaterializationService`、`SourceSyncWorkflowService`、`SourceAdminProperties`、`AdminSourceCreateRequest`、`lattice-source.yml`）已就绪，但 **controller 网关层缺失**，无法通过 API 创建 INTERNAL_MIRROR source。

---

## 5. 已验证项

### 5.1 UPLOAD 回归 ✅

```
POST /api/v1/admin/uploads → sourceId=2, status=COMPILE_QUEUED
```

UPLOAD 入口未被 INTERNAL_MIRROR 修改破坏。

### 5.2 mvn test 全量通过 ✅

1018/0/0/0, BUILD SUCCESS。包含 `SourceAdminProperties`、`SourceMaterializationService` 等相关测试。

### 5.3 配置加载 ✅

`SourceAdminProperties.mirrorRoots` 通过 `-D` 系统属性成功注入（日志无启动错误）。

### 5.4 Redline ✅

BLOCKER=0，未新增业务词/项目名/文件名特判。

---

## 6. 未验证项（因 controller 端点缺失）

| 验证项 | 状态 |
|---|---|
| 创建 INTERNAL_MIRROR source | **BLOCKED**（无端点） |
| validate | **BLOCKED** |
| sync / compile | **BLOCKED** |
| 过滤规则（排除 .git/target/build/.env/*.class/*.log 等） | **BLOCKED** |
| 路径安全（.. 拒绝、越界拒绝、未知 mirrorRootRef 拒绝） | **BLOCKED** |
| manifest hash 跳过无变化 | **BLOCKED** |
| 修改文件触发新 sync | **BLOCKED** |
| list source files 包含预期文件 | **BLOCKED** |
| GIT 回归 | **BLOCKED**（无 GIT 测试环境） |

---

## 7. 当前代码差异与修复报告不符

| 修复报告声称 | 实际代码 |
|---|---|
| `AdminSourceController.ALLOWED_SOURCE_TYPES` 新增 INTERNAL_MIRROR | **未修改**（仍为 UPLOAD/GIT） |
| `AdminSourceController` 新增 `createInternalMirrorSource()` 端点 | **未修改**（无此方法） |
| 其他 5 个文件 | **已修改**（与报告一致） |

---

## 8. 最终判定

### **BLOCKED — Controller 端点缺失**

| 维度 | 判定 |
|---|---|
| Redline | BLOCKER=0 ✅ |
| mvn test | 1018/0/0/0 ✅ |
| 后端服务代码 | 已就绪 ✅ |
| **Controller 端点** | **缺失** ❌ |
| UPLOAD 回归 | 正常 ✅ |

---

## 9. 下一步建议

**唯一最小动作**：agentA 补齐 `AdminSourceController.java` 中遗漏的 INTERNAL_MIRROR 端点和 `ALLOWED_SOURCE_TYPES` 条目（2 处代码变更），然后 agentD 重跑完整 runtime gate。

具体：对照 `AdminSourceCreateRequest` 中已新增的 `mirrorRootRef`/`projectPath` 字段，参照 `POST /git` 模式实现 `POST /internal-mirror`。

---

## 10. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / scripts / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 合成项目 fixture 不包含任何真实公司项目
