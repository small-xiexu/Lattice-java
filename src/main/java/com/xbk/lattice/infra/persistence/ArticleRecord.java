package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.time.OffsetDateTime;

/**
 * 文章记录
 *
 * 职责：表示最小文章落盘对象
 *
 * @author xiexu
 */
public class ArticleRecord {

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String lifecycle;

    private final OffsetDateTime compiledAt;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    private final List<String> sourcePaths;

    private final String metadataJson;

    private final String summary;

    private final List<String> referentialKeywords;

    private final List<String> dependsOn;

    private final List<String> related;

    private final String confidence;

    private final String reviewStatus;

    /**
     * 创建文章记录。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     */
    public ArticleRecord(
            String conceptId,
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson
    ) {
        this(
                null,
                conceptId,
                conceptId,
                title,
                content,
                lifecycle,
                compiledAt,
                sourcePaths,
                metadataJson,
                "",
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                "medium",
                "pending",
                null,
                null
        );
    }

    /**
     * 创建文章记录。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param summary 摘要
     * @param referentialKeywords 明确性关键词
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     */
    public ArticleRecord(
            String conceptId,
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson,
            String summary,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String confidence,
            String reviewStatus
    ) {
        this(
                null,
                conceptId,
                conceptId,
                title,
                content,
                lifecycle,
                compiledAt,
                sourcePaths,
                metadataJson,
                summary,
                referentialKeywords,
                dependsOn,
                related,
                confidence,
                reviewStatus,
                null,
                null
        );
    }

    /**
     * 创建文章记录。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param summary 摘要
     * @param referentialKeywords 明确性关键词
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     */
    public ArticleRecord(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson,
            String summary,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String confidence,
            String reviewStatus
    ) {
        this(
                sourceId,
                articleKey,
                conceptId,
                title,
                content,
                lifecycle,
                compiledAt,
                sourcePaths,
                metadataJson,
                summary,
                referentialKeywords,
                dependsOn,
                related,
                confidence,
                reviewStatus,
                null,
                null
        );
    }

    /**
     * 创建文章记录。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param summary 摘要
     * @param referentialKeywords 明确性关键词
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param createdAt 首次入库时间
     * @param updatedAt 最近入库时间
     */
    @JsonCreator
    public ArticleRecord(
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("lifecycle") String lifecycle,
            @JsonProperty("compiledAt") OffsetDateTime compiledAt,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("metadataJson") String metadataJson,
            @JsonProperty("summary") String summary,
            @JsonProperty("referentialKeywords") List<String> referentialKeywords,
            @JsonProperty("dependsOn") List<String> dependsOn,
            @JsonProperty("related") List<String> related,
            @JsonProperty("confidence") String confidence,
            @JsonProperty("reviewStatus") String reviewStatus,
            @JsonProperty("createdAt") OffsetDateTime createdAt,
            @JsonProperty("updatedAt") OffsetDateTime updatedAt
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.lifecycle = lifecycle;
        this.compiledAt = compiledAt;
        this.sourcePaths = sourcePaths;
        this.metadataJson = metadataJson;
        this.summary = summary;
        this.referentialKeywords = referentialKeywords;
        this.dependsOn = dependsOn;
        this.related = related;
        this.confidence = confidence;
        this.reviewStatus = reviewStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建文章记录。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     */
    public ArticleRecord(String conceptId, String title, String content, String lifecycle, OffsetDateTime compiledAt) {
        this(
                null,
                conceptId,
                conceptId,
                title,
                content,
                lifecycle,
                compiledAt,
                Collections.<String>emptyList(),
                "{}",
                "",
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                "medium",
                "pending",
                null,
                null
        );
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
     * 获取内容。
     *
     * @return 内容
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
     * 获取首次入库时间。
     *
     * @return 首次入库时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最近入库时间。
     *
     * @return 最近入库时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
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
     * 获取摘要。
     *
     * @return 摘要
     */
    public String getSummary() {
        return summary;
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
     * 获取依赖概念。
     *
     * @return 依赖概念
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * 获取相关概念。
     *
     * @return 相关概念
     */
    public List<String> getRelated() {
        return related;
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
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 基于当前文章复制一份保留 source-aware 标识的新记录。
     *
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param summary 摘要
     * @param referentialKeywords 明确性关键词
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @return 新文章记录
     */
    public ArticleRecord copy(
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson,
            String summary,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String confidence,
            String reviewStatus
    ) {
        return new ArticleRecord(
                sourceId,
                articleKey,
                conceptId,
                title,
                content,
                lifecycle,
                compiledAt,
                sourcePaths,
                metadataJson,
                summary,
                referentialKeywords,
                dependsOn,
                related,
                confidence,
                reviewStatus,
                createdAt,
                updatedAt
        );
    }
}
