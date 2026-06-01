package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧编译作业响应。
 *
 * <p>承载 admin compile job 的当前状态、进度、错误信息与审查摘要，
 * 由 {@code AdminCompileController} 从作业记录与运行态状态组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileJobResponse {

    /** 编译作业唯一标识。 */
    private final String jobId;

    /**
     * 编译源目录。
     *
     * <p>为 {@code null} 时表示作业未初始化或源目录未设置。</p>
     */
    private final String sourceDir;

    /**
     * 编译源文件名列表。
     *
     * <p>仅上传编译时有值；目录编译时为空列表或 {@code null}。</p>
     */
    private final List<String> sourceNames;

    /** 本次编译是否增量模式。 */
    private final boolean incremental;

    /** 本次编译编排模式。 */
    private final String orchestrationMode;

    /** 本次编译审查模式。 */
    private final String reviewMode;

    /**
     * 作业原始状态（如 {@code RUNNING} / {@code SUCCESS} / {@code FAILED}）。
     *
     * <p>由作业引擎直接写入。前端应优先使用 {@code derivedStatus} 展示。</p>
     */
    private final String status;

    /**
     * 派生展示状态。
     *
     * <p>由 {@code CompileJobDerivedStatusResolver} 根据 {@code status} + {@code reviewSummary}
     * 计算得出，供前端展示用，语义比原始 {@code status} 更丰富。</p>
     */
    private final String derivedStatus;

    /**
     * 当前执行 worker 标识。
     *
     * <p>为 {@code null} 表示无 worker 认领该作业。</p>
     */
    private final String workerId;

    /**
     * 当前执行步骤名（如 {@code parsing} / {@code reviewing} / {@code persisting}）。
     */
    private final String currentStep;

    /** 当前进度计数。 */
    private final int progressCurrent;

    /**
     * 总进度计数。
     *
     * <p>{@code 0} 表示无法估算总进度。</p>
     */
    private final int progressTotal;

    /** 进度提示文案。 */
    private final String progressMessage;

    /**
     * 最近心跳时间（ISO-8601 字符串）。
     *
     * <p>超过租约无心跳则作业被认为失活，其他 worker 可抢占。</p>
     */
    private final String lastHeartbeatAt;

    /**
     * 运行租约到期时间（ISO-8601 字符串）。
     *
     * <p>过期后其他 worker 可抢占该作业。</p>
     */
    private final String runningExpiresAt;

    /**
     * 错误码。
     *
     * <p>为 {@code null} 表示无错误。</p>
     */
    private final String errorCode;

    /**
     * 错误详情文本。
     *
     * <p>可能包含编译异常栈或 LLM 返回的错误原文。
     * 仅用于管理侧排查，不应展示给终端用户或记录到公开日志。</p>
     */
    private final String errorMessage;

    /** 本次编译已持久化的文章数。 */
    private final int persistedCount;

    /**
     * 重试次数（含当前执行）。
     */
    private final int attemptCount;

    /**
     * 编译审查摘要。
     *
     * <p>为 {@code null} 表示无审查步骤或审查未执行。</p>
     */
    private final AdminCompileReviewSummaryResponse reviewSummary;

    /** 作业提交时间（ISO-8601 字符串）。 */
    private final String requestedAt;

    /**
     * 作业开始执行时间（ISO-8601 字符串）。
     *
     * <p>为 {@code null} 表示尚未开始。</p>
     */
    private final String startedAt;

    /**
     * 作业完成时间（ISO-8601 字符串）。
     *
     * <p>为 {@code null} 表示未完成。</p>
     */
    private final String finishedAt;

    /**
     * 创建管理侧编译作业响应。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @param sourceNames 来源文件名列表
     * @param incremental 是否增量编译
     * @param orchestrationMode 编排模式
     * @param reviewMode 审查模式
     * @param status 状态
     * @param derivedStatus 派生展示状态
     * @param workerId worker 标识
     * @param currentStep 当前执行步骤
     * @param progressCurrent 当前进度数量
     * @param progressTotal 总进度数量
     * @param progressMessage 进度提示文案
     * @param lastHeartbeatAt 最近心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @param errorCode 错误码
     * @param persistedCount 持久化数量
     * @param errorMessage 错误信息
     * @param attemptCount 尝试次数
     * @param reviewSummary 审查摘要
     * @param requestedAt 提交时间
     * @param startedAt 开始时间
     * @param finishedAt 完成时间
     */
    public AdminCompileJobResponse(
            String jobId,
            String sourceDir,
            List<String> sourceNames,
            boolean incremental,
            String orchestrationMode,
            String reviewMode,
            String status,
            String derivedStatus,
            String workerId,
            String currentStep,
            int progressCurrent,
            int progressTotal,
            String progressMessage,
            String lastHeartbeatAt,
            String runningExpiresAt,
            String errorCode,
            int persistedCount,
            String errorMessage,
            int attemptCount,
            AdminCompileReviewSummaryResponse reviewSummary,
            String requestedAt,
            String startedAt,
            String finishedAt
    ) {
        this.jobId = jobId;
        this.sourceDir = sourceDir;
        this.sourceNames = sourceNames;
        this.incremental = incremental;
        this.orchestrationMode = orchestrationMode;
        this.reviewMode = reviewMode;
        this.status = status;
        this.derivedStatus = derivedStatus;
        this.workerId = workerId;
        this.currentStep = currentStep;
        this.progressCurrent = progressCurrent;
        this.progressTotal = progressTotal;
        this.progressMessage = progressMessage;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.runningExpiresAt = runningExpiresAt;
        this.errorCode = errorCode;
        this.persistedCount = persistedCount;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount;
        this.reviewSummary = reviewSummary;
        this.requestedAt = requestedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }
}
