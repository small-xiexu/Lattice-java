package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 摄入源文件节点
 *
 * 职责：扫描输入目录并把原始源文件集合写入工作集
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class IngestSourcesNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建摄入源文件节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public IngestSourcesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 摄入源目录内容。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     * @throws Exception 执行异常
     */
    public Map<String, Object> execute(OverAllState overAllState) throws Exception {
        CompileGraphState state = state(overAllState);
        List<RawSource> rawSources = sourceIngestSupport.ingest(
                Path.of(state.getSourceDir()),
                state.getSourceId()
        );
        if ("incremental".equalsIgnoreCase(state.getCompileMode())) {
            rawSources = sourceIngestSupport.filterChangedRawSources(rawSources);
        }
        state.setRawSourcesRef(workingSetStore().saveRawSources(state.getJobId(), rawSources));
        state.setConceptCount(rawSources.size());
        return delta(state);
    }
}
