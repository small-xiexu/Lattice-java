package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 文章人工复核审计记录
 *
 * 职责：承载单次人工复核动作、状态变化与审计扩展信息
 *
 * @author xiexu
 */
public class ArticleReviewAuditRecord {

    private final long id;

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String action;

    private final String previousReviewStatus;

    private final String nextReviewStatus;

    private final String comment;

    private final String reviewedBy;

    private final OffsetDateTime reviewedAt;

    private final String metadataJson;

    /**
     * 创建文章人工复核审计记录。
     *
     * @param id 审计主键
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param action 复核动作
     * @param previousReviewStatus 复核前状态
     * @param nextReviewStatus 复核后状态
     * @param comment 复核意见
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param metadataJson 扩展元数据 JSON
     */
    public ArticleReviewAuditRecord(
            long id,
            Long sourceId,
            String articleKey,
            String conceptId,
            String action,
            String previousReviewStatus,
            String nextReviewStatus,
            String comment,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String metadataJson
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.action = action;
        this.previousReviewStatus = previousReviewStatus;
        this.nextReviewStatus = nextReviewStatus;
        this.comment = comment;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.metadataJson = metadataJson;
    }

    /**
     * 从文章和复核上下文创建待保存审计记录。
     *
     * @param articleRecord 文章记录
     * @param action 复核动作
     * @param previousReviewStatus 复核前状态
     * @param nextReviewStatus 复核后状态
     * @param comment 复核意见
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param metadataJson 扩展元数据 JSON
     * @return 审计记录
     */
    public static ArticleReviewAuditRecord fromArticle(
            ArticleRecord articleRecord,
            String action,
            String previousReviewStatus,
            String nextReviewStatus,
            String comment,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String metadataJson
    ) {
        return new ArticleReviewAuditRecord(
                0L,
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                action,
                previousReviewStatus,
                nextReviewStatus,
                comment,
                reviewedBy,
                reviewedAt,
                metadataJson
        );
    }

    /**
     * 获取审计主键。
     *
     * @return 审计主键
     */
    public long getId() {
        return id;
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
     * 获取复核动作。
     *
     * @return 复核动作
     */
    public String getAction() {
        return action;
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
    public String getNextReviewStatus() {
        return nextReviewStatus;
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
     * 获取扩展元数据 JSON。
     *
     * @return 扩展元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }
}
