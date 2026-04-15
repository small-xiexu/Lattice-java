package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编译 WAL 配置
 *
 * 职责：承载 WAL Redis Key 前缀与 TTL 配置
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.compiler.wal")
public class CompilationWalProperties {

    private String keyPrefix = "lattice:wal:";

    private long ttlSeconds = 86400L;

    /**
     * 获取 Redis Key 前缀。
     *
     * @return Key 前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置 Redis Key 前缀。
     *
     * @param keyPrefix Key 前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 获取 WAL 条目 TTL 秒数。
     *
     * @return TTL 秒数
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 设置 WAL 条目 TTL 秒数。
     *
     * @param ttlSeconds TTL 秒数
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
