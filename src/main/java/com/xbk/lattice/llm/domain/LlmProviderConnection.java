package com.xbk.lattice.llm.domain;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * LLM Provider 连接配置。
 *
 * <p>表示后台维护的单条 Provider 连接记录——含 API 端点、加密密钥和审计信息。
 * 含敏感字段（{@code apiKeyCiphertext}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class LlmProviderConnection {

    /** 连接主键。 */
    private final Long id;
    /** 连接编码（系统内唯一标识）。 */
    private final String connectionCode;
    /** Provider 类型（如 openai / anthropic / local）。 */
    private final String providerType;
    /** Provider API 端点 URL。可能含内部网络路径。 */
    private final String baseUrl;
    /**
     * 加密后的 API Key 密文。
     *
     * <p>非明文密钥，但仍属敏感数据——禁止参与 {@code toString()} 或记录到日志。</p>
     */
    private final String apiKeyCiphertext;
    /**
     * API Key 脱敏展示值（如 {@code sk-****xxxx}）。
     *
     * <p>仅用于管理侧脱敏展示，非完整密钥。</p>
     */
    private final String apiKeyMask;
    /** 是否启用。false 时使用该连接的所有模型不可用。 */
    private final boolean enabled;
    /** 备注。 */
    private final String remarks;
    /** 创建人。 */
    private final String createdBy;
    /** 最后更新人。 */
    private final String updatedBy;
    /** 创建时间。 */
    private final OffsetDateTime createdAt;
    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;

    public LlmProviderConnection(
            Long id, String connectionCode, String providerType, String baseUrl,
            String apiKeyCiphertext, String apiKeyMask, boolean enabled, String remarks,
            String createdBy, String updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
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
}
