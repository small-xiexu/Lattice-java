package com.xbk.lattice.governance;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PropagateExecutionService 测试
 *
 * 职责：验证真正的下游传播会改写文章、清理上游标记并写入 propagation snapshot
 *
 * @author xiexu
 */
class PropagateExecutionServiceTests {

    /**
     * 验证传播执行会按 LLM 判断更新受影响文章，并清理 upstream 标记。
     */
    @Test
    void shouldRewriteAffectedDownstreamArticlesAndClearUpstreamMarkers() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article(
                        "payment-config",
                        "# Payment Config\n\n重试策略现已是 retry=5。",
                        "{}"
                ),
                article(
                        "payment-timeout",
                        "# Payment Timeout\n\n当前仍写 retry=3。",
                        "{\"upstream_corrections\":[{\"from\":\"payment-config\",\"summary\":\"重试策略改为 retry=5\"}]}"
                ),
                article(
                        "refund-manual-review",
                        "# Refund Manual Review\n\n人工审核流程不依赖重试次数。",
                        "{\"upstream_corrections\":[{\"from\":\"payment-config\",\"summary\":\"重试策略改为 retry=5\"}]}"
                )
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        CapturingLlmClient compileClient = new CapturingLlmClient(
                "{\"affected\":true,\"reason\":\"文章直接引用了旧重试次数\"}",
                "# Payment Timeout\n\n已同步为 retry=5。",
                "{\"affected\":false,\"reason\":\"该文章不依赖重试策略\"}"
        );
        PropagateExecutionService propagateExecutionService = new PropagateExecutionService(
                articleJdbcRepository,
                articleSnapshotJdbcRepository,
                newLlmGateway(compileClient)
        );

        PropagationExecutionResult result = propagateExecutionService.executePropagation("payment-config");

        assertThat(result.getProcessed()).isEqualTo(2);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(articleJdbcRepository.getLastUpserted()).isNotNull();
        assertThat(articleJdbcRepository.getLastUpserted().getConceptId()).isEqualTo("payment-timeout");
        assertThat(articleJdbcRepository.getLastUpserted().getContent())
                .contains("已同步为 retry=5。")
                .contains("<!-- propagated-fix: payment-config, ");
        assertThat(articleJdbcRepository.getLastUpserted().getReviewStatus()).isEqualTo("needs_review");
        assertThat(articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow().getMetadataJson())
                .doesNotContain("payment-config");
        assertThat(articleJdbcRepository.findByConceptId("refund-manual-review").orElseThrow().getMetadataJson())
                .doesNotContain("payment-config");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleSnapshotJdbcRepository.getSavedRecords().get(0).getSnapshotReason()).isEqualTo("propagation");
        assertThat(compileClient.getUserPrompts()).hasSize(3);
    }

    private ArticleRecord article(String conceptId, String content, String metadataJson) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                content,
                "active",
                OffsetDateTime.parse("2026-04-16T10:20:00+08:00"),
                List.of(conceptId + ".md"),
                metadataJson,
                "summary",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                "passed"
        );
    }

    private LlmGateway newLlmGateway(CapturingLlmClient compileClient) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                compileClient,
                new CapturingLlmClient("{\"passed\":true}"),
                new FakeRedisKeyValueStore(),
                createProperties()
        );
    }

    private LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(20.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return llmProperties;
    }

    private static class CapturingLlmClient implements LlmClient {

        private final Queue<String> responses = new ArrayDeque<String>();

        private final List<String> userPrompts = new ArrayList<String>();

        private CapturingLlmClient(String... responses) {
            for (String response : responses) {
                this.responses.add(response);
            }
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            userPrompts.add(userPrompt);
            return new LlmCallResult(responses.remove(), 120, 40);
        }

        private List<String> getUserPrompts() {
            return userPrompts;
        }
    }

    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> records = new LinkedHashMap<String, ArticleRecord>();

        private ArticleRecord lastUpserted;

        private FakeArticleJdbcRepository(List<ArticleRecord> records) {
            super(new JdbcTemplate());
            for (ArticleRecord record : records) {
                this.records.put(record.getConceptId(), record);
            }
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(records.get(conceptId));
        }

        @Override
        public List<ArticleRecord> findWithUpstreamCorrections(String fromConceptId) {
            List<ArticleRecord> matched = new ArrayList<ArticleRecord>();
            for (ArticleRecord record : records.values()) {
                if (record.getMetadataJson() != null && record.getMetadataJson().contains(fromConceptId)) {
                    matched.add(record);
                }
            }
            return matched;
        }

        @Override
        public void upsert(ArticleRecord articleRecord) {
            records.put(articleRecord.getConceptId(), articleRecord);
            lastUpserted = articleRecord;
        }

        @Override
        public void clearUpstreamCorrection(String downstreamConceptId, String fromConceptId) {
            ArticleRecord current = records.get(downstreamConceptId);
            if (current == null) {
                return;
            }
            records.put(downstreamConceptId, new ArticleRecord(
                    current.getConceptId(),
                    current.getTitle(),
                    current.getContent(),
                    current.getLifecycle(),
                    current.getCompiledAt(),
                    current.getSourcePaths(),
                    "{}",
                    current.getSummary(),
                    current.getReferentialKeywords(),
                    current.getDependsOn(),
                    current.getRelated(),
                    current.getConfidence(),
                    current.getReviewStatus()
            ));
        }

        private ArticleRecord getLastUpserted() {
            return lastUpserted;
        }
    }

    private static class FakeArticleSnapshotJdbcRepository extends ArticleSnapshotJdbcRepository {

        private final List<ArticleSnapshotRecord> savedRecords = new ArrayList<ArticleSnapshotRecord>();

        private FakeArticleSnapshotJdbcRepository() {
            super(new JdbcTemplate());
        }

        @Override
        public void save(ArticleSnapshotRecord articleSnapshotRecord) {
            savedRecords.add(articleSnapshotRecord);
        }

        private List<ArticleSnapshotRecord> getSavedRecords() {
            return savedRecords;
        }
    }

    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }
    }

}
