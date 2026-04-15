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

        private String embeddingModel = "text-embedding-3-small";

        private int expectedDimensions = 1536;

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
}
