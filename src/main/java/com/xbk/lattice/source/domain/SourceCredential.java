package com.xbk.lattice.source.domain;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 资料源凭据。
 *
 * <p>表示 source_credentials 中的单条密钥配置——含加密存储的凭证密文和脱敏展示值。
 * 含敏感字段（{@code secretCiphertext}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class SourceCredential {

    /** 凭据主键。 */
    private final Long id;
    /** 凭据编码（系统内唯一标识）。 */
    private final String credentialCode;
    /** 凭据类型（如 git_token / api_key）。 */
    private final String credentialType;
    /**
     * 加密后的凭证密文。
     *
     * <p>非明文凭证，但仍属敏感数据——禁止参与 {@code toString()} 或记录到日志。</p>
     */
    private final String secretCiphertext;
    /**
     * 凭证脱敏展示值。
     *
     * <p>仅用于管理侧脱敏展示，非完整凭证。</p>
     */
    private final String secretMask;
    /** 是否启用。 */
    private final boolean enabled;
    /** 创建人。 */
    private final String createdBy;
    /** 最后更新人。 */
    private final String updatedBy;
    /** 创建时间。 */
    private final OffsetDateTime createdAt;
    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;

    public SourceCredential(
            Long id, String credentialCode, String credentialType, String secretCiphertext,
            String secretMask, boolean enabled, String createdBy, String updatedBy,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
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
}
