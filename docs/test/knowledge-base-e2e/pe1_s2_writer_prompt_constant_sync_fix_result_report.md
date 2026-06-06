# PE1 S2 Writer Prompt 常量同步修复结果报告

时间：2026-06-06
执行人：agentA（代码执行 Agent）
前置报告：`pe1_s2_writer_title_preservation_prompt_fix_result_report.md`（agentA，上一轮）

---

## 1. 本轮目标

修复上一轮 `writer.md` 新增通用标题保真规则后，`CompilerPromptProviderTests.writerPromptShouldMatchLatticePromptsConstant` 测试失败的问题——将规则 14 机械同步到 `LatticePrompts.SYSTEM_COMPILE_ARTICLE` 常量。

## 2. 修改文件

`src/main/java/com/xbk/lattice/compiler/prompt/LatticePrompts.java`

- `SYSTEM_COMPILE_ARTICLE` 常量（第 75–120 行区域），在规则 13 之后追加规则 14

## 3. 为什么需要同步 Java fallback 常量

项目在 prompt 外置后采用双轨策略：
- 运行时主路径：`CompilerPromptProvider` 从 `writer.md` 文件加载 prompt
- fallback 路径：`ArticleReviewerGateway` 和 `ReviewFixService` 在 null-provider 场景下回退读取 `LatticePrompts.SYSTEM_COMPILE_ARTICLE` 常量

`CompilerPromptProviderTests.writerPromptShouldMatchLatticePromptsConstant` 通过 `normalizeWhitespace().isEqualTo()` 精确比对两者，确保 fallback 常量不会因静默过期而与活跃 prompt 产生语义漂移。上一轮仅修改了 `writer.md` 而未同步常量，导致精确比对失败。

## 4. 同步内容

在 `SYSTEM_COMPILE_ARTICLE` 常量的规则 13 之后追加规则 14：

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

文本与 `writer.md` 中的规则 14 语义完全一致。

## 5. 同步是否属于机械同步

是。本轮变更：
- 不改动 prompt 规则含义
- 不新增、不删除、不修改任何规则
- 纯文本追加，将 `writer.md` 已有规则原样复制到 Java 常量
- 不涉及业务逻辑、检索链路、答案生成

## 6. redline 结果

| 指标 | 值 |
|------|-----|
| BLOCKER | 0 |
| 总命中 | 2377 |
| 高风险 | 0 |
| 结论 | PASS |

## 7. 定向测试结果

`CompilerPromptProviderTests` + `SchemaAwarePromptsTests`：

| 测试类 | 结果 |
|--------|------|
| CompilerPromptProviderTests | 13/0/0/0 |
| SchemaAwarePromptsTests | 7/0/0/0 |
| **合计** | **20/0/0/0** |
| 结论 | PASS |

## 8. 全量 mvn test 结果

| 指标 | 值 |
|------|-----|
| 总数 | 1018 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 结论 | BUILD SUCCESS |

## 9. 行为不变声明

- Writer LLM 的标题保真行为仅由 `writer.md`（运行时主路径）决定，常量同步不改变运行时行为
- 本轮只让 fallback 常量与活跃 prompt 保持语义一致，不引入新功能

## 10. 后续 agentD runtime gate 建议

1. PE1 clean-schema 重编译（清库、重新导入全部 PE1 资料）
2. 搜索 `下一步计划`，检查 rank1 或高位结果的 sectionAnchor/title 是否包含或保留源标题文本
3. 检查 S2 是否从 PARTIAL 变为 PASS
4. PE1 Q1-Q12 和 S1-S4 做保护回归
5. PE2 Search 6/6 是否无回归

## 11. 未提交文件提醒

本轮修改未提交 commit，包括：
- `src/main/java/com/xbk/lattice/compiler/prompt/LatticePrompts.java`（常量同步规则 14）
- `docs/test/knowledge-base-e2e/pe1_s2_writer_prompt_constant_sync_fix_result_report.md`（本报告）

## 12. 明确声明

- [x] 本轮仅做 `LatticePrompts.SYSTEM_COMPILE_ARTICLE` 常量机械同步
- [x] 未修改 Query / Retrieval / Rerank / Fallback / Answer Generation 主链
- [x] 未修改 `src/test/java/**`
- [x] 未修改 eval 题集、public/hidden eval 数据
- [x] 未读取 hidden eval 内容
- [x] 未修改 redline allowlist / `scripts/scan-redline.sh`
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 常量中不包含任何具体业务词、题号、文件名、标题文本或样例答案
