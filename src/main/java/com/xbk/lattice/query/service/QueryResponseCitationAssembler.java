package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query 响应引用来源组装器
 *
 * 职责：按 projection 优先、TOP_K 兜底的规则组装 sources 与 articles
 *
 * @author xiexu
 */
public final class QueryResponseCitationAssembler {

    private static final String DERIVATION_PROJECTION = "PROJECTION";

    private static final String DERIVATION_TOP_K = "TOP_K";

    /**
     * 工具类不允许实例化。
     */
    private QueryResponseCitationAssembler() {
    }

    /**
     * 组装 sources 响应。
     *
     * @param answerProjectionBundle projection 白名单
     * @param fallbackHits TOP_K 兜底命中
     * @param allowTopKFallback 是否允许 TOP_K 兜底
     * @return sources 响应
     */
    public static List<QuerySourceResponse> toSourceResponses(
            AnswerProjectionBundle answerProjectionBundle,
            List<QueryArticleHit> fallbackHits,
            boolean allowTopKFallback
    ) {
        List<AnswerProjection> activeProjections = activeProjections(answerProjectionBundle);
        if (!activeProjections.isEmpty()) {
            return projectionSourceResponses(activeProjections, fallbackHits);
        }
        if (!allowTopKFallback) {
            return List.of();
        }
        return topKSourceResponses(fallbackHits);
    }

    /**
     * 组装 articles 响应。
     *
     * @param answerProjectionBundle projection 白名单
     * @param fallbackHits TOP_K 兜底命中
     * @param allowTopKFallback 是否允许 TOP_K 兜底
     * @return articles 响应
     */
    public static List<QueryArticleResponse> toArticleResponses(
            AnswerProjectionBundle answerProjectionBundle,
            List<QueryArticleHit> fallbackHits,
            boolean allowTopKFallback
    ) {
        List<AnswerProjection> activeProjections = activeProjections(answerProjectionBundle);
        if (!activeProjections.isEmpty()) {
            return projectionArticleResponses(activeProjections, fallbackHits);
        }
        if (!allowTopKFallback) {
            return List.of();
        }
        return topKArticleResponses(fallbackHits);
    }

    /**
     * 提取 ACTIVE projection。
     *
     * @param answerProjectionBundle projection 白名单
     * @return ACTIVE projection 列表
     */
    private static List<AnswerProjection> activeProjections(AnswerProjectionBundle answerProjectionBundle) {
        List<AnswerProjection> activeProjections = new ArrayList<AnswerProjection>();
        if (answerProjectionBundle == null || answerProjectionBundle.getProjections() == null) {
            return activeProjections;
        }
        for (AnswerProjection answerProjection : answerProjectionBundle.getProjections()) {
            if (answerProjection == null
                    || answerProjection.getStatus() != ProjectionStatus.ACTIVE
                    || answerProjection.getSourceType() == null
                    || isBlank(answerProjection.getTargetKey())) {
                continue;
            }
            activeProjections.add(answerProjection);
        }
        return activeProjections;
    }

