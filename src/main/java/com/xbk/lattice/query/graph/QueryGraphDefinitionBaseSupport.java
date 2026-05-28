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
import com.xbk.lattice.query.service.QueryTokenExtractor;
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
 * Query Graph 定义基础支持。
 *
 * 职责：承载 Graph 依赖、检索设置、命中缓存和通用状态读取工具。
 *
 * @author xiexu
 */
abstract class QueryGraphDefinitionBaseSupport {

    protected static final int TOP_K = 8;

    protected static final int RETRIEVAL_CANDIDATE_LIMIT = 16;

    protected static final int EXACT_LOOKUP_CONTEXT_LIMIT = 16;

    protected static final int EXACT_LOOKUP_SUPPORT_LIMIT = 6;

    protected static final String CHANNEL_FTS = RetrievalStrategyResolver.CHANNEL_FTS;

    protected static final String CHANNEL_ARTICLE_CHUNK_FTS = RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS;

    protected static final String CHANNEL_REFKEY = RetrievalStrategyResolver.CHANNEL_REFKEY;

    protected static final String CHANNEL_SOURCE = RetrievalStrategyResolver.CHANNEL_SOURCE;

    protected static final String CHANNEL_SOURCE_CHUNK_FTS = RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS;

    protected static final String CHANNEL_FACT_CARD_FTS = RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS;

    protected static final String CHANNEL_FACT_CARD_VECTOR = RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR;

    protected static final String CHANNEL_FACT_CARD_TERMINAL_FTS = RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS;

    protected static final String CHANNEL_CONTRIBUTION = RetrievalStrategyResolver.CHANNEL_CONTRIBUTION;

    protected static final String CHANNEL_GRAPH = RetrievalStrategyResolver.CHANNEL_GRAPH;

    protected static final String CHANNEL_ARTICLE_VECTOR = RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR;

    protected static final String CHANNEL_CHUNK_VECTOR = RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR;

    protected final FtsSearchService ftsSearchService;

    protected final ArticleChunkFtsSearchService articleChunkFtsSearchService;

    protected final RefKeySearchService refKeySearchService;

    protected final SourceSearchService sourceSearchService;

    protected final SourceChunkFtsSearchService sourceChunkFtsSearchService;

    protected final FactCardFtsSearchService factCardFtsSearchService;

    protected final FactCardVectorSearchService factCardVectorSearchService;

    protected final FactCardTerminalUnitFtsSearchService factCardTerminalUnitFtsSearchService;

    protected final ContributionSearchService contributionSearchService;

    protected final GraphSearchService graphSearchService;

    protected final VectorSearchService vectorSearchService;

    protected final ChunkVectorSearchService chunkVectorSearchService;

    protected final RrfFusionService rrfFusionService;

    protected final QueryRetrievalSettingsService queryRetrievalSettingsService;

    protected final QuerySearchProperties querySearchProperties;

    protected final QueryRewriteService queryRewriteService;

    protected final QueryIntentClassifier queryIntentClassifier;

    protected final AnswerShapeClassifier answerShapeClassifier;

    protected final RetrievalStrategyResolver retrievalStrategyResolver;

    protected final RetrievalAuditService retrievalAuditService;

    protected final AnswerGenerationService answerGenerationService;

    protected final QueryCacheStore queryCacheStore;

    protected final ReviewerAgent reviewerAgent;

    protected final QueryWorkingSetStore queryWorkingSetStore;

    protected final QueryGraphStateMapper queryGraphStateMapper;

    protected final QueryGraphConditions queryGraphConditions;

    protected final QueryFinalizationGraphFragment queryFinalizationGraphFragment;

    protected final RetrievalDispatcher retrievalDispatcher = new RetrievalDispatcher();

