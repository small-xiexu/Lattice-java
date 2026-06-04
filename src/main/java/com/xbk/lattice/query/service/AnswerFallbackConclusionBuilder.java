package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xbk.lattice.shared.json.JsonMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final Logger log = LoggerFactory.getLogger(AnswerFallbackConclusionBuilder.class);

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
        List<String> terminalUnitLines = buildTerminalUnitExactConclusionLines(
                question,
                fallbackHits,
                queryTokens,
                queryArticleHits
        );
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
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            List<QueryArticleHit> queryArticleHits
    ) {
        int fhSize = fallbackHits != null ? fallbackHits.size() : 0;
        int qaSize = queryArticleHits != null ? queryArticleHits.size() : 0;
        log.debug("[TU_TRACE] enter fhSize={} qaSize={} tokens={}", fhSize, qaSize, queryTokens);

        int bestFieldTokenMatchCount = -1;
        int bestAliasTokenMatchCount = -1;
        double bestFusedOrderScore = Double.NEGATIVE_INFINITY;
        QueryArticleHit bestCandidate = null;
        String bestExactLine = "";
        int tuTotal = 0;
        int tuQfPassed = 0;

        for (QueryArticleHit fallbackHit : fallbackHits) {
            if (!isTerminalUnitChannelHit(fallbackHit)) {
                continue;
            }
            tuTotal++;
            String exactLine = extractTerminalUnitExactLine(fallbackHit);
            boolean qf = isTerminalHitQueryFocused(fallbackHit, queryTokens);
            int ftmc = qf ? countFieldLevelTokenMatches(fallbackHit, queryTokens) : -1;
            int atmc = qf ? countFieldAliasTokenMatches(fallbackHit, queryTokens) : -1;
            double fs = fusedOrderScore(fallbackHit, queryArticleHits);

            log.debug("[TU_TRACE] cand#{} el={} qf={} ftmc={} atmc={} fs={}",
                    tuTotal, exactLine, qf, ftmc, atmc, fs);

            if (exactLine.isEmpty()) {
                continue;
            }
            if (!qf) {
                continue;
            }
            tuQfPassed++;
            int fieldTokenMatchCount = ftmc;
            int aliasTokenMatchCount = atmc;
            double fusedScore = fs;
            if (fieldTokenMatchCount > bestFieldTokenMatchCount
                    || (fieldTokenMatchCount == bestFieldTokenMatchCount
                        && aliasTokenMatchCount > bestAliasTokenMatchCount)
                    || (fieldTokenMatchCount == bestFieldTokenMatchCount
                        && aliasTokenMatchCount == bestAliasTokenMatchCount
                        && fusedScore > bestFusedOrderScore)) {
                bestFieldTokenMatchCount = fieldTokenMatchCount;
                bestAliasTokenMatchCount = aliasTokenMatchCount;
                bestFusedOrderScore = fusedScore;
                bestCandidate = fallbackHit;
                bestExactLine = exactLine;
            }
        }

        if (bestCandidate == null) {
            log.debug("[TU_TRACE] result=NONE tuTotal={} tuQfPassed={}", tuTotal, tuQfPassed);
            return List.of();
        }
        log.debug("[TU_TRACE] result=SELECTED el={} ftmc={} atmc={} fs={} tuTotal={} tuQfPassed={}",
                bestExactLine, bestFieldTokenMatchCount, bestAliasTokenMatchCount, bestFusedOrderScore, tuTotal, tuQfPassed);

        String winnerTerminalKey = extractTerminalKey(bestCandidate);
        if (winnerTerminalKey.isEmpty()) {
            return List.of("Confirmed evidence: "
                    + bestExactLine
                    + " "
                    + support.joinConclusionCitations(List.of(bestCandidate)));
        }
        String winnerParentPath = extractParentPath(bestCandidate);
        int minThreshold = Math.max(1, bestFieldTokenMatchCount / 2);

        List<CandidateProfile> additionalProfiles = new ArrayList<CandidateProfile>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            if (!isTerminalUnitChannelHit(fallbackHit)) {
                continue;
            }
            if (fallbackHit == bestCandidate) {
                continue;
            }
            String exactLine = extractTerminalUnitExactLine(fallbackHit);
            if (exactLine.isEmpty()) {
                continue;
            }
            if (!isTerminalHitQueryFocused(fallbackHit, queryTokens)) {
                continue;
            }
            String terminalKey = extractTerminalKey(fallbackHit);
            if (!terminalKey.equals(winnerTerminalKey)) {
                continue;
            }
            String parentPath = extractParentPath(fallbackHit);
            if (winnerParentPath != null && winnerParentPath.equals(parentPath)) {
                continue;
            }
            int ftmc = countFieldLevelTokenMatches(fallbackHit, queryTokens);
            if (ftmc < minThreshold) {
                continue;
            }
            if (!entityContextMatchesQuery(fallbackHit, queryTokens, question)) {
                continue;
            }
            int atmc = countFieldAliasTokenMatches(fallbackHit, queryTokens);
            double fs = fusedOrderScore(fallbackHit, queryArticleHits);
            additionalProfiles.add(new CandidateProfile(fallbackHit, exactLine, ftmc, atmc, fs, parentPath));
        }

        if (additionalProfiles.isEmpty()) {
            return List.of("Confirmed evidence: "
                    + bestExactLine
                    + " "
                    + support.joinConclusionCitations(List.of(bestCandidate)));
        }
        List<CandidateProfile> deduped = deduplicateByParentPath(additionalProfiles);
        List<CandidateProfile> selected = selectTopAdditionalCandidates(deduped, 4);

        log.debug("[TU_TRACE] additionalCandidates={} deduped={} selected={}",
                additionalProfiles.size(), deduped.size(), selected.size());

        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("Confirmed evidence: "
                + bestExactLine
                + " "
                + support.joinConclusionCitations(List.of(bestCandidate)));
        for (CandidateProfile profile : selected) {
            conclusionLines.add("Confirmed evidence: "
                    + profile.exactLine
                    + " "
                    + support.joinConclusionCitations(List.of(profile.hit)));
        }
        return conclusionLines;
    }

    /**
     * 将原始 fused order 转换为排序分数：order 越靠前分数越高。
     * 无 queryArticleHits 时回退到 QueryArticleHit.getScore()。
     */
    private static int countFieldLevelTokenMatches(QueryArticleHit hit, List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) return 0;
        String fieldHaystack = buildFieldLevelHaystack(hit);
        if (fieldHaystack.isEmpty()) return 0;
        int matchCount = 0;
        for (String token : queryTokens) {
            if (token.length() < 2) continue;
            String lower = token.toLowerCase();
            if (fieldHaystack.contains(lower)) { matchCount++; continue; }
            if (hasCjkOverlap(fieldHaystack, token)) matchCount++;
        }
        return matchCount;
    }

    private static String buildFieldLevelHaystack(QueryArticleHit hit) {
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) return "";
        try {
            JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
            StringBuilder sb = new StringBuilder();
            sb.append(' ').append(node.path("displayText").asText(""));
            JsonNode aliases = node.path("fieldAliases");
            if (aliases.isArray()) for (JsonNode alias : aliases) sb.append(' ').append(alias.asText(""));
            sb.append(' ').append(node.path("fieldDescription").asText(""));
            return sb.toString().toLowerCase();
        } catch (Exception ignored) { return ""; }
    }

    /**
     * 只统计 query token 与 fieldAliases 的匹配数。
     *
     * fieldAliases 是专门为检索相关性优化的信号（含 LLM 生成的中文别名），
     * 比 displayText 或 fieldDescription 更精准地反映字段语义与 query 的关联。
     * 当 fieldTokenMatchCount 打平时，alias-level 匹配数提供更细粒度的字段相关性排序。
     */
    private static int countFieldAliasTokenMatches(QueryArticleHit hit, List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return 0;
        }
        String aliasesHaystack = buildFieldAliasesHaystack(hit);
        if (aliasesHaystack.isEmpty()) {
            return 0;
        }
        int matchCount = 0;
        for (String token : queryTokens) {
            if (token.length() < 2) {
                continue;
            }
            String lower = token.toLowerCase();
            if (aliasesHaystack.contains(lower)) {
                matchCount++;
                continue;
            }
            if (hasCjkOverlap(aliasesHaystack, token)) {
                matchCount++;
            }
        }
        return matchCount;
    }

    private static String buildFieldAliasesHaystack(QueryArticleHit hit) {
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
            StringBuilder sb = new StringBuilder();
            JsonNode aliases = node.path("fieldAliases");
            if (aliases.isArray()) {
                for (JsonNode alias : aliases) {
                    sb.append(' ').append(alias.asText(""));
                }
            }
            return sb.toString().toLowerCase();
        } catch (Exception ignored) {
            return "";
        }
    }

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
        if (haystack.isEmpty()) {
            return false;
        }
        for (String token : queryTokens) {
            if (token.length() < 2) {
                continue;
            }
            String lowerToken = token.toLowerCase();
            if (haystack.contains(lowerToken)) {
                return true;
            }
            if (hasCjkOverlap(haystack, token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对 CJK token 做字符级 bigram 重叠匹配。
     *
     * 当 tokenizer 将中文片段切分为短 token 时，完整字符串匹配可能失败，
     * 但 token 中的 CJK bigram 可能已在 haystack 中出现。逐 bigram 重叠
     * 检查可稳健处理几乎所有 CJK 碎片匹配场景。
     */
    private static boolean hasCjkOverlap(String haystack, String token) {
        int cjkCount = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                cjkCount++;
            }
        }
        if (cjkCount < 2) {
            return false;
        }
        String lowerToken = token.toLowerCase();
        for (int i = 0; i <= lowerToken.length() - 2; i++) {
            String bigram = lowerToken.substring(i, i + 2);
            if (haystack.contains(bigram)) {
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

    /**
     * 从 terminal unit hit 的 metadataJson 中提取 terminalKey。
     *
     * @param hit terminal unit 命中
     * @return terminalKey，空字符串表示不存在
     */
    private static String extractTerminalKey(QueryArticleHit hit) {
        return extractMetadataTextField(hit, "terminalKey");
    }

    /**
     * 从 terminal unit hit 的 metadataJson 中提取 parentPath。
     *
     * @param hit terminal unit 命中
     * @return parentPath，空字符串表示不存在
     */
    private static String extractParentPath(QueryArticleHit hit) {
        return extractMetadataTextField(hit, "parentPath");
    }

    /**
     * 从 terminal unit hit 的 metadataJson 中提取指定文本字段。
     *
     * @param hit terminal unit 命中
     * @param fieldName JSON 字段名
     * @return 字段值，空字符串表示不存在
     */
    private static String extractMetadataTextField(QueryArticleHit hit, String fieldName) {
        if (hit == null) {
            return "";
        }
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
            return node.path(fieldName).asText("").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 判断 terminal unit hit 所属实体的上下文是否与 query 相关。
     *
     * 只使用 metadataJson 中的 entity-level 结构信号。不使用 hit.getContent()
     * （content 包含 display_text + field_description + field_aliases_json，
     * 会将字段语义重新带入 entity context，破坏单目标保护）。
     *
     * @param hit terminal unit 命中
     * @param queryTokens 查询 token
     * @param question 原始问题文本
     * @return 实体上下文匹配返回 true
     */
    private static boolean entityContextMatchesQuery(QueryArticleHit hit, List<String> queryTokens, String question) {
        String haystack = buildEntityContextHaystack(hit);
        if (!haystack.isEmpty() && queryTokens != null && !queryTokens.isEmpty()) {
            for (String token : queryTokens) {
                if (token.length() < 2) {
                    continue;
                }
                String lower = token.toLowerCase();
                if (haystack.contains(lower)) {
                    return true;
                }
                if (hasCjkOverlap(haystack, token)) {
                    return true;
                }
            }
        }
        return entityContextDisplayValueMatchesRawQuestion(hit, question);
    }

    /**
     * 判断原始问题文本是否显式包含 entity context display value。
     *
     * 仅消费 metadataJson.contextDisplayValues，不读取 content 或字段语义字段；
     * 用于补足 query tokenizer 漏掉后半句实体时的通用 entity-level 匹配。
     *
     * @param hit terminal unit 命中
     * @param question 原始问题文本
     * @return 原始问题包含上下文显示值时返回 true
     */
    private static boolean entityContextDisplayValueMatchesRawQuestion(QueryArticleHit hit, String question) {
        String normalizedQuestion = normalizeEntityContextText(question);
        if (normalizedQuestion.isEmpty()) {
            return false;
        }
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
            JsonNode contextDisplayValues = node.path("contextDisplayValues");
            if (!contextDisplayValues.isArray()) {
                return false;
            }
            for (JsonNode contextDisplayValue : contextDisplayValues) {
                if (!contextDisplayValue.isTextual()) {
                    continue;
                }
                String normalizedValue = normalizeEntityContextText(contextDisplayValue.asText(""));
                if (normalizedValue.length() >= 2 && normalizedQuestion.contains(normalizedValue)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 归一化 entity context 文本用于包含匹配。
     *
     * @param text 原始文本
     * @return 小写并去除空白和标点后的文本
     */
    private static String normalizeEntityContextText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lowerText = text.toLowerCase();
        StringBuilder sb = new StringBuilder(lowerText.length());
        for (int index = 0; index < lowerText.length(); index++) {
            char c = lowerText.charAt(index);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 构建 terminal unit hit 的实体上下文匹配文本。
     *
     * 只从 metadataJson 中提取 entity-level 结构信号（parentPath、pathSegments、
     * 以及名称包含 "context" 的字段值）。显式排除所有字段语义字段。
     * 不使用 hit.getContent()——content 包含 display_text + field_description
     * + field_aliases_json，会将字段语义重新带入 entity context，破坏单目标保护。
     *
     * @param hit terminal unit 命中
     * @return 实体上下文文本
     */
    private static String buildEntityContextHaystack(QueryArticleHit hit) {
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
            StringBuilder sb = new StringBuilder();
            sb.append(node.path("parentPath").asText(""));
            JsonNode segments = node.path("pathSegments");
            if (segments.isArray()) {
                for (JsonNode segment : segments) {
                    if (!segment.isNull()) {
                        sb.append(' ').append(segment.asText(""));
                    }
                }
            }
            node.fieldNames().forEachRemaining(fieldName -> {
                if (fieldName.contains("context")) {
                    JsonNode contextValue = node.path(fieldName);
                    if (contextValue.isArray()) {
                        for (JsonNode item : contextValue) {
                            if (!item.isNull() && item.isTextual()) {
                                sb.append(' ').append(item.asText(""));
                            }
                        }
                    } else if (contextValue.isTextual()) {
                        sb.append(' ').append(contextValue.asText(""));
                    }
                }
            });
            return sb.toString().toLowerCase();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 按 parentPath 去重，每个 parentPath 只保留 ftmc（然后 atmc）最高的候选。
     *
     * @param profiles 候选 profile 列表
     * @return 去重后的列表
     */
    private static List<CandidateProfile> deduplicateByParentPath(List<CandidateProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        Map<String, CandidateProfile> bestByParentPath = new LinkedHashMap<String, CandidateProfile>();
        for (CandidateProfile profile : profiles) {
            String key = profile.parentPath != null ? profile.parentPath : "";
            CandidateProfile existing = bestByParentPath.get(key);
            if (existing == null
                    || profile.ftmc > existing.ftmc
                    || (profile.ftmc == existing.ftmc && profile.atmc > existing.atmc)) {
                bestByParentPath.put(key, profile);
            }
        }
        return new ArrayList<CandidateProfile>(bestByParentPath.values());
    }

    /**
     * 从去重后的候选中选取 top N 条，按 ftmc desc, atmc desc 排序。
     *
     * @param profiles 去重后的候选列表
     * @param maxCount 最大保留数
     * @return 排序并截断后的列表
     */
    private static List<CandidateProfile> selectTopAdditionalCandidates(List<CandidateProfile> profiles, int maxCount) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        List<CandidateProfile> sorted = new ArrayList<CandidateProfile>(profiles);
        sorted.sort((a, b) -> {
            if (a.ftmc != b.ftmc) {
                return Integer.compare(b.ftmc, a.ftmc);
            }
            if (a.atmc != b.atmc) {
                return Integer.compare(b.atmc, a.atmc);
            }
            return 0;
        });
        int limit = Math.min(sorted.size(), maxCount);
        return sorted.subList(0, limit);
    }

    /**
     * Terminal unit 候选 profile：保存排序与过滤所需的信号。
     */
    private static final class CandidateProfile {
        final QueryArticleHit hit;
        final String exactLine;
        final int ftmc;
        final int atmc;
        final double fusedScore;
        final String parentPath;

        CandidateProfile(
                QueryArticleHit hit,
                String exactLine,
                int ftmc,
                int atmc,
                double fusedScore,
                String parentPath
        ) {
            this.hit = hit;
            this.exactLine = exactLine;
            this.ftmc = ftmc;
            this.atmc = atmc;
            this.fusedScore = fusedScore;
            this.parentPath = parentPath;
        }
    }
}
