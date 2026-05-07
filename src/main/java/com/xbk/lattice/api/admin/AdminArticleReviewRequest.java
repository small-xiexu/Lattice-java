package com.xbk.lattice.api.admin;

/**
 * 管理侧文章人工复核请求
 *
 * 职责：承载人工复核动作入参
 *
 * @author xiexu
 */
public class AdminArticleReviewRequest {

    private Long sourceId;

    private String reviewedBy;

    private String comment;

    private String expectedReviewStatus;

    private String correctionSummary;

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 设置资料源主键。
     *
     * @param sourceId 资料源主键
     */
    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
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

    /**
     * 获取修正摘要。
     *
     * @return 修正摘要
     */
    public String getCorrectionSummary() {
        return correctionSummary;
    }

    /**
     * 设置修正摘要。
     *
     * @param correctionSummary 修正摘要
     */
    public void setCorrectionSummary(String correctionSummary) {
        this.correctionSummary = correctionSummary;
    }
}
