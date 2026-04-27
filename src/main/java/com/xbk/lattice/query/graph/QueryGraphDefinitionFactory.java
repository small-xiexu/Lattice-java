package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncMultiCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.MultiCommand;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.ArticleChunkFtsSearchService;
import com.xbk.lattice.query.service.ChunkVectorSearchService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.GraphSearchService;
import com.xbk.lattice.query.service.QueryIntent;
import com.xbk.lattice.query.service.QueryIntentClassifier;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import com.xbk.lattice.query.service.QueryRewriteResult;
import com.xbk.lattice.query.service.QueryRewriteService;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.RetrievalAuditService;
import com.xbk.lattice.query.service.RetrievalStrategy;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.RrfFusionService;
import com.xbk.lattice.query.service.SourceChunkFtsSearchService;
import com.xbk.lattice.query.service.SourceSearchService;
import com.xbk.lattice.query.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 问答图定义工厂
 *
 * 职责：集中声明 Query Graph 的节点、顺序边与条件边
 *
 * @author xiexu
 */
@Slf4j
@Component
@Profile("jdbc")
public class QueryGraphDefinitionFactory {

    private static final int TOP_K = 8;

    private static final String CHANNEL_FTS = RetrievalStrategyResolver.CHANNEL_FTS;

    private static final String CHANNEL_ARTICLE_CHUNK_FTS = RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS;

    private static final String CHANNEL_REFKEY = RetrievalStrategyResolver.CHANNEL_REFKEY;

    private static final String CHANNEL_SOURCE = RetrievalStrategyResolver.CHANNEL_SOURCE;

    private static final String CHANNEL_SOURCE_CHUNK_FTS = RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS;

    private static final String CHANNEL_CONTRIBUTION = RetrievalStrategyResolver.CHANNEL_CONTRIBUTION;

    private static final String CHANNEL_GRAPH = RetrievalStrategyResolver.CHANNEL_GRAPH;

    private static final String CHANNEL_ARTICLE_VECTOR = RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR;

    private static final String CHANNEL_CHUNK_VECTOR = RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR;

    private final FtsSearchService ftsSearchService;

    private final ArticleChunkFtsSearchService articleChunkFtsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final SourceChunkFtsSearchService sourceChunkFtsSearchService;

    private final ContributionSearchService contributionSearchService;

    private final GraphSearchService graphSearchService;

    private final VectorSearchService vectorSearchService;

    private final ChunkVectorSearchService chunkVectorSearchService;

    private final RrfFusionService rrfFusionService;

    private final QueryRetrievalSettingsService queryRetrievalSettingsService;

    private final QueryRewriteService queryRewriteService;

    private final QueryIntentClassifier queryIntentClassifier;

    private final RetrievalStrategyResolver retrievalStrategyResolver;

    private final RetrievalAuditService retrievalAuditService;

    private final AnswerGenerationService answerGenerationService;

    private final QueryCacheStore queryCacheStore;

    private final ReviewerAgent reviewerAgent;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final QueryGraphConditions queryGraphConditions;

    private final QueryFinalizationGraphFragment queryFinalizationGraphFragment;

