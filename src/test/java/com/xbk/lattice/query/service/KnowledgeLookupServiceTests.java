package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeLookupService 测试
 *
 * 职责：验证 MCP `lattice_get` 的文章与源文件查询逻辑
 *
 * @author xiexu
 */
class KnowledgeLookupServiceTests {

    /**
     * 验证优先按 conceptId 返回文章详情。
     */
    @Test
    void shouldReturnArticleWhenConceptIdExists() {
        KnowledgeLookupService knowledgeLookupService = new KnowledgeLookupService(
                new FakeArticleJdbcRepository(
                        new ArticleRecord(
                                "payment-timeout",
                                "Payment Timeout",
                                "# Payment Timeout",
                                "active",
                                OffsetDateTime.now(),
                                List.of("payment/a.md"),
                                "{}"
                        )
                ),
                new FakeSourceFileJdbcRepository(null)
        );

        KnowledgeLookupResult result = knowledgeLookupService.get("payment-timeout");

        assertThat(result.isFound()).isTrue();
        assertThat(result.getType()).isEqualTo("article");
        assertThat(result.getTitle()).isEqualTo("Payment Timeout");
    }

    /**
     * 验证会优先按 articleKey 返回文章详情。
     */
    @Test
    void shouldPreferArticleKeyBeforeConceptId() {
        KnowledgeLookupService knowledgeLookupService = new KnowledgeLookupService(
                new FakeArticleJdbcRepository(
                        new ArticleRecord(
                                9L,
                                "payments-docs--payment-timeout",
                                "payment-timeout",
                                "Payment Timeout",
                                "# Payment Timeout",
                                "active",
                                OffsetDateTime.now(),
                                List.of("payment/a.md"),
                                "{}",
                                "",
                                List.of(),
                                List.of(),
                                List.of(),
                                "medium",
                                "pending"
                        )
                ),
                new FakeSourceFileJdbcRepository(null)
        );

        KnowledgeLookupResult result = knowledgeLookupService.get("payments-docs--payment-timeout");

        assertThat(result.isFound()).isTrue();
        assertThat(result.getType()).isEqualTo("article");
        assertThat(result.getId()).isEqualTo("payment-timeout");
    }

    /**
     * 验证 conceptId 未命中时会回退到源文件路径查询。
     */
    @Test
    void shouldFallbackToSourceLookupWhenArticleMissing() {
        KnowledgeLookupService knowledgeLookupService = new KnowledgeLookupService(
                new FakeArticleJdbcRepository(null),
                new FakeSourceFileJdbcRepository(
                        new SourceFileRecord(
                                "payment/context.md",
                                "retry=3",
                                "md",
                                32,
                                "retry=3\ninterval=30s",
                                "{}",
                                true,
                                "payment/context.md"
                        )
                )
        );

        KnowledgeLookupResult result = knowledgeLookupService.get("payment/context.md");

        assertThat(result.isFound()).isTrue();
        assertThat(result.getType()).isEqualTo("source");
        assertThat(result.getContent()).contains("interval=30s");
    }

    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final ArticleRecord record;

        private FakeArticleJdbcRepository(ArticleRecord record) {
            super(new JdbcTemplate());
            this.record = record;
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            if (record != null && record.getConceptId().equals(conceptId)) {
                return Optional.of(record);
            }
            return Optional.empty();
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            if (record != null && record.getArticleKey().equals(articleKey)) {
                return Optional.of(record);
            }
            return Optional.empty();
        }
    }

    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final SourceFileRecord record;

        private FakeSourceFileJdbcRepository(SourceFileRecord record) {
            super(new JdbcTemplate());
            this.record = record;
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            if (record != null && record.getFilePath().equals(filePath)) {
                return Optional.of(record);
            }
            return Optional.empty();
        }
    }
}
