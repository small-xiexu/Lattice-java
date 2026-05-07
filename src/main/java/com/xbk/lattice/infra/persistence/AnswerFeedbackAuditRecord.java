package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 答案反馈审计记录
 *
 * 职责：承载答案反馈队列的创建与处理动作历史
 *
 * @author xiexu
 */
public class AnswerFeedbackAuditRecord {

    private final long id;

    private final long feedbackId;

    private final String action;

    private final String previousStatus;

    private final String nextStatus;

    private final String comment;

    private final String operatedBy;

    private final OffsetDateTime operatedAt;

    private final String metadataJson;

    /**
     * 创建答案反馈审计记录。
     *
     * @param id 审计主键
     * @param feedbackId 反馈主键
     * @param action 处理动作
     * @param previousStatus 处理前状态
     * @param nextStatus 处理后状态
     * @param comment 处理说明
     * @param operatedBy 操作人
     * @param operatedAt 操作时间
     * @param metadataJson 扩展元数据 JSON
     */
    public AnswerFeedbackAuditRecord(
            long id,
            long feedbackId,
            String action,
            String previousStatus,
            String nextStatus,
            String comment,
            String operatedBy,
            OffsetDateTime operatedAt,
            String metadataJson
    ) {
        this.id = id;
        this.feedbackId = feedbackId;
        this.action = action;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.comment = comment;
        this.operatedBy = operatedBy;
        this.operatedAt = operatedAt;
        this.metadataJson = metadataJson;
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
     * 获取反馈主键。
     *
     * @return 反馈主键
     */
    public long getFeedbackId() {
        return feedbackId;
    }

    /**
     * 获取处理动作。
     *
     * @return 处理动作
     */
    public String getAction() {
        return action;
    }

    /**
     * 获取处理前状态。
     *
     * @return 处理前状态
     */
    public String getPreviousStatus() {
        return previousStatus;
    }

    /**
     * 获取处理后状态。
     *
     * @return 处理后状态
     */
    public String getNextStatus() {
        return nextStatus;
    }

    /**
     * 获取处理说明。
     *
     * @return 处理说明
     */
    public String getComment() {
        return comment;
    }

    /**
     * 获取操作人。
     *
     * @return 操作人
     */
    public String getOperatedBy() {
        return operatedBy;
    }

    /**
     * 获取操作时间。
     *
     * @return 操作时间
     */
    public OffsetDateTime getOperatedAt() {
        return operatedAt;
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
