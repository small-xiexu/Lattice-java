package com.xbk.lattice.api.admin;

/**
 * 管理侧 Vault 导出请求
 *
 * 职责：承载 Vault 导出目标目录
 *
 * @author xiexu
 */
public class AdminVaultExportRequest {

    private String vaultDir;

    /**
     * 获取 Vault 目录。
     *
     * @return Vault 目录
     */
    public String getVaultDir() {
        return vaultDir;
    }

    /**
     * 设置 Vault 目录。
     *
     * @param vaultDir Vault 目录
     */
    public void setVaultDir(String vaultDir) {
        this.vaultDir = vaultDir;
    }
}