    /**
     * 创建问答图定义工厂。
     */
    public QueryGraphDefinitionFactory(
            FtsSearchService ftsSearchService,
            ArticleChunkFtsSearchService articleChunkFtsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            SourceChunkFtsSearchService sourceChunkFtsSearchService,
            ContributionSearchService contributionSearchService,
            GraphSearchService graphSearchService,
            VectorSearchService vectorSearchService,
            ChunkVectorSearchService chunkVectorSearchService,
            RrfFusionService rrfFusionService,
            QueryRetrievalSettingsService queryRetrievalSettingsService,
            QueryRewriteService queryRewriteService,
            QueryIntentClassifier queryIntentClassifier,
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
        this.ftsSearchService = ftsSearchService;
        this.articleChunkFtsSearchService = articleChunkFtsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.sourceChunkFtsSearchService = sourceChunkFtsSearchService;
        this.contributionSearchService = contributionSearchService;
        this.graphSearchService = graphSearchService;
        this.vectorSearchService = vectorSearchService;
        this.chunkVectorSearchService = chunkVectorSearchService;
        this.rrfFusionService = rrfFusionService;
        this.queryRetrievalSettingsService = queryRetrievalSettingsService;
        this.queryRewriteService = queryRewriteService == null ? new QueryRewriteService() : queryRewriteService;
        this.queryIntentClassifier = queryIntentClassifier == null ? new QueryIntentClassifier() : queryIntentClassifier;
        this.retrievalStrategyResolver = retrievalStrategyResolver == null
                ? new RetrievalStrategyResolver()
                : retrievalStrategyResolver;
        this.retrievalAuditService = retrievalAuditService;
        this.answerGenerationService = answerGenerationService;
        this.queryCacheStore = queryCacheStore;
        this.reviewerAgent = reviewerAgent;
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.queryGraphStateMapper = queryGraphStateMapper;
        this.queryGraphConditions = queryGraphConditions;
        this.queryFinalizationGraphFragment = new QueryFinalizationGraphFragment(
                queryWorkingSetStore,
                citationCheckService,
                queryAnswerAuditPersistenceService,
                queryCacheStore,
                queryGraphStateMapper,
                queryAnswerProjectionBuilder,
                answerGenerationService
        );
    }

    /**
     * 构建问答图定义。
     *
     * @return StateGraph 定义
     * @throws Exception 构建异常
     */
    public StateGraph build() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("normalize_question", AsyncNodeAction.node_async(this::normalizeQuestion));
        stateGraph.addNode("rewrite_query", AsyncNodeAction.node_async(this::rewriteQuery));
        stateGraph.addNode("classify_intent", AsyncNodeAction.node_async(this::classifyIntent));
        stateGraph.addNode("resolve_retrieval_strategy", AsyncNodeAction.node_async(this::resolveRetrievalStrategy));
        stateGraph.addNode("check_cache", AsyncNodeAction.node_async(this::checkCache));
        stateGraph.addNode(
                "dispatch_retrieval",
                AsyncMultiCommandAction.node_async(this::dispatchRetrieval),
                Map.of(
                        "retrieve_candidates_serial", "retrieve_candidates_serial",
                        "retrieve_fts", "retrieve_fts",
                        "retrieve_article_chunk_fts", "retrieve_article_chunk_fts",
                        "retrieve_refkey", "retrieve_refkey",
                        "retrieve_source", "retrieve_source",
                        "retrieve_source_chunk_fts", "retrieve_source_chunk_fts",
                        "retrieve_contribution", "retrieve_contribution",
                        "retrieve_graph", "retrieve_graph",
                        "retrieve_article_vector", "retrieve_article_vector",
                        "retrieve_chunk_vector", "retrieve_chunk_vector"
                )
        );
        stateGraph.addNode("retrieve_candidates_serial", AsyncNodeAction.node_async(this::retrieveCandidatesSerial));
        stateGraph.addNode("retrieve_fts", AsyncNodeAction.node_async(this::retrieveFts));
        stateGraph.addNode("retrieve_article_chunk_fts", AsyncNodeAction.node_async(this::retrieveArticleChunkFts));
        stateGraph.addNode("retrieve_refkey", AsyncNodeAction.node_async(this::retrieveRefkey));
        stateGraph.addNode("retrieve_source", AsyncNodeAction.node_async(this::retrieveSource));
        stateGraph.addNode("retrieve_source_chunk_fts", AsyncNodeAction.node_async(this::retrieveSourceChunkFts));
        stateGraph.addNode("retrieve_contribution", AsyncNodeAction.node_async(this::retrieveContribution));
        stateGraph.addNode("retrieve_graph", AsyncNodeAction.node_async(this::retrieveGraph));
        stateGraph.addNode("retrieve_article_vector", AsyncNodeAction.node_async(this::retrieveArticleVector));
        stateGraph.addNode("retrieve_chunk_vector", AsyncNodeAction.node_async(this::retrieveChunkVector));
        stateGraph.addNode("fuse_candidates", AsyncNodeAction.node_async(this::fuseCandidates));
        stateGraph.addNode("answer_question", AsyncNodeAction.node_async(this::answerQuestion));
        stateGraph.addNode("review_answer", AsyncNodeAction.node_async(this::reviewAnswer));
        stateGraph.addNode("rewrite_answer", AsyncNodeAction.node_async(this::rewriteAnswer));
        stateGraph.addNode("claim_segment", AsyncNodeAction.node_async(queryFinalizationGraphFragment::claimSegment));
        stateGraph.addNode("citation_check", AsyncNodeAction.node_async(queryFinalizationGraphFragment::citationCheck));
        stateGraph.addNode("citation_repair", AsyncNodeAction.node_async(queryFinalizationGraphFragment::citationRepair));
        stateGraph.addNode("persist_response", AsyncNodeAction.node_async(queryFinalizationGraphFragment::persistResponse));
        stateGraph.addNode("finalize_response", AsyncNodeAction.node_async(queryFinalizationGraphFragment::finalizeResponse));

