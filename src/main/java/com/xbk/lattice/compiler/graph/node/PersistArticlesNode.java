package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 落库文章节点
 *
 * 职责：把通过审查或允许落库的文章正式写入文章仓储
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class PersistArticlesNode extends AbstractCompileGraphNode {

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建落库文章节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articlePersistSupport 文章落库支撑服务
     */
    public PersistArticlesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticlePersistSupport articlePersistSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 执行文章落库。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<ArticleReviewEnvelope> acceptedArticles = loadAcceptedArticles(state.getAcceptedArticlesRef());
        List<ArticleReviewEnvelope> articlesToPersist = new ArrayList<ArticleReviewEnvelope>(acceptedArticles);
        if (state.isAllowPersistNeedsHumanReview()) {
            articlesToPersist = mergeReviewEnvelopes(
                    articlesToPersist,
                    loadNeedsHumanReviewArticles(state.getNeedsHumanReviewArticlesRef())
            );
        }
        int persistedCount = articlesToPersist.isEmpty()
                ? 0
                : articlePersistSupport.persistArticles(
                        state.getJobId(),
                        articlesToPersist,
                        state.getSourceId(),
                        state.getSourceCode(),
                        state.getSourceFileIdsByPath()
                );
        state.setPersistedCount(persistedCount);
        state.setPendingReviewCount(0);
        state.setPersistedArticleIds(extractArticleIds(articlesToPersist));
        if (!articlesToPersist.isEmpty()) {
            state.setReviewedArticlesRef(workingSetStore().saveReviewedArticles(state.getJobId(), articlesToPersist));
        }
        return delta(state);
    }
}
