package com.xbk.lattice.api.admin;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QuerySearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminVectorConfigController 测试
 *
 * 职责：验证管理侧可查看、保存并即时应用 query 向量配置
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.llm.secret-encryption-key=test-phase8-key-0123456789abcdef",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false",
        "lattice.query.search.vector.enabled=false",
        "lattice.query.search.vector.embedding-model-profile-id=1",
        "lattice.query.search.vector.embedding-model=bootstrap-embedding-model",
        "lattice.query.search.vector.expected-dimensions=1536"
})
@AutoConfigureMockMvc
class AdminVectorConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QuerySearchProperties querySearchProperties;

    @Autowired
    private LlmSecretCryptoService llmSecretCryptoService;

    @Autowired
    private QueryCacheStore queryCacheStore;

    /**
     * 验证默认返回 application.yml / 环境变量中的向量配置。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposePropertyBackedVectorConfigWhenNoDatabaseOverride() throws Exception {
        resetTables();

        mockMvc.perform(get("/api/v1/admin/vector/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorEnabled").value(false))
                .andExpect(jsonPath("$.embeddingModelProfileId").value(1))
                .andExpect(jsonPath("$.providerType").value("openai"))
                .andExpect(jsonPath("$.modelName").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.profileDimensions").value(1536))
                .andExpect(jsonPath("$.configSource").value("properties"))
                .andExpect(jsonPath("$.rebuildRecommended").value(false));
    }

    /**
     * 验证未绑定 profile 时，管理侧仍能展示 properties 提供的 legacy 向量信息。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeLegacyPropertyVectorSummaryWithoutProfileOverride() throws Exception {
        resetTables();
        querySearchProperties.getVector().setEnabled(true);
        querySearchProperties.getVector().setEmbeddingModelProfileId(null);

        mockMvc.perform(get("/api/v1/admin/vector/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorEnabled").value(true))
                .andExpect(jsonPath("$.providerType").value("legacy"))
                .andExpect(jsonPath("$.modelName").value("bootstrap-embedding-model"))
                .andExpect(jsonPath("$.profileDimensions").value(1536))
                .andExpect(jsonPath("$.configSource").value("properties"));
    }

    /**
     * 验证保存后的向量配置会落库并立即作用于运行时状态。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldPersistVectorConfigAndApplyToRuntimeImmediately() throws Exception {
        resetTables();
        queryCacheStore.put("cached-question", new QueryResponse("cached", List.of(), List.of(), null, "PASSED"));

        mockMvc.perform(put("/api/v1/admin/vector/config")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"vectorEnabled\":true,"
                                + "\"embeddingModelProfileId\":2,"
                                + "\"operator\":\"tester\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorEnabled").value(true))
                .andExpect(jsonPath("$.embeddingModelProfileId").value(2))
                .andExpect(jsonPath("$.providerType").value("openai"))
                .andExpect(jsonPath("$.modelName").value("text-embedding-3-large"))
                .andExpect(jsonPath("$.profileDimensions").value(3072))
                .andExpect(jsonPath("$.configSource").value("database"))
                .andExpect(jsonPath("$.rebuildRecommended").value(true))
                .andExpect(jsonPath("$.rebuildReason").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("tester"));

        assertThat(querySearchProperties.getVector().isEnabled()).isTrue();
        assertThat(querySearchProperties.getVector().getEmbeddingModelProfileId()).isEqualTo(Long.valueOf(2L));
        assertThat(queryCacheStore.get("cached-question")).isEmpty();

        mockMvc.perform(get("/api/v1/admin/vector/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorEnabled").value(true))
                .andExpect(jsonPath("$.embeddingModelProfileId").value(2))
                .andExpect(jsonPath("$.configuredModelName").value("text-embedding-3-large"))
                .andExpect(jsonPath("$.configuredExpectedDimensions").value(3072))
                .andExpect(jsonPath("$.profileDimensions").value(3072));

        Boolean vectorEnabled = jdbcTemplate.queryForObject(
                "select vector_enabled from lattice.query_vector_settings where config_scope = 'default'",
                Boolean.class
        );
        Long embeddingModelProfileId = jdbcTemplate.queryForObject(
                "select embedding_model_profile_id from lattice.query_vector_settings where config_scope = 'default'",
                Long.class
        );
        assertThat(vectorEnabled).isTrue();
        assertThat(embeddingModelProfileId).isEqualTo(Long.valueOf(2L));
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_vector_settings CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.llm_model_profiles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.llm_provider_connections RESTART IDENTITY CASCADE");
        insertProviderConnection();
        insertEmbeddingProfile(1L, "bootstrap-embedding", "text-embedding-3-small", 1536);
        insertEmbeddingProfile(2L, "large-embedding", "text-embedding-3-large", 3072);
        querySearchProperties.getVector().setEnabled(false);
        querySearchProperties.getVector().setEmbeddingModelProfileId(Long.valueOf(1L));
        querySearchProperties.getVector().setEmbeddingModel("bootstrap-embedding-model");
        querySearchProperties.getVector().setExpectedDimensions(1536);
    }

    private void insertProviderConnection() {
        String encryptedApiKey = llmSecretCryptoService.encrypt("sk-test-openai");
        String maskedApiKey = llmSecretCryptoService.mask("sk-test-openai");
        jdbcTemplate.update(
                """
                        insert into lattice.llm_provider_connections (
                            id, connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask,
                            enabled, created_by, updated_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Long.valueOf(1L),
                "openai-main",
                "openai",
                "http://localhost:18080",
                encryptedApiKey,
                maskedApiKey,
                true,
                "tester",
                "tester"
        );
    }

    private void insertEmbeddingProfile(Long id, String modelCode, String modelName, int expectedDimensions) {
        jdbcTemplate.update(
                """
                        insert into lattice.llm_model_profiles (
                            id, model_code, connection_id, model_name, model_kind, expected_dimensions,
                            supports_dimension_override, enabled, created_by, updated_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                modelCode,
                Long.valueOf(1L),
                modelName,
                "EMBEDDING",
                Integer.valueOf(expectedDimensions),
                false,
                true,
                "tester",
                "tester"
        );
    }
}