        stateGraph.addEdge(StateGraph.START, "normalize_question");
        stateGraph.addEdge("normalize_question", "rewrite_query");
        stateGraph.addEdge("rewrite_query", "classify_intent");
        stateGraph.addEdge("classify_intent", "resolve_retrieval_strategy");
        stateGraph.addEdge("resolve_retrieval_strategy", "check_cache");
        stateGraph.addConditionalEdges(
                "check_cache",
                AsyncEdgeAction.edge_async(state -> queryGraphConditions.routeAfterCacheCheck(
                        queryGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "dispatch_retrieval", "dispatch_retrieval",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("retrieve_candidates_serial", "fuse_candidates");
        stateGraph.addEdge(
                List.of(
                        "retrieve_fts",
                        "retrieve_article_chunk_fts",
                        "retrieve_refkey",
                        "retrieve_source",
                        "retrieve_source_chunk_fts",
                        "retrieve_contribution",
                        "retrieve_graph",
                        "retrieve_article_vector",
                        "retrieve_chunk_vector"
                ),
                "fuse_candidates"
        );
        stateGraph.addConditionalEdges(
                "fuse_candidates",
                AsyncEdgeAction.edge_async(state -> queryGraphConditions.routeAfterFuseCandidates(
                        queryGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "answer_question", "answer_question",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("answer_question", "review_answer");
        stateGraph.addConditionalEdges(
                "review_answer",
                AsyncEdgeAction.edge_async(state -> routeAfterReview(queryGraphStateMapper.fromMap(state.data()))),
                Map.of(
                        "rewrite_answer", "rewrite_answer",
                        "claim_segment", "claim_segment"
                )
        );
        stateGraph.addEdge("rewrite_answer", "review_answer");
        stateGraph.addEdge("claim_segment", "citation_check");
        stateGraph.addConditionalEdges(
                "citation_check",
                AsyncEdgeAction.edge_async(state -> routeAfterCitationCheck(queryGraphStateMapper.fromMap(state.data()))),
                Map.of(
                        "citation_repair", "citation_repair",
                        "persist_response", "persist_response"
                )
        );
        stateGraph.addEdge("citation_repair", "citation_check");
        stateGraph.addEdge("persist_response", "finalize_response");
        stateGraph.addEdge("finalize_response", StateGraph.END);
        return stateGraph;
    }

    private Map<String, Object> normalizeQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
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

    private Map<String, Object> rewriteQuery(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryRetrievalSettingsState retrievalSettings = retrievalSettings();
        QueryRewriteResult rewriteResult = retrievalSettings.isRewriteEnabled()
                ? queryRewriteService.rewrite(state.getQueryId(), state.getNormalizedQuestion())
                : QueryRewriteResult.unchanged(state.getNormalizedQuestion());
        state.setRewrittenQuestion(rewriteResult.getRewrittenQuestion());
        state.setRewriteAuditRef(rewriteResult.getAuditRef());
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> classifyIntent(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryIntent queryIntent = queryIntentClassifier.classify(effectiveRetrievalQuestion(state));
        state.setQueryIntent(queryIntent.name());
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> resolveRetrievalStrategy(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryIntent queryIntent = readQueryIntent(state.getQueryIntent());
        RetrievalStrategy retrievalStrategy = retrievalStrategyResolver.resolve(
                effectiveRetrievalQuestion(state),
                queryIntent,
                retrievalSettings()
        );
        state.setRetrievalStrategyRef(queryWorkingSetStore.saveRetrievalStrategy(
                state.getQueryId(),
                retrievalStrategy
        ));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> checkCache(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
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

    private MultiCommand dispatchRetrieval(
            com.alibaba.cloud.ai.graph.OverAllState overAllState,
            com.alibaba.cloud.ai.graph.RunnableConfig runnableConfig
    ) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        state.setRetrievalMode(retrievalStrategy.isParallelEnabled() ? "parallel" : "serial");
        state.setRetrievalStartedAtEpochMs(System.currentTimeMillis());
        log.info(
                "[VECTOR][RETRIEVE][START] queryId={}, mode={}, elapsedMs=0, success=true",
                state.getQueryId(),
                state.getRetrievalMode()
        );
        if (retrievalStrategy.isParallelEnabled()) {
            return new MultiCommand(
                    List.of(
                            "retrieve_fts",
                            "retrieve_article_chunk_fts",
                            "retrieve_refkey",
                            "retrieve_source",
                            "retrieve_source_chunk_fts",
                            "retrieve_contribution",
                            "retrieve_graph",
                            "retrieve_article_vector",
                            "retrieve_chunk_vector"
                    ),
                    queryGraphStateMapper.toDeltaMap(state)
            );
        }
        return new MultiCommand(List.of("retrieve_candidates_serial"), queryGraphStateMapper.toDeltaMap(state));
    }

    private Map<String, Object> retrieveCandidatesSerial(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveAllRetrievalHits(state);
    }

    private Map<String, Object> retrieveFts(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_FTS,
                shouldSearch(state, CHANNEL_FTS) ? ftsSearchService.search(readRetrievalQuestion(state), TOP_K) : List.of()
        );
    }

    private Map<String, Object> retrieveArticleChunkFts(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_ARTICLE_CHUNK_FTS,
                shouldSearch(state, CHANNEL_ARTICLE_CHUNK_FTS)
                        ? articleChunkFtsSearchService.search(readRetrievalQuestion(state), TOP_K)
                        : List.of()
        );
    }

    private Map<String, Object> retrieveRefkey(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_REFKEY,
                shouldSearch(state, CHANNEL_REFKEY) ? refKeySearchService.search(readRetrievalQuestion(state), TOP_K) : List.of()
        );
    }

    private Map<String, Object> retrieveSource(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_SOURCE,
                shouldSearch(state, CHANNEL_SOURCE) ? sourceSearchService.search(readRetrievalQuestion(state), TOP_K) : List.of()
        );
    }

    private Map<String, Object> retrieveSourceChunkFts(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_SOURCE_CHUNK_FTS,
                shouldSearch(state, CHANNEL_SOURCE_CHUNK_FTS)
                        ? sourceChunkFtsSearchService.search(readRetrievalQuestion(state), TOP_K)
                        : List.of()
        );
    }

    private Map<String, Object> retrieveContribution(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_CONTRIBUTION,
                shouldSearch(state, CHANNEL_CONTRIBUTION)
                        ? contributionSearchService.search(readRetrievalQuestion(state), TOP_K)
                        : List.of()
        );
    }

    private Map<String, Object> retrieveGraph(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_GRAPH,
                shouldSearch(state, CHANNEL_GRAPH) ? graphSearchService.search(readRetrievalQuestion(state), TOP_K) : List.of()
        );
    }

    private Map<String, Object> retrieveArticleVector(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_ARTICLE_VECTOR,
                shouldSearch(state, CHANNEL_ARTICLE_VECTOR)
                        ? vectorSearchService.search(readRetrievalQuestion(state), TOP_K)
                        : List.of()
        );
    }

