package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleChunkVectorJdbcRepository;
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
 * ChunkVectorSearchService 测试
 *
 * 职责：验证 chunk 向量检索的共享 embedding 与降级行为
 *
 * @author xiexu
 */
class ChunkVectorSearchServiceTests {

    /**
     * 验证向量能力可用时，会使用共享 embedding 检索并聚合 chunk 命中。
     */
    @Test
    void shouldSearchChunkVectorHitsWithSharedEmbedding() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        CapturingArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository =
                new CapturingArticleChunkVectorJdbcRepository(false);
        CountingEmbeddingModel embeddingModel = new CountingEmbeddingModel(createEmbedding(0.31F, 4));
        ConfiguredVectorEmbeddingService embeddingService =
                new ConfiguredVectorEmbeddingService(querySearchProperties, embeddingModel);
        ChunkVectorSearchService chunkVectorSearchService = new ChunkVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true),
                articleChunkVectorJdbcRepository,
                embeddingService,
                new ChunkToArticleAggregator()
        );
        RetrievalExecutionContext executionContext = executionContext("巡检项目有哪些");

        List<QueryArticleHit> firstHits = chunkVectorSearchService.search(executionContext);
        List<QueryArticleHit> secondHits = chunkVectorSearchService.search(executionContext);

        assertThat(firstHits).hasSize(1);
        assertThat(secondHits).hasSize(1);
        assertThat(firstHits.get(0).getArticleKey()).isEqualTo("chunk-article");
        assertThat(articleChunkVectorJdbcRepository.getLastEmbedding()).containsExactly(createEmbedding(0.31F, 4));
        assertThat(embeddingModel.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证查询 embedding 维度不匹配时，会把异常交给 dispatcher 记录通道失败。
     */
    @Test
    void shouldThrowWhenSharedEmbeddingDimensionsMismatch() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        CapturingArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository =
                new CapturingArticleChunkVectorJdbcRepository(false);
        ChunkVectorSearchService chunkVectorSearchService = new ChunkVectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true),
                articleChunkVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(
                        querySearchProperties,
                        new CountingEmbeddingModel(createEmbedding(0.31F, 3))
                ),
                new ChunkToArticleAggregator()
        );

        assertThatThrownBy(() -> chunkVectorSearchService.search(executionContext("巡检项目有哪些")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimensions");
        assertThat(articleChunkVectorJdbcRepository.getLastEmbedding()).isNull();
    }

    /**
     * 创建启用的向量检索配置。
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
        weights.put(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR, 1.0D);
        Set<String> enabledChannels = new LinkedHashSet<String>();
        enabledChannels.add(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                question,
                QueryIntent.GENERAL,
                false,
                60,
                weights,
                enabledChannels
        );
        RetrievalQueryContext retrievalQueryContext = new RetrievalQueryContext(
                "query-1",
                question,
                question,
                QueryRewriteResult.unchanged(question),
                QueryIntent.GENERAL,
                retrievalStrategy
        );
        return new RetrievalExecutionContext(retrievalQueryContext, 3);
    }

    /**
     * 创建固定维度 embedding。
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

        /**
         * 创建固定能力探测替身。
         *
         * @param vectorTypeAvailable vector 类型是否可用
         */
        private FixedSearchCapabilityService(boolean vectorTypeAvailable) {
            this.vectorTypeAvailable = vectorTypeAvailable;
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
         * 返回文章向量索引表不可用。
         *
         * @return false
         */
        @Override
        public boolean hasArticleVectorIndex() {
            return false;
        }

        /**
         * 返回 chunk 向量索引表可用。
         *
         * @return true
         */
        @Override
        public boolean hasArticleChunkVectorIndex() {
            return true;
        }
    }

    /**
     * 捕获查询向量的 chunk 向量仓储替身。
     *
     * @author xiexu
     */
    private static class CapturingArticleChunkVectorJdbcRepository extends ArticleChunkVectorJdbcRepository {

        private final boolean failSearch;

        private float[] lastEmbedding;

        /**
         * 创建捕获仓储替身。
         *
         * @param failSearch 是否模拟查询失败
         */
        private CapturingArticleChunkVectorJdbcRepository(boolean failSearch) {
            super(new JdbcTemplate());
            this.failSearch = failSearch;
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
         * 执行固定 chunk 近邻查询。
         *
         * @param embedding 查询向量
         * @param limit 返回数量
         * @return 固定 chunk 命中
         */
        @Override
        public List<ArticleChunkVectorHit> searchNearestNeighbors(float[] embedding, int limit) {
            if (failSearch) {
                throw new IllegalStateException("chunk vector query failed");
            }
            lastEmbedding = embedding;
            return List.of(new ArticleChunkVectorHit(
                    1L,
                    2L,
                    "chunk-article",
                    "chunk-concept",
                    "巡检项目清单",
                    "巡检项目清单正文",
                    "{}",
                    "passed",
                    List.of("ops/checklist.md"),
                    0,
                    "- availability\n- latency",
                    0.87D
            ));
        }
    }

    /**
     * 计数 EmbeddingModel 替身。
     *
     * @author xiexu
     */
    private static class CountingEmbeddingModel implements EmbeddingModel {

        private final float[] embedding;

        private int callCount;

        /**
         * 创建计数 embedding 模型。
         *
         * @param embedding 固定向量
         */
        private CountingEmbeddingModel(float[] embedding) {
            this.embedding = embedding;
        }

        /**
         * 执行 embedding 请求并计数。
         *
         * @param request embedding 请求
         * @return embedding 响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            callCount++;
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

        /**
         * 返回 embedding 调用次数。
         *
         * @return 调用次数
         */
        private int getCallCount() {
            return callCount;
        }
    }
}
