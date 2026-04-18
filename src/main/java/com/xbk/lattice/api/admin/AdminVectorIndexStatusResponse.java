package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧向量索引状态响应
 *
 * 职责：承载向量能力、配置与当前索引统计信息
 *
 * @author xiexu
 */
public class AdminVectorIndexStatusResponse {

    private final boolean vectorEnabled;

    private final boolean vectorTypeAvailable;

    private final boolean vectorIndexTableAvailable;

    private final boolean indexingAvailable;

    private final Long embeddingModelProfileId;

    private final String configuredProviderType;

    private final String configuredModelName;

    private final int configuredExpectedDimensions;

    private final Integer profileDimensions;

    private final String embeddingColumnType;

    private final Integer schemaDimensions;

    private final Boolean dimensionsMatch;

    private final boolean dimensionsConsistent;

    private final boolean annIndexReady;

    private final String annIndexType;

    private final int articleCount;

    private final int indexedArticleCount;

    private final List<String> indexedModelNames;

    private final String latestUpdatedAt;

    /**
     * 创建管理侧向量索引状态响应。
     *
     * @param vectorEnabled 是否启用向量索引
     * @param vectorTypeAvailable vector 类型是否可用
     * @param vectorIndexTableAvailable 向量索引表是否可用
     * @param indexingAvailable 当前是否可执行向量索引
     * @param embeddingModelProfileId 当前配置的 embedding profile 主键
     * @param configuredProviderType 当前配置的 provider 类型
     * @param configuredModelName 当前配置的 embedding 模型名
     * @param configuredExpectedDimensions 当前配置的期望维度
     * @param profileDimensions 当前 profile 维度
     * @param embeddingColumnType 向量列数据库类型
     * @param schemaDimensions 数据库维度
     * @param dimensionsMatch 配置维度是否与数据库一致
     * @param dimensionsConsistent 维度是否一致
     * @param annIndexReady ANN 索引是否就绪
     * @param annIndexType ANN 索引类型
     * @param articleCount 当前文章总数
     * @param indexedArticleCount 当前向量索引总数
     * @param indexedModelNames 当前索引内出现过的模型名
     * @param latestUpdatedAt 最近更新时间
     */
    public AdminVectorIndexStatusResponse(
            boolean vectorEnabled,
            boolean vectorTypeAvailable,
            boolean vectorIndexTableAvailable,
            boolean indexingAvailable,
            Long embeddingModelProfileId,
            String configuredProviderType,
            String configuredModelName,
            int configuredExpectedDimensions,
            Integer profileDimensions,
            String embeddingColumnType,
            Integer schemaDimensions,
            Boolean dimensionsMatch,
            boolean dimensionsConsistent,
            boolean annIndexReady,
            String annIndexType,
            int articleCount,
            int indexedArticleCount,
            List<String> indexedModelNames,
            String latestUpdatedAt
    ) {
        this.vectorEnabled = vectorEnabled;
        this.vectorTypeAvailable = vectorTypeAvailable;
        this.vectorIndexTableAvailable = vectorIndexTableAvailable;
        this.indexingAvailable = indexingAvailable;
        this.embeddingModelProfileId = embeddingModelProfileId;
        this.configuredProviderType = configuredProviderType;
        this.configuredModelName = configuredModelName;
        this.configuredExpectedDimensions = configuredExpectedDimensions;
        this.profileDimensions = profileDimensions;
        this.embeddingColumnType = embeddingColumnType;
        this.schemaDimensions = schemaDimensions;
        this.dimensionsMatch = dimensionsMatch;
        this.dimensionsConsistent = dimensionsConsistent;
        this.annIndexReady = annIndexReady;
        this.annIndexType = annIndexType;
        this.articleCount = articleCount;
        this.indexedArticleCount = indexedArticleCount;
        this.indexedModelNames = indexedModelNames;
        this.latestUpdatedAt = latestUpdatedAt;
    }

    /**
     * 返回是否启用向量索引。
     *
     * @return 是否启用向量索引
     */
    public boolean isVectorEnabled() {
        return vectorEnabled;
    }

    /**
     * 返回 vector 类型是否可用。
     *
     * @return vector 类型是否可用
     */
    public boolean isVectorTypeAvailable() {
        return vectorTypeAvailable;
    }

    /**
     * 返回向量索引表是否可用。
     *
     * @return 向量索引表是否可用
     */
    public boolean isVectorIndexTableAvailable() {
        return vectorIndexTableAvailable;
    }

    /**
     * 返回当前是否可执行向量索引。
     *
     * @return 当前是否可执行向量索引
     */
    public boolean isIndexingAvailable() {
        return indexingAvailable;
    }

    /**
     * 返回当前配置的 embedding profile 主键。
     *
     * @return embedding profile 主键
     */
    public Long getEmbeddingModelProfileId() {
        return embeddingModelProfileId;
    }

    /**
     * 返回当前配置的 provider 类型。
     *
     * @return provider 类型
     */
    public String getConfiguredProviderType() {
        return configuredProviderType;
    }

    /**
     * 返回当前配置的 embedding 模型名。
     *
     * @return 当前配置的 embedding 模型名
     */
    public String getConfiguredModelName() {
        return configuredModelName;
    }

    /**
     * 返回当前配置的期望维度。
     *
     * @return 当前配置的期望维度
     */
    public int getConfiguredExpectedDimensions() {
        return configuredExpectedDimensions;
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
     * 返回向量列数据库类型。
     *
     * @return 向量列数据库类型
     */
    public String getEmbeddingColumnType() {
        return embeddingColumnType;
    }

    /**
     * 返回数据库维度。
     *
     * @return 数据库维度
     */
    public Integer getSchemaDimensions() {
        return schemaDimensions;
    }

    /**
     * 返回配置维度是否与数据库一致。
     *
     * @return 配置维度是否与数据库一致
     */
    public Boolean getDimensionsMatch() {
        return dimensionsMatch;
    }

    /**
     * 返回维度是否一致。
     *
     * @return 维度是否一致
     */
    public boolean isDimensionsConsistent() {
        return dimensionsConsistent;
    }

    /**
     * 返回 ANN 索引是否就绪。
     *
     * @return ANN 索引是否就绪
     */
    public boolean isAnnIndexReady() {
        return annIndexReady;
    }

    /**
     * 返回 ANN 索引类型。
     *
     * @return ANN 索引类型
     */
    public String getAnnIndexType() {
        return annIndexType;
    }

    /**
     * 返回文章总数。
     *
     * @return 文章总数
     */
    public int getArticleCount() {
        return articleCount;
    }

    /**
     * 返回向量索引总数。
     *
     * @return 向量索引总数
     */
    public int getIndexedArticleCount() {
        return indexedArticleCount;
    }

    /**
     * 返回当前索引内出现过的模型名。
     *
     * @return 当前索引内出现过的模型名
     */
    public List<String> getIndexedModelNames() {
        return indexedModelNames;
    }

    /**
     * 返回最近更新时间。
     *
     * @return 最近更新时间
     */
    public String getLatestUpdatedAt() {
        return latestUpdatedAt;
    }
}
