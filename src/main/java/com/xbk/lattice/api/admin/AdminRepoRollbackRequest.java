package com.xbk.lattice.api.admin;

/**
 * 管理侧整库回滚请求
 *
 * 职责：承载整库回滚的目标 snapshot 与 Vault 目录
 *
 * @author xiexu
 */
public class AdminRepoRollbackRequest {

    private long snapshotId;

    private String vaultDir;

    public long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getVaultDir() {
        return vaultDir;
    }

    public void setVaultDir(String vaultDir) {
        this.vaultDir = vaultDir;
    }
}
