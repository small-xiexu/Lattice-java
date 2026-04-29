package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryRequest;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.observability.StructuredEventLogger;
import com.xbk.lattice.query.deepresearch.service.DeepResearchRouter;
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

    private final DeepResearchOrchestrator deepResearchOrchestrator;

    private final DeepResearchRouter deepResearchRouter;

    private final OperationalQueryStatusService operationalQueryStatusService;

    private final PendingQueryManager pendingQueryManager;

    private final StructuredEventLogger structuredEventLogger;

    /**
     * 创建查询门面服务。
     *
     * @param queryGraphOrchestrator 问答图编排器
     * @param deepResearchOrchestrator Deep Research 编排器
     * @param deepResearchRouter Deep Research 路由器
     * @param operationalQueryStatusService 运行态问答状态服务
     * @param pendingQueryManager PendingQuery 管理器
     */
    @Autowired
    public QueryFacadeService(
            QueryGraphOrchestrator queryGraphOrchestrator,
            DeepResearchOrchestrator deepResearchOrchestrator,
            DeepResearchRouter deepResearchRouter,
            OperationalQueryStatusService operationalQueryStatusService,
            PendingQueryManager pendingQueryManager,
            StructuredEventLogger structuredEventLogger
    ) {
        this.queryGraphOrchestrator = queryGraphOrchestrator;
        this.deepResearchOrchestrator = deepResearchOrchestrator;
        this.deepResearchRouter = deepResearchRouter;
        this.operationalQueryStatusService = operationalQueryStatusService;
        this.pendingQueryManager = pendingQueryManager;
        this.structuredEventLogger = structuredEventLogger;
    }

    /**
     * 执行最小知识查询。
     *
     * @param question 查询问题
     * @return 查询响应
     */
    public QueryResponse query(String question) {
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion(question);
        return query(queryRequest);
    }

    /**
     * 执行最小知识查询。
     *
     * @param queryRequest 查询请求
     * @return 查询响应
     */
    public QueryResponse query(QueryRequest queryRequest) {
        String queryId = UUID.randomUUID().toString();
        String question = queryRequest == null ? null : queryRequest.getQuestion();
        logQueryReceived(queryId, question);
        try {
            QueryResponse baseResponse = routeAndExecute(queryRequest, queryId);
            QueryResponse finalResponse = shouldAttachPendingQuery(baseResponse)
                    ? attachPendingQuery(question, baseResponse)
                    : baseResponse;
            logQueryCompleted(finalResponse, "SUCCEEDED", null);
            return finalResponse;
        }
        catch (RuntimeException exception) {
            logQueryCompleted(new QueryResponse(null, List.of(), List.of(), queryId, null, null, null, null), "FAILED", exception);
            throw exception;
        }
    }

    /**
     * 按请求路由执行查询。
     *
     * @param queryRequest 查询请求
     * @param queryId 查询标识
     * @return 查询响应
     */
    private QueryResponse routeAndExecute(QueryRequest queryRequest, String queryId) {
        String question = queryRequest == null ? null : queryRequest.getQuestion();
        QueryResponse operationalQueryResponse = operationalQueryStatusService == null
                ? null
                : operationalQueryStatusService.resolve(question);
        if (operationalQueryResponse != null) {
            return operationalQueryResponse;
        }
        if (queryRequest != null
                && deepResearchOrchestrator != null
                && deepResearchRouter != null
                && deepResearchRouter.shouldRoute(queryRequest)) {
            return deepResearchOrchestrator.execute(queryRequest, queryId);
        }
        return queryGraphOrchestrator.execute(question, queryId);
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
                baseResponse.getReviewStatus(),
                baseResponse.getAnswerOutcome(),
                baseResponse.getGenerationMode(),
                baseResponse.getModelExecutionStatus(),
                baseResponse.getCitationCheck(),
                baseResponse.getDeepResearch()
        );
    }

    /**
     * 判断当前响应是否需要创建 pending query。
     *
     * @param queryResponse 查询响应
     * @return 是否需要创建 pending query
     */
    private boolean shouldAttachPendingQuery(QueryResponse queryResponse) {
        if (queryResponse.getSources().isEmpty() && queryResponse.getArticles().isEmpty()) {
            return false;
        }
        boolean runtimeSourcesOnly = queryResponse.getSources().stream().allMatch(source ->
                OperationalQueryStatusService.RUNTIME_STATUS_DERIVATION.equalsIgnoreCase(source.getDerivation())
        );
        boolean runtimeArticlesOnly = queryResponse.getArticles().stream().allMatch(article ->
                OperationalQueryStatusService.RUNTIME_STATUS_DERIVATION.equalsIgnoreCase(article.getDerivation())
        );
        if (runtimeSourcesOnly && runtimeArticlesOnly) {
            return false;
        }
        return true;
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
        fields.put("answerOutcome", queryResponse.getAnswerOutcome());
        fields.put("generationMode", queryResponse.getGenerationMode());
        fields.put("modelExecutionStatus", queryResponse.getModelExecutionStatus());
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
