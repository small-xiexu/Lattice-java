# Compile Review Prompt 外部化最终 Runtime Gate 报告

- **验证时间**：2026-05-18 16:01–16:08 CST
- **验证分支**：`codex/qa-polish`
- **依赖修复**：`compile_review_prompt_externalization_shared_rules_fix_result_report.md`
- **代码修改**：否（严格遵守禁止修改代码的约束）
- **Prompt 修改**：否（严格遵守禁止修改 prompt 的约束）

---

## 1. Redline 扫描

| 指标 | 值 |
|------|-----|
| 总命中 | 2101 |
| 高风险 | 0 |
| **BLOCKER** | **0** |
| REVIEW | 1859 |
| ALLOWLIST | 242 |

---

## 2. Shared Rules 占位符命中情况

```
src/main/resources/prompts/compiler/writer.md:3:{{shared-grounding-rules}}
src/main/resources/prompts/compiler/writer-image.md:3:{{shared-grounding-rules}}
src/main/resources/prompts/compiler/reviewer.md:3:{{shared-grounding-rules}}
src/main/resources/prompts/compiler/reviewer-image.md:3:{{shared-grounding-rules}}
```

| Prompt 文件 | `{{shared-grounding-rules}}` 命中 | 说明 |
|-------------|:---:|------|
| `writer.md` | ✅ (line 3) | Writer system prompt 引用共享规则 |
| `writer-image.md` | ✅ (line 3) | Writer-image prompt 引用共享规则 |
| `reviewer.md` | ✅ (line 3) | Reviewer prompt 引用共享规则 |
| `reviewer-image.md` | ✅ (line 3) | Reviewer-image prompt 引用共享规则 |
| `fixer.md` | N/A (不含占位符) | Fixer 无需共享规则，独立 prompt |
| `shared-grounding-rules.md` | 1252 bytes | 存在且非空 ✅ |

---

## 3. Spring 启动

```
Started LatticeApplication in 3.987 seconds
Tomcat started on port 18083 (http)
```

| 检查项 | 状态 |
|--------|:---:|
| Spring context 正常启动 | ✅ |
| 无 `SchemaAwarePrompts` BeanCreationException | ✅ |
| 无 `CompilerPromptProvider` 加载异常 | ✅ |
| 无 `IllegalStateException`（prompt 文件缺失/为空） | ✅ |
| 无 "Unresolved placeholder" 异常 | ✅ |
| 唯一 WARN：DeepResearch bindings not ready（预期内） | ✅ |

---

## 4. CompilerPromptProvider 加载验证

`CompilerPromptProvider` 在构造时加载 6 个外部 prompt 文件并通过 `resolveIncludes()` 完成 `{{shared-grounding-rules}}` 占位符替换。

| 文件 | 加载 | 占位符替换 |
|------|:---:|:---:|
| `prompts/compiler/shared-grounding-rules.md` | ✅ | N/A |
| `prompts/compiler/writer.md` | ✅ | ✅ |
| `prompts/compiler/writer-image.md` | ✅ | ✅ |
| `prompts/compiler/reviewer.md` | ✅ | ✅ |
| `prompts/compiler/reviewer-image.md` | ✅ | ✅ |
| `prompts/compiler/fixer.md` | ✅ | N/A（无占位符） |

**最终 provider 输出是否无未解析 "{{"**：是 ✅

验证方式：
- `CompilerPromptProvider.resolveIncludes()` 中 `resolved.contains("{{")` 检查
- 若存在未解析占位符会抛出 `IllegalStateException` → Spring 启动失败
- Spring 启动成功 = 所有占位符已解析 ✅

---

## 5. Writer / Reviewer / Fixer 外部 Prompt 使用

| 角色 | 代码路径 | 外部 Prompt 文件 | 运行时验证 |
|------|----------|-----------------|:---:|
| **Writer** | `SchemaAwarePrompts.getCompileArticlePrompt()` → `compilerPromptProvider.writerPrompt()` | `writer.md` | ✅ step=`compile_new_articles`, agent=`WriterAgent`, route=`openai` |
| **Writer (Image)** | `SchemaAwarePrompts.getCompileImageArticlePrompt()` → `compilerPromptProvider.writerImagePrompt()` | `writer-image.md` | N/A（本次无图片概念） |
| **Reviewer** | `ArticleReviewerGateway.resolveReviewSystemPrompt()` → `compilerPromptProvider.reviewerPrompt()` | `reviewer.md` | ✅ step=`review_articles`, agent=`ReviewerAgent`, route=`anthropic` |
| **Reviewer (Image)** | `ArticleReviewerGateway.resolveReviewSystemPrompt()` → `compilerPromptProvider.reviewerImagePrompt()` | `reviewer-image.md` | N/A（本次无图片文章） |
| **Fixer** | `ReviewFixService.fix()` → `compilerPromptProvider.fixerPrompt()` | `fixer.md` | N/A（未触发，见下节） |

---

## 6. Compile Review 小流量执行阶段

### LLM Job（`a294af89-...`）

