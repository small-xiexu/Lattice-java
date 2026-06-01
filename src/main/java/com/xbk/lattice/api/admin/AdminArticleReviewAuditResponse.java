package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧文章人工复核审计响应。
 *
 * <p>承载单条人工复核历史记录——含操作人、动作、状态流转、意见和扩展上下文，
 * 用于管理侧审计追溯。含审计字段（{@code comment}、{@code reviewedBy}、{@code metadataJson}），
 * 禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleReviewAuditResponse {

    /** 审计记录主键。 */
    private final long id;

    /** 资料源主键。为 {@code null} 表示多源。 */
    private final Long sourceId;

    /** 文章唯一键。 */
    private final String articleKey;

    /** 概念标识。 */
    private final String conceptId;

    /**
     * 复核动作。
     *
     * <p>可选值：{@code approve} / {@code request_changes}。
     * 驱动前端展示不同的审计动作标签。</p>
     */
    private final String action;

    /** 操作前审查状态。 */
    private final String previousReviewStatus;

    /**
     * 操作后审查状态。
     *
     * <p>与 {@code previousReviewStatus} 对比可知本次操作的状态流转路径。</p>
     */
    private final String nextReviewStatus;

    /**
     * 复核意见。
     *
     * <p>审批或驳回时填写的原因。含人工主观评价，禁止参与 {@code toString()}。</p>
     */
    private final String comment;

    /**
     * 复核人标识。
     *
     * <p>用于审计追溯和责任认定。禁止参与 {@code toString()}。</p>
     */
    private final String reviewedBy;

    /** 复核时间（ISO-8601 字符串）。 */
    private final String reviewedAt;

    /**
     * 扩展上下文 JSON。
     *
     * <p>可能包含复核时的附加信息（如页面快照、关联数据）。
     * 可能较大，禁止参与 {@code toString()}。</p>
     */
    private final String metadataJson;

    /**
     * 创建管理侧文章人工复核审计响应。
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
    public AdminArticleReviewAuditResponse(
            long id,
            Long sourceId,
            String articleKey,
            String conceptId,
            String action,
            String previousReviewStatus,
            String nextReviewStatus,
            String comment,
            String reviewedBy,
            String reviewedAt,
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
}
