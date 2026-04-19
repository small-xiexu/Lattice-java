package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 源文件分组节点
 *
 * 职责：按编译规则对原始源文件进行稳定分组
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class GroupSourcesNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建源文件分组节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public GroupSourcesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 对原始源文件进行分组。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        Map<String, List<RawSource>> groupedSources = sourceIngestSupport.groupSources(
                workingSetStore().loadRawSources(state.getRawSourcesRef())
        );
        state.setGroupedSourcesRef(workingSetStore().saveGroupedSources(state.getJobId(), groupedSources));
        return delta(state);
    }
}
