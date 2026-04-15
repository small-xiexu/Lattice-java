package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.PendingQueryJdbcRepository;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InspectService 测试
 *
 * 职责：验证 inspect 会把 active pending queries 转成标准化问题清单
 *
 * @author xiexu
 */
class InspectServiceTests {

    /**
     * 验证 inspect 会输出带 inspection id、建议答案与来源路径的问题项。
     */
    @Test
    void shouldConvertPendingQueriesIntoInspectionQuestions() {
        InspectService inspectService = new InspectService(
                new FakePendingQueryJdbcRepository(List.of(
                        new PendingQueryRecord(
                                "query-1",
                                "retry=3 是什么意思",
                                "retry=3 表示最多重试三次",
                                List.of("payment-timeout"),
                                List.of("payment/a.md"),
                                "[]",
                                "PASSED",
                                OffsetDateTime.parse("2026-04-15T20:00:00+08:00"),
                                OffsetDateTime.parse("2026-04-22T20:00:00+08:00")
                        )
                ))
        );

        InspectionReport inspectionReport = inspectService.inspect();

        assertThat(inspectionReport.getTotalQuestions()).isEqualTo(1);
        InspectionQuestion inspectionQuestion = inspectionReport.getQuestions().get(0);
        assertThat(inspectionQuestion.getId()).isEqualTo("pending:query-1");
        assertThat(inspectionQuestion.getType()).isEqualTo("pending_query");
        assertThat(inspectionQuestion.getQuestion()).isEqualTo("retry=3 是什么意思");
        assertThat(inspectionQuestion.getPrompt()).contains("确认");
        assertThat(inspectionQuestion.getSuggestedAnswer()).isEqualTo("retry=3 表示最多重试三次");
        assertThat(inspectionQuestion.getSourcePaths()).containsExactly("payment/a.md");
        assertThat(inspectionQuestion.getReviewStatus()).isEqualTo("PASSED");
    }

    private static class FakePendingQueryJdbcRepository extends PendingQueryJdbcRepository {

        private final List<PendingQueryRecord> records;

        private FakePendingQueryJdbcRepository(List<PendingQueryRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<PendingQueryRecord> findAllActive() {
            return records;
        }
    }
}
