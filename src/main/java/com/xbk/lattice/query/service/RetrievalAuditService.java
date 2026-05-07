package com.xbk.lattice.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.QueryRetrievalAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitRecord;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 检索审计服务
 *
 * 职责：把 Query / KnowledgeSearch 的检索通道命中与融合结果写入审计表
 *
 * @author xiexu
 */
@Service
public class RetrievalAuditService {

    public static final String VERSION_TAG = "retrieval-core-v2";

    private static final String AUDIT_REF_PREFIX = "query_retrieval_runs:";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final QueryRetrievalAuditJdbcRepository queryRetrievalAuditJdbcRepository;

    /**
     * 创建检索审计服务。
     *
     * @param queryRetrievalAuditJdbcRepository 检索审计仓储
     */
    public RetrievalAuditService(QueryRetrievalAuditJdbcRepository queryRetrievalAuditJdbcRepository) {
        this.queryRetrievalAuditJdbcRepository = queryRetrievalAuditJdbcRepository;
    }

    /**
     * 写入一次检索审计。
     *
     * @param queryId 查询标识
     * @param question 原始问题
     * @param normalizedQuestion 归一化问题
     * @param retrievalStrategy 检索策略
     * @param retrievalMode 检索模式
     * @param rewriteApplied 是否发生改写
     * @param rewriteAuditRef 改写审计引用
     * @param retrievalStrategyRef 检索策略引用
     * @param channelHits 通道命中
     * @param fusedHits 融合命中
     * @return 审计引用
     */
    @Transactional(rollbackFor = Exception.class)
    public String persist(
            String queryId,
            String question,
            String normalizedQuestion,
            RetrievalStrategy retrievalStrategy,
            String retrievalMode,
            boolean rewriteApplied,
            String rewriteAuditRef,
            String retrievalStrategyRef,
            Map<String, List<QueryArticleHit>> channelHits,
            List<QueryArticleHit> fusedHits
    ) {
        return persist(
                queryId,
                question,
                normalizedQuestion,
                retrievalStrategy,
                retrievalMode,
                rewriteApplied,
                rewriteAuditRef,
                retrievalStrategyRef,
                channelHits,
                fusedHits,
                Collections.<String, RetrievalChannelRun>emptyMap()
        );
    }

    /**
     * 基于上下文写入一次检索审计。
     *
     * @param retrievalQueryContext 检索上下文
     * @param channelHits 通道命中
     * @param fusedHits 融合命中
     * @return 审计引用
     */
    public String persist(
            RetrievalQueryContext retrievalQueryContext,
            Map<String, List<QueryArticleHit>> channelHits,
            List<QueryArticleHit> fusedHits
    ) {
        if (retrievalQueryContext == null) {
            return "";
        }
        RetrievalStrategy retrievalStrategy = retrievalQueryContext.getRetrievalStrategy();
        QueryRewriteResult queryRewriteResult = retrievalQueryContext.getQueryRewriteResult();
        return persist(
                retrievalQueryContext.getQueryId(),
                retrievalQueryContext.getOriginalQuestion(),
                retrievalQueryContext.getNormalizedQuestion(),
                retrievalStrategy,
                retrievalStrategy == null || !retrievalStrategy.isParallelEnabled() ? "serial" : "parallel",
                queryRewriteResult != null && queryRewriteResult.isRewriteApplied(),
                queryRewriteResult == null ? "" : queryRewriteResult.getAuditRef(),
                "",
                channelHits,
                fusedHits
        );
    }

