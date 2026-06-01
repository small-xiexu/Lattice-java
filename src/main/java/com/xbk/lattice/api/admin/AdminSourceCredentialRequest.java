package com.xbk.lattice.api.admin;

/**
 * 资料源凭据请求。
 *
 * <p>承载资料源凭据的创建和更新参数。
 * <b>注意：secret 字段为凭据明文，仅用于入参传输；服务端加密存储，不返回明文。
 * 严格禁止对此类加 {@code @Data} 或任何会在 toString() 中输出 secret 的注解。</b>
 *
 * @author xiexu
 */
public class AdminSourceCredentialRequest {

    /**
     * 凭据唯一编码。
     *
     * <p>对应 source_credentials.credential_code，用于跨环境引用凭据。</p>
     */
    private String credentialCode;

    /**
     * 凭据类型。
     *
     * <p>取值如 SSH_KEY、TOKEN、PASSWORD 等，决定服务端如何加密和使用该凭据。</p>
     */
    private String credentialType;

    /**
     * 凭据明文。
     *
     * <p><b>敏感字段。</b>仅用于创建/更新时的入参传输（经 HTTPS），服务端在
     * {@code SourceCredentialController.saveCredential()} 中加密后存储到
     * source_credentials.secret。API 响应中不会返回此字段明文。</p>
     */
    private String secret;

    /**
     * 更新人。
     *
     * <p>执行本次创建或更新操作的人员标识，用于审计追踪。</p>
     */
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
