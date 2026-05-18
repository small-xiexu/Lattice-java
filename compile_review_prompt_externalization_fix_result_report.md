# compile_review_prompt_externalization_fix_result_report.md

## 1. 任务目标

将 Compile Writer / Reviewer / Fixer system prompt 从 `LatticePrompts.java` 硬编码常量外置到 classpath `.md` 文件，由新建 `CompilerPromptProvider` @Service 统一加载，支持 `{{shared-grounding-rules}}` 占位符替换。

## 2. 变更摘要

| 类型 | 文件 | 变更说明 |
|------|------|----------|
| 新增 | `src/main/resources/prompts/compiler/shared-grounding-rules.md` | TRUTH_ANNOTATION_RULES + KNOWLEDGE_CLASSIFICATION 共享基础规则 |
| 新增 | `src/main/resources/prompts/compiler/writer.md` | Writer system prompt，含 `{{shared-grounding-rules}}` |
| 新增 | `src/main/resources/prompts/compiler/writer-image.md` | Writer Image system prompt，含 `{{shared-grounding-rules}}` |
| 新增 | `src/main/resources/prompts/compiler/reviewer.md` | Reviewer system prompt，含 `{{shared-grounding-rules}}` |
| 新增 | `src/main/resources/prompts/compiler/reviewer-image.md` | Reviewer Image system prompt，含 `{{shared-grounding-rules}}` |
| 新增 | `src/main/resources/prompts/compiler/fixer.md` | Fixer system prompt（无占位符） |
| 新增 | `src/main/java/.../prompt/CompilerPromptProvider.java` | @Service，构造时加载全部 prompt 并替换占位符，fail-fast 校验 |
| 新增 | `src/test/java/.../prompt/CompilerPromptProviderTests.java` | 11 个测试：非空校验 + 占位符解析校验 + 与 LatticePrompts 常量语义等价校验 |
| 修改 | `src/main/java/.../prompt/SchemaAwarePrompts.java` | 新增 2-param 构造器接受 CompilerPromptProvider；getCompileArticlePrompt/getCompileImageArticlePrompt 优先从 provider 取值 |
| 修改 | `src/main/java/.../node/CompileArticleNode.java` | resolveCompileSystemPrompt() 改为通过 schemaAwarePrompts 获取 image prompt |
| 修改 | `src/main/java/.../service/ArticleReviewerGateway.java` | 新增 6-param 构造器注入 CompilerPromptProvider；resolveReviewSystemPrompt() 优先从 provider 取值 |
| 修改 | `src/main/java/.../service/ReviewFixService.java` | 新增 2-param 构造器注入 CompilerPromptProvider；applyFix() 优先从 provider 取值 |
| 修改 | `src/main/java/.../service/ArticleCompileSupport.java` | @Autowired 构造器接受 CompilerPromptProvider 并传递给 SchemaAwarePrompts |

## 3. 未修改文件

- `LatticePrompts.java` — 所有常量保持不变，未删除任何内容
- 用户提示词 — 未外置任何 user prompt
- `application.yml` — 未新增配置项
- 现有测试文件 — 所有 Stub 测试类的构造器调用保持向后兼容，无需修改

## 4. 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 占位符机制 | `String.replace()` 替换 `{{shared-grounding-rules}}` | 最小实现，无模板引擎依赖 |
| 加载时机 | 构造器一次性加载 | 避免运行时 I/O，fail-fast 暴露配置问题 |
| 向后兼容 | null-safe fallback 到 LatticePrompts 常量 | 所有 1-param/4-param 测试构造器传 null，自动回退 |
| .md 文件来源 | 从 Java 常量精确提取 | 确保字节级等价，消除 Unicode 引号/空白差异 |

## 5. CompilerPromptProvider 行为

