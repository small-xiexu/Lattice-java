package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.compiler.service.ReviewFixService;
import com.xbk.lattice.query.domain.ReviewIssue;

import java.util.List;

/**
 * 默认 FixerAgent
 *
 * 职责：复用现有修复服务按审查问题修复单篇草稿
 *
 * @author xiexu
 */
public class DefaultFixerAgent implements FixerAgent {

    private static final String AGENT_ROLE = "FixerAgent";

    private static final String ROUTE_ROLE = "fixer";

    private final ReviewFixService reviewFixService;

    private final AgentModelRouter agentModelRouter;

    /**
     * 创建默认 FixerAgent。
     *
     * @param reviewFixService 审查修复服务
     * @param agentModelRouter Agent 模型路由器
     */
    public DefaultFixerAgent(ReviewFixService reviewFixService, AgentModelRouter agentModelRouter) {
        this.reviewFixService = reviewFixService;
        this.agentModelRouter = agentModelRouter;
    }

    /**
     * 执行单篇草稿修复。
     *
     * @param fixTask 修复输入任务
     * @return 修复输出结果
     */
    @Override
    public FixerResult fix(FixTask fixTask) {
        List<ReviewIssue> reviewIssues = fixTask.getReviewIssues();
        if (reviewFixService == null || reviewIssues == null || reviewIssues.isEmpty()) {
            return new FixerResult(null, false, AGENT_ROLE, resolveRoute(fixTask));
        }
        String scopeId = fixTask.getScopeId();
        String fixedContent;
        if (scopeId == null || scopeId.isBlank()) {
            fixedContent = reviewFixService.applyFix(
                    fixTask.getArticleRecord().getContent(),
                    reviewIssues,
                    fixTask.getSourceContents()
            );
        }
        else {
            fixedContent = reviewFixService.applyFix(
                    fixTask.getArticleRecord().getContent(),
                    reviewIssues,
                    fixTask.getSourceContents(),
                    scopeId,
                    fixTask.getScene(),
                    ROUTE_ROLE
            );
        }
        boolean fixed = fixedContent != null && !fixedContent.isBlank();
        return new FixerResult(fixedContent, fixed, AGENT_ROLE, resolveRoute(fixTask));
    }

    /**
     * 返回当前路由标签。
     *
     * @return 路由标签
     */
    private String resolveRoute(FixTask fixTask) {
        if (agentModelRouter == null) {
            return "fallback";
        }
        return agentModelRouter.routeFor(fixTask.getScopeId(), fixTask.getScene(), ROUTE_ROLE);
    }
}
