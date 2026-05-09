package com.xbk.lattice.query.service;

import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 答案生成精确查值 grounding 支持
 *
 * 职责：校验精确查值答案对路径、数值、结构化维度与变更语义的覆盖
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationExactLookupGroundingSupport extends AnswerGenerationPromptEvidenceSupport {

    /**
     * 创建无 LLM 的拆分支持。
     */
    AnswerGenerationExactLookupGroundingSupport() {
        super();
    }

    /**
     * 创建拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationExactLookupGroundingSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    boolean coversRequiredPathShape(String question, String normalizedAnswer, List<String> focusSnippets) {
        List<String> requestedPaths = extractRequestedPathIdentifiers(question);
        if (!requestedPaths.isEmpty()) {
            return coversRequestedPaths(normalizedAnswer, requestedPaths);
        }
        List<String> evidencePaths = extractEvidencePaths(focusSnippets);
        if (evidencePaths.isEmpty()) {
            return normalizedAnswer.contains("/");
        }
        int requiredPathCount = requiredPathCoverageCount(question, focusSnippets, evidencePaths);
        int coveredPathCount = 0;
        for (String evidencePath : evidencePaths) {
            if (normalizedAnswer.contains(lowerCase(evidencePath))) {
                coveredPathCount++;
                if (coveredPathCount >= requiredPathCount) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断答案是否覆盖用户问题中显式点名的路径。
     *
     * @param normalizedAnswer 归一化答案
     * @param requestedPaths 用户点名路径
     * @return 覆盖返回 true
     */
    boolean coversRequestedPaths(String normalizedAnswer, List<String> requestedPaths) {
        if (requestedPaths == null || requestedPaths.isEmpty()) {
            return true;
        }
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return false;
        }
        for (String requestedPath : requestedPaths) {
            if (!normalizedAnswer.contains(lowerCase(requestedPath))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断多维查值题答案是否至少覆盖了两种证据维度。
     *
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖足够返回 true
     */
    boolean coversMultipleEvidenceDimensions(
            String question,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        int evidenceDimensionCount = countEvidenceDimensions(focusSnippets);
        if (evidenceDimensionCount < 2) {
            return true;
        }
        int answerDimensionCount = countCoveredAnswerDimensions(question, normalizedAnswer, focusSnippets);
        return answerDimensionCount >= evidenceDimensionCount;
    }

    /**
     * 统计贴题证据里包含了多少种结构化维度。
     *
     * @param focusSnippets 贴题证据句
     * @return 维度数
     */
    int countEvidenceDimensions(List<String> focusSnippets) {
        int dimensionCount = 0;
        if (containsAnyPathSignal(focusSnippets)) {
            dimensionCount++;
        }
        if (extractStructuredLabels(focusSnippets).size() >= 2) {
            dimensionCount++;
        }
        if (containsAnyBatchOrOrdinalSignal(focusSnippets)) {
            dimensionCount++;
        }
        if (containsAnyChangeTrackingSignal(focusSnippets)) {
            dimensionCount++;
        }
        return dimensionCount;
    }

    /**
     * 统计答案覆盖了多少种证据维度。
     *
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖维度数
     */
    int countCoveredAnswerDimensions(
            String question,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        int coveredCount = 0;
        if (containsAnyPathSignal(focusSnippets)
                && coversRequiredPathShape(question, normalizedAnswer, focusSnippets)) {
            coveredCount++;
        }
        List<String> structuredLabels = extractStructuredLabels(focusSnippets);
        if (structuredLabels.size() >= 2
                && countCoveredStructuredLabels(normalizedAnswer, structuredLabels)
                >= Math.min(2, structuredLabels.size())) {
            coveredCount++;
        }
        if (containsAnyBatchOrOrdinalSignal(focusSnippets) && containsBatchOrOrdinalSignal(normalizedAnswer)) {
            coveredCount++;
        }
        if (containsAnyChangeTrackingSignal(focusSnippets) && containsChangeTrackingSignal(normalizedAnswer)) {
            coveredCount++;
        }
        return coveredCount;
    }

    /**
     * 从证据中提取接口或 URL path。
     *
     * @param snippets 证据句
     * @return 路径列表
     */

    /**
     * 删除显式路径契约题答案中未被用户点名的反例路径，避免把证据里的旁路示例扩写进最终结论。
     *
     * @param answerMarkdown 答案 Markdown
     * @param question 用户问题
     * @return 清理后的答案 Markdown
     */
    String removeUnrequestedPathExamples(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank() || !requiresPathContractCompanion(question)) {
            return answerMarkdown;
        }
        List<String> requestedPaths = extractRequestedPathIdentifiers(question);
        if (requestedPaths.isEmpty()) {
            return answerMarkdown;
        }
        List<String> answerPaths = extractEvidencePaths(List.of(stripEmbeddedCitationLiterals(answerMarkdown)));
        if (answerPaths.isEmpty()) {
            return answerMarkdown;
        }
        String cleanedAnswer = answerMarkdown;
        for (String answerPath : answerPaths) {
            if (!containsIdentifierIgnoreCase(requestedPaths, answerPath)) {
                cleanedAnswer = removeUnrequestedPathClause(cleanedAnswer, answerPath);
            }
        }
        String normalizedCleanedAnswer = normalizeAfterUnrequestedPathRemoval(cleanedAnswer);
        if (normalizedCleanedAnswer.isBlank()) {
            return answerMarkdown;
        }
        String normalizedAnswer = lowerCase(stripEmbeddedCitationLiterals(normalizedCleanedAnswer));
        if (!coversRequestedPaths(normalizedAnswer, requestedPaths)) {
            return answerMarkdown;
        }
        if (!coversRequestedPathContractAnswer(question, normalizedAnswer, List.of(answerMarkdown))) {
            return answerMarkdown;
        }
        return normalizedCleanedAnswer;
    }

    /**
     * 删除包含未点名 path 的否定或示例从句。
     *
     * @param answerMarkdown 答案 Markdown
     * @param unrequestedPath 未点名 path
     * @return 删除后的答案 Markdown
     */
    String removeUnrequestedPathClause(String answerMarkdown, String unrequestedPath) {
        if (answerMarkdown == null || answerMarkdown.isBlank()
                || unrequestedPath == null || unrequestedPath.isBlank()) {
            return answerMarkdown;
        }
        String quotedPath = "`?" + Pattern.quote(unrequestedPath) + "`?";
        String cleanedAnswer = answerMarkdown;
        cleanedAnswer = cleanedAnswer.replaceAll(
                "，?(?:不得|不要|不能|不应|不宜|不建议)[^。；\\n]*" + quotedPath + "[^。；\\n]*(?=[。；\\n])",
                ""
        );
        cleanedAnswer = cleanedAnswer.replaceAll(
                "，?(?:废弃|作废|反例|示例)[^。；\\n]*" + quotedPath + "[^。；\\n]*(?=[。；\\n])",
                ""
        );
        return cleanedAnswer;
    }

    /**
     * 清理删除未点名 path 从句后留下的重复标点和空白。
     *
     * @param answerMarkdown 答案 Markdown
     * @return 归一化答案
     */
    String normalizeAfterUnrequestedPathRemoval(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return "";
        }
        return answerMarkdown
                .replaceAll("；\\s*；+", "；")
                .replaceAll("，\\s*，+", "，")
                .replaceAll("；\\s*。", "。")
                .replaceAll("，\\s*。", "。")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * 计算路径题至少需要覆盖多少个接口路径。
     *
     * @param question 用户问题
     * @param focusSnippets 贴题证据句
     * @param evidencePaths 证据路径
     * @return 需要覆盖的路径数
     */
    int requiredPathCoverageCount(
            String question,
            List<String> focusSnippets,
            List<String> evidencePaths
    ) {
        if (evidencePaths == null || evidencePaths.isEmpty()) {
            return 0;
        }
        int labelCount = extractStructuredLabels(focusSnippets).size();
        if (looksLikeCompoundExactLookupQuestion(question) && labelCount >= 2) {
            return Math.min(labelCount, evidencePaths.size());
        }
        return 1;
    }

    /**
     * 根据问题语义抽取真正需要答案覆盖的证据数值。
     *
     * @param normalizedQuestion 归一化问题
     * @param focusSnippets 贴题证据句
     * @return 数值列表
     */
    List<String> extractRequiredEvidenceNumbers(String normalizedQuestion, List<String> focusSnippets) {
        if (normalizedQuestion != null && normalizedQuestion.contains("命中数")) {
            List<String> countNumbers = extractNumbersFromSignalSnippets(focusSnippets, List.of("命中", "条", "count"));
            if (!countNumbers.isEmpty()) {
                return countNumbers;
            }
        }
        return extractRepresentativeNumbers(focusSnippets);
    }

    /**
     * 从包含指定语义信号的证据句中抽取数字。
     *
     * @param snippets 证据句
     * @param signals 语义信号
     * @return 数值列表
     */
    List<String> extractNumbersFromSignalSnippets(List<String> snippets, List<String> signals) {
        List<String> numbers = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return numbers;
        }
        for (String snippet : snippets) {
            String normalizedSnippet = lowerCase(snippet);
            boolean matchedSignal = false;
            for (String signal : signals) {
                if (normalizedSnippet.contains(lowerCase(signal))) {
                    matchedSignal = true;
                    break;
                }
            }
            if (!matchedSignal) {
                continue;
            }
            for (String number : extractRepresentativeNumbers(List.of(snippet))) {
                if (!numbers.contains(number)) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    /**
     * 统计答案覆盖了多少个证据数值。
     *
     * @param normalizedAnswer 归一化答案
     * @param evidenceNumbers 证据数值
     * @return 覆盖数量
     */
    int countCoveredNumbers(String normalizedAnswer, List<String> evidenceNumbers) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank() || evidenceNumbers == null || evidenceNumbers.isEmpty()) {
            return 0;
        }
        String compactAnswer = normalizedAnswer.replace(",", "");
        int coveredCount = 0;
        for (String evidenceNumber : evidenceNumbers) {
            String compactNumber = evidenceNumber.replace(",", "");
            if (!compactNumber.isBlank() && compactAnswer.contains(compactNumber)) {
                coveredCount++;
            }
        }
        return coveredCount;
    }

    /**
     * 判断若干贴题证据句里是否出现强限制语义。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyStrongConstraintSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsStrongConstraintSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现接口/URL path。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyPathSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsPathSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现变更语义。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyChangeTrackingSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsChangeTrackingSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现规则/约束语义。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyRuleConstraintSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsRuleConstraintSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断变更类问题的答案是否覆盖了关键变更语义和问题锚点。
     *
     * @param question 用户问题
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖返回 true
     */
    boolean coversChangeTrackingAnswer(String question, String normalizedAnswer, List<String> focusSnippets) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return false;
        }
        if (coversRequestedPathContractAnswer(question, normalizedAnswer, focusSnippets)) {
            return true;
        }
        if (!containsChangeTrackingSignal(normalizedAnswer)) {
            return false;
        }
        if (containsAnyAssignmentLikeMappingSignal(focusSnippets)
                && !containsAssignmentLikeMappingSignal(normalizedAnswer)) {
            return false;
        }
        List<String> reusableAnchors = extractReusableQuestionAnchors(question);
        if (reusableAnchors.isEmpty()) {
            return true;
        }
        String normalizedAnswerWithoutCitation = lowerCase(stripEmbeddedCitationLiterals(normalizedAnswer));
        return countMatchedReusableAnchors(normalizedAnswerWithoutCitation, reusableAnchors) >= 1;
    }

    /**
     * 判断显式 path 契约题是否已覆盖用户真正询问的 path 与可变更性。
     *
     * @param question 用户问题
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖返回 true
     */
    boolean coversRequestedPathContractAnswer(
            String question,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        return requiresPathContractCompanion(question)
                && coversRequiredPathShape(question, normalizedAnswer, focusSnippets)
                && (containsPathContractSignal(normalizedAnswer) || containsStrongConstraintSignal(normalizedAnswer));
    }

    /**
     * 判断若干贴题证据句里是否出现映射/重排信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyAssignmentLikeMappingSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsAssignmentLikeMappingSignal(snippet)) {
                return true;
            }
        }
        return false;
    }
}
