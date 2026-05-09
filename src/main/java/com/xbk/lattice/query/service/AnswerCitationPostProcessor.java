package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * 答案引用后处理器
 *
 * 职责：为结构化 Markdown 答案补齐、替换与归位通用 citation
 *
 * 不属于本类的事：不做 prompt 构造、不调用 LLM、不处理特定业务问法
 *
 * @author xiexu
 */
final class AnswerCitationPostProcessor {

    private final AnswerGenerationService support;

    private final AnswerCitationResolver citationResolver;

    /**
     * 创建答案引用后处理器。
     *
     * @param support 答案生成支撑逻辑
     * @param citationResolver citation 解析器
     */
    AnswerCitationPostProcessor(
            AnswerGenerationService support,
            AnswerCitationResolver citationResolver
    ) {
        this.support = support;
        this.citationResolver = citationResolver;
    }

    /**
     * 当模型返回了结构化 JSON 但部分正文行缺少 citation 时，使用当前最相关证据补上默认引用。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 至少尝试补过引用的答案
     */
    String attachDefaultCitationWhenMissing(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        String defaultCitation = defaultCitationLiteral(question, queryArticleHits);
        if (defaultCitation.isBlank() && (queryArticleHits == null || queryArticleHits.isEmpty())) {
            return answerMarkdown;
        }
        String[] rawLines = answerMarkdown.split("\\R", -1);
        List<String> citedLines = new ArrayList<String>();
        boolean citationAttached = false;
        for (int index = 0; index < rawLines.length; index++) {
            String rawLine = rawLines[index];
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (looksLikeMarkdownTableHeaderLine(rawLines, index)) {
                citedLines.add(rawLine);
                continue;
            }
            if (!shouldAutoAttachCitation(normalizedLine)) {
                citedLines.add(rawLine);
                continue;
            }
            String lineCitation = bestCitationLiteralForLine(question, normalizedLine, queryArticleHits, defaultCitation);
            String supportedLine = removeEvidenceInsufficientMarkerIfSupported(
                    rawLine,
                    question,
                    normalizedLine,
                    queryArticleHits
            );
            if (lineCitation.isBlank()) {
                citedLines.add(supportedLine);
                continue;
            }
            if (citationResolver.containsCitationLiteral(normalizedLine)
                    && looksLikeGenericCitationCarrierLine(normalizedLine)) {
                citedLines.add(citationResolver.stripEmbeddedCitationLiterals(supportedLine));
                citationAttached = true;
                continue;
            }
            if (!citationResolver.containsCitationLiteral(normalizedLine)) {
                citedLines.add(appendCitationToLine(supportedLine, normalizedLine, lineCitation));
                citationAttached = true;
                continue;
            }
            if (!hasRelevantCitationForLine(question, normalizedLine, queryArticleHits)) {
                String lineWithoutCitation = replaceLineCitations(supportedLine, lineCitation);
                citedLines.add(lineWithoutCitation);
                citationAttached = true;
            }
            else {
                citedLines.add(supportedLine);
            }
        }
        String citedAnswer = String.join("\n", citedLines).trim();
        if (!citationAttached && !citationResolver.containsCitationLiteral(answerMarkdown)) {
            return answerMarkdown.trim() + " " + defaultCitation;
        }
        return citedAnswer;
    }

    private boolean looksLikeGenericCitationCarrierLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lineWithoutCitations = lowerCase(citationResolver.stripEmbeddedCitationLiterals(normalizedLine));
        return lineWithoutCitations.matches("^(简表|表格|对比表|总结表)(如下|如下所示)?[:：。]?$")
                || lineWithoutCitations.matches("^下面(用|以)?(简表|表格|对比表)(说明|展示)?[:：。]?$");
    }

    private String removeEvidenceInsufficientMarkerIfSupported(
            String rawLine,
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (rawLine == null || !rawLine.contains("当前证据不足")) {
            return rawLine;
        }
        int bestScore = bestCitationScoreForLine(question, normalizedLine, queryArticleHits);
        if (bestScore < 8) {
            return rawLine;
        }
        return rawLine
                .replace("（当前证据不足）", "")
                .replace("(当前证据不足)", "")
                .replace("当前证据不足", "")
                .replaceAll("\\s+([，。；：])", "$1");
    }

    private boolean hasRelevantCitationForLine(
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return false;
        }
        List<String> citationLiterals = extractCitationLiterals(normalizedLine);
        if (citationLiterals.isEmpty()) {
            return false;
        }
        int bestAvailableScore = bestCitationScoreForLine(question, normalizedLine, queryArticleHits);
        for (String citationLiteral : citationLiterals) {
            int citationScore = scoreExistingCitationForLine(question, normalizedLine, citationLiteral, queryArticleHits);
            if (citationScore >= Math.max(4, bestAvailableScore - 8)) {
                return true;
            }
        }
        return false;
    }

    private String bestCitationLiteralForLine(
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits,
            String defaultCitation
    ) {
        QueryArticleHit bestHit = bestCitationHitForLine(question, normalizedLine, queryArticleHits);
        if (bestHit == null) {
            return defaultCitation == null ? "" : defaultCitation;
        }
        String citationLiteral = citationResolver.resolveConclusionCitationLiteral(bestHit, queryArticleHits);
        return citationLiteral.isBlank() ? (defaultCitation == null ? "" : defaultCitation) : citationLiteral;
    }

    private int bestCitationScoreForLine(String question, String normalizedLine, List<QueryArticleHit> queryArticleHits) {
        QueryArticleHit bestHit = bestCitationHitForLine(question, normalizedLine, queryArticleHits);
        return bestHit == null ? Integer.MIN_VALUE : scoreCitationLineAgainstHit(question, normalizedLine, bestHit);
    }

    private QueryArticleHit bestCitationHitForLine(
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return null;
        }
        QueryArticleHit bestHit = null;
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            int score = scoreCitationLineAgainstHit(question, normalizedLine, queryArticleHit);
            if (score > bestScore) {
                bestScore = score;
                bestHit = queryArticleHit;
            }
        }
        if (bestHit == null || bestScore <= 0) {
            List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
            return fallbackHits.isEmpty() ? null : fallbackHits.get(0);
        }
        return bestHit;
    }

    private int scoreExistingCitationForLine(
            String question,
            String normalizedLine,
            String citationLiteral,
            List<QueryArticleHit> queryArticleHits
    ) {
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (!citationResolver.citationLiteralMatchesHit(citationLiteral, queryArticleHit)) {
                continue;
            }
            bestScore = Math.max(bestScore, scoreCitationLineAgainstHit(question, normalizedLine, queryArticleHit));
        }
        return bestScore;
    }

    private int scoreCitationLineAgainstHit(String question, String normalizedLine, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || normalizedLine == null || normalizedLine.isBlank()) {
            return Integer.MIN_VALUE;
        }
        String claimLine = citationResolver.stripEmbeddedCitationLiterals(normalizedLine)
                .replace("当前证据不足", "")
                .replaceAll("[（(）)]", " ")
                .trim();
        List<String> claimTokens = extractCitationLineTokens(claimLine);
        int score = 0;
        if (claimTokens.isEmpty()) {
            score += QueryEvidenceRelevanceSupport.score(question, queryArticleHit);
        }
        for (String claimToken : claimTokens) {
            int tokenScore = citationTokenWeight(claimToken);
            int matchedScore = 0;
            if (matchesCitationStructuredField(queryArticleHit, claimToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 8);
            }
            if (containsNormalizedToken(queryArticleHit.getContent(), claimToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 2);
            }
            score += matchedScore;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            score += 2;
        }
        score += Math.min(QueryEvidenceRelevanceSupport.score(question, queryArticleHit), 6);
        return score;
    }

    private List<String> extractCitationLineTokens(String value) {
        List<String> tokens = new ArrayList<String>();
        for (String token : QueryTokenExtractor.extract(value)) {
            String normalizedToken = lowerCase(token);
            if (isUsefulCitationLineToken(normalizedToken) && !tokens.contains(normalizedToken)) {
                tokens.add(normalizedToken);
            }
        }
        return tokens;
    }

    private boolean isUsefulCitationLineToken(String token) {
        if (token == null || token.isBlank() || token.length() <= 1) {
            return false;
        }
        if (List.of(
                "当前",
                "证据",
                "不足",
                "包括",
                "主要",
                "完成",
                "工作",
                "需要",
                "关注",
                "确认",
                "字段",
                "渠道",
                "支持",
                "相关",
                "用户"
        ).contains(token)) {
            return false;
        }
        return true;
    }

    private int citationTokenWeight(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        if (token.matches(".*[0-9=_./-].*")) {
            return 6;
        }
        if (token.matches("[a-z0-9_-]+")) {
            return token.length() >= 6 ? 5 : 4;
        }
        return token.length() >= 3 ? 4 : 2;
    }

    private boolean matchesCitationStructuredField(QueryArticleHit queryArticleHit, String token) {
        if (queryArticleHit == null || token == null || token.isBlank()) {
            return false;
        }
        if (containsNormalizedToken(queryArticleHit.getArticleKey(), token)
                || containsNormalizedToken(queryArticleHit.getConceptId(), token)
                || containsNormalizedToken(queryArticleHit.getTitle(), token)
                || containsNormalizedToken(extractDescription(queryArticleHit.getMetadataJson()), token)) {
            return true;
        }
        if (queryArticleHit.getSourcePaths() != null) {
            for (String sourcePath : queryArticleHit.getSourcePaths()) {
                if (containsNormalizedToken(sourcePath, token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsNormalizedToken(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return false;
        }
        return lowerCase(value).contains(lowerCase(token));
    }

    private List<String> extractCitationLiterals(String normalizedLine) {
        List<String> citationLiterals = new ArrayList<String>();
        Matcher articleMatcher = java.util.regex.Pattern.compile("\\[\\[[^\\]]+]]").matcher(normalizedLine);
        while (articleMatcher.find()) {
            citationLiterals.add(articleMatcher.group());
        }
        Matcher sourceMatcher = java.util.regex.Pattern.compile("\\[→\\s*[^\\]]+]").matcher(normalizedLine);
        while (sourceMatcher.find()) {
            citationLiterals.add(sourceMatcher.group());
        }
        return citationLiterals;
    }

    private String replaceLineCitations(String rawLine, String citationLiteral) {
        String lineWithoutCitations = citationResolver.stripEmbeddedCitationLiterals(rawLine).trim();
        if (lineWithoutCitations.isBlank()) {
            return rawLine;
        }
        String leadingWhitespace = rawLine == null ? "" : rawLine.replaceFirst("^(\\s*).*$", "$1");
        return leadingWhitespace + lineWithoutCitations + " " + citationLiteral;
    }

    private String defaultCitationLiteral(String question, List<QueryArticleHit> queryArticleHits) {
        List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String citationLiteral = citationResolver.resolveConclusionCitationLiteral(fallbackHit, queryArticleHits);
            if (!citationLiteral.isBlank()) {
                return citationLiteral;
            }
        }
        if (queryArticleHits != null) {
            for (QueryArticleHit queryArticleHit : queryArticleHits) {
                String citationLiteral = citationResolver.resolveConclusionCitationLiteral(queryArticleHit, queryArticleHits);
                if (!citationLiteral.isBlank()) {
                    return citationLiteral;
                }
            }
        }
        return "";
    }

    private boolean shouldAutoAttachCitation(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        if (normalizedLine.startsWith("|")) {
            return looksLikeMarkdownTableDataRow(normalizedLine);
        }
        return !normalizedLine.startsWith("#")
                && !lowerCaseLine.startsWith("```")
                && !lowerCaseLine.startsWith("~~~");
    }

    private String appendCitationToLine(String rawLine, String normalizedLine, String citationLiteral) {
        if (rawLine == null || rawLine.isBlank() || citationLiteral == null || citationLiteral.isBlank()) {
            return rawLine;
        }
        if (!looksLikeMarkdownTableDataRow(normalizedLine)) {
            return rawLine + " " + citationLiteral;
        }
        int lastPipeIndex = rawLine.lastIndexOf('|');
        if (lastPipeIndex <= 0) {
            return rawLine + " " + citationLiteral;
        }
        String beforeLastPipe = rawLine.substring(0, lastPipeIndex).stripTrailing();
        String afterLastPipe = rawLine.substring(lastPipeIndex);
        return beforeLastPipe + " " + citationLiteral + " " + afterLastPipe;
    }

    private boolean looksLikeMarkdownTableDataRow(String normalizedLine) {
        if (normalizedLine == null || !normalizedLine.startsWith("|")) {
            return false;
        }
        String compactLine = normalizedLine.replace("|", "")
                .replace(":", "")
                .replace("-", "")
                .trim();
        if (compactLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return !lowerCaseLine.contains("|---")
                && !lowerCaseLine.contains("| ---")
                && !lowerCaseLine.contains("|:---")
                && !lowerCaseLine.contains("| :---");
    }

    private boolean looksLikeMarkdownTableHeaderLine(String[] rawLines, int index) {
        if (rawLines == null || index < 0 || index + 1 >= rawLines.length) {
            return false;
        }
        String currentLine = rawLines[index] == null ? "" : rawLines[index].trim();
        String nextLine = rawLines[index + 1] == null ? "" : rawLines[index + 1].trim();
        return currentLine.startsWith("|") && looksLikeMarkdownTableSeparatorLine(nextLine);
    }

    private boolean looksLikeMarkdownTableSeparatorLine(String normalizedLine) {
        if (normalizedLine == null || !normalizedLine.startsWith("|")) {
            return false;
        }
        String compactLine = normalizedLine.replace("|", "")
                .replace(":", "")
                .replace("-", "")
                .trim();
        return compactLine.isBlank();
    }

    private String extractDescription(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        String marker = "\"description\":";
        int markerIndex = metadataJson.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int quoteStart = metadataJson.indexOf('"', markerIndex + marker.length());
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = metadataJson.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return "";
        }
        return metadataJson.substring(quoteStart + 1, quoteEnd);
    }

    private String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
