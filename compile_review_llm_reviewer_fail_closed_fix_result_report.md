# Compile Review LLM Reviewer Fail-Closed 修复结果报告

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/service/ArticleReviewerGateway.java`
  - 修改方法：`review(String articleContent, String sourceContents, String scopeId, String scene, String agentRole)`
  - 变更点：`review-enabled=true` 且 LLM 调用或解析链路抛出 `RuntimeException` 时，不再回退到 `RuleBasedArticleReviewer`，改为返回现有非 pass 结果 `ReviewResult.timeoutFallback()`。
- `src/test/java/com/xbk/lattice/compiler/service/ArticleReviewerGatewayTests.java`
  - 调整原有 raw 调用失败回退规则审查的测试。
  - 新增/补强：
    - `shouldUseRuleBasedReviewerWhenReviewDisabled`
    - `shouldFailClosedWhenRawInvocationFailsAndReviewEnabled`
    - `shouldFailClosedWhenRawResultCannotBeParsed`

## 2. review-enabled=false 时 rule-based 行为是否保持

是。

测试 `shouldUseRuleBasedReviewerWhenReviewDisabled` 覆盖了 `review-enabled=false` 且 LLM client 会抛异常的场景，结果仍由 `RuleBasedArticleReviewer` 产生，断言为：

- `isPass=true`
- `ReviewStatus.PASSED`

## 3. review-enabled=true + LLM approved 是否仍可 pass

是。

既有测试 `shouldWritePromptCacheWhenCompileReviewPayloadAllowsCaching` 继续覆盖 LLM 返回 approved JSON 的路径，结果仍为：

- `isPass=true`
- `ReviewStatus.PASSED`

本轮没有修改 approved JSON 解析、prompt cache write policy、prompt 模板或 LLM reviewer 配置。

## 4. review-enabled=true + LLM exception / parse failure 是否变为非 pass

是。

- LLM raw 调用异常：`shouldFailClosedWhenRawInvocationFailsAndReviewEnabled` 覆盖，返回：
  - `isPass=false`
  - `ReviewStatus.TIMEOUT_FALLBACK`
- LLM 返回不可解析内容：`shouldFailClosedWhenRawResultCannotBeParsed` 覆盖，返回：
  - `isPass=false`
  - `ReviewStatus.PARSE_FAILED`

因此启用 reviewer 后，LLM 调用异常、超时类运行时失败、JSON 解析失败或不可用输出不会再静默回退成 rule-based pass。

## 5. 非 pass 后续理论上会进入 fix 还是 needs_human_review

按现有 `ReviewDecisionPolicy`：

- `ReviewResult.timeoutFallback()` 没有 issues，属于非 pass，理论上不会进入 fixer，后续进入 `needs_human_review`。
- `ReviewResult.parseFailed()` 没有 issues，属于非 pass，理论上不会进入 fixer，后续进入 `needs_human_review`。
- 若 LLM 成功返回结构化 issues，仍走现有 `ISSUES_FOUND` 路径；在 auto fix 开启且轮次允许时可进入 fixer。本轮未修改该路径。

## 6. redline BLOCKER 是否仍为 0

是。

最终执行：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

- `BLOCKER=0`
- `REVIEW=1853`
- `ALLOWLIST=239`

## 7. mvn test 是否通过

是。

定向 reviewer 测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest='*Reviewer*' test
```

结果：

- `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

全量测试：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

- `Tests run: 816, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

## 8. 是否修改配置

否。

未修改：

- `src/main/resources/config/lattice-llm.yml`
- `application.yml`
- `application*.yml`

## 9. 是否启用 LLM reviewer

否。

本轮只修 fail-closed 行为，没有开启 LLM reviewer。

## 10. 是否触碰 persist gate / query visibility / StateGraph / prompt

否。

未修改：

- `PersistArticlesNode`
- query visibility mapper
- StateGraph / CompileArticleNode 流程
- prompt 模板
- review status enum / DB schema
- 后台 UI
- Query / AnswerGeneration / Citation / Deep Research

## 11. 是否能用现有 ReviewResult 表达 fail-closed

可以。

本轮使用现有 `ReviewResult.timeoutFallback()` 表达 LLM 调用异常、超时类运行时失败或解析链路异常后的 fail-closed 结果；使用现有 `ReviewResult.parseFailed()` 表达 LLM 返回不可解析 / 不可用内容的非 pass 结果。无需扩大到 schema、enum、StateGraph 或 prompt 修改。

