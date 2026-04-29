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
            "请结",
            "哪些",
            "需要",
            "执行",
            "动作",
            "日常",
            "维护"
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
        boolean flowQuestion = looksLikeFlowQuestion(question);
        boolean capabilityQuestion = looksLikeCapabilityQuestion(question);
        boolean operationalMaintenanceQuestion = looksLikeOperationalMaintenanceQuestion(question);
        List<String> highSignalTokens = extractHighSignalTokens(question);
        if (highSignalTokens.isEmpty() && !flowQuestion) {
            return relevantHits;
        }
        boolean capabilityTopicQuestion = capabilityQuestion && containsCapabilityTopic(highSignalTokens);
        List<String> entityLikeTokens = extractEntityLikeTokens(highSignalTokens);
        List<String> operationalAnchorTokens = extractOperationalAnchorTokens(highSignalTokens);
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (operationalMaintenanceQuestion
                    && !operationalAnchorTokens.isEmpty()
                    && !matchesAnyToken(queryArticleHit, operationalAnchorTokens)) {
                continue;
            }
            if (flowQuestion
                    && containsFlowSignal(queryArticleHit)
                    && shouldAcceptFlowSignalHit(queryArticleHit, highSignalTokens, entityLikeTokens)) {
                relevantHits.add(queryArticleHit);
                continue;
            }
            if (capabilityTopicQuestion && containsCapabilitySignal(queryArticleHit)) {
                relevantHits.add(queryArticleHit);
                continue;
            }
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
        if (looksLikeFlowQuestion(question) && containsFlowSignal(queryArticleHit)) {
            return shouldAcceptFlowSignalHit(
                    queryArticleHit,
                    highSignalTokens,
                    extractEntityLikeTokens(highSignalTokens)
            );
        }
        if (looksLikeCapabilityQuestion(question)
                && containsCapabilityTopic(highSignalTokens)
                && containsCapabilitySignal(queryArticleHit)) {
            return true;
        }
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

    private static boolean shouldAcceptFlowSignalHit(
            QueryArticleHit queryArticleHit,
            List<String> highSignalTokens,
            List<String> entityLikeTokens
    ) {
        if (highSignalTokens == null || highSignalTokens.isEmpty()) {
            return true;
        }
        if (entityLikeTokens == null || entityLikeTokens.isEmpty()) {
            return true;
        }
        for (String entityLikeToken : entityLikeTokens) {
            if (matchesStructuredField(queryArticleHit, entityLikeToken)
                    || matchesContent(queryArticleHit, entityLikeToken)) {
                return true;
            }
        }
        return false;
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

    /**
     * 提取运维补偿类问题中的强领域锚点。
     *
     * @param highSignalTokens 高信号 token
     * @return 强领域锚点
     */
    private static List<String> extractOperationalAnchorTokens(List<String> highSignalTokens) {
        List<String> anchorTokens = new ArrayList<String>();
        if (highSignalTokens == null) {
            return anchorTokens;
        }
        for (String highSignalToken : highSignalTokens) {
            String normalizedToken = normalize(highSignalToken);
            if (normalizedToken.contains("trans")
                    || normalizedToken.contains("xxljob")
                    || normalizedToken.contains("job")
                    || normalizedToken.contains("bogo")
                    || normalizedToken.contains("买一赠一")
                    || normalizedToken.contains("补偿")) {
                anchorTokens.add(highSignalToken);
            }
        }
        return anchorTokens;
    }

    /**
     * 判断命中是否匹配任一领域锚点。
     *
     * @param queryArticleHit 查询命中
     * @param anchorTokens 领域锚点
     * @return 任一锚点命中返回 true
     */
    private static boolean matchesAnyToken(QueryArticleHit queryArticleHit, List<String> anchorTokens) {
        if (queryArticleHit == null || anchorTokens == null || anchorTokens.isEmpty()) {
            return false;
        }
        for (String anchorToken : anchorTokens) {
            if (matchesStructuredField(queryArticleHit, anchorToken)
                    || matchesTitleOrDescription(queryArticleHit, anchorToken)
                    || matchesContent(queryArticleHit, anchorToken)) {
                return true;
            }
        }
        return false;
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

    private static boolean looksLikeFlowQuestion(String question) {
        String normalizedQuestion = normalize(question);
        return normalizedQuestion.contains("流程")
                || normalizedQuestion.contains("链路")
                || normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("怎么跑")
                || normalizedQuestion.contains("怎么走")
                || normalizedQuestion.contains("运行路径");
    }

    /**
     * 判断是否为运维补偿类动作问题。
     *
     * @param question 用户问题
     * @return 运维补偿动作题返回 true
     */
    private static boolean looksLikeOperationalMaintenanceQuestion(String question) {
        String normalizedQuestion = normalize(question);
        boolean hasOperationalAnchor = normalizedQuestion.contains("trans-job")
                || normalizedQuestion.contains("xxljob")
                || normalizedQuestion.contains("补偿");
        boolean asksMaintenanceAction = normalizedQuestion.contains("维护")
                || normalizedQuestion.contains("动作")
                || normalizedQuestion.contains("执行")
                || normalizedQuestion.contains("任务");
        return hasOperationalAnchor && asksMaintenanceAction;
    }

    private static boolean looksLikeCapabilityQuestion(String question) {
        String normalizedQuestion = normalize(question);
        return normalizedQuestion.contains("支持")
                || normalizedQuestion.contains("接入")
                || normalizedQuestion.contains("入口")
                || normalizedQuestion.contains("方式")
                || normalizedQuestion.contains("能力")
                || normalizedQuestion.contains("有哪些");
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

    private static boolean containsFlowSignal(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        if (containsFlowSignal(queryArticleHit.getTitle())
                || containsFlowSignal(queryArticleHit.getContent())
                || containsFlowSignal(extractDescription(queryArticleHit.getMetadataJson()))) {
            return true;
        }
        if (queryArticleHit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : queryArticleHit.getSourcePaths()) {
            if (containsFlowSignal(sourcePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFlowSignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = normalize(value);
        return value.contains("->")
                || normalizedValue.contains("主链路")
                || normalizedValue.contains("链路")
                || normalizedValue.contains("流程")
                || normalizedValue.contains("source sync")
                || normalizedValue.contains("compile graph")
                || normalizedValue.contains("query graph")
                || normalizedValue.contains("pending_queries")
                || normalizedValue.contains("contributions")
                || normalizedValue.contains("资料先进入")
                || normalizedValue.contains("再进入编译层")
                || normalizedValue.contains("提交问题")
                || normalizedValue.contains("启动问答链路");
    }

    private static boolean containsCapabilitySignal(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        if (containsCapabilitySignal(queryArticleHit.getTitle())
                || containsCapabilitySignal(queryArticleHit.getContent())
                || containsCapabilitySignal(extractDescription(queryArticleHit.getMetadataJson()))) {
            return true;
        }
        if (queryArticleHit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : queryArticleHit.getSourcePaths()) {
            if (containsCapabilitySignal(sourcePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCapabilitySignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = normalize(value);
        int listSeparatorCount = countOccurrences(value, "、")
                + countOccurrences(value, " / ")
                + countOccurrences(value, "·");
        int backtickCount = countOccurrences(value, "`");
        return normalizedValue.contains("api")
                || normalizedValue.contains("cli")
                || normalizedValue.contains("mcp")
                || normalizedValue.contains("http")
                || normalizedValue.contains("web")
                || normalizedValue.contains("sdk")
                || normalizedValue.contains("入口")
                || normalizedValue.contains("接入")
                || listSeparatorCount >= 2
                || backtickCount >= 4;
    }

    private static int countOccurrences(String value, String token) {
        if (value == null || value.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int fromIndex = 0;
        while (fromIndex >= 0) {
            fromIndex = value.indexOf(token, fromIndex);
            if (fromIndex < 0) {
                break;
            }
            count++;
            fromIndex += token.length();
        }
        return count;
    }

    private static boolean containsCapabilityTopic(List<String> highSignalTokens) {
        if (highSignalTokens == null || highSignalTokens.isEmpty()) {
            return false;
        }
        for (String highSignalToken : highSignalTokens) {
            String normalizedToken = normalize(highSignalToken);
            if (normalizedToken.contains("开发")
                    || normalizedToken.contains("接入")
                    || normalizedToken.contains("入口")
                    || normalizedToken.contains("方式")
                    || normalizedToken.contains("能力")
                    || normalizedToken.contains("api")
                    || normalizedToken.contains("cli")
                    || normalizedToken.contains("mcp")
                    || normalizedToken.contains("http")
                    || normalizedToken.contains("web")
                    || normalizedToken.contains("sdk")) {
                return true;
            }
        }
        return false;
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
