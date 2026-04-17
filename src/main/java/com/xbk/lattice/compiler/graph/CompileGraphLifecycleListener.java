package com.xbk.lattice.compiler.graph;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.xbk.lattice.compiler.config.CompileGraphProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 编译图生命周期监听器
 *
 * 职责：把 Graph 生命周期事件桥接到步骤日志记录器
 *
 * @author xiexu
 */
@Component
@Slf4j
@Profile("jdbc")
public class CompileGraphLifecycleListener implements GraphLifecycleListener {

    private final CompileGraphStateMapper compileGraphStateMapper;

    private final GraphStepLogger graphStepLogger;

    private final CompileGraphProperties compileGraphProperties;

    private final Map<String, ConcurrentLinkedDeque<StepExecutionHandle>> inflightHandleMap =
            new ConcurrentHashMap<String, ConcurrentLinkedDeque<StepExecutionHandle>>();

    /**
     * 创建编译图生命周期监听器。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param graphStepLogger Graph 步骤日志器
     * @param compileGraphProperties 编译图配置
     */
    public CompileGraphLifecycleListener(
            CompileGraphStateMapper compileGraphStateMapper,
            GraphStepLogger graphStepLogger,
            CompileGraphProperties compileGraphProperties
    ) {
        this.compileGraphStateMapper = compileGraphStateMapper;
        this.graphStepLogger = graphStepLogger;
        this.compileGraphProperties = compileGraphProperties;
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
        if (!compileGraphProperties.isPersistStepLog()) {
            return;
        }
        CompileGraphState graphState = compileGraphStateMapper.fromMap(state);
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

    @FunctionalInterface
    private interface StepLogSupplier<T> {

        T get();
    }
}
