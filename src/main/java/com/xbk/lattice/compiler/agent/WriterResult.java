package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.infra.persistence.ArticleRecord;

/**
 * WriterAgent 输出结果
 *
 * 职责：返回草稿文章与执行元信息
 *
 * @author xiexu
 */
public class WriterResult {

    private final ArticleRecord articleRecord;

    private final String agentRole;

    private final String modelRoute;

    /**
     * 创建 WriterAgent 输出结果。
     *
     * @param articleRecord 草稿文章
     * @param agentRole Agent 角色
     * @param modelRoute 模型路由
     */
    public WriterResult(ArticleRecord articleRecord, String agentRole, String modelRoute) {
        this.articleRecord = articleRecord;
        this.agentRole = agentRole;
        this.modelRoute = modelRoute;
    }

    /**
     * 返回草稿文章。
     *
     * @return 草稿文章
     */
    public ArticleRecord getArticleRecord() {
        return articleRecord;
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
