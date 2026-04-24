package com.xbk.lattice.query.citation;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CitationValidator 测试
 *
 * 职责：验证基于硬事实字面量的文章/源码核验行为
 */
class CitationValidatorTests {

    @Test
    void shouldVerifySourceCitationAgainstSourceContent() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ src/main/java/payment/RoutePlanner.java]",
                CitationSourceType.SOURCE_FILE,
                "src/main/java/payment/RoutePlanner.java",
                "RoutePlanner 暴露了 /payments 路径",
                "RoutePlanner 暴露了 /payments 路径 [→ src/main/java/payment/RoutePlanner.java]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isEqualTo("source_rule_overlap_verified");
    }

    @Test
    void shouldSkipClaimWithoutHardFactLiterals() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[[payment-routing]]",
                CitationSourceType.ARTICLE,
                "payment-routing",
                "这是一个一般性的系统描述",
                "这是一个一般性的系统描述 [[payment-routing]]"
        ));

        assertThat(result.isSkipped()).isTrue();
        assertThat(result.getReason()).isEqualTo("no_hard_fact_literals");
    }

    private static class FixedArticleJdbcRepository extends ArticleJdbcRepository {

        private FixedArticleJdbcRepository() {
            super(null);
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            if (!"payment-routing".equals(articleKey)) {
                return Optional.empty();
            }
            return Optional.of(new ArticleRecord(
                    1L,
                    "payment-routing",
                    "payment-routing",
                    "Payment Routing",
                    "RoutePlanner 暴露了 /payments 路径，payment_gateway 路由会进入补偿队列。",
                    "published",
                    OffsetDateTime.now(),
                    List.of("src/main/java/payment/RoutePlanner.java"),
                    "{}",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    "high",
                    "approved"
            ));
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return findByArticleKey(conceptId);
        }
    }

    private static class FixedSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private FixedSourceFileJdbcRepository() {
            super(null);
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            if (!"src/main/java/payment/RoutePlanner.java".equals(filePath)) {
                return Optional.empty();
            }
            return Optional.of(new SourceFileRecord(
                    101L,
                    1L,
                    "src/main/java/payment/RoutePlanner.java",
                    "src/main/java/payment/RoutePlanner.java",
                    null,
                    "@RequestMapping(\"/payments\")",
                    "JAVA",
                    128L,
                    """
                    @RequestMapping("/payments")
                    class RoutePlanner {
                        void route() {}
                    }
                    """,
                    "{}",
                    false,
                    "src/main/java/payment/RoutePlanner.java"
            ));
        }
    }
}
