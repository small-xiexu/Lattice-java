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
 * 答案生成 fallback 片段覆盖选择支持
 *
 * 职责：为结构化 fallback 片段补足多维证据形态并计算贴题候选分值
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationFallbackSnippetSelectionSupport extends AnswerGenerationReferencePathSupport {

    /**
     * 创建无 LLM 的 fallback 片段选择支持。
     */
    AnswerGenerationFallbackSnippetSelectionSupport() {
        super();
    }

    /**
     * 创建 fallback 片段选择支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationFallbackSnippetSelectionSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    List<String> selectCoverageAwareStructuredFactSnippets(
            String question,
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> focusTokens,
            int limit
    ) {
        List<String> snippets = new ArrayList<String>();
        List<String> selectedCandidates = new ArrayList<String>();
        List<String> coveredFocusTokens = new ArrayList<String>();
        if (focusTokens != null) {
            for (String focusToken : focusTokens) {
                if (coveredFocusTokens.contains(focusToken)) {
                    continue;
                }
                String matchedCandidate = selectBestRankedCandidateMatchingFocusToken(
                        rankedCandidates,
                        focusToken,
                        selectedCandidates
                );
                if (matchedCandidate.isBlank()) {
                    continue;
                }
                selectedCandidates.add(matchedCandidate);
                coveredFocusTokens.add(focusToken);
                snippets.add(stripEmbeddedCitationLiterals(matchedCandidate));
                if (snippets.size() >= limit) {
                    return snippets;
                }
            }
        }
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "number");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "status");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "ordinal");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "path");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "rule");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "change");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "flow");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "identifier");
        addDistinctMachineIdentifierCandidates(question, rankedCandidates, selectedCandidates, snippets, limit);
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            if (selectedCandidates.contains(rankedCandidate.getKey())) {
                continue;
            }
            if (matchesAnyFocusToken(rankedCandidate.getKey(), coveredFocusTokens)) {
                continue;
            }
            snippets.add(stripEmbeddedCitationLiterals(rankedCandidate.getKey()));
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets;
    }

    /**
     * 为多事实题补足不同机器标识符，避免同一标识的候选句占满答案位。
     *
     * @param question 用户问题
     * @param rankedCandidates 已排序候选句
     * @param selectedCandidates 已选候选
     * @param snippets 输出片段
     * @param limit 最大条数
     */
    void addDistinctMachineIdentifierCandidates(
            String question,
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> selectedCandidates,
            List<String> snippets,
            int limit
    ) {
        if (!shouldCollectDistinctMachineIdentifiers(question) || snippets.size() >= limit) {
            return;
        }
        Set<String> coveredIdentifiers = new LinkedHashSet<String>();
        for (String selectedCandidate : selectedCandidates) {
            coveredIdentifiers.addAll(extractMachineIdentifiers(selectedCandidate));
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            String candidate = rankedCandidate.getKey();
            if (selectedCandidates.contains(candidate)
                    || !containsMachineIdentifierSignal(candidate)) {
                continue;
            }
            List<String> identifiers = extractMachineIdentifiers(candidate);
            if (identifiers.isEmpty() || coveredIdentifiers.containsAll(identifiers)) {
                continue;
            }
            selectedCandidates.add(candidate);
            coveredIdentifiers.addAll(identifiers);
            snippets.add(stripEmbeddedCitationLiterals(candidate));
            if (snippets.size() >= limit) {
                return;
            }
        }
    }

    /**
     * 判断当前问题是否适合补足多个机器标识符候选。
     *
     * @param question 用户问题
     * @return 适合返回 true
     */
    boolean shouldCollectDistinctMachineIdentifiers(String question) {
        String normalizedQuestion = lowerCase(question);
        return containsMachineIdentifierSignal(question)
                || normalizedQuestion.contains("标识")
                || normalizedQuestion.contains("名称")
                || normalizedQuestion.contains("编号")
                || normalizedQuestion.contains("编码")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("队列")
                || normalizedQuestion.contains("主题")
                || normalizedQuestion.contains("key")
                || normalizedQuestion.contains("id")
                || normalizedQuestion.contains("url")
                || normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("topic")
                || normalizedQuestion.contains("queue");
    }

    /**
     * 为结构化查值题补足数值、状态、批次等不同证据形态。
     *
     * @param question 用户问题
     * @param rankedCandidates 已排序候选句
     * @param selectedCandidates 已选候选
     * @param snippets 输出片段
     * @param limit 最大条数
     * @param shape 需要补足的证据形态
     */
    void addBestCandidateForRequiredShape(
            String question,
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> selectedCandidates,
            List<String> snippets,
            int limit,
            String shape
    ) {
        if (snippets.size() >= limit || !requiresStructuredEvidenceShape(question, shape)) {
            return;
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            String candidate = rankedCandidate.getKey();
            if (selectedCandidates.contains(candidate) || !matchesStructuredEvidenceShape(candidate, shape)) {
                continue;
            }
            selectedCandidates.add(candidate);
            snippets.add(stripEmbeddedCitationLiterals(candidate));
            return;
        }
    }

    /**
     * 判断 fallback 摘句是否需要覆盖多种问题维度。
     *
     * @param question 用户问题
     * @return 需要覆盖多维度返回 true
     */
    boolean shouldUseCoverageAwareFallbackSnippets(String question) {
        return looksLikeStructuredFactQuestion(question)
                || looksLikeRuleConstraintQuestion(question)
                || requiresPathContractCompanion(question)
                || looksLikeChangeTrackingQuestion(question)
                || expectsBatchOrOrdinalAnswer(lowerCase(question));
    }

    /**
     * 判断问题是否要求指定证据形态。
     *
     * @param question 用户问题
     * @param shape 证据形态
     * @return 要求返回 true
     */
    boolean requiresStructuredEvidenceShape(String question, String shape) {
        String normalizedQuestion = lowerCase(question);
        if ("number".equals(shape)) {
            return looksLikeNumericQuestion(question);
        }
        if ("status".equals(shape)) {
            return normalizedQuestion.contains("结论")
                    || normalizedQuestion.contains("流量")
                    || normalizedQuestion.contains("调整")
                    || normalizedQuestion.contains("修正")
                    || normalizedQuestion.contains("降级")
                    || looksLikeStatusQuestion(question);
        }
        if ("ordinal".equals(shape)) {
            return expectsBatchOrOrdinalAnswer(normalizedQuestion);
        }
        if ("path".equals(shape)) {
            return looksLikePathQuestion(question);
        }
        if ("rule".equals(shape)) {
            return looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question);
        }
        if ("change".equals(shape)) {
            return looksLikeChangeTrackingQuestion(question);
        }
        if ("flow".equals(shape)) {
            return looksLikeFlowQuestion(question);
        }
        if ("identifier".equals(shape)) {
            return shouldCollectDistinctMachineIdentifiers(question);
        }
        return false;
    }

    /**
     * 判断候选句是否匹配指定证据形态。
     *
     * @param candidate 候选句
     * @param shape 证据形态
     * @return 匹配返回 true
     */
    boolean matchesStructuredEvidenceShape(String candidate, String shape) {
        if ("number".equals(shape)) {
            return candidate != null && candidate.matches("(?s).*\\d.*");
        }
        if ("status".equals(shape)) {
            return containsCorrectionOrStatusSignal(lowerCase(candidate)) || containsStatusSignal(lowerCase(candidate));
        }
        if ("ordinal".equals(shape)) {
            return containsBatchOrOrdinalSignal(candidate);
        }
        if ("path".equals(shape)) {
            return containsPathSignal(candidate);
        }
        if ("rule".equals(shape)) {
            return containsRuleConstraintSignal(candidate)
                    || containsStrongConstraintSignal(candidate)
                    || containsPathContractSignal(candidate);
        }
        if ("change".equals(shape)) {
            return containsChangeTrackingSignal(candidate);
        }
        if ("flow".equals(shape)) {
            return containsFlowTransitionSignal(candidate);
        }
        if ("identifier".equals(shape)) {
            return containsMachineIdentifierSignal(candidate);
        }
        return false;
    }

    /**
     * 判断候选句是否已命中过已覆盖的问题焦点。
     *
     * @param candidate 候选句
     * @param focusTokens 已覆盖焦点
     * @return 命中返回 true
     */
    boolean matchesAnyFocusToken(String candidate, List<String> focusTokens) {
        if (candidate == null || candidate.isBlank() || focusTokens == null || focusTokens.isEmpty()) {
            return false;
        }
        String normalizedCandidate = lowerCase(candidate);
        for (String focusToken : focusTokens) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (!normalizedFocusToken.isBlank() && normalizedCandidate.contains(normalizedFocusToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按焦点 token 从已排序候选句里挑选最先出现的命中项。
     *
     * @param rankedCandidates 已排序候选句
     * @param focusToken 问题焦点
     * @param selectedCandidates 已选候选句
     * @return 命中的候选句；没有则返回空串
     */
    String selectBestRankedCandidateMatchingFocusToken(
            List<Map.Entry<String, Integer>> rankedCandidates,
            String focusToken,
            List<String> selectedCandidates
    ) {
        if (rankedCandidates == null || rankedCandidates.isEmpty()) {
            return "";
        }
        String normalizedFocusToken = lowerCase(focusToken);
        if (normalizedFocusToken.isBlank()) {
            return "";
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            String candidate = rankedCandidate.getKey();
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (selectedCandidates != null && selectedCandidates.contains(candidate)) {
                continue;
            }
            if (lowerCase(candidate).contains(normalizedFocusToken)) {
                return candidate;
            }
        }
        return "";
    }

    /**
     * 合并候选句分值，保留更高分版本。
     *
     * @param candidateScores 候选分值映射
     * @param candidate 候选句
     * @param score 分值
     */
    void mergeCandidateScore(Map<String, Integer> candidateScores, String candidate, int score) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        Integer existingScore = candidateScores.get(candidate);
        if (existingScore == null || score > existingScore.intValue()) {
            candidateScores.put(candidate, Integer.valueOf(score));
        }
    }

    /**
     * 为贴题证据句增加“配置键 = 值”“当前阈值”等问题导向加权。
     *
     * @param question 用户问题
     * @param rawLine 原始候选行
     * @param normalizedLine 归一化后的候选行
     * @param preferredTokens 查询 token
     * @return 候选分值
     */
    int scoreQuestionFocusedFallbackLine(
            String question,
            String rawLine,
            String normalizedLine,
            List<String> preferredTokens
    ) {
        int score = scoreFallbackLineCandidate(rawLine, normalizedLine, preferredTokens);
        if (looksLikeStructuredFactCandidate(question, normalizedLine)) {
            score += 12;
        }
        if (looksLikeQuestionEchoLine(question, normalizedLine)) {
            score -= 60;
        }
        if (answerEvidenceNormalizer.looksLikeTableOfContentsLine(normalizedLine)) {
            score -= 80;
        }
        if (looksLikeEnumerationQuestion(question) && looksLikeEnumerationFactLine(rawLine, normalizedLine)) {
            score += 32;
        }
        if (looksLikeNumericQuestion(question) && containsCountConclusionSignal(normalizedLine)) {
            score += 28;
        }
        if (looksLikeNumericQuestion(question) && containsNumericAssignmentSignal(normalizedLine)) {
            score += 22;
        }
        if (looksLikeRuleConstraintQuestion(question) && containsRuleConstraintSignal(normalizedLine)) {
            score += 42;
        }
        if (looksLikeRuleConstraintQuestion(question) && containsStrongConstraintSignal(normalizedLine)) {
            score += 28;
        }
        if (looksLikeChangeTrackingQuestion(question) && containsChangeTrackingSignal(normalizedLine)) {
            score += 40;
        }
        if (looksLikeChangeTrackingQuestion(question) && containsAssignmentLikeMappingSignal(normalizedLine)) {
            score += 26;
        }
        if (requiresPathContractCompanion(question) && containsPathContractSignal(normalizedLine)) {
            score += 56;
            if (containsStrongConstraintSignal(normalizedLine)) {
                score += 20;
            }
            if (containsRuleConstraintSignal(normalizedLine)) {
                score += 12;
            }
        }
        if (looksLikeNumericQuestion(question) && looksLikeAdjacentEnumerationNoise(normalizedLine, question)) {
            score -= 18;
        }
        if (looksLikePathQuestion(question) && containsPathSignal(normalizedLine)) {
            score += 40;
        }
        if (looksLikePathQuestion(question) && containsStructuredLabelSignal(normalizedLine)) {
            score += 18;
        }
        if (looksLikePathQuestion(question) && looksLikePathHeaderLine(normalizedLine)) {
            score -= 36;
        }
        if (looksLikeComparisonQuestion(question) && containsComparisonSignal(normalizedLine)) {
            score += 34;
        }
        if (looksLikeComparisonQuestion(question)
                && containsMultipleHighSignalQuestionTokens(question, normalizedLine)) {
            score += 18;
        }
        if (looksLikeCompoundExactLookupQuestion(question)
                && containsPathSignal(normalizedLine)
                && containsStructuredLabelSignal(normalizedLine)) {
            score += 24;
        }
        if (containsRequestedExactIdentifier(normalizedLine, question)) {
            score += 80;
        }
        if (answerEvidenceNormalizer.startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score += 8;
        }
        if (looksLikeNumericQuestion(question) && normalizedLine.matches(".*\\d.*")) {
            score += 6;
        }
        String normalizedQuestion = lowerCase(question);
        String lowerCaseLine = lowerCase(normalizedLine);
        if (looksLikeCurrentFactQuestion(normalizedQuestion) && containsCurrentFactSignal(lowerCaseLine)) {
            score += 20;
        }
        if (looksLikeSetupChecklistQuestion(question) && containsSetupSignal(normalizedLine)) {
            score += 24;
        }
        if (looksLikeCapabilityQuestion(question) && containsCapabilitySignal(normalizedLine)) {
            score += 50;
        }
        if (looksLikeFlowQuestion(question) && containsFlowSignal(normalizedLine)) {
            score += 24;
        }
        if (looksLikeFlowQuestion(question) && containsFlowTransitionSignal(normalizedLine)) {
            score += 28;
        }
        if (shouldCollectDistinctMachineIdentifiers(question) && containsMachineIdentifierSignal(normalizedLine)) {
            score += 18;
        }
        if (looksLikeFlowQuestion(question) && containsQuestionTokenInFlowTransition(question, normalizedLine)) {
            score += 24;
        }
        if (looksLikeFlowQuestion(question) && looksLikePlantUmlDeclarationLine(normalizedLine)) {
            score -= 28;
        }
        if (looksLikeStatusQuestion(question) && containsStatusSignal(lowerCaseLine)) {
            score += 24;
        }
        if (looksLikeStatusQuestion(question) && containsPathSignal(normalizedLine)) {
            score += 12;
        }
        if (looksLikeStatusQuestion(question) && looksLikeHeadingOnlyFallbackLine(rawLine)) {
            score -= 8;
        }
        if (looksLikeFlowQuestion(question) && looksLikeHeadingOnlyFallbackLine(rawLine)) {
            score -= 8;
        }
        if (looksLikeEnumerationQuestion(question) && looksLikeHeadingOnlyFallbackLine(rawLine)) {
            score -= 18;
        }
        if (looksLikeFlowQuestion(question)
                && looksLikeLeadInSentence(normalizedLine)
                && !containsFlowSignal(normalizedLine)) {
            score -= 10;
        }
        if (looksLikeEnumerationQuestion(question)
                && looksLikeLeadInSentence(normalizedLine)
                && !looksLikeEnumerationFactLine(rawLine, normalizedLine)) {
            score -= 14;
        }
        if (looksLikeCapabilityQuestion(question) && looksLikeGenericSummarySentence(normalizedLine)) {
            score -= 18;
        }
        if (looksLikeCapabilityQuestion(question) && answerEvidenceNormalizer.startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score -= 50;
        }
        if (looksLikeSetupChecklistQuestion(question) && answerEvidenceNormalizer.startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score -= 28;
        }
        if (normalizedQuestion.contains("配置")
                && (lowerCaseLine.startsWith("代码中的") || lowerCaseLine.contains("数值一致"))) {
            score -= 8;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && lowerCaseLine.contains("一般遵循")
                && !containsStrongConstraintSignal(normalizedLine)) {
            score -= 16;
        }
        return score;
    }

    /**
     * 判断候选行是否覆盖了用户问题里显式点名的精确标识。
     *
     * @param normalizedLine 候选行
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    boolean containsRequestedExactIdentifier(String normalizedLine, String question) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        for (String requestedIdentifier : extractRequestedReferentialIdentifiers(question)) {
            if (containsExactIdentifierSignal(requestedIdentifier)
                    && lowerCaseLine.contains(lowerCase(requestedIdentifier))) {
                return true;
            }
        }
        return false;
    }
}
