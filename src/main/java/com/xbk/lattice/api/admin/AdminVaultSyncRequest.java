package com.xbk.lattice.api.admin;

/**
 * 管理侧 Vault 回写请求
 *
 * 职责：承载 Vault inbound sync 的目录和 force 开关
 *
 * @author xiexu
 */
public class AdminVaultSyncRequest {

    private String vaultDir;

    private boolean force;

    public String getVaultDir() {
        return vaultDir;
    }

    public void setVaultDir(String vaultDir) {
        this.vaultDir = vaultDir;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
