# compile review LLM reviewer fail-closed 独立验证报告

验证时间：2026-05-17
验证角色：agentD（验证/测试）
验证类型：独立验证（只验证，不改代码）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1853
- ALLOWLIST：239

无新增 BLOCKER。REVIEW +1（1852→1853）来自新增测试用例中 `FailingLlmClient` 的类名匹配，不涉及业务域特判。

## 2. ArticleReviewerGatewayTests 结果

- 命令：`mvn -Dtest=ArticleReviewerGatewayTests test`
- 结果：BUILD SUCCESS
- Tests run: 5, Failures: 0, Errors: 0, Skipped: 0

5 个测试用例全部通过，覆盖：

| 测试 | 语义 | 断言 |
|---|---|---|
| `shouldUseRuleBasedReviewerWhenReviewDisabled` | review-enabled=false → rule-based | `isPass()=true, status=PASSED` |
| `shouldFailClosedWhenRawInvocationFailsAndReviewEnabled` | review-enabled=true + LLM 异常 | `isPass()=false, status=TIMEOUT_FALLBACK` |
| `shouldFailClosedWhenRawResultCannotBeParsed` | review-enabled=true + 解析失败 | `isPass()=false, status=PARSE_FAILED` |
| 原有 2 个 passed case | 正常 LLM approved 路径 | 保持通过 |

## 3. `*Reviewer*` 定向测试结果

- 命令：`mvn -Dtest='*Reviewer*' test`
- 结果：BUILD SUCCESS
- Tests run: 21, Failures: 0, Errors: 0, Skipped: 0

覆盖范围：
- `ArticleReviewerGatewayTests`：5（本轮修改）
- `LlmReviewerGatewayTests`：5（query 侧 reviewer，未修改）
- `ReviewerAgentTests`：3（agent role 路由，未修改）
- `FactCardReviewerTests`：8（fact card 审查，未修改）

所有 reviewer 相关测试均通过，横向无回归。

## 4. 全量 Maven Test

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：BUILD SUCCESS
- Tests run: 816, Failures: 0, Errors: 0, Skipped: 0

较前序基线 814 增加 2（ArticleReviewerGatewayTests 新增 2 个 fail-closed case）。全量无回归。

## 5. 是否只修改 ArticleReviewerGateway + 相关测试

**是。**

生产代码变更仅 1 行：

```diff
- return ruleBasedArticleReviewer.review(articleContent, sourceContents);
+ return ReviewResult.timeoutFallback();
```

位置：`ArticleReviewerGateway.java` 第 135 行 `catch(RuntimeException)` 块。

测试变更仅在 `ArticleReviewerGatewayTests.java`：
- 重命名 `shouldFallbackToRuleBasedReviewerWhenRawInvocationFails` → `shouldUseRuleBasedReviewerWhenReviewDisabled`（语义修正：review-enabled=false 时走 rule-based，不是 fallback）
- 新增 `shouldFailClosedWhenRawInvocationFailsAndReviewEnabled`
- 新增 `shouldFailClosedWhenRawResultCannotBeParsed`

未触碰任何其他文件。

## 6. 是否修改配置

**否。**

未修改 `lattice-llm.yml`、`application*.yml`、`CompileReviewProperties`、`LlmProperties` 或任何配置类。`review-enabled` 默认值仍为 `false`。

## 7. 是否启用 LLM reviewer

**否。**

本轮未运行任何 compile job。`compile_review_settings` 表为空。未通过 API 或环境变量启用 LLM reviewer。

## 8. review-enabled=false 行为是否保持 rule-based

**是。**

`ArticleReviewerGateway.review()` 在 `reviewEnabled=false` 时走早返分支（第 79-84 行），直接调用 `ruleBasedArticleReviewer.review()`，不经过 LLM 调用或 try-catch。测试 `shouldUseRuleBasedReviewerWhenReviewDisabled` 已覆盖并验证结果为 `PASSED`。

## 9. review-enabled=true + LLM exception 是否非 pass

**是。**

`catch(RuntimeException)` 现在返回 `ReviewResult.timeoutFallback()`（`isPass=false`，`issues=empty`）。测试 `shouldFailClosedWhenRawInvocationFailsAndReviewEnabled` 已覆盖并断言 `isPass()=false, status=TIMEOUT_FALLBACK`。

