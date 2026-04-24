package com.xbk.lattice.compiler.graph;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.xbk.lattice.compiler.config.CompileGraphProperties;
import com.xbk.lattice.compiler.service.CompileJobLeaseManager;
import com.xbk.lattice.observability.StructuredEventLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 编译图生命周期监听器
 *
 * 职责：把 Graph 生命周期事件桥接到步骤日志记录器与结构化事件日志
 *
 * @author xiexu
 */
@Component
@Slf4j
@Profile("jdbc")
public class CompileGraphLifecycleListener implements GraphLifecycleListener {

    private static final String MDC_TRACE_ID = "traceId";

    private static final String MDC_SPAN_ID = "spanId";

    private static final String MDC_ROOT_TRACE_ID = "rootTraceId";

    private final CompileGraphStateMapper compileGraphStateMapper;

    private final GraphStepLogger graphStepLogger;

    private final CompileGraphProperties compileGraphProperties;

    private final StructuredEventLogger structuredEventLogger;

    private final CompileJobLeaseManager compileJobLeaseManager;

    private final Map<String, ConcurrentLinkedDeque<StepExecutionHandle>> inflightHandleMap =
            new ConcurrentHashMap<String, ConcurrentLinkedDeque<StepExecutionHandle>>();

    private final ThreadLocal<Map<String, String>> previousTraceContext = new ThreadLocal<Map<String, String>>();

    /**
     * 创建编译图生命周期监听器。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param graphStepLogger Graph 步骤日志器
     * @param compileGraphProperties 编译图配置
     * @param structuredEventLogger 结构化事件日志器
     * @param compileJobLeaseManager 编译作业租约管理器
     */
    @Autowired
    public CompileGraphLifecycleListener(
            CompileGraphStateMapper compileGraphStateMapper,
            GraphStepLogger graphStepLogger,
            CompileGraphProperties compileGraphProperties,
            StructuredEventLogger structuredEventLogger,
            CompileJobLeaseManager compileJobLeaseManager
    ) {
        this.compileGraphStateMapper = compileGraphStateMapper;
        this.graphStepLogger = graphStepLogger;
        this.compileGraphProperties = compileGraphProperties;
        this.structuredEventLogger = structuredEventLogger;
        this.compileJobLeaseManager = compileJobLeaseManager;
    }

    CompileGraphLifecycleListener(
            CompileGraphStateMapper compileGraphStateMapper,
            GraphStepLogger graphStepLogger,
            CompileGraphProperties compileGraphProperties
    ) {
        this(compileGraphStateMapper, graphStepLogger, compileGraphProperties, null, null);
    }

