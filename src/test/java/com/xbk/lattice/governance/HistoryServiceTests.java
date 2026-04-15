package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HistoryService 测试
 *
 * 职责：验证会按概念返回快照历史
 *
 * @author xiexu
 */
class HistoryServiceTests {

    /**
     * 验证历史服务会返回指定概念的最近历史记录。
     */
    @Test
    void shouldReturnConceptHistory() {
        HistoryService historyService = new HistoryService(
                new FakeArticleSnapshotJdbcRepository(List.of(
                        snapshot(3L, "payment-timeout", "passed", "2026-04-15T20:10:00+08:00"),
                        snapshot(2L, "payment-timeout", "needs_human_review", "2026-04-15T20:05:00+08:00")
                ))
        );

        HistoryReport historyReport = historyService.history("payment-timeout", 10);

        assertThat(historyReport.getConceptId()).isEqualTo("payment-timeout");
        assertThat(historyReport.getTotalEntries()).isEqualTo(2);
        assertThat(historyReport.getItems())
                .extracting(ArticleSnapshotRecord::getSnapshotId, ArticleSnapshotRecord::getReviewStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3L, "passed"),
                        org.assertj.core.groups.Tuple.tuple(2L, "needs_human_review")
                );
    }

    private ArticleSnapshotRecord snapshot(
            long snapshotId,
            String conceptId,
            String reviewStatus,
            String compiledAt
    ) {
        return new ArticleSnapshotRecord(
                snapshotId,
                conceptId,
                "Payment Timeout",
                "# Payment Timeout",
                "active",
                OffsetDateTime.parse(compiledAt),
                List.of("payment/a.md"),
                "{}",
                "summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                reviewStatus,
                "article_upsert",
                OffsetDateTime.parse(compiledAt).plusSeconds(5)
        );
    }

    private static class FakeArticleSnapshotJdbcRepository extends ArticleSnapshotJdbcRepository {

        private final List<ArticleSnapshotRecord> records;

        private FakeArticleSnapshotJdbcRepository(List<ArticleSnapshotRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<ArticleSnapshotRecord> findByConceptId(String conceptId, int limit) {
            return records;
        }
    }
}
