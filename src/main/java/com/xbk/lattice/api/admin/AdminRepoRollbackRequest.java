package com.xbk.lattice.api.admin;

/**
 * 管理侧整库回滚请求。
 *
 * <p>承载整库回滚操作的目标参数，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class AdminRepoRollbackRequest {

    /**
     * 回滚目标 snapshot ID。
     *
     * <p><b>服务端应校验该 snapshot 存在且属于当前 Vault，不得接受未经校验的 ID。</b>
     * 接受任意 snapshotId 可能导致回滚到不相关的快照。</p>
     */
    private long snapshotId;

    /**
     * Vault 本地仓库根目录的绝对路径。
     *
     * <p>服务端应做路径规范化和存在性校验。</p>
     */
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
