package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncMultiCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.MultiCommand;
import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.ChunkVectorSearchService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.RrfFusionService;
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

    private static final List<String> NON_CACHEABLE_ANSWER_MARKERS = List.of(
            "当前证据不足",
            "证据不足",
            "暂无法确认",
            "无法确认",
            "暂无足够证据",
            "没有足够证据"
    );

    private static final String CHANNEL_FTS = "fts";

    private static final String CHANNEL_REFKEY = "refkey";

    private static final String CHANNEL_SOURCE = "source";

    private static final String CHANNEL_CONTRIBUTION = "contribution";

    private static final String CHANNEL_ARTICLE_VECTOR = "article_vector";

    private static final String CHANNEL_CHUNK_VECTOR = "chunk_vector";

    private final FtsSearchService ftsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final ContributionSearchService contributionSearchService;

    private final VectorSearchService vectorSearchService;

    private final ChunkVectorSearchService chunkVectorSearchService;

    private final RrfFusionService rrfFusionService;

    private final QueryRetrievalSettingsService queryRetrievalSettingsService;

    private final AnswerGenerationService answerGenerationService;

    private final QueryCacheStore queryCacheStore;

    private final ReviewerAgent reviewerAgent;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final QueryGraphConditions queryGraphConditions;

    /**
     * 创建问答图定义工厂。
     */
    public QueryGraphDefinitionFactory(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            VectorSearchService vectorSearchService,
            ChunkVectorSearchService chunkVectorSearchService,
            RrfFusionService rrfFusionService,
            QueryRetrievalSettingsService queryRetrievalSettingsService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            QueryWorkingSetStore queryWorkingSetStore,
            QueryGraphStateMapper queryGraphStateMapper,
            QueryGraphConditions queryGraphConditions
    ) {
        this.ftsSearchService = ftsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.contributionSearchService = contributionSearchService;
        this.vectorSearchService = vectorSearchService;
        this.chunkVectorSearchService = chunkVectorSearchService;
        this.rrfFusionService = rrfFusionService;
        this.queryRetrievalSettingsService = queryRetrievalSettingsService;
        this.answerGenerationService = answerGenerationService;
        this.queryCacheStore = queryCacheStore;
        this.reviewerAgent = reviewerAgent;
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.queryGraphStateMapper = queryGraphStateMapper;
        this.queryGraphConditions = queryGraphConditions;
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
        stateGraph.addNode("check_cache", AsyncNodeAction.node_async(this::checkCache));
        stateGraph.addNode(
                "dispatch_retrieval",
                AsyncMultiCommandAction.node_async(this::dispatchRetrieval),
                Map.of(
                        "retrieve_candidates_serial", "retrieve_candidates_serial",
                        "retrieve_fts", "retrieve_fts",
                        "retrieve_refkey", "retrieve_refkey",
                        "retrieve_source", "retrieve_source",
                        "retrieve_contribution", "retrieve_contribution",
                        "retrieve_article_vector", "retrieve_article_vector",
                        "retrieve_chunk_vector", "retrieve_chunk_vector"
                )
        );
        stateGraph.addNode("retrieve_candidates_serial", AsyncNodeAction.node_async(this::retrieveCandidatesSerial));
        stateGraph.addNode("retrieve_fts", AsyncNodeAction.node_async(this::retrieveFts));
        stateGraph.addNode("retrieve_refkey", AsyncNodeAction.node_async(this::retrieveRefkey));
        stateGraph.addNode("retrieve_source", AsyncNodeAction.node_async(this::retrieveSource));
        stateGraph.addNode("retrieve_contribution", AsyncNodeAction.node_async(this::retrieveContribution));
        stateGraph.addNode("retrieve_article_vector", AsyncNodeAction.node_async(this::retrieveArticleVector));
        stateGraph.addNode("retrieve_chunk_vector", AsyncNodeAction.node_async(this::retrieveChunkVector));
        stateGraph.addNode("fuse_candidates", AsyncNodeAction.node_async(this::fuseCandidates));
        stateGraph.addNode("answer_question", AsyncNodeAction.node_async(this::answerQuestion));
        stateGraph.addNode("review_answer", AsyncNodeAction.node_async(this::reviewAnswer));
        stateGraph.addNode("rewrite_answer", AsyncNodeAction.node_async(this::rewriteAnswer));
        stateGraph.addNode("cache_response", AsyncNodeAction.node_async(this::cacheResponse));
        stateGraph.addNode("finalize_response", AsyncNodeAction.node_async(this::finalizeResponse));

        stateGraph.addEdge(StateGraph.START, "normalize_question");
        stateGraph.addEdge("normalize_question", "check_cache");
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
                        "retrieve_refkey",
                        "retrieve_source",
                        "retrieve_contribution",
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
                        "cache_response", "cache_response",
                        "rewrite_answer", "rewrite_answer",
                        "finalize_response", "finalize_response"
                )
        );
        stateGraph.addEdge("rewrite_answer", "review_answer");
        stateGraph.addEdge("cache_response", "finalize_response");
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

    private Map<String, Object> checkCache(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryResponse cachedResponse = queryCacheStore.get(state.getNormalizedQuestion()).orElse(null);
        if (cachedResponse != null) {
            state.setCacheHit(true);
            state.setCachedResponseRef(queryWorkingSetStore.saveResponse(state.getQueryId(), cachedResponse));
            state.setReviewStatus(cachedResponse.getReviewStatus());
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
        QueryRetrievalSettingsState retrievalSettings = retrievalSettings();
        state.setRetrievalMode(retrievalSettings.isParallelEnabled() ? "parallel" : "serial");
        state.setRetrievalStartedAtEpochMs(System.currentTimeMillis());
        log.info(
                "[VECTOR][RETRIEVE][START] queryId={}, mode={}, elapsedMs=0, success=true",
                state.getQueryId(),
                state.getRetrievalMode()
        );
        if (retrievalSettings.isParallelEnabled()) {
            return new MultiCommand(
                    List.of(
                            "retrieve_fts",
                            "retrieve_refkey",
                            "retrieve_source",
                            "retrieve_contribution",
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
        return saveSingleChannelHits(
                queryGraphStateMapper.fromMap(overAllState.data()),
                CHANNEL_FTS,
                ftsSearchService.search(readQuestion(overAllState), TOP_K)
        );
    }

    private Map<String, Object> retrieveRefkey(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        return saveSingleChannelHits(
                queryGraphStateMapper.fromMap(overAllState.data()),
                CHANNEL_REFKEY,
                refKeySearchService.search(readQuestion(overAllState), TOP_K)
        );
    }

    private Map<String, Object> retrieveSource(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        return saveSingleChannelHits(
                queryGraphStateMapper.fromMap(overAllState.data()),
                CHANNEL_SOURCE,
                sourceSearchService.search(readQuestion(overAllState), TOP_K)
        );
    }

    private Map<String, Object> retrieveContribution(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        return saveSingleChannelHits(
                queryGraphStateMapper.fromMap(overAllState.data()),
                CHANNEL_CONTRIBUTION,
                contributionSearchService.search(readQuestion(overAllState), TOP_K)
        );
    }

    private Map<String, Object> retrieveArticleVector(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        return saveSingleChannelHits(
                queryGraphStateMapper.fromMap(overAllState.data()),
                CHANNEL_ARTICLE_VECTOR,
                vectorSearchService.search(readQuestion(overAllState), TOP_K)
        );
    }

    private Map<String, Object> retrieveChunkVector(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        return saveSingleChannelHits(
                queryGraphStateMapper.fromMap(overAllState.data()),
                CHANNEL_CHUNK_VECTOR,
                chunkVectorSearchService.search(readQuestion(overAllState), TOP_K)
        );
    }

    private Map<String, Object> fuseCandidates(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryRetrievalSettingsState retrievalSettings = retrievalSettings();
        Map<String, List<QueryArticleHit>> channelHits = loadChannelHits(state);
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put(CHANNEL_FTS, retrievalSettings.getFtsWeight());
        weights.put(CHANNEL_REFKEY, retrievalSettings.getFtsWeight());
        weights.put(CHANNEL_SOURCE, retrievalSettings.getSourceWeight());
        weights.put(CHANNEL_CONTRIBUTION, retrievalSettings.getContributionWeight());
        weights.put(CHANNEL_ARTICLE_VECTOR, retrievalSettings.getArticleVectorWeight());
        weights.put(CHANNEL_CHUNK_VECTOR, retrievalSettings.getChunkVectorWeight());
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(channelHits, weights, TOP_K, retrievalSettings.getRrfK());
        state.setHasFusedHits(!fusedHits.isEmpty());
        if (!fusedHits.isEmpty()) {
            state.setFusedHitsRef(queryWorkingSetStore.saveFusedHits(state.getQueryId(), fusedHits));
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
        String answer = answerGenerationService.generate(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                state.getQuestion(),
                fusedHits
        );
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), answer));
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
        String rewrittenAnswer = answerGenerationService.rewriteFromReviewFeedback(
                state.getLlmScopeId(),
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REWRITE,
                state.getQuestion(),
                currentAnswer,
                buildRewriteGuidance(reviewResult),
                fusedHits
        );
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), rewrittenAnswer));
        state.setRewriteAttemptCount(state.getRewriteAttemptCount() + 1);
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> cacheResponse(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        QueryResponse queryResponse = buildSuccessResponse(state);
        String responseRef = queryWorkingSetStore.saveResponse(state.getQueryId(), queryResponse);
        if (shouldCacheResponse(queryResponse)) {
            queryCacheStore.put(state.getNormalizedQuestion(), queryResponse);
            state.setCachedResponseRef(responseRef);
        }
        state.setFinalResponseRef(responseRef);
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> finalizeResponse(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        if (state.getFinalResponseRef() != null) {
            return queryGraphStateMapper.toDeltaMap(state);
        }
        if (state.isCacheHit()) {
            state.setFinalResponseRef(state.getCachedResponseRef());
            return queryGraphStateMapper.toDeltaMap(state);
        }
        QueryResponse queryResponse;
        if (!state.isHasFusedHits()) {
            queryResponse = new QueryResponse("未找到相关知识", List.of(), List.of(), null, null);
        }
        else {
            queryResponse = buildSuccessResponse(state);
        }
        state.setFinalResponseRef(queryWorkingSetStore.saveResponse(state.getQueryId(), queryResponse));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private String routeAfterReview(QueryGraphState state) {
        ReviewResult reviewResult = queryWorkingSetStore.loadReviewResult(state.getReviewResultRef());
        return queryGraphConditions.routeAfterReview(state, reviewResult);
    }

    private QueryResponse buildSuccessResponse(QueryGraphState state) {
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        return new QueryResponse(
                answer,
                toSourceResponses(fusedHits),
                toArticleResponses(fusedHits),
                null,
                state.getReviewStatus()
        );
    }

    /**
     * 判断当前响应是否适合写入 query cache。
     *
     * @param queryResponse 查询响应
     * @return 是否允许写缓存
     */
    private boolean shouldCacheResponse(QueryResponse queryResponse) {
        if (queryResponse == null) {
            return false;
        }
        String answer = queryResponse.getAnswer();
        if (answer == null || answer.isBlank()) {
            return false;
        }
        if (queryResponse.getSources().isEmpty() && queryResponse.getArticles().isEmpty()) {
            return false;
        }
        for (String marker : NON_CACHEABLE_ANSWER_MARKERS) {
            if (answer.contains(marker)) {
                return false;
            }
        }
        return true;
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

    private List<QuerySourceResponse> toSourceResponses(List<QueryArticleHit> fusedHits) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        Set<String> responseKeys = new LinkedHashSet<String>();
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.ARTICLE);
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.SOURCE);
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.CONTRIBUTION);
        return sourceResponses;
    }

    private void appendSourceResponses(
            List<QuerySourceResponse> sourceResponses,
            Set<String> responseKeys,
            List<QueryArticleHit> fusedHits,
            QueryEvidenceType queryEvidenceType
    ) {
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit.getEvidenceType() != queryEvidenceType) {
                continue;
            }
            String responseIdentity = fusedHit.getArticleKey();
            if (responseIdentity == null || responseIdentity.isBlank()) {
                responseIdentity = fusedHit.getConceptId();
            }
            String responseKey = fusedHit.getEvidenceType().name() + ":" + responseIdentity;
            if (!responseKeys.add(responseKey)) {
                continue;
            }
            sourceResponses.add(new QuerySourceResponse(
                    fusedHit.getSourceId(),
                    fusedHit.getArticleKey(),
                    fusedHit.getConceptId(),
                    fusedHit.getTitle(),
                    fusedHit.getSourcePaths()
            ));
        }
    }

    private List<QueryArticleResponse> toArticleResponses(List<QueryArticleHit> fusedHits) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
                continue;
            }
            articleResponses.add(new QueryArticleResponse(
                    fusedHit.getSourceId(),
                    fusedHit.getArticleKey(),
                    fusedHit.getConceptId(),
                    fusedHit.getTitle()
            ));
        }
        return articleResponses;
    }

    private List<String> collectSourcePaths(List<QueryArticleHit> fusedHits) {
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (QueryArticleHit fusedHit : fusedHits) {
            sourcePaths.addAll(fusedHit.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }

    private String readQuestion(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        return state.getQuestion();
    }

    private Map<String, Object> saveAllRetrievalHits(QueryGraphState state) {
        Map<String, Object> delta = new LinkedHashMap<String, Object>();
        delta.putAll(saveSingleChannelHits(state, CHANNEL_FTS, ftsSearchService.search(state.getQuestion(), TOP_K)));
        delta.putAll(saveSingleChannelHits(state, CHANNEL_REFKEY, refKeySearchService.search(state.getQuestion(), TOP_K)));
        delta.putAll(saveSingleChannelHits(state, CHANNEL_SOURCE, sourceSearchService.search(state.getQuestion(), TOP_K)));
        delta.putAll(saveSingleChannelHits(state, CHANNEL_CONTRIBUTION, contributionSearchService.search(state.getQuestion(), TOP_K)));
        delta.putAll(saveSingleChannelHits(state, CHANNEL_ARTICLE_VECTOR, vectorSearchService.search(state.getQuestion(), TOP_K)));
        delta.putAll(saveSingleChannelHits(state, CHANNEL_CHUNK_VECTOR, chunkVectorSearchService.search(state.getQuestion(), TOP_K)));
        return delta;
    }

    private Map<String, Object> saveSingleChannelHits(QueryGraphState state, String channel, List<QueryArticleHit> hits) {
        String ref = queryWorkingSetStore.saveHits(state.getQueryId(), channel, hits);
        Map<String, Object> delta = new LinkedHashMap<String, Object>();
        if (CHANNEL_FTS.equals(channel)) {
            delta.put(QueryGraphStateKeys.FTS_HITS_REF, ref);
        }
        else if (CHANNEL_REFKEY.equals(channel)) {
            delta.put(QueryGraphStateKeys.REFKEY_HITS_REF, ref);
        }
        else if (CHANNEL_SOURCE.equals(channel)) {
            delta.put(QueryGraphStateKeys.SOURCE_HITS_REF, ref);
        }
        else if (CHANNEL_CONTRIBUTION.equals(channel)) {
            delta.put(QueryGraphStateKeys.CONTRIBUTION_HITS_REF, ref);
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
        channelHits.put(CHANNEL_REFKEY, queryWorkingSetStore.loadHits(state.getRefkeyHitsRef()));
        channelHits.put(CHANNEL_SOURCE, queryWorkingSetStore.loadHits(state.getSourceHitsRef()));
        channelHits.put(CHANNEL_CONTRIBUTION, queryWorkingSetStore.loadHits(state.getContributionHitsRef()));
        channelHits.put(CHANNEL_ARTICLE_VECTOR, queryWorkingSetStore.loadHits(state.getArticleVectorHitsRef()));
        channelHits.put(CHANNEL_CHUNK_VECTOR, queryWorkingSetStore.loadHits(state.getChunkVectorHitsRef()));
        return channelHits;
    }

    private QueryRetrievalSettingsState retrievalSettings() {
        return queryRetrievalSettingsService == null
                ? new QueryRetrievalSettingsService().defaultState()
                : queryRetrievalSettingsService.getCurrentState();
    }
}
