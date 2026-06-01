package com.xbk.lattice.documentparse.domain.model;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 文档解析连接。
 *
 * <p>表示单条 OCR / Document AI 供应商连接配置——含凭证加密存储、脱敏展示和连接元数据。
 * 含敏感字段（{@code credentialCiphertext}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class ProviderConnection {

    public static final String PROVIDER_TENCENT_OCR = "tencent_ocr";
    public static final String PROVIDER_ALIYUN_OCR = "aliyun_ocr";
    public static final String PROVIDER_GOOGLE_DOCUMENT_AI = "google_document_ai";
    public static final String PROVIDER_TEXTIN_XPARSE = "textin_xparse";

    /** 连接主键。 */
    private final Long id;

    /** 连接编码（系统内唯一标识）。 */
    private final String connectionCode;

    /** 供应商类型（如 tencent_ocr / aliyun_ocr）。 */
    private final String providerType;

    /** API 端点 URL。 */
    private final String baseUrl;

    /**
     * 加密后的凭证密文。
     *
     * <p>非明文凭证，但仍属敏感数据——禁止参与 {@code toString()} 或记录到日志。</p>
     */
    private final String credentialCiphertext;

    /**
     * 凭证脱敏展示值。
     *
     * <p>仅用于管理侧脱敏展示（如 {@code "已配置 JSON 凭证"}），非完整凭证。</p>
     */
    private final String credentialMask;

    /** Provider 扩展配置 JSON。可能较大。 */
    private final String configJson;

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

    public ProviderConnection(
            Long id, String connectionCode, String providerType, String baseUrl,
            String credentialCiphertext, String credentialMask, String configJson,
            boolean enabled, String createdBy, String updatedBy,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
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
}
