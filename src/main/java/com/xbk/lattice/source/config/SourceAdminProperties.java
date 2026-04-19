package com.xbk.lattice.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 资料源管理配置。
 *
 * 职责：承载资料源物化目录与管理员目录白名单等后台配置
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.source.admin")
public class SourceAdminProperties {

    private String stagingRootDir = System.getProperty("java.io.tmpdir") + "/lattice-source-sync";

    private List<String> allowedServerDirs = new ArrayList<String>();

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

    /**
     * 获取管理员白名单目录列表。
     *
     * @return 白名单目录列表
     */
    public List<String> getAllowedServerDirs() {
        return allowedServerDirs;
    }

    /**
     * 设置管理员白名单目录列表。
     *
     * @param allowedServerDirs 白名单目录列表
     */
    public void setAllowedServerDirs(List<String> allowedServerDirs) {
        this.allowedServerDirs = allowedServerDirs;
    }
}
