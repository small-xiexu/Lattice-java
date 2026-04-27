package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.config.CompileGraphProperties;
import com.xbk.lattice.compiler.config.CompileReviewProperties;
import com.xbk.lattice.compiler.graph.CompileGraphConditions;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.service.ArticleCompileSupport;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 初始化作业节点
 *
 * 职责：固化编译图运行所需的配置快照与角色路由信息
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
@Slf4j
public class InitializeJobNode extends AbstractCompileGraphNode {

    private final CompileGraphConditions compileGraphConditions;

    private final CompileReviewProperties compileReviewProperties;

    private final CompileGraphProperties compileGraphProperties;

    private final ArticleCompileSupport articleCompileSupport;

    private final ExecutionLlmSnapshotService executionLlmSnapshotService;

    /**
     * 创建初始化作业节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileGraphConditions 编译图条件路由
     * @param compileReviewProperties 编译审查配置
     * @param compileGraphProperties 编译图配置
     * @param articleCompileSupport 文章编译支撑服务
     * @param executionLlmSnapshotService 运行时快照服务
     */
    public InitializeJobNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileGraphConditions compileGraphConditions,
            CompileReviewProperties compileReviewProperties,
            CompileGraphProperties compileGraphProperties,
            ArticleCompileSupport articleCompileSupport,
            ExecutionLlmSnapshotService executionLlmSnapshotService
    ) {
        super(compileGraphStateMapper);
        this.compileGraphConditions = compileGraphConditions;
        this.compileReviewProperties = compileReviewProperties;
        this.compileGraphProperties = compileGraphProperties;
        this.articleCompileSupport = articleCompileSupport;
        this.executionLlmSnapshotService = executionLlmSnapshotService;
    }

    /**
     * 初始化编译作业状态。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        state.setOrchestrationMode(compileGraphConditions.defaultMode());
        state.setAutoFixEnabled(compileReviewProperties.isAutoFixEnabled());
        state.setAllowPersistNeedsHumanReview(compileReviewProperties.isAllowPersistNeedsHumanReview());
        state.setHumanReviewSeverityThreshold(compileReviewProperties.getHumanReviewSeverityThreshold());
        state.setMaxFixRounds(compileReviewProperties.getMaxFixRounds());
        freezeSnapshotsFailOpen(state);
        state.setCompileRoute(articleCompileSupport.currentCompileRoute(
                state.getJobId(),
                ExecutionLlmSnapshotService.COMPILE_SCENE
        ));
        state.setReviewRoute(articleCompileSupport.currentReviewRoute(
                state.getJobId(),
                ExecutionLlmSnapshotService.COMPILE_SCENE
        ));
        state.setFixRoute(articleCompileSupport.currentFixRoute(
                state.getJobId(),
                ExecutionLlmSnapshotService.COMPILE_SCENE
        ));
        state.setStepLogFailureMode(compileGraphProperties.getStepLogFailureMode());
        state.setSynthesisRequired(true);
        state.setSnapshotRequired(true);
        return delta(state);
    }

    private void freezeSnapshotsFailOpen(CompileGraphState state) {
        try {
            if (executionLlmSnapshotService == null) {
                return;
            }
            if (state.getJobId() == null || state.getJobId().isBlank()) {
                return;
            }
            if (!executionLlmSnapshotService.freezeSnapshots(
                    ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE,
                    state.getJobId(),
                    ExecutionLlmSnapshotService.COMPILE_SCENE
            ).isEmpty()) {
                state.setLlmBindingSnapshotRef(
                        ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE
                                + ":"
                                + state.getJobId()
                                + ":"
                                + ExecutionLlmSnapshotService.COMPILE_SCENE
                );
            }
        }
        catch (RuntimeException exception) {
            log.warn("Freeze llm snapshots failed for compile job {}, continue with bootstrap fallback",
                    state.getJobId(), exception);
        }
    }
}
