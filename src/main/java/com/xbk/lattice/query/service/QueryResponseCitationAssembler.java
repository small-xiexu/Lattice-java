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
public final class QueryResponseCitationAssembler {

    private static final String DERIVATION_PROJECTION = "PROJECTION";

    private static final String DERIVATION_TOP_K = "TOP_K";

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
     * 解析对外用于替换的 citation literal。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @return citation literal
     */
    private static String resolveCitationLiteral(Citation citation, AnswerProjection answerProjection) {
        if (answerProjection != null && !isBlank(answerProjection.getCitationLiteral())) {
            return answerProjection.getCitationLiteral().trim();
        }
        return citation == null || citation.getLiteral() == null ? "" : citation.getLiteral().trim();
    }

    /**
     * 从原段落中解析连续引用组，保留引用之间的空格。
     *
     * @param paragraphText 原段落
     * @param citationLiterals citation literal 列表
     * @return 原段落中的引用组，无法定位时返回直接拼接值
     */
    private static String resolveCitationGroupLiteral(String paragraphText, List<String> citationLiterals) {
        List<String> safeCitationLiterals = citationLiterals == null ? List.of() : citationLiterals;
        String fallbackLiteral = String.join("", safeCitationLiterals);
        String exactGroupLiteral = findExactCitationGroupLiteral(paragraphText, safeCitationLiterals);
        if (!isBlank(exactGroupLiteral)) {
            return exactGroupLiteral;
        }
        if (isBlank(paragraphText) || safeCitationLiterals.isEmpty()) {
            return fallbackLiteral;
        }
        int groupStart = -1;
        int searchFrom = 0;
        int groupEnd = -1;
        for (String citationLiteral : safeCitationLiterals) {
            if (isBlank(citationLiteral)) {
                continue;
            }
            CitationLiteralMatch literalMatch = findCitationLiteralMatch(paragraphText, citationLiteral, searchFrom);
            if (literalMatch == null) {
                return fallbackLiteral;
            }
            if (groupStart < 0) {
                groupStart = literalMatch.getStartIndex();
            }
            groupEnd = literalMatch.getEndIndex();
            searchFrom = groupEnd;
        }
        if (groupStart < 0 || groupEnd < groupStart) {
            return fallbackLiteral;
        }
        return paragraphText.substring(groupStart, groupEnd);
    }

    /**
     * 优先查找真实答案里连续出现的完整引用组。
     *
     * <p>模型有时会生成“规范化 literal + 更长源文件引用说明”的组合，例如
     * {@code [[article]][→ file.md, 1.1 业务背景]}。逐个 literal 查找会先命中较短的
     * {@code [→ file.md]}，导致后缀说明残留在正文中，因此这里先尝试一次整组匹配。</p>
     *
     * @param paragraphText 原段落
     * @param citationLiterals citation literal 列表
     * @return 完整引用组
     */
    private static String findExactCitationGroupLiteral(String paragraphText, List<String> citationLiterals) {
        if (isBlank(paragraphText) || citationLiterals == null || citationLiterals.isEmpty()) {
            return "";
        }
        CitationLiteralMatch groupMatch = findCitationGroupMatch(paragraphText, citationLiterals, 0);
        if (groupMatch == null) {
            return "";
        }
        return paragraphText.substring(groupMatch.getStartIndex(), groupMatch.getEndIndex());
    }

    /**
     * 按 literal 顺序查找连续引用组，并保留中间空白。
     *
     * @param paragraphText 原段落
     * @param citationLiterals citation literal 列表
     * @param searchFrom 查找起点
     * @return 匹配范围
     */
    private static CitationLiteralMatch findCitationGroupMatch(
            String paragraphText,
            List<String> citationLiterals,
            int searchFrom
    ) {
        int groupStart = -1;
        int groupEnd = -1;
        int cursor = Math.max(0, searchFrom);
        for (String citationLiteral : citationLiterals) {
            if (isBlank(citationLiteral)) {
                continue;
            }
            CitationLiteralMatch literalMatch = findCitationLiteralMatch(paragraphText, citationLiteral, cursor);
            if (literalMatch == null) {
                return null;
            }
            if (groupEnd >= 0) {
                String between = paragraphText.substring(groupEnd, literalMatch.getStartIndex());
                if (!between.trim().isEmpty()) {
                    return null;
                }
            }
            if (groupStart < 0) {
                groupStart = literalMatch.getStartIndex();
            }
            groupEnd = literalMatch.getEndIndex();
            cursor = groupEnd;
        }
        if (groupStart < 0 || groupEnd < groupStart) {
            return null;
        }
        return new CitationLiteralMatch(groupStart, groupEnd);
    }

