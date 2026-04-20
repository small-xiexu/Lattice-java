package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisQueryCacheStore 测试
 *
 * 职责：验证 Redis 查询缓存可读写并写入 TTL
 *
 * @author xiexu
 */
class RedisQueryCacheStoreTests {

    private static final String CACHE_KEY = "payment-timeout";

    /**
     * 验证 Redis 查询缓存可写入结果、读回结果，并带有 TTL。
     */
    @Test
    void shouldStoreQueryResponseIntoRedisWithTtl() {
        FakeRedisKeyValueStore fakeRedisKeyValueStore = new FakeRedisKeyValueStore();
        QueryCacheProperties properties = new QueryCacheProperties();
        properties.setKeyPrefix("llm:query:cache:");
        properties.setTtlSeconds(3600);

        RedisQueryCacheStore redisQueryCacheStore = new RedisQueryCacheStore(
                fakeRedisKeyValueStore,
                new ObjectMapper(),
                properties
        );
        QueryResponse queryResponse = new QueryResponse(
                "Payment Timeout：retry=3",
                List.of(new QuerySourceResponse("payment-timeout", "Payment Timeout", List.of("payment/analyze.json"))),
                List.of(new QueryArticleResponse("payment-timeout", "Payment Timeout"))
        );

        redisQueryCacheStore.put(CACHE_KEY, queryResponse);
        Optional<QueryResponse> cachedResponse = redisQueryCacheStore.get(CACHE_KEY);
        Long expireSeconds = fakeRedisKeyValueStore.getExpire("llm:query:cache:" + CACHE_KEY);

        assertThat(cachedResponse).isPresent();
        assertThat(cachedResponse.orElseThrow().getAnswer()).isEqualTo("Payment Timeout：retry=3");
        assertThat(expireSeconds).isNotNull();
        assertThat(expireSeconds).isPositive();
        assertThat(expireSeconds).isLessThanOrEqualTo(3600L);
    }

    /**
     * 验证可按前缀清空查询缓存。
     */
    @Test
    void shouldEvictAllQueryCacheByPrefix() {
        FakeRedisKeyValueStore fakeRedisKeyValueStore = new FakeRedisKeyValueStore();
        QueryCacheProperties properties = new QueryCacheProperties();
        properties.setKeyPrefix("llm:query:cache:");
        properties.setTtlSeconds(3600);

        RedisQueryCacheStore redisQueryCacheStore = new RedisQueryCacheStore(
                fakeRedisKeyValueStore,
                new ObjectMapper(),
                properties
        );
        QueryResponse queryResponse = new QueryResponse(
                "cached",
                List.of(),
                List.of(),
                null,
                "PASSED"
        );

        redisQueryCacheStore.put("question-a", queryResponse);
        redisQueryCacheStore.put("question-b", queryResponse);
        redisQueryCacheStore.evictAll();

        assertThat(redisQueryCacheStore.get("question-a")).isEmpty();
        assertThat(redisQueryCacheStore.get("question-b")).isEmpty();
    }

    /**
     * Redis 键值存储测试替身。
     *
     * 职责：记录字符串值与 TTL，便于验证 Redis 查询缓存行为
     *
     * @author xiexu
     */
    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private final Map<String, Long> ttlSeconds = new LinkedHashMap<String, Long>();

        /**
         * 读取字符串值。
         *
         * @param key Redis 键
         * @return 字符串值
         */
        @Override
        public String get(String key) {
            return values.get(key);
        }

        /**
         * 写入字符串值并记录 TTL。
         *
         * @param key Redis 键
         * @param value 字符串值
         * @param ttl 过期时间
         */
        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            ttlSeconds.put(key, ttl.toSeconds());
        }

        /**
         * 读取剩余 TTL 秒数。
         *
         * @param key Redis 键
         * @return TTL 秒数
         */
        @Override
        public Long getExpire(String key) {
            return ttlSeconds.get(key);
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
            ttlSeconds.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }
}
