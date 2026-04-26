package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 查询证据相关性支持工具
 *
 * 职责：统一提取高信号问题 token，并判定命中是否足够相关
 *
 * @author xiexu
 */
public final class QueryEvidenceRelevanceSupport {

    private static final Set<String> GENERIC_QUERY_TOKENS = Set.of(
            "什么",
            "这个",
            "那个",
            "项目",
            "系统",
            "当前",
            "直接",
            "相关",
            "支持",
            "实现",
            "方式",
            "时机",
            "目标",
            "关键",
            "结论",
            "问题",
            "情况",
            "功能",
            "方案",
            "策略",
            "请按",
            "请从",
            "请结"
    );

    private static final String GENERIC_HAN_CHARS = "这个那个项目支持当前直接相关目标触发时机实现方式关键结论什么请按从与及和是否到底究竟";

    private QueryEvidenceRelevanceSupport() {
    }

    /**
     * 提取更适合做相关性判定的高信号 token。
     *
     * @param question 用户问题
     * @return 高信号 token
     */
    public static List<String> extractHighSignalTokens(String question) {
        Set<String> highSignalTokens = new LinkedHashSet<String>();
        String focusQuestion = extractFocusQuestion(question);
        List<String> rawTokens = QueryTokenExtractor.extract(focusQuestion);
        for (String rawToken : rawTokens) {
            String normalizedToken = normalize(rawToken);
            if (normalizedToken.isBlank() || isGenericToken(normalizedToken)) {
                continue;
            }
            if (isAsciiToken(normalizedToken) && normalizedToken.length() >= 4) {
                highSignalTokens.add(normalizedToken);
                continue;
            }
            if (containsSpecialSignalChar(normalizedToken)) {
                highSignalTokens.add(normalizedToken);
                continue;
            }
            if (containsHanText(normalizedToken) && normalizedToken.length() >= 2) {
                highSignalTokens.add(normalizedToken);
            }
        }
        if (!highSignalTokens.isEmpty()) {
            return new ArrayList<String>(highSignalTokens);
        }
        for (String rawToken : QueryTokenExtractor.extract(question == null ? "" : question)) {
            String normalizedToken = normalize(rawToken);
            if (normalizedToken.isBlank() || isGenericToken(normalizedToken)) {
                continue;
            }
            highSignalTokens.add(normalizedToken);
            if (highSignalTokens.size() >= 3) {
                break;
            }
        }
        return new ArrayList<String>(highSignalTokens);
    }

