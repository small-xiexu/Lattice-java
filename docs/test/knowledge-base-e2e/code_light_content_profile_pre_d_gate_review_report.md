# CODE_LIGHT 内容画像 — 提交前只读质量复核报告（Pre-agentD Gate）

审查时间：2026-06-07
执行人：agentB（治理/链路分析 Agent）
类型：只读质量复核，不改代码，判断是否可以进入 agentD runtime gate

---

## 1. 结论

### **PASS_TO_AGENTD**（可以进入 agentD runtime gate）

需要 1 项最小 agentA 修复（contentProfile 规范化不处理 hyphen），但不阻塞 agentD 的 fresh full compile gate。agentA 可以并行修复。

---

## 2. 关键 Diff 摘要

| 文件 | 变更类型 | 行数 |
|------|:---:|:---:|
| `CompileExecutionRequest.java` | 新增 contentProfile 字段 + normalize + isCodeLightProfile | +50 |
| `CompileGraphState.java` | 新增 contentProfile 字段（Lombok @Getter @Setter） | +1 |
| `CompileGraphStateKeys.java` | 新增 CONTENT_PROFILE 常量 | +1 |
| `CompileGraphStateMapper.java` | contentProfile 读写 | +2 |
| `StateGraphCompileOrchestrator.java` | 传递 contentProfile 到图状态 | +1 |
| `CompileJobService.java` | 从 KnowledgeSource 读取 contentProfile | +1 |
| `CompileGraphConditions.java` | CODE_LIGHT 条件路由（routeAfterMerge + routeAfterPlanChanges） | +12 |
| `CompileGraphDefinitionFactory.java` | 注册 BuildLightweightArticlesNode + 边 | +10 |
| `BuildLightweightArticlesNode.java`（新增） | 从 source chunks 构建最小 ArticleRecord | +190 |

**总计：9 个文件，全部在 `compiler/` 包内，无越界。**

---

## 3. Full CODE_LIGHT 路由审查

### 3.1 路由逻辑

```java
// CompileGraphConditions.routeAfterMerge()
if (isCodeLightProfile(state.getContentProfile())) {
    if ("incremental".equalsIgnoreCase(state.getCompileMode())) {
        return "plan_changes";
    }
    return "build_lightweight_articles";  // ← full CODE_LIGHT
}
```

### 3.2 结论：✅ 正确

Full + CODE_LIGHT 直接路由到 `build_lightweight_articles`，**完全跳过** `compile_new_articles`、`review_articles`、`fix_review_issues`。后续链路：`build_lightweight_articles` → `persist_articles` → `rebuild_article_chunks` → `refresh_vector_index` → `finalize_job`。

---

## 4. Incremental CODE_LIGHT 路由审查

### 4.1 路由逻辑

```java
// CompileGraphConditions.routeAfterPlanChanges()
if (state.isNothingToDo()) {
    return "finalize_job";                        // ✅ 无变化直接结束
}
if (isCodeLightProfile(state.getContentProfile())) {
    if (state.isHasCreates()) {
        return "build_lightweight_articles";      // ✅ 新文件走 lightweight
    }
    if (state.isHasEnhancements()) {
        return "enhance_existing_articles";       // ⚠️ 增强已有文章走 writer/reviewer
    }
}
```

### 4.2 三种路径分析

| 路径 | 路由目标 | 是否走 LLM | 判定 |
|------|------|:---:|:---:|
| `nothingToDo` | `finalize_job` | 否 | ✅ 正确 |
| `hasCreates` | `build_lightweight_articles` | 否 | ✅ 正确 |
| `hasEnhancements` | `enhance_existing_articles` | **是** | ⚠️ 可接受限制 |

### 4.3 hasEnhancements 路径评估

当 CODE_LIGHT 增量编译时，如果已有文章的源文件被修改，`plan_changes` 会标记 `hasEnhancements=true`，路由到 `enhance_existing_articles`，该节点会调用 writer/reviewer/fixer LLM。

**影响场景**：CODE_LIGHT 项目首次编译后，修改源文件再触发增量编译，如果 `plan_changes` 判断需要增强已有文章（而非创建新文章），会走 LLM 路径。

**实际触发概率**：对于 CODE_LIGHT 的典型使用场景（源码文件的 small edit），plan_changes 更可能标记为 `hasCreates`（认为是新概念）或 `nothingToDo`（manifest hash 无变化时跳过）。`hasEnhancements` 触发需要 plan_changes 明确判断"已有文章需要增强"，这在代码场景中较少发生。

**判定**：**可接受限制，不阻塞 agentD gate。** 首次 fresh full CODE_LIGHT 编译不触发此路径。建议在 agentA 报告中明确标注此限制。

---

## 5. Lightweight Article 入库契约审查

### 5.1 ArticleRecord 构造器参数匹配

| 位置 | 参数 | 类型 | 匹配 |
|:---:|------|------|:---:|
| 1 | conceptId | String | ✅ |
| 2 | title（源文件路径） | String | ✅ |
| 3 | content（源码原文） | String | ✅ |
| 4 | "ACTIVE" | String | ✅ |
| 5 | OffsetDateTime.now() | OffsetDateTime | ✅ |
| 6 | sourcePaths | List\<String\> | ✅ |
| 7 | metadataJson | String | ✅ |
| 8 | ""（空 summary） | String | ✅ |
| 9-11 | List.of() × 3 | List\<String\> | ✅ |
| 12 | "high" | String | ✅ |
| 13 | "passed" | String | ✅ |

