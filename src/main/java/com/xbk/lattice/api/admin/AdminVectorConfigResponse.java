package com.xbk.lattice.api.admin;

/**
 * 管理侧向量配置响应
 *
 * 职责：返回当前有效向量配置、profile 摘要、来源与联动提示
 *
 * @author xiexu
 */
public class AdminVectorConfigResponse {

    private final boolean vectorEnabled;

    private final Long embeddingModelProfileId;

    private final String providerType;

    private final String modelName;

    private final Integer profileDimensions;

    private final String configSource;

    private final boolean rebuildRecommended;

    private final String rebuildReason;

    private final String createdBy;

    private final String updatedBy;

    private final String createdAt;

    private final String updatedAt;

    /**
     * 创建管理侧向量配置响应。
     *
     * @param vectorEnabled 是否启用向量检索
     * @param embeddingModelProfileId embedding 模型配置主键
     * @param providerType provider 类型
     * @param modelName 模型名称
     * @param profileDimensions profile 维度
     * @param configSource 配置来源
     * @param rebuildRecommended 是否建议重建向量索引
     * @param rebuildReason 建议原因
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AdminVectorConfigResponse(
            boolean vectorEnabled,
            Long embeddingModelProfileId,
            String providerType,
            String modelName,
            Integer profileDimensions,
            String configSource,
            boolean rebuildRecommended,
            String rebuildReason,
            String createdBy,
            String updatedBy,
            String createdAt,
            String updatedAt
    ) {
        this.vectorEnabled = vectorEnabled;
        this.embeddingModelProfileId = embeddingModelProfileId;
        this.providerType = providerType;
        this.modelName = modelName;
        this.profileDimensions = profileDimensions;
        this.configSource = configSource;
        this.rebuildRecommended = rebuildRecommended;
        this.rebuildReason = rebuildReason;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
     * 返回 provider 类型。
     *
     * @return provider 类型
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * 返回模型名称。
     *
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 返回 profile 维度。
     *
     * @return profile 维度
     */
    public Integer getProfileDimensions() {
        return profileDimensions;
    }

    /**
     * 返回配置来源。
     *
     * @return 配置来源
     */
    public String getConfigSource() {
        return configSource;
    }

    /**
     * 返回是否建议重建向量索引。
     *
     * @return 是否建议重建
     */
    public boolean isRebuildRecommended() {
        return rebuildRecommended;
    }

    /**
     * 返回建议原因。
     *
     * @return 建议原因
     */
    public String getRebuildReason() {
        return rebuildReason;
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
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回更新时间。
     *
     * @return 更新时间
     */
    public String getUpdatedAt() {
        return updatedAt;
    }
}
