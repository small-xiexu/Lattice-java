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

    private boolean autoFixEnabled = true;

    private int maxFixRounds = 1;

    private boolean allowPersistNeedsHumanReview = true;

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
}
