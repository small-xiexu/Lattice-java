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
 * 问答图定义工厂
 *
 * 职责：集中声明 Query Graph 的节点、顺序边与条件边
 *
 * @author xiexu
 */
@Slf4j
@Component
public class QueryGraphDefinitionFactory {

    private static final int TOP_K = 8;

    private static final int RETRIEVAL_CANDIDATE_LIMIT = 16;

    private static final int EXACT_LOOKUP_CONTEXT_LIMIT = 16;

    private static final int EXACT_LOOKUP_SUPPORT_LIMIT = 6;

    private static final String CHANNEL_FTS = RetrievalStrategyResolver.CHANNEL_FTS;

    private static final String CHANNEL_ARTICLE_CHUNK_FTS = RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS;

    private static final String CHANNEL_REFKEY = RetrievalStrategyResolver.CHANNEL_REFKEY;

    private static final String CHANNEL_SOURCE = RetrievalStrategyResolver.CHANNEL_SOURCE;

    private static final String CHANNEL_SOURCE_CHUNK_FTS = RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS;

    private static final String CHANNEL_FACT_CARD_FTS = RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS;

    private static final String CHANNEL_FACT_CARD_VECTOR = RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR;

    private static final String CHANNEL_CONTRIBUTION = RetrievalStrategyResolver.CHANNEL_CONTRIBUTION;

    private static final String CHANNEL_GRAPH = RetrievalStrategyResolver.CHANNEL_GRAPH;

    private static final String CHANNEL_ARTICLE_VECTOR = RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR;

    private static final String CHANNEL_CHUNK_VECTOR = RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR;

    private final FtsSearchService ftsSearchService;

    private final ArticleChunkFtsSearchService articleChunkFtsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final SourceChunkFtsSearchService sourceChunkFtsSearchService;

    private final FactCardFtsSearchService factCardFtsSearchService;

    private final FactCardVectorSearchService factCardVectorSearchService;

    private final ContributionSearchService contributionSearchService;

    private final GraphSearchService graphSearchService;

    private final VectorSearchService vectorSearchService;

    private final ChunkVectorSearchService chunkVectorSearchService;

    private final RrfFusionService rrfFusionService;

    private final QueryRetrievalSettingsService queryRetrievalSettingsService;

    private final QuerySearchProperties querySearchProperties;

    private final QueryRewriteService queryRewriteService;

    private final QueryIntentClassifier queryIntentClassifier;

    private final AnswerShapeClassifier answerShapeClassifier;

    private final RetrievalStrategyResolver retrievalStrategyResolver;

    private final RetrievalAuditService retrievalAuditService;

    private final AnswerGenerationService answerGenerationService;

    private final QueryCacheStore queryCacheStore;

    private final ReviewerAgent reviewerAgent;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final QueryGraphConditions queryGraphConditions;

    private final QueryFinalizationGraphFragment queryFinalizationGraphFragment;

    private final RetrievalDispatcher retrievalDispatcher = new RetrievalDispatcher();

