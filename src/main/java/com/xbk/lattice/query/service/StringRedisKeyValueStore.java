package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * 基于 StringRedisTemplate 的键值存储
 *
 * 职责：把查询缓存读写映射为 Redis 字符串操作
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class StringRedisKeyValueStore implements RedisKeyValueStore {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 键值存储。
     *
     * @param stringRedisTemplate Redis 字符串模板
     */
    public StringRedisKeyValueStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 读取 Redis 字符串值。
     *
     * @param key Redis 键
     * @return 字符串值
     */
    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 写入 Redis 字符串值并设置 TTL。
     *
     * @param key Redis 键
     * @param value 字符串值
     * @param ttl 过期时间
     */
    @Override
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 读取 Redis 剩余 TTL 秒数。
     *
     * @param key Redis 键
     * @return TTL 秒数
     */
    @Override
    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    /**
     * 删除指定前缀下的全部键。
     *
     * @param keyPrefix Redis 键前缀
     */
    @Override
    public void deleteByPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return;
        }
        Set<String> keys = stringRedisTemplate.keys(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        stringRedisTemplate.delete(keys);
    }
}
