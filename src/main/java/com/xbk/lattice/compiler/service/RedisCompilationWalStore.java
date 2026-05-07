package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompilationWalProperties;
import com.xbk.lattice.compiler.domain.MergedConcept;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 编译 WAL 存储
 *
 * 职责：将待提交概念暂存到 Redis，支持跨进程重试恢复
 *
 * <p>Key 方案：</p>
 * <ul>
 *   <li>{keyPrefix}{jobId}:p — Hash，字段为 conceptId，值为 JSON(MergedConcept)</li>
 *   <li>{keyPrefix}{jobId}:c — Set，元素为已提交的 conceptId</li>
 * </ul>
 *
 * @author xiexu
 */
@Slf4j
@Service
public class RedisCompilationWalStore implements CompilationWalStore {

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final CompilationWalProperties walProperties;

    /**
     * 创建 Redis 编译 WAL 存储。
     *
     * @param stringRedisTemplate Redis 字符串模板
     * @param objectMapper JSON 映射器
     * @param walProperties WAL 配置
     */
    public RedisCompilationWalStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            CompilationWalProperties walProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.walProperties = walProperties;
    }

    /**
     * 将待提交概念暂存到 Redis Hash，并设置 TTL。
     *
     * @param jobId 作业标识
     * @param mergedConcepts 合并概念列表
     */
    @Override
    public void stage(String jobId, List<MergedConcept> mergedConcepts) {
        String pendingKey = buildPendingKey(jobId);
        Map<String, String> entries = new LinkedHashMap<String, String>();
        for (MergedConcept concept : mergedConcepts) {
            entries.put(concept.getConceptId(), serialize(concept));
        }
        stringRedisTemplate.opsForHash().putAll(pendingKey, entries);
        Duration ttl = Duration.ofSeconds(walProperties.getTtlSeconds());
        stringRedisTemplate.expire(pendingKey, ttl);
        log.debug("WAL staged jobId: {}, concepts: {}", jobId, entries.keySet());
    }

    /**
     * 从 Redis 读取尚未提交的概念（排除已在 committed Set 中的条目）。
     *
     * @param jobId 作业标识
     * @return 尚未提交的概念列表
     */
    @Override
    public List<MergedConcept> loadPendingConcepts(String jobId) {
        String pendingKey = buildPendingKey(jobId);
        String committedKey = buildCommittedKey(jobId);
        Map<Object, Object> rawEntries = stringRedisTemplate.opsForHash().entries(pendingKey);
        Set<String> committedIds = stringRedisTemplate.opsForSet().members(committedKey);
        if (committedIds == null) {
            committedIds = Collections.emptySet();
        }
        List<MergedConcept> pendingConcepts = new ArrayList<MergedConcept>();
        for (Map.Entry<Object, Object> entry : rawEntries.entrySet()) {
            String conceptId = (String) entry.getKey();
            if (!committedIds.contains(conceptId)) {
                pendingConcepts.add(deserialize((String) entry.getValue()));
            }
        }
        return pendingConcepts;
    }

    /**
     * 将 conceptId 加入 Redis committed Set，并刷新 TTL。
     *
     * @param jobId 作业标识
     * @param conceptId 概念标识
     */
    @Override
    public void markCommitted(String jobId, String conceptId) {
        String committedKey = buildCommittedKey(jobId);
        stringRedisTemplate.opsForSet().add(committedKey, conceptId);
        Duration ttl = Duration.ofSeconds(walProperties.getTtlSeconds());
        stringRedisTemplate.expire(committedKey, ttl);
    }

    /**
     * 构建 pending Hash 的 Redis Key。
     *
     * @param jobId 作业标识
     * @return Redis Key
     */
    private String buildPendingKey(String jobId) {
        return walProperties.getKeyPrefix() + jobId + ":p";
    }

    /**
     * 构建 committed Set 的 Redis Key。
     *
     * @param jobId 作业标识
     * @return Redis Key
     */
    private String buildCommittedKey(String jobId) {
        return walProperties.getKeyPrefix() + jobId + ":c";
    }

    /**
     * 将 MergedConcept 序列化为 JSON 字符串。
     *
     * @param concept 合并概念
     * @return JSON 字符串
     */
    private String serialize(MergedConcept concept) {
        try {
            return objectMapper.writeValueAsString(concept);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 WAL 条目失败 conceptId: " + concept.getConceptId(), ex);
        }
    }

    /**
     * 将 JSON 字符串反序列化为 MergedConcept。
     *
     * @param json JSON 字符串
     * @return 合并概念
     */
    private MergedConcept deserialize(String json) {
        try {
            return objectMapper.readValue(json, MergedConcept.class);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("反序列化 WAL 条目失败", ex);
        }
    }
}
