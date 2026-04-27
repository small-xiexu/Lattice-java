package com.xbk.lattice.compiler.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compile 工作集配置
 *
 * 职责：承载 Compile working set 的存储模式、Redis Key 前缀与 TTL
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.compiler.working-set")
public class CompileWorkingSetProperties {

    private String store = "redis";

    private String keyPrefix = "lattice:compile:ws:";

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
