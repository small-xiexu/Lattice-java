package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.agent.AgentModelRouter;
import com.xbk.lattice.compiler.agent.DefaultFixerAgent;
import com.xbk.lattice.compiler.agent.DefaultReviewerAgent;
import com.xbk.lattice.compiler.agent.DefaultWriterAgent;
import com.xbk.lattice.compiler.agent.FixTask;
import com.xbk.lattice.compiler.agent.FixerAgent;
import com.xbk.lattice.compiler.agent.FixerResult;
import com.xbk.lattice.compiler.agent.ReviewTask;
import com.xbk.lattice.compiler.agent.ReviewerAgent;
import com.xbk.lattice.compiler.agent.ReviewerResult;
import com.xbk.lattice.compiler.agent.WriterAgent;
import com.xbk.lattice.compiler.agent.WriterResult;
import com.xbk.lattice.compiler.agent.WriterTask;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.node.CompileArticleNode;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.domain.ReviewResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 编译文章认知支撑服务
 *
 * 职责：承载编译侧 Writer/Reviewer/Fixer Agent 的上下文装配与执行
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ArticleCompileSupport {

    private final CompileArticleNode compileArticleNode;

    private final AgentModelRouter agentModelRouter;

    private final WriterAgent writerAgent;

    private final ReviewerAgent reviewerAgent;

    private final FixerAgent fixerAgent;

    /**
     * 创建编译文章认知支撑服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param sourceFileJdbcRepository 源文件仓储
     */
    @Autowired
    public ArticleCompileSupport(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            LlmProperties llmProperties,
            ExecutionLlmSnapshotService executionLlmSnapshotService
    ) {
        this(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                sourceFileJdbcRepository,
                new AgentModelRouter(executionLlmSnapshotService, llmProperties)
        );
    }

    /**
     * 创建编译文章认知支撑服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public ArticleCompileSupport(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                sourceFileJdbcRepository,
                new AgentModelRouter(llmGateway)
        );
    }

    private ArticleCompileSupport(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            AgentModelRouter agentModelRouter
    ) {
        this.compileArticleNode = new CompileArticleNode(
                llmGateway,
                sourceFileJdbcRepository,
                new DocumentSectionSelector(),
                articleReviewerGateway,
                reviewFixService,
                new SchemaAwarePrompts(compilerProperties)
        );
        this.agentModelRouter = agentModelRouter;
        this.writerAgent = new DefaultWriterAgent(this.compileArticleNode, this.agentModelRouter);
        this.reviewerAgent = new DefaultReviewerAgent(articleReviewerGateway, this.agentModelRouter);
        this.fixerAgent = new DefaultFixerAgent(reviewFixService, this.agentModelRouter);
    }

    /**
     * 编译新文章草稿。
     *
     * @param mergedConcepts 合并概念
     * @param sourceDir 源目录
     * @return 草稿文章集合
     */
    public List<ArticleRecord> compileDraftArticles(List<MergedConcept> mergedConcepts, Path sourceDir) {
        return compileDraftArticles(mergedConcepts, sourceDir, null, null, null, null);
    }

    /**
     * 编译新文章草稿。
     *
     * @param mergedConcepts 合并概念
     * @param sourceDir 源目录
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 草稿文章集合
     */
    public List<ArticleRecord> compileDraftArticles(
            List<MergedConcept> mergedConcepts,
            Path sourceDir,
            String scopeId,
            String scene
    ) {
        return compileDraftArticles(mergedConcepts, sourceDir, null, null, scopeId, scene);
    }

    /**
     * 编译新文章草稿。
     *
     * @param mergedConcepts 合并概念
     * @param sourceDir 源目录
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 草稿文章集合
     */
    public List<ArticleRecord> compileDraftArticles(
            List<MergedConcept> mergedConcepts,
            Path sourceDir,
            Long sourceId,
            String sourceCode,
            String scopeId,
            String scene
    ) {
        List<ArticleRecord> draftArticles = new ArrayList<ArticleRecord>();
        for (MergedConcept mergedConcept : mergedConcepts) {
            WriterResult writerResult = writerAgent.write(new WriterTask(
                    mergedConcept,
                    sourceDir,
                    sourceId,
                    sourceCode,
                    scopeId,
                    scene
            ));
            ArticleRecord writerArticleRecord = writerResult.getArticleRecord();
            if (writerArticleRecord != null) {
                draftArticles.add(writerArticleRecord);
            }
        }
        return draftArticles;
    }

    /**
     * 审查草稿文章。
     *
     * @param draftArticles 草稿文章集合
     * @return 审查结果集合
     */
    public List<ArticleReviewEnvelope> reviewDraftArticles(List<ArticleRecord> draftArticles) {
        return reviewDraftArticles(draftArticles, null, null);
    }

    /**
     * 审查草稿文章。
     *
     * @param draftArticles 草稿文章集合
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 审查结果集合
     */
    public List<ArticleReviewEnvelope> reviewDraftArticles(
            List<ArticleRecord> draftArticles,
            String scopeId,
            String scene
    ) {
        List<ArticleReviewEnvelope> reviewedArticles = new ArrayList<ArticleReviewEnvelope>();
        for (ArticleRecord draftArticle : draftArticles) {
            String sourceContents = compileArticleNode.buildSourceContents(
                    draftArticle.getSourcePaths(),
                    draftArticle.getSourceId()
            );
            ReviewerResult reviewerResult = reviewerAgent.review(new ReviewTask(
                    draftArticle,
                    sourceContents,
                    scopeId,
                    scene
            ));
            ReviewResult reviewResult = reviewerResult.getReviewResult();
            ArticleReviewEnvelope reviewEnvelope = new ArticleReviewEnvelope();
            reviewEnvelope.setArticle(draftArticle);
            reviewEnvelope.setReviewResult(reviewResult);
            reviewEnvelope.setReviewAttemptCount(1);
            reviewEnvelope.setFixAttemptCount(0);
            reviewEnvelope.setFixed(false);
            reviewEnvelope.setReviewerRoute(reviewerResult.getModelRoute());
            reviewEnvelope.setReviewStatus(reviewResult.isPass() ? "passed" : "pending");
            if (reviewResult.isPass()) {
                reviewEnvelope.setArticle(compileArticleNode.replaceReviewStatus(
                        draftArticle,
                        "passed",
                        draftArticle.getContent()
                ));
            }
            reviewedArticles.add(reviewEnvelope);
        }
        return reviewedArticles;
    }

    /**
     * 对审查失败文章执行修复。
     *
     * @param reviewedArticles 审查后文章集合
     * @return 修复后的文章集合
     */
    public List<ArticleReviewEnvelope> fixReviewedArticles(List<ArticleReviewEnvelope> reviewedArticles) {
        return fixReviewedArticles(reviewedArticles, null, null);
    }

    /**
     * 对审查失败文章执行修复。
     *
     * @param reviewedArticles 审查后文章集合
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 修复后的文章集合
     */
    public List<ArticleReviewEnvelope> fixReviewedArticles(
            List<ArticleReviewEnvelope> reviewedArticles,
            String scopeId,
            String scene
    ) {
        List<ArticleReviewEnvelope> fixedArticles = new ArrayList<ArticleReviewEnvelope>();
        for (ArticleReviewEnvelope reviewEnvelope : reviewedArticles) {
            if (reviewEnvelope.getReviewResult() == null || reviewEnvelope.getReviewResult().isPass()) {
                fixedArticles.add(reviewEnvelope);
                continue;
            }
            String sourceContents = compileArticleNode.buildSourceContents(
                    reviewEnvelope.getArticle().getSourcePaths(),
                    reviewEnvelope.getArticle().getSourceId()
            );
            FixerResult fixerResult = fixerAgent.fix(new FixTask(
                    reviewEnvelope.getArticle(),
                    reviewEnvelope.getReviewResult().getIssues(),
                    sourceContents,
                    scopeId,
                    scene
            ));
            reviewEnvelope.setFixerRoute(fixerResult.getModelRoute());
            if (!fixerResult.isFixed()) {
                fixedArticles.add(reviewEnvelope);
                continue;
            }
            reviewEnvelope.setArticle(compileArticleNode.replaceReviewStatus(
                    reviewEnvelope.getArticle(),
                    "pending",
                    fixerResult.getFixedContent()
            ));
            reviewEnvelope.setFixed(true);
            reviewEnvelope.setFixAttemptCount(reviewEnvelope.getFixAttemptCount() + 1);
            fixedArticles.add(reviewEnvelope);
        }
        return fixedArticles;
    }

    /**
     * 汇总文章最终落库形态。
     *
     * @param reviewEnvelope 审查包裹对象
     * @return 最终文章记录
     */
    public ArticleRecord finalizeArticleForPersist(ArticleReviewEnvelope reviewEnvelope) {
        String reviewStatus = "passed";
        if (reviewEnvelope.getReviewResult() == null || !reviewEnvelope.getReviewResult().isPass()) {
            reviewStatus = "needs_human_review";
        }
        return compileArticleNode.replaceReviewStatus(
                reviewEnvelope.getArticle(),
                reviewStatus,
                reviewEnvelope.getArticle().getContent()
        );
    }

    /**
     * 返回编译角色当前路由。
     *
     * @return 编译角色路由
     */
    public String currentCompileRoute() {
        return currentCompileRoute(null, ExecutionLlmSnapshotService.COMPILE_SCENE);
    }

    /**
     * 返回编译角色当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 编译角色路由
     */
    public String currentCompileRoute(String scopeId, String scene) {
        return agentModelRouter.routeForWriterAgent(scopeId, scene);
    }

    /**
     * 返回审查角色当前路由。
     *
     * @return 审查角色路由
     */
    public String currentReviewRoute() {
        return currentReviewRoute(null, ExecutionLlmSnapshotService.COMPILE_SCENE);
    }

    /**
     * 返回审查角色当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 审查角色路由
     */
    public String currentReviewRoute(String scopeId, String scene) {
        return agentModelRouter.routeForReviewerAgent(scopeId, scene);
    }

    /**
     * 返回修复角色当前路由。
     *
     * @return 修复角色路由
     */
    public String currentFixRoute() {
        return currentFixRoute(null, ExecutionLlmSnapshotService.COMPILE_SCENE);
    }

    /**
     * 返回修复角色当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 修复角色路由
     */
    public String currentFixRoute(String scopeId, String scene) {
        return agentModelRouter.routeForFixerAgent(scopeId, scene);
    }
}
