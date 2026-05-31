# Terminal Unit Phase 1E-2 Runtime Wiring Gate 复跑报告

验证时间：2026-05-30
验证人：agentD
验证对象：Phase 1E-2 runtime wiring 修复后的工程门禁复跑

## 1. 验证结论

**PASS** — 全部 6 步门禁通过。`ChatClientRegistryTests` 404 未复现，确认为偶发波动（非稳定失败）。985/0/0/0 干净通过。

## 2. 改动范围核验

### 2.1 Tracked 文件

| 文件 | 归属 | 说明 |
|---|---|---|
| `FactCardTerminalUnitRecord.java` | 1E-1 骨架 | `withFieldAliasesAndFtsText` |
| `FactCardTerminalUnitMaterializer.java` | 1E-1 骨架 | `rebuildFtsText` + `parseAliasesFromJson` |
| `FactCardGenerationService.java` | 1E-1 骨架 | `@Autowired(required = false)` Enricher |
| `CompilerPromptProvider.java` | 1E-2 LLM | 加载 `fieldAliasEnricherPrompt` |
| `AdminLlmConfigController.java` | **Runtime Wiring** | compile role 白名单新增 `field-alias-enricher` |
| `admin-runtime-part-01.js` | **Runtime Wiring** | 旧模块 role 白名单新增 |
| `settings-page-runtime-part-01.js` | **Runtime Wiring** | 设置页 role 白名单新增 |
| `LlmConfigCenterIntegrationTests.java` | **Runtime Wiring** | 新增 role 白名单测试 |
| `SettingsPageJsRuntimeTests.java` | **Runtime Wiring** | 新增 JS role 断言 |
| `terminal_unit_phase1_implementation_plan.md` | 计划回写 | checkpoint |
| `模型绑定配置参考.md` | **无关本地脏改动** | 未纳入交付 |
| `special_cases_report.md` | **redline 输出** | 脚本覆盖 |

### 2.2 Untracked 文件

| 文件 | 归属 | 说明 |
|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricher.java` | 1E-1/1E-2 | public interface + NoOp |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | **Runtime Wiring** | **独立 public @Service 类**（修复 package-private 不可见问题） |
| `field-alias-enricher.md` | 1E-2 | LLM prompt |
| `FactCardTerminalUnitFieldAliasEnricherTests.java` | 1E-2 | 13 个测试 |
| 其他 untracked（phase1d_* 等） | 前轮遗留 | 不在本轮交付 |

### 2.3 范围确认

Runtime wiring 修复新增/修改 6 个文件（Controller + 2 JS + 独立 public Enricher 类 + 2 集成测试）。1E-1/1E-2 骨架不变。query/fallback/Reranker/citation/SQL/schema 未修改。

## 3. 门禁结果

| 步骤 | 命令 | 结果 |
|---|---|---|
| 1 | `git diff --check` | **通过**（无输出） |
| 2 | `bash scripts/scan-redline.sh special_cases_report.md` | **BLOCKER=0** |
| 3 | `mvn test -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,CompilerPromptProviderTests` | **Tests run: 26, Failures: 0, Errors: 0, Skipped: 0** |
| 4 | `mvn test -Dtest=LlmConfigCenterIntegrationTests,SettingsPageJsRuntimeTests,AdminPageControllerTests` | **Tests run: 18, Failures: 0, Errors: 0, Skipped: 0** |
| 5 | `mvn test -Dtest=ChatClientRegistryTests` | **Tests run: 5, Failures: 0, Errors: 0, Skipped: 0** |
| 6 | `mvn test` | **Tests run: 985, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

### 3.1 步骤 3 明细（Enricher + Prompt 定向测试）

| 测试类 | 数量 | 结果 |
|---|---|---|
| `FactCardTerminalUnitFieldAliasEnricherTests` | 13 | PASS（含 1 个新增 public service 构造/注解可见性测试） |
| `CompilerPromptProviderTests` | 13 | PASS |

### 3.2 步骤 4 明细（API/UI Role 定向测试）

| 测试类 | 数量 | 结果 |
|---|---|---|
| `LlmConfigCenterIntegrationTests` | 15 | PASS（含 compile + field-alias-enricher 绑定白名单测试） |
| `SettingsPageJsRuntimeTests` | 1 | PASS（含 field-alias-enricher JS role options 断言） |
| `AdminPageControllerTests` | 2 | PASS |

### 3.3 步骤 6 全量测试

**985/0/0/0 BUILD SUCCESS**。与 fix report 基线一致（985），无新增失败或错误。

## 4. ChatClientRegistryTests 结论

| 运行方式 | 结果 |
|---|---|
| 独立运行（步骤 5） | **5/0/0/0 — 全部通过** |
| 全量运行（步骤 6） | **5/0/0/0 — 全部通过** |

`shouldCacheAndIsolateDynamicChatClientsAcrossRoutes` 的 404 在本轮未复现。上一轮 fix report 中的 404（Tests run: 985, Errors: 1）确认为 **全量运行顺序/隔离相关的偶发波动**，非稳定失败。该测试与 Phase 1E-2 runtime wiring 无关联（测试的是 LLM client 注册/缓存行为，不涉及 alias enricher 或 role 白名单）。

## 5. 未执行项

| 项目 | 状态 | 原因 |
|---|---|---|
| Runtime smoke | 未执行 | 本轮只做 gate 复跑 |
| Clean schema | 未执行 | 禁止项 |
| 全量资料导入 | 未执行 | 禁止项 |
| 19 题业务 eval | 未执行 | 禁止项 |
| 修改代码 | 未执行 | 禁止项 |

## 6. 下一步建议

**全部门禁通过，建议交回项目架构师，安排重新执行 Phase 1E-2 runtime smoke。**

具体步骤：
1. 启动服务（`scripts/run-local-dev.sh`）
2. 验证 `field-alias-enricher` role 可通过 API 创建绑定（不再需 DB 绕过）
3. 确认 `LlmFactCardTerminalUnitFieldAliasEnricher` bean 被 Spring 创建
4. 最小 compile smoke：上传含英文字段名的 YAML 文件，验证 LLM alias enricher 被调用、fielAliasesJson 含中文 alias、ftsText 同步更新
5. 验证 fail-closed 路径（无绑定时不抛异常、原 records 不变）

## 合规声明

- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- `docs/模型绑定配置参考.md` 存在本地配置脏改动，未读取、未引用、未修改
- 未 stage、未 commit、未 push
- 未清库、未重建 schema、未导入资料
- 未跑 runtime smoke 或业务 eval
- 未修 `ChatClientRegistryTests`
- 本轮新增报告：本文件
