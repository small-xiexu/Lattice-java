package com.xbk.lattice.query.deepresearch.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deep Research 工作集配置
 *
 * 职责：承载 Deep Research working set 的存储模式、Redis Key 前缀与 TTL
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.deep-research.working-set")
public class DeepResearchWorkingSetProperties {

    /**
     * 存储模式。
     *
     * <p>默认 {@code "redis"}。{@code "inmemory"} 时不跨请求持久化。</p>
     */
    private String store = "redis";

    /**
     * Redis Key 前缀。
     *
     * <p>默认 {@code "lattice:deep-research:ws:"}。用于与 query working set 隔离。</p>
     */
    private String keyPrefix = "lattice:deep-research:ws:";

    /**
     * Working set 条目 TTL 秒数。
     *
     * <p>默认 86400（24 小时）。过期后 deep research 的跨步骤上下文丢失，
     * 可能影响多步研究的连贯性。</p>
     */
    private long ttlSeconds = 86400L;

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