    /**
     * 记录节点执行前事件。
     *
     * @param nodeId 节点标识
     * @param state 状态 Map
     * @param config 运行配置
     * @param curTime 当前时间
     */
    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        CompileGraphState graphState = compileGraphStateMapper.fromMap(state);
        bindTraceContext(graphState);
        touchCurrentStep(graphState.getJobId(), nodeId);
        logStructuredStepEvent("compile_graph_step_started", nodeId, graphState, "STARTED", null);
        if (!compileGraphProperties.isPersistStepLog()) {
            return;
        }
        StepExecutionHandle handle = executeWithFailureMode(
                () -> graphStepLogger.beforeStep(nodeId, graphState, curTime),
                nodeId,
                graphState.getJobId()
        );
        if (handle != null) {
            registerHandle(resolveNodeExecutionKey(state, config, graphState.getJobId(), nodeId), handle);
        }
    }

    /**
     * 记录节点执行后事件。
     *
     * @param nodeId 节点标识
     * @param state 状态 Map
     * @param config 运行配置
     * @param curTime 当前时间
     */
    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        CompileGraphState graphState = compileGraphStateMapper.fromMap(state);
        bindTraceContextIfNecessary(graphState);
        try {
            logStructuredStepEvent("compile_graph_step_completed", nodeId, graphState, "SUCCEEDED", null);
            if (!compileGraphProperties.isPersistStepLog()) {
                if ("finalize_job".equals(nodeId)) {
                    graphStepLogger.clearJob(graphState.getJobId());
                }
                return;
            }
            String executionKey = resolveNodeExecutionKey(state, config, graphState.getJobId(), nodeId);
            StepExecutionHandle handle = pollHandle(executionKey);
            executeWithFailureMode(
                    () -> {
                        graphStepLogger.afterStep(handle, nodeId, graphState, curTime);
                        return null;
                    },
                    nodeId,
                    graphState.getJobId()
            );
            if ("finalize_job".equals(nodeId)) {
                graphStepLogger.clearJob(graphState.getJobId());
                clearExecution(resolveExecutionPrefix(state, config, graphState.getJobId()));
            }
        }
        finally {
            restoreTraceContext();
        }
    }

    /**
     * 记录节点异常事件。
     *
     * @param nodeId 节点标识
     * @param state 状态 Map
     * @param ex 异常
     * @param config 运行配置
     */
    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
        CompileGraphState graphState = compileGraphStateMapper.fromMap(state);
        bindTraceContextIfNecessary(graphState);
        try {
            logStructuredStepEvent("compile_graph_step_failed", nodeId, graphState, "FAILED", ex);
            if (compileGraphProperties.isPersistStepLog()) {
                StepExecutionHandle handle = pollHandle(resolveNodeExecutionKey(state, config, graphState.getJobId(), nodeId));
                executeWithFailureMode(
                        () -> {
                            graphStepLogger.failStep(handle, nodeId, graphState, ex);
                            return null;
                        },
                        nodeId,
                        graphState.getJobId()
                );
            }
            if (graphState.getJobId() != null) {
                graphStepLogger.clearJob(graphState.getJobId());
            }
            clearExecution(resolveExecutionPrefix(state, config, graphState.getJobId()));
        }
        finally {
            restoreTraceContext();
        }
    }

    private void registerHandle(String executionKey, StepExecutionHandle handle) {
        if (executionKey == null || handle == null) {
            return;
        }
        inflightHandleMap.computeIfAbsent(
                executionKey,
                key -> new ConcurrentLinkedDeque<StepExecutionHandle>()
        ).addLast(handle);
    }

    private StepExecutionHandle pollHandle(String executionKey) {
        if (executionKey == null) {
            return null;
        }
        ConcurrentLinkedDeque<StepExecutionHandle> handles = inflightHandleMap.get(executionKey);
        if (handles == null) {
            return null;
        }
        return handles.pollFirst();
    }

    private void clearExecution(String executionPrefix) {
        if (executionPrefix == null) {
            return;
        }
        for (String executionKey : inflightHandleMap.keySet()) {
            if (executionKey.startsWith(executionPrefix + ":")) {
                inflightHandleMap.remove(executionKey);
            }
        }
    }

    private String resolveNodeExecutionKey(
            Map<String, Object> state,
            RunnableConfig config,
            String jobId,
            String nodeId
    ) {
        String executionPrefix = resolveExecutionPrefix(state, config, jobId);
        if (executionPrefix == null || nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return executionPrefix + ":" + nodeId;
    }

    private String resolveExecutionPrefix(Map<String, Object> state, RunnableConfig config, String jobId) {
        Object executionId = state == null ? null : state.get(GraphLifecycleListener.EXECUTION_ID_KEY);
        if (executionId instanceof String) {
            String stringValue = (String) executionId;
            if (!stringValue.isBlank()) {
                return stringValue;
            }
        }
        if (config != null && config.metadata(GraphLifecycleListener.EXECUTION_ID_KEY).isPresent()) {
            Object metadataExecutionId = config.metadata(GraphLifecycleListener.EXECUTION_ID_KEY).orElse(null);
            if (metadataExecutionId instanceof String) {
                String stringValue = (String) metadataExecutionId;
                if (!stringValue.isBlank()) {
                    return stringValue;
                }
            }
        }
        if (jobId != null && !jobId.isBlank()) {
            return "job:" + jobId;
        }
        return null;
    }

    private <T> T executeWithFailureMode(StepLogSupplier<T> supplier, String nodeId, String jobId) {
        try {
            return supplier.get();
        }
        catch (RuntimeException ex) {
            if (isFailMode()) {
                throw ex;
            }
            log.warn("compile graph step log failed nodeId: {}, jobId: {}, mode: warn", nodeId, jobId, ex);
            return null;
        }
    }

    private boolean isFailMode() {
        String failureMode = compileGraphProperties.getStepLogFailureMode();
        return "fail".equalsIgnoreCase(failureMode);
    }

    /**
     * 刷新当前步骤心跳。
     *
     * @param jobId 作业标识
     * @param nodeId 节点标识
     */
    private void touchCurrentStep(String jobId, String nodeId) {
        if (compileJobLeaseManager == null || jobId == null || jobId.isBlank()) {
            return;
        }
        compileJobLeaseManager.touchCurrentStep(jobId, nodeId, "正在执行节点：" + nodeId);
    }

    private void logStructuredStepEvent(
            String eventName,
            String nodeId,
            CompileGraphState graphState,
            String status,
            Throwable throwable
    ) {
        if (structuredEventLogger == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("scene", "compile");
        fields.put("compileJobId", graphState.getJobId());
        fields.put("nodeId", nodeId);
        fields.put("compileMode", graphState.getCompileMode());
        fields.put("sourceId", graphState.getSourceId());
        fields.put("sourceSyncRunId", graphState.getSourceSyncRunId());
        fields.put("status", status);
        fields.put("pendingReviewCount", graphState.getPendingReviewCount());
        fields.put("acceptedCount", graphState.getAcceptedCount());
        fields.put("needsHumanReviewCount", graphState.getNeedsHumanReviewCount());
        fields.put("persistedCount", graphState.getPersistedCount());
        fields.put("fixAttemptCount", graphState.getFixAttemptCount());
        fields.put("nothingToDo", graphState.isNothingToDo());
        if (throwable != null) {
            fields.put("error", throwable.getMessage());
            structuredEventLogger.error(eventName, fields, throwable);
            return;
        }
        structuredEventLogger.info(eventName, fields);
    }

    private void bindTraceContextIfNecessary(CompileGraphState graphState) {
        if (previousTraceContext.get() != null) {
            return;
        }
        bindTraceContext(graphState);
    }

    private void bindTraceContext(CompileGraphState graphState) {
        Map<String, String> previousValues = new LinkedHashMap<String, String>();
        capturePreviousValue(previousValues, MDC_TRACE_ID);
        capturePreviousValue(previousValues, MDC_SPAN_ID);
        capturePreviousValue(previousValues, MDC_ROOT_TRACE_ID);
        previousTraceContext.set(previousValues);
        putIfPresent(MDC_TRACE_ID, graphState.getTraceId());
        putIfPresent(MDC_SPAN_ID, graphState.getSpanId());
        putIfPresent(MDC_ROOT_TRACE_ID, graphState.getRootTraceId());
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

    @FunctionalInterface
    private interface StepLogSupplier<T> {

        T get();
    }
}
