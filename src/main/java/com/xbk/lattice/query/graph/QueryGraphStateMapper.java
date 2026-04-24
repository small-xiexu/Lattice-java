package com.xbk.lattice.query.graph;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 问答图状态映射器
 *
 * 职责：在 QueryGraphState 与 Graph 状态 Map 之间做统一转换
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class QueryGraphStateMapper {

    /**
     * 从状态 Map 构建强类型状态。
     *
     * @param stateMap 原始状态 Map
     * @return 强类型状态
     */
    public QueryGraphState fromMap(Map<String, Object> stateMap) {
        QueryGraphState state = new QueryGraphState();
        String queryId = readString(stateMap, QueryGraphStateKeys.QUERY_ID);
        if (queryId == null) {
            queryId = readString(stateMap, QueryGraphStateKeys.LEGACY_REQUEST_ID);
        }
        state.setQueryId(queryId);
        state.setQuestion(readString(stateMap, QueryGraphStateKeys.QUESTION));
        state.setNormalizedQuestion(readString(stateMap, QueryGraphStateKeys.NORMALIZED_QUESTION));
        state.setLlmScopeType(readString(stateMap, QueryGraphStateKeys.LLM_SCOPE_TYPE));
        state.setLlmScopeId(readString(stateMap, QueryGraphStateKeys.LLM_SCOPE_ID));
        state.setTraceId(readString(stateMap, QueryGraphStateKeys.TRACE_ID));
        state.setSpanId(readString(stateMap, QueryGraphStateKeys.SPAN_ID));
        state.setRootTraceId(readString(stateMap, QueryGraphStateKeys.ROOT_TRACE_ID));
        state.setCacheHit(readBoolean(stateMap, QueryGraphStateKeys.CACHE_HIT));
        state.setHasFusedHits(readBoolean(stateMap, QueryGraphStateKeys.HAS_FUSED_HITS));
        state.setRetrievedHitGroupsRef(readString(stateMap, QueryGraphStateKeys.RETRIEVED_HIT_GROUPS_REF));
        state.setFtsHitsRef(readString(stateMap, QueryGraphStateKeys.FTS_HITS_REF));
        state.setRefkeyHitsRef(readString(stateMap, QueryGraphStateKeys.REFKEY_HITS_REF));
        state.setSourceHitsRef(readString(stateMap, QueryGraphStateKeys.SOURCE_HITS_REF));
        state.setContributionHitsRef(readString(stateMap, QueryGraphStateKeys.CONTRIBUTION_HITS_REF));
        state.setGraphHitsRef(readString(stateMap, QueryGraphStateKeys.GRAPH_HITS_REF));
        state.setArticleVectorHitsRef(readString(stateMap, QueryGraphStateKeys.ARTICLE_VECTOR_HITS_REF));
        state.setChunkVectorHitsRef(readString(stateMap, QueryGraphStateKeys.CHUNK_VECTOR_HITS_REF));
        state.setFusedHitsRef(readString(stateMap, QueryGraphStateKeys.FUSED_HITS_REF));
        state.setRetrievalMode(readString(stateMap, QueryGraphStateKeys.RETRIEVAL_MODE));
        state.setRetrievalStartedAtEpochMs(readLong(stateMap, QueryGraphStateKeys.RETRIEVAL_STARTED_AT_EPOCH_MS));
        state.setDraftAnswerRef(readString(stateMap, QueryGraphStateKeys.DRAFT_ANSWER_REF));
        state.setReviewResultRef(readString(stateMap, QueryGraphStateKeys.REVIEW_RESULT_REF));
        state.setCachedResponseRef(readString(stateMap, QueryGraphStateKeys.CACHED_RESPONSE_REF));
        state.setFinalResponseRef(readString(stateMap, QueryGraphStateKeys.FINAL_RESPONSE_REF));
        state.setClaimSegmentsRef(readString(stateMap, QueryGraphStateKeys.CLAIM_SEGMENTS_REF));
        state.setCitationCheckReportRef(readString(stateMap, QueryGraphStateKeys.CITATION_CHECK_REPORT_REF));
        state.setAnswerAuditRef(readString(stateMap, QueryGraphStateKeys.ANSWER_AUDIT_REF));
        state.setLlmBindingSnapshotRef(readString(stateMap, QueryGraphStateKeys.LLM_BINDING_SNAPSHOT_REF));
        state.setAnswerRoute(readString(stateMap, QueryGraphStateKeys.ANSWER_ROUTE));
        state.setReviewRoute(readString(stateMap, QueryGraphStateKeys.REVIEW_ROUTE));
        state.setRewriteRoute(readString(stateMap, QueryGraphStateKeys.REWRITE_ROUTE));
        state.setReviewStatus(readString(stateMap, QueryGraphStateKeys.REVIEW_STATUS));
        state.setAnswerOutcome(readString(stateMap, QueryGraphStateKeys.ANSWER_OUTCOME));
        state.setGenerationMode(readString(stateMap, QueryGraphStateKeys.GENERATION_MODE));
        state.setModelExecutionStatus(readString(stateMap, QueryGraphStateKeys.MODEL_EXECUTION_STATUS));
        state.setAnswerCacheable(readBoolean(stateMap, QueryGraphStateKeys.ANSWER_CACHEABLE));
        state.setRewriteAttemptCount(readInt(stateMap, QueryGraphStateKeys.REWRITE_ATTEMPT_COUNT));
        state.setMaxRewriteRounds(readInt(stateMap, QueryGraphStateKeys.MAX_REWRITE_ROUNDS));
        state.setCitationRepairAttemptCount(readInt(stateMap, QueryGraphStateKeys.CITATION_REPAIR_ATTEMPT_COUNT));
        return state;
    }

    /**
     * 把强类型状态转换为完整状态 Map。
     *
     * @param state 强类型状态
     * @return 状态 Map
     */
    public Map<String, Object> toMap(QueryGraphState state) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(QueryGraphStateKeys.QUERY_ID, state.getQueryId());
        values.put(QueryGraphStateKeys.QUESTION, state.getQuestion());
        values.put(QueryGraphStateKeys.NORMALIZED_QUESTION, state.getNormalizedQuestion());
        values.put(QueryGraphStateKeys.LLM_SCOPE_TYPE, state.getLlmScopeType());
        values.put(QueryGraphStateKeys.LLM_SCOPE_ID, state.getLlmScopeId());
        values.put(QueryGraphStateKeys.TRACE_ID, state.getTraceId());
        values.put(QueryGraphStateKeys.SPAN_ID, state.getSpanId());
        values.put(QueryGraphStateKeys.ROOT_TRACE_ID, state.getRootTraceId());
        values.put(QueryGraphStateKeys.CACHE_HIT, state.isCacheHit());
        values.put(QueryGraphStateKeys.HAS_FUSED_HITS, state.isHasFusedHits());
        values.put(QueryGraphStateKeys.RETRIEVED_HIT_GROUPS_REF, state.getRetrievedHitGroupsRef());
        values.put(QueryGraphStateKeys.FTS_HITS_REF, state.getFtsHitsRef());
        values.put(QueryGraphStateKeys.REFKEY_HITS_REF, state.getRefkeyHitsRef());
        values.put(QueryGraphStateKeys.SOURCE_HITS_REF, state.getSourceHitsRef());
        values.put(QueryGraphStateKeys.CONTRIBUTION_HITS_REF, state.getContributionHitsRef());
        values.put(QueryGraphStateKeys.GRAPH_HITS_REF, state.getGraphHitsRef());
        values.put(QueryGraphStateKeys.ARTICLE_VECTOR_HITS_REF, state.getArticleVectorHitsRef());
        values.put(QueryGraphStateKeys.CHUNK_VECTOR_HITS_REF, state.getChunkVectorHitsRef());
        values.put(QueryGraphStateKeys.FUSED_HITS_REF, state.getFusedHitsRef());
        values.put(QueryGraphStateKeys.RETRIEVAL_MODE, state.getRetrievalMode());
        values.put(QueryGraphStateKeys.RETRIEVAL_STARTED_AT_EPOCH_MS, state.getRetrievalStartedAtEpochMs());
        values.put(QueryGraphStateKeys.DRAFT_ANSWER_REF, state.getDraftAnswerRef());
        values.put(QueryGraphStateKeys.REVIEW_RESULT_REF, state.getReviewResultRef());
        values.put(QueryGraphStateKeys.CACHED_RESPONSE_REF, state.getCachedResponseRef());
        values.put(QueryGraphStateKeys.FINAL_RESPONSE_REF, state.getFinalResponseRef());
        values.put(QueryGraphStateKeys.CLAIM_SEGMENTS_REF, state.getClaimSegmentsRef());
        values.put(QueryGraphStateKeys.CITATION_CHECK_REPORT_REF, state.getCitationCheckReportRef());
        values.put(QueryGraphStateKeys.ANSWER_AUDIT_REF, state.getAnswerAuditRef());
        values.put(QueryGraphStateKeys.LLM_BINDING_SNAPSHOT_REF, state.getLlmBindingSnapshotRef());
        values.put(QueryGraphStateKeys.ANSWER_ROUTE, state.getAnswerRoute());
        values.put(QueryGraphStateKeys.REVIEW_ROUTE, state.getReviewRoute());
        values.put(QueryGraphStateKeys.REWRITE_ROUTE, state.getRewriteRoute());
        values.put(QueryGraphStateKeys.REVIEW_STATUS, state.getReviewStatus());
        values.put(QueryGraphStateKeys.ANSWER_OUTCOME, state.getAnswerOutcome());
        values.put(QueryGraphStateKeys.GENERATION_MODE, state.getGenerationMode());
        values.put(QueryGraphStateKeys.MODEL_EXECUTION_STATUS, state.getModelExecutionStatus());
        values.put(QueryGraphStateKeys.ANSWER_CACHEABLE, state.isAnswerCacheable());
        values.put(QueryGraphStateKeys.REWRITE_ATTEMPT_COUNT, state.getRewriteAttemptCount());
        values.put(QueryGraphStateKeys.MAX_REWRITE_ROUNDS, state.getMaxRewriteRounds());
        values.put(QueryGraphStateKeys.CITATION_REPAIR_ATTEMPT_COUNT, state.getCitationRepairAttemptCount());
        return values;
    }

    /**
     * 把强类型状态转换为增量状态 Map。
     *
     * @param state 强类型状态
     * @return 增量状态 Map
     */
    public Map<String, Object> toDeltaMap(QueryGraphState state) {
        return toMap(state);
    }

    /**
     * 读取字符串值。
     *
     * @param stateMap 状态 Map
     * @param key 键名
     * @return 字符串值
     */
    private String readString(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 读取整型值。
     *
     * @param stateMap 状态 Map
     * @param key 键名
     * @return 整型值
     */
    private int readInt(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (value instanceof Number) {
            Number number = (Number) value;
            return number.intValue();
        }
        if (value instanceof String) {
            String stringValue = (String) value;
            if (!stringValue.isBlank()) {
                return Integer.parseInt(stringValue);
            }
        }
        return 0;
    }

    /**
     * 读取长整型值。
     *
     * @param stateMap 状态 Map
     * @param key 键名
     * @return 长整型值
     */
    private long readLong(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (value instanceof Number) {
            Number number = (Number) value;
            return number.longValue();
        }
        if (value instanceof String) {
            String stringValue = (String) value;
            if (!stringValue.isBlank()) {
                return Long.parseLong(stringValue);
            }
        }
        return 0L;
    }

    /**
     * 读取布尔值。
     *
     * @param stateMap 状态 Map
     * @param key 键名
     * @return 布尔值
     */
    private boolean readBoolean(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (value instanceof Boolean) {
            Boolean booleanValue = (Boolean) value;
            return booleanValue;
        }
        if (value instanceof String) {
            String stringValue = (String) value;
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }
}
