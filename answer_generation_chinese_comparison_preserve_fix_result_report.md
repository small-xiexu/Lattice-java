# answer_generation_chinese_comparison_preserve_fix_result_report

## 1. 修改了哪些文件

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationQuestionTypeBasicSupport.java`
  - 仅修改 `looksLikeComparisonQuestion` 方法，新增一行 `|| querySemanticRules.containsAnyComparisonSignal(question);`

## 2. 是否使用配置化 comparison signals

是。通过 `querySemanticRules.containsAnyComparisonSignal(question)` 接入已在 `lattice-query-semantic.yml` 中定义好的 `comparison-signals`（包含"比较""对比""区别""差异""有何不同""有什么不同"），无需新增 Java 硬编码。

## 3. 是否新增 Java 主链中文 contains 特判

否。未在 Java 主链新增任何 `contains("差异")` / `contains("区别")` / `contains("对比")` 等中文硬编码。

## 4. 目标测试是否通过

是。`shouldKeepUnsupportedDetailCaveatInAnsweredDiffQuestion` 通过。

## 5. AnswerGenerationServiceTests 是否从 2 failures 降到 1 failure

是。修复前：65 run / 2 failures / 0 errors；修复后：65 run / 1 failure / 0 errors。只剩 `shouldFallbackRewriteWhenPlainMarkdownOmitsCitations` 一个失败。

## 6. 全量 mvn test 是否从 2 failures 降到 1 failure

是。修复前：811 run / 2 failures / 0 errors；修复后：811 run / 1 failure / 0 errors。

## 7. redline BLOCKER 是否仍为 0

是。BLOCKER = 0。

## 8. REVIEW 是否变化

否。REVIEW = 1826、ALLOWLIST = 219，与修复前一致。

## 9. 是否修改测试

否。`src/test/java/**` 未做任何修改。

## 10. 是否触碰 fallback outcome / rewrite / evidence selector / snippet selector

否。未修改 AnswerGenerationFallbackOutcomeSupport、rewriteFromReviewPayload、evidence selector、snippet selector、citation cleanup。
