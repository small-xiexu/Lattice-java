package com.xbk.lattice.compiler.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphDefinitionFactory;
import com.xbk.lattice.compiler.graph.CompileGraphLifecycleListener;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * StateGraph 编排器
 *
 * 职责：基于 Spring AI Alibaba StateGraph 执行多节点 full / incremental 编译图
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class StateGraphCompileOrchestrator implements CompileOrchestrator {

    private final CompileGraphDefinitionFactory compileGraphDefinitionFactory;

    private final CompileGraphStateMapper compileGraphStateMapper;

    private final CompileGraphLifecycleListener compileGraphLifecycleListener;

    /**
     * 创建 StateGraph 编排器。
     *
     * @param compileGraphDefinitionFactory 编译图定义工厂
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileGraphLifecycleListener 编译图生命周期监听器
     */
    public StateGraphCompileOrchestrator(
            CompileGraphDefinitionFactory compileGraphDefinitionFactory,
            CompileGraphStateMapper compileGraphStateMapper,
            CompileGraphLifecycleListener compileGraphLifecycleListener
    ) {
        this.compileGraphDefinitionFactory = compileGraphDefinitionFactory;
        this.compileGraphStateMapper = compileGraphStateMapper;
        this.compileGraphLifecycleListener = compileGraphLifecycleListener;
    }

    /**
     * 返回当前编排器模式标识。
     *
     * @return 模式标识
     */
    @Override
    public String getMode() {
        return CompileOrchestrationModes.STATE_GRAPH;
    }

    /**
     * 执行编译。
     *
     * @param executionRequest 执行请求
     * @return 编译结果
     * @throws IOException IO 异常
     */
    @Override
    public CompileResult execute(CompileExecutionRequest executionRequest) throws IOException {
        try {
            CompiledGraph compiledGraph = compileGraphDefinitionFactory.build().compile(
                    CompileConfig.builder()
                            .withLifecycleListener(compileGraphLifecycleListener)
                            .build()
            );
            CompileGraphState initialState = new CompileGraphState();
            initialState.setJobId(executionRequest.getJobId());
            initialState.setSourceDir(executionRequest.getSourceDir().toString());
            initialState.setCompileMode(executionRequest.isIncremental() ? "incremental" : "full");
            initialState.setSourceId(executionRequest.getSourceId());
            initialState.setSourceCode(executionRequest.getSourceCode());
            initialState.setSourceSyncRunId(executionRequest.getSourceSyncRunId());
            initialState.setTraceId(resolveCurrentMdcValue("traceId"));
            initialState.setSpanId(resolveCurrentMdcValue("spanId"));
            initialState.setRootTraceId(resolveRootTraceId(initialState.getTraceId()));

            Optional<OverAllState> result = compiledGraph.invoke(compileGraphStateMapper.toMap(initialState));
            OverAllState overAllState = result.orElseThrow(() -> new IllegalStateException("state graph compile returned empty state"));
            CompileGraphState finalState = compileGraphStateMapper.fromMap(overAllState.data());
            return new CompileResult(finalState.getPersistedCount(), finalState.getJobId());
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IOException("state graph compile failed", ex);
        }
    }

    /**
     * 兼容旧测试入口执行编译。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @return 编译结果
     * @throws IOException IO 异常
     */
    public CompileResult execute(Path sourceDir, boolean incremental) throws IOException {
        return execute(new CompileExecutionRequest(
                UUID.randomUUID().toString(),
                sourceDir,
                incremental,
                CompileOrchestrationModes.STATE_GRAPH,
                null,
                null,
                null
        ));
    }

    private String resolveRootTraceId(String traceId) {
        String rootTraceId = resolveCurrentMdcValue("rootTraceId");
        if (rootTraceId != null && !rootTraceId.isBlank()) {
            return rootTraceId;
        }
        return traceId;
    }

    private String resolveCurrentMdcValue(String key) {
        String value = MDC.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
