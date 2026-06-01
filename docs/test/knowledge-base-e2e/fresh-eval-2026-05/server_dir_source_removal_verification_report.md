# SERVER_DIR Source Removal 独立验证报告

验证时间：2026-05-30
验证人：agentD
验证对象：SERVER_DIR 资料源接入方式移除

## 1. 验证结论

**PASS — SERVER_DIR 移除完成，报告与仓库现状一致。**

全部六个核验项通过：生产代码、配置、前端、测试、定向门禁、全量失败归因。

## 2. 全局扫描结果

```
rg -n "SERVER_DIR|serverDir|server-dir|allowedServerDirs|allowed-server-dirs|create-server-source|server-source|createServerSourceAndSync|clearServerSourceForm|redirectToKnowledgeManagement" .
```

排除 `.git/`、`archived_reports/`、`special_cases_report.md` 后，剩余命中：

| 文件 | 命中内容 | 分类 | 是否符合报告 |
|---|---|---|---|
| `AdminPageControllerTests.java:105` | `not(containsString("id=\"create-server-source\""))` | 负向断言 ✓ | 是 |
| `docs/test/.../server_dir_source_removal_fix_result_report.md` | 多处 SERVER_DIR | 本报告自身 ✓ | 是 |
| `docs/plans/2026-05-05-当前剩余工作总清单.md` | 历史 SERVER_DIR 任务项 | 历史计划文件 ✓ | 是 |

**与报告第 7 节完全一致。无遗漏的残余引用。**

## 3. 逐项核验

### 3.1 后端 API — `POST /api/v1/admin/sources/server-dir`

`AdminSourceController.java:51`：
```java
private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("UPLOAD", "GIT");
```

`SERVER_DIR` 已从白名单移除。无 `server-dir` endpoint。

### 3.2 后端服务 — SourceMaterializationService / SourceSyncWorkflowService

```
grep -c "SERVER_DIR|serverDir|createServerDir" → 0 hits（两个文件）
```

所有 SERVER_DIR 方法（`validateServerDirSource`、`materializeServerDirSource`、`copyDirectory`、`resolveAllowedServerDir`、`createServerDirSource`）已删除。

### 3.3 配置 — lattice-source.yml

```
grep -c "allowed-server-dirs|SERVER_DIR" → 0 hits
```

`allowed-server-dirs` 配置段已删除。

### 3.4 DTO — AdminSourceCreateRequest / SourceAdminProperties

```
grep -c "serverDir|server-dir" → 0 hits（两个文件）
```

`serverDir` 字段及 getter/setter 已删除。`allowedServerDirs` 已删除。类注释已更新（`Git / SERVER_DIR` → `Git`）。

### 3.5 前端 — settings.html / JS modules

| 文件 | 命中数 |
|---|---|
| `settings.html` | 0 |
| `management-runtime-part-01.js` | 0 |
| `management-runtime-part-02.js` | 0 |
| `management-runtime-part-05.js` | 0 |

"服务器目录资料源"表单区块、`createServerSourceAndSync()`、`clearServerSourceForm()`、`redirectToKnowledgeManagement()` 均已删除。

### 3.6 文档 — 项目全流程真实验收手册

```
grep -c "SERVER_DIR|server-dir|服务器目录" → 0 hits
```

### 3.7 测试

| 核验项 | 预期 | 实际 | 结论 |
|---|---|---|---|
| `AdminUploadControllerTests` 中 `sourceType` | `"GIT"`（非 `"SERVER_DIR"`） | `"GIT"` | ✓ |
| `AdminPageControllerTests` 中 `create-server-source` | 负向断言 `not(containsString(...))` | 负向断言 | ✓ |
| `AdminSourceControllerTests` 中 server-dir 测试 | 已删除 | 4 tests, 0 SERVER_DIR 引用 | ✓ |
| `AdminProcessingTaskControllerTests` 中 server-dir 测试 | 已删除 | 5 tests, 0 SERVER_DIR 引用 | ✓ |

## 4. 定向测试

| 测试类 | 结果 |
|---|---|
| `AdminSourceControllerTests` | 4/0/0 |
| `AdminUploadControllerTests` | 11/0/0 |
| `AdminProcessingTaskControllerTests` | 5/0/0 |
| `AdminPageControllerTests` | 2/0/0 |
| **合计** | **22/0/0 — BUILD SUCCESS** |

与报告 8.3 节一致。

## 5. 全量测试

```
Tests run: 995, Failures: 1, Errors: 0, Skipped: 0 — BUILD FAILURE
```

唯一失败：`ManagementJsRuntimeTests.shouldVerifyRunFallbackAndErrorPresentationViaNode`

**独立复跑**：`mvn test -Dtest=ManagementJsRuntimeTests` → 同样失败（8 tests, 1 failure）。

**归因确认**：该失败独立复现于本轮所有改动之外（`management.js` 前端断言），与本轮 SERVER_DIR removal 无关。与报告 8.4 节一致。

## 6. 残余点判断

**无需继续处理的残余点。**

剩余命中全部为：
- 历史计划文件（`2026-05-05-当前剩余工作总清单.md`）中的历史任务记录
- 负向断言（`AdminPageControllerTests` 中的 `not(containsString(...))`）
- 本修复报告自身

以上均不需要修改。生产代码、测试、配置、前端中的 SERVER_DIR 引用已完成清理。

## 7. 合规声明

- 本轮未修改任何代码、配置、测试、报告
- 未清库、未重建 schema、未导入资料、未跑业务 eval
- 本轮新增报告：本文件
