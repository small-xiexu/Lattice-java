package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 文章向量索引记录
 *
 * 职责：承载文章 embedding 索引的持久化对象
 *
 * @author xiexu
 */
public class ArticleVectorRecord {

    private final String conceptId;

    private final String modelName;

    private final String contentHash;

    private final float[] embedding;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文章向量索引记录。
     *
     * @param conceptId 概念标识
     * @param modelName 模型名称
     * @param contentHash 内容哈希
     * @param embedding embedding 向量
     * @param updatedAt 更新时间
     */
    public ArticleVectorRecord(
            String conceptId,
            String modelName,
            String contentHash,
            float[] embedding,
            OffsetDateTime updatedAt
    ) {
        this.conceptId = conceptId;
        this.modelName = modelName;
        this.contentHash = contentHash;
        this.embedding = embedding;
        this.updatedAt = updatedAt;
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
     * 获取模型名称。
     *
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取内容哈希。
     *
     * @return 内容哈希
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * 获取 embedding 向量。
     *
     * @return embedding 向量
     */
    public float[] getEmbedding() {
        return embedding;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
