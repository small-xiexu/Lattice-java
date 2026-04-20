package com.xbk.lattice.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.api.query.QueryResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 查询缓存存储
 *
 * 职责：将查询结果写入 Redis 并按 TTL 过期
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class RedisQueryCacheStore implements QueryCacheStore {

    private final RedisKeyValueStore redisKeyValueStore;

    private final ObjectMapper objectMapper;

    private final QueryCacheProperties queryCacheProperties;

    /**
     * 创建 Redis 查询缓存存储。
     *
     * @param redisKeyValueStore Redis 键值存储
     * @param objectMapper JSON 映射器
     * @param queryCacheProperties 查询缓存配置
     */
    public RedisQueryCacheStore(
            RedisKeyValueStore redisKeyValueStore,
            ObjectMapper objectMapper,
            QueryCacheProperties queryCacheProperties
    ) {
        this.redisKeyValueStore = redisKeyValueStore;
        this.objectMapper = objectMapper;
        this.queryCacheProperties = queryCacheProperties;
    }

    /**
     * 从 Redis 读取查询结果。
     *
     * @param cacheKey 缓存键
     * @return 查询结果
     */
    @Override
    public Optional<QueryResponse> get(String cacheKey) {
        String value = redisKeyValueStore.get(buildRedisKey(cacheKey));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, QueryResponse.class));
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("反序列化查询缓存失败", ex);
        }
    }

    /**
     * 写入 Redis 查询结果。
     *
     * @param cacheKey 缓存键
     * @param queryResponse 查询结果
     */
    @Override
    public void put(String cacheKey, QueryResponse queryResponse) {
        try {
            String value = objectMapper.writeValueAsString(queryResponse);
            Duration ttl = Duration.ofSeconds(queryCacheProperties.getTtlSeconds());
            redisKeyValueStore.set(buildRedisKey(cacheKey), value, ttl);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化查询缓存失败", ex);
        }
    }

    /**
     * 清空当前前缀下的全部查询缓存。
     */
    @Override
    public void evictAll() {
        redisKeyValueStore.deleteByPrefix(queryCacheProperties.getKeyPrefix());
    }

    /**
     * 构建 Redis 缓存键。
     *
     * @param cacheKey 业务缓存键
     * @return Redis 键
     */
    private String buildRedisKey(String cacheKey) {
        return queryCacheProperties.getKeyPrefix() + cacheKey;
    }
}
