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

    private QueryEvidenceRelevanceSupport() {
    }

    /**
     * 提取更适合做相关性判定的高信号 token。
     *
     * @param question 用户问题
     * @return 高信号 token
     */
    public static List<String> extractHighSignalTokens(String question) {
        List<String> exactTokens = new ArrayList<String>();
        List<String> asciiTokens = new ArrayList<String>();
        List<String> hanFourGramTokens = new ArrayList<String>();
        List<String> hanThreeGramTokens = new ArrayList<String>();
        List<String> hanTwoGramTokens = new ArrayList<String>();
        List<String> rawTokens = QueryTokenExtractor.extract(question);
        for (String rawToken : rawTokens) {
            String normalizedToken = normalize(rawToken);
            if (!isUsableHighSignalToken(normalizedToken)) {
                continue;
            }
            if (containsSpecialSignalChar(normalizedToken)) {
                addDistinctToken(exactTokens, normalizedToken);
                continue;
            }
            if (isAsciiToken(normalizedToken)) {
                addDistinctToken(asciiTokens, normalizedToken);
                continue;
            }
            if (normalizedToken.length() >= 4) {
                addDistinctToken(hanFourGramTokens, normalizedToken);
                continue;
            }
            if (normalizedToken.length() == 3) {
                addDistinctToken(hanThreeGramTokens, normalizedToken);
                continue;
            }
            addDistinctToken(hanTwoGramTokens, normalizedToken);
        }
        List<String> highSignalTokens = new ArrayList<String>();
        addRankedTokens(highSignalTokens, exactTokens, 6, 12);
        addRankedTokens(highSignalTokens, asciiTokens, 8, 12);
        addBalancedTokens(highSignalTokens, hanFourGramTokens, 4, 12);
        addBalancedTokens(highSignalTokens, hanThreeGramTokens, 4, 12);
        addBalancedTokens(highSignalTokens, hanTwoGramTokens, 4, 12);
        if (highSignalTokens.isEmpty()) {
            return List.of();
        }
        return highSignalTokens;
    }

    /**
     * 按形态优先级追加 token。
     *
     * @param targetTokens 目标 token
     * @param sourceTokens 来源 token
     * @param groupLimit 当前分组数量上限
     * @param totalLimit 总数量上限
     */
    private static void addRankedTokens(
            List<String> targetTokens,
            List<String> sourceTokens,
            int groupLimit,
            int totalLimit
    ) {
        if (sourceTokens == null || sourceTokens.isEmpty() || groupLimit <= 0 || totalLimit <= 0) {
            return;
        }
        List<String> rankedTokens = new ArrayList<String>(sourceTokens);
        rankedTokens.sort((leftToken, rightToken) -> {
            int rankCompare = Integer.compare(tokenPriority(rightToken), tokenPriority(leftToken));
            if (rankCompare != 0) {
                return rankCompare;
            }
            return Integer.compare(rightToken.length(), leftToken.length());
        });
        int addedCount = 0;
        for (String rankedToken : rankedTokens) {
            addDistinctToken(targetTokens, rankedToken);
            addedCount++;
            if (addedCount >= groupLimit || targetTokens.size() >= totalLimit) {
                break;
            }
        }
    }

    /**
     * 从长问题 token 中按位置均衡抽样，避免只保留开头片段。
     *
     * @param targetTokens 目标 token
     * @param sourceTokens 来源 token
     * @param groupLimit 当前分组数量上限
     * @param totalLimit 总数量上限
     */
    private static void addBalancedTokens(
            List<String> targetTokens,
            List<String> sourceTokens,
            int groupLimit,
            int totalLimit
    ) {
        if (sourceTokens == null || sourceTokens.isEmpty() || groupLimit <= 0 || totalLimit <= 0) {
            return;
        }
        if (sourceTokens.size() <= groupLimit) {
            for (String sourceToken : sourceTokens) {
                addDistinctToken(targetTokens, sourceToken);
                if (targetTokens.size() >= totalLimit) {
                    break;
                }
            }
            return;
        }
        Set<Integer> selectedIndexes = new LinkedHashSet<Integer>();
        int denominator = Math.max(1, groupLimit - 1);
        for (int offset = 0; offset < groupLimit; offset++) {
            int selectedIndex = Math.round((sourceTokens.size() - 1) * (offset / (float) denominator));
            selectedIndexes.add(Integer.valueOf(selectedIndex));
        }
        for (Integer selectedIndex : selectedIndexes) {
            addDistinctToken(targetTokens, sourceTokens.get(selectedIndex.intValue()));
            if (targetTokens.size() >= totalLimit) {
                break;
            }
        }
    }

    /**
     * 去重追加 token。
     *
     * @param tokens token 列表
     * @param token 待追加 token
     */
    private static void addDistinctToken(List<String> tokens, String token) {
        if (token == null || token.isBlank() || tokens.contains(token)) {
            return;
        }
        tokens.add(token);
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
        List<String> exactSignalTokens = extractExactSignalTokens(entityLikeTokens);
        if (!exactSignalTokens.isEmpty()) {
            return countTokenMatches(queryArticleHit, exactSignalTokens) > 0;
        }
        if (!entityLikeTokens.isEmpty()) {
            return countTokenMatches(queryArticleHit, entityLikeTokens) > 0;
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

    /**
     * 提取必须精确覆盖的高信号标识 token。
     *
     * @param entityLikeTokens 实体类 token
     * @return 精确标识 token
     */
    private static List<String> extractExactSignalTokens(List<String> entityLikeTokens) {
        List<String> exactSignalTokens = new ArrayList<String>();
        if (entityLikeTokens == null) {
            return exactSignalTokens;
        }
        for (String entityLikeToken : entityLikeTokens) {
            if (containsSpecialSignalChar(entityLikeToken)) {
                exactSignalTokens.add(entityLikeToken);
            }
        }
        return exactSignalTokens;
    }

    /**
     * 统计 token 在结构化字段、标题说明或正文中的命中数量。
     *
     * @param queryArticleHit 查询命中
     * @param tokens token 列表
     * @return 命中数量
     */
    private static int countTokenMatches(QueryArticleHit queryArticleHit, List<String> tokens) {
        int matchedCount = 0;
        if (tokens == null || tokens.isEmpty()) {
            return matchedCount;
        }
        for (String token : tokens) {
            if (matchesStructuredField(queryArticleHit, token)
                    || matchesTitleOrDescription(queryArticleHit, token)
                    || matchesContent(queryArticleHit, token)) {
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

    private static boolean isUsableHighSignalToken(String token) {
        if (token == null || token.isBlank() || token.length() <= 1) {
            return false;
        }
        if (containsSpecialSignalChar(token)) {
            return true;
        }
        if (isAsciiToken(token)) {
            return token.length() >= 3;
        }
        if (containsHanText(token)) {
            return token.length() >= 2 && !isSingleRepeatedCharacter(token);
        }
        return false;
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

    /**
     * 计算 token 在截断预算中的优先级，只依赖字符形态与长度。
     *
     * @param token token
     * @return 优先级
     */
    private static int tokenPriority(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        if (containsSpecialSignalChar(token)) {
            return 120 + Math.min(token.length(), 40);
        }
        if (isAsciiToken(token)) {
            return 90 + Math.min(token.length(), 30);
        }
        if (containsHanText(token)) {
            return 20 + Math.min(token.length(), 10);
        }
        return token.length();
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

    private static boolean isSingleRepeatedCharacter(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        char firstChar = token.charAt(0);
        for (int index = 0; index < token.length(); index++) {
            if (token.charAt(index) != firstChar) {
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
