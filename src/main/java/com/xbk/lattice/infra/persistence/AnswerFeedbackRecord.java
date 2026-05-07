package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 答案反馈记录
 *
 * 职责：承载一次用户对问答结果的反馈与处理状态
 *
 * @author xiexu
 */
public class AnswerFeedbackRecord {

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

    private final OffsetDateTime handledAt;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    private final String metadataJson;

    /**
     * 创建答案反馈记录。
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
     * @param resolutionComment 处理结论说明
     * @param handledBy 处理人
     * @param handledAt 处理时间
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param metadataJson 扩展元数据 JSON
     */
    public AnswerFeedbackRecord(
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
            OffsetDateTime handledAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String metadataJson
    ) {
        this.id = id;
        this.queryId = queryId;
        this.question = question;
        this.answerSummary = answerSummary;
        this.feedbackType = feedbackType;
        this.comment = comment;
        this.articleKeys = articleKeys == null ? List.of() : List.copyOf(articleKeys);
        this.sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        this.reportedBy = reportedBy;
        this.status = status;
        this.resolutionComment = resolutionComment;
        this.handledBy = handledBy;
        this.handledAt = handledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadataJson = metadataJson;
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
     * 获取处理结论说明。
     *
     * @return 处理结论说明
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
    public OffsetDateTime getHandledAt() {
        return handledAt;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
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
