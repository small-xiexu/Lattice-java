package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.AnswerFeedbackAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.AnswerFeedbackAuditRecord;
import com.xbk.lattice.infra.persistence.AnswerFeedbackJdbcRepository;
import com.xbk.lattice.infra.persistence.AnswerFeedbackRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFeedbackService 测试
 *
 * 职责：验证答案反馈创建、归一化、处理状态和审计闭环
 *
 * @author xiexu
 */
class AnswerFeedbackServiceTests {

    /**
     * 验证创建反馈时会归一化上下文并写入创建审计。
     */
    @Test
    void shouldCreateFeedbackAndAudit() {
        FakeAnswerFeedbackJdbcRepository feedbackRepository = new FakeAnswerFeedbackJdbcRepository();
        FakeAnswerFeedbackAuditJdbcRepository auditRepository = new FakeAnswerFeedbackAuditJdbcRepository();
        AnswerFeedbackService answerFeedbackService = new AnswerFeedbackService(feedbackRepository, auditRepository);

        AnswerFeedbackRecord record = answerFeedbackService.create(new AnswerFeedbackRequest(
                "query-1",
                "  问题  ",
                "答案",
                "source_conflict",
                "来源不一致",
                List.of("article-1", "article-1", "article-2"),
                List.of("docs/a.md", "docs/a.md"),
                ""
        ));

        assertThat(record.getId()).isEqualTo(1L);
        assertThat(record.getStatus()).isEqualTo(AnswerFeedbackService.STATUS_PENDING);
        assertThat(record.getArticleKeys()).containsExactly("article-1", "article-2");
        assertThat(record.getSourcePaths()).containsExactly("docs/a.md");
        assertThat(record.getReportedBy()).isEqualTo("anonymous");
        assertThat(auditRepository.savedRecords).hasSize(1);
        assertThat(auditRepository.savedRecords.get(0).getAction()).isEqualTo("CREATE");
    }

    /**
     * 验证待处理反馈可标记处理并写处理审计。
     */
    @Test
    void shouldResolvePendingFeedbackAndAudit() {
        FakeAnswerFeedbackJdbcRepository feedbackRepository = new FakeAnswerFeedbackJdbcRepository();
        FakeAnswerFeedbackAuditJdbcRepository auditRepository = new FakeAnswerFeedbackAuditJdbcRepository();
        AnswerFeedbackService answerFeedbackService = new AnswerFeedbackService(feedbackRepository, auditRepository);
        AnswerFeedbackRecord createdRecord = answerFeedbackService.create(new AnswerFeedbackRequest(
                "query-2",
                "问题",
                "答案",
                "answer_problem",
                "答案不完整",
                List.of(),
                List.of(),
                "reporter"
        ));

        AnswerFeedbackRecord resolvedRecord = answerFeedbackService.resolve(
                createdRecord.getId(),
                new AnswerFeedbackHandleRequest("handler", "已补回归")
        );

        assertThat(resolvedRecord.getStatus()).isEqualTo(AnswerFeedbackService.STATUS_RESOLVED);
        assertThat(resolvedRecord.getResolutionComment()).isEqualTo("已补回归");
        assertThat(answerFeedbackService.countPending()).isZero();
        assertThat(auditRepository.savedRecords).extracting(AnswerFeedbackAuditRecord::getAction)
                .containsExactly("CREATE", "RESOLVE");
    }

    private static class FakeAnswerFeedbackJdbcRepository extends AnswerFeedbackJdbcRepository {

        private final List<AnswerFeedbackRecord> records = new ArrayList<AnswerFeedbackRecord>();

        private long sequence = 0L;

        private FakeAnswerFeedbackJdbcRepository() {
            super(new JdbcTemplate());
        }

        @Override
        public AnswerFeedbackRecord save(AnswerFeedbackRecord answerFeedbackRecord) {
            sequence++;
            AnswerFeedbackRecord savedRecord = copy(answerFeedbackRecord, sequence, answerFeedbackRecord.getStatus());
            records.add(savedRecord);
            return savedRecord;
        }

        @Override
        public Optional<AnswerFeedbackRecord> findById(long id) {
            return records.stream()
                    .filter(record -> record.getId() == id)
                    .findFirst();
        }

