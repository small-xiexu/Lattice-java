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
 * 答案生成 精确标识与路径支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationReferencePathSupport extends AnswerGenerationReferenceIdentifierSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationReferencePathSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationReferencePathSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

List<String> extractEvidencePaths(List<String> snippets) {
        List<String> paths = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return paths;
        }
        Pattern pathPattern = Pattern.compile("/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._*{}-]+)*");
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            Matcher pathMatcher = pathPattern.matcher(snippet);
            while (pathMatcher.find()) {
                String path = pathMatcher.group();
                if (!paths.contains(path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    List<String> extractStructuredLabels(List<String> snippets) {
        List<String> labels = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return labels;
        }
        Pattern labelPattern = Pattern.compile("\\b\\d+[A-Za-z]\\b");
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            Matcher labelMatcher = labelPattern.matcher(snippet);
            while (labelMatcher.find()) {
                String label = lowerCase(labelMatcher.group());
                if (!labels.contains(label)) {
                    labels.add(label);
                }
            }
        }
        return labels;
    }

    List<String> extractRequestedStructuredLabels(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return extractStructuredLabels(List.of(question));
    }

    /**
     * 判断候选行是否更像“标签 + 路径”的结构化事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean looksLikeStructuredPathFactLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return containsStructuredLabelSignal(normalizedLine) && containsPathSignal(normalizedLine);
    }

    /**
     * 判断候选标签里是否包含问题显式点名的标签。
     *
     * @param lineLabels 候选标签
     * @param expectedLabels 期望标签
     * @return 命中返回 true
     */
    boolean containsAnyExpectedLabel(List<String> lineLabels, List<String> expectedLabels) {
        if (lineLabels == null || lineLabels.isEmpty() || expectedLabels == null || expectedLabels.isEmpty()) {
            return false;
        }
        for (String expectedLabel : expectedLabels) {
            if (lineLabels.contains(lowerCase(expectedLabel))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统计答案覆盖了多少个结构化标签。
     *
     * @param normalizedAnswer 归一化答案
     * @param labels 标签列表
     * @return 覆盖数量
     */
    int countCoveredStructuredLabels(String normalizedAnswer, List<String> labels) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank() || labels == null || labels.isEmpty()) {
            return 0;
        }
        int coveredCount = 0;
        for (String label : labels) {
            if (!label.isBlank() && normalizedAnswer.contains(lowerCase(label))) {
                coveredCount++;
            }
        }
        return coveredCount;
    }

    /**
     * 从贴题证据里抽取代表性数字，避免把日期年份当作唯一覆盖目标。
     *
     * @param snippets 证据句
     * @return 数值列表
     */
    List<String> extractRepresentativeNumbers(List<String> snippets) {
        List<String> numbers = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return numbers;
        }
        Pattern numberPattern = Pattern.compile("(?<![A-Za-z0-9])\\d{1,3}(?:,\\d{3})+|(?<![A-Za-z0-9])\\d+(?![A-Za-z0-9])");
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            Matcher numberMatcher = numberPattern.matcher(snippet);
            while (numberMatcher.find()) {
                String number = numberMatcher.group();
                if (looksLikeLowInformationNumber(number)) {
                    continue;
                }
                if (!numbers.contains(number)) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    /**
     * 判断数值是否更像日期年份或单字符噪声。
     *
     * @param number 数值文本
     * @return 低信息数值返回 true
     */
    boolean looksLikeLowInformationNumber(String number) {
        if (number == null || number.isBlank()) {
            return true;
        }
        String compactNumber = number.replace(",", "");
        if (compactNumber.length() <= 1) {
            return true;
        }
        if (compactNumber.matches("20\\d{2}") || compactNumber.matches("19\\d{2}")) {
            return true;
        }
        return false;
    }

    boolean coversRequiredExactPathConclusion(String question, List<String> exactPathLines) {
        if (!requiresPathContractCompanion(question)) {
            return true;
        }
        if (exactPathLines == null || exactPathLines.isEmpty()) {
            return false;
        }
        for (String exactPathLine : exactPathLines) {
            if (containsPathContractSignal(exactPathLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为接口路径题优先选择最像真实 path 的证据句。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 路径题结论
     */
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
                if ((!containsPathSignal(snippet) && !pathContractSnippet) || looksLikePathHeaderLine(snippet)) {
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
     * 判断候选行是否能补充路径题里的规则、变更或状态维度。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选行
     * @return 可作为补充返回 true
     */
    boolean isExactPathCompanionLine(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        if (requiresPathContractCompanion(question)
                && !containsPathContractSignal(normalizedLine)
                && !containsRequestedExactIdentifier(normalizedLine, question)) {
            return false;
        }
        if (requiresPathContractCompanion(question) && containsPathContractSignal(normalizedLine)) {
            return !introducesUnrequestedPathForExactPathQuestion(question, normalizedLine);
        }
        boolean requiredByRule = looksLikeRuleConstraintQuestion(question)
                && (containsRuleConstraintSignal(normalizedLine) || containsStrongConstraintSignal(normalizedLine));
        boolean requiredByChange = looksLikeChangeTrackingQuestion(question)
                && (containsChangeTrackingSignal(normalizedLine) || containsStrongConstraintSignal(normalizedLine));
        boolean requiredByStatus = looksLikeStatusQuestion(question)
                && containsStatusSignal(lowerCase(normalizedLine));
        if (!requiredByRule && !requiredByChange && !requiredByStatus) {
            return false;
        }
        return !introducesUnrequestedPathForExactPathQuestion(question, normalizedLine);
    }

    /**
     * 判断显式路径题是否需要优先补充接口契约类证据。
     *
     * @param question 用户问题
     * @return 需要契约证据返回 true
     */
    boolean requiresPathContractCompanion(String question) {
        if (!containsRequestedExactPathIdentifier(question) || !looksLikePathQuestion(question)) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("path")
                || normalizedQuestion.contains("change")
                || normalizedQuestion.contains("compatible")
                || normalizedQuestion.contains("contract");
    }

    /**
     * 判断候选句是否表达 path / URL / endpoint 契约或兼容性约束。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中契约信号返回 true
     */
}
