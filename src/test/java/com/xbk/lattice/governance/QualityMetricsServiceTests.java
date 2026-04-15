package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QualityMetricsService 测试
 *
 * 职责：验证质量指标会按审查状态与反馈沉淀情况汇总
 *
 * @author xiexu
 */
class QualityMetricsServiceTests {

    /**
     * 验证质量报告会区分 passed、pending 与 needs_human_review。
     */
    @Test
    void shouldSummarizeQualityMetrics() {
        QualityMetricsService qualityMetricsService = new QualityMetricsService(
                new FakeArticleJdbcRepository(List.of(
                        article("payment-timeout", "passed"),
                        article("payment-routing", "pending"),
                        article("refund-manual-review", "needs_human_review")
                )),
                new FakeContributionJdbcRepository(List.of(
                        contribution(),
                        contribution()
                )),
                new FakeSourceFileJdbcRepository(List.of(
                        new SourceFileRecord("payment/a.md", "a", "md", 1),
                        new SourceFileRecord("payment/b.md", "b", "md", 1),
                        new SourceFileRecord("payment/c.md", "c", "md", 1),
                        new SourceFileRecord("payment/d.md", "d", "md", 1)
                ))
        );

        QualityMetricsReport report = qualityMetricsService.measure();

        assertThat(report.getTotalArticles()).isEqualTo(3);
        assertThat(report.getPassedArticles()).isEqualTo(1);
        assertThat(report.getPendingReviewArticles()).isEqualTo(1);
        assertThat(report.getNeedsHumanReviewArticles()).isEqualTo(1);
        assertThat(report.getContributionCount()).isEqualTo(2);
        assertThat(report.getSourceFileCount()).isEqualTo(4);
    }

    private ArticleRecord article(String conceptId, String reviewStatus) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId,
                "active",
                OffsetDateTime.now(),
                List.of("payment/a.md"),
                "{}",
                "summary",
                List.of("keyword"),
                List.of(),
                List.of(),
                "high",
                reviewStatus
        );
    }

    private ContributionRecord contribution() {
        return new ContributionRecord(UUID.randomUUID(), "question", "answer", "[]", "system", OffsetDateTime.now());
    }

    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final List<ArticleRecord> records;

        private FakeArticleJdbcRepository(List<ArticleRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<ArticleRecord> findAll() {
            return records;
        }
    }

    private static class FakeContributionJdbcRepository extends ContributionJdbcRepository {

        private final List<ContributionRecord> records;

        private FakeContributionJdbcRepository(List<ContributionRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<ContributionRecord> findAll() {
            return records;
        }
    }

    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final List<SourceFileRecord> records;

        private FakeSourceFileJdbcRepository(List<SourceFileRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<SourceFileRecord> findAll() {
            return records;
        }
    }
}
