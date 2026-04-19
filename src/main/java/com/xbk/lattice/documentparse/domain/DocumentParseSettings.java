package com.xbk.lattice.documentparse.domain;

import java.time.OffsetDateTime;

/**
 * 文档解析全局设置
 *
 * 职责：表示文档解析配置层的全局开关与默认连接
 *
 * @author xiexu
 */
public class DocumentParseSettings {

    public static final String DEFAULT_SCOPE = "default";

    private final Long id;

    private final String configScope;

    private final Long defaultConnectionId;

    private final boolean imageOcrEnabled;

    private final boolean scannedPdfOcrEnabled;

    private final boolean cleanupEnabled;

    private final Long cleanupModelProfileId;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建文档解析全局设置。
     *
     * @param id 主键
     * @param configScope 配置范围
     * @param defaultConnectionId 默认连接主键
     * @param imageOcrEnabled 图片 OCR 开关
     * @param scannedPdfOcrEnabled 扫描 PDF OCR 开关
     * @param cleanupEnabled 后整理开关
     * @param cleanupModelProfileId 后整理模型主键
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public DocumentParseSettings(
            Long id,
            String configScope,
            Long defaultConnectionId,
            boolean imageOcrEnabled,
            boolean scannedPdfOcrEnabled,
            boolean cleanupEnabled,
            Long cleanupModelProfileId,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.configScope = configScope;
        this.defaultConnectionId = defaultConnectionId;
        this.imageOcrEnabled = imageOcrEnabled;
        this.scannedPdfOcrEnabled = scannedPdfOcrEnabled;
        this.cleanupEnabled = cleanupEnabled;
        this.cleanupModelProfileId = cleanupModelProfileId;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 返回默认空配置。
     *
     * @return 默认空配置
     */
    public static DocumentParseSettings defaultSettings() {
        return new DocumentParseSettings(
                null,
                DEFAULT_SCOPE,
                null,
                false,
                false,
                false,
                null,
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
     * 返回配置范围。
     *
     * @return 配置范围
     */
    public String getConfigScope() {
        return configScope;
    }

    /**
     * 返回默认连接主键。
     *
     * @return 默认连接主键
     */
    public Long getDefaultConnectionId() {
        return defaultConnectionId;
    }

    /**
     * 返回图片 OCR 开关。
     *
     * @return 图片 OCR 开关
     */
    public boolean isImageOcrEnabled() {
        return imageOcrEnabled;
    }

    /**
     * 返回扫描 PDF OCR 开关。
     *
     * @return 扫描 PDF OCR 开关
     */
    public boolean isScannedPdfOcrEnabled() {
        return scannedPdfOcrEnabled;
    }

    /**
     * 返回后整理开关。
     *
     * @return 后整理开关
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