    private Map<String, Object> retrieveChunkVector(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return saveSingleChannelHits(
                state,
                CHANNEL_CHUNK_VECTOR,
                shouldSearch(state, CHANNEL_CHUNK_VECTOR)
                        ? chunkVectorSearchService.search(readRetrievalQuestion(state), TOP_K)
                        : List.of()
        );
    }

    private Map<String, Object> fuseCandidates(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        Map<String, List<QueryArticleHit>> channelHits = loadChannelHits(state);
        Map<String, Double> weights = retrievalStrategy.getChannelWeights();
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(channelHits, weights, TOP_K, retrievalStrategy.getRrfK());
        state.setHasFusedHits(!fusedHits.isEmpty());
        if (!fusedHits.isEmpty()) {
            state.setFusedHitsRef(queryWorkingSetStore.saveFusedHits(state.getQueryId(), fusedHits));
        }
        if (retrievalAuditService != null) {
            state.setRetrievalAuditRef(retrievalAuditService.persist(
                    state.getQueryId(),
                    state.getQuestion(),
                    state.getNormalizedQuestion(),
                    retrievalStrategy,
                    state.getRetrievalMode(),
                    isRewriteApplied(state),
                    state.getRewriteAuditRef(),
                    state.getRetrievalStrategyRef(),
                    channelHits,
                    fusedHits
            ));
        }
        long startedAt = state.getRetrievalStartedAtEpochMs();
        long elapsedMs = startedAt <= 0L ? 0L : System.currentTimeMillis() - startedAt;
        log.info(
                "[VECTOR][RETRIEVE][END] queryId={}, mode={}, elapsedMs={}, success=true",
                state.getQueryId(),
                state.getRetrievalMode(),
                elapsedMs
        );
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> answerQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
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
        state.setAnswerCacheable(answerPayload.isAnswerCacheable());
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> reviewAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
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

    private Map<String, Object> rewriteAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
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
        state.setAnswerCacheable(rewrittenPayload.isAnswerCacheable());
        state.setRewriteAttemptCount(state.getRewriteAttemptCount() + 1);
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private String routeAfterReview(QueryGraphState state) {
        ReviewResult reviewResult = queryWorkingSetStore.loadReviewResult(state.getReviewResultRef());
        return queryGraphConditions.routeAfterReview(state, reviewResult);
    }

    private String routeAfterCitationCheck(QueryGraphState state) {
        CitationCheckReport report = queryWorkingSetStore.loadCitationCheckReport(state.getCitationCheckReportRef());
        return queryGraphConditions.routeAfterCitationCheck(state, report);
    }

    private QueryResponse withQueryId(QueryResponse queryResponse, String queryId) {
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
                queryResponse.getDeepResearch()
        );
    }

