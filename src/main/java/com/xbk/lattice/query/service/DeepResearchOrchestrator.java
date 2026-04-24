package com.xbk.lattice.query.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.api.query.DeepResearchSummary;
import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryRequest;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchGraphDefinitionFactory;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchState;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchStateMapper;
import com.xbk.lattice.query.deepresearch.service.DeepResearchAuditPersistenceService;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionRegistry;
import com.xbk.lattice.query.deepresearch.service.DeepResearchPlanner;
import com.xbk.lattice.query.deepresearch.service.DeepResearchRouter;
import com.xbk.lattice.query.deepresearch.store.DeepResearchWorkingSetStore;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.graph.QueryWorkingSetStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deep Research 编排器
 *
 * 职责：负责深度研究的路由、图执行、最终响应收口与审计落盘
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DeepResearchOrchestrator {

    private static final int DEFAULT_MAX_LLM_CALLS = 6;

    private static final int DEFAULT_OVERALL_TIMEOUT_MS = 30_000;

    private final DeepResearchRouter deepResearchRouter;

    private final DeepResearchPlanner deepResearchPlanner;

    private final DeepResearchGraphDefinitionFactory deepResearchGraphDefinitionFactory;

    private final DeepResearchStateMapper deepResearchStateMapper;

    private final DeepResearchWorkingSetStore deepResearchWorkingSetStore;

    private final DeepResearchExecutionRegistry deepResearchExecutionRegistry;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService;

    private final DeepResearchAuditPersistenceService deepResearchAuditPersistenceService;

    private final KnowledgeSearchService knowledgeSearchService;

    /**
     * 创建 Deep Research 编排器。
     *
     * @param deepResearchRouter 路由器
     * @param deepResearchPlanner 规划器
     * @param deepResearchGraphDefinitionFactory 动态图工厂
     * @param deepResearchStateMapper 状态映射器
     * @param deepResearchWorkingSetStore 工作集存储
     * @param deepResearchExecutionRegistry 执行上下文注册表
     * @param queryWorkingSetStore Query 工作集存储
     * @param queryAnswerAuditPersistenceService Query 答案审计服务
     * @param deepResearchAuditPersistenceService Deep Research 审计服务
     * @param knowledgeSearchService 知识检索服务
     */
    public DeepResearchOrchestrator(
            DeepResearchRouter deepResearchRouter,
            DeepResearchPlanner deepResearchPlanner,
            DeepResearchGraphDefinitionFactory deepResearchGraphDefinitionFactory,
            DeepResearchStateMapper deepResearchStateMapper,
            DeepResearchWorkingSetStore deepResearchWorkingSetStore,
            DeepResearchExecutionRegistry deepResearchExecutionRegistry,
            QueryWorkingSetStore queryWorkingSetStore,
            QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService,
            DeepResearchAuditPersistenceService deepResearchAuditPersistenceService,
            KnowledgeSearchService knowledgeSearchService
    ) {
        this.deepResearchRouter = deepResearchRouter;
        this.deepResearchPlanner = deepResearchPlanner;
        this.deepResearchGraphDefinitionFactory = deepResearchGraphDefinitionFactory;
        this.deepResearchStateMapper = deepResearchStateMapper;
        this.deepResearchWorkingSetStore = deepResearchWorkingSetStore;
        this.deepResearchExecutionRegistry = deepResearchExecutionRegistry;
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.queryAnswerAuditPersistenceService = queryAnswerAuditPersistenceService;
        this.deepResearchAuditPersistenceService = deepResearchAuditPersistenceService;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    /**
     * 执行一次 Deep Research。
     *
     * @param queryRequest 查询请求
     * @param queryId 查询标识
     * @return 查询响应
     */
    public QueryResponse execute(QueryRequest queryRequest, String queryId) {
        String question = queryRequest == null ? null : queryRequest.getQuestion();
        if (!deepResearchRouter.shouldRoute(queryRequest)) {
            throw new IllegalArgumentException("当前请求未命中 Deep Research 路由");
        }
        int maxLlmCalls = queryRequest != null && queryRequest.getMaxLlmCalls() != null
                ? queryRequest.getMaxLlmCalls().intValue()
                : DEFAULT_MAX_LLM_CALLS;
        int overallTimeoutMs = queryRequest != null && queryRequest.getOverallTimeoutMs() != null
                ? queryRequest.getOverallTimeoutMs().intValue()
                : DEFAULT_OVERALL_TIMEOUT_MS;
        DeepResearchExecutionContext executionContext = deepResearchExecutionRegistry.register(
                queryId,
                maxLlmCalls,
                overallTimeoutMs
        );
        try {
            LayeredResearchPlan plan = deepResearchPlanner.plan(question);
            DeepResearchState initialState = new DeepResearchState();
            initialState.setQueryId(queryId);
            initialState.setQuestion(question);
            initialState.setLlmScopeType(ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE);
            initialState.setLlmScopeId(queryId);
            initialState.setRouteReason(deepResearchRouter.routeReason(queryRequest));
            initialState.setPlanRef(deepResearchWorkingSetStore.savePlan(queryId, plan));
            initialState.setLlmCallBudgetRemaining(maxLlmCalls);

            CompiledGraph compiledGraph = deepResearchGraphDefinitionFactory.build(plan).compile();
            Optional<OverAllState> result = compiledGraph.invoke(deepResearchStateMapper.toMap(initialState));
            DeepResearchState finalState = deepResearchStateMapper.fromMap(
                    result.orElseThrow(() -> new IllegalStateException("deep research graph returned empty state")).data()
            );
            String answerMarkdown = queryWorkingSetStore.loadAnswer(finalState.getDraftAnswerRef());
            CitationCheckReport citationCheckReport = queryWorkingSetStore.loadCitationCheckReport(finalState.getCitationCheckReportRef());
            QueryAnswerAuditSnapshot answerAuditSnapshot = queryAnswerAuditPersistenceService.persist(
                    queryId,
                    1,
                    question,
                    answerMarkdown,
                    AnswerOutcome.SUCCESS,
                    GenerationMode.LLM,
                    null,
                    false,
                    "deep_research",
                    citationCheckReport
            );
            EvidenceLedger evidenceLedger = deepResearchWorkingSetStore.loadEvidenceLedger(queryId + ":evidence-ledger");
            DeepResearchAuditSnapshot deepResearchAuditSnapshot = deepResearchAuditPersistenceService.persist(
                    queryId,
                    question,
                    finalState.getRouteReason(),
                    plan,
                    evidenceLedger,
                    executionContext.llmCallCount(),
                    citationCheckReport == null ? 0.0D : citationCheckReport.getCoverageRate(),
                    finalState.isPartialAnswer(),
                    finalState.isHasConflicts(),
                    answerAuditSnapshot == null ? null : answerAuditSnapshot.getAuditId()
            );
            finalState.setFinalResponseRef(
                    deepResearchWorkingSetStore.saveDeepResearchAudit(queryId, deepResearchAuditSnapshot)
            );
            List<QueryArticleHit> rootHits = knowledgeSearchService.search(question, 8);
            return new QueryResponse(
                    answerMarkdown,
                    toSourceResponses(rootHits),
                    toArticleResponses(rootHits),
                    queryId,
                    null,
                    finalState.isPartialAnswer() ? AnswerOutcome.PARTIAL_ANSWER : AnswerOutcome.SUCCESS,
                    GenerationMode.LLM,
                    ModelExecutionStatus.SUCCESS,
                    citationCheckReport == null ? null : citationCheckReport.toSummary(),
                    new DeepResearchSummary(
                            true,
                            plan.layerCount(),
                            plan.taskCount(),
                            finalState.getEvidenceCardCount(),
                            executionContext.llmCallCount(),
                            citationCheckReport == null ? 0.0D : citationCheckReport.getCoverageRate(),
                            finalState.isPartialAnswer(),
                            finalState.isHasConflicts()
                        )
                );
        }
        catch (Exception exception) {
            return new QueryResponse(
                    "Deep Research 执行中断，当前仅能返回部分结果。",
                    List.of(),
                    List.of(),
                    queryId,
                    null,
                    AnswerOutcome.PARTIAL_ANSWER,
                    GenerationMode.FALLBACK,
                    ModelExecutionStatus.FAILED,
                    null,
                    new DeepResearchSummary(true, 0, 0, 0, executionContext.llmCallCount(), 0.0D, true, false)
            );
        }
        finally {
            deepResearchExecutionRegistry.remove(queryId);
            deepResearchWorkingSetStore.deleteByQueryId(queryId);
            queryWorkingSetStore.deleteByQueryId(queryId);
        }
    }

    private List<QuerySourceResponse> toSourceResponses(List<QueryArticleHit> rootHits) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        for (QueryArticleHit rootHit : rootHits) {
            sourceResponses.add(new QuerySourceResponse(
                    rootHit.getSourceId(),
                    rootHit.getArticleKey(),
                    rootHit.getConceptId(),
                    rootHit.getTitle(),
                    rootHit.getSourcePaths()
            ));
        }
        return sourceResponses;
    }

    private List<QueryArticleResponse> toArticleResponses(List<QueryArticleHit> rootHits) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        for (QueryArticleHit rootHit : rootHits) {
            articleResponses.add(new QueryArticleResponse(
                    rootHit.getSourceId(),
                    rootHit.getArticleKey(),
                    rootHit.getConceptId(),
                    rootHit.getTitle()
            ));
        }
        return articleResponses;
    }
}
