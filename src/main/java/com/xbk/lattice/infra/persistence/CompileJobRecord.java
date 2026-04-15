package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 编译作业记录
 *
 * 职责：表示后台编译作业的持久化状态
 *
 * @author xiexu
 */
public class CompileJobRecord {

    private final String jobId;

    private final String sourceDir;

    private final boolean incremental;

    private final String orchestrationMode;

    private final String status;

    private final int persistedCount;

    private final String errorMessage;

    private final int attemptCount;

    private final OffsetDateTime requestedAt;

    private final OffsetDateTime startedAt;

    private final OffsetDateTime finishedAt;

    /**
     * 创建编译作业记录。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @param orchestrationMode 编排模式
     * @param status 状态
     * @param persistedCount 持久化数量
     * @param errorMessage 错误信息
     * @param attemptCount 尝试次数
     * @param requestedAt 提交时间
     * @param startedAt 开始时间
     * @param finishedAt 完成时间
     */
    public CompileJobRecord(
            String jobId,
            String sourceDir,
            boolean incremental,
            String orchestrationMode,
            String status,
            int persistedCount,
            String errorMessage,
            int attemptCount,
            OffsetDateTime requestedAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        this.jobId = jobId;
        this.sourceDir = sourceDir;
        this.incremental = incremental;
        this.orchestrationMode = orchestrationMode;
        this.status = status;
        this.persistedCount = persistedCount;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount;
        this.requestedAt = requestedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * 获取作业标识。
     *
     * @return 作业标识
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * 获取源目录。
     *
     * @return 源目录
     */
    public String getSourceDir() {
        return sourceDir;
    }

    /**
     * 获取是否增量编译。
     *
     * @return 是否增量编译
     */
    public boolean isIncremental() {
        return incremental;
    }

    /**
     * 获取编排模式。
     *
     * @return 编排模式
     */
    public String getOrchestrationMode() {
        return orchestrationMode;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取持久化数量。
     *
     * @return 持久化数量
     */
    public int getPersistedCount() {
        return persistedCount;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取尝试次数。
     *
     * @return 尝试次数
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * 获取提交时间。
     *
     * @return 提交时间
     */
    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    /**
     * 获取开始时间。
     *
     * @return 开始时间
     */
    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * 获取完成时间。
     *
     * @return 完成时间
     */
    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
