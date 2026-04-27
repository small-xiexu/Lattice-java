package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结束作业节点
 *
 * 职责：保留编译作业工作集用于恢复，并返回最终状态
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class FinalizeJobNode extends AbstractCompileGraphNode {

    /**
     * 创建结束作业节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     */
    public FinalizeJobNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
    }

    /**
     * 保留工作集并结束作业。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        return delta(state);
    }
}
