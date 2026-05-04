package com.xbk.lattice.compiler.graph;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 审查决策策略
 *
 * 职责：根据审查结果、修复轮次与自动修复开关决定后续路由
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class ReviewDecisionPolicy {

    /**
     * 对当前轮审查结果做分区。
     *
     * @param state 编译图状态
     * @param reviewedArticles 审查结果
     * @return 分区结果
     */
    public ReviewPartition partition(CompileGraphState state, List<ArticleReviewEnvelope> reviewedArticles) {
        ReviewPartition reviewPartition = new ReviewPartition();
        List<ArticleReviewEnvelope> accepted = new ArrayList<ArticleReviewEnvelope>();
        List<ArticleReviewEnvelope> fixable = new ArrayList<ArticleReviewEnvelope>();
        List<ArticleReviewEnvelope> needsHumanReview = new ArrayList<ArticleReviewEnvelope>();
        for (ArticleReviewEnvelope reviewedArticle : reviewedArticles) {
            if (reviewedArticle.getReviewResult() != null && reviewedArticle.getReviewResult().isPass()) {
                reviewedArticle.setReviewStatus("passed");
                accepted.add(reviewedArticle);
                continue;
            }
            if (state.isAutoFixEnabled()
                    && state.getFixAttemptCount() < state.getMaxFixRounds()
                    && hasIssues(reviewedArticle)) {
                reviewedArticle.setReviewStatus("pending");
                fixable.add(reviewedArticle);
                continue;
            }
            reviewedArticle.setReviewStatus("needs_human_review");
            needsHumanReview.add(reviewedArticle);
        }
        reviewPartition.setAccepted(accepted);
        reviewPartition.setFixable(fixable);
        reviewPartition.setNeedsHumanReview(needsHumanReview);
        return reviewPartition;
    }

    /**
     * 决定 review_articles 之后的路由。
     *
     * @param state 编译图状态
     * @param reviewPartition 分区结果
     * @return 条件路由键
     */
    public String decide(CompileGraphState state, ReviewPartition reviewPartition) {
        if (state.isAutoFixEnabled()
                && state.getFixAttemptCount() < state.getMaxFixRounds()
                && !reviewPartition.getFixable().isEmpty()) {
            return "fix_review_issues";
        }
        return "persist_articles";
    }

    private boolean hasIssues(ArticleReviewEnvelope reviewedArticle) {
        return reviewedArticle.getReviewResult() != null
                && reviewedArticle.getReviewResult().getIssues() != null
                && !reviewedArticle.getReviewResult().getIssues().isEmpty();
    }

}
