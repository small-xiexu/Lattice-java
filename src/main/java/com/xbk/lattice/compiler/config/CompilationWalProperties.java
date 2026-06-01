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

    /**
     * Redis WAL Key 前缀。
     *
     * <p>默认 {@code lattice:wal:}。所有 WAL 条目使用此前缀拼接唯一标识。
     * 修改后旧前缀的条目不会被自动清理，需手动或等待 TTL 过期。</p>
     */
    private String keyPrefix = "lattice:wal:";

    /**
     * WAL 条目 TTL 秒数。
     *
     * <p>默认 86400（24 小时）。超过此时间的 WAL 条目由 Redis 自动清理。
     * 过短导致编译作业审计记录提前丢失；过长占用额外 Redis 内存。</p>
     */
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
