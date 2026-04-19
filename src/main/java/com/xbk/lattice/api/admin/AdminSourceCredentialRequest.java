package com.xbk.lattice.api.admin;

/**
 * 资料源凭据请求。
 *
 * 职责：承载资料源凭据创建参数
 *
 * @author xiexu
 */
public class AdminSourceCredentialRequest {

    private String credentialCode;

    private String credentialType;

    private String secret;

    private String updatedBy;

    /**
     * 获取凭据编码。
     *
     * @return 凭据编码
     */
    public String getCredentialCode() {
        return credentialCode;
    }

    /**
     * 设置凭据编码。
     *
     * @param credentialCode 凭据编码
     */
    public void setCredentialCode(String credentialCode) {
        this.credentialCode = credentialCode;
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
     * 设置凭据类型。
     *
     * @param credentialType 凭据类型
     */
    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    /**
     * 获取凭据明文。
     *
     * @return 凭据明文
     */
    public String getSecret() {
        return secret;
    }

    /**
     * 设置凭据明文。
     *
     * @param secret 凭据明文
     */
    public void setSecret(String secret) {
        this.secret = secret;
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
     * 设置更新人。
     *
     * @param updatedBy 更新人
     */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
