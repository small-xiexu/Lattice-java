package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryCitationMarkerResponse;
import com.xbk.lattice.api.query.QueryCitationSourceResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.ClaimSegment;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query 响应引用组装基础支持。
 *
 * 职责：承载 projection、TOP_K 与 citation source 的底层装配工具。
 *
 * @author xiexu
 */
abstract class QueryResponseCitationBaseSupport {

    protected static final String DERIVATION_PROJECTION = "PROJECTION";

    protected static final String DERIVATION_TOP_K = "TOP_K";

    /**
    /**
     * 解析引用点来源类型。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @return 来源类型
     */
    protected static ProjectionCitationFormat resolveMarkerSourceType(
            Citation citation,
            AnswerProjection answerProjection
    ) {
        if (answerProjection != null && answerProjection.getSourceType() != null) {
            return answerProjection.getSourceType();
        }
        if (citation == null || citation.getSourceType() == null) {
            return null;
        }
        if (citation.getSourceType() == CitationSourceType.SOURCE_FILE) {
            return ProjectionCitationFormat.SOURCE_FILE;
        }
        return ProjectionCitationFormat.ARTICLE;
    }
    /**
     * 解析引用点目标键。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @param validationResult citation 校验结果
     * @return 目标键
     */
    protected static String resolveMarkerTargetKey(
            Citation citation,
            AnswerProjection answerProjection,
            CitationValidationResult validationResult
    ) {
        if (answerProjection != null && !isBlank(answerProjection.getTargetKey())) {
            return answerProjection.getTargetKey().trim();
        }
        if (validationResult != null && !isBlank(validationResult.getTargetKey())) {
            return validationResult.getTargetKey().trim();
        }
        if (citation != null && !isBlank(citation.getTargetKey())) {
            return citation.getTargetKey().trim();
        }
        return "";
    }
    /**
     * 解析引用点标题。
     *
     * @param sourceType 来源类型
     * @param targetKey 目标键
     * @param queryArticleHit 元数据补充命中
     * @return 展示标题
     */
    protected static String resolveMarkerTitle(
            ProjectionCitationFormat sourceType,
            String targetKey,
            QueryArticleHit queryArticleHit
    ) {
        if (sourceType == ProjectionCitationFormat.SOURCE_FILE) {
            if (queryArticleHit != null
                    && queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE
                    && !isBlank(queryArticleHit.getTitle())) {
                return queryArticleHit.getTitle();
            }
            return targetKey;
        }
        if (queryArticleHit != null && !isBlank(queryArticleHit.getTitle())) {
            return queryArticleHit.getTitle();
        }
        if (queryArticleHit != null && !isBlank(queryArticleHit.getArticleKey())) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit != null && !isBlank(queryArticleHit.getConceptId())) {
            return queryArticleHit.getConceptId();
        }
        return targetKey;
    }
    /**
     * 解析引用点来源路径。
     *
     * @param sourceType 来源类型
     * @param targetKey 目标键
     * @param queryArticleHit 元数据补充命中
     * @return 来源路径
     */
    protected static List<String> resolveMarkerSourcePaths(
            ProjectionCitationFormat sourceType,
            String targetKey,
            QueryArticleHit queryArticleHit
    ) {
        if (queryArticleHit != null && queryArticleHit.getSourcePaths() != null && !queryArticleHit.getSourcePaths().isEmpty()) {
            return queryArticleHit.getSourcePaths();
        }
        if (sourceType == ProjectionCitationFormat.SOURCE_FILE && !isBlank(targetKey)) {
            return List.of(targetKey);
        }
        return List.of();
    }
    /**
     * 转换单条 ARTICLE projection 响应。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return article 响应
     */
    protected static QueryArticleResponse toProjectionArticleResponse(
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
    protected static QueryArticleResponse toSourceFileLinkedArticleResponse(QueryArticleHit queryArticleHit) {
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
    protected static List<QuerySourceResponse> topKSourceResponses(List<QueryArticleHit> fallbackHits) {
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
                QueryEvidenceType.FACT_CARD
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
    protected static void appendTopKSourceResponses(
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
    protected static List<QueryArticleResponse> topKArticleResponses(List<QueryArticleHit> fallbackHits) {
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
    protected static Set<String> collectProjectedArticleIdentities(
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
    protected static Set<String> collectProjectedArticleSourcePaths(
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
    protected static boolean shouldSkipSourceFileProjection(
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
    protected static String sourceResponseKey(
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
     * 构造 citation marker 来源去重键。
     *
     * @param sourceResponse marker 来源响应
     * @return 去重键
     */
    protected static String citationSourceResponseKey(QueryCitationSourceResponse sourceResponse) {
        if (sourceResponse == null) {
            return "";
        }
        String targetKey = sourceResponse.getTargetKey();
        if (!isBlank(targetKey)) {
            return sourceResponse.getSourceType() + ":" + normalizeSourcePath(targetKey);
        }
        String articleKey = sourceResponse.getArticleKey();
        if (!isBlank(articleKey)) {
            return sourceResponse.getSourceType() + ":article:" + articleKey.trim();
        }
        String conceptId = sourceResponse.getConceptId();
        if (!isBlank(conceptId)) {
            return sourceResponse.getSourceType() + ":concept:" + conceptId.trim();
        }
        return sourceResponse.getSourceType() + ":" + sourceResponse.getTitle();
    }
    /**
     * 解析 projection 对外暴露的 articleKey。
     *
     * @param answerProjection projection
     * @param queryArticleHit 元数据补充命中
     * @return 对外 articleKey
     */
    protected static String resolveProjectionArticleKey(
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
    protected static String projectionArticleIdentity(
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
    protected static String topKSourceCanonicalKey(QueryArticleHit fallbackHit) {
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
    protected static Map<String, QueryArticleHit> indexHitsByTargetKey(List<QueryArticleHit> fallbackHits) {
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
            putIndexedHit(hitByTargetKey, normalizeSourcePath(fallbackHit.getArticleKey()), fallbackHit);
            putIndexedHit(hitByTargetKey, normalizeSourcePath(fallbackHit.getConceptId()), fallbackHit);
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
    protected static String articleResponseIdentity(QueryArticleHit queryArticleHit) {
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
    protected static void putIndexedHit(
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
    protected static boolean shouldReplaceIndexedHit(QueryArticleHit existingHit, QueryArticleHit candidateHit) {
        return indexedHitPriority(candidateHit) > indexedHitPriority(existingHit);
    }
    /**
     * 计算命中作为 projection 元数据补充时的优先级。
     *
     * @param queryArticleHit 命中
     * @return 优先级分值
     */
    protected static int indexedHitPriority(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return Integer.MIN_VALUE;
        }
        int priority = 0;
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            priority += 100;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            priority += 95;
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
    protected static String normalizeSourcePath(String sourcePath) {
        if (isBlank(sourcePath)) {
            return "";
        }
        String normalizedPath = sourcePath.trim();
        if (normalizedPath.startsWith("[") && normalizedPath.endsWith("]")) {
            normalizedPath = normalizedPath.substring(1, normalizedPath.length() - 1).trim();
        }
        if (normalizedPath.startsWith("→")) {
            normalizedPath = normalizedPath.substring(1).trim();
        }
        int commaIndex = normalizedPath.indexOf(',');
        if (commaIndex > 0) {
            normalizedPath = normalizedPath.substring(0, commaIndex).trim();
        }
        String lineRangeRemovedPath = normalizedPath.replaceFirst(":(?:L)?\\d+(?:-(?:L)?\\d+)?$", "").trim();
        return lineRangeRemovedPath;
    }
    /**
     * 判断字符串是否为空白。
     *
     * @param value 字符串
     * @return 空白返回 true
     */
    protected static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    protected static final class CitationLiteralMatch {

        protected final int startIndex;

        protected final int endIndex;

        /**
         * 创建 citation literal 匹配范围。
         *
         * @param startIndex 起始下标
         * @param endIndex 结束下标
         */
        protected CitationLiteralMatch(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        /**
         * 返回起始下标。
         *
         * @return 起始下标
         */
        protected int getStartIndex() {
            return startIndex;
        }

        /**
         * 返回结束下标。
         *
         * @return 结束下标
         */
        protected int getEndIndex() {
            return endIndex;
        }
    }
}
