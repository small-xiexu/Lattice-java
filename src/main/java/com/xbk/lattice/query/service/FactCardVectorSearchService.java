package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardVectorJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fact Card 向量检索服务
 *
 * 职责：基于 fact_card_vector_index 执行可降级的结构化证据卡近邻召回
 *
 * @author xiexu
 */
@Slf4j
@Service
public class FactCardVectorSearchService {

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final FactCardVectorJdbcRepository factCardVectorJdbcRepository;

    private final ConfiguredVectorEmbeddingService configuredVectorEmbeddingService;

    /**
     * 创建 Fact Card 向量检索服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param factCardVectorJdbcRepository fact card 向量仓储
     * @param configuredVectorEmbeddingService embedding 服务
     */
    public FactCardVectorSearchService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            FactCardVectorJdbcRepository factCardVectorJdbcRepository,
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService
    ) {
        this.querySearchProperties = querySearchProperties == null ? new QuerySearchProperties() : querySearchProperties;
        this.searchCapabilityService = searchCapabilityService == null
                ? SearchCapabilityService.disabled()
                : searchCapabilityService;
        this.factCardVectorJdbcRepository = factCardVectorJdbcRepository;
        this.configuredVectorEmbeddingService = configuredVectorEmbeddingService;
    }

    /**
     * 创建默认禁用的 Fact Card 向量检索服务。
     */
    public FactCardVectorSearchService() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled(), null, null);
    }

    /**
     * 使用统一检索执行上下文执行 fact card 向量近邻检索。
     *
     * @param executionContext 检索执行上下文
     * @return fact card 命中
     */
    public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
        if (executionContext == null) {
            return List.of();
        }
        return searchWithEmbedding(
                executionContext.getRetrievalQuestion(),
                executionContext.getLimit(),
                executionContext
        );
    }

    /**
     * 执行 fact card 向量近邻检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @param executionContext 检索执行上下文
     * @return fact card 命中
     */
    private List<QueryArticleHit> searchWithEmbedding(
            String question,
            int limit,
            RetrievalExecutionContext executionContext
    ) {
        if (!isQueryAvailable()) {
            return List.of();
        }

        float[] embedding = executionContext.getOrCreateQueryEmbedding(configuredVectorEmbeddingService);
        if (!hasExpectedDimensions(embedding)) {
            throw new IllegalStateException(buildDimensionMismatchMessage(embedding));
        }
        List<QueryArticleHit> hits = factCardVectorJdbcRepository.searchNearestNeighbors(embedding, limit);
        return applyReviewUsagePolicy(hits);
    }

    /**
     * 返回 fact card 向量检索当前是否可用。
     *
     * @return 是否可用
     */
    public boolean isQueryAvailable() {
        return querySearchProperties.getVector().isEnabled()
                && configuredVectorEmbeddingService != null
                && configuredVectorEmbeddingService.isAvailable()
                && factCardVectorJdbcRepository != null
                && searchCapabilityService.supportsVectorType()
                && searchCapabilityService.hasFactCardVectorIndex();
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
            return embedding != null && embedding.length > 0;
        }
        if (embedding != null && embedding.length == expectedDimensions) {
            return true;
        }
        int actualDimensions = embedding == null ? 0 : embedding.length;
        log.warn(
                "Fact card vector search fallback because embedding dimensions {} do not match expected {}",
                actualDimensions,
                expectedDimensions
        );
        return false;
    }

    /**
     * 构建维度不匹配摘要。
     *
     * @param embedding 查询向量
     * @return 摘要
     */
    private String buildDimensionMismatchMessage(float[] embedding) {
        int expectedDimensions = configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        int actualDimensions = embedding == null ? 0 : embedding.length;
        return "Fact card vector embedding dimensions " + actualDimensions
                + " do not match expected " + expectedDimensions;
    }

    /**
     * 应用 Fact Card 审查状态使用策略。
     *
     * @param hits 原始命中
     * @return 策略处理后的命中
     */
    private List<QueryArticleHit> applyReviewUsagePolicy(List<QueryArticleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<QueryArticleHit> adjustedHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit hit : hits) {
            if (hit == null || !FactCardReviewUsagePolicy.allowsQueryCandidate(hit.getReviewStatus())) {
                continue;
            }
            adjustedHits.add(new QueryArticleHit(
                    hit.getEvidenceType(),
                    hit.getSourceId(),
                    hit.getArticleKey(),
                    hit.getConceptId(),
                    hit.getTitle(),
                    hit.getContent(),
                    hit.getMetadataJson(),
                    hit.getReviewStatus(),
                    hit.getSourcePaths(),
                    FactCardReviewUsagePolicy.adjustScore(hit.getScore(), hit.getReviewStatus())
            ));
        }
        return adjustedHits;
    }
}
