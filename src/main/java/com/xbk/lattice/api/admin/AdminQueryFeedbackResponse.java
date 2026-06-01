package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧答案反馈响应。
 *
 * <p>承载答案反馈队列列表和详情的基础字段——含用户问题、答案、反馈内容、
 * 处理状态与审计信息，由 {@code AdminQueryFeedbackController} 组装返回。
 * 含用户数据与审计字段（{@code comment}、{@code resolutionComment}、{@code reportedBy}、
 * {@code handledBy}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryFeedbackResponse {

    /** 反馈记录主键。 */
    private final long id;

    /** 关联的查询会话标识。 */
    private final String queryId;

    /**
     * 用户原始问题文本。
     *
     * <p>可能含 PII，禁止参与 {@code toString()}。</p>
     */
    private final String question;

    /**
     * 答案摘要文本。
     *
     * <p>可能与完整答案不同，仅供管理侧了解被反馈的答案。禁止参与 {@code toString()}。</p>
     */
    private final String answerSummary;

    /**
     * 反馈类型（{@code positive} / {@code negative} / {@code correction}）。
     *
     * <p>驱动前端展示的反馈分类标签和处理优先级。</p>
     */
    private final String feedbackType;

    /**
     * 用户提交的反馈说明原文。
     *
     * <p>不可控的用户输入，禁止参与 {@code toString()}。</p>
     */
    private final String comment;

    /** 反馈关联的文章唯一键列表。 */
    private final List<String> articleKeys;

    /** 反馈关联的来源文件路径列表。 */
    private final List<String> sourcePaths;

    /**
     * 反馈提交人标识。
     *
     * <p>禁止参与 {@code toString()}。</p>
     */
    private final String reportedBy;

    /**
     * 处理状态。
     *
     * <p>可选值：{@code pending} / {@code resolved} / {@code dismissed}。
     * 驱动前端展示处理标签和可用的操作按钮。{@code pending} 时处理人可执行 resolve/dismiss。</p>
     */
    private final String status;

    /**
     * 处理结果说明。
     *
     * <p>{@code resolved} 或 {@code dismissed} 时填写。禁止参与 {@code toString()}。</p>
     */
    private final String resolutionComment;

    /**
     * 处理人标识。
     *
     * <p>为 {@code null} 表示尚未处理。禁止参与 {@code toString()}。</p>
     */
    private final String handledBy;

    /**
     * 处理时间（ISO-8601 字符串）。
     *
     * <p>为 {@code null} 表示尚未处理。</p>
     */
    private final String handledAt;

    /** 反馈创建时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /** 最后更新时间（ISO-8601 字符串）。 */
    private final String updatedAt;

    /**
     * 创建管理侧答案反馈响应。
     *
     * @param id 反馈主键
     * @param queryId 查询 ID
     * @param question 用户问题
     * @param answerSummary 答案摘要
     * @param feedbackType 反馈类型
     * @param comment 反馈说明
     * @param articleKeys 关联文章唯一键
     * @param sourcePaths 关联来源路径
     * @param reportedBy 反馈提交人
     * @param status 处理状态
     * @param resolutionComment 处理说明
     * @param handledBy 处理人
     * @param handledAt 处理时间
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AdminQueryFeedbackResponse(
            long id,
            String queryId,
            String question,
            String answerSummary,
            String feedbackType,
            String comment,
            List<String> articleKeys,
            List<String> sourcePaths,
            String reportedBy,
            String status,
            String resolutionComment,
            String handledBy,
            String handledAt,
            String createdAt,
            String updatedAt
    ) {
        this.id = id;
        this.queryId = queryId;
        this.question = question;
        this.answerSummary = answerSummary;
        this.feedbackType = feedbackType;
        this.comment = comment;
        this.articleKeys = articleKeys;
        this.sourcePaths = sourcePaths;
        this.reportedBy = reportedBy;
        this.status = status;
        this.resolutionComment = resolutionComment;
        this.handledBy = handledBy;
        this.handledAt = handledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
