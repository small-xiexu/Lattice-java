package com.xbk.lattice.compiler.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * StateGraph 编排器
 *
 * 职责：基于 Spring AI Alibaba StateGraph 执行可切换的 full / incremental compile
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class StateGraphCompileOrchestrator implements CompileOrchestrator {

    private final CompilePipelineService compilePipelineService;

    /**
     * 创建 StateGraph 编排器。
     *
     * @param compilePipelineService 编译链路服务
     */
    public StateGraphCompileOrchestrator(CompilePipelineService compilePipelineService) {
        this.compilePipelineService = compilePipelineService;
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
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @return 编译结果
     * @throws IOException IO 异常
     */
    @Override
    public CompileResult execute(Path sourceDir, boolean incremental) throws IOException {
        try {
            StateGraph stateGraph = new StateGraph();
            stateGraph.addNode("compile", AsyncNodeAction.node_async(state -> {
                CompileResult compileResult = executeByState(state);
                return Map.of(
                        "jobId", compileResult.getJobId(),
                        "persistedCount", compileResult.getPersistedCount(),
                        "orchestrationMode", getMode()
                );
            }));
            stateGraph.addEdge(StateGraph.START, "compile");
            stateGraph.addEdge("compile", StateGraph.END);

            CompiledGraph compiledGraph = stateGraph.compile();
            Optional<OverAllState> result = compiledGraph.invoke(Map.of(
                    "sourceDir", sourceDir.toString(),
                    "incremental", incremental
            ));
            OverAllState overAllState = result.orElseThrow(() -> new IllegalStateException("state graph compile returned empty state"));
            String jobId = overAllState.value("jobId", String.class)
                    .orElseThrow(() -> new IllegalStateException("state graph compile missing jobId"));
            int persistedCount = overAllState.value("persistedCount", Integer.class).orElse(0);
            return new CompileResult(persistedCount, jobId);
        }
        catch (GraphStateException ex) {
            throw new IllegalStateException("state graph compile failed", ex);
        }
    }

    /**
     * 基于状态对象执行 full / incremental compile。
     *
     * @param state 图状态
     * @return 编译结果
     * @throws IOException IO 异常
     */
    private CompileResult executeByState(OverAllState state) throws IOException {
        String sourceDirValue = state.value("sourceDir", String.class)
                .orElseThrow(() -> new IllegalArgumentException("state graph compile missing sourceDir"));
        boolean incremental = state.value("incremental", Boolean.class).orElse(Boolean.FALSE);
        Path resolvedSourceDir = Path.of(sourceDirValue);
        if (incremental) {
            return compilePipelineService.incrementalCompile(resolvedSourceDir);
        }
        return compilePipelineService.compile(resolvedSourceDir);
    }
}
