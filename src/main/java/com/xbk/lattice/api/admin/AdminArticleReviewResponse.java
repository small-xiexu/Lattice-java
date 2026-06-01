package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧文章人工复核响应。
 *
 * <p>返回人工复核操作后的文章状态变更与审计标识，
 * 由 {@code AdminArticleController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleReviewResponse {

    /** 资料源主键。为 {@code null} 表示多源。 */
    private final Long sourceId;

    /** 文章唯一键。 */
    private final String articleKey;

    /** 概念标识。 */
    private final String conceptId;

    /**
     * 操作前审查状态。
     *
     * <p>与请求中的 {@code expectedReviewStatus} 一致时操作成功。</p>
     */
    private final String previousReviewStatus;

    /**
     * 操作后审查状态。
     *
     * <p>与 {@code previousReviewStatus} 对比可知本次操作的状态流转路径。</p>
     */
    private final String reviewStatus;

    /** 操作人标识。 */
    private final String reviewedBy;

    /** 操作时间（ISO-8601 字符串）。 */
    private final String reviewedAt;

    /**
     * 审计记录主键。
     *
     * <p>可用于查询完整审计历史（{@link AdminArticleReviewAuditResponse}）。</p>
     */
    private final long auditId;

    /**
     * 创建管理侧文章人工复核响应。
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
    public AdminArticleReviewResponse(
            Long sourceId,
            String articleKey,
            String conceptId,
            String previousReviewStatus,
            String reviewStatus,
            String reviewedBy,
            String reviewedAt,
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
}
