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
 * Query 响应引用来源组装器
 *
 * 职责：按 projection 优先、TOP_K 兜底的规则组装 sources 与 articles
 *
 * @author xiexu
 */
public final class QueryResponseCitationAssembler extends QueryResponseCitationMarkerSupport {

    /**
     * 工具类不允许实例化。
     */
    private QueryResponseCitationAssembler() {
    }
    /**
     * 组装答案正文引用点。
     *
     * @param citationCheckReport citation 核验报告
     * @param answerProjectionBundle projection 白名单
     * @param fallbackHits 元数据补充命中
     * @return 引用点响应
     */
    public static List<QueryCitationMarkerResponse> toCitationMarkerResponses(
            CitationCheckReport citationCheckReport,
            AnswerProjectionBundle answerProjectionBundle,
            List<QueryArticleHit> fallbackHits
    ) {
        if (citationCheckReport == null || citationCheckReport.getClaimSegments() == null) {
            return List.of();
        }
        Map<String, AnswerProjection> activeProjectionByLiteral = indexActiveProjectionsByLiteral(answerProjectionBundle);
        Map<Integer, CitationValidationResult> validationResultByOrdinal = indexValidationResults(citationCheckReport);
        Map<String, QueryArticleHit> hitByTargetKey = indexHitsByTargetKey(fallbackHits);
        List<QueryCitationMarkerResponse> markerResponses = new ArrayList<QueryCitationMarkerResponse>();
        int markerOrdinal = 1;
        for (ClaimSegment claimSegment : citationCheckReport.getClaimSegments()) {
            if (claimSegment == null || claimSegment.getCitations() == null || claimSegment.getCitations().isEmpty()) {
                continue;
            }
            List<QueryCitationSourceResponse> sourceResponses = new ArrayList<QueryCitationSourceResponse>();
            List<String> citationLiterals = new ArrayList<String>();
            Set<String> sourceKeys = new LinkedHashSet<String>();
            for (Citation citation : claimSegment.getCitations()) {
                if (citation == null || isBlank(citation.getLiteral())) {
                    continue;
                }
                AnswerProjection answerProjection = activeProjectionByLiteral.get(citation.getLiteral());
                citationLiterals.add(resolveCitationLiteral(citation, answerProjection));
                CitationValidationResult validationResult = validationResultByOrdinal.get(Integer.valueOf(citation.getOrdinal()));
                QueryCitationSourceResponse sourceResponse = toCitationSourceResponse(
                        citation,
                        answerProjection,
                        validationResult,
                        hitByTargetKey
                );
                if (sourceResponse == null) {
                    continue;
                }
                String sourceKey = citationSourceResponseKey(sourceResponse);
                if (!sourceKeys.add(sourceKey)) {
                    continue;
                }
                sourceResponses.add(sourceResponse);
            }
            if (citationLiterals.isEmpty() || sourceResponses.isEmpty()) {
                continue;
            }
            markerResponses.add(new QueryCitationMarkerResponse(
                    markerOrdinal,
                    "citation-marker-" + markerOrdinal,
                    resolveCitationGroupLiteral(claimSegment.getParagraphText(), citationLiterals),
                    citationLiterals,
                    claimSegment.getClaimText(),
                    sourceResponses.size(),
                    sourceResponses
            ));
            markerOrdinal++;
        }
        return markerResponses;
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
}
