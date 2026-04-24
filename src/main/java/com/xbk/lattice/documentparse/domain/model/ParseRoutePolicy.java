package com.xbk.lattice.documentparse.domain.model;

import java.time.OffsetDateTime;

/**
 * 文档解析路由策略
 *
 * 职责：定义图片 OCR、扫描 PDF OCR 与后整理能力的默认路由
 *
 * @author xiexu
 */
public class ParseRoutePolicy {

    public static final String DEFAULT_SCOPE = "default";

    private final Long id;

    private final String policyScope;

    private final Long imageConnectionId;

    private final Long scannedPdfConnectionId;

    private final boolean cleanupEnabled;

    private final Long cleanupModelProfileId;

    private final String fallbackPolicyJson;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文档解析路由策略。
     *
     * @param id 主键
     * @param policyScope 策略作用域
     * @param imageConnectionId 图片 OCR 默认连接
     * @param scannedPdfConnectionId 扫描 PDF OCR 默认连接
     * @param cleanupEnabled 是否启用后整理
     * @param cleanupModelProfileId 后整理模型主键
     * @param fallbackPolicyJson 降级策略 JSON
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public ParseRoutePolicy(
            Long id,
            String policyScope,
            Long imageConnectionId,
            Long scannedPdfConnectionId,
            boolean cleanupEnabled,
            Long cleanupModelProfileId,
            String fallbackPolicyJson,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
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

    /**
     * 返回默认空策略。
     *
     * @return 默认空策略
     */
    public static ParseRoutePolicy defaultPolicy() {
        return new ParseRoutePolicy(
                null,
                DEFAULT_SCOPE,
                null,
                null,
                false,
                null,
                "{}",
                "system",
                "system",
                null,
                null
        );
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
     * 返回策略作用域。
     *
     * @return 策略作用域
     */
    public String getPolicyScope() {
        return policyScope;
    }

    /**
     * 返回图片 OCR 默认连接。
     *
     * @return 图片 OCR 默认连接
     */
    public Long getImageConnectionId() {
        return imageConnectionId;
    }

    /**
     * 返回扫描 PDF OCR 默认连接。
     *
     * @return 扫描 PDF OCR 默认连接
     */
    public Long getScannedPdfConnectionId() {
        return scannedPdfConnectionId;
    }

    /**
     * 返回是否启用后整理。
     *
     * @return 是否启用后整理
     */
    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    /**
     * 返回后整理模型主键。
     *
     * @return 后整理模型主键
     */
    public Long getCleanupModelProfileId() {
        return cleanupModelProfileId;
    }

    /**
     * 返回降级策略 JSON。
     *
     * @return 降级策略 JSON
     */
    public String getFallbackPolicyJson() {
        return fallbackPolicyJson;
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