        @Override
        public List<AnswerFeedbackRecord> findAll(String status, int limit) {
            List<AnswerFeedbackRecord> matchedRecords = new ArrayList<AnswerFeedbackRecord>();
            for (AnswerFeedbackRecord record : records) {
                if (status == null || status.equalsIgnoreCase(record.getStatus())) {
                    matchedRecords.add(record);
                }
            }
            return matchedRecords;
        }

        @Override
        public AnswerFeedbackRecord updateStatus(
                long id,
                String status,
                String resolutionComment,
                String handledBy,
                OffsetDateTime handledAt
        ) {
            for (int index = 0; index < records.size(); index++) {
                AnswerFeedbackRecord currentRecord = records.get(index);
                if (currentRecord.getId() == id) {
                    AnswerFeedbackRecord updatedRecord = new AnswerFeedbackRecord(
                            currentRecord.getId(),
                            currentRecord.getQueryId(),
                            currentRecord.getQuestion(),
                            currentRecord.getAnswerSummary(),
                            currentRecord.getFeedbackType(),
                            currentRecord.getComment(),
                            currentRecord.getArticleKeys(),
                            currentRecord.getSourcePaths(),
                            currentRecord.getReportedBy(),
                            status,
                            resolutionComment,
                            handledBy,
                            handledAt,
                            currentRecord.getCreatedAt(),
                            handledAt,
                            currentRecord.getMetadataJson()
                    );
                    records.set(index, updatedRecord);
                    return updatedRecord;
                }
            }
            throw new IllegalArgumentException("not found");
        }

        @Override
        public int countByStatus(String status) {
            int count = 0;
            for (AnswerFeedbackRecord record : records) {
                if (status.equalsIgnoreCase(record.getStatus())) {
                    count++;
                }
            }
            return count;
        }

        private AnswerFeedbackRecord copy(AnswerFeedbackRecord record, long id, String status) {
            return new AnswerFeedbackRecord(
                    id,
                    record.getQueryId(),
                    record.getQuestion(),
                    record.getAnswerSummary(),
                    record.getFeedbackType(),
                    record.getComment(),
                    record.getArticleKeys(),
                    record.getSourcePaths(),
                    record.getReportedBy(),
                    status,
                    record.getResolutionComment(),
                    record.getHandledBy(),
                    record.getHandledAt(),
                    record.getCreatedAt(),
                    record.getUpdatedAt(),
                    record.getMetadataJson()
            );
        }
    }

    private static class FakeAnswerFeedbackAuditJdbcRepository extends AnswerFeedbackAuditJdbcRepository {

        private final List<AnswerFeedbackAuditRecord> savedRecords = new ArrayList<AnswerFeedbackAuditRecord>();

        private long sequence = 0L;

        private FakeAnswerFeedbackAuditJdbcRepository() {
            super(new JdbcTemplate());
        }

        @Override
        public AnswerFeedbackAuditRecord save(AnswerFeedbackAuditRecord answerFeedbackAuditRecord) {
            sequence++;
            AnswerFeedbackAuditRecord savedRecord = new AnswerFeedbackAuditRecord(
                    sequence,
                    answerFeedbackAuditRecord.getFeedbackId(),
                    answerFeedbackAuditRecord.getAction(),
                    answerFeedbackAuditRecord.getPreviousStatus(),
                    answerFeedbackAuditRecord.getNextStatus(),
                    answerFeedbackAuditRecord.getComment(),
                    answerFeedbackAuditRecord.getOperatedBy(),
                    answerFeedbackAuditRecord.getOperatedAt(),
                    answerFeedbackAuditRecord.getMetadataJson()
            );
            savedRecords.add(savedRecord);
            return savedRecord;
        }

        @Override
        public List<AnswerFeedbackAuditRecord> findByFeedbackId(long feedbackId) {
            List<AnswerFeedbackAuditRecord> matchedRecords = new ArrayList<AnswerFeedbackAuditRecord>();
            for (AnswerFeedbackAuditRecord record : savedRecords) {
                if (record.getFeedbackId() == feedbackId) {
                    matchedRecords.add(record);
                }
            }
            return matchedRecords;
        }
    }
}
