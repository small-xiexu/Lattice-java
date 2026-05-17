# compile review LLM reviewer fail-closed 提交前质量复核报告

复核时间：2026-05-17
复核角色：agentD（验证/测试）
复核类型：提交前质量复核（只验证，不改代码）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1853
- ALLOWLIST：239

REVIEW +1（1852→1853）来自新增测试中 `FailingLlmClient` 类名被 redline 规则匹配，不涉及业务域、文档名、术语、问题文本或答案片段特判。其余 ALLOWLIST 均为既存工程常量候选。

## 2. Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：BUILD SUCCESS
- Tests run: 816, Failures: 0, Errors: 0, Skipped: 0

与独立验证基线 816/0/0 一致。

## 3. 本轮核心改动摘要

**生产代码（1 行）：**

`ArticleReviewerGateway.java` 第 135 行 `catch (RuntimeException)` 块：

```diff
- return ruleBasedArticleReviewer.review(articleContent, sourceContents);
+ return ReviewResult.timeoutFallback();
```

旧行为：LLM 调用抛异常 → 隐式回退到 rule-based review → 结构良好的文章可能 rule-based pass → 入库 → 可查询。这是 **fail-open**。

新行为：LLM 调用抛异常 → 返回 `ReviewResult.timeoutFallback()`（`isPass=false, issues=empty`）→ `ReviewDecisionPolicy` 将其分区为 `needs_human_review` → persist gate 阻止入库 → query visibility hard filter 确保不可查询。这是 **fail-closed**。

`review-enabled=false` 路径（第 79-84 行早返分支）未被触碰，行为不变。

**测试代码：**

`ArticleReviewerGatewayTests.java`：

| 变更 | 说明 |
|---|---|
| 重命名 `shouldFallbackToRuleBasedReviewerWhenRawInvocationFails` → `shouldUseRuleBasedReviewerWhenReviewDisabled` | 修正语义：review-enabled=false 时走 rule-based 不是 fallback，是主要路径 |
| 新增 `shouldFailClosedWhenRawInvocationFailsAndReviewEnabled` | review-enabled=true + LLM 异常 → `isPass()=false, status=TIMEOUT_FALLBACK` |
| 新增 `shouldFailClosedWhenRawResultCannotBeParsed` | review-enabled=true + 不可解析输出 → `isPass()=false, status=PARSE_FAILED` |

## 4. 是否修改配置

**否。**

未修改 `lattice-llm.yml`、`application*.yml`、`CompileReviewProperties`、`LlmProperties` 或任何配置类。`review-enabled` 默认值仍为 `false`。

## 5. 是否启用 LLM reviewer

**否。**

未通过 API、环境变量或数据库配置启用 LLM reviewer。`compile_review_settings` 表为空。未运行任何 compile job。

## 6. 是否运行 Compile / Baseline

**否。**

本轮未运行任何 compile、baseline、SWIP eval 或 query 回归。

## 7. 是否触碰 Persist Gate / Query Visibility / StateGraph / Prompt

**否。**

- **PersistArticlesNode**：未修改。
- **Query visibility mapper**：未修改（5 个 XML 文件无变更）。
- **StateGraph / CompileArticleNode / ReviewArticlesNode / FixReviewIssuesNode**：未修改。
- **ReviewDecisionPolicy**：未修改。
- **Prompt**：未修改。
- **后台 UI / Admin API**：未修改。

fail-closed 语义完全依赖现有 gate 链（ReviewDecisionPolicy → needs_human_review → PersistArticlesNode.retainPassedArticles → query hard filter），不需要新增任何 gate。

## 8. 允许提交文件清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `src/main/java/com/xbk/lattice/compiler/service/ArticleReviewerGateway.java` | 生产代码 | 1 行变更：catch 块 fail-closed |
| `src/test/java/com/xbk/lattice/compiler/service/ArticleReviewerGatewayTests.java` | 测试代码 | 重命名 1 case + 新增 2 case |
| `docs/quality-progress-and-lessons.md` | 台账 | 记录 fail-closed 收口 |
| `special_cases_report.md` | redline | 自动更新 |
| `compile_review_llm_reviewer_fail_closed_fix_result_report.md` | 报告 | fix 结果 |
| `compile_review_llm_reviewer_fail_closed_verification_report.md` | 报告 | 独立验证 |
| `compile_review_llm_reviewer_fail_closed_quality_progress_update_report.md` | 报告 | 台账更新记录 |
| `compile_review_llm_reviewer_fail_closed_pre_commit_quality_report.md` | 报告 | 本报告 |

## 9. 必须排除文件清单

| 文件 | 状态 | 排除原因 |
|---|---|---|
| `compile_review_llm_reviewer_loop_design_report.md` | 未跟踪 | LLM reviewer 闭环设计文档，属设计阶段产物，非本轮 fail-closed 修复范围 |
| `compile_review_llm_reviewer_enablement_readiness_report.md` | 未跟踪 | LLM reviewer 启用在即就绪度审查，属下一阶段文档 |
| `current_gate_snapshot_after_query_visibility.md` | 未跟踪 | 前序 query visibility 轮次门禁快照 |

## 10. 是否可以提交

**YES。**

全部检查通过：

| 检查项 | 结果 |
|---|---|
| redline BLOCKER=0 | 通过 |
| mvn test 816/0/0 | 通过 |
| 变更范围精准（1 行生产代码 + 测试） | 通过 |
| 未修改配置 | 通过 |
| 未启用 LLM reviewer | 通过 |
| 未运行 compile / baseline | 通过 |
| 未触碰 persist gate / query visibility / StateGraph / prompt | 通过 |
| 排除文件明确 | 通过 |

## 11. 建议 Commit Message

```
fix(compile): fail-closed on LLM reviewer exception

Replace rule-based fallback in ArticleReviewerGateway catch block
with ReviewResult.timeoutFallback() so that LLM exceptions and
parse failures do not silently pass through rule-based review.

When review-enabled=true and LLM invocation fails or returns
unparseable output, the article now enters needs_human_review
(not passed), which is blocked from persist and query by the
existing persist gate and query visibility hard filter.

When review-enabled=false, behavior is unchanged.

Tests: 816/0/0. ArticleReviewerGatewayTests extended with
fail-closed cases for exception and parse failure paths.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```
