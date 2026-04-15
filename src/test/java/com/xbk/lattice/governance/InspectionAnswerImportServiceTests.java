package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.PendingQueryJdbcRepository;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InspectionAnswerImportService 测试
 *
 * 职责：验证人工答案可导回 contribution 沉淀并清理对应 pending
 *
 * @author xiexu
 */
class InspectionAnswerImportServiceTests {

    /**
     * 验证导入人工最终答案后会新增 contribution，并删除对应 pending query。
     */
    @Test
    void shouldPersistContributionAndRemovePendingQueryWhenImportingAnswer() {
        PendingQueryRecord pendingQueryRecord = new PendingQueryRecord(
                "query-1",
                "retry=3 是什么意思",
                "旧答案",
                List.of("payment-timeout"),
                List.of("payment/a.md"),
                "[]",
                "PASSED",
                OffsetDateTime.parse("2026-04-15T20:00:00+08:00"),
                OffsetDateTime.parse("2026-04-22T20:00:00+08:00")
        );
        FakePendingQueryJdbcRepository pendingQueryJdbcRepository = new FakePendingQueryJdbcRepository(pendingQueryRecord);
        FakeContributionJdbcRepository contributionJdbcRepository = new FakeContributionJdbcRepository();
        InspectionAnswerImportService importService = new InspectionAnswerImportService(
                pendingQueryJdbcRepository,
                contributionJdbcRepository
        );

        InspectionImportResult importResult = importService.importAnswer(
                "pending:query-1",
                "retry=3 表示最多重试三次",
                "reviewer"
        );

        assertThat(importResult.getImportedCount()).isEqualTo(1);
        assertThat(importResult.getResolvedIds()).containsExactly("pending:query-1");
        assertThat(contributionJdbcRepository.savedRecord).isNotNull();
        assertThat(contributionJdbcRepository.savedRecord.getQuestion()).isEqualTo("retry=3 是什么意思");
        assertThat(contributionJdbcRepository.savedRecord.getAnswer()).isEqualTo("retry=3 表示最多重试三次");
        assertThat(contributionJdbcRepository.savedRecord.getConfirmedBy()).isEqualTo("reviewer");
        assertThat(contributionJdbcRepository.savedRecord.getCorrectionsJson()).contains("imported-answer");
        assertThat(pendingQueryJdbcRepository.deletedQueryIds).containsExactly("query-1");
    }

    private static class FakePendingQueryJdbcRepository extends PendingQueryJdbcRepository {

        private final PendingQueryRecord record;

        private final java.util.List<String> deletedQueryIds = new java.util.ArrayList<String>();

        private FakePendingQueryJdbcRepository(PendingQueryRecord record) {
            super(new JdbcTemplate());
            this.record = record;
        }

        @Override
        public Optional<PendingQueryRecord> findByQueryId(String queryId) {
            if (record.getQueryId().equals(queryId)) {
                return Optional.of(record);
            }
            return Optional.empty();
        }

        @Override
        public void deleteByQueryId(String queryId) {
            deletedQueryIds.add(queryId);
        }
    }

    private static class FakeContributionJdbcRepository extends ContributionJdbcRepository {

        private ContributionRecord savedRecord;

        private FakeContributionJdbcRepository() {
            super(new JdbcTemplate());
        }

        @Override
        public void save(ContributionRecord contributionRecord) {
            this.savedRecord = contributionRecord;
        }
    }
}
