package com.xbk.lattice.api.admin;

/**
 * 管理侧 repo baseline 请求。
 *
 * <p>承载建立 Git-backed repo baseline 所需的参数，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class AdminRepoBaselineRequest {

    /**
     * Vault 本地仓库根目录的绝对路径。
     *
     * <p>决定对哪个 Vault 仓库创建 baseline 快照。服务端应做路径规范化和存在性校验。</p>
     */
    private String vaultDir;

    /**
     * baseline 描述信息。
     *
     * <p>用于在 snapshot 元数据中记录本次 baseline 的目的和上下文，便于后续审计和回滚选择。</p>
     */
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
