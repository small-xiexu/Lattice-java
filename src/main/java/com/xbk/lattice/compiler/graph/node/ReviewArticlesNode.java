package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.graph.ReviewDecisionPolicy;
import com.xbk.lattice.compiler.graph.ReviewPartition;
import com.xbk.lattice.compiler.service.ArticleCompileSupport;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审查文章节点
 *
 * 职责：执行文章审查并按通过、待修复、待人工复核进行分区
 *
 * @author xiexu
 */
@Component
public class ReviewArticlesNode extends AbstractCompileGraphNode {

    private final ArticleCompileSupport articleCompileSupport;

    private final ReviewDecisionPolicy reviewDecisionPolicy;

    /**
     * 创建审查文章节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articleCompileSupport 文章编译支撑服务
     * @param reviewDecisionPolicy 审查决策策略
     */
    public ReviewArticlesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticleCompileSupport articleCompileSupport,
            ReviewDecisionPolicy reviewDecisionPolicy
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articleCompileSupport = articleCompileSupport;
        this.reviewDecisionPolicy = reviewDecisionPolicy;
    }

    /**
     * 执行文章审查与结果分区。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<ArticleReviewEnvelope> baseReviewedArticles = loadReviewedArticles(state.getReviewedArticlesRef());
        List<ArticleRecord> articlesToReview = new ArrayList<ArticleRecord>();
        if (!baseReviewedArticles.isEmpty()) {
            for (ArticleReviewEnvelope reviewedArticle : baseReviewedArticles) {
                articlesToReview.add(reviewedArticle.getArticle());
            }
        }
        else {
            articlesToReview.addAll(loadDraftArticles(state.getDraftArticlesRef()));
        }
        List<ArticleReviewEnvelope> reviewedArticles = articleCompileSupport.reviewDraftArticles(
                articlesToReview,
                state.getJobId(),
                ExecutionLlmSnapshotService.COMPILE_SCENE
        );
        mergeAttemptMetadata(baseReviewedArticles, reviewedArticles);

        ReviewPartition reviewPartition = reviewDecisionPolicy.partition(state, reviewedArticles);
        List<ArticleReviewEnvelope> acceptedArticles = mergeReviewEnvelopes(
                loadAcceptedArticles(state.getAcceptedArticlesRef()),
                reviewPartition.getAccepted()
        );
        List<ArticleReviewEnvelope> needsHumanReviewArticles = mergeReviewEnvelopes(
                loadNeedsHumanReviewArticles(state.getNeedsHumanReviewArticlesRef()),
                reviewPartition.getNeedsHumanReview()
        );
        state.setReviewedArticlesRef(workingSetStore().saveReviewedArticles(state.getJobId(), reviewedArticles));
        state.setReviewPartitionRef(workingSetStore().saveReviewPartition(state.getJobId(), reviewPartition));
        state.setAcceptedArticlesRef(saveAcceptedArticles(state.getJobId(), acceptedArticles));
        state.setNeedsHumanReviewArticlesRef(saveNeedsHumanReviewArticles(
                state.getJobId(),
                needsHumanReviewArticles
        ));
        state.setPendingReviewCount(reviewPartition.getFixable().size());
        state.setAcceptedCount(acceptedArticles.size());
        state.setNeedsHumanReviewCount(needsHumanReviewArticles.size());
        return delta(state);
    }
}
