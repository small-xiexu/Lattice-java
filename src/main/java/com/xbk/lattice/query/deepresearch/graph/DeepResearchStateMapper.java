package com.xbk.lattice.query.deepresearch.graph;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep Research 状态映射器
 *
 * 职责：在 DeepResearchState 与 Graph 状态 Map 之间做统一转换
 *
 * @author xiexu
 */
@Component
public class DeepResearchStateMapper {

    /**
     * 从状态 Map 构建强类型状态。
     *
     * @param stateMap 原始状态 Map
     * @return 强类型状态
     */
    public DeepResearchState fromMap(Map<String, Object> stateMap) {
        DeepResearchState state = new DeepResearchState();
        state.setQueryId(readString(stateMap, DeepResearchStateKeys.QUERY_ID));
        state.setQuestion(readString(stateMap, DeepResearchStateKeys.QUESTION));
        state.setLlmScopeType(readString(stateMap, DeepResearchStateKeys.LLM_SCOPE_TYPE));
        state.setLlmScopeId(readString(stateMap, DeepResearchStateKeys.LLM_SCOPE_ID));
        state.setRouteReason(readString(stateMap, DeepResearchStateKeys.ROUTE_REASON));
        state.setPlanRef(readString(stateMap, DeepResearchStateKeys.PLAN_REF));
        state.setTaskResultRefs(readStringList(stateMap, DeepResearchStateKeys.TASK_RESULT_REFS));
        state.setLedgerRef(readString(stateMap, DeepResearchStateKeys.LEDGER_REF));
        state.setCurrentLayerIndex(readInt(stateMap, DeepResearchStateKeys.CURRENT_LAYER_INDEX));
        state.setLayerSummaryRefs(readStringList(stateMap, DeepResearchStateKeys.LAYER_SUMMARY_REFS));
        state.setInternalAnswerDraftRef(readString(stateMap, DeepResearchStateKeys.INTERNAL_ANSWER_DRAFT_REF));
        state.setProjectionRef(readString(stateMap, DeepResearchStateKeys.PROJECTION_REF));
        state.setCitationCheckReportRef(readString(stateMap, DeepResearchStateKeys.CITATION_CHECK_REPORT_REF));
        state.setAnswerAuditRef(readString(stateMap, DeepResearchStateKeys.ANSWER_AUDIT_REF));
        state.setLlmCallBudgetRemaining(readInt(stateMap, DeepResearchStateKeys.LLM_CALL_BUDGET_REMAINING));
        state.setTimedOut(readBoolean(stateMap, DeepResearchStateKeys.TIMED_OUT));
        state.setPartialAnswer(readBoolean(stateMap, DeepResearchStateKeys.PARTIAL_ANSWER));
        state.setHasConflicts(readBoolean(stateMap, DeepResearchStateKeys.HAS_CONFLICTS));
        state.setEvidenceCardCount(readInt(stateMap, DeepResearchStateKeys.EVIDENCE_CARD_COUNT));
        state.setProjectionRetryCount(readInt(stateMap, DeepResearchStateKeys.PROJECTION_RETRY_COUNT));
        return state;
    }

    /**
     * 把强类型状态转换为状态 Map。
     *
     * @param state 强类型状态
     * @return 状态 Map
     */
    public Map<String, Object> toMap(DeepResearchState state) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(DeepResearchStateKeys.QUERY_ID, state.getQueryId());
        values.put(DeepResearchStateKeys.QUESTION, state.getQuestion());
        values.put(DeepResearchStateKeys.LLM_SCOPE_TYPE, state.getLlmScopeType());
        values.put(DeepResearchStateKeys.LLM_SCOPE_ID, state.getLlmScopeId());
        values.put(DeepResearchStateKeys.ROUTE_REASON, state.getRouteReason());
        values.put(DeepResearchStateKeys.PLAN_REF, state.getPlanRef());
        values.put(DeepResearchStateKeys.TASK_RESULT_REFS, new ArrayList<String>(state.getTaskResultRefs()));
        values.put(DeepResearchStateKeys.LEDGER_REF, state.getLedgerRef());
        values.put(DeepResearchStateKeys.CURRENT_LAYER_INDEX, Integer.valueOf(state.getCurrentLayerIndex()));
        values.put(DeepResearchStateKeys.LAYER_SUMMARY_REFS, new ArrayList<String>(state.getLayerSummaryRefs()));
        values.put(DeepResearchStateKeys.INTERNAL_ANSWER_DRAFT_REF, state.getInternalAnswerDraftRef());
        values.put(DeepResearchStateKeys.PROJECTION_REF, state.getProjectionRef());
        values.put(DeepResearchStateKeys.CITATION_CHECK_REPORT_REF, state.getCitationCheckReportRef());
        values.put(DeepResearchStateKeys.ANSWER_AUDIT_REF, state.getAnswerAuditRef());
        values.put(DeepResearchStateKeys.LLM_CALL_BUDGET_REMAINING, Integer.valueOf(state.getLlmCallBudgetRemaining()));
        values.put(DeepResearchStateKeys.TIMED_OUT, Boolean.valueOf(state.isTimedOut()));
        values.put(DeepResearchStateKeys.PARTIAL_ANSWER, Boolean.valueOf(state.isPartialAnswer()));
        values.put(DeepResearchStateKeys.HAS_CONFLICTS, Boolean.valueOf(state.isHasConflicts()));
        values.put(DeepResearchStateKeys.EVIDENCE_CARD_COUNT, Integer.valueOf(state.getEvidenceCardCount()));
        values.put(DeepResearchStateKeys.PROJECTION_RETRY_COUNT, Integer.valueOf(state.getProjectionRetryCount()));
        return values;
    }

    /**
     * 把强类型状态转换为增量状态 Map。
     *
     * @param state 强类型状态
     * @return 增量状态 Map
     */
    public Map<String, Object> toDeltaMap(DeepResearchState state) {
        return toMap(state);
    }

    private String readString(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int readInt(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Integer.parseInt((String) value);
        }
        return 0;
    }

    private boolean readBoolean(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (value instanceof List<?>) {
            return new ArrayList<String>((List<String>) value);
        }
        return new ArrayList<String>();
    }
}
