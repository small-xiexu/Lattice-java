# rewrite_outcome_boundary_fix_result_report

## 1. 修改文件和方法

**唯一修改文件**：`src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackOutcomeSupport.java`

两处修改：

### 修改 1：`resolveFallbackAnswerOutcome` 的 PARTIAL_ANSWER 分支（line 211-217）

在 `return evidenceOutcome` 之前增加 `evidenceOutcome == NO_RELEVANT_KNOWLEDGE` 且非精密问题的 guard：

```java
if (evidenceOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE) {
    return AnswerOutcome.PARTIAL_ANSWER;
}
```

完整分支：

```java
if (preferredOutcome == AnswerOutcome.PARTIAL_ANSWER) {
    if (evidenceOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE
            && (looksLikeStrictExactIdentifierQuestion(question) || looksLikeRequiredFacetQuestion(question))) {
        return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
    }
    if (evidenceOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE) {
        return AnswerOutcome.PARTIAL_ANSWER;
    }
    return evidenceOutcome;
}
```

### 修改 2：`containsMultiFacetQuestionSignal`（line 400-405）

接入 `querySemanticRules.containsAnyMultiFocusSeparator`，使中文多焦点分隔信号（如 `"和"`）被识别：

```java
boolean containsMultiFacetQuestionSignal(String normalizedQuestion) {
    return normalizedQuestion.contains(",")
            || normalizedQuestion.contains("/")
            || normalizedQuestion.contains("&")
            || normalizedQuestion.contains("+")
            || querySemanticRules.containsAnyMultiFocusSeparator(normalizedQuestion);
}
```

## 2. 是否只修改 resolveFallbackAnswerOutcome

修改 1 在 `resolveFallbackAnswerOutcome` 的 PARTIAL_ANSWER 分支内（净增 3 行）。修改 2 在 `containsMultiFacetQuestionSignal`（净增 1 行），属于同文件内的副作用修复——原方法只检测 ASCII 标点，不识别中文 `"和"` 分隔符，导致垃圾证据的多焦点问题未被降级保护。该方法是 `resolveFallbackAnswerOutcome` 中 `looksLikeRequiredFacetQuestion` 的下游依赖，两处修改共同保证 outcome 边界正确。

## 3. 调用点检查结果

`buildEvidencePayload` 的 PARTIAL_ANSWER 调用点：

| 文件 | 行号 | 场景 | 影响评估 |
|---|---|---|---|
| `AnswerRewriteService.java` | 149 | rewrite fallback | 目标场景：修复后正确保留 PARTIAL_ANSWER |
| `AnswerGenerationPayloadOrchestrator.java` | 160 | 统一生成 fallback | 与目标语义一致，保留 PARTIAL_ANSWER |
| `AnswerGenerationPayloadOrchestrator.java` | 224 | 统一生成 fallback | 与目标语义一致，保留 PARTIAL_ANSWER |

`resolveFallbackAnswerOutcome` 的 null 调用点（`AnswerGenerationPayloadOrchestrator.java:200`）：走 `preferredOutcome == null` 路径，不受影响。

## 4. 目标测试是否通过

是。`shouldFallbackRewriteWhenPlainMarkdownOmitsCitations` 通过。

## 5. AnswerGenerationServiceTests 是否 0 failures

是。**65 run / 0 failures / 0 errors**。

## 6. 全量 mvn test 是否 0 failures / 0 errors

是。**811 run / 0 failures / 0 errors，BUILD SUCCESS**。

## 7. redline BLOCKER 是否仍为 0

是。**BLOCKER = 0**。

## 8. REVIEW / ALLOWLIST 是否变化

否。REVIEW = 1826、ALLOWLIST = 219，与修复前一致。

## 9. 是否修改测试

**否。** 未修改 `src/test/java/**` 任何文件。

## 10. 是否触碰 rewrite parser / evidence selector / snippet selector / citation cleanup

**否。** 未修改 AnswerRewriteService、AnswerFallbackEvidenceSelector、AnswerGenerationFallbackSnippetSelectionSupport、citation cleanup 等任何禁止修改范围的文件。
