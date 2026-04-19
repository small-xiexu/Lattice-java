package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.query.domain.ReviewResult;

/**
 * ReviewerAgent 输出结果
 *
 * 职责：返回结构化审查结果与执行元信息
 *
 * @author xiexu
 */
public class ReviewerResult {

    private final ReviewResult reviewResult;

    private final String agentRole;

    private final String modelRoute;

    /**
     * 创建 ReviewerAgent 输出结果。
     *
     * @param reviewResult 审查结果
     * @param agentRole Agent 角色
     * @param modelRoute 模型路由
     */
    public ReviewerResult(ReviewResult reviewResult, String agentRole, String modelRoute) {
        this.reviewResult = reviewResult;
        this.agentRole = agentRole;
        this.modelRoute = modelRoute;
    }

    /**
     * 返回审查结果。
     *
     * @return 审查结果
     */
    public ReviewResult getReviewResult() {
        return reviewResult;
    }

    /**
     * 返回 Agent 角色。
     *
     * @return Agent 角色
     */
    public String getAgentRole() {
        return agentRole;
    }

    /**
     * 返回模型路由。
     *
     * @return 模型路由
     */
    public String getModelRoute() {
        return modelRoute;
    }
}
