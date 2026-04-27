package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * Compile 审查配置记录
 *
 * 职责：承载 compile review 后台配置的持久化对象
 *
 * @author xiexu
 */
public class CompileReviewConfigRecord {

    private final String configScope;

    private final boolean autoFixEnabled;

    private final int maxFixRounds;

    private final boolean allowPersistNeedsHumanReview;

    private final String humanReviewSeverityThreshold;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建 Compile 审查配置记录。
     *
     * @param configScope 配置作用域
     * @param autoFixEnabled 是否启用自动修复
     * @param maxFixRounds 自动修复最大轮次
     * @param allowPersistNeedsHumanReview 是否允许需人工复核文章落库
     * @param humanReviewSeverityThreshold 触发人工复核的最低严重度阈值
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public CompileReviewConfigRecord(
            String configScope,
            boolean autoFixEnabled,
            int maxFixRounds,
            boolean allowPersistNeedsHumanReview,
            String humanReviewSeverityThreshold,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.configScope = configScope;
        this.autoFixEnabled = autoFixEnabled;
        this.maxFixRounds = maxFixRounds;
        this.allowPersistNeedsHumanReview = allowPersistNeedsHumanReview;
        this.humanReviewSeverityThreshold = humanReviewSeverityThreshold;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getConfigScope() {
        return configScope;
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
