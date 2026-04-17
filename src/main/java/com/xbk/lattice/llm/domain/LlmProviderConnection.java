package com.xbk.lattice.llm.domain;

import java.time.OffsetDateTime;

/**
 * LLM Provider 连接配置
 *
 * 职责：表示后台维护的单条 Provider 连接记录
 *
 * @author xiexu
 */
public class LlmProviderConnection {

    private final Long id;

    private final String connectionCode;

    private final String providerType;

    private final String baseUrl;

    private final String apiKeyCiphertext;

    private final String apiKeyMask;

    private final boolean enabled;

    private final String remarks;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建 Provider 连接配置。
     *
     * @param id 主键
     * @param connectionCode 连接编码
     * @param providerType Provider 类型
     * @param baseUrl 基础地址
     * @param apiKeyCiphertext 加密后的 API Key
     * @param apiKeyMask 脱敏展示值
     * @param enabled 是否启用
     * @param remarks 备注
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public LlmProviderConnection(
            Long id,
            String connectionCode,
            String providerType,
            String baseUrl,
            String apiKeyCiphertext,
            String apiKeyMask,
            boolean enabled,
            String remarks,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.connectionCode = connectionCode;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyMask = apiKeyMask;
        this.enabled = enabled;
        this.remarks = remarks;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
     * 返回连接编码。
     *
     * @return 连接编码
     */
    public String getConnectionCode() {
        return connectionCode;
    }

    /**
     * 返回 Provider 类型。
     *
     * @return Provider 类型
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * 返回基础地址。
     *
     * @return 基础地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 返回加密后的 API Key。
     *
     * @return 加密后的 API Key
     */
    public String getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    /**
     * 返回脱敏后的 API Key。
     *
     * @return 脱敏后的 API Key
     */
    public String getApiKeyMask() {
        return apiKeyMask;
    }

    /**
     * 返回是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 返回备注。
     *
     * @return 备注
     */
    public String getRemarks() {
        return remarks;
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
