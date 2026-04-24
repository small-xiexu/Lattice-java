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

    private final Long sourceId;

    private final Long sourceSyncRunId;

    private final String rootTraceId;

    private final boolean incremental;

    private final String orchestrationMode;

    private final String status;

    private final String workerId;

    private final OffsetDateTime lastHeartbeatAt;

    private final OffsetDateTime runningExpiresAt;

    private final String currentStep;

    private final int progressCurrent;

    private final int progressTotal;

    private final String progressMessage;

    private final OffsetDateTime progressUpdatedAt;

    private final String errorCode;

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
     * @param workerId worker 标识
     * @param lastHeartbeatAt 最近心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @param currentStep 当前执行步骤
     * @param progressCurrent 当前进度数量
     * @param progressTotal 总进度数量
     * @param progressMessage 进度提示文案
     * @param progressUpdatedAt 最近进度更新时间
     * @param errorCode 错误码
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
            String workerId,
            OffsetDateTime lastHeartbeatAt,
            OffsetDateTime runningExpiresAt,
            String currentStep,
            int progressCurrent,
            int progressTotal,
            String progressMessage,
            OffsetDateTime progressUpdatedAt,
            String errorCode,
            int persistedCount,
            String errorMessage,
            int attemptCount,
            OffsetDateTime requestedAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        this(
                jobId,
                sourceDir,
                null,
                null,
                null,
                incremental,
                orchestrationMode,
                status,
                workerId,
                lastHeartbeatAt,
                runningExpiresAt,
                currentStep,
                progressCurrent,
                progressTotal,
                progressMessage,
                progressUpdatedAt,
                errorCode,
                persistedCount,
                errorMessage,
                attemptCount,
                requestedAt,
                startedAt,
                finishedAt
        );
    }

    /**
     * 创建编译作业记录。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @param sourceId 资料源主键
     * @param sourceSyncRunId 资料源同步运行主键
     * @param rootTraceId 根追踪标识
     * @param incremental 是否增量编译
     * @param orchestrationMode 编排模式
     * @param status 状态
     * @param workerId worker 标识
     * @param lastHeartbeatAt 最近心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @param currentStep 当前执行步骤
     * @param progressCurrent 当前进度数量
     * @param progressTotal 总进度数量
     * @param progressMessage 进度提示文案
     * @param progressUpdatedAt 最近进度更新时间
     * @param errorCode 错误码
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
            Long sourceId,
            Long sourceSyncRunId,
            String rootTraceId,
            boolean incremental,
            String orchestrationMode,
            String status,
            String workerId,
            OffsetDateTime lastHeartbeatAt,
            OffsetDateTime runningExpiresAt,
            String currentStep,
            int progressCurrent,
            int progressTotal,
            String progressMessage,
            OffsetDateTime progressUpdatedAt,
            String errorCode,
            int persistedCount,
            String errorMessage,
            int attemptCount,
            OffsetDateTime requestedAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        this.jobId = jobId;
        this.sourceDir = sourceDir;
        this.sourceId = sourceId;
        this.sourceSyncRunId = sourceSyncRunId;
        this.rootTraceId = rootTraceId;
        this.incremental = incremental;
        this.orchestrationMode = orchestrationMode;
        this.status = status;
        this.workerId = workerId;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.runningExpiresAt = runningExpiresAt;
        this.currentStep = currentStep;
        this.progressCurrent = progressCurrent;
        this.progressTotal = progressTotal;
        this.progressMessage = progressMessage;
        this.progressUpdatedAt = progressUpdatedAt;
        this.errorCode = errorCode;
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
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取资料源同步运行主键。
     *
     * @return 资料源同步运行主键
     */
    public Long getSourceSyncRunId() {
        return sourceSyncRunId;
    }

    /**
     * 获取根追踪标识。
     *
     * @return 根追踪标识
     */
    public String getRootTraceId() {
        return rootTraceId;
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
     * 获取 worker 标识。
     *
     * @return worker 标识
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * 获取最近心跳时间。
     *
     * @return 最近心跳时间
     */
    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    /**
     * 获取运行租约到期时间。
     *
     * @return 运行租约到期时间
     */
    public OffsetDateTime getRunningExpiresAt() {
        return runningExpiresAt;
    }

    /**
     * 获取当前执行步骤。
     *
     * @return 当前执行步骤
     */
    public String getCurrentStep() {
        return currentStep;
    }

    /**
     * 获取当前进度数量。
     *
     * @return 当前进度数量
     */
    public int getProgressCurrent() {
        return progressCurrent;
    }

    /**
     * 获取总进度数量。
     *
     * @return 总进度数量
     */
    public int getProgressTotal() {
        return progressTotal;
    }

    /**
     * 获取进度提示文案。
     *
     * @return 进度提示文案
     */
    public String getProgressMessage() {
        return progressMessage;
    }

    /**
     * 获取最近进度更新时间。
     *
     * @return 最近进度更新时间
     */
    public OffsetDateTime getProgressUpdatedAt() {
        return progressUpdatedAt;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
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
