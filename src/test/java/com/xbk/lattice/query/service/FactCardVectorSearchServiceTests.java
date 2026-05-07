package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardVectorJdbcRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FactCardVectorSearchService 测试
 *
 * 职责：验证 fact card 向量检索的命中转换与降级保护
 *
 * @author xiexu
 */
class FactCardVectorSearchServiceTests {

    /**
     * 验证向量能力可用时，会返回 FACT_CARD 命中。
     */
    @Test
    void shouldSearchFactCardVectorHitsWhenAvailable() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        CapturingFactCardVectorJdbcRepository factCardVectorJdbcRepository =
                new CapturingFactCardVectorJdbcRepository(false);
        FactCardVectorSearchService factCardVectorSearchService = new FactCardVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.21F, 4)))
        );

        List<QueryArticleHit> hits = factCardVectorSearchService.search(executionContext("巡检项目有哪些"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.FACT_CARD);
        assertThat(hits.get(0).getArticleKey()).isEqualTo("fc:vector-hit");
        assertThat(factCardVectorJdbcRepository.getLastEmbedding()).containsExactly(createEmbedding(0.21F, 4));
        assertThat(factCardVectorJdbcRepository.getLastLimit()).isEqualTo(3);
    }

    /**
     * 验证向量能力不可用时，会自动跳过。
     */
    @Test
    void shouldSkipWhenVectorCapabilityUnavailable() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        FactCardVectorSearchService factCardVectorSearchService = new FactCardVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(false, true),
                new CapturingFactCardVectorJdbcRepository(false),
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.21F, 4)))
        );

        List<QueryArticleHit> hits = factCardVectorSearchService.search(executionContext("巡检项目有哪些"));

        assertThat(hits).isEmpty();
    }

    /**
     * 验证查询 embedding 维度不匹配时，会把异常交给 dispatcher 记录通道失败。
     */
    @Test
    void shouldThrowWhenEmbeddingDimensionsMismatch() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        CapturingFactCardVectorJdbcRepository factCardVectorJdbcRepository =
                new CapturingFactCardVectorJdbcRepository(false);
        FactCardVectorSearchService factCardVectorSearchService = new FactCardVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.21F, 3)))
        );

        assertThatThrownBy(() -> factCardVectorSearchService.search(executionContext("巡检项目有哪些")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimensions");
        assertThat(factCardVectorJdbcRepository.getLastEmbedding()).isNull();
    }

    /**
     * 验证向量仓储异常时，会把异常交给 dispatcher 记录通道失败。
     */
    @Test
    void shouldThrowWhenRepositorySearchFails() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        FactCardVectorSearchService factCardVectorSearchService = new FactCardVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                new CapturingFactCardVectorJdbcRepository(true),
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.21F, 4)))
        );

        assertThatThrownBy(() -> factCardVectorSearchService.search(executionContext("巡检项目有哪些")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fact card vector query failed");
    }

    /**
     * 验证向量检索会过滤 conflict 卡并保留 valid 卡。
     */
    @Test
    void shouldFilterConflictFactCardVectorHits() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        CapturingFactCardVectorJdbcRepository factCardVectorJdbcRepository =
                new CapturingFactCardVectorJdbcRepository(false, List.of(
                        factCardHit("fc:conflict", "conflict", 0.99D),
                        factCardHit("fc:valid", "valid", 0.88D)
                ));
        FactCardVectorSearchService factCardVectorSearchService = new FactCardVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.21F, 4)))
        );

        List<QueryArticleHit> hits = factCardVectorSearchService.search(executionContext("巡检项目有哪些"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getArticleKey()).isEqualTo("fc:valid");
    }

    /**
     * 验证低置信向量命中会保留但降权。
     */
    @Test
    void shouldDemoteLowConfidenceFactCardVectorScore() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        CapturingFactCardVectorJdbcRepository factCardVectorJdbcRepository =
                new CapturingFactCardVectorJdbcRepository(false, List.of(
                        factCardHit("fc:low-confidence", "low_confidence", 0.90D)
                ));
        FactCardVectorSearchService factCardVectorSearchService = new FactCardVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.21F, 4)))
        );

        List<QueryArticleHit> hits = factCardVectorSearchService.search(executionContext("巡检项目有哪些"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getReviewStatus()).isEqualTo("low_confidence");
        assertThat(hits.get(0).getScore()).isLessThan(0.90D);
    }

    /**
     * 创建启用的向量配置。
     *
     * @param dimensions 期望维度
     * @return 查询检索配置
     */
    private QuerySearchProperties enabledVectorProperties(int dimensions) {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setEmbeddingModel("text-embedding-test");
        querySearchProperties.getVector().setExpectedDimensions(dimensions);
        return querySearchProperties;
    }

    /**
     * 创建检索执行上下文。
     *
     * @param question 查询问题
     * @return 检索执行上下文
     */
    private RetrievalExecutionContext executionContext(String question) {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, 1.0D);
        Set<String> enabledChannels = new LinkedHashSet<String>();
        enabledChannels.add(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                question,
                QueryIntent.GENERAL,
                false,
                60,
                weights,
                enabledChannels
        );
        RetrievalQueryContext retrievalQueryContext = new RetrievalQueryContext(
                "query-fact-card-vector-test",
                question,
                question,
                QueryRewriteResult.unchanged(question),
                QueryIntent.GENERAL,
                retrievalStrategy
        );
        return new RetrievalExecutionContext(retrievalQueryContext, 3);
    }

    /**
     * 创建固定 embedding。
     *
     * @param seed 基准值
     * @param dimensions 维度
     * @return embedding
     */
    private static float[] createEmbedding(float seed, int dimensions) {
        float[] embedding = new float[dimensions];
        for (int index = 0; index < dimensions; index++) {
            embedding[index] = seed + (index % 5) * 0.01F;
        }
        return embedding;
    }

    /**
     * 固定能力探测替身。
     *
     * @author xiexu
     */
    private static class FixedSearchCapabilityService implements SearchCapabilityService {

        private final boolean vectorTypeAvailable;

        private final boolean factCardVectorIndexAvailable;

        /**
         * 创建固定能力探测替身。
         *
         * @param vectorTypeAvailable vector 类型是否可用
         * @param factCardVectorIndexAvailable fact card 向量表是否可用
         */
        private FixedSearchCapabilityService(boolean vectorTypeAvailable, boolean factCardVectorIndexAvailable) {
            this.vectorTypeAvailable = vectorTypeAvailable;
            this.factCardVectorIndexAvailable = factCardVectorIndexAvailable;
        }

        /**
         * 返回文本搜索配置不可用。
         *
         * @param configName 配置名
         * @return false
         */
        @Override
        public boolean supportsTextSearchConfig(String configName) {
            return false;
        }

        /**
         * 返回 vector 类型是否可用。
         *
         * @return 是否可用
         */
        @Override
        public boolean supportsVectorType() {
            return vectorTypeAvailable;
        }

        /**
         * 返回文章向量表不可用。
         *
         * @return false
         */
        @Override
        public boolean hasArticleVectorIndex() {
            return false;
        }

        /**
         * 返回 fact card 向量表是否可用。
         *
         * @return 是否可用
         */
        @Override
        public boolean hasFactCardVectorIndex() {
            return factCardVectorIndexAvailable;
        }
    }

    /**
     * 捕获查询向量的 fact card 向量仓储替身。
     *
     * @author xiexu
     */
    private static class CapturingFactCardVectorJdbcRepository extends FactCardVectorJdbcRepository {

        private final boolean failSearch;

        private float[] lastEmbedding;

        private int lastLimit;

        /**
         * 创建仓储替身。
         *
         * @param failSearch 是否模拟查询失败
         */
        private final List<QueryArticleHit> hits;

        /**
         * 创建仓储替身。
         *
         * @param failSearch 是否模拟查询失败
         */
        private CapturingFactCardVectorJdbcRepository(boolean failSearch) {
            this(failSearch, List.of(factCardHit("fc:vector-hit", "valid", 0.88D)));
        }

        /**
         * 创建仓储替身。
         *
         * @param failSearch 是否模拟查询失败
         * @param hits 预置命中
         */
        private CapturingFactCardVectorJdbcRepository(boolean failSearch, List<QueryArticleHit> hits) {
            super(new JdbcTemplate());
            this.failSearch = failSearch;
            this.hits = hits;
        }

        /**
         * 返回最近查询向量。
         *
         * @return 查询向量
         */
        private float[] getLastEmbedding() {
            return lastEmbedding;
        }

        /**
         * 返回最近查询数量。
         *
         * @return 查询数量
         */
        private int getLastLimit() {
            return lastLimit;
        }

        /**
         * 执行固定近邻查询。
         *
         * @param embedding 查询向量
         * @param limit 返回数量
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> searchNearestNeighbors(float[] embedding, int limit) {
            if (failSearch) {
                throw new IllegalStateException("fact card vector query failed");
            }
            lastEmbedding = embedding;
            lastLimit = limit;
            return hits;
        }
    }

    /**
     * 构造 fact card 向量命中。
     *
     * @param articleKey 命中 key
     * @param reviewStatus 审查状态
     * @param score 分数
     * @return 查询命中
     */
    private static QueryArticleHit factCardHit(String articleKey, String reviewStatus, double score) {
        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                10L,
                articleKey,
                articleKey,
                "巡检项目清单",
                "- availability\n- latency",
                "{\"cardId\":\"" + articleKey + "\",\"answerShape\":\"ENUM\"}",
                reviewStatus,
                List.of(),
                score
        );
    }

    /**
     * 固定 EmbeddingModel 替身。
     *
     * @author xiexu
     */
    private static class FixedEmbeddingModel implements EmbeddingModel {

        private final float[] embedding;

        /**
         * 创建固定 EmbeddingModel。
         *
         * @param embedding 固定向量
         */
        private FixedEmbeddingModel(float[] embedding) {
            this.embedding = embedding;
        }

        /**
         * 执行 embedding 请求。
         *
         * @param request embedding 请求
         * @return embedding 响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of(new Embedding(embedding, 0)));
        }

        /**
         * 对文档执行 embedding。
         *
         * @param document 文档
         * @return embedding 向量
         */
        @Override
        public float[] embed(Document document) {
            return embedding;
        }
    }
}
