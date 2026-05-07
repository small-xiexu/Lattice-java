package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardVectorRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardVectorIndexService 测试
 *
 * 职责：验证 fact card 向量索引的写入、跳过与维度保护逻辑
 *
 * @author xiexu
 */
class FactCardVectorIndexServiceTests {

    /**
     * 验证功能开启且能力可用时，会为 fact card 写入向量索引。
     */
    @Test
    void shouldPersistFactCardVectorIndexWhenCapabilityAvailable() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        FakeFactCardVectorJdbcRepository factCardVectorJdbcRepository = new FakeFactCardVectorJdbcRepository();
        FactCardVectorIndexService factCardVectorIndexService = new FactCardVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                new FakeFactCardJdbcRepository(List.of(sampleFactCard(1L, "fc:vector"))),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.11F, 4)))
        );

        factCardVectorIndexService.rebuildAll();

        assertThat(factCardVectorJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(factCardVectorJdbcRepository.getSavedRecords().get(0).getCardId()).isEqualTo("fc:vector");
        assertThat(factCardVectorJdbcRepository.getSavedRecords().get(0).getEmbeddingDimensions()).isEqualTo(4);
        assertThat(factCardVectorJdbcRepository.getSavedRecords().get(0).getIndexVersion()).endsWith("-fact-card-v1");
    }

    /**
     * 验证内容、profile、维度和版本未变化时，不重复写入向量索引。
     */
    @Test
    void shouldSkipReindexWhenFactCardVectorIsCurrent() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        FakeFactCardVectorJdbcRepository factCardVectorJdbcRepository = new FakeFactCardVectorJdbcRepository();
        FactCardRecord factCardRecord = sampleFactCard(1L, "fc:stable");
        FactCardVectorIndexService factCardVectorIndexService = new FactCardVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                new FakeFactCardJdbcRepository(List.of(factCardRecord)),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.11F, 4)))
        );

        factCardVectorIndexService.indexFactCard(factCardRecord);
        factCardVectorIndexService.indexFactCard(factCardRecord);

        assertThat(factCardVectorJdbcRepository.getUpsertCount()).isEqualTo(1);
        assertThat(factCardVectorJdbcRepository.getSavedRecords()).hasSize(1);
    }

    /**
     * 验证 embedding 维度不匹配时，会跳过写入。
     */
    @Test
    void shouldSkipIndexingWhenEmbeddingDimensionsMismatch() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        FakeFactCardVectorJdbcRepository factCardVectorJdbcRepository = new FakeFactCardVectorJdbcRepository();
        FactCardVectorIndexService factCardVectorIndexService = new FactCardVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(true, true),
                new FakeFactCardJdbcRepository(List.of(sampleFactCard(1L, "fc:mismatch"))),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.11F, 3)))
        );

        factCardVectorIndexService.rebuildAll();

        assertThat(factCardVectorJdbcRepository.getSavedRecords()).isEmpty();
    }

    /**
     * 验证向量能力不可用时，会直接跳过且不影响主链。
     */
    @Test
    void shouldSkipIndexingWhenVectorCapabilityUnavailable() {
        QuerySearchProperties querySearchProperties = enabledVectorProperties(4);
        FakeFactCardVectorJdbcRepository factCardVectorJdbcRepository = new FakeFactCardVectorJdbcRepository();
        FactCardVectorIndexService factCardVectorIndexService = new FactCardVectorIndexService(
                querySearchProperties,
                new FixedSearchCapabilityService(false, true),
                new FakeFactCardJdbcRepository(List.of(sampleFactCard(1L, "fc:disabled"))),
                factCardVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, new FixedEmbeddingModel(createEmbedding(0.11F, 4)))
        );

        factCardVectorIndexService.rebuildAll();

        assertThat(factCardVectorJdbcRepository.getSavedRecords()).isEmpty();
    }

    /**
     * 创建启用的向量配置。
     *
     * @param dimensions 期望维度
     * @return 向量配置
     */
    private QuerySearchProperties enabledVectorProperties(int dimensions) {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setEmbeddingModel("text-embedding-test");
        querySearchProperties.getVector().setExpectedDimensions(dimensions);
        return querySearchProperties;
    }

    /**
     * 创建测试用 fact card。
     *
     * @param id 主键
     * @param cardId 稳定标识
     * @return fact card
     */
    private FactCardRecord sampleFactCard(Long id, String cardId) {
        return new FactCardRecord(
                id,
                cardId,
                10L,
                20L,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "巡检项目清单",
                "巡检项目包含可用性、延迟和错误率。",
                "{\"items\":[{\"name\":\"availability\"},{\"name\":\"latency\"},{\"name\":\"errorRate\"}]}",
                "- availability\n- latency\n- errorRate",
                List.of(101L),
                List.of(),
                0.92D,
                FactCardReviewStatus.VALID,
                "hash-" + cardId,
                null,
                null
        );
    }

    /**
     * 创建固定维度的 embedding。
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
     * fact card 仓储替身。
     *
     * @author xiexu
     */
    private static class FakeFactCardJdbcRepository extends FactCardJdbcRepository {

        private final List<FactCardRecord> factCardRecords;

        /**
         * 创建 fact card 仓储替身。
         *
         * @param factCardRecords fact card 列表
         */
        private FakeFactCardJdbcRepository(List<FactCardRecord> factCardRecords) {
            super(null);
            this.factCardRecords = factCardRecords;
        }

        /**
         * 返回固定 fact card 列表。
         *
         * @return fact card 列表
         */
        @Override
        public List<FactCardRecord> findAll() {
            return factCardRecords;
        }
    }

    /**
     * fact card 向量仓储替身。
     *
     * @author xiexu
     */
    private static class FakeFactCardVectorJdbcRepository extends FactCardVectorJdbcRepository {

        private final List<FactCardVectorRecord> savedRecords = new ArrayList<FactCardVectorRecord>();

        private int upsertCount;

        /**
         * 创建 fact card 向量仓储替身。
         */
        private FakeFactCardVectorJdbcRepository() {
            super(new JdbcTemplate());
        }

        /**
         * 返回已保存记录。
         *
         * @return 已保存记录
         */
        private List<FactCardVectorRecord> getSavedRecords() {
            return savedRecords;
        }

        /**
         * 返回 upsert 次数。
         *
         * @return upsert 次数
         */
        private int getUpsertCount() {
            return upsertCount;
        }

        /**
         * 保存向量记录。
         *
         * @param factCardVectorRecord 向量记录
         */
        @Override
        public void upsert(FactCardVectorRecord factCardVectorRecord) {
            upsertCount++;
            savedRecords.removeIf(record -> Objects.equals(record.getFactCardId(), factCardVectorRecord.getFactCardId()));
            savedRecords.add(factCardVectorRecord);
        }

        /**
         * 按 fact card 主键查询记录。
         *
         * @param factCardId fact card 主键
         * @return 向量记录
         */
        @Override
        public Optional<FactCardVectorRecord> findByFactCardId(Long factCardId) {
            return savedRecords.stream()
                    .filter(record -> Objects.equals(record.getFactCardId(), factCardId))
                    .findFirst();
        }

        /**
         * 返回记录数量。
         *
         * @return 记录数量
         */
        @Override
        public int countAll() {
            return savedRecords.size();
        }

        /**
         * 返回模拟 schema 维度。
         *
         * @return schema 维度
         */
        @Override
        public Optional<String> findEmbeddingColumnType() {
            return Optional.of("vector(4)");
        }

        /**
         * 测试替身不需要真实 schema 对齐。
         *
         * @param targetDimensions 目标维度
         */
        @Override
        public void alignEmbeddingColumnDimensions(int targetDimensions) {
            // no-op
        }
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
