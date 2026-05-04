package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryCitationMarkerResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryResponseCitationAssembler 测试
 *
 * 职责：验证 simple query 最终 sources/articles 的 projection 组装去重口径
 *
 * @author xiexu
 */
class QueryResponseCitationAssemblerTests {

    /**
     * 验证 citation marker 会把同一处连续引用合并成一个圆点语义，并保留资料明细。
     */
    @Test
    void shouldBuildCitationMarkersForInlineCitationGroup() {
        QueryArticleHit articleHit = new QueryArticleHit(
                11L,
                "payment-routing",
                "payment-routing",
                "Payment Routing",
                "RoutePlanner 暴露了 /payments 路径",
                "{\"description\":\"payment routing\"}",
                List.of("src/main/java/payment/RoutePlanner.java"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "src/main/java/payment/RoutePlanner.java",
                "RoutePlanner.java",
                "RoutePlanner 暴露了 /payments 路径",
                "{\"filePath\":\"src/main/java/payment/RoutePlanner.java\"}",
                List.of("src/main/java/payment/RoutePlanner.java"),
                9.5D
        );
        String answer = "RoutePlanner 暴露了 /payments 路径 [[payment-routing]][→ src/main/java/payment/RoutePlanner.java]";
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                answer,
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:payment-routing",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-routing]]",
                                "payment-routing",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:src/main/java/payment/RoutePlanner.java",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ src/main/java/payment/RoutePlanner.java]",
                                "src/main/java/payment/RoutePlanner.java",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );
        CitationCheckReport citationCheckReport = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        ).check(answer, answerProjectionBundle);

        List<QueryCitationMarkerResponse> markers = QueryResponseCitationAssembler.toCitationMarkerResponses(
                citationCheckReport,
                answerProjectionBundle,
                List.of(articleHit, sourceHit)
        );

