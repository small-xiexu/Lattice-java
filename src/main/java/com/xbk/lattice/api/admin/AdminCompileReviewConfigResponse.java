package com.xbk.lattice.api.admin;

/**
 * 管理侧 Compile 审查配置响应
 *
 * 职责：返回当前有效的 compile review 配置与来源信息
 *
 * @author xiexu
 */
public class AdminCompileReviewConfigResponse {

    private final boolean autoFixEnabled;

    private final int maxFixRounds;

    private final boolean allowPersistNeedsHumanReview;

    private final String humanReviewSeverityThreshold;

    private final String configSource;

    private final String createdBy;

    private final String updatedBy;

    private final String createdAt;

    private final String updatedAt;

    /**
     * 创建管理侧 Compile 审查配置响应。
     *
     * @param autoFixEnabled 是否启用自动修复
     * @param maxFixRounds 自动修复最大轮次
     * @param allowPersistNeedsHumanReview 是否允许需人工复核文章落库
     * @param humanReviewSeverityThreshold 人工复核严重度阈值
     * @param configSource 配置来源
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AdminCompileReviewConfigResponse(
            boolean autoFixEnabled,
            int maxFixRounds,
            boolean allowPersistNeedsHumanReview,
            String humanReviewSeverityThreshold,
            String configSource,
            String createdBy,
            String updatedBy,
            String createdAt,
            String updatedAt
    ) {
        this.autoFixEnabled = autoFixEnabled;
        this.maxFixRounds = maxFixRounds;
        this.allowPersistNeedsHumanReview = allowPersistNeedsHumanReview;
        this.humanReviewSeverityThreshold = humanReviewSeverityThreshold;
        this.configSource = configSource;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isAutoFixEnabled() {
        return autoFixEnabled;
    }

    public int getMaxFixRounds() {
        return maxFixRounds;
    }

    public boolean isAllowPersistNeedsHumanReview() {
        return allowPersistNeedsHumanReview;
    }

    public String getHumanReviewSeverityThreshold() {
        return humanReviewSeverityThreshold;
    }

    public String getConfigSource() {
        return configSource;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
