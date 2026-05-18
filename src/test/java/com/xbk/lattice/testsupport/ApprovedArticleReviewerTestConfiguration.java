package com.xbk.lattice.testsupport;

import com.xbk.lattice.compiler.service.ArticleReviewerGateway;
import com.xbk.lattice.compiler.service.CompileExecutionRequest;
import com.xbk.lattice.query.domain.ReviewResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试用通过态文章审查配置
 *
 * 职责：为非审查主题的集成测试提供稳定的 LLM reviewer 通过结果
 *
 * @author xiexu
 */
@TestConfiguration
public class ApprovedArticleReviewerTestConfiguration {

    /**
     * 提供通过态文章审查网关。
     *
     * @return 通过态文章审查网关
     */
    @Bean
    @Primary
    public ArticleReviewerGateway approvedArticleReviewerGateway() {
        return new ApprovedArticleReviewerGateway();
    }

    /**
     * 测试用通过态文章审查网关。
     *
     * 职责：避免非审查主题集成测试依赖真实 LLM 可用性
     *
     * @author xiexu
     */
    private static class ApprovedArticleReviewerGateway extends ArticleReviewerGateway {

        /**
         * 创建测试用通过态文章审查网关。
         */
        private ApprovedArticleReviewerGateway() {
            super(null, null, null, null);
        }

        /**
         * 执行文章审查。
         *
         * @param articleContent 文章内容
         * @param sourceContents 源文件正文
         * @return 审查结果
         */
        @Override
        public ReviewResult review(String articleContent, String sourceContents) {
            return ReviewResult.passed();
        }

        /**
         * 执行文章审查。
         *
         * @param articleContent 文章内容
         * @param sourceContents 源文件正文
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @return 审查结果
         */
        @Override
        public ReviewResult review(
                String articleContent,
                String sourceContents,
                String scopeId,
                String scene,
                String agentRole
        ) {
            return ReviewResult.passed();
        }

        /**
         * 执行文章审查。
         *
         * @param articleContent 文章内容
         * @param sourceContents 源文件正文
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param requestedReviewMode 请求审查模式
         * @return 审查结果
         */
        @Override
        public ReviewResult review(
                String articleContent,
                String sourceContents,
                String scopeId,
                String scene,
                String agentRole,
                String requestedReviewMode
        ) {
            return ReviewResult.passed();
        }

        /**
         * 解析当前审查路由。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @return 审查路由
         */
        @Override
        public String resolveRoute(String scopeId, String scene, String agentRole) {
            return "llm-test-approved";
        }

        /**
         * 解析当前审查路由。
         *
         * @param scopeId 作用域标识
         * @param scene 场景
         * @param agentRole Agent 角色
         * @param requestedReviewMode 请求审查模式
         * @return 审查路由
         */
        @Override
        public String resolveRoute(String scopeId, String scene, String agentRole, String requestedReviewMode) {
            String reviewMode = resolveReviewMode(scopeId, requestedReviewMode);
            if (CompileExecutionRequest.isLlmReviewMode(reviewMode)) {
                return "llm-test-approved";
            }
            return "rule-based";
        }

        /**
         * 解析当前作业的审查模式。
         *
         * @param scopeId 作用域标识
         * @param requestedReviewMode 请求审查模式
         * @return 审查模式
         */
        @Override
        public String resolveReviewMode(String scopeId, String requestedReviewMode) {
            if (requestedReviewMode == null || requestedReviewMode.isBlank()) {
                return CompileExecutionRequest.REVIEW_MODE_LLM;
            }
            return CompileExecutionRequest.normalizeReviewMode(requestedReviewMode);
        }
    }
}
