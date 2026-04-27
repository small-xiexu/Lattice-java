package com.xbk.lattice.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.query.service.RedisKeyValueStore;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis JSON 存储基类
 *
 * 职责：封装带 TTL 的 JSON 序列化读写，供 working set / cache 等外置存储复用
 *
 * @author xiexu
 */
public abstract class AbstractRedisJsonStore {

    private final RedisKeyValueStore redisKeyValueStore;

    private final ObjectMapper objectMapper;

    private final Duration ttl;

    private final String keyPrefix;

    /**
     * 创建 Redis JSON 存储基类。
     *
     * @param redisKeyValueStore Redis 键值存储
     * @param objectMapper JSON 映射器
     * @param keyPrefix Redis 键前缀
     * @param ttlSeconds TTL 秒数
     */
    protected AbstractRedisJsonStore(
            RedisKeyValueStore redisKeyValueStore,
            ObjectMapper objectMapper,
            String keyPrefix,
            long ttlSeconds
    ) {
        this.redisKeyValueStore = redisKeyValueStore;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * 保存任意 JSON 载荷。
     *
     * @param ref 工作集引用
     * @param payload 业务载荷
     */
    protected void saveJson(String ref, Object payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            redisKeyValueStore.set(buildRedisKey(ref), value, ttl);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis working set 序列化失败, ref=" + ref, exception);
        }
    }

    /**
     * 读取指定类型的 JSON 载荷。
     *
     * @param ref 工作集引用
     * @param type 类型
     * @param <T> 泛型
     * @return 业务载荷
     */
    protected <T> T loadJson(String ref, Class<T> type) {
        String value = readValue(ref);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis working set 反序列化失败, ref=" + ref, exception);
        }
    }

    /**
     * 读取复杂泛型载荷。
     *
     * @param ref 工作集引用
     * @param typeReference 泛型类型
     * @param <T> 泛型
     * @return 业务载荷
     */
    protected <T> T loadJson(String ref, TypeReference<T> typeReference) {
        String value = readValue(ref);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis working set 反序列化失败, ref=" + ref, exception);
        }
    }

    /**
     * 读取原始字符串值。
     *
     * @param ref 工作集引用
     * @return Redis 原始值
     */
    protected String readValue(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String value = redisKeyValueStore.get(buildRedisKey(ref));
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /**
     * 按业务前缀清理全部工作集键。
     *
     * @param ownerPrefix 业务前缀
     */
    protected void deleteByOwnerPrefix(String ownerPrefix) {
        if (ownerPrefix == null || ownerPrefix.isBlank()) {
            return;
        }
        redisKeyValueStore.deleteByPrefix(keyPrefix + ownerPrefix);
    }

    /**
     * 生成唯一后缀引用。
     *
     * @param ownerId 业务主键
     * @param payloadType 载荷类型
     * @return 工作集引用
     */
    protected String buildVersionedRef(String ownerId, String payloadType) {
        return ownerId + ":" + payloadType + ":" + UUID.randomUUID();
    }

    /**
     * 拼装 Redis 键。
     *
     * @param ref 工作集引用
     * @return Redis 键
     */
    protected String buildRedisKey(String ref) {
        return keyPrefix + ref;
    }
}
