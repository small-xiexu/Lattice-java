package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量 schema 检查测试
 *
 * 职责：验证 profile 维度与 schema 维度探测逻辑
 *
 * @author xiexu
 */
class VectorSchemaInspectorTest {

    /**
     * 验证 profile 与 schema 维度一致时返回 true。
     */
    @Test
    void shouldMarkDimensionsConsistentWhenProfileMatchesSchema() {
        VectorSchemaInspector inspector = new VectorSchemaInspector(
                new StubQueryVectorConfigService(new QueryVectorConfigState(
                        true,
                        1L,
                        "openai",
                        "text-embedding-3-small",
                        1536,
                        "database",
                        false,
                        "",
                        "tester",
                        "tester",
                        null,
                        null
                )),
                new StubArticleVectorJdbcRepository("vector(1536)", "hnsw")
        );

        VectorSchemaInspection inspection = inspector.inspect();

        assertThat(inspection.getProfileDimensions()).isEqualTo(1536);
        assertThat(inspection.getSchemaDimensions()).isEqualTo(1536);
        assertThat(inspection.isDimensionsConsistent()).isTrue();
        assertThat(inspection.isAnnIndexReady()).isTrue();
        assertThat(inspection.getAnnIndexType()).isEqualTo("hnsw");
    }

    /**
     * 验证 profile 与 schema 维度不一致时返回 false。
     */
    @Test
    void shouldMarkDimensionsInconsistentWhenProfileDiffersFromSchema() {
        VectorSchemaInspector inspector = new VectorSchemaInspector(
                new StubQueryVectorConfigService(new QueryVectorConfigState(
                        true,
                        2L,
                        "openai",
                        "text-embedding-3-large",
                        3072,
                        "database",
                        false,
                        "",
                        "tester",
                        "tester",
                        null,
                        null
                )),
                new StubArticleVectorJdbcRepository("vector(1536)", "")
        );

        VectorSchemaInspection inspection = inspector.inspect();

        assertThat(inspection.getProfileDimensions()).isEqualTo(3072);
        assertThat(inspection.getSchemaDimensions()).isEqualTo(1536);
        assertThat(inspection.isDimensionsConsistent()).isFalse();
        assertThat(inspection.isAnnIndexReady()).isFalse();
    }

    private static class StubQueryVectorConfigService extends QueryVectorConfigService {

        private final QueryVectorConfigState state;

        private StubQueryVectorConfigService(QueryVectorConfigState state) {
            super(
                    new QuerySearchProperties(),
                    null,
                    null,
                    null,
                    new ApplicationEventPublisher() {
                        @Override
                        public void publishEvent(Object event) {
                        }
                    },
                    new QueryCacheStore() {
                        @Override
                        public Optional<com.xbk.lattice.api.query.QueryResponse> get(String cacheKey) {
                            return Optional.empty();
                        }

                        @Override
                        public void put(String cacheKey, com.xbk.lattice.api.query.QueryResponse queryResponse) {
                        }

                        @Override
                        public void evictAll() {
                        }
                    }
            );
            this.state = state;
        }

        @Override
        public synchronized QueryVectorConfigState getCurrentState() {
            return state;
        }
    }

    private static class StubArticleVectorJdbcRepository extends ArticleVectorJdbcRepository {

        private final String embeddingColumnType;

        private final String annIndexType;

        private StubArticleVectorJdbcRepository(String embeddingColumnType, String annIndexType) {
            super(new JdbcTemplate());
            this.embeddingColumnType = embeddingColumnType;
            this.annIndexType = annIndexType;
        }

        @Override
        public Optional<String> findEmbeddingColumnType() {
            return Optional.ofNullable(embeddingColumnType);
        }

        @Override
        public Optional<String> findEmbeddingAnnIndexType() {
            if (annIndexType == null || annIndexType.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(annIndexType);
        }
    }
}
