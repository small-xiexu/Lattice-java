package com.xbk.lattice.api.admin;

/**
 * 管理侧编译草稿人工确认动作请求
 *
 * 职责：承载人工发布或驳回动作入参
 *
 * @author xiexu
 */
public class AdminCompileReviewQueueActionRequest {

    private String reviewedBy;

    private String comment;

    private String expectedReviewStatus;

    /**
     * 获取复核人。
     *
     * @return 复核人
     */
    public String getReviewedBy() {
        return reviewedBy;
    }

    /**
     * 设置复核人。
     *
     * @param reviewedBy 复核人
     */
    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
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
     * 设置复核意见。
     *
     * @param comment 复核意见
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * 获取期望原状态。
     *
     * @return 期望原状态
     */
    public String getExpectedReviewStatus() {
        return expectedReviewStatus;
    }

    /**
     * 设置期望原状态。
     *
     * @param expectedReviewStatus 期望原状态
     */
    public void setExpectedReviewStatus(String expectedReviewStatus) {
        this.expectedReviewStatus = expectedReviewStatus;
    }
}
