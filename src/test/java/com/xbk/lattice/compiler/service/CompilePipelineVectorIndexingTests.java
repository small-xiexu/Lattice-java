package com.xbk.lattice.compiler.service;

import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import com.xbk.lattice.query.service.EmbeddingClientFactory;
import com.xbk.lattice.query.service.EmbeddingRouteResolution;
import com.xbk.lattice.query.service.SearchCapabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompilePipeline 向量索引测试
 *
 * 职责：验证 full / incremental compile 会自动刷新文章向量索引
 *
 * @author xiexu
 */
@SpringBootTest(classes = {
        com.xbk.lattice.LatticeApplication.class,
        CompilePipelineVectorIndexingTests.EmbeddingTestConfiguration.class
}, properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_vector_compile_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_vector_compile_test",
        "spring.flyway.default-schema=lattice_b8_vector_compile_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.llm.secret-encryption-key=test-phase8-key-0123456789abcdef",
        "lattice.query.search.vector.enabled=true",
        "lattice.query.search.vector.embedding-model-profile-id=1",
        "lattice.query.search.vector.embedding-model=test-embedding-model",
        "lattice.compiler.ingest-max-chars=4096",
        "lattice.compiler.batch-max-chars=4096"
})
class CompilePipelineVectorIndexingTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompilePipelineService compilePipelineService;

    @Autowired
    private ArticleVectorIndexService articleVectorIndexService;

    @Autowired
    private SearchCapabilityService searchCapabilityService;

    @Autowired
    private LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 验证 full compile 完成后会落库文章向量索引。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldPersistArticleVectorIndexAfterFullCompile(@TempDir Path tempDir) throws IOException {
        resetCompileTables();
        seedEmbeddingProfile();
        assertVectorCapabilityReady();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"支付超时处理说明\","
                        + "\"snippets\":[\"retry=3\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\",\"interval=30s\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        compilePipelineService.compile(tempDir);

        Integer vectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b8_vector_compile_test.article_vector_index",
                Integer.class
        );
        Integer chunkVectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b8_vector_compile_test.article_chunk_vector_index",
                Integer.class
        );
        Long modelProfileId = jdbcTemplate.queryForObject(
                "select model_profile_id from lattice_b8_vector_compile_test.article_vector_index where concept_id = 'payment-timeout'",
                Long.class
        );

        assertThat(vectorCount).isEqualTo(1);
        assertThat(chunkVectorCount).isGreaterThan(0);
        assertThat(modelProfileId).isEqualTo(Long.valueOf(1L));
    }

    /**
     * 验证 incremental compile 会刷新已有文章的向量索引内容哈希。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldRefreshArticleVectorIndexAfterIncrementalCompile(@TempDir Path tempDir) throws IOException {
        resetCompileTables();
        seedEmbeddingProfile();
        assertVectorCapabilityReady();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Path analyzeFile = paymentDir.resolve("analyze.json");
        Files.writeString(
                analyzeFile,
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"支付超时处理说明\","
                        + "\"snippets\":[\"retry=3\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );
        compilePipelineService.compile(tempDir);

        String beforeHash = jdbcTemplate.queryForObject(
                "select content_hash from lattice_b8_vector_compile_test.article_vector_index where concept_id = 'payment-timeout'",
                String.class
        );

        Files.writeString(
                analyzeFile,
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"支付超时补偿策略\","
                        + "\"snippets\":[\"retry=5\"],"
                        + "\"sections\":[{\"heading\":\"Compensation\",\"content\":[\"retry=5\",\"manual-review\"],\"sources\":[\"payment/analyze.json#compensation\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        compilePipelineService.incrementalCompile(tempDir);

        Integer vectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b8_vector_compile_test.article_vector_index",
                Integer.class
        );
        Integer chunkVectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b8_vector_compile_test.article_chunk_vector_index",
                Integer.class
        );
        String afterHash = jdbcTemplate.queryForObject(
                "select content_hash from lattice_b8_vector_compile_test.article_vector_index where concept_id = 'payment-timeout'",
                String.class
        );

        assertThat(vectorCount).isEqualTo(1);
        assertThat(chunkVectorCount).isGreaterThan(0);
        assertThat(afterHash).isNotBlank();
        assertThat(afterHash).isNotEqualTo(beforeHash);
    }

    /**
     * 重置编译相关测试表。
     */
    private void resetCompileTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.llm_model_profiles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.llm_provider_connections RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.article_chunk_vector_index");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.article_vector_index");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_vector_compile_test.articles CASCADE");
    }

    /**
     * 写入测试用 embedding profile。
     */
    private void seedEmbeddingProfile() {
        String encryptedApiKey = llmSecretCryptoService.encrypt("sk-test-openai");
        String maskedApiKey = llmSecretCryptoService.mask("sk-test-openai");
        jdbcTemplate.update(
                """
                        insert into lattice_b8_vector_compile_test.llm_provider_connections (
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
                        insert into lattice_b8_vector_compile_test.llm_model_profiles (
                            id, model_code, connection_id, model_name, model_kind, expected_dimensions,
                            supports_dimension_override, enabled, created_by, updated_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.valueOf(1L),
                "test-embedding-model",
                Long.valueOf(1L),
                "test-embedding-model",
                "EMBEDDING",
                Integer.valueOf(1536),
                false,
                true,
                "tester",
                "tester"
        );
    }

    /**
     * 断言向量能力与索引服务在当前测试上下文中可用。
     */
    private void assertVectorCapabilityReady() {
        assertThat(searchCapabilityService.supportsVectorType()).isTrue();
        assertThat(searchCapabilityService.hasArticleVectorIndex()).isTrue();
        assertThat(articleVectorIndexService.isIndexingAvailable()).isTrue();
    }

    /**
     * 测试用 embedding 配置。
     *
     * 职责：为编译链提供无网络依赖的固定 embedding 模型
     *
     * @author xiexu
     */
    @TestConfiguration
    static class EmbeddingTestConfiguration {

        /**
         * 提供固定 embedding 客户端工厂。
         *
         * @param restClientBuilder RestClient 构建器
         * @param webClientBuilder WebClient 构建器
         * @return embedding 客户端工厂
         */
        @Bean
        @Primary
        public EmbeddingClientFactory embeddingClientFactory(
                RestClient.Builder restClientBuilder,
                WebClient.Builder webClientBuilder
        ) {
            return new FixedEmbeddingClientFactory(restClientBuilder, webClientBuilder);
        }

        /**
         * 提供固定 embedding 模型。
         *
         * @return embedding 模型
         */
        @Bean
        @Primary
        public EmbeddingModel embeddingModel() {
            return new FixedEmbeddingModel();
        }
    }

    /**
     * 固定 EmbeddingClientFactory 替身。
     *
     * 职责：在测试中拦截动态路由后的 embedding 调用，避免真实网络请求
     *
     * @author xiexu
     */
    private static class FixedEmbeddingClientFactory extends EmbeddingClientFactory {

        private final EmbeddingModel embeddingModel = new FixedEmbeddingModel();

        /**
         * 创建固定 embedding 客户端工厂。
         *
         * @param restClientBuilder RestClient 构建器
         * @param webClientBuilder WebClient 构建器
         */
        FixedEmbeddingClientFactory(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
            super(restClientBuilder, webClientBuilder);
        }

        /**
         * 返回固定 embedding 模型。
         *
         * @param routeResolution 路由解析结果
         * @return 固定 embedding 模型
         */
        @Override
        public EmbeddingModel getOrCreate(EmbeddingRouteResolution routeResolution) {
            return embeddingModel;
        }
    }

    /**
     * 固定 EmbeddingModel 替身。
     *
     * @author xiexu
     */
    private static class FixedEmbeddingModel implements EmbeddingModel {

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
         * 创建与 pgvector 基线一致的固定维度向量。
         *
         * @return 1536 维 embedding
         */
        private float[] createEmbedding() {
            float[] embedding = new float[1536];
            for (int index = 0; index < embedding.length; index++) {
                embedding[index] = 0.15F + (index % 11) * 0.01F;
            }
            return embedding;
        }
    }
}
