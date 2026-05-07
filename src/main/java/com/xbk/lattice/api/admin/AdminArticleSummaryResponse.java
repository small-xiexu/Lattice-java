package com.xbk.lattice.api.admin;

import java.util.List;

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

    private final String riskLevel;

    private final List<String> riskReasons;

    private final boolean hotspot;

    private final boolean requiresResultVerification;

    private final String compiledAt;

    private final String createdAt;

    private final String updatedAt;

    private final String summary;

    private final int sourceCount;

    private final String primarySourcePath;

    private final List<String> sourcePaths;

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
     * @param riskLevel 风险等级
     * @param riskReasons 风险原因
     * @param hotspot 是否热点
     * @param requiresResultVerification 是否需要结果抽检
     * @param compiledAt 编译时间
     * @param createdAt 首次入库时间
     * @param updatedAt 最近入库时间
     * @param summary 摘要
     * @param sourceCount 来源数量
     * @param primarySourcePath 首个来源路径
     * @param sourcePaths 完整来源路径列表
     * @param primarySourceName 首个来源文件名
     */
    public AdminArticleSummaryResponse(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String lifecycle,
            String reviewStatus,
            String riskLevel,
            List<String> riskReasons,
            boolean hotspot,
            boolean requiresResultVerification,
            String compiledAt,
            String createdAt,
            String updatedAt,
            String summary,
            int sourceCount,
            String primarySourcePath,
            List<String> sourcePaths,
            String primarySourceName
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
        this.reviewStatus = reviewStatus;
        this.riskLevel = riskLevel;
        this.riskReasons = riskReasons;
        this.hotspot = hotspot;
        this.requiresResultVerification = requiresResultVerification;
        this.compiledAt = compiledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.summary = summary;
        this.sourceCount = sourceCount;
        this.primarySourcePath = primarySourcePath;
        this.sourcePaths = sourcePaths;
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
     * 获取风险等级。
     *
     * @return 风险等级
     */
    public String getRiskLevel() {
        return riskLevel;
    }

    /**
     * 获取风险原因。
     *
     * @return 风险原因
     */
    public List<String> getRiskReasons() {
        return riskReasons;
    }

    /**
     * 获取是否热点内容。
     *
     * @return 是否热点内容
     */
    public boolean getIsHotspot() {
        return hotspot;
    }

    /**
     * 获取是否需要结果抽检。
     *
     * @return 是否需要结果抽检
     */
    public boolean getRequiresResultVerification() {
        return requiresResultVerification;
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
     * 获取首次入库时间。
     *
     * @return 首次入库时间
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最近入库时间。
     *
     * @return 最近入库时间
     */
    public String getUpdatedAt() {
        return updatedAt;
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
     * 获取首个来源路径。
     *
     * @return 首个来源路径
     */
    public String getPrimarySourcePath() {
        return primarySourcePath;
    }

    /**
     * 获取完整来源路径列表。
     *
     * @return 完整来源路径列表
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
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
