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
        List<ArticleReviewEnvelope> articlesToPersist = retainPassedArticles(acceptedArticles);
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

    /**
     * 仅保留最终审查通过的文章进入正式查询可见持久化链路。
     *
     * @param reviewedArticles 审查包裹集合
     * @return 通过审查的文章集合
     */
    private List<ArticleReviewEnvelope> retainPassedArticles(List<ArticleReviewEnvelope> reviewedArticles) {
        List<ArticleReviewEnvelope> passedArticles = new ArrayList<ArticleReviewEnvelope>();
        for (ArticleReviewEnvelope reviewedArticle : reviewedArticles) {
            if (isPassedArticle(reviewedArticle)) {
                passedArticles.add(reviewedArticle);
            }
        }
        return passedArticles;
    }

    /**
     * 判断文章是否达到正式落库门槛。
     *
     * @param reviewedArticle 审查包裹对象
     * @return 是否通过审查
     */
    private boolean isPassedArticle(ArticleReviewEnvelope reviewedArticle) {
        if (reviewedArticle == null || reviewedArticle.getArticle() == null) {
            return false;
        }
        String reviewStatus = reviewedArticle.getReviewStatus();
        if (reviewStatus == null || reviewStatus.isBlank()) {
            reviewStatus = reviewedArticle.getArticle().getReviewStatus();
        }
        return "passed".equalsIgnoreCase(reviewStatus == null ? "" : reviewStatus.trim());
    }
}
