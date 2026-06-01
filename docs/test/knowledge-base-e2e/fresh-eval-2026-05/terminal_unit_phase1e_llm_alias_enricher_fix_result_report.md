# Terminal Unit Phase 1E-2 LLM Alias Enricher 修复结果报告

## 1. 修改摘要

- `src/main/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricher.java`：在 Phase 1E-1 接口与 NoOp 骨架上新增 Spring Bean 实现 `LlmFactCardTerminalUnitFieldAliasEnricher`，接入 `LlmGateway`，使用 `compile` 场景、`field-alias-enricher` role、`enrich-field-aliases` purpose 调用 LLM；增加候选筛选、结构化输入构造、JSON 响应解析、alias 过滤去重限长限量、fail-closed 与 copy-with / `rebuildFtsText` 合并路径。
- `src/main/java/com/xbk/lattice/compiler/prompt/CompilerPromptProvider.java`：加载并暴露 `fieldAliasEnricherPrompt()`。
- `src/main/resources/prompts/compiler/field-alias-enricher.md`：新增通用字段别名生成 prompt，要求严格 JSON 输出，仅描述英文字段名生成中文检索别名任务。
- `src/test/java/com/xbk/lattice/compiler/service/FactCardTerminalUnitFieldAliasEnricherTests.java`：新增 test-only fake `LlmGateway`，覆盖 LLM 成功、异常、非 JSON、空响应、已有 CJK alias 跳过、role 不可用 fail-closed 等场景。
- `docs/test/knowledge-base-e2e/fresh-eval-2026-05/terminal_unit_phase1_implementation_plan.md`：回写 Phase 1E-2 checkpoint 状态。

## 2. 设计边界

本轮只实现 LLM alias runtime、prompt 加载与最小单测，不修改 Query / fallback / reranker / citation / RRF / SQL / schema 主链。Phase 1E-2 的唯一处理根因是英文字段名缺少中文 alias，导致中文 query 难以 lexical 命中目标 terminal unit。

LLM 输入限定为 terminal unit 通用结构信息：`terminalKey`、`fieldLabel`、`keyPath`、`parentPath`、`pathSegments`、`valueType`、`displayText`、`raw` 以及同 parentPath 下的 sibling key/value 摘要。

## 3. 红线合规

- Java 主链没有新增英文到中文映射表、业务词白名单、文件名判断、题面判断、case id 判断或答案模板。
- Prompt 没有 fresh eval 业务词示例，也没有写入特定领域答案式示例。
- LLM 输入不包含文件名、query 日志、eval 题面、case id 或 expected answer。
- 测试数据使用中性 synthetic 名称：`group_alpha`、`target_metric`、`sample_limit`、`甲类对象`、`指标参数`。
- 只对缺少 CJK alias 的英文字段 terminal unit 触发增强；字段名或已有 alias 已含 CJK 时不做 route 预检、不调用 LLM、不改写记录。

## 4. Fail-closed 行为

- `routeResolution(compile, field-alias-enricher)` 不可用、抛异常、未绑定可审计 role、模型名为空或 fallback / unknown 时，直接返回原 records。
- `generateText` 抛异常、LLM 返回空响应、非 JSON、JSON 中无 `aliases` object 或无有效 alias 时，直接返回原 records。
- 生成 alias 必须含 CJK、非空、长度不超过 20；每字段最多接收 5 个生成 alias，总 alias 数最多 20；去重后才合并。
- 生成 alias 如包含当前字段值或 normalizedValue，会被过滤，避免把答案值写进检索别名。
- 合并成功时复用 Phase 1E-1 的 `withFieldAliasesAndFtsText` 与 `rebuildFtsText`，确保 `fieldAliasesJson` 与 `ftsText` 同步更新。

## 5. 测试结果

- `git diff --check`：通过。
- `bash scripts/scan-redline.sh special_cases_report.md`：通过，`BLOCKER=0`，`REVIEW=2065`，`ALLOWLIST=260`。
- `mvn test -Dtest=FactCardTerminalUnitFieldAliasEnricherTests,CompilerPromptProviderTests`：最终通过，`Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn test`：通过，`Tests run: 983, Failures: 0, Errors: 0, Skipped: 0`，总耗时 6:30。

迭代中定向测试曾失败两次：第一次是 prompt 断言与实际文案不匹配，修正断言为当前通用任务描述；第二次是过长 alias fixture 实际未超过 20 字符，修正 synthetic fixture 后验证过滤逻辑通过。两次修复均限于本轮 alias enricher 测试与 prompt 断言范围，未扩大到 query / fallback / reranker / citation / SQL。

## 6. 未修改清单

本轮未修改：

- Query / fallback / reranker / citation / RRF 主链。
- `FactCardTerminalUnitIntentReranker.java`。
- `FactCardTerminalUnitFtsSearchService.java`。
- `LexicalSearchTokenBudget.java`。
- `QuerySemanticRules.java` 与 `lattice-query-semantic.yml`。
- `FactCardTerminalUnitMapper.xml` 与 `schema.sql`。
- `AnswerGeneration*`、fallback、citation 相关文件。
- `scripts/scan-redline.sh`、allowlist、`AGENTS.md`、`CLAUDE.md`。
- fresh eval 题集、标准答案、case id、验收口径。
- `docs/模型绑定配置参考.md`，该本地配置脏改未触碰、未引用、未纳入本轮交付。

## 7. 下一步

建议交给 agentD 做独立验证：先核验 `field-alias-enricher` role 在真实 compile runtime 下是否有可审计绑定并会触发 LLM；再在授权后做端到端验证。agentA 本轮不进入业务 eval、不清库、不重建 schema。
