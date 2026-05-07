package com.xbk.lattice.compiler.graph;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编译图状态映射器
 *
 * 职责：在 CompileGraphState 与 Graph Map 状态之间做统一转换
 *
 * @author xiexu
 */
@Component
public class CompileGraphStateMapper {

    /**
     * 从 Map 构建强类型状态。
     *
     * @param stateMap 原始状态 Map
     * @return 强类型状态
     */
    public CompileGraphState fromMap(Map<String, Object> stateMap) {
        CompileGraphState state = new CompileGraphState();
        state.setJobId(readString(stateMap, CompileGraphStateKeys.JOB_ID));
        state.setSourceDir(readString(stateMap, CompileGraphStateKeys.SOURCE_DIR));
        state.setSourceId(readLong(stateMap, CompileGraphStateKeys.SOURCE_ID));
        state.setSourceCode(readString(stateMap, CompileGraphStateKeys.SOURCE_CODE));
        state.setSourceSyncRunId(readLong(stateMap, CompileGraphStateKeys.SOURCE_SYNC_RUN_ID));
        state.setTraceId(readString(stateMap, CompileGraphStateKeys.TRACE_ID));
        state.setSpanId(readString(stateMap, CompileGraphStateKeys.SPAN_ID));
        state.setRootTraceId(readString(stateMap, CompileGraphStateKeys.ROOT_TRACE_ID));
        state.setCompileMode(readString(stateMap, CompileGraphStateKeys.COMPILE_MODE));
        state.setOrchestrationMode(readString(stateMap, CompileGraphStateKeys.ORCHESTRATION_MODE));
        state.setRawSourcesRef(readString(stateMap, CompileGraphStateKeys.RAW_SOURCES_REF));
        state.setGroupedSourcesRef(readString(stateMap, CompileGraphStateKeys.GROUPED_SOURCES_REF));
        state.setSourceBatchesRef(readString(stateMap, CompileGraphStateKeys.SOURCE_BATCHES_REF));
        state.setAnalyzedConceptsRef(readString(stateMap, CompileGraphStateKeys.ANALYZED_CONCEPTS_REF));
        state.setMergedConceptsRef(readString(stateMap, CompileGraphStateKeys.MERGED_CONCEPTS_REF));
        state.setEnhancementConceptsRef(readString(stateMap, CompileGraphStateKeys.ENHANCEMENT_CONCEPTS_REF));
        state.setConceptsToCreateRef(readString(stateMap, CompileGraphStateKeys.CONCEPTS_TO_CREATE_REF));
        state.setDraftArticlesRef(readString(stateMap, CompileGraphStateKeys.DRAFT_ARTICLES_REF));
        state.setReviewedArticlesRef(readString(stateMap, CompileGraphStateKeys.REVIEWED_ARTICLES_REF));
        state.setReviewPartitionRef(readString(stateMap, CompileGraphStateKeys.REVIEW_PARTITION_REF));
        state.setAcceptedArticlesRef(readString(stateMap, CompileGraphStateKeys.ACCEPTED_ARTICLES_REF));
        state.setNeedsHumanReviewArticlesRef(readString(stateMap, CompileGraphStateKeys.NEEDS_HUMAN_REVIEW_ARTICLES_REF));
        state.setSourceFileIdsByPath(readLongMap(stateMap, CompileGraphStateKeys.SOURCE_FILE_IDS_BY_PATH));
        state.setPersistedArticleIds(readStringList(stateMap, CompileGraphStateKeys.PERSISTED_ARTICLE_IDS));
        state.setConceptCount(readInt(stateMap, CompileGraphStateKeys.CONCEPT_COUNT));
        state.setPendingReviewCount(readInt(stateMap, CompileGraphStateKeys.PENDING_REVIEW_COUNT));
        state.setAcceptedCount(readInt(stateMap, CompileGraphStateKeys.ACCEPTED_COUNT));
        state.setNeedsHumanReviewCount(readInt(stateMap, CompileGraphStateKeys.NEEDS_HUMAN_REVIEW_COUNT));
        state.setPersistedCount(readInt(stateMap, CompileGraphStateKeys.PERSISTED_COUNT));
        state.setHasEnhancements(readBoolean(stateMap, CompileGraphStateKeys.HAS_ENHANCEMENTS));
        state.setHasCreates(readBoolean(stateMap, CompileGraphStateKeys.HAS_CREATES));
        state.setNothingToDo(readBoolean(stateMap, CompileGraphStateKeys.NOTHING_TO_DO));
        state.setAutoFixEnabled(readBoolean(stateMap, CompileGraphStateKeys.AUTO_FIX_ENABLED));
        state.setAllowPersistNeedsHumanReview(readBoolean(stateMap, CompileGraphStateKeys.ALLOW_PERSIST_NEEDS_HUMAN_REVIEW));
        state.setHumanReviewSeverityThreshold(readString(
                stateMap,
                CompileGraphStateKeys.HUMAN_REVIEW_SEVERITY_THRESHOLD
        ));
        state.setCompileRoute(readString(stateMap, CompileGraphStateKeys.COMPILE_ROUTE));
        state.setReviewRoute(readString(stateMap, CompileGraphStateKeys.REVIEW_ROUTE));
        state.setFixRoute(readString(stateMap, CompileGraphStateKeys.FIX_ROUTE));
        state.setLlmBindingSnapshotRef(readString(stateMap, CompileGraphStateKeys.LLM_BINDING_SNAPSHOT_REF));
        state.setAstExtractReportRef(readString(stateMap, CompileGraphStateKeys.AST_EXTRACT_REPORT_REF));
        state.setFixAttemptCount(readInt(stateMap, CompileGraphStateKeys.FIX_ATTEMPT_COUNT));
        state.setMaxFixRounds(readInt(stateMap, CompileGraphStateKeys.MAX_FIX_ROUNDS));
        state.setSynthesisRequired(readBoolean(stateMap, CompileGraphStateKeys.SYNTHESIS_REQUIRED));
        state.setSnapshotRequired(readBoolean(stateMap, CompileGraphStateKeys.SNAPSHOT_REQUIRED));
        state.setStepLogFailureMode(readString(stateMap, CompileGraphStateKeys.STEP_LOG_FAILURE_MODE));
        state.setGraphEntityUpsertCount(readInt(stateMap, CompileGraphStateKeys.GRAPH_ENTITY_UPSERT_COUNT));
        state.setGraphFactUpsertCount(readInt(stateMap, CompileGraphStateKeys.GRAPH_FACT_UPSERT_COUNT));
        state.setGraphRelationUpsertCount(readInt(stateMap, CompileGraphStateKeys.GRAPH_RELATION_UPSERT_COUNT));
        state.setStepSummaries(readStringList(stateMap, CompileGraphStateKeys.STEP_SUMMARIES));
        state.setErrors(readStringList(stateMap, CompileGraphStateKeys.ERRORS));
        return state;
    }

