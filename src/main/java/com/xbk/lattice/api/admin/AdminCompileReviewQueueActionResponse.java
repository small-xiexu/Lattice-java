package com.xbk.lattice.api.admin;

/**
 * 管理侧编译人工确认动作响应
 *
 * 职责：返回队列状态变更与审计标识
 *
 * @author xiexu
 */
public class AdminCompileReviewQueueActionResponse {

    private final AdminCompileReviewQueueItemResponse item;

    private final String previousReviewStatus;

    private final long auditId;

    /**
     * 创建管理侧编译人工确认动作响应。
     *
     * @param item 队列条目
     * @param previousReviewStatus 变更前状态
     * @param auditId 审计主键
     */
    public AdminCompileReviewQueueActionResponse(
            AdminCompileReviewQueueItemResponse item,
            String previousReviewStatus,
            long auditId
    ) {
        this.item = item;
        this.previousReviewStatus = previousReviewStatus;
        this.auditId = auditId;
    }

    /**
     * 获取队列条目。
     *
     * @return 队列条目
     */
    public AdminCompileReviewQueueItemResponse getItem() {
        return item;
    }

    /**
     * 获取变更前状态。
     *
     * @return 变更前状态
     */
    public String getPreviousReviewStatus() {
        return previousReviewStatus;
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