- 构造时从 classpath 加载 6 个 `.md` 文件
- `shared-grounding-rules.md` 内容替换所有模板中的 `{{shared-grounding-rules}}`
- 校验：文件缺失 → `IllegalStateException`；文件为空 → `IllegalStateException`；残留 `{{` → `IllegalStateException`
- 暴露 5 个 getter：`writerPrompt()` / `writerImagePrompt()` / `reviewerPrompt()` / `reviewerImagePrompt()` / `fixerPrompt()`

## 6. 调用链变更

### Writer
```
ArticleCompileSupport → SchemaAwarePrompts(compilerProperties, compilerPromptProvider)
  → getCompileArticlePrompt() → compilerPromptProvider.writerPrompt() + schema overlay
  → getCompileImageArticlePrompt() → compilerPromptProvider.writerImagePrompt()
CompileArticleNode.resolveCompileSystemPrompt() → schemaAwarePrompts.getCompileImageArticlePrompt()
```

### Reviewer
```
ArticleReviewerGateway.resolveReviewSystemPrompt()
  → compilerPromptProvider.reviewerPrompt() / reviewerImagePrompt()
  → fallback: LatticePrompts.SYSTEM_REVIEW / SYSTEM_REVIEW_IMAGE_ARTICLE
```

### Fixer
```
ReviewFixService.applyFix()
  → compilerPromptProvider.fixerPrompt()
  → fallback: LatticePrompts.SYSTEM_REVIEW_FIX
```

## 7. 测试适配

| 测试类 | 状态 | 说明 |
|--------|------|------|
| CompilerPromptProviderTests (新增) | 11/11 通过 | 非空、占位符解析、语义等价、grounding-rules 内容验证 |
| SchemaAwarePromptsTests | 6/6 通过 | 1-param 构造器向后兼容，无修改 |
| ReviewFixServiceTests | 1/1 通过 | 1-param 构造器向后兼容，无修改 |
| ArticleReviewerGatewayTests | 8/8 通过 | 4-param/5-param 构造器向后兼容，无修改 |
| CompileArticleReviewFlowTests | 6/6 通过 | StubArticleReviewerGateway(4-param) / StubReviewFixService(1-param) 向后兼容 |
| CompilerAgentAdaptersTests | 6/6 通过 | Stub 向后兼容 |

## 8. 验证结果

| 验证项 | 结果 |
|--------|------|
| `bash scripts/scan-redline.sh special_cases_report.md` | BLOCKER=0，exit 0 |
| 定向测试 (6 个测试类) | 38/38 通过，0 失败 |
| 全量测试 `mvn test` | **822/822 通过，0 失败，0 错误** |

## 9. 禁止项检查清单

- [x] 未改变任何 prompt 语义（通过 normalizeWhitespace 等价断言验证）
- [x] 未外置 user prompt
- [x] 未删除 LatticePrompts 中的任何常量
- [x] 未引入模板引擎（仅 String.replace）
- [x] 未添加 hot-reload / 文件监控
- [x] 未新增 application.yml 配置
- [x] 未改变现有测试文件

## 10. 新增/变更文件清单

```
src/main/resources/prompts/compiler/shared-grounding-rules.md  (新增)
src/main/resources/prompts/compiler/writer.md                   (新增)
src/main/resources/prompts/compiler/writer-image.md             (新增)
src/main/resources/prompts/compiler/reviewer.md                 (新增)
src/main/resources/prompts/compiler/reviewer-image.md           (新增)
src/main/resources/prompts/compiler/fixer.md                    (新增)
src/main/java/.../compiler/prompt/CompilerPromptProvider.java   (新增)
src/test/java/.../compiler/prompt/CompilerPromptProviderTests.java (新增)
src/main/java/.../compiler/prompt/SchemaAwarePrompts.java       (修改)
src/main/java/.../compiler/node/CompileArticleNode.java         (修改)
src/main/java/.../compiler/service/ArticleReviewerGateway.java  (修改)
src/main/java/.../compiler/service/ReviewFixService.java        (修改)
src/main/java/.../compiler/service/ArticleCompileSupport.java   (修改)
```
