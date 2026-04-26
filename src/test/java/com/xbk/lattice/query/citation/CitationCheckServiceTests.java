package com.xbk.lattice.query.citation;

import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CitationCheckService 测试
 *
 * 职责：验证 claim 分段、引用核验统计与规则修复行为
 *
 * @author xiexu
 */
class CitationCheckServiceTests {

    /**
     * 验证规则核验会正确统计 verified / skipped / unsupported。
     */
    @Test
    void shouldCheckClaimsAndAggregateCoverage() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );

        CitationCheckReport report = citationCheckService.check("""
                支付路由采用标准链路 [[payment-routing]]

                RoutePlanner 暴露了 /payments 路径 [→ src/main/java/payment/RoutePlanner.java, lines 12-24]

                退款一定会触发人工复核
                """);

        assertThat(report.getClaimSegments()).hasSize(3);
        assertThat(report.getVerifiedCount()).isEqualTo(1);
        assertThat(report.getSkippedCount()).isEqualTo(1);
        assertThat(report.getDemotedCount()).isZero();
        assertThat(report.getUnsupportedClaimCount()).isEqualTo(2);
        assertThat(report.getCoverageRate()).isEqualTo(1.0D / 3.0D);
        assertThat(report.isNoCitation()).isFalse();
        assertThat(report.getUnmatchedLiteralCount()).isZero();
        assertThat(report.getUnusedProjectionCount()).isZero();
        assertThat(report.getProjectionMismatchCount()).isZero();
    }

    /**
     * 验证修复逻辑会移除失败引用，并给无引用 claim 补“当前证据不足”标记。
     */
    @Test
    void shouldRepairDemotedAndNoCitationClaims() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        String answer = """
                支付路由采用标准链路 [[unknown-article]]

                退款一定会触发人工复核
                """;

        CitationCheckReport report = citationCheckService.check(answer);
        String repaired = citationCheckService.repair(answer, report);

        assertThat(report.getDemotedCount()).isEqualTo(1);
        assertThat(repaired).contains("支付路由采用标准链路（当前证据不足）");
        assertThat(repaired).contains("（当前证据不足）");
    }

    /**
     * 验证 projection 白名单会统计白名单外 literal 与未使用 projection。
     */
    @Test
    void shouldCountProjectionMismatchAndUnusedProjectionWhenBundleProvided() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "支付路由采用标准链路 [[payment-routing]]",
                List.of(
                        new AnswerProjection(
                                1,
                                "ev#1",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-routing]]",
                                "payment-routing",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "ev#2",
                                ProjectionCitationFormat.ARTICLE,
                                "[[unused-article]]",
                                "unused-article",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        CitationCheckReport report = citationCheckService.check("""
                支付路由采用标准链路 [[payment-routing]]

                退款一定会触发人工复核 [[unknown-article]]
                """, answerProjectionBundle);

        assertThat(report.getVerifiedCount()).isEqualTo(1);
        assertThat(report.getDemotedCount()).isEqualTo(1);
        assertThat(report.getUnmatchedLiteralCount()).isEqualTo(1);
        assertThat(report.getUnusedProjectionCount()).isEqualTo(1);
        assertThat(report.getProjectionMismatchCount()).isEqualTo(1);
    }

    /**
     * 验证传入空 projection 白名单时不会回退到直接引用校验。
     */
    @Test
    void shouldFailClosedWhenProjectionBundleIsEmpty() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "支付路由采用标准链路 [[payment-routing]]",
                List.of()
        );

        CitationCheckReport report = citationCheckService.check(
                "支付路由采用标准链路 [[payment-routing]]",
                answerProjectionBundle
        );

        assertThat(report.getVerifiedCount()).isZero();
        assertThat(report.getDemotedCount()).isEqualTo(1);
        assertThat(report.getUnmatchedLiteralCount()).isEqualTo(1);
        assertThat(report.getProjectionMismatchCount()).isEqualTo(1);
    }

    /**
     * 验证同一 literal 命中多个 ACTIVE projection 时不会被当成唯一解析。
     */
    @Test
    void shouldDemoteAmbiguousActiveProjectionLiteral() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "支付路由采用标准链路 [[payment-routing]]",
                List.of(
                        new AnswerProjection(
                                1,
                                "ev#1",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-routing]]",
                                "payment-routing",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "ev#2",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-routing]]",
                                "payment-routing-v2",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        CitationCheckReport report = citationCheckService.check(
                "支付路由采用标准链路 [[payment-routing]]",
                answerProjectionBundle
        );

        assertThat(report.getVerifiedCount()).isZero();
        assertThat(report.getDemotedCount()).isEqualTo(1);
        assertThat(report.getResults()).extracting(CitationValidationResult::getReason)
                .containsExactly("projection_literal_ambiguous");
    }

    /**
     * 验证缺失 anchorId 的 projection 不会进入 claim 校验。
     */
    @Test
    void shouldRejectProjectionWithoutAnchorId() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "支付路由采用标准链路 [[payment-routing]]",
                List.of(new AnswerProjection(
                        1,
                        "",
                        ProjectionCitationFormat.ARTICLE,
                        "[[payment-routing]]",
                        "payment-routing",
                        ProjectionStatus.ACTIVE,
                        0,
                        null
                ))
        );

        CitationCheckReport report = citationCheckService.check(
                "支付路由采用标准链路 [[payment-routing]]",
                answerProjectionBundle
        );

        assertThat(report.getResults()).extracting(CitationValidationResult::getReason)
                .containsExactly("projection_anchor_missing");
    }

    /**
     * 验证 citation literal 格式与 projection sourceType 不一致时直接降级。
     */
    @Test
    void shouldRejectProjectionSourceTypeMismatch() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "支付路由采用标准链路 [[payment-routing]]",
                List.of(new AnswerProjection(
                        1,
                        "ev#1",
                        ProjectionCitationFormat.SOURCE_FILE,
                        "[[payment-routing]]",
                        "src/main/java/payment/RoutePlanner.java",
                        ProjectionStatus.ACTIVE,
                        0,
                        null
                ))
        );

        CitationCheckReport report = citationCheckService.check(
                "支付路由采用标准链路 [[payment-routing]]",
                answerProjectionBundle
        );

        assertThat(report.getResults()).extracting(CitationValidationResult::getReason)
                .containsExactly("projection_source_type_mismatch");
    }

    /**
     * 验证未使用 projection 只进入观测指标，不触发 projection mismatch 或 repair。
     */
    @Test
    void shouldNotTreatUnusedProjectionAsProjectionMismatch() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "支付路由采用标准链路 [[payment-routing]]",
                List.of(
                        new AnswerProjection(
                                1,
                                "ev#1",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-routing]]",
                                "payment-routing",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "ev#2",
                                ProjectionCitationFormat.ARTICLE,
                                "[[unused-article]]",
                                "unused-article",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        CitationCheckReport report = citationCheckService.check(
                "支付路由采用标准链路 [[payment-routing]]",
                answerProjectionBundle
        );

        assertThat(report.getUnusedProjectionCount()).isEqualTo(1);
        assertThat(report.getProjectionMismatchCount()).isZero();
        assertThat(report.getUnsupportedClaimCount()).isZero();
        assertThat(citationCheckService.shouldRepair(report, CitationCheckOptions.defaults(), 0)).isFalse();
    }

    /**
     * 验证 repair 会把被移除的 ACTIVE projection 记录为 append-only 历史。
     */
    @Test
    void shouldAppendProjectionHistoryWhenRepairRemovesFailedLiteral() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new FixedCitationValidator()
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "退款一定会触发人工复核 [[unknown-article]]",
                List.of(new AnswerProjection(
                        1,
                        "ev#1",
                        ProjectionCitationFormat.ARTICLE,
                        "[[unknown-article]]",
                        "unknown-article",
                        ProjectionStatus.ACTIVE,
                        0,
                        null
                ))
        );
        CitationCheckReport report = citationCheckService.check(
                answerProjectionBundle.getAnswerMarkdown(),
                answerProjectionBundle
        );

        String repairedAnswer = citationCheckService.repair(answerProjectionBundle.getAnswerMarkdown(), report);
        AnswerProjectionBundle repairedBundle = citationCheckService.repairProjectionBundle(
                answerProjectionBundle,
                report,
                repairedAnswer
        );

        assertThat(repairedAnswer).doesNotContain("[[unknown-article]]");
        assertThat(repairedBundle.getProjections()).hasSize(2);
        assertThat(repairedBundle.getProjections()).extracting(AnswerProjection::getStatus)
                .containsExactly(ProjectionStatus.REPLACED, ProjectionStatus.REMOVED);
        assertThat(repairedBundle.getProjections().get(1).getRepairedFromProjectionOrdinal()).isEqualTo(1);
        assertThat(repairedBundle.getProjections().get(1).getRepairRound()).isEqualTo(1);
    }

    /**
     * 验证 no_hard_fact_literals 的 skipped claim 也会计入覆盖率。
     */
    @Test
    void shouldTreatSkippedClaimWithCitationAsCovered() {
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new SkipNoHardFactCitationValidator()
        );

        CitationCheckReport report = citationCheckService.check("""
                这是一个一般性的系统描述 [[payment-routing]]

                退款一定会触发人工复核
                """);

        assertThat(report.getSkippedCount()).isEqualTo(1);
        assertThat(report.getUnsupportedClaimCount()).isEqualTo(1);
        assertThat(report.getCoverageRate()).isEqualTo(0.5D);
    }

    private static class FixedCitationValidator extends CitationValidator {

        private FixedCitationValidator() {
            super(null, null);
        }

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
            if ("payment-routing".equals(citation.getTargetKey())) {
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
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "insufficient_overlap",
                    "",
                    citation.getOrdinal()
            );
        }
    }

    private static class SkipNoHardFactCitationValidator extends CitationValidator {

        private SkipNoHardFactCitationValidator() {
            super(null, null);
        }

        @Override
        public CitationValidationResult validate(Citation citation) {
            if ("payment-routing".equals(citation.getTargetKey())) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.SKIPPED,
                        0.0D,
                        "no_hard_fact_literals",
                        citation.getContextWindow(),
                        citation.getOrdinal()
                );
            }
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "insufficient_overlap",
                    citation.getContextWindow(),
                    citation.getOrdinal()
            );
        }
    }
}
