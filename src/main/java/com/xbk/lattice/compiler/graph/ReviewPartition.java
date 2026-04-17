package com.xbk.lattice.compiler.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 审查分区结果
 *
 * 职责：承载当前轮审查后的 accepted / fixable / needs_human_review 三类分区
 *
 * @author xiexu
 */
public class ReviewPartition {

    private List<ArticleReviewEnvelope> accepted = new ArrayList<ArticleReviewEnvelope>();

    private List<ArticleReviewEnvelope> fixable = new ArrayList<ArticleReviewEnvelope>();

    private List<ArticleReviewEnvelope> needsHumanReview = new ArrayList<ArticleReviewEnvelope>();

    /**
     * 返回 accepted 子集。
     *
     * @return accepted 子集
     */
    public List<ArticleReviewEnvelope> getAccepted() {
        return accepted;
    }

    /**
     * 设置 accepted 子集。
     *
     * @param accepted accepted 子集
     */
    public void setAccepted(List<ArticleReviewEnvelope> accepted) {
        this.accepted = accepted;
    }

    /**
     * 返回 fixable 子集。
     *
     * @return fixable 子集
     */
    public List<ArticleReviewEnvelope> getFixable() {
        return fixable;
    }

    /**
     * 设置 fixable 子集。
     *
     * @param fixable fixable 子集
     */
    public void setFixable(List<ArticleReviewEnvelope> fixable) {
        this.fixable = fixable;
    }

    /**
     * 返回 needs_human_review 子集。
     *
     * @return needs_human_review 子集
     */
    public List<ArticleReviewEnvelope> getNeedsHumanReview() {
        return needsHumanReview;
    }

    /**
     * 设置 needs_human_review 子集。
     *
     * @param needsHumanReview needs_human_review 子集
     */
    public void setNeedsHumanReview(List<ArticleReviewEnvelope> needsHumanReview) {
        this.needsHumanReview = needsHumanReview;
    }
}
