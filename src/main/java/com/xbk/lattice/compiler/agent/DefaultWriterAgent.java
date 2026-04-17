package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.compiler.service.CompileArticleNode;
import com.xbk.lattice.infra.persistence.ArticleRecord;

/**
 * 默认 WriterAgent
 *
 * 职责：复用现有草稿编译能力生成文章草稿，并回填角色路由元信息
 *
 * @author xiexu
 */
public class DefaultWriterAgent implements WriterAgent {

    private static final String AGENT_ROLE = "WriterAgent";

    private static final String ROUTE_ROLE = "writer";

    private final CompileArticleNode compileArticleNode;

    private final AgentModelRouter agentModelRouter;

    /**
     * 创建默认 WriterAgent。
     *
     * @param compileArticleNode 草稿编译节点
     * @param agentModelRouter Agent 模型路由器
     */
    public DefaultWriterAgent(CompileArticleNode compileArticleNode, AgentModelRouter agentModelRouter) {
        this.compileArticleNode = compileArticleNode;
        this.agentModelRouter = agentModelRouter;
    }

    /**
     * 执行文章草稿生成。
     *
     * @param writerTask Writer 输入任务
     * @return Writer 输出结果
     */
    @Override
    public WriterResult write(WriterTask writerTask) {
        ArticleRecord articleRecord = null;
        if (compileArticleNode != null) {
            articleRecord = compileArticleNode.compileDraft(
                    writerTask.getMergedConcept(),
                    writerTask.getSourceDir(),
                    writerTask.getScopeId(),
                    writerTask.getScene()
            );
        }
        return new WriterResult(articleRecord, AGENT_ROLE, resolveRoute(writerTask));
    }

    /**
     * 返回当前路由标签。
     *
     * @return 路由标签
     */
    private String resolveRoute(WriterTask writerTask) {
        if (agentModelRouter == null) {
            return "fallback";
        }
        return agentModelRouter.routeFor(writerTask.getScopeId(), writerTask.getScene(), ROUTE_ROLE);
    }
}
