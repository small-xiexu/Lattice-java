package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LLM 配置中心集成测试
 *
 * 职责：验证后台连接/模型/绑定配置与 compile 侧快照冻结契约
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_phase8_llm_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_phase8_llm_test",
        "spring.flyway.default-schema=lattice_phase8_llm_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.llm.secret-encryption-key=test-phase8-key-0123456789abcdef"
})
@AutoConfigureMockMvc
class LlmConfigCenterIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutionLlmSnapshotService executionLlmSnapshotService;

    /**
     * 验证后台可创建连接、模型与绑定，并冻结 compile 快照。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateMaskedConnectionModelBindingAndFreezeSnapshots() throws Exception {
        resetTables();
        String rawApiKey = "sk-compile-1234567890";
        Long connectionId = createConnection("openai-main", "openai", "http://localhost:8888", rawApiKey);
        Long modelId = createModel("gpt54-compile", connectionId, "gpt-5.4", "0.2", "4096", "300");
        createBinding("compile", "writer", modelId, "", "compile.writer.gpt54");

        mockMvc.perform(get("/api/v1/admin/llm/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].apiKeyMask").isNotEmpty());

        String storedCiphertext = jdbcTemplate.queryForObject(
                "select api_key_ciphertext from llm_provider_connections where id = ?",
                String.class,
                connectionId
        );
        assertThat(storedCiphertext).isNotEqualTo(rawApiKey);
        assertThat(storedCiphertext).doesNotContain(rawApiKey);

        assertThat(executionLlmSnapshotService.freezeSnapshots("compile_job", "job-1", "compile")).hasSize(1);
        Optional<LlmRouteResolution> route = executionLlmSnapshotService.resolveRoute(
                "compile_job",
                "job-1",
                "compile",
                "writer"
        );
        assertThat(route).isPresent();
        assertThat(route.orElseThrow().getRouteLabel()).isEqualTo("compile.writer.gpt54");
        assertThat(route.orElseThrow().getModelName()).isEqualTo("gpt-5.4");
        assertThat(route.orElseThrow().getApiKey()).isEqualTo(rawApiKey);
    }

    /**
     * 验证更新绑定后只影响新任务快照，不污染旧任务。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldOnlyAffectNewSnapshotsWhenBindingChanges() throws Exception {
        resetTables();
        Long connectionId = createConnection("shared-main", "openai_compatible", "http://localhost:8888", "sk-shared-123456");
        Long modelOneId = createModel("claude-review", connectionId, "claude-3-7-sonnet", "0.1", "2048", "300");
        Long modelTwoId = createModel("codex-review", connectionId, "gpt-5.4", "0.1", "2048", "300");
        Long bindingId = createBinding("compile", "reviewer", modelOneId, "", "compile.reviewer.claude");

        executionLlmSnapshotService.freezeSnapshots("compile_job", "job-old", "compile");

        mockMvc.perform(put("/api/v1/admin/llm/bindings/" + bindingId)
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"scene\":\"compile\","
                                + "\"agentRole\":\"reviewer\","
                                + "\"primaryModelProfileId\":" + modelTwoId + ","
                                + "\"routeLabel\":\"compile.reviewer.codex\","
                                + "\"operator\":\"admin\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeLabel").value("compile.reviewer.codex"));

        executionLlmSnapshotService.freezeSnapshots("compile_job", "job-new", "compile");

        Optional<LlmRouteResolution> oldRoute = executionLlmSnapshotService.resolveRoute(
                "compile_job",
                "job-old",
                "compile",
                "reviewer"
        );
        Optional<LlmRouteResolution> newRoute = executionLlmSnapshotService.resolveRoute(
                "compile_job",
                "job-new",
                "compile",
                "reviewer"
        );
        assertThat(oldRoute).isPresent();
        assertThat(newRoute).isPresent();
        assertThat(oldRoute.orElseThrow().getRouteLabel()).isEqualTo("compile.reviewer.claude");
        assertThat(oldRoute.orElseThrow().getModelName()).isEqualTo("claude-3-7-sonnet");
        assertThat(newRoute.orElseThrow().getRouteLabel()).isEqualTo("compile.reviewer.codex");
        assertThat(newRoute.orElseThrow().getModelName()).isEqualTo("gpt-5.4");
    }

    /**
     * 验证 query 场景也可复用同一套绑定中心并冻结问答快照。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateQueryBindingsAndFreezeQuerySnapshots() throws Exception {
        resetTables();
        Long answerConnectionId = createConnection("query-main", "openai", "http://localhost:8888", "sk-query-123456");
        Long reviewConnectionId = createConnection("query-review-main", "anthropic", "http://localhost:9999", "sk-claude-123456");
        Long answerModelId = createModel("gpt54-query-answer", answerConnectionId, "gpt-5.4", "0.2", "4096", "300");
        Long reviewerModelId = createModel("claude-query-reviewer", reviewConnectionId, "claude-3-7-sonnet", "0.0", "4096", "300");
        Long rewriteModelId = createModel("gpt54-query-rewrite", answerConnectionId, "gpt-5.4", "0.1", "4096", "300");

        createBinding("query", "answer", answerModelId, "", "query.answer.gpt54");
        createBinding("query", "reviewer", reviewerModelId, "", "query.reviewer.claude");
        createBinding("query", "rewrite", rewriteModelId, "", "query.rewrite.gpt54");

        mockMvc.perform(get("/api/v1/admin/llm/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.scene=='query' && @.agentRole=='answer')]").exists())
                .andExpect(jsonPath("$.items[?(@.scene=='query' && @.agentRole=='reviewer')]").exists())
                .andExpect(jsonPath("$.items[?(@.scene=='query' && @.agentRole=='rewrite')]").exists());

        assertThat(executionLlmSnapshotService.freezeSnapshots("query_request", "query-1", "query")).hasSize(3);

        Optional<LlmRouteResolution> answerRoute = executionLlmSnapshotService.resolveRoute(
                "query_request",
                "query-1",
                "query",
                "answer"
        );
        Optional<LlmRouteResolution> reviewerRoute = executionLlmSnapshotService.resolveRoute(
                "query_request",
                "query-1",
                "query",
                "reviewer"
        );
        Optional<LlmRouteResolution> rewriteRoute = executionLlmSnapshotService.resolveRoute(
                "query_request",
                "query-1",
                "query",
                "rewrite"
        );
        assertThat(answerRoute).isPresent();
        assertThat(reviewerRoute).isPresent();
        assertThat(rewriteRoute).isPresent();
        assertThat(answerRoute.orElseThrow().getRouteLabel()).isEqualTo("query.answer.gpt54");
        assertThat(reviewerRoute.orElseThrow().getRouteLabel()).isEqualTo("query.reviewer.claude");
        assertThat(reviewerRoute.orElseThrow().getProviderType()).isEqualTo("anthropic");
        assertThat(reviewerRoute.orElseThrow().getModelName()).isEqualTo("claude-3-7-sonnet");
        assertThat(rewriteRoute.orElseThrow().getRouteLabel()).isEqualTo("query.rewrite.gpt54");
    }

    private Long createConnection(String code, String providerType, String baseUrl, String apiKey) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/admin/llm/connections")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionCode\":\"" + code + "\","
                                + "\"providerType\":\"" + providerType + "\","
                                + "\"baseUrl\":\"" + baseUrl + "\","
                                + "\"apiKey\":\"" + apiKey + "\","
                                + "\"operator\":\"admin\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return readLong(responseBody, "id");
    }

    private Long createModel(
            String modelCode,
            Long connectionId,
            String modelName,
            String temperature,
            String maxTokens,
            String timeoutSeconds
    ) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/admin/llm/models")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"modelCode\":\"" + modelCode + "\","
                                + "\"connectionId\":" + connectionId + ","
                                + "\"modelName\":\"" + modelName + "\","
                                + "\"temperature\":" + temperature + ","
                                + "\"maxTokens\":" + maxTokens + ","
                                + "\"timeoutSeconds\":" + timeoutSeconds + ","
                                + "\"inputPricePer1kTokens\":0.002500,"
                                + "\"outputPricePer1kTokens\":0.010000,"
                                + "\"operator\":\"admin\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return readLong(responseBody, "id");
    }

    private Long createBinding(
            String scene,
            String agentRole,
            Long modelId,
            String fallbackModelId,
            String routeLabel
    ) throws Exception {
        String fallbackField = fallbackModelId == null || fallbackModelId.isBlank()
                ? ""
                : "\"fallbackModelProfileId\":" + fallbackModelId + ",";
        String responseBody = mockMvc.perform(post("/api/v1/admin/llm/bindings")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"scene\":\"" + scene + "\","
                                + "\"agentRole\":\"" + agentRole + "\","
                                + "\"primaryModelProfileId\":" + modelId + ","
                                + fallbackField
                                + "\"routeLabel\":\"" + routeLabel + "\","
                                + "\"operator\":\"admin\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return readLong(responseBody, "id");
    }

    private Long readLong(String json, String fieldName) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        return Long.valueOf(rootNode.path(fieldName).asLong());
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE execution_llm_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE agent_model_bindings RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE llm_model_profiles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE llm_provider_connections RESTART IDENTITY CASCADE");
    }
}
