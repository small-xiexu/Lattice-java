package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧编译审查人工确认队列条目响应。
 *
 * <p>返回草稿队列的详情字段——包含文章内容、审查问题、自动修复状态与人工复核信息，
 * 由 {@code AdminCompileReviewQueueController} 从队列记录组装返回。
 * 含大文本字段（{@code content}、{@code metadataJson}、{@code reviewIssuesJson}），
 * 禁止引入 {@code @Data} 以防止 {@code toString()} 输出巨量内容。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileReviewQueueItemResponse {

    /** 队列记录主键。 */
    private final long id;

    /** 所属编译作业标识。 */
    private final String jobId;

    /**
     * 资料源主键。
     *
     * <p>为 {@code null} 表示无关联 source。</p>
     */
    private final Long sourceId;

    /** 资料源编码。 */
    private final String sourceCode;

    /** 被编译的概念标识。 */
    private final String conceptId;

    /** 文章唯一键（编译生成）。 */
    private final String articleKey;

    /** 文章标题。 */
    private final String title;

    /**
     * 文章正文。
     *
     * <p>可能为长文本，仅用于管理侧预览。不应参与 {@code toString()}。</p>
     */
    private final String content;

    /**
     * 文章元数据 JSON 字符串。
     *
     * <p>可能较大，仅用于管理侧展示。</p>
     */
    private final String metadataJson;

    /**
     * 当前队列状态。
     *
     * <p>可选值：{@code needs_human_review} / {@code accepted} / {@code published} / {@code rejected}。</p>
     */
    private final String reviewStatus;

    /**
     * 审查模型路由（如 {@code auto} / {@code manual} / {@code hybrid}）。
     */
    private final String reviewRoute;

    /** 执行审查的 LLM 模型标识。 */
    private final String reviewerModel;

    /**
     * 审查发现的全部问题 JSON。
     *
     * <p>可能较大，包含每个问题的严重度、位置、建议修复方案。</p>
     */
    private final String reviewIssuesJson;

    /** 自动修复已执行轮数。 */
    private final int fixAttemptCount;

    /**
     * 自动修复最大轮次上限。
     *
     * <p>从 compile review 配置快照而来，与运行时的实时配置可能不同。</p>
     */
    private final int maxFixRounds;

    /**
     * 编译输入文件的相对路径列表。
     */
    private final List<String> sourcePaths;

    /** 队列记录创建时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /** 队列记录最后更新时间（ISO-8601 字符串）。 */
    private final String updatedAt;

    /**
     * 人工复核人标识。
     *
     * <p>为 {@code null} 表示尚未人工处理。</p>
     */
    private final String reviewedBy;

    /**
     * 人工复核时间（ISO-8601 字符串）。
     *
     * <p>为 {@code null} 表示尚未人工处理。</p>
     */
    private final String reviewedAt;

    /**
     * 人工复核意见。
     *
     * <p>为 {@code null} 表示未填写。含人工主观评价，不应参与 {@code toString()}。</p>
     */
    private final String reviewComment;

    /**
     * 人工确认发布后生成的文章唯一键。
     *
     * <p>为 {@code null} 表示尚未发布。</p>
     */
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
}
