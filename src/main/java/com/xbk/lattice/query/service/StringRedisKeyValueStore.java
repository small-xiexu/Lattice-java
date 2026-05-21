package com.xbk.lattice.query.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 StringRedisTemplate 的键值存储
 *
 * 职责：把查询缓存读写映射为 Redis 字符串操作
 *
 * @author xiexu
 */
@Service
@Slf4j
public class StringRedisKeyValueStore implements RedisKeyValueStore {

    private final StringRedisTemplate stringRedisTemplate;

    private final Map<String, LocalFallbackValue> interruptedFallbackValues =
            new ConcurrentHashMap<String, LocalFallbackValue>();

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
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                interruptedFallbackValues.remove(key);
                return value;
            }
            return getLocalFallbackValue(key);
        }
        catch (RuntimeException exception) {
            if (!isInterruptedRedisFailure(exception)) {
                throw exception;
            }
            String fallbackValue = getLocalFallbackValue(key);
            if (fallbackValue == null) {
                throw exception;
            }
            log.warn("Redis get interrupted, fallback to local value. key: {}", key, exception);
            return fallbackValue;
        }
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
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
            interruptedFallbackValues.remove(key);
        }
        catch (RuntimeException exception) {
            if (!isInterruptedRedisFailure(exception)) {
                throw exception;
            }
            interruptedFallbackValues.put(key, buildLocalFallbackValue(value, ttl));
            log.warn("Redis set interrupted, degrade to local fallback. key: {}", key, exception);
        }
    }

    /**
     * 读取 Redis 剩余 TTL 秒数。
     *
     * @param key Redis 键
     * @return TTL 秒数
     */
    @Override
    public Long getExpire(String key) {
        try {
            Long expireSeconds = stringRedisTemplate.getExpire(key);
            if (expireSeconds != null) {
                return expireSeconds;
            }
            return getLocalFallbackTtlSeconds(key);
        }
        catch (RuntimeException exception) {
            if (!isInterruptedRedisFailure(exception)) {
                throw exception;
            }
            Long fallbackTtlSeconds = getLocalFallbackTtlSeconds(key);
            if (fallbackTtlSeconds == null) {
                throw exception;
            }
            log.warn("Redis expire lookup interrupted, fallback to local TTL. key: {}", key, exception);
            return fallbackTtlSeconds;
        }
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
        deleteLocalFallbackByPrefix(keyPrefix);
        try {
            Set<String> keys = stringRedisTemplate.keys(keyPrefix + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            stringRedisTemplate.delete(keys);
        }
        catch (RuntimeException exception) {
            if (!isInterruptedRedisFailure(exception)) {
                throw exception;
            }
            log.warn("Redis deleteByPrefix interrupted, local fallback cleared only. keyPrefix: {}", keyPrefix, exception);
        }
    }

    /**
     * 判断异常是否属于线程中断引发的 Redis 访问失败。
     *
     * @param throwable 异常
     * @return 中断类失败返回 true
     */
    private boolean isInterruptedRedisFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException
                    || current instanceof InterruptedIOException
                    || current instanceof CancellationException) {
                return true;
            }
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("interrupted")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("redis command interrupted")) {
                return true;
            }
            current = current.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    /**
     * 构建本地 fallback 值。
     *
     * @param value Redis 字符串值
     * @param ttl 过期时间
     * @return fallback 值
     */
    private LocalFallbackValue buildLocalFallbackValue(String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return new LocalFallbackValue(value, null);
        }
        Instant expireAt = Instant.now().plus(ttl);
        return new LocalFallbackValue(value, expireAt);
    }

    /**
     * 读取未过期的本地 fallback 值。
     *
     * @param key Redis 键
     * @return fallback 值
     */
    private String getLocalFallbackValue(String key) {
        LocalFallbackValue fallbackValue = interruptedFallbackValues.get(key);
        if (fallbackValue == null) {
            return null;
        }
        if (fallbackValue.isExpired()) {
            interruptedFallbackValues.remove(key);
            return null;
        }
        return fallbackValue.getValue();
    }

    /**
     * 读取本地 fallback 的剩余 TTL 秒数。
     *
     * @param key Redis 键
     * @return TTL 秒数
     */
    private Long getLocalFallbackTtlSeconds(String key) {
        LocalFallbackValue fallbackValue = interruptedFallbackValues.get(key);
        if (fallbackValue == null) {
            return null;
        }
        if (fallbackValue.isExpired()) {
            interruptedFallbackValues.remove(key);
            return null;
        }
        return fallbackValue.remainingSeconds();
    }

    /**
     * 按前缀清理本地 fallback。
     *
     * @param keyPrefix Redis 键前缀
     */
    private void deleteLocalFallbackByPrefix(String keyPrefix) {
        for (String key : Set.copyOf(interruptedFallbackValues.keySet())) {
            if (key.startsWith(keyPrefix)) {
                interruptedFallbackValues.remove(key);
            }
        }
    }

    /**
     * 中断场景下的本地 fallback 值。
     *
     * 职责：在 Redis 命令被中断时暂存值与过期时间，供同进程后续步骤继续读取
     *
     * @author xiexu
     */
    private static final class LocalFallbackValue {

        private final String value;

        private final Instant expireAt;

        /**
         * 创建本地 fallback 值。
         *
         * @param value 字符串值
         * @param expireAt 过期时间
         */
        private LocalFallbackValue(String value, Instant expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        /**
         * 获取字符串值。
         *
         * @return 字符串值
         */
        private String getValue() {
            return value;
        }

        /**
         * 判断 fallback 是否过期。
         *
         * @return 过期返回 true
         */
        private boolean isExpired() {
            return expireAt != null && !expireAt.isAfter(Instant.now());
        }

        /**
         * 计算剩余 TTL 秒数。
         *
         * @return 剩余 TTL 秒数
         */
        private Long remainingSeconds() {
            if (expireAt == null) {
                return null;
            }
            long seconds = Duration.between(Instant.now(), expireAt).getSeconds();
            return Long.valueOf(Math.max(seconds, 0L));
        }
    }
}
