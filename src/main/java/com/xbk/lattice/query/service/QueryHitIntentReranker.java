package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 查询命中意图重排器
 *
 * 职责：在进入 RRF 与 fallback 前，按查询意图对截图/OCR/元文档降噪，并提升更像直接答案的结构化证据
 *
 * @author xiexu
 */
public final class QueryHitIntentReranker {

    private QueryHitIntentReranker() {
    }

    /**
     * 按查询意图重排命中。
     *
     * @param question 检索问题
     * @param queryIntent 查询意图
     * @param hits 原始命中
     * @return 重排后的命中
     */
    public static List<QueryArticleHit> rerank(String question, QueryIntent queryIntent, List<QueryArticleHit> hits) {
        if (hits == null || hits.size() <= 1 || queryIntent == null) {
            return hits == null ? List.of() : hits;
        }
        List<QueryArticleHit> adjustedHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit hit : hits) {
            adjustedHits.add(withAdjustedScore(hit, question, queryIntent));
        }
        adjustedHits.sort((leftHit, rightHit) -> {
            int scoreCompare = Double.compare(rightHit.getScore(), leftHit.getScore());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int relevanceCompare = Integer.compare(
                    QueryEvidenceRelevanceSupport.score(question, rightHit),
                    QueryEvidenceRelevanceSupport.score(question, leftHit)
            );
            if (relevanceCompare != 0) {
                return relevanceCompare;
            }
            return lowerCase(leftHit.getTitle()).compareTo(lowerCase(rightHit.getTitle()));
        });
        return adjustedHits;
    }

    /**
     * 为单条命中计算意图加权后的新分值。
     *
     * @param hit 原始命中
     * @param question 检索问题
     * @param queryIntent 查询意图
     * @return 调整后的命中
     */
    private static QueryArticleHit withAdjustedScore(QueryArticleHit hit, String question, QueryIntent queryIntent) {
        if (hit == null) {
            return null;
        }
        double adjustedScore = hit.getScore() + intentBonus(question, queryIntent, hit);
        return new QueryArticleHit(
                hit.getEvidenceType(),
                hit.getSourceId(),
                hit.getArticleKey(),
                hit.getConceptId(),
                hit.getTitle(),
                hit.getContent(),
                hit.getMetadataJson(),
                hit.getSourcePaths(),
                adjustedScore
        );
    }

    /**
     * 计算单条命中的意图加权。
     *
     * @param question 检索问题
     * @param queryIntent 查询意图
     * @param hit 查询命中
     * @return 分值增量
     */
    private static double intentBonus(String question, QueryIntent queryIntent, QueryArticleHit hit) {
        double bonus = 0.0D;
        boolean imageLikeHit = isImageLikeHit(hit);
        boolean metaDocHit = isMetaDocHit(hit);
        if (queryIntent == QueryIntent.CONFIGURATION) {
            if (matchesConfigFactCandidate(question, hit)) {
                bonus += 18.0D;
            }
            if (matchesConfigFriendlyPath(hit)) {
                bonus += 6.0D;
            }
            if (imageLikeHit) {
                bonus -= 18.0D;
            }
            if (metaDocHit) {
                bonus -= 10.0D;
            }
            return bonus;
        }
        if (queryIntent == QueryIntent.ARCHITECTURE || looksLikeArchitectureQuestion(question)) {
            if (matchesArchitectureFriendlyPath(hit)) {
                bonus += 12.0D;
            }
            if (imageLikeHit) {
                bonus -= 14.0D;
            }
            if (metaDocHit) {
                bonus -= 8.0D;
            }
        }
        return bonus;
    }

    /**
     * 判断当前命中是否包含更像配置键的结构化候选。
     *
     * @param question 检索问题
     * @param hit 查询命中
     * @return 命中结构化配置候选返回 true
     */
    private static boolean matchesConfigFactCandidate(String question, QueryArticleHit hit) {
        for (String candidate : extractConfigCandidates(question)) {
            if (candidate.isBlank()) {
                continue;
            }
            if (containsIgnoreCase(hit.getArticleKey(), candidate)
                    || containsIgnoreCase(hit.getConceptId(), candidate)
                    || containsIgnoreCase(hit.getTitle(), candidate)
                    || containsIgnoreCase(hit.getContent(), candidate)) {
                return true;
            }
            if (hit.getSourcePaths() == null) {
                continue;
            }
            for (String sourcePath : hit.getSourcePaths()) {
                if (containsIgnoreCase(sourcePath, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 提取配置题更可能的 key 候选。
     *
     * @param question 检索问题
     * @return 配置 key 候选
     */
    private static List<String> extractConfigCandidates(String question) {
        List<String> asciiTokens = new ArrayList<String>();
        for (String token : QueryTokenExtractor.extract(question)) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalizedToken = lowerCase(token);
            if (!normalizedToken.matches("[a-z0-9_-]{2,}")) {
                continue;
            }
            asciiTokens.add(normalizedToken);
        }
        List<String> candidates = new ArrayList<String>();
        if (asciiTokens.size() < 2) {
            return candidates;
        }
        for (int start = 0; start < asciiTokens.size(); start++) {
            for (int end = start + 2; end <= asciiTokens.size() && end <= start + 4; end++) {
                List<String> window = asciiTokens.subList(start, end);
                candidates.add(String.join(".", window));
                candidates.add(String.join("_", window));
                candidates.add(String.join("-", window));
            }
        }
        return candidates;
    }

    /**
     * 判断命中是否来自更适合回答配置题的文件。
     *
     * @param hit 查询命中
     * @return 配置友好来源返回 true
     */
    private static boolean matchesConfigFriendlyPath(QueryArticleHit hit) {
        if (hit == null || hit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : hit.getSourcePaths()) {
            String normalizedPath = lowerCase(sourcePath);
            if (normalizedPath.endsWith(".yaml")
                    || normalizedPath.endsWith(".yml")
                    || normalizedPath.endsWith(".properties")
                    || normalizedPath.endsWith(".json")
                    || normalizedPath.endsWith(".java")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断命中是否来自更适合回答架构题的文件。
     *
     * @param hit 查询命中
     * @return 架构友好来源返回 true
     */
    private static boolean matchesArchitectureFriendlyPath(QueryArticleHit hit) {
        if (hit == null || hit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : hit.getSourcePaths()) {
            String normalizedPath = lowerCase(sourcePath);
            if (normalizedPath.contains("/architecture/")
                    || normalizedPath.contains("architecture/")
                    || normalizedPath.contains("/adr/")
                    || normalizedPath.contains("adr/")
                    || normalizedPath.contains("design")
                    || normalizedPath.contains("mq")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断命中是否主要来自截图或图片。
     *
     * @param hit 查询命中
     * @return 图片类命中返回 true
     */
    private static boolean isImageLikeHit(QueryArticleHit hit) {
        if (hit == null || hit.getSourcePaths() == null || hit.getSourcePaths().isEmpty()) {
            return false;
        }
        for (String sourcePath : hit.getSourcePaths()) {
            String normalizedPath = lowerCase(sourcePath);
            if (!(normalizedPath.endsWith(".png")
                    || normalizedPath.endsWith(".jpg")
                    || normalizedPath.endsWith(".jpeg")
                    || normalizedPath.endsWith(".gif")
                    || normalizedPath.endsWith(".svg")
                    || normalizedPath.endsWith(".bmp")
                    || normalizedPath.endsWith(".webp"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断命中是否更像说明型元文档。
     *
     * @param hit 查询命中
     * @return 元文档返回 true
     */
    private static boolean isMetaDocHit(QueryArticleHit hit) {
        String text = lowerCase(joinHitIdentity(hit));
        return text.contains("images")
                || text.contains("readme")
                || text.contains("benchmark")
                || text.contains("guide")
                || text.contains("handbook")
                || text.contains("manual")
                || text.contains("checklist")
                || text.contains("清单")
                || text.contains("手册")
                || text.contains("截图")
                || text.contains("方案")
                || text.contains("overview")
                || text.contains("settings")
                || text.contains("developer");
    }

    /**
     * 判断问题是否更偏架构解释。
     *
     * @param question 检索问题
     * @return 架构解释题返回 true
     */
    private static boolean looksLikeArchitectureQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("消息队列")
                || normalizedQuestion.contains("同步调用")
                || normalizedQuestion.contains("异步")
                || normalizedQuestion.contains("解耦")
                || normalizedQuestion.contains("为什么要")
                || normalizedQuestion.contains("架构")
                || normalizedQuestion.contains("设计")
                || normalizedQuestion.contains("取舍");
    }

    /**
     * 拼接命中的身份字段。
     *
     * @param hit 查询命中
     * @return 身份文本
     */
    private static String joinHitIdentity(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(hit.getArticleKey()).append('\n');
        builder.append(hit.getConceptId()).append('\n');
        builder.append(hit.getTitle()).append('\n');
        if (hit.getSourcePaths() != null) {
            for (String sourcePath : hit.getSourcePaths()) {
                builder.append(sourcePath).append('\n');
            }
        }
        return builder.toString();
    }

    /**
     * 忽略大小写判断包含关系。
     *
     * @param text 文本
     * @param token 片段
     * @return 包含返回 true
     */
    private static boolean containsIgnoreCase(String text, String token) {
        return lowerCase(text).contains(lowerCase(token));
    }

    /**
     * 转成小写字符串。
     *
     * @param text 原始文本
     * @return 小写文本
     */
    private static String lowerCase(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
