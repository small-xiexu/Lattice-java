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

    /**
     * Query 重写开关。
     *
     * <p>默认 {@code true}。{@code false} 时跳过 LLM 重写步骤，原始 query 直接检索
     * （fail-open：不影响检索可用性，但召回质量可能下降）。</p>
     */
    private boolean rewriteEnabled = true;

    /**
     * 最大重写轮次。
     *
     * <p>默认 1。每轮重写后重新评估检索结果质量。每增一轮增加一次 LLM 调用成本。</p>
     */
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
