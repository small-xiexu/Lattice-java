package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Snapshot rollback 测试
 *
 * 职责：验证基于快照恢复文章与不匹配失败场景
 *
 * @author xiexu
 */
class SnapshotRollbackServiceTests {

    /**
     * 验证 rollback 会按 snapshotId 恢复文章并写入 rollback 留痕。
     */
    @Test
    void shouldRestoreArticleFromSnapshotAndCaptureRollbackTrace() {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article("payment-timeout", "# Current Payment Timeout", "needs_human_review")
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository(
                snapshot(7L, "payment-timeout", "# Snapshot Payment Timeout", "passed")
        );
        SnapshotService snapshotService = new SnapshotService(articleSnapshotJdbcRepository, articleJdbcRepository);

        RollbackResult result = snapshotService.rollback("payment-timeout", 7L);

        assertThat(result.getConceptId()).isEqualTo("payment-timeout");
        assertThat(result.getRestoredSnapshotId()).isEqualTo(7L);
        assertThat(articleJdbcRepository.getLastUpserted()).isNotNull();
        assertThat(articleJdbcRepository.getLastUpserted().getContent()).isEqualTo("# Snapshot Payment Timeout");
        assertThat(articleJdbcRepository.getLastUpserted().getReviewStatus()).isEqualTo("passed");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleSnapshotJdbcRepository.getSavedRecords().get(0).getSnapshotReason()).isEqualTo("rollback|from:7");
    }

    /**
     * 验证快照与 conceptId 不匹配时会失败。
     */
    @Test
    void shouldRejectRollbackWhenSnapshotDoesNotBelongToConcept() {
        SnapshotService snapshotService = new SnapshotService(
                new FakeArticleSnapshotJdbcRepository(snapshot(7L, "refund-timeout", "# Snapshot", "passed")),
                new FakeArticleJdbcRepository(List.of(article("payment-timeout", "# Current", "passed")))
        );

        assertThatThrownBy(() -> snapshotService.rollback("payment-timeout", 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("快照与概念不匹配");
    }

    private ArticleRecord article(String conceptId, String content, String reviewStatus) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                content,
                "active",
                OffsetDateTime.parse("2026-04-16T10:30:00+08:00"),
                List.of("payment/a.md"),
                "{}",
                "summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                reviewStatus
        );
    }

    private ArticleSnapshotRecord snapshot(long snapshotId, String conceptId, String content, String reviewStatus) {
        return new ArticleSnapshotRecord(
                snapshotId,
                conceptId,
                conceptId,
                content,
                "active",
                OffsetDateTime.parse("2026-04-15T20:05:00+08:00"),
                List.of("payment/a.md"),
                "{}",
                "summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                reviewStatus,
                "article_upsert",
                OffsetDateTime.parse("2026-04-15T20:05:05+08:00")
        );
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

        private final ArticleSnapshotRecord record;

        private final List<ArticleSnapshotRecord> savedRecords = new ArrayList<ArticleSnapshotRecord>();

        private FakeArticleSnapshotJdbcRepository(ArticleSnapshotRecord record) {
            super(new JdbcTemplate());
            this.record = record;
        }

        @Override
        public Optional<ArticleSnapshotRecord> findBySnapshotId(long snapshotId) {
            if (record != null && record.getSnapshotId() == snapshotId) {
                return Optional.of(record);
            }
            return Optional.empty();
        }

        @Override
        public void save(ArticleSnapshotRecord articleSnapshotRecord) {
            savedRecords.add(articleSnapshotRecord);
        }

        private List<ArticleSnapshotRecord> getSavedRecords() {
            return savedRecords;
        }
    }
}
