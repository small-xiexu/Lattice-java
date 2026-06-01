package com.xbk.lattice.source.domain;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 资料源。
 *
 * <p>表示系统中的单个知识资料源主记录——含标识、类型、生命周期状态、同步上下文和配置。
 * 为不可变领域对象，由 service 层通过构造器创建后不再修改。
 *
 * @author xiexu
 */
@Getter
public class KnowledgeSource {

    /** 资料源主键。 */
    private final Long id;
    /** 资料源编码（系统内唯一标识）。 */
    private final String sourceCode;
    /** 资料源名称。 */
    private final String name;
    /** 资料源类型（UPLOAD / GIT）。 */
    private final String sourceType;
    /** 内容画像（如 code / document / mixed）。 */
    private final String contentProfile;
    /** 生命周期状态（ACTIVE / DISABLED / ARCHIVED）。 */
    private final String status;
    /** 可见性（NORMAL / ADMIN_ONLY）。 */
    private final String visibility;
    /** 默认同步模式（AUTO / FULL / INCREMENTAL）。 */
    private final String defaultSyncMode;
    /** 资料源配置 JSON。可能含 repo 路径、Vault 引用等，可能较大。 */
    private final String configJson;
    /** 扩展元数据 JSON（含 bundleSummary 等）。可能较大。 */
    private final String metadataJson;
    /** 最近成功同步的 manifest 哈希。用于检测输入变更。 */
    private final String latestManifestHash;
    /** 最近一次同步运行主键。为 null 表示从未同步。 */
    private final Long lastSyncRunId;
    /** 最近一次同步状态。为 null 表示从未同步。 */
    private final String lastSyncStatus;
    /** 最近一次同步时间。为 null 表示从未同步。 */
    private final OffsetDateTime lastSyncAt;
    /** 创建时间。 */
    private final OffsetDateTime createdAt;
    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;

    public KnowledgeSource(
            Long id, String sourceCode, String name, String sourceType, String contentProfile,
            String status, String visibility, String defaultSyncMode, String configJson,
            String metadataJson, String latestManifestHash, Long lastSyncRunId,
            String lastSyncStatus, OffsetDateTime lastSyncAt, OffsetDateTime createdAt, OffsetDateTime updatedAt
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
}
