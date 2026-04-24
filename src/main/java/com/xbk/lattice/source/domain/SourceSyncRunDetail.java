package com.xbk.lattice.source.domain;

import java.util.List;

/**
 * 同步运行详情视图。
 *
 * 职责：承载管理侧上传轮询与人工确认需要展示的运行信息
 *
 * @author xiexu
 */
public class SourceSyncRunDetail {

    private final Long runId;

    private final Long sourceId;

    private final String sourceName;

    private final String sourceType;

    private final String status;

    private final String resolverMode;

    private final String resolverDecision;

    private final String syncAction;

    private final Long matchedSourceId;

    private final String compileJobId;

    private final String compileJobStatus;

    private final String compileDerivedStatus;

    private final String compileCurrentStep;

    private final Integer compileProgressCurrent;

    private final Integer compileProgressTotal;

    private final String compileProgressMessage;

    private final String compileLastHeartbeatAt;

    private final String compileRunningExpiresAt;

    private final String compileErrorCode;

    private final String manifestHash;

    private final String message;

    private final String errorMessage;

    private final List<String> sourceNames;

    private final String evidenceJson;

    private final String requestedAt;

    private final String updatedAt;

    private final String startedAt;

    private final String finishedAt;

    /**
     * 创建同步运行详情。
     *
     * @param runId 运行主键
     * @param sourceId 资料源主键
     * @param sourceName 资料源名称
     * @param sourceType 资料源类型
     * @param status 运行状态
     * @param resolverMode 识别模式
     * @param resolverDecision 识别决策
     * @param syncAction 同步动作
     * @param matchedSourceId 命中的资料源主键
     * @param compileJobId 编译作业主键
     * @param compileJobStatus 编译作业状态
     * @param compileDerivedStatus 编译作业派生状态
     * @param compileCurrentStep 编译当前步骤
     * @param compileProgressCurrent 编译当前进度
     * @param compileProgressTotal 编译总进度
     * @param compileProgressMessage 编译进度提示
     * @param compileLastHeartbeatAt 编译最近心跳时间
     * @param compileRunningExpiresAt 编译租约到期时间
     * @param compileErrorCode 编译错误码
     * @param manifestHash manifest 哈希
     * @param message 提示信息
     * @param errorMessage 错误信息
     * @param sourceNames 来源文件名
     * @param evidenceJson 证据 JSON
     * @param requestedAt 请求时间
     * @param updatedAt 更新时间
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    public SourceSyncRunDetail(
            Long runId,
            Long sourceId,
            String sourceName,
            String sourceType,
            String status,
            String resolverMode,
            String resolverDecision,
            String syncAction,
            Long matchedSourceId,
            String compileJobId,
            String compileJobStatus,
            String compileDerivedStatus,
            String compileCurrentStep,
            Integer compileProgressCurrent,
            Integer compileProgressTotal,
            String compileProgressMessage,
            String compileLastHeartbeatAt,
            String compileRunningExpiresAt,
            String compileErrorCode,
            String manifestHash,
            String message,
            String errorMessage,
            List<String> sourceNames,
            String evidenceJson,
            String requestedAt,
            String updatedAt,
            String startedAt,
            String finishedAt
    ) {
        this.runId = runId;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.sourceType = sourceType;
        this.status = status;
        this.resolverMode = resolverMode;
        this.resolverDecision = resolverDecision;
        this.syncAction = syncAction;
        this.matchedSourceId = matchedSourceId;
        this.compileJobId = compileJobId;
        this.compileJobStatus = compileJobStatus;
        this.compileDerivedStatus = compileDerivedStatus;
        this.compileCurrentStep = compileCurrentStep;
        this.compileProgressCurrent = compileProgressCurrent;
        this.compileProgressTotal = compileProgressTotal;
        this.compileProgressMessage = compileProgressMessage;
        this.compileLastHeartbeatAt = compileLastHeartbeatAt;
        this.compileRunningExpiresAt = compileRunningExpiresAt;
        this.compileErrorCode = compileErrorCode;
        this.manifestHash = manifestHash;
        this.message = message;
        this.errorMessage = errorMessage;
        this.sourceNames = sourceNames;
        this.evidenceJson = evidenceJson;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getStatus() {
        return status;
    }

    public String getResolverMode() {
        return resolverMode;
    }

    public String getResolverDecision() {
        return resolverDecision;
    }

    public String getSyncAction() {
        return syncAction;
    }

    public Long getMatchedSourceId() {
        return matchedSourceId;
    }

    public String getCompileJobId() {
        return compileJobId;
    }

    public String getCompileJobStatus() {
        return compileJobStatus;
    }

    public String getCompileDerivedStatus() {
        return compileDerivedStatus;
    }

    public String getCompileCurrentStep() {
        return compileCurrentStep;
    }

    public Integer getCompileProgressCurrent() {
        return compileProgressCurrent;
    }

    public Integer getCompileProgressTotal() {
        return compileProgressTotal;
    }

    public String getCompileProgressMessage() {
        return compileProgressMessage;
    }

    public String getCompileLastHeartbeatAt() {
        return compileLastHeartbeatAt;
    }

    public String getCompileRunningExpiresAt() {
        return compileRunningExpiresAt;
    }

    public String getCompileErrorCode() {
        return compileErrorCode;
    }

    public String getManifestHash() {
        return manifestHash;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getSourceNames() {
        return sourceNames;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public String getRequestedAt() {
        return requestedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }
}
