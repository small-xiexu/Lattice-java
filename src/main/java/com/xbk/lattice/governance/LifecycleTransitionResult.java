package com.xbk.lattice.governance;

/**
 * 生命周期切换结果
 *
 * 职责：返回单篇文章生命周期变更后的最小结果
 *
 * @author xiexu
 */
public class LifecycleTransitionResult {

    private final String conceptId;

    private final String title;

    private final String lifecycle;

    private final String reason;

    private final String updatedBy;

    private final String updatedAt;

    /**
     * 创建生命周期切换结果。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param lifecycle 生命周期
     * @param reason 原因
     * @param updatedBy 更新人
     * @param updatedAt 更新时间
     */
    public LifecycleTransitionResult(
            String conceptId,
            String title,
            String lifecycle,
            String reason,
            String updatedBy,
            String updatedAt
    ) {
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
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
     * 获取原因。
     *
     * @return 原因
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
