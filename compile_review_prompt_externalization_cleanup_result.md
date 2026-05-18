# Compile Review Prompt Externalization 报告清理与台账更新结果

执行时间：2026-05-18
执行 Agent：agentC
前置条件：pre-commit 质量复核已通过（redline BLOCKER=0，mvn test=824/0/0）

## 更新了 quality-progress 的哪些内容

`docs/quality-progress-and-lessons.md` 更新了以下章节：

- **时间戳**：更新为 prompt externalization pre-commit 质量复核通过后。
- **当前阶段**：新增 compile review prompt externalization 完成记录（外置实现 + 两轮回归修复 + pre-commit 复核）。
- **当前 Gate**：
  - redline 更新为 `BLOCKER=0 / REVIEW=1859 / ALLOWLIST=242`。
  - mvn test 更新为 `824/0/0`。
  - 新增 compile review prompt externalization 行。
- **多 Agent 职责**：更新 agentC、agentD 状态。
- **已验证结论**：新增 4 条（prompt externalization 完成 + 两轮回归修复 + prompt 红线扫描 + pre-commit 复核）。
- **踩坑记录**：新增 3 条——
  - prompt 外置后存量 Java 常量与外部文件双轨并存（有意保留的向后兼容兜底）。
  - shared rules 占位符未生效导致 prompt 文件内联重复。
  - Spring 多构造器无 `@Autowired` 导致 BeanCreationException。
- **下一步计划**：标记 14（默认 LLM pre-commit）和 18（prompt 文件化）为已完成，新增 19（提交 prompt externalization）。

## 删除了哪些报告（5 份）

| # | 文件 | 删除理由 |
|---|---|---|
| 1 | `compile_review_prompt_externalization_runtime_verification_report.md` | 第一轮 runtime 验证（发现 DI 问题），被 final runtime gate 报告取代 |
| 2 | `compile_review_prompt_externalization_runtime_reverification_report.md` | 第二轮 runtime 验证（发现 shared rules 问题），被 final runtime gate 报告取代 |
| 3 | `compile_review_prompt_externalization_architecture_review_report.md` | 架构审查中间报告，结论已纳入 pre-commit 质量复核 |
| 4 | `compile_review_prompt_externalization_cleanup_plan.md` | 本轮清理计划，已执行完毕 |
| 5 | `compile_review_prompt_externalization_design_report.md` | 设计文档，设计结论已写入 quality-progress 台账 |

## 保留了哪些报告（6 份）

### 本轮核心锚点（5 份）

| # | 文件 | 保留理由 |
|---|---|---|
| 1 | `compile_review_prompt_externalization_fix_result_report.md` | 本轮核心修复：prompt 外置实现，记录所有变更文件 |
| 2 | `compile_review_prompt_externalization_schema_di_fix_result_report.md` | DI 修复结果：Spring 多构造器注入问题根因与修复 |
| 3 | `compile_review_prompt_externalization_shared_rules_fix_result_report.md` | Shared rules 修复结果：占位符替换 + 测试补强 |
| 4 | `compile_review_prompt_externalization_final_runtime_gate_report.md` | 最终 runtime gate：启动 + compile job 端到端 + prompt 红线扫描 |
| 5 | `compile_review_prompt_externalization_pre_commit_quality_report.md` | pre-commit 质量复核：全量审查结论 |

### 始终保留（1 份）

| # | 文件 | 保留理由 |
|---|---|---|
| 6 | `special_cases_report.md` | redline 基线 |

## 是否修改源码

**否。**

## 是否修改测试

**否。**

## 是否修改 prompt

**否。**

## 是否可以进入提交

**是。**

pre-commit 质量复核已通过（redline BLOCKER=0，mvn test=824/0/0），无业务硬编码/case 特判/eval 污染。

## 建议提交文件清单

### 源码（7 个文件）

| 文件 | 类型 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/prompt/CompilerPromptProvider.java` | 新增 |
| `src/main/resources/prompts/compiler/shared-grounding-rules.md` | 新增 |
| `src/main/resources/prompts/compiler/writer.md` | 新增 |
| `src/main/resources/prompts/compiler/writer-image.md` | 新增 |
| `src/main/resources/prompts/compiler/reviewer.md` | 新增 |
| `src/main/resources/prompts/compiler/reviewer-image.md` | 新增 |
| `src/main/resources/prompts/compiler/fixer.md` | 新增 |

### 修改文件（5 个）

| 文件 | 变更说明 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/prompt/SchemaAwarePrompts.java` | 新增 `@Autowired` 双参数构造器 |
| `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java` | image prompt 改为经 provider 获取 |
| `src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java` | 构造链传入 `CompilerPromptProvider` |
| `src/main/java/com/xbk/lattice/compiler/service/ArticleReviewerGateway.java` | Reviewer prompt 经 provider 获取 |
| `src/main/java/com/xbk/lattice/compiler/service/ReviewFixService.java` | Fixer prompt 经 provider 获取 |

### 测试（1 个文件）

| 文件 | 类型 |
|---|---|
| `src/test/java/com/xbk/lattice/compiler/prompt/CompilerPromptProviderTests.java` | 新增 |

### 锚点报告（5 份）

| 文件 |
|---|
| `compile_review_prompt_externalization_fix_result_report.md` |
| `compile_review_prompt_externalization_schema_di_fix_result_report.md` |
| `compile_review_prompt_externalization_shared_rules_fix_result_report.md` |
| `compile_review_prompt_externalization_final_runtime_gate_report.md` |
| `compile_review_prompt_externalization_pre_commit_quality_report.md` |

### 台账（2 份）

| 文件 |
|---|
| `docs/quality-progress-and-lessons.md` |
| `special_cases_report.md` |

## 确认清单

- [x] 是否修改源码：**否**
- [x] 是否修改测试：**否**
- [x] 是否修改 prompt：**否**
- [x] 是否修改配置/脚本：**否**
- [x] 是否删除必须保留报告：**否**
- [x] 是否误删 final runtime gate 报告：**否**
- [x] 是否误删 pre-commit quality 报告：**否**
- [x] 是否提交代码：**否**

## 下一步建议

提交 prompt externalization 代码 + 最终锚点报告。提交建议内容见上方"建议提交文件清单"。