    /**
     * 创建问答图定义工厂。
     */
    protected QueryGraphDefinitionBaseSupport(
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
        this.ftsSearchService = ftsSearchService;
        this.articleChunkFtsSearchService = articleChunkFtsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.sourceChunkFtsSearchService = sourceChunkFtsSearchService;
        this.factCardFtsSearchService = factCardFtsSearchService;
        this.factCardVectorSearchService = factCardVectorSearchService == null
                ? new FactCardVectorSearchService()
                : factCardVectorSearchService;
        this.factCardTerminalUnitFtsSearchService = factCardTerminalUnitFtsSearchService == null
                ? new FactCardTerminalUnitFtsSearchService(null)
                : factCardTerminalUnitFtsSearchService;
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

    protected List<String> collectSourcePaths(List<QueryArticleHit> fusedHits) {
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (QueryArticleHit fusedHit : fusedHits) {
            sourcePaths.addAll(fusedHit.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }
    protected Map<String, Object> saveAllRetrievalHits(QueryGraphState state) {
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
    protected RetrievalDispatchPlan buildDispatchPlan(RetrievalStrategy retrievalStrategy) {
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
                        CHANNEL_FACT_CARD_TERMINAL_FTS,
                        "fact_card",
                        context -> factCardTerminalUnitFtsSearchService.search(
                                context.getRetrievalQuestion(),
                                context.getLimit()
                        )
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
    protected RetrievalQueryContext buildRetrievalQueryContext(QueryGraphState state) {
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
    protected Map<String, Object> saveDispatchedChannelHits(
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
    protected Map<String, Object> saveChannelHitsRef(QueryGraphState state, String channel, List<QueryArticleHit> hits) {
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
        else if (CHANNEL_FACT_CARD_TERMINAL_FTS.equals(channel)) {
            delta.put(QueryGraphStateKeys.FACT_CARD_TERMINAL_UNIT_HITS_REF, ref);
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
    protected QueryIntent readQueryIntent(QueryGraphState state) {
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
    protected Map<String, List<QueryArticleHit>> loadChannelHits(QueryGraphState state) {
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(CHANNEL_FTS, queryWorkingSetStore.loadHits(state.getFtsHitsRef()));
        channelHits.put(CHANNEL_ARTICLE_CHUNK_FTS, queryWorkingSetStore.loadHits(state.getArticleChunkHitsRef()));
        channelHits.put(CHANNEL_REFKEY, queryWorkingSetStore.loadHits(state.getRefkeyHitsRef()));
        channelHits.put(CHANNEL_SOURCE, queryWorkingSetStore.loadHits(state.getSourceHitsRef()));
        channelHits.put(CHANNEL_SOURCE_CHUNK_FTS, queryWorkingSetStore.loadHits(state.getSourceChunkHitsRef()));
        channelHits.put(CHANNEL_FACT_CARD_FTS, queryWorkingSetStore.loadHits(state.getFactCardHitsRef()));
        channelHits.put(CHANNEL_FACT_CARD_TERMINAL_FTS, queryWorkingSetStore.loadHits(state.getFactCardTerminalUnitHitsRef()));
        channelHits.put(CHANNEL_FACT_CARD_VECTOR, queryWorkingSetStore.loadHits(state.getFactCardVectorHitsRef()));
        channelHits.put(CHANNEL_CONTRIBUTION, queryWorkingSetStore.loadHits(state.getContributionHitsRef()));
        channelHits.put(CHANNEL_GRAPH, queryWorkingSetStore.loadHits(state.getGraphHitsRef()));
        channelHits.put(CHANNEL_ARTICLE_VECTOR, queryWorkingSetStore.loadHits(state.getArticleVectorHitsRef()));
        channelHits.put(CHANNEL_CHUNK_VECTOR, queryWorkingSetStore.loadHits(state.getChunkVectorHitsRef()));
        return channelHits;
    }
    protected QueryRetrievalSettingsState retrievalSettings() {
        return queryRetrievalSettingsService == null
                ? new QueryRetrievalSettingsService().defaultState()
                : queryRetrievalSettingsService.getCurrentState();
    }
    protected RetrievalStrategy currentStrategy(QueryGraphState state) {
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
    protected String readRetrievalQuestion(QueryGraphState state) {
        RetrievalStrategy retrievalStrategy = currentStrategy(state);
        if (retrievalStrategy.getRetrievalQuestion() != null && !retrievalStrategy.getRetrievalQuestion().isBlank()) {
            return retrievalStrategy.getRetrievalQuestion();
        }
        return effectiveRetrievalQuestion(state);
    }
    protected String effectiveRetrievalQuestion(QueryGraphState state) {
        if (state.getRewrittenQuestion() != null && !state.getRewrittenQuestion().isBlank()) {
            return state.getRewrittenQuestion();
        }
        if (state.getNormalizedQuestion() != null && !state.getNormalizedQuestion().isBlank()) {
            return state.getNormalizedQuestion();
        }
        return state.getQuestion();
    }
    protected boolean isRewriteApplied(QueryGraphState state) {
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
    protected QueryIntent readQueryIntent(String queryIntent) {
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
    protected AnswerShape readAnswerShape(String answerShape) {
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
    protected boolean looksLikeExactLookupQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return !QueryTokenExtractor.extractExactIdentifierTokens(question).isEmpty()
                || normalizedQuestion.matches("(?s).*\\d+.*")
                || normalizedQuestion.contains("count")
                || normalizedQuestion.contains("value")
                || normalizedQuestion.contains("status")
                || normalizedQuestion.contains("state")
                || normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("url")
                || normalizedQuestion.contains("config")
                || normalizedQuestion.contains("rule")
                || normalizedQuestion.contains("policy");
    }
    /**
     * 判断问题中是否存在必须精确命中的路径、配置键或字段键。
     *
     * @param question 用户问题
     * @return 存在精确 token 返回 true
     */
    protected boolean hasStrictExactToken(String question) {
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
    protected String lowerCase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
    /**
     * 返回空安全文本。
     *
     * @param value 原始文本
     * @return 空安全文本
     */
    protected String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
