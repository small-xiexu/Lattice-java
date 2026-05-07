package com.xbk.lattice.query.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 查询检索配置
 *
 * 职责：承载 FTS 与向量检索增强相关参数
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.query.search")
public class QuerySearchProperties {

    private FtsProperties fts = new FtsProperties();

    private VectorProperties vector = new VectorProperties();

    private RetrievalDispatchProperties retrievalDispatch = new RetrievalDispatchProperties();

    /**
     * 获取 FTS 配置。
     *
     * @return FTS 配置
     */
    public FtsProperties getFts() {
        return fts;
    }

    /**
     * 设置 FTS 配置。
     *
     * @param fts FTS 配置
     */
    public void setFts(FtsProperties fts) {
        this.fts = fts;
    }

    /**
     * 获取向量检索配置。
     *
     * @return 向量检索配置
     */
    public VectorProperties getVector() {
        return vector;
    }

    /**
     * 设置向量检索配置。
     *
     * @param vector 向量检索配置
     */
    public void setVector(VectorProperties vector) {
        this.vector = vector;
    }

    /**
     * 获取召回调度配置。
     *
     * @return 召回调度配置
     */
    public RetrievalDispatchProperties getRetrievalDispatch() {
        return retrievalDispatch;
    }

    /**
     * 设置召回调度配置。
     *
     * @param retrievalDispatch 召回调度配置
     */
    public void setRetrievalDispatch(RetrievalDispatchProperties retrievalDispatch) {
        this.retrievalDispatch = retrievalDispatch == null
                ? new RetrievalDispatchProperties()
                : retrievalDispatch;
    }

    /**
     * FTS 配置。
     *
     * 职责：控制 pg_jieba 相关配置探测与回退策略
     *
     * @author xiexu
     */
    public static class FtsProperties {

        private boolean enabled = true;

        private String preferredTsConfig = "jiebacfg";

        private String fallbackTsConfig = "simple";

        /**
         * 获取是否启用增强 FTS。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用增强 FTS。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取首选 ts config。
         *
         * @return 首选 ts config
         */
        public String getPreferredTsConfig() {
            return preferredTsConfig;
        }

        /**
         * 设置首选 ts config。
         *
         * @param preferredTsConfig 首选 ts config
         */
        public void setPreferredTsConfig(String preferredTsConfig) {
            this.preferredTsConfig = preferredTsConfig;
        }

        /**
         * 获取回退 ts config。
         *
         * @return 回退 ts config
         */
        public String getFallbackTsConfig() {
            return fallbackTsConfig;
        }

        /**
         * 设置回退 ts config。
         *
         * @param fallbackTsConfig 回退 ts config
         */
        public void setFallbackTsConfig(String fallbackTsConfig) {
            this.fallbackTsConfig = fallbackTsConfig;
        }
    }

    /**
     * 向量检索配置。
     *
     * 职责：控制 pgvector 检索与索引行为
     *
     * @author xiexu
     */
    public static class VectorProperties {

        private boolean enabled = false;

        private Long embeddingModelProfileId;

        private String embeddingModel = "embedding-3";

        private int expectedDimensions = 2000;

        /**
         * 获取是否启用向量检索。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用向量检索。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 embedding 模型配置主键。
         *
         * @return 模型配置主键
         */
        public Long getEmbeddingModelProfileId() {
            return embeddingModelProfileId;
        }

        /**
         * 设置 embedding 模型配置主键。
         *
         * @param embeddingModelProfileId 模型配置主键
         */
        public void setEmbeddingModelProfileId(Long embeddingModelProfileId) {
            this.embeddingModelProfileId = embeddingModelProfileId;
        }

        /**
         * 获取 embedding 模型名称。
         *
         * @return embedding 模型名称
         */
        public String getEmbeddingModel() {
            return embeddingModel;
        }

        /**
         * 设置 embedding 模型名称。
         *
         * @param embeddingModel embedding 模型名称
         */
        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        /**
         * 获取期望的 embedding 维度。
         *
         * @return 期望维度
         */
        public int getExpectedDimensions() {
            return expectedDimensions;
        }

        /**
         * 设置期望的 embedding 维度。
         *
         * @param expectedDimensions 期望维度
         */
        public void setExpectedDimensions(int expectedDimensions) {
            this.expectedDimensions = expectedDimensions;
        }
    }

    /**
     * 召回调度配置。
     *
     * 职责：控制统一 dispatcher 的并发上限、分组上限和超时预算
     *
     * @author xiexu
     */
    public static class RetrievalDispatchProperties {

        private int maxConcurrency = 4;

        private int maxConcurrencyPerGroup = 2;

        private long channelTimeoutMillis = 8_000L;

        private long totalDeadlineMillis = 12_000L;

        /**
         * 获取最大并发数。
         *
         * @return 最大并发数
         */
        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        /**
         * 设置最大并发数。
         *
         * @param maxConcurrency 最大并发数
         */
        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        /**
         * 获取单组最大并发数。
         *
         * @return 单组最大并发数
         */
        public int getMaxConcurrencyPerGroup() {
            return maxConcurrencyPerGroup;
        }

        /**
         * 设置单组最大并发数。
         *
         * @param maxConcurrencyPerGroup 单组最大并发数
         */
        public void setMaxConcurrencyPerGroup(int maxConcurrencyPerGroup) {
            this.maxConcurrencyPerGroup = maxConcurrencyPerGroup;
        }

        /**
         * 获取单通道超时毫秒。
         *
         * @return 单通道超时毫秒
         */
        public long getChannelTimeoutMillis() {
            return channelTimeoutMillis;
        }

        /**
         * 设置单通道超时毫秒。
         *
         * @param channelTimeoutMillis 单通道超时毫秒
         */
        public void setChannelTimeoutMillis(long channelTimeoutMillis) {
            this.channelTimeoutMillis = channelTimeoutMillis;
        }

        /**
         * 获取总截止毫秒。
         *
         * @return 总截止毫秒
         */
        public long getTotalDeadlineMillis() {
            return totalDeadlineMillis;
        }

        /**
         * 设置总截止毫秒。
         *
         * @param totalDeadlineMillis 总截止毫秒
         */
        public void setTotalDeadlineMillis(long totalDeadlineMillis) {
            this.totalDeadlineMillis = totalDeadlineMillis;
        }
    }
}
