package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 刷新向量索引节点
 *
 * 职责：为已落库文章刷新向量索引
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class RefreshVectorIndexNode extends AbstractCompileGraphNode {

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建刷新向量索引节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articlePersistSupport 文章落库支撑服务
     */
    public RefreshVectorIndexNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticlePersistSupport articlePersistSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 刷新已落库文章的向量索引。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        if (state.getPersistedCount() > 0 && state.getReviewedArticlesRef() != null) {
            articlePersistSupport.refreshVectorIndex(
                    workingSetStore().loadReviewedArticles(state.getReviewedArticlesRef())
            );
        }
        return delta(state);
    }
}
