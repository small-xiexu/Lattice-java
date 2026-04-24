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

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final ContributionSearchService contributionSearchService;

    private final GraphSearchService graphSearchService;

    private final VectorSearchService vectorSearchService;

    private final ChunkVectorSearchService chunkVectorSearchService;

    private final RrfFusionService rrfFusionService;

    private final QueryRetrievalSettingsService queryRetrievalSettingsService;

    /**
     * 创建知识检索服务。
     *
     * @param ftsSearchService 文章 FTS 检索
     * @param refKeySearchService referential keywords 检索
     * @param sourceSearchService 源文件检索
     * @param contributionSearchService contribution 检索
     * @param graphSearchService 图谱检索
     * @param vectorSearchService 向量检索
     * @param rrfFusionService RRF 融合服务
     */
    @Autowired
    public KnowledgeSearchService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            GraphSearchService graphSearchService,
            VectorSearchService vectorSearchService,
            ChunkVectorSearchService chunkVectorSearchService,
            RrfFusionService rrfFusionService,
            QueryRetrievalSettingsService queryRetrievalSettingsService
    ) {
        this.ftsSearchService = ftsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.contributionSearchService = contributionSearchService;
        this.graphSearchService = graphSearchService;
        this.vectorSearchService = vectorSearchService;
        this.chunkVectorSearchService = chunkVectorSearchService;
        this.rrfFusionService = rrfFusionService;
        this.queryRetrievalSettingsService = queryRetrievalSettingsService;
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
                refKeySearchService,
                sourceSearchService,
                contributionSearchService,
                new GraphSearchService(),
                new VectorSearchService(),
                new ChunkVectorSearchService(),
                rrfFusionService,
                new QueryRetrievalSettingsService()
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
        int safeLimit = limit <= 0 ? 5 : limit;
        List<QueryArticleHit> ftsHits = ftsSearchService.search(question, safeLimit);
        List<QueryArticleHit> refKeyHits = refKeySearchService.search(question, safeLimit);
        List<QueryArticleHit> sourceHits = sourceSearchService.search(question, safeLimit);
        List<QueryArticleHit> contributionHits = contributionSearchService.search(question, safeLimit);
        List<QueryArticleHit> graphHits = graphSearchService.search(question, safeLimit);
        List<QueryArticleHit> articleVectorHits = vectorSearchService.search(question, safeLimit);
        List<QueryArticleHit> chunkVectorHits = chunkVectorSearchService.search(question, safeLimit);
        QueryRetrievalSettingsState settings = queryRetrievalSettingsService == null
                ? new QueryRetrievalSettingsService().defaultState()
                : queryRetrievalSettingsService.getCurrentState();
        return rrfFusionService.fuse(
                Map.of(
                        "fts", ftsHits,
                        "refkey", refKeyHits,
                        "source", sourceHits,
                        "contribution", contributionHits,
                        "graph", graphHits,
                        "article_vector", articleVectorHits,
                        "chunk_vector", chunkVectorHits
                ),
                Map.of(
                        "fts", settings.getFtsWeight(),
                        "refkey", settings.getFtsWeight(),
                        "source", settings.getSourceWeight(),
                        "contribution", settings.getContributionWeight(),
                        "graph", settings.getGraphWeight(),
                        "article_vector", settings.getArticleVectorWeight(),
                        "chunk_vector", settings.getChunkVectorWeight()
                ),
                safeLimit,
                settings.getRrfK()
        );
    }
}
