package com.xbk.lattice.documentparse.domain.model;

import java.time.OffsetDateTime;

/**
 * 文档解析连接
 *
 * 职责：表示单条 OCR / Document AI 供应商连接配置
 *
 * @author xiexu
 */
public class ProviderConnection {

    public static final String PROVIDER_TENCENT_OCR = "tencent_ocr";

    public static final String PROVIDER_ALIYUN_OCR = "aliyun_ocr";

    public static final String PROVIDER_GOOGLE_DOCUMENT_AI = "google_document_ai";

    public static final String PROVIDER_TEXTIN_XPARSE = "textin_xparse";

    private final Long id;

    private final String connectionCode;

    private final String providerType;

    private final String baseUrl;

    private final String credentialCiphertext;

    private final String credentialMask;

    private final String configJson;

    private final boolean enabled;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文档解析连接。
     *
     * @param id 主键
     * @param connectionCode 连接编码
     * @param providerType 供应商类型
     * @param baseUrl 基础地址
     * @param credentialCiphertext 加密后的凭证
     * @param credentialMask 凭证脱敏值
     * @param configJson 配置 JSON
     * @param enabled 是否启用
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public ProviderConnection(
            Long id,
            String connectionCode,
            String providerType,
            String baseUrl,
            String credentialCiphertext,
            String credentialMask,
            String configJson,
            boolean enabled,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.connectionCode = connectionCode;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.credentialCiphertext = credentialCiphertext;
        this.credentialMask = credentialMask;
        this.configJson = configJson;
        this.enabled = enabled;
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
     * 返回供应商类型。
     *
     * @return 供应商类型
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
     * 返回加密后的凭证。
     *
     * @return 加密后的凭证
     */
    public String getCredentialCiphertext() {
        return credentialCiphertext;
    }

    /**
     * 返回凭证脱敏值。
     *
     * @return 凭证脱敏值
     */
    public String getCredentialMask() {
        return credentialMask;
    }

    /**
     * 返回配置 JSON。
     *
     * @return 配置 JSON
     */
    public String getConfigJson() {
        return configJson;
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
