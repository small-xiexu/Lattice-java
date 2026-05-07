package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * 捕获仓库快照节点
 *
 * 职责：在编译写入完成后记录一次整库快照
 *
 * @author xiexu
 */
@Component
public class CaptureRepoSnapshotNode extends AbstractCompileGraphNode {

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建捕获仓库快照节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param articlePersistSupport 文章落库支撑服务
     */
    public CaptureRepoSnapshotNode(
            CompileGraphStateMapper compileGraphStateMapper,
            ArticlePersistSupport articlePersistSupport
    ) {
        super(compileGraphStateMapper);
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 捕获仓库快照。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        if (state.isSnapshotRequired() && state.getPersistedCount() > 0) {
            String triggerEvent = "incremental".equalsIgnoreCase(state.getCompileMode())
                    ? "compile.incremental.graph"
                    : "compile.full.graph";
            articlePersistSupport.captureRepoSnapshot(
                    triggerEvent,
                    Path.of(state.getSourceDir()),
                    state.getPersistedCount()
            );
        }
        return delta(state);
    }
}
