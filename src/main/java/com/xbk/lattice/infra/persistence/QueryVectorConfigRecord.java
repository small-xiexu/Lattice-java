package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * Query 向量配置记录
 *
 * 职责：承载 query 向量开关与 embedding profile 配置
 *
 * @author xiexu
 */
public class QueryVectorConfigRecord {

    private final String configScope;

    private final boolean vectorEnabled;

    private final Long embeddingModelProfileId;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建 Query 向量配置记录。
     *
     * @param configScope 配置作用域
     * @param vectorEnabled 是否启用向量检索
     * @param embeddingModelProfileId embedding 模型配置主键
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public QueryVectorConfigRecord(
            String configScope,
            boolean vectorEnabled,
            Long embeddingModelProfileId,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.configScope = configScope;
        this.vectorEnabled = vectorEnabled;
        this.embeddingModelProfileId = embeddingModelProfileId;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 返回配置作用域。
     *
     * @return 配置作用域
     */
    public String getConfigScope() {
        return configScope;
    }

    /**
     * 返回是否启用向量检索。
     *
     * @return 是否启用
     */
    public boolean isVectorEnabled() {
        return vectorEnabled;
    }

    /**
     * 返回 embedding 模型配置主键。
     *
     * @return 模型配置主键
     */
    public Long getEmbeddingModelProfileId() {
        return embeddingModelProfileId;
    }

    /**
     * 返回创建人。
     *
     * @return 创建人
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 返回更新人。
     *
     * @return 更新人
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
