package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.xbk.lattice.observability.StructuredEventLogger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 问答图生命周期监听器
 *
 * 职责：为 Query Graph 输出节点级结构化执行事件
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class QueryGraphLifecycleListener implements GraphLifecycleListener {

    private static final String MDC_TRACE_ID = "traceId";

    private static final String MDC_SPAN_ID = "spanId";

    private static final String MDC_ROOT_TRACE_ID = "rootTraceId";

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final StructuredEventLogger structuredEventLogger;

    private final ThreadLocal<Map<String, String>> previousTraceContext = new ThreadLocal<Map<String, String>>();

    /**
     * 创建问答图生命周期监听器。
     *
     * @param queryGraphStateMapper 问答图状态映射器
     * @param structuredEventLogger 结构化事件日志器
     */
    @Autowired
    public QueryGraphLifecycleListener(
            QueryGraphStateMapper queryGraphStateMapper,
            StructuredEventLogger structuredEventLogger
    ) {
        this.queryGraphStateMapper = queryGraphStateMapper;
        this.structuredEventLogger = structuredEventLogger;
    }

    public QueryGraphLifecycleListener(QueryGraphStateMapper queryGraphStateMapper) {
        this(queryGraphStateMapper, null);
    }

    /**
     * 记录节点执行前事件。
     *
     * @param nodeId 节点标识
     * @param state 当前状态
     * @param config 运行配置
     * @param curTime 当前时间
     */
    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(state);
        bindTraceContext(queryGraphState);
        logStructuredStepEvent("query_graph_step_started", nodeId, queryGraphState, "STARTED", null);
    }

    /**
     * 记录节点执行后事件。
     *
     * @param nodeId 节点标识
     * @param state 当前状态
     * @param config 运行配置
     * @param curTime 当前时间
     */
    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(state);
        bindTraceContextIfNecessary(queryGraphState);
        try {
            logStructuredStepEvent("query_graph_step_completed", nodeId, queryGraphState, "SUCCEEDED", null);
        }
        finally {
            restoreTraceContext();
        }
    }

    /**
     * 记录节点异常事件。
     *
     * @param nodeId 节点标识
     * @param state 当前状态
     * @param ex 异常
     * @param config 运行配置
     */
    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(state);
        bindTraceContextIfNecessary(queryGraphState);
        try {
            logStructuredStepEvent("query_graph_step_failed", nodeId, queryGraphState, "FAILED", ex);
        }
        finally {
            restoreTraceContext();
        }
    }

    private void bindTraceContext(QueryGraphState queryGraphState) {
        Map<String, String> previousValues = new LinkedHashMap<String, String>();
        capturePreviousValue(previousValues, MDC_TRACE_ID);
        capturePreviousValue(previousValues, MDC_SPAN_ID);
        capturePreviousValue(previousValues, MDC_ROOT_TRACE_ID);
        previousTraceContext.set(previousValues);
        putIfPresent(MDC_TRACE_ID, queryGraphState.getTraceId());
        putIfPresent(MDC_SPAN_ID, queryGraphState.getSpanId());
        putIfPresent(MDC_ROOT_TRACE_ID, queryGraphState.getRootTraceId());
    }

    private void bindTraceContextIfNecessary(QueryGraphState queryGraphState) {
        if (previousTraceContext.get() != null) {
            return;
        }
        bindTraceContext(queryGraphState);
    }

    private void capturePreviousValue(Map<String, String> previousValues, String key) {
        previousValues.put(key, MDC.get(key));
    }

    private void putIfPresent(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

    private void restoreTraceContext() {
        Map<String, String> previousValues = previousTraceContext.get();
        previousTraceContext.remove();
        if (previousValues == null || previousValues.isEmpty()) {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
            MDC.remove(MDC_ROOT_TRACE_ID);
            return;
        }
        restoreValue(MDC_TRACE_ID, previousValues.get(MDC_TRACE_ID));
        restoreValue(MDC_SPAN_ID, previousValues.get(MDC_SPAN_ID));
        restoreValue(MDC_ROOT_TRACE_ID, previousValues.get(MDC_ROOT_TRACE_ID));
    }

    private void restoreValue(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

    private void logStructuredStepEvent(
            String eventName,
            String nodeId,
            QueryGraphState queryGraphState,
            String status,
            Throwable throwable
    ) {
        if (structuredEventLogger == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("scene", "query");
        fields.put("queryId", queryGraphState.getQueryId());
        fields.put("nodeId", nodeId);
        fields.put("status", status);
        fields.put("reviewStatus", queryGraphState.getReviewStatus());
        fields.put("rewriteAttemptCount", queryGraphState.getRewriteAttemptCount());
        fields.put("questionLength", resolveQuestionLength(queryGraphState));
        fields.put("answerOutcome", queryGraphState.getAnswerOutcome());
        fields.put("generationMode", queryGraphState.getGenerationMode());
        fields.put("modelExecutionStatus", queryGraphState.getModelExecutionStatus());
        fields.put("fallbackReason", queryGraphState.getFallbackReason());
        if (throwable != null) {
            fields.put("error", throwable.getMessage());
            structuredEventLogger.error(eventName, fields, throwable);
            return;
        }
        structuredEventLogger.info(eventName, fields);
    }

    private int resolveQuestionLength(QueryGraphState queryGraphState) {
        String question = queryGraphState.getNormalizedQuestion();
        if (question == null || question.isBlank()) {
            question = queryGraphState.getQuestion();
        }
        return question == null ? 0 : question.length();
    }
}
