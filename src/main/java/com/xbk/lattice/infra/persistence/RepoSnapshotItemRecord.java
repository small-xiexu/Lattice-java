package com.xbk.lattice.infra.persistence;

/**
 * 整库快照明细记录
 *
 * 职责：表示整库级 snapshot 中的单条实体快照内容
 *
 * @author xiexu
 */
public class RepoSnapshotItemRecord {

    private final long id;

    private final long snapshotId;

    private final String entityType;

    private final String entityId;

    private final String contentHash;

    private final String payloadJson;

    /**
     * 创建整库快照明细记录。
     *
     * @param id 主键
     * @param snapshotId 快照标识
     * @param entityType 实体类型
     * @param entityId 实体标识
     * @param contentHash 内容哈希
     * @param payloadJson JSON 载荷
     */
    public RepoSnapshotItemRecord(
            long id,
            long snapshotId,
            String entityType,
            String entityId,
            String contentHash,
            String payloadJson
    ) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.contentHash = contentHash;
        this.payloadJson = payloadJson;
    }

    public long getId() {
        return id;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}
