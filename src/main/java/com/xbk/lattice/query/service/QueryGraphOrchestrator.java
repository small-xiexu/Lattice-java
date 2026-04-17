package com.xbk.lattice.query.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.graph.InMemoryQueryWorkingSetStore;
import com.xbk.lattice.query.graph.QueryGraphConditions;
import com.xbk.lattice.query.graph.QueryGraphDefinitionFactory;
import com.xbk.lattice.query.graph.QueryGraphLifecycleListener;
import com.xbk.lattice.query.graph.QueryGraphState;
import com.xbk.lattice.query.graph.QueryGraphStateMapper;
import com.xbk.lattice.query.graph.QueryWorkingSetStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 问答图编排器
 *
 * 职责：基于 Spring AI Alibaba Graph 执行查询、审查与重写编排
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryGraphOrchestrator {

    private final QueryGraphDefinitionFactory queryGraphDefinitionFactory;

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final QueryGraphLifecycleListener queryGraphLifecycleListener;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final QueryReviewProperties queryReviewProperties;

    private volatile CompiledGraph compiledGraph;

    /**
     * 创建问答图编排器。
     *
     * @param queryGraphDefinitionFactory 问答图定义工厂
     * @param queryGraphStateMapper 问答图状态映射器
     * @param queryGraphLifecycleListener 问答图生命周期监听器
     * @param queryWorkingSetStore 问答图工作集存储
     * @param queryReviewProperties 查询审查配置
     */
    @Autowired
    public QueryGraphOrchestrator(
            QueryGraphDefinitionFactory queryGraphDefinitionFactory,
            QueryGraphStateMapper queryGraphStateMapper,
            QueryGraphLifecycleListener queryGraphLifecycleListener,
            QueryWorkingSetStore queryWorkingSetStore,
            QueryReviewProperties queryReviewProperties
    ) {
        this.queryGraphDefinitionFactory = queryGraphDefinitionFactory;
        this.queryGraphStateMapper = queryGraphStateMapper;
        this.queryGraphLifecycleListener = queryGraphLifecycleListener;
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.queryReviewProperties = queryReviewProperties;
    }

    /**
     * 创建兼容单测的问答图编排器。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService 引用词检索服务
     * @param sourceSearchService 源文件检索服务
     * @param contributionSearchService Contribution 检索服务
     * @param vectorSearchService 向量检索服务
     * @param rrfFusionService RRF 融合服务
     * @param answerGenerationService 答案生成服务
     * @param queryCacheStore 查询缓存存储
     * @param reviewerAgent 审查代理
     * @param queryReviewProperties 查询审查配置
     */
    public QueryGraphOrchestrator(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            VectorSearchService vectorSearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            QueryReviewProperties queryReviewProperties
    ) {
        QueryWorkingSetStore inMemoryQueryWorkingSetStore = new InMemoryQueryWorkingSetStore();
        QueryGraphStateMapper inMemoryStateMapper = new QueryGraphStateMapper();
        this.queryGraphDefinitionFactory = new QueryGraphDefinitionFactory(
                ftsSearchService,
                refKeySearchService,
                sourceSearchService,
                contributionSearchService,
                vectorSearchService,
                rrfFusionService,
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                inMemoryQueryWorkingSetStore,
                inMemoryStateMapper,
                new QueryGraphConditions(queryReviewProperties)
        );
        this.queryGraphStateMapper = inMemoryStateMapper;
        this.queryGraphLifecycleListener = new QueryGraphLifecycleListener(inMemoryStateMapper);
        this.queryWorkingSetStore = inMemoryQueryWorkingSetStore;
        this.queryReviewProperties = queryReviewProperties;
    }

    /**
     * 执行查询图。
     *
     * @param question 查询问题
     * @return 查询响应
     */
    public QueryResponse execute(String question) {
        String queryId = UUID.randomUUID().toString();
        try {
            QueryGraphState initialState = new QueryGraphState();
            initialState.setQueryId(queryId);
            initialState.setQuestion(question);
            initialState.setRewriteAttemptCount(0);
            initialState.setMaxRewriteRounds(queryReviewProperties.getMaxRewriteRounds());

            Optional<OverAllState> result = resolveCompiledGraph().invoke(queryGraphStateMapper.toMap(initialState));
            OverAllState overAllState = result.orElseThrow(() -> new IllegalStateException("query graph returned empty state"));
            QueryGraphState finalState = queryGraphStateMapper.fromMap(overAllState.data());
            QueryResponse queryResponse = queryWorkingSetStore.loadResponse(finalState.getFinalResponseRef());
            if (queryResponse == null) {
                throw new IllegalStateException("query graph did not produce final response");
            }
            return queryResponse;
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IllegalStateException("query graph execute failed", ex);
        }
        finally {
            queryWorkingSetStore.deleteByQueryId(queryId);
        }
    }

    /**
     * 懒加载编译后的图。
     *
     * @return 已编译图
     * @throws Exception 构建异常
     */
    private CompiledGraph resolveCompiledGraph() throws Exception {
        if (compiledGraph != null) {
            return compiledGraph;
        }
        synchronized (this) {
            if (compiledGraph == null) {
                compiledGraph = queryGraphDefinitionFactory.build().compile(
                        CompileConfig.builder()
                                .withLifecycleListener(queryGraphLifecycleListener)
                                .build()
                );
            }
            return compiledGraph;
        }
    }
}
