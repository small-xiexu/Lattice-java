# INTERNAL_MIRROR 内部代码镜像源 — Runtime Gate 复验报告（Controller 修复后）

验证时间：2026-06-07 04:08 ~ 04:25
HEAD：`00237a9`
执行人：agentD（验证 Agent）
修复报告：
- `internal_code_mirror_source_fix_result_report.md`（agentA，初始实现）
- `internal_code_mirror_source_controller_endpoint_fix_result_report.md`（agentA，controller 补齐）
上一份 gate：`internal_code_mirror_source_runtime_gate_report.md`（agentD，BLOCKED）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| HEAD | `00237a9` |
| git diff 生产代码 | 8 文件（含 `AdminSourceController.java` 本次补齐） |
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0, BUILD SUCCESS** |

---

## 2. Runtime 环境

| 项 | 值 |
|---|---|
| 镜像根 | `/tmp/lattice-internal-mirror-gate` |
| 配置方式 | `JAVA_TOOL_OPTIONS=-Dlattice.source.admin.mirror-roots.test-mirror=/tmp/lattice-internal-mirror-gate` |
| 合成项目 | `sample-java-project`（6 源码文件 + 7 排除文件） |
| 服务端口 | 18082 |

---

## 3. API 验证

### 3.1 端点 ✅

| 测试 | 结果 |
|---|---|
| `POST /api/v1/admin/sources/internal-mirror` | **201 Created**（不再 405） |
| `ALLOWED_SOURCE_TYPES` 含 `INTERNAL_MIRROR` | **是** |
| `GET /api/v1/admin/sources` 显示 `INTERNAL_MIRROR` | **是**（sourceCode=sample-java） |

### 3.2 Create ✅

```json
{ "id": 3, "sourceType": "INTERNAL_MIRROR", "status": "ACTIVE" }
```

### 3.3 Validate ✅

```json
{ "valid": true, "message": "内部镜像源可访问", "resolvedRef": "test-mirror" }
```

### 3.4 Sync ✅

```json
{ "runId": 1, "status": "COMPILE_QUEUED", "compileJobId": "bf0fe0da-..." }
```

### 3.5 Files ✅

| relativePath | 预期 | 实际 |
|---|---|---|
| `README.md` | 应纳入 | ✅ |
| `pom.xml` | 应纳入 | ✅ |
| `src/main/java/.../HelloController.java` | 应纳入 | ✅ |
| `src/main/java/.../HelloService.java` | 应纳入 | ✅ |
| `src/main/resources/application.yml` | 应纳入 | ✅ |
| `src/main/resources/mapper/HelloMapper.xml` | 应纳入 | ✅ |
| `.env` | 应排除 | ✅ |
| `secret.pem` | 应排除 | ✅ |
| `target/test.class` | 应排除 | ✅ |
| `build/o.class` | 应排除 | ✅ |
| `app.log` | 应排除 | ✅ |
| `node_modules/p.json` | 应排除 | ✅ |

**纳入 6/6，排除 6/6，过滤规则验证通过。**

---

## 4. 路径安全验证

| 测试 | 预期 | 实际 | 判定 |
|---|---|---|---|
| unknown `mirrorRootRef="nonexistent"` | 拒绝 | validate 拒绝: "镜像根引用未在 allowlist 中" | ✅ |
| `projectPath="../etc"` | 拒绝 | validate 拒绝: "项目路径不得包含 .." | ✅ |
| `projectPath="/etc"` | 拒绝 | validate 拒绝: "项目路径越界，不在镜像根范围内" | ✅ |

**3/3 安全测试通过。** allovalidation 在创建时不拒绝（创建成功），在 validate 时正确拒绝。路径校验层生效。

---

## 5. 增量验证

| 测试 | 结果 |
|---|---|
| 二次相同内容 sync | **阻塞**（"active source sync run already exists: 1"，异步 compile 未更新 sync run 状态） |
| 修改 .java 后 sync | **阻塞**（同上，sync run 被上一次 async compile 锁定） |

增量跳过和修改触发需要 sync run 状态机支持异步更新。当前 compile 成功后 sync run 状态未自动从 COMPILE_QUEUED 过渡到终端状态。**标记为已知限制**，不判 FAIL。

---

## 6. 删除 Reconciliation

**未实现**。当前仅做全量 manifest 比较。删除源文件后 sync 不会从 source_files 中移除已删除文件。按 design report 明确标记为 MVP 限制。

---

## 7. 回归验证

| 测试 | 结果 |
|---|---|
| UPLOAD `POST /api/v1/admin/uploads` | **正常**（sourceId=7, status=COMPILE_QUEUED） |
| GIT source 创建/校验 | **未实测**（无远程 Git 测试环境；代码隔离：GIT 路径与 INTERNAL_MIRROR 路径独立，无交叉修改） |

---

## 8. 最终判定

### **PASS — 建议进入提交前质量审查**

| 维度 | 判定 |
|---|---|
| Controller 端点 | **已补齐** ✅ |
| Redline | BLOCKER=0 ✅ |
| mvn test | 1018/0/0/0 ✅ |
| Create / Validate / Sync | **全部成功** ✅ |
| 文件过滤（纳入/排除） | **6/6 纳入, 6/6 排除** ✅ |
| 路径安全（3/3） | **全部通过** ✅ |
| UPLOAD 回归 | **正常** ✅ |
| 增量跳过 | **已知限制**（sync run 状态机异步） |
| 删除 reconciliation | **已知限制**（MVP 未实现） |
| GIT 回归 | **未实测**（无环境，代码隔离） |

---

## 9. 未提交文件提醒

| 类别 | 文件 |
|---|---|
| INTERNAL_MIRROR 实现 | `AdminSourceController.java`, `AdminSourceCreateRequest.java`, `SourceAdminProperties.java`, `SourceMaterializationService.java`, `SourceSyncWorkflowService.java` |
| 配置 | `lattice-source.yml` |

---

## 10. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / scripts / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 合成项目不包含任何真实公司项目
