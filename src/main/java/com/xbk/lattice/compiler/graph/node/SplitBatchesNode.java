package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 批次切分节点
 *
 * 职责：把分组后的源文件集合切分为可分析批次
 *
 * @author xiexu
 */
@Component
public class SplitBatchesNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建批次切分节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public SplitBatchesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 执行批次切分。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        Map<String, List<SourceBatch>> sourceBatches = sourceIngestSupport.splitBatches(
                workingSetStore().loadGroupedSources(state.getGroupedSourcesRef())
        );
        state.setSourceBatchesRef(workingSetStore().saveSourceBatches(state.getJobId(), sourceBatches));
        return delta(state);
    }
}
