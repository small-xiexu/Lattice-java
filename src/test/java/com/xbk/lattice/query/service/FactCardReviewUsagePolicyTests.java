package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardReviewUsagePolicy 测试
 *
 * 职责：验证卡级审查状态在 query 使用策略中的通用语义
 *
 * @author xiexu
 */
class FactCardReviewUsagePolicyTests {

    /**
     * 验证冲突卡不会进入 query 候选。
     */
    @Test
    void shouldRejectConflictCandidate() {
        boolean allowed = FactCardReviewUsagePolicy.allowsQueryCandidate("conflict");

        assertThat(allowed).isFalse();
    }

    /**
     * 验证不完整卡仍可作为主证据，由覆盖校验提示缺口。
     */
    @Test
    void shouldAllowIncompleteAsPrimaryEvidence() {
        assertThat(FactCardReviewUsagePolicy.allowsQueryCandidate("incomplete")).isTrue();
        assertThat(FactCardReviewUsagePolicy.allowsPrimaryEvidence("incomplete")).isTrue();
    }

    /**
     * 验证人工审查卡只作为背景，不参与结构化主证据保护。
     */
    @Test
    void shouldTreatNeedsHumanReviewAsBackgroundOnly() {
        assertThat(FactCardReviewUsagePolicy.allowsQueryCandidate("needs_human_review")).isTrue();
        assertThat(FactCardReviewUsagePolicy.allowsPrimaryEvidence("needs_human_review")).isFalse();
        assertThat(FactCardReviewUsagePolicy.isBackgroundOnly("needs_human_review")).isTrue();
    }

    /**
     * 验证低置信和人工审查卡会降权。
     */
    @Test
    void shouldDemoteLowConfidenceAndHumanReviewScores() {
        assertThat(FactCardReviewUsagePolicy.adjustScore(10.0D, "valid")).isEqualTo(10.0D);
        assertThat(FactCardReviewUsagePolicy.adjustScore(10.0D, "low_confidence")).isLessThan(10.0D);
        assertThat(FactCardReviewUsagePolicy.adjustScore(10.0D, "needs_human_review")).isLessThan(10.0D);
    }
}