```
步骤                                  角色            路由        状态
────────────────────────────────────────────────────────────────────
initialize_job                                       —           succeeded
ingest_sources                                       —           succeeded
persist_source_files                                 —           succeeded
persist_source_file_chunks                           —           succeeded
extract_ast_graph                                    —           succeeded
group_sources                                        —           succeeded
split_batches                                        —           succeeded
analyze_batches                                      —           succeeded
merge_concepts                                       —           succeeded
compile_new_articles                 WriterAgent     openai      succeeded
review_articles                      ReviewerAgent   anthropic   succeeded
persist_articles                                     —           succeeded
rebuild_article_chunks                               —           succeeded
refresh_vector_index                                 —           succeeded
generate_synthesis_artifacts                         —           succeeded
capture_repo_snapshot                                —           succeeded
finalize_job                                         —           succeeded
```

**17/17 步骤全部 succeeded ✅**

### 阶段分析

| 阶段 | 执行 | 说明 |
|------|:---:|------|
| Writer | ✅ | `compile_new_articles`, WriterAgent, route=openai |
| Reviewer | ✅ | `review_articles`, ReviewerAgent, route=anthropic |
| Fixer | ❌ | `fixStepPresent=false`。Reviewer flagged `needsHumanReview` 但未标记 fixable issue。这是 fail-closed 的正确行为。 |
| Re-Review | ❌ | Fixer 未触发，无 re-review loop |
| Persist Gate | ✅ | `acceptedCount=0` → `persistedCount=0` |

### RULE_BASED Job（`baf716e1-...`）

| 指标 | 值 |
|------|-----|
| reviewMode | RULE_BASED |
| reviewRoute | rule-based |
| acceptedCount | 1 |
| persistedCount | 1 |
| status | SUCCEEDED |

---

## 7. Persist Gate

| 指标 | LLM Job | RULE_BASED Job |
|------|:---:|:---:|
| `acceptedCount` | 0 | 1 |
| `persistedCount` | 0 | 1 |
| articles 表新增 | 0 条 | 1 条 |
| article_chunks 新增 | 0 | 1 |
| query 可见 | **否** ✅ | **是** ✅ |

```
-- articles 表最终状态
 article_key                     | review_status | chunk_count
---------------------------------+---------------+-------------
 payments-docs--payment-timeout  | pending       | 0
 default-source--test-facts      | passed        | 1
```

- LLM 审查未通过的 article **未入库** ✅
- 未入库 → **query 不可见** ✅
- RULE_BASED 通过 article **正常入库 + chunk 已建** ✅
- Persist gate **正常生效** ✅

---

## 8. mvn test

```
Tests run: 824, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 指标 | 值 |
|------|-----|
| 总数 | 824 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| 编译 | BUILD SUCCESS |
| CompilerPromptProviderTests | 13 / 0 / 0 |

---

## 9. 是否修改代码

**必须为否：否 ✅**

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改 `src/main/resources/**`
- 未修改 `scripts/scan-redline.sh`
- 未修改 redline allowlist
- 未修改 AGENTS.md / CLAUDE.md

---

## 10. 是否修改 Prompt

**必须为否：否 ✅**

- 未修改 `src/main/resources/prompts/compiler/*.md`（全部 6 个文件未触碰）
- 仅读取验证占位符，未做任何编辑

---

## 11. Runtime Gate 总览

| 验证项 | 状态 |
|--------|:---:|
| Redline BLOCKER=0 | ✅ |
| `{{shared-grounding-rules}}` 在 4 个 role prompt 中命中 | ✅ |
| shared-grounding-rules.md 存在且非空 | ✅ |
| Spring 启动成功 | ✅ |
| 无 SchemaAwarePrompts BeanCreationException | ✅ |
| CompilerPromptProvider 成功加载 6 个 prompt | ✅ |
| 无未解析 "{{" 占位符 | ✅ |
| Writer 使用外部 prompt（WriterAgent/openai） | ✅ |
| Reviewer 使用外部 prompt（ReviewerAgent/anthropic） | ✅ |
| Fixer 外部 prompt 已接入（未触发但路径就绪） | ✅ |
| Compile review loop 17 步全部 succeeded | ✅ |
| Persist gate 阻断 LLM 未通过内容 | ✅ |
| RULE_BASED 通过内容正常入库/建 chunk | ✅ |
| mvn test 824 / 0 / 0 | ✅ |
| 未修改代码 | ✅ |
| 未修改 prompt | ✅ |

---

## 12. 下一步建议

**最终 Runtime Gate 全部通过 ✅**。建议立即进入 **pre-commit quality review**，重点审查：

1. 6 个外部 prompt 文件的内容质量与 legacy `LatticePrompts` 常量功能对等性
2. `shared-grounding-rules.md` 规则是否完整覆盖 Writer/Reviewer 共有约束
3. `ArticleReviewerGateway` 多构造器链的复杂度（当前 3 个构造器，可考虑后续简化）
4. `ReviewFixService` 双构造器模式与 `@Autowired` 一致性

**所有 gate 条件满足，prompt 外部化改动可以进入 pre-commit review。**
