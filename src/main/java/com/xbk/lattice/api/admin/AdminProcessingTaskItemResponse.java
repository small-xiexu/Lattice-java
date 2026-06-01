package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 当前处理任务条目响应。
 *
 * <p>统一承载 source sync 与 standalone compile 两类任务的工作台展示字段——
 * 任务标识、主状态、编译关联、提示错误、展示字段、审查关联、来源数据与时间戳。
 * 由 {@code AdminProcessingTaskController} 从 {@code AdminProcessingTaskPresentation} 组装返回。
 * 禁止引入 {@code @Data}：{@code errorMessage} 可能含异常信息，{@code evidenceJson} 可能很大。
 *
 * @author xiexu
 */
@Getter
public class AdminProcessingTaskItemResponse {

    // ── 任务标识 ──

    /** 任务主键。 */
    private final String taskId;

    /** 任务类型（如 {@code source_sync} / {@code standalone_compile}）。 */
    private final String taskType;

    /** 展示标题。 */
    private final String title;

    /** 同步运行主键。为 {@code null} 表示非 sync 任务。 */
    private final Long runId;

    /** 资料源主键。为 {@code null} 表示无关联 source。 */
    private final Long sourceId;

    /** 资料源名称。 */
    private final String sourceName;

    /** 资料源类型。 */
    private final String sourceType;

    // ── 主状态与解析 ──

    /** 主状态（数据库原始值）。 */
    private final String status;

    /** 识别模式（source 匹配策略）。 */
    private final String resolverMode;

    /** 识别决策（source 匹配结果）。 */
    private final String resolverDecision;

    /** 同步动作（如 {@code sync} / {@code skip}）。 */
    private final String syncAction;

    /** 候选资料源主键。为 {@code null} 表示无候选。 */
    private final Long matchedSourceId;

    // ── 编译关联 ──

    /** 编译任务主键。为 {@code null} 表示无关联 compile job。 */
    private final String compileJobId;

    /** 编译任务原始状态。 */
    private final String compileJobStatus;

    /** 编译任务派生展示状态（由 {@code CompileJobDerivedStatusResolver} 计算）。 */
    private final String compileDerivedStatus;

    /** 编译当前执行步骤。 */
    private final String compileCurrentStep;

    /** 编译当前进度计数。为 {@code null} 表示无进度数据。 */
    private final Integer compileProgressCurrent;

    /** 编译总进度计数。为 {@code null} 表示无法估算。 */
    private final Integer compileProgressTotal;

    /** 编译进度提示文案。 */
    private final String compileProgressMessage;

    /** 编译最近心跳时间（ISO-8601）。 */
    private final String compileLastHeartbeatAt;

    /** 编译租约到期时间（ISO-8601）。 */
    private final String compileRunningExpiresAt;

    /** 编译错误码。为 {@code null} 表示无错误。 */
    private final String compileErrorCode;

    // ── 清单与提示 ──

    /** manifest 内容哈希（用于检测输入变更）。 */
    private final String manifestHash;

    /** 提示文案（由后端生成的上下文提示）。 */
    private final String message;

    /**
     * 错误信息。
     *
     * <p>可能含异常栈或后端错误原文，仅管理侧排查用。禁止参与 {@code toString()}。</p>
     */
    private final String errorMessage;

    /** 来源文件名预览列表（截断）。 */
    private final List<String> sourceNames;

    /** 可用操作动作列表。 */
    private final List<AdminProcessingTaskActionResponse> actions;

    // ── 展示字段（由 AdminProcessingTaskDisplayStatus 派生） ──

    /**
     * 展示状态码。
     *
     * <p>取自 {@code AdminProcessingTaskDisplayStatus} 枚举的 {@code code} 字段。
     * 驱动前端状态标签的颜色、图标和可执行操作。</p>
     */
    private final String displayStatus;

    /** 展示状态文案（取自枚举的 {@code label}）。 */
    private final String displayStatusLabel;

    /** 当前步骤展示文案。 */
    private final String currentStepLabel;

    /** 下一步操作提示文案。可为空。 */
    private final String nextStepHint;

    /** 当前进度文案（如"3/5 步骤"）。 */
    private final String progressText;

    /** 原因摘要（解释当前状态的成因）。 */
    private final String reasonSummary;

    /** 任务线索（后端生成的诊断提示）。 */
    private final String operationalNote;

    /** 完整步骤链。 */
    private final List<AdminProcessingTaskStepResponse> progressSteps;

    /**
     * 展示色调。
     *
     * <p>取自 {@code AdminProcessingTaskDisplayStatus} 枚举的 {@code tone} 字段
     * （如 {@code info} / {@code warning} / {@code error} / {@code success}）。</p>
     */
    private final String displayTone;

    /** 是否仍需前端轮询（取自枚举的 {@code processingActive}）。 */
    private final boolean processingActive;

    /** 是否需要人工操作（取自枚举的 {@code requiresManualAction}）。 */
    private final boolean requiresManualAction;

    /** 通知语气（取自枚举的 {@code noticeTone}，如 {@code warning} / {@code info}）。 */
    private final String noticeTone;

    /** 任务完成时的提示文案。可为空。 */
    private final String completionNotice;

    // ── 审查关联 ──

    /**
     * 编译审查摘要。
     *
     * <p>为 {@code null} 表示无审查步骤或审查未执行。</p>
     */
    private final AdminCompileReviewSummaryResponse compileReviewSummary;

    /** 待人工确认的文章数。 */
    private final int pendingHumanReviewCount;

