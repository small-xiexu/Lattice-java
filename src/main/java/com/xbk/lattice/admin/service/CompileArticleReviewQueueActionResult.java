package com.xbk.lattice.admin.service;

import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import lombok.Getter;

/**
 * 编译文章人工确认动作结果。
 *
 * <p>返回人工确认操作的执行结果，包括变更后的队列记录、
 * 变更前的原始状态和审计主键。调用方通过这个结构确认操作是否成功、
 * 记录审计追踪并获取最新的队列状态。
 *
 * @author xiexu
 */
@Getter
public class CompileArticleReviewQueueActionResult {

    /**
     * 变更后的队列记录。
     *
     * <p>操作完成后队列中的最新记录快照。调用方从中读取文章的当前 review_status、
     * reviewer 信息、时间戳等用于前端状态刷新。</p>
     */
    private final CompileArticleReviewQueueRecord queueRecord;

    /**
     * 变更前状态。
     *
     * <p>操作执行前该队列记录的 review_status 值。调用方用它做审计对比——
     * 记录从什么状态变更到了什么状态。</p>
     */
    private final String previousReviewStatus;

    /**
     * 审计主键。
     *
     * <p>本次操作的审计记录 ID。调用方可以用它关联审计日志、
     * 查询操作详情和追踪审批链路。</p>
     */
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
}
