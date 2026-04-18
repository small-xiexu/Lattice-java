package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleChunkVectorJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chunk 向量检索服务
 *
 * 职责：基于 article_chunk_vector_index 执行可降级的 chunk 级近邻召回
 *
 * @author xiexu
 */
@Slf4j
@Service
@Profile("jdbc")
public class ChunkVectorSearchService {

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository;

    private final ConfiguredVectorEmbeddingService configuredVectorEmbeddingService;

    private final ChunkToArticleAggregator chunkToArticleAggregator;

    /**
     * 创建 chunk 向量检索服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleChunkVectorJdbcRepository chunk 向量仓储
     * @param configuredVectorEmbeddingService embedding 服务
     * @param chunkToArticleAggregator chunk 聚合器
     */
    @Autowired
    public ChunkVectorSearchService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository,
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService,
            ChunkToArticleAggregator chunkToArticleAggregator
    ) {
        this.querySearchProperties = querySearchProperties;
        this.searchCapabilityService = searchCapabilityService;
        this.articleChunkVectorJdbcRepository = articleChunkVectorJdbcRepository;
        this.configuredVectorEmbeddingService = configuredVectorEmbeddingService;
        this.chunkToArticleAggregator = chunkToArticleAggregator;
    }

    /**
     * 创建默认禁用的 chunk 向量检索服务。
     */
    public ChunkVectorSearchService() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled(), null, null, null);
    }

    /**
     * 执行 chunk 级向量检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 聚合后的 article 命中
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (!isQueryAvailable()) {
            return List.of();
        }

        try {
            float[] embedding = configuredVectorEmbeddingService.embed(question);
            if (!hasExpectedDimensions(embedding)) {
                return List.of();
            }
            List<ArticleChunkVectorHit> chunkHits = articleChunkVectorJdbcRepository.searchNearestNeighbors(embedding, limit);
            return chunkToArticleAggregator.aggregate(chunkHits);
        }
        catch (RuntimeException ex) {
            log.warn("Chunk vector search fallback because embedding or query failed", ex);
            return List.of();
        }
    }

    /**
     * 返回 chunk 向量检索当前是否可用。
     *
     * @return 是否可用
     */
    public boolean isQueryAvailable() {
        return querySearchProperties.getVector().isEnabled()
                && configuredVectorEmbeddingService != null
                && configuredVectorEmbeddingService.isAvailable()
                && articleChunkVectorJdbcRepository != null
                && chunkToArticleAggregator != null
                && searchCapabilityService.supportsVectorType();
    }

    private boolean hasExpectedDimensions(float[] embedding) {
        int expectedDimensions = configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        if (expectedDimensions <= 0) {
            return true;
        }
        if (embedding != null && embedding.length == expectedDimensions) {
            return true;
        }
        int actualDimensions = embedding == null ? 0 : embedding.length;
        log.warn(
                "Chunk vector search fallback because embedding dimensions {} do not match expected {}",
                actualDimensions,
                expectedDimensions
        );
        return false;
    }
}
