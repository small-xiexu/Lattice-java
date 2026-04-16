package com.xbk.lattice.governance;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmCallResult;
import com.xbk.lattice.compiler.service.LlmClient;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.compiler.service.LlmUsageRecord;
import com.xbk.lattice.compiler.service.LlmUsageStore;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LintFixService 测试
 *
 * 职责：验证 fixable lint 问题会被自动修复并写入 lint_fix snapshot
 *
 * @author xiexu
 */
class LintFixServiceTests {

    /**
     * 验证 fix(report) 只处理 fixable 问题，并保留 skipped 统计。
     */
    @Test
    void shouldFixOnlyFixableIssuesAndCaptureLintFixSnapshot() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article("payment-timeout", "# Payment Timeout")
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        LintFixService lintFixService = new LintFixService(
                articleJdbcRepository,
                articleSnapshotJdbcRepository,
                newLlmGateway(new FixedLlmClient("# Payment Timeout\n\nsummary: updated"))
        );

        LintFixResult result = lintFixService.fix(new LintReport(
                List.of("gaps", "grounding"),
                List.of(
                        new LintIssue("gaps", "payment-timeout", "缺少 summary", true, "补齐 2-3 句摘要"),
                        new LintIssue("grounding", "payment-timeout", "缺少 source_paths", false, null)
                )
        ));

        assertThat(result.getFixed()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(articleJdbcRepository.getLastUpserted()).isNotNull();
        assertThat(articleJdbcRepository.getLastUpserted().getContent()).contains("summary: updated");
        assertThat(articleJdbcRepository.getLastUpserted().getReviewStatus()).isEqualTo("needs_review");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleSnapshotJdbcRepository.getSavedRecords().get(0).getSnapshotReason()).isEqualTo("lint_fix");
    }

    private ArticleRecord article(String conceptId, String content) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                content,
                "active",
                OffsetDateTime.parse("2026-04-16T10:45:00+08:00"),
                List.of("payment/a.md"),
                "{}",
                "",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                "passed"
        );
    }

    private LlmGateway newLlmGateway(FixedLlmClient compileClient) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmUsageStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        LlmProperties properties = new LlmProperties();
        properties.setCompileModel("openai");
        properties.setReviewerModel("anthropic");
        properties.setBudgetUsd(10.0D);
        properties.setCacheTtlSeconds(3600L);
        properties.setCacheKeyPrefix("llm:test:");
        return constructor.newInstance(
                compileClient,
                compileClient,
                new FakeRedisKeyValueStore(),
                new FakeLlmUsageStore(),
                properties
        );
    }

    private static class FixedLlmClient implements LlmClient {

        private final String content;

        private FixedLlmClient(String content) {
            this.content = content;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult(content, 120, 30);
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
        public void upsert(ArticleRecord articleRecord) {
            records.put(articleRecord.getConceptId(), articleRecord);
            lastUpserted = articleRecord;
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

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            // 无操作
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }
    }

    private static class FakeLlmUsageStore implements LlmUsageStore {

        @Override
        public void save(LlmUsageRecord llmUsageRecord) {
            // 无操作
        }
    }
}
