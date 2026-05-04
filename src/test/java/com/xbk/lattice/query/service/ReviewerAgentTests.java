package com.xbk.lattice.query.service;

import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.domain.ReviewStatus;
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

        assertThat(reviewResult.isPass()).isFalse();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.TIMEOUT_FALLBACK);
        assertThat(reviewResult.getIssues()).isEmpty();
    }

    /**
     * 验证正常审查结果会被透传为 PASSED。
     */
    @Test
    void shouldReturnParsedReviewResultWhenGatewaySucceeds() {
        ReviewerAgent reviewerAgent = new ReviewerAgent(
                new StaticReviewerGateway("{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"),
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
     * 验证带作用域的审查会把 scope / scene / role 透传给底层网关。
     */
    @Test
    void shouldDelegateScopedReviewArgumentsToGateway() {
        CapturingReviewerGateway reviewerGateway = new CapturingReviewerGateway(
                "{\"approved\":true,\"rewriteRequired\":false,\"riskLevel\":\"LOW\",\"issues\":[],\"userFacingRewriteHints\":[],\"cacheWritePolicy\":\"WRITE\"}"
        );
        ReviewerAgent reviewerAgent = new ReviewerAgent(reviewerGateway, new ReviewResultParser());

        ReviewResult reviewResult = reviewerAgent.review(
                "query-1",
                "query",
                "reviewer",
                "payment timeout retry=3 是什么配置",
                "Payment Timeout：retry=3",
                List.of("payment/analyze.json")
        );

        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.PASSED);
        assertThat(reviewerGateway.scopeId).isEqualTo("query-1");
        assertThat(reviewerGateway.scene).isEqualTo("query");
        assertThat(reviewerGateway.agentRole).isEqualTo("reviewer");
        assertThat(reviewerAgent.currentRoute("query-1", "query", "reviewer")).isEqualTo("query.reviewer.claude");
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

    /**
     * 捕获作用域参数的审查网关替身。
     *
     * @author xiexu
     */
    private static class CapturingReviewerGateway implements ReviewerGateway {

        private final String rawResult;

        private String scopeId;

        private String scene;

        private String agentRole;

        /**
         * 创建捕获作用域参数的网关替身。
         *
         * @param rawResult 原始结果
         */
        private CapturingReviewerGateway(String rawResult) {
            this.rawResult = rawResult;
        }

        /**
         * 返回固定原始结果。
         *
         * @param reviewPrompt 审查提示词
         * @return 原始审查输出
         */
        @Override
        public String review(String reviewPrompt) {
            return rawResult;
        }

        /**
         * 捕获带作用域的审查参数。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param reviewPrompt 审查提示词
         * @return 原始审查输出
         */
        @Override
        public String review(String scopeId, String scene, String agentRole, String reviewPrompt) {
            this.scopeId = scopeId;
            this.scene = scene;
            this.agentRole = agentRole;
            return rawResult;
        }

        /**
         * 返回固定路由。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @return 路由标签
         */
        @Override
        public String currentRoute(String scopeId, String scene, String agentRole) {
            return "query.reviewer.claude";
        }
    }
}
