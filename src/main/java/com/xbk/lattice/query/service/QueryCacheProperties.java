package com.xbk.lattice.query.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 查询缓存配置
 *
 * 职责：承载查询缓存 Redis Key 前缀与 TTL 配置
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.query.cache")
public class QueryCacheProperties {

    /**
     * LLM 查询缓存 Redis Key 前缀。
     *
     * <p>默认 {@code "llm:query:cache:"}。</p>
     */
    private String keyPrefix = "llm:query:cache:";

    /**
     * 缓存 TTL 秒数。
     *
     * <p>默认 3600（1 小时）。过期后相同 query 重新调用 LLM，增加成本和延迟。
     * 过长导致答案陈旧；过短导致 LLM 调用频率和成本上升。</p>
     */
    private long ttlSeconds = 3600L;

    /**
     * 获取缓存 key 前缀。
     *
     * @return key 前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置缓存 key 前缀。
     *
     * @param keyPrefix key 前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 获取缓存 TTL 秒数。
     *
     * @return TTL 秒数
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 设置缓存 TTL 秒数。
     *
     * @param ttlSeconds TTL 秒数
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