    private boolean isCacheableOutcome(AnswerOutcome answerOutcome) {
        return answerOutcome == AnswerOutcome.SUCCESS;
    }

    private AnswerOutcome readAnswerOutcome(String answerOutcome) {
        if (answerOutcome == null || answerOutcome.isBlank()) {
            return null;
        }
        return AnswerOutcome.valueOf(answerOutcome);
    }

    private String enumName(Enum<?> enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.name();
    }

    private String buildRewriteGuidance(ReviewResult reviewResult) {
        if (reviewResult == null || reviewResult.getIssues().isEmpty()) {
            return "审查未通过，请基于证据增强答案的可验证性。";
        }
        List<String> issueDescriptions = new ArrayList<String>();
        for (ReviewIssue reviewIssue : reviewResult.getIssues()) {
            issueDescriptions.add(reviewIssue.getCategory() + ":" + reviewIssue.getDescription());
        }
        return String.join("; ", issueDescriptions);
    }

    private List<String> collectSourcePaths(List<QueryArticleHit> fusedHits) {
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (QueryArticleHit fusedHit : fusedHits) {
            sourcePaths.addAll(fusedHit.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }

    private Map<String, Object> saveAllRetrievalHits(QueryGraphState state) {
        Map<String, Object> delta = new LinkedHashMap<String, Object>();
        String retrievalQuestion = readRetrievalQuestion(state);
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_FTS,
                shouldSearch(state, CHANNEL_FTS) ? ftsSearchService.search(retrievalQuestion, TOP_K) : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_ARTICLE_CHUNK_FTS,
                shouldSearch(state, CHANNEL_ARTICLE_CHUNK_FTS)
                        ? articleChunkFtsSearchService.search(retrievalQuestion, TOP_K)
                        : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_REFKEY,
                shouldSearch(state, CHANNEL_REFKEY) ? refKeySearchService.search(retrievalQuestion, TOP_K) : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_SOURCE,
                shouldSearch(state, CHANNEL_SOURCE) ? sourceSearchService.search(retrievalQuestion, TOP_K) : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_SOURCE_CHUNK_FTS,
                shouldSearch(state, CHANNEL_SOURCE_CHUNK_FTS)
                        ? sourceChunkFtsSearchService.search(retrievalQuestion, TOP_K)
                        : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_CONTRIBUTION,
                shouldSearch(state, CHANNEL_CONTRIBUTION)
                        ? contributionSearchService.search(retrievalQuestion, TOP_K)
                        : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_GRAPH,
                shouldSearch(state, CHANNEL_GRAPH) ? graphSearchService.search(retrievalQuestion, TOP_K) : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_ARTICLE_VECTOR,
                shouldSearch(state, CHANNEL_ARTICLE_VECTOR) ? vectorSearchService.search(retrievalQuestion, TOP_K) : List.of()
        ));
        delta.putAll(saveSingleChannelHits(
                state,
                CHANNEL_CHUNK_VECTOR,
                shouldSearch(state, CHANNEL_CHUNK_VECTOR) ? chunkVectorSearchService.search(retrievalQuestion, TOP_K) : List.of()
        ));
        return delta;
    }

