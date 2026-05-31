# Terminal Unit Phase 1E-2 LLM Alias Enricher Runtime Wiring 修复结果报告

## 1. 修复结论

已修复 runtime wiring 阻塞：真实 LLM alias enricher 不再作为 package-private 顶层类藏在 interface 文件中，而是拆为独立的 public `@Service` 实现 `LlmFactCardTerminalUnitFieldAliasEnricher`，并保留 public 构造器，确保 Spring 能稳定创建 bean 并注入 `FactCardGenerationService`。

同时补齐 `field-alias-enricher` 在 compile 场景的后端和静态前端 role 白名单，后台配置页可通过 API/UI 创建该 role 的绑定，不需要 DB INSERT 绕过正式入口。

## 2. 修改摘要

- `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java`：保留 public interface 与 NoOp 骨架。
- `src/main/java/com/xbk/lattice/compiler/service/LlmFactCardTerminalUnitFieldAliasEnricher.java`：新增 public `@Service` 真实实现类，复用既有 alias 生成、过滤、解析和 fail-closed 逻辑。
- `src/main/java/com/xbk/lattice/api/admin/AdminLlmConfigController.java`：compile role 白名单新增 `field-alias-enricher`。
- `src/main/resources/static/admin/modules/settings-page-runtime-part-01.js`：设置页静态 role options 新增 `field-alias-enricher`。
- `src/main/resources/static/admin/modules/admin-runtime-part-01.js`：旧 runtime 模块静态 role options 新增 `field-alias-enricher`。
- `src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricherTests.java`：补充 public Spring service 构造与注解可见性测试。
- `src/test/java/com/xbk/lattice/api/admin/LlmConfigCenterIntegrationTests.java`：补充 compile + `field-alias-enricher` 绑定创建白名单测试。
- `src/test/java/com/xbk/lattice/api/admin/SettingsPageJsRuntimeTests.java`：补充设置页 JS role options 包含 `field-alias-enricher` 的断言。
- `docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1_implementation_plan.md`：回写本轮 checkpoint。

## 3. 设计边界

本轮只修 Spring bean 注册与 role 白名单，不改 alias 生成策略、不改 prompt、不改解析过滤限长限量规则、不改 fail-closed 行为。

未修改 query / fallback / reranker / citation / RRF 主链，未修改 FTS 检索、intent reranker、token budget、query semantic、Mapper、SQL schema、AnswerGeneration 或 citation 相关实现。

## 4. 合规检查

- 无英文到中文业务词映射表、无 if/switch 特判、无业务词白名单。
- 测试数据使用 synthetic role、route、key，不使用 fresh eval 字段名、文件名、答案值或 case id。
- Prompt 与 alias runtime 策略未改，未新增业务答案式示例。
- 未写入 API key、baseUrl 或模型配置。
- 未修改模型绑定配置参考文档。
- 未清库、未重建 schema、未导入资料、未跑业务 eval。

## 5. 测试结果

- `git diff --check`：通过。
- `bash scripts/scan-redline.sh special_cases_report.md`：通过，`BLOCKER=0`；汇总为总命中 2325、高风险 0、中风险 2065、低风险 260、REVIEW 2065、ALLOWLIST 260。
- `mvn test -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,CompilerPromptProviderTests`：通过，Tests run: 26, Failures: 0, Errors: 0, Skipped: 0。
- `mvn test -Dtest=LlmConfigCenterIntegrationTests,SettingsPageJsRuntimeTests,AdminPageControllerTests`：通过，Tests run: 18, Failures: 0, Errors: 0, Skipped: 0。用于覆盖后端 role validation 与静态前端 role options；未发现独立 npm/JS 定向测试入口。
- `mvn test`：失败。全量跑完 Tests run: 985, Failures: 0, Errors: 1, Skipped: 0；唯一错误为 `ChatClientRegistryTests.shouldCacheAndIsolateDynamicChatClientsAcrossRoutes:91` 抛出 `org.springframework.ai.retry.NonTransientAiException: 404 -`。该失败位于 LLM client registry 测试，不属于本轮 alias runtime wiring 根因与允许修改范围，本轮按要求停止扩大修改并记录。

## 6. 未修改清单

- 未修改 query / fallback / reranker / citation / RRF 主链。
- 未修改 `FactCardTerminalUnitFtsSearchService.java`、`FactCardTerminalUnitIntentReranker.java`、`LexicalSearchTokenBudget.java`。
- 未修改 `QuerySemanticRules.java` / `lattice-query-semantic.yml`。
- 未修改 `FactCardTerminalUnitMapper.xml` / `schema.sql`。
- 未修改 `AnswerGeneration*`、fallback、citation 相关文件。
- 未修改 `scripts/scan-redline.sh`、allowlist、`AGENTS.md`、`CLAUDE.md`。
- 未修改 fresh eval 题集、标准答案、case id、验收口径。
- 未修改模型绑定配置参考文档。

## 7. 下一步

建议先将全量 `mvn test` 中独立的 `ChatClientRegistryTests` 404 按单独根因处理或明确豁免，再交给 agentD 重新做 Phase 1E-2 runtime smoke。不要在本轮自行清库、重建、导入资料或进入业务 eval。
