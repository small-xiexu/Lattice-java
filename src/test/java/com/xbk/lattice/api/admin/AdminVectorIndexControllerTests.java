package com.xbk.lattice.api.admin;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import com.xbk.lattice.query.service.EmbeddingClientFactory;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.EmbeddingRouteResolution;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminVectorIndexController 测试
 *
 * 职责：验证管理侧可查看向量索引状态并触发全量重建
 *
 * @author xiexu
 */
@SpringBootTest(classes = {
        com.xbk.lattice.LatticeApplication.class,
        AdminVectorIndexControllerTests.EmbeddingTestConfiguration.class
}, properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_vector_admin_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_vector_admin_test",
        "spring.flyway.default-schema=lattice_b8_vector_admin_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.llm.secret-encryption-key=test-phase8-key-0123456789abcdef",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false",
        "lattice.query.search.vector.enabled=true",
        "lattice.query.search.vector.embedding-model-profile-id=1",
        "lattice.query.search.vector.embedding-model=test-embedding-model",
        "lattice.query.search.vector.expected-dimensions=1536"
})
@AutoConfigureMockMvc
class AdminVectorIndexControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private LlmSecretCryptoService llmSecretCryptoService;

    @Autowired
    private QueryCacheStore queryCacheStore;

    /**
     * 验证管理侧接口可返回向量索引状态摘要。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeVectorStatusViaAdminApi() throws Exception {
        resetTables();
        ensureVectorInfrastructure();
        seedEmbeddingProfile(1536);
        articleJdbcRepository.upsert(createArticleRecord("payment-timeout"));

        mockMvc.perform(get("/api/v1/admin/vector/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorEnabled").value(true))
                .andExpect(jsonPath("$.vectorTypeAvailable").value(true))
                .andExpect(jsonPath("$.vectorIndexTableAvailable").value(true))
                .andExpect(jsonPath("$.indexingAvailable").value(true))
                .andExpect(jsonPath("$.embeddingModelProfileId").value(1))
                .andExpect(jsonPath("$.configuredModelName").value("test-embedding-model"))
                .andExpect(jsonPath("$.configuredExpectedDimensions").value(1536))
                .andExpect(jsonPath("$.embeddingColumnType").value("vector(1536)"))
                .andExpect(jsonPath("$.schemaDimensions").value(1536))
                .andExpect(jsonPath("$.dimensionsMatch").value(true))
                .andExpect(jsonPath("$.articleCount").value(1))
                .andExpect(jsonPath("$.indexedArticleCount").value(0));
    }

    /**
     * 验证管理侧可先清空旧索引后按当前配置重建向量索引。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldRebuildVectorIndexViaAdminApi() throws Exception {
        resetTables();
        ensureVectorInfrastructure();
        seedEmbeddingProfile(1536);
        articleJdbcRepository.upsert(createArticleRecord("payment-timeout"));
        insertLegacyVectorRecord("payment-timeout", "legacy-embedding-model", "legacy-hash");

        mockMvc.perform(post("/api/v1/admin/vector/rebuild")
                        .contentType(APPLICATION_JSON)
                        .content("{\"truncateFirst\":true,\"operator\":\"tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetArticleCount").value(1))
                .andExpect(jsonPath("$.previousIndexedArticleCount").value(1))
                .andExpect(jsonPath("$.indexedArticleCount").value(1))
                .andExpect(jsonPath("$.previousIndexedChunkCount").value(0))
                .andExpect(jsonPath("$.indexedChunkCount").value(1))
                .andExpect(jsonPath("$.truncateFirst").value(true))
                .andExpect(jsonPath("$.configuredModelName").value("test-embedding-model"))
                .andExpect(jsonPath("$.operator").value("tester"))
                .andExpect(jsonPath("$.rebuiltAt").isNotEmpty());

        Long modelProfileId = jdbcTemplate.queryForObject(
                "select model_profile_id from lattice_b8_vector_admin_test.article_vector_index where concept_id = 'payment-timeout'",
                Long.class
        );
        assertThat(modelProfileId).isEqualTo(Long.valueOf(1L));
    }

    /**
     * 验证当当前 embedding profile 维度与 schema 不一致时，
     * 勾选 truncateFirst 的重建会先修复向量列维度，再完成索引重建。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldAlignVectorSchemaDimensionsDuringTruncateRebuild() throws Exception {
        resetTables();
        ensureVectorInfrastructure();
        seedEmbeddingProfile(1024);
        articleJdbcRepository.upsert(createArticleRecord("payment-timeout"));
        queryCacheStore.put(
                "为什么订单服务不直接同步调用库存服务，而要走消息队列？",
                new QueryResponse("旧缓存答案", List.of(), List.of(), null, "PASSED")
        );

        mockMvc.perform(get("/api/v1/admin/vector/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredExpectedDimensions").value(1024))
                .andExpect(jsonPath("$.schemaDimensions").value(1536))
                .andExpect(jsonPath("$.dimensionsMatch").value(false));

        mockMvc.perform(post("/api/v1/admin/vector/rebuild")
                        .contentType(APPLICATION_JSON)
                        .content("{\"truncateFirst\":true,\"operator\":\"tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetArticleCount").value(1))
                .andExpect(jsonPath("$.indexedArticleCount").value(1))
                .andExpect(jsonPath("$.indexedChunkCount").value(1))
                .andExpect(jsonPath("$.truncateFirst").value(true));

        mockMvc.perform(get("/api/v1/admin/vector/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredExpectedDimensions").value(1024))
                .andExpect(jsonPath("$.schemaDimensions").value(1024))
                .andExpect(jsonPath("$.dimensionsMatch").value(true))
                .andExpect(jsonPath("$.indexedArticleCount").value(1));

        assertThat(findEmbeddingColumnType("article_vector_index")).isEqualTo("vector(1024)");
        assertThat(findEmbeddingColumnType("article_chunk_vector_index")).isEqualTo("vector(1024)");
        assertThat(queryCacheStore.get("为什么订单服务不直接同步调用库存服务，而要走消息队列？")).isEmpty();
    }

    /**
     * 创建测试文章记录。
     *
     * @param conceptId 概念标识
     * @return 文章记录
     */
    private ArticleRecord createArticleRecord(String conceptId) {
        return new ArticleRecord(
                conceptId,
                "Payment Timeout",
                "# Payment Timeout\n\n- retry=3\n- interval=30s",
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("payment/order.md"),
                "{\"description\":\"payment summary\"}",
                "payment summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "high",
                "passed"
        );
    }

    /**
     * 确保向量扩展与索引表已准备就绪。
     */
    private void ensureVectorInfrastructure() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
    }

    /**
     * 插入旧向量记录。
     *
     * @param conceptId 概念标识
     * @param modelName 模型名
     * @param contentHash 内容哈希
     */
    private void insertLegacyVectorRecord(String conceptId, String modelName, String contentHash) {
        String vectorLiteral = createVectorLiteral(0.05F, 1536);
        jdbcTemplate.update(
                """
                        insert into lattice_b8_vector_admin_test.article_vector_index (
                            article_key, concept_id, model_profile_id, embedding_dimensions, index_version, content_hash, embedding, updated_at
                        ) values (?, ?, ?, ?, ?, ?, cast(? as public.vector), now())
                        on conflict (article_key) do update
                        set concept_id = excluded.concept_id,
                            model_profile_id = excluded.model_profile_id,
                            embedding_dimensions = excluded.embedding_dimensions,
                            index_version = excluded.index_version,
                            content_hash = excluded.content_hash,
                            embedding = excluded.embedding,
                            updated_at = excluded.updated_at
                        """,
                conceptId,
                conceptId,
                Long.valueOf(1L),
                Integer.valueOf(1536),
                "legacy-1536-article-v1",
                contentHash,
                vectorLiteral
        );
    }

    /**
     * 创建固定维度向量字面量。
     *
     * @param seed 基准值
     * @param dimensions 维度数
     * @return 向量字面量
     */
    private String createVectorLiteral(float seed, int dimensions) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('[');
        for (int index = 0; index < dimensions; index++) {
            if (index > 0) {
                stringBuilder.append(',');
            }
            stringBuilder.append(seed + (index % 9) * 0.01F);
        }
        stringBuilder.append(']');
        return stringBuilder.toString();
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_admin_test.article_vector_index CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_admin_test.llm_model_profiles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_admin_test.llm_provider_connections RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_admin_test.articles CASCADE");
    }

    /**
     * 写入测试用 embedding profile。
     */
    private void seedEmbeddingProfile(int expectedDimensions) {
        String encryptedApiKey = llmSecretCryptoService.encrypt("sk-test-openai");
        String maskedApiKey = llmSecretCryptoService.mask("sk-test-openai");
        jdbcTemplate.update(
                """
                        insert into lattice_b8_vector_admin_test.llm_provider_connections (
                            id, connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask, enabled, created_by, updated_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.valueOf(1L),
                "test-openai",
                "openai",
                "http://localhost:18080",
                encryptedApiKey,
                maskedApiKey,
                true,
                "tester",
                "tester"
        );
        jdbcTemplate.update(
                """
                        insert into lattice_b8_vector_admin_test.llm_model_profiles (
                            id, model_code, connection_id, model_name, model_kind, expected_dimensions,
                            supports_dimension_override, enabled, created_by, updated_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.valueOf(1L),
                "test-embedding-model",
                Long.valueOf(1L),
                "test-embedding-model",
                "EMBEDDING",
                Integer.valueOf(expectedDimensions),
                false,
                true,
                "tester",
                "tester"
        );
    }

    private String findEmbeddingColumnType(String tableName) {
        String columnType = jdbcTemplate.queryForObject(
                """
                        select format_type(a.atttypid, a.atttypmod)
                        from pg_attribute a
                        join pg_class c on c.oid = a.attrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = 'lattice_b8_vector_admin_test'
                          and c.relname = ?
                          and a.attname = 'embedding'
                          and a.attnum > 0
                          and not a.attisdropped
                        """,
                String.class,
                tableName
        );
        if (columnType == null) {
            return "";
        }
        int vectorIndex = columnType.lastIndexOf("vector(");
        if (vectorIndex < 0) {
            return columnType;
        }
        return columnType.substring(vectorIndex);
    }

    /**
     * 测试用 embedding 配置。
     *
     * 职责：为向量重建提供无网络依赖的固定 embedding 模型
     *
     * @author xiexu
     */
    @TestConfiguration
    static class EmbeddingTestConfiguration {

        /**
         * 提供固定 embedding 模型。
         *
         * @return embedding 模型
         */
        @Bean
        @Primary
        public EmbeddingModel embeddingModel() {
            return new FixedEmbeddingModel(1536);
        }

        /**
         * 提供固定 embedding 客户端工厂。
         *
         * @return embedding 客户端工厂
         */
        @Bean
        @Primary
        public EmbeddingClientFactory embeddingClientFactory() {
            return new EmbeddingClientFactory(RestClient.builder(), WebClient.builder()) {

                /**
                 * 返回固定 embedding 模型。
                 *
                 * @param routeResolution 路由解析结果
                 * @return embedding 模型
                 */
                @Override
                public EmbeddingModel getOrCreate(EmbeddingRouteResolution routeResolution) {
                    int dimensions = routeResolution.getExpectedDimensions() == null
                            ? 1536
                            : routeResolution.getExpectedDimensions().intValue();
                    return new FixedEmbeddingModel(dimensions);
                }
            };
        }
    }

    /**
     * 固定 EmbeddingModel 替身。
     *
     * @author xiexu
     */
    private static class FixedEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        private FixedEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        /**
         * 执行 embedding 请求。
         *
         * @param request embedding 请求
         * @return embedding 响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of(new Embedding(createEmbedding(), 0)));
        }

        /**
         * 对文档执行 embedding。
         *
         * @param document 文档
         * @return embedding 向量
         */
        @Override
        public float[] embed(Document document) {
            return createEmbedding();
        }

        /**
         * 创建固定维度 embedding。
         *
         * @return 固定维度 embedding
         */
        private float[] createEmbedding() {
            float[] embedding = new float[dimensions];
            for (int index = 0; index < embedding.length; index++) {
                embedding[index] = 0.15F + (index % 11) * 0.01F;
            }
            return embedding;
        }
    }
}
