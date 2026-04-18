package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量检索服务
 *
 * 职责：基于 pgvector 执行可降级的文章近邻召回
 *
 * @author xiexu
 */
@Slf4j
@Service
@Profile("jdbc")
public class VectorSearchService {

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final ArticleVectorJdbcRepository articleVectorJdbcRepository;

    private final ConfiguredVectorEmbeddingService configuredVectorEmbeddingService;

    /**
     * 创建向量检索服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleVectorJdbcRepository 文章向量索引仓储
     * @param configuredVectorEmbeddingService 可配置 embedding 服务
     */
    @Autowired
    public VectorSearchService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleVectorJdbcRepository articleVectorJdbcRepository,
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService
    ) {
        this.querySearchProperties = querySearchProperties;
        this.searchCapabilityService = searchCapabilityService;
        this.articleVectorJdbcRepository = articleVectorJdbcRepository;
        this.configuredVectorEmbeddingService = configuredVectorEmbeddingService;
    }

    /**
     * 创建向量检索服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleVectorJdbcRepository 文章向量索引仓储
     * @param embeddingModel embedding 模型
     */
    public VectorSearchService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleVectorJdbcRepository articleVectorJdbcRepository,
            EmbeddingModel embeddingModel
    ) {
        this(
                querySearchProperties,
                searchCapabilityService,
                articleVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, embeddingModel)
        );
    }

    /**
     * 创建默认禁用的向量检索服务。
     */
    public VectorSearchService() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled(), null, (EmbeddingModel) null);
    }

    /**
     * 执行向量近邻检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 文章命中
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
            return articleVectorJdbcRepository.searchNearestNeighbors(embedding, limit);
        }
        catch (RuntimeException ex) {
            log.warn("Vector search fallback because embedding or query failed", ex);
            return List.of();
        }
    }

    /**
     * 返回向量检索当前是否可用。
     *
     * @return 是否可用
     */
    public boolean isQueryAvailable() {
        return querySearchProperties.getVector().isEnabled()
                && articleVectorJdbcRepository != null
                && configuredVectorEmbeddingService != null
                && configuredVectorEmbeddingService.isAvailable()
                && searchCapabilityService.supportsVectorType()
                && searchCapabilityService.hasArticleVectorIndex();
    }

    /**
     * 校验查询向量维度是否与当前索引基线一致。
     *
     * @param embedding 查询向量
     * @return 是否匹配
     */
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
                "Vector search fallback because embedding dimensions {} do not match expected {}",
                actualDimensions,
                expectedDimensions
        );
        return false;
    }
}
