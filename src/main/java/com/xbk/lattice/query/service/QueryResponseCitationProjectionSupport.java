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
 * Query 响应 projection 来源装配支持。
 *
 * 职责：按 projection 白名单组装 sources、articles 与 marker 来源。
 *
 * @author xiexu
 */
abstract class QueryResponseCitationProjectionSupport extends QueryResponseCitationBaseSupport {

    /**
     * 提取 ACTIVE projection。
     *
     * @param answerProjectionBundle projection 白名单
     * @return ACTIVE projection 列表
     */
    protected static List<AnswerProjection> activeProjections(AnswerProjectionBundle answerProjectionBundle) {
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
     * 索引 ACTIVE projection。
     *
     * @param answerProjectionBundle projection 白名单
     * @return citation literal 到 projection 的映射
     */
    protected static Map<String, AnswerProjection> indexActiveProjectionsByLiteral(
            AnswerProjectionBundle answerProjectionBundle
    ) {
        Map<String, AnswerProjection> activeProjectionByLiteral = new LinkedHashMap<String, AnswerProjection>();
        for (AnswerProjection answerProjection : activeProjections(answerProjectionBundle)) {
            if (answerProjection == null || isBlank(answerProjection.getCitationLiteral())) {
                continue;
            }
            activeProjectionByLiteral.putIfAbsent(answerProjection.getCitationLiteral(), answerProjection);
        }
        return activeProjectionByLiteral;
    }
    /**
     * 索引 citation 校验结果。
     *
     * @param citationCheckReport citation 核验报告
     * @return citation ordinal 到校验结果的映射
     */
    protected static Map<Integer, CitationValidationResult> indexValidationResults(CitationCheckReport citationCheckReport) {
        Map<Integer, CitationValidationResult> validationResultByOrdinal =
                new LinkedHashMap<Integer, CitationValidationResult>();
        if (citationCheckReport == null || citationCheckReport.getResults() == null) {
            return validationResultByOrdinal;
        }
        for (CitationValidationResult validationResult : citationCheckReport.getResults()) {
            if (validationResult == null) {
                continue;
            }
            validationResultByOrdinal.put(Integer.valueOf(validationResult.getOrdinal()), validationResult);
        }
        return validationResultByOrdinal;
    }
    /**
     * 按 projection 组装 sources。
     *
     * @param activeProjections ACTIVE projection
     * @param fallbackHits 元数据补充命中
     * @return sources 响应
     */
    protected static List<QuerySourceResponse> projectionSourceResponses(
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
    protected static List<QueryArticleResponse> projectionArticleResponses(
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
    protected static void appendProjectionArticleResponse(
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
    protected static void appendSourceFileLinkedArticleResponse(
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
    protected static QuerySourceResponse toProjectionSourceResponse(
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
     * 转换单条 citation marker 来源响应。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @param validationResult citation 校验结果
     * @param hitByTargetKey 命中索引
     * @return marker 来源响应
     */
    protected static QueryCitationSourceResponse toCitationSourceResponse(
            Citation citation,
            AnswerProjection answerProjection,
            CitationValidationResult validationResult,
            Map<String, QueryArticleHit> hitByTargetKey
    ) {
        ProjectionCitationFormat sourceType = resolveMarkerSourceType(citation, answerProjection);
        String targetKey = resolveMarkerTargetKey(citation, answerProjection, validationResult);
        if (sourceType == null || isBlank(targetKey)) {
            return null;
        }
        QueryArticleHit queryArticleHit = hitByTargetKey.get(targetKey);
        List<String> sourcePaths = resolveMarkerSourcePaths(sourceType, targetKey, queryArticleHit);
        String title = resolveMarkerTitle(sourceType, targetKey, queryArticleHit);
        String sourceTypeName = sourceType.name();
        String validationStatus = validationResult == null || validationResult.getStatus() == null
                ? ""
                : validationResult.getStatus().name();
        String reason = validationResult == null ? "" : validationResult.getReason();
        String matchedExcerpt = validationResult == null ? "" : validationResult.getMatchedExcerpt();
        double score = queryArticleHit == null ? 0.0D : queryArticleHit.getScore();
        return new QueryCitationSourceResponse(
                sourceTypeName,
                targetKey,
                queryArticleHit == null ? null : queryArticleHit.getSourceId(),
                queryArticleHit == null ? null : queryArticleHit.getArticleKey(),
                queryArticleHit == null ? null : queryArticleHit.getConceptId(),
                title,
                sourcePaths,
                matchedExcerpt,
                validationStatus,
                reason,
                score
        );
    }
}
