package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.compiler.service.ArticleReviewerGateway;
import com.xbk.lattice.query.domain.ReviewResult;

/**
 * 默认 ReviewerAgent
 *
 * 职责：复用现有文章审查网关完成单轮结构化审查
 *
 * @author xiexu
 */
public class DefaultReviewerAgent implements ReviewerAgent {

    private static final String AGENT_ROLE = "ReviewerAgent";

    private static final String ROUTE_ROLE = "reviewer";

    private final ArticleReviewerGateway articleReviewerGateway;

    private final AgentModelRouter agentModelRouter;

    /**
     * 创建默认 ReviewerAgent。
     *
     * @param articleReviewerGateway 文章审查网关
     * @param agentModelRouter Agent 模型路由器
     */
    public DefaultReviewerAgent(
            ArticleReviewerGateway articleReviewerGateway,
            AgentModelRouter agentModelRouter
    ) {
        this.articleReviewerGateway = articleReviewerGateway;
        this.agentModelRouter = agentModelRouter;
    }

    /**
     * 执行单篇草稿审查。
     *
     * @param reviewTask 审查输入任务
     * @return 审查输出结果
     */
    @Override
    public ReviewerResult review(ReviewTask reviewTask) {
        ReviewResult reviewResult = ReviewResult.passed();
        if (articleReviewerGateway != null) {
            String scopeId = reviewTask.getScopeId();
            if (scopeId == null || scopeId.isBlank()) {
                reviewResult = articleReviewerGateway.review(
                        reviewTask.getArticleRecord().getContent(),
                        reviewTask.getSourceContents()
                );
            }
            else {
                reviewResult = articleReviewerGateway.review(
                        reviewTask.getArticleRecord().getContent(),
                        reviewTask.getSourceContents(),
                        scopeId,
                        reviewTask.getScene(),
                        ROUTE_ROLE
                );
            }
        }
        return new ReviewerResult(reviewResult, AGENT_ROLE, resolveRoute(reviewTask));
    }

    /**
     * 返回当前路由标签。
     *
     * @return 路由标签
     */
    private String resolveRoute(ReviewTask reviewTask) {
        if (agentModelRouter == null) {
            return "rule-based";
        }
        return agentModelRouter.routeFor(reviewTask.getScopeId(), reviewTask.getScene(), ROUTE_ROLE);
    }
}
