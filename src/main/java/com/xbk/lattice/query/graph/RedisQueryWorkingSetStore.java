package com.xbk.lattice.query.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.redis.AbstractRedisJsonStore;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import com.xbk.lattice.query.service.RetrievalChannelRun;
import com.xbk.lattice.query.service.RetrievalStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 版 Query 工作集存储
 *
 * 职责：将 Query Graph 的大对象工作集序列化到 Redis，并按 TTL 保留用于恢复
 *
 * @author xiexu
 */
@Component
@ConditionalOnProperty(prefix = "lattice.query.working-set", name = "store", havingValue = "redis")
public class RedisQueryWorkingSetStore extends AbstractRedisJsonStore implements QueryWorkingSetStore {

    private static final TypeReference<List<QueryArticleHit>> QUERY_HITS_TYPE = new TypeReference<List<QueryArticleHit>>() {
    };

    private static final TypeReference<List<List<QueryArticleHit>>> QUERY_HIT_GROUPS_TYPE =
            new TypeReference<List<List<QueryArticleHit>>>() {
            };

    private static final TypeReference<Map<String, RetrievalChannelRun>> RETRIEVAL_CHANNEL_RUNS_TYPE =
            new TypeReference<Map<String, RetrievalChannelRun>>() {
            };

    private static final TypeReference<List<ClaimSegment>> CLAIM_SEGMENTS_TYPE = new TypeReference<List<ClaimSegment>>() {
    };

    /**
     * 创建 Redis 版 Query 工作集存储。
     *
     * @param redisKeyValueStore Redis 键值存储
     * @param objectMapper JSON 映射器
     * @param properties Query 工作集配置
     */
    public RedisQueryWorkingSetStore(
            RedisKeyValueStore redisKeyValueStore,
            ObjectMapper objectMapper,
            QueryWorkingSetProperties properties
    ) {
        super(redisKeyValueStore, objectMapper, properties.getKeyPrefix(), properties.getTtlSeconds());
    }

    @Override
    public String saveRetrievedHitGroups(String queryId, List<List<QueryArticleHit>> hitGroups) {
        String ref = buildRef(queryId, "retrieved-hit-groups");
        saveJson(ref, hitGroups == null ? List.of() : hitGroups);
        return ref;
    }

    @Override
    public List<List<QueryArticleHit>> loadRetrievedHitGroups(String ref) {
        List<List<QueryArticleHit>> hitGroups = loadJson(ref, QUERY_HIT_GROUPS_TYPE);
        return hitGroups == null ? List.of() : hitGroups;
    }

    @Override
    public String saveHits(String queryId, String channel, List<QueryArticleHit> hits) {
        String ref = buildRef(queryId, "hits-" + channel);
        saveJson(ref, hits == null ? List.of() : hits);
        return ref;
    }

    @Override
    public List<QueryArticleHit> loadHits(String ref) {
        List<QueryArticleHit> hits = loadJson(ref, QUERY_HITS_TYPE);
        return hits == null ? List.of() : hits;
    }

    @Override
    public String saveFusedHits(String queryId, List<QueryArticleHit> fusedHits) {
        String ref = buildRef(queryId, "fused-hits");
        saveJson(ref, fusedHits == null ? List.of() : fusedHits);
        return ref;
    }

    @Override
    public List<QueryArticleHit> loadFusedHits(String ref) {
        List<QueryArticleHit> hits = loadJson(ref, QUERY_HITS_TYPE);
        return hits == null ? List.of() : hits;
    }

    @Override
    public String saveRetrievalStrategy(String queryId, RetrievalStrategy retrievalStrategy) {
        String ref = buildRef(queryId, "retrieval-strategy");
        saveJson(ref, retrievalStrategy);
        return ref;
    }

    @Override
    public RetrievalStrategy loadRetrievalStrategy(String ref) {
        return loadJson(ref, RetrievalStrategy.class);
    }

    @Override
    public String saveRetrievalChannelRuns(String queryId, Map<String, RetrievalChannelRun> channelRuns) {
        String ref = buildRef(queryId, "retrieval-channel-runs");
        saveJson(ref, channelRuns == null ? Map.of() : channelRuns);
        return ref;
    }

    @Override
    public Map<String, RetrievalChannelRun> loadRetrievalChannelRuns(String ref) {
        Map<String, RetrievalChannelRun> channelRuns = loadJson(ref, RETRIEVAL_CHANNEL_RUNS_TYPE);
        return channelRuns == null ? Map.of() : channelRuns;
    }

    @Override
    public String saveAnswer(String queryId, String answer) {
        String ref = buildRef(queryId, "draft-answer");
        saveJson(ref, answer);
        return ref;
    }

    @Override
    public String loadAnswer(String ref) {
        return loadJson(ref, String.class);
    }

    @Override
    public String saveReviewResult(String queryId, ReviewResult reviewResult) {
        String ref = buildRef(queryId, "review-result");
        saveJson(ref, reviewResult);
        return ref;
    }

    @Override
    public ReviewResult loadReviewResult(String ref) {
        return loadJson(ref, ReviewResult.class);
    }

    @Override
    public String saveClaimSegments(String queryId, List<ClaimSegment> claimSegments) {
        String ref = buildRef(queryId, "claim-segments");
        saveJson(ref, claimSegments == null ? List.of() : claimSegments);
        return ref;
    }

    @Override
    public List<ClaimSegment> loadClaimSegments(String ref) {
        List<ClaimSegment> claimSegments = loadJson(ref, CLAIM_SEGMENTS_TYPE);
        return claimSegments == null ? List.of() : claimSegments;
    }

    @Override
    public String saveCitationCheckReport(String queryId, CitationCheckReport report) {
        String ref = buildRef(queryId, "citation-check-report");
        saveJson(ref, report);
        return ref;
    }

    @Override
    public CitationCheckReport loadCitationCheckReport(String ref) {
        return loadJson(ref, CitationCheckReport.class);
    }

    @Override
    public String saveAnswerAudit(String queryId, QueryAnswerAuditSnapshot answerAuditSnapshot) {
        String ref = buildRef(queryId, "answer-audit");
        saveJson(ref, answerAuditSnapshot);
        return ref;
    }

    @Override
    public QueryAnswerAuditSnapshot loadAnswerAudit(String ref) {
        return loadJson(ref, QueryAnswerAuditSnapshot.class);
    }

    @Override
    public String saveAnswerProjectionBundle(String queryId, AnswerProjectionBundle answerProjectionBundle) {
        String ref = buildRef(queryId, "answer-projection-bundle");
        saveJson(ref, answerProjectionBundle);
        return ref;
    }

    @Override
    public AnswerProjectionBundle loadAnswerProjectionBundle(String ref) {
        return loadJson(ref, AnswerProjectionBundle.class);
    }

    @Override
    public String saveResponse(String queryId, QueryResponse queryResponse) {
        String ref = buildRef(queryId, "response");
        saveJson(ref, queryResponse);
        return ref;
    }

    @Override
    public QueryResponse loadResponse(String ref) {
        return loadJson(ref, QueryResponse.class);
    }

    @Override
    public void deleteByQueryId(String queryId) {
        deleteByOwnerPrefix(queryId + ":");
    }

    private String buildRef(String queryId, String suffix) {
        return queryId + ":" + suffix;
    }
}
