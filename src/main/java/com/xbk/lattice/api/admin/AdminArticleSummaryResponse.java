package com.xbk.lattice.api.admin;

/**
 * 管理侧文章摘要响应
 *
 * 职责：承载管理侧文章列表中的单篇文章摘要
 *
 * @author xiexu
 */
public class AdminArticleSummaryResponse {

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String lifecycle;

    private final String reviewStatus;

    private final String compiledAt;

    private final String summary;

    private final int sourceCount;

    private final String primarySourceName;

    /**
     * 创建管理侧文章摘要响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param lifecycle 生命周期
     * @param reviewStatus 审查状态
     * @param compiledAt 编译时间
     * @param summary 摘要
     * @param sourceCount 来源数量
     * @param primarySourceName 首个来源文件名
     */
    public AdminArticleSummaryResponse(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String lifecycle,
            String reviewStatus,
            String compiledAt,
            String summary,
            int sourceCount,
            String primarySourceName
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
        this.reviewStatus = reviewStatus;
        this.compiledAt = compiledAt;
        this.summary = summary;
        this.sourceCount = sourceCount;
        this.primarySourceName = primarySourceName;
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
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
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
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
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
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取编译时间。
     *
     * @return 编译时间
     */
    public String getCompiledAt() {
        return compiledAt;
    }

    /**
     * 获取摘要。
     *
     * @return 摘要
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 获取来源数量。
     *
     * @return 来源数量
     */
    public int getSourceCount() {
        return sourceCount;
    }

    /**
     * 获取首个来源文件名。
     *
     * @return 首个来源文件名
     */
    public String getPrimarySourceName() {
        return primarySourceName;
    }
}
