package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 文章分块向量索引记录
 *
 * 职责：承载 article_chunk_vector_index 的持久化对象
 *
 * @author xiexu
 */
public class ArticleChunkVectorRecord {

    private final Long articleChunkId;

    private final Long articleId;

    private final String conceptId;

    private final int chunkIndex;

    private final Long modelProfileId;

    private final String contentHash;

    private final float[] embedding;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文章分块向量索引记录。
     *
     * @param articleChunkId 分块主键
     * @param articleId 文章主键
     * @param conceptId 概念标识
     * @param chunkIndex 分块序号
     * @param modelProfileId 模型配置主键
     * @param contentHash 内容哈希
     * @param embedding 向量
     * @param updatedAt 更新时间
     */
    public ArticleChunkVectorRecord(
            Long articleChunkId,
            Long articleId,
            String conceptId,
            int chunkIndex,
            Long modelProfileId,
            String contentHash,
            float[] embedding,
            OffsetDateTime updatedAt
    ) {
        this.articleChunkId = articleChunkId;
        this.articleId = articleId;
        this.conceptId = conceptId;
        this.chunkIndex = chunkIndex;
        this.modelProfileId = modelProfileId;
        this.contentHash = contentHash;
        this.embedding = embedding;
        this.updatedAt = updatedAt;
    }

    public Long getArticleChunkId() {
        return articleChunkId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public String getConceptId() {
        return conceptId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public Long getModelProfileId() {
        return modelProfileId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
