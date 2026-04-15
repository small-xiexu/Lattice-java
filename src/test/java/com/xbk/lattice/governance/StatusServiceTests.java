package com.xbk.lattice.governance;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StatusService 测试
 *
 * 职责：验证知识库状态汇总指标计算正确
 *
 * @author xiexu
 */
class StatusServiceTests {

    /**
     * 验证状态快照会汇总文章、源文件、贡献、pending 与待处理文章数量。
     */
    @Test
    void shouldSummarizeKnowledgeBaseStatus() {
        StatusService statusService = new StatusService(
                new FakeArticleJdbcRepository(List.of(
                        article("payment-timeout", "passed"),
                        article("refund-manual-review", "needs_human_review")
                )),
                new FakeSourceFileJdbcRepository(List.of(
                        new SourceFileRecord("payment/a.md", "a", "md", 1),
                        new SourceFileRecord("payment/b.md", "b", "md", 1),
                        new SourceFileRecord("payment/c.md", "c", "md", 1)
                )),
                new FakeContributionJdbcRepository(List.of(
                        contribution("retry=3 是什么配置"),
                        contribution("refund-manual-review 是什么")
                )),
                new FixedPendingQueryManager(List.of(
                        pending("query-1"),
                        pending("query-2")
                ))
        );

        StatusSnapshot snapshot = statusService.snapshot();

        assertThat(snapshot.getArticleCount()).isEqualTo(2);
        assertThat(snapshot.getSourceFileCount()).isEqualTo(3);
        assertThat(snapshot.getContributionCount()).isEqualTo(2);
        assertThat(snapshot.getPendingQueryCount()).isEqualTo(2);
        assertThat(snapshot.getReviewPendingArticleCount()).isEqualTo(1);
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

    private ContributionRecord contribution(String question) {
        return new ContributionRecord(UUID.randomUUID(), question, "answer", "[]", "system", OffsetDateTime.now());
    }

    private PendingQueryRecord pending(String queryId) {
        return new PendingQueryRecord(
                queryId,
                "question",
                "answer",
                List.of(),
                List.of(),
                "[]",
                "PASSED",
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(7)
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

    private static class FixedPendingQueryManager implements PendingQueryManager {

        private final List<PendingQueryRecord> records;

        private FixedPendingQueryManager(List<PendingQueryRecord> records) {
            this.records = records;
        }

        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingQueryRecord correct(String queryId, String correction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void confirm(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void discard(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            return records.stream()
                    .filter(record -> record.getQueryId().equals(queryId))
                    .findFirst()
                    .orElseThrow(UnsupportedOperationException::new);
        }

        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            return records;
        }
    }
}