        assertThat(markers).hasSize(1);
        assertThat(markers.get(0).getCitationLiteral())
                .isEqualTo("[[payment-routing]][→ src/main/java/payment/RoutePlanner.java]");
        assertThat(markers.get(0).getSourceCount()).isEqualTo(2);
        assertThat(markers.get(0).getSources()).hasSize(2);
        assertThat(markers.get(0).getSources()).extracting(source -> source.getSourceType())
                .containsExactly("ARTICLE", "SOURCE_FILE");
        assertThat(markers.get(0).getSources().get(1).getTitle())
                .isEqualTo("src/main/java/payment/RoutePlanner.java");
    }

    /**
     * 验证 citation marker 会用原文中带行号的 SOURCE_FILE 引用作为替换范围。
     */
    @Test
    void shouldBuildCitationMarkerLiteralWithLineScopedSourceCitation() {
        QueryArticleHit articleHit = new QueryArticleHit(
                11L,
                "payment-routing",
                "payment-routing",
                "Payment Routing",
                "RoutePlanner 暴露了 /payments 路径",
                "{\"description\":\"payment routing\"}",
                List.of("src/main/java/payment/RoutePlanner.java"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "src/main/java/payment/RoutePlanner.java",
                "RoutePlanner.java",
                "RoutePlanner 暴露了 /payments 路径",
                "{\"filePath\":\"src/main/java/payment/RoutePlanner.java\"}",
                List.of("src/main/java/payment/RoutePlanner.java"),
                9.5D
        );
        String answer = "RoutePlanner 暴露了 /payments 路径 [[payment-routing]]"
                + " [→ src/main/java/payment/RoutePlanner.java, lines 12-24]";
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                answer,
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:payment-routing",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-routing]]",
                                "payment-routing",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:src/main/java/payment/RoutePlanner.java",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ src/main/java/payment/RoutePlanner.java]",
                                "src/main/java/payment/RoutePlanner.java",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );
        CitationCheckReport citationCheckReport = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        ).check(answer, answerProjectionBundle);

        List<QueryCitationMarkerResponse> markers = QueryResponseCitationAssembler.toCitationMarkerResponses(
                citationCheckReport,
                answerProjectionBundle,
                List.of(articleHit, sourceHit)
        );

        assertThat(markers).hasSize(1);
        assertThat(markers.get(0).getCitationLiteral())
                .isEqualTo("[[payment-routing]] [→ src/main/java/payment/RoutePlanner.java, lines 12-24]");
        assertThat(markers.get(0).getCitationLiterals())
                .containsExactly("[[payment-routing]]", "[→ src/main/java/payment/RoutePlanner.java]");
    }

    /**
     * 验证 citation marker 会把章节说明也纳入完整替换范围，避免正文残留原始引用尾巴。
     */
    @Test
    void shouldBuildCitationMarkerLiteralWithSourceSectionSuffix() {
        QueryArticleHit articleHit = new QueryArticleHit(
                11L,
                "fc-fulfillment-digital",
                "fc-fulfillment-digital",
                "FC 履约中台",
                "FC 是履约中台系统",
                "{\"description\":\"fc fulfillment\"}",
                List.of("卡券三期-迁移方案.md"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "卡券三期-迁移方案.md",
                "卡券三期-迁移方案.md",
                "FC 是履约中台系统",
                "{\"filePath\":\"卡券三期-迁移方案.md\"}",
                List.of("卡券三期-迁移方案.md"),
                9.5D
        );
        String answer = "FC 是履约中台 [[fc-fulfillment-digital]]"
                + "[→ 卡券三期-迁移方案.md, 1.1 业务背景]";
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                answer,
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:fc-fulfillment-digital",
                                ProjectionCitationFormat.ARTICLE,
                                "[[fc-fulfillment-digital]]",
                                "fc-fulfillment-digital",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:卡券三期-迁移方案.md",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ 卡券三期-迁移方案.md]",
                                "卡券三期-迁移方案.md",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );
        CitationCheckReport citationCheckReport = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        ).check(answer, answerProjectionBundle);

        List<QueryCitationMarkerResponse> markers = QueryResponseCitationAssembler.toCitationMarkerResponses(
                citationCheckReport,
                answerProjectionBundle,
                List.of(articleHit, sourceHit)
        );

        assertThat(markers).hasSize(1);
        assertThat(markers.get(0).getCitationLiteral())
                .isEqualTo("[[fc-fulfillment-digital]][→ 卡券三期-迁移方案.md, 1.1 业务背景]");
        assertThat(markers.get(0).getCitationLiterals())
                .containsExactly("[[fc-fulfillment-digital]]", "[→ 卡券三期-迁移方案.md]");
    }

    /**
     * 验证同一 article 同时出现 ARTICLE 与 SOURCE_FILE projection 时，只保留 article 级来源。
     */
    @Test
    void shouldPreferArticleSourceWhenArticleAndSourceFileProjectSameHit() {
        QueryArticleHit articleHit = new QueryArticleHit(
                11L,
                "payment-timeout-article",
                "payment-timeout",
                "Payment Timeout",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("payments/gateway-config.yaml"),
                10.0D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论：`payment.timeout.retry` 默认值为 `5`。[[payment-timeout-article]][→ payments/gateway-config.yaml]",
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:payment-timeout-article",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-timeout-article]]",
                                "payment-timeout-article",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:payments/gateway-config.yaml",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ payments/gateway-config.yaml]",
                                "payments/gateway-config.yaml",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                answerProjectionBundle,
                List.of(articleHit),
                true
        );
        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                answerProjectionBundle,
                List.of(articleHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("payment-timeout-article");
        assertThat(sourceResponses.get(0).getSourcePaths()).containsExactly("payments/gateway-config.yaml");
        assertThat(sourceResponses.get(0).getDerivation()).isEqualTo("PROJECTION");
        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("payment-timeout-article");
        assertThat(articleResponses.get(0).getDerivation()).isEqualTo("PROJECTION");
    }

    /**
     * 验证 ARTICLE projection 命中 conceptId、SOURCE_FILE projection 命中 articleKey 时，也不会重复返回 articles/sources。
     */
    @Test
    void shouldDeduplicateProjectionResponsesAcrossConceptIdAndArticleKeyIdentities() {
        QueryArticleHit articleHit = new QueryArticleHit(
                21L,
                "legacy-default--readme",
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("README.md"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"filePath\":\"README.md\"}",
                List.of("README.md"),
                9.0D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论：`payment.timeout.retry` 默认值为 `5`。[[readme]][→ README.md]",
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:readme",
                                ProjectionCitationFormat.ARTICLE,
                                "[[readme]]",
                                "readme",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:README.md",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ README.md]",
                                "README.md",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );
        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(sourceResponses.get(0).getConceptId()).isEqualTo("readme");
        assertThat(sourceResponses.get(0).getSourcePaths()).containsExactly("README.md");
        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(articleResponses.get(0).getConceptId()).isEqualTo("readme");
    }

    /**
     * 验证当 SOURCE 命中先占据 conceptId、ARTICLE 命中后补全 articleKey/路径时，最终仍只返回统一 article 身份。
     */
    @Test
    void shouldPreferArticleMetadataWhenSourceHitOnlyProvidesConceptIdentity() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"filePath\":\"README.md\"}",
                List.of(),
                9.5D
        );
        QueryArticleHit articleHit = new QueryArticleHit(
                22L,
                "legacy-default--readme",
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("README.md"),
                9.0D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论：`payment.timeout.retry` 默认值为 `5`。[[readme]][→ README.md]",
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:readme",
                                ProjectionCitationFormat.ARTICLE,
                                "[[readme]]",
                                "readme",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:README.md",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ README.md]",
                                "README.md",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );
        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(sourceResponses.get(0).getConceptId()).isEqualTo("readme");
        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(articleResponses.get(0).getConceptId()).isEqualTo("readme");
    }

    /**
     * 验证 TOP_K 兜底 articles 会按 article 身份去重，而不是把同一 article 重复返回。
     */
    @Test
    void shouldDeduplicateTopKArticleResponsesByArticleIdentity() {
        QueryArticleHit firstHit = new QueryArticleHit(
                11L,
                "payment-timeout-article",
                "payment-timeout",
                "Payment Timeout",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("payments/gateway-config.yaml"),
                10.0D
        );
        QueryArticleHit duplicateHit = new QueryArticleHit(
                12L,
                "payment-timeout-article",
                "payment-timeout",
                "Payment Timeout Duplicate",
                "payment.timeout.retry=5",
                "{\"description\":\"duplicate hit from another channel\"}",
                List.of("payments/gateway-config.yaml"),
                9.2D
        );

        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                null,
                List.of(firstHit, duplicateHit),
                true
        );

        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("payment-timeout-article");
        assertThat(articleResponses.get(0).getDerivation()).isEqualTo("TOP_K");
    }

    /**
     * 验证 TOP_K 兜底 sources 会优先保留 article 级来源，而不是再把同一路径的 source hit 重复暴露。
     */
    @Test
    void shouldDeduplicateTopKSourceResponsesAcrossEvidenceTypes() {
        QueryArticleHit articleHit = new QueryArticleHit(
                31L,
                "legacy-default--readme",
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("README.md"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "README.md#0",
                "README.md",
                "payment.timeout.retry=5",
                "{\"filePath\":\"README.md\"}",
                List.of("README.md"),
                9.0D
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                null,
                List.of(articleHit, sourceHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(sourceResponses.get(0).getSourcePaths()).containsExactly("README.md");
        assertThat(sourceResponses.get(0).getDerivation()).isEqualTo("TOP_K");
    }

    private static class FixedCitationValidator extends com.xbk.lattice.query.citation.CitationValidator {

        /**
         * 创建固定结果 citation 校验器。
         */
        private FixedCitationValidator() {
            super(null, null);
        }

        /**
         * 返回固定 citation 校验结果。
         *
         * @param citation 引用
         * @return 校验结果
         */
        @Override
        public CitationValidationResult validate(Citation citation) {
            if (citation.getSourceType() == CitationSourceType.SOURCE_FILE) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.SKIPPED,
                        0.0D,
                        "source_file_skip",
                        citation.getContextWindow(),
                        citation.getOrdinal()
                );
            }
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    0.8D,
                    "rule_overlap_verified",
                    citation.getContextWindow(),
                    citation.getOrdinal()
            );
        }
    }
}
