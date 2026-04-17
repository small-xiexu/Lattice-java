package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.service.IncrementalCompilePlanResult;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 增量规划节点
 *
 * 职责：对增量编译场景生成增强与新建的概念计划
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class PlanChangesNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建增量规划节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public PlanChangesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 计算增量变化计划。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        IncrementalCompilePlanResult planResult = sourceIngestSupport.planIncrementalGraphChanges(
                workingSetStore().loadMergedConcepts(state.getMergedConceptsRef())
        );
        state.setEnhancementConceptsRef(workingSetStore().saveEnhancementConcepts(
                state.getJobId(),
                planResult.getEnhancementConcepts()
        ));
        state.setConceptsToCreateRef(workingSetStore().saveConceptsToCreate(
                state.getJobId(),
                planResult.getConceptsToCreate()
        ));
        state.setHasEnhancements(!planResult.getEnhancementConcepts().isEmpty());
        state.setHasCreates(!planResult.getConceptsToCreate().isEmpty());
        state.setNothingToDo(planResult.isNothingToDo());
        return delta(state);
    }
}