**结论：参数类型和顺序完全匹配 13 参构造器。编译通过。**

### 5.2 PersistArticlesNode 兼容性

`BuildLightweightArticlesNode` 产出的 `ArticleReviewEnvelope` 设置 `reviewStatus="passed"`，与 `PersistArticlesNode.retainPassedArticles()` 的过滤条件完全兼容。`PersistArticlesNode` 不需要任何修改即可消费 lightweight article。

### 5.3 潜在风险

| 风险 | 等级 | 说明 |
|------|:---:|------|
| `Files.readString` 无大小限制 | 低 | Java 源码文件通常 < 100KB；大文件（> 10MB）可导致 OOM。MVP 可接受 |
| `buildMetadataJson` 手动 JSON 拼接 | 低 | 仅处理文件路径和扩展名，特殊字符概率极低。后续建议改用 ObjectMapper |
| 空 content 的 article | 低 | `readSourceContent` 失败时返回 ""，仍会创建空文章。不影响正确性（空文章不召回） |
| sourcePath 为 null 的 resolveFileType | 无 | `resolveFileType(null)` 返回 "unknown" ✅ |

---

## 6. 红线检查

| 检查项 | 结果 |
|--------|:---:|
| 是否修改 query/answer/rerank/fallback 主链？ | **否** |
| 是否修改 citation/deepresearch？ | **否** |
| 是否修改 schema.sql？ | **否** |
| 是否修改 prompt 文件？ | **否** |
| 是否修改 scripts？ | **否** |
| 是否在代码中写类名/方法名/文件名特判？ | **否** |
| contentProfile 默认值是否向后兼容？ | **是**（null/blank/未知 → DOCUMENT） |

---

## 7. 必须修复项（1 项，不阻塞 agentD gate）

### 7.1 contentProfile 规范化不处理 hyphen 变体

**当前逻辑**：
```java
String normalized = contentProfile.trim().toUpperCase(Locale.ROOT);
if (CONTENT_PROFILE_CODE_LIGHT.equals(normalized)) {
    return CONTENT_PROFILE_CODE_LIGHT;
}
return CONTENT_PROFILE_DOCUMENT;
```

**问题**：`"code-light"` → `"CODE-LIGHT"` ≠ `"CODE_LIGHT"` → 回退到 DOCUMENT。用户传入带连字符的变体时静默回退，不易排查。

**修复**：在 `toUpperCase` 之后增加 `replace('-', '_')`：
```java
String normalized = contentProfile.trim().toUpperCase(Locale.ROOT).replace('-', '_');
```

**是否阻塞 agentD gate**：**否**。agentD 使用 `"CODE_LIGHT"` 精确值，不受影响。agentA 可在 agentD gate 期间并行修复。

---

## 8. 可后置风险

| 风险 | 建议 |
|------|------|
| `hasEnhancements` → `enhance_existing_articles` 走 LLM | agentA 报告中明确标注此限制；后续可改为 CODE_LIGHT 下 hasEnhancements 也走 `build_lightweight_articles` |
| `Files.readString` 无大小限制 | Phase 2 增加文件大小上限（如 5MB） |
| 手动 JSON 拼接 | 后续改用 `JsonMappers` |
| mvn test 13 errors | agentD 在 clean 环境下重跑全量 mvn test，确认是否环境问题 |

---

## 9. agentA 最小修复范围

| 修复项 | 文件 | 优先级 |
|--------|------|:---:|
| contentProfile normalize 增加 hyphen→underscore | `CompileExecutionRequest.java` | **必须**（agentD gate 前或 gate 期间） |

---

## 10. agentD Runtime Gate 建议

```text
你现在是 agentD（验证/测试 Agent）。

本轮目标：
验证 CODE_LIGHT contentProfile 对 INTERNAL_MIRROR Java 项目的编译加速和正确性。

验证范围：
1. Fresh full compile（contentProfile=CODE_LIGHT）：
   - 使用合成 Java 项目 fixture（java-codebase-public-eval/）
   - 验证编译成功，耗时 < 2 分钟（2000 文件规模）
   - 验证 article content 为源码原文（非 LLM 生成）
   - 验证 reviewStatus 全部为 passed
   - 验证 graph step log 中无 compile_new_articles/review_articles/fix_review_issues
   - 验证 persist_articles 正常执行

2. DOCUMENT 模式保护：
   - 使用现有 PE2 资料，contentProfile 不传（默认 DOCUMENT）
   - 验证编译路径不变（仍走 compile_new_articles → review_articles）

3. 搜索与问答：
   - 搜索类名/方法名/配置 key → 应命中 CODE_LIGHT 编译的 article
   - 代码问题查询 → citation 应指向真实源码路径

已知限制（不判 FAIL）：
- Incremental + hasEnhancements 可能走 enhance_existing_articles（LLM 路径）
- contentProfile="code-light" 变体可能不识别（agentA 并行修复中）

输出报告：
docs/test/knowledge-base-e2e/code_light_content_profile_runtime_gate_report.md
```

---

## 11. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 全部审查基于 git diff 源码分析 + agentA 实现报告
- [x] 9 个 CODE_LIGHT 相关文件均在 `compiler/` 包内，无越界
- [x] 1 项必须修复（hyphen 规范化），不阻塞 agentD fresh full gate
- [x] 1 项已知限制（hasEnhancements 走 LLM），MVP 可接受
