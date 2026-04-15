package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DependencyGraphService 测试
 *
 * 职责：验证会从 depends_on / related / [[wiki-links]] 构建依赖图边
 *
 * @author xiexu
 */
class DependencyGraphServiceTests {

    /**
     * 验证依赖图会同时解析 depends_on、related 与 wiki-links。
     */
    @Test
    void shouldBuildEdgesFromDependsOnRelatedAndWikiLinks() {
        DependencyGraphService dependencyGraphService = new DependencyGraphService(
                new FakeArticleJdbcRepository(List.of(
                        article("payment-config", List.of(), List.of(), "# Payment Config"),
                        article("retry-policy", List.of(), List.of(), "# Retry Policy"),
                        article("gateway-timeout", List.of(), List.of(), "# Gateway Timeout"),
                        article(
                                "payment-timeout",
                                List.of("payment-config"),
                                List.of("retry-policy"),
                                "# Payment Timeout\nSee [[gateway-timeout]]"
                        ),
                        article(
                                "refund-manual-review",
                                List.of(),
                                List.of(),
                                "# Refund Manual Review\nSee [[payment-timeout]]"
                        )
                ))
        );

        DependencyGraphSnapshot snapshot = dependencyGraphService.snapshot();

        assertThat(snapshot.getEdges())
                .extracting(DependencyGraphEdge::getUpstreamConceptId, DependencyGraphEdge::getDownstreamConceptId, DependencyGraphEdge::getRelationType)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("payment-config", "payment-timeout", "depends_on"),
                        org.assertj.core.groups.Tuple.tuple("retry-policy", "payment-timeout", "related"),
                        org.assertj.core.groups.Tuple.tuple("payment-timeout", "retry-policy", "related"),
                        org.assertj.core.groups.Tuple.tuple("gateway-timeout", "payment-timeout", "wiki_link"),
                        org.assertj.core.groups.Tuple.tuple("payment-timeout", "refund-manual-review", "wiki_link")
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
