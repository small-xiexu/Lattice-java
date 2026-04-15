package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleVectorRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量检索服务测试
 *
 * 职责：验证 pgvector 检索的启用、降级与召回行为
 *
 * @author xiexu
 */
class VectorSearchServiceTests {

    /**
     * 验证关闭向量检索时，会直接返回空结果。
     */
    @Test
    void shouldReturnEmptyWhenVectorSearchDisabled() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(false);

        VectorSearchService vectorSearchService = new VectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                new FakeArticleVectorJdbcRepository(),
                new FixedEmbeddingModel(createEmbedding(0.1F, 1536))
        );

        assertThat(vectorSearchService.search("退款状态是什么", 5)).isEmpty();
    }

    /**
     * 验证能力可用时，会返回向量近邻文章命中。
     */
    @Test
    void shouldReturnNearestVectorHitsWhenCapabilitiesAvailable() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        articleVectorJdbcRepository.setQueryResults(List.of(
                new QueryArticleHit(
                        "refund-status",
                        "Refund Status",
                        "退款状态流转说明",
                        "{\"source\":\"vector\"}",
                        List.of("refund/status.md"),
                        0.93D
                )
        ));

        VectorSearchService vectorSearchService = new VectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(createEmbedding(0.1F, 1536))
        );

        List<QueryArticleHit> hits = vectorSearchService.search("退款状态是什么", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getConceptId()).isEqualTo("refund-status");
        assertThat(articleVectorJdbcRepository.getLastQueryEmbedding()).hasSize(1536);
        assertThat(articleVectorJdbcRepository.getLastQueryEmbedding()[0]).isEqualTo(0.1F);
    }

    /**
     * 验证 embedding 调用失败时，会自动降级为空结果而不是中断主链。
     */
    @Test
    void shouldGracefullyFallbackWhenEmbeddingFails() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);

        VectorSearchService vectorSearchService = new VectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                new FakeArticleVectorJdbcRepository(),
                new FixedEmbeddingModel(new IllegalStateException("embedding failed"))
        );

        assertThat(vectorSearchService.search("退款状态是什么", 5)).isEmpty();
    }

    /**
     * 验证查询向量维度不匹配时，会自动降级为空结果。
     */
    @Test
    void shouldFallbackWhenEmbeddingDimensionsMismatch() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setExpectedDimensions(1536);

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        articleVectorJdbcRepository.setQueryResults(List.of(
                new QueryArticleHit(
                        "refund-status",
                        "Refund Status",
                        "退款状态流转说明",
                        "{\"source\":\"vector\"}",
                        List.of("refund/status.md"),
                        0.93D
                )
        ));

        VectorSearchService vectorSearchService = new VectorSearchService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(new float[]{0.1F, 0.2F, 0.3F})
        );

        assertThat(vectorSearchService.search("退款状态是什么", 5)).isEmpty();
        assertThat(articleVectorJdbcRepository.getLastQueryEmbedding()).isNull();
    }

    /**
     * 创建固定维度的 embedding 向量。
     *
     * @param seed 基准值
     * @param dimensions 维度数
     * @return embedding 向量
     */
    private static float[] createEmbedding(float seed, int dimensions) {
        float[] embedding = new float[dimensions];
        for (int index = 0; index < dimensions; index++) {
            embedding[index] = seed + (index % 7) * 0.01F;
        }
        return embedding;
    }

    /**
     * 固定能力探测替身。
     *
     * @author xiexu
     */
    private static class FixedSearchCapabilityService implements SearchCapabilityService {

        private final boolean textSearchConfigAvailable;

        private final boolean vectorTypeAvailable;

        private final boolean vectorIndexAvailable;

        /**
         * 创建固定能力探测替身。
         *
         * @param textSearchConfigAvailable 文本搜索配置是否可用
         * @param vectorTypeAvailable 向量类型是否可用
         * @param vectorIndexAvailable 向量索引表是否可用
         */
        private FixedSearchCapabilityService(
                boolean textSearchConfigAvailable,
                boolean vectorTypeAvailable,
                boolean vectorIndexAvailable
        ) {
            this.textSearchConfigAvailable = textSearchConfigAvailable;
            this.vectorTypeAvailable = vectorTypeAvailable;
            this.vectorIndexAvailable = vectorIndexAvailable;
        }

        /**
         * 返回文本搜索配置是否可用。
         *
         * @param configName 配置名
         * @return 是否可用
         */
        @Override
        public boolean supportsTextSearchConfig(String configName) {
            return textSearchConfigAvailable;
        }

        /**
         * 返回向量类型是否可用。
         *
         * @return 是否可用
         */
        @Override
        public boolean supportsVectorType() {
            return vectorTypeAvailable;
        }

        /**
         * 返回向量索引表是否可用。
         *
         * @return 是否可用
         */
        @Override
        public boolean hasArticleVectorIndex() {
            return vectorIndexAvailable;
        }
    }

    /**
     * 向量仓储替身。
     *
     * @author xiexu
     */
    private static class FakeArticleVectorJdbcRepository extends ArticleVectorJdbcRepository {

        private final List<ArticleVectorRecord> savedRecords = new ArrayList<ArticleVectorRecord>();

        private List<QueryArticleHit> queryResults = List.of();

        private float[] lastQueryEmbedding;

        /**
         * 创建向量仓储替身。
         */
        private FakeArticleVectorJdbcRepository() {
            super(new JdbcTemplate());
        }

        /**
         * 设置查询返回结果。
         *
         * @param queryResults 查询返回结果
         */
        private void setQueryResults(List<QueryArticleHit> queryResults) {
            this.queryResults = queryResults;
        }

        /**
         * 获取最近一次查询向量。
         *
         * @return 最近一次查询向量
         */
        private float[] getLastQueryEmbedding() {
            return lastQueryEmbedding;
        }

        /**
         * 保存向量记录。
         *
         * @param articleVectorRecord 向量记录
         */
        @Override
        public void upsert(ArticleVectorRecord articleVectorRecord) {
            savedRecords.add(articleVectorRecord);
        }

        /**
         * 查询已有向量记录。
         *
         * @param conceptId 概念标识
         * @return 向量记录
         */
        @Override
        public Optional<ArticleVectorRecord> findByConceptId(String conceptId) {
            return savedRecords.stream()
                    .filter(record -> record.getConceptId().equals(conceptId))
                    .findFirst();
        }

        /**
         * 查询向量近邻。
         *
         * @param embedding 查询向量
         * @param limit 返回数量
         * @return 文章命中
         */
        @Override
        public List<QueryArticleHit> searchNearestNeighbors(float[] embedding, int limit) {
            lastQueryEmbedding = embedding;
            return queryResults;
        }
    }

    /**
     * 固定 EmbeddingModel 替身。
     *
     * @author xiexu
     */
    private static class FixedEmbeddingModel implements EmbeddingModel {

        private final float[] embedding;

        private final RuntimeException runtimeException;

        /**
         * 创建固定 embedding 模型替身。
         *
         * @param embedding 固定向量
         */
        private FixedEmbeddingModel(float[] embedding) {
            this.embedding = embedding;
            this.runtimeException = null;
        }

        /**
         * 创建异常 embedding 模型替身。
         *
         * @param runtimeException 运行时异常
         */
        private FixedEmbeddingModel(RuntimeException runtimeException) {
            this.embedding = null;
            this.runtimeException = runtimeException;
        }

        /**
         * 执行 embedding 请求。
         *
         * @param request embedding 请求
         * @return embedding 响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            if (runtimeException != null) {
                throw runtimeException;
            }
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
            if (runtimeException != null) {
                throw runtimeException;
            }
            return embedding;
        }
    }
}
