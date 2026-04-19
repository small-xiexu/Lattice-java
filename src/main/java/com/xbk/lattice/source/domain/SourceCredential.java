package com.xbk.lattice.source.domain;

import java.time.OffsetDateTime;

/**
 * 资料源凭据。
 *
 * 职责：表示 source_credentials 中的单条密钥配置
 *
 * @author xiexu
 */
public class SourceCredential {

    private final Long id;

    private final String credentialCode;

    private final String credentialType;

    private final String secretCiphertext;

    private final String secretMask;

    private final boolean enabled;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建资料源凭据。
     *
     * @param id 主键
     * @param credentialCode 凭据编码
     * @param credentialType 凭据类型
     * @param secretCiphertext 密文
     * @param secretMask 脱敏值
     * @param enabled 是否启用
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public SourceCredential(
            Long id,
            String credentialCode,
            String credentialType,
            String secretCiphertext,
            String secretMask,
            boolean enabled,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.credentialCode = credentialCode;
        this.credentialType = credentialType;
        this.secretCiphertext = secretCiphertext;
        this.secretMask = secretMask;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取凭据编码。
     *
     * @return 凭据编码
     */
    public String getCredentialCode() {
        return credentialCode;
    }

    /**
     * 获取凭据类型。
     *
     * @return 凭据类型
     */
    public String getCredentialType() {
        return credentialType;
    }

    /**
     * 获取密文。
     *
     * @return 密文
     */
    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    /**
     * 获取脱敏值。
     *
     * @return 脱敏值
     */
    public String getSecretMask() {
        return secretMask;
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
     * 获取创建人。
     *
     * @return 创建人
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 获取更新人。
     *
     * @return 更新人
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
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
