package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 答案 fallback 结论构建器
 *
 * 职责：为确定性 fallback 答案选择合适的结论模板并组装结论行
 *
 * 不属于本类的事：不计算底层证据分值、不解析 source 内容、不补 citation 细则
 *
 * @author xiexu
 */
final class AnswerFallbackConclusionBuilder {

    private final AnswerGenerationService support;

    private final AnswerSpreadsheetFieldDefinitionConclusionBuilder spreadsheetFieldDefinitionConclusionBuilder;

    /**
     * 创建 fallback 结论构建器。
     *
     * @param support 答案生成支撑逻辑
     */
    AnswerFallbackConclusionBuilder(AnswerGenerationService support) {
        this.support = support;
        this.spreadsheetFieldDefinitionConclusionBuilder =
                new AnswerSpreadsheetFieldDefinitionConclusionBuilder(support);
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
        return buildEvidenceConclusionLines(question, fallbackHits, queryTokens, null);
    }

    List<String> buildEvidenceConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> comparisonOptions = support.extractComparisonOptions(question);
        if (comparisonOptions.size() >= 2) {
            List<String> comparisonLines = buildComparisonFallbackConclusionLines(
                    comparisonOptions.get(0),
                    comparisonOptions.get(1),
                    fallbackHits,
                    queryTokens
            );
            if (!comparisonLines.isEmpty()) {
                return comparisonLines;
            }
        }
        return buildGeneralFallbackConclusionLines(question, fallbackHits, queryTokens, queryArticleHits);
    }

    /**
     * 为二选一问题生成更直接的 deterministic fallback 结论。
     *
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 结论行
     */
    private List<String> buildComparisonFallbackConclusionLines(
            String leftOption,
            String rightOption,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (leftOption.isBlank() || rightOption.isBlank() || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<QueryArticleHit> leftHits = new ArrayList<QueryArticleHit>();
        List<QueryArticleHit> rightHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String matchedOption = support.matchComparisonOption(fallbackHit, leftOption, rightOption);
            if (leftOption.equals(matchedOption)) {
                leftHits.add(fallbackHit);
                continue;
            }
            if (rightOption.equals(matchedOption)) {
                rightHits.add(fallbackHit);
            }
        }
        if (leftHits.isEmpty() || rightHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit leftRepresentativeHit = leftHits.get(0);
        QueryArticleHit rightRepresentativeHit = rightHits.get(0);
        String leftSnippet = support.trimTrailingFallbackPunctuation(
                support.selectOptionSpecificFallbackSnippet(leftRepresentativeHit, leftOption, queryTokens)
        );
        String rightSnippet = support.trimTrailingFallbackPunctuation(
                support.selectOptionSpecificFallbackSnippet(rightRepresentativeHit, rightOption, queryTokens)
        );
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("Evidence for "
                + leftOption
                + ": "
                + leftSnippet
                + ". "
                + support.joinConclusionCitations(leftHits));
        conclusionLines.add("Evidence for "
                + rightOption
                + ": "
                + rightSnippet
                + ". "
                + support.joinConclusionCitations(rightHits));
        List<QueryArticleHit> preferredSummaryHits = preferredComparisonSummaryHits(leftOption, rightOption, leftHits, rightHits);
        String conflictSummaryCitations = joinConflictConclusionCitations(fallbackHits, preferredSummaryHits);
        if (leftHits.size() == rightHits.size()) {
            if (!conflictSummaryCitations.isBlank()) {
                conclusionLines.add("Multiple evidence lines support different positions; verify the primary source before deciding. "
                        + conflictSummaryCitations);
            }
            return conclusionLines;
        }
        String preferredOption = leftHits.size() > rightHits.size() ? leftOption : rightOption;
        if (!conflictSummaryCitations.isBlank()) {
            conclusionLines.add("Evidence currently leans toward "
                    + preferredOption
                    + ", but the source statements are not fully converged. "
                    + conflictSummaryCitations);
        }
        return conclusionLines;
    }

    /**
     * 为冲突总结句挑选更稳的 citation，只引用同时覆盖“当前口径偏向”与“资料存在冲突”的证据。
     *
     * @param fallbackHits fallback 证据
     * @param preferredHits 倾向侧代表证据
     * @return citation 串
     */
    private String joinConflictConclusionCitations(
            List<QueryArticleHit> fallbackHits,
            List<QueryArticleHit> preferredHits
    ) {
        List<QueryArticleHit> conflictSignalHits = new ArrayList<QueryArticleHit>();
        if (fallbackHits != null) {
            for (QueryArticleHit fallbackHit : fallbackHits) {
                if (support.containsConflictSignal(fallbackHit)) {
                    conflictSignalHits.add(fallbackHit);
                }
            }
        }
        if (!conflictSignalHits.isEmpty()) {
            return support.joinConclusionCitations(conflictSignalHits);
        }
        return support.joinConclusionCitations(preferredHits);
    }

    /**
     * 返回冲突总结句优先使用的代表证据。
     *
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @param leftHits 左侧命中
     * @param rightHits 右侧命中
     * @return 代表证据
     */
    private List<QueryArticleHit> preferredComparisonSummaryHits(
            String leftOption,
            String rightOption,
            List<QueryArticleHit> leftHits,
            List<QueryArticleHit> rightHits
    ) {
        if (leftHits == null || rightHits == null || leftHits.isEmpty() || rightHits.isEmpty()) {
            return List.of();
        }
        String preferredOption = leftHits.size() >= rightHits.size() ? leftOption : rightOption;
        List<QueryArticleHit> preferredHits = leftHits.size() >= rightHits.size() ? leftHits : rightHits;
        List<QueryArticleHit> optionAwareHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit preferredHit : preferredHits) {
            if (support.matchesComparisonOption(support.buildFallbackEvidenceHaystack(preferredHit), preferredOption)) {
                optionAwareHits.add(preferredHit);
            }
        }
        if (!optionAwareHits.isEmpty()) {
            return optionAwareHits;
        }
        return List.of(preferredHits.get(0));
    }

    /**
     * 为普通问题生成 deterministic fallback 结论。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 结论行
     */
    private List<String> buildGeneralFallbackConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> conclusionLines = new ArrayList<String>();
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return conclusionLines;
        }
        QueryArticleHit primaryHit = fallbackHits.get(0);
        List<String> focusedFieldDefinitionLines =
                spreadsheetFieldDefinitionConclusionBuilder.buildFocusedSpreadsheetFieldDefinitionConclusionLines(
                        question,
                        fallbackHits
                );
        if (!focusedFieldDefinitionLines.isEmpty()) {
            return focusedFieldDefinitionLines;
        }
        List<String> fieldDefinitionLines =
                spreadsheetFieldDefinitionConclusionBuilder.buildSpreadsheetFieldDefinitionConclusionLines(
                        question,
                        primaryHit
                );
        if (!fieldDefinitionLines.isEmpty()) {
            return fieldDefinitionLines;
        }
        List<String> comparisonDifferenceLines = support.buildComparisonDifferenceConclusionLines(question, fallbackHits, queryTokens);
        if (!comparisonDifferenceLines.isEmpty()) {
            return comparisonDifferenceLines;
        }
        if (support.containsRequestedExactPathIdentifier(question)) {
            List<String> exactPathLines = support.buildExactPathConclusionLines(question, fallbackHits, queryTokens);
            if (!exactPathLines.isEmpty() && support.coversRequiredExactPathConclusion(question, exactPathLines)) {
                return exactPathLines;
            }
        }
        List<String> exactStructuredListLines = support.buildExactStructuredListConclusionLines(question, fallbackHits);
        if (!exactStructuredListLines.isEmpty()) {
            return exactStructuredListLines;
        }
        List<String> terminalUnitLines = buildTerminalUnitExactConclusionLines(fallbackHits, queryTokens, queryArticleHits);
        if (!terminalUnitLines.isEmpty()) {
            return terminalUnitLines;
        }
        List<String> aggregatedConclusionLines = support.buildAggregatedEvidenceConclusionLines(question, fallbackHits, queryTokens);
        if (!aggregatedConclusionLines.isEmpty()
                && (!support.containsRequestedExactPathIdentifier(question)
                || support.coversRequiredExactPathConclusion(question, aggregatedConclusionLines))) {
            return aggregatedConclusionLines;
        }
        List<String> exactPathLines = support.buildExactPathConclusionLines(question, fallbackHits, queryTokens);
        if (!exactPathLines.isEmpty() && support.coversRequiredExactPathConclusion(question, exactPathLines)) {
            return exactPathLines;
        }
        if (support.looksLikeSetupChecklistQuestion(question)
                || contentContainsMultipleSetupChecklistItems(primaryHit.getContent())) {
            List<String> setupSnippets = support.selectFallbackContentLines(primaryHit.getContent());
            List<String> setupSteps = support.extractSetupChecklistSteps(setupSnippets);
            if (setupSteps.isEmpty()) {
                setupSnippets = support.selectQuestionFocusedFallbackSnippets(question, primaryHit, queryTokens, 6);
                setupSteps = support.extractSetupChecklistSteps(setupSnippets);
            }
            if (!setupSteps.isEmpty()) {
                conclusionLines.add("Confirmed setup evidence: "
                        + String.join("; ", setupSteps)
                        + " "
                        + support.joinConclusionCitations(List.of(primaryHit)));
                return conclusionLines;
            }
        }
        int desiredSnippetCount = support.desiredFallbackConclusionSnippetCount(question);
        List<String> primarySnippets = support.selectQuestionFocusedFallbackSnippets(
                question,
                primaryHit,
                queryTokens,
                desiredSnippetCount
        );
        if (!primarySnippets.isEmpty()) {
            for (int index = 0; index < primarySnippets.size(); index++) {
                String prefix = index == 0 ? "Confirmed evidence: " : "Additional evidence: ";
                conclusionLines.add(prefix
                        + primarySnippets.get(index)
                        + " "
                        + support.joinConclusionCitations(List.of(primaryHit)));
            }
            if (primarySnippets.size() > 1) {
                return conclusionLines;
            }
        }
        else {
            conclusionLines.add("Confirmed evidence: "
                    + support.selectFallbackEvidenceSnippet(primaryHit, queryTokens)
                    + " "
                    + support.joinConclusionCitations(List.of(primaryHit)));
        }
        if (fallbackHits.size() > 1 && support.shouldIncludeSecondaryFallbackHit(question, primaryHit, fallbackHits.get(1), queryTokens)) {
            QueryArticleHit secondaryHit = fallbackHits.get(1);
            conclusionLines.add("Additional evidence: "
                    + support.selectQuestionFocusedFallbackSnippet(question, secondaryHit, queryTokens)
                    + " "
                    + support.joinConclusionCitations(List.of(secondaryHit)));
        }
        return conclusionLines;
    }

    /**
     * 从 fallback hits 中提取 terminal unit 的 displayText exact line 作为结论。
     *
     * 扫描所有 fallback hits，找到第一个 query-focused terminal unit，
     * 从其 content 中提取 keyPath = value 格式的精确行。
     */
    private List<String> buildTerminalUnitExactConclusionLines(
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            List<QueryArticleHit> queryArticleHits
    ) {
        double bestScore = Double.NEGATIVE_INFINITY;
        QueryArticleHit bestCandidate = null;
        String bestExactLine = "";
        for (QueryArticleHit fallbackHit : fallbackHits) {
            if (!isTerminalUnitChannelHit(fallbackHit)) {
                continue;
            }
            String exactLine = extractTerminalUnitExactLine(fallbackHit);
            if (exactLine.isEmpty()) {
                continue;
            }
            if (!isTerminalHitQueryFocused(fallbackHit, queryTokens)) {
                continue;
            }
            double score = fusedOrderScore(fallbackHit, queryArticleHits);
            if (bestCandidate == null || score > bestScore) {
                bestScore = score;
                bestCandidate = fallbackHit;
                bestExactLine = exactLine;
            }
        }
        if (bestCandidate == null) {
            return List.of();
        }
        return List.of("Confirmed evidence: "
                + bestExactLine
                + " "
                + support.joinConclusionCitations(List.of(bestCandidate)));
    }

    /**
     * 将原始 fused order 转换为排序分数：order 越靠前分数越高。
     * 无 queryArticleHits 时回退到 QueryArticleHit.getScore()。
     */
    private static double fusedOrderScore(QueryArticleHit hit, List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return hit.getScore();
        }
        int index = queryArticleHits.indexOf(hit);
        if (index < 0) {
            return -1.0D;
        }
        return (double) (queryArticleHits.size() - index);
    }

    /**
     * 判断 terminal unit hit 与 query 是否相关。
     *
     * 检查 hit 的 content、metadata（fieldAliases、fieldDescription、displayText）
     * 等通用证据文本是否包含 query token，而非只检查 displayText exact line。
     * 这允许中文 query 匹配英文字段 terminal unit——中文 alias/description
     * 提供相关性信号，而最终结论仍输出 exact displayText。
     */
    private static boolean isTerminalHitQueryFocused(
            QueryArticleHit hit,
            List<String> queryTokens
    ) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return true;
        }
        String haystack = buildTerminalHitEvidenceHaystack(hit);
        for (String token : queryTokens) {
            if (token.length() >= 2 && haystack.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String buildTerminalHitEvidenceHaystack(QueryArticleHit hit) {
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
     * 从 terminal unit hit 的 content 中提取 keyPath = value 格式的精确行。
     */
    private static String extractTerminalUnitExactLine(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        String metadataJson = hit.getMetadataJson();
        if (metadataJson != null) {
            String displayText = extractJsonStringValue(metadataJson, "\"displayText\":");
            if (!displayText.isBlank() && displayText.contains("=")) {
                return displayText;
            }
        }
        String content = hit.getContent();
        if (content != null) {
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.contains(" = ") && !trimmed.startsWith("[") && !trimmed.startsWith("{")) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    /**
     * 从 JSON 字符串中提取指定键的字符串值。
     */
    private static String extractJsonStringValue(String json, String marker) {
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int quoteStart = json.indexOf('"', markerIndex + marker.length());
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return "";
        }
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 判断命中是否来自 terminal unit FTS channel。
     */
    private static boolean isTerminalUnitChannelHit(QueryArticleHit hit) {
        return TerminalUnitHitMetadataSupport.isTerminalUnitChannelHit(hit);
    }

    /**
     * 检测 content 中是否包含多个 setup checklist 条目（编号列表 + setup 信号）。
     *
     * @param content 证据内容
     * @return 是否包含 >= 2 个 setup checklist 条目
     */
    private boolean contentContainsMultipleSetupChecklistItems(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        List<String> contentLines = support.selectFallbackContentLines(content);
        List<String> setupSteps = support.extractSetupChecklistSteps(contentLines);
        return setupSteps.size() >= 2;
    }
}
