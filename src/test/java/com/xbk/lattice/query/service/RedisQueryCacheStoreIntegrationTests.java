package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisQueryCacheStore 集成测试
 *
 * 职责：验证真实 Redis 环境下的查询缓存读写与 TTL 行为
 *
 * @author xiexu
 */
class RedisQueryCacheStoreIntegrationTests {

    private LettuceConnectionFactory lettuceConnectionFactory;

    private StringRedisTemplate stringRedisTemplate;

    /**
     * 初始化真实 Redis 连接。
     */
    @BeforeEach
    void setUp() {
        lettuceConnectionFactory = new LettuceConnectionFactory("127.0.0.1", 6379);
        lettuceConnectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    /**
     * 清理 Redis 测试数据并关闭连接。
     */
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        lettuceConnectionFactory.destroy();
    }

    /**
     * 验证真实 Redis 环境下可完成查询缓存读写与 TTL 设置。
     */
    @Test
    void shouldReadAndWriteQueryCacheAgainstRealRedis() {
        QueryCacheProperties queryCacheProperties = new QueryCacheProperties();
        queryCacheProperties.setKeyPrefix("test:llm:query:cache:");
        queryCacheProperties.setTtlSeconds(120);

        RedisQueryCacheStore redisQueryCacheStore = new RedisQueryCacheStore(
                new StringRedisKeyValueStore(stringRedisTemplate),
                new ObjectMapper(),
                queryCacheProperties
        );
        QueryResponse queryResponse = new QueryResponse(
                "Payment Timeout：retry=3",
                List.of(new QuerySourceResponse("payment-timeout", "Payment Timeout", List.of("payment/analyze.json"))),
                List.of(new QueryArticleResponse("payment-timeout", "Payment Timeout")),
                null,
                "PASSED"
        );

        redisQueryCacheStore.put("payment-timeout", queryResponse);
        Optional<QueryResponse> cachedResponse = redisQueryCacheStore.get("payment-timeout");
        Long expireSeconds = stringRedisTemplate.getExpire("test:llm:query:cache:payment-timeout");

        assertThat(cachedResponse).isPresent();
        assertThat(cachedResponse.orElseThrow().getAnswer()).isEqualTo("Payment Timeout：retry=3");
        assertThat(cachedResponse.orElseThrow().getReviewStatus()).isEqualTo("PASSED");
        assertThat(expireSeconds).isNotNull();
        assertThat(expireSeconds).isPositive();
        assertThat(expireSeconds).isLessThanOrEqualTo(120L);
    }
}
