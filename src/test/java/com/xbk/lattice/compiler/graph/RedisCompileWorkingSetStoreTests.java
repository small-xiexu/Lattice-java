package com.xbk.lattice.compiler.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisCompileWorkingSetStore 测试
 *
 * 职责：验证 Compile working set 的 Redis round-trip、TTL 与按 jobId 清理行为
 *
 * @author xiexu
 */
class RedisCompileWorkingSetStoreTests {

    /**
     * 验证编译工作集可写入 Redis 并读回核心载荷。
     */
    @Test
    void shouldRoundTripCompileWorkingSetArtifactsIntoRedis() {
        FakeRedisKeyValueStore fakeRedisKeyValueStore = new FakeRedisKeyValueStore();
        CompileWorkingSetProperties properties = new CompileWorkingSetProperties();
        properties.setKeyPrefix("test:compile:ws:");
        properties.setTtlSeconds(300L);
        RedisCompileWorkingSetStore redisCompileWorkingSetStore = new RedisCompileWorkingSetStore(
                fakeRedisKeyValueStore,
                new ObjectMapper().findAndRegisterModules(),
                properties
        );
        RawSource rawSource = RawSource.text("src/main/java/demo/App.java", "class App {}", "JAVA", 32L);
        SourceBatch sourceBatch = new SourceBatch("batch-1", "java", List.of(rawSource));
        ArticleReviewEnvelope articleReviewEnvelope = new ArticleReviewEnvelope();
        articleReviewEnvelope.setArticle(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "published",
                OffsetDateTime.now(),
                List.of("payment/timeouts.md"),
                "{}"
        ));
        ReviewPartition reviewPartition = new ReviewPartition();
        reviewPartition.setAccepted(List.of(articleReviewEnvelope));
        AstGraphExtractReport astGraphExtractReport = new AstGraphExtractReport();
        astGraphExtractReport.setEntityUpsertCount(2);

        String rawSourcesRef = redisCompileWorkingSetStore.saveRawSources("job-1", List.of(rawSource));
        String sourceBatchesRef = redisCompileWorkingSetStore.saveSourceBatches("job-1", Map.of("java", List.of(sourceBatch)));
        String reviewPartitionRef = redisCompileWorkingSetStore.saveReviewPartition("job-1", reviewPartition);
        String astReportRef = redisCompileWorkingSetStore.saveAstExtractReport("job-1", astGraphExtractReport);

        assertThat(redisCompileWorkingSetStore.loadRawSources(rawSourcesRef)).hasSize(1);
        assertThat(redisCompileWorkingSetStore.loadRawSources(rawSourcesRef).get(0).getRelativePath())
                .isEqualTo("src/main/java/demo/App.java");
        assertThat(redisCompileWorkingSetStore.loadSourceBatches(sourceBatchesRef).get("java")).hasSize(1);
        assertThat(redisCompileWorkingSetStore.loadReviewPartition(reviewPartitionRef).getAccepted()).hasSize(1);
        assertThat(redisCompileWorkingSetStore.loadAstExtractReport(astReportRef).getEntityUpsertCount()).isEqualTo(2);
        assertThat(fakeRedisKeyValueStore.getExpire("test:compile:ws:" + rawSourcesRef)).isEqualTo(Long.valueOf(300L));

        redisCompileWorkingSetStore.deleteByJobId("job-1");

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
