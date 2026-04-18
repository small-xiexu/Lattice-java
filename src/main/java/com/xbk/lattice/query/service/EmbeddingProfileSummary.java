package com.xbk.lattice.query.service;

/**
 * Embedding profile 摘要
 *
 * 职责：承载后台向量配置展示所需的 provider、模型名与维度信息
 *
 * @author xiexu
 */
public class EmbeddingProfileSummary {

    private final Long profileId;

    private final String providerType;

    private final String modelName;

    private final Integer expectedDimensions;

    /**
     * 创建 Embedding profile 摘要。
     *
     * @param profileId profile 主键
     * @param providerType provider 类型
     * @param modelName 模型名称
     * @param expectedDimensions 期望维度
     */
    public EmbeddingProfileSummary(
            Long profileId,
            String providerType,
            String modelName,
            Integer expectedDimensions
    ) {
        this.profileId = profileId;
        this.providerType = providerType;
        this.modelName = modelName;
        this.expectedDimensions = expectedDimensions;
    }

    /**
     * 返回 profile 主键。
     *
     * @return profile 主键
     */
    public Long getProfileId() {
        return profileId;
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
     * 返回期望维度。
     *
     * @return 期望维度
     */
    public Integer getExpectedDimensions() {
        return expectedDimensions;
    }
}
