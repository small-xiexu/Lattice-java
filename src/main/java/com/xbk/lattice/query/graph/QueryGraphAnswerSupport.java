package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.AnswerShapeClassifier;
import com.xbk.lattice.query.service.ArticleChunkFtsSearchService;
import com.xbk.lattice.query.service.ChunkVectorSearchService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FactCardFtsSearchService;
import com.xbk.lattice.query.service.FactCardTerminalUnitFtsSearchService;
import com.xbk.lattice.query.service.FactCardVectorSearchService;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.GraphSearchService;
import com.xbk.lattice.query.service.QueryIntent;
import com.xbk.lattice.query.service.QueryIntentClassifier;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.QueryEvidenceRelevanceSupport;
import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import com.xbk.lattice.query.service.QuerySearchProperties;
import com.xbk.lattice.query.service.QueryRewriteResult;
import com.xbk.lattice.query.service.QueryRewriteService;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.RetrievalAuditService;
import com.xbk.lattice.query.service.RetrievalChannelRun;
import com.xbk.lattice.query.service.RetrievalDispatchPlan;
import com.xbk.lattice.query.service.RetrievalDispatchResult;
import com.xbk.lattice.query.service.RetrievalDispatcher;
import com.xbk.lattice.query.service.RetrievalExecutionContext;
import com.xbk.lattice.query.service.RetrievalQueryContext;
import com.xbk.lattice.query.service.RetrievalStrategy;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.RrfFusionService;
import com.xbk.lattice.query.service.SourceChunkFtsSearchService;
import com.xbk.lattice.query.service.SourceSearchService;
import com.xbk.lattice.query.service.SupplierRetrievalChannel;
import com.xbk.lattice.query.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Query Graph 回答节点支持。
 *
 * 职责：执行问题标准化、意图分类、答案生成、审查与重写节点。
 *
 * @author xiexu
 */
abstract class QueryGraphAnswerSupport extends QueryGraphRetrievalSupport {

    /**
     * 创建 Query Graph 回答节点支持。
     */
    protected QueryGraphAnswerSupport(
            FtsSearchService ftsSearchService,
            ArticleChunkFtsSearchService articleChunkFtsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            SourceChunkFtsSearchService sourceChunkFtsSearchService,
            FactCardFtsSearchService factCardFtsSearchService,
            FactCardVectorSearchService factCardVectorSearchService,
            FactCardTerminalUnitFtsSearchService factCardTerminalUnitFtsSearchService,
            ContributionSearchService contributionSearchService,
            GraphSearchService graphSearchService,
            VectorSearchService vectorSearchService,
            ChunkVectorSearchService chunkVectorSearchService,
            RrfFusionService rrfFusionService,
            QueryRetrievalSettingsService queryRetrievalSettingsService,
            QuerySearchProperties querySearchProperties,
            QueryRewriteService queryRewriteService,
            QueryIntentClassifier queryIntentClassifier,
            AnswerShapeClassifier answerShapeClassifier,
            RetrievalStrategyResolver retrievalStrategyResolver,
            RetrievalAuditService retrievalAuditService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            QueryWorkingSetStore queryWorkingSetStore,
            CitationCheckService citationCheckService,
            QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService,
            QueryGraphStateMapper queryGraphStateMapper,
            QueryGraphConditions queryGraphConditions,
            QueryAnswerProjectionBuilder queryAnswerProjectionBuilder
    ) {
        super(
                ftsSearchService,
                articleChunkFtsSearchService,
                refKeySearchService,
                sourceSearchService,
                sourceChunkFtsSearchService,
                factCardFtsSearchService,
                factCardVectorSearchService,
                factCardTerminalUnitFtsSearchService,
                contributionSearchService,
                graphSearchService,
                vectorSearchService,
                chunkVectorSearchService,
                rrfFusionService,
                queryRetrievalSettingsService,
                querySearchProperties,
                queryRewriteService,
                queryIntentClassifier,
                answerShapeClassifier,
                retrievalStrategyResolver,
                retrievalAuditService,
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                queryWorkingSetStore,
                citationCheckService,
                queryAnswerAuditPersistenceService,
                queryGraphStateMapper,
                queryGraphConditions,
                queryAnswerProjectionBuilder
        );
    }

