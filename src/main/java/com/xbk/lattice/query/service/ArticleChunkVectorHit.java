package com.xbk.lattice.query.service;

import java.util.List;

/**
 * 分块向量命中
 *
 * 职责：承载 chunk 级向量召回后用于聚合的中间结果
 *
 * @author xiexu
 */
public class ArticleChunkVectorHit {

    private final Long articleId;

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String metadataJson;

    private final String reviewStatus;

    private final List<String> sourcePaths;

    private final int chunkIndex;

    private final String chunkText;

    private final double score;

    /**
     * 创建分块向量命中。
     *
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param metadataJson 元数据
     * @param sourcePaths 来源路径
     * @param chunkIndex 分块序号
     * @param chunkText 分块文本
     * @param score 评分
     */
    public ArticleChunkVectorHit(
            Long articleId,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            int chunkIndex,
            String chunkText,
            double score
    ) {
        this(articleId, conceptId, title, content, metadataJson, null, sourcePaths, chunkIndex, chunkText, score);
    }

    /**
     * 创建分块向量命中。
     *
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param metadataJson 元数据
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param chunkIndex 分块序号
     * @param chunkText 分块文本
     * @param score 评分
     */
    public ArticleChunkVectorHit(
            Long articleId,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            int chunkIndex,
            String chunkText,
            double score
    ) {
        this(articleId, null, null, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, chunkIndex, chunkText, score);
    }

    /**
     * 创建分块向量命中。
     *
     * @param articleId 文章主键
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param metadataJson 元数据
     * @param sourcePaths 来源路径
     * @param chunkIndex 分块序号
     * @param chunkText 分块文本
     * @param score 评分
     */
    public ArticleChunkVectorHit(
            Long articleId,
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            int chunkIndex,
            String chunkText,
            double score
    ) {
        this.articleId = articleId;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.sourcePaths = sourcePaths;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.score = score;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getArticleKey() {
        return articleKey;
    }

    public String getConceptId() {
        return conceptId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public double getScore() {
        return score;
    }
}
