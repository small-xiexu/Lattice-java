package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.management.ManagementFactory;

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

    private String workerId = resolveDefaultWorkerId();

    private long heartbeatIntervalSeconds = 15L;

    private long leaseDurationSeconds = 300L;

    private long stalledThresholdSeconds = 600L;

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
     * 获取当前实例的 worker 标识。
     *
     * @return worker 标识
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * 设置当前实例的 worker 标识。
     *
     * @param workerId worker 标识
     */
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    /**
     * 获取心跳续租间隔秒数。
     *
     * @return 心跳续租间隔秒数
     */
    public long getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    /**
     * 设置心跳续租间隔秒数。
     *
     * @param heartbeatIntervalSeconds 心跳续租间隔秒数
     */
    public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    /**
     * 获取运行租约时长秒数。
     *
     * @return 运行租约时长秒数
     */
    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    /**
     * 设置运行租约时长秒数。
     *
     * @param leaseDurationSeconds 运行租约时长秒数
     */
    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    /**
     * 获取疑似卡住阈值秒数。
     *
     * @return 疑似卡住阈值秒数
     */
    public long getStalledThresholdSeconds() {
        return stalledThresholdSeconds;
    }

    /**
     * 设置疑似卡住阈值秒数。
     *
     * @param stalledThresholdSeconds 疑似卡住阈值秒数
     */
    public void setStalledThresholdSeconds(long stalledThresholdSeconds) {
        this.stalledThresholdSeconds = stalledThresholdSeconds;
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

    /**
     * 生成默认 worker 标识。
     *
     * @return 默认 worker 标识
     */
    private static String resolveDefaultWorkerId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return "worker-" + runtimeName.replace(':', '-').replace(' ', '-');
    }
}
