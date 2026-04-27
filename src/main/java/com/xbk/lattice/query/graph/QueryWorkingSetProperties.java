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

    private String store = "redis";

    private String keyPrefix = "lattice:query:ws:";

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
