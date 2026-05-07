package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
public class KnowledgeSearchService {

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

    private final QueryRewriteService queryRewriteService;

    private final QueryIntentClassifier queryIntentClassifier;

    private final AnswerShapeClassifier answerShapeClassifier;

    private final RetrievalStrategyResolver retrievalStrategyResolver;

    private final RetrievalAuditService retrievalAuditService;

    private final QuerySearchProperties querySearchProperties;

    private final RetrievalDispatcher retrievalDispatcher = new RetrievalDispatcher();

    /**
     * 创建知识检索服务。
     *
     * @param ftsSearchService 文章 FTS 检索
     * @param articleChunkFtsSearchService 文章分块 FTS 检索
     * @param refKeySearchService referential keywords 检索
     * @param sourceSearchService 源文件检索
     * @param sourceChunkFtsSearchService 源文件分块 FTS 检索
     * @param factCardFtsSearchService Fact Card FTS 检索
     * @param factCardVectorSearchService Fact Card 向量检索
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
            FactCardFtsSearchService factCardFtsSearchService,
            FactCardVectorSearchService factCardVectorSearchService,
            ContributionSearchService contributionSearchService,
            GraphSearchService graphSearchService,
            VectorSearchService vectorSearchService,
            ChunkVectorSearchService chunkVectorSearchService,
            RrfFusionService rrfFusionService,
            QueryRetrievalSettingsService queryRetrievalSettingsService,
            QueryRewriteService queryRewriteService,
            QueryIntentClassifier queryIntentClassifier,
            AnswerShapeClassifier answerShapeClassifier,
            RetrievalStrategyResolver retrievalStrategyResolver,
            RetrievalAuditService retrievalAuditService,
            QuerySearchProperties querySearchProperties
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
        this.queryRewriteService = queryRewriteService == null ? new QueryRewriteService() : queryRewriteService;
        this.queryIntentClassifier = queryIntentClassifier == null ? new QueryIntentClassifier() : queryIntentClassifier;
        this.answerShapeClassifier = answerShapeClassifier == null ? new AnswerShapeClassifier() : answerShapeClassifier;
        this.retrievalStrategyResolver = retrievalStrategyResolver == null
                ? new RetrievalStrategyResolver()
                : retrievalStrategyResolver;
        this.retrievalAuditService = retrievalAuditService;
        this.querySearchProperties = querySearchProperties == null
                ? new QuerySearchProperties()
                : querySearchProperties;
    }

    /**
     * 创建知识检索服务。
     *
     * @param ftsSearchService 文章 FTS 检索
     * @param articleChunkFtsSearchService 文章分块 FTS 检索
     * @param refKeySearchService referential keywords 检索
     * @param sourceSearchService 源文件检索
     * @param sourceChunkFtsSearchService 源文件分块 FTS 检索
     * @param factCardFtsSearchService Fact Card FTS 检索
     * @param factCardVectorSearchService Fact Card 向量检索
     * @param contributionSearchService contribution 检索
     * @param graphSearchService 图谱检索
     * @param vectorSearchService 向量检索
     * @param chunkVectorSearchService Chunk 向量检索
     * @param rrfFusionService RRF 融合服务
     * @param queryRetrievalSettingsService 检索配置服务
     * @param queryRewriteService Query 改写服务
     * @param queryIntentClassifier Query 意图分类器
     * @param answerShapeClassifier 答案形态分类器
     * @param retrievalStrategyResolver 检索策略解析器
     * @param retrievalAuditService 检索审计服务
     */
    public KnowledgeSearchService(
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
            QueryRewriteService queryRewriteService,
            QueryIntentClassifier queryIntentClassifier,
            AnswerShapeClassifier answerShapeClassifier,
            RetrievalStrategyResolver retrievalStrategyResolver,
            RetrievalAuditService retrievalAuditService
    ) {
        this(
                ftsSearchService,
                articleChunkFtsSearchService,
                refKeySearchService,
                sourceSearchService,
                sourceChunkFtsSearchService,
                factCardFtsSearchService,
                factCardVectorSearchService,
                contributionSearchService,
                graphSearchService,
                vectorSearchService,
                chunkVectorSearchService,
                rrfFusionService,
                queryRetrievalSettingsService,
                queryRewriteService,
                queryIntentClassifier,
                answerShapeClassifier,
                retrievalStrategyResolver,
                retrievalAuditService,
                new QuerySearchProperties()
        );
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
                new FactCardFtsSearchService(null),
                new FactCardVectorSearchService(),
                contributionSearchService,
                new GraphSearchService(),
                new VectorSearchService(),
                new ChunkVectorSearchService(),
                rrfFusionService,
                new QueryRetrievalSettingsService(),
                new QueryRewriteService(),
                new QueryIntentClassifier(),
                new AnswerShapeClassifier(),
                new RetrievalStrategyResolver(),
                null,
                new QuerySearchProperties()
        );
    }

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
     * @param chunkVectorSearchService Chunk 向量检索
     * @param rrfFusionService RRF 融合服务
     * @param queryRetrievalSettingsService 检索配置服务
     * @param queryRewriteService Query 改写服务
     * @param queryIntentClassifier Query 意图分类器
     * @param answerShapeClassifier 答案形态分类器
     * @param retrievalStrategyResolver 检索策略解析器
     * @param retrievalAuditService 检索审计服务
     */
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
            AnswerShapeClassifier answerShapeClassifier,
            RetrievalStrategyResolver retrievalStrategyResolver,
            RetrievalAuditService retrievalAuditService
    ) {
        this(
                ftsSearchService,
                articleChunkFtsSearchService,
                refKeySearchService,
                sourceSearchService,
                sourceChunkFtsSearchService,
                new FactCardFtsSearchService(null),
                new FactCardVectorSearchService(),
                contributionSearchService,
                graphSearchService,
                vectorSearchService,
                chunkVectorSearchService,
                rrfFusionService,
                queryRetrievalSettingsService,
                queryRewriteService,
                queryIntentClassifier,
                answerShapeClassifier,
                retrievalStrategyResolver,
                retrievalAuditService,
                new QuerySearchProperties()
        );
    }

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
     * @param chunkVectorSearchService Chunk 向量检索
     * @param rrfFusionService RRF 融合服务
     * @param queryRetrievalSettingsService 检索配置服务
     * @param queryRewriteService Query 改写服务
     * @param queryIntentClassifier Query 意图分类器
     * @param retrievalStrategyResolver 检索策略解析器
     * @param retrievalAuditService 检索审计服务
     */
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
        this(
                ftsSearchService,
                articleChunkFtsSearchService,
                refKeySearchService,
                sourceSearchService,
                sourceChunkFtsSearchService,
                new FactCardFtsSearchService(null),
                new FactCardVectorSearchService(),
                contributionSearchService,
                graphSearchService,
                vectorSearchService,
                chunkVectorSearchService,
                rrfFusionService,
                queryRetrievalSettingsService,
                queryRewriteService,
                queryIntentClassifier,
                new AnswerShapeClassifier(),
                retrievalStrategyResolver,
                retrievalAuditService,
                new QuerySearchProperties()
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
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(retrievalQueryContext, safeLimit);
        RetrievalDispatchResult dispatchResult = retrievalDispatcher.dispatch(
                buildDispatchPlan(retrievalStrategy),
                executionContext
        );
        Map<String, List<QueryArticleHit>> channelHits = dispatchResult.getChannelHits();
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(channelHits, retrievalStrategy, safeLimit);
        fusedHits = applyReviewQualityGuardrail(fusedHits);
        if (retrievalAuditService != null) {
            retrievalAuditService.persist(retrievalQueryContext, dispatchResult, fusedHits);
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
        AnswerShape answerShape = answerShapeClassifier.classify(retrievalQuestion);
        RetrievalStrategy retrievalStrategy = retrievalStrategyResolver.resolve(
                retrievalQuestion,
                queryIntent,
                answerShape,
                settings
        );
        return new RetrievalQueryContext(
                queryId,
                question,
                normalizedQuestion,
                queryRewriteResult,
                queryIntent,
                answerShape,
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
     * 构建固定顺序检索计划。
     *
     * @return 检索计划
     */
    private RetrievalDispatchPlan buildDispatchPlan(RetrievalStrategy retrievalStrategy) {
        QuerySearchProperties.RetrievalDispatchProperties dispatchProperties =
                querySearchProperties.getRetrievalDispatch();
        return new RetrievalDispatchPlan(List.of(
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_FTS,
                        "lexical",
                        context -> ftsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
                        "lexical",
                        context -> articleChunkFtsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_REFKEY,
                        "lexical",
                        context -> refKeySearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_SOURCE,
                        "source",
                        context -> sourceSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                        "source",
                        context -> sourceChunkFtsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                        "fact_card",
                        context -> factCardFtsSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                        "vector",
                        factCardVectorSearchService::search
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_CONTRIBUTION,
                        "graph",
                        context -> contributionSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_GRAPH,
                        "graph",
                        context -> graphSearchService.search(context.getRetrievalQuestion(), context.getLimit())
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                        "vector",
                        vectorSearchService::search
                ),
                new SupplierRetrievalChannel(
                        RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR,
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
     * 对融合后的候选应用统一质量门禁，优先保留已通过审查的文章。
     *
     * @param fusedHits 融合候选
     * @return 门禁处理后的候选
     */
    private List<QueryArticleHit> applyReviewQualityGuardrail(List<QueryArticleHit> fusedHits) {
        if (fusedHits == null || fusedHits.isEmpty()) {
            return List.of();
        }
        List<QueryArticleHit> passedHits = new java.util.ArrayList<QueryArticleHit>();
        List<QueryArticleHit> nonPassedHits = new java.util.ArrayList<QueryArticleHit>();
        for (QueryArticleHit fusedHit : fusedHits) {
            if (isPassedArticleHit(fusedHit)) {
                passedHits.add(fusedHit);
                continue;
            }
            nonPassedHits.add(fusedHit);
        }
        if (passedHits.isEmpty()) {
            return fusedHits;
        }
        passedHits.addAll(nonPassedHits);
        return passedHits;
    }

    /**
     * 判断候选是否为已通过审查的文章命中。
     *
     * @param fusedHit 融合候选
     * @return 已通过审查返回 true
     */
    private boolean isPassedArticleHit(QueryArticleHit fusedHit) {
        if (fusedHit == null || fusedHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
            return false;
        }
        String reviewStatus = fusedHit.getReviewStatus();
        return reviewStatus != null && "passed".equalsIgnoreCase(reviewStatus.trim());
    }
}
