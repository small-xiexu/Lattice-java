package com.xbk.lattice.infra.persistence;

/**
 * 文章分块记录
 *
 * 职责：承载 article_chunks 的最小读取模型
 *
 * @author xiexu
 */
public class ArticleChunkRecord {

    private final Long id;

    private final Long articleId;

    private final String conceptId;

    private final int chunkIndex;

    private final String chunkText;

    /**
     * 创建文章分块记录。
     *
     * @param id 主键
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param chunkIndex 分块序号
     * @param chunkText 分块文本
     */
    public ArticleChunkRecord(
            Long id,
            Long articleId,
            String conceptId,
            int chunkIndex,
            String chunkText
    ) {
        this.id = id;
        this.articleId = articleId;
        this.conceptId = conceptId;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
    }

    /**
     * 返回主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 返回文章主键。
     *
     * @return 文章主键
     */
    public Long getArticleId() {
        return articleId;
    }

    /**
     * 返回概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 返回分块序号。
     *
     * @return 分块序号
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 返回分块文本。
     *
     * @return 分块文本
     */
    public String getChunkText() {
        return chunkText;
    }
}
