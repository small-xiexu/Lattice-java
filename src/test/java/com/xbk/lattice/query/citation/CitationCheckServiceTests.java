package com.xbk.lattice.query.citation;

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
        assertThat(report.getCoverageRate()).isEqualTo(2.0D / 3.0D);
        assertThat(report.isNoCitation()).isFalse();
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
        assertThat(repaired).contains("（引用未通过核验：unknown-article）");
        assertThat(repaired).contains("（当前证据不足）");
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
}
