package com.xbk.lattice.admin.service;

import lombok.Getter;

/**
 * 编译文章人工确认动作请求。
 *
 * <p>承载对编译审查队列中待确认文章的 approve 或 reject 操作参数，
 * 包括复核人身份、复核意见和乐观锁期望状态。
 *
 * @author xiexu
 */
@Getter
public class CompileArticleReviewQueueActionRequest {

    /**
     * 复核人。
     *
     * <p>执行 approve/reject 操作的复核人员标识，用于审计追踪和责任归属。</p>
     */
    private final String reviewedBy;

    /**
     * 复核意见。
     *
     * <p>复核人对该文章的审批说明或拒绝原因。审批通过时可为空，拒绝时建议填写原因。</p>
     */
    private final String comment;

    /**
     * 期望原状态。
     *
     * <p>乐观锁字段——复核人期望在执行操作前队列记录的当前状态。
     * 如果队列记录的实际状态与此不符（说明被其他人并发修改），操作会被拒绝。
     * 用于防止并发 approve/reject 导致的状态覆盖。</p>
     */
    private final String expectedReviewStatus;

    /**
     * 创建编译文章人工确认动作请求。
     *
     * @param reviewedBy 复核人
     * @param comment 复核意见
     * @param expectedReviewStatus 期望原状态
     */
    public CompileArticleReviewQueueActionRequest(
            String reviewedBy,
            String comment,
            String expectedReviewStatus
    ) {
        this.reviewedBy = reviewedBy;
        this.comment = comment;
        this.expectedReviewStatus = expectedReviewStatus;
    }
}
