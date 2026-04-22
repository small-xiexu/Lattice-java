package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 生成合成产物节点
 *
 * 职责：在文章落库后刷新合成产物
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class GenerateSynthesisArtifactsNode extends AbstractCompileGraphNode {

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建生成合成产物节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param articlePersistSupport 文章落库支撑服务
     */
    public GenerateSynthesisArtifactsNode(
            CompileGraphStateMapper compileGraphStateMapper,
            ArticlePersistSupport articlePersistSupport
    ) {
        super(compileGraphStateMapper);
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 刷新合成产物。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        if (state.isSynthesisRequired() && state.getPersistedCount() > 0) {
            articlePersistSupport.generateGraphSynthesisArtifacts(state.getJobId());
        }
        return delta(state);
    }
}
