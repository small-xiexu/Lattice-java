package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧编译人工确认队列条目响应
 *
 * 职责：返回草稿队列的列表与详情字段
 *
 * @author xiexu
 */
public class AdminCompileReviewQueueItemResponse {

    private final long id;

    private final String jobId;

    private final Long sourceId;

    private final String sourceCode;

    private final String conceptId;

    private final String articleKey;

    private final String title;

    private final String content;

    private final String metadataJson;

    private final String reviewStatus;

    private final String reviewRoute;

    private final String reviewerModel;

    private final String reviewIssuesJson;

    private final int fixAttemptCount;

    private final int maxFixRounds;

    private final List<String> sourcePaths;

    private final String createdAt;

    private final String updatedAt;

    private final String reviewedBy;

    private final String reviewedAt;

    private final String reviewComment;

    private final String publishedArticleKey;

    /**
     * 创建管理侧编译人工确认队列条目响应。
     *
     * @param id 队列主键
     * @param jobId 编译作业标识
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param conceptId 概念标识
     * @param articleKey 文章唯一键
     * @param title 标题
     * @param content 正文
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 队列状态
     * @param reviewRoute 审查路由
     * @param reviewerModel 审查模型
     * @param reviewIssuesJson 审查问题 JSON
     * @param fixAttemptCount 已修复轮数
     * @param maxFixRounds 最大修复轮数
     * @param sourcePaths 来源路径
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param reviewComment 复核意见
     * @param publishedArticleKey 发布后的文章唯一键
     */
    public AdminCompileReviewQueueItemResponse(
            long id,
            String jobId,
            Long sourceId,
            String sourceCode,
            String conceptId,
            String articleKey,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            String reviewRoute,
            String reviewerModel,
            String reviewIssuesJson,
            int fixAttemptCount,
            int maxFixRounds,
            List<String> sourcePaths,
            String createdAt,
            String updatedAt,
            String reviewedBy,
            String reviewedAt,
            String reviewComment,
            String publishedArticleKey
    ) {
        this.id = id;
        this.jobId = jobId;
        this.sourceId = sourceId;
        this.sourceCode = sourceCode;
        this.conceptId = conceptId;
        this.articleKey = articleKey;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.reviewRoute = reviewRoute;
        this.reviewerModel = reviewerModel;
        this.reviewIssuesJson = reviewIssuesJson;
        this.fixAttemptCount = fixAttemptCount;
        this.maxFixRounds = maxFixRounds;
        this.sourcePaths = sourcePaths;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
        this.publishedArticleKey = publishedArticleKey;
    }

    /**
     * 获取队列主键。
     *
     * @return 队列主键
     */
    public long getId() {
        return id;
    }

    /**
     * 获取编译作业标识。
     *
     * @return 编译作业标识
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取资料源编码。
     *
     * @return 资料源编码
     */
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取正文。
     *
     * @return 正文
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 获取队列状态。
     *
     * @return 队列状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取审查路由。
     *
     * @return 审查路由
     */
    public String getReviewRoute() {
        return reviewRoute;
    }

    /**
     * 获取审查模型。
     *
     * @return 审查模型
     */
    public String getReviewerModel() {
        return reviewerModel;
    }

    /**
     * 获取审查问题 JSON。
     *
     * @return 审查问题 JSON
     */
    public String getReviewIssuesJson() {
        return reviewIssuesJson;
    }

    /**
     * 获取已修复轮数。
     *
     * @return 已修复轮数
     */
    public int getFixAttemptCount() {
        return fixAttemptCount;
    }

    /**
     * 获取最大修复轮数。
     *
     * @return 最大修复轮数
     */
    public int getMaxFixRounds() {
        return maxFixRounds;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
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

    /**
     * 获取复核人。
     *
     * @return 复核人
     */
    public String getReviewedBy() {
        return reviewedBy;
    }

    /**
     * 获取复核时间。
     *
     * @return 复核时间
     */
    public String getReviewedAt() {
        return reviewedAt;
    }

    /**
     * 获取复核意见。
     *
     * @return 复核意见
     */
    public String getReviewComment() {
        return reviewComment;
    }

    /**
     * 获取发布后的文章唯一键。
     *
     * @return 发布后的文章唯一键
     */
    public String getPublishedArticleKey() {
        return publishedArticleKey;
    }
}
