package com.xbk.lattice.query.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryIntent;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import com.xbk.lattice.query.service.RetrievalStrategy;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisQueryWorkingSetStore 测试
 *
 * 职责：验证 Query working set 的 Redis round-trip、TTL 与按 queryId 清理行为
 *
 * @author xiexu
 */
class RedisQueryWorkingSetStoreTests {

    /**
     * 验证 Query 工作集可写入 Redis 并读回关键对象。
     */
    @Test
    void shouldRoundTripQueryWorkingSetArtifactsIntoRedis() {
        FakeRedisKeyValueStore fakeRedisKeyValueStore = new FakeRedisKeyValueStore();
        QueryWorkingSetProperties properties = new QueryWorkingSetProperties();
        properties.setKeyPrefix("test:query:ws:");
        properties.setTtlSeconds(120L);
        RedisQueryWorkingSetStore redisQueryWorkingSetStore = new RedisQueryWorkingSetStore(
                fakeRedisKeyValueStore,
                new ObjectMapper().findAndRegisterModules(),
                properties
        );
        List<QueryArticleHit> hits = List.of(new QueryArticleHit(
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "{}",
                List.of("payment/timeouts.md"),
                0.91D
        ));
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "payment timeout retry policy",
                QueryIntent.TROUBLESHOOTING,
                AnswerShape.STATUS,
                true,
                60,
                new LinkedHashMap<String, Double>(Map.of(RetrievalStrategyResolver.CHANNEL_FTS, 1.1D)),
                new LinkedHashSet<String>(List.of(RetrievalStrategyResolver.CHANNEL_FTS))
        );
        QueryResponse queryResponse = new QueryResponse(
                "retry=3 [[payment-timeout]]",
                List.of(),
                List.of(),
                "query-1",
                "PASSED",
                AnswerOutcome.SUCCESS,
                GenerationMode.LLM,
                ModelExecutionStatus.SUCCESS,
                null,
                null
        );
        Citation citation = new Citation(
                1,
                "[[payment-timeout]]",
                CitationSourceType.ARTICLE,
                "payment-timeout",
                "retry=3",
                "retry=3 [[payment-timeout]]"
        );
        ClaimSegment claimSegment = new ClaimSegment(
                0,
                "retry=3",
                "retry=3 [[payment-timeout]]",
                List.of(citation)
        );
        CitationValidationResult validationResult = new CitationValidationResult(
                "payment-timeout",
                CitationSourceType.ARTICLE,
                CitationValidationStatus.VERIFIED,
                0.92D,
                "rule_overlap_verified",
                "retry=3",
                1
        );
        CitationCheckReport citationCheckReport = new CitationCheckReport(
                "retry=3 [[payment-timeout]]",
                List.of(claimSegment),
                List.of(validationResult),
                1,
                0,
                0,
                false,
                1.0D,
                0,
                0,
                0,
                0
        );

        String hitsRef = redisQueryWorkingSetStore.saveHits("query-1", "fts", hits);
        String strategyRef = redisQueryWorkingSetStore.saveRetrievalStrategy("query-1", retrievalStrategy);
        String responseRef = redisQueryWorkingSetStore.saveResponse("query-1", queryResponse);
        String reportRef = redisQueryWorkingSetStore.saveCitationCheckReport("query-1", citationCheckReport);

        assertThat(redisQueryWorkingSetStore.loadHits(hitsRef)).hasSize(1);
        assertThat(redisQueryWorkingSetStore.loadHits(hitsRef).get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(redisQueryWorkingSetStore.loadRetrievalStrategy(strategyRef)).isNotNull();
        assertThat(redisQueryWorkingSetStore.loadRetrievalStrategy(strategyRef).getQueryIntent())
                .isEqualTo(QueryIntent.TROUBLESHOOTING);
        assertThat(redisQueryWorkingSetStore.loadRetrievalStrategy(strategyRef).getAnswerShape())
                .isEqualTo(AnswerShape.STATUS);
        assertThat(redisQueryWorkingSetStore.loadResponse(responseRef)).isNotNull();
        assertThat(redisQueryWorkingSetStore.loadResponse(responseRef).getAnswer()).contains("payment-timeout");
        assertThat(redisQueryWorkingSetStore.loadCitationCheckReport(reportRef)).isNotNull();
        assertThat(redisQueryWorkingSetStore.loadCitationCheckReport(reportRef).getResults()).hasSize(1);
        assertThat(fakeRedisKeyValueStore.getExpire("test:query:ws:" + hitsRef)).isEqualTo(Long.valueOf(120L));

        redisQueryWorkingSetStore.deleteByQueryId("query-1");

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
