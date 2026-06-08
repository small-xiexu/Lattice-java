package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FTS 配置解析测试
 *
 * 职责：验证 pg_jieba 配置的启用、检测与降级逻辑
 *
 * @author xiexu
 */
class FtsConfigResolverTests {

    /**
     * 验证查询端 ts config 固定为 simple，与写入端 to_tsvector('simple', ...) 保持一致。
     * 原 preferred-ts-config 优先行为已移除，避免查询端与写入端 ts config 不匹配
     * 导致结构化字段值（如 "A"、"SUP-001"、"2.5"）FTS 匹配失败。
     */
    @Test
    void shouldUsePreferredTsConfigWhenCapabilityExists() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getFts().setEnabled(true);
        querySearchProperties.getFts().setPreferredTsConfig("lattice_jieba_copy");

        FtsConfigResolver ftsConfigResolver = new FtsConfigResolver(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true)
        );

        assertThat(ftsConfigResolver.resolveArticleTsConfig()).isEqualTo("simple");
    }

    /**
     * 验证首选 ts config 不存在时，会自动回退到 simple。
     */
    @Test
    void shouldFallbackToSimpleWhenPreferredTsConfigMissing() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getFts().setEnabled(true);
        querySearchProperties.getFts().setPreferredTsConfig("missing_cfg");

        FtsConfigResolver ftsConfigResolver = new FtsConfigResolver(
                querySearchProperties,
                new FixedSearchCapabilityService(false, true, true)
        );

        assertThat(ftsConfigResolver.resolveArticleTsConfig()).isEqualTo("simple");
    }

    /**
     * 验证关闭增强 FTS 后，会直接回退到 fallback 配置。
     */
    @Test
    void shouldFallbackWhenEnhancedFtsDisabled() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getFts().setEnabled(false);
        querySearchProperties.getFts().setPreferredTsConfig("lattice_jieba_copy");
        querySearchProperties.getFts().setFallbackTsConfig("simple");

        FtsConfigResolver ftsConfigResolver = new FtsConfigResolver(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true)
        );

        assertThat(ftsConfigResolver.resolveArticleTsConfig()).isEqualTo("simple");
    }

    /**
     * 固定能力探测替身。
     *
     * @author xiexu
     */
    private static class FixedSearchCapabilityService implements SearchCapabilityService {

        private final boolean textSearchConfigAvailable;

        private final boolean vectorTypeAvailable;

        private final boolean vectorIndexAvailable;

        /**
         * 创建固定能力探测替身。
         *
         * @param textSearchConfigAvailable 文本搜索配置是否可用
         * @param vectorTypeAvailable 向量类型是否可用
         * @param vectorIndexAvailable 向量索引表是否可用
         */
        private FixedSearchCapabilityService(
                boolean textSearchConfigAvailable,
                boolean vectorTypeAvailable,
                boolean vectorIndexAvailable
        ) {
            this.textSearchConfigAvailable = textSearchConfigAvailable;
            this.vectorTypeAvailable = vectorTypeAvailable;
            this.vectorIndexAvailable = vectorIndexAvailable;
        }

        /**
         * 返回文本搜索配置是否可用。
         *
         * @param configName 配置名
         * @return 是否可用
         */
        @Override
        public boolean supportsTextSearchConfig(String configName) {
            return textSearchConfigAvailable;
        }

        /**
         * 返回向量类型是否可用。
         *
         * @return 是否可用
         */
        @Override
        public boolean supportsVectorType() {
            return vectorTypeAvailable;
        }

        /**
         * 返回向量索引表是否可用。
         *
         * @return 是否可用
         */
        @Override
        public boolean hasArticleVectorIndex() {
            return vectorIndexAvailable;
        }
    }
}
