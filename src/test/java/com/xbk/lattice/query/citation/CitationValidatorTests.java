package com.xbk.lattice.query.citation;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
    void shouldVerifySourceCitationUsingSameParagraphContextWhenClaimHasPartialSupport() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                null,
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ docs/interface-contract.md]",
                CitationSourceType.SOURCE_FILE,
                "docs/interface-contract.md",
                "迁移后对外 path 不可以改",
                """
                `POST /api/demo/v2/inventory/sync` 是库存同步接口。迁移后对外 path 不可以改。
                新链路入口侧必须保持 API path、请求参数、响应参数与旧接口完全一致 [→ docs/interface-contract.md]
                """
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isEqualTo("source_context_overlap_verified");
    }

    @Test
    void shouldDemoteContextCitationWhenClaimIntroducesUnsupportedStrictFact() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                null,
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ docs/interface-contract.md]",
                CitationSourceType.SOURCE_FILE,
                "docs/interface-contract.md",
                "调用方只需要调整目标地址，额外调用 /api/demo/v2/inventory/review 完成人工复核",
                """
                `POST /api/demo/v2/inventory/sync` 是库存同步接口。调用方只需要调整目标地址，额外调用 /api/demo/v2/inventory/review 完成人工复核。
                新链路入口侧必须保持 API path、请求参数、响应参数与旧接口完全一致 [→ docs/interface-contract.md]
                """
        ));

        assertThat(result.isDemoted()).isTrue();
        assertThat(result.getReason()).isEqualTo("source_insufficient_overlap");
    }

    @Test
    void shouldFailSourceCitationWhenSourceFileIsMissing() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                null,
                null
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
                new FixedSourceFileJdbcRepository(),
                null,
                null
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

    /**
     * 验证 SOURCE_FILE citation 能通过 terminal unit 结构化证据验证。
     */
    @Test
    void shouldVerifySourceCitationByTerminalUnitEvidence() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                new FixedTerminalUnitJdbcRepository(),
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ src/main/java/payment/RoutePlanner.java]",
                CitationSourceType.SOURCE_FILE,
                "src/main/java/payment/RoutePlanner.java",
                "RoutePlanner.exposed_path = /payments",
                "RoutePlanner.exposed_path = /payments [→ src/main/java/payment/RoutePlanner.java]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isIn("terminal_unit_evidence_verified",
                "terminal_unit_evidence_near_complete_verified");
    }

    /**
     * 验证 5-token claim 含 3 个匹配（overlap=0.60）在 0.60 阈值下通过
     * high-confidence partial overlap 验证。
     */
    @Test
    void shouldVerifyGreaterTokenClaimWithThreeMatchOverlap() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                null,
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ config/system-guide.yaml]",
                CitationSourceType.SOURCE_FILE,
                "config/system-guide.yaml",
                "xx-yy-zz = 10",
                "xx-yy-zz = 10 [→ config/system-guide.yaml]"
        ));

        assertThat(result.isVerified()).isTrue();
        assertThat(result.getReason()).isEqualTo("source_near_complete_overlap_verified");
    }

    /**
     * 验证 4-token claim 仅含 2 个匹配（overlap=0.50）低于 0.60 阈值仍被 DEMOTED。
     */
    @Test
    void shouldDemoteClaimBelowMinimumOverlapThreshold() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                null,
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ config/system-guide.yaml]",
                CitationSourceType.SOURCE_FILE,
                "config/system-guide.yaml",
                "aa-bb = 10",
                "aa-bb = 10 [→ config/system-guide.yaml]"
        ));

        assertThat(result.isDemoted()).isTrue();
    }

    /**
     * 验证 SOURCE_FILE citation 有 terminal unit 但值不匹配时不会 VERIFIED，
     * 而是回退或 DEMOTED。
     */
    @Test
    void shouldNotVerifyTerminalUnitEvidenceWhenValueMismatch() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                new FixedTerminalUnitJdbcRepository(),
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ src/main/java/payment/RoutePlanner.java]",
                CitationSourceType.SOURCE_FILE,
                "src/main/java/payment/RoutePlanner.java",
                "RoutePlanner.exposed_path = /unknown-path",
                "RoutePlanner.exposed_path = /unknown-path [→ src/main/java/payment/RoutePlanner.java]"
        ));

        assertThat(result.isDemoted()).isTrue();
        assertThat(result.getReason()).isEqualTo("source_insufficient_overlap");
    }

    /**
     * 验证没有 terminal unit 的 source 行为不变，仍走原有 overlap 验证。
     */
    @Test
    void shouldFallbackToSourceOverlapWhenNoTerminalUnitExists() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                new FixedTerminalUnitJdbcRepository(),
                null
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

    /**
     * 验证跨 source file 的 terminal unit 不会被误用于验证。
     */
    @Test
    void shouldNotCrossMatchTerminalUnitsFromDifferentSourceFile() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                new FixedTerminalUnitJdbcRepository(),
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ gateway-field-definitions.xlsx]",
                CitationSourceType.SOURCE_FILE,
                "gateway-field-definitions.xlsx",
                "渠道 = 01",
                "渠道 = 01 [→ gateway-field-definitions.xlsx]"
        ));

        assertThat(result.getReason()).isNotEqualTo("terminal_unit_evidence_verified");
        assertThat(result.getReason()).isNotEqualTo("terminal_unit_evidence_near_complete_verified");
    }

    /**
     * 验证同 source file 下多个 terminal unit 不会因拼接而导致假阳性。
     *
     * Unit A 覆盖 path/key，Unit B 覆盖 value，但没有任何单条 unit 同时覆盖两者。
     * 旧拼接实现会因整体高 overlap 而 VERIFIED，逐条验证必须返回 null 并回退。
     */
    @Test
    void shouldNotVerifyTerminalUnitEvidenceByCombiningDifferentUnits() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                new FixedTerminalUnitJdbcRepository(),
                null
        );

        CitationValidationResult result = citationValidator.validate(new Citation(
                0,
                "[→ src/main/java/payment/RoutePlanner.java]",
                CitationSourceType.SOURCE_FILE,
                "src/main/java/payment/RoutePlanner.java",
                "RoutePlanner.exposed_path = 30s",
                "RoutePlanner.exposed_path = 30s [→ src/main/java/payment/RoutePlanner.java]"
        ));

        assertThat(result.getReason()).isNotEqualTo("terminal_unit_evidence_verified");
        assertThat(result.getReason()).isNotEqualTo("terminal_unit_evidence_near_complete_verified");
    }

    /**
     * 验证非 key=value 格式 claim 即使 source 下有 terminal units 也不走
     * terminal unit 证据路径，仍保持原有 source overlap 验证。
     */
    @Test
    void shouldSkipTerminalUnitEvidenceForNonKeyValueClaim() {
        CitationValidator citationValidator = new CitationValidator(
                new FixedArticleJdbcRepository(),
                new FixedSourceFileJdbcRepository(),
                new FixedTerminalUnitJdbcRepository(),
                null
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
        assertThat(result.getReason()).isIn("source_direct_line_match_verified", "source_rule_overlap_verified");
    }

    private static class FixedTerminalUnitJdbcRepository extends FactCardTerminalUnitJdbcRepository {

        private FixedTerminalUnitJdbcRepository() {
            super(null);
        }

        @Override
        public boolean tableAvailable() {
            return true;
        }

        @Override
        public List<FactCardTerminalUnitRecord> findBySourceFileId(Long sourceFileId) {
            if (sourceFileId != null && sourceFileId == 101L) {
                return List.of(
                        new FactCardTerminalUnitRecord(
                                null, "unit-1", "terminal-unit:unit-1",
                                1L, "payment-routing-card",
                                1L, 101L,
                                List.of(), List.of(),
                                FactCardType.FACT_ENUM, AnswerShape.POLICY,
                                "key_value_list", 0,
                                "RoutePlanner.exposed_path", "RoutePlanner",
                                "exposed_path", "[]",
                                "exposed_path", "[]",
                                "RoutePlanner exposes /payments path",
                                "RoutePlanner.exposed_path = /payments",
                                "/payments", "/payments",
                                "path", "{}", "/payments RoutePlanner.exposed_path",
                                "{}", FactCardReviewStatus.LOW_CONFIDENCE,
                                0.8, "hash-1",
                                null, null
                        ),
                        new FactCardTerminalUnitRecord(
                                null, "unit-2", "terminal-unit:unit-2",
                                1L, "payment-routing-card",
                                1L, 101L,
                                List.of(), List.of(),
                                FactCardType.FACT_ENUM, AnswerShape.POLICY,
                                "key_value_list", 1,
                                "RoutePlanner.timeout", "RoutePlanner",
                                "timeout", "[]",
                                "timeout", "[]",
                                "RoutePlanner timeout setting",
                                "RoutePlanner.timeout = 30s",
                                "30s", "30s",
                                "string", "{}", "30s RoutePlanner.timeout",
                                "{}", FactCardReviewStatus.LOW_CONFIDENCE,
                                0.8, "hash-2",
                                null, null
                        )
                );
            }
            return List.of();
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
            if ("config/system-guide.yaml".equals(filePath)) {
                return Optional.of(new SourceFileRecord(
                        105L,
                        1L,
                        "config/system-guide.yaml",
                        "config/system-guide.yaml",
                        null,
                        "YAML",
                        "YAML",
                        256L,
                        "xx yy 10",
                        "{}",
                        false,
                        "config/system-guide.yaml"
                ));
            }
            if ("docs/interface-contract.md".equals(filePath)) {
                return Optional.of(new SourceFileRecord(
                        104L,
                        1L,
                        "docs/interface-contract.md",
                        "docs/interface-contract.md",
                        null,
                        "Markdown",
                        "MARKDOWN",
                        1024L,
                        """
                        - `/api/demo/v2/inventory/sync` 是库存同步接口。
                        - 新链路入口侧必须保持 API path、请求参数、响应参数与旧接口完全一致。
                        - 调用方只切换目标地址（DNS/配置），上游系统保持零代码改造。
                        """,
                        "{}",
                        false,
                        "docs/interface-contract.md"
                ));
            }
            return Optional.empty();
        }
    }
}
