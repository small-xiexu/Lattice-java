package com.xbk.lattice.admin.service;

/**
 * 编译文章人工确认动作请求
 *
 * 职责：承载 approve/reject 的复核人、意见与并发期望状态
 *
 * @author xiexu
 */
public class CompileArticleReviewQueueActionRequest {

    private final String reviewedBy;

    private final String comment;

    private final String expectedReviewStatus;

    /**
     * 创建编译文章人工确认动作请求。
     *
     * @param reviewedBy 复核人
     * @param comment 复核意见
     * @param expectedReviewStatus 期望原状态
     */
    public CompileArticleReviewQueueActionRequest(
            String reviewedBy,
            String comment,
            String expectedReviewStatus
    ) {
        this.reviewedBy = reviewedBy;
        this.comment = comment;
        this.expectedReviewStatus = expectedReviewStatus;
    }

    /**
     * 获取复核人。
     *
     * @return 复核人
     */
    public String getReviewedBy() {
        return reviewedBy;
    }

    /**
     * 获取复核意见。
     *
     * @return 复核意见
     */
    public String getComment() {
        return comment;
    }

    /**
     * 获取期望原状态。
     *
     * @return 期望原状态
     */
    public String getExpectedReviewStatus() {
        return expectedReviewStatus;
    }
}