    /**
     * 基于上下文写入带通道运行摘要的检索审计。
     *
     * @param retrievalQueryContext 检索上下文
     * @param dispatchResult 调度结果
     * @param fusedHits 融合命中
     * @return 审计引用
     */
    public String persist(
            RetrievalQueryContext retrievalQueryContext,
            RetrievalDispatchResult dispatchResult,
            List<QueryArticleHit> fusedHits
    ) {
        if (retrievalQueryContext == null) {
            return "";
        }
        Map<String, List<QueryArticleHit>> channelHits = dispatchResult == null
                ? Collections.<String, List<QueryArticleHit>>emptyMap()
                : dispatchResult.getChannelHits();
        Map<String, RetrievalChannelRun> channelRuns = dispatchResult == null
                ? Collections.<String, RetrievalChannelRun>emptyMap()
                : dispatchResult.getChannelRuns();
        RetrievalStrategy retrievalStrategy = retrievalQueryContext.getRetrievalStrategy();
        QueryRewriteResult queryRewriteResult = retrievalQueryContext.getQueryRewriteResult();
        return persist(
                retrievalQueryContext.getQueryId(),
                retrievalQueryContext.getOriginalQuestion(),
                retrievalQueryContext.getNormalizedQuestion(),
                retrievalStrategy,
                retrievalStrategy == null || !retrievalStrategy.isParallelEnabled() ? "serial" : "parallel",
                queryRewriteResult != null && queryRewriteResult.isRewriteApplied(),
                queryRewriteResult == null ? "" : queryRewriteResult.getAuditRef(),
                "",
                channelHits,
                fusedHits,
                channelRuns
        );
    }

    /**
     * 写入一次带通道运行摘要的检索审计。
     *
     * @param queryId 查询标识
     * @param question 原始问题
     * @param normalizedQuestion 归一化问题
     * @param retrievalStrategy 检索策略
     * @param retrievalMode 检索模式
     * @param rewriteApplied 是否发生改写
     * @param rewriteAuditRef 改写审计引用
     * @param retrievalStrategyRef 检索策略引用
     * @param channelHits 通道命中
     * @param fusedHits 融合命中
     * @param channelRuns 通道运行摘要
     * @return 审计引用
     */
    @Transactional(rollbackFor = Exception.class)
    public String persist(
            String queryId,
            String question,
            String normalizedQuestion,
            RetrievalStrategy retrievalStrategy,
            String retrievalMode,
            boolean rewriteApplied,
            String rewriteAuditRef,
            String retrievalStrategyRef,
            Map<String, List<QueryArticleHit>> channelHits,
            List<QueryArticleHit> fusedHits,
            Map<String, RetrievalChannelRun> channelRuns
    ) {
        if (queryRetrievalAuditJdbcRepository == null || retrievalStrategy == null) {
            return "";
        }
        String effectiveQueryId = normalizeQueryId(queryId);
        String effectiveQuestion = defaultString(question);
        String effectiveNormalizedQuestion = normalizeQuestion(normalizedQuestion, effectiveQuestion);
        List<QueryArticleHit> safeFusedHits = safeHits(fusedHits);
        Map<String, List<QueryArticleHit>> safeChannelHits = channelHits == null
                ? Collections.<String, List<QueryArticleHit>>emptyMap()
                : channelHits;
        String strategyTag = buildStrategyTag(retrievalStrategy, rewriteApplied);
        int factCardHitCount = countFactCardHits(safeChannelHits);
        int sourceChunkHitCount = countSourceChunkHits(safeChannelHits);
        Map<String, Integer> fusedRankByKey = buildFusedRankByKey(safeFusedHits);
        String coverageStatus = resolveCoverageStatus(
                retrievalStrategy,
                safeChannelHits,
                fusedRankByKey,
                factCardHitCount,
                sourceChunkHitCount
        );
        Long runId = queryRetrievalAuditJdbcRepository.insertRun(new QueryRetrievalRunRecord(
                effectiveQueryId,
                effectiveQuestion,
                effectiveNormalizedQuestion,
                defaultString(retrievalStrategy.getRetrievalQuestion()),
                VERSION_TAG,
                strategyTag,
                retrievalStrategy.getQueryIntent().name(),
                retrievalStrategy.getAnswerShape().name(),
                normalizeMode(retrievalMode, retrievalStrategy),
                rewriteApplied,
                defaultString(rewriteAuditRef),
                defaultString(retrievalStrategyRef),
                safeFusedHits.size(),
                retrievalStrategy.getEnabledChannels().size(),
                factCardHitCount,
                sourceChunkHitCount,
                coverageStatus,
                toChannelRunSummaryJson(channelRuns)
        ));
        persistChannelHits(runId, retrievalStrategy, safeChannelHits, fusedRankByKey);
        return AUDIT_REF_PREFIX + runId;
    }