## 10. review-enabled=true + parse failure 是否非 pass

**是。**

`ReviewResultParser` 解析失败返回 `ReviewResult.parseFailed()`（`isPass=false`，`issues=empty`），该路径在 `try` 块内正常返回，不与 catch 块混淆。测试 `shouldFailClosedWhenRawResultCannotBeParsed` 已覆盖并断言 `isPass()=false, status=PARSE_FAILED`。

## 11. TIMEOUT_FALLBACK / PARSE_FAILED 后续路由

根据 `ReviewDecisionPolicy.partition()` 源码逐行追踪：

| 步骤 | 条件 | TIMEOUT_FALLBACK | PARSE_FAILED |
|---|---|---|---|
| `isPass()` | `reviewResult.isPass()` | **false** | **false** |
| → accepted? | isPass=true | **否** | **否** |
| `hasIssues()` | issues != null && !isEmpty | **否**（issues=empty list） | **否**（issues=empty list） |
| → fixable? | autoFixEnabled && fixAttemptCount < maxFixRounds && hasIssues | **否** | **否** |
| → 最终 | 默认分支 | **needs_human_review** | **needs_human_review** |

**结论：TIMEOUT_FALLBACK 和 PARSE_FAILED 均进入 `needs_human_review`，不进入 fixer。**

不进入 fixer 的原因：两个状态都返回空 issues 列表（`List.of()`），`hasIssues()` 返回 false，无法传给 fixer 修复。这是正确的——LLM 异常和不可解析输出没有可修复的 issue，强行 fix 没有意义。

## 12. 是否触碰 persist gate / query visibility / StateGraph / prompt

**否。**

- **PersistArticlesNode**：未修改。`needs_human_review` 文章会被 `retainPassedArticles()` 过滤，不入库。
- **Query visibility mapper**：未修改。即使文章入库，`review_status='passed' AND lifecycle='ACTIVE'` hard filter 也阻止查询。
- **StateGraph / CompileArticleNode / ReviewArticlesNode / FixReviewIssuesNode**：未修改。
- **Prompt**：未修改。
- **ReviewDecisionPolicy**：未修改。`partition()` 逻辑未变，只是输入从 rule-based pass 变成了 `timeoutFallback()`。

fail-closed 语义完全依赖现有 persist gate 和 query visibility gate，不需要新增 gate。

## 13. 是否可以进入质量台账更新与 pre-commit 复核

**YES。**

全部检查通过：

| 检查项 | 结果 |
|---|---|
| redline BLOCKER=0 | 通过 |
| ArticleReviewerGatewayTests 5/0/0 | 通过 |
| `*Reviewer*` 定向测试 21/0/0 | 通过 |
| 全量 mvn test 816/0/0 | 通过 |
| 只修改 ArticleReviewerGateway + 测试 | 通过（1 行生产代码 + 测试重命名 + 2 新 case） |
| 未修改配置 | 通过 |
| 未启用 LLM reviewer | 通过 |
| review-enabled=false 保持 rule-based | 通过 |
| LLM exception → 非 pass | 通过 |
| parse failure → 非 pass | 通过 |
| 未触碰 persist gate / query visibility / StateGraph / prompt | 通过 |

## 14. 独立评估

本轮修复是纯 fail-closed 语义修正，风险极低，理由：

1. **变更面积极小**：生产代码仅 1 行（`return ruleBasedArticleReviewer.review(...)` → `return ReviewResult.timeoutFallback()`），不涉及任何配置、流程、gate 或 prompt 改动。
2. **fail-closed 路径已有下游 gate 兜底**：`TIMEOUT_FALLBACK` 和 `PARSE_FAILED` 都进入 `needs_human_review`，而 persist gate 和 query visibility hard filter 已经就位，确保非 passed 文章不入库、不可查询。本轮修复只是让异常不再绕过这两道 gate。
3. **review-enabled=false 路径未触碰**：当前生产仍为 rule-based 审查，行为完全不变。
4. **将来启用 LLM reviewer 时自动受保护**：一旦 `review-enabled=true`，LLM 异常和不可解析输出不会静默 rule-based pass——这是 fail-closed 语义的唯一起效场景。
