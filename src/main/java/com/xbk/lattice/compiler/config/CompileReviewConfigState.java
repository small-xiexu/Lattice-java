package com.xbk.lattice.compiler.config;

import java.time.OffsetDateTime;

/**
 * Compile 审查配置状态
 *
 * 职责：承载后台可读写的 compile review 运行时配置快照
 *
 * @author xiexu
 */
public class CompileReviewConfigState {

    private final boolean autoFixEnabled;

    private final int maxFixRounds;

    private final boolean allowPersistNeedsHumanReview;

    private final String humanReviewSeverityThreshold;

    private final String configSource;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建 Compile 审查配置状态。
     *
     * @param autoFixEnabled 是否启用自动修复
     * @param maxFixRounds 自动修复最大轮次
     * @param allowPersistNeedsHumanReview 是否允许需人工复核文章落库
     * @param humanReviewSeverityThreshold 触发人工复核的最低严重度阈值
     * @param configSource 配置来源
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public CompileReviewConfigState(
            boolean autoFixEnabled,
            int maxFixRounds,
            boolean allowPersistNeedsHumanReview,
            String humanReviewSeverityThreshold,
            String configSource,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
