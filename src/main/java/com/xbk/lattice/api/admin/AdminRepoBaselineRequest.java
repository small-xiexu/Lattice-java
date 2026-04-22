package com.xbk.lattice.api.admin;

/**
 * 管理侧 repo baseline 请求
 *
 * 职责：承载建立 Git-backed repo baseline 所需的 Vault 目录与可选描述
 *
 * @author xiexu
 */
public class AdminRepoBaselineRequest {

    private String vaultDir;

    private String description;

    public String getVaultDir() {
        return vaultDir;
    }

    public void setVaultDir(String vaultDir) {
        this.vaultDir = vaultDir;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
