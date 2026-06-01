package com.xbk.lattice.api.admin;

/**
 * 管理侧 Vault 回写请求。
 *
 * <p>承载 Vault inbound sync 的目录和覆盖策略，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class AdminVaultSyncRequest {

    /**
     * Vault 本地仓库根目录的绝对路径。
     *
     * <p>服务端应做路径规范化和存在性校验，防止路径遍历攻击。</p>
     */
    private String vaultDir;

    /**
     * 是否强制覆盖。
     *
     * <p>为 true 时 sync 操作会强制覆盖本地未提交的变更或绕过部分安全检查。
     * 为 false（默认）时以安全模式执行，遇到冲突会中止。</p>
     */
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
