package com.xbk.lattice.governance;

import java.time.OffsetDateTime;

/**
 * 文章人工复核结果
 *
 * 职责：承载人工复核后的状态变化和审计标识
 *
 * @author xiexu
 */
public class ArticleManualReviewResult {

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String previousReviewStatus;

    private final String reviewStatus;

    private final String reviewedBy;

    private final OffsetDateTime reviewedAt;

    private final long auditId;

    /**
     * 创建文章人工复核结果。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param previousReviewStatus 复核前状态
     * @param reviewStatus 复核后状态
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param auditId 审计主键
     */
    public ArticleManualReviewResult(
            Long sourceId,
            String articleKey,
            String conceptId,
            String previousReviewStatus,
            String reviewStatus,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            long auditId
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.previousReviewStatus = previousReviewStatus;
        this.reviewStatus = reviewStatus;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.auditId = auditId;
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
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取复核前状态。
     *
     * @return 复核前状态
     */
    public String getPreviousReviewStatus() {
        return previousReviewStatus;
    }

    /**
     * 获取复核后状态。
     *
     * @return 复核后状态
     */
    public String getReviewStatus() {
        return reviewStatus;
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
     * 获取复核时间。
     *
     * @return 复核时间
     */
    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    /**
     * 获取审计主键。
     *
     * @return 审计主键
     */
    public long getAuditId() {
        return auditId;
    }
}
