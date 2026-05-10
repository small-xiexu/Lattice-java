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
 * 答案生成 fallback 聚合结论支持
 *
 * 职责：收集、筛选并装配 deterministic fallback 的聚合事实结论
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationFallbackAggregationSupport extends AnswerGenerationFallbackComparisonSupport {

    /**
     * 创建无 LLM 的拆分支持。
     */
    AnswerGenerationFallbackAggregationSupport() {
        super();
    }

    /**
     * 创建拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationFallbackAggregationSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    List<EvidenceLineMatch> collectRankedEvidenceLineMatches(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            int limit
    ) {
        List<EvidenceLineMatch> matches = new ArrayList<EvidenceLineMatch>();
        Set<String> semanticKeys = new LinkedHashSet<String>();
        int hitLimit = Math.min(10, fallbackHits.size());
        int perHitLimit = Math.max(6, desiredFallbackConclusionSnippetCount(question));
        boolean focusedStructuredFactAggregation = shouldUseFocusedStructuredFactAggregation(question);
        for (int hitIndex = 0; hitIndex < hitLimit; hitIndex++) {
            QueryArticleHit fallbackHit = fallbackHits.get(hitIndex);
            List<String> snippets = selectAggregationCandidateLines(question, fallbackHit, queryTokens, perHitLimit);
            for (String snippet : snippets) {
                if (looksLikeCurrentFactQuestion(lowerCase(question))
                        && !containsCurrentFactSignal(lowerCase(snippet))) {
                    continue;
                }
                if (focusedStructuredFactAggregation && !matchesAnyStructuredFactFocusToken(question, snippet)) {
                    continue;
                }
                if (!looksLikeUsefulAggregatedEvidenceLine(question, snippet)) {
                    continue;
                }
                String semanticKey = aggregatedEvidenceSemanticKey(question, snippet);
                if (semanticKey.isBlank() || semanticKeys.contains(semanticKey)) {
                    continue;
                }
                semanticKeys.add(semanticKey);
                int score = scoreQuestionFocusedFallbackLine(question, snippet, snippet, queryTokens);
                matches.add(new EvidenceLineMatch(fallbackHit, snippet, score));
            }
        }
        matches.sort((leftMatch, rightMatch) -> Integer.compare(rightMatch.getScore(), leftMatch.getScore()));
        if (matches.size() <= limit) {
            return matches;
        }
        return new ArrayList<EvidenceLineMatch>(matches.subList(0, limit));
    }

    /**
     * 判断聚合题是否已经拆出明确的结构化焦点，需要按焦点收敛候选行。
     *
     * @param question 用户问题
     * @return 需要按焦点收敛返回 true
     */
    boolean shouldUseFocusedStructuredFactAggregation(String question) {
        return looksLikeStructuredFactQuestion(question)
                && containsMultiFocusSeparator(lowerCase(question))
                && extractStructuredFactFocusTokens(question).size() >= 2;
    }

    /**
     * 判断候选行是否覆盖任一结构化事实焦点。
     *
     * @param question 用户问题
     * @param snippet 候选行
     * @return 覆盖焦点返回 true
     */
    boolean matchesAnyStructuredFactFocusToken(String question, String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return false;
        }
        int delimiterIndex = answerEvidenceNormalizer.structuredAssignmentDelimiterIndex(snippet);
        if (delimiterIndex > 0) {
            String assignmentKey = snippet.substring(0, delimiterIndex).trim();
            if (matchesStructuredFactFocusToken(question, assignmentKey)) {
                return true;
            }
        }
        String lowerCaseSnippet = lowerCase(snippet);
        for (String focusToken : extractStructuredFactFocusTokens(question)) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (!normalizedFocusToken.isBlank() && lowerCaseSnippet.contains(normalizedFocusToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为聚合回答收集候选事实行。
     *
     * @param question 用户问题
     * @param fallbackHit fallback 命中
     * @param queryTokens 查询 token
     * @param perHitLimit 每条命中内的基础候选数
     * @return 候选事实行
     */
    List<String> selectAggregationCandidateLines(
            String question,
            QueryArticleHit fallbackHit,
            List<String> queryTokens,
            int perHitLimit
    ) {
        List<String> candidates = new ArrayList<String>();
        candidates.addAll(selectQuestionFocusedFallbackSnippets(question, fallbackHit, queryTokens, perHitLimit));
        if (fallbackHit == null) {
            return candidates;
        }
        for (String contentLine : selectFallbackContentLines(fallbackHit.getContent())) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(contentLine);
            if (normalizedLine.isBlank() || candidates.contains(normalizedLine)) {
                continue;
            }
            candidates.add(normalizedLine);
        }
        return candidates;
    }

    /**
     * 为聚合回答追加结论行，并按语义键去重。
     *
     * @param question 用户问题
     * @param match 候选事实
     * @param conclusionLines 输出结论
     * @param selectedSemanticKeys 已选语义键
     */
    @Override
    void appendAggregatedConclusionLine(
            String question,
            EvidenceLineMatch match,
            List<String> conclusionLines,
            Set<String> selectedSemanticKeys
    ) {
        if (match == null || conclusionLines == null || selectedSemanticKeys == null) {
            return;
        }
        String line = match.getLine();
        if (line == null || line.isBlank()) {
            return;
        }
        String semanticKey = aggregatedEvidenceSemanticKey(question, line);
        if (!semanticKey.isBlank() && selectedSemanticKeys.contains(semanticKey)) {
            return;
        }
        if (!semanticKey.isBlank()) {
            selectedSemanticKeys.add(semanticKey);
        }
        String prefix = conclusionLines.isEmpty() ? "当前可确认的信息是：" : "同一问题的补充事实是：";
        conclusionLines.add(prefix
                + line
                + " "
                + joinConclusionCitations(List.of(match.getQueryArticleHit())));
    }

    /**
     * 从同一命中里挑选与当前事实相邻的结构化补充行。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param primaryLine 已选主事实
     * @param limit 最多补充条数
     * @return 补充行
     */
    List<String> selectCompanionStructuredLines(
            String question,
            QueryArticleHit queryArticleHit,
            String primaryLine,
            int limit
    ) {
        List<String> companionLines = new ArrayList<String>();
        if (queryArticleHit == null || primaryLine == null || primaryLine.isBlank() || limit <= 0) {
            return companionLines;
        }
        List<String> contentLines = selectFallbackContentLines(queryArticleHit.getContent());
        int primaryIndex = indexOfNormalizedFallbackLine(contentLines, primaryLine);
        if (primaryIndex < 0) {
            return companionLines;
        }
        int scanUpperBound = Math.min(contentLines.size(), primaryIndex + 8);
        for (int index = primaryIndex + 1; index < scanUpperBound; index++) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(contentLines.get(index));
            if (normalizedLine.isBlank()
                    || normalizedLine.equals(primaryLine)
                    || containsUncertainEvidenceSignal(lowerCase(normalizedLine))
                    || !looksLikeUsefulAggregatedEvidenceLine(question, normalizedLine)) {
                continue;
            }
            if (!looksLikeCompanionEvidenceLine(normalizedLine)) {
                continue;
            }
            if (!companionLines.contains(normalizedLine)) {
                companionLines.add(normalizedLine);
            }
            if (companionLines.size() >= limit) {
                break;
            }
        }
        return companionLines;
    }

    /**
     * 查找归一化候选行在原始内容行列表中的位置。
     *
     * @param contentLines 原始内容行
     * @param targetLine 目标候选行
     * @return 下标；未找到返回 -1
     */
    int indexOfNormalizedFallbackLine(List<String> contentLines, String targetLine) {
        if (contentLines == null || contentLines.isEmpty() || targetLine == null || targetLine.isBlank()) {
            return -1;
        }
        for (int index = 0; index < contentLines.size(); index++) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(contentLines.get(index));
            if (targetLine.equals(normalizedLine)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 判断一条相邻候选是否更像主事实的结构化补充。
     *
     * @param normalizedLine 归一化候选句
     * @return 结构化补充返回 true
     */
    boolean looksLikeCompanionEvidenceLine(String normalizedLine) {
        return containsPathSignal(normalizedLine)
                || containsStructuredLabelSignal(normalizedLine)
                || containsBatchOrOrdinalSignal(normalizedLine)
                || containsChangeTrackingSignal(normalizedLine)
                || containsCorrectionOrStatusSignal(lowerCase(normalizedLine))
                || containsFlowTransitionSignal(normalizedLine);
    }

    /**
     * 判断候选句是否适合作为聚合直答事实。
     *
     * @param question 用户问题
     * @param snippet 候选句
     * @return 适合返回 true
     */
    boolean looksLikeUsefulAggregatedEvidenceLine(String question, String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return false;
        }
        if (looksLikePlantUmlDeclarationLine(snippet) || looksLikeLeadInSentence(snippet)) {
            return false;
        }
        String lowerCaseSnippet = lowerCase(snippet);
        if (lowerCaseSnippet.contains("应视为")
                || lowerCaseSnippet.contains("来源未展开")
                || lowerCaseSnippet.contains("不能进一步断言")
                || lowerCaseSnippet.contains("未提供校准依据")
                || containsUncertainEvidenceSignal(lowerCaseSnippet)) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
            if (looksLikePathQuestion(question)
                    && (looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question))) {
                if (containsRequestedExactPathIdentifier(question)
                        && introducesUnrequestedPathForExactPathQuestion(question, snippet)) {
                    return false;
                }
                return containsPathSignal(snippet)
                        || containsRequestedExactIdentifier(snippet, question)
                    || containsStrongConstraintSignal(snippet)
                    || containsRuleConstraintSignal(snippet)
                    || containsChangeTrackingSignal(snippet);
        }
        if (containsMultiFocusSeparator(normalizedQuestion)) {
            return containsPathSignal(snippet)
                    || containsStructuredLabelSignal(snippet)
                    || containsBatchOrOrdinalSignal(snippet)
                    || containsCorrectionOrStatusSignal(lowerCase(snippet))
                    || containsChangeTrackingSignal(snippet)
                    || containsMachineIdentifierSignal(snippet);
        }
        if (looksLikeNumericQuestion(question) || expectsBatchOrOrdinalAnswer(normalizedQuestion)) {
            return snippet.matches("(?s).*\\d.*")
                    || containsBatchOrOrdinalSignal(snippet)
                    || containsCorrectionOrStatusSignal(lowerCase(snippet));
        }
        if (shouldCollectDistinctMachineIdentifiers(question) && containsMachineIdentifierSignal(snippet)) {
            return containsFlowTransitionSignal(snippet)
                    || containsPathSignal(snippet)
                    || containsMultipleHighSignalQuestionTokens(question, snippet);
        }
        if (looksLikePathQuestion(question)) {
            return containsPathSignal(snippet) || containsStrongConstraintSignal(snippet);
        }
        return containsMultipleHighSignalQuestionTokens(question, snippet);
    }

    /**
     * 判断候选证据是否带有不确定、推断或缺口语气。
     *
     * @param lowerCaseSnippet 小写候选句
     * @return 不确定候选返回 true
     */
    boolean containsUncertainEvidenceSignal(String lowerCaseSnippet) {
        if (lowerCaseSnippet == null || lowerCaseSnippet.isBlank()) {
            return false;
        }
        return lowerCaseSnippet.contains("[推断]")
                || lowerCaseSnippet.contains("推断")
                || lowerCaseSnippet.contains("证据不足")
                || lowerCaseSnippet.contains("证据缺口")
                || lowerCaseSnippet.contains("未直接描述")
                || lowerCaseSnippet.contains("未直接提供")
                || lowerCaseSnippet.contains("有待补充")
                || lowerCaseSnippet.contains("无法形成");
    }

    /**
     * 生成聚合证据行的语义去重键。
     *
     * @param question 用户问题
     * @param snippet 候选句
     * @return 去重键
     */
    @Override
    String aggregatedEvidenceSemanticKey(String question, String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        List<String> paths = extractEvidencePaths(List.of(snippet));
        if (!paths.isEmpty()) {
            return String.join("|", paths);
        }
        List<String> identifiers = extractMachineIdentifiers(snippet);
        if (!identifiers.isEmpty()) {
            return String.join("|", identifiers);
        }
        if (looksLikePathQuestion(question)
                && (looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question))) {
            if (containsStrongConstraintSignal(snippet)) {
                return "strong-constraint";
            }
            if (containsRuleConstraintSignal(snippet)) {
                return "rule-constraint";
            }
            if (containsChangeTrackingSignal(snippet)) {
                return "change-tracking";
            }
        }
        Matcher labelMatcher = Pattern.compile("\\b\\d+[A-Za-z]\\b|\\b[A-Za-z]+\\d+[A-Za-z]?\\b").matcher(snippet);
        if (labelMatcher.find()) {
            return lowerCase(labelMatcher.group());
        }
        String compactSnippet = normalizeQuestionEchoText(snippet);
        return compactSnippet.length() <= 80 ? compactSnippet : compactSnippet.substring(0, 80);
    }

    /**
     * 从候选句中提取带分隔符的机器标识符。
     *
     * @param snippet 候选句
     * @return 标识列表
     */
    @Override
    List<String> extractMachineIdentifiers(String snippet) {
        List<String> identifiers = new ArrayList<String>();
        if (snippet == null || snippet.isBlank()) {
            return identifiers;
        }
        Matcher identifierMatcher = Pattern.compile("[A-Za-z0-9]+[-_][A-Za-z0-9][A-Za-z0-9_-]*").matcher(snippet);
        while (identifierMatcher.find()) {
            String identifier = lowerCase(identifierMatcher.group());
            if (!identifiers.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    /**
     * 为“差异/是否一致”类问题优先选择带对比结论的证据句。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 差异题结论
     */
    List<String> buildComparisonDifferenceConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (!looksLikeComparisonQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit bestHit = null;
        String bestSnippet = "";
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit fallbackHit : fallbackHits) {
            List<String> snippets = selectQuestionFocusedFallbackSnippets(question, fallbackHit, queryTokens, 4);
            for (String snippet : snippets) {
                if (!containsComparisonSignal(snippet)) {
                    continue;
                }
                int snippetScore = scoreQuestionFocusedFallbackLine(question, snippet, snippet, queryTokens);
                if (snippetScore > bestScore) {
                    bestScore = snippetScore;
                    bestHit = fallbackHit;
                    bestSnippet = snippet;
                }
            }
        }
        if (bestHit == null || bestSnippet.isBlank()) {
            return List.of();
        }
        return List.of("当前可确认的信息是：" + bestSnippet + " " + joinConclusionCitations(List.of(bestHit)));
    }

    /**
     * 判断问题是否带有多焦点分隔符。
     *
     * @param normalizedQuestion 归一化问题
     * @return 多焦点返回 true
     */
    private boolean containsMultiFocusSeparator(String normalizedQuestion) {
        return normalizedQuestion != null
                && (normalizedQuestion.contains(",")
                || normalizedQuestion.contains("/")
                || normalizedQuestion.contains("&")
                || normalizedQuestion.contains("+"));
    }

    /**
     * 为“多个标签 + 多个路径”的结构化问题直接提取精确事实列表。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 精确列表结论
     */
    List<String> buildExactStructuredListConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits
    ) {
        if (!looksLikeCompoundExactLookupQuestion(question)
                || !looksLikePathQuestion(question)
                || fallbackHits == null
                || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<String> expectedLabels = extractRequestedStructuredLabels(question);
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedPaths = new LinkedHashSet<String>();
        Set<String> selectedLabels = new LinkedHashSet<String>();
        int hitLimit = Math.min(6, fallbackHits.size());
        for (int hitIndex = 0; hitIndex < hitLimit; hitIndex++) {
            QueryArticleHit fallbackHit = fallbackHits.get(hitIndex);
            for (String rawLine : selectFallbackContentLines(fallbackHit.getContent())) {
                String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
                if (!looksLikeStructuredPathFactLine(normalizedLine)) {
                    continue;
                }
                List<String> linePaths = extractEvidencePaths(List.of(normalizedLine));
                if (linePaths.isEmpty()) {
                    continue;
                }
                List<String> lineLabels = extractStructuredLabels(List.of(normalizedLine));
                if (!expectedLabels.isEmpty() && lineLabels.isEmpty()) {
                    continue;
                }
                if (!expectedLabels.isEmpty() && !containsAnyExpectedLabel(lineLabels, expectedLabels)) {
                    continue;
                }
                String primaryPath = linePaths.get(0);
                if (selectedPaths.contains(primaryPath)) {
                    continue;
                }
                selectedPaths.add(primaryPath);
                selectedLabels.addAll(lineLabels);
                String prefix = conclusionLines.isEmpty() ? "当前可确认的信息是：" : "同一问题的补充事实是：";
                conclusionLines.add(prefix
                        + normalizedLine
                        + " "
                        + joinConclusionCitations(List.of(fallbackHit)));
                if (selectedPaths.size() >= 3
                        && (expectedLabels.isEmpty() || selectedLabels.containsAll(expectedLabels))) {
                    return conclusionLines;
                }
            }
        }
        if (conclusionLines.size() >= 2
                && (expectedLabels.isEmpty() || selectedLabels.containsAll(expectedLabels))) {
            return conclusionLines;
        }
        return List.of();
    }
}
