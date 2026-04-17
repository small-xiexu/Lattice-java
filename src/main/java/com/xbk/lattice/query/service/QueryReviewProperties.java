package com.xbk.lattice.query.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 查询审查配置
 *
 * 职责：承载问答图中的重写开关与最大重写轮次
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.query.review")
public class QueryReviewProperties {

    private boolean rewriteEnabled = true;

    private int maxRewriteRounds = 1;

    /**
     * 是否启用重写。
     *
     * @return 是否启用重写
     */
    public boolean isRewriteEnabled() {
        return rewriteEnabled;
    }

    /**
     * 设置是否启用重写。
     *
     * @param rewriteEnabled 是否启用重写
     */
    public void setRewriteEnabled(boolean rewriteEnabled) {
        this.rewriteEnabled = rewriteEnabled;
    }

    /**
     * 获取最大重写轮次。
     *
     * @return 最大重写轮次
     */
    public int getMaxRewriteRounds() {
        return maxRewriteRounds;
    }

    /**
     * 设置最大重写轮次。
     *
     * @param maxRewriteRounds 最大重写轮次
     */
    public void setMaxRewriteRounds(int maxRewriteRounds) {
        this.maxRewriteRounds = maxRewriteRounds;
    }
}