    /**
     * 过滤足够相关的命中，避免低信号结果污染 fallback / researcher。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 相关命中
     */
    public static List<QueryArticleHit> filterRelevantHits(String question, List<QueryArticleHit> queryArticleHits) {
        List<QueryArticleHit> relevantHits = new ArrayList<QueryArticleHit>();
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return relevantHits;
        }
        List<String> highSignalTokens = extractHighSignalTokens(question);
        if (highSignalTokens.isEmpty()) {
            return relevantHits;
        }
        List<String> entityLikeTokens = extractEntityLikeTokens(highSignalTokens);
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (isRelevant(queryArticleHit, highSignalTokens, entityLikeTokens)) {
                relevantHits.add(queryArticleHit);
            }
        }
        return relevantHits;
    }

    /**
     * 判断单条命中是否足够相关。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 相关返回 true
     */
    public static boolean isRelevant(String question, QueryArticleHit queryArticleHit) {
        List<String> highSignalTokens = extractHighSignalTokens(question);
        if (highSignalTokens.isEmpty()) {
            return false;
        }
        return isRelevant(queryArticleHit, highSignalTokens, extractEntityLikeTokens(highSignalTokens));
    }

    /**
     * 计算单条命中的相关性分值。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 相关性分值
     */
    public static int score(String question, QueryArticleHit queryArticleHit) {
        return score(queryArticleHit, extractHighSignalTokens(question));
    }

    private static boolean isRelevant(
            QueryArticleHit queryArticleHit,
            List<String> highSignalTokens,
            List<String> entityLikeTokens
    ) {
        if (queryArticleHit == null || highSignalTokens == null || highSignalTokens.isEmpty()) {
            return false;
        }
        int score = score(queryArticleHit, highSignalTokens);
        if (score <= 0) {
            return false;
        }
        if (!entityLikeTokens.isEmpty()) {
            return countStructuredFieldMatches(queryArticleHit, entityLikeTokens) > 0 || score >= 8;
        }
        return countStrongTokenMatches(queryArticleHit, highSignalTokens) >= 1 || score >= 5;
    }

    private static int score(QueryArticleHit queryArticleHit, List<String> highSignalTokens) {
        if (queryArticleHit == null || highSignalTokens == null || highSignalTokens.isEmpty()) {
            return 0;
        }
        int totalScore = 0;
        for (String highSignalToken : highSignalTokens) {
            if (highSignalToken == null || highSignalToken.isBlank()) {
                continue;
            }
            int tokenScore = tokenScore(highSignalToken);
            int matchedScore = 0;
            if (matchesStructuredField(queryArticleHit, highSignalToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 6);
            }
            if (matchesTitleOrDescription(queryArticleHit, highSignalToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 4);
            }
            if (matchesContent(queryArticleHit, highSignalToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 2);
            }
            totalScore += matchedScore;
        }
        return totalScore;
    }

    private static int countStrongTokenMatches(QueryArticleHit queryArticleHit, List<String> highSignalTokens) {
        int matchedCount = 0;
        for (String highSignalToken : highSignalTokens) {
            if (!isStrongToken(highSignalToken)) {
                continue;
            }
            if (matchesStructuredField(queryArticleHit, highSignalToken)
                    || matchesTitleOrDescription(queryArticleHit, highSignalToken)
                    || matchesContent(queryArticleHit, highSignalToken)) {
                matchedCount++;
            }
        }
        return matchedCount;
    }

    private static int countStructuredFieldMatches(QueryArticleHit queryArticleHit, List<String> entityLikeTokens) {
        int matchedCount = 0;
        for (String entityLikeToken : entityLikeTokens) {
            if (matchesStructuredField(queryArticleHit, entityLikeToken)) {
                matchedCount++;
            }
        }
        return matchedCount;
    }

    private static List<String> extractEntityLikeTokens(List<String> highSignalTokens) {
        List<String> entityLikeTokens = new ArrayList<String>();
        if (highSignalTokens == null) {
            return entityLikeTokens;
        }
        for (String highSignalToken : highSignalTokens) {
            if (isEntityLikeToken(highSignalToken)) {
                entityLikeTokens.add(highSignalToken);
            }
        }
        return entityLikeTokens;
    }

    private static boolean matchesStructuredField(QueryArticleHit queryArticleHit, String token) {
        if (queryArticleHit == null || token == null || token.isBlank()) {
            return false;
        }
        if (containsToken(queryArticleHit.getArticleKey(), token)
                || containsToken(queryArticleHit.getConceptId(), token)
                || containsToken(queryArticleHit.getTitle(), token)
                || containsToken(extractDescription(queryArticleHit.getMetadataJson()), token)) {
            return true;
        }
        if (queryArticleHit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : queryArticleHit.getSourcePaths()) {
            if (containsToken(sourcePath, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTitleOrDescription(QueryArticleHit queryArticleHit, String token) {
        return containsToken(queryArticleHit == null ? null : queryArticleHit.getTitle(), token)
                || containsToken(queryArticleHit == null ? null : extractDescription(queryArticleHit.getMetadataJson()), token);
    }

    private static boolean matchesContent(QueryArticleHit queryArticleHit, String token) {
        return containsToken(queryArticleHit == null ? null : queryArticleHit.getContent(), token);
    }

    private static boolean containsToken(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return false;
        }
        return normalize(value).contains(normalize(token));
    }

    private static String extractFocusQuestion(String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        int compareIndex = normalizedQuestion.indexOf("对比");
        if (compareIndex >= 0 && compareIndex < normalizedQuestion.length() - 2) {
            normalizedQuestion = normalizedQuestion.substring(compareIndex + 2).trim();
        }
        normalizedQuestion = normalizedQuestion.replace("有什么区别", "");
        normalizedQuestion = normalizedQuestion.replace("区别是什么", "");
        normalizedQuestion = normalizedQuestion.replace("的关键结论是什么", "");
        normalizedQuestion = normalizedQuestion.replaceAll("[？?。！!]+$", "");
        if (normalizedQuestion.endsWith("是什么")) {
            normalizedQuestion = normalizedQuestion.substring(0, normalizedQuestion.length() - 3).trim();
        }
        if (normalizedQuestion.endsWith("吗")) {
            normalizedQuestion = normalizedQuestion.substring(0, normalizedQuestion.length() - 1).trim();
        }
        return normalizedQuestion;
    }

    private static boolean isGenericToken(String token) {
        return GENERIC_QUERY_TOKENS.contains(token)
                || token.length() <= 1
                || token.startsWith("请")
                || token.endsWith("什么")
                || containsOnlyGenericHanChars(token);
    }

    private static boolean isEntityLikeToken(String token) {
        return containsSpecialSignalChar(token) || isAsciiToken(token);
    }

    private static boolean isStrongToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (isEntityLikeToken(token)) {
            return true;
        }
        return containsHanText(token) && token.length() >= 3;
    }

    private static int tokenScore(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        if (containsSpecialSignalChar(token)) {
            return 6;
        }
        if (isAsciiToken(token)) {
            return token.length() >= 6 ? 5 : 4;
        }
        if (containsHanText(token) && token.length() >= 4) {
            return 5;
        }
        if (containsHanText(token) && token.length() == 3) {
            return 4;
        }
        return 3;
    }

    private static boolean containsSpecialSignalChar(String token) {
        return token != null
                && (token.contains("_")
                || token.contains("-")
                || token.contains("=")
                || token.contains("/")
                || token.contains("."));
    }

    private static boolean isAsciiToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if ((value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean containsHanText(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            if (Character.UnicodeScript.of(token.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOnlyGenericHanChars(String token) {
        if (token == null || token.isBlank() || !containsHanText(token)) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char currentChar = token.charAt(index);
            if (Character.UnicodeScript.of(currentChar) != Character.UnicodeScript.HAN) {
                return false;
            }
            if (GENERIC_HAN_CHARS.indexOf(currentChar) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String extractDescription(String metadataJson) {
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