    /**
     * 持久化通道命中明细。
     *
     * @param runId run 主键
     * @param retrievalStrategy 检索策略
     * @param safeChannelHits 通道命中
     * @param fusedRankByKey 融合排序
     */
    private void persistChannelHits(
            Long runId,
            RetrievalStrategy retrievalStrategy,
            Map<String, List<QueryArticleHit>> safeChannelHits,
            Map<String, Integer> fusedRankByKey
    ) {
        for (Map.Entry<String, List<QueryArticleHit>> entry : safeChannelHits.entrySet()) {
            String channelName = entry.getKey();
            List<QueryArticleHit> hits = safeHits(entry.getValue());
            double channelWeight = retrievalStrategy.weightOf(channelName);
            for (int index = 0; index < hits.size(); index++) {
                persistSingleChannelHit(runId, channelName, channelWeight, index, hits.get(index), fusedRankByKey);
            }
        }
    }

    /**
     * 持久化单条通道命中。
     *
     * @param runId run 主键
     * @param channelName 通道名称
     * @param channelWeight 通道权重
     * @param index 通道内索引
     * @param queryArticleHit 查询命中
     * @param fusedRankByKey 融合排序
     */
    private void persistSingleChannelHit(
            Long runId,
            String channelName,
            double channelWeight,
            int index,
            QueryArticleHit queryArticleHit,
            Map<String, Integer> fusedRankByKey
    ) {
        String hitKey = hitKeyOf(queryArticleHit);
        Integer fusedRank = fusedRankByKey.get(hitKey);
        FactCardHitAuditMetadata factCardMetadata = extractFactCardMetadata(queryArticleHit);
        queryRetrievalAuditJdbcRepository.insertChannelHit(new QueryRetrievalChannelHitRecord(
                runId,
                channelName,
                index + 1,
                fusedRank,
                fusedRank != null,
                channelWeight,
                resolveEvidenceType(queryArticleHit),
                defaultString(queryArticleHit == null ? null : queryArticleHit.getArticleKey()),
                defaultString(queryArticleHit == null ? null : queryArticleHit.getConceptId()),
                defaultString(queryArticleHit == null ? null : queryArticleHit.getTitle()),
                queryArticleHit == null ? 0.0D : queryArticleHit.getScore(),
                factCardMetadata.factCardId(),
                factCardMetadata.cardType(),
                factCardMetadata.reviewStatus(),
                factCardMetadata.confidence(),
                factCardMetadata.sourceChunkIdsJson(),
                toJsonArray(queryArticleHit == null ? null : queryArticleHit.getSourcePaths()),
                normalizeMetadataJson(queryArticleHit == null ? null : queryArticleHit.getMetadataJson())
        ));
    }

    private String normalizeQueryId(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return queryId.trim();
    }

    private String normalizeQuestion(String normalizedQuestion, String question) {
        if (normalizedQuestion != null && !normalizedQuestion.isBlank()) {
            return normalizedQuestion.trim();
        }
        return question == null ? "" : question.trim();
    }

    private String normalizeMode(String retrievalMode, RetrievalStrategy retrievalStrategy) {
        if (retrievalMode != null && !retrievalMode.isBlank()) {
            return retrievalMode.trim().toLowerCase(Locale.ROOT);
        }
        return retrievalStrategy != null && retrievalStrategy.isParallelEnabled() ? "parallel" : "serial";
    }

