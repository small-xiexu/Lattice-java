package com.xbk.lattice.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 资料源管理配置。
 *
 * 职责：承载资料源物化目录等后台配置
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.source.admin")
public class SourceAdminProperties {

    /**
     * source sync staging 根目录。
     *
     * <p>默认 {@code /tmp/lattice-source-sync}。source 同步时在此目录下创建临时工作区。
     * 应确保有足够磁盘空间，且不被其他进程误删。路径遍历风险——用户可控的 source 配置
     * 可能影响实际写入路径。</p>
     */
    private String stagingRootDir = System.getProperty("java.io.tmpdir") + "/lattice-source-sync";

    /**
     * 获取资料源物化 staging 根目录。
     *
     * @return staging 根目录
     */
    public String getStagingRootDir() {
        return stagingRootDir;
    }

    /**
     * 设置资料源物化 staging 根目录。
     *
     * @param stagingRootDir staging 根目录
     */
    public void setStagingRootDir(String stagingRootDir) {
        this.stagingRootDir = stagingRootDir;
    }
}
