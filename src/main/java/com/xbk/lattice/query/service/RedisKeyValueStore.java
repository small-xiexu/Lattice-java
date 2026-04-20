package com.xbk.lattice.query.service;

import java.time.Duration;

/**
 * Redis 键值存储抽象
 *
 * 职责：隔离 Redis 客户端细节，便于缓存逻辑测试
 *
 * @author xiexu
 */
public interface RedisKeyValueStore {

    /**
     * 读取字符串值。
     *
     * @param key Redis 键
     * @return 字符串值
     */
    String get(String key);

    /**
     * 写入字符串值并设置 TTL。
     *
     * @param key Redis 键
     * @param value 字符串值
     * @param ttl 过期时间
     */
    void set(String key, String value, Duration ttl);

    /**
     * 读取剩余 TTL 秒数。
     *
     * @param key Redis 键
     * @return TTL 秒数
     */
    Long getExpire(String key);

    /**
     * 删除指定前缀下的全部键。
     *
     * @param keyPrefix Redis 键前缀
     */
    void deleteByPrefix(String keyPrefix);
}
