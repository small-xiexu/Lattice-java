package com.xbk.lattice.compiler.config;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Compile 审查配置状态。
 *
 * <p>承载后台可读写的 compile review 运行时配置快照——含 auto-fix、人工复核和落库策略。
 * 与 {@code CompileReviewProperties} 对应，但作为不可变快照传递，不受配置变更影响。
 *
 * @author xiexu
 */
@Getter
public class CompileReviewConfigState {

    /**
     * 是否启用自动修复。
     *
     * <p>{@code false} 时所有审查问题直接进入人工复核队列，review queue 可能积压。</p>
     */
    private final boolean autoFixEnabled;

    /**
     * 自动修复最大轮次。
     *
     * <p>每轮修复后重新审查。过大可能导致 LLM 调用次数激增。</p>
     */
    private final int maxFixRounds;

    /**
     * 是否允许"需人工复核"文章落库。
     *
     * <p>{@code false} 时阻止 needs_human_review 文章写入，编译产出可能为零。</p>
     */
    private final boolean allowPersistNeedsHumanReview;

    /**
     * 人工复核严重度阈值。
     *
     * <p>审查问题严重度 {@code >=} 此阈值时触发人工复核。</p>
     */
    private final String humanReviewSeverityThreshold;

    /** 配置来源（如 {@code manual} / {@code auto} / {@code yaml}）。 */
    private final String configSource;

    /** 配置创建人。 */
    private final String createdBy;

    /** 配置最后更新人。 */
    private final String updatedBy;

    /** 配置创建时间。 */
    private final OffsetDateTime createdAt;

    /** 配置最后更新时间。 */
    private final OffsetDateTime updatedAt;

    /**
     * 创建 Compile 审查配置状态。
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
}
