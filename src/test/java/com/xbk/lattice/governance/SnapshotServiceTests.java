package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SnapshotService 测试
 *
 * 职责：验证会返回最近文章快照摘要
 *
 * @author xiexu
 */
class SnapshotServiceTests {

    /**
     * 验证快照服务会返回最近快照列表与数量。
     */
    @Test
    void shouldSummarizeRecentSnapshots() {
        SnapshotService snapshotService = new SnapshotService(
                new FakeArticleSnapshotJdbcRepository(List.of(
                        snapshot(2L, "payment-timeout", "passed", "second summary", "2026-04-15T20:05:00+08:00"),
                        snapshot(1L, "refund-manual-review", "pending", "first summary", "2026-04-15T20:00:00+08:00")
                ))
        );

        SnapshotReport snapshotReport = snapshotService.snapshot(10);

        assertThat(snapshotReport.getTotalSnapshots()).isEqualTo(2);
        assertThat(snapshotReport.getItems())
                .extracting(ArticleSnapshotRecord::getSnapshotId, ArticleSnapshotRecord::getConceptId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2L, "payment-timeout"),
                        org.assertj.core.groups.Tuple.tuple(1L, "refund-manual-review")
                );
    }

    private ArticleSnapshotRecord snapshot(
            long snapshotId,
            String conceptId,
            String reviewStatus,
            String summary,
            String compiledAt
    ) {
        return new ArticleSnapshotRecord(
                snapshotId,
                conceptId,
                conceptId,
                "# " + conceptId,
                "active",
                OffsetDateTime.parse(compiledAt),
                List.of("payment/a.md"),
                "{}",
                summary,
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
        public List<ArticleSnapshotRecord> findRecent(int limit) {
            return records;
        }
    }
}
