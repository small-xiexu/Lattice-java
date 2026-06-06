# PE1 S2 Writer 标题保真 Prompt 修复结果报告

时间：2026-06-06
执行人：agentA（代码执行 Agent）
前置归因：`pe1_s2_section_anchor_partial_analysis_report.md`（agentB）

---

## 1. 本轮目标

增强 Writer system prompt 的通用"源文档标题保真"约束，修复 Writer LLM 将源 Markdown 结构标题语义改写（如 `## 下一步计划` → `## 落地建议`）导致的 section anchor 不精确问题。

## 2. 根因摘要

- 源文档存在明确 Markdown 结构标题
- Writer LLM 生成 article 时将其改写为语义相近但措辞不同的标题
- chunking 和 `extractSectionAnchor()` 本身没有 bug——它们正确提取了 chunk 中的首行标题
- 搜索能召回相关 chunk，但展示 anchor 不再是源标题文本，用户无法直观关联搜索词与结果
- 根因属于 **Writer 内容重组导致源文档标题丢失**，不是 search/rerank/chunk/extractSectionAnchor 的问题

## 3. 修改文件

`src/main/resources/prompts/compiler/writer.md`

注：任务中引用的 `writer-text.md` 为 prompt 外置前的旧文件名，当前实际文件为 `writer.md`。

## 4. 修改内容

在 Rules 部分新增第 14 条通用规则（第 19 行）：

```
14. When source materials contain explicit section headings (e.g., Markdown
   `##` / `###` lines in structured sections), preserve the original heading
   text as the article section title whenever possible. If you need to
   reorganize, merge, or adjust headings for article flow, retain the original
   heading text near the beginning of the corresponding section as an alias,
   anchor, or searchable phrase — do not silently replace it with a
   semantically similar but differently worded new heading. Consistent heading
   text is essential for search retrieval, citation anchoring, and
   section-anchor stability.
```

## 5. 为什么是通用修复，不是 case 特判

- 规则中**不包含**任何具体业务词、题号、文件名、标题文本或样例答案
- 规则对所有文档、所有 Markdown 标题一视同仁
- 约束的是"保留源标题文本"这个通用行为，不针对任何特定资料
- 同时给出了合理的例外路径：如需合并/调整标题，仍应保留原始标题文本作为别名

## 6. writer-image.md 无需修改

`writer-image.md` 是图像/OCR 专用 prompt，不涉及 Markdown 结构标题保真场景，无需同步修改。

## 7. redline 结果

| 指标 | 值 |
|------|-----|
| BLOCKER | 0 |
| REVIEW | 2115 |
| ALLOWLIST | 262 |
| 结论 | PASS |

## 8. mvn test 结果

| 指标 | 值 |
|------|-----|
| 总数 | 1018 |
| 通过 | 1017 |
| 失败 | 1 |
| 错误 | 0 |
| 结论 | **未通过** |

### 失败测试

`CompilerPromptProviderTests.writerPromptShouldMatchLatticePromptsConstant`

**失败原因**：该测试对 `writer.md` 加载内容与 `LatticePrompts.SYSTEM_COMPILE_ARTICLE` Java 常量做 `normalizeWhitespace().isEqualTo()` 精确比对。本轮在 `writer.md` 新增规则 14 后，外部文件内容不再与存量常量完全一致，测试失败是**预期行为**。

**修复方式**：需将规则 14 同步追加到 `LatticePrompts.java` 中的 `SYSTEM_COMPILE_ARTICLE` 常量。该变更属于 prompt 常量的机械同步，不涉及业务逻辑改动。因本轮任务禁止修改 `src/main/java/**`，未执行此同步。

**影响范围**：该常量仅用于 `ArticleReviewerGateway` 和 `ReviewFixService` 的 null-provider fallback 路径（安全兜底），不经过 `CompilerPromptProvider` 的正常加载路径。运行时实际生效的是外部 `writer.md` 文件，不受常量不同步影响。

## 9. 提示词工程运行测试

项目无独立的 Prompt 或 Compiler 层定向测试。`CompilerPromptProviderTests` 是离本轮修改最近的测试类，其 13 个测试中唯一失败的就是上述常量精确比对测试。

## 10. 行为影响范围

- Writer LLM 生成 article 时会更忠实地保留源文档的标题文本
- 对所有文档类型（Markdown、PDF、XLSX 等有 structured sections 的源文件）均生效
- 不改变 query/retrieval/rerank/citation/fallback 主链行为
- 不改变 chunking 逻辑、section anchor 提取逻辑
- 不改变输出格式、confidence 标注、referential_keywords 生成

## 11. 风险与回归关注点

- Writer 可能在合并源文档多个标题时产生冗长标题——规则允许"合并或调整"，只要求在节内容中保留原始标题
- 对已 PASS 的 PE2 题目无负面影响（更忠实的标题不会让答案变错）
- 可能增加文章标题的字面长度，但对 token 成本影响极小

## 12. 后续 agentD runtime gate 建议

1. PE1 clean-schema 重编译（清库、重新导入全部 PE1 资料）
2. 搜索 `下一步计划`，检查 rank1 或高位结果的 sectionAnchor/title 是否保留或包含源标题文本
3. 检查 S2 是否从 PARTIAL 变为 PASS
4. PE1 Q1-Q12 和 S1-S4 做保护回归
5. PE2 Search 6/6 无回归确认

## 13. 未提交文件提醒

本轮修改未提交 commit，包括：
- `src/main/resources/prompts/compiler/writer.md`（新增规则 14）
- `docs/test/knowledge-base-e2e/pe1_s2_writer_title_preservation_prompt_fix_result_report.md`（本报告）

## 14. 明确声明

- [x] 未修改 `src/main/java/**`
- [x] 未修改 `src/test/java/**`
- [x] 未修改 Query / Retrieval / Rerank / Fallback / Answer Generation 主链
- [x] 未修改 eval 题集、public/hidden eval 数据
- [x] 未读取 hidden eval 内容
- [x] 未修改 redline allowlist
- [x] 未修改 `scripts/scan-redline.sh`
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 规则中不包含任何具体业务词、题号、文件名、标题文本或样例答案
