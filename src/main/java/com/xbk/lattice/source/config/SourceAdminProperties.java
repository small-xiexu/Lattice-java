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
