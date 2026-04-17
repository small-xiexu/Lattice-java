package com.xbk.lattice.api.admin;

import com.xbk.lattice.vault.snapshot.VaultDiffSummary;

import java.util.List;

/**
 * 管理侧整库 diff 响应
 *
 * 职责：承载目标 snapshot 对应的 Vault Git 差异摘要
 *
 * @author xiexu
 */
public class AdminRepoDiffResponse {

    private final long snapshotId;

    private final String targetCommitId;

    private final String currentCommitId;

    private final List<VaultDiffSummary> items;

    public AdminRepoDiffResponse(
            long snapshotId,
            String targetCommitId,
            String currentCommitId,
            List<VaultDiffSummary> items
    ) {
        this.snapshotId = snapshotId;
        this.targetCommitId = targetCommitId;
        this.currentCommitId = currentCommitId;
        this.items = items;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public String getTargetCommitId() {
        return targetCommitId;
    }

    public String getCurrentCommitId() {
        return currentCommitId;
    }

    public List<VaultDiffSummary> getItems() {
        return items;
    }

    public int getCount() {
        return items == null ? 0 : items.size();
    }
}