    /**
     * 转换为完整状态 Map。
     *
     * @param state 强类型状态
     * @return 完整状态 Map
     */
    public Map<String, Object> toMap(CompileGraphState state) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(CompileGraphStateKeys.JOB_ID, state.getJobId());
        values.put(CompileGraphStateKeys.SOURCE_DIR, state.getSourceDir());
        values.put(CompileGraphStateKeys.SOURCE_ID, state.getSourceId());
        values.put(CompileGraphStateKeys.SOURCE_CODE, state.getSourceCode());
        values.put(CompileGraphStateKeys.SOURCE_SYNC_RUN_ID, state.getSourceSyncRunId());
        values.put(CompileGraphStateKeys.TRACE_ID, state.getTraceId());
        values.put(CompileGraphStateKeys.SPAN_ID, state.getSpanId());
        values.put(CompileGraphStateKeys.ROOT_TRACE_ID, state.getRootTraceId());
        values.put(CompileGraphStateKeys.COMPILE_MODE, state.getCompileMode());
        values.put(CompileGraphStateKeys.ORCHESTRATION_MODE, state.getOrchestrationMode());
        values.put(CompileGraphStateKeys.RAW_SOURCES_REF, state.getRawSourcesRef());
        values.put(CompileGraphStateKeys.GROUPED_SOURCES_REF, state.getGroupedSourcesRef());
        values.put(CompileGraphStateKeys.SOURCE_BATCHES_REF, state.getSourceBatchesRef());
        values.put(CompileGraphStateKeys.ANALYZED_CONCEPTS_REF, state.getAnalyzedConceptsRef());
        values.put(CompileGraphStateKeys.MERGED_CONCEPTS_REF, state.getMergedConceptsRef());
        values.put(CompileGraphStateKeys.ENHANCEMENT_CONCEPTS_REF, state.getEnhancementConceptsRef());
        values.put(CompileGraphStateKeys.CONCEPTS_TO_CREATE_REF, state.getConceptsToCreateRef());
        values.put(CompileGraphStateKeys.DRAFT_ARTICLES_REF, state.getDraftArticlesRef());
        values.put(CompileGraphStateKeys.REVIEWED_ARTICLES_REF, state.getReviewedArticlesRef());
        values.put(CompileGraphStateKeys.REVIEW_PARTITION_REF, state.getReviewPartitionRef());
        values.put(CompileGraphStateKeys.ACCEPTED_ARTICLES_REF, state.getAcceptedArticlesRef());
        values.put(CompileGraphStateKeys.NEEDS_HUMAN_REVIEW_ARTICLES_REF, state.getNeedsHumanReviewArticlesRef());
        values.put(CompileGraphStateKeys.SOURCE_FILE_IDS_BY_PATH, state.getSourceFileIdsByPath());
        values.put(CompileGraphStateKeys.PERSISTED_ARTICLE_IDS, state.getPersistedArticleIds());
        values.put(CompileGraphStateKeys.CONCEPT_COUNT, state.getConceptCount());
        values.put(CompileGraphStateKeys.PENDING_REVIEW_COUNT, state.getPendingReviewCount());
        values.put(CompileGraphStateKeys.ACCEPTED_COUNT, state.getAcceptedCount());
        values.put(CompileGraphStateKeys.NEEDS_HUMAN_REVIEW_COUNT, state.getNeedsHumanReviewCount());
        values.put(CompileGraphStateKeys.PERSISTED_COUNT, state.getPersistedCount());
        values.put(CompileGraphStateKeys.HAS_ENHANCEMENTS, state.isHasEnhancements());
        values.put(CompileGraphStateKeys.HAS_CREATES, state.isHasCreates());
        values.put(CompileGraphStateKeys.NOTHING_TO_DO, state.isNothingToDo());
        values.put(CompileGraphStateKeys.AUTO_FIX_ENABLED, state.isAutoFixEnabled());
        values.put(CompileGraphStateKeys.ALLOW_PERSIST_NEEDS_HUMAN_REVIEW, state.isAllowPersistNeedsHumanReview());
        values.put(CompileGraphStateKeys.HUMAN_REVIEW_SEVERITY_THRESHOLD, state.getHumanReviewSeverityThreshold());
        values.put(CompileGraphStateKeys.COMPILE_ROUTE, state.getCompileRoute());
        values.put(CompileGraphStateKeys.REVIEW_ROUTE, state.getReviewRoute());
        values.put(CompileGraphStateKeys.FIX_ROUTE, state.getFixRoute());
        values.put(CompileGraphStateKeys.LLM_BINDING_SNAPSHOT_REF, state.getLlmBindingSnapshotRef());
        values.put(CompileGraphStateKeys.AST_EXTRACT_REPORT_REF, state.getAstExtractReportRef());
        values.put(CompileGraphStateKeys.FIX_ATTEMPT_COUNT, state.getFixAttemptCount());
        values.put(CompileGraphStateKeys.MAX_FIX_ROUNDS, state.getMaxFixRounds());
        values.put(CompileGraphStateKeys.SYNTHESIS_REQUIRED, state.isSynthesisRequired());
        values.put(CompileGraphStateKeys.SNAPSHOT_REQUIRED, state.isSnapshotRequired());
        values.put(CompileGraphStateKeys.STEP_LOG_FAILURE_MODE, state.getStepLogFailureMode());
        values.put(CompileGraphStateKeys.GRAPH_ENTITY_UPSERT_COUNT, state.getGraphEntityUpsertCount());
        values.put(CompileGraphStateKeys.GRAPH_FACT_UPSERT_COUNT, state.getGraphFactUpsertCount());
        values.put(CompileGraphStateKeys.GRAPH_RELATION_UPSERT_COUNT, state.getGraphRelationUpsertCount());
        values.put(CompileGraphStateKeys.STEP_SUMMARIES, state.getStepSummaries());
        values.put(CompileGraphStateKeys.ERRORS, state.getErrors());
        return values;
    }

    /**
     * 转换为增量状态 Map。
     *
     * @param state 强类型状态
     * @return 增量状态 Map
     */
    public Map<String, Object> toDeltaMap(CompileGraphState state) {
        return toMap(state);
    }

    private String readString(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        return value == null ? null : String.valueOf(value);
    }

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

    private Long readLong(Map<String, Object> stateMap, String key) {
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
        return null;
    }

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

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (!(value instanceof List<?>)) {
            return new ArrayList<String>();
        }
        List<?> listValue = (List<?>) value;
        List<String> values = new ArrayList<String>();
        for (Object item : listValue) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> readLongMap(Map<String, Object> stateMap, String key) {
        Object value = stateMap.get(key);
        if (!(value instanceof Map<?, ?>)) {
            return new LinkedHashMap<String, Long>();
        }
        Map<String, Long> values = new LinkedHashMap<String, Long>();
        Map<?, ?> rawMap = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            values.put(String.valueOf(entry.getKey()), Long.valueOf(String.valueOf(entry.getValue())));
        }
        return values;
    }
}