    private Map<String, Object> saveSingleChannelHits(QueryGraphState state, String channel, List<QueryArticleHit> hits) {
        String ref = queryWorkingSetStore.saveHits(state.getQueryId(), channel, hits);
        Map<String, Object> delta = new LinkedHashMap<String, Object>();
        if (CHANNEL_FTS.equals(channel)) {
            delta.put(QueryGraphStateKeys.FTS_HITS_REF, ref);
        }
        else if (CHANNEL_ARTICLE_CHUNK_FTS.equals(channel)) {
            delta.put(QueryGraphStateKeys.ARTICLE_CHUNK_HITS_REF, ref);
        }
        else if (CHANNEL_REFKEY.equals(channel)) {
            delta.put(QueryGraphStateKeys.REFKEY_HITS_REF, ref);
        }
        else if (CHANNEL_SOURCE.equals(channel)) {
            delta.put(QueryGraphStateKeys.SOURCE_HITS_REF, ref);
        }
        else if (CHANNEL_SOURCE_CHUNK_FTS.equals(channel)) {
            delta.put(QueryGraphStateKeys.SOURCE_CHUNK_HITS_REF, ref);
        }
        else if (CHANNEL_CONTRIBUTION.equals(channel)) {
            delta.put(QueryGraphStateKeys.CONTRIBUTION_HITS_REF, ref);
        }
        else if (CHANNEL_GRAPH.equals(channel)) {
            delta.put(QueryGraphStateKeys.GRAPH_HITS_REF, ref);
        }
        else if (CHANNEL_ARTICLE_VECTOR.equals(channel)) {
            delta.put(QueryGraphStateKeys.ARTICLE_VECTOR_HITS_REF, ref);
        }
        else if (CHANNEL_CHUNK_VECTOR.equals(channel)) {
            delta.put(QueryGraphStateKeys.CHUNK_VECTOR_HITS_REF, ref);
        }
        return delta;
    }

