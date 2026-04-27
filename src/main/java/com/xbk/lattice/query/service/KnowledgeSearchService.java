package com.xbk.lattice.query.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识检索服务
 *
 * 职责：为 MCP 与治理侧提供不生成答案的多路融合检索能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class KnowledgeSearchService {

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

    /**
     * 创建知识检索服务。
     *
     * @param ftsSearchService 文章 FTS 检索
     * @param articleChunkFtsSearchService 文章分块 FTS 检索
     * @param refKeySearchService referential keywords 检索
     * @param sourceSearchService 源文件检索
     * @param sourceChunkFtsSearchService 源文件分块 FTS 检索
     * @param contributionSearchService contribution 检索
     * @param graphSearchService 图谱检索
     * @param vectorSearchService 向量检索
     * @param rrfFusionService RRF 融合服务
     */
    @Autowired
    public KnowledgeSearchService(
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
            RetrievalAuditService retrievalAuditService
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
    }

    /**
     * 创建知识检索服务。
     *
     * @param ftsSearchService 文章 FTS 检索
     * @param refKeySearchService referential keywords 检索
     * @param sourceSearchService 源文件检索
     * @param contributionSearchService contribution 检索
     * @param rrfFusionService RRF 融合服务
     */
    public KnowledgeSearchService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            RrfFusionService rrfFusionService
    ) {
        this(
                ftsSearchService,
                new ArticleChunkFtsSearchService(null),
                refKeySearchService,
                sourceSearchService,
                new SourceChunkFtsSearchService(null),
                contributionSearchService,
                new GraphSearchService(),
                new VectorSearchService(),
                new ChunkVectorSearchService(),
                rrfFusionService,
                new QueryRetrievalSettingsService(),
                new QueryRewriteService(),
                new QueryIntentClassifier(),
                new RetrievalStrategyResolver(),
                null
        );
    }

    /**
     * 执行多路融合检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 融合命中列表
     */
    public List<QueryArticleHit> search(String question, int limit) {
        RetrievalQueryContext retrievalQueryContext = prepareContext(null, question);
        return search(retrievalQueryContext, limit);
    }

    /**
     * 执行多路融合检索。
     *
     * @param retrievalQueryContext 检索上下文
     * @param limit 返回数量
     * @return 融合命中列表
     */
    public List<QueryArticleHit> search(RetrievalQueryContext retrievalQueryContext, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        RetrievalStrategy retrievalStrategy = retrievalQueryContext.getRetrievalStrategy();
        String retrievalQuestion = retrievalQueryContext.getRetrievalQuestion();
        List<QueryArticleHit> ftsHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_FTS,
                retrievalStrategy,
                () -> ftsSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> articleChunkHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
                retrievalStrategy,
                () -> articleChunkFtsSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> refKeyHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_REFKEY,
                retrievalStrategy,
                () -> refKeySearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> sourceHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_SOURCE,
                retrievalStrategy,
                () -> sourceSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> sourceChunkHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                retrievalStrategy,
                () -> sourceChunkFtsSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> contributionHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_CONTRIBUTION,
                retrievalStrategy,
                () -> contributionSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> graphHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_GRAPH,
                retrievalStrategy,
                () -> graphSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> articleVectorHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                retrievalStrategy,
                () -> vectorSearchService.search(retrievalQuestion, safeLimit)
        );
        List<QueryArticleHit> chunkVectorHits = searchChannel(
                RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR,
                retrievalStrategy,
                () -> chunkVectorSearchService.search(retrievalQuestion, safeLimit)
        );
        Map<String, List<QueryArticleHit>> channelHits = Map.of(
                "fts", ftsHits,
                "article_chunk_fts", articleChunkHits,
                "refkey", refKeyHits,
                "source", sourceHits,
                "source_chunk_fts", sourceChunkHits,
                "contribution", contributionHits,
                "graph", graphHits,
                "article_vector", articleVectorHits,
                "chunk_vector", chunkVectorHits
        );
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                channelHits,
                retrievalStrategy.getChannelWeights(),
                safeLimit,
                retrievalStrategy.getRrfK()
        );
        if (retrievalAuditService != null) {
            retrievalAuditService.persist(retrievalQueryContext, channelHits, fusedHits);
        }
        return fusedHits;
    }

    /**
     * 准备检索上下文。
     *
     * @param queryId 查询标识
     * @param question 查询问题
     * @return 检索上下文
     */
    public RetrievalQueryContext prepareContext(String queryId, String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        QueryRetrievalSettingsState settings = currentSettings();
        QueryRewriteResult queryRewriteResult = settings.isRewriteEnabled()
                ? queryRewriteService.rewrite(queryId, normalizedQuestion)
                : QueryRewriteResult.unchanged(normalizedQuestion);
        String retrievalQuestion = queryRewriteResult.getRewrittenQuestion();
        QueryIntent queryIntent = queryIntentClassifier.classify(retrievalQuestion);
        RetrievalStrategy retrievalStrategy = retrievalStrategyResolver.resolve(
                retrievalQuestion,
                queryIntent,
                settings
        );
        return new RetrievalQueryContext(
                queryId,
                question,
                normalizedQuestion,
                queryRewriteResult,
                queryIntent,
                retrievalStrategy
        );
    }

    /**
     * 返回当前检索配置。
     *
     * @return 当前检索配置
     */
    private QueryRetrievalSettingsState currentSettings() {
        return queryRetrievalSettingsService == null
                ? new QueryRetrievalSettingsService().defaultState()
                : queryRetrievalSettingsService.getCurrentState();
    }

    /**
     * 按策略执行单通道检索。
     *
     * @param channel 通道名
     * @param retrievalStrategy 检索策略
     * @param supplier 检索执行器
     * @return 通道命中
     */
    private List<QueryArticleHit> searchChannel(
            String channel,
            RetrievalStrategy retrievalStrategy,
            java.util.function.Supplier<List<QueryArticleHit>> supplier
    ) {
        if (retrievalStrategy == null || !retrievalStrategy.isChannelEnabled(channel)) {
            return List.of();
        }
        return supplier.get();
    }
}
