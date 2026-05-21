package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StringRedisKeyValueStore 测试
 *
 * 职责：验证 Redis interrupted 场景下的最小降级与真实错误透传语义
 *
 * @author xiexu
 */
class StringRedisKeyValueStoreTests {

    /**
     * 验证 Redis set 被中断时会降级到本地 fallback，后续读取仍可拿回值。
     */
    @Test
    void shouldFallbackToLocalValueWhenRedisSetIsInterrupted() {
        TestStringRedisTemplate stringRedisTemplate = new TestStringRedisTemplate();
        stringRedisTemplate.setSetException(
                new RedisSystemException("Redis command interrupted", new InterruptedException("shutdown"))
        );
        stringRedisTemplate.setGetException(
                new RedisSystemException("Redis command interrupted", new InterruptedException("shutdown"))
        );
        StringRedisKeyValueStore redisKeyValueStore = new StringRedisKeyValueStore(stringRedisTemplate);

        redisKeyValueStore.set("compile:ws:key", "payload", Duration.ofSeconds(60));

        assertThat(redisKeyValueStore.get("compile:ws:key")).isEqualTo("payload");
        assertThat(redisKeyValueStore.getExpire("compile:ws:key"))
                .isNotNull()
                .isGreaterThanOrEqualTo(0L)
                .isLessThanOrEqualTo(60L);
    }

    /**
     * 验证非中断类 Redis 异常仍会原样冒泡，不会被误吞。
     */
    @Test
    void shouldPropagateNonInterruptedRedisFailure() {
        TestStringRedisTemplate stringRedisTemplate = new TestStringRedisTemplate();
        RedisSystemException redisSystemException =
                new RedisSystemException("serializer broken", new IllegalStateException("boom"));
        stringRedisTemplate.setSetException(redisSystemException);
        StringRedisKeyValueStore redisKeyValueStore = new StringRedisKeyValueStore(stringRedisTemplate);

        assertThatThrownBy(() -> redisKeyValueStore.set("compile:ws:key", "payload", Duration.ofSeconds(60)))
                .isSameAs(redisSystemException);
    }

    /**
     * RedisTemplate 测试替身。
     *
     * 职责：精确模拟 set/get 被中断或失败的场景
     *
     * @author xiexu
     */
    private static final class TestStringRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private final Map<String, Long> ttlSeconds = new LinkedHashMap<String, Long>();

        private final ValueOperations<String, String> valueOperations = createValueOperations();

        private RuntimeException setException;

        private RuntimeException getException;

        /**
         * 设置 set 场景异常。
         *
         * @param setException 异常
         */
        private void setSetException(RuntimeException setException) {
            this.setException = setException;
        }

        /**
         * 设置 get 场景异常。
         *
         * @param getException 异常
         */
        private void setGetException(RuntimeException getException) {
            this.getException = getException;
        }

        /**
         * 返回 ValueOperations 替身。
         *
         * @return ValueOperations
         */
        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }

        /**
         * 读取 TTL。
         *
         * @param key Redis 键
         * @return TTL 秒数
         */
        @Override
        public Long getExpire(String key) {
            return ttlSeconds.get(key);
        }

        /**
         * 删除指定键集合。
         *
         * @param keys Redis 键集合
         * @return 删除数量
         */
        @Override
        public Long delete(Collection<String> keys) {
            long deleted = 0L;
            for (String key : keys) {
                if (values.remove(key) != null) {
                    ttlSeconds.remove(key);
                    deleted++;
                }
            }
            return Long.valueOf(deleted);
        }

        /**
         * 按前缀查询键集合。
         *
         * @param pattern 查询模式
         * @return 键集合
         */
        @Override
        public Set<String> keys(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            return values.keySet().stream()
                    .filter(key -> key.startsWith(prefix))
                    .collect(java.util.stream.Collectors.toSet());
        }

        /**
         * 创建 ValueOperations 代理。
         *
         * @return ValueOperations 代理
         */
        @SuppressWarnings("unchecked")
        private ValueOperations<String, String> createValueOperations() {
            InvocationHandler invocationHandler = (proxy, method, args) -> {
                if ("set".equals(method.getName())) {
                    writeValue((String) args[0], (String) args[1], (Duration) args[2]);
                    return null;
                }
                if ("get".equals(method.getName())) {
                    return readValue((String) args[0]);
                }
                throw new UnsupportedOperationException("Unsupported method: " + method.getName());
            };
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class[]{ValueOperations.class},
                    invocationHandler
            );
        }

        /**
         * 写入字符串值。
         *
         * @param key Redis 键
         * @param value 字符串值
         * @param ttl TTL
         */
        private void writeValue(String key, String value, Duration ttl) {
            if (setException != null) {
                throw setException;
            }
            values.put(key, value);
            ttlSeconds.put(key, Long.valueOf(ttl.getSeconds()));
        }

        /**
         * 读取字符串值。
         *
         * @param key Redis 键
         * @return 字符串值
         */
        private String readValue(String key) {
            if (getException != null) {
                throw getException;
            }
            return values.get(key);
        }
    }
}