    /**
     * 创建问答图定义工厂。
     */
    public QueryGraphDefinitionFactory(
            FtsSearchService ftsSearchService,
            ArticleChunkFtsSearchService articleChunkFtsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            SourceChunkFtsSearchService sourceChunkFtsSearchService,
            FactCardFtsSearchService factCardFtsSearchService,
            FactCardVectorSearchService factCardVectorSearchService,
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
        this.ftsSearchService = ftsSearchService;
        this.articleChunkFtsSearchService = articleChunkFtsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.sourceChunkFtsSearchService = sourceChunkFtsSearchService;
        this.factCardFtsSearchService = factCardFtsSearchService;
        this.factCardVectorSearchService = factCardVectorSearchService == null
                ? new FactCardVectorSearchService()
                : factCardVectorSearchService;
        this.contributionSearchService = contributionSearchService;
        this.graphSearchService = graphSearchService;
        this.vectorSearchService = vectorSearchService;
        this.chunkVectorSearchService = chunkVectorSearchService;
        this.rrfFusionService = rrfFusionService;
        this.queryRetrievalSettingsService = queryRetrievalSettingsService;
        this.querySearchProperties = querySearchProperties == null
                ? new QuerySearchProperties()
                : querySearchProperties;
        this.queryRewriteService = queryRewriteService == null ? new QueryRewriteService() : queryRewriteService;
        this.queryIntentClassifier = queryIntentClassifier == null ? new QueryIntentClassifier() : queryIntentClassifier;
        this.answerShapeClassifier = answerShapeClassifier == null ? new AnswerShapeClassifier() : answerShapeClassifier;
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
        stateGraph.addNode("dispatch_retrieval", AsyncNodeAction.node_async(this::dispatchRetrieval));
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
        stateGraph.addEdge("dispatch_retrieval", "fuse_candidates");
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
        String retrievalQuestion = effectiveRetrievalQuestion(state);
        QueryIntent queryIntent = queryIntentClassifier.classify(retrievalQuestion);
        state.setQueryIntent(queryIntent.name());
        state.setAnswerShape(answerShapeClassifier.classify(retrievalQuestion).name());
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> resolveRetrievalStrategy(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
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

    private Map<String, Object> dispatchRetrieval(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        state.setRetrievalMode(retrievalStrategy.isParallelEnabled() ? "parallel" : "serial");
        state.setRetrievalStartedAtEpochMs(System.currentTimeMillis());
        log.info(
                "[VECTOR][RETRIEVE][START] queryId={}, mode={}, elapsedMs=0, success=true",
                state.getQueryId(),
                state.getRetrievalMode()
        );
        Map<String, Object> delta = queryGraphStateMapper.toDeltaMap(state);
        delta.putAll(saveAllRetrievalHits(state));
        return delta;
    }

    private Map<String, Object> fuseCandidates(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        Map<String, List<QueryArticleHit>> channelHits = loadChannelHits(state);
        Map<String, RetrievalChannelRun> channelRuns =
                queryWorkingSetStore.loadRetrievalChannelRuns(state.getRetrievalChannelRunsRef());
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(channelHits, retrievalStrategy, TOP_K);
        fusedHits = enrichExactLookupSupportHits(state.getQuestion(), fusedHits, channelHits);
        fusedHits = filterFusedHits(state.getQuestion(), fusedHits);
        state.setHasFusedHits(!fusedHits.isEmpty());
        state.setFusedHitsRef(queryWorkingSetStore.saveFusedHits(state.getQueryId(), fusedHits));
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
                    fusedHits,
                    channelRuns
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

    /**
     * 对融合后的 TOP-K 再做问题相关性过滤，避免低相关文章进入生成上下文。
     *
     * @param question 用户问题
     * @param fusedHits 融合命中
     * @return 过滤后的命中；若过滤为空则保留原始命中
     */
    private List<QueryArticleHit> filterFusedHits(String question, List<QueryArticleHit> fusedHits) {
        List<QueryArticleHit> relevantHits = QueryEvidenceRelevanceSupport.filterRelevantHits(question, fusedHits);
        if (relevantHits.isEmpty()) {
            if (hasStrictExactToken(question)) {
                return relevantHits;
            }
            return fusedHits;
        }
        return relevantHits;
    }

    /**
     * 精确查值题额外补入更直接的 source/refkey 证据，避免 fused 结果只剩概述性 article。
     *
     * @param question 用户问题
     * @param fusedHits 当前融合命中
     * @param channelHits 各通道命中
     * @return 补齐后的融合命中
     */
    private List<QueryArticleHit> enrichExactLookupSupportHits(
            String question,
            List<QueryArticleHit> fusedHits,
            Map<String, List<QueryArticleHit>> channelHits
    ) {
        if (!looksLikeExactLookupQuestion(question) || channelHits == null || channelHits.isEmpty()) {
            return fusedHits;
        }
        List<QueryArticleHit> supportCandidates = collectExactLookupSupportCandidates(question, channelHits);
        if (supportCandidates.isEmpty()) {
            return fusedHits;
        }
        List<QueryArticleHit> enrichedHits = fusedHits == null
                ? new ArrayList<QueryArticleHit>()
                : new ArrayList<QueryArticleHit>(fusedHits);
        for (QueryArticleHit supportCandidate : supportCandidates) {
            if (containsEquivalentHit(enrichedHits, supportCandidate)) {
                continue;
            }
            enrichedHits.add(supportCandidate);
            if (enrichedHits.size() >= EXACT_LOOKUP_CONTEXT_LIMIT) {
                break;
            }
        }
        return enrichedHits;
    }

    /**
     * 收集精确查值题更值得补进 fused hits 的 support 证据。
     *
     * @param question 用户问题
     * @param channelHits 各通道命中
     * @return 候选证据
     */
    private List<QueryArticleHit> collectExactLookupSupportCandidates(
            String question,
            Map<String, List<QueryArticleHit>> channelHits
    ) {
        List<QueryArticleHit> supportHits = new ArrayList<QueryArticleHit>();
        addExactLookupSupportHits(
                supportHits,
                question,
                channelHits.get(CHANNEL_SOURCE_CHUNK_FTS)
        );
        addExactLookupSupportHits(
                supportHits,
                question,
                channelHits.get(CHANNEL_SOURCE)
        );
        addExactLookupSupportHits(
                supportHits,
                question,
                channelHits.get(CHANNEL_REFKEY)
        );
        supportHits.sort((leftHit, rightHit) -> Integer.compare(
                scoreExactLookupSupportHit(question, rightHit),
                scoreExactLookupSupportHit(question, leftHit)
        ));
        if (supportHits.size() <= EXACT_LOOKUP_SUPPORT_LIMIT) {
            return supportHits;
        }
        return new ArrayList<QueryArticleHit>(supportHits.subList(0, EXACT_LOOKUP_SUPPORT_LIMIT));
    }

    /**
     * 从单个通道里挑出适合精确查值题的补充证据。
     *
     * @param supportHits 目标列表
     * @param question 用户问题
     * @param channelHits 通道命中
     */
    private void addExactLookupSupportHits(
            List<QueryArticleHit> supportHits,
            String question,
            List<QueryArticleHit> channelHits
    ) {
        if (channelHits == null || channelHits.isEmpty()) {
            return;
        }
        for (QueryArticleHit channelHit : channelHits) {
            if (channelHit == null || !QueryEvidenceRelevanceSupport.isRelevant(question, channelHit)) {
                continue;
            }
            int supportScore = scoreExactLookupSupportHit(question, channelHit);
            if (supportScore <= 0) {
                continue;
            }
            supportHits.add(channelHit);
            if (supportHits.size() >= EXACT_LOOKUP_CONTEXT_LIMIT) {
                return;
            }
        }
    }

    /**
     * 为精确查值题评估单条 support 证据的价值。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 支持分值
     */
    private int scoreExactLookupSupportHit(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return Integer.MIN_VALUE;
        }
        String normalizedQuestion = lowerCase(question);
        String haystack = lowerCase(queryArticleHit.getTitle())
                + " "
                + lowerCase(queryArticleHit.getContent())
                + " "
                + lowerCase(queryArticleHit.getMetadataJson());
        int score = QueryEvidenceRelevanceSupport.score(question, queryArticleHit);
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            score += 16;
        }
        if (normalizedQuestion.contains("命中数") && haystack.matches("(?s).*\\d.*")) {
            score += 18;
        }
        if ((normalizedQuestion.contains("路径") || normalizedQuestion.contains("接口")) && haystack.contains("/")) {
            score += 18;
        }
        if ((normalizedQuestion.contains("结论") || normalizedQuestion.contains("状态"))
                && (haystack.contains("修正为")
                || haystack.contains("确认")
                || haystack.contains("生效")
                || haystack.contains("启用")
                || haystack.contains("禁用"))) {
            score += 20;
        }
        if ((normalizedQuestion.contains("差异") || normalizedQuestion.contains("不同") || normalizedQuestion.contains("是否一致"))
                && (haystack.contains("不同") || haystack.contains("不一致") || haystack.contains("差异"))) {
            score += 18;
        }
        if ((normalizedQuestion.contains("批") || normalizedQuestion.contains("场景"))
                && haystack.contains("第")
                && haystack.contains("批")) {
            score += 16;
        }
        return score;
    }

    /**
     * 判断融合命中中是否已有同一条证据。
     *
     * @param fusedHits 融合命中
     * @param candidate 候选命中
     * @return 已存在返回 true
     */
    private boolean containsEquivalentHit(List<QueryArticleHit> fusedHits, QueryArticleHit candidate) {
        if (fusedHits == null || fusedHits.isEmpty() || candidate == null) {
            return false;
        }
        String candidateKey = hitIdentity(candidate);
        for (QueryArticleHit fusedHit : fusedHits) {
            if (candidateKey.equals(hitIdentity(fusedHit))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成查询命中的稳定身份键。
     *
     * @param queryArticleHit 查询命中
     * @return 身份键
     */
    private String hitIdentity(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        String sourcePathText = queryArticleHit.getSourcePaths() == null
                ? ""
                : String.join("|", queryArticleHit.getSourcePaths());
        return queryArticleHit.getEvidenceType()
                + "|"
                + nullToEmpty(queryArticleHit.getArticleKey())
                + "|"
                + nullToEmpty(queryArticleHit.getConceptId())
                + "|"
                + sourcePathText;
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
        state.setFallbackReason(answerPayload.getFallbackReason());
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
        state.setFallbackReason(rewrittenPayload.getFallbackReason());
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
                queryResponse.getDeepResearch(),
                queryResponse.getFallbackReason(),
                queryResponse.getCitationMarkers(),
                queryResponse.getStructuredEvidence()
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
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(
                buildRetrievalQueryContext(state),
                RETRIEVAL_CANDIDATE_LIMIT
        );
        RetrievalDispatchResult dispatchResult = retrievalDispatcher.dispatch(
                buildDispatchPlan(currentStrategy(state)),
                executionContext
        );
        for (Map.Entry<String, List<QueryArticleHit>> entry : dispatchResult.getChannelHits().entrySet()) {
            delta.putAll(saveDispatchedChannelHits(state, entry.getKey(), entry.getValue()));
        }
        delta.put(
                QueryGraphStateKeys.RETRIEVAL_CHANNEL_RUNS_REF,
                queryWorkingSetStore.saveRetrievalChannelRuns(state.getQueryId(), dispatchResult.getChannelRuns())
        );
        return delta;
    }

    /**
     * 构建固定顺序检索计划。
     *
     * @return 检索计划
     */
    private RetrievalDispatchPlan buildDispatchPlan(RetrievalStrategy retrievalStrategy) {
        QuerySearchProperties.RetrievalDispatchProperties dispatchProperties =
                querySearchProperties.getRetrievalDispatch();
        return new RetrievalDispatchPlan(List.of(
                new SupplierRetrievalChannel(
                        CHANNEL_FTS,
                        "lexical",
                        context -> ftsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_ARTICLE_CHUNK_FTS,
                        "lexical",
                        context -> articleChunkFtsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_REFKEY,
                        "lexical",
                        context -> refKeySearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_SOURCE,
                        "source",
                        context -> sourceSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_SOURCE_CHUNK_FTS,
                        "source",
                        context -> sourceChunkFtsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_FACT_CARD_FTS,
                        "fact_card",
                        context -> factCardFtsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_FACT_CARD_VECTOR,
                        "vector",
                        factCardVectorSearchService::search
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_CONTRIBUTION,
                        "graph",
                        context -> contributionSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_GRAPH,
                        "graph",
                        context -> graphSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_ARTICLE_VECTOR,
                        "vector",
                        vectorSearchService::search
                ),
                new SupplierRetrievalChannel(
                        CHANNEL_CHUNK_VECTOR,
                        "vector",
                        chunkVectorSearchService::search
                )
        ),
                retrievalStrategy != null && retrievalStrategy.isParallelEnabled(),
                dispatchProperties.getMaxConcurrency(),
                dispatchProperties.getMaxConcurrencyPerGroup(),
                dispatchProperties.getChannelTimeoutMillis(),
                dispatchProperties.getTotalDeadlineMillis()
        );
    }

    /**
     * 基于图状态构建检索查询上下文。
     *
     * @param state 图状态
     * @return 检索查询上下文
     */
    private RetrievalQueryContext buildRetrievalQueryContext(QueryGraphState state) {
        QueryRewriteResult queryRewriteResult = QueryRewriteResult.unchanged(readRetrievalQuestion(state));
        return new RetrievalQueryContext(
                state.getQueryId(),
                state.getQuestion(),
                state.getNormalizedQuestion(),
                queryRewriteResult,
                readQueryIntent(state),
                readAnswerShape(state.getAnswerShape()),
                currentStrategy(state)
        );
    }

    /**
     * 保存统一 dispatcher 已处理过的通道命中。
     *
     * @param state 图状态
     * @param channel 通道名称
     * @param hits 通道命中
     * @return 状态增量
     */
    private Map<String, Object> saveDispatchedChannelHits(
            QueryGraphState state,
            String channel,
            List<QueryArticleHit> hits
    ) {
        List<QueryArticleHit> safeHits = hits == null ? List.of() : hits;
        return saveChannelHitsRef(state, channel, safeHits);
    }

    /**
     * 保存通道命中并返回对应 working set 引用。
     *
     * @param state 图状态
     * @param channel 通道名称
     * @param hits 通道命中
     * @return 状态增量
     */
    private Map<String, Object> saveChannelHitsRef(QueryGraphState state, String channel, List<QueryArticleHit> hits) {
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
        else if (CHANNEL_FACT_CARD_FTS.equals(channel)) {
            delta.put(QueryGraphStateKeys.FACT_CARD_HITS_REF, ref);
        }
        else if (CHANNEL_FACT_CARD_VECTOR.equals(channel)) {
            delta.put(QueryGraphStateKeys.FACT_CARD_VECTOR_HITS_REF, ref);
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

    /**
     * 读取当前查询意图。
     *
     * @param state 图状态
     * @return 查询意图
     */
    private QueryIntent readQueryIntent(QueryGraphState state) {
        if (state == null || state.getQueryIntent() == null || state.getQueryIntent().isBlank()) {
            return QueryIntent.GENERAL;
        }
        try {
            return QueryIntent.valueOf(state.getQueryIntent());
        }
        catch (IllegalArgumentException exception) {
            return QueryIntent.GENERAL;
        }
    }

    private Map<String, List<QueryArticleHit>> loadChannelHits(QueryGraphState state) {
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(CHANNEL_FTS, queryWorkingSetStore.loadHits(state.getFtsHitsRef()));
        channelHits.put(CHANNEL_ARTICLE_CHUNK_FTS, queryWorkingSetStore.loadHits(state.getArticleChunkHitsRef()));
        channelHits.put(CHANNEL_REFKEY, queryWorkingSetStore.loadHits(state.getRefkeyHitsRef()));
        channelHits.put(CHANNEL_SOURCE, queryWorkingSetStore.loadHits(state.getSourceHitsRef()));
        channelHits.put(CHANNEL_SOURCE_CHUNK_FTS, queryWorkingSetStore.loadHits(state.getSourceChunkHitsRef()));
        channelHits.put(CHANNEL_FACT_CARD_FTS, queryWorkingSetStore.loadHits(state.getFactCardHitsRef()));
        channelHits.put(CHANNEL_FACT_CARD_VECTOR, queryWorkingSetStore.loadHits(state.getFactCardVectorHitsRef()));
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

    private RetrievalStrategy currentStrategy(QueryGraphState state) {
        RetrievalStrategy retrievalStrategy = queryWorkingSetStore.loadRetrievalStrategy(state.getRetrievalStrategyRef());
        if (retrievalStrategy != null) {
            return retrievalStrategy;
        }
        return retrievalStrategyResolver.resolve(
                effectiveRetrievalQuestion(state),
                readQueryIntent(state.getQueryIntent()),
                readAnswerShape(state.getAnswerShape()),
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

    /**
     * 读取当前答案形态。
     *
     * @param answerShape 答案形态字符串
     * @return 答案形态
     */
    private AnswerShape readAnswerShape(String answerShape) {
        if (answerShape == null || answerShape.isBlank()) {
            return AnswerShape.GENERAL;
        }
        try {
            return AnswerShape.valueOf(answerShape);
        }
        catch (IllegalArgumentException exception) {
            return AnswerShape.GENERAL;
        }
    }

    /**
     * 判断当前问题是否属于精确查值/精确结论类问题。
     *
     * @param question 用户问题
     * @return 精确查值题返回 true
     */
    private boolean looksLikeExactLookupQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("几")
                || normalizedQuestion.contains("列出")
                || normalizedQuestion.contains("有哪些")
                || normalizedQuestion.contains("哪些")
                || normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("规范")
                || normalizedQuestion.contains("规则")
                || normalizedQuestion.contains("命名")
                || normalizedQuestion.contains("格式")
                || normalizedQuestion.contains("结论")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("归属")
                || normalizedQuestion.contains("对应")
                || normalizedQuestion.contains("是否一致")
                || normalizedQuestion.contains("是否生效")
                || normalizedQuestion.contains("是否启用");
    }

    /**
     * 判断问题中是否存在必须精确命中的路径、配置键或字段键。
     *
     * @param question 用户问题
     * @return 存在精确 token 返回 true
     */
    private boolean hasStrictExactToken(String question) {
        List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        for (String highSignalToken : highSignalTokens) {
            if (highSignalToken.contains("_")
                    || highSignalToken.contains("-")
                    || highSignalToken.contains("=")
                    || highSignalToken.contains("/")
                    || highSignalToken.contains(".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 转成小写空安全文本。
     *
     * @param value 原始文本
     * @return 小写文本
     */
    private String lowerCase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * 返回空安全文本。
     *
     * @param value 原始文本
     * @return 空安全文本
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
