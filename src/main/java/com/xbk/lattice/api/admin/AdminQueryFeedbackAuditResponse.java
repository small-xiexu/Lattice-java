package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧答案反馈审计响应。
 *
 * <p>承载答案反馈处理历史的展示字段——含操作动作、状态流转、操作人和扩展上下文，
 * 用于管理侧追溯反馈的完整处理链路。
 * 含审计和大文本字段（{@code comment}、{@code operatedBy}、{@code metadataJson}），
 * 禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryFeedbackAuditResponse {

    /** 审计记录主键。 */
    private final long id;

    /** 关联的反馈记录主键。 */
    private final long feedbackId;

    /**
     * 处理动作。
     *
     * <p>可选值：{@code create} / {@code resolve} / {@code dismiss}。
     * 驱动前端审计历史中的动作标签展示。</p>
     */
    private final String action;

    /** 操作前处理状态。 */
    private final String previousStatus;

    /**
     * 操作后处理状态。
     *
     * <p>与 {@code previousStatus} 对比可知本次操作的状态流转路径
     * （如 {@code pending → resolved}）。</p>
     */
    private final String nextStatus;

    /**
     * 操作时填写的说明。
     *
     * <p>含管理员主观评价，禁止参与 {@code toString()}。</p>
     */
    private final String comment;

    /**
     * 操作人标识。
     *
     * <p>用于审计追溯和责任认定。禁止参与 {@code toString()}。</p>
     */
    private final String operatedBy;

    /** 操作时间（ISO-8601 字符串）。 */
    private final String operatedAt;

    /**
     * 扩展上下文 JSON。
     *
     * <p>可能含操作时的额外快照信息。可能较大，禁止参与 {@code toString()}。</p>
     */
    private final String metadataJson;

    /**
     * 创建管理侧答案反馈审计响应。
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
    public AdminQueryFeedbackAuditResponse(
            long id,
            long feedbackId,
            String action,
            String previousStatus,
            String nextStatus,
            String comment,
            String operatedBy,
            String operatedAt,
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
}
