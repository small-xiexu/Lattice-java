package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.QualityMetricsHistoryJdbcRepository;
import com.xbk.lattice.infra.persistence.QualityMetricsHistoryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QualityMetrics 历史趋势测试
 *
 * 职责：验证质量历史写入与 trend 计算
 *
 * @author xiexu
 */
class QualityMetricsHistoryTests {

    /**
     * 验证 measure() 后会写入一条带派生比率的历史记录。
     */
    @Test
    void measureShouldPersistHistoryRecordWithDerivedRates() {
        FakeQualityMetricsHistoryJdbcRepository historyJdbcRepository = new FakeQualityMetricsHistoryJdbcRepository(List.of());
        QualityMetricsService qualityMetricsService = new QualityMetricsService(
                new FakeArticleJdbcRepository(List.of(
                        article("payment-timeout", "passed", List.of("payment/a.md"), List.of("retry=3")),
                        article("payment-routing", "passed", List.of("payment/b.md"), List.of()),
                        article("refund-manual-review", "pending", List.of(), List.of()),
                        article("ops-console", "needs_human_review", List.of("ops/a.md"), List.of())
                )),
                new FakeContributionJdbcRepository(List.of(contribution(), contribution())),
                new FakeSourceFileJdbcRepository(List.of(
                        new SourceFileRecord("payment/a.md", "a", "md", 1L),
                        new SourceFileRecord("payment/b.md", "b", "md", 1L),
                        new SourceFileRecord("ops/a.md", "c", "md", 1L)
                )),
                historyJdbcRepository
        );

        QualityMetricsReport report = qualityMetricsService.measure();

        assertThat(report.getTotalArticles()).isEqualTo(4);
        assertThat(historyJdbcRepository.getSavedRecords()).hasSize(1);
        QualityMetricsHistoryRecord record = historyJdbcRepository.getSavedRecords().get(0);
        assertThat(record.getReviewPassRate()).isEqualTo(50.0D);
        assertThat(record.getGroundingRate()).isEqualTo(75.0D);
        assertThat(record.getReferentialRate()).isEqualTo(25.0D);
    }

    /**
     * 验证 trend(days) 会返回最新与最早历史之间的 delta。
     */
    @Test
    void trendShouldReturnDeltasFromHistoryWindow() {
        OffsetDateTime baseline = OffsetDateTime.parse("2026-04-10T10:00:00+08:00");
        OffsetDateTime latest = OffsetDateTime.parse("2026-04-16T10:00:00+08:00");
        QualityMetricsService qualityMetricsService = new QualityMetricsService(
                new FakeArticleJdbcRepository(List.of()),
                new FakeContributionJdbcRepository(List.of()),
                new FakeSourceFileJdbcRepository(List.of()),
                new FakeQualityMetricsHistoryJdbcRepository(List.of(
                        history(1L, baseline, 10, 40.0D, 55.0D, 20.0D),
                        history(2L, latest, 15, 60.0D, 75.0D, 50.0D)
                ))
        );

        QualityMetricsTrend trend = qualityMetricsService.trend(7);

        assertThat(trend.getDays()).isEqualTo(7);
        assertThat(trend.getLatestMeasuredAt()).isEqualTo(latest);
        assertThat(trend.getTotalArticlesDelta()).isEqualTo(5);
        assertThat(trend.getReviewPassRateDelta()).isEqualTo(20.0D);
        assertThat(trend.getGroundingRateDelta()).isEqualTo(20.0D);
        assertThat(trend.getReferentialRateDelta()).isEqualTo(30.0D);
    }

    private ArticleRecord article(
            String conceptId,
            String reviewStatus,
            List<String> sourcePaths,
            List<String> referentialKeywords
    ) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId,
                "active",
                OffsetDateTime.now(),
                sourcePaths,
                "{}",
                "summary",
                referentialKeywords,
                List.of(),
                List.of(),
                "high",
                reviewStatus
        );
    }

    private ContributionRecord contribution() {
        return new ContributionRecord(UUID.randomUUID(), "question", "answer", "[]", "system", OffsetDateTime.now());
    }

    private QualityMetricsHistoryRecord history(
            long id,
            OffsetDateTime measuredAt,
            int totalArticles,
            double reviewPassRate,
            double groundingRate,
            double referentialRate
    ) {
        return new QualityMetricsHistoryRecord(
                id,
                measuredAt,
                totalArticles,
                0,
                0,
                0,
                0,
                0,
                reviewPassRate,
                groundingRate,
                referentialRate
        );
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

    private static class FakeQualityMetricsHistoryJdbcRepository extends QualityMetricsHistoryJdbcRepository {

        private final List<QualityMetricsHistoryRecord> records;

        private final List<QualityMetricsHistoryRecord> savedRecords = new ArrayList<QualityMetricsHistoryRecord>();

        private FakeQualityMetricsHistoryJdbcRepository(List<QualityMetricsHistoryRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public void save(QualityMetricsHistoryRecord record) {
            savedRecords.add(record);
        }

        @Override
        public List<QualityMetricsHistoryRecord> findSince(int days) {
            return records;
        }

        private List<QualityMetricsHistoryRecord> getSavedRecords() {
            return savedRecords;
        }
    }
}
