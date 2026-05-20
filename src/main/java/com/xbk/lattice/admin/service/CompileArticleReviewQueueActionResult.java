package com.xbk.lattice.admin.service;

import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;

/**
 * 编译文章人工确认动作结果
 *
 * 职责：返回队列状态变化、发布文章与审计标识
 *
 * @author xiexu
 */
public class CompileArticleReviewQueueActionResult {

    private final CompileArticleReviewQueueRecord queueRecord;

    private final String previousReviewStatus;

    private final long auditId;

    /**
     * 创建编译文章人工确认动作结果。
     *
     * @param queueRecord 队列记录
     * @param previousReviewStatus 变更前状态
     * @param auditId 审计主键
     */
    public CompileArticleReviewQueueActionResult(
            CompileArticleReviewQueueRecord queueRecord,
            String previousReviewStatus,
            long auditId
    ) {
        this.queueRecord = queueRecord;
        this.previousReviewStatus = previousReviewStatus;
        this.auditId = auditId;
    }

    /**
     * 获取队列记录。
     *
     * @return 队列记录
     */
    public CompileArticleReviewQueueRecord getQueueRecord() {
        return queueRecord;
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
