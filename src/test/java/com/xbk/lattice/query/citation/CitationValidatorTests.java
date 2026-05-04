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
 *
 * @author xiexu
 */
class CitationValidatorTests {

    /**
     * 验证 ARTICLE claim 校验基于 targetKey 对应 article 全文，而不是答案上下文摘录。
     */
    @Test
    void shouldVerifyArticleCitationAgainstTargetArticleContent() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[[payment-routing]]",
                CitationSourceType.ARTICLE,
                "payment-routing",
                "PaymentService 会使用 payment_gateway 路由",
                "短摘录 [[payment-routing]]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isEqualTo("rule_overlap_verified");
        assertThat(result.getMatchedExcerpt()).contains("payment_gateway");
    }

    /**
     * 验证 source-level 内容无法支撑硬事实时会降级。
     */
    @Test
    void shouldDemoteArticleCitationWhenTargetContentDoesNotSupportClaim() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[[payment-routing]]",
                CitationSourceType.ARTICLE,
                "payment-routing",
                "PaymentService 会写入 refund_queue",
                "PaymentService 会写入 refund_queue [[payment-routing]]"
        ));

        assertThat(result.isDemoted()).isTrue();
        assertThat(result.getReason()).isEqualTo("insufficient_overlap");
    }

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
    void shouldVerifySourceCitationByDirectLineMatchWhenClaimWrapsEvidenceLine() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ src/main/java/payment/RoutePlanner.java]",
                CitationSourceType.SOURCE_FILE,
                "src/main/java/payment/RoutePlanner.java",
                "当前可确认的信息是：RoutePlanner 暴露了 /payments 路径",
                "当前可确认的信息是：RoutePlanner 暴露了 /payments 路径 [→ src/main/java/payment/RoutePlanner.java]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isIn("source_direct_line_match_verified", "source_rule_overlap_verified");
    }

    @Test
    void shouldVerifyNearCompleteEnumerationOverlapForSpreadsheetFacts() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ gateway-field-definitions.xlsx]",
                CitationSourceType.SOURCE_FILE,
                "gateway-field-definitions.xlsx",
                "会员卡渠道支持 01/02/04/51/52/61/62/99",
                "会员卡渠道支持 01/02/04/51/52/61/62/99 [→ gateway-field-definitions.xlsx]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isEqualTo("source_near_complete_overlap_verified");
    }

    /**
     * 验证中文单位里的数字事实也能和证据侧数字匹配。
     */
    @Test
    void shouldVerifyNumericFactsEmbeddedInChineseText() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ standard-guide.pdf]",
                CitationSourceType.SOURCE_FILE,
                "standard-guide.pdf",
                "到2030年，标准数量超过300项",
                "到2030年，标准数量超过300项 [→ standard-guide.pdf]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isIn("source_direct_line_match_verified", "source_rule_overlap_verified");
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

    @Test
    void shouldVerifyLatinTermClaimInsideChineseSentence() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[[conflict-lock]]",
                CitationSourceType.ARTICLE,
                "conflict-lock",
                "作用机制 1. 系统采用 Redis distributed lock 串行化处理",
                "作用机制 1. 系统采用 Redis distributed lock 串行化处理 [[conflict-lock]]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isEqualTo("rule_overlap_verified");
    }

    @Test
    void shouldFailSourceCitationWhenSourceFileIsMissing() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ src/main/java/payment/MissingPlanner.java]",
                CitationSourceType.SOURCE_FILE,
                "src/main/java/payment/MissingPlanner.java",
                "MissingPlanner 暴露了 /payments 路径",
                "MissingPlanner 暴露了 /payments 路径 [→ src/main/java/payment/MissingPlanner.java]"
        ));

        assertThat(result.getStatus()).isEqualTo(CitationValidationStatus.NOT_FOUND);
        assertThat(result.getReason()).isEqualTo("source_file_not_found");
    }

    /**
     * 验证缺失 targetKey 时直接失败，不进入仓储查找。
     */
    @Test
    void shouldRejectCitationWithoutTargetKey() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[[]]",
                CitationSourceType.ARTICLE,
                "",
                "PaymentService 会使用 payment_gateway 路由",
                "PaymentService 会使用 payment_gateway 路由 [[]]"
        ));

        assertThat(result.isDemoted()).isTrue();
        assertThat(result.getReason()).isEqualTo("target_key_missing");
    }

    private static class FixedArticleJdbcRepository extends ArticleJdbcRepository {

        private FixedArticleJdbcRepository() {
            super(null);
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            if ("conflict-lock".equals(articleKey)) {
                return Optional.of(new ArticleRecord(
                        1L,
                        "conflict-lock",
                        "conflict-lock",
                        "Conflict Lock",
                        "库存并发控制采用 Redis distributed lock 串行化处理，以避免并发扣减冲突。",
                        "published",
                        OffsetDateTime.now(),
                        List.of("conflict-lock.md"),
                        "{}",
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        "high",
                        "approved"
                ));
            }
            if (!"payment-routing".equals(articleKey)) {
                return Optional.empty();
            }
            return Optional.of(new ArticleRecord(
                    1L,
                    "payment-routing",
                    "payment-routing",
                    "Payment Routing",
                    "PaymentService 通过 RoutePlanner 暴露了 /payments 路径，payment_gateway 路由会进入补偿队列。",
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
            if ("gateway-field-definitions.xlsx".equals(filePath)) {
                return Optional.of(new SourceFileRecord(
                        102L,
                        1L,
                        "gateway-field-definitions.xlsx",
                        "gateway-field-definitions.xlsx",
                        null,
                        "XLSX",
                        "XLSX",
                        256L,
                        """
                        渠道,transactionType
                        会员卡渠道,01 02 04 51 52 61 62
                        """,
                        "{}",
                        false,
                        "gateway-field-definitions.xlsx"
                ));
            }
            if ("src/main/java/payment/RoutePlanner.java".equals(filePath)) {
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
            if ("standard-guide.pdf".equals(filePath)) {
                return Optional.of(new SourceFileRecord(
                        103L,
                        1L,
                        "standard-guide.pdf",
                        "standard-guide.pdf",
                        null,
                        "PDF",
                        "PDF",
                        512L,
                        "到2027年，标准体系基本建立。到2030年，标准数量超过300项，形成持续迭代机制。",
                        "{}",
                        false,
                        "standard-guide.pdf"
                ));
            }
            return Optional.empty();
        }
    }
}
