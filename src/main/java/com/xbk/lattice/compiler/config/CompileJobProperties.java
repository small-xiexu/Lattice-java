package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编译作业配置
 *
 * 职责：承载后台编译作业 worker 与上传暂存目录参数
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.compiler.jobs")
public class CompileJobProperties {

    private boolean workerEnabled = true;

    private long pollDelayMs = 1000L;

    private String uploadRootDir = System.getProperty("java.io.tmpdir") + "/lattice-admin-uploads";

    /**
     * 是否启用后台 worker。
     *
     * @return 是否启用后台 worker
     */
    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    /**
     * 设置是否启用后台 worker。
     *
     * @param workerEnabled 是否启用后台 worker
     */
    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    /**
     * 获取轮询间隔毫秒数。
     *
     * @return 轮询间隔毫秒数
     */
    public long getPollDelayMs() {
        return pollDelayMs;
    }

    /**
     * 设置轮询间隔毫秒数。
     *
     * @param pollDelayMs 轮询间隔毫秒数
     */
    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    /**
     * 获取上传暂存目录。
     *
     * @return 上传暂存目录
     */
    public String getUploadRootDir() {
        return uploadRootDir;
    }

    /**
     * 设置上传暂存目录。
     *
     * @param uploadRootDir 上传暂存目录
     */
    public void setUploadRootDir(String uploadRootDir) {
        this.uploadRootDir = uploadRootDir;
    }
}
