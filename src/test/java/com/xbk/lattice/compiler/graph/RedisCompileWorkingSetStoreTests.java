package com.xbk.lattice.compiler.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import com.xbk.lattice.query.service.StringRedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
     * 验证 Redis set/get 被中断时，compile working set 仍可通过本地 fallback 读回刚保存的草稿。
     */
    @Test
    void shouldLoadDraftArticlesFromLocalFallbackWhenRedisCommandIsInterrupted() {
        TestStringRedisTemplate stringRedisTemplate = new TestStringRedisTemplate();
        stringRedisTemplate.setSetException(
                new RedisSystemException("Redis command interrupted", new InterruptedException("shutdown"))
        );
        stringRedisTemplate.setGetException(
                new RedisSystemException("Redis command interrupted", new InterruptedException("shutdown"))
        );
        CompileWorkingSetProperties properties = new CompileWorkingSetProperties();
        properties.setKeyPrefix("test:compile:ws:");
        properties.setTtlSeconds(300L);
        RedisCompileWorkingSetStore redisCompileWorkingSetStore = new RedisCompileWorkingSetStore(
                new StringRedisKeyValueStore(stringRedisTemplate),
                new ObjectMapper().findAndRegisterModules(),
                properties
        );
        ArticleRecord articleRecord = new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "published",
                OffsetDateTime.now(),
                List.of("payment/timeouts.md"),
                "{}"
        );

        String draftArticlesRef = redisCompileWorkingSetStore.saveDraftArticles("job-2", List.of(articleRecord));

        assertThat(redisCompileWorkingSetStore.loadDraftArticles(draftArticlesRef)).hasSize(1);
        assertThat(redisCompileWorkingSetStore.loadDraftArticles(draftArticlesRef).get(0).getConceptId())
                .isEqualTo("payment-timeout");
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

    /**
     * RedisTemplate 测试替身。
     *
     * 职责：模拟 working set 读写时的 Redis interrupted 场景
     *
     * @author xiexu
     */
    private static final class TestStringRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

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
            return null;
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
