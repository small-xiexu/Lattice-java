package com.xbk.lattice.compiler.agent;

/**
 * FixerAgent 输出结果
 *
 * 职责：返回修复后的文章内容与执行元信息
 *
 * @author xiexu
 */
public class FixerResult {

    private final String fixedContent;

    private final boolean fixed;

    private final String agentRole;

    private final String modelRoute;

    /**
     * 创建 FixerAgent 输出结果。
     *
     * @param fixedContent 修复后内容
     * @param fixed 是否修复成功
     * @param agentRole Agent 角色
     * @param modelRoute 模型路由
     */
    public FixerResult(String fixedContent, boolean fixed, String agentRole, String modelRoute) {
        this.fixedContent = fixedContent;
        this.fixed = fixed;
        this.agentRole = agentRole;
        this.modelRoute = modelRoute;
    }

    /**
     * 返回修复后内容。
     *
     * @return 修复后内容
     */
    public String getFixedContent() {
        return fixedContent;
    }

    /**
     * 返回是否修复成功。
     *
     * @return 是否修复成功
     */
    public boolean isFixed() {
        return fixed;
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
