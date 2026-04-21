package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.observability.StructuredEventLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 查询门面服务
 *
 * 职责：负责查询参数规范化、图编排调用与 pending query 收口
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryFacadeService {

    private final QueryGraphOrchestrator queryGraphOrchestrator;

    private final PendingQueryManager pendingQueryManager;

    private final StructuredEventLogger structuredEventLogger;

    /**
     * 创建查询门面服务。
     *
     * @param queryGraphOrchestrator 问答图编排器
     * @param pendingQueryManager PendingQuery 管理器
     */
    @Autowired
    public QueryFacadeService(
            QueryGraphOrchestrator queryGraphOrchestrator,
            PendingQueryManager pendingQueryManager,
            StructuredEventLogger structuredEventLogger
    ) {
        this.queryGraphOrchestrator = queryGraphOrchestrator;
        this.pendingQueryManager = pendingQueryManager;
        this.structuredEventLogger = structuredEventLogger;
    }

    /**
     * 创建兼容单测的查询门面服务。
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
     * @param pendingQueryManager PendingQuery 管理器
     */
    public QueryFacadeService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            VectorSearchService vectorSearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            PendingQueryManager pendingQueryManager
    ) {
        this.queryGraphOrchestrator = new QueryGraphOrchestrator(
                ftsSearchService,
                refKeySearchService,
                sourceSearchService,
                contributionSearchService,
                vectorSearchService,
                rrfFusionService,
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                new QueryReviewProperties()
        );
        this.pendingQueryManager = pendingQueryManager;
        this.structuredEventLogger = null;
    }

    /**
     * 创建兼容单测的查询门面服务。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService 引用词检索服务
     * @param rrfFusionService RRF 融合服务
     * @param answerGenerationService 答案生成服务
     * @param queryCacheStore 查询缓存存储
     * @param reviewerAgent 审查代理
     * @param pendingQueryManager PendingQuery 管理器
     */
    public QueryFacadeService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            PendingQueryManager pendingQueryManager
    ) {
        this(
                ftsSearchService,
                refKeySearchService,
                new SourceSearchService(null),
                new ContributionSearchService(null),
                new VectorSearchService(),
                rrfFusionService,
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                pendingQueryManager
        );
    }

    /**
     * 执行最小知识查询。
     *
     * @param question 查询问题
     * @return 查询响应
     */
    public QueryResponse query(String question) {
        String queryId = UUID.randomUUID().toString();
        logQueryReceived(queryId, question);
        try {
            QueryResponse baseResponse = queryGraphOrchestrator.execute(question, queryId);
            QueryResponse finalResponse = shouldAttachPendingQuery(baseResponse)
                    ? attachPendingQuery(question, baseResponse)
                    : baseResponse;
            logQueryCompleted(finalResponse, "SUCCEEDED", null);
            return finalResponse;
        }
        catch (RuntimeException exception) {
            logQueryCompleted(new QueryResponse(null, List.of(), List.of(), queryId, null), "FAILED", exception);
            throw exception;
        }
    }

    /**
     * 为查询结果附加新的 pending query。
     *
     * @param question 问题
     * @param baseResponse 基础查询响应
     * @return 带 queryId 的查询响应
     */
    private QueryResponse attachPendingQuery(String question, QueryResponse baseResponse) {
        String queryId = pendingQueryManager.createPendingQuery(question, baseResponse).getQueryId();
        return new QueryResponse(
                baseResponse.getAnswer(),
                baseResponse.getSources(),
                baseResponse.getArticles(),
                queryId,
                baseResponse.getReviewStatus()
        );
    }

    /**
     * 判断当前响应是否需要创建 pending query。
     *
     * @param queryResponse 查询响应
     * @return 是否需要创建 pending query
     */
    private boolean shouldAttachPendingQuery(QueryResponse queryResponse) {
        return !(queryResponse.getSources().isEmpty() && queryResponse.getArticles().isEmpty());
    }

    private void logQueryReceived(String queryId, String question) {
        if (structuredEventLogger == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("scene", ExecutionLlmSnapshotService.QUERY_SCENE);
        fields.put("status", "RECEIVED");
        fields.put("queryId", queryId);
        fields.put("scopeType", ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE);
        fields.put("scopeId", queryId);
        fields.put("questionLength", question == null ? 0 : question.length());
        structuredEventLogger.info("query_received", fields);
    }

    private void logQueryCompleted(QueryResponse queryResponse, String status, Throwable throwable) {
        if (structuredEventLogger == null || queryResponse == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("scene", ExecutionLlmSnapshotService.QUERY_SCENE);
        fields.put("status", status);
        fields.put("queryId", queryResponse.getQueryId());
        fields.put("scopeType", ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE);
        fields.put("scopeId", queryResponse.getQueryId());
        fields.put("reviewStatus", queryResponse.getReviewStatus());
        fields.put("sourceCount", queryResponse.getSources() == null ? 0 : queryResponse.getSources().size());
        fields.put("articleCount", queryResponse.getArticles() == null ? 0 : queryResponse.getArticles().size());
        if (throwable != null) {
            fields.put("error", throwable.getMessage());
            structuredEventLogger.error("query_completed", fields, throwable);
            return;
        }
        structuredEventLogger.info("query_completed", fields);
    }
}
