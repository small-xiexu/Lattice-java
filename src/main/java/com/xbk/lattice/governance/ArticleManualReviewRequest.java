package com.xbk.lattice.governance;

/**
 * 文章人工复核请求
 *
 * 职责：承载人工复核操作所需的状态校验、操作者与意见
 *
 * @author xiexu
 */
public class ArticleManualReviewRequest {

    private final Long sourceId;

    private final String reviewedBy;

    private final String comment;

    private final String expectedReviewStatus;

    private final String correctionSummary;

    /**
     * 创建文章人工复核请求。
     *
     * @param sourceId 资料源主键
     * @param reviewedBy 复核人
     * @param comment 复核意见
     * @param expectedReviewStatus 期望原状态
     * @param correctionSummary 修正摘要
     */
    public ArticleManualReviewRequest(
            Long sourceId,
            String reviewedBy,
            String comment,
            String expectedReviewStatus,
            String correctionSummary
    ) {
        this.sourceId = sourceId;
        this.reviewedBy = reviewedBy;
        this.comment = comment;
        this.expectedReviewStatus = expectedReviewStatus;
        this.correctionSummary = correctionSummary;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
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

    /**
     * 获取修正摘要。
     *
     * @return 修正摘要
     */
    public String getCorrectionSummary() {
        return correctionSummary;
    }
}
