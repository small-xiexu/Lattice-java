package com.xbk.lattice.governance.repo;

import java.time.OffsetDateTime;

/**
 * 整库回滚结果
 *
 * 职责：承载整库级 rollback 的目标快照与完成时间
 *
 * @author xiexu
 */
public class RepoRollbackResult {

    private final long restoredSnapshotId;

    private final OffsetDateTime restoredAt;

    /**
     * 创建整库回滚结果。
     *
     * @param restoredSnapshotId 恢复来源快照标识
     * @param restoredAt 恢复时间
     */
    public RepoRollbackResult(long restoredSnapshotId, OffsetDateTime restoredAt) {
        this.restoredSnapshotId = restoredSnapshotId;
        this.restoredAt = restoredAt;
    }

    public long getRestoredSnapshotId() {
        return restoredSnapshotId;
    }

    public OffsetDateTime getRestoredAt() {
        return restoredAt;
    }
}
