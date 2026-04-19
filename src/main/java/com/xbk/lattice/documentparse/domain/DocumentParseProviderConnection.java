package com.xbk.lattice.documentparse.domain;

import java.time.OffsetDateTime;

/**
 * 文档解析供应商连接配置
 *
 * 职责：表示后台维护的单条文档解析连接记录
 *
 * @author xiexu
 */
public class DocumentParseProviderConnection {

    public static final String PROVIDER_TENCENT_OCR = "tencent_ocr";

    public static final String PROVIDER_ALIYUN_OCR = "aliyun_ocr";

    public static final String PROVIDER_GOOGLE_DOCUMENT_AI = "google_document_ai";

    private final Long id;

    private final String connectionCode;

    private final String providerType;

    private final String baseUrl;

    private final String endpointPath;

    private final String credentialCiphertext;

    private final String credentialMask;

    private final String extraConfigJson;

    private final boolean enabled;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文档解析连接配置。
     *
     * @param id 主键
     * @param connectionCode 连接编码
     * @param providerType 供应商类型
     * @param baseUrl 基础地址
     * @param endpointPath 接口路径
     * @param credentialCiphertext 加密后的访问凭证
     * @param credentialMask 凭证脱敏值
     * @param extraConfigJson 扩展配置
     * @param enabled 是否启用
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public DocumentParseProviderConnection(
            Long id,
            String connectionCode,
            String providerType,
            String baseUrl,
            String endpointPath,
            String credentialCiphertext,
            String credentialMask,
            String extraConfigJson,
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
        this.endpointPath = endpointPath;
        this.credentialCiphertext = credentialCiphertext;
        this.credentialMask = credentialMask;
        this.extraConfigJson = extraConfigJson;
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
     * 返回接口路径。
     *
     * @return 接口路径
     */
    public String getEndpointPath() {
        return endpointPath;
    }

    /**
     * 返回加密后的访问凭证。
     *
     * @return 加密后的访问凭证
     */
    public String getCredentialCiphertext() {
        return credentialCiphertext;
    }

    /**
     * 返回脱敏后的访问凭证。
     *
     * @return 脱敏后的访问凭证
     */
    public String getCredentialMask() {
        return credentialMask;
    }

    /**
     * 返回扩展配置。
     *
     * @return 扩展配置
     */
    public String getExtraConfigJson() {
        return extraConfigJson;
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