    /** 已发布的文章数。 */
    private final int publishedCount;

    /** 已驳回的文章数。 */
    private final int rejectedCount;

    // ── 来源数据 ──

    /**
     * 证据 JSON。
     *
     * <p>可能较大，包含 source 匹配或编译的详细证据数据。禁止参与 {@code toString()}。</p>
     */
    private final String evidenceJson;

    // ── 时间戳 ──

    /** 任务提交时间（ISO-8601）。 */
    private final String requestedAt;

    /** 任务最后更新时间（ISO-8601）。 */
    private final String updatedAt;

    /** 任务开始时间（ISO-8601）。为 {@code null} 表示尚未开始。 */
    private final String startedAt;

    /** 任务完成时间（ISO-8601）。为 {@code null} 表示未完成。 */
    private final String finishedAt;

    /**
     * 创建当前处理任务条目响应（小构造器——委托大构造器，pending/published/rejected 计数默认 0）。
     *
     * @param taskId 任务主键
     * @param taskType 任务类型
     * @param title 展示标题
     * @param runId 同步运行主键
     * @param sourceId 资料源主键
     * @param sourceName 资料源名称
     * @param sourceType 资料源类型
     * @param status 主状态
     * @param resolverMode 识别模式
     * @param resolverDecision 识别决策
     * @param syncAction 同步动作
     * @param matchedSourceId 候选资料源主键
     * @param compileJobId 编译任务主键
     * @param compileJobStatus 编译任务状态
     * @param compileDerivedStatus 编译任务派生状态
     * @param compileCurrentStep 编译当前步骤
     * @param compileProgressCurrent 编译当前进度
     * @param compileProgressTotal 编译总进度
     * @param compileProgressMessage 编译进度文案
     * @param compileLastHeartbeatAt 编译最近心跳时间
     * @param compileRunningExpiresAt 编译租约到期时间
     * @param compileErrorCode 编译错误码
     * @param manifestHash manifest 哈希
     * @param message 提示文案
     * @param errorMessage 错误文案
     * @param sourceNames 来源预览
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
     * @param compileReviewSummary 编译审查摘要
     * @param evidenceJson 证据 JSON
     * @param requestedAt 提交时间
     * @param updatedAt 更新时间
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    public AdminProcessingTaskItemResponse(
            String taskId,
            String taskType,
            String title,
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
            List<AdminProcessingTaskActionResponse> actions,
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
            AdminCompileReviewSummaryResponse compileReviewSummary,
            String evidenceJson,
            String requestedAt,
            String updatedAt,
            String startedAt,
            String finishedAt
    ) {
        this(
                taskId, taskType, title, runId, sourceId, sourceName, sourceType,
                status, resolverMode, resolverDecision, syncAction, matchedSourceId,
                compileJobId, compileJobStatus, compileDerivedStatus, compileCurrentStep,
                compileProgressCurrent, compileProgressTotal, compileProgressMessage,
                compileLastHeartbeatAt, compileRunningExpiresAt, compileErrorCode,
                manifestHash, message, errorMessage, sourceNames, actions,
                displayStatus, displayStatusLabel, currentStepLabel, nextStepHint,
                progressText, reasonSummary, operationalNote, progressSteps,
                displayTone, processingActive, requiresManualAction, noticeTone,
                completionNotice, compileReviewSummary,
                0, 0, 0,
                evidenceJson, requestedAt, updatedAt, startedAt, finishedAt
        );
    }

    /**
     * 创建当前处理任务条目响应（大构造器——完整参数，含审查计数）。
     *
     * @param taskId 任务主键
     * @param taskType 任务类型
     * @param title 展示标题
     * @param runId 同步运行主键
     * @param sourceId 资料源主键
     * @param sourceName 资料源名称
     * @param sourceType 资料源类型
     * @param status 主状态
     * @param resolverMode 识别模式
     * @param resolverDecision 识别决策
     * @param syncAction 同步动作
     * @param matchedSourceId 候选资料源主键
     * @param compileJobId 编译任务主键
     * @param compileJobStatus 编译任务状态
     * @param compileDerivedStatus 编译任务派生状态
     * @param compileCurrentStep 编译当前步骤
     * @param compileProgressCurrent 编译当前进度
     * @param compileProgressTotal 编译总进度
     * @param compileProgressMessage 编译进度文案
     * @param compileLastHeartbeatAt 编译最近心跳时间
     * @param compileRunningExpiresAt 编译租约到期时间
     * @param compileErrorCode 编译错误码
     * @param manifestHash manifest 哈希
     * @param message 提示文案
     * @param errorMessage 错误文案
     * @param sourceNames 来源预览
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
     * @param compileReviewSummary 编译审查摘要
     * @param pendingHumanReviewCount 待人工确认数量
     * @param publishedCount 已发布数量
     * @param rejectedCount 已驳回数量
     * @param evidenceJson 证据 JSON
     * @param requestedAt 提交时间
     * @param updatedAt 更新时间
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    public AdminProcessingTaskItemResponse(
            String taskId,
            String taskType,
            String title,
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
            List<AdminProcessingTaskActionResponse> actions,
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
            AdminCompileReviewSummaryResponse compileReviewSummary,
            int pendingHumanReviewCount,
            int publishedCount,
            int rejectedCount,
            String evidenceJson,
            String requestedAt,
            String updatedAt,
            String startedAt,
            String finishedAt
    ) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.title = title;
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
        this.compileReviewSummary = compileReviewSummary;
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
