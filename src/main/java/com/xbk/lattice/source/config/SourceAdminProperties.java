package com.xbk.lattice.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;

/**
 * 资料源管理配置。
 *
 * 职责：承载资料源物化目录、镜像根 allowlist 等后台配置
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
     * 内部镜像根 allowlist。
     *
     * <p>key 为镜像根引用名（mirrorRootRef），value 为服务器上的绝对规范路径。
     * INTERNAL_MIRROR 资料源只能绑定此映射中的引用，不能通过 API 传入任意绝对路径。
     * 默认空映射——未配置时不允许创建 INTERNAL_MIRROR 资料源。</p>
     */
    private Map<String, String> mirrorRoots = Collections.emptyMap();

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
     * 获取镜像根 allowlist。
     *
     * @return 镜像根引用 → 绝对路径 映射，未配置时为空
     */
    public Map<String, String> getMirrorRoots() {
        return mirrorRoots;
    }

    /**
     * 设置镜像根 allowlist。
     *
     * @param mirrorRoots 镜像根引用映射
     */
    public void setMirrorRoots(Map<String, String> mirrorRoots) {
        this.mirrorRoots = mirrorRoots != null ? Collections.unmodifiableMap(mirrorRoots) : Collections.emptyMap();
    }
}
