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
        state.setCacheHit(readBoolean(stateMap, QueryGraphStateKeys.CACHE_HIT));
        state.setHasFusedHits(readBoolean(stateMap, QueryGraphStateKeys.HAS_FUSED_HITS));
        state.setRetrievedHitGroupsRef(readString(stateMap, QueryGraphStateKeys.RETRIEVED_HIT_GROUPS_REF));
        state.setFusedHitsRef(readString(stateMap, QueryGraphStateKeys.FUSED_HITS_REF));
        state.setDraftAnswerRef(readString(stateMap, QueryGraphStateKeys.DRAFT_ANSWER_REF));
        state.setReviewResultRef(readString(stateMap, QueryGraphStateKeys.REVIEW_RESULT_REF));
        state.setCachedResponseRef(readString(stateMap, QueryGraphStateKeys.CACHED_RESPONSE_REF));
        state.setFinalResponseRef(readString(stateMap, QueryGraphStateKeys.FINAL_RESPONSE_REF));
        state.setReviewStatus(readString(stateMap, QueryGraphStateKeys.REVIEW_STATUS));
        state.setRewriteAttemptCount(readInt(stateMap, QueryGraphStateKeys.REWRITE_ATTEMPT_COUNT));
        state.setMaxRewriteRounds(readInt(stateMap, QueryGraphStateKeys.MAX_REWRITE_ROUNDS));
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
        values.put(QueryGraphStateKeys.CACHE_HIT, state.isCacheHit());
        values.put(QueryGraphStateKeys.HAS_FUSED_HITS, state.isHasFusedHits());
        values.put(QueryGraphStateKeys.RETRIEVED_HIT_GROUPS_REF, state.getRetrievedHitGroupsRef());
        values.put(QueryGraphStateKeys.FUSED_HITS_REF, state.getFusedHitsRef());
        values.put(QueryGraphStateKeys.DRAFT_ANSWER_REF, state.getDraftAnswerRef());
        values.put(QueryGraphStateKeys.REVIEW_RESULT_REF, state.getReviewResultRef());
        values.put(QueryGraphStateKeys.CACHED_RESPONSE_REF, state.getCachedResponseRef());
        values.put(QueryGraphStateKeys.FINAL_RESPONSE_REF, state.getFinalResponseRef());
        values.put(QueryGraphStateKeys.REVIEW_STATUS, state.getReviewStatus());
        values.put(QueryGraphStateKeys.REWRITE_ATTEMPT_COUNT, state.getRewriteAttemptCount());
        values.put(QueryGraphStateKeys.MAX_REWRITE_ROUNDS, state.getMaxRewriteRounds());
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
