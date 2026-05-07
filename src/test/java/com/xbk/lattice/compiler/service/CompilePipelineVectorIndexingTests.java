package com.xbk.lattice.compiler.service;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import com.xbk.lattice.query.service.EmbeddingClientFactory;
import com.xbk.lattice.query.service.EmbeddingRouteResolution;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.SearchCapabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
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

    @Autowired
    private QueryCacheStore queryCacheStore;

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
                "select count(*) from lattice.article_vector_index",
                Integer.class
        );
        Integer chunkVectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.article_chunk_vector_index",
                Integer.class
        );
        Long modelProfileId = jdbcTemplate.queryForObject(
                "select model_profile_id from lattice.article_vector_index where concept_id = 'payment-timeout'",
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
                "select content_hash from lattice.article_vector_index where concept_id = 'payment-timeout'",
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
        queryCacheStore.put("payment timeout compensation", new QueryResponse("旧缓存答案", List.of(), List.of(), null, "PASSED"));

        compilePipelineService.incrementalCompile(tempDir);

        Integer vectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.article_vector_index",
                Integer.class
        );
        Integer chunkVectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.article_chunk_vector_index",
                Integer.class
        );
        String afterHash = jdbcTemplate.queryForObject(
                "select content_hash from lattice.article_vector_index where concept_id = 'payment-timeout'",
                String.class
        );

        assertThat(vectorCount).isEqualTo(1);
        assertThat(chunkVectorCount).isGreaterThan(0);
        assertThat(afterHash).isNotBlank();
        assertThat(afterHash).isNotEqualTo(beforeHash);
        assertThat(queryCacheStore.get("payment timeout compensation")).isEmpty();
    }

    /**
     * 验证首次编译遇到 schema 维度与 embedding profile 不一致时，
     * 会在空向量表场景下自动对齐维度并正常写入索引。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldAlignVectorSchemaDimensionsDuringCompileWhenTablesAreEmpty(@TempDir Path tempDir) throws IOException {
        resetCompileTables();
        seedEmbeddingProfile(1024);
        assertVectorCapabilityReady();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"支付超时处理说明\","
                        + "\"snippets\":[\"retry=5\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=5\",\"interval=30s\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        compilePipelineService.compile(tempDir);

        Integer vectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.article_vector_index",
                Integer.class
        );
        Integer chunkVectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.article_chunk_vector_index",
                Integer.class
        );

        assertThat(vectorCount).isEqualTo(1);
        assertThat(chunkVectorCount).isGreaterThan(0);
        assertThat(findEmbeddingColumnType("article_vector_index")).contains("vector(1024)");
        assertThat(findEmbeddingColumnType("article_chunk_vector_index")).contains("vector(1024)");
    }

    /**
     * 重置编译相关测试表。
     */
    private void resetCompileTables() {
        queryCacheStore.evictAll();
        jdbcTemplate.execute("TRUNCATE TABLE lattice.llm_model_profiles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.llm_provider_connections RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_chunk_vector_index");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_vector_index");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    /**
     * 写入测试用 embedding profile。
     */
    private void seedEmbeddingProfile() {
        seedEmbeddingProfile(1536);
    }

    /**
     * 写入测试用 embedding profile。
     *
     * @param expectedDimensions 期望维度
     */
    private void seedEmbeddingProfile(int expectedDimensions) {
        String encryptedApiKey = llmSecretCryptoService.encrypt("sk-test-openai");
        String maskedApiKey = llmSecretCryptoService.mask("sk-test-openai");
        jdbcTemplate.update(
                """
                        insert into lattice.llm_provider_connections (
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
                        insert into lattice.llm_model_profiles (
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

    /**
     * 断言向量能力与索引服务在当前测试上下文中可用。
     */
    private void assertVectorCapabilityReady() {
        assertThat(searchCapabilityService.supportsVectorType()).isTrue();
        assertThat(searchCapabilityService.hasArticleVectorIndex()).isTrue();
        assertThat(articleVectorIndexService.isIndexingAvailable()).isTrue();
    }

    /**
     * 读取指定向量表的 embedding 列类型。
     *
     * @param tableName 表名
     * @return 列类型
     */
    private String findEmbeddingColumnType(String tableName) {
        return jdbcTemplate.queryForObject(
                """
                        select format_type(a.atttypid, a.atttypmod)
                        from pg_attribute a
                        join pg_class c on c.oid = a.attrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = 'lattice'
                          and c.relname = ?
                          and a.attname = 'embedding'
                          and a.attnum > 0
                          and not a.attisdropped
                        """,
                String.class,
                tableName
        );
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
            return new EmbeddingResponse(List.of(new Embedding(createEmbedding(resolveDimensions(request)), 0)));
        }

        /**
         * 对文档执行 embedding。
         *
         * @param document 文档
         * @return embedding 向量
         */
        @Override
        public float[] embed(Document document) {
            return createEmbedding(1536);
        }

        /**
         * 根据请求中的 options 解析目标维度。
         *
         * @param request embedding 请求
         * @return 目标维度
         */
        private int resolveDimensions(EmbeddingRequest request) {
            if (request == null) {
                return 1536;
            }
            EmbeddingOptions embeddingOptions = request.getOptions();
            if (embeddingOptions instanceof OpenAiEmbeddingOptions openAiEmbeddingOptions
                    && openAiEmbeddingOptions.getDimensions() != null
                    && openAiEmbeddingOptions.getDimensions().intValue() > 0) {
                return openAiEmbeddingOptions.getDimensions().intValue();
            }
            return 1536;
        }

        /**
         * 创建指定维度的固定向量。
         *
         * @param dimensions 目标维度
         * @return 固定 embedding
         */
        private float[] createEmbedding(int dimensions) {
            float[] embedding = new float[dimensions];
            for (int index = 0; index < embedding.length; index++) {
                embedding[index] = 0.15F + (index % 11) * 0.01F;
            }
            return embedding;
        }
    }
}
