package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 概念归并节点
 *
 * 职责：将分析结果归并为最终待编译概念，并暂存到 WAL
 *
 * @author xiexu
 */
@Component
public class MergeConceptsNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建概念归并节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public MergeConceptsNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 执行概念归并与 WAL 暂存。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<MergedConcept> mergedConcepts = sourceIngestSupport.mergeConcepts(
                workingSetStore().loadAnalyzedConcepts(state.getAnalyzedConceptsRef())
        );
        sourceIngestSupport.stageWal(state.getJobId(), mergedConcepts);
        state.setMergedConceptsRef(workingSetStore().saveMergedConcepts(state.getJobId(), mergedConcepts));
        state.setConceptCount(mergedConcepts.size());
        return delta(state);
    }
}
