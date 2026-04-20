package com.xbk.lattice.api.admin;

/**
 * 资料源凭据响应。
 *
 * 职责：对外返回资料源凭据的脱敏视图
 *
 * @author xiexu
 */
public class AdminSourceCredentialResponse {

    private final Long id;

    private final String credentialCode;

    private final String credentialType;

    private final String secretMask;

    private final boolean enabled;

    private final String updatedAt;

    /**
     * 创建资料源凭据响应。
     *
     * @param id 主键
     * @param credentialCode 凭据编码
     * @param credentialType 凭据类型
     * @param secretMask 脱敏值
     * @param enabled 是否启用
     * @param updatedAt 更新时间
     */
    public AdminSourceCredentialResponse(
            Long id,
            String credentialCode,
            String credentialType,
            String secretMask,
            boolean enabled,
            String updatedAt
    ) {
        this.id = id;
        this.credentialCode = credentialCode;
        this.credentialType = credentialType;
        this.secretMask = secretMask;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCredentialCode() {
        return credentialCode;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public String getSecretMask() {
        return secretMask;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