    private Map<String, List<QueryArticleHit>> loadChannelHits(QueryGraphState state) {
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(CHANNEL_FTS, queryWorkingSetStore.loadHits(state.getFtsHitsRef()));
        channelHits.put(CHANNEL_ARTICLE_CHUNK_FTS, queryWorkingSetStore.loadHits(state.getArticleChunkHitsRef()));
        channelHits.put(CHANNEL_REFKEY, queryWorkingSetStore.loadHits(state.getRefkeyHitsRef()));
        channelHits.put(CHANNEL_SOURCE, queryWorkingSetStore.loadHits(state.getSourceHitsRef()));
        channelHits.put(CHANNEL_SOURCE_CHUNK_FTS, queryWorkingSetStore.loadHits(state.getSourceChunkHitsRef()));
        channelHits.put(CHANNEL_CONTRIBUTION, queryWorkingSetStore.loadHits(state.getContributionHitsRef()));
        channelHits.put(CHANNEL_GRAPH, queryWorkingSetStore.loadHits(state.getGraphHitsRef()));
        channelHits.put(CHANNEL_ARTICLE_VECTOR, queryWorkingSetStore.loadHits(state.getArticleVectorHitsRef()));
        channelHits.put(CHANNEL_CHUNK_VECTOR, queryWorkingSetStore.loadHits(state.getChunkVectorHitsRef()));
        return channelHits;
    }

    private QueryRetrievalSettingsState retrievalSettings() {
        return queryRetrievalSettingsService == null
                ? new QueryRetrievalSettingsService().defaultState()
                : queryRetrievalSettingsService.getCurrentState();
    }

    private boolean shouldSearch(QueryGraphState state, String channel) {
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        return retrievalStrategy.isChannelEnabled(channel);
    }

    private RetrievalStrategy currentStrategy(QueryGraphState state) {
        RetrievalStrategy retrievalStrategy = queryWorkingSetStore.loadRetrievalStrategy(state.getRetrievalStrategyRef());
        if (retrievalStrategy != null) {
            return retrievalStrategy;
        }
        return retrievalStrategyResolver.resolve(
                effectiveRetrievalQuestion(state),
                readQueryIntent(state.getQueryIntent()),
                retrievalSettings()
        );
    }

    private String readRetrievalQuestion(QueryGraphState state) {
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        if (retrievalStrategy.getRetrievalQuestion() != null && !retrievalStrategy.getRetrievalQuestion().isBlank()) {
            return retrievalStrategy.getRetrievalQuestion();
        }
        return effectiveRetrievalQuestion(state);
    }

    private String effectiveRetrievalQuestion(QueryGraphState state) {
        if (state.getRewrittenQuestion() != null && !state.getRewrittenQuestion().isBlank()) {
            return state.getRewrittenQuestion();
        }
        if (state.getNormalizedQuestion() != null && !state.getNormalizedQuestion().isBlank()) {
            return state.getNormalizedQuestion();
        }
        return state.getQuestion();
    }

    private boolean isRewriteApplied(QueryGraphState state) {
        if (state == null) {
            return false;
        }
        String rewrittenQuestion = state.getRewrittenQuestion();
        String normalizedQuestion = state.getNormalizedQuestion();
        if (rewrittenQuestion == null || rewrittenQuestion.isBlank()) {
            return false;
        }
        if (normalizedQuestion == null) {
            return false;
        }
        return !rewrittenQuestion.equals(normalizedQuestion);
    }

    private QueryIntent readQueryIntent(String queryIntent) {
        if (queryIntent == null || queryIntent.isBlank()) {
            return QueryIntent.GENERAL;
        }
        try {
            return QueryIntent.valueOf(queryIntent);
        }
        catch (IllegalArgumentException exception) {
            return QueryIntent.GENERAL;
        }
    }
}
