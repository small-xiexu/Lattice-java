package com.xbk.lattice.source.domain;

import java.time.OffsetDateTime;

/**
 * 资料源
 *
 * 职责：表示系统中的单个知识资料源主记录
 *
 * @author xiexu
 */
public class KnowledgeSource {

    private final Long id;

    private final String sourceCode;

    private final String name;

    private final String sourceType;

    private final String contentProfile;

    private final String status;

    private final String visibility;

    private final String defaultSyncMode;

    private final String configJson;

    private final String metadataJson;

    private final String latestManifestHash;

    private final Long lastSyncRunId;

    private final String lastSyncStatus;

    private final OffsetDateTime lastSyncAt;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建资料源。
     *
     * @param id 主键
     * @param sourceCode 资料源编码
     * @param name 名称
     * @param sourceType 资料源类型
     * @param contentProfile 内容画像
     * @param status 状态
     * @param visibility 可见性
     * @param defaultSyncMode 默认同步模式
     * @param configJson 配置 JSON
     * @param metadataJson 元数据 JSON
     * @param latestManifestHash 最近成功 manifest
     * @param lastSyncRunId 最近同步运行
     * @param lastSyncStatus 最近同步状态
     * @param lastSyncAt 最近同步时间
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public KnowledgeSource(
            Long id,
            String sourceCode,
            String name,
            String sourceType,
            String contentProfile,
            String status,
            String visibility,
            String defaultSyncMode,
            String configJson,
            String metadataJson,
            String latestManifestHash,
            Long lastSyncRunId,
            String lastSyncStatus,
            OffsetDateTime lastSyncAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.sourceCode = sourceCode;
        this.name = name;
        this.sourceType = sourceType;
        this.contentProfile = contentProfile;
        this.status = status;
        this.visibility = visibility;
        this.defaultSyncMode = defaultSyncMode;
        this.configJson = configJson;
        this.metadataJson = metadataJson;
        this.latestManifestHash = latestManifestHash;
        this.lastSyncRunId = lastSyncRunId;
        this.lastSyncStatus = lastSyncStatus;
        this.lastSyncAt = lastSyncAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getName() {
        return name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getContentProfile() {
        return contentProfile;
    }

    public String getStatus() {
        return status;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getDefaultSyncMode() {
        return defaultSyncMode;
    }

    public String getConfigJson() {
        return configJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public String getLatestManifestHash() {
        return latestManifestHash;
    }

    public Long getLastSyncRunId() {
        return lastSyncRunId;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
