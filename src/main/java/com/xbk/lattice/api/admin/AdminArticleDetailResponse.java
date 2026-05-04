package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧文章详情响应
 *
 * 职责：承载管理侧文章详情展示内容
 *
 * @author xiexu
 */
public class AdminArticleDetailResponse {

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String lifecycle;

    private final String compiledAt;

    private final String createdAt;

    private final String updatedAt;

    private final String summary;

    private final String reviewStatus;

    private final String confidence;

    private final int sourceCount;

    private final String primarySourcePath;

    private final List<String> sourcePaths;

    private final List<String> referentialKeywords;

    private final List<String> dependsOn;

    private final List<String> related;

    private final String metadataJson;

    /**
     * 创建管理侧文章详情响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param createdAt 首次入库时间
     * @param updatedAt 最近入库时间
     * @param summary 摘要
     * @param reviewStatus 审查状态
     * @param confidence 置信度
     * @param sourceCount 来源数量
     * @param primarySourcePath 首个来源路径
     * @param sourcePaths 来源路径
     * @param referentialKeywords 明确性关键词
     * @param dependsOn 依赖关系
     * @param related 相关关系
     * @param metadataJson 元数据 JSON
     */
    public AdminArticleDetailResponse(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String lifecycle,
            String compiledAt,
            String createdAt,
            String updatedAt,
            String summary,
            String reviewStatus,
            String confidence,
            int sourceCount,
            String primarySourcePath,
            List<String> sourcePaths,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String metadataJson
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.lifecycle = lifecycle;
        this.compiledAt = compiledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.summary = summary;
        this.reviewStatus = reviewStatus;
        this.confidence = confidence;
        this.sourceCount = sourceCount;
        this.primarySourcePath = primarySourcePath;
        this.sourcePaths = sourcePaths;
        this.referentialKeywords = referentialKeywords;
        this.dependsOn = dependsOn;
        this.related = related;
        this.metadataJson = metadataJson;
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
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取置信度。
     *
     * @return 置信度
     */
    public String getConfidence() {
        return confidence;
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
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取明确性关键词。
     *
     * @return 明确性关键词
     */
    public List<String> getReferentialKeywords() {
        return referentialKeywords;
    }

    /**
     * 获取依赖关系。
     *
     * @return 依赖关系
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * 获取相关关系。
     *
     * @return 相关关系
     */
    public List<String> getRelated() {
        return related;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }
}
