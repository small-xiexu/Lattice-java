package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧编译审查人工确认动作响应。
 *
 * <p>返回队列状态变更结果与审计标识，由 {@code AdminCompileReviewQueueController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileReviewQueueActionResponse {

    /**
     * 操作后的队列条目当前快照。
     *
     * <p>包含人工确认后的最新状态和所有关联字段。</p>
     */
    private final AdminCompileReviewQueueItemResponse item;

    /**
     * 操作前队列状态。
     *
     * <p>与请求中的 {@code expectedReviewStatus} 一致时操作成功，
     * 用于前端确认状态流转路径。</p>
     */
    private final String previousReviewStatus;

    /**
     * 操作审计记录主键。
     *
     * <p>可用于追溯本次人工确认的完整审计链路（谁、何时、从什么状态变更到什么状态）。</p>
     */
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
}
