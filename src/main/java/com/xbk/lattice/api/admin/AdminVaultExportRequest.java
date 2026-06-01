package com.xbk.lattice.api.admin;

/**
 * 管理侧 Vault 导出请求。
 *
 * <p>承载 Vault 导出操作的目标目录参数，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class AdminVaultExportRequest {

    /**
     * Vault 本地仓库根目录的绝对路径。
     *
     * <p>服务端在 {@code VaultController.export()} 中将其转为 {@code Path.of(vaultDir)} 使用。
     * <b>服务端应做路径规范化（normalize）和存在性校验，防止路径遍历攻击。</b>
     * 为空时行为由服务端决定——通常会导致导出失败。</p>
     */
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
