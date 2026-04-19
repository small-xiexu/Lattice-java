package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文档解析配置中心集成测试
 *
 * 职责：验证文档解析连接、连接测试与全局设置保存链路
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_phase_b_document_parse_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_phase_b_document_parse_test",
        "spring.flyway.default-schema=lattice_phase_b_document_parse_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.llm.secret-encryption-key=test-phase-b-key-0123456789abcdef"
})
@AutoConfigureMockMvc
class DocumentParseConfigIntegrationTests {

    private HttpServer httpServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 关闭测试使用的本地 HTTP 服务。
     */
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    /**
     * 验证后台可创建文档解析连接、探测连接并保存全局设置。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateProbeAndPersistDocumentParseConfig() throws Exception {
        resetTables();
        AtomicReference<String> capturedAuthorization = new AtomicReference<String>("");
        int port = startHttpServer(capturedAuthorization);
        Long cleanupModelId = createCleanupModel("http://127.0.0.1:" + port);
        String rawCredential = "{\"apiKey\":\"doc-parse-token-123456\"}";

        String createConnectionResponse = mockMvc.perform(post("/api/v1/admin/document-parse/connections")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionCode\":\"tencent-ocr-main\","
                                + "\"providerType\":\"tencent_ocr\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"endpointPath\":\"/ocr/v1/general-basic\","
                                + "\"credential\":" + quote(rawCredential) + ","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionCode").value("tencent-ocr-main"))
                .andExpect(jsonPath("$.credentialMask").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long connectionId = readLong(createConnectionResponse, "id");

        mockMvc.perform(get("/api/v1/admin/document-parse/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].providerType").value("tencent_ocr"))
                .andExpect(jsonPath("$.items[0].endpointPath").value("/ocr/v1/general-basic"));

        String storedCiphertext = jdbcTemplate.queryForObject(
                "select credential_ciphertext from document_parse_provider_connections where id = ?",
                String.class,
                connectionId
        );
        assertThat(storedCiphertext).isNotEqualTo(rawCredential);
        assertThat(storedCiphertext).doesNotContain(rawCredential);

        mockMvc.perform(post("/api/v1/admin/document-parse/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionId\":" + connectionId
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.endpoint").value("http://127.0.0.1:" + port + "/ocr/v1/general-basic"));
        assertThat(capturedAuthorization.get()).isEqualTo("Bearer doc-parse-token-123456");

        mockMvc.perform(put("/api/v1/admin/document-parse/settings")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"defaultConnectionId\":" + connectionId + ","
                                + "\"imageOcrEnabled\":true,"
                                + "\"scannedPdfOcrEnabled\":true,"
                                + "\"cleanupEnabled\":true,"
                                + "\"cleanupModelProfileId\":" + cleanupModelId
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultConnectionId").value(connectionId))
                .andExpect(jsonPath("$.imageOcrEnabled").value(true))
                .andExpect(jsonPath("$.scannedPdfOcrEnabled").value(true))
                .andExpect(jsonPath("$.cleanupEnabled").value(true))
                .andExpect(jsonPath("$.cleanupModelProfileId").value(cleanupModelId));

        mockMvc.perform(get("/api/v1/admin/document-parse/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configScope").value("default"))
                .andExpect(jsonPath("$.defaultConnectionId").value(connectionId))
                .andExpect(jsonPath("$.cleanupModelProfileId").value(cleanupModelId));
    }

    private Long createCleanupModel(String baseUrl) throws Exception {
        String connectionResponse = mockMvc.perform(post("/api/v1/admin/llm/connections")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionCode\":\"cleanup-llm\","
                                + "\"providerType\":\"openai\","
                                + "\"baseUrl\":\"" + baseUrl + "\","
                                + "\"apiKey\":\"sk-cleanup-123456\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long connectionId = readLong(connectionResponse, "id");

        String modelResponse = mockMvc.perform(post("/api/v1/admin/llm/models")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionId\":" + connectionId + ","
                                + "\"modelName\":\"gpt-5.4\","
                                + "\"modelKind\":\"CHAT\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return readLong(modelResponse, "id");
    }

    private int startHttpServer(AtomicReference<String> capturedAuthorization) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/models", new JsonHandler("""
                {"data":[{"id":"gpt-5.4"}]}
                """));
        httpServer.createContext("/ocr/v1/general-basic", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] responseBytes = """
                        {"status":"ok"}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            }
        });
        httpServer.start();
        return httpServer.getAddress().getPort();
    }

    private void resetTables() {
        jdbcTemplate.execute("delete from document_parse_settings");
        jdbcTemplate.execute("delete from document_parse_provider_connections");
        jdbcTemplate.execute("delete from execution_llm_snapshots");
        jdbcTemplate.execute("delete from agent_model_bindings");
        jdbcTemplate.execute("delete from llm_model_profiles");
        jdbcTemplate.execute("delete from llm_provider_connections");
    }

    private Long readLong(String responseBody, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path(fieldName).asLong();
    }

    private String quote(String value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static class JsonHandler implements HttpHandler {

        private final byte[] payload;

        private JsonHandler(String body) {
            this.payload = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        }
    }
}