    /**
     * 从段落中查找 citation literal，兼容 SOURCE_FILE 带行号或章节说明的原文。
     *
     * @param paragraphText 原段落
     * @param citationLiteral 规范化后的 citation literal
     * @param searchFrom 查找起点
     * @return citation literal 在原段落中的范围
     */
    private static CitationLiteralMatch findCitationLiteralMatch(
            String paragraphText,
            String citationLiteral,
            int searchFrom
    ) {
        int exactIndex = paragraphText.indexOf(citationLiteral, searchFrom);
        CitationLiteralMatch exactMatch = exactIndex < 0
                ? null
                : new CitationLiteralMatch(exactIndex, exactIndex + citationLiteral.length());
        if (!isSourceCitationLiteral(citationLiteral)) {
            return exactMatch;
        }
        CitationLiteralMatch sourceMatch = findSourceCitationLiteralMatch(paragraphText, citationLiteral, searchFrom);
        if (exactMatch == null) {
            return sourceMatch;
        }
        if (sourceMatch == null) {
            return exactMatch;
        }
        return exactMatch.getStartIndex() <= sourceMatch.getStartIndex() ? exactMatch : sourceMatch;
    }

    /**
     * 判断 citation literal 是否为源文件引用。
     *
     * @param citationLiteral citation literal
     * @return 源文件引用返回 true
     */
    private static boolean isSourceCitationLiteral(String citationLiteral) {
        if (isBlank(citationLiteral)) {
            return false;
        }
        String normalizedLiteral = citationLiteral.trim();
        return normalizedLiteral.startsWith("[→") || normalizedLiteral.startsWith("[[→");
    }

    /**
     * 匹配 SOURCE_FILE 引用在原段落中的完整写法。
     *
     * @param paragraphText 原段落
     * @param citationLiteral 规范化后的 SOURCE_FILE citation literal
     * @param searchFrom 查找起点
     * @return 原段落中的完整 SOURCE_FILE 引用范围
     */
    private static CitationLiteralMatch findSourceCitationLiteralMatch(
            String paragraphText,
            String citationLiteral,
            int searchFrom
    ) {
        String targetKey = normalizeSourcePath(citationLiteral);
        if (isBlank(targetKey)) {
            return null;
        }
        String quotedTargetKey = Pattern.quote(targetKey);
        Pattern sourcePattern = Pattern.compile(
                "(?:\\[\\[→\\s*" + quotedTargetKey + "(?::(?:L)?\\d+(?:-(?:L)?\\d+)?)?\\s*(?:,[^\\]]+)?]]"
                        + "|\\[→\\s*" + quotedTargetKey + "(?::(?:L)?\\d+(?:-(?:L)?\\d+)?)?\\s*(?:,[^\\]]+)?])"
        );
        Matcher matcher = sourcePattern.matcher(paragraphText);
        if (!matcher.find(Math.max(0, searchFrom))) {
            return null;
        }
        return new CitationLiteralMatch(matcher.start(), matcher.end());
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
     * 索引 ACTIVE projection。
     *
     * @param answerProjectionBundle projection 白名单
     * @return citation literal 到 projection 的映射
     */
    private static Map<String, AnswerProjection> indexActiveProjectionsByLiteral(
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
    private static Map<Integer, CitationValidationResult> indexValidationResults(CitationCheckReport citationCheckReport) {
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
     * 转换单条 citation marker 来源响应。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @param validationResult citation 校验结果
     * @param hitByTargetKey 命中索引
     * @return marker 来源响应
     */
    private static QueryCitationSourceResponse toCitationSourceResponse(
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

    /**
     * 解析引用点来源类型。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @return 来源类型
     */
    private static ProjectionCitationFormat resolveMarkerSourceType(
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
    private static String resolveMarkerTargetKey(
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
    private static String resolveMarkerTitle(
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
    private static List<String> resolveMarkerSourcePaths(
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
     * 构造 citation marker 来源去重键。
     *
     * @param sourceResponse marker 来源响应
     * @return 去重键
     */
    private static String citationSourceResponseKey(QueryCitationSourceResponse sourceResponse) {
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
    private static String normalizeSourcePath(String sourcePath) {
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
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class CitationLiteralMatch {

        private final int startIndex;

        private final int endIndex;

        /**
         * 创建 citation literal 匹配范围。
         *
         * @param startIndex 起始下标
         * @param endIndex 结束下标
         */
        private CitationLiteralMatch(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        /**
         * 返回起始下标。
         *
         * @return 起始下标
         */
        private int getStartIndex() {
            return startIndex;
        }

        /**
         * 返回结束下标。
         *
         * @return 结束下标
         */
        private int getEndIndex() {
            return endIndex;
        }
    }
}
