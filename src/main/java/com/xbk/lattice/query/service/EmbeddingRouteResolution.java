package com.xbk.lattice.query.service;

/**
 * Embedding 路由解析结果
 *
 * 职责：承载一次向量调用所需的 profile、provider、模型与维度信息
 *
 * @author xiexu
 */
public class EmbeddingRouteResolution {

    private final Long profileId;

    private final String providerType;

    private final String baseUrl;

    private final String apiKey;

    private final String modelName;

    private final Integer expectedDimensions;

    private final Integer timeoutSeconds;

    /**
     * 创建 Embedding 路由解析结果。
     *
     * @param profileId profile 主键
     * @param providerType provider 类型
     * @param baseUrl baseUrl
     * @param apiKey 解密后的 API Key
     * @param modelName 模型名称
     * @param expectedDimensions 期望维度
     * @param timeoutSeconds 超时秒数
     */
    public EmbeddingRouteResolution(
            Long profileId,
            String providerType,
            String baseUrl,
            String apiKey,
            String modelName,
            Integer expectedDimensions,
            Integer timeoutSeconds
    ) {
        this.profileId = profileId;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.expectedDimensions = expectedDimensions;
        this.timeoutSeconds = timeoutSeconds;
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
     * 返回 baseUrl。
     *
     * @return baseUrl
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 返回解密后的 API Key。
     *
     * @return API Key
     */
    public String getApiKey() {
        return apiKey;
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

    /**
     * 返回超时秒数。
     *
     * @return 超时秒数
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
