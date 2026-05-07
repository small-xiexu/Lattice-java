package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 批次分析节点
 *
 * 职责：把源文件批次分析为可归并的概念集合
 *
 * @author xiexu
 */
@Component
public class AnalyzeBatchesNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建批次分析节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public AnalyzeBatchesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 执行批次分析。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<AnalyzedConcept> analyzedConcepts = sourceIngestSupport.analyzeBatches(
                workingSetStore().loadSourceBatches(state.getSourceBatchesRef()),
                Path.of(state.getSourceDir())
        );
        state.setAnalyzedConceptsRef(workingSetStore().saveAnalyzedConcepts(state.getJobId(), analyzedConcepts));
        state.setConceptCount(analyzedConcepts.size());
        return delta(state);
    }
}
