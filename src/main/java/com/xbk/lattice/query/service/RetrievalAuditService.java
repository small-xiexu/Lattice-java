package com.xbk.lattice.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.QueryRetrievalAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitRecord;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunRecord;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
        Long runId = queryRetrievalAuditJdbcRepository.insertRun(new QueryRetrievalRunRecord(
                effectiveQueryId,
                effectiveQuestion,
                effectiveNormalizedQuestion,
                defaultString(retrievalStrategy.getRetrievalQuestion()),
                VERSION_TAG,
                strategyTag,
                retrievalStrategy.getQueryIntent().name(),
                normalizeMode(retrievalMode, retrievalStrategy),
                rewriteApplied,
                defaultString(rewriteAuditRef),
                defaultString(retrievalStrategyRef),
                safeFusedHits.size(),
                retrievalStrategy.getEnabledChannels().size()
        ));
        Map<String, Integer> fusedRankByKey = buildFusedRankByKey(safeFusedHits);
        for (Map.Entry<String, List<QueryArticleHit>> entry : safeChannelHits.entrySet()) {
            String channelName = entry.getKey();
            List<QueryArticleHit> hits = safeHits(entry.getValue());
            double channelWeight = retrievalStrategy.weightOf(channelName);
            for (int index = 0; index < hits.size(); index++) {
                QueryArticleHit queryArticleHit = hits.get(index);
                String hitKey = hitKeyOf(queryArticleHit);
                Integer fusedRank = fusedRankByKey.get(hitKey);
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
                        toJsonArray(queryArticleHit == null ? null : queryArticleHit.getSourcePaths()),
                        normalizeMetadataJson(queryArticleHit == null ? null : queryArticleHit.getMetadataJson())
                ));
            }
        }
        return AUDIT_REF_PREFIX + runId;
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
                + "|mode=" + (retrievalStrategy.isParallelEnabled() ? "parallel" : "serial")
                + "|rewrite=" + (rewriteApplied ? "on" : "off")
                + "|graph=" + (graphEnabled ? "on" : "off")
                + "|vector=" + (vectorEnabled ? "on" : "off");
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
}