    private String buildStrategyTag(RetrievalStrategy retrievalStrategy, boolean rewriteApplied) {
        if (retrievalStrategy == null) {
            return "";
        }
        boolean vectorEnabled = retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR)
                || retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR);
        boolean graphEnabled = retrievalStrategy.isChannelEnabled(RetrievalStrategyResolver.CHANNEL_GRAPH);
        return "intent=" + retrievalStrategy.getQueryIntent().name()
                + "|shape=" + retrievalStrategy.getAnswerShape().name()
                + "|mode=" + (retrievalStrategy.isParallelEnabled() ? "parallel" : "serial")
                + "|rewrite=" + (rewriteApplied ? "on" : "off")
                + "|graph=" + (graphEnabled ? "on" : "off")
                + "|vector=" + (vectorEnabled ? "on" : "off");
    }

    /**
     * 统计 Fact Card 通道命中数。
     *
     * @param channelHits 通道命中
     * @return Fact Card 命中数
     */
    private int countFactCardHits(Map<String, List<QueryArticleHit>> channelHits) {
        int hitCount = 0;
        hitCount += safeHits(channelHits.get(RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS)).size();
        hitCount += safeHits(channelHits.get(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR)).size();
        return hitCount;
    }

    /**
     * 统计 source chunk 通道命中数。
     *
     * @param channelHits 通道命中
     * @return source chunk 命中数
     */
    private int countSourceChunkHits(Map<String, List<QueryArticleHit>> channelHits) {
        return safeHits(channelHits.get(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS)).size();
    }

    /**
     * 解析结构化证据覆盖状态。
     *
     * @param retrievalStrategy 检索策略
     * @param channelHits 通道命中
     * @param fusedRankByKey 融合排序
     * @param factCardHitCount Fact Card 命中数
     * @param sourceChunkHitCount Source Chunk 命中数
     * @return 覆盖状态
     */
    private String resolveCoverageStatus(
            RetrievalStrategy retrievalStrategy,
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, Integer> fusedRankByKey,
            int factCardHitCount,
            int sourceChunkHitCount
    ) {
        if (retrievalStrategy == null || retrievalStrategy.getAnswerShape() == null
                || retrievalStrategy.getAnswerShape() == AnswerShape.GENERAL) {
            return "not_applicable";
        }
        if (factCardHitCount <= 0 && sourceChunkHitCount <= 0) {
            return "missing";
        }
        if (hasPrimaryStructuredHitInFused(channelHits, fusedRankByKey)) {
            return "covered";
        }
        return "partial";
    }

    /**
     * 判断结构化主证据是否进入融合结果。
     *
     * @param channelHits 通道命中
     * @param fusedRankByKey 融合排序
     * @return 已进入返回 true
     */
    private boolean hasPrimaryStructuredHitInFused(
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, Integer> fusedRankByKey
    ) {
        return hasAnyHitInFused(channelHits.get(RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS), fusedRankByKey)
                || hasAnyHitInFused(channelHits.get(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR), fusedRankByKey)
                || hasAnyHitInFused(channelHits.get(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS), fusedRankByKey);
    }

    /**
     * 判断通道命中是否进入融合结果。
     *
     * @param hits 通道命中
     * @param fusedRankByKey 融合排序
     * @return 已进入返回 true
     */
    private boolean hasAnyHitInFused(List<QueryArticleHit> hits, Map<String, Integer> fusedRankByKey) {
        for (QueryArticleHit hit : safeHits(hits)) {
            if (fusedRankByKey.containsKey(hitKeyOf(hit))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取 Fact Card 命中审计元数据。
     *
     * @param queryArticleHit 查询命中
     * @return Fact Card 审计元数据
     */
    private FactCardHitAuditMetadata extractFactCardMetadata(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() != QueryEvidenceType.FACT_CARD) {
            return FactCardHitAuditMetadata.empty();
        }
        JsonNode metadataNode = readMetadataNode(queryArticleHit.getMetadataJson());
        return new FactCardHitAuditMetadata(
                readLong(metadataNode, "factCardId"),
                readText(metadataNode, "cardType"),
                defaultString(queryArticleHit.getReviewStatus()),
                readDouble(metadataNode, "confidence"),
                readJsonArray(metadataNode, "sourceChunkIds")
        );
    }

    /**
     * 读取元数据 JSON。
     *
     * @param metadataJson 元数据 JSON
     * @return JSON 节点
     */
    private JsonNode readMetadataNode(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(metadataJson);
        }
        catch (JsonProcessingException exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    /**
     * 从 JSON 节点读取长整型。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 长整型值
     */
    private Long readLong(JsonNode node, String fieldName) {
        JsonNode valueNode = node == null ? null : node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.canConvertToLong()) {
            return null;
        }
        return Long.valueOf(valueNode.longValue());
    }

    /**
     * 从 JSON 节点读取文本。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return 文本值
     */
    private String readText(JsonNode node, String fieldName) {
        JsonNode valueNode = node == null ? null : node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return "";
        }
        return valueNode.asText("");
    }

    /**
     * 从 JSON 节点读取 double。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return double 值
     */
    private Double readDouble(JsonNode node, String fieldName) {
        JsonNode valueNode = node == null ? null : node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.isNumber()) {
            return null;
        }
        return Double.valueOf(valueNode.doubleValue());
    }

    /**
     * 从 JSON 节点读取数组并序列化。
     *
     * @param node JSON 节点
     * @param fieldName 字段名
     * @return JSON 数组文本
     */
    private String readJsonArray(JsonNode node, String fieldName) {
        JsonNode valueNode = node == null ? null : node.get(fieldName);
        if (valueNode == null || !valueNode.isArray()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(valueNode);
        }
        catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private Map<String, Integer> buildFusedRankByKey(List<QueryArticleHit> fusedHits) {
        Map<String, Integer> fusedRankByKey = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < fusedHits.size(); index++) {
            fusedRankByKey.putIfAbsent(hitKeyOf(fusedHits.get(index)), Integer.valueOf(index + 1));
        }
        return fusedRankByKey;
    }

    private List<QueryArticleHit> safeHits(List<QueryArticleHit> hits) {
        return hits == null ? List.<QueryArticleHit>of() : hits;
    }

    private String hitKeyOf(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return "article:" + queryArticleHit.getArticleKey().trim();
        }
        if (queryArticleHit.getConceptId() != null && !queryArticleHit.getConceptId().isBlank()) {
            return "concept:" + queryArticleHit.getConceptId().trim();
        }
        return "title:" + defaultString(queryArticleHit.getTitle());
    }

    private String resolveEvidenceType(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() == null) {
            return QueryEvidenceType.ARTICLE.name();
        }
        return queryArticleHit.getEvidenceType().name();
    }

    private String toJsonArray(List<String> sourcePaths) {
        try {
            return OBJECT_MAPPER.writeValueAsString(sourcePaths == null ? List.of() : sourcePaths);
        }
        catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    /**
     * 序列化通道运行摘要。
     *
     * @param channelRuns 通道运行摘要
     * @return JSON 文本
     */
    private String toChannelRunSummaryJson(Map<String, RetrievalChannelRun> channelRuns) {
        if (channelRuns == null || channelRuns.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(channelRuns);
        }
        catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String normalizeMetadataJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readTree(metadataJson));
        }
        catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    /**
     * Fact Card 命中审计元数据。
     *
     * 职责：承载从 QueryArticleHit metadata 中解析出的结构化审计字段
     *
     * @author xiexu
     */
    private static class FactCardHitAuditMetadata {

        private final Long factCardId;

        private final String cardType;

        private final String reviewStatus;

        private final Double confidence;

        private final String sourceChunkIdsJson;

        /**
         * 创建 Fact Card 命中审计元数据。
         *
         * @param factCardId Fact Card 数据库主键
         * @param cardType Fact Card 类型
         * @param reviewStatus 审查状态
         * @param confidence 置信度
         * @param sourceChunkIdsJson Source Chunk ID JSON
         */
        private FactCardHitAuditMetadata(
                Long factCardId,
                String cardType,
                String reviewStatus,
                Double confidence,
                String sourceChunkIdsJson
        ) {
            this.factCardId = factCardId;
            this.cardType = cardType;
            this.reviewStatus = reviewStatus;
            this.confidence = confidence;
            this.sourceChunkIdsJson = sourceChunkIdsJson;
        }

        /**
         * 返回空 Fact Card 审计元数据。
         *
         * @return 空元数据
         */
        private static FactCardHitAuditMetadata empty() {
            return new FactCardHitAuditMetadata(null, "", "", null, "[]");
        }

        /**
         * 获取 Fact Card 数据库主键。
         *
         * @return Fact Card 数据库主键
         */
        private Long factCardId() {
            return factCardId;
        }

        /**
         * 获取 Fact Card 类型。
         *
         * @return Fact Card 类型
         */
        private String cardType() {
            return cardType;
        }

        /**
         * 获取审查状态。
         *
         * @return 审查状态
         */
        private String reviewStatus() {
            return reviewStatus;
        }

        /**
         * 获取置信度。
         *
         * @return 置信度
         */
        private Double confidence() {
            return confidence;
        }

        /**
         * 获取 Source Chunk ID JSON。
         *
         * @return Source Chunk ID JSON
         */
        private String sourceChunkIdsJson() {
            return sourceChunkIdsJson;
        }
    }
}
