package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧答案反馈响应
 *
 * 职责：承载答案反馈队列列表和详情的基础字段
 *
 * @author xiexu
 */
public class AdminQueryFeedbackResponse {

    private final long id;

    private final String queryId;

    private final String question;

    private final String answerSummary;

    private final String feedbackType;

    private final String comment;

    private final List<String> articleKeys;

    private final List<String> sourcePaths;

    private final String reportedBy;

    private final String status;

    private final String resolutionComment;

    private final String handledBy;

    private final String handledAt;

    private final String createdAt;

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

    /**
     * 获取反馈主键。
     *
     * @return 反馈主键
     */
    public long getId() {
        return id;
    }

    /**
     * 获取查询 ID。
     *
     * @return 查询 ID
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 获取用户问题。
     *
     * @return 用户问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取答案摘要。
     *
     * @return 答案摘要
     */
    public String getAnswerSummary() {
        return answerSummary;
    }

    /**
     * 获取反馈类型。
     *
     * @return 反馈类型
     */
    public String getFeedbackType() {
        return feedbackType;
    }

    /**
     * 获取反馈说明。
     *
     * @return 反馈说明
     */
    public String getComment() {
        return comment;
    }

    /**
     * 获取关联文章唯一键。
     *
     * @return 关联文章唯一键
     */
    public List<String> getArticleKeys() {
        return articleKeys;
    }

    /**
     * 获取关联来源路径。
     *
     * @return 关联来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取反馈提交人。
     *
     * @return 反馈提交人
     */
    public String getReportedBy() {
        return reportedBy;
    }

    /**
     * 获取处理状态。
     *
     * @return 处理状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取处理说明。
     *
     * @return 处理说明
     */
    public String getResolutionComment() {
        return resolutionComment;
    }

    /**
     * 获取处理人。
     *
     * @return 处理人
     */
    public String getHandledBy() {
        return handledBy;
    }

    /**
     * 获取处理时间。
     *
     * @return 处理时间
     */
    public String getHandledAt() {
        return handledAt;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public String getCreatedAt() {
        return createdAt;
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