    protected Map<String, Object> normalizeQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        String question = state.getQuestion() == null ? "" : state.getQuestion();
        state.setNormalizedQuestion(question.trim());
        if (state.getLlmScopeType() == null || state.getLlmScopeType().isBlank()) {
            state.setLlmScopeType(ExecutionLlmSnapshotService.QUERY_SCOPE_TYPE);
        }
        if (state.getLlmScopeId() == null || state.getLlmScopeId().isBlank()) {
            state.setLlmScopeId(state.getQueryId());
        }
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> rewriteQuery(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryRetrievalSettingsState retrievalSettings = retrievalSettings();
        QueryRewriteResult rewriteResult = retrievalSettings.isRewriteEnabled()
                ? queryRewriteService.rewrite(state.getQueryId(), state.getNormalizedQuestion())
                : QueryRewriteResult.unchanged(state.getNormalizedQuestion());
        state.setRewrittenQuestion(rewriteResult.getRewrittenQuestion());
        state.setRewriteAuditRef(rewriteResult.getAuditRef());
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> classifyIntent(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        String retrievalQuestion = effectiveRetrievalQuestion(state);
        QueryIntent queryIntent = queryIntentClassifier.classify(retrievalQuestion);
        state.setQueryIntent(queryIntent.name());
        state.setAnswerShape(answerShapeClassifier.classify(retrievalQuestion).name());
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> resolveRetrievalStrategy(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryIntent queryIntent = readQueryIntent(state.getQueryIntent());
        RetrievalStrategy retrievalStrategy = retrievalStrategyResolver.resolve(
                effectiveRetrievalQuestion(state),
                queryIntent,
                readAnswerShape(state.getAnswerShape()),
                retrievalSettings()
        );
        state.setRetrievalStrategyRef(queryWorkingSetStore.saveRetrievalStrategy(
                state.getQueryId(),
                retrievalStrategy
        ));
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> checkCache(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryResponse cachedResponse = queryCacheStore.get(state.getNormalizedQuestion()).orElse(null);
        if (cachedResponse != null) {
            QueryResponse responseForCurrentQuery = withQueryId(cachedResponse, state.getQueryId());
            state.setCacheHit(true);
            state.setCachedResponseRef(queryWorkingSetStore.saveResponse(state.getQueryId(), responseForCurrentQuery));
            state.setReviewStatus(responseForCurrentQuery.getReviewStatus());
            state.setAnswerOutcome(enumName(responseForCurrentQuery.getAnswerOutcome()));
            state.setGenerationMode(enumName(responseForCurrentQuery.getGenerationMode()));
            state.setModelExecutionStatus(enumName(responseForCurrentQuery.getModelExecutionStatus()));
            state.setAnswerCacheable(isCacheableOutcome(responseForCurrentQuery.getAnswerOutcome()));
        }
        else {
            state.setCacheHit(false);
        }
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> answerQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        state.setAnswerRoute(answerGenerationService.currentRoute(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER
        ));
        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                state.getQuestion(),
                fusedHits
        );
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), answerPayload.getAnswerMarkdown()));
        state.setAnswerOutcome(answerPayload.getAnswerOutcome().name());
        state.setGenerationMode(answerPayload.getGenerationMode().name());
        state.setModelExecutionStatus(answerPayload.getModelExecutionStatus().name());
        state.setFallbackReason(answerPayload.getFallbackReason());
        state.setAnswerCacheable(answerPayload.isAnswerCacheable());
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> reviewAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        state.setReviewRoute(reviewerAgent.currentRoute(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REVIEWER
        ));
        ReviewResult reviewResult = reviewerAgent.review(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REVIEWER,
                state.getQuestion(),
                answer,
                readAnswerOutcome(state.getAnswerOutcome()),
                collectSourcePaths(fusedHits)
        );
        state.setReviewResultRef(queryWorkingSetStore.saveReviewResult(state.getQueryId(), reviewResult));
        state.setReviewStatus(reviewResult.getStatus().name());
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected Map<String, Object> rewriteAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String currentAnswer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        ReviewResult reviewResult = queryWorkingSetStore.loadReviewResult(state.getReviewResultRef());
        state.setRewriteRoute(answerGenerationService.currentRoute(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REWRITE
        ));
        QueryAnswerPayload rewrittenPayload = answerGenerationService.rewriteFromReviewPayload(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REWRITE,
                state.getQuestion(),
                currentAnswer,
                buildRewriteGuidance(reviewResult),
                fusedHits
        );
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), rewrittenPayload.getAnswerMarkdown()));
        state.setAnswerOutcome(rewrittenPayload.getAnswerOutcome().name());
        state.setGenerationMode(rewrittenPayload.getGenerationMode().name());
        state.setModelExecutionStatus(rewrittenPayload.getModelExecutionStatus().name());
        state.setFallbackReason(rewrittenPayload.getFallbackReason());
        state.setAnswerCacheable(rewrittenPayload.isAnswerCacheable());
        state.setRewriteAttemptCount(state.getRewriteAttemptCount() + 1);
        return queryGraphStateMapper.toDeltaMap(state);
    }
    protected String routeAfterReview(QueryGraphState state) {
        ReviewResult reviewResult = queryWorkingSetStore.loadReviewResult(state.getReviewResultRef());
        return queryGraphConditions.routeAfterReview(state, reviewResult);
    }
    protected String routeAfterCitationCheck(QueryGraphState state) {
        CitationCheckReport report = queryWorkingSetStore.loadCitationCheckReport(state.getCitationCheckReportRef());
        return queryGraphConditions.routeAfterCitationCheck(state, report);
    }
    protected QueryResponse withQueryId(QueryResponse queryResponse, String queryId) {
        return new QueryResponse(
                queryResponse.getAnswer(),
                queryResponse.getSources(),
                queryResponse.getArticles(),
                queryId,
                queryResponse.getReviewStatus(),
                queryResponse.getAnswerOutcome(),
                queryResponse.getGenerationMode(),
                queryResponse.getModelExecutionStatus(),
                queryResponse.getCitationCheck(),
                queryResponse.getDeepResearch(),
                queryResponse.getFallbackReason(),
                queryResponse.getCitationMarkers(),
                queryResponse.getStructuredEvidence()
        );
    }
    protected boolean isCacheableOutcome(AnswerOutcome answerOutcome) {
        return answerOutcome == AnswerOutcome.SUCCESS;
    }
    protected AnswerOutcome readAnswerOutcome(String answerOutcome) {
        if (answerOutcome == null || answerOutcome.isBlank()) {
            return null;
        }
        return AnswerOutcome.valueOf(answerOutcome);
    }
    protected String enumName(Enum<?> enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.name();
    }
    protected String buildRewriteGuidance(ReviewResult reviewResult) {
        if (reviewResult == null || reviewResult.getIssues().isEmpty()) {
            return "REVIEW_REWRITE_REQUIRED";
        }
        List<String> issueDescriptions = new ArrayList<String>();
        for (ReviewIssue reviewIssue : reviewResult.getIssues()) {
            issueDescriptions.add(reviewIssue.getCategory() + ":" + reviewIssue.getDescription());
        }
        return String.join("; ", issueDescriptions);
    }
}
