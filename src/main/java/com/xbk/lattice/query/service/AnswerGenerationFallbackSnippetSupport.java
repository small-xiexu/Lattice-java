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
 * 答案生成 fallback 证据句支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationFallbackSnippetSupport extends AnswerGenerationFallbackSnippetSelectionSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationFallbackSnippetSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationFallbackSnippetSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

List<QueryArticleHit> selectFallbackEvidenceHits(String question, List<QueryArticleHit> queryArticleHits) {
        return answerFallbackEvidenceSelector.selectFallbackEvidenceHits(question, queryArticleHits);
    }

    /**
     * 计算单条命中在当前问题下最优“事实句”的分值，用于 fallback 排序。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 最优事实句分值
     */
    int scoreQuestionFocusedFallbackHit(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens
    ) {
        if (queryArticleHit == null) {
            return Integer.MIN_VALUE;
        }
        List<String> rawCandidates = new ArrayList<String>();
        rawCandidates.addAll(selectMatchedLines(queryArticleHit.getContent(), queryTokens));
        rawCandidates.addAll(answerEvidenceNormalizer.selectStructuredJsonValueLines(queryArticleHit.getContent()));
        if (requiresPathContractCompanion(question)) {
            rawCandidates.addAll(selectPathContractCandidateLines(queryArticleHit));
        }
        if (looksLikeStructuredFactQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeCapabilityQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeEnumerationQuestion(question)) {
            rawCandidates.addAll(selectFallbackContentLines(queryArticleHit.getContent()));
        }
        int bestScore = Integer.MIN_VALUE;
        for (String rawCandidate : rawCandidates) {
            String normalizedCandidate = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawCandidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            if (looksLikeQuestionEchoLine(question, normalizedCandidate)) {
                continue;
            }
            int candidateScore = scoreQuestionFocusedFallbackLine(question, rawCandidate, normalizedCandidate, queryTokens);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
            }
        }
        if (bestScore > Integer.MIN_VALUE && looksLikeStructuredFactQuestion(question)) {
            bestScore += scoreStructuredFactHitCoverage(question, rawCandidates);
        }
        if (looksLikeExactLookupQuestion(question)
                && queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE
                && bestScore > Integer.MIN_VALUE) {
            bestScore += 24;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE
                && lowerCase(queryArticleHit.getContent()).contains("review_status: needs_human_review")
                && bestScore > Integer.MIN_VALUE) {
            bestScore -= 18;
        }
        return bestScore;
    }

    /**
     * 计算结构化查值题在单条命中内的问题焦点覆盖和当前口径信号。
     *
     * @param question 用户问题
     * @param rawCandidates 候选行
     * @return 覆盖加分
     */
    int scoreStructuredFactHitCoverage(String question, List<String> rawCandidates) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return 0;
        }
        List<String> focusTokens = extractStructuredFactFocusTokens(question);
        if (focusTokens.isEmpty()) {
            return 0;
        }
        int score = 0;
        boolean currentFactQuestion = looksLikeCurrentFactQuestion(lowerCase(question));
        boolean hasCurrentFactSignal = false;
        for (String focusToken : focusTokens) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (normalizedFocusToken.isBlank()) {
                continue;
            }
            boolean matchedFocus = false;
            boolean matchedCurrentFocus = false;
            for (String rawCandidate : rawCandidates) {
                String normalizedCandidate = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawCandidate);
                if (normalizedCandidate.isBlank()) {
                    continue;
                }
                String lowerCaseCandidate = lowerCase(normalizedCandidate);
                if (containsCurrentFactSignal(lowerCaseCandidate)) {
                    hasCurrentFactSignal = true;
                }
                if (!lowerCaseCandidate.contains(normalizedFocusToken)) {
                    continue;
                }
                matchedFocus = true;
                if (currentFactQuestion && containsCurrentFactSignal(lowerCaseCandidate)) {
                    matchedCurrentFocus = true;
                }
            }
            if (matchedFocus) {
                score += 10;
            }
            if (matchedCurrentFocus) {
                score += 16;
            }
        }
        if (currentFactQuestion && hasCurrentFactSignal) {
            score += 16;
        }
        return score;
    }

    /**
     * 选择 deterministic fallback 中更适合展示给用户的证据摘要。
     *
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 证据摘要
     */
    String selectFallbackEvidenceSnippet(QueryArticleHit queryArticleHit, List<String> queryTokens) {
        String matchedLine = selectBestFallbackMatchedLine(selectMatchedLines(queryArticleHit.getContent(), queryTokens), queryTokens);
        if (!matchedLine.isBlank()) {
            return matchedLine;
        }
        String description = extractDescription(queryArticleHit.getMetadataJson());
        if (!description.isEmpty()) {
            return stripEmbeddedCitationLiterals(description);
        }
        String contentLine = selectBestFallbackMatchedLine(selectFallbackContentLines(queryArticleHit.getContent()), queryTokens);
        if (!contentLine.isBlank()) {
            return contentLine;
        }
        return stripEmbeddedCitationLiterals(extractEvidenceSnippet(queryArticleHit.getContent()));
    }

    /**
     * 围绕当前问题挑选更像“最终回答”的证据句；配置/阈值题优先返回 key=value 类事实句。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 更贴题的证据句
     */
    String selectQuestionFocusedFallbackSnippet(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens
    ) {
        List<String> snippets = selectQuestionFocusedFallbackSnippets(question, queryArticleHit, queryTokens, 1);
        if (!snippets.isEmpty()) {
            return snippets.get(0);
        }
        return selectFallbackEvidenceSnippet(queryArticleHit, queryTokens);
    }

    /**
     * 围绕当前问题挑选若干条最直接的证据句。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @param limit 最大条数
     * @return 证据句列表
     */
    List<String> selectQuestionFocusedFallbackSnippets(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens,
            int limit
    ) {
        if (queryArticleHit == null || limit <= 0) {
            return List.of();
        }
        List<String> rawCandidates = new ArrayList<String>();
        rawCandidates.addAll(selectMatchedLines(queryArticleHit.getContent(), queryTokens));
        rawCandidates.addAll(answerEvidenceNormalizer.selectStructuredJsonValueLines(queryArticleHit.getContent()));
        if (looksLikeStructuredFactQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeCapabilityQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeEnumerationQuestion(question)) {
            rawCandidates.addAll(selectFallbackContentLines(queryArticleHit.getContent()));
        }
        Map<String, Integer> scoredCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredFactCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredStatusCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredPolicyCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredOrdinalCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredFlowCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredExactIdentifierCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredSetupCandidates = new LinkedHashMap<String, Integer>();
        for (String rawCandidate : rawCandidates) {
            String normalizedCandidate = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawCandidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            if (looksLikeQuestionEchoLine(question, normalizedCandidate)) {
                continue;
            }
            int candidateScore = scoreQuestionFocusedFallbackLine(question, rawCandidate, normalizedCandidate, queryTokens);
            mergeCandidateScore(scoredCandidates, normalizedCandidate, candidateScore);
            if (looksLikeStructuredFactCandidate(question, normalizedCandidate)) {
                mergeCandidateScore(scoredFactCandidates, normalizedCandidate, candidateScore);
            }
            if (looksLikeStatusQuestion(question)
                    && containsStatusSignal(lowerCase(normalizedCandidate))) {
                mergeCandidateScore(scoredStatusCandidates, normalizedCandidate, candidateScore);
            }
            if ((looksLikeRuleConstraintQuestion(question)
                    && (containsRuleConstraintSignal(normalizedCandidate)
                    || containsStrongConstraintSignal(normalizedCandidate)
                    || containsChangeTrackingSignal(normalizedCandidate)
                    || containsComparisonSignal(normalizedCandidate)))
                    || (requiresPathContractCompanion(question) && containsPathContractSignal(normalizedCandidate))) {
                mergeCandidateScore(scoredPolicyCandidates, normalizedCandidate, candidateScore);
            }
            if (expectsBatchOrOrdinalAnswer(lowerCase(question))
                    && containsBatchOrOrdinalSignal(normalizedCandidate)) {
                mergeCandidateScore(scoredOrdinalCandidates, normalizedCandidate, candidateScore);
            }
            if (looksLikeFlowQuestion(question) && containsFlowSignal(normalizedCandidate)) {
                mergeCandidateScore(scoredFlowCandidates, normalizedCandidate, candidateScore);
            }
            if (looksLikeSetupChecklistQuestion(question) && containsSetupSignal(normalizedCandidate)) {
                mergeCandidateScore(scoredSetupCandidates, normalizedCandidate, candidateScore);
            }
            if (containsRequestedExactIdentifier(normalizedCandidate, question)) {
                mergeCandidateScore(scoredExactIdentifierCandidates, normalizedCandidate, candidateScore);
            }
        }
        Map<String, Integer> preferredCandidates;
        if (looksLikeSetupChecklistQuestion(question) && !scoredSetupCandidates.isEmpty()) {
            preferredCandidates = scoredSetupCandidates;
        }
        else if (!scoredExactIdentifierCandidates.isEmpty()
                && (containsRequestedExactPathIdentifier(question) || scoredFactCandidates.isEmpty())) {
            preferredCandidates = mergePreferredCandidates(scoredExactIdentifierCandidates, scoredPolicyCandidates);
        }
        else if (expectsBatchOrOrdinalAnswer(lowerCase(question)) && !scoredOrdinalCandidates.isEmpty()) {
            preferredCandidates = scoredOrdinalCandidates;
        }
        else if (looksLikeRuleConstraintQuestion(question) && !scoredPolicyCandidates.isEmpty()) {
            preferredCandidates = scoredPolicyCandidates;
        }
        else if (looksLikeStatusQuestion(question) && !scoredStatusCandidates.isEmpty()) {
            preferredCandidates = scoredStatusCandidates;
        }
        else if (looksLikeFlowQuestion(question) && !scoredFlowCandidates.isEmpty()) {
            preferredCandidates = scoredFlowCandidates;
        }
        else if (looksLikeEnumerationQuestion(question) && !looksLikeStructuredFactQuestion(question)) {
            preferredCandidates = scoredCandidates;
        }
        else {
            preferredCandidates = scoredFactCandidates.isEmpty() ? scoredCandidates : scoredFactCandidates;
        }
        if (preferredCandidates.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<String, Integer>> rankedCandidates =
                new ArrayList<Map.Entry<String, Integer>>(preferredCandidates.entrySet());
        rankedCandidates.sort((leftEntry, rightEntry) -> {
            int scoreCompare = Integer.compare(rightEntry.getValue(), leftEntry.getValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Integer.compare(leftEntry.getKey().length(), rightEntry.getKey().length());
        });
        if (limit > 1 && shouldUseCoverageAwareFallbackSnippets(question)) {
            List<String> focusTokens = extractStructuredFactFocusTokens(question);
            return selectCoverageAwareStructuredFactSnippets(question, rankedCandidates, focusTokens, limit);
        }
        List<String> snippets = new ArrayList<String>();
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            snippets.add(stripEmbeddedCitationLiterals(rankedCandidate.getKey()));
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets;
    }

    /**
     * 为显式 path 契约题补充全篇契约候选，避免长文档前部的相邻接口列表截断后续约束行。
     *
     * @param queryArticleHit 查询命中
     * @return path 契约候选行
     */
    List<String> selectPathContractCandidateLines(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<String>();
        String description = extractDescription(queryArticleHit.getMetadataJson());
        if (containsPathContractSignal(description)) {
            candidates.add(description);
        }
        for (String contentLine : selectFallbackContentLines(queryArticleHit.getContent())) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(contentLine);
            if (!normalizedLine.isBlank() && containsPathContractSignal(normalizedLine)) {
                candidates.add(normalizedLine);
            }
        }
        return candidates;
    }

    /**
     * 合并主候选和补充候选，保留主候选优先顺序。
     *
     * @param primaryCandidates 主候选
     * @param secondaryCandidates 补充候选
     * @return 合并后的候选
     */
    Map<String, Integer> mergePreferredCandidates(
            Map<String, Integer> primaryCandidates,
            Map<String, Integer> secondaryCandidates
    ) {
        Map<String, Integer> mergedCandidates = new LinkedHashMap<String, Integer>();
        if (primaryCandidates != null) {
            mergedCandidates.putAll(primaryCandidates);
        }
        if (secondaryCandidates != null) {
            for (Map.Entry<String, Integer> secondaryCandidate : secondaryCandidates.entrySet()) {
                mergeCandidateScore(mergedCandidates, secondaryCandidate.getKey(), secondaryCandidate.getValue().intValue());
            }
        }
        return mergedCandidates;
    }

    /**
     * 针对“分别是多少”这类问题，优先让多条答案覆盖不同问题焦点，避免同一配置项重复占满结果位。
     *
     * @param rankedCandidates 已排序候选句
     * @param focusTokens 问题焦点
     * @param limit 最大条数
     * @return 更均衡的结构化事实句
     */
}
