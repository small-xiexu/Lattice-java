package com.xbk.lattice.query.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询文章命中
 *
 * 职责：承载查询阶段需要的文章与评分信息
 *
 * @author xiexu
 */
public class QueryArticleHit {

    private final QueryEvidenceType evidenceType;

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String metadataJson;

    private final String reviewStatus;

    private final List<String> sourcePaths;

    private final double score;

    /**
     * 创建查询文章命中。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            double score
    ) {
        this(QueryEvidenceType.ARTICLE, null, null, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, score);
    }

    /**
     * 创建查询命中。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            QueryEvidenceType evidenceType,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询命中。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            QueryEvidenceType evidenceType,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, null, null, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            QueryEvidenceType evidenceType,
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, sourceId, articleKey, conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param evidenceType 证据类型
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(sourceId, articleKey, conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            double score
    ) {
        this(QueryEvidenceType.ARTICLE, sourceId, articleKey, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, score);
    }

    /**
     * 创建查询命中。
     *
     * @param evidenceType 证据类型
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    @JsonCreator
    public QueryArticleHit(
            @JsonProperty("evidenceType") QueryEvidenceType evidenceType,
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("metadataJson") String metadataJson,
            @JsonProperty("reviewStatus") String reviewStatus,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("score") double score
    ) {
        this.evidenceType = evidenceType;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.sourcePaths = sourcePaths;
        this.score = score;
    }

    /**
     * 获取证据类型。
     *
     * @return 证据类型
     */
    public QueryEvidenceType getEvidenceType() {
        return evidenceType;
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
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 获取文章审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
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
     * 获取评分。
     *
     * @return 评分
     */
    public double getScore() {
        return score;
    }
}
