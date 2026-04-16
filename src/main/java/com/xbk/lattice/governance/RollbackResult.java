package com.xbk.lattice.governance;

import java.time.OffsetDateTime;

/**
 * 快照回滚结果
 *
 * 职责：承载单次 rollback 的恢复目标与恢复时间
 *
 * @author xiexu
 */
public class RollbackResult {

    private final String conceptId;

    private final long restoredSnapshotId;

    private final OffsetDateTime restoredAt;

    /**
     * 创建快照回滚结果。
     *
     * @param conceptId 概念标识
     * @param restoredSnapshotId 恢复来源快照标识
     * @param restoredAt 恢复时间
     */
    public RollbackResult(String conceptId, long restoredSnapshotId, OffsetDateTime restoredAt) {
        this.conceptId = conceptId;
        this.restoredSnapshotId = restoredSnapshotId;
        this.restoredAt = restoredAt;
    }

    public String getConceptId() {
        return conceptId;
    }

    public long getRestoredSnapshotId() {
        return restoredSnapshotId;
    }

    public OffsetDateTime getRestoredAt() {
        return restoredAt;
    }
}
