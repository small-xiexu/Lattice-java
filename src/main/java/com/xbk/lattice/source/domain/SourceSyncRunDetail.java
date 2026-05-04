package com.xbk.lattice.source.domain;

import com.xbk.lattice.api.admin.AdminProcessingTaskStepResponse;

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

    private final List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> actions;

    private final String displayStatus;

    private final String displayStatusLabel;

    private final String currentStepLabel;

    private final String nextStepHint;

    private final String progressText;

    private final String reasonSummary;

    private final String operationalNote;

    private final List<AdminProcessingTaskStepResponse> progressSteps;

    private final String displayTone;

    private final boolean processingActive;

    private final boolean requiresManualAction;

    private final String noticeTone;

    private final String completionNotice;

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
     * @param actions 可用动作
     * @param displayStatus 展示状态
     * @param displayStatusLabel 展示状态文案
     * @param currentStepLabel 当前步骤文案
     * @param nextStepHint 下一步提示
     * @param progressText 当前进度文案
     * @param reasonSummary 原因摘要
     * @param operationalNote 任务线索
     * @param progressSteps 完整步骤链
     * @param displayTone 展示色调
     * @param processingActive 是否仍需轮询
     * @param requiresManualAction 是否需要人工处理
     * @param noticeTone 通知语气
     * @param completionNotice 完成提示
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
            List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> actions,
            String displayStatus,
            String displayStatusLabel,
            String currentStepLabel,
            String nextStepHint,
            String progressText,
            String reasonSummary,
            String operationalNote,
            List<AdminProcessingTaskStepResponse> progressSteps,
            String displayTone,
            boolean processingActive,
            boolean requiresManualAction,
            String noticeTone,
            String completionNotice,
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
        this.actions = actions;
        this.displayStatus = displayStatus;
        this.displayStatusLabel = displayStatusLabel;
        this.currentStepLabel = currentStepLabel;
        this.nextStepHint = nextStepHint;
        this.progressText = progressText;
        this.reasonSummary = reasonSummary;
        this.operationalNote = operationalNote;
        this.progressSteps = progressSteps;
        this.displayTone = displayTone;
        this.processingActive = processingActive;
        this.requiresManualAction = requiresManualAction;
        this.noticeTone = noticeTone;
        this.completionNotice = completionNotice;
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

    public List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> getActions() {
        return actions;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    public String getDisplayStatusLabel() {
        return displayStatusLabel;
    }

    public String getCurrentStepLabel() {
        return currentStepLabel;
    }

    public String getNextStepHint() {
        return nextStepHint;
    }

    public String getProgressText() {
        return progressText;
    }

    public String getReasonSummary() {
        return reasonSummary;
    }

    public String getOperationalNote() {
        return operationalNote;
    }

    public List<AdminProcessingTaskStepResponse> getProgressSteps() {
        return progressSteps;
    }

    public String getDisplayTone() {
        return displayTone;
    }

    public boolean isProcessingActive() {
        return processingActive;
    }

    public boolean isRequiresManualAction() {
        return requiresManualAction;
    }

    public String getNoticeTone() {
        return noticeTone;
    }

    public String getCompletionNotice() {
        return completionNotice;
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