    /**
     * 按 projection 组装 sources。
     *
     * @param activeProjections ACTIVE projection
     * @param fallbackHits 元数据补充命中
     * @return sources 响应
     */
    private static List<QuerySourceResponse> projectionSourceResponses(
            List<AnswerProjection> activeProjections,
            List<QueryArticleHit> fallbackHits
    ) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        Map<String, QueryArticleHit> hitByTargetKey = indexHitsByTargetKey(fallbackHits);
        Set<String> projectedArticleIdentities = collectProjectedArticleIdentities(activeProjections, hitByTargetKey);
        Set<String> projectedArticleSourcePaths = collectProjectedArticleSourcePaths(activeProjections, hitByTargetKey);
        Set<String> responseKeys = new LinkedHashSet<String>();
        for (AnswerProjection answerProjection : activeProjections) {
            QueryArticleHit queryArticleHit = hitByTargetKey.get(answerProjection.getTargetKey());
            if (shouldSkipSourceFileProjection(
                    answerProjection,
                    queryArticleHit,
                    projectedArticleIdentities,
                    projectedArticleSourcePaths
            )) {
                continue;
            }
            String responseKey = sourceResponseKey(answerProjection, queryArticleHit);
            if (!responseKeys.add(responseKey)) {
                continue;
            }
            sourceResponses.add(toProjectionSourceResponse(answerProjection, queryArticleHit));
        }
        return sourceResponses;
    }

    /**
     * 按 ARTICLE projection 组装 articles。
     *
     * @param activeProjections ACTIVE projection
     * @param fallbackHits 元数据补充命中
     * @return articles 响应
     */
    private static List<QueryArticleResponse> projectionArticleResponses(
            List<AnswerProjection> activeProjections,
            List<QueryArticleHit> fallbackHits
    ) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        Map<String, QueryArticleHit> hitByTargetKey = indexHitsByTargetKey(fallbackHits);
        Set<String> responseKeys = new LinkedHashSet<String>();
        for (AnswerProjection answerProjection : activeProjections) {
            QueryArticleHit queryArticleHit = hitByTargetKey.get(answerProjection.getTargetKey());
            if (answerProjection.getSourceType() == ProjectionCitationFormat.ARTICLE) {
                appendProjectionArticleResponse(articleResponses, responseKeys, answerProjection, queryArticleHit);
                continue;
            }
            if (answerProjection.getSourceType() == ProjectionCitationFormat.SOURCE_FILE) {
                appendSourceFileLinkedArticleResponse(articleResponses, responseKeys, queryArticleHit);
            }
        }
        return articleResponses;
    }

    /**
     * 追加 ARTICLE projection 对应的 article 响应。
     *
     * @param articleResponses article 响应列表
     * @param responseKeys 去重键
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     */
    private static void appendProjectionArticleResponse(
            List<QueryArticleResponse> articleResponses,
            Set<String> responseKeys,
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit
    ) {
        String responseKey = projectionArticleIdentity(answerProjection, queryArticleHit);
        if (isBlank(responseKey)) {
            responseKey = answerProjection.getTargetKey();
        }
        if (!responseKeys.add(responseKey)) {
            return;
        }
        articleResponses.add(toProjectionArticleResponse(answerProjection, queryArticleHit));
    }

    /**
     * 追加 SOURCE_FILE projection 关联到的 article 响应。
     *
     * @param articleResponses article 响应列表
     * @param responseKeys 去重键
     * @param queryArticleHit 元数据补充命中
     */
    private static void appendSourceFileLinkedArticleResponse(
            List<QueryArticleResponse> articleResponses,
            Set<String> responseKeys,
            QueryArticleHit queryArticleHit
    ) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
            return;
        }
        String responseKey = articleResponseIdentity(queryArticleHit);
        if (isBlank(responseKey) || !responseKeys.add(responseKey)) {
            return;
        }
        articleResponses.add(toSourceFileLinkedArticleResponse(queryArticleHit));
    }

    /**
     * 转换单条 projection source 响应。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return source 响应
     */
    private static QuerySourceResponse toProjectionSourceResponse(
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit
    ) {
        if (answerProjection.getSourceType() == ProjectionCitationFormat.SOURCE_FILE) {
            List<String> sourcePaths = List.of(answerProjection.getTargetKey());
            String articleKey = queryArticleHit == null || isBlank(queryArticleHit.getArticleKey())
                    ? null
                    : queryArticleHit.getArticleKey();
            String title = queryArticleHit == null || isBlank(queryArticleHit.getTitle())
                    ? answerProjection.getTargetKey()
                    : queryArticleHit.getTitle();
            return new QuerySourceResponse(
                    queryArticleHit == null ? null : queryArticleHit.getSourceId(),
                    articleKey,
                    queryArticleHit == null ? null : queryArticleHit.getConceptId(),
                    title,
                    sourcePaths,
                    DERIVATION_PROJECTION
            );
        }
        String articleKey = resolveProjectionArticleKey(answerProjection, queryArticleHit);
        String title = queryArticleHit == null || isBlank(queryArticleHit.getTitle())
                ? articleKey
                : queryArticleHit.getTitle();
        List<String> sourcePaths = queryArticleHit == null || queryArticleHit.getSourcePaths() == null
                ? List.of()
                : queryArticleHit.getSourcePaths();
        return new QuerySourceResponse(
                queryArticleHit == null ? null : queryArticleHit.getSourceId(),
                articleKey,
                queryArticleHit == null ? null : queryArticleHit.getConceptId(),
                title,
                sourcePaths,
                DERIVATION_PROJECTION
        );
    }

    /**
     * 转换单条 ARTICLE projection 响应。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return article 响应
     */
    private static QueryArticleResponse toProjectionArticleResponse(
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit
    ) {
        String articleKey = resolveProjectionArticleKey(answerProjection, queryArticleHit);
        String title = queryArticleHit == null || isBlank(queryArticleHit.getTitle())
                ? articleKey
                : queryArticleHit.getTitle();
        return new QueryArticleResponse(
                queryArticleHit == null ? null : queryArticleHit.getSourceId(),
                articleKey,
                queryArticleHit == null ? null : queryArticleHit.getConceptId(),
                title,
                DERIVATION_PROJECTION
        );
    }

    /**
     * 转换 SOURCE_FILE projection 关联 article 响应。
     *
     * @param queryArticleHit article 元数据命中
     * @return article 响应
     */
    private static QueryArticleResponse toSourceFileLinkedArticleResponse(QueryArticleHit queryArticleHit) {
        String title = isBlank(queryArticleHit.getTitle())
                ? articleResponseIdentity(queryArticleHit)
                : queryArticleHit.getTitle();
        return new QueryArticleResponse(
                queryArticleHit.getSourceId(),
                queryArticleHit.getArticleKey(),
                queryArticleHit.getConceptId(),
                title,
                DERIVATION_PROJECTION
        );
    }

    /**
     * 按 TOP_K 组装 sources 兜底响应。
     *
     * @param fallbackHits TOP_K 命中
     * @return sources 响应
     */
    private static List<QuerySourceResponse> topKSourceResponses(List<QueryArticleHit> fallbackHits) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        Set<String> responseKeys = new LinkedHashSet<String>();
        Set<String> canonicalSourceKeys = new LinkedHashSet<String>();
        appendTopKSourceResponses(
                sourceResponses,
                responseKeys,
                canonicalSourceKeys,
                fallbackHits,
                QueryEvidenceType.ARTICLE
        );
        appendTopKSourceResponses(
                sourceResponses,
                responseKeys,
                canonicalSourceKeys,
                fallbackHits,
                QueryEvidenceType.GRAPH
        );
        appendTopKSourceResponses(
                sourceResponses,
                responseKeys,
                canonicalSourceKeys,
                fallbackHits,
                QueryEvidenceType.SOURCE
        );
        appendTopKSourceResponses(
                sourceResponses,
                responseKeys,
                canonicalSourceKeys,
                fallbackHits,
                QueryEvidenceType.CONTRIBUTION
        );
        return sourceResponses;
    }

    /**
     * 追加指定证据类型的 TOP_K source 响应。
     *
     * @param sourceResponses sources 响应
     * @param responseKeys 去重键
     * @param fallbackHits TOP_K 命中
     * @param queryEvidenceType 证据类型
     */
    private static void appendTopKSourceResponses(
            List<QuerySourceResponse> sourceResponses,
            Set<String> responseKeys,
            Set<String> canonicalSourceKeys,
            List<QueryArticleHit> fallbackHits,
            QueryEvidenceType queryEvidenceType
    ) {
        if (fallbackHits == null) {
            return;
        }
        for (QueryArticleHit fallbackHit : fallbackHits) {
            if (fallbackHit == null || fallbackHit.getEvidenceType() != queryEvidenceType) {
                continue;
            }
            String responseIdentity = fallbackHit.getArticleKey();
            if (isBlank(responseIdentity)) {
                responseIdentity = fallbackHit.getConceptId();
            }
            String responseKey = fallbackHit.getEvidenceType().name() + ":" + responseIdentity;
            String canonicalSourceKey = topKSourceCanonicalKey(fallbackHit);
            if (!isBlank(canonicalSourceKey) && canonicalSourceKeys.contains(canonicalSourceKey)) {
                continue;
            }
            if (!responseKeys.add(responseKey)) {
                continue;
            }
            if (!isBlank(canonicalSourceKey)) {
                canonicalSourceKeys.add(canonicalSourceKey);
            }
            sourceResponses.add(new QuerySourceResponse(
                    fallbackHit.getSourceId(),
                    fallbackHit.getArticleKey(),
                    fallbackHit.getConceptId(),
                    fallbackHit.getTitle(),
                    fallbackHit.getSourcePaths(),
                    DERIVATION_TOP_K
            ));
        }
    }

    /**
     * 按 TOP_K 组装 articles 兜底响应。
     *
     * @param fallbackHits TOP_K 命中
     * @return articles 响应
     */
    private static List<QueryArticleResponse> topKArticleResponses(List<QueryArticleHit> fallbackHits) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        Set<String> responseKeys = new LinkedHashSet<String>();
        if (fallbackHits == null) {
            return articleResponses;
        }
        for (QueryArticleHit fallbackHit : fallbackHits) {
            if (fallbackHit == null || fallbackHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
                continue;
            }
            String responseKey = articleResponseIdentity(fallbackHit);
            if (isBlank(responseKey) || !responseKeys.add(responseKey)) {
                continue;
            }
            articleResponses.add(new QueryArticleResponse(
                    fallbackHit.getSourceId(),
                    fallbackHit.getArticleKey(),
                    fallbackHit.getConceptId(),
                    fallbackHit.getTitle(),
                    DERIVATION_TOP_K
            ));
        }
        return articleResponses;
    }

    /**
     * 收集已显式投成 ARTICLE 的 article 身份。
     *
     * @param activeProjections ACTIVE projection
     * @param hitByTargetKey 命中索引
     * @return 已显式暴露的 article 身份
     */
    private static Set<String> collectProjectedArticleIdentities(
            List<AnswerProjection> activeProjections,
            Map<String, QueryArticleHit> hitByTargetKey
    ) {
        Set<String> projectedArticleIdentities = new LinkedHashSet<String>();
        for (AnswerProjection answerProjection : activeProjections) {
            if (answerProjection == null || answerProjection.getSourceType() != ProjectionCitationFormat.ARTICLE) {
                continue;
            }
            QueryArticleHit queryArticleHit = hitByTargetKey.get(answerProjection.getTargetKey());
            String articleIdentity = projectionArticleIdentity(answerProjection, queryArticleHit);
            if (!isBlank(articleIdentity)) {
                projectedArticleIdentities.add(articleIdentity);
            }
        }
        return projectedArticleIdentities;
    }

    /**
     * 收集 ARTICLE projection 已覆盖的源文件路径。
     *
     * @param activeProjections ACTIVE projection
     * @param hitByTargetKey 命中索引
     * @return 已覆盖的源文件路径
     */
    private static Set<String> collectProjectedArticleSourcePaths(
            List<AnswerProjection> activeProjections,
            Map<String, QueryArticleHit> hitByTargetKey
    ) {
        Set<String> projectedArticleSourcePaths = new LinkedHashSet<String>();
        for (AnswerProjection answerProjection : activeProjections) {
            if (answerProjection == null || answerProjection.getSourceType() != ProjectionCitationFormat.ARTICLE) {
                continue;
            }
            QueryArticleHit queryArticleHit = hitByTargetKey.get(answerProjection.getTargetKey());
            if (queryArticleHit == null || queryArticleHit.getSourcePaths() == null) {
                continue;
            }
            for (String sourcePath : queryArticleHit.getSourcePaths()) {
                String normalizedSourcePath = normalizeSourcePath(sourcePath);
                if (!isBlank(normalizedSourcePath)) {
                    projectedArticleSourcePaths.add(normalizedSourcePath);
                }
            }
        }
        return projectedArticleSourcePaths;
    }

    /**
     * 判断 SOURCE_FILE projection 是否只是 ARTICLE projection 的冗余来源。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @param projectedArticleIdentities 已显式暴露的 article 身份
     * @return 冗余来源返回 true
     */
    private static boolean shouldSkipSourceFileProjection(
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit,
            Set<String> projectedArticleIdentities,
            Set<String> projectedArticleSourcePaths
    ) {
        if (answerProjection == null || answerProjection.getSourceType() != ProjectionCitationFormat.SOURCE_FILE) {
            return false;
        }
        String normalizedTargetKey = normalizeSourcePath(answerProjection.getTargetKey());
        if (!isBlank(normalizedTargetKey) && projectedArticleSourcePaths.contains(normalizedTargetKey)) {
            return true;
        }
        if (queryArticleHit == null) {
            return false;
        }
        String articleIdentity = articleResponseIdentity(queryArticleHit);
        return !isBlank(articleIdentity) && projectedArticleIdentities.contains(articleIdentity);
    }

    /**
     * 构造 projection source 响应去重键。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return 去重键
     */
    private static String sourceResponseKey(
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit
    ) {
        if (answerProjection.getSourceType() == ProjectionCitationFormat.ARTICLE) {
            String articleIdentity = projectionArticleIdentity(answerProjection, queryArticleHit);
            return ProjectionCitationFormat.ARTICLE.name() + ":" + articleIdentity;
        }
        return ProjectionCitationFormat.SOURCE_FILE.name() + ":" + answerProjection.getTargetKey();
    }

    /**
     * 解析 projection 对外暴露的 articleKey。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return 对外 articleKey
     */
    private static String resolveProjectionArticleKey(
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit
    ) {
        if (queryArticleHit != null && !isBlank(queryArticleHit.getArticleKey())) {
            return queryArticleHit.getArticleKey();
        }
        if (answerProjection == null || isBlank(answerProjection.getTargetKey())) {
            return "";
        }
        return answerProjection.getTargetKey().trim();
    }

    /**
     * 解析 projection 对外与去重共用的 article 身份。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return article 身份
     */
    private static String projectionArticleIdentity(
            AnswerProjection answerProjection,
            QueryArticleHit queryArticleHit
    ) {
        String articleKey = resolveProjectionArticleKey(answerProjection, queryArticleHit);
        if (!isBlank(articleKey)) {
            return articleKey;
        }
        if (queryArticleHit != null && !isBlank(queryArticleHit.getConceptId())) {
            return queryArticleHit.getConceptId();
        }
        if (answerProjection == null || isBlank(answerProjection.getTargetKey())) {
            return "";
        }
        return answerProjection.getTargetKey().trim();
    }

    /**
     * 计算 TOP_K source 的跨证据去重键。
     *
     * @param fallbackHit TOP_K 命中
     * @return 去重键
     */
    private static String topKSourceCanonicalKey(QueryArticleHit fallbackHit) {
        if (fallbackHit == null) {
            return "";
        }
        if (fallbackHit.getSourcePaths() != null && !fallbackHit.getSourcePaths().isEmpty()) {
            String normalizedSourcePath = normalizeSourcePath(fallbackHit.getSourcePaths().get(0));
            if (!isBlank(normalizedSourcePath)) {
                return normalizedSourcePath;
            }
        }
        String articleIdentity = articleResponseIdentity(fallbackHit);
        if (!isBlank(articleIdentity)) {
            return articleIdentity;
        }
        return "";
    }

    /**
     * 按可引用目标键索引命中。
     *
     * @param fallbackHits 元数据补充命中
     * @return 目标键到命中的映射
     */
    private static Map<String, QueryArticleHit> indexHitsByTargetKey(List<QueryArticleHit> fallbackHits) {
        Map<String, QueryArticleHit> hitByTargetKey = new LinkedHashMap<String, QueryArticleHit>();
        if (fallbackHits == null) {
            return hitByTargetKey;
        }
        for (QueryArticleHit fallbackHit : fallbackHits) {
            if (fallbackHit == null) {
                continue;
            }
            putIndexedHit(hitByTargetKey, fallbackHit.getArticleKey(), fallbackHit);
            putIndexedHit(hitByTargetKey, fallbackHit.getConceptId(), fallbackHit);
            if (fallbackHit.getSourcePaths() == null) {
                continue;
            }
            for (String sourcePath : fallbackHit.getSourcePaths()) {
                putIndexedHit(hitByTargetKey, normalizeSourcePath(sourcePath), fallbackHit);
            }
        }
        return hitByTargetKey;
    }

    /**
     * 构造 article 响应去重身份。
     *
     * @param queryArticleHit article 命中
     * @return articleKey 优先、conceptId 兜底的身份
     */
    private static String articleResponseIdentity(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (!isBlank(queryArticleHit.getArticleKey())) {
            return queryArticleHit.getArticleKey();
        }
        if (!isBlank(queryArticleHit.getConceptId())) {
            return queryArticleHit.getConceptId();
        }
        return "";
    }

    /**
     * 在目标键非空时写入索引。
     *
     * @param hitByTargetKey 目标键索引
     * @param targetKey 候选目标键
     * @param queryArticleHit 命中
     */
    private static void putIndexedHit(
            Map<String, QueryArticleHit> hitByTargetKey,
            String targetKey,
            QueryArticleHit queryArticleHit
    ) {
        if (isBlank(targetKey)) {
            return;
        }
        String normalizedTargetKey = targetKey.trim();
        QueryArticleHit existingHit = hitByTargetKey.get(normalizedTargetKey);
        if (existingHit == null || shouldReplaceIndexedHit(existingHit, queryArticleHit)) {
            hitByTargetKey.put(normalizedTargetKey, queryArticleHit);
        }
    }

    /**
     * 判断新的索引命中是否应替换已有命中。
     *
     * @param existingHit 已有命中
     * @param candidateHit 候选命中
     * @return 需要替换返回 true
     */
    private static boolean shouldReplaceIndexedHit(QueryArticleHit existingHit, QueryArticleHit candidateHit) {
        return indexedHitPriority(candidateHit) > indexedHitPriority(existingHit);
    }

    /**
     * 计算命中作为 projection 元数据补充时的优先级。
     *
     * @param queryArticleHit 命中
     * @return 优先级分值
     */
    private static int indexedHitPriority(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return Integer.MIN_VALUE;
        }
        int priority = 0;
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            priority += 100;
        }
        if (!isBlank(queryArticleHit.getArticleKey())) {
            priority += 30;
        }
        if (!isBlank(queryArticleHit.getConceptId())) {
            priority += 20;
        }
        if (queryArticleHit.getSourcePaths() != null && !queryArticleHit.getSourcePaths().isEmpty()) {
            priority += 10;
        }
        if (!isBlank(queryArticleHit.getTitle())) {
            priority += 5;
        }
        return priority;
    }

    /**
     * 归一化源文件路径。
     *
     * @param sourcePath 原始路径
     * @return 去除行号描述后的路径
     */
    private static String normalizeSourcePath(String sourcePath) {
        if (isBlank(sourcePath)) {
            return "";
        }
        String normalizedPath = sourcePath.trim();
        int commaIndex = normalizedPath.indexOf(',');
        if (commaIndex > 0) {
            return normalizedPath.substring(0, commaIndex).trim();
        }
        return normalizedPath;
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 字符串
     * @return 空白返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
