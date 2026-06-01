package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 资料源凭据响应。
 *
 * <p>对外返回资料源凭据的脱敏视图，由 {@code AdminSourceCredentialController.toResponse()}
 * 从 {@code SourceCredential} 构造。secretMask 为脱敏值而非明文，可安全出现在日志和序列化输出中。
 *
 * @author xiexu
 */
@Getter
public class AdminSourceCredentialResponse {

    /**
     * 主键。
     *
     * <p>对应 source_credentials.id。调用方通过它标识和操作具体的凭据记录。</p>
     */
    private final Long id;

    /**
     * 凭据唯一编码。
     *
     * <p>对应 source_credentials.credential_code，用于跨模块引用凭据。</p>
     */
    private final String credentialCode;

    /**
     * 凭据类型。
     *
     * <p>如 SSH_KEY、TOKEN、PASSWORD 等，调用方据此了解该凭据的用途和认证方式。</p>
     */
    private final String credentialType;

    /**
     * 凭据脱敏值。
     *
     * <p>由 {@code SourceCredential.credentialMask()} 方法生成的脱敏字符串（如 ghp_abc***xyz），
     * 用于后台管理界面展示——让管理员确认凭据存在且类型正确，但不暴露明文。</p>
     */
    private final String secretMask;

    /**
     * 是否启用。
     *
     * <p>对应 source_credentials.enabled。为 false 时该凭据不会被用于源数据拉取。</p>
     */
    private final boolean enabled;

    /**
     * 更新时间。
     *
     * <p>对应 source_credentials.updated_at，用于后台展示凭据的最近维护时间。</p>
     */
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
}
