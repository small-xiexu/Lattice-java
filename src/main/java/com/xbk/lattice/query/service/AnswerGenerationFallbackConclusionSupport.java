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
 * 答案生成 fallback 结论支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationFallbackConclusionSupport extends AnswerGenerationFallbackAggregationSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationFallbackConclusionSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationFallbackConclusionSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

String buildEvidenceMarkdown(String question, List<QueryArticleHit> queryArticleHits) {
        return answerFallbackMarkdownBuilder.buildEvidenceMarkdown(question, queryArticleHits);
    }

    /**
     * 为 deterministic fallback 构造更像最终回答的结论行。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 结论行
     */
    List<String> buildEvidenceConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        List<String> terminalFieldExactPathLines =
                buildTerminalFieldExactPathConclusionLines(question, fallbackHits, queryTokens);
        if (!terminalFieldExactPathLines.isEmpty()) {
            return terminalFieldExactPathLines;
        }
        return answerFallbackConclusionBuilder.buildEvidenceConclusionLines(question, fallbackHits, queryTokens);
    }

    /**
     * 为末级字段明确的结构化路径查值题构建 exact path 结论。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中列表
     * @param queryTokens 查询 token
     * @return 结论行
     */
    private List<String> buildTerminalFieldExactPathConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (fallbackHits == null
                || fallbackHits.isEmpty()
                || !hasSingleTerminalFieldIntent(question, queryTokens)) {
            return List.of();
        }
        QueryArticleHit bestHit = null;
        String bestLine = "";
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit fallbackHit : fallbackHits) {
            for (String rawLine : selectFallbackContentLines(fallbackHit.getContent())) {
                String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
                if (!looksLikeStructuredPathValueCandidate(normalizedLine)) {
                    continue;
                }
                int terminalScore = scoreStructuredPathTerminalFieldMatch(question, normalizedLine, queryTokens);
                if (terminalScore <= 0) {
                    continue;
                }
                int lineScore = scoreQuestionFocusedFallbackLine(question, rawLine, normalizedLine, queryTokens);
                if (lineScore > bestScore) {
                    bestScore = lineScore;
                    bestHit = fallbackHit;
                    bestLine = normalizedLine;
                }
            }
        }
        if (bestHit == null || bestLine.isBlank()) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedSemanticKeys = new LinkedHashSet<String>();
        appendAggregatedConclusionLine(
                question,
                new EvidenceLineMatch(bestHit, bestLine, bestScore),
                conclusionLines,
                selectedSemanticKeys
        );
        return conclusionLines;
    }

    /**
     * 判断问题是否只询问一个通用末级字段意图。
     *
     * @param question 用户问题
     * @param queryTokens 查询 token
     * @return 单一末级字段意图返回 true
     */
    private boolean hasSingleTerminalFieldIntent(String question, List<String> queryTokens) {
        List<String> terminalFieldTokens = extractQuestionTerminalFieldTokens(question, queryTokens);
        if (terminalFieldTokens.isEmpty()) {
            return false;
        }
        Set<String> canonicalTokens = new LinkedHashSet<String>();
        for (String terminalFieldToken : terminalFieldTokens) {
            String canonicalToken = canonicalTerminalFieldToken(terminalFieldToken);
            if (!canonicalToken.isBlank()) {
                canonicalTokens.add(canonicalToken);
            }
        }
        return canonicalTokens.size() == 1;
    }

    /**
     * 构建精确路径结论行。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中列表
     * @param queryTokens 查询 token
     * @return 路径结论行
     */
    @Override
    List<String> buildExactPathConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (!looksLikePathQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit bestHit = null;
        String bestSnippet = "";
        int bestScore = Integer.MIN_VALUE;
        List<EvidenceLineMatch> requestedPathMatches = new ArrayList<EvidenceLineMatch>();
        List<EvidenceLineMatch> fallbackPathMatches = new ArrayList<EvidenceLineMatch>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            List<String> snippets = selectExactPathCandidateLines(question, fallbackHit, queryTokens);
            for (String snippet : snippets) {
                boolean pathContractSnippet = requiresPathContractCompanion(question) && containsPathContractSignal(snippet);
                boolean structuredPathSnippet = looksLikeQuestionFocusedStructuredPathValueCandidate(
                        question,
                        snippet,
                        queryTokens
                );
                if ((!containsPathSignal(snippet) && !pathContractSnippet && !structuredPathSnippet)
                        || looksLikePathHeaderLine(snippet)) {
                    continue;
                }
                if (containsRequestedExactPathIdentifier(question)
                        && introducesUnrequestedPathForExactPathQuestion(question, snippet)) {
                    continue;
                }
                int snippetScore = scoreQuestionFocusedFallbackLine(question, snippet, snippet, queryTokens);
                EvidenceLineMatch evidenceLineMatch = new EvidenceLineMatch(fallbackHit, snippet, snippetScore);
                if (containsRequestedExactIdentifier(snippet, question)) {
                    requestedPathMatches.add(evidenceLineMatch);
                }
                else {
                    fallbackPathMatches.add(evidenceLineMatch);
                }
            }
        }
        List<EvidenceLineMatch> primaryMatches = requestedPathMatches.isEmpty() ? fallbackPathMatches : requestedPathMatches;
        for (EvidenceLineMatch primaryMatch : primaryMatches) {
            if (primaryMatch.getScore() > bestScore) {
                bestScore = primaryMatch.getScore();
                bestHit = primaryMatch.getQueryArticleHit();
                bestSnippet = primaryMatch.getLine();
            }
        }
        if (bestHit == null || bestSnippet.isBlank()) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedSemanticKeys = new LinkedHashSet<String>();
        appendAggregatedConclusionLine(
                question,
                new EvidenceLineMatch(bestHit, bestSnippet, bestScore),
                conclusionLines,
                selectedSemanticKeys
        );
        for (EvidenceLineMatch companionMatch : selectExactPathCompanionMatches(
                question,
                fallbackHits,
                queryTokens,
                selectedSemanticKeys
        )) {
            appendAggregatedConclusionLine(question, companionMatch, conclusionLines, selectedSemanticKeys);
            if (conclusionLines.size() >= desiredFallbackConclusionSnippetCount(question)) {
                break;
            }
        }
        return conclusionLines;
    }

    /**
     * 为显式 path 题收集候选行；除贴题摘句外，全篇补扫点名 path 与 path 契约行。
     *
     * @param question 用户问题
     * @param fallbackHit fallback 命中
     * @param queryTokens 查询 token
     * @return 候选行
     */
    @Override
    List<String> selectExactPathCandidateLines(
            String question,
            QueryArticleHit fallbackHit,
            List<String> queryTokens
    ) {
        List<String> candidates = new ArrayList<String>();
        if (fallbackHit == null) {
            return candidates;
        }
        candidates.addAll(selectQuestionFocusedFallbackSnippets(question, fallbackHit, queryTokens, 3));
        if (!containsRequestedExactPathIdentifier(question)) {
            return candidates;
        }
        for (String rawLine : selectFallbackContentLines(fallbackHit.getContent())) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
            if (normalizedLine.isBlank() || candidates.contains(normalizedLine)) {
                continue;
            }
            if (containsRequestedExactIdentifier(normalizedLine, question)
                    || containsPathContractSignal(normalizedLine)) {
                candidates.add(normalizedLine);
            }
        }
        return candidates;
    }

    /**
     * 为路径精确题补足同问题里的规则、变更或状态维度，避免只返回路径值。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @param selectedSemanticKeys 已选语义键
     * @return 补充事实
     */
    @Override
    List<EvidenceLineMatch> selectExactPathCompanionMatches(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            Set<String> selectedSemanticKeys
    ) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<EvidenceLineMatch> companionMatches = new ArrayList<EvidenceLineMatch>();
        int hitLimit = Math.min(8, fallbackHits.size());
        for (int hitIndex = 0; hitIndex < hitLimit; hitIndex++) {
            QueryArticleHit fallbackHit = fallbackHits.get(hitIndex);
            List<String> rawLines = new ArrayList<String>();
            rawLines.addAll(selectFallbackContentLines(fallbackHit.getContent()));
            if (requiresPathContractCompanion(question)) {
                rawLines.addAll(selectPathContractCandidateLines(fallbackHit));
            }
            for (String rawLine : rawLines) {
                String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
                if (normalizedLine.isBlank() || !isExactPathCompanionLine(question, normalizedLine)) {
                    continue;
                }
                String semanticKey = aggregatedEvidenceSemanticKey(question, normalizedLine);
                if (!semanticKey.isBlank()
                        && selectedSemanticKeys != null
                        && selectedSemanticKeys.contains(semanticKey)) {
                    continue;
                }
                int score = scoreQuestionFocusedFallbackLine(question, rawLine, normalizedLine, queryTokens);
                companionMatches.add(new EvidenceLineMatch(fallbackHit, normalizedLine, score));
            }
        }
        companionMatches.sort((leftMatch, rightMatch) -> Integer.compare(rightMatch.getScore(), leftMatch.getScore()));
        return companionMatches;
    }

    /**
     * 构建聚合证据结论行。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中列表
     * @param queryTokens 查询 token
     * @return 聚合结论行
     */
    List<String> buildAggregatedEvidenceConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (!shouldAggregateEvidenceConclusion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        if (looksLikeEnumerationQuestion(question)
                && !looksLikeStructuredFactQuestion(question)
                && !shouldAggregateEnumerationConclusion(question)) {
            return List.of();
        }
        int desiredCount = Math.max(2, desiredFallbackConclusionSnippetCount(question));
        if (looksLikePathQuestion(question)
                && (looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question))) {
            desiredCount = Math.max(3, desiredCount);
        }
        List<EvidenceLineMatch> rankedMatches = collectRankedEvidenceLineMatches(
                question,
                fallbackHits,
                queryTokens,
                Math.min(8, desiredCount + 2)
        );
        if (rankedMatches.size() < 2) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedSemanticKeys = new LinkedHashSet<String>();
        for (int index = 0; index < rankedMatches.size(); index++) {
            EvidenceLineMatch match = rankedMatches.get(index);
            appendAggregatedConclusionLine(question, match, conclusionLines, selectedSemanticKeys);
            if (conclusionLines.size() >= desiredCount) {
                return conclusionLines;
            }
            for (String companionLine : selectCompanionStructuredLines(question, match.getQueryArticleHit(), match.getLine(), 2)) {
                EvidenceLineMatch companionMatch = new EvidenceLineMatch(
                        match.getQueryArticleHit(),
                        companionLine,
                        match.getScore() - 1
                );
                appendAggregatedConclusionLine(question, companionMatch, conclusionLines, selectedSemanticKeys);
                if (conclusionLines.size() >= desiredCount) {
                    return conclusionLines;
                }
            }
        }
        return conclusionLines;
    }

    /**
     * 判断是否需要跨命中聚合多条事实。
     *
     * @param question 用户问题
     * @return 需要返回 true
     */
    boolean shouldAggregateEvidenceConclusion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains(",")
                || normalizedQuestion.contains("/")
                || normalizedQuestion.contains("&")
                || normalizedQuestion.contains("+")
                || normalizedQuestion.contains("count")
                || requiresPathContractCompanion(question)
                || (looksLikePathQuestion(question) && looksLikeRuleConstraintQuestion(question));
    }

    /**
     * 判断枚举题是否仍应走精确证据聚合，而不是退回普通枚举兜底。
     *
     * @param question 用户问题
     * @return 应聚合返回 true
     */
    boolean shouldAggregateEnumerationConclusion(String question) {
        return looksLikePathQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeNumericQuestion(question)
                || looksLikeRuleConstraintQuestion(question);
    }

}
