package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 编译文章人工确认队列记录
 *
 * 职责：承载 needs_human_review 编译草稿的长期人工确认状态
 *
 * @author xiexu
 */
public class CompileArticleReviewQueueRecord {

    private final long id;

    private final String jobId;

    private final Long sourceId;

    private final String sourceCode;

    private final String conceptId;

    private final String articleKey;

    private final String title;

    private final String content;

    private final String lifecycle;

    private final OffsetDateTime compiledAt;

    private final List<String> sourcePaths;

    private final String metadataJson;

    private final String reviewStatus;

    private final String reviewRoute;

    private final String reviewerModel;

    private final String reviewIssuesJson;

    private final int fixAttemptCount;

    private final int maxFixRounds;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    private final String reviewedBy;

    private final OffsetDateTime reviewedAt;

    private final String reviewComment;

    private final String publishedArticleKey;

    /**
     * 创建编译文章人工确认队列记录。
     *
     * @param id 队列主键
     * @param jobId 编译作业标识
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param conceptId 概念标识
     * @param articleKey 文章唯一键
     * @param title 标题
     * @param content 正文
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 队列状态
     * @param reviewRoute 审查路由
     * @param reviewerModel 审查模型
     * @param reviewIssuesJson 审查问题 JSON
     * @param fixAttemptCount 已修复轮数
     * @param maxFixRounds 最大修复轮数
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param reviewComment 复核意见
     * @param publishedArticleKey 发布后的文章唯一键
     */
    public CompileArticleReviewQueueRecord(
            long id,
            String jobId,
            Long sourceId,
            String sourceCode,
            String conceptId,
            String articleKey,
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson,
            String reviewStatus,
            String reviewRoute,
            String reviewerModel,
            String reviewIssuesJson,
            int fixAttemptCount,
            int maxFixRounds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String reviewedBy,
            OffsetDateTime reviewedAt,
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
        this.lifecycle = lifecycle;
        this.compiledAt = compiledAt;
        this.sourcePaths = sourcePaths;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.reviewRoute = reviewRoute;
        this.reviewerModel = reviewerModel;
        this.reviewIssuesJson = reviewIssuesJson;
        this.fixAttemptCount = fixAttemptCount;
        this.maxFixRounds = maxFixRounds;
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
     * 获取生命周期。
     *
     * @return 生命周期
     */
    public String getLifecycle() {
        return lifecycle;
    }

    /**
     * 获取编译时间。
     *
     * @return 编译时间
     */
    public OffsetDateTime getCompiledAt() {
        return compiledAt;
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
    public OffsetDateTime getReviewedAt() {
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
