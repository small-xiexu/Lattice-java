package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧编译审查摘要响应。
 *
 * <p>承载 compile job 审查步骤、路由、自动修复触发情况与统计计数的可观测字段，
 * 由 compile job service 在编译完成后组装，嵌套于 {@link AdminCompileJobResponse} 中返回。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileReviewSummaryResponse {

    /**
     * 编译编排中是否包含 review 步骤。
     *
     * <p>{@code false} 时后续所有审查字段均为占位值。</p>
     */
    private final boolean reviewStepPresent;

    /** review 步骤名称。 */
    private final String reviewStepName;

    /** 执行审查的 Agent 角色（如 {@code reviewer} / {@code auditor}）。 */
    private final String reviewAgentRole;

    /** 编译请求时指定的审查模式。 */
    private final String requestedReviewMode;

    /** 实际审查模型路由。 */
    private final String reviewRoute;

    /** 审查模式前端展示文案。 */
    private final String reviewModeLabel;

    /**
     * 审查通过的文章数。
     *
     * <p>为 {@code null} 表示审查步骤未执行或统计不可用。</p>
     */
    private final Integer acceptedCount;

    /**
     * 待审查的文章数。
     *
     * <p>为 {@code null} 表示无统计。</p>
     */
    private final Integer pendingReviewCount;

    /**
     * 需要人工复核的文章数。
     *
     * <p>{@code > 0} 时前端应展示醒目的待处理提示，引导用户进入 review queue 处理。</p>
     */
    private final Integer needsHumanReviewCount;

    /**
     * 编译编排中是否包含 auto-fix 步骤。
     *
     * <p>{@code false} 时后续所有 auto-fix 字段均为占位值。</p>
     */
    private final boolean fixStepPresent;

    /** auto-fix 步骤名称。 */
    private final String fixStepName;

    /**
     * 自动修复实际尝试次数。
     *
     * <p>为 {@code null} 表示无修复步骤或统计不可用。</p>
     */
    private final Integer fixAttemptCount;

    /** 自动修复使用的模型路由。 */
    private final String fixRoute;

    /**
     * 自动修复展示文案。
     *
     * <p>由服务端生成，前端直接展示。</p>
     */
    private final String fixDisplayMessage;

    /**
     * 审查展示警示文案。
     *
     * <p>含 {@code needsHumanReviewCount > 0} 时的警告信息。
     * 为 {@code null} 表示无警示。</p>
     */
    private final String reviewDisplayWarning;

    /**
     * 创建管理侧编译审查摘要响应。
     *
     * @param reviewStepPresent 是否记录审查步骤
     * @param reviewStepName 审查步骤名称
     * @param reviewAgentRole 审查 Agent 角色
     * @param requestedReviewMode 请求审查模式
     * @param reviewRoute 审查模型路由
     * @param reviewModeLabel 审查模式展示文案
     * @param acceptedCount 审查通过数量
     * @param pendingReviewCount 待审查数量
     * @param needsHumanReviewCount 需要人工复核数量
     * @param fixStepPresent 是否记录自动修复步骤
     * @param fixStepName 自动修复步骤名称
     * @param fixAttemptCount 自动修复尝试次数
     * @param fixRoute 自动修复模型路由
     * @param fixDisplayMessage 自动修复展示文案
     * @param reviewDisplayWarning 审查展示警示文案
     */
    public AdminCompileReviewSummaryResponse(
            boolean reviewStepPresent,
            String reviewStepName,
            String reviewAgentRole,
            String requestedReviewMode,
            String reviewRoute,
            String reviewModeLabel,
            Integer acceptedCount,
            Integer pendingReviewCount,
            Integer needsHumanReviewCount,
            boolean fixStepPresent,
            String fixStepName,
            Integer fixAttemptCount,
            String fixRoute,
            String fixDisplayMessage,
            String reviewDisplayWarning
    ) {
        this.reviewStepPresent = reviewStepPresent;
        this.reviewStepName = reviewStepName;
        this.reviewAgentRole = reviewAgentRole;
        this.requestedReviewMode = requestedReviewMode;
        this.reviewRoute = reviewRoute;
        this.reviewModeLabel = reviewModeLabel;
        this.acceptedCount = acceptedCount;
        this.pendingReviewCount = pendingReviewCount;
        this.needsHumanReviewCount = needsHumanReviewCount;
        this.fixStepPresent = fixStepPresent;
        this.fixStepName = fixStepName;
        this.fixAttemptCount = fixAttemptCount;
        this.fixRoute = fixRoute;
        this.fixDisplayMessage = fixDisplayMessage;
        this.reviewDisplayWarning = reviewDisplayWarning;
    }
}
