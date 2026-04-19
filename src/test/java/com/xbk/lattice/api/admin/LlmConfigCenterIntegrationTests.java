package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
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

    private HttpServer httpServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutionLlmSnapshotService executionLlmSnapshotService;

    /**
     * 关闭探测测试使用的本地 HTTP 服务。
     */
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

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

        mockMvc.perform(get("/api/v1/admin/llm/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].scene").value("compile"))
                .andExpect(jsonPath("$.items[0].agentRole").value("writer"))
                .andExpect(jsonPath("$.items[0].primaryModelProfileId").value(modelId));

        String storedCiphertext = jdbcTemplate.queryForObject(
                "select api_key_ciphertext from llm_provider_connections where id = ?",
                String.class,
                connectionId
        );
        assertThat(storedCiphertext).isNotEqualTo(rawApiKey);
        assertThat(storedCiphertext).doesNotContain(rawApiKey);

        assertThat(executionLlmSnapshotService.freezeSnapshots("compile_job", "job-1", "compile")).hasSize(1);
        Optional<LlmRouteResolution> writerRoute = executionLlmSnapshotService.resolveRoute(
                "compile_job",
                "job-1",
                "compile",
                "writer"
        );
        assertThat(writerRoute).isPresent();
        assertThat(writerRoute.orElseThrow().getRouteLabel()).isEqualTo("compile.writer.gpt54");
        assertThat(writerRoute.orElseThrow().getModelName()).isEqualTo("gpt-5.4");
        assertThat(writerRoute.orElseThrow().getApiKey()).isEqualTo(rawApiKey);
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

    /**
     * 验证精简后的内部页即使不提交 modelCode / routeLabel，也能由后台自动补齐。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldAutoGenerateModelCodeAndRouteLabelWhenUiOmitsAdvancedFields() throws Exception {
        resetTables();
        Long connectionId = createConnection("ask-main", "openai", "http://localhost:8888", "sk-auto-123456");

        String modelResponseBody = mockMvc.perform(post("/api/v1/admin/llm/models")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionId\":" + connectionId + ","
                                + "\"modelName\":\"gpt-5.4\","
                                + "\"modelKind\":\"CHAT\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelCode").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long modelId = readLong(modelResponseBody, "id");
        String modelCode = readString(modelResponseBody, "modelCode");

        String bindingResponseBody = mockMvc.perform(post("/api/v1/admin/llm/bindings")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"scene\":\"query\","
                                + "\"agentRole\":\"answer\","
                                + "\"primaryModelProfileId\":" + modelId + ","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeLabel").value("query.answer." + modelCode))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(readString(bindingResponseBody, "routeLabel")).isEqualTo("query.answer." + modelCode);

        assertThat(executionLlmSnapshotService.freezeSnapshots("query_request", "query-auto", "query")).hasSize(1);
        Optional<LlmRouteResolution> answerRoute = executionLlmSnapshotService.resolveRoute(
                "query_request",
                "query-auto",
                "query",
                "answer"
        );
        assertThat(answerRoute).isPresent();
        assertThat(answerRoute.orElseThrow().getRouteLabel()).isEqualTo("query.answer." + modelCode);
        assertThat(answerRoute.orElseThrow().getModelName()).isEqualTo("gpt-5.4");
    }

    /**
     * 验证 AI 接入页可直接测试未保存的 OpenAI 连接。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldProbeUnsavedOpenAiConnection() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<String>();
        int port = startJsonServer(
                "/v1/models",
                new FixedJsonHandler("{\"data\":[{\"id\":\"gpt-5.4\"}]}", "Authorization", authorizationHeader)
        );

        mockMvc.perform(post("/api/v1/admin/llm/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"providerType\":\"openai\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"apiKey\":\"sk-probe-123456\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("openai"))
                .andExpect(jsonPath("$.endpoint").value("/v1/models"))
                .andExpect(jsonPath("$.message").value(containsString("OpenAI 连接成功")));

        assertThat(authorizationHeader.get()).isEqualTo("Bearer sk-probe-123456");
    }

    /**
     * 验证测试连接时，编辑已有连接可复用已保存密钥而不要求重新输入。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldProbeSavedConnectionWithoutReEnteringApiKey() throws Exception {
        resetTables();
        AtomicReference<String> authorizationHeader = new AtomicReference<String>();
        int port = startJsonServer(
                "/v1/models",
                new FixedJsonHandler("{\"data\":[{\"id\":\"gpt-5.4\"},{\"id\":\"gpt-4.1\"}]}", "Authorization", authorizationHeader)
        );
        Long connectionId = createConnection(
                "probe-saved",
                "openai_compatible",
                "http://127.0.0.1:" + port,
                "sk-saved-123456"
        );

        mockMvc.perform(post("/api/v1/admin/llm/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionId\":" + connectionId
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("openai"))
                .andExpect(jsonPath("$.message").value(containsString("可访问 2 个模型")));

        assertThat(authorizationHeader.get()).isEqualTo("Bearer sk-saved-123456");
    }

    /**
     * 验证 AI 接入页可直接测试未保存的对话模型。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldProbeUnsavedChatModel() throws Exception {
        resetTables();
        AtomicReference<String> authorizationHeader = new AtomicReference<String>();
        int port = startJsonServer(
                "/v1/chat/completions",
                new FixedJsonHandler(
                        "{"
                                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"OK\"}}],"
                                + "\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":1}"
                                + "}",
                        "Authorization",
                        authorizationHeader
                )
        );
        Long connectionId = createConnection(
                "model-probe-openai",
                "openai",
                "http://127.0.0.1:" + port,
                "sk-model-123456"
        );

        mockMvc.perform(post("/api/v1/admin/llm/models/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionId\":" + connectionId + ","
                                + "\"modelName\":\"gpt-5.4\","
                                + "\"modelKind\":\"CHAT\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("openai"))
                .andExpect(jsonPath("$.modelKind").value("CHAT"))
                .andExpect(jsonPath("$.message").value(containsString("已返回对话结果")));

        assertThat(authorizationHeader.get()).isEqualTo("Bearer sk-model-123456");
    }

    /**
     * 验证 AI 接入页可测试已保存的向量模型，并检查返回维度。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldProbeSavedEmbeddingModel() throws Exception {
        resetTables();
        AtomicReference<String> authorizationHeader = new AtomicReference<String>();
        int port = startJsonServer(
                "/v1/embeddings",
                new FixedJsonHandler(
                        "{"
                                + "\"data\":[{\"index\":0,\"embedding\":[0.11,0.22,0.33]}],"
                                + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}"
                                + "}",
                        "Authorization",
                        authorizationHeader
                )
        );
        Long connectionId = createConnection(
                "embedding-probe-openai",
                "openai",
                "http://127.0.0.1:" + port,
                "sk-embed-123456"
        );
        String responseBody = mockMvc.perform(post("/api/v1/admin/llm/models")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionId\":" + connectionId + ","
                                + "\"modelName\":\"text-embedding-3-small\","
                                + "\"modelKind\":\"EMBEDDING\","
                                + "\"expectedDimensions\":3,"
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long modelId = readLong(responseBody, "id");

        mockMvc.perform(post("/api/v1/admin/llm/models/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"modelId\":" + modelId
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("openai"))
                .andExpect(jsonPath("$.modelKind").value("EMBEDDING"))
                .andExpect(jsonPath("$.message").value(containsString("已返回 3 维向量")));

        assertThat(authorizationHeader.get()).isEqualTo("Bearer sk-embed-123456");
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

    private String readString(String json, String fieldName) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        return rootNode.path(fieldName).asText();
    }

    /**
     * 启动返回固定 JSON 的本地 HTTP 服务。
     *
     * @param path 监听路径
     * @param handler 处理器
     * @return 随机端口
     * @throws IOException IO 异常
     */
    private int startJsonServer(String path, HttpHandler handler) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(path, handler);
        httpServer.start();
        return httpServer.getAddress().getPort();
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE execution_llm_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE agent_model_bindings RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE llm_model_profiles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE llm_provider_connections RESTART IDENTITY CASCADE");
    }

    /**
     * 固定 JSON 响应处理器
     *
     * 职责：记录指定请求头，并返回固定 JSON 内容
     *
     * @author xiexu
     */
    private static class FixedJsonHandler implements HttpHandler {

        private final String responseBody;

        private final String headerName;

        private final AtomicReference<String> capturedHeader;

        private FixedJsonHandler(
                String responseBody,
                String headerName,
                AtomicReference<String> capturedHeader
        ) {
            this.responseBody = responseBody;
            this.headerName = headerName;
            this.capturedHeader = capturedHeader;
        }

        /**
         * 处理测试请求并返回固定 JSON。
         *
         * @param exchange HTTP 交换对象
         * @throws IOException IO 异常
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            capturedHeader.set(exchange.getRequestHeaders().getFirst(headerName));
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
