# SERVER_DIR 资料源接入方式移出修复结果报告

修复时间：2026-05-30
执行人：agentA
修复类型：架构收敛 — 资料源类型从 UPLOAD/GIT/SERVER_DIR 收敛为 UPLOAD/GIT

---

## 1. 本轮唯一目标

资料源类型收敛为 **UPLOAD + GIT**，彻底移除 SERVER_DIR 资料源接入方式。按全新项目处理，不考虑存量数据、不做迁移、不做兼容、不保留 deprecated 分支。

---

## 2. 删除的后端能力

| 文件 | 删除内容 |
|---|---|
| `SourceMaterializationService.java` | 删除 `validateServerDirSource()`、`materializeServerDirSource()`、`copyDirectory()`、`resolveAllowedServerDir()` 四个方法；`validate()` / `materialize()` 的 SERVER_DIR 分支；未使用的 import（List、StandardCopyOption、Stream） |
| `SourceSyncWorkflowService.java` | 删除 `createServerDirSource()` 方法；`buildConfigJson()` 的 SERVER_DIR 分支 |
| `AdminSourceController.java` | 删除 `POST /api/v1/admin/sources/server-dir` endpoint；`ALLOWED_SOURCE_TYPES` 从 `Set.of("UPLOAD", "GIT", "SERVER_DIR")` 改为 `Set.of("UPLOAD", "GIT")` |
| `AdminSourceCreateRequest.java` | 删除 `serverDir` 字段及 getter/setter |
| `SourceAdminProperties.java` | 删除 `allowedServerDirs` 字段及 getter/setter；移除未使用的 `ArrayList`/`List` import |

---

## 3. 删除的配置项

| 文件 | 删除内容 |
|---|---|
| `src/main/resources/config/lattice-source.yml` | 删除 `allowed-server-dirs` 配置段（`${LATTICE_SOURCE_ADMIN_ALLOWED_SERVER_DIR_1}`） |
| 测试 `@SpringBootTest(properties)` | 删除 `lattice.source.admin.allowed-server-dirs[0]=${java.io.tmpdir}` |

---

## 4. 删除的前端入口

| 文件 | 删除内容 |
|---|---|
| `settings.html` | 删除"服务器目录资料源"表单区块（名称、编码、目录输入 + 创建按钮） |
| `settings.js` | 删除 `createServerSourceAndSync()` 函数、`clearServerSourceForm()` 函数、`redirectToKnowledgeManagement()` 函数；`bindEvents()` 中的 `create-server-source` 事件绑定 |

---

## 5. 注释/CLI 清理

| 文件 | 变更 |
|---|---|
| `SourceMaterializationResult.java` | 类注释：`Git / SERVER_DIR` → `Git` |
| `SourceValidationResult.java` | 类注释：`Git / SERVER_DIR` → `Git` |
| `AdminSourceValidationResponse.java` | 类注释：`Git / SERVER_DIR` → `Git` |
| `AdminSourceCreateRequest.java` | 类注释：`Git / SERVER_DIR` → `Git` |
| `SourceListCommand.java` | CLI help 文案：`UPLOAD / GIT / SERVER_DIR` → `UPLOAD / GIT` |

---

## 6. 测试调整清单

| 文件 | 变更 |
|---|---|
| `AdminSourceControllerTests.java` | 删除 `shouldCreateSyncAndListServerDirSourceFiles` 测试；移除 `allowed-server-dirs` 配置 |
| `AdminProcessingTaskControllerTests.java` | 删除 `shouldExposeServerDirSyncInProcessingTasks` 测试；移除 `allowed-server-dirs` 配置 |
| `AdminPageControllerTests.java` | 删除登录态 `containsString("id=\"create-server-source\"")` 断言；匿名态负向断言保留 |
| `AdminUploadControllerTests.java` | `sourceType="SERVER_DIR"` → `"GIT"`（standalone compile 测试中 synthetic source 类型） |

---

## 7. 最终收尾扫描结果

