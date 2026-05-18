# Compile Review Prompt Externalization Pre-Commit Quality Report

## 1. 结论

**建议提交。**

本轮审查未发现红线 BLOCKER、业务硬编码、case 特判、eval 污染、shared rules 死配置、user prompt 错误外部化、prompt 缺失/空文件静默降级，且全量 `mvn test` 通过。当前改动符合“将 Compile Writer / Reviewer / Fixer system prompt 外置”的设计边界。

提交时建议只纳入本次实现必要文件和最终报告，避免把过期过程报告一并打包。

## 2. Gate 结果

| 项 | 结果 |
|---|---:|
| redline BLOCKER | 0 |
| redline REVIEW | 1859 |
| redline ALLOWLIST | 242 |
| `mvn test` | 824 / 0 / 0 |
| BUILD | SUCCESS |

说明：

- `special_cases_report.md` 由 redline 扫描刷新。
- Maven 日志中出现的 timeout / transport / lifecycle listener stack trace 来自故障回归测试的预期注入场景，对应测试均通过。
- 未发现未说明的测试失败；此前提到的 Anthropic flaky 本轮未复现。

## 3. 变更范围审查

| 范围 | 审查结果 |
|---|---|
| `CompilerPromptProvider` | 新增统一 classpath loader；构造期加载 6 个 prompt；替换 shared rules；缺失/空文件/未解析占位符 fail-fast |
| `SchemaAwarePrompts` | Spring 双参数构造器注入 provider；Writer text/image prompt 优先走 provider；单参数构造器保留测试兼容 |
| `CompileArticleNode` | image writer prompt 改为经 `SchemaAwarePrompts.getCompileImageArticlePrompt()` 获取 |
| `ArticleCompileSupport` | Spring 构造链传入 `CompilerPromptProvider`，确保 StateGraph Writer 运行路径使用外部 prompt |
| `ArticleReviewerGateway` | Reviewer text/image system prompt 经 provider 获取；保留 null-provider 测试兼容 fallback |
| `ReviewFixService` | Fixer system prompt 经 provider 获取；保留 null-provider 测试兼容 fallback |
| `src/main/resources/prompts/compiler/*.md` | 6 个 prompt 文件存在；4 个 role prompt 引用 `{{shared-grounding-rules}}` |
| `CompilerPromptProviderTests` | 13 个测试覆盖非空、占位符解析、shared rules 注入、语义等价、缺失/空文件 fail-fast |

未纳入范围但仍存在的旧常量使用：

- `AnalyzeNode`、incremental enhancement、synthesis artifacts 等非 Writer/Reviewer/Fixer 外置目标仍使用 `LatticePrompts`。
- `IncrementalCompileBaseSupport` 仍通过单参数 `SchemaAwarePrompts` 构造旧式 direct/incremental 支撑链。这属于此前已识别的 legacy direct compile 封存议题；正常 StateGraph 用户入口不因此绕过本轮外置路径，不阻塞本次提交。

## 4. 红线 / 污染审查

污染词扫描：

```bash
rg -n "Q-|caseId|expectedAnswer|query-regression|hidden eval|SWIP|卡券|dpfm|银行|结算|测试集" src/main/resources/prompts/compiler src/main/java/com/xbk/lattice/compiler
```

结果：无命中。

判断：

- 未发现业务硬编码。
- 未发现 case 特判。
- 未发现 hidden eval / public eval 污染。
- prompt 文件内容是通用编译、审查、修复约束；未绑定具体资料、题目、答案片段或业务词。

## 5. Shared Rules 风险

**未发现 shared-grounding-rules 死配置风险。**

当前命中：

| 文件 | 占位符 |
|---|---|
| `writer.md` | `{{shared-grounding-rules}}` |
| `writer-image.md` | `{{shared-grounding-rules}}` |
| `reviewer.md` | `{{shared-grounding-rules}}` |
| `reviewer-image.md` | `{{shared-grounding-rules}}` |
| `fixer.md` | 不需要 shared rules，占位符 N/A |

