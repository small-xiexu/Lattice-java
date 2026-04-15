package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PropagationService 测试
 *
 * 职责：验证纠错传播会按依赖图找出 downstream 影响范围
 *
 * @author xiexu
 */
class PropagationServiceTests {

    /**
     * 验证传播会返回直接与间接受影响的下游文章。
     */
    @Test
    void shouldFindTransitivelyImpactedDownstreamArticles() {
        DependencyGraphService dependencyGraphService = new DependencyGraphService(
                new FakeArticleJdbcRepository(List.of(
                        article("payment-config", List.of(), List.of(), "# Payment Config"),
                        article("retry-policy", List.of(), List.of(), "# Retry Policy"),
                        article(
                                "payment-timeout",
                                List.of("payment-config"),
                                List.of("retry-policy"),
                                "# Payment Timeout"
                        ),
                        article(
                                "refund-manual-review",
                                List.of(),
                                List.of(),
                                "# Refund Manual Review\nSee [[payment-timeout]]"
                        )
                ))
        );
        PropagationService propagationService = new PropagationService(dependencyGraphService);

        PropagationReport propagationReport = propagationService.propagate("payment-config", "更新重试策略");

        assertThat(propagationReport.getRootConceptId()).isEqualTo("payment-config");
        assertThat(propagationReport.getCorrectionSummary()).isEqualTo("更新重试策略");
        assertThat(propagationReport.getImpactedCount()).isEqualTo(3);
        assertThat(propagationReport.getImpactedConceptIds())
                .containsExactly("payment-timeout", "retry-policy", "refund-manual-review");
        assertThat(propagationReport.getItems())
                .extracting(PropagationItem::getConceptId, PropagationItem::getDepth)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("payment-timeout", 1),
                        org.assertj.core.groups.Tuple.tuple("retry-policy", 2),
                        org.assertj.core.groups.Tuple.tuple("refund-manual-review", 2)
                );
    }

    private ArticleRecord article(
            String conceptId,
            List<String> dependsOn,
            List<String> related,
            String content
    ) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                content,
                "active",
                OffsetDateTime.now(),
                List.of(conceptId + ".md"),
                "{}",
                "summary",
                List.of(),
                dependsOn,
                related,
                "medium",
                "passed"
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
}
