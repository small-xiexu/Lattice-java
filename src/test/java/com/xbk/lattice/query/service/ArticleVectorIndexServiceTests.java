package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleVectorRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量索引服务测试
 *
 * 职责：验证文章向量索引的落库、跳过与去重逻辑
 *
 * @author xiexu
 */
class ArticleVectorIndexServiceTests {

    /**
     * 验证功能开启且能力可用时，会为文章写入向量索引。
     */
    @Test
    void shouldPersistVectorIndexWhenCapabilityAvailable() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setEmbeddingModel("text-embedding-3-small");

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        ArticleVectorIndexService articleVectorIndexService = new ArticleVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(createEmbedding(0.11F, 1536))
        );

        articleVectorIndexService.indexArticle(createArticleRecord("payment-timeout", "支付超时处理说明"));

        assertThat(articleVectorJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleVectorJdbcRepository.getSavedRecords().get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(articleVectorJdbcRepository.getSavedRecords().get(0).getModelName()).isEqualTo("text-embedding-3-small");
    }

    /**
     * 验证内容哈希未变化时，不会重复写入向量索引。
     */
    @Test
    void shouldSkipReindexWhenContentHashUnchanged() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        ArticleVectorIndexService articleVectorIndexService = new ArticleVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(createEmbedding(0.11F, 1536))
        );

        ArticleRecord articleRecord = createArticleRecord("payment-timeout", "支付超时处理说明");
        articleVectorIndexService.indexArticle(articleRecord);
        articleVectorIndexService.indexArticle(articleRecord);

        assertThat(articleVectorJdbcRepository.getSavedRecords()).hasSize(1);
    }

    /**
     * 验证 embedding 模型发生变化时，即使正文未变也会刷新向量索引。
     */
    @Test
    void shouldReindexWhenModelChangesEvenIfContentHashUnchanged() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setEmbeddingModel("text-embedding-3-small");

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        ArticleVectorIndexService articleVectorIndexService = new ArticleVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(createEmbedding(0.11F, 1536))
        );

        ArticleRecord articleRecord = createArticleRecord("payment-timeout", "支付超时处理说明");
        articleVectorIndexService.indexArticle(articleRecord);

        querySearchProperties.getVector().setEmbeddingModel("text-embedding-3-large");
        articleVectorIndexService.indexArticle(articleRecord);

        assertThat(articleVectorJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleVectorJdbcRepository.getSavedRecords().get(0).getModelName()).isEqualTo("text-embedding-3-large");
    }

    /**
     * 验证会按当前配置把 embedding 模型名与维度下发到真实请求。
     */
    @Test
    void shouldPassConfiguredModelAndDimensionsIntoEmbeddingRequest() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setEmbeddingModel("text-embedding-3-large");
        querySearchProperties.getVector().setExpectedDimensions(3072);

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        FixedEmbeddingModel fixedEmbeddingModel = new FixedEmbeddingModel(createEmbedding(0.11F, 3072));
        ArticleVectorIndexService articleVectorIndexService = new ArticleVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                fixedEmbeddingModel
        );

        articleVectorIndexService.indexArticle(createArticleRecord("payment-timeout", "支付超时处理说明"));

        assertThat(fixedEmbeddingModel.getLastRequestedModel()).isEqualTo("text-embedding-3-large");
        assertThat(fixedEmbeddingModel.getLastRequestedDimensions()).isEqualTo(3072);
    }

    /**
     * 验证 embedding 不可用时，会自动跳过索引而不是抛出异常。
     */
    @Test
    void shouldSkipIndexingWhenEmbeddingFails() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        ArticleVectorIndexService articleVectorIndexService = new ArticleVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(new IllegalStateException("embedding failed"))
        );

        articleVectorIndexService.indexArticle(createArticleRecord("payment-timeout", "支付超时处理说明"));

        assertThat(articleVectorJdbcRepository.getSavedRecords()).isEmpty();
    }

    /**
     * 验证 embedding 维度不匹配时，会跳过索引写入。
     */
    @Test
    void shouldSkipIndexingWhenEmbeddingDimensionsMismatch() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setExpectedDimensions(1536);

        FakeArticleVectorJdbcRepository articleVectorJdbcRepository = new FakeArticleVectorJdbcRepository();
        ArticleVectorIndexService articleVectorIndexService = new ArticleVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true, true),
                articleVectorJdbcRepository,
                new FixedEmbeddingModel(new float[]{0.11F, 0.22F, 0.33F})
        );

        articleVectorIndexService.indexArticle(createArticleRecord("payment-timeout", "支付超时处理说明"));

        assertThat(articleVectorJdbcRepository.getSavedRecords()).isEmpty();
    }

    /**
     * 创建测试文章记录。
     *
     * @param conceptId 概念标识
     * @param summary 摘要
     * @return 文章记录
     */
    private ArticleRecord createArticleRecord(String conceptId, String summary) {
        return new ArticleRecord(
                conceptId,
                "Payment Timeout",
                "# Payment Timeout\n\n" + summary,
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("payment/timeout.md"),
                "{\"description\":\"" + summary + "\"}",
                summary,
                List.of("retry=3"),
                List.of(),
                List.of(),
                "high",
                "passed"
        );
    }

    /**
     * 创建固定维度的 embedding 向量。
     *
     * @param seed 基准值
     * @param dimensions 维度数
     * @return embedding 向量
     */
    private float[] createEmbedding(float seed, int dimensions) {
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

        /**
         * 创建向量仓储替身。
         */
        private FakeArticleVectorJdbcRepository() {
            super(new JdbcTemplate());
        }

        /**
         * 获取已保存的向量记录。
         *
         * @return 向量记录列表
         */
        private List<ArticleVectorRecord> getSavedRecords() {
            return savedRecords;
        }

        /**
         * 保存向量记录。
         *
         * @param articleVectorRecord 向量记录
         */
        @Override
        public void upsert(ArticleVectorRecord articleVectorRecord) {
            savedRecords.removeIf(record -> record.getArticleKey().equals(articleVectorRecord.getArticleKey()));
            savedRecords.add(articleVectorRecord);
        }

        /**
         * 查询已有向量记录。
         *
         * @param articleKey 文章唯一键
         * @return 向量记录
         */
        @Override
        public Optional<ArticleVectorRecord> findByArticleKey(String articleKey) {
            return savedRecords.stream()
                    .filter(record -> record.getArticleKey().equals(articleKey))
                    .findFirst();
        }

        @Override
        public int countAll() {
            return savedRecords.size();
        }

        @Override
        public Optional<String> findEmbeddingColumnType() {
            return Optional.empty();
        }

        @Override
        public void alignEmbeddingColumnDimensions(int targetDimensions) {
            // 测试替身不依赖真实数据库 schema，对齐动作在这里视为 no-op。
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

        private String lastRequestedModel;

        private Integer lastRequestedDimensions;

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
            captureRequestOptions(request);
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

        /**
         * 返回最近一次请求使用的模型名称。
         *
         * @return 模型名称
         */
        private String getLastRequestedModel() {
            return lastRequestedModel;
        }

        /**
         * 返回最近一次请求使用的维度。
         *
         * @return 维度
         */
        private Integer getLastRequestedDimensions() {
            return lastRequestedDimensions;
        }

        /**
         * 捕获最近一次 embedding 请求选项。
         *
         * @param request embedding 请求
         */
        private void captureRequestOptions(EmbeddingRequest request) {
            if (!(request.getOptions() instanceof OpenAiEmbeddingOptions)) {
                return;
            }
            OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions) request.getOptions();
            lastRequestedModel = options.getModel();
            lastRequestedDimensions = options.getDimensions();
        }
    }
}