`CompilerPromptProviderTests.rolePromptsShouldContainSharedGroundingRulesContent()` 已覆盖 provider 输出中包含 `TRUTH LEVEL ANNOTATIONS`、`KNOWLEDGE CLASSIFICATION`、`Referential Knowledge`，且不含未解析 `{{`。

## 6. Fail-Fast / 静默降级审查

**未发现 prompt 文件缺失或空文件时静默降级风险。**

`CompilerPromptProvider` 行为：

- classpath 文件缺失：抛 `IllegalStateException("Compiler prompt file missing...")`。
- loader 返回 `null`：抛缺失异常。
- 文件为空或空白：抛 `IllegalStateException("Compiler prompt file is empty...")`。
- 替换后仍存在 `{{`：抛 `IllegalStateException("Unresolved placeholder...")`。
- Spring runtime 中 provider 是 `@Service`，启动期加载全部 prompt；资源异常会阻断启动。

保留的 `LatticePrompts` fallback 只在 provider 为 `null` 的测试/手工构造场景生效，不是 prompt 文件缺失时的生产静默降级。

## 7. User Prompt 边界

**未发现 user prompt 错误外部化风险。**

- 外部 `.md` 文件只承载 system prompt。
- Writer user prompt 仍由 `CompileArticleNode.buildCompilePrompt(...)` 动态组装。
- Reviewer user prompt 仍由 `ArticleReviewerGateway.review(...)` 动态组装。
- Fixer user prompt 仍由 `ReviewFixService.applyFix(...)` 动态组装。
- `SCHEMA.md` 追加到 Writer system prompt 是既有 schema overlay 行为，本轮未扩大。

## 8. Review Loop / Persist Gate

**未发现破坏 compile review loop / persist gate 的风险。**

依据：

- 代码改动只替换 system prompt 来源，不改 Writer -> Reviewer -> Fixer -> Reviewer 控制流。
- 不改 `ReviewDecisionPolicy`、`PersistArticlesNode`、query visibility hard filter。
- `compile_review_prompt_externalization_final_runtime_gate_report.md` 已验证：compile review 小流量 17 步 succeeded，LLM 未通过内容 `acceptedCount=0`、`persistedCount=0`，RULE_BASED 通过内容正常 persist。

## 9. 是否需要 Clean Rebuild / Query Baseline

不建议额外跑 clean rebuild：

- 本轮已执行 `mvn test`，Maven 已复制 resources、重新编译 changed source/test source。
- Spring 启动和 runtime gate 已验证 6 个 prompt 从 classpath 成功加载。
- 再跑 `mvn clean test` 对当前风险增量很低。

不建议跑完整 query baseline：

- 本轮变更集中在 compile system prompt 外部化，不修改 Query / AnswerGeneration / Retrieval / Citation 主链。
- 全量单测已覆盖 query 相关测试。
- Query baseline 成本高、归因弱，不适合作为本次 prompt 外部化提交前门禁。

## 10. 提交建议

**可以提交。**

建议提交内容：

- `src/main/java/com/xbk/lattice/compiler/prompt/CompilerPromptProvider.java`
- `src/main/java/com/xbk/lattice/compiler/prompt/SchemaAwarePrompts.java`
- `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
- `src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java`
- `src/main/java/com/xbk/lattice/compiler/service/ArticleReviewerGateway.java`
- `src/main/java/com/xbk/lattice/compiler/service/ReviewFixService.java`
- `src/main/resources/prompts/compiler/*.md`
- `src/test/java/com/xbk/lattice/compiler/prompt/CompilerPromptProviderTests.java`
- 必要的最终验证/质量报告

不建议提交过期过程报告；若要保留，也应明确它们是审计附件，而不是当前状态结论。

## 11. 本轮修改说明

- 本轮是否修改代码：**否**。
- 本轮是否修改 prompt：**否**。
- 本轮是否修改测试：**否**。
- 本轮是否修改配置/脚本/redline allowlist：**否**。
- 本轮新增报告：`compile_review_prompt_externalization_pre_commit_quality_report.md`。
- 运行 redline 时刷新了 `special_cases_report.md`。
