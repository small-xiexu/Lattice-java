package com.xbk.lattice.governance;

/**
 * 生命周期条目
 *
 * 职责：描述单篇知识文章当前的生命周期状态与留痕信息
 *
 * @author xiexu
 */
public class LifecycleItem {

    private final String conceptId;

    private final String title;

    private final String lifecycle;

    private final String reviewStatus;

    private final String reason;

    private final String updatedBy;

    private final String updatedAt;

    /**
     * 创建生命周期条目。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param lifecycle 生命周期
     * @param reviewStatus 审查状态
     * @param reason 生命周期原因
     * @param updatedBy 更新人
     * @param updatedAt 更新时间
     */
    public LifecycleItem(
            String conceptId,
            String title,
            String lifecycle,
            String reviewStatus,
            String reason,
            String updatedBy,
            String updatedAt
    ) {
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
        this.reviewStatus = reviewStatus;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
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
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取生命周期。
     *
     * @return 生命周期
     */
    public String getLifecycle() {
        return lifecycle;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取生命周期原因。
     *
     * @return 生命周期原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 获取更新人。
     *
     * @return 更新人
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public String getUpdatedAt() {
        return updatedAt;
    }
}
