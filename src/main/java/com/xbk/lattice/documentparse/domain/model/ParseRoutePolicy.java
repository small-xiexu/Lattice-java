package com.xbk.lattice.documentparse.domain.model;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 文档解析路由策略。
 *
 * <p>定义图片 OCR、扫描 PDF OCR 与后整理能力的默认路由——确定各文档类型对应的解析连接和清理模型。
 *
 * @author xiexu
 */
@Getter
public class ParseRoutePolicy {

    public static final String DEFAULT_SCOPE = "default";

    /** 策略主键。为 null 时表示默认空策略。 */
    private final Long id;

    /** 策略作用域标识（固定为 {@code "default"}）。 */
    private final String policyScope;

    /** 图片 OCR 的默认解析连接主键。为 null 时不对图片做 OCR 路由。 */
    private final Long imageConnectionId;

    /** 扫描 PDF OCR 的默认解析连接主键。为 null 时不对扫描 PDF 做 OCR 路由。 */
    private final Long scannedPdfConnectionId;

    /** 是否启用后整理（cleanup）步骤。 */
    private final boolean cleanupEnabled;

    /** 后整理使用的 LLM 模型配置主键。仅 cleanupEnabled=true 时生效。 */
    private final Long cleanupModelProfileId;

    /** 降级路由规则 JSON。为空 {@code "{}"} 时不执行降级路由。 */
    private final String fallbackPolicyJson;

    /** 创建人。 */
    private final String createdBy;

    /** 最后更新人。 */
    private final String updatedBy;

    /** 创建时间。为 null 时表示尚未持久化。 */
    private final OffsetDateTime createdAt;

    /** 最后更新时间。为 null 时表示尚未持久化。 */
    private final OffsetDateTime updatedAt;

    public ParseRoutePolicy(
            Long id, String policyScope, Long imageConnectionId, Long scannedPdfConnectionId,
            boolean cleanupEnabled, Long cleanupModelProfileId, String fallbackPolicyJson,
            String createdBy, String updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.policyScope = policyScope;
        this.imageConnectionId = imageConnectionId;
        this.scannedPdfConnectionId = scannedPdfConnectionId;
        this.cleanupEnabled = cleanupEnabled;
        this.cleanupModelProfileId = cleanupModelProfileId;
        this.fallbackPolicyJson = fallbackPolicyJson;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ParseRoutePolicy defaultPolicy() {
        return new ParseRoutePolicy(null, DEFAULT_SCOPE, null, null, false, null, "{}", "system", "system", null, null);
    }
}
