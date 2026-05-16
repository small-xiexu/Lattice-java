package com.xbk.lattice.api.admin;

/**
 * 管理侧编译审查摘要响应。
 *
 * 职责：承载 compile job 审查步骤、路由与自动修复触发情况的可观测字段
 *
 * @author xiexu
 */
public class AdminCompileReviewSummaryResponse {

    private final boolean reviewStepPresent;

    private final String reviewStepName;

    private final String reviewAgentRole;

    private final String reviewRoute;

    private final String reviewModeLabel;

    private final Integer acceptedCount;

    private final Integer pendingReviewCount;

    private final Integer needsHumanReviewCount;

    private final boolean fixStepPresent;

    private final String fixStepName;

    private final Integer fixAttemptCount;

    private final String fixRoute;

    private final String fixDisplayMessage;

    private final String reviewDisplayWarning;

    /**
     * 创建管理侧编译审查摘要响应。
     *
     * @param reviewStepPresent 是否记录审查步骤
     * @param reviewStepName 审查步骤名称
     * @param reviewAgentRole 审查 Agent 角色
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

    /**
     * 判断是否记录审查步骤。
     *
     * @return 是否记录审查步骤
     */
    public boolean isReviewStepPresent() {
        return reviewStepPresent;
    }

    /**
     * 获取审查步骤名称。
     *
     * @return 审查步骤名称
     */
    public String getReviewStepName() {
        return reviewStepName;
    }

    /**
     * 获取审查 Agent 角色。
     *
     * @return 审查 Agent 角色
     */
    public String getReviewAgentRole() {
        return reviewAgentRole;
    }

    /**
     * 获取审查模型路由。
     *
     * @return 审查模型路由
     */
    public String getReviewRoute() {
        return reviewRoute;
    }

    /**
     * 获取审查模式展示文案。
     *
     * @return 审查模式展示文案
     */
    public String getReviewModeLabel() {
        return reviewModeLabel;
    }

    /**
     * 获取审查通过数量。
     *
     * @return 审查通过数量
     */
    public Integer getAcceptedCount() {
        return acceptedCount;
    }

    /**
     * 获取待审查数量。
     *
     * @return 待审查数量
     */
    public Integer getPendingReviewCount() {
        return pendingReviewCount;
    }

    /**
     * 获取需要人工复核数量。
     *
     * @return 需要人工复核数量
     */
    public Integer getNeedsHumanReviewCount() {
        return needsHumanReviewCount;
    }

    /**
     * 判断是否记录自动修复步骤。
     *
     * @return 是否记录自动修复步骤
     */
    public boolean isFixStepPresent() {
        return fixStepPresent;
    }

    /**
     * 获取自动修复步骤名称。
     *
     * @return 自动修复步骤名称
     */
    public String getFixStepName() {
        return fixStepName;
    }

    /**
     * 获取自动修复尝试次数。
     *
     * @return 自动修复尝试次数
     */
    public Integer getFixAttemptCount() {
        return fixAttemptCount;
    }

    /**
     * 获取自动修复模型路由。
     *
     * @return 自动修复模型路由
     */
    public String getFixRoute() {
        return fixRoute;
    }

    /**
     * 获取自动修复展示文案。
     *
     * @return 自动修复展示文案
     */
    public String getFixDisplayMessage() {
        return fixDisplayMessage;
    }

    /**
     * 获取审查展示警示文案。
     *
     * @return 审查展示警示文案
     */
    public String getReviewDisplayWarning() {
        return reviewDisplayWarning;
    }
}
