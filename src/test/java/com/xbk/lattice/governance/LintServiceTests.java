package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LintService 测试
 *
 * 职责：验证最小 6 维治理检查会输出对应问题
 *
 * @author xiexu
 */
class LintServiceTests {

    /**
     * 验证缺少 summary/source/refkey、生命周期异常、依赖缺失与待人工处理状态都会被发现。
     */
    @Test
    void shouldReportIssuesAcrossSixDimensions() {
        LintService lintService = new LintService(
                new FakeArticleJdbcRepository(List.of(
                        new ArticleRecord(
                                "payment-timeout",
                                "Payment Timeout",
                                "# Payment Timeout",
                                "archived",
                                OffsetDateTime.now(),
                                List.of(),
                                "{}",
                                "",
                                List.of(),
                                List.of("missing-concept"),
                                List.of(),
                                "medium",
                                "needs_human_review"
                        ),
                        new ArticleRecord(
                                "payment-timeout-copy",
                                "Payment Timeout",
                                "# Payment Timeout Copy",
                                "active",
                                OffsetDateTime.now(),
                                List.of("payment/a.md"),
                                "{}",
                                "summary",
                                List.of("retry=3"),
                                List.of(),
                                List.of(),
                                "medium",
                                "passed"
                        )
                ))
        );

        LintReport lintReport = lintService.lint();

        assertThat(lintReport.getCheckedDimensions())
                .containsExactly("consistency", "gaps", "freshness", "propagation", "grounding", "referential");
        assertThat(lintReport.getIssues())
                .extracting(LintIssue::getDimension)
                .contains("consistency", "gaps", "freshness", "propagation", "grounding", "referential");
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
}
