package com.xbk.lattice.query.deepresearch.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisDeepResearchWorkingSetStore 测试
 *
 * 职责：验证 Deep Research working set 的 Redis round-trip、TTL 与按 queryId 清理行为
 *
 * @author xiexu
 */
class RedisDeepResearchWorkingSetStoreTests {

    /**
     * 验证计划与审计对象可写入 Redis 并读回。
     */
    @Test
    void shouldRoundTripDeepResearchArtifactsIntoRedis() {
        FakeRedisKeyValueStore fakeRedisKeyValueStore = new FakeRedisKeyValueStore();
        DeepResearchWorkingSetProperties properties = new DeepResearchWorkingSetProperties();
        properties.setKeyPrefix("test:deep:ws:");
        properties.setTtlSeconds(240L);
        RedisDeepResearchWorkingSetStore redisDeepResearchWorkingSetStore = new RedisDeepResearchWorkingSetStore(
                fakeRedisKeyValueStore,
                new ObjectMapper().findAndRegisterModules(),
                properties
        );
        LayeredResearchPlan layeredResearchPlan = new LayeredResearchPlan();
        layeredResearchPlan.setRootQuestion("支付超时怎么排查");
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-1");
        researchTask.setQuestion("支付超时重试配置在哪里");
        ResearchLayer researchLayer = new ResearchLayer();
        researchLayer.setLayerIndex(0);
        researchLayer.setTasks(List.of(researchTask));
        layeredResearchPlan.setLayers(List.of(researchLayer));
        DeepResearchAuditSnapshot deepResearchAuditSnapshot = new DeepResearchAuditSnapshot(8L, 3);

        String planRef = redisDeepResearchWorkingSetStore.savePlan("query-1", layeredResearchPlan);
        String auditRef = redisDeepResearchWorkingSetStore.saveDeepResearchAudit("query-1", deepResearchAuditSnapshot);

        assertThat(redisDeepResearchWorkingSetStore.loadPlan(planRef)).isNotNull();
        assertThat(redisDeepResearchWorkingSetStore.loadPlan(planRef).getRootQuestion()).isEqualTo("支付超时怎么排查");
        assertThat(redisDeepResearchWorkingSetStore.loadPlan(planRef).taskCount()).isEqualTo(1);
        assertThat(redisDeepResearchWorkingSetStore.loadDeepResearchAudit(auditRef)).isInstanceOf(DeepResearchAuditSnapshot.class);
        DeepResearchAuditSnapshot loadedAudit = (DeepResearchAuditSnapshot) redisDeepResearchWorkingSetStore
                .loadDeepResearchAudit(auditRef);
        assertThat(loadedAudit.getRunId()).isEqualTo(8L);
        assertThat(fakeRedisKeyValueStore.getExpire("test:deep:ws:" + planRef)).isEqualTo(Long.valueOf(240L));

        redisDeepResearchWorkingSetStore.deleteByQueryId("query-1");

        assertThat(fakeRedisKeyValueStore.values).isEmpty();
    }

    /**
     * Redis 键值存储测试替身。
     *
     * @author xiexu
     */
    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new ConcurrentHashMap<String, String>();

        private final Map<String, Long> expires = new ConcurrentHashMap<String, Long>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            expires.put(key, Long.valueOf(ttl.getSeconds()));
        }

        @Override
        public Long getExpire(String key) {
            return expires.get(key);
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            for (String key : List.copyOf(values.keySet())) {
                if (key.startsWith(keyPrefix)) {
                    values.remove(key);
                    expires.remove(key);
                }
            }
        }
    }
}
