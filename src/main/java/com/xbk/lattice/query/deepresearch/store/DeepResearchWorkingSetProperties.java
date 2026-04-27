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

    private String store = "redis";

    private String keyPrefix = "lattice:deep-research:ws:";

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
