package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧 Compile 审查配置响应。
 *
 * <p>返回当前生效的 compile review 配置与来源信息，
 * 由 {@code AdminCompileReviewConfigController} 从持久化配置组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileReviewConfigResponse {

    /**
     * 当前是否启用自动修复。
     *
     * <p>{@code false} 时前端应展示"自动修复已关闭"提示，
     * 所有审查问题将直接进入人工复核队列。</p>
     */
    private final boolean autoFixEnabled;

    /**
     * 当前自动修复最大轮次。
     */
    private final int maxFixRounds;

    /**
     * 当前是否允许需人工复核文章落库。
     *
     * <p>{@code false} 时前端应展示阻止提示——仅 {@code accepted} 文章会落库，
     * {@code needs_human_review} 文章被阻塞。</p>
     */
    private final boolean allowPersistNeedsHumanReview;

    /**
     * 当前人工复核严重度阈值。
     *
     * <p>审查问题严重度 {@code >=} 此阈值时触发人工复核。</p>
     */
    private final String humanReviewSeverityThreshold;

    /**
     * 配置来源标识（如 {@code manual} / {@code auto}）。
     *
     * <p>用于管理侧追溯配置变更路径，不参与 compile 行为决策。</p>
     */
    private final String configSource;

    /** 配置创建人。 */
    private final String createdBy;

    /** 配置最后更新人。 */
    private final String updatedBy;

    /** 配置创建时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /** 配置最后更新时间（ISO-8601 字符串）。 */
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
}
