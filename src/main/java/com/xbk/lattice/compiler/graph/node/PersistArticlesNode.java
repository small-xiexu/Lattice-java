package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.service.ArticleAtomicWriteService;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import org.springframework.beans.factory.annotation.Autowired;
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
public class PersistArticlesNode extends AbstractCompileGraphNode {

    private final ArticleAtomicWriteService articleAtomicWriteService;

    /**
     * 创建落库文章节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articleAtomicWriteService 文章原子写入服务
     */
    @Autowired
    public PersistArticlesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticleAtomicWriteService articleAtomicWriteService
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articleAtomicWriteService = articleAtomicWriteService;
    }

    /**
     * 创建测试替身可注入的落库文章节点。
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
        this(
                compileGraphStateMapper,
                compileWorkingSetStore,
                new ArticleAtomicWriteService(articlePersistSupport)
        );
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
                : articleAtomicWriteService.persistArticlesAtomic(
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