```
rg -n "SERVER_DIR|serverDir|server-dir|allowedServerDirs|allowed-server-dirs|create-server-source|server-source|createServerSourceAndSync|clearServerSourceForm|redirectToKnowledgeManagement" .
```

剩余命中（排除 `.git/`、`archived_reports/`、`special_cases_report.md`）：

| 文件 | 命中内容 | 分类 |
|---|---|---|
| `AdminPageControllerTests.java` | `not(containsString("id=\"create-server-source\""))` | 负向断言，确认前端按钮已移除（正确保留） |
| `docs/test/.../server_dir_source_removal_fix_result_report.md` | 多处 SERVER_DIR 引用 | 本报告自身（预计在归档后不再作为扫描对象） |
| `docs/plans/2026-05-05-当前剩余工作总清单.md` | 历史 SERVER_DIR 任务项 | 历史计划文件，可不清理 |

**结论**：当前有效生产代码、测试、配置、前端、运行产物中的 SERVER_DIR 引用已清理完毕。剩余命中全部为历史报告/历史计划/负向断言，不需要进一步修改。

---

## 8. 门禁与测试

### 8.1 git diff --check：通过

### 8.2 redline scan

```
BLOCKER=0
```

### 8.3 SERVER_DIR removal targeted verification

| 测试类 | 结果 |
|---|---|
| `AdminSourceControllerTests` | **4/0/0** |
| `AdminUploadControllerTests` | **11/0/0** |
| `AdminProcessingTaskControllerTests` | **5/0/0** |
| `AdminPageControllerTests` | **2/0/0** |
| 定向组合 | **22/0/0 — 全部通过** |

### 8.4 全量 mvn test

```
Tests run: 995, Failures: 0, Errors: 1, Skipped: 0 — BUILD FAILURE
```

1 个 error：`ManagementJsRuntimeTests.shouldVerifyRunFallbackAndErrorPresentationViaNode`。

**归因**：经 `git stash` 回退至干净 HEAD（`21e25e9`）独立复跑，该测试同样失败。结论为预存问题，与本轮 SERVER_DIR removal 无关。**本轮 SERVER_DIR removal 未引入任何新测试失败。**

---

## 9. 明确声明

1. **不考虑存量 SERVER_DIR 数据**：`source_type=SERVER_DIR` 的历史记录不会在本次修改后正常工作，不做迁移、不做兼容。
2. **不保留 deprecated 分支**：已从所有生产代码、测试、配置、前端、运行产物中完全删除。
3. **本轮未修改 query / answer / terminal unit 相关代码**。
4. **未清库、未重建 schema、未导入资料、未跑业务 eval**。

---

## 10. 最终状态

| 维度 | 状态 |
|---|---|
| 代码移除 | **完成** — 所有有效文件中的 SERVER_DIR 引用已清理 |
| 报告一致性 | **已修正** — 过期错误结论已删除，反映最新扫描与测试事实 |
| redline | **BLOCKER=0** |
| 定向测试 | **22/0/0 — 全部通过** |
| 全量测试 | **被既有无关失败阻塞**（`ManagementJsRuntimeTests`，HEAD 上也存在） |

---

## 11. 下一步

1. 交 agentD 做 SERVER_DIR removal 独立验证：
   - 确认 settings 页面不再出现"服务器目录资料源"表单
   - 确认 `POST /api/v1/admin/sources/server-dir` 返回 405
   - 确认 UPLOAD 和 GIT 资料源创建/同步/列表功能正常
2. `ManagementJsRuntimeTests` 既有失败建议单独开一轮处理，不混入 SERVER_DIR removal 范围。

---

## 合规声明

- 本轮未修改 query/answer/retrieval/citation/reranker/terminal unit 代码
- 本轮未修改 schema.sql
- 本轮未修改 redline 脚本或 allowlist
- 本轮未读取 hidden eval
- 本轮未清库、未重建、未导入、未跑业务 eval
- 本轮未 stage、未 commit、未 push
- 新增报告：1（本报告）
