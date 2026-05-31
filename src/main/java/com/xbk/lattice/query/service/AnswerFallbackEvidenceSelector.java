package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 答案 fallback 证据选择器
 *
 * 职责：为确定性 fallback 答案选择、补充、排序与去重候选证据
 *
 * 不属于本类的事：不构建 Markdown、不拼接 citation、不生成候选事实句正文
 *
 * @author xiexu
 */
final class AnswerFallbackEvidenceSelector {

    private static final int COMPLEMENTARY_STRUCTURED_PATH_FACT_CARD_MIN_SCORE = 80;

    private final AnswerGenerationService support;

    private final AnswerMarkdownEvidenceNormalizer evidenceNormalizer = new AnswerMarkdownEvidenceNormalizer();

    private final AnswerFallbackEvidenceSupport evidenceSupport;

    /**
     * 创建 fallback 证据选择器。
     *
     * @param support 答案生成支撑逻辑
     */
    AnswerFallbackEvidenceSelector(AnswerGenerationService support) {
        this.support = support;
        this.evidenceSupport = new AnswerFallbackEvidenceSupport(support);
    }

    /**
     * 选择 deterministic fallback 使用的证据，优先 article/contribution，必要时回落到 source/graph。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return fallback 证据集合
     */
    List<QueryArticleHit> selectFallbackEvidenceHits(String question, List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return List.of();
        }
        List<QueryArticleHit> comparisonHits = selectComparisonFallbackEvidenceHits(question, queryArticleHits);
        if (!comparisonHits.isEmpty()) {
            return enrichPathContractCompanionHits(question, comparisonHits, queryArticleHits);
        }
        List<QueryArticleHit> complementaryHits = selectComplementaryEvidenceByQuestionTokens(question, queryArticleHits);
        if (!complementaryHits.isEmpty()) {
            return enrichPathContractCompanionHits(question, complementaryHits, queryArticleHits);
        }
        List<QueryArticleHit> sortedAllRelevantHits = deduplicateSortedFallbackEvidenceHits(
                question,
                sortFallbackEvidenceHits(question, filterFallbackEvidenceHits(queryArticleHits, question, false))
        );
        List<QueryArticleHit> allRelevantHits = support.shouldAggregateEvidenceConclusion(question)
                ? sortedAllRelevantHits
                : retainDirectStructuredEvidence(question, sortedAllRelevantHits);
        List<QueryArticleHit> preferredArticleHits = deduplicateSortedFallbackEvidenceHits(
                question,
                sortFallbackEvidenceHits(
                        question,
                        filterFallbackEvidenceHits(queryArticleHits, question, true)
                )
        );
        if (preferredArticleHits.isEmpty()) {
            return enrichPathContractCompanionHits(question, allRelevantHits, queryArticleHits);
        }
        List<QueryArticleHit> retainedArticleHits = support.shouldAggregateEvidenceConclusion(question)
                ? preferredArticleHits
                : retainDirectStructuredEvidence(question, preferredArticleHits);
        if (shouldPreferMixedEvidence(question, retainedArticleHits, allRelevantHits)) {
            return enrichPathContractCompanionHits(question, allRelevantHits, queryArticleHits);
        }
        return enrichPathContractCompanionHits(question, retainedArticleHits, queryArticleHits);
    }

    /**
     * 为二选一 / 对比题保留两侧选项各自命中的证据，避免全局问题 token 过滤掉其中一侧。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 对比证据
     */
    private List<QueryArticleHit> selectComparisonFallbackEvidenceHits(
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> comparisonOptions = support.extractComparisonOptions(question);
        if (comparisonOptions.size() < 2) {
            return List.of();
        }
        String leftOption = comparisonOptions.get(0);
        String rightOption = comparisonOptions.get(1);
        List<QueryArticleHit> comparisonHits = new ArrayList<QueryArticleHit>();
        boolean hasLeftHit = false;
        boolean hasRightHit = false;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            String matchedOption = support.matchComparisonOption(queryArticleHit, leftOption, rightOption);
            if (matchedOption.isBlank()) {
                continue;
            }
            addDistinctFallbackHit(comparisonHits, queryArticleHit);
            if (leftOption.equals(matchedOption)) {
                hasLeftHit = true;
            }
            if (rightOption.equals(matchedOption)) {
                hasRightHit = true;
            }
        }
        return hasLeftHit && hasRightHit ? comparisonHits : List.of();
    }

    /**
     * 为显式 path 契约题补充同源或异源的 path 契约证据。
     *
     * @param question 用户问题
     * @param selectedHits 已选证据
     * @param candidateHits 候选证据
     * @return 补充后的证据
     */
    private List<QueryArticleHit> enrichPathContractCompanionHits(
            String question,
            List<QueryArticleHit> selectedHits,
            List<QueryArticleHit> candidateHits
    ) {
        if (!support.requiresPathContractCompanion(question) || candidateHits == null || candidateHits.isEmpty()) {
            return selectedHits == null ? List.of() : selectedHits;
        }
        List<QueryArticleHit> enrichedHits = new ArrayList<QueryArticleHit>();
        if (selectedHits != null) {
            enrichedHits.addAll(selectedHits);
        }
        if (containsPathContractEvidence(enrichedHits)) {
            return enrichedHits;
        }
        List<QueryArticleHit> sortedCandidates = sortFallbackEvidenceHits(question, candidateHits);
        for (QueryArticleHit candidateHit : sortedCandidates) {
            if (!containsPathContractEvidence(List.of(candidateHit))) {
                continue;
            }
            addDistinctFallbackHit(question, enrichedHits, candidateHit);
            return enrichedHits;
        }
        return enrichedHits;
    }

    /**
     * 判断命中集合是否含有 path 契约证据。
     *
     * @param queryArticleHits 查询命中
     * @return 包含返回 true
     */
    private boolean containsPathContractEvidence(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return false;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            if (support.containsPathContractSignal(support.extractDescription(queryArticleHit.getMetadataJson()))) {
                return true;
            }
            for (String contentLine : support.selectFallbackContentLines(queryArticleHit.getContent())) {
                String normalizedLine = evidenceNormalizer.normalizeFallbackLineCandidate(contentLine);
                if (support.containsPathContractSignal(normalizedLine)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 为多主题问题保留互补证据，避免单篇结构化文档把另一组问题主体挤掉。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 能分别覆盖多个问题高信号词时返回互补候选，否则返回空集合
     */
    private List<QueryArticleHit> selectComplementaryEvidenceByQuestionTokens(
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (support.extractComparisonOptions(question).size() >= 2
                || support.shouldAggregateEvidenceConclusion(question)) {
            return List.of();
        }
        List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (highSignalTokens.size() < 2) {
            return List.of();
        }
        List<QueryArticleHit> sortedHits = deduplicateSortedFallbackEvidenceHits(
                question,
                sortFallbackEvidenceHits(question, filterFallbackEvidenceHits(queryArticleHits, question, false))
        );
        List<QueryArticleHit> candidates = sortedHits.isEmpty() ? queryArticleHits : sortedHits;
        List<QueryArticleHit> selectedHits = new ArrayList<QueryArticleHit>();
        QueryArticleHit firstSourceHit = firstSourceHit(candidates);
        if (firstSourceHit != null) {
            addDistinctFallbackHit(question, selectedHits, firstSourceHit);
        }
        for (String highSignalToken : highSignalTokens) {
            QueryArticleHit tokenHit = support.findHitContainingAny(candidates, List.of(highSignalToken));
            addDistinctFallbackHit(question, selectedHits, tokenHit);
        }
        addQuestionFocusedStructuredPathFactCard(question, candidates, selectedHits);
        return selectedHits.size() >= 2 ? selectedHits : List.of();
    }

    /**
     * 在互补证据已选中原文和摘要时，补入同样贴题的结构化路径事实卡片。
     *
     * @param question 用户问题
     * @param candidateHits 候选证据
     * @param selectedHits 已选证据
     */
    private void addQuestionFocusedStructuredPathFactCard(
            String question,
            List<QueryArticleHit> candidateHits,
            List<QueryArticleHit> selectedHits
    ) {
        if (!shouldSupplementStructuredPathFactCard(question, selectedHits)) {
            return;
        }
        List<String> queryTokens = support.extractQueryTokens(question);
        for (QueryArticleHit candidateHit : candidateHits) {
            if (!isQuestionFocusedStructuredPathFactCard(question, candidateHit, queryTokens)) {
                continue;
            }
            if (containsFallbackHit(question, selectedHits, candidateHit)) {
                continue;
            }
            addDistinctFallbackHit(question, selectedHits, candidateHit);
            return;
        }
    }

    /**
     * 判断当前互补证据集合是否需要补充结构化路径事实卡片。
     *
     * @param question 用户问题
     * @param selectedHits 已选证据
     * @return 需要补充返回 true
     */
    private boolean shouldSupplementStructuredPathFactCard(String question, List<QueryArticleHit> selectedHits) {
        return support.looksLikeExactLookupQuestion(question)
                && selectedHits != null
                && selectedHits.size() >= 2
                && containsEvidenceType(selectedHits, QueryEvidenceType.SOURCE)
                && (containsEvidenceType(selectedHits, QueryEvidenceType.ARTICLE)
                || containsEvidenceType(selectedHits, QueryEvidenceType.CONTRIBUTION));
    }

    /**
     * 判断候选是否为高分且贴合问题焦点的结构化路径事实卡片。
     *
     * @param question 用户问题
     * @param candidateHit 候选证据
     * @param queryTokens 查询 token
     * @return 命中返回 true
     */
    private boolean isQuestionFocusedStructuredPathFactCard(
            String question,
            QueryArticleHit candidateHit,
            List<String> queryTokens
    ) {
        if (candidateHit == null || candidateHit.getEvidenceType() != QueryEvidenceType.FACT_CARD) {
            return false;
        }
        int focusedScore = support.scoreQuestionFocusedFallbackHit(question, candidateHit, queryTokens);
        if (focusedScore < COMPLEMENTARY_STRUCTURED_PATH_FACT_CARD_MIN_SCORE) {
            return false;
        }
        for (String rawLine : selectStructuredPathFactCardCandidateLines(candidateHit)) {
            String normalizedLine = evidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
            if (support.looksLikeQuestionFocusedStructuredPathValueCandidate(question, normalizedLine, queryTokens)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 选择 fact card 中可参与结构化路径事实判断的候选行。
     *
     * @param candidateHit 候选证据
     * @return 候选行
     */
    private List<String> selectStructuredPathFactCardCandidateLines(QueryArticleHit candidateHit) {
        List<String> candidateLines = new ArrayList<String>();
        if (candidateHit == null) {
            return candidateLines;
        }
        candidateLines.addAll(support.selectFallbackContentLines(candidateHit.getContent()));
        candidateLines.addAll(evidenceNormalizer.selectStructuredJsonValueLines(candidateHit.getContent()));
        return candidateLines;
    }

    /**
     * 判断已选证据是否已经包含同一 fallback 证据。
     *
     * @param question 用户问题
     * @param selectedHits 已选证据
     * @param candidateHit 候选证据
     * @return 已包含返回 true
     */
    private boolean containsFallbackHit(
            String question,
            List<QueryArticleHit> selectedHits,
            QueryArticleHit candidateHit
    ) {
        if (selectedHits == null || candidateHit == null) {
            return false;
        }
        String candidateKey = evidenceSupport.canonicalKey(question, candidateHit);
        for (QueryArticleHit selectedHit : selectedHits) {
            if (evidenceSupport.canonicalKey(question, selectedHit).equals(candidateKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取第一条原文证据，避免 fallback 只保留摘要卡片而丢掉同源原文。
     *
     * @param queryArticleHits 查询命中
     * @return 原文命中
     */
    private QueryArticleHit firstSourceHit(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return null;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit != null && queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
                return queryArticleHit;
            }
        }
        return null;
    }

    /**
     * 按 fallback 规范去重追加证据。
     *
     * @param selectedHits 已选证据
     * @param fallbackHit 待追加证据
     */
    private void addDistinctFallbackHit(List<QueryArticleHit> selectedHits, QueryArticleHit fallbackHit) {
        if (fallbackHit == null) {
            return;
        }
        String canonicalKey = evidenceSupport.canonicalKey(fallbackHit);
        for (QueryArticleHit selectedHit : selectedHits) {
            if (evidenceSupport.canonicalKey(selectedHit).equals(canonicalKey)) {
                return;
            }
        }
        selectedHits.add(fallbackHit);
    }

    /**
     * 按问题感知去重追加 fallback 证据。
     *
     * @param question 用户问题
     * @param selectedHits 已选证据
     * @param fallbackHit 待追加证据
     */
    private void addDistinctFallbackHit(
            String question,
            List<QueryArticleHit> selectedHits,
            QueryArticleHit fallbackHit
    ) {
        if (fallbackHit == null) {
            return;
        }
        String canonicalKey = evidenceSupport.canonicalKey(question, fallbackHit);
        for (QueryArticleHit selectedHit : selectedHits) {
            if (evidenceSupport.canonicalKey(question, selectedHit).equals(canonicalKey)) {
                return;
            }
        }
        selectedHits.add(fallbackHit);
    }

    /**
     * 判断当前问题是否应优先采用更贴题的 source/graph 证据，而不是固定优先 article 摘要。
     *
     * @param question 用户问题
     * @param articlePreferredHits article/contribution 候选
     * @param allRelevantHits 全量候选
     * @return 应优先采用全量候选返回 true
     */
    private boolean shouldPreferMixedEvidence(
            String question,
            List<QueryArticleHit> articlePreferredHits,
            List<QueryArticleHit> allRelevantHits
    ) {
        if (allRelevantHits == null || allRelevantHits.isEmpty()) {
            return false;
        }
        if (articlePreferredHits == null || articlePreferredHits.isEmpty()) {
            return true;
        }
        if (support.shouldAggregateEvidenceConclusion(question)
                && containsEvidenceType(allRelevantHits, QueryEvidenceType.SOURCE)) {
            return true;
        }
        QueryArticleHit bestOverallHit = allRelevantHits.get(0);
        if (bestOverallHit.getEvidenceType() == QueryEvidenceType.ARTICLE
                || bestOverallHit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
            return false;
        }
        List<String> queryTokens = support.extractQueryTokens(question);
        int bestOverallScore = support.scoreQuestionFocusedFallbackHit(question, bestOverallHit, queryTokens);
        int bestArticleScore = support.scoreQuestionFocusedFallbackHit(question, articlePreferredHits.get(0), queryTokens);
        if (bestOverallScore > bestArticleScore) {
            return true;
        }
        return support.looksLikeStatusQuestion(question) && bestOverallScore >= bestArticleScore;
    }

    /**
     * 判断候选命中集合里是否包含指定证据类型。
     *
     * @param queryArticleHits 查询命中
     * @param evidenceType 证据类型
     * @return 包含返回 true
     */
    private boolean containsEvidenceType(List<QueryArticleHit> queryArticleHits, QueryEvidenceType evidenceType) {
        if (queryArticleHits == null || queryArticleHits.isEmpty() || evidenceType == null) {
            return false;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit != null && queryArticleHit.getEvidenceType() == evidenceType) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当首条命中已经能通过标题/结构化字段直接回答时，丢弃只在正文顺带提及实体的旁证，避免污染 fallback。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中
     * @return 收敛后的 fallback 命中
     */
    private List<QueryArticleHit> retainDirectStructuredEvidence(String question, List<QueryArticleHit> fallbackHits) {
        if (fallbackHits == null || fallbackHits.size() <= 1) {
            return fallbackHits == null ? List.of() : fallbackHits;
        }
        List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (highSignalTokens.isEmpty()) {
            return fallbackHits;
        }
        QueryArticleHit primaryHit = fallbackHits.get(0);
        if (!support.matchesStructuredOrTitle(primaryHit, highSignalTokens)) {
            return fallbackHits;
        }
        List<QueryArticleHit> retainedHits = new ArrayList<QueryArticleHit>();
        retainedHits.add(primaryHit);
        for (int index = 1; index < fallbackHits.size(); index++) {
            QueryArticleHit fallbackHit = fallbackHits.get(index);
            if (support.matchesStructuredOrTitle(fallbackHit, highSignalTokens)) {
                retainedHits.add(fallbackHit);
            }
        }
        return retainedHits;
    }

    /**
     * 按问题相关性与证据类型过滤 deterministic fallback 命中。
     *
     * @param queryArticleHits 查询命中
     * @param question 用户问题
     * @param preferArticleEvidence 是否仅保留 article / contribution 级证据
     * @return 过滤后的命中
     */
    private List<QueryArticleHit> filterFallbackEvidenceHits(
            List<QueryArticleHit> queryArticleHits,
            String question,
            boolean preferArticleEvidence
    ) {
        List<QueryArticleHit> filteredHits = new ArrayList<QueryArticleHit>();
        List<String> highSignalTokens = preferArticleEvidence
                ? QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)
                : List.of();
        for (QueryArticleHit queryArticleHit : QueryEvidenceRelevanceSupport.filterRelevantHits(question, queryArticleHits)) {
            if (queryArticleHit == null) {
                continue;
            }
            if (preferArticleEvidence
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.CONTRIBUTION) {
                if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD
                        && isTerminalUnitChannelHit(queryArticleHit)
                        && isTerminalUnitQueryFocused(queryArticleHit, highSignalTokens)) {
                    filteredHits.add(queryArticleHit);
                }
                continue;
            }
            filteredHits.add(queryArticleHit);
        }
        if (filteredHits.isEmpty()) {
            filteredHits.addAll(selectQuestionScoredFallbackEvidenceHits(queryArticleHits, question, preferArticleEvidence));
        }
        return filteredHits;
    }

    /**
     * 判断 terminal unit hit 是否与 query 高信号 token 相关。
     *
     * 检查 terminal hit 的 content、metadata displayText、fieldDescription
     * 是否包含 query 高信号 token，作为 structed fact/exact lookup
     * 问题类型检测之外的第二层终端 unit 聚焦判断。
     */
    private static boolean isTerminalUnitQueryFocused(
            QueryArticleHit hit,
            List<String> highSignalTokens
    ) {
        if (highSignalTokens == null || highSignalTokens.isEmpty()) {
            return false;
        }
        String haystack = buildTerminalUnitEvidenceHaystack(hit);
        for (String token : highSignalTokens) {
            if (token.length() >= 2 && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 terminal unit hit 的通用证据文本，用于 query token 匹配。
     */
    private static String buildTerminalUnitEvidenceHaystack(QueryArticleHit hit) {
        StringBuilder sb = new StringBuilder();
        String content = hit.getContent();
        if (content != null) {
            sb.append(content.toLowerCase());
        }
        String metadataJson = hit.getMetadataJson();
        if (metadataJson != null) {
            sb.append(' ');
            sb.append(metadataJson.toLowerCase());
        }
        return sb.toString();
    }

    /**
     * 判断命中是否来自 terminal unit FTS channel。
     *
     * 委托到 {@link TerminalUnitHitMetadataSupport} 做结构化 JSON 解析，
     * 避免 JSONB 文本格式（compact vs spaced）导致 contains 匹配失败。
     */
    private static boolean isTerminalUnitChannelHit(QueryArticleHit hit) {
        return TerminalUnitHitMetadataSupport.isTerminalUnitChannelHit(hit);
    }

    /**
     * 当问题 token 与证据表述没有直接重叠时，使用通用候选句分值补选证据。
     *
     * @param queryArticleHits 查询命中
     * @param question 用户问题
     * @param preferArticleEvidence 是否仅保留 article / contribution 级证据
     * @return 补选证据
     */
    private List<QueryArticleHit> selectQuestionScoredFallbackEvidenceHits(
            List<QueryArticleHit> queryArticleHits,
            String question,
            boolean preferArticleEvidence
    ) {
        List<QueryArticleHit> scoredHits = new ArrayList<QueryArticleHit>();
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return scoredHits;
        }
        List<String> queryTokens = support.extractQueryTokens(question);
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            if (preferArticleEvidence
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.CONTRIBUTION) {
                if (queryArticleHit.getEvidenceType() != QueryEvidenceType.FACT_CARD
                        || !isTerminalUnitChannelHit(queryArticleHit)
                        || !isTerminalUnitQueryFocused(queryArticleHit,
                                QueryEvidenceRelevanceSupport.extractHighSignalTokens(question))) {
                    continue;
                }
            }
            if (requiresRequestedIdentifierCoverage(question)
                    && !hitContainsRequestedIdentifier(question, queryArticleHit)) {
                continue;
            }
            int score = support.scoreQuestionFocusedFallbackHit(question, queryArticleHit, queryTokens);
            if (score >= 20) {
                addDistinctFallbackHit(scoredHits, queryArticleHit);
            }
        }
        return scoredHits;
    }

    /**
     * 判断问题是否点名了需要在证据里覆盖的英文或结构化标识。
     *
     * @param question 用户问题
     * @return 需要覆盖返回 true
     */
    private boolean requiresRequestedIdentifierCoverage(String question) {
        return !support.extractRequestedReferentialIdentifiers(question).isEmpty();
    }

    /**
     * 判断命中是否覆盖问题里点名的标识。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 覆盖返回 true
     */
    private boolean hitContainsRequestedIdentifier(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        String haystack = String.join(
                " ",
                evidenceSupport.lowerCase(queryArticleHit.getArticleKey()),
                evidenceSupport.lowerCase(queryArticleHit.getConceptId()),
                evidenceSupport.lowerCase(queryArticleHit.getTitle()),
                evidenceSupport.lowerCase(support.extractDescription(queryArticleHit.getMetadataJson())),
                evidenceSupport.lowerCase(queryArticleHit.getContent()),
                evidenceSupport.lowerCase(String.join(" ", queryArticleHit.getSourcePaths()))
        );
        for (String requestedIdentifier : support.extractRequestedReferentialIdentifiers(question)) {
            if (!requestedIdentifier.isBlank() && haystack.contains(evidenceSupport.lowerCase(requestedIdentifier))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对已排序 fallback 命中按问题语义去重。
     *
     * @param question 用户问题
     * @param sortedHits 已排序命中
     * @return 去重后的命中
     */
    private List<QueryArticleHit> deduplicateSortedFallbackEvidenceHits(
            String question,
            List<QueryArticleHit> sortedHits
    ) {
        if (sortedHits == null || sortedHits.isEmpty()) {
            return List.of();
        }
        Map<String, QueryArticleHit> hitsByCanonicalKey = new LinkedHashMap<String, QueryArticleHit>();
        for (QueryArticleHit sortedHit : sortedHits) {
            if (sortedHit == null) {
                continue;
            }
            String canonicalKey = evidenceSupport.canonicalKey(question, sortedHit);
            hitsByCanonicalKey.putIfAbsent(canonicalKey, sortedHit);
        }
        return new ArrayList<QueryArticleHit>(hitsByCanonicalKey.values());
    }

    /**
     * 按问题相关性重排 fallback 证据，优先让更像“直接回答”的命中排前。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 重排后的命中
     */
    private List<QueryArticleHit> sortFallbackEvidenceHits(String question, List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.size() <= 1) {
            return queryArticleHits == null ? List.of() : queryArticleHits;
        }
        List<String> queryTokens = support.extractQueryTokens(question);
        List<QueryArticleHit> sortedHits = new ArrayList<QueryArticleHit>(queryArticleHits);
        sortedHits.sort((leftHit, rightHit) -> {
            int focusedSnippetCompare = Integer.compare(
                    support.scoreQuestionFocusedFallbackHit(question, rightHit, queryTokens),
                    support.scoreQuestionFocusedFallbackHit(question, leftHit, queryTokens)
            );
            if (focusedSnippetCompare != 0) {
                return focusedSnippetCompare;
            }
            int scoreCompare = Integer.compare(
                    QueryEvidenceRelevanceSupport.score(question, rightHit),
                    QueryEvidenceRelevanceSupport.score(question, leftHit)
            );
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int evidencePriorityCompare = Integer.compare(
                    evidenceSupport.priority(rightHit),
                    evidenceSupport.priority(leftHit)
            );
            if (evidencePriorityCompare != 0) {
                return evidencePriorityCompare;
            }
            return Double.compare(rightHit.getScore(), leftHit.getScore());
        });
        return sortedHits;
    }

}
