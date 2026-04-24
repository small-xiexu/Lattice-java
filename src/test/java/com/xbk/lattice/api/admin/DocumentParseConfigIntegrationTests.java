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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        AtomicReference<String> capturedSecretId = new AtomicReference<String>("");
        AtomicReference<String> capturedSecretKey = new AtomicReference<String>("");
        int port = startHttpServer(capturedSecretId, capturedSecretKey);
        Long cleanupModelId = createCleanupModel("http://127.0.0.1:" + port);
        String rawCredential = "{\"secretId\":\"doc-parse-id-123456\",\"secretKey\":\"doc-parse-key-654321\"}";

        mockMvc.perform(get("/api/v1/admin/document-parse/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.items[0].providerType").value("tencent_ocr"))
                .andExpect(jsonPath("$.items[3].providerType").value("textin_xparse"));

        String createConnectionResponse = mockMvc.perform(post("/api/v1/admin/document-parse/connections")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionCode\":\"tencent-ocr-main\","
                                + "\"providerType\":\"tencent_ocr\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"credentialJson\":" + quote(rawCredential) + ","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/ocr/v1/general-basic\\\"}\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionCode").value("tencent-ocr-main"))
                .andExpect(jsonPath("$.credentialMask").isNotEmpty())
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long connectionId = readLong(createConnectionResponse, "id");

        mockMvc.perform(get("/api/v1/admin/document-parse/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].providerType").value("tencent_ocr"))
                .andExpect(jsonPath("$.items[0].configJson").value("{\"endpointPath\":\"/ocr/v1/general-basic\"}"));

        String storedCiphertext = jdbcTemplate.queryForObject(
                "select credential_ciphertext from document_parse_connections where id = ?",
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
        assertThat(capturedSecretId.get()).isEqualTo("doc-parse-id-123456");
        assertThat(capturedSecretKey.get()).isEqualTo("doc-parse-key-654321");

        mockMvc.perform(put("/api/v1/admin/document-parse/policies/default")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"imageConnectionId\":" + connectionId + ","
                                + "\"scannedPdfConnectionId\":" + connectionId + ","
                                + "\"cleanupEnabled\":true,"
                                + "\"cleanupModelProfileId\":" + cleanupModelId
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyScope").value("default"))
                .andExpect(jsonPath("$.imageConnectionId").value(connectionId))
                .andExpect(jsonPath("$.scannedPdfConnectionId").value(connectionId))
                .andExpect(jsonPath("$.cleanupEnabled").value(true))
                .andExpect(jsonPath("$.cleanupModelProfileId").value(cleanupModelId));

        mockMvc.perform(get("/api/v1/admin/document-parse/policies/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyScope").value("default"))
                .andExpect(jsonPath("$.imageConnectionId").value(connectionId))
                .andExpect(jsonPath("$.scannedPdfConnectionId").value(connectionId))
                .andExpect(jsonPath("$.cleanupModelProfileId").value(cleanupModelId));
    }

    /**
     * 验证 TextIn xParse 连接测试会走官方 multipart 协议。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldProbeTextInConnectionThroughMultipartProtocol() throws Exception {
        resetTables();
        AtomicReference<String> capturedAppId = new AtomicReference<String>("");
        AtomicReference<String> capturedSecretCode = new AtomicReference<String>("");
        AtomicReference<String> capturedBody = new AtomicReference<String>("");
        int port = startTextInHttpServer(capturedAppId, capturedSecretCode, capturedBody);

        mockMvc.perform(post("/api/v1/admin/document-parse/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"providerType\":\"textin_xparse\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"credentialJson\":\"{\\\"appId\\\":\\\"textin-app-id\\\",\\\"secretCode\\\":\\\"textin-secret-code\\\"}\","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/api/v1/xparse/parse/sync\\\",\\\"parseConfigJson\\\":\\\"{\\\\\\\"parse_mode\\\\\\\":\\\\\\\"scan\\\\\\\"}\\\"}\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("textin_xparse"))
                .andExpect(jsonPath("$.endpoint").value("http://127.0.0.1:" + port + "/api/v1/xparse/parse/sync"));

        assertThat(capturedAppId.get()).isEqualTo("textin-app-id");
        assertThat(capturedSecretCode.get()).isEqualTo("textin-secret-code");
        assertThat(capturedBody.get()).contains("name=\"file\"");
        assertThat(capturedBody.get()).contains("filename=\"probe.pdf\"");
        assertThat(capturedBody.get()).contains("name=\"config\"");
        assertThat(capturedBody.get()).contains("{\"parse_mode\":\"scan\"}");
    }

    /**
     * 验证阿里云 OCR 与 Google Document AI 已迁移到独立 Adapter。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldProbeAliyunAndGoogleAdaptersWithoutRegression() throws Exception {
        resetTables();
        AtomicReference<String> capturedAliyunAccessKeyId = new AtomicReference<String>("");
        AtomicReference<String> capturedAliyunAccessKeySecret = new AtomicReference<String>("");
        AtomicReference<String> capturedGoogleAuthorization = new AtomicReference<String>("");
        AtomicReference<String> capturedGoogleProjectId = new AtomicReference<String>("");
        int port = startMultiProviderHttpServer(
                capturedAliyunAccessKeyId,
                capturedAliyunAccessKeySecret,
                capturedGoogleAuthorization,
                capturedGoogleProjectId
        );

        mockMvc.perform(post("/api/v1/admin/document-parse/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"providerType\":\"aliyun_ocr\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"credentialJson\":\"{\\\"accessKeyId\\\":\\\"aliyun-ak\\\",\\\"accessKeySecret\\\":\\\"aliyun-sk\\\"}\","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/ocr/v1/general\\\"}\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("aliyun_ocr"));

        mockMvc.perform(post("/api/v1/admin/document-parse/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"providerType\":\"google_document_ai\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"credentialJson\":\"{\\\"bearerToken\\\":\\\"google-bearer-token\\\",\\\"projectId\\\":\\\"gcp-project-id\\\"}\","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/v1/documents:process\\\"}\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("google_document_ai"));

        assertThat(capturedAliyunAccessKeyId.get()).isEqualTo("aliyun-ak");
        assertThat(capturedAliyunAccessKeySecret.get()).isEqualTo("aliyun-sk");
        assertThat(capturedGoogleAuthorization.get()).isEqualTo("Bearer google-bearer-token");
        assertThat(capturedGoogleProjectId.get()).isEqualTo("gcp-project-id");
    }

    /**
     * 验证连接测试失败时会返回可读报错。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldReturnReadableProbeFailureMessageWhenTextInProbeFails() throws Exception {
        resetTables();
        int port = startFailingTextInHttpServer();

        String responseBody = mockMvc.perform(post("/api/v1/admin/document-parse/connections/test")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"providerType\":\"textin_xparse\","
                                + "\"baseUrl\":\"http://127.0.0.1:" + port + "\","
                                + "\"credentialJson\":\"{\\\"appId\\\":\\\"textin-app-id\\\",\\\"secretCode\\\":\\\"bad-secret\\\"}\","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/api/v1/xparse/parse/sync\\\"}\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.providerType").value("textin_xparse"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode result = objectMapper.readTree(responseBody);
        assertThat(result.path("message").asText())
                .contains("TextIn xParse")
                .contains("invalid textin secret");
    }

    /**
     * 验证管理侧连接支持创建、编辑和删除，并在删除时清理默认策略引用。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateUpdateAndDeleteDocumentParseConnection() throws Exception {
        resetTables();
        String rawCredential = "{\"appId\":\"textin-app-id\",\"secretCode\":\"textin-secret-code\"}";

        String createResponse = mockMvc.perform(post("/api/v1/admin/document-parse/connections")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionCode\":\"textin-main\","
                                + "\"providerType\":\"textin_xparse\","
                                + "\"baseUrl\":\"https://api.textin.com\","
                                + "\"credentialJson\":" + quote(rawCredential) + ","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/api/v1/xparse/parse/sync\\\"}\","
                                + "\"enabled\":true"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionCode").value("textin-main"))
                .andExpect(jsonPath("$.providerType").value("textin_xparse"))
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long connectionId = readLong(createResponse, "id");
        String originalCiphertext = jdbcTemplate.queryForObject(
                "select credential_ciphertext from document_parse_connections where id = ?",
                String.class,
                connectionId
        );

        mockMvc.perform(put("/api/v1/admin/document-parse/policies/default")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"imageConnectionId\":" + connectionId + ","
                                + "\"scannedPdfConnectionId\":" + connectionId + ","
                                + "\"cleanupEnabled\":false"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageConnectionId").value(connectionId))
                .andExpect(jsonPath("$.scannedPdfConnectionId").value(connectionId));

        mockMvc.perform(put("/api/v1/admin/document-parse/connections/" + connectionId)
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"connectionCode\":\"textin-main-v2\","
                                + "\"providerType\":\"textin_xparse\","
                                + "\"baseUrl\":\"https://api.textin.com/\","
                                + "\"configJson\":\"{\\\"endpointPath\\\":\\\"/api/v1/xparse/parse/sync\\\",\\\"parseConfigJson\\\":\\\"{\\\\\\\"parse_mode\\\\\\\":\\\\\\\"scan\\\\\\\"}\\\"}\","
                                + "\"enabled\":false"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionCode").value("textin-main-v2"))
                .andExpect(jsonPath("$.baseUrl").value("https://api.textin.com"))
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.configJson").value("{\"endpointPath\":\"/api/v1/xparse/parse/sync\",\"parseConfigJson\":\"{\\\"parse_mode\\\":\\\"scan\\\"}\"}"));

        String updatedCiphertext = jdbcTemplate.queryForObject(
                "select credential_ciphertext from document_parse_connections where id = ?",
                String.class,
                connectionId
        );
        assertThat(updatedCiphertext).isEqualTo(originalCiphertext);

        mockMvc.perform(get("/api/v1/admin/document-parse/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].connectionCode").value("textin-main-v2"))
                .andExpect(jsonPath("$.items[0].enabled").value(false));

        mockMvc.perform(delete("/api/v1/admin/document-parse/connections/" + connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(connectionId))
                .andExpect(jsonPath("$.status").value("deleted"));

        mockMvc.perform(get("/api/v1/admin/document-parse/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        String policyBody = mockMvc.perform(get("/api/v1/admin/document-parse/policies/default"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode policy = objectMapper.readTree(policyBody);
        assertThat(policy.path("imageConnectionId").isNull()).isTrue();
        assertThat(policy.path("scannedPdfConnectionId").isNull()).isTrue();
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

    private int startHttpServer(
            AtomicReference<String> capturedSecretId,
            AtomicReference<String> capturedSecretKey
    ) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/models", new JsonHandler("""
                {"data":[{"id":"gpt-5.4"}]}
                """));
        httpServer.createContext("/ocr/v1/general-basic", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedSecretId.set(exchange.getRequestHeaders().getFirst("x-secret-id"));
                capturedSecretKey.set(exchange.getRequestHeaders().getFirst("x-secret-key"));
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

    private int startTextInHttpServer(
            AtomicReference<String> capturedAppId,
            AtomicReference<String> capturedSecretCode,
            AtomicReference<String> capturedBody
    ) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/xparse/parse/sync", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedAppId.set(exchange.getRequestHeaders().getFirst("x-ti-app-id"));
                capturedSecretCode.set(exchange.getRequestHeaders().getFirst("x-ti-secret-code"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
                byte[] responseBytes = """
                        {"code":200,"msg":"ok","data":{"markdown":"# probe\\ntextin ready"}}
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

    private int startMultiProviderHttpServer(
            AtomicReference<String> capturedAliyunAccessKeyId,
            AtomicReference<String> capturedAliyunAccessKeySecret,
            AtomicReference<String> capturedGoogleAuthorization,
            AtomicReference<String> capturedGoogleProjectId
    ) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ocr/v1/general", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedAliyunAccessKeyId.set(exchange.getRequestHeaders().getFirst("x-access-key-id"));
                capturedAliyunAccessKeySecret.set(exchange.getRequestHeaders().getFirst("x-access-key-secret"));
                byte[] responseBytes = """
                        {"status":"ok"}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            }
        });
        httpServer.createContext("/v1/documents:process", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedGoogleAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                capturedGoogleProjectId.set(exchange.getRequestHeaders().getFirst("x-project-id"));
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

    private int startFailingTextInHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/xparse/parse/sync", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] responseBytes = """
                        {"code":401,"msg":"invalid textin secret"}
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
        jdbcTemplate.execute("delete from document_parse_route_policies");
        jdbcTemplate.execute("delete from document_parse_connections");
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
