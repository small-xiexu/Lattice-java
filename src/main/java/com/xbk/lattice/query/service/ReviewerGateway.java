package com.xbk.lattice.query.service;

/**
 * 审查网关
 *
 * 职责：抽象 ReviewerAgent 的原始审查输出来源
 *
 * @author xiexu
 */
public interface ReviewerGateway {

    /**
     * 执行单轮审查。
     *
     * @param reviewPrompt 审查提示词
     * @return 原始审查输出
     */
    String review(String reviewPrompt);

    /**
     * 在指定作用域下执行单轮审查。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param reviewPrompt 审查提示词
     * @return 原始审查输出
     */
    default String review(String scopeId, String scene, String agentRole, String reviewPrompt) {
        return review(reviewPrompt);
    }

    /**
     * 返回当前审查调用的路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    default String currentRoute(String scopeId, String scene, String agentRole) {
        return "rule-based";
    }
}
