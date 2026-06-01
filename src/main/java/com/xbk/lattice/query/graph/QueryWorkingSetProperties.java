package com.xbk.lattice.query.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Query 工作集配置
 *
 * 职责：承载 Query working set 的存储模式、Redis Key 前缀与 TTL
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.query.working-set")
public class QueryWorkingSetProperties {

    /**
     * 存储模式。
     *
     * <p>默认 {@code "redis"}。{@code "inmemory"} 时 working set 仅存在于当前 JVM 内存中，
     * 不跨请求持久化，服务重启后丢失所有上下文。</p>
     */
    private String store = "redis";

    /**
     * Redis Key 前缀。
     *
     * <p>默认 {@code "lattice:query:ws:"}。用于隔离不同环境的 working set 数据。</p>
     */
    private String keyPrefix = "lattice:query:ws:";

    /**
     * Working set 条目 TTL 秒数。
     *
     * <p>默认 86400（24 小时）。过期后自动清理，影响跨轮次对话的上下文保留时长。
     * 过短导致多轮对话上下文丢失；过长占用额外 Redis 内存。</p>
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
