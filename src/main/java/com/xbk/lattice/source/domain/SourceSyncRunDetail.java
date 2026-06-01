package com.xbk.lattice.source.domain;

import com.xbk.lattice.api.admin.AdminProcessingTaskStepResponse;
import lombok.Getter;

import java.util.List;

/**
 * 同步运行详情视图。
 *
 * <p>承载管理侧上传轮询与人工确认需要展示的运行信息——含编译进度、步骤链、展示状态和审查计数。
 * 双构造器：小构造器委托大构造器，pending/published/rejected 默认 0。
 *
 * @author xiexu
 */
@Getter
public class SourceSyncRunDetail {

    /** 运行主键。 */
    private final Long runId;
    /** 资料源主键。 */
    private final Long sourceId;
    /** 资料源名称。 */
    private final String sourceName;
    /** 资料源类型。 */
    private final String sourceType;
    /** 运行状态。 */
    private final String status;
    /** 识别模式。 */
    private final String resolverMode;
    /** 识别决策。 */
    private final String resolverDecision;
    /** 同步动作。 */
    private final String syncAction;
    /** 命中的资料源主键。 */
    private final Long matchedSourceId;
    /** 编译作业主键。 */
    private final String compileJobId;
    /** 编译作业状态。 */
    private final String compileJobStatus;
    /** 编译派生展示状态。 */
    private final String compileDerivedStatus;
    /** 编译当前步骤。 */
    private final String compileCurrentStep;
    /** 编译当前进度。 */
    private final Integer compileProgressCurrent;
    /** 编译总进度。 */
    private final Integer compileProgressTotal;
    /** 编译进度提示。 */
    private final String compileProgressMessage;
    /** 编译最近心跳时间。 */
    private final String compileLastHeartbeatAt;
    /** 编译租约到期时间。 */
    private final String compileRunningExpiresAt;
    /** 编译错误码。 */
    private final String compileErrorCode;
    /** manifest 哈希。 */
    private final String manifestHash;
    /** 提示信息。 */
    private final String message;
    /** 错误信息。可能含异常详情，禁止 toString()。 */
    private final String errorMessage;
    /** 来源文件名列表。 */
    private final List<String> sourceNames;
    /** 可用操作动作。 */
    private final List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> actions;
    /** 展示状态码。 */
    private final String displayStatus;
    /** 展示状态文案。 */
    private final String displayStatusLabel;
    /** 当前步骤文案。 */
    private final String currentStepLabel;
    /** 下一步提示。 */
    private final String nextStepHint;
    /** 进度文案。 */
    private final String progressText;
    /** 原因摘要。 */
    private final String reasonSummary;
    /** 任务线索。 */
    private final String operationalNote;
    /** 完整步骤链。 */
    private final List<AdminProcessingTaskStepResponse> progressSteps;
    /** 展示色调。 */
    private final String displayTone;
    /** 是否仍需轮询。 */
    private final boolean processingActive;
    /** 是否需要人工处理。 */
    private final boolean requiresManualAction;
    /** 通知语气。 */
    private final String noticeTone;
    /** 完成提示。 */
    private final String completionNotice;
    /** 待人工确认数量。 */
    private final int pendingHumanReviewCount;
    /** 已发布数量。 */
    private final int publishedCount;
    /** 已驳回数量。 */
    private final int rejectedCount;
    /** 证据 JSON。可能较大。 */
    private final String evidenceJson;
    /** 请求时间。 */
    private final String requestedAt;
    /** 更新时间。 */
    private final String updatedAt;
    /** 开始时间。 */
    private final String startedAt;
    /** 完成时间。 */
    private final String finishedAt;

    /** 小构造器——委托大构造器，审查计数默认 0。 */
    public SourceSyncRunDetail(
            Long runId, Long sourceId, String sourceName, String sourceType, String status,
            String resolverMode, String resolverDecision, String syncAction, Long matchedSourceId,
            String compileJobId, String compileJobStatus, String compileDerivedStatus,
            String compileCurrentStep, Integer compileProgressCurrent, Integer compileProgressTotal,
            String compileProgressMessage, String compileLastHeartbeatAt, String compileRunningExpiresAt,
            String compileErrorCode, String manifestHash, String message, String errorMessage,
            List<String> sourceNames,
            List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> actions,
            String displayStatus, String displayStatusLabel, String currentStepLabel,
            String nextStepHint, String progressText, String reasonSummary, String operationalNote,
            List<AdminProcessingTaskStepResponse> progressSteps, String displayTone,
            boolean processingActive, boolean requiresManualAction, String noticeTone,
            String completionNotice, String evidenceJson, String requestedAt, String updatedAt,
            String startedAt, String finishedAt
    ) {
        this(runId, sourceId, sourceName, sourceType, status, resolverMode, resolverDecision,
                syncAction, matchedSourceId, compileJobId, compileJobStatus, compileDerivedStatus,
                compileCurrentStep, compileProgressCurrent, compileProgressTotal, compileProgressMessage,
                compileLastHeartbeatAt, compileRunningExpiresAt, compileErrorCode, manifestHash,
                message, errorMessage, sourceNames, actions, displayStatus, displayStatusLabel,
                currentStepLabel, nextStepHint, progressText, reasonSummary, operationalNote,
                progressSteps, displayTone, processingActive, requiresManualAction, noticeTone,
                completionNotice, 0, 0, 0, evidenceJson, requestedAt, updatedAt, startedAt, finishedAt);
    }

    /** 大构造器——完整参数。 */
    public SourceSyncRunDetail(
            Long runId, Long sourceId, String sourceName, String sourceType, String status,
            String resolverMode, String resolverDecision, String syncAction, Long matchedSourceId,
            String compileJobId, String compileJobStatus, String compileDerivedStatus,
            String compileCurrentStep, Integer compileProgressCurrent, Integer compileProgressTotal,
            String compileProgressMessage, String compileLastHeartbeatAt, String compileRunningExpiresAt,
            String compileErrorCode, String manifestHash, String message, String errorMessage,
            List<String> sourceNames,
            List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> actions,
            String displayStatus, String displayStatusLabel, String currentStepLabel,
            String nextStepHint, String progressText, String reasonSummary, String operationalNote,
            List<AdminProcessingTaskStepResponse> progressSteps, String displayTone,
            boolean processingActive, boolean requiresManualAction, String noticeTone,
            String completionNotice, int pendingHumanReviewCount, int publishedCount,
            int rejectedCount, String evidenceJson, String requestedAt, String updatedAt,
            String startedAt, String finishedAt
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
        this.pendingHumanReviewCount = pendingHumanReviewCount;
        this.publishedCount = publishedCount;
        this.rejectedCount = rejectedCount;
        this.evidenceJson = evidenceJson;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }
}
