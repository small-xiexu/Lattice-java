package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReviewerAgent 测试
 *
 * 职责：验证审查超时与正常审查的确定行为
 *
 * @author xiexu
 */
class ReviewerAgentTests {

    /**
     * 验证审查超时时会返回确定的 TIMEOUT_FALLBACK 结果。
     */
    @Test
    void shouldReturnTimeoutFallbackWhenReviewerTimesOut() {
        ReviewerAgent reviewerAgent = new ReviewerAgent(
                new TimeoutReviewerGateway(),
                new ReviewResultParser()
        );

        ReviewResult reviewResult = reviewerAgent.review(
                "payment timeout retry=3 是什么配置",
                "Payment Timeout：retry=3",
                List.of("payment/analyze.json")
        );

        assertThat(reviewResult.isPass()).isTrue();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.TIMEOUT_FALLBACK);
        assertThat(reviewResult.getIssues()).isEmpty();
    }

    /**
     * 验证正常审查结果会被透传为 PASSED。
     */
    @Test
    void shouldReturnParsedReviewResultWhenGatewaySucceeds() {
        ReviewerAgent reviewerAgent = new ReviewerAgent(
                new StaticReviewerGateway("{\"pass\":true,\"issues\":[]}"),
                new ReviewResultParser()
        );

        ReviewResult reviewResult = reviewerAgent.review(
                "payment timeout retry=3 是什么配置",
                "Payment Timeout：retry=3",
                List.of("payment/analyze.json")
        );

        assertThat(reviewResult.isPass()).isTrue();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.PASSED);
    }

    /**
     * 固定返回超时的审查网关替身。
     *
     * @author xiexu
     */
    private static class TimeoutReviewerGateway implements ReviewerGateway {

        /**
         * 执行审查。
         *
         * @param reviewPrompt 审查提示词
         * @return 审查原始输出
         */
        @Override
        public String review(String reviewPrompt) {
            throw new ReviewTimeoutException("review timed out");
        }
    }

    /**
     * 固定返回原始结果的审查网关替身。
     *
     * @author xiexu
     */
    private static class StaticReviewerGateway implements ReviewerGateway {

        private final String rawResult;

        /**
         * 创建固定返回值的审查网关替身。
         *
         * @param rawResult 原始结果
         */
        private StaticReviewerGateway(String rawResult) {
            this.rawResult = rawResult;
        }

        /**
         * 执行审查。
         *
         * @param reviewPrompt 审查提示词
         * @return 审查原始输出
         */
        @Override
        public String review(String reviewPrompt) {
            return rawResult;
        }
    }
}
