# compile_reviewer_payload_slimming_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
  - `compile(MergedConcept, Path)`
  - `buildReviewSourceContents(ArticleRecord)`
  - `buildReviewSourceContents(MergedConcept, ArticleRecord)`
  - `selectContentBySourceRefs(String, List<String>, String)`
  - `buildRelevantSourceContents(...)`
  - `resolveSourceContentCandidates(...)`
  - `calculateSourceContentBudget(...)`
  - `selectBoundedRelevantContent(...)`
  - `appendBoundedSourceBlock(...)`
  - `boundText(...)`
  - `buildReviewSourceRefs(...)`
  - `collectSourceRefs(...)`
  - `extractArticleSourceRefs(...)`
  - `buildReviewConceptTerms(...)`
  - `buildArticleTerms(...)`
  - `addTextIfPresent(...)`
  - `safeList(...)`
- `src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java`
  - `reviewDraftArticles(...)`
  - `fixReviewedArticles(...)`
- `src/test/java/com/xbk/lattice/compiler/service/CompileArticleReviewFlowTests.java`
  - 新增 `shouldPassRelevantSourcePayloadToReviewerAndFixer()`
  - 新增 `shouldBuildReviewPayloadFromArticleSourceRefs()`
  - 补充测试辅助方法与 stub 记录能力

## 2. Reviewer / Fixer 之前的 payload 构造方式是什么

- 之前先由 `CompileArticleNode.buildSourceContents(...)` 按 `source path` 把所有 source file 全文拼接成：
  - `=== Source: path ===`
  - `全文`
  - `=== End ===`
- 然后再交给 Reviewer / Fixer 走前缀截断：
  - Reviewer：`ArticleReviewerGateway.review(...)` 内部截前 `12000` 字符
  - Fixer：`ReviewFixService.applyFix(...)` 内部截前 `10000` 字符
- 结果是“先吃全文，再做前缀裁剪”，不是“最相关片段优先”。

## 3. 现在改成了什么相关片段选择方式

- 现在在进入 Reviewer / Fixer 之前，先走 `CompileArticleNode.buildReviewSourceContents(...)` 做通用相关片段选择。
- 选择顺序：
  - 先按 `source path` 逐个处理来源文件
  - 优先命中 `sourceRef`
    - 合并概念阶段直接用 `ConceptSection.sourceRefs`
    - 图编排审查阶段从文章正文里的 `[→ source_path, section]` 引用反解 `sourceRef`
  - 若 `sourceRef` 没命中，再按文章标题、`conceptId`、摘要、章节标题、`referential_keywords` 等通用关键词走 `DocumentSectionSelector`
  - 最后按总 payload 上限和单 source 预算做有界截断，而不是简单截全文前缀
- 当前预算：
  - 总 Reviewer/Fixer source payload 上限：`9000` 字符
  - 单 source 默认上限：`4000` 字符

## 4. 是否复用了 Writer 的相关内容选择逻辑

- 是。
- 复用了 Writer 已有的两层核心能力：
  - `selectContentBySourceRefs(...)`
  - `DocumentSectionSelector.readSection(...) / select(...)`
- 本轮没有另造一套与 Writer 风格完全不同的选择器，只是把 Writer 的“sourceRef 优先 + 关键词章节回退”推广到 Reviewer / Fixer。

## 5. 是否减少 Reviewer 覆盖面

- 否。
- Reviewer / Fixer 仍然覆盖全部 `sourcePaths`，只是每个 source 从“全文前缀”改成“相关片段优先 + 有界截断”。

## 6. 是否新增业务特判

- 否。
- 本轮只使用通用信号：
  - `source path`
  - `sourceRef`
  - 文章标题 / `conceptId`
  - 摘要
  - 章节标题
  - `referential_keywords`
  - Markdown 章节读取与统一字符预算

## 7. redline BLOCKER 是否仍为 0

- 是。
- 复跑 `bash scripts/scan-redline.sh special_cases_report.md` 后，`special_cases_report.md` 汇总仍为：
  - `BLOCKER：0`

## 8. 测试是否通过

- 是。
- 定向测试通过：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=CompileArticleReviewFlowTests test`
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=CompileArticleReviewFlowTests,SchemaAwarePromptsTests,ArticleReviewerGatewayTests,ReviewFixServiceTests test`
- 全量测试通过：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`Tests run: 857, Failures: 0, Errors: 0, Skipped: 0`

## 9. 下一轮是否建议交给 agentD 做性能复验

- 是。
- 本轮已经把根因收敛到 payload construction，并确认功能与门禁未回退；下一轮适合交给 agentD 做 Reviewer / Fixer 端到端耗时复验，验证：
  - Reviewer 是否明显低于旧的全文前缀截断方案
  - Fixer 输入是否同步变瘦
  - Writer gate 之后的第二瓶颈是否继续下移或缩小
