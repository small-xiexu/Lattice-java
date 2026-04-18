package com.xbk.lattice.query.service;

/**
 * 检索能力探测服务
 *
 * 职责：探测 PostgreSQL 检索增强能力是否可用
 *
 * @author xiexu
 */
public interface SearchCapabilityService {

    /**
     * 返回文本搜索配置是否可用。
     *
     * @param configName 配置名
     * @return 是否可用
     */
    boolean supportsTextSearchConfig(String configName);

    /**
     * 返回 vector 类型是否可用。
     *
     * @return 是否可用
     */
    boolean supportsVectorType();

    /**
     * 返回文章向量索引表是否可用。
     *
     * @return 是否可用
     */
    boolean hasArticleVectorIndex();

    /**
     * 返回文章分块向量索引表是否可用。
     *
     * @return 是否可用
     */
    default boolean hasArticleChunkVectorIndex() {
        return false;
    }

    /**
     * 返回默认禁用的能力探测实现。
     *
     * @return 禁用实现
     */
    static SearchCapabilityService disabled() {
        return DisabledSearchCapabilityServiceHolder.INSTANCE;
    }

    /**
     * 默认禁用实现持有者。
     *
     * @author xiexu
     */
    final class DisabledSearchCapabilityServiceHolder {

        private static final SearchCapabilityService INSTANCE = new SearchCapabilityService() {

            /**
             * 返回文本搜索配置不可用。
             *
             * @param configName 配置名
             * @return false
             */
            @Override
            public boolean supportsTextSearchConfig(String configName) {
                return false;
            }

            /**
             * 返回向量类型不可用。
             *
             * @return false
             */
            @Override
            public boolean supportsVectorType() {
                return false;
            }

            /**
             * 返回向量索引表不可用。
             *
             * @return false
             */
            @Override
            public boolean hasArticleVectorIndex() {
                return false;
            }

            /**
             * 返回分块向量索引表不可用。
             *
             * @return false
             */
            @Override
            public boolean hasArticleChunkVectorIndex() {
                return false;
            }
        };

        private DisabledSearchCapabilityServiceHolder() {
        }
    }
}
