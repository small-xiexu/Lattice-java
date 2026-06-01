package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编译审查配置
 *
 * 职责：承载图编排中的文章审查、自动修复与落库策略参数
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.compiler.review")
public class CompileReviewProperties {

    /**
     * 自动修复总开关。
     *
     * <p>默认 {@code true}。{@code false} 时所有审查问题直接进入人工复核队列，
     * review queue 可能快速积压。</p>
     */
    private boolean autoFixEnabled = true;

    /**
     * 自动修复最大轮次。
     *
     * <p>默认 1。每轮修复后重新审查。过大（如 10+）可能导致修复死循环，
     * LLM 成本激增。</p>
     */
    private int maxFixRounds = 1;

    /**
     * 是否允许"需人工复核"状态的文章落库。
     *
     * <p>默认 {@code false}（fail-closed）。{@code false} 时阻止所有
     * {@code needs_human_review} 文章写入，编译产出可能为零。</p>
     */
    private boolean allowPersistNeedsHumanReview = false;

    /**
     * 人工复核严重度阈值。
     *
     * <p>默认 {@code "HIGH"}。审查问题严重度 {@code >=} 此阈值时触发人工复核。
     * 设置为最低级别时几乎所有问题都需人工处理。</p>
     */
    private String humanReviewSeverityThreshold = "HIGH";

    /**
     * 是否启用自动修复。
     *
     * @return 是否启用自动修复
     */
    public boolean isAutoFixEnabled() {
        return autoFixEnabled;
    }

    /**
     * 设置是否启用自动修复。
     *
     * @param autoFixEnabled 是否启用自动修复
     */
    public void setAutoFixEnabled(boolean autoFixEnabled) {
        this.autoFixEnabled = autoFixEnabled;
    }

    /**
     * 获取最大修复轮次。
     *
     * @return 最大修复轮次
     */
    public int getMaxFixRounds() {
        return maxFixRounds;
    }

    /**
     * 设置最大修复轮次。
     *
     * @param maxFixRounds 最大修复轮次
     */
    public void setMaxFixRounds(int maxFixRounds) {
        this.maxFixRounds = maxFixRounds;
    }

    /**
     * 是否允许带需人工复核状态落库。
     *
     * @return 是否允许带需人工复核状态落库
     */
    public boolean isAllowPersistNeedsHumanReview() {
        return allowPersistNeedsHumanReview;
    }

    /**
     * 设置是否允许带需人工复核状态落库。
     *
     * @param allowPersistNeedsHumanReview 是否允许带需人工复核状态落库
     */
    public void setAllowPersistNeedsHumanReview(boolean allowPersistNeedsHumanReview) {
        this.allowPersistNeedsHumanReview = allowPersistNeedsHumanReview;
    }

    /**
     * 返回触发人工复核的最低严重度阈值。
     *
     * @return 严重度阈值
     */
    public String getHumanReviewSeverityThreshold() {
        return humanReviewSeverityThreshold;
    }

    /**
     * 设置触发人工复核的最低严重度阈值。
     *
     * @param humanReviewSeverityThreshold 严重度阈值
     */
    public void setHumanReviewSeverityThreshold(String humanReviewSeverityThreshold) {
        this.humanReviewSeverityThreshold = humanReviewSeverityThreshold;
    }
}
