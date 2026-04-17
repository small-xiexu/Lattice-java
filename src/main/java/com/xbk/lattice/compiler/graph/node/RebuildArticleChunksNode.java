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
 * 重建文章分块节点
 *
 * 职责：为已落库文章重建查询用 chunk 数据
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class RebuildArticleChunksNode extends AbstractCompileGraphNode {

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建重建文章分块节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articlePersistSupport 文章落库支撑服务
     */
    public RebuildArticleChunksNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticlePersistSupport articlePersistSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 重建已落库文章的分块。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        if (state.getPersistedCount() > 0 && state.getReviewedArticlesRef() != null) {
            articlePersistSupport.rebuildArticleChunks(
                    workingSetStore().loadReviewedArticles(state.getReviewedArticlesRef())
            );
        }
        return delta(state);
    }
}
