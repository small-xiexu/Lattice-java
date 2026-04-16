package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 调用配置
 *
 * 职责：承载编译/审查模型、预算与缓存参数
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.llm")
public class LlmProperties {

    private String compileModel = "openai";

    private String reviewerModel = "anthropic";

    private double budgetUsd = 10.0D;

    private long cacheTtlSeconds = 86400L;

    private String cacheKeyPrefix = "llm:cache:";

    private boolean reviewEnabled = false;

    private int maxInputChars = 64000;

    /**
     * 获取编译模型标识。
     *
     * @return 编译模型标识
     */
    public String getCompileModel() {
        return compileModel;
    }

    /**
     * 设置编译模型标识。
     *
     * @param compileModel 编译模型标识
     */
    public void setCompileModel(String compileModel) {
        this.compileModel = compileModel;
    }

    /**
     * 获取审查模型标识。
     *
     * @return 审查模型标识
     */
    public String getReviewerModel() {
        return reviewerModel;
    }

    /**
     * 设置审查模型标识。
     *
     * @param reviewerModel 审查模型标识
     */
    public void setReviewerModel(String reviewerModel) {
        this.reviewerModel = reviewerModel;
    }

    /**
     * 获取预算上限（美元）。
     *
     * @return 预算上限
     */
    public double getBudgetUsd() {
        return budgetUsd;
    }

    /**
     * 设置预算上限（美元）。
     *
     * @param budgetUsd 预算上限
     */
    public void setBudgetUsd(double budgetUsd) {
        this.budgetUsd = budgetUsd;
    }

    /**
     * 获取缓存 TTL 秒数。
     *
     * @return 缓存 TTL 秒数
     */
    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    /**
     * 设置缓存 TTL 秒数。
     *
     * @param cacheTtlSeconds 缓存 TTL 秒数
     */
    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /**
     * 获取缓存 key 前缀。
     *
     * @return 缓存 key 前缀
     */
    public String getCacheKeyPrefix() {
        return cacheKeyPrefix;
    }

    /**
     * 设置缓存 key 前缀。
     *
     * @param cacheKeyPrefix 缓存 key 前缀
     */
    public void setCacheKeyPrefix(String cacheKeyPrefix) {
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    /**
     * 是否启用真实审查。
     *
     * @return 是否启用真实审查
     */
    public boolean isReviewEnabled() {
        return reviewEnabled;
    }

    /**
     * 设置是否启用真实审查。
     *
     * @param reviewEnabled 是否启用真实审查
     */
    public void setReviewEnabled(boolean reviewEnabled) {
        this.reviewEnabled = reviewEnabled;
    }

    /**
     * 获取单次调用的最大输入字符数。
     *
     * @return 最大输入字符数
     */
    public int getMaxInputChars() {
        return maxInputChars;
    }

    /**
     * 设置单次调用的最大输入字符数。
     *
     * @param maxInputChars 最大输入字符数
     */
    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }
}
